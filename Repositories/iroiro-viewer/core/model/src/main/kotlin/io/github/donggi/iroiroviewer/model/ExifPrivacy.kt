package io.github.donggi.iroiroviewer.model

/**
 * 사진의 EXIF 에서 **지우는 태그와, 지울 수 있는 형식** — 한 벌만 둔다.
 *
 * 쓰는 곳이 셋이다 — 뷰어의 '메타데이터 제거하고 공유'(`core:ui` 의 `ExifReader.sanitizedCopy`), 갤러리에서 여럿 골라 '위치 지우고
 * 공유'(`core:io` 의 `ShareZips.cleanCopies`), 갤러리의 '원본에서 위치 지우기'(`FileOpEngine.stripLocation`). `core:ui` 와 `core:io` 는
 * 서로를 보지 않으므로 둘 다 보는 이 모듈에 둔다 — 두 벌이면 한쪽만 고쳐진다.
 *
 * 태그 이름은 ExifInterface 의 `TAG_*` 상수의 **값**이다(이 모듈은 안드로이드를 보지 않는다). 상수와 같은지는 `core:io` 의 시험이
 * 지킨다(`core:io` 의 `GalleryLocationTest` — 자바의 문자열 상수는 컴파일 때 박히므로 JVM 시험에서 클래스를 올리지 않고 견줄 수 있다).
 */
object ExifPrivacy {

    /**
     * 위치 태그 — **GPS 칸 전부**(EXIF 2.32 의 GPS IFD 가 정한 서른두 칸). 찍은 자리뿐 아니라 '향한 자리'(`GPSDest*`)·방향·속도·고도도
     * 그 자리를 말해 준다. '원본에서 위치 지우기' 는 **이것만** 지운다 — 나머지(찍은 날·카메라·방향)는 사용자의 기록이다.
     */
    val LOCATION_TAGS: List<String> = listOf(
        "GPSVersionID",
        "GPSLatitudeRef",
        "GPSLatitude",
        "GPSLongitudeRef",
        "GPSLongitude",
        "GPSAltitudeRef",
        "GPSAltitude",
        "GPSTimeStamp",
        "GPSSatellites",
        "GPSStatus",
        "GPSMeasureMode",
        "GPSDOP",
        "GPSSpeedRef",
        "GPSSpeed",
        "GPSTrackRef",
        "GPSTrack",
        "GPSImgDirectionRef",
        "GPSImgDirection",
        "GPSMapDatum",
        "GPSDestLatitudeRef",
        "GPSDestLatitude",
        "GPSDestLongitudeRef",
        "GPSDestLongitude",
        "GPSDestBearingRef",
        "GPSDestBearing",
        "GPSDestDistanceRef",
        "GPSDestDistance",
        "GPSProcessingMethod",
        "GPSAreaInformation",
        "GPSDateStamp",
        "GPSDifferential",
        "GPSHPositioningError",
    )

    /**
     * 남에게 보내는 사본에서 지우는 태그 — 위치에 더해 사람을 특정할 수 있는 것(일련번호·소유자·사용자 주석·설명·작가·저작권)과
     * `MakerNote`(제조사가 무엇을 넣는지 공개하지 않는다), 그리고 그림 속 주인공의 자리(`SubjectLocation` — 화소 좌표라 지도 위치는
     * 아니지만 예전부터 지웠다). 방향은 남긴다 — 지우면 사본이 눕는다.
     */
    val SHARE_TAGS: List<String> = LOCATION_TAGS + listOf(
        "SubjectLocation",
        "BodySerialNumber",
        "CameraOwnerName",
        "MakerNote",
        "UserComment",
        "ImageDescription",
        "Artist",
        "Copyright",
    )

    /** XMP 덩어리의 태그 이름(ExifInterface 의 `TAG_XMP`). */
    const val XMP_TAG = "Xmp"

    /**
     * 남에게 보내는 사본에서 지운다 — [SHARE_TAGS] 와 **XMP 통째**. [set] 은 ExifInterface 의 `setAttribute` 다.
     *
     * XMP 는 두 번 지운다. ExifInterface 1.4.2 의 JPEG 은 IFD0 의 XMP 칸(태그 700)이 있으면 그것을, 없으면 따로 된 XMP 덩이를 지운다(바이트코드 —
     * `setAttribute` 의 `getXmpHandlingForImageType`) — 둘 다 있는 파일은 첫 번째가 700 을, 두 번째가 따로 된 덩이를 지운다. PNG 은 `iTXt` 의 XMP 를
     * 지운다. **WebP 의 `XMP ` 청크는 지우지 못한다**(그 그릇은 태그 700 만 본다) — 그래서 다 쓴 사본을 [MetadataLocation] 으로 다시 본다.
     */
    fun scrubForShare(set: (String, String?) -> Unit) {
        for (t in SHARE_TAGS) set(t, null)
        set(XMP_TAG, null)
        set(XMP_TAG, null)
    }

    /**
     * 지운 **사본**을 만들 수 있는 형식(확장자) — ExifInterface 의 `saveAttributes` 가 쓰는 JPEG·PNG·WebP. `jfif`·`jpe` 는 JPEG 의
     * 다른 이름이다. HEIC·AVIF·RAW 는 쓰지 못한다 — 다시 인코딩하면 사용자의 HEIC 가 JPEG 이 된다.
     */
    fun canScrubCopy(name: String): Boolean = extensionOf(name) in COPY_EXTENSIONS

    /**
     * **원본 자리에서** 지울 수 있는 형식(확장자) — 회전 저장과 같은 표(JPEG·WebP). 다시 쓴 파일이 잘리지 않았는지 볼 길이 있는
     * 그릇만 받는다(`FileOpEngine.rewrittenWhole`). 움직이는 WebP 는 엔진이 내용을 보고 한 번 더 거른다 — 여기는 이름만 본다.
     */
    fun canScrubInPlace(name: String): Boolean = extensionOf(name) in IN_PLACE_EXTENSIONS

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private val COPY_EXTENSIONS = setOf("jpg", "jpeg", "jfif", "jpe", "png", "webp")
    private val IN_PLACE_EXTENSIONS = setOf("jpg", "jpeg", "jfif", "jpe", "webp")
}
