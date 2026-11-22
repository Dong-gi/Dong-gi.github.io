package io.github.donggi.iroiroviewer.model

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 갤러리의 앨범 — 훑기 결과를 폴더로 묶기(`GalleryAlbums`). */
class GalleryAlbumsTest {

    private fun e(path: String, kind: FileKind = FileKind.IMAGE) = FileEntry(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = false,
        size = 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = kind,
    )

    private val cam = "/storage/emulated/0/DCIM/Camera"
    private val shots = "/storage/emulated/0/Pictures/Screenshots"
    private val kakao = "/storage/emulated/0/Android/media/com.kakao.talk/KakaoTalk"

    /** 목록은 새것부터다 — 앨범의 차례·표지·개수가 그 차례를 그대로 물려받는다. */
    @Test
    fun `앨범은 가장 새 것이 새로운 폴더부터 표지는 그 폴더의 맨 앞`() {
        val items = listOf(
            e("$shots/s3.png"),
            e("$cam/c2.jpg"),
            e("$shots/s2.png"),
            e("$kakao/k1.jpg"),
            e("$cam/c1.mp4", FileKind.VIDEO),
            e("$shots/s1.png"),
        )
        val albums = GalleryAlbums.of(items)
        assertEquals(listOf(shots, cam, kakao), albums.map { it.folder })
        assertEquals(listOf("s3.png", "c2.jpg", "k1.jpg"), albums.map { it.cover.name })
        assertEquals(listOf(3, 2, 1), albums.map { it.count })
        assertEquals(listOf("Screenshots", "Camera", "KakaoTalk"), albums.map { it.label })
    }

    @Test
    fun `빈 목록은 앨범이 없다`() {
        assertTrue(GalleryAlbums.of(emptyList()).isEmpty())
    }

    /** 내부 저장소와 SD 카드의 `DCIM/Camera` — 이름이 겹치면 겹치지 않을 때까지 위 폴더를 붙인다. 겹치지 않는 것은 그대로. */
    @Test
    fun `이름이 겹치면 겹치지 않는 가장 짧은 꼬리`() {
        val sd = "/storage/1234-ABCD/DCIM/Camera"
        val other = "/storage/emulated/0/Download/Camera"
        val labels = GalleryAlbums.labels(listOf(cam, sd, shots, other))
        assertEquals("0/DCIM/Camera", labels[cam])
        assertEquals("1234-ABCD/DCIM/Camera", labels[sd])
        assertEquals("Download/Camera", labels[other])
        assertEquals("Screenshots", labels[shots])
    }

    /** 볼륨의 경로 조각 대신 볼륨의 화면 이름을 쓴다 — SD 카드의 `DCIM/Camera` 가 `0/…`·`1234-ABCD/…` 로 보이지 않는다. */
    @Test
    fun `겹친 이름은 볼륨의 화면 이름으로 가른다`() {
        val sd = "/storage/1234-ABCD/DCIM/Camera"
        val volumes = mapOf("/storage/emulated/0" to "내부 저장소", "/storage/1234-ABCD/" to "SD 카드")
        val labels = GalleryAlbums.labels(listOf(cam, sd, shots, "/storage/emulated/0"), volumes)
        assertEquals("내부 저장소/DCIM/Camera", labels[cam])
        assertEquals("SD 카드/DCIM/Camera", labels[sd])
        assertEquals("Screenshots", labels[shots])
        // 볼륨 맨 위에 둔 사진의 앨범은 그 볼륨의 이름이다(경로 조각 `0` 이 아니다).
        assertEquals("내부 저장소", labels["/storage/emulated/0"])
        val albums = GalleryAlbums.of(listOf(e("$cam/a.jpg"), e("$sd/b.jpg")), volumes)
        assertEquals(listOf("내부 저장소/DCIM/Camera", "SD 카드/DCIM/Camera"), albums.map { it.label })
    }

    /** 한쪽이 더 늘릴 조각이 없으면(뿌리 바로 아래) 다른 쪽만 는다 — 그래도 끝난다. */
    @Test
    fun `한쪽이 더 늘 수 없어도 끝난다`() {
        val labels = GalleryAlbums.labels(listOf("/Camera", "/x/Camera"))
        assertEquals("Camera", labels["/Camera"])
        assertEquals("x/Camera", labels["/x/Camera"])
    }

    @Test
    fun `바로 위 폴더`() {
        assertEquals("/a/b", GalleryAlbums.folderOf("/a/b/c.jpg"))
        assertEquals("", GalleryAlbums.folderOf("c.jpg"))
        assertEquals("", GalleryAlbums.folderOf("/c.jpg"))
    }

    /**
     * 앨범을 열면 **갤러리의 날짜 구간을 잘라 쓴다** — 그 폴더의 것만 세고, 하나도 없는 날은 빠지고, 자리는 앨범 목록의 자리다(누른 칸과
     * 열리는 사진이 어긋나지 않게).
     */
    @Test
    fun `앨범의 날짜 구간은 갤러리 구간을 자른 것`() {
        val zone = ZoneId.of("Asia/Seoul")
        val d1 = DayBucket.dayOf(1_700_000_000_000L, zone)
        val d2 = DayBucket.dayOf(1_690_000_000_000L, zone)
        val d3 = DayBucket.dayOf(1_680_000_000_000L, zone)
        val items = listOf(
            e("$cam/a.jpg"), e("$shots/b.png"), // 날 1
            e("$shots/c.png"), // 날 2 — 카메라 없음
            e("$cam/d.jpg"), e("$shots/e.png"), e("$cam/f.jpg"), // 날 3
        )
        val sections = listOf(
            DayBucket.Section(d1, 0, 2),
            DayBucket.Section(d2, 2, 1),
            DayBucket.Section(d3, 3, 3),
        )
        val sub = GalleryAlbums.subset(items, sections, cam)
        assertEquals(listOf("a.jpg", "d.jpg", "f.jpg"), sub.items.map { it.name })
        assertEquals(listOf(DayBucket.Section(d1, 0, 1), DayBucket.Section(d3, 1, 2)), sub.sections)
        // 구간은 앨범 목록을 빈틈 없이 덮는다.
        assertEquals(sub.items.size, sub.sections.sumOf { it.count })
    }

    @Test
    fun `없는 폴더의 앨범은 비었다`() {
        val zone = ZoneId.of("UTC")
        val items = listOf(e("$cam/a.jpg"))
        val sub = GalleryAlbums.subset(items, listOf(DayBucket.Section(DayBucket.dayOf(0, zone), 0, 1)), "/nowhere")
        assertTrue(sub.items.isEmpty())
        assertTrue(sub.sections.isEmpty())
    }

    /** 목록을 넘는 구간(있을 수 없지만)이 와도 던지지 않는다. */
    @Test
    fun `목록을 넘는 구간은 목록에서 멈춘다`() {
        val zone = ZoneId.of("UTC")
        val items = listOf(e("$cam/a.jpg"))
        val sub = GalleryAlbums.subset(items, listOf(DayBucket.Section(DayBucket.dayOf(0, zone), 0, 5)), cam)
        assertEquals(1, sub.items.size)
        assertEquals(1, sub.sections.single().count)
    }
}
