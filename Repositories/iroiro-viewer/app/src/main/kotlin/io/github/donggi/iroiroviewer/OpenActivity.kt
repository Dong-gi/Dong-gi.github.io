package io.github.donggi.iroiroviewer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewModelScope
import io.github.donggi.iroiroviewer.io.ContentSniff
import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.IncomingFiles
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.playback.FolderQueue
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import io.github.donggi.iroiroviewer.playback.PlayerSupport
import io.github.donggi.iroiroviewer.playback.SubtitleNames
import io.github.donggi.iroiroviewer.ui.IroProgress
import io.github.donggi.iroiroviewer.ui.IroiroTheme
import io.github.donggi.iroiroviewer.ui.LocalViewerWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * **다른 앱이 넘긴 파일을 연다**(14단계 뒤 — 사용자 요청 '다른 앱에서 이 앱으로 열 수 없음 => 추가해주세요'). 메일·메신저의 첨부,
 * 브라우저가 받은 파일, 다른 파일 관리자의 '다음으로 열기'(`ACTION_VIEW`)와 문서·압축의 '공유'(`ACTION_SEND`)가 여기로 온다.
 *
 * 받은 URI 는 `IncomingFiles` 가 경로로 바꾼다(제자리 또는 앱 캐시의 사본 — 그 KDoc). 그다음은 파일 목록에서 누른 것과 같다 —
 * 같은 [Root] 를 그 뷰어에서 시작하게 띄운다([Opened]). 소리·영상은 재생기에 넘기고 이 화면은 끝난다.
 *
 * **[MainActivity] 와 따로 둔 까닭은 태스크다.** 이 화면은 보낸 앱의 태스크에 쌓이고(`taskAffinity` 가 비었다 — 보낸 쪽이 새
 * 태스크로 띄워도 우리 파일 목록 위에 얹히지 않는다), 뷰어를 닫으면 끝나서 **보낸 앱으로 돌아간다.** 파일 목록 위에 얹히면 뒤로
 * 가기가 보낸 앱이 아니라 우리 목록으로 간다. 최근 앱에서도 끝나면 사라진다(`autoRemoveFromRecents`).
 *
 * 받는 형식은 매니페스트의 필터가 정한다 — 그림·소리·영상·글의 계열 전체, 이 앱이 여는 문서·압축·만화의 형식,
 * `application/octet-stream`. **APK·글꼴은 받지 않는다** — 이 앱에 뷰어가 없고, 받으면 우리 '다른 앱으로 열기' 가 우리를 다시
 * 고르는 고리가 된다(`ExternalOpen` 의 KDoc '우리 자신'). 형식이 맞아도 뷰어가 없는 파일(이름이 알려 주는 종류가 기타)은 이름과
 * '다른 앱으로 열기' 를 보인다.
 *
 * **내보낸 액티비티라 인텐트의 모든 것이 남이 적은 값이다.** 받는 것은 데이터 URI(또는 공유의 스트림)와 형식뿐이고, extras 를
 * ViewModel 의 저장 상태로 넘기지 않는다([withoutIntentArgs]).
 */
class OpenActivity : ComponentActivity() {

    private val vm: OpenViewModel by viewModels()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = super.defaultViewModelCreationExtras.withoutIntentArgs()

