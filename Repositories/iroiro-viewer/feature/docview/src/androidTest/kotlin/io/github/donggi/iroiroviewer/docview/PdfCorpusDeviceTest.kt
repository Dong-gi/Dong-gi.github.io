package io.github.donggi.iroiroviewer.docview

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfEngine
import io.github.donggi.iroiroviewer.docview.pdf.PdfOpener
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * **실세계 PDF 말뭉치**를 실제 pdfium 으로 연다 — 앱이 쓰는 `PdfOpener` 그대로, 기록된 암호로.
 *
 * ## 말뭉치를 기기에 올리는 법(말뭉치는 커밋하지 않는다)
 *
 * ```
 * adb push samples-local/corpus/manifest.json /sdcard/Download/corpus/manifest.json
 * adb push samples-local/corpus/oracle /sdcard/Download/corpus/      (oracle/PDF-*.json 이 필요하다)
 * adb push samples-local/corpus/PDF-01.pdf /sdcard/Download/corpus/  (PDF 마다)
 * ```
 *
 * 올리지 않았으면 `Assume` 으로 건너뛴다. 이름이 전부 ASCII 다(함정 표 — 비ASCII 이름의 `adb push` 는 멈춘다).
 *
 * ## 셸로 꺼낸다
 *
 * 시험 APK 는 `/sdcard/Download` 에 셸이 올린 파일을 읽을 권한이 없다(범위 저장소 — PDF 는 미디어가 아니다).
 * 그래서 `UiAutomation.executeShellCommand("cat …")` 로 셸이 읽게 하고, 받은 바이트를 앱 전용 폴더에
 * 쓴다. pdfium 은 파일 서술자를 요구한다.
 *
 * ## 무엇을 보는가
 *
 * 파일마다 기대값([expected], JVM 의 `PdfCorpusTest` 와 같은 표)대로 — 잠기지 않은 것은 열리고, 사용자 암호가
 * 걸린 것은 암호 없이 `PasswordRequired(wrongPassword=false)`·틀린 암호에 `PasswordRequired(true)`·맞는 암호
 * (사용자·소유자 둘 다)로 열리고, 소유자 암호만 걸린 것은 묻지 않고 열리고, 인증서는 `Encrypted` 다.
 * 열린 것은 **쪽 수가 qpdf 의 쪽 수와 같아야 하고**(`oracle/<ID>.json` 의 `pages`), 첫 쪽·가운데 쪽
 * (`쪽 수 / 2`)·마지막 쪽을 그려 **잉크가 있어야 한다** — 오라클이 그 쪽에 글이나 그림이 있다고 한 경우만
 * (책에는 원래 빈 쪽이 있다). 여는 데 60초, 쪽 하나를 그리는 데 30초를 넘으면 실패다.
 *
 * 두 기기에서 길이 다르다 — API 31 은 우리 복호화기 + 메모리 파일, API 35 는 플랫폼이 암호를 받는다.
 * 실패를 한 번에 다 보려고 파일마다 멈추지 않고 모아서 마지막에 실패한다.
 */
class PdfCorpusDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var work: File

    private sealed interface Expect {
        data object Plain : Expect
        data class Password(val user: String, val owner: String, val alsoOpens: List<String> = emptyList()) : Expect
        data class OwnerOnly(val owner: String) : Expect
        data object Certificate : Expect

        /**
         * 첨부만 잠근 문서(`/EFF`, AuthEvent `/EFOpen`). 명세상 여는 데 암호가 필요 없지만 pdfium 이 묻는지는
         * 확인하지 못했다 — 암호 없이 **열리거나** `PasswordRequired(false)` 면 된다. 맞는 암호로는 열려야 한다.
         */
        data class AttachmentsOnly(val user: String) : Expect
    }

    /** JVM 의 `PdfCorpusTest.expected` 와 같은 표(근거는 거기 주석). */
    private val expected: Map<String, Expect> = mapOf(
        "PDF-01" to Expect.Plain, "PDF-02" to Expect.Plain, "PDF-03" to Expect.Plain, "PDF-04" to Expect.Plain,
        "PDF-05" to Expect.Plain, "PDF-06" to Expect.Plain, "PDF-09" to Expect.Plain, "PDF-10" to Expect.Plain,
        "PDF-11" to Expect.Plain, "PDF-12" to Expect.Plain, "PDF-13" to Expect.Plain, "PDF-14" to Expect.Plain,
        "PDF-15" to Expect.Plain, "PDF-16" to Expect.Plain, "PDF-24" to Expect.Plain, "PDF-25" to Expect.Plain,
        "PDF-26" to Expect.Plain,
        "PDF-17" to Expect.Password("view", "master"),
        "PDF-18" to Expect.Password("view", "master"),
        "PDF-19" to Expect.OwnerOnly("master"),
        "PDF-20" to Expect.Password("user", "owner"),
        "PDF-21" to Expect.Password("openpassword", "permissionpassword"),
        "PDF-22" to Expect.Password("hôtel", "âge", alsoOpens = listOf("ho\u0302tel")),
        "PDF-23" to Expect.Certificate,
        "PDF-28" to Expect.AttachmentsOnly("attachment"),
    )

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "pdfcorpus-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    @Test
    fun 말뭉치의_PDF_가_기대대로_열리고_그려진다() {
        val manifestText = shellText("$ROOT/manifest.json")
        assumeTrue("$ROOT/manifest.json 이 없다 — 말뭉치를 올리지 않았다", manifestText.isNotBlank())
        val manifest = JSONArray(manifestText)
        val problems = ArrayList<String>()
        var seen = 0
        for (k in 0 until manifest.length()) {
            val item = manifest.getJSONObject(k)
            val name = item.getString("file")
            if (!name.endsWith(".pdf")) continue
            val id = item.getString("id")
            val expect = expected[id]
            if (expect == null) {
                problems.add("$id: 기대값 표에 없다 — 먼저 분류하라")
                continue
            }
            val local = File(work, name)
            if (!shellCopy("$ROOT/$name", local) || local.length() == 0L) {
                Log.i(TAG, "$id: 기기에 없다 — 건너뛴다")
                continue
            }
            val oracleText = shellText("$ROOT/oracle/$id.json")
            val oracle = if (oracleText.isBlank()) null else JSONObject(oracleText)
            seen++
            val before = problems.size
            // **오라클 없이 통과시키지 않는다.** 없으면 쪽 수도 잉크도 견주지 못한 채 '열렸다' 만으로 통과한다
            // (검토가 잡았다) — `oracle/` 을 함께 올리지 않은 것이다.
            if (oracle == null) problems.add("$id: $ROOT/oracle/$id.json 이 없다 — 쪽 수·잉크를 견줄 수 없다")
            try {
                check(id, expect, local, oracle, problems)
            } catch (t: Throwable) {
                problems.add("$id: 예외가 새어 나왔다 — $t")
            } finally {
                local.delete()
            }
            Log.i(TAG, "$id: ${if (problems.size == before) "통과" else problems.subList(before, problems.size)}")
        }
        assumeTrue("기기에 올라온 PDF 가 없다", seen > 0)
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    private fun check(id: String, expect: Expect, file: File, oracle: JSONObject?, problems: MutableList<String>) {
        when (expect) {
            Expect.Plain -> openAndDraw(id, file, null, oracle, problems)
            is Expect.OwnerOnly -> {
                // 여기서 암호를 물으면 사용자는 있지도 않은 암호를 요구받는다.
                openAndDraw(id, file, null, oracle, problems)
                openAndDraw(id, file, expect.owner, oracle, problems, drawPages = false)
            }
            is Expect.Password -> {
                val first = failureOf(file, null)
                if (first !is OpenFailure.PasswordRequired || first.wrongPassword) {
                    problems.add("$id: 암호 없이 PasswordRequired(false) 여야 한다 — $first")
                }
                val wrong = failureOf(file, expect.user + "x")
                if (wrong !is OpenFailure.PasswordRequired || !wrong.wrongPassword) {
                    problems.add("$id: 틀린 암호에 PasswordRequired(true) 여야 한다 — $wrong")
                }
                openAndDraw(id, file, expect.user, oracle, problems)
                openAndDraw(id, file, expect.owner, oracle, problems, drawPages = false)
                for (pw in expect.alsoOpens) openAndDraw(id, file, pw, oracle, problems, drawPages = false)
            }
            is Expect.AttachmentsOnly -> {
                val (doc, failure) = open(file, null)
                doc?.close()
                val ok = doc != null || (failure is OpenFailure.PasswordRequired && !failure.wrongPassword)
                if (!ok) problems.add("$id: 암호 없이 열리거나 PasswordRequired(false) 여야 한다 — $failure")
                Log.i(TAG, "$id: 암호 없이 ${if (doc != null) "열렸다" else "$failure"}")
                openAndDraw(id, file, expect.user, oracle, problems)
            }
            Expect.Certificate -> {
                // 물어도 소용이 없다 — 암호 창을 띄우면 안 된다.
                val failure = failureOf(file, null)
                if (failure !is OpenFailure.Encrypted) problems.add("$id: Encrypted 여야 한다 — $failure")
            }
        }
    }

    /** 열어서 쪽 수를 견주고 첫·가운데·마지막 쪽을 그린다. */
    private fun openAndDraw(
        id: String,
        file: File,
        password: String?,
        oracle: JSONObject?,
        problems: MutableList<String>,
        drawPages: Boolean = true,
    ) {
        val label = if (password == null) "$id(암호 없이)" else "$id('$password')"
        val started = System.nanoTime()
        val (doc, failure) = open(file, password)
        if (doc == null) {
            problems.add("$label: 열리지 않았다 — $failure")
            return
        }
        doc.use { d ->
            val openMs = (System.nanoTime() - started) / 1_000_000
            if (openMs > OPEN_LIMIT_MS) problems.add("$label: 여는 데 ${openMs}ms")
            val want = oracle?.optInt("pages", -1) ?: -1
            if (want >= 0 && d.pageCount != want) problems.add("$label: 쪽 수 ${d.pageCount} ≠ qpdf $want")
            if (!drawPages) return
            val probe = oracle?.optJSONObject("probe_pages")
            for (ordinal in listOf(0, d.pageCount / 2, d.pageCount - 1).distinct()) {
                val t0 = System.nanoTime()
                val bitmap = draw(d, ordinal)
                val ms = (System.nanoTime() - t0) / 1_000_000
                if (ms > RENDER_LIMIT_MS) problems.add("$label: ${ordinal + 1}쪽을 그리는 데 ${ms}ms")
                if (bitmap == null) {
                    problems.add("$label: ${ordinal + 1}쪽을 그리지 못했다")
                    continue
                }
                val hint = probe?.optJSONObject(ordinal.toString())
                val wantInk = hint != null && (hint.optInt("text_chars") > 0 || hint.optBoolean("has_xobject"))
                val ink = inkRatio(bitmap)
                Log.i(TAG, "$label ${ordinal + 1}/${d.pageCount}쪽 ${bitmap.width}x${bitmap.height} 잉크 $ink ${ms}ms")
                if (wantInk && ink <= 0.0) problems.add("$label: ${ordinal + 1}쪽이 비었다(오라클은 글·그림이 있다고 한다)")
            }
        }
    }

    /** 연다. 성공이면 문서를(부르는 쪽이 닫는다), 실패면 이유를 준다. */
    private fun open(file: File, password: String?): Pair<PdfDocument?, OpenFailure?> = runBlocking {
        var opened: OpenedDocument? = null
        try {
            val outcome = withTimeout(OPEN_LIMIT_MS) {
                PdfOpener(password?.toCharArray()).open(FileDocumentSource(file)) { opened = it }
            }
            when (outcome) {
                is OpenOutcome.Success -> {
                    opened = null
                    (outcome.document as PdfDocument) to null
                }
                is OpenOutcome.Failed -> null to outcome.failure
            }
        } finally {
            opened?.close()
        }
    }

    private fun failureOf(file: File, password: String?): OpenFailure? {
        val (doc, failure) = open(file, password)
        doc?.close()
        return failure
    }

    /** 쪽 하나를 긴 변 1,500 화소 안으로(작은 쪽은 1pt = 1px) 그린다. */
    private fun draw(doc: PdfDocument, ordinal: Int): Bitmap? = runBlocking {
        val size = doc.sizeOf(ordinal) ?: return@runBlocking null
        val scale = minOf(1.0, 1500.0 / maxOf(size[0], size[1]).coerceAtLeast(1))
        val w = (size[0] * scale).toInt().coerceAtLeast(1)
        val h = (size[1] * scale).toInt().coerceAtLeast(1)
        doc.render(ordinal, PdfEngine.Spec(w, h, scale))
    }

    /**
     * 흰색이 아닌 화소의 비율. **화소를 전부 본다** — 격자로 표본을 뜨면 글자 세 개짜리 쪽(말뭉치에 있다)의
     * 가는 획이 격자 사이로 빠진다. 종이의 흰색은 우리가 칠한 것이라(`eraseColor`) 정확히 `WHITE` 다.
     */
    private fun inkRatio(bitmap: Bitmap): Double {
        val row = IntArray(bitmap.width)
        var ink = 0L
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (p in row) if (p != Color.WHITE) ink++
        }
        val total = bitmap.width.toLong() * bitmap.height
        return if (total == 0L) 0.0 else ink.toDouble() / total
    }

    // ---- 셸로 꺼내기 ------------------------------------------------------------------

    private fun shellText(path: String): String {
        val out = java.io.ByteArrayOutputStream()
        shellStream(path) { it.copyTo(out) }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun shellCopy(path: String, dest: File): Boolean {
        FileOutputStream(dest).use { out -> shellStream(path) { it.copyTo(out) } }
        return dest.isFile
    }

    private fun shellStream(path: String, block: (java.io.InputStream) -> Unit) {
        val pfd = instrumentation.uiAutomation.executeShellCommand("cat $path")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use(block)
    }

    private companion object {
        const val TAG = "PdfCorpus"
        const val ROOT = "/sdcard/Download/corpus"
        const val OPEN_LIMIT_MS = 60_000L
        const val RENDER_LIMIT_MS = 30_000L
    }
}
