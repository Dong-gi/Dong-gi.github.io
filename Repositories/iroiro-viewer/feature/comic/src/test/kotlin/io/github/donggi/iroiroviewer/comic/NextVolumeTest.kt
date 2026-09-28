package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.NextVolume.Candidate
import io.github.donggi.iroiroviewer.io.TrashStore
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 다음 권 고르기(14단계). **파일 관리자의 이름 차례**(자연 정렬)에서 지금 책 바로 뒤의 권이다.
 */
class NextVolumeTest {

    private fun file(name: String) = Candidate(name, isDirectory = false)
    private fun dir(name: String) = Candidate(name, isDirectory = true)

    private fun pick(
        current: Candidate,
        siblings: List<Candidate>,
        showHidden: Boolean = false,
        comicFolders: Set<String> = emptySet(),
    ): String? = NextVolume.pick(current, siblings, showHidden, isComicFolder = { it.name in comicFolders })?.name

    /** 사전식 정렬이면 `10권` 이 `2권` 앞에 온다. 목록과 같은 자연 정렬이어야 한다. */
    @Test
    fun `자연 정렬로 바로 뒤의 권을 고른다`() {
        val siblings = listOf("Vol 10.cbz", "Vol 2.cbz", "Vol 9.cbz", "Vol 1.cbz").map(::file)
        assertEquals("Vol 10.cbz", pick(file("Vol 9.cbz"), siblings))
        assertEquals("Vol 2.cbz", pick(file("Vol 1.cbz"), siblings))
        assertNull(pick(file("Vol 10.cbz"), siblings), "마지막 권 뒤에는 없다")
    }

    @Test
    fun `한글 이름도 목록의 차례를 따른다`() {
        val siblings = listOf("원피스 3권.cb7", "원피스 1권.cb7", "원피스 2권.cb7").map(::file)
        assertEquals("원피스 2권.cb7", pick(file("원피스 1권.cb7"), siblings))
    }

    /** 권이 아닌 것(그림·글·영상)은 건너뛴다. */
    @Test
    fun `권이 아닌 파일은 건너뛴다`() {
        val siblings = listOf("a.cbz", "a.jpg", "a.txt", "b.mp4", "c.cbr").map(::file)
        assertEquals("c.cbr", pick(file("a.cbz"), siblings))
    }

    @Test
    fun `만화 확장자는 서로 섞여도 권이다`() {
        val siblings = listOf("01.cbz", "02.cb7", "03.cbr", "04.cbt").map(::file)
        assertEquals("02.cb7", pick(file("01.cbz"), siblings))
        assertEquals("03.cbr", pick(file("02.cb7"), siblings))
        assertEquals("04.cbt", pick(file("03.cbr"), siblings))
    }

    /**
     * **일반 압축은 지금 책과 확장자가 같을 때만 권이다.** 권마다 `.zip` 으로 묶은 시리즈는 흔하지만, 만화(`.cbz`)
     * 옆의 `.zip` 은 아무 압축 파일일 수 있다.
     */
    @Test
    fun `일반 압축은 지금 책과 확장자가 같을 때만 권이다`() {
        val zips = listOf("1.zip", "2.zip", "3.7z").map(::file)
        assertEquals("2.zip", pick(file("1.zip"), zips))
        assertNull(pick(file("2.zip"), zips), "확장자가 다른 7z 는 권이 아니다")
        val mixed = listOf("1.cbz", "2.zip", "3.cbz").map(::file)
        assertEquals("3.cbz", pick(file("1.cbz"), mixed), "만화 옆의 zip 은 권이 아니다")
        // 지금 책이 zip 이어도 뒤의 만화 확장자는 권이다.
        assertEquals("2.cbz", pick(file("1.zip"), listOf(file("1.zip"), file("2.cbz"))))
    }

    /** 폴더 권은 **쪽을 담은** 폴더만. 다른 폴더(부록·권을 모은 폴더)는 건너뛴다. */
    @Test
    fun `쪽을 담은 폴더만 권이다`() {
        val siblings = listOf(dir("1권"), dir("2권 부록"), dir("3권"))
        assertEquals("3권", pick(dir("1권"), siblings, comicFolders = setOf("1권", "3권")))
    }

