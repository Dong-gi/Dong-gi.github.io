package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 시험용 HWPX 패키지를 **메모리에서** 짠다. 실세계 문서를 커밋하지 않으므로(CLAUDE.md '표본') 시험이 보는
 * 구조는 대부분 여기서 만든다. 나쁜 모양(빠진 매니페스트·깨진 XML·다른 포맷의 `mimetype`)도 쉽게 만들 수
 * 있어야 하므로 아무것도 검사하지 않는다.
 *
 * 한글이 쓰는 모양을 따른다: `mimetype` 이 **저장(무압축)된 첫 항목**, `content.hpf` 의 `href` 는 뿌리 기준.
 */
internal class TinyHwpx {

    private class Entry(val name: String, val data: ByteArray, val stored: Boolean)

    private val sections = ArrayList<String>()
    private val extra = ArrayList<Entry>()
    private val items = ArrayList<Triple<String, String, String>>()
    var header: String? = Hwpx.header()
    var mimetype: String? = "application/hwp+zip"
    var writeContentHpf = true
    var writeContainer = true
    var title = ""

    /** 암호: 잠글 항목 이름들과 암호. */
    private var lockPassword: CharArray? = null
    private var lockNames: Set<String> = emptySet()
    var tamperChecksum = false
    var iterations = 1024

    fun section(xml: String): TinyHwpx {
        sections.add(xml)
        return this
    }

    /** 이진 항목과 그 매니페스트 항목(`id` → `href`). */
    fun binary(id: String, href: String, mediaType: String, data: ByteArray): TinyHwpx {
        extra.add(Entry(href, data, stored = true))
        items.add(Triple(id, href, mediaType))
        return this
    }

    /** 매니페스트에 적지 않는 항목(미리보기·스크립트·설정). */
    fun file(name: String, data: ByteArray, stored: Boolean = false): TinyHwpx {
        extra.add(Entry(name, data, stored))
        return this
    }

    /** 매니페스트 항목만(패키지에 없는 것 — 바깥 그림). */
    fun item(id: String, href: String, mediaType: String): TinyHwpx {
        items.add(Triple(id, href, mediaType))
        return this
    }

    fun lock(password: CharArray, names: Set<String>): TinyHwpx {
        lockPassword = password
        lockNames = names
        return this
    }

    fun build(): ByteArray {
        val all = ArrayList<Entry>()
        header?.let { all.add(Entry("Contents/header.xml", it.toByteArray(), false)) }
        sections.forEachIndexed { i, s -> all.add(Entry("Contents/section$i.xml", s.toByteArray(), false)) }
        all.addAll(extra)
        val manifest = StringBuilder()
        val locked = ArrayList<Entry>()
        val pw = lockPassword
        if (pw != null) {
            manifest.append("""<?xml version="1.0" encoding="UTF-8"?><odf:manifest xmlns:odf="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">""")
            for (e in all) {
                if (e.name !in lockNames) {
                    locked.add(e)
                    continue
                }
                val enc = TestCrypto.encrypt(pw, e.data, iterations)
                val checksum = if (tamperChecksum) ByteArray(32) else enc.checksum
                manifest.append("""<odf:file-entry full-path="${e.name}" media-type="application/xml" size="${e.data.size}">""")
                manifest.append("""<odf:encryption-data checksum-type="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#sha256-1k" checksum="${b64(checksum)}">""")
                manifest.append("""<odf:algorithm algorithm-name="http://www.w3.org/2001/04/xmlenc#aes256-cbc" initialisation-vector="${b64(enc.iv)}"/>""")
                manifest.append("""<odf:key-derivation key-derivation-name="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#pbkdf2" key-size="32" iteration-count="$iterations" salt="${b64(enc.salt)}"/>""")
                manifest.append("""<odf:start-key-generation start-key-generation-name="http://www.w3.org/2000/09/xmldsig#sha256" key-size="32"/>""")
                manifest.append("</odf:encryption-data></odf:file-entry>")
                locked.add(Entry(e.name, enc.data, stored = true))
            }
            manifest.append("</odf:manifest>")
        } else {
            locked.addAll(all)
            manifest.append("""<?xml version="1.0" encoding="UTF-8"?><odf:manifest xmlns:odf="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"/>""")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            mimetype?.let { put(zip, "mimetype", it.toByteArray(), stored = true) }
            put(zip, "version.xml", VERSION.toByteArray(), stored = true)
            for (e in locked) put(zip, e.name, e.data, e.stored)
            if (writeContainer) put(zip, "META-INF/container.xml", CONTAINER.toByteArray(), stored = false)
            put(zip, "META-INF/manifest.xml", manifest.toString().toByteArray(), stored = false)
            if (writeContentHpf) put(zip, "Contents/content.hpf", contentHpf().toByteArray(), stored = false)
        }
        return out.toByteArray()
    }

