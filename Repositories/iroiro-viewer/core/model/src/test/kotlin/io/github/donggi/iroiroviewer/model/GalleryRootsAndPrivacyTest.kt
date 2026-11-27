package io.github.donggi.iroiroviewer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 갤러리가 훑는 뿌리(`GalleryRoots`)와 EXIF 에서 지우는 것(`ExifPrivacy`). */
class GalleryRootsAndPrivacyTest {

    /** 볼륨마다 사진 폴더 셋과 `Android/media` — 상한은 `Android/media` 에만 건다. */
    @Test
    fun `볼륨마다 넷이고 상한은 Android media 에만`() {
        val roots = GalleryRoots.candidates(listOf("/storage/emulated/0", "/storage/1234-ABCD/"))
        assertEquals(
            listOf(
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Android/media",
                "/storage/1234-ABCD/DCIM",
                "/storage/1234-ABCD/Pictures",
                "/storage/1234-ABCD/Download",
                "/storage/1234-ABCD/Android/media",
            ),
            roots.map { it.path },
        )
        for (r in roots) {
            val expected = if (r.path.endsWith("/Android/media")) GalleryRoots.MAX_MEDIA_FILES else Int.MAX_VALUE
            assertEquals(expected, r.maxFiles, r.path)
        }
        assertTrue(GalleryRoots.candidates(emptyList()).isEmpty())
    }

    /** 메신저 폴더의 사진인가 — 썸네일을 디스크에 남기지 않는 판단. 이름에 `Android/media` 가 들어간 사용자 폴더는 아니다. */
    @Test
    fun `Android media 아래인가`() {
        assertTrue(GalleryRoots.isMediaPath("/storage/emulated/0/Android/media/com.kakao.talk/KakaoTalk/a.jpg"))
        assertTrue(GalleryRoots.isMediaPath("/storage/1234-ABCD/Android/media/x/a.jpg"))
        assertFalse(GalleryRoots.isMediaPath("/storage/emulated/0/DCIM/Camera/a.jpg"))
        assertFalse(GalleryRoots.isMediaPath("/storage/emulated/0/MyAndroid/mediafiles/a.jpg"))
    }

    /** 글로 적힌 위치 — XMP 의 `exif:GPSLatitude`, DJI 의 `GpsLongitude`(대소문자), 읽는 버퍼의 경계에 걸친 것도. EXIF 의 숫자 칸은 아니다. */
    @Test
    fun `글로 적힌 위치를 찾는다`() {
        assertTrue(MetadataLocation.contains("<rdf:Description exif:GPSLatitude=\"37,33.99N\"/>".toByteArray()))
        assertTrue(MetadataLocation.contains("drone-dji:GpsLongitude=\"126.97\"".toByteArray()))
        assertFalse(MetadataLocation.contains("<x:xmpmeta><exif:DateTimeOriginal>2024</exif:DateTimeOriginal>".toByteArray()))
        assertFalse(MetadataLocation.contains(byteArrayOf(0x88.toByte(), 0x25, 0, 4, 0, 0, 0, 1)))
        // 64 KiB 버퍼의 경계에 걸쳐 놓는다.
        val pad = ByteArray(64 * 1024 - 5) { 'a'.code.toByte() }
        val stream = java.io.ByteArrayInputStream(pad + "GPSLatitude".toByteArray() + ByteArray(100))
        assertTrue(MetadataLocation.contains(stream))
        assertFalse(MetadataLocation.contains(java.io.ByteArrayInputStream(ByteArray(200_000))))
    }

    /** WebP 의 `XMP ` 청크 — ExifInterface 가 지우지 못하므로, 긴 이름이 없어도(`photoshop:City` 따위) 있기만 하면 위치가 있는 것으로 친다. */
    @Test
    fun `WebP 에 XMP 청크가 있으면 위치가 있는 것으로 친다`() {
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun webp(vararg chunks: Pair<String, ByteArray>): ByteArray {
            var body = "WEBP".toByteArray()
            for ((t, d) in chunks) body += t.toByteArray() + le(d.size) + d + (if (d.size % 2 == 1) byteArrayOf(0) else ByteArray(0))
            return "RIFF".toByteArray() + le(body.size) + body
        }
        val f = java.nio.file.Files.createTempFile("loc", ".webp").toFile()
        try {
            f.writeBytes(webp("VP8 " to ByteArray(31), "EXIF" to ByteArray(20)))
            assertFalse(MetadataLocation.fileHasLocation(f))
            f.writeBytes(webp("VP8 " to ByteArray(31), "XMP " to "<x:xmpmeta><photoshop:City>Seoul</photoshop:City></x:xmpmeta>".toByteArray()))
            assertTrue(MetadataLocation.fileHasLocation(f))
            // 위치가 없어 보이는 XMP 도 — 지우지 못하는 청크다.
            f.writeBytes(webp("VP8X" to ByteArray(10), "VP8 " to ByteArray(31), "XMP " to "<x:xmpmeta/>".toByteArray()))
            assertTrue(MetadataLocation.fileHasLocation(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `빈 갤러리 안내에 Android media 가 든다`() {
        assertEquals(listOf("DCIM", "Pictures", "Download", "Android/media"), GalleryRoots.SHOWN_FOLDERS)
    }

    /** 사본은 JPEG·PNG·WebP, 원본 자리는 JPEG·WebP(회전 저장과 같은 표). 대소문자는 가리지 않는다. 영상·HEIC 는 둘 다 아니다. */
    @Test
    fun `지울 수 있는 형식`() {
        for (n in listOf("a.jpg", "a.JPEG", "a.jfif", "a.jpe", "a.png", "a.WebP")) assertTrue(ExifPrivacy.canScrubCopy(n), n)
        for (n in listOf("a.jpg", "a.JPEG", "a.jfif", "a.jpe", "a.webp")) assertTrue(ExifPrivacy.canScrubInPlace(n), n)
        assertFalse(ExifPrivacy.canScrubInPlace("a.png"), "PNG 는 원본 자리에서 고치지 않는다")
        for (n in listOf("a.heic", "a.avif", "a.dng", "a.mp4", "a.gif", "jpg", "a.jpg.txt")) {
            assertFalse(ExifPrivacy.canScrubCopy(n), n)
            assertFalse(ExifPrivacy.canScrubInPlace(n), n)
        }
    }

    /** 위치 태그는 GPS 칸 전부(서른두 칸)이고 겹치지 않는다. 사본은 그것을 모두 지운다. 방향은 어느 쪽도 지우지 않는다 — 지우면 사진이 눕는다. */
    @Test
    fun `위치 태그는 사본에서도 지우고 방향은 남긴다`() {
        assertEquals(32, ExifPrivacy.LOCATION_TAGS.size)
        assertEquals(ExifPrivacy.LOCATION_TAGS.size, ExifPrivacy.LOCATION_TAGS.toSet().size)
        assertTrue(ExifPrivacy.LOCATION_TAGS.all { it.startsWith("GPS") })
        assertTrue(ExifPrivacy.SHARE_TAGS.containsAll(ExifPrivacy.LOCATION_TAGS))
        assertEquals(ExifPrivacy.SHARE_TAGS.size, ExifPrivacy.SHARE_TAGS.toSet().size)
        assertFalse("Orientation" in ExifPrivacy.SHARE_TAGS)
        // 원본 자리의 위치 지우기는 찍은 날·카메라를 남긴다.
        for (kept in listOf("DateTimeOriginal", "Make", "Model", "Orientation", "MakerNote")) {
            assertFalse(kept in ExifPrivacy.LOCATION_TAGS, kept)
        }
    }
}
