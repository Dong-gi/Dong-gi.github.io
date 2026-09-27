package io.github.donggi.iroiroviewer.docview.pdf.crypt

import io.github.donggi.iroiroviewer.safety.PdfLimits
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.fail

/**
 * **실세계 PDF 말뭉치**를 JVM 이 답할 수 있는 데까지 돈다.
 *
 * pdfium 은 안드로이드에서만 돌므로 여기서 보는 것은 **앱이 pdfium 앞에서 스스로 하는 일** 둘이다 —
 * 잠긴 문서를 가르는 것(`PdfDecryptor.inspect`, 화면이 암호를 물을지 정한다)과 푸는 것
 * (`PdfDecryptor.decrypt`, API 34 이하에서 pdfium 에 넘길 평문을 만든다). 앱과 같이 **파일 채널**에서 읽는다.
 * 그리는 일은 계측 시험(`PdfCorpusDeviceTest`)이 한다.
 *
 * ## 오라클이 우리가 아니다
 *
 * `oracle/<ID>.json` 은 qpdf(pikepdf)가 **같은 암호로 연** 문서에서 뽑았다 — 스트림마다 복호화한 날것
 * 바이트의 SHA-256(`read_raw_bytes`), 쪽마다 내용 스트림의 객체 번호, 쪽 수. 우리가 쓴 평문 PDF 에서
 * 같은 번호의 스트림을 읽어 **바이트로** 견준다. 한 바이트만 어긋나게 풀어도 SHA-256 이 다르다.
 * 우리 결과는 `out/<ID>.dec.pdf` 로 남기고, `samples-local/corpus/tools/` 의 `verify_pdf_out.py` 가 qpdf 와 엄격한
 * pypdf 로 다시 읽어 경고와 쪽마다의 글을 견준다(보고서의 표).
 *
 * ## 무엇을 기대하는가 — 파일마다 적었다
 *
 * 표본 조사의 초안(`manifest.json` 의 `expected`)을 반증조의 교정과 오라클을 읽고 다듬은 것이 [expected] 다.
 * 말뭉치에 새 PDF 가 들어왔는데 표에 없으면 시험이 실패한다.
 */
class PdfCorpusTest {

    private sealed interface Expect {
        /** 잠기지 않았다. 우리 파서도 쪽 수를 오라클과 같이 읽어야 한다(같은 구조의 잠긴 파일을 풀 수 있는가). */
        data object Plain : Expect

        /** 사용자 암호가 걸렸다. 없으면 묻고, 틀리면 틀렸다고 하고, 맞으면 풀린다(소유자 암호로도). */
        data class Password(val user: String, val owner: String, val alsoOpens: List<String> = emptyList()) : Expect

        /** 소유자 암호만 걸렸다. 묻지 않고 열린다(빈 사용자 암호). 소유자 암호로도 열린다. */
        data class OwnerOnly(val owner: String) : Expect

        /** 인증서(공개 키 처리기). 물어도 소용이 없다 — `Unsupported`. */
        data object Certificate : Expect
    }

