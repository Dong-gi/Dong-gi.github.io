package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 패키지가 담은 문서의 종류. */
enum class OoxmlKind { DOCX, XLSX, PPTX }

/**
 * OOXML 을 여는 것 — 평문 패키지(ZIP)와 **암호가 걸린 패키지**(CFB 안의 `EncryptedPackage`).
 *
 * ## 변환기를 모른다
 *
 * 이 모듈은 docx·xlsx·pptx 모듈을 보지 않는다(그것들이 이 모듈 위에 선다). 무엇으로 바꿀지는
 * [build] 로 받는다 — `app/FormatRegistry` 가 세 변환기를 이어 준다. 조립은 `app` 의 일이다.
 *
 * ## 종류를 확장자로 정하지 않는다
 *
 * 판별기(`OoxmlProbe`)가 docx 로 불렀어도 **패키지가 말하는 종류**로 연다(본문 부분의 콘텐츠
 * 형식). 암호가 걸린 파일은 풀기 전까지 속을 볼 수 없어 판별기가 확장자로 짐작할 수밖에 없고,
 * 확장자는 사람이 바꾸는 값이다.
 *
 * ## 암호
 *
 * 암호는 사용자가 넣은 것이다(없으면 null). **여는이는 쓰기만 하고 지우지 않는다** — 주인은
 * 부르는 쪽(`DocViewModel`)이다(CLAUDE.md '암호를 다루는 규칙'). 풀어 낸 평문 패키지는
 * **메모리에만** 있다 — 저장소에 쓰지 않는다.
 */
class OoxmlOpener(
    private val password: CharArray?,
    private val limits: ParseLimits = ParseLimits.DEFAULT,
    private val build: suspend (OpcPackage, OoxmlKind, ParseLimits, ProgressSink) -> FlowDocument,
) : DocumentOpener {

    override val handles: Set<FormatId> =
        setOf(FormatId.DOCX, FormatId.XLSX, FormatId.PPTX, FormatId.LEGACY_OFFICE)

    override suspend fun open(
        source: DocumentSource,
        progress: ProgressSink,
        onOpen: (OpenedDocument) -> Unit,
    ): OpenOutcome {
        var pkg: OpcPackage? = null
        // 열쇠 유도(10만 회)는 코루틴 밖의 막히는 계산이다. 시간 상한(`withTimeout`)은 스레드를
        // 인터럽트하지 않고 잡(job)만 취소하므로, **잡의 상태를 직접 들여다봐야** 멈춘다.
        val context = currentCoroutineContext()
        return try {
            val head = source.head(8)
            val opened = when {
                isZip(head) -> OpcPackage.open(source, limits)
                isCfb(head) -> when (val r = OfficeCfb.open(source, password, limits) { checkCancelled(context) }) {
                    // 평문은 패키지가 닫힐 때 지운다(여는 데 실패해도). 문서가 패키지의 주인이 되므로
                    // 문서를 닫는 것이 곧 평문을 지우는 것이다.
                    is OfficeCfb.Result.Decrypted -> OpcPackage.open(
                        ByteArrayDocumentSource(r.bytes, source.displayName),
                        limits,
                        wipeOnClose = r.bytes,
                    )
                    is OfficeCfb.Result.Failed -> return OpenOutcome.Failed(r.failure)
                }
                else -> return OpenOutcome.Failed(OpenFailure.Corrupt("OOXML 이 아니다"))
            }
            pkg = opened
            currentCoroutineContext().ensureActive()
            val kind = OoxmlKinds.of(opened)
                ?: return OpenOutcome.Failed(OpenFailure.Unsupported("알 수 없는 OOXML 본문"))
            progress.report(1, 3)
            val doc = build(opened, kind, limits, progress)
            // 문서가 패키지의 주인이 됐다. 이 뒤로는 문서를 닫으면 패키지도 닫힌다.
            pkg = null
            onOpen(doc)
            if (doc.parts.isEmpty()) {
                // `onOpen` 으로 건넨 뒤라 부르는 쪽의 `finally` 가 닫는다(`DocumentOpener` 의 계약).
                return OpenOutcome.Failed(OpenFailure.Corrupt("보여 줄 부분이 없다"))
            }
            progress.report(3, 3)
            OpenOutcome.Success(doc)
        } catch (t: Throwable) {
            OpenOutcome.Failed(t.toOpenFailure())
        } finally {
            pkg?.close()
        }
    }

    /**
     * CFB 풀기처럼 막히는(blocking) 자리에서 부른다. 잡이 취소됐으면 `CancellationException`,
     * 스레드가 인터럽트됐으면 `InterruptedIOException` 을 던진다(`Thread.interrupted()` 는
     * 플래그를 지우므로 쓰지 않는다 — 함정 표).
     */
    private fun checkCancelled(context: kotlin.coroutines.CoroutineContext) {
        context.ensureActive()
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("취소")
    }

    companion object {
        fun isZip(head: ByteArray): Boolean =
            head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                head[2] == 0x03.toByte() && head[3] == 0x04.toByte()

        private val CFB_MAGIC = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        fun isCfb(head: ByteArray): Boolean =
            head.size >= 8 && (0 until 8).all { head[it] == CFB_MAGIC[it] }
    }
}

