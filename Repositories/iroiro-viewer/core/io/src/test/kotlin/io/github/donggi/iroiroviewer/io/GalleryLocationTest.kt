package io.github.donggi.iroiroviewer.io

import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.ExifPrivacy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 갤러리의 위치 지우기 — 지우는 태그의 이름, 결과 문장의 갈래, 사본의 자리, 다시 쓴 사본의 잘림 검사.
 */
class GalleryLocationTest {

    /**
     * `ExifPrivacy` 의 태그 이름은 ExifInterface 상수의 **값**이다(그 모듈은 안드로이드를 보지 않는다). 하나라도 틀리면 `setAttribute` 가 말없이
     * 아무것도 지우지 않는다. 자바의 문자열 상수는 컴파일 때 박히므로 ExifInterface 를 올리지 않고(정적 초기화가 `android.util.Log` 를 부른다)
     * 견줄 수 있다.
     */
    @Test
    fun `태그 이름이 ExifInterface 의 상수와 같다`() {
        val gps = listOf(
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        )
        assertEquals(gps, ExifPrivacy.LOCATION_TAGS)
        val extra = listOf(
            ExifInterface.TAG_SUBJECT_LOCATION,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_MAKER_NOTE,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
        )
        assertEquals(gps + extra, ExifPrivacy.SHARE_TAGS)
        assertEquals(ExifInterface.TAG_XMP, ExifPrivacy.XMP_TAG)
    }

    /** 사본에서는 위치 칸들과 XMP 를 **두 번** 지운다(JPEG 의 IFD0 칸 700 과 따로 된 XMP 덩이가 함께 있는 파일). */
    @Test
    fun `공유 사본은 XMP 를 두 번 지운다`() {
        val calls = ArrayList<Pair<String, String?>>()
        ExifPrivacy.scrubForShare { tag, value -> calls += tag to value }
        assertTrue(calls.all { it.second == null })
        assertEquals(ExifPrivacy.SHARE_TAGS, calls.map { it.first }.dropLast(2))
        assertEquals(listOf(ExifPrivacy.XMP_TAG, ExifPrivacy.XMP_TAG), calls.takeLast(2).map { it.first })
    }

    /**
     * 결과 문장에 덧붙일 갈래 — 0 인 것은 말하지 않고, 차례는 위치 없음 → 지울 수 없는 형식 → XMP 에 위치 → 실패 → 남은 것(취소).
     * 남은 것은 고른 수에서 다룬 수를 뺀 것이다.
     */
    @Test
    fun `위치 지우기 결과의 갈래`() {
        assertEquals(emptyList(), FileOpText.locationParts(FileOpEngine.LocationTally(stripped = 3, total = 3)))
        assertEquals(
            listOf(
                FileOpText.LocationPart.NO_LOCATION to 2,
                FileOpText.LocationPart.UNSUPPORTED to 1,
                FileOpText.LocationPart.XMP to 3,
                FileOpText.LocationPart.FAILED to 4,
            ),
            FileOpText.locationParts(FileOpEngine.LocationTally(0, 2, 1, 3, 4, total = 10)),
        )
        // 도중에 취소 — 10장 가운데 4장을 다뤘다.
        val cancelled = FileOpEngine.LocationTally(stripped = 3, unsupported = 1, total = 10)
        assertEquals(4, cancelled.stripped + cancelled.skipped)
        assertEquals(6, cancelled.left)
        assertEquals(
            listOf(FileOpText.LocationPart.UNSUPPORTED to 1, FileOpText.LocationPart.LEFT to 6),
            FileOpText.locationParts(cancelled),
        )
    }

