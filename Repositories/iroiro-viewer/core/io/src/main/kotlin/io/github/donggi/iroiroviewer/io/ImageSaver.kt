package io.github.donggi.iroiroviewer.io

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 그림의 한 자리를 **새 파일로** 저장한다(사용자 요청 '지정한 직사각형 영역을 별도 저장'). 그리기·메모도
 * 여기를 지난다 — 요청에 [FileOpManager.Request.SaveCrop.overlay] 가 실려 오면 뜨는 쪽이 그 위에 그려 주고, 이름의 꼬리만 다르다
 * ([MARKUP_TAG]). 형식·EXIF·이름 맡기는 아래 그대로다.
 *
 * 뜨는 일은 [CropSupport.Renderer] 가 하고(화면이 본 그대로 — 그 까닭은 [CropSupport]), 여기는 쓰는 일만 한다.
 *
 * ## 원본은 건드리지 않는다
 *
 * 결과는 원본 곁의 새 파일(`사진_crop.jpg`·`사진_markup.jpg`)이다. 임시본에 다 쓴 뒤 **끝에** 없는 이름을 맡는다([NewFiles.claim]) —
 * 그사이 같은 이름이 생겼으면 `(2)` 가 붙는다. 실제 이름은 결과가 싣는다.
 *
 * ## 형식 — 원본과 같게, 못 쓰면 가장 가까운 것으로
 *
 * | 원본 | 결과 | 까닭 |
 * |---|---|---|
 * | JPEG | JPEG(품질 95) | 다시 인코딩한다 — 무손실 자르기(MCU 단위)는 플랫폼에 없다 |
 * | PNG | PNG | 무손실 |
 * | WebP | 무손실이면 무손실, 아니면 손실(95) | 원본이 무손실이었는지는 `VP8L` 청크로 안다 |
 * | GIF(정지)·BMP·WBMP | PNG | 플랫폼이 그 형식으로 쓰지 못한다. 무손실이다 |
 * | HEIC·AVIF 등 | JPEG(95) | 플랫폼의 `Bitmap.compress` 는 HEIF 를 쓰지 못한다(새 의존 없이) |
 * | 투명한 것이 JPEG 가 될 자리 | PNG | JPEG 에는 투명이 없다 — 투명한 자리가 검게 칠해진다 |
 *
 * ## 메타데이터 — 날짜와 기기만
 *
 * JPEG 로 쓸 때 원본의 **찍은 날짜와 기기**(EXIF `DateTimeOriginal`·`Make`·`Model` 등)를 옮긴다 — 갤러리가 찍은 날로 묶는다.
 * **위치(GPS)는 옮기지 않는다** — 잘라 낸 그림은 대개 남에게 보내려는 것이다. 방향 태그도 옮기지 않는다 — 화소를 이미 화면
 * 방향으로 세워 썼다.
 *
 * 날짜는 임시본을 **제자리에서 다시 쓰며** 입힌다(`ExifInterface.saveAttributes`). 그 뒤 임시본이 온전한지 보고, 상했으면 버리고 날짜
 * 없이 다시 쓴다([copyDatesIntact]) — 날짜 없는 온전한 그림이 잘린 그림보다 낫다.
 */
internal object ImageSaver {

    /** 결과 이름의 꼬리 — `사진_crop.jpg`. */
    const val TAG = "crop"

    /** 그리기·메모의 꼬리 — `사진_markup.jpg`. */
    const val MARKUP_TAG = "markup"

    data class Result(
        val outcome: FileOpEngine.Outcome,
        val path: String?,
        val note: FileOpManager.SavedNote? = null,
    )

    /**
     * 결과의 인코딩. 플랫폼의 `Bitmap.CompressFormat` 을 그대로 들지 않는 것은 JVM 시험 때문이다 — 안드로이드 스텁의 열거형은
     * 초기화에서 던진다. 쓰는 자리에서 [compressFormat] 으로 옮긴다.
     */
    internal enum class Encoding { JPEG, PNG, WEBP_LOSSY, WEBP_LOSSLESS }

    /** 결과 형식. [copyDates] 면 원본의 날짜·기기 EXIF 를 옮긴다. */
    internal data class Output(
        val encoding: Encoding,
        val quality: Int,
        val extension: String,
        val copyDates: Boolean,
    ) {
        fun compressFormat(): Bitmap.CompressFormat = when (encoding) {
            Encoding.JPEG -> Bitmap.CompressFormat.JPEG
            Encoding.PNG -> Bitmap.CompressFormat.PNG
            Encoding.WEBP_LOSSY -> Bitmap.CompressFormat.WEBP_LOSSY
            Encoding.WEBP_LOSSLESS -> Bitmap.CompressFormat.WEBP_LOSSLESS
        }
    }

