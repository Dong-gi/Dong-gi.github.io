package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 파일명 인코딩 회귀 표본 — **이름 바이트를 그대로 박아 넣은** 작은 ZIP 을 만든다.
 *
 * ## 왜 바이트를 직접 넣는가
 *
 * 라이브러리에 인코딩을 주고 쓰면 라이브러리가 플래그·추가필드를 제 판단으로 붙인다. 시험 대상은
 * '다른 도구가 만든 아카이브' 이므로 이름 칸에 들어갈 **바이트**를 우리가 정한다. ISO-8859-1 은
 * 바이트를 1:1 로 문자에 대응시키므로, 그 인코딩으로 쓰면 정확히 이 바이트가 이름 칸에 들어간다.
 * UTF-8 표시(일반 목적 비트 11)는 라이브러리가 ISO-8859-1 에서 세우지 않으므로 쓴 뒤에 고친다.
 */
internal object NameSamples {

    /** 한(C7 D1) 글(B1 DB) 이(C0 CC) 름(B8 A7) + ".txt" — 다른 도구가 CP949 로 적은 모양 그대로. */
    val KOREAN_CP949: ByteArray = byteArrayOf(
        0xC7.toByte(), 0xD1.toByte(), 0xB1.toByte(), 0xDB.toByte(),
        0xC0.toByte(), 0xCC.toByte(), 0xB8.toByte(), 0xA7.toByte(),
    ) + ".txt".toByteArray(StandardCharsets.US_ASCII)

    /** 기기에 CP949 가 없으면 null — 그때 시험은 할 것이 없다. */
    val cp949: Charset? get() = EntryNameDecoder.cp949

    fun bytesOf(text: String, charset: Charset): ByteArray = text.toByteArray(charset)

    /**
     * 이름 바이트 [raw] 인 엔트리 하나짜리 ZIP. [utf8Flag] 면 비트 11 을 **로컬 헤더와 중앙 디렉터리
     * 양쪽에서** 세운다 — 한쪽만 고치면 라이브러리가 어느 쪽을 읽느냐에 따라 시험이 흔들린다.
     */
    fun zipWithRawName(file: File, raw: ByteArray, utf8Flag: Boolean): File {
        file.parentFile?.mkdirs()
        ZipArchiveOutputStream(file).use { out ->
            out.setEncoding(StandardCharsets.ISO_8859_1.name())
            out.setUseLanguageEncodingFlag(false)
            out.setFallbackToUTF8(false)
            out.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER)
            out.putArchiveEntry(ZipArchiveEntry(String(raw, StandardCharsets.ISO_8859_1)))
            out.write("내용".toByteArray())
            out.closeArchiveEntry()
        }
        if (utf8Flag) setGeneralPurposeBit(file, 0x0800)
        return file
    }

    /**
     * Info-ZIP 유니코드 경로 추가필드(0x7075)가 붙은 ZIP. 이름 칸에는 [legacy] 로 적은 바이트가,
     * 추가필드에는 UTF-8 이름과 **이름 칸 바이트의 CRC** 가 들어간다(라이브러리가 만든다).
     */
    fun zipWithUnicodeExtra(file: File, name: String, legacy: Charset): File {
        file.parentFile?.mkdirs()
        ZipArchiveOutputStream(file).use { out ->
            out.setEncoding(legacy.name())
            out.setUseLanguageEncodingFlag(false)
            out.setFallbackToUTF8(false)
            out.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS)
            out.putArchiveEntry(ZipArchiveEntry(name))
            out.write("내용".toByteArray())
            out.closeArchiveEntry()
        }
        return file
    }

    /** 모든 엔트리의 일반 목적 비트에 [mask] 를 세운다(로컬 헤더 오프셋 6, 중앙 디렉터리 오프셋 8). */
    private fun setGeneralPurposeBit(file: File, mask: Int) {
        val bytes = file.readBytes()
        var i = 0
        while (i + 4 <= bytes.size) {
            if (bytes[i] == 'P'.code.toByte() && bytes[i + 1] == 'K'.code.toByte()) {
                val at = when {
                    bytes[i + 2].toInt() == 3 && bytes[i + 3].toInt() == 4 -> i + 6
                    bytes[i + 2].toInt() == 1 && bytes[i + 3].toInt() == 2 -> i + 8
                    else -> -1
                }
                if (at >= 0 && at + 1 < bytes.size) {
                    bytes[at] = (bytes[at].toInt() or (mask and 0xFF)).toByte()
                    bytes[at + 1] = (bytes[at + 1].toInt() or (mask ushr 8)).toByte()
                }
            }
            i++
        }
        file.writeBytes(bytes)
    }
}