    /** 맨 위 이름이 겹치면 `(2)`·`(3)` 을 붙인다 — 확장자 앞에, 대소문자를 가리지 않고, 폴더면 그 아래 항목도 함께. 겹치지 않는 것은 그대로. */
    @Test
    fun `갤러리 묶음의 겹친 이름`() {
        fun item(path: String, name: String, dir: Boolean = false) = ZipMaker.Item(File(path), name, dir, 1, 0)
        val items = listOf(
            item("/a/DCIM/IMG_1.jpg", "IMG_1.jpg"),
            item("/a/Pictures/IMG_1.jpg", "IMG_1.jpg"),
            item("/b/kakao/img_1.JPG", "img_1.JPG"),
            item("/a/Pictures/other.png", "other.png"),
            item("/a/x/Album", "Album/", dir = true),
            item("/a/x/Album/p.jpg", "Album/p.jpg"),
            item("/a/y/Album", "Album/", dir = true),
            item("/a/y/Album/p.jpg", "Album/p.jpg"),
            item("/a/.hidden", ".hidden"),
            item("/b/.hidden", ".hidden"),
        )
        assertEquals(
            listOf(
                "IMG_1.jpg", "IMG_1 (2).jpg", "img_1 (3).JPG", "other.png",
                "Album/", "Album/p.jpg", "Album (2)/", "Album (2)/p.jpg",
                ".hidden", ".hidden (2)",
            ),
            ZipMaker.uniqueTopLevel(items).map { it.name },
        )
        // 겹치지 않으면 손대지 않는다.
        val plain = listOf(item("/a/1.jpg", "1.jpg"), item("/a/2.jpg", "2.jpg"))
        assertEquals(plain, ZipMaker.uniqueTopLevel(plain))
    }

    /** 사본은 `zip/<무작위>/<번호>/<이름>`, ZIP 은 `zip/<무작위>/<이름>` — 둘 다 같은 `<무작위>` 자리로 지울 때를 센다. 밖이면 null. */
    @Test
    fun `공유 자리의 무작위 폴더를 찾는다`() {
        val root = File("/cache/share/${ShareZips.DIR}")
        assertEquals(File(root, "u1"), ShareZips.batchDirOf(File(root, "u1/사진.zip")))
        assertEquals(File(root, "u1"), ShareZips.batchDirOf(File(root, "u1/0/IMG_1.jpg")))
        assertNull(ShareZips.batchDirOf(File("/storage/emulated/0/DCIM/a.jpg")))
        assertNull(ShareZips.batchDirOf(File(root, "u1/0/deep/IMG_1.jpg")))
    }

    /** 다시 쓴 PNG 사본은 `IEND` 로 끝나야 온전하다 — 잘렸으면 끝의 12바이트가 그것이 아니다. */
    @Test
    fun `PNG 는 IEND 로 끝나야 온전하다`() {
        val dir = Files.createTempDirectory("rw").toFile()
        try {
            val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            val iend = byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())
            val body = ByteArray(40) { it.toByte() }
            val whole = File(dir, "a.png").apply { writeBytes(sig + body + iend) }
            assertTrue(FileOpEngine.rewrittenWhole(whole, null))
            val cut = File(dir, "b.png").apply { writeBytes((sig + body + iend).copyOf(sig.size + body.size + 6)) }
            assertFalse(FileOpEngine.rewrittenWhole(cut, null))
            // PNG 도 WebP 도 아닌 것은 온전하다고 하지 않는다.
            val other = File(dir, "c.bin").apply { writeBytes(ByteArray(64)) }
            assertFalse(FileOpEngine.rewrittenWhole(other, null))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** JPEG 은 화소 자료의 끝이 같아야 온전하다(SOS 부터 뜬 끝 — `jpegTailOf`). */
    @Test
    fun `JPEG 은 끝이 같아야 온전하다`() {
        val dir = Files.createTempDirectory("rw").toFile()
        try {
            // SOI · APP1(길이 8) · SOS(길이 4) · 자료 · EOI
            val head = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0, 8, 1, 2, 3, 4, 5, 6)
            val sos = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 4, 9, 9)
            val data = ByteArray(100) { (it * 7).toByte() } + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
            val f = File(dir, "a.jpg").apply { writeBytes(head + sos + data) }
            val tail = FileOpEngine.jpegTailOf(f)!!
            assertEquals(sos.size + data.size, tail.size)
            // EXIF 가 자라도(머리가 길어져도) 끝이 같으면 온전하다.
            val grown = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0, 12) + ByteArray(10) + sos + data
            f.writeBytes(grown)
            assertTrue(FileOpEngine.rewrittenWhole(f, tail))
            f.writeBytes(grown.copyOf(grown.size - 3))
            assertFalse(FileOpEngine.rewrittenWhole(f, tail))
        } finally {
            dir.deleteRecursively()
        }
    }
}
