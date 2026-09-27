package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.format.html.HancomChars
import io.github.donggi.iroiroviewer.format.html.HancomCss
import io.github.donggi.iroiroviewer.format.html.HancomFonts
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

/** HWPX 를 흐름 문서로 여는 입구. 패키지는 [HwpxOpener] 가 열어 건넨다. */
internal object HwpxDocument {

    /**
     * 패키지를 흐름 문서로. **패키지의 주인이 돌려준 문서로 넘어간다** — 문서를 닫으면 패키지도 닫힌다.
     * 던지면 여는이가 패키지를 닫고 `toOpenFailure` 로 옮긴다.
     *
     * 보여 줄 부분이 없으면 부분이 **없는** 문서를 돌려준다 — 여는이가 [HwpxFlowDocument.emptyFailure] 로 끝낸다.
     */
    suspend fun open(pkg: HwpxPackage, limits: ParseLimits, progress: ProgressSink, options: HwpxOptions): HwpxFlowDocument {
        val context = currentCoroutineContext()
        // 여기서 던지면 문서는 부르는 쪽에 닿지 않는다 — 패키지는 여는이의 `finally` 가 닫는다(문서는 패키지 말고
        // 쥔 자원이 없다).
        val doc = HwpxFlowDocument(pkg, limits, options)
        doc.prepare(context)
        context.ensureActive()
        progress.report(3, 4)
        return doc
    }
}

/**
 * 열린 HWPX 하나.
 *
 * ## 부분
 *
 * 구역 하나가 부분 하나다. 긴 구역(보도자료의 통계 부록은 구역 하나가 13 MB 다)은 docx 처럼 조각으로 나눈다
 * ([HwpxOptions.chunk] — HWP 5.0 과 같은 기준). 목차는 개요 문단에서 온다 — 한국 공공 문서는 개요 스타일을 거의 쓰지 않아 목차가 비는 일이
 * 흔하다(표본 아홉 모두 그렇다). 그것이 문서의 사실이다.
 *
 * ## 여는 동안 한 번 훑는다
 *
 * 머리(`header.xml`)를 읽고 구역마다 한 번 끝까지 훑어 조각의 경계·목차·책갈피·표의 칸 정보를 얻는다([HwpxWalker]
 * 의 주석). 버린 것(그릴 수 없는 그림·차트·수식…)도 이때 센다.
 *
 * ## 구역이 다 깨졌을 때 — 미리보기 글
 *
 * 구역을 하나도 읽지 못했지만 `Preview/PrvText.txt`(한글이 저장할 때 적는 앞부분의 글)가 있으면 **그것을 부분
 * 하나로** 보인다. 깨진 구역마다 [FlowWarnings.PART_FAILED] 를 남긴다 — 아무것도 못 보여 주는 것보다 앞부분의
 * 글이라도 보이는 편이 낫고, 경고가 그것이 원문 전체가 아님을 말한다.
 *
 * ## 배포용 문서
 *
 * `settings.xml` 의 `ha:docdistribute`(한컴 모델 `DocDistribute.cpp` — `key`·`nocopy`·`noprint`)가 있으면 배포용
 * 문서다. **그 내용이 어떻게 잠기는지는 알려져 있지 않다**(조사 노트 — 명세 문서가 HWP 5.0 의 것뿐이고 HWPX 의
 * 배포용 실물을 구하지 못했다). 그래서 암호학을 짐작하지 않는다: 구역이 읽히면 그대로 보이고, 하나도 읽히지 않으면
 * 미리보기 글(글처럼 보일 때만 — 위 절)을 보이고, 그것도 없으면 [OpenFailure.Encrypted] 로 끝낸다.
 */
