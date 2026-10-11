package io.github.donggi.iroiroviewer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.archive.ArchiveScreen
import io.github.donggi.iroiroviewer.browser.BrowserScreen
import io.github.donggi.iroiroviewer.comic.ComicScreen
import io.github.donggi.iroiroviewer.browser.BrowserViewModel
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.browser.opResultMessage
import io.github.donggi.iroiroviewer.diag.DiagScreen
import io.github.donggi.iroiroviewer.docview.DocViewScreen
import io.github.donggi.iroiroviewer.image.ImageViewerScreen
import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.ShareHelper
import io.github.donggi.iroiroviewer.io.StorageAccess
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.settings.SettingsScreen
import io.github.donggi.iroiroviewer.text.TextEditScreen
import io.github.donggi.iroiroviewer.text.TextViewerScreen
import io.github.donggi.iroiroviewer.text.TextViewer
import io.github.donggi.iroiroviewer.ui.IroiroTheme
import io.github.donggi.iroiroviewer.ui.LocalViewerWindow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    /**
     * 인텐트의 extras 를 ViewModel 의 저장 상태로 넘기지 않는다([withoutIntentArgs]) — 런처 액티비티라 내보내져 있어서, 남의 앱이
     * extras 로 파일 목록의 자리(`browser.stack`)를 앱 내부 폴더로 지어내거나 모르는 클래스로 프로세스를 죽일 수 있었다.
     */
    override val defaultViewModelCreationExtras: CreationExtras
        get() = super.defaultViewModelCreationExtras.withoutIntentArgs()

    /** 뷰어가 창에 바라는 것(시스템 바·화면 켜 둠·볼륨 키)을 이 창에 건다 — 쥐는 쪽은 액티비티다([ViewerWindowHost]). */
    private val viewerWindow = ViewerWindowHost(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishIfDuplicateLaunch()) return
        // 릴리스에서는 로그 호출 지점 자체가 사라진다. Iro 의 주석 참고.
        Iro.debug = BuildConfig.DEBUG
        enableEdgeToEdge()
        viewerWindow.start()
        setContent {
            CompositionLocalProvider(LocalViewerWindow provides viewerWindow.window) {
                // 바의 아이콘 색은 [viewerWindow] 가 건다 — 테마가 함께 걸면 회전마다 뷰어의 값을 덮는다.
                IroiroTheme(systemBarIcons = false) {
                    Root()
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewerWindow.onUserInteraction()
    }

    // 볼륨 키 넘기기(만화, 설정) — 창이 키를 받기 전에 묻는다. 삼키면 소리가 바뀌지 않는다(`ViewerWindowHost.onKey`).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        viewerWindow.onKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        viewerWindow.onKey(event) || super.onKeyUp(keyCode, event)

    override fun onStop() {
        super.onStop()
        viewerWindow.onStop()
    }

    /**
     * **런처가 얹은 군더더기 인스턴스면 그 자리에서 끝낸다.**
     *
     * 사용자 지적: "앱 아이콘을 누르면 항상 최초 메인 화면으로 이동하는데, 액티비티가
     * 정리되지 않았다면 해당 액티비티를 이어서 하면 좋겠습니다."
     *
     * ## 왜 그런 일이 생기는가
     *
     * 런처 인텐트(`ACTION_MAIN` + `CATEGORY_LAUNCHER` + `FLAG_ACTIVITY_NEW_TASK`)를 받으면
     * 시스템은 같은 affinity 의 태스크를 찾아 앞으로 가져온다. 그런데 **새 인스턴스를
     * 만들지 않고 앞으로만 가져오는 것은 그 태스크의 base 인텐트가 이 인텐트와
     * `filterEquals` 로 같을 때뿐이다.** 태스크를 처음 만든 인텐트가 런처 인텐트가
     * 아니었으면(재생 화면을 여는 `FLAG_ACTIVITY_NEW_TASK` 인텐트, `am start -n`, 일부
     * 런처·바로가기) 둘이 같지 않고, 그러면 `launchMode` 가 standard 인 이 액티비티가
     * **하던 화면 위에 새로 하나 더 쌓인다.**
     *
     * 에뮬레이터에서 실제로 재현했다 — 태스크가 `[MainActivity, PlayerActivity]` 인 상태에서
     * 런처 인텐트를 보내면 `[MainActivity, PlayerActivity, MainActivity]` 가 된다.
     *
     * ## 왜 launchMode 로 고치지 않는가
     *
     * `singleTask` 나 `singleInstance` 로 바꾸면 런처 인텐트가 이 액티비티를 **태스크의
     * 뿌리로 되감으면서 그 위의 재생 화면을 지워 버린다.** 사용자가 바라는 것의 정반대다.
     * `singleTop` 은 이미 맨 위일 때만 듣는데 여기서 문제가 되는 상황은 맨 위가 아닐 때다.
     *
     * 그래서 **선언이 아니라 이 한 번의 검사로** 막는다. 군더더기 인스턴스를 끝내면 그
     * 아래에 그대로 살아 있던 화면이 드러난다. 인텐트에 든 것이 없으므로 버릴 것도 없다.
     *
     * `isTaskRoot` 검사가 함께 있어야 한다 — 뿌리 인스턴스는 정상적인 첫 실행이라
     * 끝내면 앱이 아예 안 뜬다.
     */
    private fun finishIfDuplicateLaunch(): Boolean {
        val launcherIntent = !isTaskRoot &&
            intent?.action == android.content.Intent.ACTION_MAIN &&
            intent?.hasCategory(android.content.Intent.CATEGORY_LAUNCHER) == true
        if (launcherIntent) {
            Iro.d { "런처가 얹은 군더더기 MainActivity 를 끝낸다 — 아래 화면을 이어서 쓴다" }
            finish()
        }
        return launcherIntent
    }
}

/** 이 프로세스에서 휴지통 정합성 검사를 이미 돌렸는가. 회전마다 다시 돌지 않게 한다. */
private var reconciledOnce = false

/**
 * 다른 앱이 넘긴 파일로 연 화면([OpenActivity])이 [Root] 에 주는 것.
 *
 * @param screen 처음 띄울 뷰어. 거기서 닫으면 [exit] 다 — 파일 목록으로 가지 않는다(그 화면은 보낸 앱의 태스크에 산다).
 * @param readOnly 앱 캐시에 둔 사본이다. 지우기·회전·풀기를 내지 않는다(`IncomingFiles`).
 */
internal class Opened(val screen: AppScreen, val readOnly: Boolean, val exit: () -> Unit)

/**
 * 권한이 없으면 왜 필요한지 설명하고 설정으로 보낸다. 있으면 브라우저로 넘긴다.
 *
 * 설정에서 돌아오는 것은 액티비티 결과가 아니라 단순한 재개라서, 화면이 다시 보일
 * 때마다 상태를 새로 읽는 것이 유일하게 맞는 방법이다.
 *
 * **다른 앱이 넘긴 파일**([opened])로 열면 권한 화면을 거치지 않는다 — 받은 파일은 권한 없이 읽히는 사본이거나, 권한이 있어서
 * 제자리로 연 파일이다(`IncomingFiles`). 그때는 파일 목록에 속한 일(알림 권한 묻기·휴지통 정리·푼 폴더로 가기)도 하지 않는다.
 */
@Composable
internal fun Root(modifier: Modifier = Modifier, opened: Opened? = null) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted()) }

    LifecycleResumeEffect(Unit) {
        granted = StorageAccess.isGranted()
        onPauseOrDispose { }
    }

    if (!granted && opened == null) {
        PermissionScreen(modifier)
        return
    }

    val vm: BrowserViewModel = viewModel()
    var screen by rememberSaveable(stateSaver = AppScreen.Saver) {
        mutableStateOf(opened?.screen ?: AppScreen.Browser)
    }
    // 뷰어를 닫으면 어디로 가는가. 보통은 파일 목록이다. 다른 앱이 넘긴 파일로 열었으면 이 화면을 끝낸다 — 보낸 앱으로 돌아간다.
    // 받은 압축 목록에서 연 만화만은 그 압축 목록으로 돌아간다(파일 목록에서 연 압축이 그렇듯 한 겹 위다). 받은 만화에서 넘긴 다음
    // 권은 처음 권으로 되돌아가지 않고 끝낸다 — 보낸 앱이 준 것은 책이지 책장이 아니다.
    val home: () -> Unit = {
        when {
            opened == null -> screen = AppScreen.Browser
            opened.screen is AppScreen.Archive && screen != opened.screen -> screen = opened.screen
            else -> opened.exit()
        }
    }
    val readOnly = opened?.readOnly == true
    // 뷰어에 넘긴 목록은 **메모리 참조**다. 사진 3,000장 경로를 저장 상태에 담으면
    // TransactionTooLarge 로 죽는다. 프로세스가 죽어 살아났을 때는 시작 경로 하나만
    // 남아 있고(AppScreen.Saver), 그 한 장을 먼저 띄운 뒤 폴더를 다시 나열해 좌우를 채운다.
    var viewerEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // ---- 편집기(`feature:editor` — 따로 된 프로세스 :editor) ---------------------------------------------------------
    // 편집기를 띄우는 중이거나 떠 있다(결과가 오기 전) — 그동안 온 문서 누름은 흘려보낸다. `startActivity` 는 받는 화면이
    // 뜨기 전에 돌아오고(함정 표) `:editor` 프로세스가 차갑게 서는 데 1초 가까이 걸려, 두 번 누르면 편집기가 두 겹 뜨고 앞 세션이 주인을 잃었다.
    // '다른 앱으로 열기' 처럼 시간(`SETTLE_MS`)으로 막지 않고 결과가 올 때까지 막는 것은 그 서는 시간이 그보다 길 수 있어서다 — 결과는 편집기가
    // 어떻게 끝나든 온다. 저장 상태에 두지 않는다(화면이 다시 서면 편집기가 이미 앞에 있다).
    var editorPending by remember { mutableStateOf(false) }
    // 문서를 편집기에 넘긴 세션의 표와 어디서 넘겼는가(문서 뷰어 'doc' / 텍스트 뷰어 'text' / 폴더의 새 문서 'browser'). 저장 상태에 둔다 —
    // 편집기가 앞에 있는 동안 이 프로세스가 거둬져도 결과를 받아 세션을 닫는다(`EditorSlots` 의 기록도 디스크에 있다).
    var editorToken by rememberSaveable { mutableStateOf<String?>(null) }
    var editorFrom by rememberSaveable { mutableStateOf<String?>(null) }
    // 바뀌면 문서 뷰어가 같은 경로를 다시 연다(편집기에서 돌아와 저장한 것을 보인다).
    var docReopen by remember { mutableIntStateOf(0) }
    // 바뀌면 텍스트 뷰어가 같은 경로를 다시 읽는다(편집기의 표 편집기에서 CSV 를 저장하고 돌아왔다).
    var textReopen by remember { mutableIntStateOf(0) }
    val editorOpenFailed = stringResource(R.string.editor_open_failed)
    val editorSavedNew = stringResource(R.string.editor_saved_new)
    val editorSavedNewKept = stringResource(R.string.editor_saved_new_kept)
    val editorSavedKept = stringResource(io.github.donggi.iroiroviewer.io.R.string.io_saved_doc_kept)
    // 읽는 동안 화면 켜 두기(분) — 편집기의 보기 모드에 실어 보낸다(그 프로세스는 이 설정 저장소를 열지 않는다 — DataStore 는 한 프로세스의 것이다).
    // 띄울 때 첫 값을 기다려 읽는다(`readerKeepScreenMinutes`) — 모아 둔 값을 쓰면 앱이 막 선 때(다른 앱이 넘긴 문서) 아직 초기값이라 설정과 다른 분이
    // 갔다.
    val prefs = remember { AppPreferences(context.applicationContext) }
    // 편집기가 쥔 세션을 붙든다 — 이 프로세스가 거둬졌다 다시 섰으면(표는 저장 상태로 되살아났다) 세션의 개수 상한이 그것을 지우지 않게.
    LaunchedEffect(editorToken) { editorToken?.let(EditorSlots::hold) }
    val editorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 결과의 데이터는 보지 않는다 — 무엇을 썼는지는 저장 자리(세션)가 안다. 닫으며 세션을 지운다. 읽기 전용 사본을 보기만 했으면
        // 세션이 없다(`summary` 가 null).
        val token = editorToken
        val from = editorFrom
        editorToken = null
        editorFrom = null
        editorPending = false
        scope.launch {
            // 세션을 닫는 일(기록 쓰기·자리 지우기)은 디스크 일이다 — 주 스레드 밖에서.
            val summary = token?.let { t -> withContext(IroDispatchers.io) { EditorSlots.close(context, t) } }
            // 다른 앱이 넘긴 문서를 보고 닫았다 — 이 화면도 끝난다. 알릴 것은 스낵바를 띄울 화면이 없어 토스트로 말한다.
            val leaving = from == FROM_VIEW && screen is AppScreen.Editor
            when (from) {
                // 원래 문서를 다시 연다 — 뷰어는 넘기기 전에 문서를 닫았다(`DocEdit`). 편집기가 형식을 바꿔 곁에 새 파일을 만들었어도(PDF → .docx)
                // 그 새 파일은 이 뷰어가 보이는 형식이 아니다(그것을 열어 '이 앱이 다루지 않는 문서입니다' 가 떴다). 새 파일은 아래 알림이
                // 말한다. 세션이 없어도(기록을 잃었다) 다시 연다 — 닫힌 채 '여는 중' 으로 남지 않게.
                FROM_DOC -> {
                    if (screen !is AppScreen.Doc && summary != null) screen = AppScreen.Doc(summary.original)
                    docReopen++
                }
                // CSV 를 표 편집기로 고쳤다 — 텍스트 뷰어가 원래 글을 다시 읽는다(편집기가 쓴 인코딩을 다시 판정한다). 새 파일은 아래 알림이 말한다.
                FROM_TEXT -> if (summary != null) {
                    val shown = summary.original
                    if (summary.last != null) TextViewer.changedElsewhere(shown)
                    screen = AppScreen.Text(shown)
                    textReopen++
                }
                // 보기 모드로 연 문서. 다른 앱이 넘긴 것이면 이 화면도 끝내고(`home`), 파일 목록에서 연 것이면 — 편집으로 바꿔
                // 저장했으면 — 목록을 새로 읽는다.
                FROM_VIEW -> if (leaving) home() else if (summary?.last != null) vm.refresh()
                // 폴더의 '+ 새 문서'.
                else -> if (summary != null) vm.refresh()
            }
            // 한 번 말한다 — 새로 만든 파일과, 고치기 전 판을 휴지통에 남긴 것(글 편집기는 저장할 때 말하는데 편집기의 저장은 어디서도 말하지
            // 않았다).
            val notice = summary?.let(EditorSlots::noticeOf) ?: return@launch
            val text = when {
                notice.newFile != null && notice.trashed -> editorSavedNewKept.format(notice.newFile)
                notice.newFile != null -> editorSavedNew.format(notice.newFile)
                else -> editorSavedKept.format(java.io.File(summary.original).name)
            }
            if (leaving) {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
            } else {
                vm.snackbar.showSnackbar(text)
            }
        }
    }
    /**
     * [path] 를 편집기로 연다([kind] 는 편집기의 형식 글자). [from] 이 [FROM_VIEW] 면 **보기 모드**(오피스·한글 문서의
     * 뷰어), 그 밖은 편집 모드다. 저장 자리(세션)를 열어 함께 넘긴다 — 보기 모드에서도 넘기므로 편집기의 '편집' 단추로 곧바로 고쳐
     * 저장할 수 있다. 읽기 전용 사본(다른 앱이 넘긴 것)은 자리 없이 보기만 한다. 넘기지 못하면 세션을 닫고 말한 뒤 `onFailed` 를 부른다.
     *
     * 세션을 여는 디스크 일과 설정 읽기는 주 스레드 밖에서 한 뒤 띄운다. 편집기를 띄우는 중이거나 떠 있으면
     * (`editorPending`) 아무것도 하지 않는다 — 두 번 누름이 편집기를 두 겹 띄우지 않게.
     */
    fun openInEditor(path: String, kind: String, from: String, onFailed: () -> Unit = {}) {
        if (!editorPending) {
            editorPending = true
            scope.launch {
                val view = from == FROM_VIEW
                var session: EditorSlots.Session? = null
                var started = false
                try {
                    val document = withContext(IroDispatchers.io) { ShareHelper.uriOf(context, path) }
                    if (document != null && !readOnly) {
                        // 폴더의 '+ 새 문서' 로 만든 빈 문서면 첫 저장에 빈 원본을 휴지통에 남기지 않는다.
                        session = withContext(IroDispatchers.io) { EditorSlots.open(context, path, kind, fresh = from == FROM_BROWSER) }
                    }
                    // 보기 모드는 읽는 화면이다 — 이 앱의 문서 뷰어가 하던 대로 화면을 켜 둔다(편집기가 건다).
                    val minutes = if (view) readerKeepScreenMinutes(prefs) else 0
                    // 고치러 왔는데 자리를 열지 못했으면 넘기지 않는다. 보러 왔으면 자리 없이도 보인다.
                    if (document != null && (session != null || view)) {
                        editorToken = session?.token
                        editorFrom = from
                        val mode = if (view) EditorApp.MODE_VIEW else EditorApp.MODE_EDIT
                        val slot = session?.let { EditorSlots.uriOf(context, it.token) }
                        started = try {
                            editorLauncher.launch(EditorApp.intent(context, document, kind, mode, java.io.File(path).name, slot, minutes))
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        } catch (e: SecurityException) {
                            false
                        }
                    }
                } finally {
                    if (!started) {
                        editorToken = null
                        editorFrom = null
                        editorPending = false
                        session?.let { s -> withContext(NonCancellable + IroDispatchers.io) { EditorSlots.close(context, s.token) } }
                    }
                }
                if (!started) {
                    onFailed()
                    vm.snackbar.showSnackbar(editorOpenFailed)
                }
            }
        }
    }
    /**
     * 문서를 연다(뷰어 교체). 편집기가 쪽을 PC 처럼 그리는 형식(한글·오피스·ODF — `EditorApp.viewKindFor`)이면 편집기의
     * 보기 모드로, 아니면(PDF·EPUB, 엔진 묶음 없이 지은 빌드) 이 앱의 문서 뷰어로. 파일 목록은 그대로 뒤에 남는다. 편집기를 띄우는 중(창이 뜨기
     * 전)에 온 누름은 흘려보낸다 — 두 번째 문서가 그 편집기 밑에 열리지 않게.
     */
    val openDocument: (String) -> Unit = { path ->
        if (!editorPending) {
            val kind = EditorApp.viewKindFor(context, java.io.File(path).name)
            if (kind == null) screen = AppScreen.Doc(path) else openInEditor(path, kind, FROM_VIEW) { screen = AppScreen.Doc(path) }
        }
    }

    if (opened == null) NotificationPermissionGate()

    // 지금 화면이 창에 바라는 것 — 어두운 뷰어면 바의 아이콘을 밝게, 읽는 화면이면 화면을 켜 둔다. 거는 것은 액티비티다
    // (`ViewerWindowHost`). 이 화면(Root)이 사라지면 거둔다 — 다른 앱에서 연 화면이 다른 파일로 바뀌는 길.
    val viewerWindow = LocalViewerWindow.current
    SideEffect { viewerWindow?.setScreen(darkBars = ViewerScreens.darkBars(screen), reading = ViewerScreens.reading(screen)) }
    DisposableEffect(viewerWindow) {
        onDispose { viewerWindow?.setScreen(darkBars = false, reading = false) }
    }

    // **작업 결과를 여기 한 곳에서 받는다.** 화면마다 받으면 그 화면이 사라지는 순간
    // 소비가 멎고, 그 뒤에 끝난 작업의 성패가 조용히 사라진다(3·4단계가 고친 실패 형태).
    //
    // 그래서 새 불변식이 생긴다 — **Root 가 그릴 수 있는 모든 화면은 `vm.snackbar` 에
    // 붙은 SnackbarHost 를 하나씩 가져야 한다.** 붙지 않은 화면으로 넘어가면
    // `showSnackbar` 가 영영 돌아오지 않아 그 뒤 모든 결과가 막힌다. 그 하나에 기대지
    // 않으려고 시간 상한도 함께 건다 — 보험이 싸다.
    val undoLabel = stringResource(io.github.donggi.iroiroviewer.browser.R.string.browser_undo)
    // 풀기가 끝나면 푼 폴더로 가는 단추(8단계가 14단계로 미룬 것). 갈 곳이 있는지는 결과가 정한다
    // (`FileOpManager.Finished.folderToOpen` — 하나도 못 풀고 실패했으면 없다).
    val openFolderLabel = stringResource(io.github.donggi.iroiroviewer.browser.R.string.browser_open_folder)
    // **`context.getString` 을 컴포저블 안에서 부르지 않는다.** 그 값은 구성 변경을
    // 따라가지 않아 언어를 바꿔도 옛 문자열이 남는다(lint `LocalContextGetResourceValueCall`).
    val appName = stringResource(R.string.app_name)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // **앞에 있을 때만 받는다.** 결과는 한 줄(`FileOpManager.results`)을 나눠 받는 것이라, 가려진 액티비티가 받으면 보이지 않는
        // 스낵바에 떴다 사라진다 — 다른 앱에서 연 화면(OpenActivity)이 푼 압축의 결과가 뒤에 깔린 파일 목록으로 가는 것이 그
        // 모양이다. 앞에 아무도 없는 동안 끝난 것은 줄에 남았다가 돌아왔을 때 뜬다(CLAUDE.md 함정 표의 '값을 지우는 소비자를 …').
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.opResults.collect { finished ->
                vm.onOperationFinished(finished)
                val trashed = (finished.extra as? FileOpManager.Extra.Trashed)?.uuids
                // 푼 폴더로 가는 단추는 파일 목록이 있는 쪽에서만 — 다른 앱에서 연 화면에는 목록이 없다.
                val openable = if (opened == null) vm.folderToOpen(finished) else null
                val action = when {
                    !trashed.isNullOrEmpty() -> undoLabel
                    openable != null -> openFolderLabel
                    else -> null
                }
                val result = withTimeoutOrNull(10_000) {
                    vm.snackbar.showSnackbar(
                        message = opResultMessage(context, finished),
                        actionLabel = action,
                        // 동작 단추를 붙이면 기본 기간이 무기한이 된다(CLAUDE.md 함정 표) — 손으로 적는다.
                        duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) {
                    when {
                        !trashed.isNullOrEmpty() -> vm.undoTrash(trashed)
                        openable != null -> {
                            // 압축 화면에서 풀었을 수 있다 — 브라우저로 돌아가 그 폴더를 연다.
                            vm.openFolder(openable)
                            screen = AppScreen.Browser
                        }
                    }
                }
            }
        }
    }

    // 재생 화면이 뒤로 가며 연 파일 목록이면 지금 트는 파일의 폴더에서 시작한다(`FileListLauncher`). 위 결과 받기와 같은 까닭으로
    // 앞에 있을 때만 꺼낸다 — 가려진 파일 목록이 꺼내면 보이지 않는 화면이 그 폴더로 갈 뿐이다.
    if (opened == null) {
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                FileListRequests.pending.collect { request ->
                    if (request == null) return@collect
                    FileListRequests.consume(request)
                    vm.openFolder(request.folder)
                    screen = AppScreen.Browser
                }
            }
        }
    }

    // 기간이 지난 휴지통 항목을 지우고 기록과 파일을 맞춘다. 다른 앱이 휴지통 폴더를
    // 건드렸거나 볼륨을 포맷했을 수 있으므로 시작할 때 한 번 본다.
    //
    // **프로세스당 한 번이다.** `LaunchedEffect(Unit)` 은 액티비티가 다시 만들어질
    // 때마다(화면 회전) 다시 발화하는데, 이것은 파일을 지울 수 있는 작업이라 회전마다
    // 도는 것이 맞지 않는다.
    LaunchedEffect(Unit) {
        if (opened == null && !reconciledOnce) {
            reconciledOnce = true
            vm.reconcileTrash()
        }
    }

    when (val s = screen) {
        is AppScreen.Diag -> {
            // 돌아갈 곳은 연 자리가 정한다 — 첫 화면이면 첫 화면, 설정이면 설정(`AppScreen.Diag.back`).
            BackHandler { screen = s.back }
            // 진단 화면은 제목 막대가 없다(`feature:diag` 는 목록만 그린다). 설정에서 들어온 사람이 돌아갈 길을 눈으로
            // 찾을 수 있게 여기서 막대를 씌운다. **스낵바는 여기 하나** — Root 불변식(위 `vm.opResults` 주석).
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(vm.snackbar) },
                topBar = { BackTopBar(stringResource(R.string.diag_screen_title)) { screen = s.back } },
            ) { inner ->
                DiagScreen(Modifier.padding(inner).consumeWindowInsets(inner))
            }
        }

        is AppScreen.Settings -> {
            BackHandler { screen = AppScreen.Browser }
            // 첫 화면과 같은 모양 — 바깥 Scaffold 는 인셋만 소비하고, 제목 막대와 **스낵바는 설정 화면의 Scaffold 가**
            // 든다. 둘 다 `vm.snackbar` 에 붙이면 같은 알림이 두 번 그려진다.
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                SettingsScreen(
                    appVersion = stringResource(
                        R.string.app_version_value,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    snackbar = vm.snackbar,
                    onBack = { screen = AppScreen.Browser },
                    onOpenNotice = { screen = AppScreen.Notice(back = AppScreen.Settings) },
                    onOpenDiagnostics = { screen = AppScreen.Diag(back = AppScreen.Settings) },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }

        is AppScreen.Viewer -> {
            // **바깥 Scaffold 를 씌우지 않는다.** Scaffold 의 기본 인셋은 시스템 바를 따라가는데, 뷰어는 막대를 감출 때 시스템 바도
            // 함께 감춘다(`SystemBarsHidden` — 액티비티가 건다). 그 인셋은 바가 숨고 나타나는 애니메이션 내내 프레임마다 변하고,
            // 그 패딩을 받으면 확대·이동의 기준 상자가 프레임마다 움직여 경계 계산이 깨진다. 그림은 인셋 0 으로 꽉 채우고, 겹치는
            // 막대만 바가 숨어도 변하지 않는 인셋(`…IgnoringVisibility`)을 쓴다.
            val list = viewerEntries.ifEmpty { listOfNotNull(entryOf(s.startPath)) }
            val index = list.indexOfFirst { it.path == s.startPath }.coerceAtLeast(0)
            val generation by vm.contentGeneration.collectAsStateWithLifecycle()
            ImageViewerScreen(
                entries = list,
                startIndex = index,
                snackbar = vm.snackbar,
                generation = generation,
                onClose = home,
                onShare = { entry ->
                    ShareHelper.share(context, listOf(entry.path), appName)
                },
                onShareSanitized = { entry, onFailed -> vm.shareSanitized(context, entry.path, onFailed) },
                onDelete = if (readOnly) null else { entry -> vm.trash(listOf(entry.path)) },
                onRotate = if (readOnly) null else { entry, degrees -> vm.rotate(entry.path, degrees) },
                // 잘라 낸 그림은 원본 곁에 쓴다 — 받은 사본(앱 캐시)의 곁은 사용자가 찾아갈 수 없는 자리다.
                onCrop = if (readOnly) null else { entry, rect, w, h -> vm.crop(entry.path, rect, w, h) },
                // 그린 그림도 원본 곁에 쓴다 — 같은 까닭으로 읽기 전용 사본에는 내지 않는다.
                onMarkup = if (readOnly) null else { path, overlay, w, h -> vm.markup(path, overlay, w, h) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is AppScreen.Archive -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                ArchiveScreen(
                    path = s.path,
                    snackbar = vm.snackbar,
                    onClose = home,
                    onExtract = if (readOnly) null else { plan, newFolder, conflict ->
                        vm.extract(
                            archivePath = s.path,
                            entryIndices = plan.indices,
                            destParent = plan.destParent,
                            newFolderName = if (newFolder) plan.folderName else null,
                            conflict = conflict,
                        )
                    },
                    // 고지에서 뒤로 가면 이 압축 목록으로 돌아온다(예전에는 첫 화면으로 떨어졌다).
                    onOpenNotice = { screen = AppScreen.Notice(back = s) },
                    // 압축 목록의 그림을 탭하면 만화 뷰어가 그 쪽에서 열린다.
                    onOpenImageEntry = { index -> screen = AppScreen.Comic(s.path, index) },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }

        is AppScreen.Notice -> {
            BackHandler { screen = s.back }
            // 스낵바는 여기 하나 — 고지 화면 자신은 알림을 띄울 일이 없지만 Root 불변식은 모든 화면에 걸린다.
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(vm.snackbar) },
                topBar = { BackTopBar(stringResource(R.string.notice_title)) { screen = s.back } },
            ) { inner ->
                NoticeScreen(Modifier.padding(inner).consumeWindowInsets(inner))
            }
        }

        is AppScreen.Text -> {
            // 이미지 뷰어와 달리 **바깥 Scaffold 를 씌워도 된다.** 확대·이동의 기준
            // 상자가 없어서 인셋이 프레임마다 변해도 깨질 것이 없다.
            //
            // **여기에 BackHandler 를 두지 않는다.** 뷰어가 자기 것을 나중에 등록하므로
            // 그쪽이 이기는데, 두 개를 두면 '찾기를 먼저 닫는다' 는 뷰어의 규칙이
            // 등록 순서에 달린 것처럼 보인다. 뒤로가기의 주인은 뷰어 하나다.
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                TextViewerScreen(
                    path = s.path,
                    snackbar = vm.snackbar,
                    onClose = home,
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                    // 편집 — 받은 사본에는 없다(곁에 쓰면 사용자가 찾아갈 수 없는 앱 캐시다).
                    onEdit = if (readOnly) null else { encoding -> screen = AppScreen.Edit(s.path, encoding, fromViewer = true) },
                    // CSV 를 편집기의 표 편집기로 — 돌아오면 저장한 것을 다시 읽는다(`textReopen`).
                    onEditor = if (readOnly) null else { kind -> openInEditor(s.path, kind, FROM_TEXT) },
                    reopen = textReopen,
                )
            }
        }

        is AppScreen.Edit -> {
            // 텍스트 뷰어와 같은 틀 — 바깥 Scaffold 는 인셋만 소비한다. 뒤로 가기의 주인은 편집 화면이다(고친 것이 있으면 먼저 묻는다).
            // 뷰어에서 열었으면 그 뷰어로, 폴더 화면의 '새 글 파일' 로 열었으면 목록으로 돌아간다.
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                TextEditScreen(
                    path = s.path,
                    encoding = s.encoding,
                    snackbar = vm.snackbar,
                    onClose = { if (s.fromViewer) screen = AppScreen.Text(s.path) else home() },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }

        is AppScreen.Editor -> {
            // 다른 앱이 넘긴 오피스·한글 문서를 편집기의 보기 모드로. 편집기가 앞에 서 있는 동안 이 화면은 비어 있고, 편집기가
            // 닫히면 결과를 받은 자리에서 끝난다(`home`). **한 번만 띄운다** — 띄웠다는 표시를 저장 상태에 둔다(회전·프로세스 재생성 때
            // 두 번 띄우지 않게). 띄우지 못했으면 문서 뷰어로 물러난다(그 뷰어가 까닭과 '다른 앱으로 열기' 를 말한다). 띄우기 전에 설정의 '화면 켜
            // 두기' 첫 값을 기다린다(`openInEditor` — 앱이 막 선 길이다).
            var launched by rememberSaveable(s) { mutableStateOf(false) }
            LaunchedEffect(s) {
                if (!launched) {
                    launched = true
                    openInEditor(s.path, s.kind, FROM_VIEW) { screen = AppScreen.Doc(s.path) }
                }
            }
            BackHandler { home() }
            // 모든 화면이 알림 자리를 갖는다(위 '작업 결과를 여기 한 곳에서 받는다' 의 불변식).
            Box(Modifier.fillMaxSize()) {
                SnackbarHost(vm.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }

        is AppScreen.Comic -> {
            // 이미지 뷰어와 같은 이유로 **바깥 Scaffold 를 씌우지 않는다** — 막대와 함께 시스템 바가 숨고 나타나는 동안
            // 인셋이 프레임마다 변하면 확대·이동의 기준 상자가 함께 움직인다.
            ComicScreen(
                path = s.path,
                snackbar = vm.snackbar,
                onClose = home,
                startEntryIndex = s.entryIndex,
                // 다음 권으로 넘어가면 앱의 화면 상태도 그 책을 가리키게 — 프로세스가 죽었다 살아나도 보던 책으로 돌아온다.
                onOpenBook = { next -> screen = AppScreen.Comic(next, -1) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is AppScreen.Doc -> {
            // 만화 뷰어와 같은 이유로 **바깥 Scaffold 를 씌우지 않는다** — PDF 는 막대와 함께 시스템 바가 숨고 나타나는 동안
            // 인셋이 프레임마다 변하면 확대·이동의 기준 상자가 함께 움직인다.
            DocViewScreen(
                path = s.path,
                snackbar = vm.snackbar,
                onClose = home,
                modifier = Modifier.fillMaxSize(),
                // ⋮ 의 '편집' — 편집기로(지금은 PDF). 받은 사본에는 없다(곁에 쓰면 사용자가 찾아갈 수 없는 앱 캐시다).
                // 돌아오면 원래 문서를 다시 연다(`docReopen` — 새 파일은 알림이 말한다). 띄우지 못해도 다시 연다 — 뷰어는 넘기기 전에 문서를
                // 닫았다(`DocEdit` — 예전에는 '여는 중' 동그라미가 남았다).
                onEditor = if (readOnly) null else { kind -> openInEditor(s.path, kind, FROM_DOC) { docReopen++ } },
                reopen = docReopen,
            )
        }

        is AppScreen.Browser -> {
            // **인셋을 소비까지 해야 한다.** `padding(inner)` 만 걸면 안쪽 화면의
            // Scaffold·TopAppBar 가 시스템 바 인셋을 한 번 더 더해, 상태표시줄
            // 높이만큼 빈 띠가 두 번 생긴다.
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                BrowserScreen(
                    vm = vm,
                    onOpenDiagnostics = { screen = AppScreen.Diag() },
                    onOpenSettings = { screen = AppScreen.Settings },
                    onOpenImage = { entries, start ->
                        viewerEntries = entries
                        screen = AppScreen.Viewer(entries[start].path)
                    },
                    onOpenText = { entry -> screen = AppScreen.Text(entry.path) },
                    onOpenArchive = { entry -> screen = AppScreen.Archive(entry.path) },
                    onOpenComic = { path -> screen = AppScreen.Comic(path, -1) },
                    // 오피스·한글 문서는 편집기의 보기 모드로, PDF·EPUB 은 문서 뷰어로(`openDocument`).
                    onOpenDocument = openDocument,
                    // + → '새 글 파일' 로 만든 빈 파일. 닫으면 목록으로 돌아온다.
                    onOpenEditor = { path -> screen = AppScreen.Edit(path, encoding = null, fromViewer = false) },
                    // + → '새 문서(Word)' 넷으로 만든 빈 문서 — 편집기의 편집 모드로 연다(종류는 글자 이름으로 온다 — 목록은
                    // 편집기를 모른다). 돌아오면 이 목록에 그대로 있다.
                    onOpenOfficeEditor = { path, _ ->
                        val editorKind = EditorApp.kindFor(context, path)
                        if (editorKind != null) {
                            openInEditor(path, editorKind, FROM_BROWSER)
                        } else {
                            scope.launch { vm.snackbar.showSnackbar(editorOpenFailed) }
                        }
                    },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }
    }
}

/**
 * 뒤로 가는 화살표가 있는 제목 막대 — 스스로 막대를 그리지 않는 화면(고지·진단)에 `app` 이 씌운다.
 * 시스템 뒤로가기와 **같은 곳**으로 간다(부르는 쪽이 같은 람다를 넘긴다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_back))
            }
        },
        title = { Text(title) },
    )
}

/**
 * 편집기에 넘긴 자리 — 문서 뷰어의 '편집' 이면 돌아와 그 문서를 다시 열고, 텍스트 뷰어의 '표로 편집'(CSV)이면 그 글을 다시 읽고, 폴더의 '새
 * 문서' 면 목록을 새로 읽는다. 보기 모드로 연 문서([FROM_VIEW])는 목록으로 돌아오거나(다른 앱이 넘긴 것이면) 끝난다.
 */
private const val FROM_DOC = "doc"
private const val FROM_TEXT = "text"
private const val FROM_BROWSER = "browser"
private const val FROM_VIEW = "view"

/**
 * 설정의 '읽는 동안 화면 켜 두기'(분) — **첫 값을 기다린다**. 앱이 막 섰을 때 모아 둔 값은 아직 초기값(10분)이라, 다른 앱이
 * 넘긴 문서의 보기 모드가 '켜 두지 않음' 으로 둔 사람에게도 10분을 실어 갔다. 설정 파일이 끝내 답하지 않으면 초기값으로 — 편집기를 띄우는 길이
 * 거기서 멈추지 않게.
 */
private suspend fun readerKeepScreenMinutes(prefs: AppPreferences): Int =
    withTimeoutOrNull(SETTINGS_WAIT_MS) { prefs.readerKeepScreenMinutes.first() } ?: AppPreferences.KEEP_SCREEN_DEFAULT_MINUTES

/** [readerKeepScreenMinutes] 가 설정 파일을 기다리는 상한. 작은 파일 하나라 보통 몇 ms 다. */
private const val SETTINGS_WAIT_MS = 2_000L

/** 프로세스가 죽었다 살아났을 때 쓰는 한 장짜리 폴백. */
private fun entryOf(path: String): FileEntry? {
    val f = java.io.File(path)
    if (!f.isFile) return null
    return FileEntry(
        name = f.name,
        path = path,
        isDirectory = false,
        size = f.length(),
        lastModified = f.lastModified(),
        isHidden = f.name.startsWith('.'),
        isSymlink = false,
        kind = io.github.donggi.iroiroviewer.model.FileKind.IMAGE,
    )
}

/**
 * 알림 권한을 한 번 물어본다.
 *
 * **파일 작업 진행 알림에 필요하다.** 재생 알림(5단계)보다 이것이 먼저 쓰이므로 여기서
 * 묻는다. 거부해도 앱은 그대로 돈다 — 진행이 화면 안의 바에만 보이고, 앱을 나가면
 * 무엇이 진행 중인지 알 수 없게 될 뿐이다.
 */
@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            // 기기에 따라 앱을 지정한 설정 화면이 없다. 없으면 전체 목록 화면으로 간다.
            failed = StorageAccess.requestIntents(context).none { intent ->
                try {
                    context.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
        }) {
            Text(stringResource(R.string.permission_button))
        }
        if (failed) Text(stringResource(R.string.permission_unavailable))
    }
}
