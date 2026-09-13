package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.safety.PlaybackLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 폴더를 큐로 바꾸는 규칙.
 *
 * `android.*` 를 쓰지 않으므로 전부 여기서 돈다 — 상한과 창 계산이 에뮬레이터 없이
 * 박힌다. **8단계가 치명으로 고친 '고른 것과 다른 것이 열린다'** 가 이 파일이 지키는 것이다.
 */
class FolderQueueTest {

    private fun f(
        name: String,
        kind: FileKind = FileKind.AUDIO,
        dir: Boolean = false,
        locked: Boolean = false,
        link: Boolean = false,
    ) = FileEntry(
        name = name,
        path = "/v/$name",
        isDirectory = dir,
        size = 1000,
        lastModified = 0,
        isHidden = false,
        isSymlink = link,
        kind = kind,
        isLocked = locked,
    )

    /**
     * **큐에 드는 종류는 둘뿐이다** — 이 집합이 재생목록 화면의 아이콘 분기를 떠받친다.
     *
     * `PlaylistPanel` 의 `when` 은 `VIDEO` 면 캠코더, **그 밖에는 전부 음표**로 그린다.
     * `else` 로 끝나는 `when` 이라 종류가 늘어도 컴파일러가 아무 말을 하지 않는다 —
     * 9단계가 cbz·cbr 을 `ARCHIVE` 에서 `COMIC` 으로 옮기면서 다른 모듈의 분기가 조용히
     * 틀린 그 형태다(CLAUDE.md 함정 표).
     *
     * 그래서 여기서 집합 자체를 단언한다. [FolderQueue.isPlayable] 에 종류를 하나 더하면
     * **화면보다 먼저 이 시험이 깨진다.**
     */
    @Test
    fun 큐에_드는_종류는_소리와_영상_둘뿐이다() {
        for (kind in FileKind.entries) {
            val playable = FolderQueue.isPlayable(f("x", kind = kind))
            val expected = kind == FileKind.AUDIO || kind == FileKind.VIDEO
            assertEquals(expected, playable, "$kind 의 판정이 바뀌었다 — PlaylistPanel 의 아이콘 분기를 함께 보라")
        }
    }

    @Test
    fun 소리를_가진_것만_고른다() {
        val list = listOf(
            f("a.mp3"),
            f("b.mp4", FileKind.VIDEO),
            f("c.txt", FileKind.TEXT),
            f("사진.png", FileKind.IMAGE),
            f("폴더", dir = true),
            f("책.cbz", FileKind.COMIC),
        )
        val plan = FolderQueue.plan(list, null)
        assertEquals(listOf("a.mp3", "b.mp4"), plan.items.map { it.name })
        assertTrue(FolderQueue.hasPlayable(list))
        assertFalse(FolderQueue.hasPlayable(list.filter { it.kind == FileKind.TEXT }))
    }

    /** 들어갈 수 없는 것과 링크는 뺀다. 바이트를 꺼낼 수 없거나 어디를 가리키는지 모른다. */
    @Test
    fun 잠긴_것과_링크는_뺀다() {
        val list = listOf(f("정상.mp3"), f("잠김.mp3", locked = true), f("링크.mp3", link = true))
        assertEquals(listOf("정상.mp3"), FolderQueue.plan(list, null).items.map { it.name })
    }

    /** 누른 것에서 시작한다. 앞의 것은 큐에 남아 있어야 '이전' 이 된다. */
    @Test
    fun 누른_자리에서_시작한다() {
        val list = (1..10).map { f("%02d.mp3".format(it)) }
        val plan = FolderQueue.plan(list, "/v/05.mp3")
        assertEquals(10, plan.items.size)
        assertEquals(4, plan.startIndex)
        assertEquals("05.mp3", plan.items[plan.startIndex].name)
    }

    /** ▶ 로 시작하면 처음부터. */
    @Test
    fun 누른_것이_없으면_처음부터() {
        val list = (1..5).map { f("$it.mp3") }
        assertEquals(0, FolderQueue.plan(list, null).startIndex)
    }

    /** 누른 것이 재생할 수 없는 종류였으면 큐의 처음부터 튼다. 죽지 않는다. */
    @Test
    fun 누른_것이_목록에_없으면_처음부터() {
        val list = listOf(f("a.mp3"), f("메모.txt", FileKind.TEXT))
        val plan = FolderQueue.plan(list, "/v/메모.txt")
        assertEquals(0, plan.startIndex)
        assertEquals("a.mp3", plan.items[0].name)
    }

    @Test
    fun 미디어가_없으면_빈_계획() {
        val plan = FolderQueue.plan(listOf(f("x.txt", FileKind.TEXT)), null)
        assertTrue(plan.isEmpty)
    }

    /**
     * **상한보다 적은 보통 폴더에서 죽지 않는다.**
     *
     * 창 시작을 `coerceIn(0, count - MAX)` 로 구하면 `count < MAX` 일 때 하한이 상한보다
     * 커져 `IllegalArgumentException` 이 난다. 거의 모든 폴더가 그 경우다.
     */
    @Test
    fun 상한보다_적으면_전부_담고_죽지_않는다() {
        for (n in intArrayOf(1, 2, 7, 100, PlaybackLimits.MAX_QUEUE_ITEMS - 1, PlaybackLimits.MAX_QUEUE_ITEMS)) {
            val list = (1..n).map { f("%05d.mp3".format(it)) }
            for (tap in intArrayOf(0, n / 2, n - 1)) {
                val plan = FolderQueue.plan(list, list[tap].path)
                assertEquals(n, plan.items.size, "n=$n")
                assertEquals(tap, plan.startIndex, "n=$n tap=$tap")
            }
        }
    }

    /** 상한을 넘으면 자르되 **누른 항목은 반드시 담는다.** 어디를 눌러도. */
    @Test
    fun 상한을_넘어도_누른_항목은_큐에_있다() {
        val n = PlaybackLimits.MAX_QUEUE_ITEMS * 3
        val list = (1..n).map { f("%05d.mp3".format(it)) }
        for (tap in intArrayOf(0, 1, n / 3, n / 2, n - 2, n - 1)) {
            val plan = FolderQueue.plan(list, list[tap].path)
            assertEquals(PlaybackLimits.MAX_QUEUE_ITEMS, plan.items.size, "tap=$tap")
            assertTrue(plan.startIndex in plan.items.indices, "시작 자리가 창 밖이다: tap=$tap")
            assertEquals(list[tap].name, plan.items[plan.startIndex].name, "tap=$tap")
        }
    }

    /** 창은 이어진 조각이어야 한다 — 건너뛴 항목이 섞이면 '다음 곡' 이 폴더 순서를 어긴다. */
    @Test
    fun 창은_이어진_조각이다() {
        val n = PlaybackLimits.MAX_QUEUE_ITEMS * 2
        val list = (1..n).map { f("%05d.mp3".format(it)) }
        val plan = FolderQueue.plan(list, list[n - 1].path)
        val names = plan.items.map { it.name }
        val firstAt = list.indexOfFirst { it.name == names.first() }
        assertEquals(list.subList(firstAt, firstAt + names.size).map { it.name }, names)
    }
}