    private fun contentHpf(): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><opf:package xmlns:opf="http://www.idpf.org/2007/opf/" version="" unique-identifier="" id="">""")
        sb.append("<opf:metadata><opf:title>").append(Hwpx.esc(title)).append("</opf:title><opf:language>ko</opf:language></opf:metadata>")
        sb.append("<opf:manifest>")
        if (header != null) sb.append("""<opf:item id="header" href="Contents/header.xml" media-type="application/xml"/>""")
        for (i in sections.indices) sb.append("""<opf:item id="section$i" href="Contents/section$i.xml" media-type="application/xml"/>""")
        for ((id, href, type) in items) sb.append("""<opf:item id="$id" href="${Hwpx.esc(href)}" media-type="$type" isEmbeded="1"/>""")
        sb.append("</opf:manifest><opf:spine>")
        if (header != null) sb.append("""<opf:itemref idref="header" linear="yes"/>""")
        for (i in sections.indices) sb.append("""<opf:itemref idref="section$i" linear="yes"/>""")
        sb.append("</opf:spine></opf:package>")
        return sb.toString()
    }

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray, stored: Boolean) {
        val e = ZipEntry(name)
        if (stored) {
            e.method = ZipEntry.STORED
            e.size = data.size.toLong()
            e.compressedSize = data.size.toLong()
            e.crc = CRC32().apply { update(data) }.value
        }
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    companion object {
        private const val VERSION =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><hv:HCFVersion xmlns:hv="http://www.hancom.co.kr/hwpml/2011/version" tagetApplication="WORDPROCESSOR" major="5" minor="1" micro="1" buildNumber="0" os="1" xmlVersion="1.5" application="Hancom Office Hangul" appVersion="13, 0, 0, 1408 WIN32LEWindows_10"/>"""
        private const val CONTAINER =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><ocf:container xmlns:ocf="urn:oasis:names:tc:opendocument:xmlns:container"><ocf:rootfiles><ocf:rootfile full-path="Contents/content.hpf" media-type="application/hwpml-package+xml"/><ocf:rootfile full-path="Preview/PrvText.txt" media-type="text/plain"/></ocf:rootfiles></ocf:container>"""
    }
}

/**
 * 시험 쪽 암호기. 한컴 모델의 차례(조사 노트, O16 으로 확인)를 **JCA 만으로 따로** 적었다: deflate(머리 없음) →
 * 0 으로 16 의 배수까지 채움 → AES-256-CBC(열쇠 = PBKDF2-HMAC-SHA1(SHA-256(암호 UTF-8), salt, 반복, 32)).
 * PBKDF2 만은 제품의 것을 쓴다 — JCA 의 것은 이진 암호를 받지 못한다(`Pbkdf2` 의 주석). 그것은 RFC 6070 의
 * 값으로 따로 시험한다.
 */
internal object TestCrypto {
    class Encrypted(val data: ByteArray, val iv: ByteArray, val salt: ByteArray, val checksum: ByteArray)

    fun encrypt(password: CharArray, plain: ByteArray, iterations: Int = 1024): Encrypted {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(16).also { rnd.nextBytes(it) }
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(plain)
        deflater.finish()
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)
        while (!deflater.finished()) buf.write(tmp, 0, deflater.deflate(tmp))
        deflater.end()
        var deflated = buf.toByteArray()
        deflated = deflated.copyOf((deflated.size + 15) / 16 * 16)
        val start = MessageDigest.getInstance("SHA-256").digest(String(password).toByteArray(Charsets.UTF_8))
        val key = Pbkdf2.derive("HmacSHA1", start, salt, iterations, 32) {}
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val data = c.doFinal(deflated)
        val checksum = MessageDigest.getInstance("SHA-256").digest(plain.copyOf(minOf(1024, plain.size)))
        return Encrypted(data, iv, salt, checksum)
    }
}

/** 본문 XML 조각 짜개. */
internal object Hwpx {
    const val NS = "xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\"" +
        " xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\"" +
        " xmlns:hc=\"http://www.hancom.co.kr/hwpml/2011/core\"" +
        " xmlns:hh=\"http://www.hancom.co.kr/hwpml/2011/head\""

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun sec(vararg paragraphs: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><hs:sec $NS>${paragraphs.joinToString("")}</hs:sec>"""

