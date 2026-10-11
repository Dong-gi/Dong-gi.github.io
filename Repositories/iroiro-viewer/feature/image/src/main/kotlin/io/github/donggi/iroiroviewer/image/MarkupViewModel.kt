package io.github.donggi.iroiroviewer.image

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.ImageProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 그리는 화면의 도구. 펜·형광펜·직선은 획을 긋고([StrokeKind]), 글은 메모를 달고, 지우개는 획·메모를 통째로 지운다. */
internal enum class MarkupTool { PEN, HIGHLIGHTER, LINE, TEXT, ERASER }

/**
 * 도구마다의 빛깔과 굵기(글자 크기) 단계, 메모의 바탕 상자. **그림을 바꿔도 남는다**(화면이 사는 동안) — 같은 빛깔로 여러 장에 표시하는
 * 일이 흔하다. 앱을 다시 켜면 처음 값이다(설정에 적지 않았다).
 */
internal data class MarkupStyle(
    val tool: MarkupTool = MarkupTool.PEN,
    val colors: Map<MarkupTool, Int> = DEFAULT_COLORS,
    val levels: Map<MarkupTool, Int> = emptyMap(),
    val boxed: Boolean = true,
) {
    fun colorOf(t: MarkupTool): Int = colors[t] ?: MarkupColor.RED.argb
    fun levelOf(t: MarkupTool): Int = levels[t] ?: DEFAULT_LEVEL

    companion object {
        /** 펜·직선은 빨강(표시하는 빛깔), 형광펜은 노랑(관행), 메모는 흰 글에 어두운 상자(어느 사진 위에서도 읽힌다). */
        val DEFAULT_COLORS = mapOf(
            MarkupTool.PEN to MarkupColor.RED.argb,
            MarkupTool.HIGHLIGHTER to MarkupColor.YELLOW.argb,
            MarkupTool.LINE to MarkupColor.RED.argb,
            MarkupTool.TEXT to MarkupColor.WHITE.argb,
        )

        /** 세 단계(얇게·보통·굵게 / 작게·보통·크게)의 가운데. */
        const val DEFAULT_LEVEL = 1
    }
}

/**
 * 메모 칸에 펼친 메모. **화면 쪽 값이다** — 확인을 누르기 전까지 그린 것에 들지 않는다. ViewModel 이 들어 밤 모드 등으로 화면이
 * 다시 서도 쓰던 글이 남고, 저장 상태 번들에는 싣지 않는다(함정 표의 `rememberTextFieldState`).
 *
 * @param id 고치는 메모의 신원. null 이면 새 메모다.
 * @param x·[y] 새 메모는 누른 자리, 고치는 메모는 지금 자리(그림 좌표).
 * @param level 고른 글자 크기 단계. null 이면 고치는 메모의 크기([size]) 그대로다 — 메모의 크기는 달 때의 확대에 따라 정해져 단계로
 *   되돌릴 수 없다.
 */
internal data class NoteEdit(
    val id: Long?,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val level: Int?,
    val size: Float?,
    val boxed: Boolean,
) {
    override fun toString(): String = "NoteEdit(id=$id, ${text.length}자)"
}

/**
 * 그리는 그림 하나. [probe] 는 열 때 잰 원본(보이는 방향의 치수 — 저장할 때 그사이 바뀌었는지 이것으로 본다, 확대 조각의 방향).
 * [fileLength]·[fileModified] 는 초안을 되살릴 때 같은 파일인지 보는 값이다.
 */
internal class MarkupSession(
    val path: String,
    val probe: ImageProbe,
    val fileLength: Long,
    val fileModified: Long,
    val history: MarkupHistory,
) {
    val width: Int get() = probe.displayWidth
    val height: Int get() = probe.displayHeight

    /** 그림 좌표의 높이(폭 = 1). */
    val aspect: Float get() = height.toFloat() / width

    fun with(history: MarkupHistory) = MarkupSession(path, probe, fileLength, fileModified, history)

    override fun toString(): String = "MarkupSession(${history.marks.size})"
}

/**
 * 그리기·메모의 상태 — **획 목록은 여기 든다**(공통 지침: 큰 것을 저장 상태 번들에 싣지 않는다). 화면(`MarkupScreen`)은 이것을 그리고
 * 손가락이 만든 것을 넘긴다. 액티비티에 매이므로 회전·밤 모드를 건너 산다.
 *
 * **프로세스가 죽으면** 이것도 사라진다 — 앱이 화면에서 사라질 때 초안을 앱 전용 폴더에 적고([writeDraft]), 다시 서면 화면이 저장
 * 상태에 남긴 경로로 되살린다([restoreDraft]). 초안은 저장하거나 버리면 지운다([close]).
 */
