package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * PDF 쪽 뽑기·합치기를 파일 작업 큐에 잇는 **이음매**([ExtractSupport]·[CropSupport] 와 같은 모양).
 *
 * 새 PDF 를 쓰는 일은 사용자 저장소에 새 파일을 만드는 일이라 이 큐를 지난다(같은 폴더를 두 작업이 동시에 만지지 않게, 진행·취소·
 * 포그라운드 서비스·결과 보고를 그대로 얻게). 그런데 PDF 를 읽는 해석기(xref·객체 스트림·암호)는 `feature:docview` 에 있고, 의존 표는
 * `core:io` 가 그것을 보는 것을 허락하지 않는다. 그래서 **PDF 를 짓는 일**([Engine])은 `feature:docview` 가 하고 `app` 이 꽂으며,
 * **파일을 쓰는 일**(임시본·공간 지킴·fsync·`O_EXCL` 로 놓기·미디어 색인 — [PdfPageSaver])은 여기서 한다 — 사용자 파일을 쓰는 코드는
 * 이 모듈에만 둔다.
 */
object PdfPagesSupport {

    /**
     * 원본 하나와 고른 쪽.
     *
     * @param pages 0부터의 쪽 번호, 오름차순·겹침 없이. null 이면 전부.
     * @param expectedCount 화면(플랫폼)이 센 쪽 수 — 엔진이 센 것과 다르면 고른 번호가 다른 쪽일 수 있어 거절한다. null 이면 엔진이
     *   플랫폼에 스스로 묻는다(합치기).
     * @param password 사용자 암호 — 그 문서를 열 때 넣은 세션 암호([SessionPasswords])의 **사본**. 요청이 들고, 쓴 뒤([PdfPageSaver])나
     *   돌지 않고 버려질 때(큐의 `settle`) 0 으로 덮는다. 메모리에만 있다 — 인텐트·저장 상태·디스크에 싣지 않는다.
     */
    class Part(val path: String, val pages: List<Int>?, val expectedCount: Int? = null, val password: CharArray? = null) {
        // 암호·경로를 `toString`(로그·예외)에 싣지 않는다.
        override fun toString(): String = "Part(pages=${pages?.size ?: "all"}, locked=${password != null})"
    }

    /**
     * 잠금 — 없음, 소유자 암호만(빈 사용자 암호로 풀린다), 사용자 암호(세션 암호로 푼다 — 없으면 '암호를 먼저 넣어 여세요'), 암호로는 열 수
     * 없는 방식(인증서·전용 처리기 — 거절). **결과는 언제나 잠기지 않은 PDF 다**(사용자 2026-11-22).
     */
    enum class Lock { NONE, OWNER_ONLY, PASSWORD, UNSUPPORTED }

    sealed interface Inspection {
        /** [pageCount] 는 잠겨 읽지 못했으면(암호가 없거나 틀렸다) null. */
        data class Readable(val pageCount: Int?, val lock: Lock) : Inspection
        data object Unreadable : Inspection
    }

    sealed interface Written {
        data class Done(val pages: Int) : Written
        data class Refused(val reason: FileOpEngine.Reason) : Written
    }

    /** 엔진이 쓰는 곳 — 길이 자리를 나중에 고쳐 써야 해서 [patch] 가 있다. */
    interface Output {
        val position: Long
        fun write(b: ByteArray, off: Int, len: Int)
        fun patch(at: Long, b: ByteArray)
    }

    /** PDF 를 읽고 짓는 것. `feature:docview` 가 구현하고 `app` 이 꽂는다. 둘 다 막는 함수다 — 부르는 쪽이 IO 디스패처에서 부른다. */
    interface Engine {
        /** @param password 사용자 암호(세션 암호의 사본) — 엔진은 덮지 않는다(부르는 쪽의 배열이다). */
        fun inspect(path: String, password: CharArray?, checkCancelled: () -> Unit): Inspection

        /**
         * @param titles 원본마다 목차에 적을 이름(합치기의 파일 항목).
         * @param checkCancelled 객체마다 부른다. 취소면 던진다.
         * @param onPage (쓴 쪽 수, 전체, 원본 이름).
         */
        fun write(
            parts: List<Part>,
            titles: List<String>,
            output: Output,
            checkCancelled: () -> Unit,
            onPage: (Int, Int, String) -> Unit,
        ): Written
    }

    /** **꽂히지 않으면 요청은 실패로 끝난다**([ExtractSupport.runner] 와 같은 판단). */
    @Volatile
    var engine: Engine? = null
}

