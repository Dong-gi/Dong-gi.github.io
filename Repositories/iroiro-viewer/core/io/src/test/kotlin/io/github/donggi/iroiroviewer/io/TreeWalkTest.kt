package io.github.donggi.iroiroviewer.io

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 폴더 나무 훑기의 세 겹(`TreeWalk`)과 그 위의 둘 — ZIP 만들기의 계획(`ZipMaker.plan`)·한계(`ZipMaker.admit`)와 폴더 정보(`FolderStats.tally`),
 * 그리고 묶어 보내기의 지우는 때(`ShareZips.isStale`).
 *
 * 기기의 나무로는 고리를 만들 수 없다(링크는 따라가지 않고 디렉터리 하드링크는 만들 수 없다). 가짜 나무([FakeFs])로 만든다.
 */
class TreeWalkTest {

    /** 가짜 파일시스템. 경로마다 (종류, inode, 링크 수, 크기)와 폴더면 이름 목록. 목록이 null 인 폴더는 '읽지 못한다'. */
    private class FakeFs : TreeWalk.Fs {
        private class Node(val stat: TreeWalk.Stat?, val children: Array<String>?)

        private val nodes = HashMap<String, Node>()
        private var nextIno = 100L

        fun dir(path: String, vararg children: String, ino: Long = nextIno++, readable: Boolean = true) {
            nodes[path] = Node(stat(TreeWalk.Type.DIRECTORY, ino, 1, 0), if (readable) arrayOf(*children) else null)
        }

        fun file(path: String, size: Long, ino: Long = nextIno++, links: Long = 1) {
            nodes[path] = Node(stat(TreeWalk.Type.FILE, ino, links, size), null)
        }

        fun link(path: String) {
            nodes[path] = Node(stat(TreeWalk.Type.LINK, nextIno++, 1, 10), null)
        }

        fun fifo(path: String) {
            nodes[path] = Node(stat(TreeWalk.Type.OTHER, nextIno++, 1, 0), null)
        }

        /** lstat 이 실패하는 자리(그사이 지워졌다). */
        fun gone(path: String) {
            nodes[path] = Node(null, null)
        }

        private fun stat(type: TreeWalk.Type, ino: Long, links: Long, size: Long) =
            TreeWalk.Stat(type, dev = 7, ino = ino, links = links, size = size, modifiedMillis = 1_000)

        override fun lstat(path: String): TreeWalk.Stat? = nodes[path]?.stat

        override fun list(path: String): Array<String>? = nodes[path]?.children?.copyOf()
    }

    /** 만난 것을 적는다. */
    private class Log(private val stopAfter: Int = Int.MAX_VALUE) : TreeWalk.Visitor {
        val entries = ArrayList<String>()
        val skips = ArrayList<TreeWalk.Skip>()
        var unreadable = 0

        override fun entry(path: String, relative: String, stat: TreeWalk.Stat, depth: Int): Boolean {
            if (entries.size >= stopAfter) return false
            entries += relative
            return true
        }

        override fun skipped(why: TreeWalk.Skip) {
            skips += why
        }

        override fun unreadable() {
            unreadable++
        }
    }

