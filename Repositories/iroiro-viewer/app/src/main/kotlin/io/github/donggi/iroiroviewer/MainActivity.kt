package io.github.donggi.iroiroviewer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.archive.ArchiveScreen
import io.github.donggi.iroiroviewer.browser.BrowserScreen
import io.github.donggi.iroiroviewer.comic.ComicScreen
import io.github.donggi.iroiroviewer.browser.BrowserViewModel
import io.github.donggi.iroiroviewer.browser.opResultMessage
import io.github.donggi.iroiroviewer.diag.DiagScreen
import io.github.donggi.iroiroviewer.docview.DocViewScreen
import io.github.donggi.iroiroviewer.image.ImageViewerScreen
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.ShareHelper
import io.github.donggi.iroiroviewer.io.StorageAccess
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.text.TextViewerScreen
import io.github.donggi.iroiroviewer.ui.IroiroTheme
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishIfDuplicateLaunch()) return
        // 릴리스에서는 로그 호출 지점 자체가 사라진다. Iro 의 주석 참고.
        Iro.debug = BuildConfig.DEBUG
        enableEdgeToEdge()
        setContent {
            IroiroTheme {
                Root()
            }
        }
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
 * 권한이 없으면 왜 필요한지 설명하고 설정으로 보낸다. 있으면 브라우저로 넘긴다.
 *
 * 설정에서 돌아오는 것은 액티비티 결과가 아니라 단순한 재개라서, 화면이 다시 보일
 * 때마다 상태를 새로 읽는 것이 유일하게 맞는 방법이다.
 */
