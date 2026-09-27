package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.Documents
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.archive.ZipArchiveReader
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * **실세계 OOXML 표본**(`samples-local/corpus/`, 저장소에 넣지 않는다)을 앱과 같은 길로 여는 시험 틀.
 *
 * docx·xlsx·pptx 의 시험이 함께 쓴다 — 세 벌로 두면 한쪽만 고쳐지는 날이 온다(5단계의 '문구 두 벌').
 * 변환기는 모르므로(이 모듈이 변환기를 보지 않는다) 부르는 시험이 [Build] 로 넘긴다.
 *
 * ## 앱과 같은 길
 *
 * `app/FormatRegistry` 가 하는 일을 그대로 밟는다 — 앞 64바이트와 **8단계의 ZIP 리더가 읽은 이름**으로
 * 판별하고(HWPX·EPUB 판별기가 먼저 도는 차례까지 흉내 낸다), `OoxmlOpener(password) { … }` 를
 * `Documents.open`(시간 상한·예외 변환)으로 부른 뒤, 화면처럼 **모든 부분의 HTML** 을 청하고 거기 적힌
 * 그림을 `openResource` 로 받아 본다. 판별기가 맡지 않으면 앱은 '여는이가 없다' 로 끝나므로 여기서도 그렇다.
 *
 * ## 표본과 오라클
 *
 * `manifest.json` 이 파일마다 출처·암호·**초안** 기대를 적는다. 초안은 조사 단계의 짐작이라 조사 노트와
 * 반박 검토를 읽고 고친 값을 [REFINED] 에 둔다(고친 까닭과 함께). 오라클(`oracle/<ID>.*`)은 우리 코드와
 * 아무것도 나누지 않는 도구가 만든다 — python-docx·openpyxl·python-pptx·msoffcrypto-tool·olefile, 그리고
 * 표본을 낸 프로젝트(POI·Tika·LibreOffice)의 시험이 단언하는 글. 만드는 스크립트는 말뭉치 곁(`samples-local/corpus/tools/` — 말뭉치와 함께
 * 커밋하지 않는다)의 `make_oracles.py`·`probe_corpus.py` 다.
 */
object OoxmlCorpus {

    /** 변환기를 잇는 것 — `FormatRegistry.ooxml` 의 람다와 같은 모양. */
    typealias Build = suspend (OpcPackage, OoxmlKind, ParseLimits, ProgressSink) -> FlowDocument

    enum class Family { DOCX, XLSX, PPTX }

    /** 고친 기대. 초안(`manifest.json` 의 `expected`)의 열 가지 이름을 앱이 실제로 내야 할 결과로 옮겼다. */
    enum class Expect {
        /** 열리고 부분마다 글이 있다. */
        OPENS,

        /** 암호 없이 `PasswordRequired(false)`, 틀린 암호로 `PasswordRequired(true)`, 적힌 암호로 열린다. */
        PASSWORD,

        /** `VelvetSweatshop` 으로 잠겼다 — **묻지 않고** 열린다. */
        OPENS_DEFAULT_PASSWORD,

        /** 이전 형식(.doc·.xls) — 암호를 주든 말든 `LegacyFormat`. 암호를 묻지 않는다. */
        LEGACY,

        /** 다루지 않는 갈래(xlsb) — `Unsupported`. 깨졌다고 말하지 않는다. */
        UNSUPPORTED,

        /**
         * 열 수 없는 깨진 파일 — `Corrupt` 로 끝난다. '다루지 않는 문서'(판별기가 맡지 않음)나 '입출력 실패' 로
         * 끝나면 사용자가 형식이나 저장소를 의심하므로 틀린 것이다(잘린 docx 가 둘 다였다).
         */
        CORRUPT,

        /** 무엇으로 끝나든(실패·부분 성공) 예외가 새지 않고, 멎지 않고, 메모리가 묶인다. */
        ROBUST,

        /** 상한에 걸린다 — `TRUNCATED` 경고와 함께 열리거나 `TooLarge`. 메모리가 넘치지 않는다. */
        BIG,
    }