    @Test
    fun `링크와 장치는 따라가지도 담지도 않는다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "x.txt", "ln", "pipe")
            file("/s/a/x.txt", 5)
            link("/s/a/ln")
            fifo("/s/a/pipe")
        }
        val log = Log()
        TreeWalk.walk(listOf("/s/a"), fs, log, sorted = true)
        assertEquals(listOf("a", "a/x.txt"), log.entries)
        assertEquals(listOf(TreeWalk.Skip.NOT_REGULAR, TreeWalk.Skip.NOT_REGULAR), log.skips)
    }

    @Test
    fun `같은 폴더(장치·inode)를 두 번 들어가지 않는다 — 고리`() = runTest {
        // b 안의 loop 가 a 와 같은 inode 다(디렉터리 하드링크·바인드 마운트의 고리).
        val fs = FakeFs().apply {
            dir("/s/a", "b", ino = 1)
            dir("/s/a/b", "loop", ino = 2)
            dir("/s/a/b/loop", "b", ino = 1)
        }
        val log = Log()
        TreeWalk.walk(listOf("/s/a"), fs, log)
        assertEquals(listOf("a", "a/b"), log.entries)
        assertEquals(listOf(TreeWalk.Skip.SEEN), log.skips)
    }

    @Test
    fun `하드링크로 이름이 둘인 파일은 한 번만`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "one", "two", "solo")
            file("/s/a/one", 100, ino = 50, links = 2)
            file("/s/a/two", 100, ino = 50, links = 2)
            file("/s/a/solo", 1)
        }
        val log = Log()
        TreeWalk.walk(listOf("/s/a"), fs, log, sorted = true)
        assertEquals(listOf("a", "a/one", "a/solo"), log.entries)
        assertEquals(listOf(TreeWalk.Skip.SEEN), log.skips)
    }

    @Test
    fun `깊이 64 까지 — 그 아래는 건너뛴다`() = runTest {
        val fs = FakeFs()
        // 깊이 0 이 루트, 깊이 70 까지 폴더가 이어진다.
        var path = "/r"
        for (d in 0 until 70) {
            fs.dir(path, "d")
            path = "$path/d"
        }
        fs.dir(path)
        val log = Log()
        TreeWalk.walk(listOf("/r"), fs, log)
        assertEquals(TreeWalk.MAX_DEPTH + 1, log.entries.size, "깊이 0..64")
        assertEquals(listOf(TreeWalk.Skip.DEPTH), log.skips)
    }

    @Test
    fun `읽지 못한 폴더와 사라진 항목을 센다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "locked", "gone", "ok")
            dir("/s/a/locked", readable = false)
            gone("/s/a/gone")
            file("/s/a/ok", 3)
        }
        val log = Log()
        TreeWalk.walk(listOf("/s/a"), fs, log, sorted = true)
        // 못 읽는 폴더 자체는 담는다(ZIP 의 빈 폴더처럼) — 그 안을 못 본 것만 센다.
        assertEquals(listOf("a", "a/locked", "a/ok"), log.entries)
        assertEquals(2, log.unreadable)
    }

    @Test
    fun `거짓을 돌려주면 그 자리에서 멈춘다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "1", "2", "3")
            file("/s/a/1", 1)
            file("/s/a/2", 1)
            file("/s/a/3", 1)
            dir("/s/b", "4")
            file("/s/b/4", 1)
        }
        val log = Log(stopAfter = 2)
        TreeWalk.walk(listOf("/s/a", "/s/b"), fs, log, sorted = true)
        assertEquals(listOf("a", "a/1"), log.entries)
    }

    @Test
    fun `이름 잇기와 마지막 조각`() {
        assertEquals("/a/b", TreeWalk.join("/a", "b"))
        assertEquals("/b", TreeWalk.join("/", "b"))
        assertEquals("c", TreeWalk.nameOf("/a/b/c"))
        assertEquals("c", TreeWalk.nameOf("/a/b/c/"))
    }

    // ---- ZIP 만들기의 계획 ----

    @Test
    fun `ZIP 계획 — 폴더는 빗금으로 끝나고 이름 차례이며 상한에서 멈춘다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "z.txt", "m", "a.txt", "ln")
            file("/s/a/z.txt", 9)
            file("/s/a/a.txt", 4)
            dir("/s/a/m")
            link("/s/a/ln")
        }
        val plan = ZipMaker.plan(listOf("/s/a"), fs)
        assertEquals(listOf("a/", "a/a.txt", "a/m/", "a/z.txt"), plan.items.map { it.name })
        assertEquals(listOf(true, false, true, false), plan.items.map { it.isDirectory })
        assertEquals(13L, plan.workOnce)
        assertEquals(1, plan.skipped)
        assertFalse(plan.tooMany)

        val many = FakeFs()
        val names = (0..ZipMaker.MAX_ENTRIES).map { "f$it" }.toTypedArray()
        many.dir("/s/big", *names)
        for (n in names) many.file("/s/big/$n", 1)
        val big = ZipMaker.plan(listOf("/s/big"), many)
        assertTrue(big.tooMany)
        assertEquals(ZipMaker.MAX_ENTRIES, big.items.size)
    }

    @Test
    fun `묶어 보내기의 한계 — 크기 상한·남은 공간`() {
        val limits = ZipMaker.Limits(maxInputBytes = 1_000, reserveBytes = 100)
        assertNull(ZipMaker.admit(1_000, usable = 1_100, limits))
        assertEquals(FileOpEngine.Reason.TOO_LARGE, ZipMaker.admit(1_001, usable = 1_000_000, limits))
        assertEquals(FileOpEngine.Reason.NO_SPACE, ZipMaker.admit(1_000, usable = 1_099, limits))
        // 잴 수 없었다(0) — 막지 않는다. 쓰는 동안의 지킴이 그대로 돈다.
        assertNull(ZipMaker.admit(1_000, usable = 0, limits))
        // 크기 상한이 공간보다 먼저다 — 공간을 비워도 소용없는 것을 '공간이 없다' 로 말하지 않는다.
        assertEquals(FileOpEngine.Reason.TOO_LARGE, ZipMaker.admit(5_000, usable = 10, limits))
    }

    @Test
    fun `묶어 보내기의 상한은 문구가 말하는 2 GB 다`() {
        // `io_error_too_large` 가 '2 GB' 라고 적는다 — 값을 바꾸면 문구를 함께 고친다.
        assertEquals(2L * 1024 * 1024 * 1024, ShareZips.MAX_INPUT_BYTES)
        assertTrue(ShareZips.RESERVE_BYTES > ZipMaker.SPACE_MARGIN_BYTES, "다 묶은 끝에서 공간 지킴에 걸리지 않게")
    }

    @Test
    fun `묶어 보내기의 ZIP 은 놓은 지 한 시간 지나면 지운다`() {
        val now = 10L * ShareZips.KEEP_MS
        assertFalse(ShareZips.isStale(now, now))
        assertFalse(ShareZips.isStale(now - ShareZips.KEEP_MS, now))
        assertTrue(ShareZips.isStale(now - ShareZips.KEEP_MS - 1, now))
        // 시계를 조금 앞으로 맞춘 기기 — 둔다.
        assertFalse(ShareZips.isStale(now + 60_000, now))
        // 시계가 크게 뒤로 간 기기 — 영영 남지 않게 지운다.
        assertTrue(ShareZips.isStale(now + ShareZips.KEEP_MS + 1, now))
    }

    @Test
    fun `공유 창을 띄울 때 지울 때를 다시 센다 — 묶어 보내기의 자리 안에서만`() {
        val root = kotlin.io.path.createTempDirectory("sharezip").toFile()
        try {
            val dir = java.io.File(java.io.File(root, ShareZips.DIR), "u1").apply { mkdirs() }
            val zip = java.io.File(dir, "a.zip").apply { writeText("x") }
            dir.setLastModified(1_000_000L)
            ShareZips.touch(zip.path)
            assertTrue(dir.lastModified() > 1_000_000L)

            // 다른 자리(받은 사본의 inbox 등)는 건드리지 않는다.
            val other = java.io.File(java.io.File(root, "inbox"), "u2").apply { mkdirs() }
            val f = java.io.File(other, "b.zip").apply { writeText("y") }
            other.setLastModified(1_000_000L)
            ShareZips.touch(f.path)
            assertEquals(1_000_000L, other.lastModified())
        } finally {
            root.deleteRecursively()
        }
    }

    // ---- 숨김 규칙 ----

    @Test
    fun `묶어 보내기의 숨김 규칙 — MediaStore 임시 이름은 늘, 점 이름은 보기가 꺼졌을 때`() {
        for (show in listOf(true, false)) {
            assertTrue(ShareZips.excludes(".trashed-1712345678-IMG_1.jpg", show))
            assertTrue(ShareZips.excludes(".pending-1712345678-VID_2.mp4", show))
            assertFalse(ShareZips.excludes("사진.jpg", show))
            assertFalse(ShareZips.excludes("a.trashed-x", show), "점으로 시작할 때만")
        }
        assertTrue(ShareZips.excludes(".nomedia", showHidden = false))
        assertTrue(ShareZips.excludes(".thumbnails", showHidden = false))
        assertFalse(ShareZips.excludes(".nomedia", showHidden = true))
    }

    @Test
    fun `거르는 이름은 폴더째 빠지고 건너뜀에 든다 — 루트는 걸지 않는 것이 기본`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "x.jpg", ".hidden", ".trashed-1-y.jpg", "sub")
            file("/s/a/x.jpg", 10)
            dir("/s/a/.hidden", "secret.txt")
            file("/s/a/.hidden/secret.txt", 99)
            file("/s/a/.trashed-1-y.jpg", 7)
            dir("/s/a/sub", ".pending-2-z.mp4", "ok.txt")
            file("/s/a/sub/.pending-2-z.mp4", 5)
            file("/s/a/sub/ok.txt", 1)
        }
        val log = Log()
        TreeWalk.walk(listOf("/s/a"), fs, log, sorted = true, exclude = { ShareZips.excludes(it, showHidden = false) })
        assertEquals(listOf("a", "a/sub", "a/sub/ok.txt", "a/x.jpg"), log.entries)
        assertEquals(3, log.skips.count { it == TreeWalk.Skip.EXCLUDED }, "폴더 하나·파일 둘 — 숨은 폴더의 안은 세지 않는다")

        // 보기가 켜져 있으면 점 폴더는 담고 MediaStore 임시 이름만 뺀다.
        val on = Log()
        TreeWalk.walk(listOf("/s/a"), fs, on, sorted = true, exclude = { ShareZips.excludes(it, showHidden = true) })
        assertEquals(listOf("a", "a/.hidden", "a/.hidden/secret.txt", "a/sub", "a/sub/ok.txt", "a/x.jpg"), on.entries)
        assertEquals(2, on.skips.count { it == TreeWalk.Skip.EXCLUDED })

        // 루트에는 기본으로 걸지 않는다(폴더 정보가 고른 숨은 폴더를 센다). 묶어 보내기는 건다.
        val root = Log()
        TreeWalk.walk(listOf("/s/a/.hidden"), fs, root, exclude = { it.startsWith('.') })
        assertEquals(listOf(".hidden", ".hidden/secret.txt"), root.entries)
        val rootOn = Log()
        TreeWalk.walk(listOf("/s/a/.hidden"), fs, rootOn, exclude = { it.startsWith('.') }, excludeRoots = true)
        assertTrue(rootOn.entries.isEmpty())
    }

    @Test
    fun `묶어 보내기의 계획 — 뺀 것은 건너뜀, 크기에도 들지 않는다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "x.jpg", ".nomedia", ".cache")
            file("/s/a/x.jpg", 10)
            file("/s/a/.nomedia", 0)
            dir("/s/a/.cache", "big.bin")
            file("/s/a/.cache/big.bin", 1_000_000)
        }
        val plan = ZipMaker.plan(listOf("/s/a"), fs, exclude = { ShareZips.excludes(it, showHidden = false) })
        assertEquals(listOf("a/", "a/x.jpg"), plan.items.map { it.name })
        assertEquals(2, plan.skipped)
        assertEquals(10L, plan.workOnce)
        // ZIP 만들기(사용자 저장소에 남는 것)는 거르지 않는다.
        assertEquals(5, ZipMaker.plan(listOf("/s/a"), fs).items.size)
    }

    @Test
    fun `폴더 정보도 목록의 숨김 규칙 — 크기도 그 기준`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "x", ".cache", ".nomedia")
            file("/s/a/x", 10)
            dir("/s/a/.cache", "y")
            file("/s/a/.cache/y", 500)
            file("/s/a/.nomedia", 0)
        }
        val off = FolderStats.tally("/s/a", fs, { 0L }, FolderStats.hiddenRule(showHidden = false)) {}
        assertEquals(FolderStats.Count(files = 1, folders = 0, bytes = 10, partial = false, done = true), off)
        val on = FolderStats.tally("/s/a", fs, { 0L }, FolderStats.hiddenRule(showHidden = true)) {}
        assertEquals(FolderStats.Count(files = 3, folders = 1, bytes = 510, partial = false, done = true), on)
        // 고른 폴더 자체가 숨은 이름이어도 센다(루트에는 걸지 않는다).
        assertEquals(1, FolderStats.tally("/s/a/.cache", fs, { 0L }, FolderStats.hiddenRule(false)) {}.files)
    }

    @Test
    fun `띄우지 않은 묶음은 한 시간이 지나도 지우지 않는다 — 띄우면 그때부터 센다`() {
        val root = kotlin.io.path.createTempDirectory("sharezip-purge").toFile()
        try {
            val old = 1_000_000L
            val now = old + 10 * ShareZips.KEEP_MS
            val shown = java.io.File(root, "shown").apply { mkdirs(); java.io.File(this, "a.zip").writeText("a"); setLastModified(old) }
            val waiting = java.io.File(root, "waiting").apply { mkdirs(); java.io.File(this, "b.zip").writeText("b"); setLastModified(old) }
            ShareZips.markUnshown(waiting)
            ShareZips.purgeIn(root, now)
            assertFalse(shown.exists(), "띄운 지 오래된 것은 지운다")
            assertTrue(waiting.exists(), "아직 띄우지 않은 것은 둔다")

            // 공유 창을 띄우면 빠지고 시각이 지금이 된다 — 그 뒤 한 시간이 지나면 지운다.
            val zipDir = java.io.File(java.io.File(root, ShareZips.DIR), "w2").apply { mkdirs() }
            val zip = java.io.File(zipDir, "c.zip").apply { writeText("c") }
            ShareZips.markUnshown(zipDir)
            zipDir.setLastModified(old)
            ShareZips.purgeIn(zipDir.parentFile!!, now)
            assertTrue(zipDir.exists())
            ShareZips.touch(zip.path)
            ShareZips.purgeIn(zipDir.parentFile!!, System.currentTimeMillis() + 2 * ShareZips.KEEP_MS)
            assertFalse(zipDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `ZIP 이름 — 여럿이면 화면의 폴더 이름(볼륨 맨 위는 볼륨 이름), 비면 대신 이름`() {
        val many = listOf(java.io.File("/storage/emulated/0/a.jpg"), java.io.File("/storage/emulated/0/b.jpg"))
        assertEquals("내부 저장소.zip", ZipMaker.baseName(many, "내부 저장소", "압축 파일"))
        assertEquals("압축 파일.zip", ZipMaker.baseName(many, "", "압축 파일"))
        assertEquals("a.zip", ZipMaker.baseName(listOf(java.io.File("/storage/emulated/0/a.jpg")), "무엇이든", "압축 파일"))
    }

    // ---- 폴더 정보 ----

    @Test
    fun `폴더 정보 — 안의 파일·폴더 수와 길이의 합, 고른 폴더 자체는 세지 않는다`() = runTest {
        val fs = FakeFs().apply {
            dir("/s/a", "x", "sub", "ln", "hard1", "hard2")
            file("/s/a/x", 10)
            dir("/s/a/sub", "y")
            file("/s/a/sub/y", 32)
            link("/s/a/ln")
            file("/s/a/hard1", 100, ino = 9, links = 2)
            file("/s/a/hard2", 100, ino = 9, links = 2)
        }
        val c = FolderStats.tally("/s/a", fs, { 0L }) {}
        assertEquals(FolderStats.Count(files = 3, folders = 1, bytes = 142, partial = false, done = true), c)
    }

    @Test
    fun `폴더 정보 — 읽지 못한 것·깊이 상한이 있으면 일부다`() = runTest {
        val locked = FakeFs().apply {
            dir("/s/a", "Android")
            dir("/s/a/Android", readable = false)
        }
        val c = FolderStats.tally("/s/a", locked, { 0L }) {}
        assertTrue(c.partial)
        assertEquals(1, c.folders)

        val deep = FakeFs()
        var path = "/r"
        for (d in 0 until 70) {
            deep.dir(path, "d")
            path = "$path/d"
        }
        deep.dir(path)
        assertTrue(FolderStats.tally("/r", deep, { 0L }) {}.partial)

        // 링크·두 번째 이름은 빠뜨린 것이 아니다 — 일부가 아니다.
        val links = FakeFs().apply {
            dir("/s/a", "ln")
            link("/s/a/ln")
        }
        assertFalse(FolderStats.tally("/s/a", links, { 0L }) {}.partial)
    }

    @Test
    fun `폴더 정보 — 세는 동안 시계가 흐를 때만 중간값을 낸다`() = runTest {
        val fs = FakeFs()
        val names = (0 until 10).map { "f$it" }.toTypedArray()
        fs.dir("/s/a", *names)
        for (n in names) fs.file("/s/a/$n", 1)
        // 시계가 항목마다 100 ms 씩 간다 — 150 ms 마다 하나.
        var t = 0L
        val seen = ArrayList<FolderStats.Count>()
        val last = FolderStats.tally("/s/a", fs, { t.also { t += 100_000_000L } }) { seen += it }
        assertTrue(seen.isNotEmpty() && seen.size < 11, "중간값 ${seen.size}개")
        assertTrue(seen.all { !it.done })
        assertTrue(seen.zipWithNext().all { (a, b) -> a.files <= b.files }, "늘기만 한다")
        assertTrue(last.done)
        assertEquals(10, last.files)

        // 시계가 서 있으면 중간값이 없다.
        val none = ArrayList<FolderStats.Count>()
        FolderStats.tally("/s/a", fs, { 0L }) { none += it }
        assertTrue(none.isEmpty())
    }
}
