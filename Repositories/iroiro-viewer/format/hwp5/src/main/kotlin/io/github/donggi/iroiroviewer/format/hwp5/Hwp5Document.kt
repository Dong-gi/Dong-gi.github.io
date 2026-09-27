package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.format.html.HancomCss
import io.github.donggi.iroiroviewer.format.html.HancomFonts
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException

/**
 * 열린 HWP 5.0 하나.
 *
 * ## 여는 동안 한 번 훑는다
 *
 * `DocInfo`(글자 모양·문단 모양·번호·글꼴·그림 목록)를 읽고, 구역을 **차례로 하나씩 풀어 끝까지 훑어** 조각의 경계·목차·
 * 문서 안 링크의 대상을 얻는다([Hwp5Walker] 의 주석). 버린 것(그릴 수 없는 그림·도형·수식…)도 이때 센다 — 열자마자 배지가
 * 정확하고, 조각을 다시 그려도 두 번 세지 않는다. 구역 바이트는 훑은 뒤 버린다(꾸러미가 최근 것 하나만 든다).
 *
 * ## 조각은 그때그때 그린다
 *
 * [renderBody] 는 조각의 구역을 (꾸러미의 캐시에서, 없으면 다시 풀어) 조각의 첫 문단 자리에서 곧바로 걷기 시작한다. 시작
 * 상태(번호 문단·각주 번호·구역 설정)는 훑기가 찍어 둔 것에서 이어 간다. 각주·미주는 조각의 끝에 모은다.
 *
 * ## 깨진 본문
 *
 * 구역 하나가 중간에서 깨졌으면 **깨진 자리까지는 보여 준다** — 훑기는 거기서 멈춘 채 조각을 정하고, 그리기는 쓴 데까지
 * 닫아 돌려주며 [FlowWarnings.PART_FAILED] 를 남긴다. `DocInfo` 가 깨졌으면 본문은 기본 서식으로 그리고
 * [FlowWarnings.AUX_FAILED] 를 남긴다. 본문을 **하나도** 읽지 못했으면(암호 때문이 아니라) 한글이 함께 저장해 두는
 * 미리 보기 글(`PrvText`)을 부분 하나로 보인다 — 서식도 표도 없는 앞부분 몇 KB 지만 빈 화면보다 낫다.
 */
