package io.github.donggi.iroiroviewer.ui.image

import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.ExifContainerGuard
import io.github.donggi.iroiroviewer.model.ExifPrivacy
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.model.JpegLayout
import io.github.donggi.iroiroviewer.model.MetadataLocation
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 사진에 적힌 촬영 정보. **값만 돌려준다.**
 *
 * 문구를 만들지 않는 것이 규칙이다 — 순수/공용 모듈이 화면 문장을 만들기 시작하면
 * 나중에 용어를 통일할 때 이 모듈까지 뒤져야 한다. 라벨은 `feature` 의 `strings.xml` 에 있다.
 *
 * ## 위치 정보를 따로 다루는 이유
 *
 * 이 앱은 개인용이지만 **공유 기능이 있다.** 사진 한 장에 찍힌 좌표는 집 주소이기도 하다.
 * 그래서 [ExifSummary.gps] 를 나머지와 섞지 않고, 화면이 기본으로 접어 둘 수 있게 가른다.
 * 지도 앱으로 보내는 인텐트도 만들지 않는다 — 좌표를 다른 앱에 흘리는 가장 쉬운 길이다.
 */
data class ExifSummary(
    val takenAt: String?,
    val cameraMake: String?,
    val cameraModel: String?,
    val lens: String?,
    val exposure: String?,
    val aperture: String?,
    val iso: String?,
    val focalLength: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val orientation: Int,
    /** 위도·경도. 없으면 null. **기본으로 감춘다.** */
    val gps: Pair<Double, Double>?,
    /** 일련번호·소유자처럼 사람을 특정할 수 있는 것. 역시 기본으로 감춘다. */
    val bodySerial: String?,
    val owner: String?,
)

object ExifReader {

