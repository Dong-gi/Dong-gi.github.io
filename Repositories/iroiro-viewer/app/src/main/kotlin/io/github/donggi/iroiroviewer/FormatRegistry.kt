package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.docview.DocumentSupport
import io.github.donggi.iroiroviewer.docview.pdf.PdfOpener
import io.github.donggi.iroiroviewer.docview.pdf.PdfProbe
import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.archive.ZipArchiveReader
import io.github.donggi.iroiroviewer.format.docx.DocxDocument
import io.github.donggi.iroiroviewer.format.epub.EpubOpener
import io.github.donggi.iroiroviewer.format.epub.EpubProbe
import io.github.donggi.iroiroviewer.format.hwp5.Hwp5Opener
import io.github.donggi.iroiroviewer.format.hwp5.Hwp5Probe
import io.github.donggi.iroiroviewer.format.hwpx.HwpxOpener
import io.github.donggi.iroiroviewer.format.hwpx.HwpxProbe
import io.github.donggi.iroiroviewer.format.opc.OoxmlKind
import io.github.donggi.iroiroviewer.format.opc.OoxmlOpener
import io.github.donggi.iroiroviewer.format.opc.OoxmlProbe
import io.github.donggi.iroiroviewer.format.pptx.PptxDocument
import io.github.donggi.iroiroviewer.format.xlsx.XlsxDocument
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.safety.EntryBudget
import java.util.concurrent.CancellationException

/**
 * **무엇을 무엇으로 여는가** 를 아는 단 한 곳.
 *
 * 2단계의 계획이 적어 두고 11단계까지 비어 있던 파일이다(모듈 지도가 "아직 없다" 고
 * 현재형으로 가리키고 있었다). 11단계가 여는 포맷이 둘이 되면서 실제로 자리가 생겼다.
 *
 * ## 왜 `app` 에 있는가
 *
 * 포맷 모듈을 **전부** 볼 수 있는 곳이 여기뿐이다. `feature:docview` 가 직접 골랐다면
 * 12·13단계에 가서 포맷 모듈 일곱을 보게 되고, 그것은 의존 표가 금지한 모양이다
 * (`feature:*` 는 자기 포맷 모듈 하나). 조립은 `app` 이 한다 —
 * `ExtractSupport`·`CoverSupport`·`PlayerSupport` 와 같은 판단이다.
 *
 * ## 확장자만 보지 않는다
 *
 * 판별은 `FormatProbe` 가 하고, 그것은 매직 바이트와 ZIP 엔트리 이름을 본다.
 * **목록 화면은 확장자만 본다**(`FileKind`) — 폴더 하나를 그리려고 파일 수천 개의 앞부분을
 * 읽을 수는 없기 때문이다. 그 경계가 `ProbeContext` 의 주석에 적혀 있고, 여기가 그
 * '실제로 열 때' 다.
 *
 * ## ZIP 엔트리 이름을 게으르게 공급한다
 *
 * 12·13단계에 가면 docx·xlsx·pptx·hwpx 가 **전부 ZIP** 이라 이름을 봐야 갈린다.
 * 그래서 공급을 여기서 해 두되, **매직이 `PK` 일 때만** 연다 — 그렇지 않으면 아무도
 * 이 값을 건드리지 않는다.
 */
object FormatRegistry : DocumentSupport.Registry {

    /**
     * 판별기. **순서가 뜻을 갖는다** — 먼저 답하는 것이 이긴다.
     *
     * ZIP 계열(HWPX·EPUB·OOXML)을 앞에 두는 것은 그쪽이 매직과 엔트리 이름으로 확실히 갈리기
     * 때문이고, PDF 를 뒤에 두는 것은 그것이 확장자로도 맡기 때문이다. EPUB 과 OOXML 은
     * 서로를 가로채지 않는다 — EPUB 에는 `[Content_Types].xml` 이 없고 OOXML 에는 `mimetype` 이 없다.
     *
     * **HWPX 는 EPUB 보다 먼저다.** HWPX 에도 `mimetype` 과 `META-INF/container.xml` 이 있어, 이름만 보는
     * EPUB 판별기가 먼저 돌면 한글 문서를 전자책으로 연다(13단계에서 잡았다). HWP 5.0 은 CFB 라
     * OOXML 판별기(오피스 확장자만 맡는다)와 겹치지 않는다.
     */
    private val probes: List<FormatProbe> = listOf(HwpxProbe, EpubProbe, OoxmlProbe, Hwp5Probe, PdfProbe)

