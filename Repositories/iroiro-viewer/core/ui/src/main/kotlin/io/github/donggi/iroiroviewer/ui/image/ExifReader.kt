package io.github.donggi.iroiroviewer.ui.image

import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
            ?: return@withContext null

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
            pixelWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
            pixelHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
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
            src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            val exif = ExifInterface(dest.absolutePath)
            for (t in STRIPPED) exif.setAttribute(t, null)
            // 방향은 남긴다. 지우면 사본이 눕는다 — '정보를 지우는 것' 이 '그림을 망치는 것'
            // 이 되면 안 된다.
            exif.saveAttributes()
            dest
        }.getOrElse {
            dest.delete()
            null
        }
    }

    /** 이 확장자에서 메타데이터를 지울 수 있는가. */
    fun canSanitize(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")

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
            "jpg", "jpeg" -> true
            "webp" -> !ImageFormats.isAnimatedWebp(file)
            else -> false
        }
    }

    /**
     * 지우는 태그.
     *
     * 위치·일련번호·소유자·사용자 주석이 핵심이고, `MakerNote` 는 제조사가 무엇을 넣는지
     * 공개하지 않으므로 통째로 지운다. EXIF 썸네일도 지운다 — 본문을 편집해도 썸네일에
     * 옛 그림이 남는 것이 알려진 유출 경로다.
     */
    private val STRIPPED = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_MAKER_NOTE,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_SUBJECT_LOCATION,
    )
}