internal class Hwp5FlowDocument(
    pkg: Hwp5Package,
    limits: ParseLimits,
    private val options: Hwp5Options = Hwp5Options(),
) : FlowDocumentBase<Hwp5Package>(pkg, limits, FormatId.HWP5) {

    @Volatile
    private var layout: Hwp5Layout = Hwp5Layout.EMPTY

    @Volatile
    private var env: Hwp5Env? = null

    @Volatile
    private var sections: List<CfbEntry> = emptyList()

    @Volatile
    private var partList: List<FlowPart> = emptyList()

    /** 본문을 읽지 못해 대신 보이는 미리 보기 글. 있으면 부분은 이것 하나다. */
    @Volatile
    private var preview: String? = null

    /**
     * 제목은 **비워 둔다**(화면이 파일 이름으로 채운다). 요약 정보(`\u0005HwpSummaryInformation`)에 제목 칸이 있지만 실물에서
     * 문서와 맞지 않았다 — 2002년 양식의 제목이 2021년 보도자료에 그대로 남아 있는 식이다(표본 K17·K19·K06·O01). 틀린 제목을
     * 보이는 것보다 파일 이름이 낫다.
     */
    override val title: String get() = ""
    override val kind: FlowKind = FlowKind.DOCUMENT
    override val parts: List<FlowPart> get() = partList
    override val outline: List<FlowOutline> get() = layout.outline

    /**
     * 바탕 스타일은 HWPX 변환기와 **한 벌**이다([HancomCss.FLOW]). 처음에는 이 모듈이 따로 들고 있어 같은 보도자료가 포맷마다
     * 달리 보였다(13단계 짝 대조 — 칸의 기본 세로 정렬, 제목의 줄 간격, 캡션 크기 …).
     */
    override val css: String = HancomCss.FLOW

    /** 배포용 문서의 제한 표시(뜻을 확인하지 못해 따르지 않는다 — [Hwp5Distribution]). 시험이 본다. */
    internal val distributionFlags: Int? get() = locked { pkg.distributionFlags }

    /** 여는 동안 한 번. */
    fun prepare(checkCancel: () -> Unit, progress: ProgressSink) = locked {
        val info = readDocInfo(checkCancel)
        pkg.registerBinItems(info.binItems)
        val environment = Hwp5Env(
            info,
            unsupported,
            isDisplayableImage = { isDisplayableImage(it) },
            binName = { pkg.binName(it) },
            fitsResource = { name, cancel -> pkg.fitsResource(name, options.maxImageBytes, cancel) },
        )
        val entries = pkg.sectionEntries(info.sectionCount)
        if (info.sectionCount > entries.size) {
            // 적힌 구역의 스트림이 없다 — 있는 데까지 보인다.
            warn(FlowWarnings.PART_FAILED, "Section${entries.size}")
        }
        val collector = ScanCollector(options.chunk, checkCancel)
        val state = WalkState()
        for ((i, entry) in entries.withIndex()) {
            checkCancel()
            // 이 구역의 차례 0 번 문단이 문서 전체에서 몇 번째인가 — 본문의 `id`(`p-번호`)가 문서 안에서 하나뿐이게.
            val base = collector.topBlocks
            val ok = tolerant {
                val bytes = pkg.section(i, entry, checkCancel)
                Hwp5Walker(
                    environment,
                    sink = null,
                    state = state,
                    cfg = WalkConfig(scan = collector, recordFeatures = true, section = i, globalBase = base, checkCancel = checkCancel),
                ).section(bytes, 0)
            }
            // 첫 문단도 읽지 못한 구역에는 부분이 생기지 않는다 — 그리기가 알릴 자리가 없으니 여기서 알린다.
            if (!ok && collector.topBlocks == base) warn(FlowWarnings.PART_FAILED, "Section$i")
            progress.report((i + 1).toLong(), entries.size.toLong() + 1)
        }
        val lay = collector.build()
        env = environment
        sections = entries
        if (lay.chunks.isEmpty()) {
            val text = tolerantValue { pkg.previewText() }
            if (!text.isNullOrBlank()) {
                preview = text
                partList = listOf(FlowPart(partPath(0), ""))
                warn(FlowWarnings.PART_FAILED, partName(0))
            }
            return@locked
        }
        layout = lay
        partList = lay.chunks.mapIndexed { i, c -> FlowPart(partPath(i), c.label) }
    }

    /** `DocInfo` 를 읽는다. 깨졌거나 너무 크면 **없는 것으로 치고** 알린다(본문은 기본 서식으로 그린다). */
    private fun readDocInfo(checkCancel: () -> Unit): DocInfo {
        val info = tolerantValue { DocInfo.parse(pkg.docInfoBytes(checkCancel), checkCancel) }
        if (info == null) {
            warn(FlowWarnings.AUX_FAILED, "DocInfo")
            return DocInfo.EMPTY
        }
        if (info.brokenRecords > 0) warn(FlowWarnings.AUX_FAILED, "DocInfo")
        return info
    }

    override fun renderBody(index: Int): String {
        preview?.let { return renderPreview(it) }
        val environment = env ?: return ""
        val lay = layout
        val chunk = lay.chunks[index]
        val label = partName(index)
        val w = HtmlWriter(options.maxChars)
        // 바탕 글자가 명조면 부분 전체를 명조로 — 글자마다 적지 않으려는 것이다(`RunStyle.css`, HWPX 와 같다). 각주도 이 안에 든다.
        if (HancomFonts.isSerif(environment.defaultRun.font)) w.start("div", "class" to "serif")
        val cancel = { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("취소") }
        val bytes = pkg.section(chunk.section, sections[chunk.section], cancel)
        val walker = Hwp5Walker(
            environment,
            sink = w,
            state = chunk.state.copy(),
            cfg = WalkConfig(
                section = chunk.section,
                startOrdinal = chunk.startOrdinal,
                endOrdinal = chunk.endOrdinal,
                globalBase = chunk.globalStart - chunk.startOrdinal,
                chunkIndex = index,
                layout = lay,
                partHref = { partPath(it) },
                onTruncated = { warn(FlowWarnings.TRUNCATED, label) },
                checkCancel = cancel,
            ),
        )
        guarded(label) { walker.section(bytes, chunk.startOffset) }
        if (!w.full && walker.noteRefs.isNotEmpty()) {
            guarded(label) { renderNotes(environment, w, bytes, walker.noteRefs, lay, index, label) }
        }
        if (w.full) warn(FlowWarnings.TRUNCATED, label)
        w.closeAll()
        return w.toString()
    }

    /**
     * 조각이 가리킨 각주·미주를 끝에 모아 그린다(각주 먼저, 미주 다음 — 각자 본문에 나온 차례). 새 번호 지정(`nwno`)이 번호를
     * 되돌릴 수 있어 나온 차례가 번호 차례와 다를 수 있다(13단계 짝 대조).
     */
    private fun renderNotes(
        environment: Hwp5Env,
        w: HtmlWriter,
        bytes: ByteArray,
        refs: List<NoteRef>,
        lay: Hwp5Layout,
        index: Int,
        label: String,
    ) {
        w.start("section", "class" to "notes")
        w.void("hr")
        try {
            for (endnote in listOf(false, true)) {
                for (ref in refs) {
                    if (ref.endnote != endnote) continue
                    if (w.full) throw StopWalk
                    w.start("div", "id" to ref.id, "class" to "note")
                    Hwp5Walker(
                        environment,
                        sink = w,
                        state = WalkState(),
                        cfg = WalkConfig(
                            notes = true,
                            chunkIndex = index,
                            layout = lay,
                            partHref = { partPath(it) },
                            onTruncated = { warn(FlowWarnings.TRUNCATED, label) },
                        ),
                    ).noteBody(bytes, ref)
                    w.end("div")
                }
            }
        } catch (e: StopWalk) {
            // 쓰기 상한 — 부르는 쪽이 `w.full` 을 보고 알린다.
        }
        w.end("section")
    }

    /**
     * 미리 보기 글을 줄마다 문단으로. 서식은 없다. `div.preview` 로 싼다 — 바탕 스타일이 그 안의 문단에만 줄 사이를 조금
     * 띄운다(서식 없는 글이 한 덩이로 붙어 보이지 않게). HWPX 의 미리보기와 같은 모양이다(13단계 짝 대조). 상한에서 멈추면
     * 알린다 — 처음에는 조용히 잘랐다(HWPX 는 알렸다).
     */
    private fun renderPreview(text: String): String {
        val w = HtmlWriter(options.maxChars)
        w.start("div", "class" to "preview")
        for (line in text.split('\n')) {
            if (w.full) break
            val t = line.trimEnd('\r')
            w.start("p")
            if (t.isEmpty()) w.void("br") else w.text(t)
            w.end("p")
        }
        w.end("div")
        if (w.full) warn(FlowWarnings.TRUNCATED, partName(0))
        w.closeAll()
        return w.toString()
    }

    /** 훑기의 실패는 거기서 멈춘 것으로 친다(위 '깨진 본문'). 취소만 위로 올린다. 끝까지 갔으면 참. */
    private inline fun tolerant(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: Exception) {
        // 그리기가 같은 자리에서 멈추며 경고를 남긴다.
        false
    }

    private inline fun <T> tolerantValue(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** 그리기의 실패는 쓴 데까지 살리고 경고를 남긴다. 예외의 글은 어디에도 옮기지 않는다. */
    private inline fun guarded(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            warn(FlowWarnings.TRUNCATED, label)
        } catch (e: Exception) {
            warn(FlowWarnings.PART_FAILED, label)
        }
    }
}
