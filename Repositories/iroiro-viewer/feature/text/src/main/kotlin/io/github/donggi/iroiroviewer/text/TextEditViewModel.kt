package io.github.donggi.iroiroviewer.text

import android.app.Application
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileStamp
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.TextSaver
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * 글·코드 파일 **편집기**의 상태(사용자 요청 '글·코드 파일 편집과 새 글 파일 만들기').
 *
 * ## 고치는 글은 여기 든다 — 저장 상태 번들이 아니다
 *
 * [TextFieldState] 는 이 뷰모델이 든다. `rememberTextFieldState()` 는 `rememberSaveable` 이라 글 전체(와 되돌리기 기록)를 저장 상태
 * 번들에 싣는다 — 수백 KB 글을 고치다 홈으로 나가면 바인더 한도를 넘어 앱이 죽고, 사용자 파일의 내용이 시스템이 디스크에 쓸 수
 * 있는 번들에 남는다(함정 표). 회전·밤 모드는 뷰모델이 견디고, **프로세스가 죽는 것은 초안이 견딘다**([TextDrafts]) — 고칠 때마다
 * 2초 쉬면 앱 전용 폴더에 적고, 화면을 떠나거나 앱이 뒤로 갈 때도 적는다. 같은 편집 세션(화면이 저장 상태에 든 표)으로 다시 열리면
 * 묻지 않고 잇고, 다른 때 남은 초안이면 '이어서 고치기 / 버리기' 를 묻는다.
 *
 * ## 열기 — 저장해도 손대지 않은 곳이 바뀌지 않는 글만
 *
 * 고칠 수 있는 자리인가([TextSaver.placeOf]), 상한 이하인가([TextLimits.EDIT_MAX_BYTES]), 엄격하게 읽히고 다시 쓰면 같은 바이트인가
 * ([EditCodec.decode]). 읽는 동안 파일이 바뀌면 다시 읽는다 — 글과 '연 때의 표'([FileStamp])가 같은 판이어야 저장할 때의 '밖에서
 * 바뀌었는가' 가 뜻을 갖는다.
 *
 * ## 저장 — 묻는 차례
 *
 * 줄 끝이 섞였으면 한 번 묻고(맞출 줄 끝을 말한다), 인코딩으로 쓸 수 없는 글자가 있으면 'UTF-8 로 바꿔 저장 / 취소' 를 묻는다(그
 * 글자를 고른다). 그다음 파일 작업 큐에 넣는다(`SaveText` — 실체는 `core:io` 의 [TextSaver]). 큐가 '밖에서 바뀌었다'·'고치기 전 판을
 * 휴지통에 남기지 못했다' 로 멈추면 묻고 다시 넣는다. 결과는 [TextSaveJobs] 로 이 뷰모델이 받는다 — 화면이 뒤로 가 있어도 받아야
 * 다음 저장이 자기 저장을 '밖에서 바뀌었다' 로 보지 않는다.
 *
 * 설정(글자 크기·줄 접기·줄 번호)은 텍스트 뷰어의 그 갈래 설정 그대로다([ViewerSettings] — 바꾸면 뷰어에도 남는다).
 */
class TextEditViewModel(app: Application, store: TextViewerStore) : AndroidViewModel(app) {

    sealed interface Screen {
        data object Loading : Screen

        /** 고칠 수 없다. [encoding] 은 그때 쓰려던 인코딩(문장에 넣는다), [limitBytes] 는 크기 상한. */
        data class Refused(val why: Refusal, val encoding: TextEncoding? = null, val limitBytes: Long = 0) : Screen

        /** 고치는 중. [state] 는 세션마다 새것이다. [format] 은 저장할 때 입힐 꼴(UTF-8 로 바꿔 저장하면 바뀐다). */
        class Ready(
            val name: String,
            val state: TextFieldState,
            val format: TextFormat,
            val kind: TextViewerKind,
        ) : Screen
    }

    enum class Refusal { PLACE, READ_ONLY, TOO_LARGE, UNREADABLE, BINARY, UNSUPPORTED_ENCODING, MALFORMED, NOT_ROUND_TRIP }

    /** 사용자에게 묻는 것. 화면이 대화상자로 그리고 답을 이 뷰모델의 함수로 넘긴다. */
    sealed interface Question {
        /** 저장하지 않은 고침(초안)이 있다. [changedSince] 면 그 뒤에 파일이 다른 곳에서 바뀌었다. */
        data class Resume(val changedSince: Boolean) : Question

