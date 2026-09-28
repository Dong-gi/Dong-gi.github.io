package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchiveKind
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import io.github.donggi.iroiroviewer.format.archive.Archives
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** 압축 화면의 '다른 앱으로 열기' 를 어디에 띄우는가([ArchiveOpenWith]). */
class ArchiveOpenWithTest {

    private val kinds = ArchiveViewModel.State.Failed.Kind.entries

    /** 종류마다 답을 적는다. 종류가 늘면 [모든_종류의_답이_표에_있다] 가 깨진다(화면의 `when` 은 컴파일이 멈춘다). */
    private val expected = mapOf(
        // 이 앱이 목록을 못 보일 뿐 — 다른 압축 앱은 연다.
        ArchiveViewModel.State.Failed.Kind.UNSUPPORTED to true,
        ArchiveViewModel.State.Failed.Kind.CORRUPT to true,
        ArchiveViewModel.State.Failed.Kind.TOO_LARGE to true,
        ArchiveViewModel.State.Failed.Kind.TOO_BIG to true,
        ArchiveViewModel.State.Failed.Kind.ENCRYPTED to true,
        // 파일에 닿지 못했다 — 받는 앱도 닿지 못한다.
        ArchiveViewModel.State.Failed.Kind.UNREADABLE to false,
    )

    @Test
    fun 모든_종류의_답이_표에_있다() {
        assertEquals(kinds.toSet(), expected.keys)
    }

    @Test
    fun 실패_화면은_이_앱이_못_보여_줄_때만_권한다() {
        for (kind in kinds) assertEquals(expected.getValue(kind), ArchiveOpenWith.onFailure(kind), kind.name)
    }

    @Test
    fun 읽는_중에는_파일에_닿은_뒤부터_누를_수_있다() {
        // 닿기 전 — 없거나 열리지 않는 파일일 수 있다. 있는데 열리지 않는 파일은 `ExternalOpen` 이 거르지 못한다.
        assertFalse(ArchiveOpenWith.inMenu(ArchiveViewModel.State.Loading()))
        // 닿았다 — 압축한 tar 의 목록(몇 초)을 기다리지 않는다.
        assertTrue(ArchiveOpenWith.inMenu(ArchiveViewModel.State.Loading(reached = true)))
    }

    @Test
    fun 닿아_본_뒤의_상태가_메뉴를_정한다() {
        val reached = reachState(reachable = true)
        assertEquals(ArchiveViewModel.State.Loading(reached = true), reached)
        assertTrue(ArchiveOpenWith.inMenu(reached))
        val unreachable = reachState(reachable = false)
        assertEquals(ArchiveViewModel.State.Failed(ArchiveViewModel.State.Failed.Kind.UNREADABLE), unreachable)
        assertFalse(ArchiveOpenWith.inMenu(unreachable))
    }

    @Test
    fun 메뉴는_파일에_닿지_못한_때만_흐리다() {
        assertTrue(ArchiveOpenWith.inMenu(ArchiveViewModel.State.Loading(reached = true)))
        // 헤더까지 잠겨 암호를 묻는 중이어도 — 실패 화면에는 단추가 없지만(이 앱이 묻는다) 메뉴는 사용자가 고른 것이다.
        assertTrue(ArchiveOpenWith.inMenu(ArchiveViewModel.State.NeedsPassword))
        assertTrue(ArchiveOpenWith.inMenu(ArchiveViewModel.State.Ready(doc())))
        for (kind in kinds) {
            assertEquals(expected.getValue(kind), ArchiveOpenWith.inMenu(ArchiveViewModel.State.Failed(kind)), kind.name)
        }
        assertFalse(ArchiveOpenWith.inMenu(ArchiveViewModel.State.Failed(ArchiveViewModel.State.Failed.Kind.UNREADABLE)))
    }