    /** 글 하나짜리 문단. */
    fun p(text: String, paraPr: Int = 0, charPr: Int = 0, style: Int = 0) =
        """<hp:p paraPrIDRef="$paraPr" styleIDRef="$style"><hp:run charPrIDRef="$charPr"><hp:t>${esc(text)}</hp:t></hp:run></hp:p>"""

    /** 날것의 안쪽을 가진 문단. */
    fun pRaw(inner: String, paraPr: Int = 0) = """<hp:p paraPrIDRef="$paraPr" styleIDRef="0">$inner</hp:p>"""

    fun run(inner: String, charPr: Int = 0) = """<hp:run charPrIDRef="$charPr">$inner</hp:run>"""

    fun t(text: String) = "<hp:t>${esc(text)}</hp:t>"

    /** 칸 하나. [paragraphs] 는 칸 안의 문단들. */
    fun tc(col: Int, row: Int, paragraphs: String, colSpan: Int = 1, rowSpan: Int = 1, fill: Int? = null, width: Int = 1000) =
        """<hp:tc name="" header="0"${fill?.let { " borderFillIDRef=\"$it\"" } ?: ""}><hp:subList>$paragraphs</hp:subList>""" +
            """<hp:cellAddr colAddr="$col" rowAddr="$row"/><hp:cellSpan colSpan="$colSpan" rowSpan="$rowSpan"/>""" +
            """<hp:cellSz width="$width" height="1000"/><hp:cellMargin left="0" right="0" top="0" bottom="0"/></hp:tc>"""

    fun tbl(rows: Int, cols: Int, vararg trs: String, caption: String = "") =
        """<hp:tbl rowCnt="$rows" colCnt="$cols" borderFillIDRef="1"><hp:sz width="10000" height="1000"/>""" +
            """<hp:pos treatAsChar="1"/><hp:outMargin left="0" right="0" top="0" bottom="0"/>$caption<hp:inMargin left="0" right="0" top="0" bottom="0"/>""" +
            trs.joinToString("") { "<hp:tr>$it</hp:tr>" } + "</hp:tbl>"

