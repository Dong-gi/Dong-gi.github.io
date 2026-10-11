package io.github.donggi.iroiroviewer.io

import android.content.res.AssetManager
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * **빈 오피스 문서의 서식**(뷰어 교체). 폴더 화면의 + → '새 문서·새 시트·새 프레젠테이션·새 한글 문서' 가 만드는 빈 파일은
 * 이 앱에 든 서식(`assets/blank/blank.<종류>`)을 옮겨 적은 것이다. 그 전에는 이 앱의 오피스 쓰개(걷어 냈다 — 아래 '왜 서식인가')가 지었고,
 * 서식은 그 쓰개가 마지막으로 지은 것이다 — 만든 시각 자리에 표지([STAMP])를 적어 두었다.
 *
 * 옮겨 적으며 하는 일은 둘뿐이다.
 *
 * - **항목의 차례·이름·압축 방식·시각을 그대로 둔다.** HWPX 는 `mimetype` 이 맨 앞의 압축하지 않은(STORED) 항목이어야 한다(EPUB 과 같은
 *   규칙 — 한글이 그것으로 알아본다). STORED 항목은 머리에 크기·CRC 를 먼저 적어야 해서 다 읽은 뒤 쓴다.
 * - **만든 시각 자리의 표지를 지금으로 바꾼다** — OOXML 은 `docProps/core.xml`(만든·고친 시각), HWPX 는 `Contents/content.hpf`(같다).
 *   그 밖의 바이트는 손대지 않는다.
 *
 * ## 왜 서식인가
 *
 * 문서를 보고 고치는 일은 편집기(`feature:editor` — rhwp·ONLYOFFICE)가 한다. 빈 문서 하나를 지으려고 오피스·한글 쓰개를 앱에 남겨 둘
 * 까닭이 없다 — 빈 문서는 날마다 같은 바이트다(시각만 다르다). 서식은 편집기가 열고 저장하는지 기기에서 확인한 것이다.
 */
object BlankDocuments {

    /** 서식이 있는 종류(확장자). 폴더 화면의 + 메뉴가 이 차례로 보인다. */
    val KINDS: List<String> = listOf("docx", "xlsx", "pptx", "hwpx")

    /** 서식이 만든 시각 자리에 적어 둔 표지 — 서식을 지을 때 시각을 0 으로 주었다. */
    internal const val STAMP = "1970-01-01T00:00:00Z"

    /** 표지를 지금으로 바꾸는 항목 — 그 밖의 항목은 바이트 그대로 옮긴다. */
    private val STAMPED = setOf("docProps/core.xml", "Contents/content.hpf")

    /** 서식 항목 하나의 상한 — 우리 서식은 모두 수 KB 다. 넘으면 서식이 아니다(던진다). */
    private const val ENTRY_LIMIT = 1L * 1024 * 1024

    /** 서식 항목 수의 상한 — 같은 판단(우리 서식은 스물둘을 넘지 않는다). */
    private const val ENTRIES_LIMIT = 256

    fun assetPath(kind: String): String = "blank/blank.$kind"

    /** 이 빌드에 [kind] 의 서식이 있는가. */
    fun available(assets: AssetManager, kind: String): Boolean {
        if (kind !in KINDS) return false
        return try {
            assets.open(assetPath(kind)).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 서식 [template] 을 [out] 에 옮겨 적는다 — 만든 시각은 [now](밀리초, 초 아래는 버린다). [out] 은 **닫지 않는다**(비우기만 한다 —
     * 임시본을 내리고 놓는 것은 큐다). 돌려주는 것은 옮겨 적은 항목의 이름(차례대로) — [verify] 가 견준다.
     *
     * @throws IOException 서식을 읽지 못했거나 서식의 꼴이 아니다(항목이 없다·너무 크다), 또는 쓰지 못했다.
     */
    fun write(template: InputStream, out: OutputStream, now: Long): List<String> {
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(Math.floorDiv(now, 1000L)))
        val names = ArrayList<String>()
        val zip = ZipOutputStream(BufferedOutputStream(KeepOpenStream(out), BUFFER))
        try {
            ZipInputStream(template).use { input ->
                while (true) {
                    val source = input.nextEntry ?: break
                    if (names.size >= ENTRIES_LIMIT) throw IOException("서식의 항목이 너무 많다")
                    val name = source.name
                    var bytes = readEntry(input)
                    if (name in STAMPED) bytes = String(bytes, Charsets.UTF_8).replace(STAMP, stamp).toByteArray(Charsets.UTF_8)
                    val entry = ZipEntry(name)
                    entry.time = source.time
                    // 크기·CRC 를 머리에 먼저 적는다 — 그래야 데이터 기술자(일반 목적 비트 3)가 붙지 않는다. 서식도 그렇게 지어졌고, OCF 를
                    // 따르는 읽개(HWPX)가 그것을 마다할 수 있다. 압축한 크기는 쓰개와 **같은 차례로** 한 번 더 압축해 잰다(아래).
                    entry.method = if (source.method == ZipEntry.STORED) ZipEntry.STORED else ZipEntry.DEFLATED
                    entry.size = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                    entry.compressedSize = if (entry.method == ZipEntry.STORED) bytes.size.toLong() else deflatedLength(bytes)
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                    names += name
                }
            }
            if (names.isEmpty()) throw IOException("서식에 항목이 없다")
        } finally {
            // 닫아야 압축기의 네이티브 메모리가 풀린다 — 아래 흐름은 KeepOpenStream 이 지킨다.
            zip.close()
        }
        return names
    }

    /**
     * 방금 쓴 [file] 이 온전한가 — 모든 항목을 끝까지 읽어(그 길에서 CRC 를 확인한다) 이름이 [names] 와 차례까지 같은가. 판단만 — 던지지
     * 않는다.
     */
    fun verify(file: File, names: List<String>): Boolean = try {
        val seen = ArrayList<String>()
        ZipInputStream(FileInputStream(file)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                readEntry(input)
                seen += entry.name
            }
        }
        seen == names
    } catch (e: IOException) {
        false
    }

    /**
     * [bytes] 를 `ZipOutputStream` 이 압축할 때와 **같은 차례로** 압축한 길이 — 같은 수준(기본)·같은 날것 꼴(nowrap)·같은 덩이(512)로
     * `DeflaterOutputStream` 에 한 번에 쓰고 끝낸다(그 쓰개가 항목 하나를 그렇게 쓴다). zlib 은 같은 입력·같은 차례에 같은 바이트를 내므로
     * 쓰개가 닫을 때 견주는 압축 크기와 맞는다 — 어긋나면 쓰개가 `ZipException` 을 던지고 새 문서는 실패로 끝난다(잘못된 파일이 남지 않는다).
     */
    private fun deflatedLength(bytes: ByteArray): Long {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        try {
            val sink = ByteArrayOutputStream()
            val out = DeflaterOutputStream(sink, deflater, ZIP_DEFLATE_BUFFER)
            out.write(bytes)
            out.finish()
            return sink.size().toLong()
        } finally {
            deflater.end()
        }
    }

    /** `ZipOutputStream` 이 물려받은 `DeflaterOutputStream` 의 덩이 크기(그 생성자의 기본값). */
    private const val ZIP_DEFLATE_BUFFER = 512

    private fun readEntry(input: ZipInputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > ENTRY_LIMIT) throw IOException("서식의 항목이 너무 크다")
        }
        return buffer.toByteArray()
    }

    private const val BUFFER = 64 * 1024
}