internal class HwpxFlowDocument(
    pkg: HwpxPackage,
    limits: ParseLimits,
    private val options: HwpxOptions,
) : FlowDocumentBase<HwpxPackage>(pkg, limits, FormatId.HWPX) {

    @Volatile
    private var layout: HwpxLayout = HwpxLayout.EMPTY

    @Volatile
    private var env: HwpxEnv? = null

    @Volatile
    private var partList: List<FlowPart> = emptyList()

    /** 구역을 읽지 못해 미리보기 글을 보인다. */
    @Volatile
    private var previewOnly = false

    /** 배포용 문서(`ha:docdistribute`). */
    @Volatile
    var distribution = false
        private set

    /** 보여 줄 부분이 없을 때 여는이가 돌려줄 실패. */
    @Volatile
    var emptyFailure: OpenFailure? = null
        private set

    /**
     * 제목은 **비워 둔다** — 화면이 파일 이름으로 채운다. HWP 5.0 변환기와 같은 판단이다: 공공 문서의 `opf:title` 은
     * 서식 파일에서 물려받은 낡은 값이 흔하다(`공표용 보도자료`, `3`, `1111` — 13단계 표본). 파일 이름이 더 정확하다.
     * 읽은 값(`HwpxPackage.title`)은 남겨 둔다.
     */
    override val title: String get() = ""
    override val kind: FlowKind = FlowKind.DOCUMENT
    override val parts: List<FlowPart> get() = partList
    override val outline: List<FlowOutline> get() = layout.outline
    /** 바탕 스타일 — HWP 5.0 변환기와 같은 한 벌(`HancomCss.FLOW`, 13단계 짝 대조가 일곱 군데의 어긋남을 찾았다). */
    override val css: String = HancomCss.FLOW

    /** 여는 동안 한 번. 머리·설정을 읽고 구역을 훑는다. */
    fun prepare(context: CoroutineContext) = locked {
        val checkCancel = { context.ensureActive() }
        val header = auxiliary(pkg.header, checkCancel = checkCancel) { p -> HwpxHeaderParser.parse(p, limits, checkCancel) }
            ?: HwpxHeader.EMPTY
        distribution = auxiliary(pkg.settings, quiet = true, checkCancel = checkCancel) { p -> hasDocDistribute(p) } ?: false
        checkCancel()

        val environment = HwpxEnv(pkg, limits, header, unsupported) { isDisplayableImage(it) }
        val collector = ScanCollector(options.chunk, checkCancel)
        val state = WalkState()
        val failed = ArrayList<Int>()
        var limitHit = false
        for ((s, name) in pkg.sections.withIndex()) {
            collector.beginSection(s, state)
            val walker = HwpxWalker(
                environment,
                main = null,
                notes = null,
                state = state,
                cfg = WalkConfig(section = s, scan = collector, recordFeatures = true, checkCancel = checkCancel),
            )
            val ok = try {
                tolerant {
                    val parsed = pkg.parser(name, checkCancel) ?: return@tolerant false
                    parsed.second.use { walker.section(parsed.first) }
                }
            } catch (e: ParseLimitExceededException) {
                // 구역 하나가 상한(해제량·압축비·XML 깊이)을 넘었다. 그 앞까지는 보이고, 구역이 다 이렇게 실패하면
                // '깨진 파일' 이 아니라 '너무 크다' 로 알린다.
                limitHit = true
                false
            }
            // 깨진 자리까지는 보인다(docx 와 같다). **처음부터** 못 읽은 구역만 '통째로 실패' 다.
            if (!ok && walker.topBlocks == 0) failed.add(s)
        }
        val lay = collector.build()
        env = environment

        val sectionCount = pkg.sections.size
        if (sectionCount == 0 || failed.size == sectionCount) {
            for (s in failed) warn(FlowWarnings.PART_FAILED, FlowWarnings.PART_NUMBER_PREFIX + (s + 1))
            if (pkg.preview != null && previewText() != null) {
                previewOnly = true
                layout = HwpxLayout(emptyList(), emptyList(), emptyMap(), emptyMap(), lay.sections)
                partList = listOf(FlowPart(partPath(0), ""))
            } else {
                layout = HwpxLayout.EMPTY
                partList = emptyList()
                emptyFailure = when {
                    distribution -> OpenFailure.Encrypted("배포용 문서의 본문을 읽지 못했다")
                    limitHit -> OpenFailure.TooLarge("구역이 너무 크다")
                    else -> OpenFailure.Corrupt("보여 줄 구역이 없다")
                }
            }
            return@locked
        }
        layout = lay
        partList = lay.chunks.mapIndexed { i, c -> FlowPart(partPath(i), c.label) }
    }

    override fun renderBody(index: Int): String {
        if (previewOnly) return renderPreview()
        val environment = env ?: return ""
        val lay = layout
        val chunk = lay.chunks[index]
        val sectionName = pkg.sections.getOrNull(chunk.section) ?: return ""
        val label = partName(index)
        val w = HtmlWriter(options.maxChars)
        val noteSink = NoteSink(options.maxChars / 4)
        // 바탕 글자가 명조면 부분 전체를 명조로 — 문단·글자마다 적지 않으려는 것이다([HwpxWalker] 의 `baseCss`·`runCss`).
        val serif = HancomFonts.isSerif(environment.header.defaultChar.font)
        if (serif) w.start("div", "class" to "serif")
        val cancel = { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("취소") }
        // 구역의 처음부터 걸으면 `hp:secPr` 을 다시 지난다 — 훑기와 같게 기본값에서 시작한다.
        val info = if (chunk.start == 0) SectionInfo.DEFAULT else lay.sections.getOrElse(chunk.section) { SectionInfo.DEFAULT }
        val walker = HwpxWalker(
            environment,
            main = w,
            notes = noteSink,
            state = chunk.state.copy(),
            cfg = WalkConfig(
                section = chunk.section,
                start = chunk.start,
                end = chunk.end,
                partIndex = index,
                bookmarkParts = lay.bookmarks,
                tables = lay.tables,
                partHref = { partPath(it) },
                onTruncated = { warn(FlowWarnings.TRUNCATED, label) },
                checkCancel = cancel,
                sectionInfo = info,
                maxChars = options.maxChars,
            ),
        )
        guarded(label) {
            val parsed = pkg.parser(sectionName, cancel)
            val rooted = parsed?.second?.use { walker.section(parsed.first) } ?: false
            if (!rooted) warn(FlowWarnings.PART_FAILED, label)
        }
        if (w.full) warn(FlowWarnings.TRUNCATED, label)
        w.closeAll()
        val notesHtml = noteSink.finish()
        // 각주는 본문의 `div.serif` 밖에 붙는다. 같은 글꼴로 보이게 한 겹 더 싼다(상수 태그만 이어 붙인다).
        return w.toString() + if (serif && notesHtml.isNotEmpty()) "<div class=\"serif\">$notesHtml</div>" else notesHtml
    }

    /** 미리보기 글을 문단으로. 한글은 표의 칸을 `<` `>` 로 감싸 적는다 — 그대로 보인다. */
    private fun renderPreview(): String {
        val text = previewText() ?: return ""
        val w = HtmlWriter(options.maxChars)
        w.start("div", "class" to "preview")
        for (line in text.split('\n')) {
            val t = line.trimEnd('\r')
            w.start("p")
            if (t.isEmpty()) w.void("br") else w.text(t)
            w.end("p")
            if (w.full) break
        }
        w.end("div")
        if (w.full) warn(FlowWarnings.TRUNCATED, partName(0))
        return w.closeAll().toString()
    }

    /**
     * `Preview/PrvText.txt` 의 글. 표본은 전부 UTF-8 이다(HWP 5.0 의 `PrvText` 는 UTF-16LE). 바이트 순서 표시가
     * 있으면 그것을 따르고, 홀수 자리마다 0 이 많으면 UTF-16LE 로 읽는다.
     *
     * **글처럼 보이지 않으면 없는 것으로 친다**([looksLikeText]). 구역을 못 읽은 까닭이 암호라면(암호 매니페스트가
     * 깨졌거나, 풀 줄 모르는 배포용 보호) 미리보기도 암호문이고, UTF-16LE 는 어떤 바이트열도 글자로 읽으므로 그대로
     * 두면 뜻 없는 한자·한글이 '문서의 앞부분' 으로 보인다.
     */
    private fun previewText(): String? {
        val name = pkg.preview ?: return null
        val bytes = try {
            pkg.readBytes(name, HwpxLimits.MAX_PREVIEW_BYTES) ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return try {
            decodePreview(bytes).takeIf { it.isNotBlank() && looksLikeText(it) }
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * 보조 부분 하나(머리·설정)를 읽는다. **깨졌거나 너무 크면 없는 것으로 친다** — 머리 하나 때문에 본문을 못
     * 여는 것보다 서식 없이라도 여는 편이 낫다. 그 사실은 말한다([FlowWarnings.AUX_FAILED]). 취소만 위로 올린다.
     */
    private inline fun <T> auxiliary(
        name: String?,
        quiet: Boolean = false,
        noinline checkCancel: () -> Unit = {},
        read: (XmlPullParser) -> T,
    ): T? {
        if (name == null) return null
        return try {
            // 잠긴 머리는 열쇠 유도(PBKDF2)를 거친다 — 그 동안에도 취소를 본다.
            val (p, stream) = pkg.parser(name, checkCancel) ?: return null
            stream.use {
                if (!HwpxXml.toRoot(p, limits)) return null
                read(p)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            if (!quiet) warn(FlowWarnings.AUX_FAILED, name)
            null
        }
    }

    /** 훑기의 실패는 거기서 멈춘 것으로 친다. 거짓이면 실패했다. 취소와 상한은 위로 올린다(부르는 쪽이 가른다). */
    private inline fun tolerant(block: () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: ParseLimitExceededException) {
        throw e
    } catch (e: Exception) {
        false
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

    /** `settings.xml` 에 `docdistribute` 가 있는가. */
    private fun hasDocDistribute(p: XmlPullParser): Boolean {
        var found = false
        var count = 0
        HwpxXml.eachChild(p, limits) { name ->
            if (name == "docdistribute") found = true
            HwpxXml.skip(p, limits)
            if (++count > MAX_SETTINGS_ITEMS) return found
        }
        return found
    }

    companion object {
        private const val MAX_SETTINGS_ITEMS = 10_000

        /**
         * 글처럼 보이는가 — 글자의 95% 이상이 한국 문서에 나오는 영역([TEXT_RANGES]: 기본 라틴·한글·한자·일본 가나·
         * 문장 부호·기호·전각)에 들어야 한다. 무작위 바이트를 UTF-16LE 로 읽으면 이 영역이 60% 쯤이라(나머지는 벵골·
         * 몽골·이 문자·확장 한자·사설 영역·짝 잃은 대리 문자…) 16 자만 되어도 거의 걸러지고, 한글이 쓴 실물 미리보기는
         * 99.7% 이상이었다(표본 여덟 — K33 의 사설 영역 글자 셋이 가장 많다).
         */
        fun looksLikeText(text: String): Boolean {
            var usual = 0
            var count = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                    // 보조 평면(확장 한자·그림 문자). 사설 영역만 아니면 센다 — 무작위로는 짝이 거의 맞지 않는다.
                    if (!HancomChars.isPrivateUse(Character.toCodePoint(c, text[i + 1]))) usual++
                    i += 2
                } else {
                    val code = c.code
                    if (TEXT_RANGES.any { code in it }) usual++
                    i++
                }
                count++
            }
            return usual * 20L >= count * 19L
        }

        /** [looksLikeText] 가 '흔한 글자' 로 치는 영역. 제어 문자·U+FFFD·사설 영역·대리 문자는 들지 않는다. */
        private val TEXT_RANGES = listOf(
            0x09..0x0A, 0x0D..0x0D, 0x20..0x7E,
            0xA0..0x24F, // 라틴-1 보충·라틴 확장
            0x370..0x4FF, // 그리스·키릴
            0x1100..0x11FF, // 한글 자모
            0x1E00..0x1EFF, // 라틴 확장 추가(베트남어)
            0x2000..0x27BF, // 문장 부호·위첨자·통화·글자 같은 기호·화살표·수학·도형·딩뱃
            0x2E80..0x33FF, // 한자 부수·CJK 문장 부호·가나·한글 호환 자모·원/괄호 문자·CJK 호환(㎡)
            0x4E00..0x9FFF, // CJK 통합 한자
            0xA960..0xA97F, // 한글 자모 확장 A
            0xAC00..0xD7FF, // 한글 음절·자모 확장 B
            0xF900..0xFAFF, // CJK 호환 한자
            0xFE30..0xFE4F, // CJK 호환 꼴
            0xFF00..0xFFEF, // 반각·전각
        )

        fun decodePreview(bytes: ByteArray): String {
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            // 영문이 많은 UTF-16LE 는 홀수 자리가 0 이다. 한글뿐인 UTF-16LE 는 그렇지 않지만 UTF-8 로는 거의 언제나
            // 깨진 바이트열이다 — 엄격한 UTF-8 해독이 실패하면 UTF-16LE 로 읽는다.
            var zeros = 0
            val probe = minOf(bytes.size, 512)
            var i = 1
            while (i < probe) {
                if (bytes[i] == 0.toByte()) zeros++
                i += 2
            }
            if (probe >= 4 && zeros * 4 >= probe / 2) return String(bytes, Charsets.UTF_16LE)
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                String(bytes, Charsets.UTF_16LE)
            }
        }
    }
}