    /**
     * @param writeDates 원본의 날짜를 JPEG 임시본에 제자리에서 쓴다(기본은 [copyDates]). 시험이 되쓰다 상한 임시본을 심는 자리다.
     * @param onPlaced 새 파일을 놓은 순간에 불린다(`NewFiles.place`) — 그 뒤에 온 취소가 결과를 버려도 큐가 '놓였다' 를 안다.
     */
    suspend fun saveCrop(
        request: FileOpManager.Request.SaveCrop,
        renderer: CropSupport.Renderer,
        media: MediaIndex?,
        onProgress: (FileOpEngine.Progress) -> Unit,
        writeDates: (src: File, dst: File) -> Unit = ::copyDates,
        onPlaced: (Result) -> Unit = {},
    ): Result = withContext(IroDispatchers.io) {
        val src = File(request.path)
        if (!src.isFile) return@withContext failed(FileOpEngine.Reason.NOT_FOUND, src.name)
        val dir = src.parentFile ?: return@withContext failed(FileOpEngine.Reason.IO, src.name)
        // 끝을 모르는 일이다(뜨고 쓰는 두 걸음) — 이름과 '1개 중 0개' 만 알린다.
        onProgress(FileOpEngine.Progress(0, 0, 0, 1, src.name))

        val rendered = renderer.render(
            CropSupport.Ask(
                path = request.path,
                left = request.left,
                top = request.top,
                right = request.right,
                bottom = request.bottom,
                expectedWidth = request.expectedWidth,
                expectedHeight = request.expectedHeight,
                maxPixels = maxPixels(),
                overlay = request.overlay,
            )
        )
        val ok = when (rendered) {
            is CropSupport.Rendered.Ok -> rendered
            CropSupport.Rendered.Changed -> return@withContext failed(FileOpEngine.Reason.CHANGED, src.name)
            CropSupport.Rendered.Unsupported -> return@withContext failed(FileOpEngine.Reason.UNSUPPORTED, src.name)
            CropSupport.Rendered.Unreadable ->
                return@withContext failed(if (src.isFile) FileOpEngine.Reason.IO else FileOpEngine.Reason.NOT_FOUND, src.name)
        }
        val bitmap = ok.bitmap
        try {
            val output = outputFor(ok.sourceMime, NewFiles.extensionOf(src.name), bitmap.hasAlpha(), isLosslessWebp(src))
            val name = NewFiles.derivedName(src.name, if (request.overlay != null) MARKUP_TAG else TAG, output.extension)
            // 죽은 프로세스가 남긴 임시본을 걷는다(한 시간 넘은 것만 — 다른 작업과 같다).
            AtomicFileWriter.sweepPartials(dir)
            var pending = try {
                AtomicFileWriter.begin(File(dir, name), 0L)
            } catch (e: IOException) {
                return@withContext failed(NewFiles.reasonOf(e), name)
            }
            try {
                encode(bitmap, output, pending, dir)
                currentCoroutineContext().ensureActive()
                if (output.copyDates && !copyDatesIntact(src, pending.tempFile, writeDates)) {
                    // 날짜를 입히다 임시본이 상했다 — 버리고 날짜 없이 다시 쓴다. 날짜 없는 온전한 그림이 잘린 그림보다 낫다(ZIP 만들기의
                    // '읽는 도중에 실패하면 전부 버린다' 와 같은 판단 — 상한 것을 놓지 않는다).
                    Iro.e(message = "EXIF 날짜를 입히다 잘라 낸 그림의 임시본이 상했다 — 날짜 없이 다시 쓴다")
                    pending.abort()
                    pending = AtomicFileWriter.begin(File(dir, name), 0L)
                    encode(bitmap, output, pending, dir)
                }
                val note = if (ok.downscaled) FileOpManager.SavedNote.Downscaled(bitmap.width, bitmap.height) else null
                val target = NewFiles.place(pending, dir, name, media) { t ->
                    onPlaced(Result(FileOpEngine.Outcome.Done(1, 0, 0), t.absolutePath, note))
                }
                Result(FileOpEngine.Outcome.Done(1, 0, 0), target.absolutePath, note)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { pending.abort() }
                Result(FileOpEngine.Outcome.Cancelled, null)
            } catch (t: Throwable) {
                withContext(NonCancellable) { pending.abort() }
                Iro.e(message = "${if (request.overlay != null) "그리기" else "잘라"} 저장이 실패했다: ${t::class.java.simpleName}")
                failed(NewFiles.reasonOf(t), name)
            }
        } finally {
            // 아직 아무도 그리지 않은 비트맵이다(축출된 비트맵과 다르다) — 곧바로 놓는다.
            bitmap.recycle()
        }
    }