    /** 폴더 권과 압축 권이 섞인 시리즈 — **폴더를 앞에 모으지 않는다.** */
    @Test
    fun `폴더와 압축이 섞여도 이름 차례를 따른다`() {
        val siblings = listOf(dir("1권"), file("2권.cbz"), dir("3권"))
        assertEquals("2권.cbz", pick(dir("1권"), siblings, comicFolders = setOf("1권", "3권")))
        assertEquals("3권", pick(file("2권.cbz"), siblings, comicFolders = setOf("1권", "3권")))
    }

    /**
     * 숨김 항목은 목록이 숨김을 보일 때만 후보다. 이름이 숫자로 시작하는 지금 책은 차례의 맨 앞이다 — 자연 정렬은
     * 숫자를 글자(`.` 포함)보다 앞에 둔다 — 그래서 `.2.cbz` 가 반드시 **뒤에** 온다.
     */
    @Test
    fun `숨김 항목은 목록이 숨김을 보일 때만 후보다`() {
        val siblings = listOf(file("1.cbz"), file(".2.cbz"))
        assertNull(pick(file("1.cbz"), siblings, showHidden = false))
        assertEquals(".2.cbz", pick(file("1.cbz"), siblings, showHidden = true))
    }

    /** 숨김을 보이는 목록에서도 우리 휴지통은 권이 아니다. 지금 책(`0`)이 차례의 맨 앞이라 휴지통은 반드시 뒤에 온다. */
    @Test
    fun `휴지통 폴더는 언제나 뺀다`() {
        val siblings = listOf(dir("a"), dir(TrashStore.DIR_NAME), dir("b"))
        assertNull(
            pick(dir("0"), siblings, showHidden = true, comicFolders = setOf(TrashStore.DIR_NAME)),
            "휴지통에 그림이 있어도 권이 아니다",
        )
        // 대조군 — 휴지통이 아닌 숨김 폴더는 권이 될 수 있다.
        assertEquals(".x", pick(dir("0"), siblings + dir(".x"), showHidden = true, comicFolders = setOf(TrashStore.DIR_NAME, ".x")))
    }

    /** 지금 책이 목록에 없어도(나열한 뒤 옮겨졌다) 이름 차례의 자리에서 뒤를 본다. */
    @Test
    fun `지금 책이 목록에 없어도 그 자리의 뒤를 고른다`() {
        val siblings = listOf("1.cbz", "3.cbz").map(::file)
        assertEquals("3.cbz", pick(file("2.cbz"), siblings))
    }

    /** 폴더를 들여다보는 횟수를 묶는다 — 권 아닌 폴더가 수천 개 늘어서 있어도 책을 여는 일이 길어지지 않는다. */
    @Test
    fun `폴더를 들여다보는 횟수에 상한이 있다`() {
        val many = (1..(NextVolume.MAX_FOLDER_PROBES + 10)).map { dir("x%04d".format(it)) }
        var probes = 0
        val result = NextVolume.pick(dir("x0000"), many + dir("zzz"), showHidden = false, isComicFolder = {
            probes++
            it.name == "zzz"
        })
        assertNull(result)
        assertEquals(NextVolume.MAX_FOLDER_PROBES, probes)
    }

    /** 이름으로 짐작한 파일이 실제로는 읽을 수 없으면 그 다음을 본다. */
    @Test
    fun `읽을 수 없는 권은 건너뛴다`() {
        val siblings = listOf("1.cbz", "2.cbz", "3.cbz").map(::file)
        val next = NextVolume.pick(file("1.cbz"), siblings, false, isComicFolder = { false }, isReadableFile = { it.name != "2.cbz" })
        assertEquals("3.cbz", next?.name)
    }

    // ---- 언제 권하는가 --------------------------------------------------------------------------

    /** 끝에 닿았고 다음 권을 찾았을 때 권한다 — **둘 중 무엇이 먼저 오든.** 짧은 책은 찾기 전에 끝에 닿는다. */
    @Test
    fun `끝에 닿고 다음 권을 찾으면 차례와 상관없이 권한다`() {
        val foundFirst = NextOfferGate()
        assertEquals(false, foundFirst.onFound(true), "끝에 닿기 전에는 권하지 않는다")
        assertEquals(true, foundFirst.onReachedEnd())

        val endFirst = NextOfferGate()
        assertEquals(false, endFirst.onReachedEnd(), "아직 찾는 중이면 권하지 않는다")
        assertEquals(true, endFirst.onFound(true), "찾은 뒤에 권한다")
    }

