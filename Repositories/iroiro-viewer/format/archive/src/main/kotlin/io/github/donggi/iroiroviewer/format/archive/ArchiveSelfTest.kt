package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.util.zip.CRC32

/**
 * 기기에서 압축 경로가 실제로 도는지 **스스로 확인**한다.
 *
 * JVM 단위 테스트가 이미 같은 것을 확인하지만, 그것으로는 잡히지 않는 것이 하나
 * 있다 — **R8 이 릴리스 APK 에서 디코더를 지웠는가.** LZMA2 해제기는 이름으로만
 * 불려서 축소기가 도달 불가능으로 판단하고 지워도 빌드가 성공한다. 그리고 사용자가
 * 7z 를 여는 순간에야 터진다. 그래서 릴리스 빌드에서 실제로 돌려 보는 경로가 필요하다.
 *
 * 표본을 기기에서 만드는 이유는 그것 말고 방법이 없기 때문이다 — 개발 PC 에
 * 7-Zip 도 WinRAR 도 없고, 표본을 저장소에 커밋하면 공개 웹에 올라간다.
 *
 * **결과는 값으로만 돌려준다.** 이 모듈은 순수 JVM 이고 화면을 모른다 — 문구를 여기서
 * 만들면 번역·용어 통일이 이 파일까지 와야 하고, 모듈 경계가 뜻을 잃는다.
 */
object ArchiveSelfTest {

    enum class Check {
        /** 플래그 없는 CP949 파일명을 살려 내는가. */
        CP949_ZIP,

        /** 플래그 없는 UTF-8 파일명을 살려 내는가. */
        UTF8_NO_FLAG_ZIP,

        /** LZMA2 디코더가 이 APK 에 살아 있는가. R8 관문. */
        LZMA2_SEVEN_Z,

        /** 경로 탈출 이름을 막는가. */
        PATH_TRAVERSAL,
    }

    /**
     * @param ok 판정. null 은 '이 환경에서는 확인할 수 없음'.
     * @param values 화면이 문구에 끼워 넣을 값. 순서가 의미를 가진다.
     * @param failureClass 예외가 났다면 그 클래스 이름. 문구가 아니라 식별자다.
     */
    data class Result(
        val check: Check,
        val ok: Boolean?,
        val values: List<String> = emptyList(),
        val failureClass: String? = null,
    )

