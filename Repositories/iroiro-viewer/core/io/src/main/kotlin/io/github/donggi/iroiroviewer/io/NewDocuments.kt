package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.content.res.AssetManager
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * **빈 오피스 문서**를 만든다(폴더 화면의 + → '새 문서(Word)·새 시트(Excel)·새 프레젠테이션(PowerPoint)·새 한글 문서').
 * 파일 작업 큐([FileOpManager.Request.CreateDocument])로 부른다 — 새 글 파일([TextSaver.create])이 큐를 지나므로 그쪽에 맞췄다(같은 폴더를
 * 복사와 동시에 만지지 않는 것이 이 큐의 일이다). 만든 문서는 곧바로 편집기(`feature:editor`)가 연다.
 *
 * 빈 문서는 이 앱에 든 **서식**을 옮겨 적은 것이다([BlankDocuments]).
 *
 * ## 차례
 *
 * 1. 이름을 검사만 한다([PathRules.validate] — 고쳐 주지 않는다. 새 폴더·새 글 파일과 같다). 확장자는 종류와 같아야 한다
 *    (`보고서.docx` 에 `docx` — 화면이 지운 확장자를 다시 붙인다. 다른 확장자로 바꾸면 그 이름으로 열리는 형식과 내용이 어긋난다).
 * 2. 같은 이름이 이미 있으면 [FileOpEngine.Reason.EXISTS] — 임시본을 짓기 전에 미리 본다(이름 칸이 닫히지 않고 칸 안에서 말한다).
 * 3. 임시본([AtomicFileWriter.begin])에 서식을 옮겨 적는다(만든 시각만 지금으로 — [BlankDocuments.write]).
 * 4. 다시 읽어 본다([BlankDocuments.verify] — 모든 항목의 CRC 와 이름의 차례) — 아니면 [FileOpEngine.Reason.VERIFY_FAILED].
 * 5. **그 이름 그대로** 맡아 놓는다([NewFiles.place] 의 `exact` — `O_EXCL`). 2 와 5 사이에 그 이름이 생겼으면 덮지 않고 EXISTS.
 *    번호(`(2)`)를 붙이지 않는 까닭은 사용자가 친 이름이라서다 — 만든 뒤 곧바로 편집기가 그 이름으로 연다.
 *
 * 만든 경로는 [FileOpManager.Extra.Saved] 가 싣는다(새 글 파일과 같다).
 */
object NewDocuments {

    /**
     * 폴더 화면의 + 메뉴가 보일 '새 문서' 의 종류([BlankDocuments.KINDS] 의 차례) — 서식이 있고 **편집기가 그 형식을 고칠 수 있는** 것만.
     * 만들어 놓고 열 수 없는 문서를 만들지 않는다(엔진 묶음 없이 지은 빌드는 빈 목록이다).
     */
    fun creatable(context: Context): List<String> {
        val editable = EditorApp.kinds(context)
        return BlankDocuments.KINDS.filter { it in editable && BlankDocuments.available(context.assets, it) }
    }

    /**
     * 빈 문서를 만든다([FileOpManager.Request.CreateDocument] — 차례는 머리말). 파일 작업 큐의 작업 스레드에서 돈다.
     *
     * @param assets 서식을 읽는 곳(앱의 자산).
     * @param now 문서의 만든 시각(밀리초 — `docProps/core.xml`·HWPX 의 `content.hpf`). 시험이 박는다.
     */
    suspend fun create(
        request: FileOpManager.Request.CreateDocument,
        assets: AssetManager,
        media: MediaIndex?,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
        now: Long = System.currentTimeMillis(),
    ): FileOpEngine.Outcome = withContext(IroDispatchers.io) {
        val verdict = PathRules.validate(request.name)
        if (verdict is PathRules.Verdict.Rejected) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.INVALID_NAME, request.name)
        }
        val name = (verdict as PathRules.Verdict.Ok).name
        if (!nameFits(name, request.kind)) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.INVALID_NAME, name)
        if (!BlankDocuments.available(assets, request.kind)) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_EDITABLE, name)
        }
        val dir = try {
            File(request.dir).canonicalFile
        } catch (e: IOException) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, name)
        }
        if (!dir.isDirectory) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, name)
        if (!IncomingFiles.isUnderStorage(dir.path) || TrashStore.isTrashPath(dir.path)) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PERMISSION, name)
        }
        val target = File(dir, name)
        // 미리 본다 — 끝의 `O_EXCL` 이 진짜 관문이지만, 있는 이름에 빈 문서를 지어 볼 까닭이 없다.
        if (target.exists()) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.EXISTS, name)

        AtomicFileWriter.sweepPartials(dir)
        val pending = try {
            AtomicFileWriter.begin(target, lastModified = 0L)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(e), name)
        }
        var handedOff = false
        try {
            val names = try {
                assets.open(BlankDocuments.assetPath(request.kind)).use { template -> BlankDocuments.write(template, pending.stream, now) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Iro.d { "빈 문서를 짓지 못했다: ${e.javaClass.simpleName}" }
                return@withContext FileOpEngine.Outcome.Failed(NewFiles.reasonOfWrite(e, dir), name)
            }
            if (!BlankDocuments.verify(pending.tempFile, names)) {
                return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.VERIFY_FAILED, name)
            }

            handedOff = true
            val done = FileOpEngine.Outcome.Done(1, 0, 0)
            try {
                // 내리기 → 마지막 취소 → (취소 없이) 이름 맡기·옮기기·색인 → onPlaced. 차례는 [NewFiles.place] 가 정한다.
                NewFiles.place(pending, dir, name, media, exact = true) { placed ->
                    onExtra(FileOpManager.Extra.Saved(placed.path))
                    onPlaced(done)
                }
            } catch (e: Exception) {
                withContext(NonCancellable) { pending.abort() }
                if (e is CancellationException) throw e
                return@withContext FileOpEngine.Outcome.Failed(NewFiles.reasonOf(e), name)
            }
            done
        } finally {
            if (!handedOff) withContext(NonCancellable) { pending.abort() }
        }
    }

    /** 이름의 확장자가 종류와 같은가(대소문자는 가리지 않는다 — `보고서.DOCX`). 판단만 — JVM 시험이 답한다. */
    internal fun nameFits(name: String, kind: String): Boolean =
        kind.isNotEmpty() && NewFiles.extensionOf(name) == kind.lowercase(java.util.Locale.ROOT)
}