        /** 줄 끝이 섞여 있다 — 저장하면 [target] 으로 맞춘다. */
        data class MixedEndings(val counts: LineEndingCounts, val target: LineEnding) : Question

        /** 이 인코딩으로 쓸 수 없는 글자가 있다 — [sample] 이 그 첫 글자다. */
        data class Unmappable(val encoding: TextEncoding, val sample: String) : Question

        /** 연 뒤에 파일이 다른 곳에서 바뀌었다(지워졌다). */
        data object ChangedElsewhere : Question

        /** 고치기 전 판을 휴지통에 남기지 못했다. */
        data object PreviousNotKept : Question

        /** 저장하지 않은 고침이 있는데 닫으려 한다. */
        data object CloseUnsaved : Question
    }

    /** 한 번만 쓰는 사건 — 알림 줄·화면 닫기·글자 자리로 옮기기. */
    sealed interface Event {
        /**
         * 저장의 결과(저장했다·못 했다·취소). 문장은 그것을 만든 `core:io` 의 `FileOpText.textMessage` 가 고른다 — 앱의 결과 줄과 같은
         * 문장이다. [converted] 면 UTF-8 로 바꿔 저장했다(편집기가 덧붙여 말한다).
         */
        class Result(val finished: FileOpManager.Finished, val converted: Boolean = false) : Event

        /** UTF-8 로도 쓸 수 없는 글자(짝이 없는 대리 문자)가 있다. */
        data object Unencodable : Event

        /** 저장이 끝나면 닫는다(저장하는 중에 닫기를 눌렀다). */
        data object ClosingAfterSave : Event
        data class Replaced(val count: Int) : Event
        data object NotFound : Event
        data object Close : Event
    }

    /** 찾기·바꾸기 칸. [total] 은 겹치지 않게 찾은 수, [current] 는 지금 고른 것이 몇 번째인가(아니면 0). */
    data class FindState(
        val open: Boolean = false,
        val query: String = "",
        val replacement: String = "",
        val ignoreCase: Boolean = true,
        val total: Int = 0,
        val current: Int = 0,
    )

    /** 저장 중이다. [id] 는 큐에 넣은 요청의 신원(아직 묻는 중이면 null) — 화면이 '앞의 작업을 기다린다' 를 가른다. */
    data class Saving(val id: String?)

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _question = MutableStateFlow<Question?>(null)
    val question: StateFlow<Question?> = _question.asStateFlow()

    private val _dirty = MutableStateFlow(false)

    /** 저장하지 않은 고침이 있는가(조금 늦게 따라온다 — 닫을 때는 그 자리에서 다시 잰다). */
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _lines = MutableStateFlow(1)

    /** 줄 수 — 줄 번호 칸의 폭을 정한다. */
    val lines: StateFlow<Int> = _lines.asStateFlow()

    private val _saving = MutableStateFlow<Saving?>(null)
    val saving: StateFlow<Saving?> = _saving.asStateFlow()

    private val _find = MutableStateFlow(FindState())
    val find: StateFlow<FindState> = _find.asStateFlow()

    /** 화면이 이 글자 자리를 보이게 옮겨야 한다(찾은 것·쓸 수 없는 글자). 화면이 처리하면 [consumeReveal]. */
    private val _reveal = MutableStateFlow<Int?>(null)
    val reveal: StateFlow<Int?> = _reveal.asStateFlow()