    suspend fun read(file: File): ExifSummary? = withContext(IroDispatchers.io) {
        if (!file.isFile) return@withContext null
        // 덩이·IFD 칸의 길이가 터무니없으면 넘기지 않는다 — 할당이 성공해도 다른 스레드가 죽는다(`ExifContainerGuard`).
        // HEIF·AVIF 는 검사기가 걸어 본 Exif 블록만 연다(ExifInterface 의 HEIF 갈래를 타지 않는다). 다 읽고 나서 닫는다.
        val src = runCatching { ExifContainerGuard.openForExif(file) }.getOrNull() ?: return@withContext null
        val exif = runCatching { src.use { ExifInterface(it.input, it.streamType) } }.getOrNull() ?: return@withContext null

        fun tag(name: String): String? = exif.getAttribute(name)?.takeIf { it.isNotBlank() }

        ExifSummary(
            // 촬영 시각은 벽시계 그대로 보여 준다. 시간대 태그가 없는 사진이 훨씬 많고,
            // 없는 것을 UTC 로 가정해 변환하면 **찍은 시각이 아닌 수**가 화면에 뜬다.
            takenAt = tag(ExifInterface.TAG_DATETIME_ORIGINAL) ?: tag(ExifInterface.TAG_DATETIME),
            cameraMake = tag(ExifInterface.TAG_MAKE),
            cameraModel = tag(ExifInterface.TAG_MODEL),
            lens = tag(ExifInterface.TAG_LENS_MODEL),
            exposure = tag(ExifInterface.TAG_EXPOSURE_TIME),
            aperture = tag(ExifInterface.TAG_F_NUMBER),
            iso = tag(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            focalLength = tag(ExifInterface.TAG_FOCAL_LENGTH),
            pixelWidth = src.orFallback(ExifInterface.TAG_IMAGE_WIDTH, exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)),
            pixelHeight = src.orFallback(ExifInterface.TAG_IMAGE_LENGTH, exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)),
            orientation = src.orFallback(
                ExifInterface.TAG_ORIENTATION,
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            ),
            gps = exif.latLong?.let { it[0] to it[1] },
            bodySerial = tag(ExifInterface.TAG_BODY_SERIAL_NUMBER),
            owner = tag(ExifInterface.TAG_CAMERA_OWNER_NAME),
        )
    }

    /**
     * 사람을 특정할 수 있는 태그를 지운 **사본**을 만든다. 원본은 건드리지 않는다.
     *
     * 되는 형식은 `saveAttributes` 가 쓸 수 있는 것뿐이다(JPEG·PNG·WebP). HEIC·AVIF·RAW 는
     * 라이브러리가 쓰기를 지원하지 않아 **재인코딩 말고는 길이 없는데, 그것은 사용자의
     * HEIC 를 JPEG 으로 바꾸는 일**이라 하지 않는다. 화면이 "이 형식은 제거할 수 없습니다"
     * 로 정확히 끝낸다.
     *
     * @return 정제된 사본. 만들 수 없으면 null.
     */
    suspend fun sanitizedCopy(src: File, destDir: File): File? = withContext(IroDispatchers.io) {
        if (!src.isFile) return@withContext null
        if (!canSanitize(src.name)) return@withContext null
        if (!destDir.isDirectory && !destDir.mkdirs()) return@withContext null

        val dest = File(destDir, src.name)
        runCatching {
            // 덩이·IFD 칸의 길이가 터무니없는 원본은 정제하지 않는다 — '제거할 수 없다' 로 끝난다(null).
            check(ExifContainerGuard.allows(src))
            // JPEG 은 주 그림의 EOI 에서 자르고 IPTC(APP13)를 뺀다(`JpegLayout.copyForShare` — 여럿 골라 보내는 길과 같다).
            dest.outputStream().use { JpegLayout.copyForShare(src, it) }
            val exif = ExifInterface(dest.absolutePath)
            // 지우는 것은 여럿 골라 보내는 길(`ShareZips.cleanCopies`)과 한 벌이다(`ExifPrivacy.scrubForShare` — XMP 도 통째로).
            // 방향은 남긴다. 지우면 사본이 눕는다 — '정보를 지우는 것' 이 '그림을 망치는 것' 이 되면 안 된다.
            ExifPrivacy.scrubForShare(exif::setAttribute)
            exif.saveAttributes()
            // 글로 적힌 위치가 남았으면(WebP 의 `XMP ` 청크 — 라이브러리가 지우지 못한다) 보내지 않는다 — '제거할 수 없다' 로 끝난다.
            check(!MetadataLocation.fileHasLocation(dest))
            dest
        }.getOrElse {
            dest.delete()
            null
        }
    }

    /** 이 확장자에서 메타데이터를 지울 수 있는가. `jfif`·`jpe` 는 JPEG 의 다른 이름이다(목록이 그림으로 보는 이름과 맞춘다). */
    fun canSanitize(name: String): Boolean = ExifPrivacy.canScrubCopy(name)

    /**
     * 이 파일에 **회전을 저장할 수 있는가.**
     *
     * 기준이 "라이브러리가 쓸 수 있는가" 가 아니라 **"쓰면 실제로 돌아 보이는가"** 임에
     * 주의하라. 셋으로 갈린다.
     *
     * - **JPEG** — 쓸 수 있고 디코더가 EXIF 방향을 적용한다. 된다.
     * - **PNG** — 쓸 수는 있는데 **안드로이드의 PNG 디코더가 EXIF 를 읽지 않는다.**
     *   파일은 바뀌는데 어느 뷰어에서도 아무 일이 일어나지 않는다 — 가장 나쁜 결과다.
     * - **WebP** — 쓸 수 있고 적용된다. 다만 **움직이는 WebP 는 제외한다**:
     *   `saveAttributes` 가 컨테이너를 통째로 다시 쓰는데 프레임이 보존되는지
     *   확인하지 못했고, 확인 못 한 것으로 사용자 파일을 걸지 않는다([ImageFormats]).
     * - 그 밖(HEIC·AVIF·RAW·GIF·BMP) — `saveAttributes` 자체가 던진다.
     *   재인코딩은 사용자의 HEIC 를 JPEG 으로 바꾸는 일이라 하지 않는다.
     */
    fun canRotate(file: File): Boolean {
        return when (file.name.substringAfterLast('.', "").lowercase()) {
            // `jfif`·`jpe` 는 JPEG 의 다른 이름이다. 엔진(`FileOpEngine.canRotateFile`)과 같은 표여야 한다.
            // 덩이·IFD 칸의 길이가 터무니없는 그릇은 엔진이 거절한다 — 화면이 먼저 '이 형식은 …저장할 수 없습니다' 로 끝내게 같은 검사를 둔다.
            "jpg", "jpeg", "jfif", "jpe" -> containerOk(file)
            "webp" -> !ImageFormats.isAnimatedWebp(file) && containerOk(file)
            else -> false
        }
    }

    /** [ExifContainerGuard] 를 지나는가. 열지 못하면 아니다. */
    private fun containerOk(file: File): Boolean = try {
        ExifContainerGuard.allows(file)
    } catch (e: IOException) {
        false
    }
}