/** 패키지가 담은 문서의 종류를 가린다. */
object OoxmlKinds {

    /**
     * 본문 부분의 콘텐츠 형식으로 가르고, 형식이 없으면 부분 이름(`word/`·`xl/`·`ppt/`)으로 짐작한다.
     * 매크로·서식 파일(`docm`·`dotx`·`xlsm`·`potx`)도 같은 종류로 연다 — 매크로는 돌리지 않는다.
     */
    fun of(pkg: OpcPackage): OoxmlKind? {
        val main = pkg.mainDocument ?: return byNames(pkg)
        val type = pkg.contentType(main)?.lowercase()
        // 이진 통합 문서(xlsb — 본문이 `workbook.bin`)는 OPC 겉만 같고 속이 XML 이 아니다. `ms-excel` 이 들었다고
        // xlsx 로 열면 변환기가 XML 을 읽다 넘어져 '깨진 파일' 이 된다 — 다루지 않는 갈래라고 말한다(암호 xlsb
        // 표본을 여는이에 곧바로 넣어 보고 잡았다. 앱에서는 판별기가 `.xlsb` 를 맡지 않아 여기까지 오지 않는다).
        if (type != null && ".binary." in type) return null
        if (type == null && main.endsWith(".bin", ignoreCase = true)) return null
        if (type != null) {
            when {
                "wordprocessingml" in type || "ms-word" in type -> return OoxmlKind.DOCX
                "spreadsheetml" in type || "ms-excel" in type -> return OoxmlKind.XLSX
                "presentationml" in type || "ms-powerpoint" in type -> return OoxmlKind.PPTX
            }
        }
        return when {
            main.startsWith("word/", ignoreCase = true) -> OoxmlKind.DOCX
            main.startsWith("xl/", ignoreCase = true) -> OoxmlKind.XLSX
            main.startsWith("ppt/", ignoreCase = true) -> OoxmlKind.PPTX
            else -> null
        }
    }

    private fun byNames(pkg: OpcPackage): OoxmlKind? = when {
        pkg.has("word/document.xml") -> OoxmlKind.DOCX
        pkg.has("xl/workbook.xml") -> OoxmlKind.XLSX
        pkg.has("ppt/presentation.xml") -> OoxmlKind.PPTX
        else -> null
    }
}

/**
 * 판별기. ZIP 이면 **엔트리 이름**으로(이름을 못 읽는 깨진 ZIP 은 오피스 확장자로), CFB(OLE2)면 **확장자**로 가른다.
 *
 * CFB 는 암호가 걸린 OOXML 일 수도, 이전 형식(`.doc`)일 수도, HWP 5.0(13단계)일 수도 있다.
 * 속을 보려면 CFB 를 읽어야 하는데 판별은 가벼워야 하므로 **오피스 확장자일 때만** 맡는다 —
 * 무엇인지는 여는이([OfficeCfb])가 속을 보고 정한다. `.hwp` 는 맡지 않는다.
 */
object OoxmlProbe : FormatProbe {

    private val DOCX_EXT = setOf("docx", "docm", "dotx", "dotm")
    private val XLSX_EXT = setOf("xlsx", "xlsm", "xltx", "xltm")
    private val PPTX_EXT = setOf("pptx", "pptm", "potx", "potm", "ppsx", "ppsm")
    private val LEGACY_EXT = setOf("doc", "dot", "xls", "xlt", "ppt", "pot", "pps")

    override fun probe(context: io.github.donggi.iroiroviewer.format.ProbeContext): FormatId? {
        val head = context.head
        val ext = context.extension
        if (OoxmlOpener.isZip(head)) {
            // 이름을 못 읽었다 — 중앙 디렉터리가 잘렸거나 깨졌다. 오피스 확장자면 **맡는다**: 맡지 않으면 앱은
            // '다루지 않는 문서' 라고 말하는데, 다루는 형식의 깨진 파일이다. 여는이가 '깨진 파일' 로 끝낸다
            // (EPUB 판별기가 이름을 못 읽을 때 확장자로 맡는 것과 같은 판단. 잘린 docx 표본이 잡았다).
            val names = context.zipEntryNames.value ?: return when (ext) {
                in DOCX_EXT -> FormatId.DOCX
                in XLSX_EXT -> FormatId.XLSX
                in PPTX_EXT -> FormatId.PPTX
                else -> null
            }
            val lower = names.mapTo(HashSet()) { it.lowercase() }
            if ("[content_types].xml" !in lower) return null
            return when {
                "word/document.xml" in lower -> FormatId.DOCX
                "xl/workbook.xml" in lower -> FormatId.XLSX
                "ppt/presentation.xml" in lower -> FormatId.PPTX
                ext in DOCX_EXT -> FormatId.DOCX
                ext in XLSX_EXT -> FormatId.XLSX
                ext in PPTX_EXT -> FormatId.PPTX
                else -> null
            }
        }
        if (OoxmlOpener.isCfb(head)) {
            return when (ext) {
                in DOCX_EXT -> FormatId.DOCX
                in XLSX_EXT -> FormatId.XLSX
                in PPTX_EXT -> FormatId.PPTX
                in LEGACY_EXT -> FormatId.LEGACY_OFFICE
                else -> null
            }
        }
        return null
    }
}