    /**
     * 머리. 글자 모양 0(바탕, 10pt)·1(굵게 빨강 20pt)·2(위 첨자)·3(취소선 `3D` — 켜지지 않아야 한다)·4(취소선),
     * 문단 모양 0(보통)·1(가운데, `hp:switch` 로 두 벌의 여백)·2(개요 1수준)·3(개요 2수준)·4(번호 문단 1수준, 번호
     * 정의 2)·5(글머리표 1)·6(옛 파일 모양: `switch` 없이 두 배 값), 번호 정의 1(개요: `^1.`·`^2.`)·2(`(^1)`·`^N`),
     * 테두리 1(없음)·2(배경 #eaf1dd).
     */
    fun header(extraNumbering: String = "") = """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?><hh:head $NS version="1.5" secCnt="1">""" +
        """<hh:beginNum page="1" footnote="1" endnote="1" pic="1" tbl="1" equation="1"/><hh:refList>""" +
        """<hh:fontfaces itemCnt="2"><hh:fontface lang="HANGUL" fontCnt="2"><hh:font id="0" face="함초롬바탕" type="TTF"/><hh:font id="1" face="휴먼명조" type="TTF"/></hh:fontface>""" +
        """<hh:fontface lang="LATIN" fontCnt="1"><hh:font id="0" face="Arial" type="TTF"/></hh:fontface></hh:fontfaces>""" +
        """<hh:borderFills itemCnt="2">""" +
        """<hh:borderFill id="1"><hh:leftBorder type="NONE"/><hh:rightBorder type="NONE"/><hh:topBorder type="NONE"/><hh:bottomBorder type="NONE"/></hh:borderFill>""" +
        """<hh:borderFill id="2"><hh:leftBorder type="SOLID"/><hh:rightBorder type="SOLID"/><hh:topBorder type="SOLID"/><hh:bottomBorder type="SOLID"/><hc:fillBrush><hc:winBrush faceColor="#EAF1DD" hatchColor="#000000" alpha="0"/></hc:fillBrush></hh:borderFill>""" +
        """</hh:borderFills><hh:charProperties itemCnt="5">""" +
        """<hh:charPr id="0" height="1000" textColor="#000000" shadeColor="none"><hh:fontRef hangul="0" latin="0"/><hh:underline type="NONE" shape="SOLID"/><hh:strikeout shape="NONE"/></hh:charPr>""" +
        """<hh:charPr id="1" height="2000" textColor="#FF0000" shadeColor="none"><hh:fontRef hangul="1" latin="0"/><hh:bold/><hh:underline type="BOTTOM" shape="SOLID"/><hh:strikeout shape="NONE"/></hh:charPr>""" +
        """<hh:charPr id="2" height="1000" textColor="#000000" shadeColor="none"><hh:fontRef hangul="0"/><hh:supscript/></hh:charPr>""" +
        """<hh:charPr id="3" height="1000" textColor="#000000" shadeColor="none"><hh:fontRef hangul="0"/><hh:strikeout shape="3D"/></hh:charPr>""" +
        """<hh:charPr id="4" height="1000" textColor="#000000" shadeColor="none"><hh:fontRef hangul="0"/><hh:strikeout shape="SOLID"/><hh:italic/></hh:charPr>""" +
        """</hh:charProperties><hh:paraProperties itemCnt="7">""" +
        """<hh:paraPr id="0"><hh:align horizontal="JUSTIFY"/><hh:heading type="NONE" idRef="0" level="0"/></hh:paraPr>""" +
        """<hh:paraPr id="1"><hh:align horizontal="CENTER"/><hh:heading type="NONE" idRef="0" level="0"/>""" +
        """<hp:switch><hp:case hp:required-namespace="http://www.hancom.co.kr/hwpml/2016/HwpUnitChar"><hh:margin><hc:intent value="-3790" unit="HWPUNIT"/><hc:left value="0" unit="HWPUNIT"/><hc:right value="0" unit="HWPUNIT"/><hc:prev value="1000" unit="HWPUNIT"/><hc:next value="0" unit="HWPUNIT"/></hh:margin><hh:lineSpacing type="PERCENT" value="130" unit="HWPUNIT"/></hp:case>""" +
        """<hp:default><hh:margin><hc:intent value="-7580" unit="HWPUNIT"/><hc:left value="0" unit="HWPUNIT"/><hc:right value="0" unit="HWPUNIT"/><hc:prev value="2000" unit="HWPUNIT"/><hc:next value="0" unit="HWPUNIT"/></hh:margin><hh:lineSpacing type="PERCENT" value="130" unit="HWPUNIT"/></hp:default></hp:switch></hh:paraPr>""" +
        """<hh:paraPr id="2"><hh:align horizontal="LEFT"/><hh:heading type="OUTLINE" idRef="0" level="0"/></hh:paraPr>""" +
        """<hh:paraPr id="3"><hh:align horizontal="LEFT"/><hh:heading type="OUTLINE" idRef="0" level="1"/></hh:paraPr>""" +
        """<hh:paraPr id="4"><hh:heading type="NUMBER" idRef="2" level="0"/></hh:paraPr>""" +
        """<hh:paraPr id="5"><hh:heading type="BULLET" idRef="1" level="0"/></hh:paraPr>""" +
        """<hh:paraPr id="6"><hh:heading type="NONE" idRef="0" level="0"/><hh:margin><hc:intent value="-4216"/><hc:left value="0"/><hc:right value="0"/><hc:prev value="1000"/><hc:next value="0"/></hh:margin></hh:paraPr>""" +
        """</hh:paraProperties><hh:numberings itemCnt="2">""" +
        """<hh:numbering id="1" start="0"><hh:paraHead start="1" level="1" numFormat="DIGIT">^1.</hh:paraHead><hh:paraHead start="1" level="2" numFormat="HANGUL_SYLLABLE">^2.</hh:paraHead></hh:numbering>""" +
        """<hh:numbering id="2" start="0"><hh:paraHead start="1" level="1" numFormat="CIRCLED_DIGIT">(^1)</hh:paraHead><hh:paraHead start="3" level="2" numFormat="ROMAN_SMALL">^N</hh:paraHead></hh:numbering>""" +
        extraNumbering +
        """</hh:numberings><hh:bullets itemCnt="1"><hh:bullet id="1" char="&#xF0A7;" useImage="0"><hh:paraHead level="0"/></hh:bullet></hh:bullets>""" +
        """<hh:styles itemCnt="2"><hh:style id="0" type="PARA" name="바탕글" paraPrIDRef="0" charPrIDRef="0"/><hh:style id="1" type="PARA" name="개요 1" paraPrIDRef="2" charPrIDRef="0"/></hh:styles>""" +
        """</hh:refList></hh:head>"""

