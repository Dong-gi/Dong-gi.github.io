package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.FormatProbe
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/**
 * HWPX(OWPML, KS X 6101)를 연다 — 평문 패키지와 **암호가 걸린 패키지**.
 *
 * ## 암호
 *
 * 암호 HWPX 는 ODF 모양의 매니페스트(`META-INF/manifest.xml`)에 항목마다 암호 정보를 적는다. 푸는 방식은 한컴이
 * Apache-2.0 으로 공개한 OWPML 모델에 있다(`HwpxDecryptor` 의 주석) — 11단계의 '잠근 방식이 공개돼 있으면 연다'
 * 에 든다. 암호 없이 열면 [OpenFailure.PasswordRequired](`wrongPassword = false`), 틀리면 `true`. 풀린 평문은
 * **메모리에만** 있고 다 읽은 뒤 0 으로 덮는다.
 *
 * @param password 사용자가 넣은 암호(없으면 null). **쓰기만 하고 지우지 않는다**(주인은 `DocViewModel`).
 */
class HwpxOpener(
    private val password: CharArray?,
    private val limits: ParseLimits = ParseLimits.DEFAULT,
) : DocumentOpener {

    /** 시험이 조각 나누기·상한을 작은 값으로 태울 때 쓴다. */
    internal var options: HwpxOptions = HwpxOptions()

    override val handles: Set<FormatId> = setOf(FormatId.HWPX)

    override suspend fun open(
        source: DocumentSource,
        progress: ProgressSink,
        onOpen: (OpenedDocument) -> Unit,
    ): OpenOutcome {
        var pkg: HwpxPackage? = null
        // 열쇠 유도는 코루틴 밖의 막히는 계산이다. 시간 상한(`withTimeout`)은 스레드를 인터럽트하지 않고 잡만
        // 취소하므로 잡의 상태를 직접 들여다봐야 멈춘다(`OoxmlOpener` 와 같다).
        val context = currentCoroutineContext()
        return try {
            val head = source.head(4)
            if (!isZip(head)) return OpenOutcome.Failed(OpenFailure.Corrupt("HWPX 가 아니다"))
            val opened = HwpxPackage.open(source, limits)
            pkg = opened
            progress.report(1, 4)
            if (opened.encrypted || opened.encryptionUnsupported != null) {
                opened.encryptionUnsupported?.let {
                    return OpenOutcome.Failed(OpenFailure.Unsupported("다루지 않는 방식으로 잠긴 HWPX"))
                }
                val pw = password
                    ?: return OpenOutcome.Failed(OpenFailure.PasswordRequired("암호가 걸린 HWPX", wrongPassword = false))
                when (opened.unlock(pw) { checkCancelled(context) }) {
                    UnlockResult.Unlocked -> Unit
                    UnlockResult.WrongPassword ->
                        return OpenOutcome.Failed(OpenFailure.PasswordRequired("암호가 맞지 않다", wrongPassword = true))
                    is UnlockResult.Unsupported ->
                        return OpenOutcome.Failed(OpenFailure.Unsupported("다루지 않는 방식으로 잠긴 HWPX"))
                }
            }
            context.ensureActive()
            opened.loadContents { checkCancelled(context) }
            progress.report(2, 4)
            val doc = HwpxDocument.open(opened, limits, progress, options)
            // 문서가 패키지의 주인이 됐다. 이 뒤로는 문서를 닫으면 패키지도 닫힌다.
            pkg = null
            onOpen(doc)
            if (doc.parts.isEmpty()) {
                // `onOpen` 으로 건넨 뒤라 부르는 쪽의 `finally` 가 닫는다(`DocumentOpener` 의 계약).
                return OpenOutcome.Failed(doc.emptyFailure ?: OpenFailure.Corrupt("보여 줄 부분이 없다"))
            }
            progress.report(4, 4)
            OpenOutcome.Success(doc)
        } catch (t: Throwable) {
            OpenOutcome.Failed(t.toOpenFailure())
        } finally {
            pkg?.close()
        }
    }

    /**
     * 막히는 자리에서 부른다. 잡이 취소됐으면 `CancellationException`, 스레드가 인터럽트됐으면
     * `InterruptedIOException`(`Thread.interrupted()` 는 플래그를 지우므로 쓰지 않는다 — 함정 표).
     */
    private fun checkCancelled(context: CoroutineContext) {
        context.ensureActive()
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("취소")
    }

    internal companion object {
        fun isZip(head: ByteArray): Boolean =
            head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
    }
}

/**
 * 판별기. **EPUB 판별기보다 먼저 돈다**(`FormatRegistry`) — HWPX 에도 `mimetype` 과 `META-INF/container.xml` 이
 * 있어 그 이름만 보는 EPUB 판별기가 가로챈다. 그래서 HWPX 만의 표지로 가른다: 저장(무압축)된 첫 항목 `mimetype`
 * 의 내용이 `application/hwp+zip` 이거나, 항목 이름에 `Contents/content.hpf` 가 있다.
 *
 * **가볍게** — 열리는 모든 파일에 대해 돈다. 앞부분 바이트를 먼저 보고, 그것으로 가려지지 않을 때만 ZIP 의 항목
 * 이름(게으르게 공급된다)을 본다. 확장자는 보지 않는다: `.hwpx` 라는 이름의 HTML(K35)이나 HWP 5.0(K36)이 실제로
 * 올라와 있다.
 */
object HwpxProbe : FormatProbe {

    /** ZIP 의 지역 헤더 뒤, 첫 엔트리 이름이 시작하는 자리. */
    private const val NAME_AT = 30

    private const val STORED_HEAD = "mimetype" + HwpxPackage.HWPX_MIME

    override fun probe(context: ProbeContext): FormatId? {
        if (!context.headStartsWith(0x50, 0x4B, 0x03, 0x04)) return null
        val head = context.head
        if (head.size >= NAME_AT + STORED_HEAD.length) {
            val text = String(head, NAME_AT, STORED_HEAD.length, Charsets.ISO_8859_1)
            if (text == STORED_HEAD) return FormatId.HWPX
        }
        val names = context.zipEntryNames.value ?: return null
        return if (names.any { it.equals("Contents/content.hpf", ignoreCase = true) }) FormatId.HWPX else null
    }
}