/**
 * 쪽 뽑기·합치기의 쓰는 쪽 — 원본 곁(합치기는 첫 원본 곁)에 임시본을 열고, 엔진이 다 쓰면 내리고 끝에 `O_EXCL` 로 없는 이름을 맡는다
 * ([NewFiles.place] — 남의 파일을 덮지 않는다). 쓰는 동안 16 MiB 마다 남은 공간을 보고 64 MiB 밑이면 멈춘다(ZIP 만들기의 지킴).
 */
object PdfPageSaver {

    data class Result(val outcome: FileOpEngine.Outcome, val path: String?)

    suspend fun save(
        context: android.content.Context,
        request: FileOpManager.Request.PdfPages,
        media: MediaIndex?,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onPlaced: (Result) -> Unit = {},
    ): Result = try {
        saveUnwiped(context, request, media, onProgress, onPlaced)
    } finally {
        // 암호 사본은 쓰고 나면 덮는다(돌지 않고 버려진 길은 큐가 덮는다).
        for (part in request.parts) part.password?.fill('\u0000')
    }

    private suspend fun saveUnwiped(
        context: android.content.Context,
        request: FileOpManager.Request.PdfPages,
        media: MediaIndex?,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onPlaced: (Result) -> Unit,
    ): Result = withContext(IroDispatchers.io) {
        val engine = PdfPagesSupport.engine
            ?: return@withContext failed(FileOpEngine.Reason.IO, request.name).also { Iro.e(message = "PDF 쪽 엔진이 꽂히지 않았다") }
        val sources = request.parts.map { File(it.path) }
        sources.firstOrNull { !it.isFile }?.let { return@withContext failed(FileOpEngine.Reason.NOT_FOUND, it.name) }
        val dir = File(request.destDir)
        if (!dir.isDirectory) return@withContext failed(FileOpEngine.Reason.NOT_FOUND, dir.name)
        // **둘째 겹** — 화면이 메뉴를 감추지만 큐가 스스로도 본다: 원본은 사용자 저장소의 파일이어야 하고(앱 캐시의 받은 사본·휴지통 아님),
        // 놓을 자리도 사용자 저장소여야 한다(앱 캐시에 쓰면 사용자가 찾아갈 수 없다).
        if (request.parts.any { !isUserFile(context, it.path) } || !isUserDir(dir)) {
            return@withContext failed(FileOpEngine.Reason.PERMISSION, request.name)
        }
        if (usableSpaceOf(dir) < KEEP_FREE_BYTES) return@withContext failed(FileOpEngine.Reason.NO_SPACE, request.name)
        // 원본마다 (크기·수정 시각) — 엔진은 쓰는 동안 원본을 여러 번 다시 읽는다(쪽 사전을 들지 않는다). 그사이 원본이 제자리에서 바뀌면
        // 앞뒤가 다른 판에서 읽은 객체가 섞이므로, 놓기 직전에 다시 견줘 다르면 놓지 않는다.
        val stamps = sources.map { FileStamp.of(it) }
        val total = request.parts.sumOf { it.pages?.size ?: 0 }
        onProgress(FileOpEngine.Progress(0, 0, 0, total, sources.first().name))
        AtomicFileWriter.sweepPartials(dir)
        val pending = try {
            AtomicFileWriter.beginRandomAccess(File(dir, request.name))
        } catch (e: IOException) {
            return@withContext failed(NewFiles.reasonOf(e), request.name)
        }
        val job = currentCoroutineContext().job
        try {
            val out = FileOutput(pending.file, dir)
            val written = engine.write(
                request.parts,
                sources.map { titleOf(it.name) },
                out,
                checkCancelled = { if (!job.isActive) throw CancellationException() },
            ) { done, all, name -> onProgress(FileOpEngine.Progress(0, 0, done, all, name)) }
            when (written) {
                is PdfPagesSupport.Written.Refused -> {
                    withContext(NonCancellable) { pending.abort() }
                    failed(written.reason, request.name)
                }
                is PdfPagesSupport.Written.Done -> {
                    out.flush()
                    if (!unchanged(stamps, sources)) {
                        withContext(NonCancellable) { pending.abort() }
                        return@withContext failed(FileOpEngine.Reason.CHANGED, request.name)
                    }
                    val done = FileOpEngine.Outcome.Done(1, 0, 0)
                    val target = NewFiles.place(pending, dir, request.name, media) { t -> onPlaced(Result(done, t.absolutePath)) }
                    Result(done, target.absolutePath)
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { pending.abort() }
            Result(FileOpEngine.Outcome.Cancelled, null)
        } catch (t: Throwable) {
            withContext(NonCancellable) { pending.abort() }
            Iro.e(message = "PDF 쪽 저장이 실패했다: ${t::class.java.simpleName}")
            failed(NewFiles.reasonOf(t).let { if (it == FileOpEngine.Reason.IO) NewFiles.reasonWhenSilent(dir, it) else it }, request.name)
        }
    }

    /** 시작할 때 적은 표([stamps])와 지금 원본들의 표가 모두 같은가(지워졌으면 다르다). */
    internal fun unchanged(stamps: List<FileStamp?>, sources: List<File>): Boolean =
        stamps.size == sources.size && sources.indices.all { stamps[it] != null && FileStamp.of(sources[it]) == stamps[it] }

    private fun failed(reason: FileOpEngine.Reason, name: String) = Result(FileOpEngine.Outcome.Failed(reason, name), null)

    /** 사용자 저장소(`/storage/` 아래)의 파일이고, 받은 사본·휴지통이 아니다. 정규화한 경로로 본다. */
    internal fun isUserFile(context: android.content.Context, path: String): Boolean {
        val canonical = try {
            File(path).canonicalPath
        } catch (e: IOException) {
            return false
        }
        return canonical.startsWith(STORAGE_ROOT) && !IncomingFiles.isCopy(context, path) && !TrashStore.isTrashPath(canonical)
    }

    private fun isUserDir(dir: File): Boolean {
        val canonical = try {
            dir.canonicalPath
        } catch (e: IOException) {
            return false
        }
        return canonical.startsWith(STORAGE_ROOT) && !TrashStore.isTrashPath(canonical)
    }

    private const val STORAGE_ROOT = "/storage/"

    /** 목차에 적는 원본의 이름 — 확장자를 뗀다. */
    internal fun titleOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    @SuppressLint("UsableSpace") // 남길 몫을 재는 자리라 보수적인 값이 맞다(ZIP 만들기와 같다).
    private fun usableSpaceOf(dir: File): Long = dir.usableSpace

    /**
     * 임시본 위의 쓰는 곳 — 256 KiB 씩 모아 쓴다(엔진은 객체마다 작은 조각을 낸다). 길이 자리 고치기([patch])는 모은 곳 안이면 거기서,
     * 이미 내린 곳이면 그 자리에 쓰고 돌아온다. 16 MiB 마다 남은 공간을 본다.
     */
    private class FileOutput(private val file: RandomAccessFile, private val dir: File) : PdfPagesSupport.Output {
        private val buf = ByteArray(BUFFER)
        private var bufLen = 0
        private var flushed = 0L
        private var sinceCheck = 0L

        override val position: Long get() = flushed + bufLen

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len >= BUFFER) {
                flush()
                file.write(b, off, len)
                flushed += len
            } else {
                if (bufLen + len > BUFFER) flush()
                System.arraycopy(b, off, buf, bufLen, len)
                bufLen += len
            }
            sinceCheck += len
            if (sinceCheck >= SPACE_CHECK_BYTES) {
                sinceCheck = 0
                if (usableSpaceOf(dir) < KEEP_FREE_BYTES) throw NewFiles.Failed(FileOpEngine.Reason.NO_SPACE)
            }
        }

        override fun patch(at: Long, b: ByteArray) {
            require(at >= 0 && at + b.size <= position) { "patch out of range" }
            if (at >= flushed) {
                System.arraycopy(b, 0, buf, (at - flushed).toInt(), b.size)
            } else {
                // 내린 곳과 모은 곳에 걸칠 수 있다 — 내리고 쓴다.
                flush()
                file.seek(at)
                file.write(b)
                file.seek(flushed)
            }
        }

        fun flush() {
            if (bufLen == 0) return
            file.write(buf, 0, bufLen)
            flushed += bufLen
            bufLen = 0
        }
    }