    private fun failed(reason: FileOpEngine.Reason, name: String) = Result(FileOpEngine.Outcome.Failed(reason, name), null)

    /** 비트맵을 임시본에 쓴다 — **닫지 않고 비우기만 한다**(흐름은 `NewFiles.place` 가 fsync 한 뒤에 닫는다). */
    private fun encode(bitmap: Bitmap, output: Output, pending: AtomicFileWriter.Pending, dir: File) {
        val buffered = BufferedOutputStream(pending.stream, BUFFER)
        if (!bitmap.compress(output.compressFormat(), output.quality, buffered)) {
            // 인코더는 까닭을 말하지 않는다(쓰기가 실패해도 false 하나다) — 공간이 거의 없으면 그것이 까닭이다.
            throw NewFiles.Failed(NewFiles.reasonWhenSilent(dir, FileOpEngine.Reason.IO))
        }
        buffered.flush()
    }

    /**
     * 결과 한 장의 화소 상한. 힙의 셋째 몫과 캔버스가 받는 상한(100 MiB — API 31 의 `Canvas` 가 그보다 큰 비트맵을 거절한다,
     * `PdfLimits` 의 같은 값) 가운데 작은 것을 ARGB 4바이트로 나눈다. 12MP 사진은 대개 줄이지 않고 들어온다(힙 등급 192MB 에서 16MP).
     */
    internal fun maxPixels(heap: Long = Runtime.getRuntime().maxMemory()): Long =
        minOf(heap / 3, CANVAS_MAX_BYTES) / 4

    /** 원본 형식에서 결과 형식을 고른다(머리말의 표). [sourceExt] 는 원본의 확장자(소문자, 점 없이). */
    internal fun outputFor(mime: String?, sourceExt: String, hasAlpha: Boolean, losslessWebp: Boolean): Output {
        val m = mime?.lowercase(java.util.Locale.ROOT)
        val png = Output(Encoding.PNG, 100, "png", copyDates = false)
        return when {
            m == "image/png" -> png
            m == "image/webp" -> if (losslessWebp) {
                Output(Encoding.WEBP_LOSSLESS, 100, "webp", copyDates = false)
            } else {
                Output(Encoding.WEBP_LOSSY, JPEG_QUALITY, "webp", copyDates = false)
            }
            m in LOSSLESS_TO_PNG -> png
            // 그 밖은 JPEG 가 된다 — 투명한 것이 있으면 PNG(JPEG 에는 투명이 없다).
            hasAlpha -> png
            else -> Output(
                Encoding.JPEG,
                JPEG_QUALITY,
                if (m == "image/jpeg" && sourceExt in JPEG_EXTENSIONS) sourceExt else "jpg",
                copyDates = true,
            )
        }
    }