    private val expected: Map<String, Expect> = mapOf(
        "PDF-01" to Expect.Plain, "PDF-02" to Expect.Plain, "PDF-03" to Expect.Plain, "PDF-04" to Expect.Plain,
        "PDF-05" to Expect.Plain, "PDF-06" to Expect.Plain, "PDF-09" to Expect.Plain, "PDF-10" to Expect.Plain,
        "PDF-11" to Expect.Plain, "PDF-12" to Expect.Plain, "PDF-13" to Expect.Plain, "PDF-14" to Expect.Plain,
        "PDF-15" to Expect.Plain, "PDF-16" to Expect.Plain, "PDF-25" to Expect.Plain, "PDF-26" to Expect.Plain,
        // LibreOffice 의 no-eof.pdf. 초안은 '깨진 파일' 이었지만 qpdf·pypdf 가 경고 없이 1쪽으로 연다 —
        // LibreOffice 자기 토크나이저가 돌던 입력일 뿐이다. 잠기지 않은 멀쩡한 파일로 본다.
        "PDF-24" to Expect.Plain,
        // Acrobat 5.0 이 잠근 R3(RC4-128). qpdf 시험의 enc-R3,V2,U=view,O=master.
        "PDF-17" to Expect.Password("view", "master"),
        // Acrobat XI 가 잠근 R6(AES-256), 선형화 + 객체 스트림.
        "PDF-18" to Expect.Password("view", "master"),
        // Acrobat XI 의 소유자 암호만(R6). 초안은 'password' 였다.
        "PDF-19" to Expect.OwnerOnly("master"),
        // Acrobat 이 만든 PDFBox 표본. 128비트는 AES 가 아니라 R3 RC4-128 이었다(pikepdf 로 확인).
        "PDF-20" to Expect.Password("user", "owner"),
        // LibreOffice 가 잠근 R3.
        "PDF-21" to Expect.Password("openpassword", "permissionpassword"),
        // pdfium 의 encrypted_hello_world_r6: 사용자 'hôtel', 소유자 'âge'. 조합형(NFD)으로 친 것도 열려야 한다
        // (R6 은 SASLprep — NFKC 가 조합한다).
        "PDF-22" to Expect.Password("hôtel", "âge", alsoOpens = listOf("ho\u0302tel", "a\u0302ge")),
        // PDFBox AESkeylength128 — /Adobe.PubSec.
        "PDF-23" to Expect.Certificate,
        // Acrobat XI 의 첨부만 잠근 R6(/EFF, AuthEvent /EFOpen). 사용자·소유자 암호가 같다('attachment').
        // Acrobat 은 묻지 않고 여는데 우리는 묻는다 — 보고서의 '한계'.
        "PDF-28" to Expect.Password("attachment", "attachment"),
    )

    private val perFileLimitMs = 120_000L
    private val report = StringBuilder()

