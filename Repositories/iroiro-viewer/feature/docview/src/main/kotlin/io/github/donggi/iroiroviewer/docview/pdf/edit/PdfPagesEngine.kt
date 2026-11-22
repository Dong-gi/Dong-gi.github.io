package io.github.donggi.iroiroviewer.docview.pdf.edit

import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSink
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.PdfPagesSupport
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * 쪽 뽑기·합치기의 **짓는 쪽** — 파일 작업 큐의 이음매([PdfPagesSupport.Engine])에 [PdfPageCopier] 를 잇는다. `app` 이 꽂는다.
 * 쓰는 곳(임시본·공간·놓기)은 `core:io` 의 것이다 — 여기는 파일을 읽기만 한다. 받은 암호 배열은 덮지 않는다(부르는 쪽의 것이다).
 */
object PdfPagesEngine : PdfPagesSupport.Engine {

    override fun inspect(path: String, password: CharArray?, checkCancelled: () -> Unit): PdfPagesSupport.Inspection = try {
        FileInputStream(File(path)).use { input ->
            when (val i = PdfPageCopier.inspect(FileChannelSource(input.channel), password, checkCancelled = checkCancelled)) {
                is PdfPageCopier.Inspection.Readable -> PdfPagesSupport.Inspection.Readable(i.pageCount, lockOf(i.lock))
                PdfPageCopier.Inspection.Unreadable -> PdfPagesSupport.Inspection.Unreadable
            }
        }
    } catch (e: IOException) {
        PdfPagesSupport.Inspection.Unreadable
    }

    override fun write(
        parts: List<PdfPagesSupport.Part>,
        titles: List<String>,
        output: PdfPagesSupport.Output,
        checkCancelled: () -> Unit,
        onPage: (Int, Int, String) -> Unit,
    ): PdfPagesSupport.Written {
        val inputs = ArrayList<FileInputStream>(parts.size)
        try {
            val sources = parts.mapIndexed { i, part ->
                checkCancelled()
                val file = File(part.path)
                // 화면이 센 쪽 수가 없으면(합치기) 플랫폼에 묻는다 — 우리 나무 걷기가 pdfium 과 다르게 세는 망가진 문서에서 쪽이 뒤섞이지 않게.
                // 플랫폼이 열지 못하면 견줄 것이 없어 보지 않는다.
                val expected = part.expectedCount ?: platformPageCount(file, part.password)
                val input = FileInputStream(file).also { inputs += it }
                PdfPageCopier.Source(
                    titles.getOrElse(i) { "" },
                    FileChannelSource(input.channel),
                    part.pages,
                    expected,
                    part.password,
                )
            }
            val sink = object : PdfSink {
                override val position: Long get() = output.position
                override fun write(b: ByteArray, off: Int, len: Int) = output.write(b, off, len)
                override fun patch(at: Long, b: ByteArray) = output.patch(at, b)
            }
            return when (val o = PdfPageCopier.write(sources, sink, checkCancelled = checkCancelled, onPage = onPage)) {
                is PdfPageCopier.Outcome.Done -> PdfPagesSupport.Written.Done(o.pages)
                is PdfPageCopier.Outcome.Refused -> PdfPagesSupport.Written.Refused(reasonOf(o.reason))
            }
        } finally {
            for (input in inputs) runCatching { input.close() }
        }
    }

    /**
     * 플랫폼(pdfium)이 세는 쪽 수 — 열지 못하면 null. 암호는 `String` 으로 건너간다(`LoadParams.Builder.setPassword` 가 그것만 받는다 —
     * 뷰어가 여는 길과 같은 한계, CLAUDE.md '암호가 걸린 파일').
     */
    private fun platformPageCount(file: File, password: CharArray?): Int? {
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = if (password != null && password.isNotEmpty()) {
                PdfRenderer(pfd, LoadParams.Builder().setPassword(String(password)).build())
            } else {
                PdfRenderer(pfd)
            }
            pfd = null // 렌더러가 가졌다 — 닫는 것은 렌더러다
            try {
                renderer.pageCount
            } finally {
                renderer.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            null
        } finally {
            runCatching { pfd?.close() }
        }
    }

    private fun lockOf(l: PdfPageCopier.Lock): PdfPagesSupport.Lock = when (l) {
        PdfPageCopier.Lock.NONE -> PdfPagesSupport.Lock.NONE
        PdfPageCopier.Lock.OWNER_ONLY -> PdfPagesSupport.Lock.OWNER_ONLY
        PdfPageCopier.Lock.PASSWORD -> PdfPagesSupport.Lock.PASSWORD
        PdfPageCopier.Lock.UNSUPPORTED -> PdfPagesSupport.Lock.UNSUPPORTED
    }

    internal fun reasonOf(r: PdfPageCopier.Refusal): FileOpEngine.Reason = when (r) {
        PdfPageCopier.Refusal.LOCKED -> FileOpEngine.Reason.PDF_LOCKED
        PdfPageCopier.Refusal.LOCK_UNSUPPORTED -> FileOpEngine.Reason.PDF_LOCK_UNSUPPORTED
        PdfPageCopier.Refusal.UNREADABLE -> FileOpEngine.Reason.PDF_UNREADABLE
        PdfPageCopier.Refusal.CHANGED -> FileOpEngine.Reason.CHANGED
        PdfPageCopier.Refusal.TOO_BIG -> FileOpEngine.Reason.PDF_TOO_BIG
    }
}