    fun consumeReveal() {
        _reveal.value = null
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val viewerSettings = ViewerSettings(viewModelScope, store)

    /** 뷰어의 그 갈래 설정. 읽는 동안은 null. */
    val settings: StateFlow<TextViewerDefaults?> = viewerSettings.value

    fun updateSettings(transform: (TextViewerDefaults) -> TextViewerDefaults) = viewerSettings.update(transform)

    /** 편집 세션 하나 — 화면에 들어와 나갈 때까지. */
    private class Session(
        val path: String,
        val token: Long,
        val name: String,
        val kind: TextViewerKind,
        val state: TextFieldState,
        val draftFile: File,
        var format: TextFormat,
        /** 마지막으로 연·저장한 글. 이것과 다르면 저장하지 않은 고침이 있다. */
        var baseline: String,
        /** [baseline] 의 파일 표. 저장할 때 '밖에서 바뀌었는가' 를 이것과 견준다. */
        var stamp: FileStamp,
    ) {
        /** 이 세션에서 저장한 적이 있다 — 고치기 전 판은 첫 저장에서 한 번만 휴지통에 넣는다. */
        var savedOnce = false

        /** 섞인 줄 끝을 맞추겠다고 답했다. */
        var mixedConfirmed = false

        /** '저장하지 않음' 으로 닫았다 — 떠날 때 초안을 다시 적지 않는다. */
        var discarded = false
    }

    /** 저장 한 번 — 묻고 다시 넣는 동안 같은 바이트를 든다. */
    private class Attempt(
        val session: Session,
        val text: String,
        var format: TextFormat,
        var closeAfter: Boolean,
    ) {
        var bytes: ByteArray? = null
        var id: String? = null
        var overwriteChanged = false
        var withoutPrevious = false
        var converted = false
    }

    private var session: Session? = null
    private var loadingKey: Pair<String, Long>? = null
    private var loadJob: Job? = null
    private var watchJob: Job? = null
    private var findJob: Job? = null
    private var attempt: Attempt? = null

    /** '이어서 고치기 / 버리기' 를 묻는 동안의 초안. 답하기 전에는 초안을 쓰지도 지우지도 않는다. */
    private var pendingDraft: TextDrafts.Draft? = null

    /** 큐에서 돌아온 결과 — [TextSaveJobs] 가 작업 스레드에서 넣고 여기서 차례로 받는다. */
    private val results = Channel<FileOpManager.Finished>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (f in results) onResult(f)
        }
    }

    // ---- 열기 ---------------------------------------------------------------------------------

    /**
     * @param encodingName 뷰어에서 손으로 고른 인코딩(`TextEncoding.name`). null 이면 판정한다.
     * @param token 편집 세션의 표 — 회전·프로세스 재생성에는 같은 값, 화면을 떠났다 돌아오면 새 값.
     */
    fun open(path: String, encodingName: String?, token: Long) {
        viewerSettings.enter(token, viewerKindOf(path))
        val cur = session
        if (cur != null && cur.path == path && cur.token == token) return
        if (loadingKey == path to token) return
        closeSession()
        loadingKey = path to token
        loadJob = viewModelScope.launch {
            try {
                load(path, encodingOf(encodingName), token)
            } finally {
                if (loadingKey == path to token) loadingKey = null
            }
        }
    }

    private suspend fun load(path: String, forcedByViewer: TextEncoding?, token: Long) {
        _screen.value = Screen.Loading
        val app = getApplication<Application>()
        val read = withContext(IroDispatchers.io) { readForEdit(path) }
        if (read is Read.Refused) {
            _screen.value = Screen.Refused(read.why, limitBytes = TextLimits.EDIT_MAX_BYTES)
            return
        }
        read as Read.Ok
        val draftDir = TextDrafts.dirOf(app.filesDir)
        val draftFile = TextDrafts.fileOf(draftDir, read.canonicalPath)
        val draft = withContext(IroDispatchers.io) {
            TextDrafts.purgeStale(draftDir)
            TextDrafts.read(draftFile)
        }
        // 뷰어에서 고른 인코딩이 먼저다. 없으면 초안을 쓰던 인코딩(그 세션이 고른 것이었을 수 있다), 그것도 없으면 판정한다.
        val forced = forcedByViewer ?: draft?.encoding?.let(::encodingOf)
        val decoded = withContext(IroDispatchers.parsing) { EditCodec.decode(read.bytes, read.bytes.size, forced) }
        if (decoded is EditCodec.Decoded.Refused) {
            _screen.value = Screen.Refused(refusalOf(decoded.why), decoded.encoding)
            return
        }
        decoded as EditCodec.Decoded.Ok
        val s = Session(
            path = path,
            token = token,
            name = File(path).name,
            kind = viewerKindOf(path),
            state = TextFieldState(decoded.text, TextRange(0)),
            draftFile = draftFile,
            format = decoded.format,
            baseline = decoded.text,
            stamp = read.stamp,
        )
        session = s
        if (draft != null) {
            when (val choice = TextDrafts.choiceFor(draft, decoded.text, token, read.stamp)) {
                TextDrafts.Choice.Discard -> launchDraftDelete(draftFile)
                // 연 때의 표는 그 세션의 것이다 — 그사이 밖에서 바뀌었으면 저장할 때 묻는다.
                TextDrafts.Choice.Continue -> {
                    applyDraft(s, draft)
                    s.stamp = FileStamp(draft.baseSize, draft.baseModified)
                }
                is TextDrafts.Choice.Ask -> {
                    pendingDraft = draft
                    _question.value = Question.Resume(changedSince = choice.changedSince)
                }
            }
        }
        _screen.value = Screen.Ready(s.name, s.state, s.format, s.kind)
        watch(s)
    }

    /** 초안의 글을 **고침으로** 얹는다 — 되돌리기 한 번이면 파일의 글로 돌아간다. */
    private fun applyDraft(s: Session, draft: TextDrafts.Draft) {
        s.state.edit {
            replace(0, length, draft.text)
            val start = draft.selectionStart.coerceIn(0, length)
            selection = TextRange(start, draft.selectionEnd.coerceIn(start, length))
        }
    }

    /** '이어서 고치기'. 그 뒤에 파일이 바뀌었다고 말한 물음이었으면 그것을 알고 고른 것이다 — 지금 파일의 표로 잇는다. */
    fun resumeDraft() {
        val draft = pendingDraft ?: return
        val s = session ?: return
        pendingDraft = null
        _question.value = null
        applyDraft(s, draft)
    }

    /** '버리기' — 초안을 지우고 파일의 글로 고친다. */
    fun discardDraft() {
        pendingDraft ?: return
        val s = session ?: return
        pendingDraft = null
        _question.value = null
        launchDraftDelete(s.draftFile)
    }

    // ---- 바뀜을 따라가기 -----------------------------------------------------------------------

    /**
     * 글이 바뀔 때마다 — 잠깐 쉬고([SETTLE_MS]) '고친 것이 있는가'·줄 수·찾은 수를 다시 재고, 더 쉬면([DRAFT_IDLE_MS]) 초안을
     * 적는다. `collectLatest` 라 치는 동안에는 앞의 셈을 버린다 — 글자 하나마다 파일 크기만큼 셈하지 않는다.
     */
    private fun watch(s: Session) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            snapshotFlow { s.state.text }.collectLatest { seq ->
                delay(SETTLE_MS)
                val text = seq.toString()
                val (dirty, lines) = withContext(IroDispatchers.parsing) { (text != s.baseline) to lineCountOf(text) }
                _dirty.value = dirty
                _lines.value = lines
                if (_find.value.open) refreshFind()
                delay(DRAFT_IDLE_MS - SETTLE_MS)
                writeDraft(s, text, s.state.selection, dirty)
            }
        }
    }

    private suspend fun writeDraft(s: Session, text: String, selection: TextRange, dirty: Boolean) {
        if (session !== s || pendingDraft != null || s.discarded) return
        try {
            if (dirty) TextDrafts.write(s.draftFile, draftOf(s, text, selection)) else TextDrafts.delete(s.draftFile)
        } catch (e: IOException) {
            // 초안을 못 적어도 편집은 그대로 간다 — 저장은 따로다.
            Iro.d { "초안을 적지 못했다: ${e.javaClass.simpleName}" }
        }
    }

    private fun draftOf(s: Session, text: String, selection: TextRange) = TextDrafts.Draft(
        session = s.token,
        baseSize = s.stamp.size,
        baseModified = s.stamp.modified,
        encoding = s.format.encoding.name,
        selectionStart = selection.min,
        selectionEnd = selection.max,
        text = text,
    )

    /**
     * 지금의 글을 **곧바로** 초안에 적는다 — 앱이 뒤로 갔다(그 뒤 언제든 프로세스가 죽을 수 있다), 화면을 떠났다, 뷰모델이 치워진다.
     * 늦춘 쓰기를 기다리지 않는다. 쓰기는 프로세스의 범위에서 돈다 — 뷰모델이 곧 사라져도 끝난다.
     */
    fun flushDraft() {
        val s = session ?: return
        if (pendingDraft != null || s.discarded) return
        val text = s.state.text.toString()
        val draft = draftOf(s, text, s.state.selection)
        val dirty = text != s.baseline
        TextDrafts.scope.launch {
            try {
                if (dirty) TextDrafts.write(s.draftFile, draft) else TextDrafts.delete(s.draftFile)
            } catch (e: IOException) {
                Iro.d { "초안을 적지 못했다: ${e.javaClass.simpleName}" }
            }
        }
    }

    private fun launchDraftDelete(file: File) {
        TextDrafts.scope.launch {
            try {
                TextDrafts.delete(file)
            } catch (e: IOException) {
                Iro.d { "초안을 지우지 못했다: ${e.javaClass.simpleName}" }
            }
        }
    }

    /**
     * 화면을 **떠났다**(회전이 아니다 — 화면이 가른다). 고친 것이 있으면 초안에 적고 세션을 놓는다 — 글을 다음에 이 화면에 들어올
     * 때까지 쥐지 않는다. 앱의 결과 줄이 앞을 바꾸는 길(풀기의 '열기' 등)로 닫는 물음 없이 떠나도 고친 글은 초안에 남는다.
     */
    fun onScreenLeft() {
        flushDraft()
        closeSession()
    }

    override fun onCleared() {
        flushDraft()
        closeSession()
    }

    private fun closeSession() {
        loadJob?.cancel()
        watchJob?.cancel()
        findJob?.cancel()
        // 돌고 있는 저장은 끝까지 간다 — 그 결과는 앱의 결과 줄이 말한다(받을 편집기가 없다).
        attempt?.id?.let(TextSaveJobs::forget)
        attempt = null
        session = null
        pendingDraft = null
        loadingKey = null
        _saving.value = null
        _question.value = null
        _dirty.value = false
        _reveal.value = null
        _find.value = FindState()
        _screen.value = Screen.Loading
        // 앞 세션의 알림이 다음 세션에서 뜨지 않게.
        while (_events.tryReceive().isSuccess) Unit
    }

    // ---- 저장 ---------------------------------------------------------------------------------

    /** 저장한다. [closeAfter] 면 다 저장한 뒤 닫는다(닫기 물음의 '저장'). */
    fun save(closeAfter: Boolean = false) {
        val s = session ?: return
        val running = attempt
        if (running != null) {
            if (closeAfter) running.closeAfter = true
            return
        }
        if (_question.value != null) return
        val a = Attempt(s, s.state.text.toString(), s.format, closeAfter)
        attempt = a
        _saving.value = Saving(null)
        proceed(a)
    }

    /** 묻는 차례 — 섞인 줄 끝 → 쓸 수 없는 글자 → 큐. */
    private fun proceed(a: Attempt) {
        val mixed = a.format.mixed
        if (mixed != null && !a.session.mixedConfirmed) {
            _question.value = Question.MixedEndings(mixed, a.format.lineEnding)
            return
        }
        viewModelScope.launch {
            when (val encoded = withContext(IroDispatchers.parsing) { EditCodec.encode(a.text, a.format) }) {
                is EditCodec.Encoded.Ok -> {
                    a.bytes = encoded.bytes
                    enqueue(a)
                }
                is EditCodec.Encoded.Unmappable -> {
                    if (attempt !== a) return@launch
                    if (a.format.encoding == TextEncoding.UTF_8) {
                        endAttempt()
                        _events.trySend(Event.Unencodable)
                        return@launch
                    }
                    // 그 글자를 골라 보인다 — 무엇 때문에 묻는지 화면에서 보인다(글이 그사이 바뀌지 않았을 때만).
                    val s = a.session
                    if (session === s && s.state.text.toString() == a.text) {
                        s.state.edit { selection = TextRange(encoded.index, minOf(encoded.index + encoded.sample.length, length)) }
                        _reveal.value = encoded.index
                    }
                    _question.value = Question.Unmappable(a.format.encoding, encoded.sample)
                }
            }
        }
    }

    private fun enqueue(a: Attempt) {
        if (attempt !== a) return
        val s = a.session
        val bytes = a.bytes ?: return
        val id = UUID.randomUUID().toString()
        a.id = id
        _saving.value = Saving(id)
        // 결과를 받을 자리를 **넣기 전에** 건다 — 넣자마자 끝난 결과가 먼저 오면 앱의 결과 줄로 새어 나간다.
        TextSaveJobs.expect(id) { f -> results.trySend(f) }
        FileOpManager.enqueue(
            getApplication(),
            FileOpManager.Request.SaveText(
                id = id,
                path = s.path,
                bytes = bytes,
                expected = s.stamp,
                keepPrevious = !s.savedOnce,
                overwriteChanged = a.overwriteChanged,
                withoutPrevious = a.withoutPrevious,
                sink = TextSaveJobs.sink,
            ),
        )
    }

    private fun onResult(f: FileOpManager.Finished) {
        val a = attempt?.takeIf { it.id == f.id } ?: return
        val s = a.session
        when (val o = f.outcome) {
            is FileOpEngine.Outcome.Done -> {
                val saved = f.extra as? FileOpManager.Extra.TextSaved
                saved?.let { s.stamp = it.stamp }
                s.baseline = a.text
                // 저장한 파일의 줄 끝은 이제 하나다 — 다음 저장에서 다시 묻지 않는다.
                s.format = a.format.copy(mixed = null)
                s.savedOnce = true
                endAttempt()
                // 뷰어가 옛 색인을 버리고 다시 읽게(인코딩이 바뀌었을 수 있다).
                TextSaves.record(s.path, s.format.encoding)
                if (a.converted) _screen.value = Screen.Ready(s.name, s.state, s.format, s.kind)
                val now = s.state.text.toString()
                val dirty = now != a.text
                _dirty.value = dirty
                // 저장하는 사이에 더 고쳤으면 초안은 그 글로, 아니면 지운다.
                if (dirty) {
                    val draft = draftOf(s, now, s.state.selection)
                    TextDrafts.scope.launch {
                        try {
                            TextDrafts.write(s.draftFile, draft)
                        } catch (e: IOException) {
                            Iro.d { "초안을 적지 못했다: ${e.javaClass.simpleName}" }
                        }
                    }
                } else {
                    launchDraftDelete(s.draftFile)
                }
                _events.trySend(Event.Result(f, converted = a.converted))
                if (a.closeAfter && !dirty) _events.trySend(Event.Close)
            }
            is FileOpEngine.Outcome.Failed -> when (o.reason) {
                FileOpEngine.Reason.CHANGED_ELSEWHERE -> {
                    _saving.value = null
                    _question.value = Question.ChangedElsewhere
                }
                FileOpEngine.Reason.PREVIOUS_NOT_KEPT -> {
                    _saving.value = null
                    _question.value = Question.PreviousNotKept
                }
                else -> {
                    endAttempt()
                    _events.trySend(Event.Result(f))
                }
            }
            is FileOpEngine.Outcome.Cancelled -> {
                endAttempt()
                _events.trySend(Event.Result(f))
            }
        }
    }

    private fun endAttempt() {
        attempt = null
        _saving.value = null
    }

    /** 섞인 줄 끝을 [Question.MixedEndings] 의 줄 끝으로 맞추고 저장한다. */
    fun confirmMixedEndings() {
        val a = attempt ?: return
        a.session.mixedConfirmed = true
        _question.value = null
        proceed(a)
    }

    /** 'UTF-8 로 바꿔 저장'. */
    fun convertToUtf8() {
        val a = attempt ?: return
        a.format = a.format.asUtf8()
        a.converted = true
        _question.value = null
        proceed(a)
    }

    /** '덮어쓰기' — 밖에서 바뀐 것을 알고도 쓴다. */
    fun overwriteChanged() {
        val a = attempt ?: return
        a.overwriteChanged = true
        _question.value = null
        enqueue(a)
    }

    /** '그래도 저장' — 고치기 전 판을 남기지 못해도 쓴다. */
    fun saveWithoutPrevious() {
        val a = attempt ?: return
        a.withoutPrevious = true
        _question.value = null
        enqueue(a)
    }

    /** 저장의 물음을 '취소' 로 닫았다 — 저장을 그만둔다(닫으려던 것도 그만둔다). */
    fun cancelSaveQuestion() {
        _question.value = null
        endAttempt()
    }

    // ---- 닫기 ---------------------------------------------------------------------------------

    /**
     * 닫기를 눌렀다(뒤로 가기·화살표). 곧바로 닫아도 되면 참 — 아니면 묻거나(저장하지 않은 고침), 저장이 끝나면 닫는다([Event.Close]).
     * '고친 것이 있는가' 는 그 자리에서 다시 잰다(늦게 따라오는 [dirty] 로 정하면 막 친 글자를 잃는다).
     */
    fun requestClose(): Boolean {
        val s = session ?: return true
        val running = attempt
        if (running != null) {
            running.closeAfter = true
            _events.trySend(Event.ClosingAfterSave)
            return false
        }
        if (s.state.text.toString() == s.baseline) return true
        _question.value = Question.CloseUnsaved
        return false
    }

    /** 닫기 물음의 '저장'. */
    fun closeSaving() {
        _question.value = null
        save(closeAfter = true)
    }

    /** 닫기 물음의 '저장하지 않음' — 고친 글을 버린다(초안도). */
    fun closeDiscarding() {
        _question.value = null
        val s = session ?: return
        s.discarded = true
        launchDraftDelete(s.draftFile)
        _events.trySend(Event.Close)
    }

    /** 닫기 물음의 '취소'. */
    fun cancelClose() {
        _question.value = null
    }

    // ---- 찾기·바꾸기 --------------------------------------------------------------------------

    fun openFind() {
        _find.update { it.copy(open = true) }
        refreshFind()
    }

    fun closeFind() {
        findJob?.cancel()
        _find.update { it.copy(open = false, total = 0, current = 0) }
    }

    fun setQuery(query: String) {
        _find.update { it.copy(query = query) }
        refreshFind()
    }

    fun setReplacement(replacement: String) {
        _find.update { it.copy(replacement = replacement) }
    }

    fun toggleIgnoreCase() {
        _find.update { it.copy(ignoreCase = !it.ignoreCase) }
        refreshFind()
    }

    fun findNext() {
        val s = session ?: return
        val f = _find.value
        if (f.query.isEmpty()) return
        val at = FindText.next(s.state.text, f.query, s.state.selection.max, f.ignoreCase)
        if (at < 0) {
            _events.trySend(Event.NotFound)
            return
        }
        select(s, at, at + f.query.length)
    }

    fun findPrevious() {
        val s = session ?: return
        val f = _find.value
        if (f.query.isEmpty()) return
        val at = FindText.previous(s.state.text, f.query, s.state.selection.min, f.ignoreCase)
        if (at < 0) {
            _events.trySend(Event.NotFound)
            return
        }
        select(s, at, at + f.query.length)
    }

    /** 고른 글이 찾는 말이면 바꾸고 다음 것을 고른다. 아니면 다음 것을 고르기만 한다(무엇을 바꾸는지 먼저 보인다). */
    fun replaceOne() {
        val s = session ?: return
        val f = _find.value
        if (f.query.isEmpty()) return
        val sel = s.state.selection
        if (FindText.selects(s.state.text, f.query, sel.min, sel.max, f.ignoreCase)) {
            val replacement = LineEndings.normalize(f.replacement)
            s.state.edit {
                replace(sel.min, sel.max, replacement)
                selection = TextRange(sel.min + replacement.length)
            }
        }
        findNext()
    }

    /** 모두 바꾼다 — 고침 하나로(되돌리기 한 번이면 모두 돌아온다). */
    fun replaceAll() {
        val s = session ?: return
        val f = _find.value
        if (f.query.isEmpty()) return
        val starts = FindText.all(s.state.text, f.query, f.ignoreCase)
        if (starts.isEmpty()) {
            _events.trySend(Event.NotFound)
            return
        }
        val replacement = LineEndings.normalize(f.replacement)
        val length = f.query.length
        s.state.edit {
            // 뒤에서부터 바꾼다 — 앞의 자리가 밀리지 않는다.
            for (i in starts.indices.reversed()) replace(starts[i], starts[i] + length, replacement)
        }
        _events.trySend(Event.Replaced(starts.size))
        refreshFind()
    }

    private fun select(s: Session, start: Int, end: Int) {
        s.state.edit { selection = TextRange(start, end) }
        _reveal.value = start
        refreshFind()
    }

    /** 찾은 수와 지금 것이 몇 번째인가를 다시 잰다 — 글 전체를 한 번 훑는다(파싱 디스패처에서). */
    private fun refreshFind() {
        val s = session ?: return
        val f = _find.value
        findJob?.cancel()
        if (!f.open || f.query.isEmpty()) {
            _find.update { it.copy(total = 0, current = 0) }
            return
        }
        // 글은 바뀌지 않는 판이다(`TextFieldState.text` 는 고칠 때마다 새 판을 준다) — 다른 스레드에서 읽어도 된다.
        val text = s.state.text
        val sel = s.state.selection
        findJob = viewModelScope.launch {
            val starts = withContext(IroDispatchers.parsing) { FindText.all(text, f.query, f.ignoreCase) }
            val current = FindText.position(starts, sel.min, sel.max, f.query.length)
            _find.update {
                if (it.query == f.query && it.ignoreCase == f.ignoreCase) it.copy(total = starts.size, current = current) else it
            }
        }
    }

    // ---- 읽기 ---------------------------------------------------------------------------------

    private sealed interface Read {
        class Ok(val canonicalPath: String, val bytes: ByteArray, val stamp: FileStamp) : Read
        data class Refused(val why: Refusal) : Read
    }

    /**
     * 편집할 바이트와 그 표를 **같은 판으로** 읽는다 — 읽기 앞뒤의 표가 같을 때까지 몇 번 다시 읽는다. 끝내 흔들리면 읽기 **전**의
     * 표를 든다 — 지금 파일과 다를 것이라 저장할 때 '밖에서 바뀌었다' 를 묻는다(조용히 덮어쓰지 않는다).
     */
    private fun readForEdit(path: String): Read {
        when (TextSaver.placeOf(path)) {
            TextSaver.Place.OK -> Unit
            TextSaver.Place.OUTSIDE -> return Read.Refused(Refusal.PLACE)
            TextSaver.Place.NOT_FILE -> return Read.Refused(Refusal.UNREADABLE)
            TextSaver.Place.READ_ONLY -> return Read.Refused(Refusal.READ_ONLY)
        }
        val file = try {
            File(path).canonicalFile
        } catch (e: IOException) {
            return Read.Refused(Refusal.UNREADABLE)
        }
        var last: Read? = null
        repeat(READ_TRIES) {
            val before = FileStamp.of(file) ?: return Read.Refused(Refusal.UNREADABLE)
            if (before.size > TextLimits.EDIT_MAX_BYTES) return Read.Refused(Refusal.TOO_LARGE)
            val bytes = try {
                readBounded(file, TextLimits.EDIT_MAX_BYTES.toInt())
            } catch (e: IOException) {
                return Read.Refused(Refusal.UNREADABLE)
            } ?: return Read.Refused(Refusal.TOO_LARGE)
            val ok = Read.Ok(file.path, bytes, before)
            if (FileStamp.of(file) == before && bytes.size.toLong() == before.size) return ok
            last = ok
        }
        return last ?: Read.Refused(Refusal.UNREADABLE)
    }

    /**
     * [max] 바이트까지 읽는다. 넘으면 null. `File.readBytes()` 는 쓰지 않는다(크기를 묶지 못한다 — 코드 규칙), `readNBytes` 는 API 33
     * 부터다(함정 표).
     */
    private fun readBounded(file: File, max: Int): ByteArray? = FileInputStream(file).use { input ->
        var buf = ByteArray(minOf(maxOf(file.length(), 0L), max.toLong()).toInt() + 1)
        var n = 0
        while (true) {
            if (n == buf.size) {
                if (buf.size > max) return@use null
                buf = buf.copyOf(minOf(buf.size * 2, max + 1))
            }
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        if (n > max) null else buf.copyOf(n)
    }

    private companion object {
        /** 글이 바뀐 뒤 '고친 것이 있는가'·줄 수를 재기까지 쉬는 시간. */
        const val SETTLE_MS = 150L

        /** 글이 바뀐 뒤 초안을 적기까지 쉬는 시간('고칠 때마다, 늦춰서'). */
        const val DRAFT_IDLE_MS = 2_000L

        /** 읽기 앞뒤의 표가 같을 때까지 다시 읽는 수. */
        const val READ_TRIES = 3

        fun encodingOf(name: String?): TextEncoding? = name?.let { n -> TextEncoding.entries.firstOrNull { it.name == n } }

        fun refusalOf(why: EditCodec.Why): Refusal = when (why) {
            EditCodec.Why.BINARY -> Refusal.BINARY
            EditCodec.Why.UNSUPPORTED_ENCODING -> Refusal.UNSUPPORTED_ENCODING
            EditCodec.Why.MALFORMED -> Refusal.MALFORMED
            EditCodec.Why.NOT_ROUND_TRIP -> Refusal.NOT_ROUND_TRIP
        }

        fun lineCountOf(text: String): Int {
            var n = 1
            for (c in text) if (c == '\n') n++
            return n
        }
    }
}
