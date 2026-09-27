package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.format.html.FlowPackage
import io.github.donggi.iroiroviewer.safety.DecryptLimits
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException

/**
 * HWP 5.0 의 꾸러미 — CFB 한 벌. 흐름 문서 바탕([FlowPackage])에는 **`BinData` 저장소의 그림**만 내보이고, 이 모듈의
 * 문서는 여기서 `DocInfo`·구역 스트림을 받아 간다. 바탕이 모든 접근을 한 잠금으로 세우므로 스레드 안전하지 않다.
 *
 * ## 그림의 이름
 *
 * 바깥에 내보이는 이름은 `BinData/BIN0001.jpg` 다(`FlowPackage` 의 '앞의 `/` 없는 한 모양'). CFB 의 이름 비교는
 * 대소문자를 가리지 않으므로(MS-CFB 2.6.4) 찾기도 그렇게 한다 — 실물은 16진을 대문자로 적지만(`BIN000A`) 확장자는
 * 문서마다 다르다(`BIN0001.PNG`).
 *
 * ## 그림의 압축
 *
 * `BIN_DATA` 레코드의 압축 칸을 따른다([BinItem.compression]) — '따른다' 면 `FileHeader` 의 비트 0, '압축' 이면
 * 언제나, '압축하지 않음' 이면 날것. 그래도 풀리지 않으면 **그림에 한해서만** 날것으로 쓴다(pyhwp·hwplib 이 같이
 * 너그럽다). 본문·`DocInfo` 에서는 그렇게 하지 않는다 — 날것을 레코드로 읽으면 쓰레기가 글자가 된다.
 *
 * ## 구역 한 벌을 든다
 *
 * 그리는 동안 **가장 최근 구역 하나**의 풀린 바이트를 든다([section]). 긴 구역은 조각 여럿으로 나뉘어 차례로 그려지므로
 * 조각마다 다시 풀지 않게 한다. 배포용 문서면 그것이 평문이라 바꿀 때와 닫을 때 0 으로 덮는다.
 */