    private const val BUFFER = 256 * 1024
    private const val SPACE_CHECK_BYTES = 16L * 1024 * 1024

    /** 이만큼은 남겨 둔다(ZIP 만들기의 지킴과 같다). */
    private const val KEEP_FREE_BYTES = 64L * 1024 * 1024
}

/**
 * 쪽 범위 글(`1-3, 7, 10-12`)과 새 이름 — 순수 함수라 JVM 시험이 지킨다.
 */
object PdfPageRanges {

    sealed interface Parsed {
        /** 0부터의 쪽 번호, 오름차순·겹침 없이. */
        data class Pages(val pages: List<Int>) : Parsed

        data class Error(val kind: ErrorKind) : Parsed
    }

    enum class ErrorKind {
        /** 아무것도 적지 않았다. */
        EMPTY,

        /** 고른 쪽이 한 번에 만드는 상한([MAX_PAGES])을 넘는다. */
        TOO_MANY,

        /** 읽을 수 없는 글자(숫자·`-`·`,` 밖)나 꼴. */
        SYNTAX,

        /** 1 아래이거나 쪽 수 위. */
        OUT_OF_RANGE,

        /** `7-3` 처럼 끝이 앞보다 작다. */
        REVERSED,
    }

    /**
     * 범위 글을 읽는다. 쉼표(`,`·`、`·`，`)나 빈칸으로 가르고, 범위는 `-`·`~`·`–`(줄표)로 잇는다. 쪽은 1부터. 겹치거나 차례가 뒤섞인 것은
     * 모아 오름차순으로 — 뽑은 문서는 원본의 차례를 따른다(같은 쪽을 두 번 넣지 않는다 — 쪽 객체는 나무에 한 번만 걸 수 있다).
     *
     * 입력은 [MAX_INPUT] 자까지 — 그 너머는 읽지 않고 [ErrorKind.SYNTAX] 다.
     */
    fun parse(text: String, pageCount: Int): Parsed {
        if (text.length > MAX_INPUT) return Parsed.Error(ErrorKind.SYNTAX)
        val normalized = text.map { c ->
            when (c) {
                '、', '，', ';' -> ','
                '~', '–', '—', '−', '〜', '～' -> '-'
                in '０'..'９' -> '0' + (c - '０')
                else -> c
            }
        }.joinToString("")
        val tokens = normalized.split(',').flatMap { part ->
            // `1 - 3` 의 빈칸은 범위의 일부다 — 줄표 둘레의 빈칸을 먼저 걷고 나머지 빈칸으로 가른다.
            part.replace(Regex("""\s*-\s*"""), "-").trim().split(Regex("""\s+"""))
        }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Parsed.Error(ErrorKind.EMPTY)
        val picked = java.util.TreeSet<Int>()
        for (t in tokens) {
            val dash = t.indexOf('-')
            val (a, b) = if (dash < 0) {
                val n = number(t) ?: return Parsed.Error(ErrorKind.SYNTAX)
                n to n
            } else {
                val a = number(t.substring(0, dash)) ?: return Parsed.Error(ErrorKind.SYNTAX)
                val b = number(t.substring(dash + 1)) ?: return Parsed.Error(ErrorKind.SYNTAX)
                a to b
            }
            if (a < 1 || b < 1 || a > pageCount || b > pageCount) return Parsed.Error(ErrorKind.OUT_OF_RANGE)
            if (b < a) return Parsed.Error(ErrorKind.REVERSED)
            for (p in a..b) {
                picked += p - 1
                if (picked.size > MAX_PAGES) return Parsed.Error(ErrorKind.TOO_MANY)
            }
        }
        return Parsed.Pages(picked.toList())
    }

