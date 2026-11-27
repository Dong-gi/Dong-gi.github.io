package io.github.donggi.iroiroviewer.io

import android.media.MediaMetadataRetriever
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.CaptureDates
import io.github.donggi.iroiroviewer.model.DayBucket
import io.github.donggi.iroiroviewer.model.ExifContainerGuard
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.GalleryRoots
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.time.ZoneId

/**
 * 갤러리가 볼 곳을 훑는다.
 *
 * ## 왜 MediaStore 를 쓰지 않는가
 *
 * 안드로이드에는 이미 사진 색인이 있고 갤러리 앱들이 그것을 읽는다. 그런데 그 색인은
 * **미디어 스캐너가 본 것만** 담는다. 다른 앱이 우회 경로로 쓴 파일, 방금 `adb push`
 * 한 파일, 스캐너가 훑기 전의 파일은 없다. 우리는 모든 파일 접근 권한으로 파일시스템을
 * 직접 보는 앱이고, "폴더에 있는데 갤러리에는 없다" 가 이 앱에서 가장 나쁜 답이다.
 *
 * 그리고 MediaStore 를 읽으려면 `READ_MEDIA_IMAGES` 를 또 선언해야 한다 — 권한을 하나
 * 더 받아 이미 할 수 있는 일을 하는 셈이다.
 *
 * ## 규칙
 *
 * - **`.nomedia` 가 있는 폴더는 통째로 건너뛴다.** 그것이 안드로이드에서 '이 폴더는
 *   갤러리에 넣지 마라' 를 뜻하는 유일한 약속이고, 우리 휴지통도 그 표시를 단다.
 * - 숨김 폴더(`.` 로 시작)는 들어가지 않는다.
 * - 뿌리는 볼륨마다 DCIM·Pictures·Download 와 **`Android/media`**(메신저가 받은 사진) — 뒤의 것은 담는 수에 상한이 있다([GalleryRoots]).
 * - 새것이 위로. 사진 목록의 관행이고, 대개 찾는 것은 방금 찍은 것이다.
 * - 사진과 **영상**을 함께 담는다. `.ts`·`.mts` 처럼 TypeScript 와 확장자를 나눠 쓰는 것은 뺀다 — `Download` 에 소스 파일이 섞인다.
 *
 * ## 찍은 날로 묶는다 — 두 번 내놓는다
 *
 * 시각은 **찍힌 시각**이다 — 사진은 EXIF `DateTimeOriginal`(+ `OffsetTimeOriginal`), 영상은 `MediaMetadataRetriever` 의 날짜
 * ([CaptureDates]). 없으면 수정일. 그런데 그것을 읽으려면 파일을 하나하나 열어야 하고(1만 장이면 수십 초 — 추정), `lstat` 만 하는
 * 훑기(1만 개 394 ms)에 견주면 수십 배다. 그래서:
 *
 * 1. 훑고 나서 **캐시([CaptureDateCache])에 있는 것은 그 값, 없는 것은 수정일**로 곧바로 [Scan.Ready] 를 내놓는다 — 격자가 바로 선다.
 * 2. 캐시에 없던 것을 읽어 캐시에 넣고, 순서가 바뀌었으면 **한 번 더** [Scan.Ready] 를 내놓는다. 중간에 조금씩 다시 내놓지 않는다 —
 *    보는 동안 칸이 여러 번 자리를 옮기면 무엇을 보던 중인지 잃는다.
 *
 * 두 번째부터는 거의 다 캐시에 있으므로 1 에서 끝난다. 읽던 중에 화면을 떠나 훑기가 끊겨도 그때까지 읽은 것은 캐시에 남는다.
 */
object GalleryScanner {

    /** 기본으로 보는 폴더 이름 — 규칙은 [GalleryRoots]. */
    val DEFAULT_FOLDERS: List<String> get() = GalleryRoots.SHOWN_FOLDERS