    /**
     * 원본이 무손실 WebP 인가. RIFF 청크 머리만 훑는다 — 첫 그림 청크가 `VP8L` 이면 무손실이다(`VP8X` 다음의 `ALPH`·`VP8` 이면
     * 손실). 못 읽으면 손실로 친다 — 크기가 커지는 쪽으로 틀리는 것보다 낫다.
     */
    internal fun isLosslessWebp(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 16) return@use false
            val head = ByteArray(12)
            raf.readFully(head)
            if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WEBP") {
                return@use false
            }
            var pos = 12L
            var scanned = 0
            while (pos + 8 <= raf.length() && scanned < 16) {
                raf.seek(pos)
                val tag = ByteArray(4).also { raf.readFully(it) }
                val sizeBytes = ByteArray(4).also { raf.readFully(it) }
                val size = (sizeBytes[0].toLong() and 0xFF) or ((sizeBytes[1].toLong() and 0xFF) shl 8) or
                    ((sizeBytes[2].toLong() and 0xFF) shl 16) or ((sizeBytes[3].toLong() and 0xFF) shl 24)
                when (String(tag, Charsets.US_ASCII)) {
                    "VP8L" -> return@use true
                    "VP8 " -> return@use false
                }
                pos += 8 + size + (size and 1)
                scanned++
            }
            false
        }
    } catch (e: IOException) {
        false
    }

    /**
     * 원본의 날짜·기기 EXIF 를 결과(JPEG 임시본)에 옮기고([writeDates]), 임시본이 **온전한가**를 돌려준다. 날짜를 못 옮긴 것은 실패가
     * 아니다 — 날짜 없는 그림이 없는 그림보다 낫다. 상했으면 false 다 — 부르는 쪽이 날짜 없이 다시 쓴다.
     *
     * 던졌는가가 아니라 **결과를 본다.** `ExifInterface.saveAttributes` 는 임시본을 제자리에서 다시 쓴다(`FileOutputStream(이름)` 으로
     * 자르고 처음부터 — exifinterface 1.4.2 바이트코드로 확인). 도중에 던지면 제 사본에서 되살리지만 그 되살리기까지 실패하면 잘린 채
     * 남고, 마지막 버퍼를 비우는 쓰기(닫을 때)가 실패하면 **던지지도 않는다**(`closeQuietly` 가 삼킨다 — 공간이 찬 때가 그렇다). 예전에는
     * 던진 것까지 삼키고 저장을 이어, 잘린 파일을 '저장했습니다' 로 놓을 수 있었다.
     */
    private fun copyDatesIntact(src: File, dst: File, writeDates: (File, File) -> Unit): Boolean {
        // 끝을 뜨지 못하면 손대지 않는다 — 날짜 없이 그대로 놓는다.
        val before = JpegTail.of(dst) ?: return true
        try {
            writeDates(src, dst)
        } catch (e: IOException) {
            Iro.d { "EXIF 날짜를 옮기지 못했다: ${e::class.java.simpleName}" }
        } catch (e: RuntimeException) {
            Iro.d { "EXIF 날짜를 옮기지 못했다: ${e::class.java.simpleName}" }
        }
        return before.keptIn(dst)
    }

    /** 원본의 날짜·기기 EXIF 를 [dst](JPEG 임시본)에 제자리에서 쓴다. 위치·방향은 옮기지 않는다(머리말). 던질 수 있다. */
    private fun copyDates(src: File, dst: File) {
        val from = ExifInterface(src.absolutePath)
        val to = ExifInterface(dst.absolutePath)
        var changed = false
        for (tag in DATE_TAGS) {
            val value = from.getAttribute(tag) ?: continue
            to.setAttribute(tag, value)
            changed = true
        }
        if (changed) to.saveAttributes()
    }

    /**
     * JPEG 임시본의 끝 — 날짜를 입히기 전에 떠 둔다. `saveAttributes` 는 처음 두 바이트(SOI) 뒤에 EXIF 를 끼우고 나머지를 그대로
     * 옮기므로, 온전히 다시 썼으면 새 파일은 짧아지지 않고 끝의 바이트(부호화한 화소와 EOI)가 같다. 쓰다 잘렸으면 끝이 다르다 — EOI
     * (`FF D9`)는 화소 자리에 나올 수 없다(바이트 채우기).
     */
    internal class JpegTail private constructor(private val length: Long, private val tail: ByteArray) {

        /** [file] 이 이 끝을 그대로 품고 있는가 — 짧아지지 않았고 끝의 바이트가 같다. 읽지 못하면 false. */
        fun keptIn(file: File): Boolean = try {
            RandomAccessFile(file, "r").use { raf ->
                val n = raf.length()
                if (n < length) return@use false
                val now = ByteArray(tail.size)
                raf.seek(n - tail.size)
                raf.readFully(now)
                now.contentEquals(tail)
            }
        } catch (e: IOException) {
            false
        }

        companion object {
            /** [file] 의 끝을 뜬다 — SOI(2바이트) 뒤에서 끝까지 가운데 마지막 [TAIL_BYTES] 바이트. 너무 짧거나 읽지 못하면 null. */
            fun of(file: File): JpegTail? = try {
                RandomAccessFile(file, "r").use { raf ->
                    val n = raf.length()
                    if (n <= 2) return@use null
                    val k = minOf(n - 2, TAIL_BYTES.toLong()).toInt()
                    val b = ByteArray(k)
                    raf.seek(n - k)
                    raf.readFully(b)
                    JpegTail(n, b)
                }
            } catch (e: IOException) {
                null
            }

            /** 견줄 끝의 길이 — 화소 자리만 들어갈 만큼(머리는 수백 바이트다), 다시 쓰다 잃는 버퍼(8 KiB)보다 작아도 끝이 어긋나 안다. */
            private const val TAIL_BYTES = 4096
        }
    }

    private val DATE_TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
    )

    // 플랫폼 디코더가 아이콘을 `image/x-ico` 로 말한다(ImageDecoder·BitmapFactory — `x-icon` 은 확장자 표의 이름이다).
    private val LOSSLESS_TO_PNG = setOf(
        "image/gif", "image/bmp", "image/x-ms-bmp", "image/vnd.wap.wbmp", "image/x-ico", "image/x-icon",
    )
    private val JPEG_EXTENSIONS = setOf("jpg", "jpeg", "jfif", "jpe")
    private const val JPEG_QUALITY = 95
    private const val CANVAS_MAX_BYTES = 100L * 1024 * 1024
    private const val BUFFER = 64 * 1024
}