internal class MarkupViewModel(app: Application) : AndroidViewModel(app) {

    private val _session = MutableStateFlow<MarkupSession?>(null)

    /** 지금 그리는 그림. null 이면 그리는 화면이 닫혀 있다. */
    val session: StateFlow<MarkupSession?> = _session.asStateFlow()

    private val _style = MutableStateFlow(MarkupStyle())
    val style: StateFlow<MarkupStyle> = _style.asStateFlow()

    private val _noteEdit = MutableStateFlow<NoteEdit?>(null)

    /** 펼친 메모 칸. null 이면 닫혀 있다. */
    val noteEdit: StateFlow<NoteEdit?> = _noteEdit.asStateFlow()

    /** 그린 것의 신원. 한 그림 안에서만 유일하면 된다 — 되살리면 가장 큰 것 다음부터. */
    private var nextId = 1L

    /**
     * [path] 를 그리기 시작한다. 원본의 크기·수정 시각을 재는 입출력이라 곧바로 열리지 않는다(몇 ms). 다른 그림의 초안이 남아 있으면
     * 지운다 — 초안은 한 번에 하나다.
     */
    fun open(path: String, probe: ImageProbe) {
        _noteEdit.value = null
        viewModelScope.launch {
            val (length, modified) = withContext(IroDispatchers.io) { File(path).let { it.length() to it.lastModified() } }
            nextId = 1L
            _session.value = MarkupSession(path, probe, length, modified, MarkupHistory.EMPTY)
        }
        draft { it.delete() }
    }

    /** 그리는 화면을 닫는다(저장했거나 버렸다). 초안도 지운다. */
    fun close() {
        _session.value = null
        _noteEdit.value = null
        draft { it.delete() }
    }

    fun newId(): Long = nextId++

    fun add(mark: Mark) = edit { it.add(mark) }

    fun remove(ids: Set<Long>) = edit { it.remove(ids) }

    fun replace(mark: Mark) = edit { it.replace(mark) }

    fun clear() = edit { it.clear() }

    fun undo() = edit { it.undo() }

    fun redo() = edit { it.redo() }

    private inline fun edit(change: (MarkupHistory) -> MarkupHistory) {
        _session.update { s -> s?.with(change(s.history)) }
    }

    fun setTool(tool: MarkupTool) = _style.update { it.copy(tool = tool) }

    fun setColor(tool: MarkupTool, argb: Int) = _style.update { it.copy(colors = it.colors + (tool to argb)) }

    fun setLevel(tool: MarkupTool, level: Int) = _style.update { it.copy(levels = it.levels + (tool to level)) }

    fun setBoxed(boxed: Boolean) = _style.update { it.copy(boxed = boxed) }

    // ---- 메모 칸 ----

    /** 빈 자리를 눌렀다 — 새 메모 칸을 그 자리로 연다. 빛깔·크기·바탕은 글 도구의 지금 값이다. */
    fun startNote(x: Float, y: Float) {
        val s = _style.value
        _noteEdit.value = NoteEdit(
            id = null,
            x = x,
            y = y,
            text = "",
            color = s.colorOf(MarkupTool.TEXT),
            level = s.levelOf(MarkupTool.TEXT),
            size = null,
            boxed = s.boxed,
        )
    }

    /** 메모를 눌렀다 — 그 메모로 칸을 연다. */
    fun editNote(note: Note) {
        _noteEdit.value = NoteEdit(note.id, note.x, note.y, note.text, note.color, level = null, size = note.size, boxed = note.boxed)
    }

    fun updateNote(change: (NoteEdit) -> NoteEdit) = _noteEdit.update { it?.let(change) }

    /**
     * 칸을 닫는다. [keepStyle] 을 주면(확인을 눌렀다) 거기서 고른 빛깔·크기·바탕을 글 도구의 다음 값으로도 남긴다 — 다음 메모가 같은
     * 꼴로 시작한다.
     */
    fun closeNote(keepStyle: NoteEdit? = null) {
        if (keepStyle != null) {
            _style.update { s ->
                s.copy(
                    colors = s.colors + (MarkupTool.TEXT to keepStyle.color),
                    levels = keepStyle.level?.let { s.levels + (MarkupTool.TEXT to it) } ?: s.levels,
                    boxed = keepStyle.boxed,
                )
            }
        }
        _noteEdit.value = null
    }

    // ---- 초안 ----