    /** 숫자만(9자리까지 — 넘으면 범위 밖으로 친다). */
    private fun number(s: String): Int? {
        if (s.isEmpty() || s.any { it !in '0'..'9' }) return null
        return if (s.length > 9) Int.MAX_VALUE else s.toInt()
    }

    /**
     * 고른 쪽을 이름 꼬리로 — `[0,1,2,6]` → `p1-3_7`. [MAX_TAG] 자를 넘으면 `p1-120_57pages`(처음·끝·쪽 수).
     */
    fun tag(pages: List<Int>): String {
        if (pages.isEmpty()) return "p"
        val runs = StringBuilder("p")
        var i = 0
        while (i < pages.size) {
            var j = i
            while (j + 1 < pages.size && pages[j + 1] == pages[j] + 1) j++
            if (runs.length > 1) runs.append('_')
            runs.append(pages[i] + 1)
            if (j > i) runs.append('-').append(pages[j] + 1)
            i = j + 1
        }
        if (runs.length <= MAX_TAG) return runs.toString()
        return "p${pages.first() + 1}-${pages.last() + 1}_${pages.size}pages"
    }

    /** 쪽 뽑기의 새 이름 — `보고서.pdf` → `보고서_p1-3_7.pdf`. */
    fun extractName(sourceName: String, pages: List<Int>): String = NewFiles.derivedName(sourceName, tag(pages), "pdf")

    /** 합치기의 새 이름 — 첫 파일 기준 `보고서_merged.pdf`. */
    fun mergeName(firstName: String): String = NewFiles.derivedName(firstName, "merged", "pdf")

    /** 합칠 수 있는 파일인가 — 이름으로만(목록을 그릴 때 파일을 열지 않는다). 내용은 큐가 연다. */
    fun isPdfName(name: String): Boolean = NewFiles.extensionOf(name) == "pdf"

    const val MAX_INPUT = 1000
    private const val MAX_TAG = 40

    /** 합치기에 한 번에 넣는 파일 수의 상한(엔진의 상한과 같다). */
    const val MAX_MERGE_FILES = 100

    /** 한 번에 만드는 PDF 의 쪽 수 상한(엔진의 상한과 같다 — 대화상자가 미리 말한다). */
    const val MAX_PAGES = 20_000
}
