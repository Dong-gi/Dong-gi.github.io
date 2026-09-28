package io.github.donggi.iroiroviewer.comic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 쪽 목록(격자)의 **썸네일**을 청하고 든다(14단계). 9단계가 'solid 아카이브에서 격자 한 화면은 패스 한 번이라 값이
 * 크다' 며 미뤄 둔 것이다. **안드로이드를 모른다** — 디코딩은 부르는 쪽이 [run] 에 함수로 넘긴다. 그래서 무엇을 언제
 * 받고 무엇을 버리는지가 JVM 시험으로 선다.
 *
 * ## 무엇을 받는가 — 보이는 자리만
 *
 * 격자가 보이는 칸의 범위(앞뒤 여유 포함)를 [setWanted] 로 알린다. 300쪽을 전부 줄여 두지 않는다 — 그 값은 격자를
 * 열 때마다 책 한 권을 다 푸는 일이고, 대부분은 보지도 않는다.
 *
 * - 무작위 접근(ZIP·폴더): **보이는 칸을 먼저**(위에서 아래로), 그다음 아래 여유, 마지막으로 위 여유(가까운 것부터)를
 *   하나씩 읽는다. ZIP 의 쪽 하나는 수백 ms 가 들 수 있어(9단계 실측) 차례가 곧 체감이다.
 * - solid(7z·RAR): 아카이브에 적힌 차례로 **한 번 훑으며** 원하는 쪽을 모두 받는다(`SolidComicSource.scan`).
 *   쪽마다 패스를 돌리면 창 하나에 패스 하나라 뒤쪽 쪽일수록 제곱으로 비싸다. 훑는 도중 원하는 범위가 **앞으로**
 *   옮겨 가면 같은 훑기가 이어 받고, **뒤로** 옮겨 가 이미 지나간 자리를 원하면 그 훑기가 끝난 뒤 한 번 더 훑는다.
 *   **격자를 여는 값은 '보이는 칸 가운데 가장 뒤의 쪽까지 푸는 시간' 이다** — 앞쪽이면 곧, 300쪽짜리의 끝이면
 *   9단계 실측의 창 밖 패스만큼(1~2초) 걸린다. 칸은 받는 대로 하나씩 채워진다.
 *
 * ## 메모리 — 상한 안에서만, 닫으면 전부 놓는다
 *
 * 썸네일 전체의 바이트를 [capBytes] 로 묶는다. 넘으면 **원하는 범위의 가운데에서 가장 먼 것**부터 놓는다. 원하는 범위
 * 조차 상한에 다 들지 않으면(힙이 작은 기기) 그 칸은 이번 범위 동안 다시 청하지 않는다 — 받고 버리고 다시 받는 되풀이를
 * 막는다. 쪽 원본은 **디스크에도 창에도 남지 않는다**(CLAUDE.md '만화 쪽을 디스크에 쓰지 않는다').
 *
 * @param capBytes 썸네일 전체의 상한. 0 이하면 아무것도 받지 않는다 — 칸에 쪽 번호만 보인다.
 * @param sizeOf 썸네일 하나의 바이트.
 */
