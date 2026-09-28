package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.format.cfb.CfbLimits
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext

/**
 * HWP 5.0(한글 2002 이후의 `.hwp`)을 흐름 문서로 연다.
 *
 * 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
 * (한컴의 '한글 문서 파일 형식 5.0' rev 1.3 이 이 문장을 제품의 화면·설명서·도움말·소스에 적으라고 요구한다 — 소스에는
 * 여기 적었고, 화면에는 `app` 의 고지 화면이 같은 문장을 보인다(`NoticeCatalogTest` 가 둘을 바이트로 견준다). '한글' 의 첫
 * 음절은 옛한글 조합형 자모 셋(U+1112 U+119E U+11AB)이다 — 명세 원문(S01)에서 읽었다. 처음 옮길 때 그 음절이 빠져
 * '한글과컴퓨터의 글 문서 파일' 로 적혀 있었다.)
 *
 * ## 여는 차례
 *
 * 1. **CFB 가 아니면** — 앞머리가 `HWP Document File V3` 이면 한글 97 이전(HWP 3.0)이라 '이전 형식' 으로, 아니면 깨진 파일로.
 * 2. `FileHeader` 의 서명·판(5.x 만)·속성을 본다. **암호(비트 1)는 묻지 않고** '열 수 없는 방식으로 잠긴 문서' 로 끝낸다 —
 *    방식이 공개 명세에 없다(명세는 비트와 판 번호만 적는다. 거꾸로 짜 맞춘 구현은 쓰지 않는다 — CLAUDE.md '암호가 걸린 파일').
 *    DRM·인증서(비트 4·8·10)도 같다 — 열쇠가 암호가 아니다.
 * 3. 배포용(비트 2)이면 `ViewText` 를 풀어 읽는다([Hwp5Distribution] — 열쇠가 파일 안에 있어 암호를 묻지 않는다).
 * 4. 문서를 만들자마자 [DocumentOpener.open] 의 `onOpen` 으로 넘긴다(소유권). 훑기가 끝나면 성공.
 *
 * @param password 사용자가 넣은 암호(없으면 null). **쓰지 않는다** — 이 여는이는 암호를 받아 푸는 방식이 없다(위 2).
 *   계약의 모양을 다른 여는이와 맞추려고 받는다. 지우지도 않는다(주인은 `DocViewModel`).
 */
class Hwp5Opener internal constructor(
    @Suppress("unused") private val password: CharArray?,
    private val limits: ParseLimits,
    private val options: Hwp5Options,
) : DocumentOpener {

    constructor(password: CharArray?, limits: ParseLimits = ParseLimits.DEFAULT) : this(password, limits, Hwp5Options())

    override val handles: Set<FormatId> = setOf(FormatId.HWP5)

    override suspend fun open(
        source: DocumentSource,
        progress: ProgressSink,
        onOpen: (OpenedDocument) -> Unit,
    ): OpenOutcome {
        var cfb: CfbFile? = null
        var pkg: Hwp5Package? = null
        // 훑기는 코루틴 밖의 막히는 계산이다. 시간 상한(`withTimeout`)은 스레드를 인터럽트하지 않고 잡만 취소하므로
        // **잡의 상태를 직접 들여다봐야** 멈춘다(`OoxmlOpener` 와 같다).
        val context = currentCoroutineContext()
        return try {
            val head = source.head(32)
            if (!CfbFile.isCfb(head)) {
                return OpenOutcome.Failed(
                    if (Hwp5FileHeader.isHwp3(head)) OpenFailure.LegacyFormat(HWP3) else OpenFailure.Corrupt("HWP 가 아니다")
                )
            }
            val file = CfbFile.open(source, CfbLimits.from(limits))
            cfb = file
            val headerEntry = file.find("FileHeader")?.takeIf { it.isStream }
                ?: return OpenOutcome.Failed(OpenFailure.Corrupt("HWP 가 아니다"))
            val header = Hwp5FileHeader.parse(file.readStream(headerEntry, Hwp5Limits.MAX_FILE_HEADER_BYTES))
            if (header.major != 5) return OpenOutcome.Failed(OpenFailure.Unsupported("HWP ${header.major}.x"))
            if (header.password) return OpenOutcome.Failed(OpenFailure.Encrypted("암호가 걸린 HWP"))
            if (header.drm) return OpenOutcome.Failed(OpenFailure.Encrypted("DRM 이 걸린 HWP"))
            val p = Hwp5Package(file, header)
            pkg = p
            cfb = null
            context.ensureActive()
            val doc = Hwp5FlowDocument(p, limits, options)
            doc.prepare({ checkCancelled(context) }, progress)
            // 문서가 꾸러미의 주인이 됐다. 이 뒤로는 문서를 닫으면 꾸러미도 닫힌다.
            pkg = null
            onOpen(doc)
            if (doc.parts.isEmpty()) {
                // `onOpen` 으로 건넨 뒤라 부르는 쪽의 `finally` 가 닫는다(`DocumentOpener` 의 계약).
                return OpenOutcome.Failed(OpenFailure.Corrupt("보여 줄 부분이 없다"))
            }
            OpenOutcome.Success(doc)
        } catch (t: Throwable) {
            OpenOutcome.Failed(t.toOpenFailure())
        } finally {
            pkg?.close()
            cfb?.close()
        }
    }

    /**
     * 막히는 자리에서 부른다. 잡이 취소됐으면 `CancellationException`, 스레드가 인터럽트됐으면 `InterruptedIOException`
     * (`Thread.interrupted()` 는 플래그를 지우므로 쓰지 않는다 — 함정 표).
     */
    private fun checkCancelled(context: CoroutineContext) {
        context.ensureActive()
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("취소")
    }

    companion object {
        /** `OpenFailure.LegacyFormat` 의 `detail` — 화면이 오피스의 이전 형식과 가를 때 본다. */
        const val HWP3 = "HWP 3.0"
    }
}

/**
 * 판별기. **CFB(OLE2)이고 확장자가 한글의 것**(`hwp`·`hwt`·`hwpx`)이면 맡는다 — 속(`FileHeader` 의 서명)은
 * 여는이가 본다. `.hwpx` 를 넣는 것은 이름만 HWPX 이고 속이 HWP 5.0 인 파일이 공공 문서에 실제로 있기
 * 때문이다(13단계 표본 K36). 한글 97 이전(HWP 3.0)은 CFB 가 아니라 앞머리가 `HWP Document File V3` 인
 * 파일이다 — 그것도 맡아 여는이가 '이전 형식' 으로 정확히 말하게 한다. 모든 파일에 대해 돌므로 앞머리만 본다.
 */
object Hwp5Probe : FormatProbe {

    private val EXTENSIONS = setOf("hwp", "hwt", "hwpx")

    override fun probe(context: ProbeContext): FormatId? {
        if (context.extension !in EXTENSIONS) return null
        if (CfbFile.isCfb(context.head)) return FormatId.HWP5
        if (Hwp5FileHeader.isHwp3(context.head)) return FormatId.HWP5
        return null
    }
}