    /** 재귀 상한. DCIM 아래가 이보다 깊은 경우는 없고, 깊으면 링크 고리를 의심한다. */
    private const val MAX_DEPTH = 8

    /** 이만큼마다 진행을 알린다. */
    private const val PROGRESS_EVERY = 200

    sealed interface Scan {
        data class Scanning(val found: Int) : Scan

        /**
         * @param items 사진과 영상. 찍힌 시각(없으면 수정일)이 늦은 것부터.
         * @param sections 날짜 구간. [items] 의 자리를 번호로 가리킨다 — 항목을 날짜별
         *   목록으로 다시 담지 않는 이유는 [DayBucket.Section] 의 주석에 있다.
         */
        data class Ready(
            val items: List<FileEntry>,
            val sections: List<DayBucket.Section>,
            val roots: List<String>,
            val scanMillis: Long,
        ) : Scan
    }

    /** 이 볼륨들에서 실제로 있는 뿌리 — 사진 폴더 셋과 `Android/media`([GalleryRoots]). */
    fun defaultRoots(volumes: List<VolumeRegistry.Volume>): List<GalleryRoots.Root> =
        GalleryRoots.candidates(volumes.map { it.path }).filter { File(it.path).isDirectory }

    /**
     * @param dates 찍힌 시각의 캐시. 보통 [CaptureDateCache.shared].
     */
    fun scan(roots: List<GalleryRoots.Root>, dates: CaptureDateCache): Flow<Scan> = flow {
        emit(Scan.Scanning(0))
        val started = System.nanoTime()
        val found = ArrayList<FileEntry>(512)
        val visited = HashSet<Long>()
        for (root in roots) {
            // 뿌리마다 담을 수의 상한(`Android/media` — [GalleryRoots.MAX_MEDIA_FILES]). 닿으면 그 뿌리만 멈춘다.
            val budget = Budget(root.maxFiles)
            walk(File(root.path), visited, 0, budget) { entry ->
                found += entry
                if (found.size % PROGRESS_EVERY == 0) emit(Scan.Scanning(found.size))
            }
            if (budget.left <= 0) Iro.d { "갤러리 뿌리 하나가 상한(${root.maxFiles}개)에 닿아 멈췄다" }
        }
        // 시간대는 훑기마다 한 번 잡는다 — 시차 없는 EXIF 를 셈하는 데도, 날로 묶는 데도, 캐시의 꼬리표로도 같은 값을 쓴다.
        val zone = ZoneId.systemDefault()
        dates.useZone(zone.id)
        val times = LongArray(found.size)
        val unknown = ArrayList<Int>()
        for (i in found.indices) {
            val e = found[i]
            val cached = dates.lookup(e.path, e.size, e.lastModified)
            if (cached == null) unknown += i
            times[i] = if (cached == null || cached == CaptureDateCache.NONE) e.lastModified else cached
        }
        val rootPaths = roots.map { it.path }
        // 폴더를 끝까지 훑은 지금 — 사라진 파일의 줄을 지운다(지운 사진의 경로가 캐시에 남지 않게). 날짜를 다 읽은 뒤로 미루면
        // 읽기가 끊길 때마다(화면을 떠났다·다시 훑었다) 지우지 못한다. 뒤에서 넣는 경로는 모두 `found` 에 있다.
        dates.retainOnly(found.mapTo(HashSet(found.size * 2)) { it.path })
        try {
            val first = ready(found, times, rootPaths, started, zone)
            // 디버그 빌드에서만 남는다(수와 시간만 — 경로는 적지 않는다). 1만 장의 첫 훑기를 재는 자리다.
            Iro.d { "갤러리 ${found.size}개 · 첫 내놓기 ${first.scanMillis}ms · 날짜 모름 ${unknown.size}개" }
            emit(first)
            var changed = false
            for (i in unknown) {
                currentCoroutineContext().ensureActive()
                val e = found[i]
                val taken = when (val r = takenMillis(e, zone)) {
                    is CaptureDateRead.Result.Taken -> r.millis.also { dates.put(e.path, e.size, e.lastModified, it, zone.id) }
                    CaptureDateRead.Result.NoDate ->
                        null.also { dates.put(e.path, e.size, e.lastModified, CaptureDateCache.NONE, zone.id) }
                    // 열지 못했다 — 일시적일 수 있으니 남기지 않는다. 다음 훑기가 다시 연다.
                    CaptureDateRead.Result.Unknown -> null
                }
                if (taken != null && taken != times[i]) {
                    times[i] = taken
                    changed = true
                }
            }
            if (unknown.isNotEmpty()) {
                Iro.d { "갤러리 날짜 읽기 ${unknown.size}개 끝 · ${(System.nanoTime() - started) / 1_000_000}ms · 순서 바뀜 $changed" }
            }
            if (changed) emit(ready(found, times, rootPaths, started, zone))
        } finally {
            // 끊겨도 읽은 만큼은 남긴다. 다음 훑기가 같은 파일을 다시 열지 않는다.
            withContext(NonCancellable) { dates.save() }
        }
    }.flowOn(IroDispatchers.io)

