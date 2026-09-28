package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.format.opc.OpcNames
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.OpcRelationship
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** SmartArt 의 글 한 문단. [level] 은 DrawingML 의 목록 수준(`a:pPr@lvl`, 0부터) — 들여쓰기로만 쓴다. */
internal class SmartArtPara(val text: String, val level: Int)

/**
 * SmartArt 하나에서 읽은 글. [shapes] 는 **글이 있는 도형**마다의 문단(그림 부분에 적힌 차례), [textless] 는 글이 없는 도형
 * (화살표·바탕 도형)의 수다 — 그리지 않으므로 버린 도형으로 센다. [truncated] 면 글이 상한에서 잘렸다.
 */
internal class SmartArtText(val shapes: List<List<SmartArtPara>>, val textless: Int, val truncated: Boolean) {
    val chars: Int get() = shapes.sumOf { s -> s.sumOf { it.text.length } }

    /**
     * 들고 있는 동안의 대략의 바이트 — 훑기가 문서 전체의 합에 상한을 건다(`ScanCollector.smartArt`). **글자 수만 세면 안 된다**:
     * 한 글자짜리 문단도 객체가 셋(목록·문단·문자열)이라 글자보다 수십 배 무겁다. 글자 합의 상한만 두었더니 한 글자짜리 도형
     * 수천 개를 가진 그림 수백 개가 상한 안에서 수백 MB 를 쥐었다(검토가 잡았다).
     */
    fun approxBytes(): Long {
        var n = 64L
        for (s in shapes) {
            n += SHAPE_BYTES
            for (para in s) n += PARA_BYTES + 2L * para.text.length
        }
        return n
    }

    private companion object {
        /** 도형 하나의 문단 목록(`ArrayList` 와 그 배열). */
        const val SHAPE_BYTES = 80L

        /** 문단 하나(`SmartArtPara` 와 `String` 과 그 배열의 머리). */
        const val PARA_BYTES = 72L
    }
}

/**
 * docx 의 SmartArt 를 **글로** 옮긴다. 도형의 모양·배치는 그리지 않는다(흐름 렌더다).
 *
 * ## 어디서 읽는가 — 캐시된 그림
 *
 * 본문의 `dgm:relIds@r:dm` 은 **데이터 부분**(노드의 글과 연결)을 가리킨다. 데이터만으로 글을 늘어놓으려면 레이아웃 정의를
 * 풀어야 한다. 오피스는 배치를 끝낸 모양을 **그림 부분**(`dsp:drawing`, 관계 유형 `diagramDrawing`)에 함께 저장하고, 그 안의
 * 도형(`dsp:sp`)마다 글상자(`dsp:txBody`)가 있다 — pptx 변환기가 그리는 것과 같은 부분이다(`PptxDocument.findDiagramDrawing`).
 * 데이터 부분의 `dsp:dataModelExt@relId` 가 **본문 부분의 관계** 하나를 가리키고, 그것이 없으면 번호로 짝짓는다
 * (`data3.xml` ↔ `drawing3.xml` — 오피스가 붙이는 이름). 그림 부분이 없는 파일(LibreOffice 가 쓴 것 등)은 글을 알 길이
 * 없어 SmartArt 로 센다 — pptx 와 같은 판단이다.
 *
 * ## 읽는 차례
 *
 * 그림 부분의 도형 차례를 그대로 쓴다. 오피스는 데이터의 노드 차례로 도형을 적는다 — 실물 셋(DX11 의 순환형 a·b·c,
 * DX13·DX19 의 목록형)에서 그랬고, 순환형은 자리(위→아래)로 늘어놓으면 `a·c·b` 가 된다. 자리로 다시 늘어놓지 않는다.
 */
internal object DocxSmartArt {

    /** SmartArt 하나의 글 상한(글자). 넘으면 자르고 알린다. 사람이 만든 도식은 수백 자다. */
    const val MAX_CHARS = 20_000

    /**
     * SmartArt 하나에서 글로 옮기는 문단의 수. 넘으면 자르고 알린다 — 글자 상한만으로는 한 글자짜리 문단 2만 개가 든다
     * ([SmartArtText.approxBytes] 의 주석). 사람이 만든 도식은 노드가 수십 개다.
     */
    const val MAX_PARAGRAPHS = 1_000

    /** SmartArt 하나에서 들여다보는 도형의 수(글 없는 것 포함). 넘으면 거기서 읽기를 멈추고 알린다. */
    const val MAX_SHAPES = 2_000

    /** 문단 하나의 수준 상한 — 들여쓰기가 화면 밖으로 밀리지 않게. */
    const val MAX_LEVEL = 8

    /** 파서의 사건 이만큼마다 취소를 본다 — 그림·데이터 부분 하나가 32 MB 까지 들어온다. */
    private const val CANCEL_EVERY = 4_096

    private val DIGITS = Regex("\\d+")