    /** 끝에서 앞뒤로 넘길 때마다 알림이 뜨면 방해다 — 책 한 권에 **한 번만**. */
    @Test
    fun `책 한 권에 한 번만 권한다`() {
        val g = NextOfferGate()
        g.onFound(true)
        assertEquals(true, g.onReachedEnd())
        assertEquals(false, g.onReachedEnd())
        assertEquals(false, g.onReachedEnd())
    }

    @Test
    fun `다음 권이 없으면 권하지 않는다`() {
        val g = NextOfferGate()
        g.onFound(false)
        assertEquals(false, g.onReachedEnd())
    }

    /** 책이 바뀌면 앞 책의 '끝에 닿았다' 와 '이미 권했다' 가 따라오지 않는다(액티비티에 묶인 VM 의 함정과 같은 자리). */
    @Test
    fun `책이 바뀌면 처음부터 다시 센다`() {
        val g = NextOfferGate()
        g.onFound(true)
        assertEquals(true, g.onReachedEnd())
        g.reset()
        assertEquals(false, g.onFound(true), "새 책은 아직 끝에 닿지 않았다")
        assertEquals(true, g.onReachedEnd(), "새 책의 끝에서 다시 권한다")
    }

    // ---- 파일시스템 ------------------------------------------------------------------------------

    private val tmp: File = File(System.getProperty("java.io.tmpdir"), "iroiro-next-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    @Test
    fun `폴더에서 실제로 고른다`() {
        File(tmp, "Vol 1.cbz").writeBytes(byteArrayOf(1))
        File(tmp, "Vol 2 표지.jpg").writeBytes(byteArrayOf(1))
        File(tmp, "Vol 10.cbz").writeBytes(byteArrayOf(1))
        File(tmp, "Vol 2").apply { mkdirs() }.let { File(it, "001.png").writeBytes(byteArrayOf(1)) }
        File(tmp, "Vol 3 부록").apply { mkdirs() }.let { File(it, "readme.txt").writeBytes(byteArrayOf(1)) }
        File(tmp, "Vol 4.cbz").mkdirs() // 권 이름을 가진 **폴더** — 파일이 아니므로 건너뛴다.
        File(tmp, "Vol 5.cbr").writeBytes(byteArrayOf(1))

        assertEquals("Vol 2", NextVolume.find(File(tmp, "Vol 1.cbz"), showHidden = false)?.name)
        assertEquals("Vol 5.cbr", NextVolume.find(File(tmp, "Vol 2"), showHidden = false)?.name)
        assertEquals("Vol 10.cbz", NextVolume.find(File(tmp, "Vol 5.cbr"), showHidden = false)?.name)
        assertNull(NextVolume.find(File(tmp, "Vol 10.cbz"), showHidden = false))
    }

    /**
     * 파일 후보는 책을 여는 문(`ComicOpen.open`)과 **같은 탐침**으로 가른다 — 실제로 열어 본다(`isReachable`). `canRead()` 가
     * 참이어도 열리지 않는 권(FUSE 의 권한 거부)을 권하면 누르자마자 '읽을 수 없다' 로 끝난다. JVM 에서는 있는데 열리지 않는
     * 파일을 만들 수 없어 탐침을 바꿔 끼운다.
     */
    @Test
    fun `열리지 않는 권은 권하지 않는다`() {
        listOf("Vol 1.cbz", "Vol 2.cbz", "Vol 3.cbz").forEach { File(tmp, it).writeBytes(byteArrayOf(1)) }
        val locked = File(tmp, "Vol 2.cbz")
        // 이 파일은 `canRead()` 로는 읽힌다 — 그 답으로 고르면 Vol 2 가 나온다.
        assertEquals(true, locked.canRead())
        val next = NextVolume.find(File(tmp, "Vol 1.cbz"), showHidden = false) { f ->
            f != locked && isReachable(f)
        }
        assertEquals("Vol 3.cbz", next?.name)
        // 기본 탐침은 실제로 연다 — 열리는 권은 그대로 고른다.
        assertEquals("Vol 2.cbz", NextVolume.find(File(tmp, "Vol 1.cbz"), showHidden = false)?.name)
    }
}
