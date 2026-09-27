package io.github.donggi.iroiroviewer.docview.pdf

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.MemfdSink
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDecryptor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * PDF 를 여는 것. **`DocumentOpener` 계약의 첫 구현이다** — 2단계가 계약만 세워 두고
 * 11단계까지 구현이 하나도 없었다.
 *
 * ## 아카이브 안의 PDF 를 열지 않는다
 *
 * 첫 줄에서 [DocumentSource.asFile] 을 본다. null 이면 거기서 끝난다 — pdfium 은
 * **seekable 한 파일 서술자**를 요구하고(파이프를 주면 `file descriptor not seekable`),
 * 아카이브 엔트리·메모리 바이트에는 그런 것이 없다.
 *
 * 여는 유일한 길은 문서를 통째로 임시 파일에 뽑는 것인데, 그것은 9단계가 만화 쪽을
 * 디스크에 쓰지 않기로 한 판단보다 **더 나쁘다**: 쪽 한 장이 아니라 **문서 전체의 평문
 * 사본**이고, 숨긴 폴더의 문서도 예외가 아니다. 그래서 계약 수준에서 끝낸다 — 이 한 줄이
 * '압축 파일 안의 문서는 풀어서 여세요' 를 코드로 보증한다.
 *
 * ## 암호
 *
 * PDF 의 표준 보안 처리기(명세 7.6.4, R2~R6)는 **공개 명세**다. 그래서 암호를 받아 연다.
 * 공개되지 않은 방식(상업 DRM 처리기)과 암호가 아니라 인증서를 요구하는 공개 키 처리기는
 * 암호를 물어도 소용이 없으므로 [OpenFailure.Encrypted] 로 끝낸다.
 *
 * 1. **먼저 암호 없이** 플랫폼으로 연다. 소유자 암호만 걸린 문서(빈 사용자 암호)는 pdfium 이
 *    스스로 연다 — 거기서 암호를 물으면 사용자는 모르는 암호를 요구받는다.
 * 2. pdfium 이 `SecurityException` 을 내면 **우리가 `/Encrypt` 를 읽어 가른다**
 *    ([PdfDecryptor.inspect]). pdfium 은 '암호가 필요하다' 와 '모르는 처리기다' 를 같은 예외로
 *    말하기 때문이다.
 * 3. 암호를 받으면 API 35 이상은 플랫폼이 직접 받고([PdfEngine.openWithPassword]), 그 아래는
 *    우리가 풀어 **메모리 파일**에 평문 한 벌을 만든 뒤 연다([MemfdSink]). 평문을 저장소에
 *    쓰지 않는다.
 * 4. API 35 에서 플랫폼이 거절해도 **우리 것으로 한 번 더** 해 본다. 우리 쪽은 암호를
 *    바이트로 옮기는 후보가 더 많다(R2~R4 의 CP949 — 한글 암호를 옛 도구가 그렇게 적었다).
 *
 * **이 객체는 암호를 지우지 않는다.** 암호 배열의 주인은 부르는 쪽(`DocViewModel`)이고,
 * 거기서 여는 일이 끝나는 대로 지운다. 여기서 지우면 주인이 모르는 사이에 값이 사라진다.
 */
class PdfOpener(private val password: CharArray? = null) : DocumentOpener {

    override val handles: Set<FormatId> = setOf(FormatId.PDF)

    /**
     * @param onOpen 문서를 **만든 자리에서** 부른다. 시간 상한·취소로 반환값이 버려져도
     *   호출자가 닫을 수 있게 하는 장치다([OpenedDocument] 의 주석).
     */
    override suspend fun open(
        source: DocumentSource,
        progress: ProgressSink,
        onOpen: (OpenedDocument) -> Unit,
    ): OpenOutcome {
        val file = source.asFile()
            ?: return OpenOutcome.Failed(OpenFailure.Unsupported("파일이 아닌 원본"))
        if (!file.isFile) return OpenOutcome.Failed(OpenFailure.Corrupt("파일이 없다"))
        if (!file.canRead()) return OpenOutcome.Failed(OpenFailure.NoPermission("읽을 수 없다"))

        val attempt = if (password == null) openPlain(file) else openLocked(file, password)
        return when (attempt) {
            is Attempt.Failed -> OpenOutcome.Failed(attempt.failure)
            is Attempt.Ok -> {
                val doc = PdfDocument(attempt.opened.renderer, attempt.opened.pageCount)
                // **만들자마자 건넨다.** 아래에서 실패로 끝내더라도 이미 건넸으므로
                // 호출자의 `finally` 가 닫는다 — 여기서 우리도 닫지만 두 번 닫아도 안전하다.
                onOpen(doc)
                progress.report(1, 1)
                if (attempt.opened.pageCount <= 0) {
                    doc.close()
                    OpenOutcome.Failed(OpenFailure.Corrupt("쪽이 하나도 없다"))
                } else {
                    OpenOutcome.Success(doc)
                }
            }
        }
    }