    /**
     * 여는이. 받는 것은 사용자가 넣은 암호다(없으면 null).
     *
     * **암호를 받는 포맷만 그것을 쓴다.** PDF 의 표준 보안 처리기는 공개 명세라 우리가 푼다.
     * EPUB 에는 암호라는 것이 없다(암호화는 DRM 이고, 그것은 암호로 풀리지 않는다).
     */
    private val openers: Map<FormatId, (CharArray?) -> DocumentOpener> = mapOf(
        FormatId.EPUB to { _ -> EpubOpener() },
        FormatId.PDF to { password -> PdfOpener(password) },
        // OOXML 넷은 **한 여는이**가 맡는다. 판별기가 확장자로 짐작한 종류가 아니라 패키지가
        // 말하는 종류로 열기 때문이다(`OoxmlOpener` 의 주석). 암호(MS-OFFCRYPTO)도 거기서 받는다.
        FormatId.DOCX to ::ooxml,
        FormatId.XLSX to ::ooxml,
        FormatId.PPTX to ::ooxml,
        FormatId.LEGACY_OFFICE to ::ooxml,
        // 한글. 암호 HWPX 는 공개된 방식(한컴이 공개한 OWPML 모델)이라 암호를 받는다. HWP 5.0 의
        // 암호 문서는 방식이 공개되지 않아 받지 않고 '암호로는 열 수 없다' 로 끝난다(여는이의 주석).
        FormatId.HWPX to { password -> HwpxOpener(password) },
        FormatId.HWP5 to { password -> Hwp5Opener(password) },
    )

    /** 세 변환기를 이어 준 OOXML 여는이. */
    private fun ooxml(password: CharArray?): DocumentOpener = OoxmlOpener(password) { pkg, kind, limits, progress ->
        when (kind) {
            OoxmlKind.DOCX -> DocxDocument.open(pkg, limits, progress)
            OoxmlKind.XLSX -> XlsxDocument.open(pkg, limits, progress)
            OoxmlKind.PPTX -> PptxDocument.open(pkg, limits, progress)
        }
    }

    /** `app` 이 시작할 때 이음매에 꽂는다. */
    fun install() {
        DocumentSupport.registry = this
    }

    override fun openerFor(source: DocumentSource, password: CharArray?): DocumentOpener? {
        val id = probe(source) ?: return null
        val opener = openers[id]?.invoke(password)
        Iro.d(TAG) { "${id.name} 으로 연다" }
        return opener
    }

    private fun probe(source: DocumentSource): FormatId? {
        val head = try {
            source.head(HEAD_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Iro.d(TAG) { "앞부분을 읽지 못했다: ${t::class.java.simpleName}" }
            return null
        }
        val context = ProbeContext(
            source = source,
            extension = MimeResolver.extensionOf(source.displayName),
            head = head,
            zipEntryNames = lazy { zipNames(source, head) },
        )
        for (probe in probes) probe.probe(context)?.let { return it }
        return null
    }

    /**
     * ZIP 이면 엔트리 이름, 아니면 null.
     *
     * **8단계의 리더로 연다.** 엔트리 수·경로 탈출 방어가 거기 있고, 판별 한 번을 위해
     * 그것을 우회하는 두 번째 ZIP 경로를 만들지 않는다.
     */
    private fun zipNames(source: DocumentSource, head: ByteArray): List<String>? {
        if (head.size < 4) return null
        if (head[0] != 0x50.toByte() || head[1] != 0x4B.toByte()) return null
        return try {
            ZipArchiveReader(source, EntryBudget()).use { reader ->
                reader.entries.map { it.name }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // 열지 못하면 '이름을 모른다' 이지 '실패' 가 아니다. 다음 판별기가 답한다.
            Iro.d(TAG) { "ZIP 목록을 읽지 못했다: ${t::class.java.simpleName}" }
            null
        }
    }

    /**
     * 앞부분을 몇 바이트 읽는가.
     *
     * EPUB 의 고정 자리 매직이 30번째 바이트에서 시작해 27자를 차지하므로 최소 57 이
     * 필요하다. 64 는 `DocumentSource.head` 의 기본값이고 그것으로 충분하다.
     */
    private const val HEAD_BYTES = 64

    private const val TAG = "format"
}