    /**
     * **실제 실패의 길을 끝까지 탄다** — 리더가 던진 것 → [failureKindOf] → 단추. 종류를 옮기면(`failureKindOf` 를 고치면)
     * '다른 앱으로 열기' 가 조용히 사라지는 자리를 여기서 잡는다.
     */
    @Test
    fun iso와_tar가_아닌_gz와_깨진_zip에는_단추가_뜬다() {
        val dir = createTempDir()
        try {
            // iso — 우리가 아는 서명이 없다(ISO 9660 의 표지는 32 KiB 뒤에 있다).
            val iso = File(dir, "disc.iso").apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }
            // tar 가 아닌 것을 싼 gz 하나.
            val gz = File(dir, "note.txt.gz").apply {
                GZIPOutputStream(outputStream()).use { it.write("그냥 글 한 줄\n".toByteArray()) }
            }
            // ZIP 서명 뒤가 쓰레기다.
            val zip = File(dir, "broken.zip").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(200) { 0x7F })
            }
            val cases = mapOf(
                iso to ArchiveViewModel.State.Failed.Kind.UNSUPPORTED,
                gz to ArchiveViewModel.State.Failed.Kind.UNSUPPORTED,
                zip to ArchiveViewModel.State.Failed.Kind.CORRUPT,
            )
            for ((file, want) in cases) {
                val kind = kindOfOpening(file)
                assertEquals(want, kind, file.name)
                assertTrue(ArchiveOpenWith.onFailure(kind), file.name)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 이_앱이_풀지_않는_잠금에는_단추가_뜬다() {
        // PKWARE 의 강한 암호화는 리더가 `UnsupportedEncryptionException` 으로 알린다(`ZipDecryption`, 모듈 안쪽 타입).
        // [failureKindOf] 가 이름으로 가르므로 같은 이름의 예외로 그 길을 탄다.
        class UnsupportedEncryptionException : IOException("풀지 않는 암호화")
        val kind = failureKindOf(UnsupportedEncryptionException())
        assertEquals(ArchiveViewModel.State.Failed.Kind.ENCRYPTED, kind)
        assertTrue(ArchiveOpenWith.onFailure(kind))
    }

    /**
     * **열리지 않는 파일은 '깨졌다' 가 아니다.** 리더가 받으면 그 예외(`FileNotFoundException` — 안드로이드의 권한 거부도
     * 이것이다)가 [failureKindOf] 에서 CORRUPT 가 되어 단추가 뜬다. 그래서 목록을 읽기 전에 [isReachable] 로 가른다 —
     * 그 답은 단추가 없는 UNREADABLE 이다.
     */
    @Test
    fun 닿지_못하는_파일은_깨진_파일이_아니다() {
        val denied = FileNotFoundException("EACCES (Permission denied)")
        // 앞에서 가르지 않으면 이렇게 된다 — 이 시험이 지키는 까닭.
        assertEquals(ArchiveViewModel.State.Failed.Kind.CORRUPT, failureKindOf(denied))
        assertTrue(ArchiveOpenWith.onFailure(failureKindOf(denied)))

        val dir = createTempDir()
        try {
            val zip = File(dir, "a.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)) }
            assertTrue(isReachable(zip))
            assertFalse(isReachable(zip) { throw denied }, "있는데 열리지 않는다")
            assertFalse(isReachable(dir), "폴더")
            assertTrue(zip.delete())
            assertFalse(isReachable(zip), "없다")
        } finally {
            dir.deleteRecursively()
        }
        assertFalse(ArchiveOpenWith.onFailure(ArchiveViewModel.State.Failed.Kind.UNREADABLE))
    }

    /**
     * **읽는 사이에 없어진 파일도 '깨졌다' 가 아니다.** 열어 볼 때는 닿았는데 목록을 읽는 몇십 초 사이에 지워졌거나 SD 를
     * 뺐으면 리더는 맨 `IOException` 을 던지고, 그것만 보면 CORRUPT — 없는 파일에 단추가 뜬다. 실패한 뒤에 한 번 더 닿아
     * 본 답을 함께 본다.
     */
    @Test
    fun 읽는_사이에_없어진_파일은_깨진_파일이_아니다() {
        val gone = IOException("EIO")
        assertEquals(ArchiveViewModel.State.Failed.Kind.CORRUPT, failureKindOf(gone, reachable = true))
        assertEquals(ArchiveViewModel.State.Failed.Kind.UNREADABLE, failureKindOf(gone, reachable = false))
        assertFalse(ArchiveOpenWith.onFailure(failureKindOf(gone, reachable = false)))
        // 닿지 못하면 무엇이 던져졌든 읽을 수 없다 — 상한·잠금도.
        class UnsupportedEncryptionException : IOException("풀지 않는 암호화")
        for (t in listOf(UnsupportedEncryptionException(), IllegalStateException("분할"), FileNotFoundException("ENOENT"))) {
            assertEquals(ArchiveViewModel.State.Failed.Kind.UNREADABLE, failureKindOf(t, reachable = false), t.toString())
        }
        // 닿으면 예외의 종류 그대로다.
        assertEquals(ArchiveViewModel.State.Failed.Kind.ENCRYPTED, failureKindOf(UnsupportedEncryptionException(), reachable = true))
        assertEquals(ArchiveViewModel.State.Failed.Kind.UNSUPPORTED, failureKindOf(IllegalStateException("분할"), reachable = true))
    }

    private fun kindOfOpening(file: File): ArchiveViewModel.State.Failed.Kind {
        try {
            Archives.open(FileDocumentSource(file)).use { }
        } catch (t: Throwable) {
            return failureKindOf(t)
        }
        fail("${file.name} 이 열렸다 — 이 표본은 실패의 길을 타지 않는다")
    }

    private fun createTempDir(): File =
        File.createTempFile("archiveopenwith", "").also { it.delete(); it.mkdirs() }

    private fun doc() = ArchiveViewModel.Doc(
        path = "/storage/emulated/0/a.zip",
        name = "a.zip",
        fileBytes = 22,
        kind = ArchiveKind.ZIP,
        tree = ArchiveTree.build(emptyList()),
        entries = emptyList(),
        solid = false,
    )
}