    /**
     * 시각이 늦은 것부터 늘어놓고 날짜로 묶는다.
     *
     * **날짜 묶기를 여기서 한다.** 이 flow 는 `IroDispatchers.io` 위에서 돌고,
     * 화면은 주 스레드다 — 1만 장의 시각을 주 스레드에서 날짜로 바꾸면 격자가
     * 그려지기 전에 한 번 멎는다. 구간은 훑기의 결과이지 그리기의 일이 아니다.
     *
     * 시간대는 **기기 설정**을 쓴다. 사용자가 사진을 찍은 날은 그 사람의 시계로
     * 센 날이다(시험이 이 둘을 갈라 확인한다). 시차 칸이 있는 사진도 순간으로 바꾼 뒤 기기 시계로 센다 — 도쿄에서 밤 11시 반에 찍은
     * 것은 서울의 기기에서도 같은 날이고, 도쿄에서 새벽 1시(`+09:00`)에 찍은 것은 뉴욕(겨울, `-05:00`)의 기기에서 그 전날 오전 11시다.
     */
    private fun ready(
        found: List<FileEntry>,
        times: LongArray,
        roots: List<String>,
        started: Long,
        zone: ZoneId,
    ): Scan.Ready {
        // `sortedByDescending` 은 안정 정렬이다 — 시각이 같으면 훑은 차례 그대로라 다시 훑어도 칸이 뒤바뀌지 않는다.
        val order = found.indices.sortedByDescending { times[it] }
        return Scan.Ready(
            items = order.map { found[it] },
            sections = DayBucket.sectionsOf(order.map { times[it] }, zone),
            roots = roots,
            scanMillis = (System.nanoTime() - started) / 1_000_000,
        )
    }