    class Entry(
        val id: String,
        val file: File,
        val format: String,
        /** 문서가 적어 둔 암호(괄호의 설명은 뗐다). */
        val password: String?,
        val draft: String,
        val expect: Expect,
        /** 초안과 다르게 정한 까닭. 같으면 빈 문자열. */
        val why: String,
    ) {
        val family: Family get() = familyOf(file.name)
        override fun toString() = "$id(${file.name})"
    }

    /**
     * 초안을 고친 것. **여기 없는 표본은 초안 그대로다.** 까닭은 조사 노트(`research-samples/ooxml.md`)와
     * 반박 검토(`samples-existing.json` 의 `check.problems`), 그리고 오라클이 실제로 보인 것이다.
     */
    val REFINED: Map<String, Pair<Expect, String>> = mapOf(
        "EN07" to (Expect.OPENS_DEFAULT_PASSWORD to "robust→기본 암호로 열림: POI csv 의 '일부러 망가뜨림' 은 무한 반복 회귀용이고, 명세대로 쓴 독립 복호화기가 HMAC 까지 맞고 12,810바이트 ZIP 을 낸다(POI dataLength 와 같다)"),
        "EN15" to (Expect.PASSWORD to "그릇의 스트림 크기(7,848)가 섹터 사슬(6,144)보다 크다. 명세대로 min(선언, 사슬)을 읽으면 풀리고 ZIP 항목 크기가 POI bug57080 의 열 개와 같다"),
        "EN20" to (Expect.UNSUPPORTED to "판별기가 .xlsb 를 맡지 않아 앱은 암호를 묻지 않고 '다루지 않는다' 로 끝난다. 여는이를 직접 불러 암호로 풀어도 xlsb 본문이라 Unsupported 여야 한다"),
        "CR01" to (Expect.CORRUPT to "763바이트로 잘린 ZIP(끝 레코드가 없다) — 열 것이 없다. Tika 도 예외를 기대한다"),
        "CR02" to (Expect.ROBUST to "퍼저 표본 — 항목이 적힌 크기보다 길고, 끝 레코드가 가리키는 중앙 디렉터리 자리(14,399)가 파일(12,632바이트) 밖이다. 우리 ZIP 리더도 파이썬 zipfile 도 중앙 디렉터리에서 거절한다('깨진 파일'). 부분 성공이어도 받아들인다"),
        "CR06" to (Expect.ROBUST to "DTD 엔티티 폭탄 — SafeXml 이 DTD 를 거절한다. 시트 하나의 실패 또는 여는 실패"),
        "CR08" to (Expect.OPENS to "'앱이 거절한다' 는 POI 의 동작. 우리는 먼저 나온 항목을 쓰고 flow.duplicate_part 를 남긴다(반박 검토)"),
        "CR10" to (Expect.ROBUST to "OSS-Fuzz 표본(slide1.xml 의 CRC 가 틀렸다) — 부분 실패 또는 여는 실패, 새는 예외 없음"),
        "CR14" to (Expect.OPENS to "바깥을 가리키는 필드 코드 표본. 본문은 정상이다 — 열리되 바깥 주소가 src·href 로 남지 않아야 한다"),
    )

