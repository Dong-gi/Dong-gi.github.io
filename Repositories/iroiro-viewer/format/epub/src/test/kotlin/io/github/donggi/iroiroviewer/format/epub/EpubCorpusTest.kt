package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.Documents
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **실세계 EPUB 말뭉치**를 앱이 쓰는 길 그대로 연다 — `Documents.open(EpubOpener)` → `EpubBook` →
 * 모든 장의 `chapterHtml` 과 매니페스트의 모든 자원(`openResource`·`styleSheet`).
 *
 * ## 무엇과 견주는가
 *
 * 기대값은 우리가 아니다. `samples-local/corpus/tools/` 의 `oracle_epub.py` 가 **zipfile·lxml** 로 OPF·nav·NCX 를 따로 읽어
 * 장 수(`linear="no"` 를 뺀다)·목차 줄 수·장마다의 글·버려야 할 요소의 수를 적고, 난독화 글꼴은 파이썬이
 * 따로 푼 결과를 **원본 저장소가 공개한 평문 글꼴의 git blob SHA-1** 과 맞춰 둔다(`matches_published`).
 * 여기서는 그 값과 **정확히**(수) 또는 **낱말 주머니의 재현율·정밀도**(글)로 견준다.
 *
 * ## 무엇을 기대하는가 — 파일마다 적었다
 *
 * 표본 조사의 초안(`manifest.json` 의 `expected`)을 반증조의 교정을 읽고 다듬은 것이 [expected] 다.
 * 말뭉치에 새 EPUB 이 들어왔는데 표에 없으면 **시험이 실패한다** — 기대값 없이 '열리기만 하면 통과' 는
 * 아무것도 시험하지 않는다.
 *
 * ## 막는 것
 *
 * 파일 하나가 여는 데 30초(`ParseLimits.openTimeoutMs`), 장과 자원을 전부 읽는 데 120초를 넘으면 실패다.
 * EPUB 여는이는 막히는 호출이라 `withTimeout` 이 닿지 않는다 — 그래서 **다른 스레드**에서 돌리고 벽시계로
 * 끊는다. 힙의 최고치도 적는다.
 *
 * 말뭉치는 커밋하지 않는다(`samples-local/`, 없으면 건너뛴다).
 */
class EpubCorpusTest {

    private enum class Expect {
        /** 열리고 모든 장이 글을 낸다. */
        OPENS,

        /** 본문이 공개되지 않은 방식(DRM)으로 잠겼다. 암호를 묻지 않고 `Encrypted` 로 끝나야 한다. */
        REFUSE_DRM,
    }

    /** 반증조의 교정을 읽고 다듬은 기대값(보고서의 표와 같다). */
    private val expected = mapOf(
        "E03" to Expect.OPENS, // 세로쓰기·일본어 파일 이름·미디어 오버레이(소리는 버린다). 16 중 linear=no 1 → 15장
        "E04" to Expect.OPENS, // 세로쓰기, nav 만
        "E05" to Expect.OPENS, // IDPF 난독화 OTF 셋 — 푼 바이트가 공개된 평문 글꼴과 같아야 한다
        "E06" to Expect.OPENS, // W3C 난독화 양성 시험 — 푼 Lobster.ttf 가 공개된 평문과 같아야 한다
        "E07" to Expect.OPENS, // 고정 레이아웃(흐름으로 그린다)
        "E08" to Expect.OPENS, // 차례에 JPEG·PNG 13개, 모두 XHTML 대체본(fallback)이 있다
        "E09" to Expect.OPENS, // 차례에 SVG 6개
        "E10" to Expect.OPENS, // MathML 94장, 2 MB 장
        "E11" to Expect.OPENS, // 미디어 오버레이, linear=no 2 → 142장
        "E12" to Expect.OPENS, // 차례 2,014 — 시간 안에
        "E14" to Expect.OPENS, // nav 에 landmarks·page-list(숨김) — 목차 22 줄, 스크립트 1 을 센다
        "E16" to Expect.OPENS, // EPUB 2, NCX 만(구텐베르크)
        "E17" to Expect.OPENS, // 구텐베르크의 'ncx' id 함정(id=ncx 가 XHTML nav, 진짜 NCX 는 ncx2)
        "E18" to Expect.OPENS, // 법제처 한국어 EPUB 2
        "E20" to Expect.OPENS, // W3C XXE — 바깥 엔티티를 풀지 않는다
        "E21" to Expect.REFUSE_DRM, // Readium LCP(aes256-cbc 로 본문이 잠겼다)
    )