internal class PageThumbs<T : Any>(
    val pageCount: Int,
    private val capBytes: Long,
    private val sizeOf: (T) -> Long,
) : ScanDemand {

    private val lock = Any()

    private var wanted: IntRange = IntRange.EMPTY

    /** 지금 화면에 보이는 칸. [wanted] 안에 있다. 먼저 받고, 상한에서 가장 늦게 놓는다. */
    private var visible: IntRange = IntRange.EMPTY
    private var demandVersion = 0L
    private val thumbs = HashMap<Int, T>()
    private var heldBytes = 0L
    private val failed = HashSet<Int>()

    /** 이번 범위 동안 다시 청하지 않을 쪽 — 상한 때문에 놓았거나 한 장이 상한보다 컸다. */
    private val dropped = HashSet<Int>()

    /** 받은(실패 포함) 횟수. [run] 이 '이번 훑기가 아무것도 못 했는가' 를 가르는 데 쓴다. */
    private var deliveries = 0L

    private val _version = MutableStateFlow(0L)

    /** 칸이 다시 그려야 할 때 바뀐다. 화면은 이 값을 읽고 [get] 으로 꺼낸다. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** 원하는 범위가 바뀌었다는 신호. [run] 이 기다린다. 일련번호다 — 같은 값은 합쳐지므로(함정 표). */
    private val demandSignal = MutableStateFlow(0L)

    /** 지금 들고 있는 썸네일의 바이트. 시험이 상한을 단언한다. */
    val bytesHeld: Long get() = synchronized(lock) { heldBytes }

    /**
     * 격자가 원하는 범위를 알린다. [range] 는 앞뒤 여유를 포함한 것, [shown] 은 그 가운데 **지금 보이는** 칸이다
     * (모르면 [range] 전체). 책 밖은 누른다.
     */
    fun setWanted(range: IntRange, shown: IntRange = range) {
        val clamped = clampTo(range, 0, pageCount - 1)
        val seen = if (clamped.isEmpty()) IntRange.EMPTY else clampTo(shown, clamped.first, clamped.last)
        val shownNow = if (seen.isEmpty()) clamped else seen
        val version = synchronized(lock) {
            if (clamped == wanted && shownNow == visible) return
            wanted = clamped
            visible = shownNow
            demandVersion++
            // 범위가 바뀌었으니 '이번 범위 동안 다시 청하지 않는다' 도 끝났다.
            dropped.clear()
            demandVersion
        }
        demandSignal.value = version
    }

    fun get(ordinal: Int): T? = synchronized(lock) { thumbs[ordinal] }

    /** 읽지 못한 쪽인가. 칸이 번호만 보인다. */
    fun isFailed(ordinal: Int): Boolean = synchronized(lock) { ordinal in failed }

    /** 격자를 닫는다. **든 것을 전부 놓는다** — 격자가 없는 동안 이 몫은 선명화 조각과 띠가 쓴다. */
    fun clear() {
        synchronized(lock) {
            thumbs.clear()
            heldBytes = 0L
            failed.clear()
            dropped.clear()
            wanted = IntRange.EMPTY
            visible = IntRange.EMPTY
            demandVersion++
        }
        _version.update { it + 1 }
    }

    private fun clampTo(range: IntRange, lo: Int, hi: Int): IntRange =
        if (hi < lo || range.isEmpty() || range.last < lo || range.first > hi) IntRange.EMPTY else max(lo, range.first)..min(hi, range.last)

    private fun missing(ordinal: Int): Boolean =
        ordinal !in thumbs && ordinal !in failed && ordinal !in dropped

    override fun next(): Int? = synchronized(lock) {
        if (capBytes <= 0 || wanted.isEmpty()) return null
        // 보이는 칸(위에서 아래로) → 아래 여유 → 위 여유(보이는 칸에 가까운 것부터).
        visible.firstOrNull { missing(it) }
            ?: ((visible.last + 1)..wanted.last).firstOrNull { missing(it) }
            ?: (visible.first - 1 downTo wanted.first).firstOrNull { missing(it) }
    }

    override fun wants(ordinal: Int): Boolean = synchronized(lock) {
        capBytes > 0 && ordinal in wanted && missing(ordinal)
    }

    override fun wantsAnyExcept(passed: (Int) -> Boolean): Boolean = synchronized(lock) {
        capBytes > 0 && wanted.any { missing(it) && !passed(it) }
    }

    private fun hasMissing(): Boolean = synchronized(lock) { capBytes > 0 && wanted.any { missing(it) } }

    /**
     * 쪽 하나를 받았다. null 이면 읽지 못했다.
     *
     * **원하지 않게 된 쪽도 받는다** — 받는 사이에 범위가 옮겨 갔을 뿐이고, 상한이 알아서 먼 것부터 놓는다.
     */
    fun deliver(ordinal: Int, value: T?) {
        synchronized(lock) {
            deliveries++
            if (value == null) {
                failed += ordinal
            } else {
                val size = sizeOf(value).coerceAtLeast(1L)
                if (size > capBytes) {
                    dropped += ordinal
                } else {
                    thumbs.put(ordinal, value)?.let { heldBytes -= sizeOf(it).coerceAtLeast(1L) }
                    heldBytes += size
                    evictOverCap()
                }
            }
        }
        _version.update { it + 1 }
    }

    /**
     * 상한을 넘으면 **보이는 칸의 가운데에서 가장 먼 것**부터 놓는다. 여유를 앞뒤로 같게 두므로 범위 밖이 범위 안보다
     * 먼저 나간다(책의 처음·끝에서 여유가 한쪽으로 잘려도 보이는 칸은 가장 늦게 나간다).
     */
    private fun evictOverCap() {
        if (heldBytes <= capBytes) return
        val center = if (visible.isEmpty()) 0.0 else (visible.first + visible.last) / 2.0
        val order = thumbs.keys.sortedByDescending { abs(it - center) }
        for (victim in order) {
            if (heldBytes <= capBytes) break
            val v = thumbs.remove(victim) ?: continue
            heldBytes -= sizeOf(v).coerceAtLeast(1L)
            if (victim in wanted) dropped += victim
        }
    }

    /**
     * 격자가 떠 있는 동안 돈다. **격자의 코루틴에서 부른다** — 격자가 닫히면 취소되고, 취소는 해제 루프에 인터럽트로
     * 닿는다. 돌려주지 않는다(취소로만 끝난다).
     *
     * @param scan 쪽을 여럿 받는 길 — [ComicSource.scan](화면에서는 `ComicPageStore.scan`).
     * @param decode 쪽 원본을 썸네일로. **해제 스레드에서** 불린다(블로킹). 못 줄이면 null.
     */
    suspend fun run(
        scan: suspend (ScanDemand, (ordinal: Int, bytes: ByteArray?) -> Unit) -> Unit,
        decode: (ByteArray) -> T?,
    ) {
        if (capBytes <= 0) return
        while (true) {
            demandSignal.first { hasMissing() }
            val (versionBefore, deliveriesBefore) = synchronized(lock) { demandVersion to deliveries }
            scan(this) { ordinal, bytes ->
                val thumb = if (bytes == null) null else decodeOrNull(bytes, decode)
                deliver(ordinal, thumb)
            }
            synchronized(lock) {
                // **아무것도 받지 못했는데 범위도 그대로면** 소스가 그 쪽들을 줄 수 없다(깨진 아카이브라 훑기가 중간에
                // 죽었다). 그대로 두면 같은 훑기를 끝없이 되풀이한다 — 실패로 적어 번호만 보이게 한다.
                if (deliveries == deliveriesBefore && demandVersion == versionBefore) {
                    for (o in wanted) if (missing(o)) failed += o
                }
            }
            _version.update { it + 1 }
        }
    }

    private fun decodeOrNull(bytes: ByteArray, decode: (ByteArray) -> T?): T? = try {
        decode(bytes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * 썸네일 크기 — [boxWidth]×[boxHeight] 안에 가로세로 비를 지켜 넣는다. **키우지 않는다**(작은 쪽은 그대로).
 *
 * @return `[폭, 높이]`. 모르는 크기면 null.
 */
internal fun thumbSize(width: Int, height: Int, boxWidth: Int, boxHeight: Int): IntArray? {
    if (width <= 0 || height <= 0 || boxWidth <= 0 || boxHeight <= 0) return null
    val k = min(1.0, min(boxWidth.toDouble() / width, boxHeight.toDouble() / height))
    val w = ceil(width * k).toInt().coerceIn(1, width)
    val h = ceil(height * k).toInt().coerceIn(1, height)
    return intArrayOf(w, h)
}