    /**
     * 찍힌 시각을 읽는다. 파일을 연다(입출력 디스패처에서만). 무엇이 실패해도 던지지 않는다 — [CaptureDateRead] 가 가른다
     * (조작한 PNG 의 `eXIf` 길이로 ExifInterface 가 `OutOfMemoryError` 를 던지는 것까지).
     */
    private fun takenMillis(e: FileEntry, zone: ZoneId): CaptureDateRead.Result = when (e.kind) {
        FileKind.VIDEO -> CaptureDateRead.read(
            open = {
                // `setDataSource` 의 실패는 '열지 못했다' 다(틀 수 없는 영상·잠깐의 입출력 — 다음 훑기가 다시 본다).
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(e.path)
                } catch (t: Throwable) {
                    r.close()
                    throw t
                }
                r
            },
            parse = { r -> r.use { CaptureDates.videoMillis(it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)) } },
        )
        FileKind.IMAGE -> if (MimeResolver.extensionOf(e.name) !in EXIF_EXTENSIONS) {
            CaptureDateRead.Result.NoDate
        } else {
            CaptureDateRead.read(
                // 여는 것과 읽는 것을 가른다 — 열지 못한 것은 남기지 않고, 연 뒤 파서가 무너진 것은 '날짜 없음' 으로 남긴다.
                // `FileInputStream` 을 넘기면 ExifInterface 가 그 서술자로 되감아 읽는다.
                open = { FileInputStream(e.path) },
                parse = { input ->
                    input.use {
                        // 덩이·IFD 칸의 길이가 터무니없으면 ExifInterface 에 넘기지 않는다 — 할당이 **성공해도** 다른 스레드가 죽는다(`ExifContainerGuard`).
                        // 이미 연 서술자의 채널로 위치를 지정해 읽는다 — 검사한 파일이 곧 읽는 파일이고, 스트림의 위치는 그대로다.
                        // EXIF 가 없는 PNG·WebP(스크린숏 대부분)는 ExifInterface 를 열지 않고 '날짜 없음' 이다.
                        // HEIF·AVIF 는 이 서술자로 추출기에 물어 검사기가 걸어 본 Exif 블록만 연다(ExifInterface 의 HEIF 갈래를 타지 않는다).
                        val src = ExifContainerGuard.openForExif(it, requireExif = true) ?: return@use null
                        src.use { s ->
                            val exif = ExifInterface(s.input, s.streamType)
                            CaptureDates.exifMillis(
                                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                                exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                                zone,
                            )
                        }
                    }
                },
            )
        }
        else -> CaptureDateRead.Result.NoDate
    }

    /** 한 뿌리에서 더 담을 수 있는 수. */
    private class Budget(var left: Int)

    private suspend fun walk(
        dir: File,
        visited: MutableSet<Long>,
        depth: Int,
        budget: Budget,
        onFound: suspend (FileEntry) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > MAX_DEPTH || budget.left <= 0) return
        if (TrashStore.isTrashPath(dir.absolutePath)) return
        // 이 한 줄이 '숨긴 사진첩' 을 존중한다. 다른 갤러리 앱과 같은 약속이다.
        if (File(dir, NOMEDIA).exists()) return

        val children = dir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (budget.left <= 0) return
            val st = try {
                Os.lstat(child.absolutePath)
            } catch (e: ErrnoException) {
                continue
            }
            // 링크는 따라가지 않는다. 고리를 만들 수 있고 볼륨 밖으로 나간다.
            if (OsConstants.S_ISLNK(st.st_mode)) continue
            val name = child.name
            if (name.startsWith('.')) continue

            if (OsConstants.S_ISDIR(st.st_mode)) {
                // 하드링크로 만든 디렉터리 고리를 막는다(복사 엔진과 같은 방식).
                val key = st.st_dev * 1_000_003L + st.st_ino
                if (!visited.add(key)) continue
                walk(child, visited, depth + 1, budget, onFound)
                continue
            }

            val kind = MimeResolver.kindOf(name, isDirectory = false)
            if (kind != FileKind.IMAGE && kind != FileKind.VIDEO) continue
            if (kind == FileKind.VIDEO && ContentSniff.isAmbiguousStream(name)) continue
            budget.left--
            onFound(
                FileEntry(
                    name = name,
                    path = child.absolutePath,
                    isDirectory = false,
                    size = st.st_size,
                    lastModified = st.st_mtime * 1000L,
                    isHidden = false,
                    isSymlink = false,
                    kind = kind,
                )
            )
        }
    }

    private const val NOMEDIA = ".nomedia"

    /**
     * `ExifInterface` 로 날짜를 읽는 형식. GIF·BMP·SVG 는 EXIF 가 없어 열지 않는다. DNG·TIFF 는 내용이 TIFF 라 검사기(`ExifContainerGuard`)가
     * 넘기지 않으므로 열지 않는다(수정 시각으로 묶인다).
     */
    private val EXIF_EXTENSIONS = setOf("jpg", "jpeg", "jpe", "png", "webp", "heic", "heif", "avif")
}