internal class Hwp5Package(
    private val cfb: CfbFile,
    val header: Hwp5FileHeader,
) : FlowPackage {

    private val binEntries: Map<String, CfbEntry>
    private var binModes: Map<String, Int> = emptyMap()
    private val warningList = ArrayList<ParseWarning>()

    private var cachedIndex = -1
    private var cached: ByteArray? = null

    /** [fitsResource] 가 잰 것(스트림 이름의 소문자 → 들어가는가)과, 그때의 상한. */
    private val fitCache = HashMap<String, Boolean>()
    private var fitCap = -1L

    /** 배포용 문서의 제한 표시(뜻을 확인하지 못했다 — [Hwp5Distribution]). 처음 푼 구역의 값. */
    var distributionFlags: Int? = null
        private set

    init {
        val map = HashMap<String, CfbEntry>()
        cfb.find("BinData")?.takeIf { it.isStorage }?.let { storage ->
            for (e in cfb.children(storage)) {
                if (e.isStream) map.putIfAbsent(e.name.lowercase(), e)
            }
        }
        binEntries = map
    }

    /** `DocInfo` 의 바이너리 데이터 목록 — 스트림마다의 압축 방식을 여기서 안다. */
    fun registerBinItems(items: List<BinItem>) {
        val m = HashMap<String, Int>()
        for (b in items) {
            val name = b.streamName ?: continue
            m.putIfAbsent(name.lowercase(), b.compression)
        }
        binModes = m
    }

    /** 바이너리 데이터 이름(`BIN0001.jpg`)을 바깥 이름으로. 스트림이 없으면 null. */
    fun binName(streamName: String): String? = binEntries[streamName.lowercase()]?.let { PREFIX + it.name }

    /** 풀어 낸 `DocInfo`. 없으면 [HwpFormatException]. */
    fun docInfoBytes(checkCancel: () -> Unit): ByteArray {
        val entry = cfb.find("DocInfo")?.takeIf { it.isStream } ?: throw HwpFormatException("DocInfo 가 없다")
        val raw = cfb.readStream(entry, Hwp5Limits.MAX_DOCINFO_BYTES)
        if (!header.compressed) return raw
        return HwpInflate.inflate(raw, 0, raw.size, Hwp5Limits.MAX_DOCINFO_BYTES, "maxDocInfoBytes", checkCancel = checkCancel)
    }

    /**
     * 본문 구역의 스트림들. 배포용이면 `ViewText`, 아니면 `BodyText` 에서 `Section0`, `Section1` … 을 차례로.
     * [declared] 는 `DOCUMENT_PROPERTIES` 가 적은 구역 수(모르면 0) — 적힌 수가 있으면 그만큼만 보고, 빠진 스트림이
     * 있으면 거기서 멈춘다(부르는 쪽이 알린다).
     */
    fun sectionEntries(declared: Int): List<CfbEntry> {
        val storage = cfb.find(if (header.distribution) "ViewText" else "BodyText")?.takeIf { it.isStorage }
            ?: return emptyList()
        val limit = if (declared > 0) minOf(declared, Hwp5Limits.MAX_SECTIONS) else Hwp5Limits.MAX_SECTIONS
        val out = ArrayList<CfbEntry>()
        for (i in 0 until limit) {
            val e = cfb.child(storage, "Section$i")?.takeIf { it.isStream } ?: break
            out.add(e)
        }
        return out
    }

    /**
     * 구역 [index] 의 레코드 흐름(풀린 것). 가장 최근 것 하나를 든다.
     *
     * 배포용이면 복호화한 뒤 푼다. 평문 한 벌의 예산(`DecryptLimits.MAX_BYTES`)과 구역 상한 가운데 작은 쪽으로 읽는다.
     */
    fun section(index: Int, entry: CfbEntry, checkCancel: () -> Unit): ByteArray {
        cached?.let { if (cachedIndex == index) return it }
        dropCache()
        val bytes = if (header.distribution) {
            val cap = minOf(Hwp5Limits.MAX_SECTION_BYTES, DecryptLimits.MAX_BYTES)
            val raw = cfb.readStream(entry, cap)
            val decoded = Hwp5Distribution.decode(raw, header.compressed, cap, checkCancel)
            if (distributionFlags == null) distributionFlags = decoded.flags
            decoded.bytes
        } else {
            val raw = cfb.readStream(entry, Hwp5Limits.MAX_SECTION_BYTES)
            if (header.compressed) {
                HwpInflate.inflate(raw, 0, raw.size, Hwp5Limits.MAX_SECTION_BYTES, "maxSectionBytes", checkCancel = checkCancel)
            } else {
                raw
            }
        }
        cached = bytes
        cachedIndex = index
        return bytes
    }

    /**
     * 미리 보기 글(`PrvText`) — UTF-16LE, 압축하지 않는다(명세 표 2). 본문을 읽지 못했을 때만 쓴다. 없으면 null.
     */
    fun previewText(): String? {
        val entry = cfb.find("PrvText")?.takeIf { it.isStream } ?: return null
        val raw = cfb.readStream(entry, Hwp5Limits.MAX_PREVIEW_BYTES)
        val n = raw.size / 2
        val chars = CharArray(n)
        for (i in 0 until n) chars[i] = raw.u16(2 * i).toChar()
        return String(chars).trimEnd('\u0000')
    }

    private fun dropCache() {
        // 배포용이 아니어도 덮는다 — 값이 작고, 어느 쪽인지 가리는 분기를 하나 줄인다.
        cached?.fill(0)
        cached = null
        cachedIndex = -1
    }

    // ---- FlowPackage -------------------------------------------------------------------------

    private fun entryOf(name: String): CfbEntry? {
        if (!name.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = name.substring(PREFIX.length)
        if (rest.isEmpty() || '/' in rest) return null
        return binEntries[rest.lowercase()]
    }

    override fun has(name: String): Boolean = entryOf(name) != null

    override fun canonical(name: String): String? = entryOf(name)?.let { PREFIX + it.name }

    override fun contentType(name: String): String? {
        val entry = entryOf(name) ?: return null
        return when (entry.name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            // 화면이 그리지 못하는 것 — 형식을 알려 바탕이 확장자로 짐작하지 않게 한다.
            "wmf" -> "image/wmf"
            "emf" -> "image/emf"
            "tif", "tiff" -> "image/tiff"
            "ole" -> "application/x-ole-storage"
            else -> null
        }
    }

    override fun readBytes(name: String, max: Long): ByteArray? {
        val entry = entryOf(name) ?: return null
        val cap = resourceCap(max)
        val raw = cfb.readStream(entry, cap)
        if (!isCompressed(entry)) return raw
        return try {
            HwpInflate.inflate(raw, 0, raw.size, cap, "maxResourceBytes")
        } catch (e: HwpFormatException) {
            // 그림에 한해서만 날것으로(위 주석). 상한 초과는 그대로 올린다.
            raw
        } catch (e: ParseLimitExceededException) {
            throw e
        }
    }

    /** [readBytes] 가 실제로 쓰는 상한 — 배포용 문서면 평문 한 벌의 예산과 작은 쪽. */
    private fun resourceCap(max: Long): Long = if (header.distribution) minOf(max, DecryptLimits.MAX_BYTES) else max

    /** `BIN_DATA` 의 압축 칸을 따른다(위 '그림의 압축'). */
    private fun isCompressed(entry: CfbEntry): Boolean = when (binModes[entry.name.lowercase()] ?: 0) {
        1 -> true
        2 -> false
        else -> header.compressed
    }

    /**
     * 그림 [name](바깥 이름, `BinData/BIN0001.png`)을 [max] 바이트 안에서 내줄 수 있는가 — [readBytes] 가 **상한 때문에**
     * 던지지 않을 것인가. 넘는 그림은 바탕이 내주지 않으므로 `img` 를 쓰면 깨진 그림 표시만 뜨고 배지에는 아무것도 없다
     * (13단계 짝 대조 — HWPX 는 그 그림을 '그릴 수 없는 그림' 으로 센다). 걷기가 `img` 를 쓰기 전에 묻는다.
     *
     * [readBytes] 와 같은 길을 잰다: 날것의 스트림이 상한을 넘으면 넘는다(압축이든 아니든 그것부터 읽는다). 압축이면 **풀어서
     * 세기만** 한다([HwpInflate.exceeds] — 결과를 들지 않는다). 압축이 깨졌으면 [readBytes] 가 날것을 내주므로 들어간다.
     * 스트림 자체가 깨졌으면(체인) 크기로는 가르지 않는다 — 들어간다고 답한다(그 그림은 어차피 읽히지 않고, 여기서 던지면
     * 훑기가 구역을 거기서 멈춘다).
     *
     * 그림마다 **한 번만** 잰다 — 훑기가 재고, 그리기는 기억한 것을 쓴다. 취소는 [checkCancel] 로 본다(푸는 동안 1 MiB 마다).
     */
    fun fitsResource(name: String, max: Long, checkCancel: () -> Unit): Boolean {
        val entry = entryOf(name) ?: return true
        if (max != fitCap) {
            fitCache.clear()
            fitCap = max
        }
        val key = entry.name.lowercase()
        fitCache[key]?.let { return it }
        val fits = try {
            measureFits(entry, resourceCap(max), checkCancel)
        } catch (e: CorruptFormatException) {
            // 압축이 깨진 것(날것으로 내준다)과 체인이 깨진 것(위 주석) — 크기의 문제가 아니다.
            true
        }
        fitCache[key] = fits
        return fits
    }

    private fun measureFits(entry: CfbEntry, cap: Long, checkCancel: () -> Unit): Boolean {
        // 적힌 크기가 상한 안이면 체인을 걷지 않는다 — 실제 길이는 적힌 크기를 넘지 않는다(`CfbFile.streamLength`).
        if (entry.size > cap && cfb.streamLength(entry) > cap) return false
        if (!isCompressed(entry)) return true
        return cfb.openStream(entry).use { !HwpInflate.exceeds(it, cap, checkCancel) }
    }

    override val warnings: List<ParseWarning> get() = warningList

    /** 비트 3(스크립트). `Scripts` 저장소는 거의 모든 실물에 있지만(빈 기본 스크립트) 비트가 켜진 것은 없었다 — 비트만 믿는다. */
    override val hasMacros: Boolean get() = header.scripts

    override fun close() {
        dropCache()
        cfb.close()
    }

    companion object {
        const val PREFIX = "BinData/"
    }
}