    /** 뷰어가 창에 바라는 것을 이 창에 건다 — [MainActivity] 와 같은 장치다([ViewerWindowHost]). 받은 파일도 같은 뷰어로 연다. */
    private val viewerWindow = ViewerWindowHost(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 릴리스에서는 로그 호출 지점 자체가 사라진다. Iro 의 주석 참고.
        Iro.debug = BuildConfig.DEBUG
        enableEdgeToEdge()
        viewerWindow.start()
        vm.start(IncomingRequest.of(intent))
        // 재생기에 넘겼으면 끝난다. 화면(컴포지션)이 아니라 여기서 기다린다 — 재생 화면이 올라오면 이 화면은 멈추는데, 멈춘 화면의
        // 컴포지션은 그 값을 보지 못해 돌아왔을 때 빈 화면이 한 번 보인다.
        lifecycleScope.launch {
            vm.handedOff.first { it }
            finish()
        }
        setContent {
            CompositionLocalProvider(LocalViewerWindow provides viewerWindow.window) {
                IroiroTheme(systemBarIcons = false) {
                    OpenRoot(vm, onExit = ::finish)
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewerWindow.onUserInteraction()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        viewerWindow.onKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        viewerWindow.onKey(event) || super.onKeyUp(keyCode, event)

    override fun onStop() {
        super.onStop()
        viewerWindow.onStop()
    }

    /**
     * 이 화면이 맨 위에 있을 때 새로 넘어온 파일(`launchMode="singleTop"`). 보낸 쪽이 새 태스크로 띄우면 시스템은 이 화면을 뿌리로
     * 가진 옛 태스크를 찾아 앞으로 가져온다 — `taskAffinity` 가 비어 있어도 뿌리의 구성 요소가 같으면 그 태스크다. 그 위에 새 화면을
     * 쌓으면 새 파일을 닫을 때 옛 파일이 다시 보인다. 그래서 맨 위의 화면이 새 요청을 처음부터 다시 받는다.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.restart(IncomingRequest.of(intent))
    }
}

/**
 * **인텐트의 extras 를 ViewModel 의 저장 상태 기본값으로 넘기지 않는** 생성 정보. 내보낸 두 액티비티([OpenActivity]·[MainActivity])가
 * 쓴다.
 *
 * AndroidX 의 기본값(`ComponentActivity.defaultViewModelCreationExtras`)은 인텐트 extras 를 `DEFAULT_ARGS_KEY` 로 실어, 새로 만드는
 * `SavedStateHandle` 을 그것으로 채운다(내비게이션이 인자를 넘기는 길이다). 내보낸 액티비티에서는 그것이 구멍이다 — 남의 앱이 extras
 * 만으로 우리의 저장 상태를 지어내고(받기 화면의 '되살린 경로' 로 받기 검사를 통째로 건너뛰어 앱의 DB 를 뷰어에 띄우거나, 파일 목록이
 * 앱 내부 폴더에서 시작하게), 우리가 모르는 클래스를 실으면 그 상태를 만드는 순간 꺼내다 던져 **프로세스가 통째로 죽는다**(파일 작업·
 * 재생 서비스와 함께). 이 앱은 extras 를 저장 상태로 받는 화면이 없다.
 */
internal fun CreationExtras.withoutIntentArgs(): CreationExtras =
    MutableCreationExtras(this).apply { set(DEFAULT_ARGS_KEY, Bundle()) }

/** 다른 앱이 넘긴 것 — URI 하나와 보낸 쪽이 적은 형식. */
internal class IncomingRequest(val uri: Uri, val type: String?) {

    companion object {

        /** 받을 것이 없으면 null(모르는 동작·URI 없는 공유·글만 실은 공유). */
        fun of(intent: Intent?): IncomingRequest? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { IncomingRequest(it, intent.type) }
            Intent.ACTION_SEND -> streamOf(intent)?.let { IncomingRequest(it, intent.type) }
            else -> null
        }

        /**
         * 공유가 실은 파일. **보낸 쪽의 꾸러미를 믿지 않는다** — 우리가 모르는 클래스가 extras 에 들어 있으면 꺼내는 순간
         * `BadParcelableException` 이다. 그때는 `ClipData`(시스템이 공유의 URI 를 옮겨 담는다)를 본다.
         */
        private fun streamOf(intent: Intent): Uri? {
            val extra = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            } catch (e: RuntimeException) {
                null
            }
            return extra ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        }
    }
}

/**
 * 받은 파일을 어느 화면이 여는가. 파일 목록의 누름(`feature:browser` 의 `TapRoute`)과 같은 표다 — 그쪽은 그 모듈 안의 것이라 여기
 * 한 벌을 더 둔다. `else` 없는 `when` 이라 종류가 늘면 여기서 컴파일이 멈춘다(CLAUDE.md 함정 표의 '종류를 옮기면 …').
 */
internal enum class IncomingRoute {
    IMAGE,
    TEXT,
    ARCHIVE,
    COMIC,
    DOCUMENT,

    /** 소리·영상 — 재생기에 넘기고 이 화면은 끝난다. */
    PLAYER,

    /** 이 앱에 뷰어가 없다(APK·글꼴·기타). 이름과 '다른 앱으로 열기' 를 보인다. */
    NONE,
    ;

    /**
     * 이 갈래가 [Root] 에서 여는 화면. 재생기·뷰어 없음은 [Root] 를 거치지 않는다(null). 문서는 [editorKind] 가 있으면(편집기가 그 형식을
     * 쪽 그대로 보인다 — `EditorApp.viewKindFor`) 편집기의 보기 모드로, 없으면 이 앱의 문서 뷰어로.
     */
    fun screenFor(path: String, editorKind: String? = null): AppScreen? = when (this) {
        IMAGE -> AppScreen.Viewer(path)
        TEXT -> AppScreen.Text(path)
        ARCHIVE -> AppScreen.Archive(path)
        COMIC -> AppScreen.Comic(path, -1)
        DOCUMENT -> if (editorKind != null) AppScreen.Editor(path, editorKind) else AppScreen.Doc(path)
        PLAYER, NONE -> null
    }

    companion object {

        fun of(kind: FileKind): IncomingRoute = when (kind) {
            FileKind.AUDIO, FileKind.VIDEO -> PLAYER
            FileKind.IMAGE -> IMAGE
            FileKind.TEXT, FileKind.CODE -> TEXT
            FileKind.COMIC -> COMIC
            FileKind.ARCHIVE -> ARCHIVE
            FileKind.PDF, FileKind.EBOOK,
            FileKind.DOCUMENT, FileKind.SHEET, FileKind.SLIDE,
            FileKind.HWP -> DOCUMENT
            FileKind.FOLDER, FileKind.APK, FileKind.FONT, FileKind.OTHER -> NONE
        }

        /**
         * 파일로. `.ts`·`.mts` 는 앞머리로 가른다 — TypeScript 면 글이다(파일 목록의 누름과 같다). 디스크를 읽으므로 입출력
         * 디스패처에서 부른다.
         */
        fun of(file: File): IncomingRoute =
            if (ContentSniff.isTextNamedLikeStream(file)) TEXT else of(MimeResolver.kindOf(file.name, isDirectory = false))
    }
}

/**
 * 받기와 넘기기. 액티비티에 묶여 회전을 건너고, 받은 경로는 저장 상태에 남겨 프로세스가 죽었다 살아나도 다시 받지 않는다
 * (그 경로가 지금도 쓸 만한지 한 번 더 보고, 사본이 치워졌으면 다시 받는다 — 보낸 앱의 허락은 이 화면이 사는 동안 남는다).
 */
internal class OpenViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    sealed interface State {
        /** 받는 중. [total] 은 모르면 -1. */
        data class Receiving(val copied: Long, val total: Long) : State

        /** 받았다. [copy] 면 앱 캐시의 사본이다 — 읽기 전용으로 연다. */
        data class Ready(val path: String, val copy: Boolean, val route: IncomingRoute) : State

        data class Failed(@StringRes val message: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Receiving(0, -1))
    val state: StateFlow<State> = _state.asStateFlow()

    /** 요청의 차례. 새 파일이 넘어올 때마다([restart]) 하나씩 는다 — 화면이 이것을 열쇠로 처음부터 다시 그린다. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** 재생기에 넘겼다. 액티비티가 이것을 보고 끝난다. */
    private val _handedOff = MutableStateFlow(false)
    val handedOff: StateFlow<Boolean> = _handedOff.asStateFlow()

    private var started = false
    private var job: Job? = null
    private var handOff: Job? = null

    /**
     * 이 화면이 만든 사본. **다 만든 그 순간 쥔다**(`IncomingFiles.receive` 의 `onCopied`) — 돌려받기를 기다리면 그 사이에 화면이
     * 끝났을 때 사본이 아무도 모르게 남는다. 화면이 끝나거나 다른 파일로 바뀌면 지운다.
     */
    @Volatile
    private var copyPath: String? = null

    /** 재생기에 넘긴 사본 — 재생이 읽으므로 지우지 않는다(하루 뒤 치운다 — `IncomingFiles.purgeStale`). */
    @Volatile
    private var keepCopy = false

    /** 한 번만 받는다. 회전으로 다시 불려도 그대로 둔다. */
    fun start(request: IncomingRequest?) {
        if (started) return
        started = true
        val generation = _generation.value
        job = viewModelScope.launch { receive(request, generation) }
    }

    /** 새 파일이 넘어왔다([OpenActivity.onNewIntent]). 하던 것을 멈추고 앞의 사본을 지운 뒤 처음부터 받는다. */
    fun restart(request: IncomingRequest?) {
        job?.cancel()
        handOff?.cancel()
        handOff = null
        discardCopy()
        saved.remove<String>(KEY_PATH)
        saved.remove<Boolean>(KEY_COPY)
        saved.remove<String>(KEY_ROUTE)
        _handedOff.value = false
        _state.value = State.Receiving(0, -1)
        _generation.value++
        started = false
        start(request)
    }

    private suspend fun receive(request: IncomingRequest?, generation: Int) {
        val app = getApplication<Application>()
        // 프로세스가 죽었다 살아났다. 저장 상태는 우리가 쓴 것이지만(인텐트의 extras 는 들어오지 않는다 — withoutIntentArgs) 그
        // 경로가 지금도 쓸 만한지 한 번 더 본다 — 사본이면 우리 사본 폴더 안인지, 제자리면 지금도 제자리로 열리는지.
        restored()?.let { r ->
            if (withContext(IroDispatchers.io) { IncomingFiles.stillUsable(app, r.path, r.copy) }) {
                if (r.copy) copyPath = r.path
                _state.value = r
                return
            }
        }
        if (request == null) {
            _state.value = State.Failed(R.string.open_nothing)
            return
        }
        val type = request.type ?: withContext(IroDispatchers.io) { typeOf(app, request.uri) }
        withContext(IroDispatchers.io) { IncomingFiles.purgeStale(app, keep = ::isPlaying) }
        // 받는 일은 남의 제공자와 남이 적은 값을 다룬다. 우리가 미처 거르지 못한 실패가 이 화면(내보낸 액티비티)을 넘어
        // 앱을 죽이게 두지 않는다 — 읽지 못한 것으로 친다.
        val outcome = try {
            IncomingFiles.receive(
                app,
                request.uri,
                type,
                progress = { copied, total -> _state.value = State.Receiving(copied, total) },
                // 그 사이에 다른 파일로 바뀌었으면(restart) 이 사본을 받을 사람이 없다 — 곧바로 지운다.
                onCopied = { path ->
                    if (_generation.value == generation) copyPath = path else IncomingFiles.discardLater(app, path)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Iro.d { "받지 못했다: ${e.javaClass.simpleName}" }
            IncomingFiles.Outcome.Failed(IncomingFiles.Failure.UNREADABLE)
        } catch (e: java.io.IOException) {
            Iro.d { "받지 못했다: ${e.javaClass.simpleName}" }
            IncomingFiles.Outcome.Failed(IncomingFiles.Failure.UNREADABLE)
        }
        when (outcome) {
            is IncomingFiles.Outcome.Failed -> _state.value = State.Failed(IncomingFiles.messageOf(outcome.failure))
            is IncomingFiles.Outcome.Ok -> {
                val path = outcome.received.path
                val route = withContext(IroDispatchers.io) { IncomingRoute.of(File(path)) }
                Iro.d { "받았다: ${outcome.received.javaClass.simpleName} → $route" }
                val ready = State.Ready(path, outcome.received is IncomingFiles.Received.Copied, route)
                save(ready)
                _state.value = ready
                if (route == IncomingRoute.PLAYER) handToPlayer(ready)
            }
        }
    }

    /**
     * 소리·영상을 재생기에 넘긴다 — 파일 목록에서 누른 것과 같은 길(`PlaybackConnection.playQueue` + 재생 화면). 큐는 이 파일
     * 하나다(보낸 쪽이 고른 것은 그 파일이다). 제자리면 그 폴더의 자막 파일을 함께 넘겨 이름으로 짝지은 자막이 붙는다.
     * 재생 화면에서 뒤로 가면 보낸 앱으로 돌아간다(`fromOutside`).
     *
     * **화면이 아니라 여기서 돈다.** 화면의 효과로 돌리면 그 사이에 화면이 다시 설 때(밤 모드처럼 `configChanges` 에 없는 바뀜)
     * 깃발만 서고 넘기기는 끊겨, 아무것도 틀지 않은 채 끝났다.
     */
    private fun handToPlayer(ready: State.Ready) {
        if (handOff != null) return
        handOff = viewModelScope.launch {
            val app = getApplication<Application>()
            val file = File(ready.path)
            val (entry, subtitles) = withContext(IroDispatchers.io) {
                entryOf(file) to if (ready.copy) emptyList() else subtitlesBeside(file)
            }
            keepCopy = true
            PlaybackConnection.playQueue(app, FolderQueue.plan(listOf(entry), entry.path), subtitles)
            PlayerSupport.open(app, showPlaylist = entry.kind == FileKind.AUDIO, fromOutside = true)
            _handedOff.value = true
        }
    }

    override fun onCleared() {
        // 다 본 사본을 남기지 않는다 — 메일의 명세서 같은 첨부가 캐시에 쌓이지 않게. 화면이 끝날 때만 불린다(회전은 아니다).
        discardCopy()
    }

    private fun discardCopy() {
        val path = copyPath ?: return
        copyPath = null
        if (!keepCopy) IncomingFiles.discardLater(getApplication(), path)
        keepCopy = false
    }

    private fun save(ready: State.Ready) {
        saved[KEY_PATH] = ready.path
        saved[KEY_COPY] = ready.copy
        saved[KEY_ROUTE] = ready.route.name
    }

    private fun restored(): State.Ready? {
        val path = saved.get<String>(KEY_PATH) ?: return null
        val copy = saved.get<Boolean>(KEY_COPY) ?: return null
        val route = saved.get<String>(KEY_ROUTE)?.let { name -> IncomingRoute.entries.firstOrNull { it.name == name } }
            ?: return null
        return State.Ready(path, copy, route)
    }

    private companion object {
        const val KEY_PATH = "open.path"
        const val KEY_COPY = "open.copy"
        const val KEY_ROUTE = "open.route"

        /** 보낸 쪽이 형식을 적지 않았으면 제공자에게 묻는다. 제공자는 남의 코드라 무엇으로든 실패할 수 있다. */
        fun typeOf(context: Context, uri: Uri): String? = try {
            context.contentResolver.getType(uri)
        } catch (e: RuntimeException) {
            if (e is CancellationException) throw e
            null
        }

        /** 재생기가 지금 든 파일인가 — 그 사본은 하루가 지났어도 치우지 않는다. */
        fun isPlaying(file: File): Boolean =
            PlaybackConnection.state.value.fileKey == FileKey.of(file.name, file.length(), file.lastModified())

        fun entryOf(file: File): FileEntry = FileEntry(
            name = file.name,
            path = file.path,
            isDirectory = false,
            size = file.length(),
            lastModified = file.lastModified(),
            isHidden = file.name.startsWith('.'),
            isSymlink = false,
            kind = MimeResolver.kindOf(file.name, isDirectory = false),
        )

        fun subtitlesBeside(file: File): List<FileEntry> =
            file.parentFile?.listFiles { f -> f.isFile && SubtitleNames.isSubtitle(f.name) }
                ?.map(::entryOf)
                .orEmpty()
    }
}

/**
 * 받는 동안 → 그 뷰어([Root]) · 재생기 · 뷰어가 없음 · 실패. 새 파일이 넘어오면(요청의 차례가 바뀌면) 처음부터 다시 그린다 —
 * 앞 파일의 화면 상태(뷰어 자리 등)가 새 파일에 옮겨 가지 않게.
 */
@Composable
internal fun OpenRoot(vm: OpenViewModel, onExit: () -> Unit) {
    val generation by vm.generation.collectAsStateWithLifecycle()
    key(generation) { OpenContent(vm, onExit) }
}

@Composable
private fun OpenContent(vm: OpenViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is OpenViewModel.State.Receiving -> ReceivingScreen(s, onCancel = onExit)
        is OpenViewModel.State.Failed -> MessageScreen(name = null, message = stringResource(s.message), onClose = onExit)
        is OpenViewModel.State.Ready -> {
            val context = LocalContext.current
            // 편집기가 이 문서를 쪽 그대로 보이는가(오피스·한글) — 꾸러미 관리자에 한 번 묻는다(경로가 바뀔 때만).
            val editorKind = remember(s.path) { EditorApp.viewKindFor(context, File(s.path).name) }
            val screen = s.route.screenFor(s.path, editorKind)
            when {
                screen != null -> Root(opened = Opened(screen, readOnly = s.copy, exit = onExit))
                // 재생기에 넘기는 중 — 넘기는 일은 ViewModel 이, 끝내는 일은 액티비티가 한다.
                s.route == IncomingRoute.PLAYER -> Box(Modifier.fillMaxSize())
                else -> NoViewerScreen(s.path, onClose = onExit)
            }
        }
    }
}

/**
 * 받는 중. 제자리로 여는 것은 곧 끝나므로 **잠깐 기다렸다가** 그린다 — 그러지 않으면 열 때마다 '받는 중' 이 한 번 번쩍인다.
 * 취소는 기다리지 않고 화면을 끝낸다(복사는 `ViewModel` 과 함께 멈추고 남은 조각은 지워진다).
 */
@Composable
private fun ReceivingScreen(s: OpenViewModel.State.Receiving, onCancel: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(RECEIVING_DELAY_MS)
        shown = true
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        if (!shown) return@Scaffold
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.open_receiving), style = MaterialTheme.typography.titleMedium)
            val fraction = if (s.total > 0) (s.copied.toFloat() / s.total).coerceIn(0f, 1f) else null
            IroProgress(progress = fraction?.let { f -> { f } }, modifier = Modifier.fillMaxWidth())
            Text(
                text = if (s.total > 0) {
                    stringResource(R.string.open_receiving_of, Format.size(s.copied), Format.size(s.total))
                } else {
                    Format.size(s.copied)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.open_cancel)) }
        }
    }
}

/**
 * 이 앱에 뷰어가 없는 파일. 이름을 보이고 다른 앱으로 넘긴다 — 고르는 창에서는 우리가 빠진다(`ExternalOpen`). 넘긴 뒤에도 이 화면은
 * 남는다 — 받은 앱이 사본을 다 읽기 전에 끝나 사본을 지우지 않게(`OpenViewModel.onCleared`), 그리고 다른 앱을 다시 고를 수 있게.
 */
@Composable
private fun NoViewerScreen(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<Int?>(null) }
    MessageScreen(name = File(path).name, message = stringResource(R.string.open_no_viewer), onClose = onClose) {
        Button(onClick = { notice = ExternalOpen.messageOf(ExternalOpen.open(context, path, ExternalOpen.Mode.CHOOSE)) }) {
            Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with))
        }
        notice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MessageScreen(
    name: String?,
    message: String,
    onClose: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            actions()
            TextButton(onClick = onClose) { Text(stringResource(R.string.open_close)) }
        }
    }
}

/** 이보다 빨리 끝나면 '받는 중' 을 그리지 않는다. */
private const val RECEIVING_DELAY_MS = 300L
