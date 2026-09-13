package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 엔트리 목록 → 폴더 트리.
 *
 * **이 시험이 지키는 것은 '보이는 것과 푸는 것이 같은 것을 가리키는가' 다.** 트리가
 * 엔트리를 하나로 접거나 순서를 바꾸면, 사용자가 고른 줄과 실제로 풀리는 바이트가
 * 달라진다 — 2단계가 이름을 신원으로 쓰다가 이미 한 번 겪은 결함이다.
 */
class ArchiveTreeTest {

    private fun entry(
        index: Int,
        name: String,
        dir: Boolean = false,
        size: Long = 100,
        encrypted: Boolean = false,
        link: Boolean = false,
    ) = ArchiveEntry(
        index = index,
        name = name,
        safeName = ArchiveEntry.sanitize(name),
        declaredSize = size,
        compressedSize = size / 2,
        isDirectory = dir,
        isLink = link,
        isEncrypted = encrypted,
        crc = -1,
        nameCharset = "시험",
    )

    private fun names(n: ArchiveTree.Node) = n.children.map { it.name }

    /** 많은 압축 도구가 디렉터리 엔트리를 쓰지 않는다. 없으면 우리가 만들어야 한다. */
    @Test
    fun `디렉터리 엔트리가 없어도 폴더가 생긴다`() {
        val tree = ArchiveTree.build(
            listOf(
                entry(0, "x/y/z/1.txt"),
                entry(1, "x/y/2.txt"),
                entry(2, "x/3.txt"),
            ),
        )
        assertEquals(listOf("x"), names(tree.root))
        val x = tree.folderAt("x")
        assertNotNull(x)
        assertEquals(listOf("y", "3.txt"), names(x))
        assertEquals(-1, x.entryIndex, "합성한 폴더는 엔트리 번호가 없어야 한다")
        val z = tree.folderAt("x/y/z")
        assertNotNull(z)
        assertEquals(listOf("1.txt"), names(z))
        assertEquals(3, tree.root.fileCount)
    }

    /**
     * **같은 이름 두 엔트리가 접히면 안 된다.**
     *
     * ZIP 명세가 금지하지 않는 구성이고, 접히면 사용자가 고른 줄과 다른 바이트가 열린다.
     */
    @Test
    fun `같은 이름이 둘이면 둘 다 남는다`() {
        val tree = ArchiveTree.build(listOf(entry(0, "same.txt"), entry(1, "same.txt")))
        assertEquals(2, tree.root.children.size)
        assertEquals(listOf(0, 1), tree.root.children.map { it.entryIndex })
        assertEquals(listOf("same.txt", "same.txt"), names(tree.root))
    }

    /** 풀 수 없는 이름은 **숨기지 않는다.** 숨기면 무엇이 들었는지 영영 알 수 없다. */
    @Test
    fun `풀 수 없는 이름도 보이되 표시된다`() {
        val tree = ArchiveTree.build(
            listOf(
                entry(0, "ok.txt"),
                entry(1, "../../../etc/passwd"),
                entry(2, "trailing. "),
            ),
        )
        val unsafe = tree.root.children.filter { it.unsafe }
        assertEquals(2, unsafe.size, "탈출 이름과 끝점 이름 둘 다 표시돼야 한다")
        assertTrue(unsafe.all { it.entryIndex >= 0 })

        // 풀기 대상에는 들어가지 않는다.
        val indices = ArrayList<Int>()
        tree.root.collectIndices(indices)
        assertEquals(listOf(0), indices)
    }

    /** 절대경로는 다듬어 트리에 넣되 **원래 이름을 함께 든다.** */
    @Test
    fun `절대경로는 다듬어지고 원래 이름이 남는다`() {
        val tree = ArchiveTree.build(listOf(entry(0, "/etc/passwd")))
        val etc = tree.folderAt("etc")
        assertNotNull(etc)
        val f = etc.children.single()
        assertEquals("passwd", f.name)
        assertEquals("/etc/passwd", f.rawName)
        assertEquals("etc/passwd", f.path)
    }

