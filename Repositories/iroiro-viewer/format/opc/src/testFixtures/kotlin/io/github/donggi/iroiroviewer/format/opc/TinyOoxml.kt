package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentSource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 시험용 OOXML 패키지를 **메모리에서** 짠다. docx·xlsx·pptx 의 시험이 함께 쓴다.
 *
 * 실세계 문서를 커밋하지 않으므로(CLAUDE.md '표본') 시험이 보는 구조는 대부분 여기서 만든다.
 * 깨진 관계·빠진 콘텐츠 형식·뿌리를 벗어나는 대상 같은 **나쁜 모양**도 쉽게 만들 수 있어야 한다 —
 * 그래서 이 짜개는 아무것도 검사하지 않는다.
 */
class TinyOoxml {

    private class Part(val name: String, val contentType: String?, val data: ByteArray)

    private data class Rel(val id: String, val type: String, val target: String, val external: Boolean)

    private val parts = ArrayList<Part>()
    private val rels = LinkedHashMap<String?, MutableList<Rel>>()
    private var writeContentTypes = true

    /** XML 부분 하나. [contentType] 이 null 이면 `Override` 를 적지 않는다. */
    fun xml(name: String, contentType: String?, xml: String): TinyOoxml = bytes(name, contentType, xml.toByteArray())

    /** 이진 부분 하나(그림 등). */
    fun bytes(name: String, contentType: String?, data: ByteArray): TinyOoxml {
        parts.add(Part(name.removePrefix("/"), contentType, data))
        return this
    }

    /** 관계 하나. [source] 가 null 이면 패키지 관계. [target] 은 적힌 문자열 그대로 들어간다. */
    fun rel(source: String?, id: String, type: String, target: String, external: Boolean = false): TinyOoxml {
        rels.getOrPut(source?.removePrefix("/")) { ArrayList() }.add(Rel(id, type, target, external))
        return this
    }

    /** `[Content_Types].xml` 을 빼고 짠다(없는 패키지를 시험할 때). */
    fun withoutContentTypes(): TinyOoxml {
        writeContentTypes = false
        return this
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (writeContentTypes) {
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
                sb.append("""<Default Extension="png" ContentType="image/png"/>""")
                sb.append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
                sb.append("""<Default Extension="emf" ContentType="image/x-emf"/>""")
                for (p in parts) {
                    if (p.contentType != null) sb.append("""<Override PartName="/${p.name}" ContentType="${p.contentType}"/>""")
                }
                sb.append("</Types>")
                put(zip, "[Content_Types].xml", sb.toString().toByteArray())
            }
            for ((source, list) in rels) {
                val relsName = if (source == null) "_rels/.rels" else {
                    val dir = source.substringBeforeLast('/', "")
                    val file = source.substringAfterLast('/')
                    (if (dir.isEmpty()) "" else "$dir/") + "_rels/$file.rels"
                }
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (r in list) {
                    sb.append("""<Relationship Id="${r.id}" Type="${r.type}" Target="${escape(r.target)}"""")
                    if (r.external) sb.append(""" TargetMode="External"""")
                    sb.append("/>")
                }
                sb.append("</Relationships>")
                put(zip, relsName, sb.toString().toByteArray())
            }
            for (p in parts) put(zip, p.name, p.data)
        }
        return out.toByteArray()
    }

    /** 짠 것을 원본으로. */
    fun source(displayName: String = "tiny.docx"): DocumentSource = ByteArrayDocumentSource(build(), displayName)

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    companion object {
        const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val REL_OFFICE_DOCUMENT = "$R/officeDocument"
        const val REL_STYLES = "$R/styles"
        const val REL_NUMBERING = "$R/numbering"
        const val REL_IMAGE = "$R/image"
        const val REL_HYPERLINK = "$R/hyperlink"
        const val REL_FOOTNOTES = "$R/footnotes"
        const val REL_ENDNOTES = "$R/endnotes"
        const val REL_SHARED_STRINGS = "$R/sharedStrings"
        const val REL_WORKSHEET = "$R/worksheet"
        const val REL_SLIDE = "$R/slide"
        const val REL_SLIDE_LAYOUT = "$R/slideLayout"
        const val REL_SLIDE_MASTER = "$R/slideMaster"
        const val REL_THEME = "$R/theme"
        const val REL_CHART = "$R/chart"

        /** 엄격(Strict) 이름공간의 관계 유형 앞머리. */
        const val R_STRICT = "http://purl.oclc.org/ooxml/officeDocument/relationships"

        const val CT_DOCX_MAIN = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
        const val CT_XLSX_MAIN = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
        const val CT_PPTX_MAIN = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"

        /** 과도기(Transitional) 이름공간들. XML 조각을 짤 때 쓴다. */
        const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val NS_S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val NS_R = R
    }
}
