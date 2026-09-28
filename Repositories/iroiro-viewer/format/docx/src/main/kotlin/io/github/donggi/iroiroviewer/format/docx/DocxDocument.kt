package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.format.opc.OpcFlowDocument
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * 글(docx) 을 흐름 문서로 여는 입구. `app/FormatRegistry` 가 `OoxmlOpener` 에 이어 준다.
 */
object DocxDocument {

    /**
     * 패키지를 흐름 문서로. **패키지의 주인이 돌려준 문서로 넘어간다** — 문서를 닫으면 패키지도
     * 닫힌다. 던지면 여는이가 패키지를 닫고 `toOpenFailure` 로 옮긴다.
     *
     * 본문 부분이 없으면 부분이 **없는** 문서를 돌려준다 — 여는이가 그것을 '보여 줄 부분이 없다' 로
     * 끝낸다(`FlowDocument.parts` 의 약속).
     */
    suspend fun open(pkg: OpcPackage, limits: ParseLimits, progress: ProgressSink): FlowDocument =
        open(pkg, limits, progress, DocxOptions())

    internal suspend fun open(
        pkg: OpcPackage,
        limits: ParseLimits,
        progress: ProgressSink,
        options: DocxOptions,
    ): FlowDocument {
        val context = currentCoroutineContext()
        val doc = DocxFlowDocument(pkg, limits, options)
        doc.prepare(context)
        context.ensureActive()
        progress.report(2, 3)
        return doc
    }
}

/**
 * 열린 docx 하나.
 *
 * ## 여는 동안 한 번 훑는다
 *
 * 스타일·번호·각주 설정·메모 목록을 읽고, 본문을 **한 번 끝까지 훑어** 조각의 경계·목차·책갈피·표의 행 합치기·
 * SmartArt 의 글을 얻는다([DocxWalker] 의 주석). 버린 것(그릴 수 없는 그림·차트·수식…)도 이때 센다 — 열자마자 배지가
 * 정확하고, 조각을 다시 그려도 두 번 세지 않는다.
 *
 * ## 각주와 메모는 조각의 끝에
 *
 * 조각이 가리킨 각주·미주를 먼저, 그다음 메모를 모아 그린다. 메모는 본문과 따로 보일 자리가 필요해 12단계는 세기만
 * 했다 — 이제 각주처럼 본문의 작은 표지와 조각 끝의 본문을 링크로 잇는다([DocxComments]).
 *
 * ## 조각은 그때그때 그린다
 *
 * [renderBody] 는 본문을 처음부터 다시 흘려 읽되 조각 앞의 블록은 들여다보지 않고 건너뛴다. 조각의
 * 시작 상태(목록 번호·각주 번호)는 훑기가 찍어 둔 것에서 이어 간다.
 *
 * ## 깨진 본문
 *
 * 본문 XML 이 중간에서 깨졌으면 **깨진 자리까지는 보여 준다.** 훑기는 거기서 멈춘 채 조각을 정하고,
 * 그리기는 거기까지 쓴 것을 닫아 돌려주며 [FlowWarnings.PART_FAILED] 를 남긴다. 조각을 통째로
 * '읽지 못함' 으로 바꾸면 앞의 멀쩡한 쪽까지 잃는다.
 */