    @Test
    fun 말뭉치의_PDF_를_가르고_푼다() {
        assumeTrue("samples-local/corpus 가 없다", Corpus.dir != null)
        val items = Corpus.manifest().filter { (it["file"] as String).endsWith(".pdf") }
        assumeTrue("말뭉치에 PDF 가 없다", items.isNotEmpty())
        report.append("id\texpect\tinspect\tour_pages\toracle_pages\tpasswords\tstreams_equal\trebuilt\tms\n")
        val problems = ArrayList<String>()
        for (item in items) {
            val id = item["id"] as String
            val expect = expected[id]
            if (expect == null) {
                problems.add("$id: 기대값 표에 없다 — 먼저 분류하라")
                continue
            }
            val file = File(Corpus.dir, item["file"] as String)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit(Callable { runOne(id, expect, file, Corpus.oracle(id)) })
                problems.addAll(future.get(perFileLimitMs, TimeUnit.MILLISECONDS))
            } catch (e: TimeoutException) {
                problems.add("$id: 시간 상한(${perFileLimitMs}ms)을 넘었다")
            } catch (e: ExecutionException) {
                problems.add("$id: 예외가 새어 나왔다 — ${e.cause}")
            } finally {
                pool.shutdownNow()
            }
        }
        Corpus.outFile("pdf-corpus.tsv")?.writeText(report.toString(), Charsets.UTF_8)
        println(report)
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /**
     * 말뭉치에서 **망가진 파일을 만들어** 가르고 푼다 — 반으로 자른 것, 앞 1 KiB 만 남긴 것, 가운데 4 KiB 를
     * 뒤섞은 것. 결과는 무엇이든 되지만 `PdfOpener` 가 받는 예외(`PdfSyntaxException`·`TooLarge` — 둘 다
     * 입출력 계열이거나 따로 받는다) 말고는 새지 않아야 하고, 시간 안에 끝나야 한다. 잠긴 파일은 기록된
     * 암호로도 푼다(복호화 경로가 망가진 스트림을 만나는 자리).
     */
    @Test
    fun 망가뜨린_말뭉치에서_멈추지_않는다() {
        assumeTrue("samples-local/corpus 가 없다", Corpus.dir != null)
        val items = Corpus.manifest().filter { (it["file"] as String).endsWith(".pdf") }
        assumeTrue("말뭉치에 PDF 가 없다", items.isNotEmpty())
        val problems = ArrayList<String>()
        for (item in items) {
            val id = item["id"] as String
            val bytes = File(Corpus.dir, item["file"] as String).readBytes()
            val password = when (val e = expected[id]) {
                is Expect.Password -> e.user
                is Expect.OwnerOnly -> ""
                else -> "x"
            }
            val variants = listOf(
                "반" to bytes.copyOf(bytes.size / 2),
                "1KiB" to bytes.copyOf(minOf(bytes.size, 1024)),
                "뒤섞음" to bytes.copyOf().also { b ->
                    val mid = b.size / 2
                    for (k in mid until minOf(b.size, mid + 4096)) b[k] = (b[k].toInt() xor 0x5A).toByte()
                },
            )
            for ((name, data) in variants) {
                val pool = Executors.newSingleThreadExecutor()
                try {
                    val future = pool.submit(Callable {
                        tolerate { PdfDecryptor.inspect(ByteArraySource(data)) }
                        tolerate {
                            PdfDecryptor.decrypt(ByteArraySource(data), password.toCharArray(), MemorySink(), PdfLimits.MAX_DECRYPTED_BYTES)
                        }
                    })
                    future.get(perFileLimitMs, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    problems.add("$id($name): 시간 상한을 넘었다")
                } catch (e: ExecutionException) {
                    problems.add("$id($name): 예외가 새어 나왔다 — ${e.cause}")
                } finally {
                    pool.shutdownNow()
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /** `PdfOpener` 가 받는 두 예외는 삼킨다. 그 밖의 것은 그대로 던진다. */
    private inline fun tolerate(block: () -> Unit) {
        try {
            block()
        } catch (e: PdfSyntaxException) {
            // 받는다 — '깨졌다'.
        } catch (e: PdfDecryptor.TooLarge) {
            // 받는다 — '너무 크다'.
        }
    }

    private fun runOne(id: String, expect: Expect, file: File, oracle: Map<String, Any?>?): List<String> {
        val problems = ArrayList<String>()
        val started = System.nanoTime()
        val oraclePages = (oracle?.get("pages") as Long?)?.toInt()

        // ---- 암호 없이 들여다보기 — 앱이 pdfium 의 거절 뒤에 부르는 그 함수 ------------------------
        val inspection: Any = try {
            channel(file) { PdfDecryptor.inspect(it) }
        } catch (e: PdfSyntaxException) {
            e
        }
        val inspectName = if (inspection is PdfSyntaxException) "PdfSyntaxException" else inspection.toString()
        val wantInspect = when (expect) {
            Expect.Plain -> PdfDecryptor.Inspection.NotEncrypted
            is Expect.Password -> PdfDecryptor.Inspection.NeedsPassword
            is Expect.OwnerOnly -> PdfDecryptor.Inspection.OpenWithoutPassword
            Expect.Certificate -> null
        }
        when {
            expect == Expect.Certificate ->
                if (inspection !is PdfDecryptor.Inspection.Unsupported) problems.add("$id: 인증서인데 $inspectName")
            inspection != wantInspect -> problems.add("$id: 들여다보기 $inspectName ≠ $wantInspect")
        }

        var ourPages: Int? = null
        var rebuilt: Boolean? = null
        val passwords = ArrayList<String>()
        var streams = ""
        when (expect) {
            Expect.Plain -> {
                // 잠기지 않은 파일에서 우리 파서가 뼈대를 옳게 읽는가 — 같은 구조(선형화·xref 스트림·증분
                // 저장·객체 스트림)로 잠긴 파일이면 이 파서가 풀어야 한다.
                try {
                    channel(file) { src ->
                        val probe = Probe(PdfFile.open(src), plainResolver = true)
                        ourPages = probe.pages().size
                        rebuilt = probe.file.rebuilt
                    }
                } catch (e: PdfSyntaxException) {
                    problems.add("$id: 우리 파서가 뼈대를 읽지 못했다 — ${e.message}")
                }
                if (oraclePages != null && ourPages != oraclePages) problems.add("$id: 쪽 수 $ourPages ≠ 오라클 $oraclePages")
            }

            Expect.Certificate -> {
                val r = decrypt(file, "anything").first
                passwords.add("anything=$r")
                if (r !is PdfDecryptor.Result.Unsupported) problems.add("$id: 인증서 문서를 푼다고 한다 — $r")
            }

            is Expect.Password, is Expect.OwnerOnly -> {
                val good = when (expect) {
                    is Expect.Password -> listOf(expect.user, expect.owner) + expect.alsoOpens
                    is Expect.OwnerOnly -> listOf("", expect.owner)
                    else -> emptyList()
                }
                val bad = when (expect) {
                    is Expect.Password -> listOf("", expect.user + "x", expect.user.uppercase())
                    else -> listOf("wrong-password")
                }.filter { it !in good }
                for (pw in bad) {
                    val r = decrypt(file, pw).first
                    passwords.add("'${pw}'=${r.javaClass.simpleName}")
                    if (r != PdfDecryptor.Result.WrongPassword) problems.add("$id: 틀린 암호 '$pw' 에 $r")
                }
                var equal = 0
                var total = 0
                for ((n, pw) in good.withIndex()) {
                    val (r, out) = decrypt(file, pw)
                    passwords.add("'${pw}'=${r.javaClass.simpleName}")
                    if (r != PdfDecryptor.Result.Ok) {
                        problems.add("$id: 맞는 암호 '$pw' 에 $r")
                        continue
                    }
                    if (n == 0) Corpus.outFile("$id.dec.pdf")?.writeBytes(out)
                    val (e, t, issues) = compareWithOracle(id, out, oracle)
                    equal = e
                    total = t
                    problems.addAll(issues)
                    val probe = Probe(PdfFile.open(ByteArraySource(out)), plainResolver = true)
                    ourPages = probe.pages().size
                    rebuilt = probe.file.rebuilt
                }
                streams = "$equal/$total"
            }
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        report.append(
            listOf(
                id, expect.javaClass.simpleName, inspectName, ourPages?.toString().orEmpty(),
                oraclePages?.toString().orEmpty(), passwords.joinToString(" "), streams,
                rebuilt?.toString().orEmpty(), ms.toString(),
            ).joinToString("\t")
        ).append('\n')
        return problems
    }

    /**
     * 우리 평문 PDF 를 오라클과 견준다 — 스트림마다 날것 바이트의 SHA-256, 쪽 수, 쪽마다 내용 스트림 번호.
     * 그리고 결과의 xref 가 **다시 세우지 않고** 읽혀야 한다(우리 리더가 관대해서 통과하는 것이 아니다).
     */
    private fun compareWithOracle(id: String, out: ByteArray, oracle: Map<String, Any?>?): Triple<Int, Int, List<String>> {
        val issues = ArrayList<String>()
        val probe = Probe(PdfFile.open(ByteArraySource(out)), plainResolver = true)
        if (probe.file.rebuilt) issues.add("$id: 결과의 xref 가 틀려 다시 세워야 했다")
        if (probe.file.trailer["Encrypt"] != null) issues.add("$id: 결과에 /Encrypt 가 남았다")
        if (oracle == null) return Triple(0, 0, issues)
        val pages = probe.pages()
        val want = (oracle["pages"] as Long).toInt()
        if (pages.size != want) issues.add("$id: 결과의 쪽 수 ${pages.size} ≠ 오라클 $want")
        @Suppress("UNCHECKED_CAST")
        val contents = oracle["page_contents_objs"] as List<List<Long>>?
        contents?.forEachIndexed { i, nums ->
            val ours = probe.contentNumbers(pages.getOrNull(i))
            if (ours != nums.map { it.toInt() }) issues.add("$id: ${i + 1}쪽 내용 스트림 $ours ≠ 오라클 $nums")
        }
        @Suppress("UNCHECKED_CAST")
        val hashes = oracle["stream_raw_sha256"] as Map<String, String>? ?: emptyMap()
        var equal = 0
        for ((num, sha) in hashes) {
            val got = probe.rawStream(num.toInt())?.let { sha256(it) }
            if (got == sha) equal++ else issues.add("$id: $num 번 스트림이 qpdf 가 푼 것과 다르다")
        }
        return Triple(equal, hashes.size, issues)
    }

    private fun decrypt(file: File, password: String): Pair<PdfDecryptor.Result, ByteArray> = channel(file) { src ->
        val sink = MemorySink()
        val r = PdfDecryptor.decrypt(src, password.toCharArray(), sink, PdfLimits.MAX_DECRYPTED_BYTES)
        r to sink.toByteArray()
    }

    private fun <T> channel(file: File, block: (PdfSource) -> T): T =
        FileInputStream(file).channel.use { block(FileChannelSource(it)) }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /**
     * 뼈대를 따라 읽는 도구. **잠기지 않은 파일**(원본 또는 우리 결과)에만 쓴다 — 객체 스트림을 그대로 푼다.
     */
    private class Probe(val file: PdfFile, private val plainResolver: Boolean) {
        private val objStreams = HashMap<Int, PdfDecryptor.ObjStm?>()

        fun resolve(o: PdfObj?): PdfObj? {
            var v = o
            repeat(16) {
                val r = v as? PdfRef ?: return v
                v = when (val e = file.entries[r.num]) {
                    is PdfFile.InFile -> file.readAt(e.offset)?.takeIf { it.num == r.num }?.value
                    is PdfFile.InStream -> objStm(e.stream)?.get(e.index)
                    else -> null
                }
            }
            return v
        }

        private fun objStm(num: Int): PdfDecryptor.ObjStm? = objStreams.getOrPut(num) {
            if (!plainResolver) return@getOrPut null
            val e = file.entries[num] as? PdfFile.InFile ?: return@getOrPut null
            val o = file.readAt(e.offset) ?: return@getOrPut null
            if (!o.isStream) return@getOrPut null
            PdfDecryptor.ObjStm.parse(o.dict!!, file.readBytes(o.dataStart, file.streamLength(o) { resolve(it) }.toInt()))
        }

        /** 쪽 나무의 잎. 순환은 방문 집합이 끊는다. */
        fun pages(): List<PdfDict> {
            val catalog = resolve(file.trailer["Root"]) as? PdfDict ?: return emptyList()
            val out = ArrayList<PdfDict>()
            val seen = HashSet<Int>()
            fun walk(node: PdfObj?, depth: Int) {
                if (depth > 64) return
                if (node is PdfRef && !seen.add(node.num)) return
                val d = resolve(node) as? PdfDict ?: return
                val kids = resolve(d["Kids"]) as? PdfArray
                if (kids == null || d.name("Type") == "Page") {
                    out.add(d)
                    return
                }
                for (k in kids.items) walk(k, depth + 1)
            }
            walk(catalog["Pages"], 0)
            return out
        }

        fun contentNumbers(page: PdfDict?): List<Int> = when (val c = page?.get("Contents")) {
            is PdfRef -> {
                // `/Contents 5 0 R` 가 배열을 가리킬 수도 있다.
                when (val v = resolve(c)) {
                    is PdfArray -> v.items.mapNotNull { (it as? PdfRef)?.num }
                    else -> listOf(c.num)
                }
            }
            is PdfArray -> c.items.mapNotNull { (it as? PdfRef)?.num }
            else -> emptyList()
        }

        fun rawStream(num: Int): ByteArray? {
            val e = file.entries[num] as? PdfFile.InFile ?: return null
            val o = file.readAt(e.offset) ?: return null
            if (!o.isStream) return null
            return file.readBytes(o.dataStart, file.streamLength(o) { resolve(it) }.toInt())
        }
    }
}