@Composable
private fun Root(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted()) }

    LifecycleResumeEffect(Unit) {
        granted = StorageAccess.isGranted()
        onPauseOrDispose { }
    }

    if (!granted) {
        PermissionScreen(modifier)
        return
    }

    val vm: BrowserViewModel = viewModel()
    var screen by rememberSaveable(stateSaver = AppScreen.Saver) {
        mutableStateOf<AppScreen>(AppScreen.Browser)
    }
    // 뷰어에 넘긴 목록은 **메모리 참조**다. 사진 3,000장 경로를 저장 상태에 담으면
    // TransactionTooLarge 로 죽는다. 프로세스가 죽어 살아났을 때는 시작 경로 하나만
    // 남아 있고(AppScreen.Saver), 그 한 장을 먼저 띄운 뒤 폴더를 다시 나열해 좌우를 채운다.
    var viewerEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }

    NotificationPermissionGate()

    // **작업 결과를 여기 한 곳에서 받는다.** 화면마다 받으면 그 화면이 사라지는 순간
    // 소비가 멎고, 그 뒤에 끝난 작업의 성패가 조용히 사라진다(3·4단계가 고친 실패 형태).
    //
    // 그래서 새 불변식이 생긴다 — **Root 가 그릴 수 있는 모든 화면은 `vm.snackbar` 에
    // 붙은 SnackbarHost 를 하나씩 가져야 한다.** 붙지 않은 화면으로 넘어가면
    // `showSnackbar` 가 영영 돌아오지 않아 그 뒤 모든 결과가 막힌다. 그 하나에 기대지
    // 않으려고 시간 상한도 함께 건다 — 보험이 싸다.
    val undoLabel = stringResource(io.github.donggi.iroiroviewer.browser.R.string.browser_undo)
    // **`context.getString` 을 컴포저블 안에서 부르지 않는다.** 그 값은 구성 변경을
    // 따라가지 않아 언어를 바꿔도 옛 문자열이 남는다(lint `LocalContextGetResourceValueCall`).
    val appName = stringResource(R.string.app_name)
    LaunchedEffect(Unit) {
        vm.opResults.collect { finished ->
            vm.onOperationFinished(finished)
            val trashed = (finished.extra as? FileOpManager.Extra.Trashed)?.uuids
            val result = withTimeoutOrNull(10_000) {
                vm.snackbar.showSnackbar(
                    message = opResultMessage(context, finished),
                    actionLabel = if (!trashed.isNullOrEmpty()) undoLabel else null,
                )
            }
            if (result == SnackbarResult.ActionPerformed && trashed != null) vm.undoTrash(trashed)
        }
    }

    // 기간이 지난 휴지통 항목을 지우고 기록과 파일을 맞춘다. 다른 앱이 휴지통 폴더를
    // 건드렸거나 볼륨을 포맷했을 수 있으므로 시작할 때 한 번 본다.
    //
    // **프로세스당 한 번이다.** `LaunchedEffect(Unit)` 은 액티비티가 다시 만들어질
    // 때마다(화면 회전) 다시 발화하는데, 이것은 파일을 지울 수 있는 작업이라 회전마다
    // 도는 것이 맞지 않는다.
    LaunchedEffect(Unit) {
        if (!reconciledOnce) {
            reconciledOnce = true
            vm.reconcileTrash()
        }
    }

    when (val s = screen) {
        is AppScreen.Diag -> {
            BackHandler { screen = AppScreen.Browser }
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                DiagScreen(Modifier.padding(inner).consumeWindowInsets(inner))
            }
        }

        is AppScreen.Viewer -> {
            // **바깥 Scaffold 를 씌우지 않는다.** Scaffold 의 기본 인셋은 시스템 바를
            // 따라가고 그 값은 막대가 숨겨지고 나타나는 애니메이션 내내 프레임마다 변한다.
            // 그 패딩을 받으면 확대·이동의 기준 상자가 프레임마다 움직여 경계 계산이 깨진다.
            val list = viewerEntries.ifEmpty { listOfNotNull(entryOf(s.startPath)) }
            val index = list.indexOfFirst { it.path == s.startPath }.coerceAtLeast(0)
            val generation by vm.contentGeneration.collectAsStateWithLifecycle()
            ImageViewerScreen(
                entries = list,
                startIndex = index,
                snackbar = vm.snackbar,
                generation = generation,
                onClose = { screen = AppScreen.Browser },
                onShare = { entry ->
                    ShareHelper.share(context, listOf(entry.path), appName)
                },
                onShareSanitized = { entry -> vm.shareSanitized(context, entry.path) },
                onDelete = { entry -> vm.trash(listOf(entry.path)) },
                onRotate = { entry, degrees -> vm.rotate(entry.path, degrees) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is AppScreen.Archive -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                ArchiveScreen(
                    path = s.path,
                    snackbar = vm.snackbar,
                    onClose = { screen = AppScreen.Browser },
                    onExtract = { plan, newFolder, conflict ->
                        vm.extract(
                            archivePath = s.path,
                            entryIndices = plan.indices,
                            destParent = plan.destParent,
                            newFolderName = if (newFolder) plan.folderName else null,
                            conflict = conflict,
                        )
                    },
                    onOpenNotice = { screen = AppScreen.Notice },
                    // 압축 목록의 그림을 탭하면 만화 뷰어가 그 쪽에서 열린다.
                    onOpenImageEntry = { index -> screen = AppScreen.Comic(s.path, index) },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }

        is AppScreen.Notice -> {
            BackHandler { screen = AppScreen.Browser }
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
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
                    onClose = { screen = AppScreen.Browser },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }

        is AppScreen.Comic -> {
            // 이미지 뷰어와 같은 이유로 **바깥 Scaffold 를 씌우지 않는다** — 인셋이
            // 프레임마다 변하면 확대·이동의 기준 상자가 함께 움직인다.
            ComicScreen(
                path = s.path,
                snackbar = vm.snackbar,
                onClose = { screen = AppScreen.Browser },
                startEntryIndex = s.entryIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }

        is AppScreen.Doc -> {
            // 만화 뷰어와 같은 이유로 **바깥 Scaffold 를 씌우지 않는다** — 인셋이
            // 프레임마다 변하면 확대·이동의 기준 상자가 함께 움직인다.
            DocViewScreen(
                path = s.path,
                snackbar = vm.snackbar,
                onClose = { screen = AppScreen.Browser },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is AppScreen.Browser -> {
            // **인셋을 소비까지 해야 한다.** `padding(inner)` 만 걸면 안쪽 화면의
            // Scaffold·TopAppBar 가 시스템 바 인셋을 한 번 더 더해, 상태표시줄
            // 높이만큼 빈 띠가 두 번 생긴다.
            Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                BrowserScreen(
                    vm = vm,
                    onOpenDiagnostics = { screen = AppScreen.Diag },
                    onOpenImage = { entries, start ->
                        viewerEntries = entries
                        screen = AppScreen.Viewer(entries[start].path)
                    },
                    onOpenText = { entry -> screen = AppScreen.Text(entry.path) },
                    onOpenArchive = { entry -> screen = AppScreen.Archive(entry.path) },
                    onOpenComic = { path -> screen = AppScreen.Comic(path, -1) },
                    onOpenDocument = { path -> screen = AppScreen.Doc(path) },
                    modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                )
            }
        }
    }
}

/**
 * 지금 보고 있는 화면.
 *
 * **`navigation-compose` 를 들이지 않는다.** 화면이 셋이고 백스택이 한 겹인데, 그 라이브러리의
 * 지금 권장 형태인 타입 안전 라우트는 `kotlin.plugin.serialization` 과
 * `kotlinx-serialization-json` 을 요구한다 — 둘 다 이 저장소의 버전 카탈로그에 없다.
 * 경로를 라우트 문자열에 넣으려면 임의의 유니코드와 `/` 를 인코딩해야 하는 문제도 그대로다.
 */
private sealed interface AppScreen {
    data object Browser : AppScreen
    data object Diag : AppScreen

    /**
     * 이미지 뷰어. **저장 상태에는 시작 경로 하나만** 남긴다.
     *
     * 목록 전체를 담으면 사진이 많은 폴더에서 번들이 MB 단위가 되고
     * `TransactionTooLargeException` 으로 죽는다.
     */
    data class Viewer(val startPath: String) : AppScreen

    /** 텍스트 뷰어. 목록이 필요 없어 경로 하나가 전부다. */
    data class Text(val path: String) : AppScreen

    /** 압축 파일 탐색. 아카이브 안 폴더 위치는 화면이 들고 있으므로 여기에는 경로만 남는다. */
    data class Archive(val path: String) : AppScreen

    /**
     * 만화 뷰어. 경로와 **엔트리 번호**뿐이다.
     *
     * 쪽 목록을 저장 상태에 담지 않는다 — 300쪽짜리 이름 목록이 번들에 들어가고,
     * 그것은 뷰어에 사진 목록을 담았을 때와 같은 형태의 `TransactionTooLargeException`
     * 이다. 프로세스가 죽었다 살아나면 목록은 다시 만들고, 읽던 쪽은 이어보기가 안다.
     */
    data class Comic(val path: String, val entryIndex: Int) : AppScreen

    /**
     * 문서 뷰어. 경로 하나가 전부다.
     *
     * 쪽 수도 읽던 쪽도 담지 않는다 — 쪽 수는 문서를 열면 곧 알고, 읽던 쪽은
     * `doc_progress` 가 안다. 만화 뷰어가 쪽 목록을 담지 않기로 한 것과 같은 판단이다.
     */
    data class Doc(val path: String) : AppScreen

    /** 오픈소스 고지. 라이선스가 요구하는 화면이라 어디서든 닿아야 한다. */
    data object Notice : AppScreen

    companion object {
        val Saver: androidx.compose.runtime.saveable.Saver<AppScreen, Any> =
            androidx.compose.runtime.saveable.Saver(
                save = {
                    when (it) {
                        is Browser -> "b"
                        is Diag -> "d"
                        is Viewer -> "v:" + it.startPath
                        is Text -> "t:" + it.path
                        is Archive -> "a:" + it.path
                        is Comic -> "c:" + it.entryIndex + ":" + it.path
                        is Doc -> "p:" + it.path
                        is Notice -> "n"
                    }
                },
                restore = {
                    val v = it as String
                    when {
                        v == "d" -> Diag
                        v.startsWith("v:") -> Viewer(v.removePrefix("v:"))
                        v.startsWith("t:") -> Text(v.removePrefix("t:"))
                        v.startsWith("a:") -> Archive(v.removePrefix("a:"))
                        v.startsWith("c:") -> {
                            val rest = v.removePrefix("c:")
                            val at = rest.indexOf(':')
                            Comic(rest.substring(at + 1), rest.substring(0, at).toIntOrNull() ?: -1)
                        }
                        v.startsWith("p:") -> Doc(v.removePrefix("p:"))
                        v == "n" -> Notice
                        else -> Browser
                    }
                },
            )
    }
}

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