internal class DocxFlowDocument(
    pkg: OpcPackage,
    limits: ParseLimits,
    private val options: DocxOptions,
) : OpcFlowDocument(pkg, limits, FormatId.DOCX) {

    @Volatile
    private var layout: DocxLayout = DocxLayout.EMPTY

    @Volatile
    private var env: DocxEnv? = null

    @Volatile
    private var partList: List<FlowPart> = emptyList()

    override val title: String get() = layout.title
    override val kind: FlowKind = FlowKind.DOCUMENT
    override val parts: List<FlowPart> get() = partList
    override val outline: List<FlowOutline> get() = layout.outline
    override val css: String = DOCX_CSS

    /** 여는 동안 한 번. 스타일·번호·설정·메모 목록을 읽고 본문과 각주(와 가리킨 메모)를 훑는다. */
    fun prepare(context: CoroutineContext) = locked {
        val main = pkg.mainDocument ?: pkg.canonical("word/document.xml") ?: return@locked
        val checkCancel = { context.ensureActive() }

        val styles = auxiliary(pkg.relationshipsOfType(main, "styles").firstOrNull()?.target) { p ->
            DocxStyles.parse(p, limits)
        } ?: DocxStyles.EMPTY
        val numbering = auxiliary(pkg.relationshipsOfType(main, "numbering").firstOrNull()?.target) { p ->
            DocxNumbering.parse(p, limits, styles)
        } ?: DocxNumbering.empty(styles)
        val notes = auxiliary(pkg.relationshipsOfType(main, "settings").firstOrNull()?.target) { p ->
            readNoteFormats(p)
        } ?: (NoteFormat("decimal", 1) to NoteFormat("lowerRoman", 1))
        // 메모 — 목록(번호·머리글자·지은이)만 읽는다. 본문은 그리는 조각이 가리킨 것만 그때 읽는다(각주와 같다).
        // 깨졌으면 알리고(보조 부분의 실패) 메모 표지는 보일 글이 없으므로 버린 것으로 센다.
        val commentsPart = pkg.relationshipsOfType(main, "comments").firstOrNull()?.target?.let { pkg.canonical(it) }
        val comments = commentsPart?.let { part -> auxiliary(part) { p -> DocxComments.read(p, limits, part) } } ?: DocxComments.NONE
        // 문서 속성은 제목 하나뿐이라 깨져도 알리지 않는다 — 화면이 파일 이름으로 채운다.
        val title = auxiliary(pkg.relationshipsOfType(null, "core-properties").firstOrNull()?.target, quiet = true) { p ->
            readTitle(p)
        }.orEmpty()
        checkCancel()

        val environment = DocxEnv(
            pkg = pkg,
            limits = limits,
            main = main,
            styles = styles,
            numbering = numbering,
            footnotesPart = pkg.relationshipsOfType(main, "footnotes").firstOrNull()?.target?.let { pkg.canonical(it) },
            endnotesPart = pkg.relationshipsOfType(main, "endnotes").firstOrNull()?.target?.let { pkg.canonical(it) },
            footnoteFormat = notes.first,
            endnoteFormat = notes.second,
            features = unsupported,
            isDisplayableImage = { isDisplayableImage(it) },
            comments = comments,
        )

        val initial = WalkState()
        val collector = ScanCollector(options.chunk, initial, checkCancel)
        val walker = DocxWalker(
            environment,
            sink = null,
            state = initial,
            cfg = WalkConfig(sourcePart = main, scan = collector, recordFeatures = true, checkCancel = checkCancel),
        )
        tolerant { pkg.parser(main)?.let { (p, stream) -> stream.use { walker.document(p) } } }
        for (part in listOfNotNull(environment.footnotesPart, environment.endnotesPart)) {
            tolerant { scanNotes(environment, part, checkCancel) }
        }
        // 메모의 본문도 버린 것을 센다 — 본문·각주가 가리킨 것만(가리키지 않은 메모는 워드도 보이지 않는다).
        comments.part?.let { part -> if (environment.referencedComments.isNotEmpty()) tolerant { scanComments(environment, part, checkCancel) } }

        val lay = collector.build(title)
        env = environment
        layout = lay
        partList = lay.chunks.mapIndexed { i, c -> FlowPart(partPath(i), c.label) }
    }

    override fun renderBody(index: Int): String {
        val environment = env ?: return ""
        val lay = layout
        val chunk = lay.chunks[index]
        val label = partName(index)
        val w = HtmlWriter(options.maxChars)
        val cancel = { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("취소") }
        // 본문과 각주가 가리킨 메모를 함께 모은다 — 조각의 끝에서 그것들만 그린다.
        val commentSink = CommentSink()
        val walker = DocxWalker(
            environment,
            sink = w,
            state = chunk.state.copy(),
            cfg = WalkConfig(
                sourcePart = environment.main,
                start = chunk.start,
                end = chunk.end,
                chunkIndex = index,
                bookmarkChunks = lay.bookmarks,
                tableSpans = lay.tableSpans,
                partHref = { partPath(it) },
                onTruncated = { warn(FlowWarnings.TRUNCATED, label) },
                checkCancel = cancel,
                smartArts = lay.smartArts,
                comments = commentSink,
            ),
        )
        guarded(label) { pkg.parser(environment.main)?.let { (p, stream) -> stream.use { walker.document(p) } } }
        if (!w.full && walker.noteRefs.isNotEmpty()) {
            guarded(label) { renderNotes(environment, w, walker.noteRefs, lay, index, commentSink) }
        }
        if (!w.full && commentSink.ordinals.isNotEmpty()) {
            guarded(label) { renderComments(environment, w, commentSink, lay, index) }
        }
        if (w.full) warn(FlowWarnings.TRUNCATED, label)
        w.closeAll()
        return w.toString()
    }

    /**
     * 조각의 끝에 그 조각이 가리킨 각주·미주를 모아 그린다. 각주 부분을 한 번 흘려 읽으며 가리킨 것만
     * 그린다(차례는 부분에 적힌 차례 — 워드가 번호 차례대로 적는다).
     */
    private fun renderNotes(environment: DocxEnv, w: HtmlWriter, refs: List<NoteRef>, lay: DocxLayout, index: Int, comments: CommentSink) {
        w.start("section", "class" to "notes")
        w.void("hr")
        try {
            for (endnote in listOf(false, true)) {
                val part = (if (endnote) environment.endnotesPart else environment.footnotesPart) ?: continue
                val wanted = HashMap<Int, NoteRef>()
                for (r in refs) if (r.endnote == endnote) wanted.putIfAbsent(r.id, r)
                if (wanted.isEmpty()) continue
                val (p, stream) = pkg.parser(part) ?: continue
                stream.use {
                    eachNote(p) { id, separator ->
                        val ref = wanted[id]
                        if (separator || ref == null) {
                            OoxmlXml.skip(p, limits)
                        } else {
                            // 상한에 닿은 뒤에 연 태그는 닫는 태그만 쓰인다(걷기의 `start` 주석).
                            if (w.full) throw StopWalk
                            val prefix = if (endnote) "en" else "fn"
                            w.start("div", "id" to "$prefix-$id", "class" to "note")
                            val walker = DocxWalker(
                                environment,
                                sink = w,
                                state = WalkState(),
                                cfg = WalkConfig(
                                    sourcePart = part,
                                    notes = true,
                                    chunkIndex = index,
                                    bookmarkChunks = lay.bookmarks,
                                    partHref = { partPath(it) },
                                    // 각주 안의 메모도 이 조각의 메모 쪽에 든다.
                                    comments = comments,
                                ),
                            )
                            walker.note(p, ref)
                            w.end("div")
                        }
                    }
                }
            }
        } catch (e: StopWalk) {
            // 쓰기 상한 — 부르는 쪽이 `w.full` 을 보고 알린다.
        }
        w.end("section")
    }

    /**
     * 조각의 끝(각주 뒤)에 그 조각이 가리킨 메모를 모아 그린다. 메모 부분을 한 번 흘려 읽으며 가리킨 것만 그린다 — 차례는
     * 부분에 적힌 차례(워드가 문서 차례로 적는다, [CommentInfo.ordinal]). 메모마다 머리(표지 + 지은이)를 두고, 머리의 표지를
     * 누르면 본문의 표지로 돌아간다. 메모 본문의 `w:annotationRef`(메모 쪽의 표지 자리)는 머리가 대신한다.
     */
    private fun renderComments(environment: DocxEnv, w: HtmlWriter, sink: CommentSink, lay: DocxLayout, index: Int) {
        val part = environment.comments.part ?: return
        w.start("section", "class" to "comments")
        w.void("hr")
        // 같은 `w:id` 가 둘 적힌 부분 — 목록은 먼저 나온 것을 쓴다([DocxComments.read]). 뒤의 것까지 그리면 같은 `id` 의 상자가
        // 둘이 되고 뒤의 본문이 앞의 번호를 단다.
        val drawn = HashSet<Int>()
        try {
            val (p, stream) = pkg.parser(part) ?: return
            stream.use {
                eachComment(p) { id ->
                    val info = environment.comments.find(id)
                    if (info == null || info.ordinal !in sink.ordinals || !drawn.add(info.ordinal)) {
                        OoxmlXml.skip(p, limits)
                    } else {
                        // 상한에 닿은 뒤에 연 태그는 닫는 태그만 쓰인다(걷기의 `start` 주석).
                        if (w.full) throw StopWalk
                        w.start("div", "id" to info.noteId, "class" to "cmt")
                        w.start("p", "class" to "cmh")
                        w.start("sup", "class" to "cmnum")
                        w.start("a", "href" to "#" + info.backId)
                        w.text(info.label)
                        w.end("sup")
                        if (info.author.isNotEmpty()) w.text(" " + info.author)
                        w.end("p")
                        val walker = DocxWalker(
                            environment,
                            sink = w,
                            state = WalkState(),
                            cfg = WalkConfig(
                                sourcePart = part,
                                notes = true,
                                inComment = true,
                                chunkIndex = index,
                                bookmarkChunks = lay.bookmarks,
                                partHref = { partPath(it) },
                            ),
                        )
                        walker.note(p, null)
                        w.end("div")
                    }
                }
            }
        } catch (e: StopWalk) {
            // 쓰기 상한 — 부르는 쪽이 `w.full` 을 보고 알린다.
        } finally {
            w.end("section")
        }
    }

    /** 메모 부분을 훑는다 — 가리킨 메모의 본문에서 버린 것을 세려고. */
    private fun scanComments(environment: DocxEnv, part: String, checkCancel: () -> Unit) {
        val (p, stream) = pkg.parser(part) ?: return
        stream.use {
            val walker = DocxWalker(
                environment,
                sink = null,
                state = WalkState(),
                cfg = WalkConfig(sourcePart = part, recordFeatures = true, notes = true, inComment = true, checkCancel = checkCancel),
            )
            var n = 0
            // 같은 `w:id` 의 둘째 항목은 그려지지 않으므로(목록이 첫째를 쓴다) 세지도 않는다.
            val walked = HashSet<Int>()
            eachComment(p) { id ->
                if (id in environment.referencedComments && walked.add(id)) walker.note(p, null) else OoxmlXml.skip(p, limits)
                if (++n and 63 == 0) checkCancel()
            }
        }
    }

    /** `w:comments` 의 각 `w:comment`. [onComment] 는 항목을 끝까지 소비해야 한다. */
    private inline fun eachComment(p: XmlPullParser, onComment: (id: Int) -> Unit) {
        var ev = p.eventType
        while (ev != XmlPullParser.START_TAG) {
            if (ev == XmlPullParser.END_DOCUMENT) return
            ev = p.nextGuarded(limits)
        }
        DocxProps.eachChild(p, limits) { name ->
            val id = OoxmlXml.int(p, "id")
            if (name == "comment" && id != null) onComment(id) else OoxmlXml.skip(p, limits)
        }
    }

    /** 각주 부분을 훑는다 — 버린 것을 세려고. 번호·조각과는 관계가 없다. */
    private fun scanNotes(environment: DocxEnv, part: String, checkCancel: () -> Unit) {
        val (p, stream) = pkg.parser(part) ?: return
        stream.use {
            val walker = DocxWalker(
                environment,
                sink = null,
                state = WalkState(),
                cfg = WalkConfig(sourcePart = part, recordFeatures = true, notes = true, checkCancel = checkCancel),
            )
            var n = 0
            eachNote(p) { _, separator ->
                if (separator) OoxmlXml.skip(p, limits) else walker.note(p, null)
                if (++n and 63 == 0) checkCancel()
            }
        }
    }

    /** `w:footnotes`·`w:endnotes` 의 각 항목. [onNote] 는 항목을 끝까지 소비해야 한다. */
    private inline fun eachNote(p: XmlPullParser, onNote: (id: Int, separator: Boolean) -> Unit) {
        var ev = p.eventType
        while (ev != XmlPullParser.START_TAG) {
            if (ev == XmlPullParser.END_DOCUMENT) return
            ev = p.nextGuarded(limits)
        }
        DocxProps.eachChild(p, limits) { name ->
            val id = OoxmlXml.int(p, "id")
            if ((name == "footnote" || name == "endnote") && id != null) {
                // 구분선(`separator`·`continuationSeparator`·`continuationNotice`)은 본문이 아니다.
                onNote(id, OoxmlXml.attr(p, "type").let { it != null && it != "normal" })
            } else {
                OoxmlXml.skip(p, limits)
            }
        }
    }

    /**
     * 보조 부분 하나(스타일·번호·설정·문서 속성)를 읽는다. **깨졌거나 너무 크면 없는 것으로 친다** —
     * 스타일 표 하나 때문에 본문을 못 여는 것보다 서식 없이라도 여는 편이 낫다. 취소만 위로 올린다.
     */
    private inline fun <T> auxiliary(name: String?, quiet: Boolean = false, read: (XmlPullParser) -> T): T? {
        if (name == null) return null
        return try {
            val (p, stream) = pkg.parser(name) ?: return null
            stream.use {
                var ev = p.eventType
                while (ev != XmlPullParser.START_TAG) {
                    if (ev == XmlPullParser.END_DOCUMENT) return null
                    ev = p.nextGuarded(limits)
                }
                read(p)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            // 본문은 기본 서식으로 그린다. 그 사실은 말한다 — 조용히 넘기면 문서가 원래 그렇게 생긴 줄 안다.
            if (!quiet) warn(FlowWarnings.AUX_FAILED, pkg.canonical(name) ?: name)
            null
        }
    }

    /** 훑기의 실패는 거기서 멈춘 것으로 친다(위 '깨진 본문'). 취소만 위로 올린다. */
    private inline fun tolerant(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            // 그리기가 같은 자리에서 멈추며 경고를 남긴다.
        }
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

    /** `w:settings` 의 각주·미주 번호 모양. 없으면 워드의 기본값(각주 1·2·3, 미주 i·ii·iii). */
    private fun readNoteFormats(p: XmlPullParser): Pair<NoteFormat, NoteFormat> {
        var foot = NoteFormat("decimal", 1)
        var end = NoteFormat("lowerRoman", 1)
        DocxProps.eachChild(p, limits) { name ->
            if (name == "footnotePr" || name == "endnotePr") {
                var fmt: String? = null
                var start: Int? = null
                DocxProps.eachChild(p, limits) { n ->
                    when (n) {
                        "numFmt" -> fmt = OoxmlXml.attr(p, "val")
                        "numStart" -> start = OoxmlXml.int(p, "val")
                    }
                    OoxmlXml.skip(p, limits)
                }
                val base = if (name == "footnotePr") foot else end
                val f = NoteFormat(fmt ?: base.numFmt, (start ?: base.start).coerceIn(0, 100_000))
                if (name == "footnotePr") foot = f else end = f
            } else {
                OoxmlXml.skip(p, limits)
            }
        }
        return foot to end
    }

    /** `docProps/core.xml` 의 `dc:title`. */
    private fun readTitle(p: XmlPullParser): String? {
        var title: String? = null
        DocxProps.eachChild(p, limits) { name ->
            if (name == "title" && p.namespace == DC_NS && title == null) {
                title = OoxmlXml.collectText(p, limits, MAX_TITLE_CHARS).trim().replace(SPACES, " ")
            } else {
                OoxmlXml.skip(p, limits)
            }
        }
        return title?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val DC_NS = "http://purl.org/dc/elements/1.1/"
        private const val MAX_TITLE_CHARS = 500
        private val SPACES = Regex("\\s+")

        /**
         * 글의 기본 모양. 문단은 `pre-wrap` 이다 — 워드는 공백 여럿과 탭을 글자로 다루는데 HTML 은
         * 합쳐 버린다. 표는 칸마다 가는 선을 두르고(워드 표의 테두리를 읽지 않는다), 목록 표지는
         * 내어쓰기 자리에 놓는다. 메모(`.cmt`)는 왼쪽 줄로 각주와 가르고, SmartArt 의 글(`.sa`)은 점선 상자로
         * 글상자(`.tb`)와 가른다 — 도형의 모양은 그리지 않았다는 표시다.
         */
        private const val DOCX_CSS = """
p,h1,h2,h3,h4,h5,h6{white-space:pre-wrap;tab-size:4;}
p{margin:0 0 .55em;line-height:1.5;}
h1,h2,h3,h4,h5,h6{margin:1em 0 .45em;line-height:1.3;}
p.h7{font-weight:bold;margin:.8em 0 .4em;}
.mk{display:inline-block;text-indent:0;padding-right:.4em;white-space:nowrap;}
table{border-collapse:collapse;margin:.6em 0;}
td{border:1px solid #9e9e9e;padding:2pt 5pt;vertical-align:top;}
td.gap{border:none;}
td>:last-child{margin-bottom:0;}
.tb{border:1px solid #bdbdbd;border-radius:3px;padding:4pt 8pt;margin:.5em 0;}
.ext{text-decoration:underline dotted;}
a{color:#1a56c4;}
.math{font-family:monospace;}
sup.fnref a,sup.fnnum a,sup.cmref a,sup.cmnum a{text-decoration:none;}
sup.cmref a,sup.cmnum a{color:#b26a00;}
.notes,.comments{font-size:.88em;margin-top:2em;}
.notes hr,.comments hr{border:none;border-top:1px solid #bdbdbd;width:30%;margin:0 0 .6em;}
.note{margin:.2em 0;}
.cmt{margin:.4em 0;padding-left:.5em;border-left:2px solid #d7b36a;}
.cmh{font-weight:bold;margin:0 0 .2em;}
.sa{border:1px dashed #bdbdbd;border-radius:3px;padding:4pt 8pt;margin:.5em 0;}
img{vertical-align:text-bottom;}
"""
    }
}