    private sealed interface Attempt {
        data class Ok(val opened: PdfEngine.Opened.Ok) : Attempt
        data class Failed(val failure: OpenFailure) : Attempt
    }

    private fun PdfEngine.Opened.toAttempt(): Attempt = when (this) {
        is PdfEngine.Opened.Ok -> Attempt.Ok(this)
        is PdfEngine.Opened.Error -> Attempt.Failed(PdfFailures.of(cause))
    }

    /**
     * 암호 없이. 실패가 암호 때문이면 그 사정을 우리가 가른다.
     *
     * **플랫폼이 무슨 예외를 던지든 가른다.** 레거시(API 31)는 잠긴 문서를 전부
     * `SecurityException` 으로 알리는데, API 35 의 재구현은 **인증서로 잠긴 문서를
     * `IOException`** 으로 알린다(태블릿 에뮬레이터에서 실측). 예외 종류만 믿으면 그 문서가
     * '깨진 파일' 로 나간다. 그래서 `/Encrypt` 를 우리가 읽어, 잠겨 있으면 잠긴 사정으로
     * 옮기고 잠겨 있지 않으면 플랫폼의 판정을 그대로 둔다.
     */
    private suspend fun openPlain(file: File): Attempt {
        val opened = PdfEngine.open(file)
        if (opened !is PdfEngine.Opened.Error) return opened.toAttempt()
        val platformSaysLocked = opened.cause is SecurityException
        if (!platformSaysLocked && opened.cause !is IOException) return opened.toAttempt()
        val job = currentCoroutineContext()[Job]
        val inspection = try {
            withContext(IroDispatchers.parsing) {
                FileInputStream(file).channel.use {
                    PdfDecryptor.inspect(FileChannelSource(it)) { job?.ensureActive() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // 플랫폼이 '깨졌다' 고 했고 우리도 못 읽는다 — 그 판정이 맞다.
            if (!platformSaysLocked) return opened.toAttempt()
            // pdfium 은 잠겼다고 하는데 우리는 구조를 못 읽는다. 35 이상이면 플랫폼이
            // 암호를 받아 줄 수 있으니 묻고, 그 아래는 받아도 풀 길이 없으니 여기서 끝낸다.
            Iro.d(TAG) { "잠긴 PDF 의 구조를 읽지 못했다: ${e::class.java.simpleName}" }
            return if (Build.VERSION.SDK_INT >= 35) {
                Attempt.Failed(OpenFailure.PasswordRequired("암호가 필요한 문서"))
            } else {
                Attempt.Failed(OpenFailure.Corrupt("잠긴 문서의 구조를 읽지 못했다"))
            }
        }
        Iro.d(TAG) { "잠긴 PDF? $inspection (플랫폼: ${opened.cause::class.java.simpleName})" }
        return when (inspection) {
            // 35 아래에서 상한을 넘는 문서는 **암호를 묻지 않는다.** 맞는 암호를 넣어도 우리가
            // 메모리 파일에 담을 수 없으니, 물으면 쓸모없는 암호를 받는 셈이다.
            is PdfDecryptor.Inspection.NeedsPassword ->
                if (Build.VERSION.SDK_INT < 35 && file.length() > PdfLimits.MAX_DECRYPTED_BYTES) {
                    Attempt.Failed(OpenFailure.TooLarge("복호화 상한을 넘는다"))
                } else {
                    Attempt.Failed(OpenFailure.PasswordRequired("암호가 필요한 문서"))
                }
            // 빈 암호로 풀리는데 pdfium 이 거절했다 — 우리가 빈 암호로 풀어 넘긴다.
            is PdfDecryptor.Inspection.OpenWithoutPassword -> decryptAndOpen(file, CharArray(0))
            is PdfDecryptor.Inspection.Unsupported ->
                Attempt.Failed(OpenFailure.Encrypted("풀 수 없는 보안 처리기"))
            // 잠겨 있지 않다. 플랫폼이 '깨졌다' 고 했다면 그 판정이 맞고, '잠겼다' 고 했는데
            // 우리 눈에 `/Encrypt` 가 없다면 암호를 물을 근거가 없다.
            is PdfDecryptor.Inspection.NotEncrypted ->
                if (platformSaysLocked) {
                    Attempt.Failed(OpenFailure.Encrypted("알 수 없는 방식으로 잠긴 문서"))
                } else {
                    opened.toAttempt()
                }
        }
    }

    /** 사용자가 넣은 암호로. */
    private suspend fun openLocked(file: File, password: CharArray): Attempt {
        if (Build.VERSION.SDK_INT >= 35) {
            val opened = PdfEngine.openWithPassword(file, password)
            if (opened !is PdfEngine.Opened.Error || opened.cause !is SecurityException) {
                return opened.toAttempt()
            }
            Iro.d(TAG) { "플랫폼이 암호를 거절했다. 우리 후보로 한 번 더" }
            // **플랫폼이 거절한 뒤의 실패는 '틀렸다' 다.** 우리 길은 한 번 더 해 보는 것일 뿐이라,
            // 거기서 난 '너무 크다'·'구조를 못 읽는다' 로 끝내면 오타 하나에 암호 창이 사라지고
            // 맞는 암호로는 플랫폼이 열었을 문서가 막다른 실패로 남는다(적대적 검토가 잡았다).
            val ours = decryptAndOpen(file, password)
            return if (ours is Attempt.Ok) {
                ours
            } else {
                Attempt.Failed(OpenFailure.PasswordRequired("암호가 맞지 않다", wrongPassword = true))
            }
        }
        return decryptAndOpen(file, password)
    }

    /**
     * 우리가 풀어 메모리 파일에 담고, 그것을 연다.
     *
     * 원본 크기로 먼저 거른다 — 결과는 원본과 거의 같은 크기이고(스트림은 압축된 채로
     * 옮긴다), 넘을 것이 뻔한 것을 끝까지 풀어 메모리를 채울 이유가 없다. 선언을 믿는 것이
     * 아니라 **실제 파일 크기**이고, 결과도 [PdfDecryptor] 가 쓰는 동안 다시 센다.
     */
    private suspend fun decryptAndOpen(file: File, password: CharArray): Attempt {
        val max = PdfLimits.MAX_DECRYPTED_BYTES
        if (file.length() > max) return Attempt.Failed(OpenFailure.TooLarge("복호화 상한을 넘는다"))
        val job = currentCoroutineContext()[Job]
        // **서술자를 `withContext` 의 반환값으로 넘기지 않는다.** 돌아오는 사이에 취소되면
        // 코루틴이 값을 버리고, 받은 적 없는 서술자는 아무도 닫지 못한다(함정 표의
        // '코루틴 경계 너머로 Closeable 을 돌려주지 마라'). 만든 자리에서 여기 적어 두고,
        // 주인이 바뀌기 전까지는 `finally` 가 닫는다.
        var pending: ParcelFileDescriptor? = null
        try {
            val result = withContext(IroDispatchers.parsing) {
                FileInputStream(file).channel.use { channel ->
                    MemfdSink.create().use { sink ->
                        val r = PdfDecryptor.decrypt(FileChannelSource(channel), password, sink, max) {
                            job?.ensureActive()
                        }
                        Iro.d(TAG) { "복호화: $r, ${sink.position} 바이트" }
                        // 복제본을 받는다. 싱크의 원본 서술자는 `use` 가 닫고, 메모리는 복제본이
                        // 살아 있는 동안 남는다.
                        if (r == PdfDecryptor.Result.Ok) pending = sink.descriptor()
                        r
                    }
                }
            }
            return when (result) {
                PdfDecryptor.Result.Ok -> {
                    val descriptor = pending!!
                    pending = null // 주인이 바뀐다. 이제 닫는 것은 PdfEngine 이다
                    PdfEngine.open(descriptor).toAttempt()
                }
                PdfDecryptor.Result.WrongPassword ->
                    Attempt.Failed(OpenFailure.PasswordRequired("암호가 맞지 않다", wrongPassword = true))
                // 암호를 받았는데 잠겨 있지 않다 — 그냥 연다.
                PdfDecryptor.Result.NotEncrypted -> PdfEngine.open(file).toAttempt()
                is PdfDecryptor.Result.Unsupported ->
                    Attempt.Failed(OpenFailure.Encrypted("풀 수 없는 보안 처리기"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfDecryptor.TooLarge) {
            return Attempt.Failed(OpenFailure.TooLarge("복호화 상한을 넘었다"))
        } catch (e: PdfSyntaxException) {
            Iro.d(TAG) { "잠긴 PDF 의 구조를 읽지 못했다: ${e::class.java.simpleName}" }
            return Attempt.Failed(OpenFailure.Corrupt("잠긴 문서의 구조를 읽지 못했다"))
        } catch (e: ErrnoException) {
            // memfd 를 만들지 못했다(SELinux·메모리). 평문을 디스크에 쓰는 길로 물러서지 않는다.
            Iro.d(TAG) { "메모리 파일을 만들지 못했다: errno ${e.errno}" }
            return Attempt.Failed(OpenFailure.Unsupported("메모리 파일을 만들 수 없다"))
        } catch (e: IOException) {
            return Attempt.Failed(OpenFailure.Io("입출력이 실패했다"))
        } catch (e: OutOfMemoryError) {
            return Attempt.Failed(OpenFailure.TooLarge("메모리가 모자랐다"))
        } finally {
            try {
                pending?.close()
            } catch (_: IOException) {
                // 닫기의 실패로 할 일은 없다.
            }
        }
    }

    private companion object {
        const val TAG = "pdf"
    }
}
