package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 시험 표본을 **코드로 만든다.**
 *
 * ## 왜 커밋하지 않는가
 *
 * 상위 저장소가 GitHub Pages 로 통째로 서비스되므로 커밋하는 것은 공개하는 것이다.
 * 게다가 아카이브 표본은 '악의적인 이름' 과 '압축폭탄' 을 일부러 담고 있어서, 그것을
 * 웹에 올려 두는 것은 그 자체로 좋은 생각이 아니다. 그래서 시험이 돌 때마다 만든다.
 *
 * ## 이 기계에는 7-Zip 도 WinRAR 도 없다
 *
 * 그래서 7z 표본은 commons-compress 로 만든다. **그것이 만드는 7z 는 solid 가 아니다** —
 * 엔트리마다 폴더를 따로 쓴다. 진짜 solid 아카이브의 해제 비용은 이 표본으로 재지 못한다.
 * 다만 [SevenZArchiveReader.open] 이 엔트리를 **순차로 찾아가는 비용**(nextEntry 를
 * index 번 부른다)은 solid 여부와 무관하므로 그쪽은 이 표본으로 정확히 잰다.
 *
 * RAR 은 아예 만들지 못한다(junrar 에 쓰기 구현이 없다). 실측표에 '미검증' 으로 남는다.
 */
internal object ArchiveSamples {

    /**
     * 엔트리 [count] 개짜리 7z. 각 엔트리는 [bytesEach] 바이트이고 내용이 서로 다르다.
     *
     * 내용을 다르게 하는 것은 **엔트리가 뒤섞였는지 알아보기 위해서**다. 전부 같은
     * 바이트로 채우면 리더가 엉뚱한 엔트리를 돌려줘도 시험이 통과한다.
     */
    fun sevenZ(file: File, count: Int, bytesEach: Int): File {
        file.parentFile?.mkdirs()
        SevenZOutputFile(file).use { out ->
            out.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2)))
            for (i in 0 until count) {
                val entry = SevenZArchiveEntry()
                entry.name = "e$i/데이터$i.bin"
                out.putArchiveEntry(entry)
                out.write(payload(i, bytesEach))
                out.closeArchiveEntry()
            }
        }
        return file
    }

    /** 엔트리 [count] 개짜리 ZIP. 7z 와 같은 내용이라 둘을 나란히 견줄 수 있다. */
    fun zip(file: File, count: Int, bytesEach: Int): File {
        file.parentFile?.mkdirs()
        ZipArchiveOutputStream(file).use { out ->
            out.setEncoding(StandardCharsets.UTF_8.name())
            for (i in 0 until count) {
                val entry = ZipArchiveEntry("e$i/데이터$i.bin")
                out.putArchiveEntry(entry)
                out.write(payload(i, bytesEach))
                out.closeArchiveEntry()
            }
        }
        return file
    }

    /**
     * 디렉터리 엔트리가 **없는** ZIP.
     *
     * 많은 압축 도구가 디렉터리 엔트리를 쓰지 않는다. 그래서 `a/b/c.txt` 를 풀려면
     * 푸는 쪽이 중간 폴더를 스스로 만들어야 한다 — 그것을 잊으면 정상 아카이브가
     * 통째로 실패한다.
     */
    fun zipWithoutDirEntries(file: File, paths: List<String>): File {
        file.parentFile?.mkdirs()
        ZipArchiveOutputStream(file).use { out ->
            out.setEncoding(StandardCharsets.UTF_8.name())
            for (p in paths) {
                out.putArchiveEntry(ZipArchiveEntry(p))
                out.write("$p 의 내용\n".toByteArray())
                out.closeArchiveEntry()
            }
        }
        return file
    }

    /** 이름을 그대로 박아 넣은 ZIP. 경로 탈출·같은 이름·끝점 같은 표본을 만든다. */
    fun zipWithNames(file: File, names: List<String>): File {
        file.parentFile?.mkdirs()
        ZipArchiveOutputStream(file).use { out ->
            out.setEncoding(StandardCharsets.UTF_8.name())
            for ((i, n) in names.withIndex()) {
                out.putArchiveEntry(ZipArchiveEntry(n))
                out.write("엔트리 $i\n".toByteArray())
                out.closeArchiveEntry()
            }
        }
        return file
    }

    /**
     * 이미 만든 ZIP 에서 [entryName] 엔트리에 **암호 플래그만** 세운다.
     *
     * commons-compress 는 암호 걸린 엔트리를 **쓰지 못한다** — `ZipArchiveOutputStream.write`
     * 가 `UnsupportedZipFeatureException` 으로 거부한다. 그래서 정상으로 쓴 뒤 바이트를
     * 직접 고친다. 내용을 실제로 암호화하지는 않지만 상관없다: 이 앱은 복호화하지 않으므로
     * **플래그를 보고 거절하는지**만 확인하면 된다.
     *
     * 고칠 곳이 둘이다 — 로컬 헤더(`PK\3\4`)의 오프셋 6 과 중앙 디렉터리(`PK\1\2`)의
     * 오프셋 8. 한쪽만 고치면 라이브러리가 어느 쪽을 읽느냐에 따라 시험이 흔들린다.
     */
    fun markEncrypted(file: File, entryName: String): File {
        val bytes = file.readBytes()
        val name = entryName.toByteArray(StandardCharsets.UTF_8)

        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        fun nameAt(start: Int, len: Int) = bytes.copyOfRange(start, start + len).contentEquals(name)

        var i = 0
        while (i + 4 <= bytes.size) {
            if (bytes[i] == 'P'.code.toByte() && bytes[i + 1] == 'K'.code.toByte()) {
                val sig3 = bytes[i + 2].toInt()
                val sig4 = bytes[i + 3].toInt()
                if (sig3 == 3 && sig4 == 4 && i + 30 <= bytes.size) {
                    val n = u16(i + 26)
                    if (i + 30 + n <= bytes.size && nameAt(i + 30, n)) bytes[i + 6] = (bytes[i + 6].toInt() or 1).toByte()
                } else if (sig3 == 1 && sig4 == 2 && i + 46 <= bytes.size) {
                    val n = u16(i + 28)
                    if (i + 46 + n <= bytes.size && nameAt(i + 46, n)) bytes[i + 8] = (bytes[i + 8].toInt() or 1).toByte()
                }
            }
            i++
        }
        file.writeBytes(bytes)
        return file
    }

    /**
     * 엔트리 [i] 의 내용. 앞 16바이트에 번호를 적어 **어느 엔트리인지 내용만 보고**
     * 알 수 있게 한다.
     */
    fun payload(i: Int, bytes: Int): ByteArray {
        val head = "ENTRY-$i".padEnd(16, '.').toByteArray()
        val body = ByteArray(bytes.coerceAtLeast(head.size) - head.size) { ((i + it) % 251).toByte() }
        return head + body
    }
}