    /**
     * [source] 부분의 SmartArt([data] 는 데이터 부분의 관계)의 그림 부분 이름. 찾지 못하면 null.
     * 자기 자신을 가리키는 그림은 받지 않는다(부분이 자기를 다시 읽는 고리를 끊는다).
     */
    fun drawingPart(
        pkg: OpcPackage,
        limits: ParseLimits,
        source: String,
        data: OpcRelationship,
        checkCancel: () -> Unit = {},
    ): String? {
        if (data.external) return null
        val dataName = pkg.canonical(data.target) ?: return null
        val drawings = pkg.relationshipsOfType(source, "diagramDrawing")
        if (drawings.isEmpty()) return null
        val byExt = dataModelExtRelId(pkg, limits, dataName, checkCancel)?.let { id -> drawings.firstOrNull { it.id == id } }
        val rel = byExt ?: run {
            val number = DIGITS.find(dataName.substringAfterLast('/'))?.value ?: return null
            drawings.firstOrNull { DIGITS.find(it.target.substringAfterLast('/'))?.value == number }
        } ?: return null
        val name = pkg.canonical(rel.target) ?: return null
        return name.takeIf { OpcNames.key(it) != OpcNames.key(source) }
    }

    /** 데이터 부분의 `dsp:dataModelExt@relId` — 그 id 는 데이터 부분이 아니라 **본문 부분**의 관계에 있다. */
    private fun dataModelExtRelId(pkg: OpcPackage, limits: ParseLimits, dataName: String, checkCancel: () -> Unit): String? {
        val (p, stream) = pkg.parser(dataName) ?: return null
        stream.use {
            var e = p.eventType
            var events = 0
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "dataModelExt") {
                    return OoxmlXml.attr(p, "relId")?.takeIf { it.isNotBlank() }
                }
                if (++events % CANCEL_EVERY == 0) checkCancel()
                e = p.nextGuarded(limits)
            }
        }
        return null
    }

    /**
     * 그림 부분([p] 는 막 입력을 물린 파서)의 도형마다 글을 모은다. 묶음(`dsp:grpSp`) 안의 도형도 차례대로 본다.
     * 도형이 하나도 없으면 null — 읽을 것이 없는 그림은 찾지 못한 것과 같이 센다.
     *
     * 글자([maxChars])·문단([MAX_PARAGRAPHS])·도형([MAX_SHAPES]) 가운데 하나라도 상한에 닿으면 자르고 [SmartArtText.truncated] 로
     * 알린다. 도형 상한에 닿으면 그 뒤는 읽지 않는다 — 글 없는 도형의 수도 거기까지만 센다.
     */
    fun read(p: XmlPullParser, limits: ParseLimits, maxChars: Int = MAX_CHARS, checkCancel: () -> Unit = {}): SmartArtText? {
        val shapes = ArrayList<List<SmartArtPara>>()
        var textless = 0
        var budget = maxChars
        var paragraphs = 0
        var seen = 0
        var truncated = false
        var e = p.eventType
        var events = 0
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "sp") {
                if (seen >= MAX_SHAPES) {
                    truncated = true
                    break
                }
                if (seen and 63 == 0) checkCancel()
                seen++
                val paras = ArrayList<SmartArtPara>()
                DocxProps.eachChild(p, limits) { name ->
                    if (name == "txBody") {
                        DocxProps.eachChild(p, limits) { child ->
                            if (child == "p") {
                                val (para, cut) = paragraph(p, limits, budget)
                                if (cut) truncated = true
                                if (para.text.isNotBlank()) {
                                    if (paragraphs < MAX_PARAGRAPHS) {
                                        budget -= para.text.length
                                        paragraphs++
                                        paras.add(para)
                                    } else {
                                        truncated = true
                                    }
                                }
                            } else {
                                OoxmlXml.skip(p, limits)
                            }
                        }
                    } else {
                        OoxmlXml.skip(p, limits)
                    }
                }
                if (paras.isEmpty()) textless++ else shapes.add(paras)
            }
            if (++events % CANCEL_EVERY == 0) checkCancel()
            e = p.nextGuarded(limits)
        }
        return if (seen > 0) SmartArtText(shapes, textless, truncated) else null
    }

    /**
     * `a:p` 하나 — 글 덩이(`a:r`)·필드(`a:fld`)의 `a:t` 와 줄바꿈(`a:br`). [budget] 이 다하면 자르고 (문단, 잘렸는가) 를 준다.
     * 끝나면 `a:p` 의 끝 태그에 서 있다.
     */
    private fun paragraph(p: XmlPullParser, limits: ParseLimits, budget: Int): Pair<SmartArtPara, Boolean> {
        var level = 0
        val sb = StringBuilder()
        var cut = false
        DocxProps.eachChild(p, limits) { name ->
            when (name) {
                "pPr" -> {
                    level = (OoxmlXml.int(p, "lvl") ?: 0).coerceIn(0, MAX_LEVEL)
                    OoxmlXml.skip(p, limits)
                }
                "r", "fld" -> DocxProps.eachChild(p, limits) { c ->
                    if (c == "t") {
                        val room = budget - sb.length
                        val t = OoxmlXml.collectText(p, limits, maxOf(0, room) + 1)
                        if (t.length > room) {
                            cut = true
                            if (room > 0) sb.append(t, 0, room)
                        } else {
                            sb.append(t)
                        }
                    } else {
                        OoxmlXml.skip(p, limits)
                    }
                }
                "br" -> {
                    if (sb.length < budget) sb.append('\n')
                    OoxmlXml.skip(p, limits)
                }
                else -> OoxmlXml.skip(p, limits)
            }
        }
        return SmartArtPara(sb.toString(), level) to cut
    }
}