    /** `a` 가 파일이면서 `a/b` 도 있는 아카이브. 합치지 않고 형제로 둔다. */
    @Test
    fun `같은 이름이 파일이자 폴더일 수 있다`() {
        val tree = ArchiveTree.build(listOf(entry(0, "a"), entry(1, "a/b.txt")))
        assertEquals(2, tree.root.children.size)
        val dir = tree.root.children.first { it.isDirectory }
        val file = tree.root.children.first { !it.isDirectory }
        assertEquals("a", dir.name)
        assertEquals("a", file.name)
        assertEquals(0, file.entryIndex)
        assertEquals(listOf("b.txt"), names(dir))
    }

    /** 목록 화면과 **같은 규칙**으로 정렬한다 — 폴더 먼저, 그 다음 한국어 자연 정렬. */
    @Test
    fun `폴더가 먼저고 자연 정렬이다`() {
        val tree = ArchiveTree.build(
            listOf(
                entry(0, "10.txt"),
                entry(1, "2.txt"),
                entry(2, "나폴더/x", dir = false),
                entry(3, "가폴더/x", dir = false),
                entry(4, "가.txt"),
            ),
        )
        assertEquals(listOf("가폴더", "나폴더", "2.txt", "10.txt", "가.txt"), names(tree.root))
    }

    @Test
    fun `폴더째 풀기는 하위 엔트리를 전부 모은다`() {
        val tree = ArchiveTree.build(
            listOf(
                entry(0, "docs/", dir = true), // 진짜 디렉터리 엔트리
                entry(1, "docs/a.txt"),
                entry(2, "docs/sub/b.txt"),
                entry(3, "밖.txt"),
            ),
        )
        val docs = tree.folderAt("docs")
        assertNotNull(docs)
        assertEquals(0, docs.entryIndex, "진짜 디렉터리 엔트리는 번호를 갖는다")
        val indices = ArrayList<Int>()
        docs.collectIndices(indices)
        assertEquals(listOf(1, 2), indices.sorted(), "폴더 엔트리 자신은 풀 대상이 아니다")
        assertEquals(2, docs.fileCount)
    }

    @Test
    fun `크기 합계는 선언 크기를 더한 것이다`() {
        val tree = ArchiveTree.build(
            listOf(entry(0, "a/1", size = 100), entry(1, "a/2", size = 250), entry(2, "b", size = 7)),
        )
        assertEquals(357, tree.root.totalDeclaredSize)
        assertEquals(350, tree.folderAt("a")!!.totalDeclaredSize)
    }

    @Test
    fun `없는 폴더를 물으면 null`() {
        val tree = ArchiveTree.build(listOf(entry(0, "a/1.txt")))
        assertNull(tree.folderAt("없다"))
        assertNull(tree.folderAt("a/1.txt"), "파일은 폴더가 아니다")
        assertEquals(tree.root, tree.folderAt(""))
    }

    /** 진짜 ZIP 으로 한 번 훑는다 — 리더와 트리가 같은 번호를 쓰는지 확인한다. */
    @Test
    fun `진짜 ZIP 을 읽어 트리를 세운다`() {
        val dir = Files.createTempDirectory("iroiro-tree").toFile()
        try {
            val zip = ArchiveSamples.zipWithoutDirEntries(
                File(dir, "t.zip"),
                listOf("x/y/z/1.txt", "x/y/2.txt", "x/3.txt", "루트.txt"),
            )
            ZipArchiveReader(FileDocumentSource(zip), EntryBudget(ParseLimits.DEFAULT)).use { reader ->
                val tree = ArchiveTree.build(reader.entries)
                assertEquals(listOf("x", "루트.txt"), names(tree.root))
                assertEquals(4, tree.root.fileCount)

                // 트리가 든 번호로 리더를 열면 그 파일이 나와야 한다.
                val node = tree.folderAt("x/y/z")!!.children.single()
                val text = reader.open(reader.entries[node.entryIndex]).use { String(it.readBytes()) }
                assertTrue(text.startsWith("x/y/z/1.txt"), "다른 엔트리가 열렸다: $text")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