    /**
     * 그리던 것을 초안으로 적는다. 앱이 화면에서 사라질 때(ON_STOP) 부른다 — 시스템은 그 뒤에만 프로세스를 거둔다. 그린 것이 없으면
     * 초안을 지운다(되살릴 것이 없다).
     */
    fun writeDraft() {
        val s = _session.value ?: return
        val marks = s.history.marks
        if (marks.isEmpty()) {
            draft { it.delete() }
            return
        }
        val data = MarkupDraft.Data(s.path, s.fileLength, s.fileModified, s.width, s.height, marks)
        draft { file ->
            file.parentFile?.mkdirs()
            // 다 쓴 뒤 이름을 바꾼다 — 쓰는 도중에 죽으면 반쯤 쓴 초안이 아니라 앞의 초안(또는 없음)이 남는다.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { MarkupDraft.write(it, data) }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw IOException("초안의 이름을 바꾸지 못했다")
            }
        }
    }

    /**
     * 프로세스가 죽었다 살아났다 — 화면이 저장 상태에 남긴 [path] 의 초안을 되살린다. 초안이 없거나, 다른 그림의 것이거나, 원본이
     * 그사이 바뀌었으면(크기·수정 시각·치수) 되살리지 않고 초안을 지운다.
     *
     * @return 되살렸는가.
     */
    suspend fun restoreDraft(path: String): Boolean {
        if (_session.value != null) return true
        val data = withContext(IroDispatchers.io) {
            try {
                draftFile().takeIf { it.isFile }?.inputStream()?.use { MarkupDraft.read(it) }
            } catch (e: IOException) {
                null
            }
        }
        val probe = if (data != null && data.path == path) ImageIo.probe(path) else null
        if (data == null || probe == null || !sameImage(path, probe, data)) {
            Iro.d { "그리기 초안을 되살리지 않는다 — ${if (data == null) "없다" else "원본이 다르다"}" }
            draft { it.delete() }
            return false
        }
        // 읽는 사이 사용자가 새로 그리기를 열었으면 그것이 이긴다.
        if (_session.value != null) return true
        nextId = (data.marks.maxOfOrNull { it.id } ?: 0L) + 1L
        _session.value = MarkupSession(path, probe, data.fileLength, data.fileModified, MarkupHistory.of(data.marks))
        Iro.d { "그리기 초안을 되살렸다 — ${data.marks.size}개" }
        return true
    }

    /** 초안을 적을 때의 그 원본인가 — 정지 그림이고, 보이는 방향의 치수·크기·수정 시각이 같다. */
    private suspend fun sameImage(path: String, probe: ImageProbe, data: MarkupDraft.Data): Boolean {
        if (probe.isAnimated || probe.displayWidth != data.width || probe.displayHeight != data.height) return false
        return withContext(IroDispatchers.io) {
            File(path).let { it.length() == data.fileLength && it.lastModified() == data.fileModified }
        }
    }

    /** 초안 파일에 하는 일 하나를 줄 세운다([DraftQueue]). */
    private fun draft(work: (File) -> Unit) = DraftQueue.submit(draftFile(), work)

    /** 앱 전용 폴더(백업하지 않는 쪽)의 초안 하나. 사용자 파일이 아니다 — 사용자가 찾아갈 일도, 큐를 지날 일도 없다. */
    private fun draftFile(): File = File(File(getApplication<Application>().noBackupFilesDir, DRAFT_DIR), DRAFT_NAME)

    private companion object {
        const val DRAFT_DIR = "markup"
        const val DRAFT_NAME = "draft.bin"
    }
}

/**
 * 초안 파일에 하는 일(쓰기·지우기)의 줄 — **프로세스에 하나**이고 낸 차례대로 돈다. 지운 뒤에 앞의 쓰기가 끝나 지운 초안이 되살아나지
 * 않게. ViewModel 의 스코프를 쓰지 않는 것은 액티비티를 끝내는 순간 낸 지우기(뷰어가 사라지며 그리기를 접는다)가 그 스코프와 함께
 * 거둬지지 않게 하려는 것이다 — 그러면 그린 것이 앱 폴더에 남는다. 일은 작고 짧다(초안 하나). 실패는 삼킨다 — 초안은 덤이다.
 */
private object DraftQueue {
    private val scope = CoroutineScope(SupervisorJob() + IroDispatchers.io)
    private var last: Job? = null

    @Synchronized
    fun submit(file: File, work: (File) -> Unit) {
        val previous = last
        last = scope.launch {
            previous?.join()
            try {
                work(file)
            } catch (e: IOException) {
                Iro.e(message = "그리기 초안을 다루지 못했다: ${e::class.java.simpleName}")
            } catch (e: SecurityException) {
                Iro.e(message = "그리기 초안을 다루지 못했다: ${e::class.java.simpleName}")
            }
        }
    }
}
