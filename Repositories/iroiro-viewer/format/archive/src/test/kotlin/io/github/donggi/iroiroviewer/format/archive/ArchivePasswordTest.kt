package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 암호가 걸린 아카이브를 **리더 수준에서** 연다 — 목록·읽기·순차 풀기·암호 확인.
 *
 * 표본은 `ZipDecryptionTest` 와 같다(`archivecrypt/`, 반디집·pyzipper·py7zr 가 잠갔다).
 * 7z 는 둘이다 — 내용만 잠근 것(목록은 암호 없이 보인다)과 **헤더까지 잠근 것**(목록조차
 * 암호가 있어야 보인다).
 */
class ArchivePasswordTest {

    private val dir = File(javaClass.classLoader!!.getResource("archivecrypt/hello.txt")!!.toURI()).parentFile
    private fun plain(name: String) = File(dir, name).readBytes()
    private fun source(name: String) = FileDocumentSource(File(dir, name))

    private fun readAll(reader: ArchiveReader, name: String): ByteArray =
        reader.open(reader.entries.first { it.name == name }).use { it.readBytes() }

    private val zips = listOf("zipcrypto-bandizip.zip", "aes128-pyzipper.zip", "aes256-pyzipper.zip")

    @Test
    fun 암호_없이_열면_자물쇠만_보인다() {
        for (name in zips + "7z-aes-py7zr.7z") {
            Archives.open(source(name)).use { r ->
                assertTrue(r.entries.isNotEmpty(), name)
                assertTrue(r.entries.all { it.isEncrypted }, "$name: 자물쇠가 없다 ${r.entries}")
                assertTrue(r.entries.none { it.isReadable }, "$name: 암호 없이 읽힌다고 한다")
            }
        }
    }

    @Test
    fun 암호를_넣으면_평문이_나온다() {
        for (name in zips + "7z-aes-py7zr.7z" + "7z-aes-header-py7zr.7z") {
            Archives.open(source(name), password = "iroiro".toCharArray()).use { r ->
                assertTrue(r.entries.all { it.isReadable }, "$name: ${r.entries}")
                assertContentEquals(plain("hello.txt"), readAll(r, "hello.txt"), name)
                assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"), name)
                assertTrue(Archives.verifyPassword(r), name)
            }
        }
    }

    @Test
    fun 한글_암호가_풀린다() {
        for (name in listOf("zipcrypto-bandizip-ko.zip", "7z-aes-ko-py7zr.7z")) {
            Archives.open(source(name), password = "비밀번호".toCharArray()).use { r ->
                assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"), name)
            }
        }
    }

    @Test
    fun 틀린_암호는_확인에서_걸린다() {
        for (name in zips + "7z-aes-py7zr.7z") {
            Archives.open(source(name), password = "iroiro!".toCharArray()).use { r ->
                assertFalse(Archives.verifyPassword(r), name)
            }
        }
        // ZIP 은 항목 머리에서 곧바로 '틀렸다' 가 나온다.
        Archives.open(source("aes256-pyzipper.zip"), password = "x".toCharArray()).use { r ->
            val e = assertFailsWith<ArchivePasswordException> { readAll(r, "pattern.bin") }
            assertTrue(e.wrongPassword)
        }
    }

    @Test
    fun 헤더까지_잠긴_7z_는_여는_순간_암호를_묻는다() {
        val needed = assertFailsWith<ArchivePasswordException> { Archives.open(source("7z-aes-header-py7zr.7z")) }
        assertFalse(needed.wrongPassword)
        // 틀린 암호는 라이브러리가 '헤더가 없다' 로만 알린다 — 그것을 '틀렸다' 로 옮겨야 한다.
        val wrong = assertFailsWith<ArchivePasswordException> {
            Archives.open(source("7z-aes-header-py7zr.7z"), password = "wrong".toCharArray())
        }
        assertTrue(wrong.wrongPassword)
        // 메시지에 경로가 없다(라이브러리의 예외는 절대경로를 싣는다).
        assertFalse(dir.path in (needed.message ?: ""))
    }