    private val openLimitMs = 30_000L
    private val traverseLimitMs = 120_000L

    /** 이 크기가 넘는 힙 최고치는 '메모리가 묶여 있다' 가 아니다. 시험 JVM 은 512 MB 다. */
    private val heapLimitBytes = 400L * 1024 * 1024

    private val report = StringBuilder()

    @Test
    fun 말뭉치의_EPUB_이_기대대로_열린다() {
        assumeTrue("samples-local/corpus 가 없다", Corpus.dir != null)
        val items = Corpus.manifest().filter { (it["file"] as String).endsWith(".epub") }
        assumeTrue("말뭉치에 EPUB 이 없다", items.isNotEmpty())
        report.append("id\texpect\toutcome\tchapters\ttoc\trecall_min\tprecision_min\trecall_mean\tprecision_mean\tfonts\tunsupported\topen_ms\ttotal_ms\tpeak_heap_mb\n")

        val problems = ArrayList<String>()
        for (item in items) {
            val id = item["id"] as String
            val expect = expected[id]
            if (expect == null) {
                problems.add("$id: 기대값 표에 없다 — 먼저 분류하라")
                continue
            }
            val file = File(Corpus.dir, item["file"] as String)
            val oracle = Corpus.oracle(id)
            try {
                problems.addAll(runOne(id, expect, file, oracle))
            } catch (e: TimeoutException) {
                problems.add("$id: 시간 상한을 넘었다")
            }
        }
        Corpus.outFile("epub-corpus.tsv")?.writeText(report.toString(), Charsets.UTF_8)
        println(report)
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /**
     * 말뭉치에서 **망가진 책을 만들어** 연다 — 반으로 자른 것(중앙 디렉터리가 없다)과, 가운데 4 KiB 를
     * 뒤섞은 것(압축된 장 하나가 깨진다). 열리든 못 열리든 되지만 **예외가 밖으로 새지 않고, 시간 안에
     * 끝나야 하며, 못 열면 '깨진 파일' 이어야 한다**(`Io` 가 아니다). 장을 읽다 나는 입출력 예외는 화면의
     * 공급기가 받아 404 로 끝내므로(`EpubReader`) 허용한다.
     * 만든 파일은 임시 파일이고 지운다.
     */
    @Test
    fun 망가뜨린_말뭉치에서_멈추지_않는다() {
        assumeTrue("samples-local/corpus 가 없다", Corpus.dir != null)
        val items = Corpus.manifest().filter { (it["file"] as String).endsWith(".epub") }
        assumeTrue("말뭉치에 EPUB 이 없다", items.isNotEmpty())
        val problems = ArrayList<String>()
        for (item in items) {
            val id = item["id"] as String
            val bytes = File(Corpus.dir, item["file"] as String).readBytes()
            val variants = listOf(
                "반" to bytes.copyOf(bytes.size / 2),
                "뒤섞음" to bytes.copyOf().also { b ->
                    val mid = b.size / 2
                    for (k in mid until minOf(b.size, mid + 4096)) b[k] = (b[k].toInt() xor 0x5A).toByte()
                },
            )
            for ((name, data) in variants) {
                val tmp = File.createTempFile("iroiro-corpus-$id-", ".epub")
                val pool = Executors.newSingleThreadExecutor()
                try {
                    tmp.writeBytes(data)
                    val future = pool.submit(Callable { brokenOne(tmp) })
                    future.get(openLimitMs + traverseLimitMs, TimeUnit.MILLISECONDS)
                        ?.let { problems.add("$id($name): $it") }
                } catch (e: TimeoutException) {
                    problems.add("$id($name): 시간 상한을 넘었다")
                } catch (e: java.util.concurrent.ExecutionException) {
                    problems.add("$id($name): 예외가 새어 나왔다 — ${e.cause}")
                } finally {
                    pool.shutdownNow()
                    tmp.delete()
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /**
     * 망가진 책 하나. 열리면 모든 장을 읽는다. 허용하지 않는 예외는 그대로 던진다.
     *
     * @return 문제가 있으면 그 문장. **못 열면 '깨진 파일' 이어야 한다** — `Io`('입출력이 실패했습니다')면
     *   덜 받은 책이 디스크 고장처럼 읽힌다(검토가 잡았다. 예전에는 반으로 자른 책이 전부 그랬다).
     */
    private fun brokenOne(file: File): String? {
        var orphan: OpenedDocument? = null
        val outcome = runBlocking { Documents.open(EpubOpener(), FileDocumentSource(file)) { orphan = it } }
        if (outcome !is OpenOutcome.Success) {
            orphan?.close()
            val failure = (outcome as OpenOutcome.Failed).failure
            return if (failure is OpenFailure.Io) "망가진 책이 입출력 실패로 끝났다 — $failure" else null
        }
        (outcome.document as EpubBook).use { book ->
            for (i in book.spine.indices) {
                try {
                    book.chapterHtml(i)
                } catch (e: java.io.IOException) {
                    // 공급기가 받는다(위 주석).
                } catch (e: io.github.donggi.iroiroviewer.safety.ParseLimitExceededException) {
                    // 상한도 공급기가 받는다.
                }
            }
        }
        return null
    }

    /** 파일 하나. 다른 스레드에서 돌리고 벽시계로 끊는다. 문제를 문장으로 돌려준다. */
    private fun runOne(id: String, expect: Expect, file: File, oracle: Map<String, Any?>?): List<String> {
        val pool = Executors.newSingleThreadExecutor()
        try {
            resetPeaks()
            val started = System.nanoTime()
            val future = pool.submit(Callable { check(id, expect, file, oracle, started) })
            return try {
                future.get(openLimitMs + traverseLimitMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw e
            } catch (e: java.util.concurrent.ExecutionException) {
                listOf("$id: 예외가 새어 나왔다 — ${e.cause}")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun check(id: String, expect: Expect, file: File, oracle: Map<String, Any?>?, started: Long): List<String> {
        val problems = ArrayList<String>()
        // 앱(`DocViewModel`)과 같은 모양 — 만든 자리에서 받아 두고, 성공이면 책으로 닫고 실패면 버려진 것을 닫는다.
        var orphan: OpenedDocument? = null
        val outcome = runBlocking { Documents.open(EpubOpener(), FileDocumentSource(file)) { orphan = it } }
        val openMs = (System.nanoTime() - started) / 1_000_000
        if (openMs > openLimitMs) problems.add("$id: 여는 데 ${openMs}ms")

        when (expect) {
            Expect.REFUSE_DRM -> {
                orphan?.close()
                val failure = (outcome as? OpenOutcome.Failed)?.failure
                if (failure !is OpenFailure.Encrypted) problems.add("$id: Encrypted 여야 한다 — $outcome")
                row(id, expect, outcome.toString(), "", "", null, null, "", "", openMs, openMs)
                return problems
            }
            Expect.OPENS -> Unit
        }
        if (outcome !is OpenOutcome.Success) {
            orphan?.close()
            problems.add("$id: 열리지 않았다 — $outcome")
            row(id, expect, outcome.toString(), "", "", null, null, "", "", openMs, openMs)
            return problems
        }
        val book = outcome.document as EpubBook
        book.use { b ->
            problems.addAll(traverse(id, b, oracle, openMs, started))
        }
        return problems
    }

    private fun traverse(id: String, book: EpubBook, oracle: Map<String, Any?>?, openMs: Long, started: Long): List<String> {
        val problems = ArrayList<String>()
        if (book.spine.isEmpty()) problems.add("$id: 차례가 비었다")

        // ---- 장 수·목차·제목(수는 정확히) -------------------------------------------
        @Suppress("UNCHECKED_CAST")
        val chapters = oracle?.get("chapters") as List<Map<String, Any?>>?
        if (oracle != null) {
            val want = (oracle["linear_chapters"] as Long).toInt()
            if (book.spine.size != want) problems.add("$id: 장 수 ${book.spine.size} ≠ 오라클 $want")
            val toc = (oracle["toc_expected"] as Long).toInt()
            if (book.toc.size != toc) problems.add("$id: 목차 ${book.toc.size}줄 ≠ 오라클 $toc")
            val title = oracle["title"] as String
            if (book.title != title) problems.add("$id: 제목 '${book.title}' ≠ '$title'")
        }

        // ---- 장마다 본문 -----------------------------------------------------------
        val recalls = ArrayList<Double>()
        val precisions = ArrayList<Double>()
        for (i in book.spine.indices) {
            val html = book.chapterHtml(i)
            if (html == null) {
                problems.add("$id: ${i + 1}장의 HTML 이 없다")
                continue
            }
            structural(id, i, html)?.let { problems.add(it) }
            val want = chapters?.getOrNull(i)
            if (want != null) {
                val oursPath = book.spine[i].path
                val shownPath = want["shown_path"] as String?
                if (shownPath != null && oursPath != shownPath && want["image"] != true) {
                    problems.add("$id: ${i + 1}장이 ${oursPath} — 오라클은 $shownPath 를 보인다")
                }
                if (want["image"] == true) {
                    // 그림 한 장을 보여 주는 쪽이어야 하고, 그림 바이트가 글로 새어 나오면 안 된다.
                    if (!html.contains("<img") || !html.contains("src=\"data:image/")) {
                        problems.add("$id: ${i + 1}장(그림)에 그림이 없다")
                    }
                    val leaked = CorpusText.tokens(CorpusText.visibleText(html)).size
                    if (leaked > 0) problems.add("$id: ${i + 1}장(그림)에 글이 ${leaked}낱말 보인다")
                } else {
                    val ours = CorpusText.tokens(CorpusText.visibleText(html))
                    val theirs = CorpusText.tokens(want["text"] as String)
                    val (r, p) = CorpusText.recallPrecision(ours, theirs)
                    recalls.add(r)
                    precisions.add(p)
                    // 오라클이 1,000 낱말 넘게 적은 장은 97%, 작은 장은 낱말 셋까지 어긋나도 된다 —
                    // 풀지 않은 엔티티(`&xxe;`) 하나가 짧은 장의 비율을 크게 흔든다.
                    val missing = theirs.size - (r * theirs.size).toInt()
                    val extra = ours.size - (p * ours.size).toInt()
                    val okR = r >= 0.97 || missing <= 3
                    val okP = p >= 0.97 || extra <= 3
                    if (!okR || !okP) {
                        problems.add("$id: ${i + 1}장 글 재현율 %.3f 정밀도 %.3f (낱말 %d / 오라클 %d)".format(r, p, ours.size, theirs.size))
                    }
                }
            }
        }
        val dropped = book.unsupported.snapshot()

        // ---- 버린 것의 집계 — 오라클이 lxml 로 센 수와 같아야 한다 --------------------------
        if (chapters != null) {
            val want = HashMap<String, Int>()
            for (c in chapters) {
                @Suppress("UNCHECKED_CAST")
                val e = c["elements"] as Map<String, Any?>? ?: continue
                for ((k, v) in e) want[k] = (want[k] ?: 0) + (v as Long).toInt()
            }
            val pairs = listOf(
                "script" to UnsupportedFeatures.SCRIPT,
                "frame" to UnsupportedFeatures.FRAME,
                "embedded-object" to UnsupportedFeatures.EMBEDDED_OBJECT,
                "event-handler" to UnsupportedFeatures.EVENT_HANDLER,
                "unknown-element" to UnsupportedFeatures.UNKNOWN_ELEMENT,
                "form" to UnsupportedFeatures.FORM,
                "remote-reference" to UnsupportedFeatures.REMOTE_REFERENCE,
            )
            for ((k, kind) in pairs) {
                val w = want[k] ?: 0
                val got = dropped[kind] ?: 0
                if (w != got) problems.add("$id: '$kind' ${got}개 ≠ 오라클 $w")
            }
        }

        // ---- 자원 전부 ---------------------------------------------------------------
        @Suppress("UNCHECKED_CAST")
        val resources = oracle?.get("resources") as List<Map<String, Any?>>?
        for (res in resources.orEmpty()) {
            val path = res["path"] as String
            val stream = book.openResource(path)
            if (stream == null) {
                problems.add("$id: 자원 $path 를 열지 못했다")
                continue
            }
            val n = stream.use { s -> var t = 0L; val buf = ByteArray(64 * 1024); while (true) { val k = s.read(buf); if (k < 0) break; t += k }; t }
            val size = (res["size"] as Long)
            if (n != size) problems.add("$id: 자원 $path ${n}바이트 ≠ $size")
            val type = (res["media_type"] as String).ifBlank { null }
            if (book.mediaTypeOf(path) != type) problems.add("$id: $path 의 MIME ${book.mediaTypeOf(path)} ≠ $type")
            if (type == "text/css" && book.styleSheet(path) == null) problems.add("$id: 스타일시트 $path 가 null")
        }

        // ---- 난독화 글꼴 — 푼 바이트가 공개된 평문과 같아야 한다 ------------------------------
        val fontNotes = ArrayList<String>()
        @Suppress("UNCHECKED_CAST")
        for (font in (oracle?.get("fonts") as List<Map<String, Any?>>?).orEmpty()) {
            val want = font["plain_sha256"] as String? ?: continue
            val path = font["path"] as String
            if (font["matches_published"] != true) problems.add("$id: 오라클의 $path 가 공개된 평문과 맞지 않는다 — 오라클부터 의심하라")
            val got = book.openResource(path)?.use { sha256(it.readBytes()) }
            val same = got == want
            fontNotes.add("${path.substringAfterLast('/')}=${if (same) "같다" else "다르다"}")
            if (!same) problems.add("$id: 글꼴 $path 를 푼 바이트가 평문과 다르다")
        }

        // ---- 표본마다 제 질문 -------------------------------------------------------------
        when (id) {
            // W3C pub-xml-external-id: 바깥 엔티티(`foo.xhtml` 의 'The test fails.')를 풀면 안 된다.
            "E20" -> {
                val html = book.chapterHtml(0).orEmpty()
                if ("test fails" in html.lowercase()) problems.add("$id: 바깥 엔티티가 풀렸다")
            }
            // 구텐베르크: id 가 'ncx' 인 항목은 XHTML nav 이고 진짜 NCX 는 'ncx2' 다. 목차가 차례를 가리켜야 한다.
            "E17" -> if (book.toc.any { it.spineIndex < 0 }) problems.add("$id: 목차가 차례 밖을 가리킨다 ${book.toc}")
        }

        // ---- 시간·메모리 -----------------------------------------------------------------
        val totalMs = (System.nanoTime() - started) / 1_000_000
        if (totalMs - openMs > traverseLimitMs) problems.add("$id: 다 읽는 데 ${totalMs - openMs}ms")
        val peak = peakHeap()
        if (peak > heapLimitBytes) problems.add("$id: 힙 최고치 ${peak shr 20} MB")

        row(
            id, Expect.OPENS, "Success",
            "${book.spine.size}/${oracle?.get("linear_chapters")}",
            "${book.toc.size}/${oracle?.get("toc_expected")}",
            recalls, precisions, fontNotes.joinToString(" "), dropped.toString(), openMs, totalMs, peak,
        )
        return problems
    }

    /**
     * 우리가 `text/html` 로 내주는 문서의 **모양**. 모두 WebView 에서만 보이는 결함이라 글 대조가 잡지 못한다.
     *
     * * 첫 태그 앞에 글이 있으면 그것이 화면 맨 위에 찍힌다(DOCTYPE 안의 엔티티 선언을 잘못 건너뛰면 `]>`).
     * * DOCTYPE 이 없으면 쿼크 모드로 그린다 — 표가 본문의 글자 크기를 물려받지 않는다. 원본 XHTML 은 XML 이라
     *   어느 읽기 프로그램에서도 표준 모드다.
     * * 언어가 `xml:lang` 에만 있으면 HTML 파서는 언어를 모른다 — 한자가 기기 로캘의 자형으로 그려진다.
     * * HTML 요소를 XHTML 식으로 스스로 닫으면(`<div/>`·`<title/>`) HTML 파서는 **여는 태그**로 읽는다 —
     *   뒤의 본문이 통째로 그 안에 들어간다(`<title/>` 이면 본문이 제목이 되어 화면이 빈다). SVG·MathML
     *   안은 스스로 닫기가 유효하므로 보지 않는다.
     */
    private fun structural(id: String, i: Int, html: String): String? {
        val lead = html.substringBefore('<')
        if (lead.isNotBlank()) return "$id: ${i + 1}장의 첫 태그 앞에 글이 있다: '${lead.trim().take(40)}'"
        if (!html.trimStart().startsWith("<!DOCTYPE", ignoreCase = true)) return "$id: ${i + 1}장에 DOCTYPE 이 없다(쿼크 모드)"
        val root = Regex("(?i)<html\\b[^>]*>").find(html)?.value.orEmpty()
        if ("xml:lang=" in root && !Regex("\\slang=").containsMatchIn(root)) {
            return "$id: ${i + 1}장의 언어가 xml:lang 에만 있다 — HTML 에서는 언어가 아니다: $root"
        }
        val outsideForeign = Regex("(?is)<(svg|math)[\\s>].*?</\\1\\s*>").replace(html, "")
        val m = SELF_CLOSED.find(outsideForeign)
        if (m != null) return "$id: ${i + 1}장에 스스로 닫힌 HTML 요소 '${m.value.take(60)}'"
        return null
    }

    private fun row(
        id: String, expect: Expect, outcome: String, chapters: String, toc: String,
        recalls: List<Double>?, precisions: List<Double>?, fonts: String, unsupported: String,
        openMs: Long, totalMs: Long, peak: Long = peakHeap(),
    ) {
        fun f(v: Double?) = v?.let { "%.4f".format(it) } ?: ""
        report.append(
            listOf(
                id, expect.name, outcome.take(80), chapters, toc,
                f(recalls?.minOrNull()), f(precisions?.minOrNull()),
                f(recalls?.takeIf { it.isNotEmpty() }?.average()), f(precisions?.takeIf { it.isNotEmpty() }?.average()),
                fonts, unsupported, openMs.toString(), totalMs.toString(), (peak shr 20).toString(),
            ).joinToString("\t")
        ).append('\n')
    }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun resetPeaks() {
        System.gc()
        ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.forEach { it.resetPeakUsage() }
    }

    private fun peakHeap(): Long =
        ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used }

    private companion object {
        /** HTML 의 짝 있는 요소를 스스로 닫은 것. 짝 없는 요소(`br`·`img`…)는 들지 않는다. */
        val SELF_CLOSED = Regex(
            "(?i)<(a|abbr|b|big|blockquote|body|caption|cite|code|dd|div|dl|dt|em|figcaption|figure|h[1-6]|" +
                "head|html|i|label|li|nav|ol|p|pre|q|rt|ruby|s|section|small|span|strong|style|sub|sup|table|" +
                "tbody|td|textarea|th|thead|title|tr|u|ul|aside|header|footer|article|main)\\b[^>]*/>",
        )
    }
}