    /** 표본 폴더. 없으면 null — 시험은 건너뛴다(CLAUDE.md '표본'). */
    val dir: File? by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "samples-local/corpus") }
            .firstOrNull { File(it, "manifest.json").isFile }
    }

    fun familyOf(name: String): Family = when (name.substringAfterLast('.').lowercase()) {
        "docx", "docm", "dotx", "dotm", "doc", "dot" -> Family.DOCX
        "xlsx", "xlsm", "xltx", "xltm", "xlsb", "xls", "xlt" -> Family.XLSX
        else -> Family.PPTX
    }

    private val OOXML_EXT = setOf(
        "docx", "docm", "dotx", "dotm", "doc", "xlsx", "xlsm", "xltx", "xltm", "xlsb", "xls",
        "pptx", "pptm", "potx", "potm", "ppsx", "ppsm", "ppt",
    )

    /** 이 시험들의 범위(OOXML·이전 형식) 전부. */
    fun entries(): List<Entry> {
        val d = dir ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val list = MiniJson.parse(File(d, "manifest.json").readText(Charsets.UTF_8)) as List<Map<String, Any?>>
        return list.mapNotNull { m ->
            val file = File(d, m["file"] as String)
            if (file.name.substringAfterLast('.').lowercase() !in OOXML_EXT || !file.isFile) return@mapNotNull null
            val id = m["id"] as String
            val draft = m["expected"] as String
            val refined = REFINED[id]
            Entry(
                id = id,
                file = file,
                format = m["format"] as String,
                password = (m["password"] as String?)?.substringBefore(" (")?.trim()?.ifEmpty { null },
                draft = draft,
                expect = refined?.first ?: fromDraft(draft),
                why = refined?.second.orEmpty(),
            )
        }
    }

    fun entries(family: Family): List<Entry> = entries().filter { it.family == family }

    private fun fromDraft(draft: String): Expect = when (draft) {
        "opens" -> Expect.OPENS
        "password" -> Expect.PASSWORD
        "opens-default-password" -> Expect.OPENS_DEFAULT_PASSWORD
        "legacy" -> Expect.LEGACY
        "unsupported-xlsb" -> Expect.UNSUPPORTED
        "big" -> Expect.BIG
        "corrupt-graceful", "robust" -> Expect.ROBUST
        // 조사 노트의 두 갈래(인증서·DRM)는 이 범위에 표본이 없다. 모르는 이름은 최소 약속만 건다.
        else -> Expect.ROBUST
    }

    // ---- 오라클 --------------------------------------------------------------------------------------

    fun oracle(id: String, ext: String): File? = dir?.let { File(it, "oracle/$id.$ext") }?.takeIf { it.isFile }

    fun oracleLines(id: String, ext: String): List<String>? = oracle(id, ext)?.readLines(Charsets.UTF_8)

    fun oracleCounts(id: String): Map<String, String> =
        oracleLines(id, "counts").orEmpty().filter { '\t' in it }.associate { it.substringBefore('\t') to it.substringAfter('\t') }

    // ---- 앱과 같은 길 ----------------------------------------------------------------------------------

    /** 부분 하나를 화면처럼 청한 결과. */
    class PartResult(val label: String, val html: String?, val text: String)

    class Run(
        val probed: FormatId?,
        val failure: OpenFailure?,
        val flowKind: FlowKind?,
        val parts: List<PartResult>,
        val warnings: List<ParseWarning>,
        val unsupported: Map<String, Int>,
        val ms: Long,
        val peakHeapMb: Long,
        /** 여는이·`partHtml`·`openResource` 밖으로 샌 예외. 있으면 그 자체로 결함이다. */
        val escaped: Throwable?,
        val timedOut: Boolean,
        val images: Int,
        val imagesServed: Int,
        /** 본문의 `src`·`href` 에 남은 바깥 주소. 위생기가 지웠어야 한다. */
        val externalRefs: List<String>,
    ) {
        val succeeded: Boolean get() = failure == null && !timedOut && escaped == null && flowKind != null
        val text: String get() = parts.joinToString("\n") { it.text }
        fun warned(code: String) = warnings.any { it.code == code }

        fun summary(): String = when {
            timedOut -> "TIMEOUT"
            escaped != null -> "ESCAPED ${escaped::class.java.simpleName}"
            failure != null -> "${failure::class.java.simpleName}(${failure.detail})" +
                ((failure as? OpenFailure.PasswordRequired)?.let { " wrong=${it.wrongPassword}" } ?: "")
            else -> "OK parts=${parts.size} nullParts=${parts.count { it.html == null }} img=$imagesServed/$images " +
                "warn=${warnings.map { it.code.removePrefix("flow.") }.distinct()} unsup=$unsupported"
        }
    }

    /**
     * `FormatRegistry.probe` 를 흉내 낸다 — 판별기 차례(HWPX → EPUB → OOXML → HWP 5.0 → PDF)에서
     * OOXML 앞의 둘이 가로채는 조건을 그대로 두고, OOXML 판별기는 **실물**을 부른다. 뒤의 둘은 이 범위의
     * 확장자를 맡지 않는다(HWP 는 `.hwp`·`.hwt`·`.hwpx`, PDF 는 `%PDF` 또는 `.pdf`).
     */
    fun probeAsApp(source: DocumentSource): FormatId? {
        val head = try {
            source.head(64)
        } catch (e: Exception) {
            return null
        }
        val ext = source.displayName.substringAfterLast('.', "").lowercase()
        val names = lazy {
            if (head.size < 4 || head[0] != 0x50.toByte() || head[1] != 0x4B.toByte()) {
                null
            } else {
                try {
                    ZipArchiveReader(source, EntryBudget()).use { r -> r.entries.map { it.name } }
                } catch (e: Exception) {
                    null
                }
            }
        }
        val isLocalZip = head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() && head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        fun stored(mime: String): Boolean =
            head.size >= 30 + 8 + mime.length && String(head, 30, 8 + mime.length, Charsets.US_ASCII) == "mimetype$mime"
        if (isLocalZip) {
            // HwpxProbe
            if (stored("application/hwp+zip")) return FormatId.HWPX
            if (names.value?.any { it.equals("Contents/content.hpf", ignoreCase = true) } == true) return FormatId.HWPX
            // EpubProbe
            if (stored("application/epub+zip")) return FormatId.EPUB
            val n = names.value
            if (n != null && n.any { it == "META-INF/container.xml" }) return FormatId.EPUB
            if (n == null && ext == "epub") return FormatId.EPUB
        }
        return OoxmlProbe.probe(ProbeContext(source, ext, head, names))
    }

    /**
     * 표본 하나를 앱처럼 연다. [timeoutMs] 는 **여는 일과 모든 부분을 그리는 일을 합친** 상한이다 —
     * 앱의 시간 상한(`Documents.open`, 30초)은 여는 일에만 걸리고 부분 그리기에는 없으므로, 멎는 부분을
     * 여기서 따로 잡는다. 넘으면 그 스레드를 인터럽트하고 [Run.timedOut].
     */
    fun run(file: File, password: String?, build: Build, timeoutMs: Long = 120_000): Run {
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "corpus-${file.name}").apply { isDaemon = true } }
        val rt = Runtime.getRuntime()
        System.gc()
        val base = rt.totalMemory() - rt.freeMemory()
        val peak = java.util.concurrent.atomic.AtomicLong(base)
        val sampling = java.util.concurrent.atomic.AtomicBoolean(true)
        val sampler = Thread {
            while (sampling.get()) {
                peak.accumulateAndGet(rt.totalMemory() - rt.freeMemory()) { a, b -> maxOf(a, b) }
                try {
                    Thread.sleep(5)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }.apply { isDaemon = true; start() }
        val started = System.nanoTime()
        val future = pool.submit<Run> { runInside(file, password, build, started) }
        val result = try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause ?: e
            Run(null, null, null, emptyList(), emptyList(), emptyMap(), elapsed(started), 0, cause, false, 0, 0, emptyList())
        } finally {
            sampling.set(false)
            pool.shutdownNow()
        }
        sampler.join(1000)
        val peakMb = (peak.get() - base).coerceAtLeast(0) / (1024 * 1024)
        return result?.let {
            Run(it.probed, it.failure, it.flowKind, it.parts, it.warnings, it.unsupported, it.ms, peakMb, it.escaped, false, it.images, it.imagesServed, it.externalRefs)
        } ?: Run(null, null, null, emptyList(), emptyList(), emptyMap(), elapsed(started), peakMb, null, true, 0, 0, emptyList())
    }

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000

    private fun runInside(file: File, password: String?, build: Build, started: Long): Run {
        val source = FileDocumentSource(file)
        val probed = probeAsApp(source)
        fun failed(f: OpenFailure) = Run(probed, f, null, emptyList(), emptyList(), emptyMap(), elapsed(started), 0, null, false, 0, 0, emptyList())
        // `DocViewModel.openNow` — 여는이가 없으면 '다루지 않는다'.
        if (probed == null) return failed(OpenFailure.Unsupported("여는이가 없다"))
        if (probed !in OOXML_IDS) return failed(OpenFailure.Unsupported("다른 여는이: ${probed.name}"))
        val pw = password?.toCharArray()
        var orphan: OpenedDocument? = null
        try {
            val outcome = try {
                runBlocking { Documents.open(OoxmlOpener(pw, build = build), source) { orphan = it } }
            } finally {
                // 주인(`DocViewModel`)이 하듯 여는 일이 끝나면 지운다. 여는이는 지우지 않는다.
                pw?.fill('\u0000')
            }
            if (outcome is OpenOutcome.Failed) return failed(outcome.failure)
            val doc = (outcome as OpenOutcome.Success).document as FlowDocument
            orphan = null
            return doc.use { render(it, probed, started) }
        } catch (t: Throwable) {
            return Run(probed, null, null, emptyList(), emptyList(), emptyMap(), elapsed(started), 0, t, false, 0, 0, emptyList())
        } finally {
            orphan?.close()
        }
    }

    private val OOXML_IDS = setOf(FormatId.DOCX, FormatId.XLSX, FormatId.PPTX, FormatId.LEGACY_OFFICE)

    private val IMG = Regex("<img\\b[^>]*?\\ssrc=\"([^\"]*)\"")
    private val URL_ATTR = Regex("\\s(src|href|srcset|poster|data)=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val EXTERNAL = Regex("^\\s*(https?:|file:|content:|ftp:|//|mailto:|javascript:|data:)", RegexOption.IGNORE_CASE)

    /** 화면처럼 — 목차·제목을 읽고, 모든 부분을 청하고, 거기 적힌 그림을 받는다. */
    private fun render(doc: FlowDocument, probed: FormatId, started: Long): Run {
        doc.title
        doc.outline
        val parts = ArrayList<PartResult>()
        var images = 0
        var served = 0
        val external = ArrayList<String>()
        for (i in doc.parts.indices) {
            val html = doc.partHtml(i)
            // 부분 경로가 저 자신을 가리키는가(화면이 링크를 가로챌 때 쓴다).
            check(doc.partIndexOf(doc.parts[i].path) == i) { "부분 경로가 저 자신을 가리키지 않는다: $i" }
            if (html != null) {
                for (m in URL_ATTR.findAll(html)) {
                    val v = m.groupValues[2]
                    if (EXTERNAL.containsMatchIn(v)) external.add(v.take(80))
                }
                for (m in IMG.findAll(html)) {
                    images++
                    val path = percentDecode(m.groupValues[1].substringBefore('#'))
                    doc.openResource(path)?.use { s ->
                        val buf = ByteArray(64 * 1024)
                        var any = false
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            if (n > 0) any = true
                        }
                        if (any) served++
                    }
                    doc.mediaTypeOf(path)
                }
            }
            parts.add(PartResult(doc.parts[i].label, html, html?.let { visibleText(it, doc.kind == FlowKind.SHEETS) }.orEmpty()))
        }
        return Run(
            probed, null, doc.kind, parts, doc.warnings, doc.unsupported.snapshot(), elapsed(started), 0, null, false,
            images, served, external,
        )
    }

    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                out.write(s.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    // ---- 글자 비교 ------------------------------------------------------------------------------------

    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "li", "ul", "ol", "table", "tr", "td", "th", "thead", "tbody", "h1", "h2", "h3", "h4", "h5", "h6",
        "section", "article", "hr", "img", "figure", "figcaption", "blockquote", "pre", "dt", "dd", "dl", "aside", "nav",
    )
    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|amp|quot|apos|nbsp|#39);")

    /**
     * HTML 에서 **사람에게 보이는 글자**만. 머리(`head`·`style`)를 버리고, 시트의 행·열 머리(`th`)는
     * 문서의 글이 아니므로 뺀다. 블록 태그는 줄 바꿈, 인라인 태그는 아무것도 아니게 — `Hel<b>lo</b>` 가
     * 두 낱말이 되지 않게.
     */
    fun visibleText(html: String, dropHeaderCells: Boolean): String {
        var s = html.replace(Regex("(?s)<head\\b.*?</head>"), " ").replace(Regex("(?s)<style\\b.*?</style>"), " ")
            .replace(Regex("(?s)<!DOCTYPE[^>]*>|<!--.*?-->"), " ")
        if (dropHeaderCells) s = s.replace(Regex("(?s)<th\\b[^>]*>.*?</th>"), " ")
        // 목록 표지(`span.mk`)와 위·아래 첨자는 화면에서 본문과 떨어져 보인다 — 각주 번호 `1` 이 앞 낱말에
        // 붙어 `prostoy1` 이 되거나 표지 `NEW-1-FORMAT` 이 `Level1` 에 붙지 않게 경계를 둔다.
        s = s.replace(Regex("(?s)(<span class=\"mk\"[^>]*>.*?</span>)"), "$1 ")
            .replace(Regex("</?su[pb]\\b[^>]*>"), " ")
        s = TAG.replace(s) { m -> if (m.groupValues[2].lowercase() in BLOCK_TAGS) "\n" else "" }
        return ENTITY.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "amp" -> "&"
                e == "quot" -> "\""
                e == "apos" || e == "#39" -> "'"
                e == "nbsp" -> " "
                e.startsWith("#x") -> String(Character.toChars(e.substring(2).toInt(16)))
                else -> String(Character.toChars(e.substring(1).toInt()))
            }
        }
    }

    private val TOKEN = Regex("[\\p{L}\\p{N}]+")

    fun tokens(s: String): List<String> = TOKEN.findAll(s.lowercase()).map { it.value }.toList()

    /** 낱말 다중 집합의 되부름(오라클의 낱말 중 우리에게 있는 몫)과 정밀도(우리 낱말 중 오라클에 있는 몫). */
    class Score(val oracleTokens: Int, val ourTokens: Int, val matched: Int) {
        val recall: Double get() = if (oracleTokens == 0) 1.0 else matched.toDouble() / oracleTokens
        val precision: Double get() = if (ourTokens == 0) (if (oracleTokens == 0) 1.0 else 0.0) else matched.toDouble() / ourTokens
        override fun toString() = "recall=%.3f precision=%.3f (oracle %d, ours %d, matched %d)".format(recall, precision, oracleTokens, ourTokens, matched)
    }

    fun score(oracle: String, ours: String): Score {
        val a = tokens(oracle).groupingBy { it }.eachCount()
        val b = tokens(ours).groupingBy { it }.eachCount()
        var matched = 0
        for ((k, n) in a) matched += minOf(n, b[k] ?: 0)
        return Score(a.values.sum(), b.values.sum(), matched)
    }

    /** 공백을 하나로 모은 글 — 단언 문장(`must`)을 찾을 때 쓴다. */
    fun squash(s: String): String = s.replace('\u00A0', ' ').replace(Regex("\\s+"), " ")

    // ---- 판정 ----------------------------------------------------------------------------------------

    /** 표본 하나의 판정 — 보고 줄과 어긋난 것들. [main] 은 내용을 본 실행(열린 것)이다. */
    class Verdict(val entry: Entry, val line: String, val problems: List<String>, val main: Run?, val score: Score?)

    /**
     * 고친 기대([Entry.expect])대로 되는가. 모든 표본에 **새는 예외 없음·멎지 않음·메모리 묶임** 을 걸고,
     * 열려야 하는 것은 내용을 오라클과 견준다.
     *
     * @param minRecall 오라클 낱말의 되부름 하한. 오라클이 우리보다 적게 보는 것(머리말·글상자·각주)은
     *   정밀도를 깎을 뿐 되부름은 깎지 않으므로, 되부름이 모자라면 우리가 **빠뜨린** 것이다.
     * @param skipMust 오라클 단언 가운데 이 앱이 일부러 그리지 않는 것(까닭은 부르는 쪽이 적는다).
     * @param extra 포맷마다 더 볼 것(열린 실행을 받는다).
     */
    fun verify(
        entry: Entry,
        build: Build,
        minRecall: Double = 0.95,
        skipMust: Set<String> = emptySet(),
        allowPartFailed: Boolean = false,
        extra: (Run) -> List<String> = { emptyList() },
    ): Verdict {
        val problems = ArrayList<String>()
        val runs = ArrayList<Pair<String, Run>>()
        fun go(label: String, pw: String?): Run = run(entry.file, pw, build).also { runs.add(label to it) }
        fun expectFailure(r: Run, label: String, ok: (OpenFailure) -> Boolean, want: String) {
            if (r.failure == null || !ok(r.failure)) problems.add("$label: $want 이어야 하는데 ${r.summary()}")
        }
        var main: Run? = null
        when (entry.expect) {
            Expect.OPENS, Expect.OPENS_DEFAULT_PASSWORD -> main = go("암호 없이", null)
            Expect.PASSWORD -> {
                val pw = requireNotNull(entry.password) { "${entry.id}: 암호가 적혀 있지 않다" }
                expectFailure(go("암호 없이", null), "암호 없이", { it is OpenFailure.PasswordRequired && !it.wrongPassword }, "PasswordRequired(false)")
                expectFailure(go("틀린 암호", "wrong-$pw"), "틀린 암호", { it is OpenFailure.PasswordRequired && it.wrongPassword }, "PasswordRequired(true)")
                main = go("적힌 암호", pw)
            }
            Expect.LEGACY -> {
                expectFailure(go("암호 없이", null), "암호 없이", { it is OpenFailure.LegacyFormat }, "LegacyFormat")
                entry.password?.let { expectFailure(go("적힌 암호", it), "적힌 암호", { f -> f is OpenFailure.LegacyFormat }, "LegacyFormat") }
            }
            Expect.UNSUPPORTED -> {
                expectFailure(go("암호 없이", null), "암호 없이", { it is OpenFailure.Unsupported }, "Unsupported")
                entry.password?.let { expectFailure(go("적힌 암호", it), "적힌 암호", { f -> f is OpenFailure.Unsupported }, "Unsupported") }
            }
            Expect.CORRUPT -> expectFailure(go("암호 없이", null), "암호 없이", { it is OpenFailure.Corrupt }, "Corrupt")
            Expect.ROBUST -> go("암호 없이", null)
            Expect.BIG -> {
                val r = go("암호 없이", null)
                val ok = (r.succeeded && r.warned(FlowWarnings.TRUNCATED)) || r.failure is OpenFailure.TooLarge
                if (!ok) problems.add("TRUNCATED 경고와 함께 열리거나 TooLarge 여야 하는데 ${r.summary()}")
                // 줄여서 열었다면 **앞부분이 실제로 보여야** 한다 — 부분 실패 표시(⚠)만 남은 '줄였다' 는 줄인 것이 아니다.
                if (r.succeeded) {
                    val flat = squash(r.text)
                    if (flat.isBlank()) problems.add("줄였다면서 보이는 글이 없다")
                    for (must in oracleLines(entry.id, "must").orEmpty().filter { it.isNotBlank() }) {
                        if (squash(must).trim() !in flat) problems.add("단언 글이 없다: '$must'")
                    }
                }
            }
        }
        val heapLimitMb = Runtime.getRuntime().maxMemory() / (1024 * 1024) * 85 / 100
        for ((label, r) in runs) {
            if (r.timedOut) problems.add("$label: 시간 상한을 넘었다(멎었다)")
            r.escaped?.let { problems.add("$label: 예외가 샜다 ${it::class.java.name}: ${it.message?.take(120)}") }
            if (r.peakHeapMb > heapLimitMb) problems.add("$label: 힙을 ${r.peakHeapMb} MB 썼다(한도 $heapLimitMb)")
            if ((r.failure as? OpenFailure.TooLarge)?.detail?.contains("메모리") == true) problems.add("$label: 메모리가 모자랐다")
        }
        var score: Score? = null
        val m = main
        if (m != null) {
            if (!m.succeeded) {
                problems.add("열려야 하는데 ${m.summary()}")
            } else {
                // 사람이 견줘 볼 수 있게 우리가 그린 글을 남긴다(로컬 전용 폴더).
                dir?.let { d ->
                    File(d, "oracle/ours").mkdirs()
                    File(d, "oracle/ours/${entry.id}.txt").writeText(
                        m.parts.withIndex().joinToString("\n") { (i, p) -> "# part $i ${p.label}\n${p.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")}" },
                        Charsets.UTF_8,
                    )
                    m.parts.forEachIndexed { i, p -> p.html?.let { File(d, "oracle/ours/${entry.id}.$i.html").writeText(it, Charsets.UTF_8) } }
                }
                if (m.parts.isEmpty()) problems.add("부분이 없다")
                m.parts.forEachIndexed { i, p -> if (p.html == null) problems.add("부분 $i 의 HTML 이 null") }
                if (!allowPartFailed && m.warned(FlowWarnings.PART_FAILED)) {
                    problems.add("부분 실패: ${m.warnings.filter { it.code == FlowWarnings.PART_FAILED }.map { it.detail }}")
                }
                if (m.externalRefs.isNotEmpty()) problems.add("바깥 주소가 남았다: ${m.externalRefs.take(3)}")
                val oracleText = oracleLines(entry.id, "txt")
                if (oracleText != null) {
                    val s = score(oracleText.joinToString("\n"), m.text)
                    score = s
                    if (s.recall < minRecall) problems.add("되부름 %.3f < %.2f (%s)".format(s.recall, minRecall, s))
                    if (s.oracleTokens > 0 && m.text.isBlank()) problems.add("오라클에는 글이 있는데 우리는 비었다")
                }
                val flat = squash(m.text)
                for (must in oracleLines(entry.id, "must").orEmpty().filter { it.isNotBlank() }) {
                    if (must in skipMust) continue
                    if (squash(must).trim() !in flat) problems.add("단언 글이 없다: '$must'")
                }
                problems += extra(m)
            }
        }
        val line = listOf(
            entry.id, entry.file.name, entry.expect.name,
            runs.joinToString(" | ") { (label, r) -> "$label→${r.summary()}" },
            score?.let { "R=%.3f P=%.3f".format(it.recall, it.precision) } ?: "-",
            "${runs.sumOf { it.second.ms }}ms", "${runs.maxOfOrNull { it.second.peakHeapMb } ?: 0}MB",
            if (problems.isEmpty()) "PASS" else "FAIL: " + problems.joinToString(" / "),
        ).joinToString("\t")
        return Verdict(entry, line, problems, m, score)
    }

    // ---- 결과 기록 ------------------------------------------------------------------------------------

    /** 보고용 한 줄(탭). 시험 출력과 `oracle/results-<family>.tsv` 에 남긴다(로컬 전용). */
    fun record(family: Family, lines: List<String>) {
        val d = dir ?: return
        File(d, "oracle/results-${family.name.lowercase()}.tsv").writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        lines.forEach { println(it) }
    }
}

/**
 * 시험용 최소 JSON 읽개(표본 목록 하나를 읽는 데만 쓴다). 의존을 늘리지 않으려는 것이다 — JVM 시험에는
 * `org.json` 이 없다(안드로이드의 것이다).
 */
internal object MiniJson {
    fun parse(s: String): Any? = Reader(s).run { value().also { ws(); require(i == s.length) { "JSON 뒤에 남는 글자" } } }

    private class Reader(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("JSON 이 아니다: $c @$i")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "JSON 이 아니다 @$i" }
            i += word.length
            return v
        }

        fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDouble()
        }

        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out.add(value())
                ws()
                if (s[i++] == ']') return out
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') {
                i++
                return out
            }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i++] == ':') { "JSON 이 아니다 @$i" }
                out[k] = value()
                ws()
                if (s[i++] == '}') return out
            }
        }
    }
}