    @Test
    fun 순차_풀기도_암호로_푼다() {
        for (name in listOf("aes256-pyzipper.zip", "7z-aes-py7zr.7z")) {
            val got = HashMap<String, ByteArray>()
            Archives.open(source(name), password = "iroiro".toCharArray()).use { r ->
                r.extractSequentially(object : EntrySink {
                    val bufs = HashMap<Int, ByteArrayOutputStream>()
                    override fun begin(entry: ArchiveEntry): OutputStream? =
                        if (entry.isReadable) ByteArrayOutputStream().also { bufs[entry.index] = it } else null

                    override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                        assertEquals(null, failure, "$name ${entry.name}")
                        got[entry.name] = bufs.getValue(entry.index).toByteArray()
                    }
                })
            }
            assertContentEquals(plain("hello.txt"), got["hello.txt"], name)
            assertContentEquals(plain("pattern.bin"), got["pattern.bin"], name)
        }
    }

    @Test
    fun 넘긴_배열을_지워도_리더는_읽는다() {
        // 리더는 자기 사본을 든다. 부르는 쪽(VM)이 열자마자 자기 배열을 지워도 된다.
        val pw = "iroiro".toCharArray()
        Archives.open(source("7z-aes-py7zr.7z"), password = pw).use { r ->
            pw.fill('\u0000')
            assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"))
        }
    }

    // ---- 구현 뒤 검토가 잡은 것들의 회귀 -------------------------------------------------
    //
    // 표본은 스크래치패드의 `mkreviewcrypt.py` 가 만들었다. ZipCrypto 둘은 우리가 잠갔으므로 **파이썬
    // 표준 `zipfile` 이 푸는 것**으로 확인했고, 7z 둘은 py7zr, AES+LZMA ZIP 은 pyzipper 가 만들었다.

    /**
     * **데이터 기술자 항목의 확인 바이트는 헤더에 적힌 DOS 시각에서 온다.** Info-ZIP `zip -e` 가
     * 만드는 모양(비트 3 + UT 추가 필드)이다. 예전 판은 `entry.time` 에서 되만들었는데, 라이브러리가
     * UT 의 UTC 시각을 주고 그것을 기기의 시간대로 되돌리면 시가 어긋나 **맞는 암호가 '틀렸다'** 가
     * 됐다. 표본의 UT 시각은 분이 0 이라 어느 시간대에서도 헤더의 시각(12:34)과 높은 바이트가 다르다.
     */
    @Test
    fun 데이터_기술자_항목은_헤더의_DOS_시각으로_확인한다() {
        Archives.open(source("zipcrypto-dd-ut.zip"), password = "iroiro".toCharArray()).use { r ->
            assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"))
            assertTrue(Archives.verifyPassword(r))
        }
    }

    /**
     * **후보를 항목마다 고르지 않는다.** 표본의 큰 항목은 UTF-8 후보가 확인 바이트를 우연히
     * 통과하도록 머리를 골랐다(파이썬 `zipfile` 도 UTF-8 로는 'Bad CRC-32' 로 걸린다). 항목마다 첫
     * 통과 후보를 쓰면 그 항목만 틀린 열쇠로 풀려 깨진다.
     */
    @Test
    fun 한글_암호의_UTF8_후보가_우연히_통과해도_CP949_로_푼다() {
        // 표본이 그 길을 정말 타는지부터 — 후보를 한꺼번에 넘겨 대 보면 깨진다.
        org.apache.commons.compress.archivers.zip.ZipFile.builder()
            .setFile(File(dir, "zipcrypto-ko-collide.zip")).get().use { zip ->
                val e = zip.getEntry("pattern.bin")!!
                assertFailsWith<java.io.IOException> {
                    zip.getRawInputStream(e).use { raw ->
                        ZipDecryption.open(e, raw, ZipDecryption.candidates("비밀번호".toCharArray())).use { it.readBytes() }
                    }
                }
            }
        // 큰 항목을 먼저 열어도 리더는 아카이브의 후보를 먼저 정한다.
        Archives.open(source("zipcrypto-ko-collide.zip"), password = "비밀번호".toCharArray()).use { r ->
            assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"))
            assertContentEquals(plain("hello.txt"), readAll(r, "hello.txt"))
            assertTrue(Archives.verifyPassword(r))
        }
    }

    /**
     * **LZMA(LZMA2 가 아니다) + AES 7z 를 암호 없이 열어도 목록이 선다.** LZMA 해제기는 만들면서
     * 바로 읽어 암호를 요구한다 — 예전 판은 그 예외가 생성자 밖으로 새어 '이 앱이 풀지 않는 방식' 이라는
     * 막다른 화면이 됐다. 틀린 암호도 예외가 아니라 '틀렸다' 여야 한다.
     */
    @Test
    fun LZMA_와_AES_로_잠근_7z_도_목록이_서고_암호를_묻는다() {
        Archives.open(source("7z-lzma-aes-py7zr.7z")).use { r ->
            assertEquals(2, r.entries.size)
            assertTrue(r.entries.all { it.isEncrypted && it.needsPassword }, "${r.entries}")
        }
        Archives.open(source("7z-lzma-aes-py7zr.7z"), password = "iroiro".toCharArray()).use { r ->
            assertContentEquals(plain("pattern.bin"), readAll(r, "pattern.bin"))
            assertTrue(Archives.verifyPassword(r))
        }
        Archives.open(source("7z-lzma-aes-py7zr.7z"), password = "iroiro!".toCharArray()).use { r ->
            assertFalse(Archives.verifyPassword(r))
        }
    }

    /**
     * **암호와 상관없는 7z 의 목록을 막지 않는다.** 사전이 64 MiB 인 폴더는 우리 메모리 상한에 걸려
     * 해제기를 만들 수 없다. 예전 판은 암호를 가리려고 모든 폴더의 해제기를 만들어, 이런 7z(7-Zip 의
     * 'Ultra')의 **목록까지** '깨졌다' 로 나갔다.
     */
    @Test
    fun 해제기를_만들_수_없는_7z_도_목록은_선다() {
        Archives.open(source("7z-lzma2-64m-py7zr.7z")).use { r ->
            assertEquals(listOf("hello.txt", "pattern.bin"), r.entries.map { it.name })
            assertTrue(r.entries.none { it.isEncrypted })
        }
    }

    /**
     * **암호로도 못 여는 항목에는 암호를 묻지 않는다.** AES 안쪽이 LZMA 인 ZIP(7-Zip 이 만든다)은 맞는
     * 암호를 넣어도 우리가 풀지 못한다. 묻게 두면 '틀렸다' 가 영원히 되풀이된다.
     */
    @Test
    fun 안쪽을_못_푸는_ZIP_항목은_암호를_묻지_않는다() {
        Archives.open(source("aes-lzma-pyzipper.zip")).use { r ->
            val e = r.entries.single()
            assertTrue(e.isEncrypted && e.lockedForGood)
            assertFalse(e.needsPassword)
        }
        Archives.open(source("aes-lzma-pyzipper.zip"), password = "iroiro".toCharArray()).use { r ->
            val e = r.entries.single()
            assertFalse(e.decryptable || e.isReadable)
            // 확인할 항목이 없다 — 물을 것이 없으므로 참이다(화면이 애초에 묻지 않는다).
            assertTrue(Archives.verifyPassword(r))
        }
    }
}