    /** 첫 문단의 구역 정의(`hp:secPr`)를 가진 문단 — 한글이 쓰는 모양. */
    fun secPrParagraph(text: String, footnoteSuffix: String = ")") =
        """<hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0"><hp:secPr id="" outlineShapeIDRef="1">""" +
            """<hp:pagePr width="59528" height="84188"><hp:margin left="5669" right="5669" top="4251" bottom="4251"/></hp:pagePr>""" +
            """<hp:footNotePr><hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar="$footnoteSuffix" supscript="0"/></hp:footNotePr>""" +
            """</hp:secPr><hp:ctrl><hp:colPr id="" type="NEWSPAPER" colCount="1"/></hp:ctrl></hp:run>""" +
            """<hp:run charPrIDRef="0"><hp:t>${esc(text)}</hp:t></hp:run></hp:p>"""

    fun open(bytes: ByteArray, password: CharArray? = null, options: HwpxOptions = HwpxOptions()): OpenOutcome = runBlocking {
        val opener = HwpxOpener(password).also { it.options = options }
        var doc: OpenedDocument? = null
        val outcome = opener.open(ByteArrayDocumentSource(bytes, "sample.hwpx")) { doc = it }
        if (outcome !is OpenOutcome.Success) doc?.close()
        outcome
    }

    /** 열어서 흐름 문서를 준다. 실패하면 시험이 실패한다. */
    fun doc(bytes: ByteArray, password: CharArray? = null, options: HwpxOptions = HwpxOptions()): FlowDocument {
        val o = open(bytes, password, options)
        check(o is OpenOutcome.Success) { "열리지 않았다: $o" }
        return o.document as FlowDocument
    }

    fun doc(t: TinyHwpx, options: HwpxOptions = HwpxOptions()): FlowDocument = doc(t.build(), null, options)
}

/** 부분 하나의 `<body>` 안쪽. */
internal fun FlowDocument.body(index: Int = 0): String =
    partHtml(index)!!.substringAfter("<body>").substringBeforeLast("</body>")

private val BLOCK = Regex("<(p|h[1-6])\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
private val MARKER = Regex("<span class=\"mk\"[^>]*>.*?</span>", RegexOption.DOT_MATCHES_ALL)
private val MARKER_TEXT = Regex("<span class=\"mk\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
private val TAG = Regex("<[^>]+>")

/** HTML 의 글자만(태그를 떼고 엔티티를 푼다). */
internal fun plain(html: String): String =
    TAG.replace(html, "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

/** 문단·제목마다의 글(목록 표지는 뺀다). */
internal fun paragraphs(html: String): List<String> =
    BLOCK.findAll(html).map { plain(MARKER.replace(it.groupValues[2], "")) }.toList()

/** 목록 표지들(문서 차례). */
internal fun markers(html: String): List<String> = MARKER_TEXT.findAll(html).map { plain(it.groupValues[1]) }.toList()

internal fun String.occurrences(needle: String): Int {
    var n = 0
    var i = indexOf(needle)
    while (i >= 0) {
        n++
        i = indexOf(needle, i + needle.length)
    }
    return n
}