    /**
     * 표본을 만들고 읽어 본 뒤 지운다.
     *
     * @param workDir 이 실행 전용 디렉터리. **호출자가 매번 다른 이름을 준다** —
     *   화면 회전 등으로 두 실행이 겹치면 먼저 끝난 쪽이 상대의 표본을 지워
     *   멀쩡한 디코더를 실패로 보고하게 된다.
     */
    fun run(workDir: File): List<Result> {
        workDir.mkdirs()
        return try {
            listOf(cp949Zip(workDir), utf8NoFlagZip(workDir), lzma2SevenZ(workDir), pathTraversal(workDir))
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * **고정된 CP949 바이트열**로 시험한다.
     *
     * 우리가 고른 charset 으로 써 놓고 같은 charset 으로 읽으면 왕복이 깨질 수가 없어
     * 아무것도 증명하지 못한다. 그래서 다른 도구가 만든 아카이브와 똑같이, 이름 바이트를
     * 코드에 박아 넣고 쓴다. ISO-8859-1 은 바이트를 1:1 로 문자에 대응시키므로
     * 그 문자열을 그 인코딩으로 쓰면 **정확히 이 바이트**가 엔트리 이름으로 들어간다.
     */
    private fun cp949Zip(dir: File): Result = guard(Check.CP949_ZIP) {
        val rawName = String(KOREAN_NAME_CP949, Charsets.ISO_8859_1)
        val f = writeZip(File(dir, "cp949.zip"), rawName, "ISO-8859-1", flag = false)
        val e = firstEntry(f)
        Result(Check.CP949_ZIP, e.name == KOREAN_NAME, listOf(e.name, e.nameCharset))
    }

    private fun utf8NoFlagZip(dir: File): Result = guard(Check.UTF8_NO_FLAG_ZIP) {
        val f = writeZip(File(dir, "utf8.zip"), KOREAN_NAME, "UTF-8", flag = false)
        val e = firstEntry(f)
        Result(Check.UTF8_NO_FLAG_ZIP, e.name == KOREAN_NAME, listOf(e.name, e.nameCharset))
    }

    /**
     * **이 항목이 2단계의 관문이다.** 실제로 압축이 걸린 7z 를 만들어 다시 푼다.
     * 저장(무압축) 모드로 새면 디코더가 지워져도 통과해 버리므로, 압축비를 함께
     * 확인해 시험이 무의미해지지 않게 한다.
     */
    private fun lzma2SevenZ(dir: File): Result = guard(Check.LZMA2_SEVEN_Z) {
        val body = "가나다라마바사".repeat(2000).toByteArray()
        val f = File(dir, "lzma2.7z")
        SevenZOutputFile(f).use { out ->
            out.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2)))
            val e = SevenZArchiveEntry()
            e.name = "압축된.txt"
            e.size = body.size.toLong()
            e.setHasStream(true)
            out.putArchiveEntry(e)
            out.write(body)
            out.closeArchiveEntry()
            out.finish()
        }
        val ratio = body.size.toLong() / maxOf(1L, f.length())
        val crc = CRC32()
        var read = 0L
        Archives.open(FileDocumentSource(f)).use { r ->
            r.open(r.entries.first()).use { s ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n <= 0) break
                    crc.update(buf, 0, n)
                    read += n
                }
            }
        }
        Result(
            Check.LZMA2_SEVEN_Z,
            read == body.size.toLong() && ratio >= 4,
            listOf(read.toString(), ratio.toString(), crc.value.toString(16)),
        )
    }

    private fun pathTraversal(dir: File): Result = guard(Check.PATH_TRAVERSAL) {
        val f = writeZip(File(dir, "evil.zip"), "../../../etc/passwd", "UTF-8", flag = true)
        val e = firstEntry(f)
        Result(Check.PATH_TRAVERSAL, e.safeName == null, listOf(e.name))
    }

    private fun firstEntry(f: File): ArchiveEntry =
        Archives.open(FileDocumentSource(f), budget = EntryBudget()).use { it.entries.first() }

    private fun writeZip(f: File, entryName: String, encoding: String, flag: Boolean): File {
        ZipArchiveOutputStream(f).use { out ->
            out.setEncoding(encoding)
            out.setUseLanguageEncodingFlag(flag)
            // 이름을 담지 못하는 인코딩이면 몰래 UTF-8 로 바꾸는 기능이 있는데, 그러면
            // '플래그 없는 CP949' 라는 시험 대상 자체가 사라진다.
            out.setFallbackToUTF8(false)
            out.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER)
            out.putArchiveEntry(ZipArchiveEntry(entryName))
            out.write(BODY)
            out.closeArchiveEntry()
        }
        return f
    }

    private inline fun guard(check: Check, block: () -> Result): Result = try {
        block()
    } catch (t: Throwable) {
        // 여기서 던지면 진단 화면 전체가 죽는다. 실패도 결과다.
        Result(check, false, failureClass = t::class.java.simpleName)
    }

    /** 한글 음절이 든 이름. 인코딩 판정의 시험 대상이다. */
    private const val KOREAN_NAME = "한글이름.txt"

    /**
     * 위 이름을 CP949 로 적은 바이트. 다른 도구가 만든 한국어 아카이브와 같은 모양이다.
     * 한(C7 D1) 글(B1 DB) 이(C0 CC) 름(B8 A7) + ".txt"
     */
    private val KOREAN_NAME_CP949 = byteArrayOf(
        0xC7.toByte(), 0xD1.toByte(), 0xB1.toByte(), 0xDB.toByte(),
        0xC0.toByte(), 0xCC.toByte(), 0xB8.toByte(), 0xA7.toByte(),
        '.'.code.toByte(), 't'.code.toByte(), 'x'.code.toByte(), 't'.code.toByte(),
    )

    private val BODY = ByteArray(16) { it.toByte() }
}
