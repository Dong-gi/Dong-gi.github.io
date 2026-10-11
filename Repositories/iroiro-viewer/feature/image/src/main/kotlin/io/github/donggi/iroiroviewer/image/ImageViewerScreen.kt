package io.github.donggi.iroiroviewer.image

import android.graphics.drawable.AnimatedImageDrawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.io.CropSupport
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.ui.SystemBarsHidden
import io.github.donggi.iroiroviewer.ui.gesture.DismissMath
import io.github.donggi.iroiroviewer.ui.gesture.DismissState
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import io.github.donggi.iroiroviewer.ui.gesture.dragToDismiss
import io.github.donggi.iroiroviewer.ui.icon.RotateRightIcon
import io.github.donggi.iroiroviewer.ui.image.AnimationPlan
import io.github.donggi.iroiroviewer.ui.image.ExifReader
import io.github.donggi.iroiroviewer.ui.image.ImageFormats
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.ImageProbe
import io.github.donggi.iroiroviewer.ui.image.RegionMath
import io.github.donggi.iroiroviewer.ui.image.RegionOrientation
import io.github.donggi.iroiroviewer.ui.image.ZoomableImage
import io.github.donggi.iroiroviewer.ui.image.ZoomablePainter
import io.github.donggi.iroiroviewer.ui.image.decodeAnimated
import io.github.donggi.iroiroviewer.ui.image.messageRes
import io.github.donggi.iroiroviewer.ui.image.rememberAnimatedPainter
import io.github.donggi.iroiroviewer.ui.image.rememberRasterDetail
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 전체화면 이미지 뷰어.
 *
 * ## 차용한 관행과 그 근거
 *
 * 널리 쓰이는 뷰어(구글 포토·삼성 갤러리 등)에서 사람들이 **몸으로 익힌** 동작만 가져왔다.
 * 새로 배우게 하지 않는 것이 이 화면의 목표다.
 *
 * | 동작 | 결과 | 왜 |
 * |---|---|---|
 * | 한 번 탭 | 상·하단 막대 감추기/보이기 — 시스템 바도 함께 | 사진만 보고 싶을 때 즉시 치운다. 가장자리를 밀면 바가 잠깐 보인다 |
 * | 더블탭 | 짚은 자리를 중심으로 확대/원래대로 | 두 손가락을 쓸 수 없는 한 손 조작 |
 * | 핀치 | 확대·축소 | |
 * | 끌기(확대 상태) | 사진 안에서 이동. 가장자리에 닿은 뒤 **새로** 밀면 다음/이전 사진 | 구글 포토의 관행(`ZoomState.claimsDrag`) |
 * | 좌우로 밀기(배율 1) | 다음/이전 사진 | **폴더 목록 그대로**를 순서로 쓴다 |
 * | 아래로 끌기(배율 1) | 닫기 | 끌수록 그림이 내려가며 옅어지고, 문턱을 넘겨 놓거나 빠르게 튕기면 닫힌다 |
 * | 뒤로가기 | 목록으로 | 아래로 끌어 닫기와 **같은 곳**(`onClose`)으로 간다 |
 *
 * **움직이는 그림(GIF·애니WebP)도 같은 표를 따른다**(14단계). 만화 뷰어는 움직이는 쪽을 확대하지
 * 않지만, 여기서는 사진과 섞인 폴더에서 어떤 장만 두 번 두드려도 아무 일이 없으면 고장으로 읽힌다 —
 * 까닭과 대가(선명화 조각 없음)는 `ZoomablePainter` 에 적었다. 트는 것은 **자리 잡은 장 하나**뿐이다.
 * 페이저가 앞뒤 한 장씩을 화면 밖에서도 구성해 두므로, 그대로 두면 보이지도 않는 GIF 가 프레임을 푼다.
 *
 * **SVG 는 잠긴 WebView 가 그린다**(`SvgPage`, 14단계 뒤). 몸짓은 위 표와 같다 — 확대·이동은 WebView 가, 두 번 두드림은 같은
 * 2.5배, 넘기기·끌어 닫기는 페이저가 한다. 화면 회전은 없다(메뉴가 꺼진다).
 *
 * ## 이 뷰어가 못 여는 그림 — 다른 앱으로 연다
 *
 * 디코더가 풀지 못한 장(TIFF, 기기가 못 푸는 HEIC/AVIF, 뿌리가 svg 도 비트맵도 아닌 `.svg`, 깨진 파일)은 문구 아래에
 * '다른 앱으로 열기' 단추를 단다.
 * ⋮ 메뉴에도 지금 장을 여는 줄이 있다. 둘 다 **언제나 고르는 창**이다 — 사용자가 '다른 앱으로' 라고 말했으므로 기본
 * 앱으로 곧장 보내지 않는다. 띄우지 못했으면(파일이 그새 사라졌다 등) 이 화면의 스낵바가 우리 문장으로 말한다. 무엇에
 * 단추를 다는가는 [ImageOpenWith] 가 정한다(파일이 없어진 장에는 달지 않는다).
 *
 * ## 끌어 닫기의 제스처 계층
 *
 * ```
 * HorizontalPager                      ← 가로 밀기
 *   Box.dragToDismiss                  ← 축을 한 번만 정한다(DismissArbiter). 움직이지 않는다
 *     Box.graphicsLayer(내려가기·옅어지기)
 *       ImagePage → ZoomableImage(탭 / transformable)
 * ```
 *
 * 형제 `pointerInput` 의 순서에 기대지 않고 부모–자식으로 세운다(6단계의 규칙). 한 장의 인식기가
 * 자식이라 먼저 보고, 끌어 닫기가 그 위, 페이저가 맨 위다. 누가 이기는가는 `DismissMath` 의 주석과
 * JVM 시험이 답한다.
 *
 * ## 잘라서 저장
 *
 * ⋮ 의 '잘라서 저장' 이 그림 위에 사각형을 고르는 화면([CropScreen])을 덮는다. 저장은 원본 곁의 새 파일이고 파일 작업 큐가 한다
 * ([onCrop] → `FileOpManager.Request.SaveCrop`) — 결과(실제 이름)는 앱의 결과 소비자가 이 화면의 스낵바로 말한다. **정지 그림만**
 * 자른다(움직이는 그림·SVG·못 연 장은 메뉴가 꺼진다). 회전을 저장하는 중이면 끈다 — 화면은 이미 돌았는데 파일은 아직이라, 고른
 * 자리가 저장될 때의 파일과 어긋난다.
 *
 * ## 그리기·메모
 *
 * ⋮ 의 '그리기·메모' 가 그림 위에 펜·형광펜·직선·글 메모를 다는 화면([MarkupScreen])을 덮는다. **잘라서 저장과 같은 조건**에서만
 * 켠다(같은 판단 하나 — `editableProbe`). 저장도 같은 길이다 — 그린 것을 실은 잘라 저장 요청([onMarkup] → `SaveCrop.overlay`)이
 * 그림 전체를 원본 해상도로 뜬 위에 그려 `이름_markup` 으로 쓴다. 그린 것은 [MarkupViewModel] 이 들고(저장 상태 번들에 싣지 않는다),
 * 프로세스가 죽었다 살아나면 저장 상태에 남긴 경로 하나로 초안을 되살린다.
 *
 * ## 이 화면이 바깥 `Scaffold` 밖에 있는 이유
 *
 * 막대를 감추면 **시스템 바도 함께 감춘다**([SystemBarsHidden], 거는 것은 액티비티다). `Scaffold` 의 기본 인셋은
 * 시스템 바를 따라가고, 그 값은 바가 숨고 나타나는 **애니메이션 내내 프레임마다 변한다.** 그 패딩을 받으면 확대·이동의 기준
 * 상자가 프레임마다 움직여 경계 계산이 어긋난다. 사진은 인셋 0 으로 꽉 채우고, 겹치는 막대만 자기 인셋을 쓴다 — 그것도 바가
 * 숨어도 변하지 않는 인셋(`…IgnoringVisibility`)이라, 막대가 사라지거나 나타나는 동안 글자가 위아래로 미끄러지지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageViewerScreen(
    entries: List<FileEntry>,
    startIndex: Int,
    snackbar: SnackbarHostState,
    /** 파일 내용이 바뀐 횟수. 회전은 수정시각을 보존하므로 이것 말고는 단서가 없다. */
    generation: Int,
    onClose: () -> Unit,
    onShare: (FileEntry) -> Unit,
    /**
     * 메타데이터를 지운 사본을 공유한다. 만들지 못했으면 둘째 인자를 부른다 — **이 화면이 그 자리에서 말한다**(예전에는
     * 파일 목록으로 돌아간 뒤에야 엉뚱한 문장이 떴고, 다른 앱에서 받은 사진이면 아예 뜨지 않았다).
     */
    onShareSanitized: (FileEntry, onFailed: () -> Unit) -> Unit,
    /**
     * 지우기(휴지통으로)와 회전(EXIF 저장). **null 이면 그 단추를 내지 않는다** — 다른 앱이 넘겨 앱 캐시에 둔 사본은 읽기
     * 전용이다(`IncomingFiles` — 보낸 쪽의 원본이 아니고, 캐시는 사용자가 찾아갈 수 없는 자리다).
     */
    onDelete: ((FileEntry) -> Unit)?,
    onRotate: ((FileEntry, Int) -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * 고른 자리를 새 파일로 저장한다 — (장, 화면 방향의 원본 화소 `[왼, 위, 오른, 아래]`, 고를 때 본 원본의 화면 방향 폭·높이).
     * **null 이면 메뉴에 내지 않는다**(읽기 전용 사본 — [onDelete] 와 같은 까닭).
     */
    onCrop: ((FileEntry, IntArray, Int, Int) -> Unit)? = null,
    /**
     * 그린 것을 새 파일로 저장한다 — (원본 경로, 그릴 것, 그릴 때 본 원본의 화면 방향 폭·높이). **null 이면 메뉴에 내지 않는다**
     * ([onCrop] 과 같은 까닭). 장이 아니라 경로를 받는 것은 프로세스가 죽었다 되살린 그리기가 지금 목록에 없는 그림일 수 있어서다.
     */
    onMarkup: ((String, CropSupport.Overlay, Int, Int) -> Unit)? = null,
) {
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, entries.lastIndex),
        pageCount = { entries.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    // 막대를 감추면 시스템 바도 감춘다 — 뷰어를 닫으면(이 화면이 컴포지션에서 빠지면) 되돌아온다.
    SystemBarsHidden(hidden = !chromeVisible)
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var rotateNotice by remember { mutableStateOf(false) }

    // 화면에서 먼저 돌리고, 파일 쓰기는 뒤에서 한다. 저장이 끝나면 [generation] 이 올라
    // 다시 디코딩되므로 이 값을 0 으로 되돌린다 — 그러지 않으면 **두 번 돌아간다.**
    val displayRotation = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(generation) { displayRotation.clear() }

    // 끌어 닫기는 화면에 하나다 — 끄는 것은 한 장이지만 막대의 불투명도도 따라가야 한다.
    val dismiss = remember { DismissState() }
    // 인식기는 처음 붙을 때 람다를 잡는다(`dragToDismiss` 주석). 닫는 곳은 여기서 새로 읽게 한다.
    val closeNow by rememberUpdatedState(onClose)
    // 디버그 빌드에서만 남는다. 끌기가 **시작됐는가**(가로 밀기·핀치·두드림과 갈렸는가)와 놓았을 때
    // 닫혔는가를 logcat 으로 판정한다 — 제스처를 화면 캡처로 판정하려 들지 마라(함정 표).
    LaunchedEffect(dismiss) {
        // 첫 값(아직 끌지 않았다)은 사건이 아니다.
        snapshotFlow { dismiss.dragging }.drop(1).collect { dragging ->
            Iro.d {
                if (dragging) "끌어 닫기 시작" else "끌어 닫기 놓음 · 내려온 ${dismiss.offsetY} · 닫힘 ${dismiss.closing}"
            }
        }
    }

    // 예산 — 화면 크기와 힙 등급. 선명화 조각의 상한과 움직이는 그림의 표본이 같은 표에서 나온다
    // (만화·PDF 와 같은 표다).
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val budget = remember(config) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val m = context.resources.displayMetrics
        ImageLimits.budgetOf(m.widthPixels, m.heightPixels, am.memoryClass)
    }

    // 장마다 '첫 장면만 보여 준다' 의 까닭. 아래 막대 곁에서 **지금 장의 것**을 말한다.
    // **목록이 바뀌어도 비우지 않는다** — 한 장은 자기 상태가 바뀔 때만 알리므로, 다른 사진을 지워
    // 목록이 바뀐 순간 비우면 보고 있던 APNG 의 고지가 말없이 사라진다. 경로가 키라 남는 것은 작다.
    val stillReasons = remember { mutableStateMapOf<String, AnimationPlan.StillReason>() }

    // 장마다 못 연 까닭. ⋮ 메뉴가 **지금 장의 것**을 본다 — 파일에 닿지 못한 장(UNREADABLE)에는 '다른 앱으로 열기' 줄을
    // 세우지 않는다([ImageOpenWith.menuTarget]). 판정은 그 장 안에서 난다(디코딩이 실패한 뒤 여는 탐침). 비우지 않는 까닭은
    // [stillReasons] 와 같다 — 한 장은 자기 상태가 바뀔 때만 알린다.
    val pageFailures = remember { mutableStateMapOf<String, ImageOpenWith.PageFailure>() }

    // 장마다 잘라 저장할 수 있는가 — 정지 그림으로 열렸고 원본 치수를 쟀다(고르는 화면이 그 치수로 좌표를 잡는다). 비우지 않는
    // 까닭은 [stillReasons] 와 같다.
    val croppable = remember { mutableStateMapOf<String, ImageProbe>() }
    // 고르는 화면에 띄운 장. null 이면 닫혀 있다.
    var cropping by remember { mutableStateOf<Pair<FileEntry, ImageProbe>?>(null) }

    // 잘라 저장·그리기가 **함께 쓰는** 조건 — 정지 그림으로 열렸고(원본 치수를 쟀다), 화면에서 돌려 두고 아직 저장하지 않은 동안이
    // 아니다(화면은 이미 돌았는데 파일은 아직이라 고른 자리·그린 자리가 저장될 때의 파일과 어긋난다). 되면 그 장의 원본 치수, 아니면 null.
    fun editableProbe(e: FileEntry?): ImageProbe? =
        e?.takeIf { (displayRotation[it.path] ?: 0) == 0 }?.let { croppable[it.path] }

    // 그리기·메모. 그린 것은 ViewModel 이 든다 — 저장 상태 번들에 싣지 않는다(크기). 액티비티에 매여 밤 모드를 건너 산다.
    val markupVm: MarkupViewModel = viewModel()
    val markup by markupVm.session.collectAsStateWithLifecycle()
    // 프로세스가 죽었다 살아나면 ViewModel 이 비어 있다 — 그리던 그림의 경로 하나만 저장 상태에 두고(작다), 그린 것은 앱이 화면에서
    // 사라질 때 적어 둔 초안에서 되살린다. 되살리지 못하면(초안이 없다·원본이 바뀌었다) 뷰어 그대로다.
    var markupPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(markup?.path) { markup?.path?.let { markupPath = it } }
    LaunchedEffect(Unit) {
        val saved = markupPath ?: return@LaunchedEffect
        if (markupVm.session.value == null && (onMarkup == null || !markupVm.restoreDraft(saved))) markupPath = null
    }
    // 뷰어가 사라지면(다른 앱이 다른 파일을 넘겨 화면이 바뀌었다, 액티비티를 끝냈다) 그리던 것을 접는다. 구성이 바뀌어(밤 모드) 다시
    // 서거나 시스템이 액티비티를 거두는 것이면 그대로 둔다 — 그때는 ViewModel 과 초안이 이어 준다.
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(markupVm, lifecycle) {
        onDispose {
            val destroying = lifecycle.currentState == Lifecycle.State.DESTROYED
            if (!destroying || activity?.isFinishing == true) markupVm.close()
        }
    }

    val openWith = rememberOpenWith(snackbar)
    // 메타데이터를 지운 공유가 실패했을 때 — 결과는 ViewModel 의 일에서 늦게 오므로 이 화면의 스코프로 띄운다(화면을 떠났으면 말하지 않는다).
    val scope = rememberCoroutineScope()
    val sanitizeFailed = stringResource(R.string.image_sanitize_failed)

    BackHandler { onClose() }

    // 화면에서 벗어난 장의 확대를 되돌린다. 되돌리지 않으면 나중에 그 장으로 돌아왔을 때
    // 전에 확대해 둔 자리에서 시작해 '내가 뭘 한 거지' 가 된다.
    //
    // **표가 바뀌면 효과도 다시 선다**(키에 `pages`). 표는 목록(`entries`)이 바뀔 때 새로 서는데, 예전에는
    // 효과가 처음 잡은 표와 목록을 쥔 채 돌아 **새 표의 장은 떠나도 되돌려지지 않았다** — 돌아오면 확대된
    // 채이고, 그 장에서는 끌어 닫기도 막힌다(`DismissMath.canStart` 가 확대 상태를 거절한다). 지금의 `app` 은
    // 목록을 뷰어를 열 때 한 번만 넘기므로 아직 드러나지 않았지만, 목록을 도중에 바꾸지 않는다는 것은 이
    // 화면의 계약이 아니다. `pages` 가 `entries` 를 키로 서므로 다시 선 효과는 새 목록도 함께 쥔다.
    val pages = remember(entries) { HashMap<String, ZoomState>() }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            val here = entries.getOrNull(settled)?.path
            pages.forEach { (path, st) -> if (path != here) st.reset() }
        }
    }

    // 고르는 화면·그리는 화면이 덮는 동안 뒤의 뷰어를 화면 낭독기에서 감춘다 — 손가락은 덮는 화면이 막지만 낭독기의 초점은 그 아래의
    // 장·막대로 내려가, 보이지 않는 '회전'·'지우기' 를 읽고 누를 수 있었다.
    val behindCrop = if (cropping != null || markup != null) Modifier.clearAndSetSemantics {} else Modifier

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            // 앞뒤 한 장씩만 살려 둔다. 넘기는 동안 두 장이 화면에 걸치므로 최악은 넷이다.
            // 이보다 줄이면 되돌아 밀 때마다 다시 디코딩해 체감이 그만큼 나빠진다.
            beyondViewportPageCount = 1,
            key = { entries[it].path },
            modifier = Modifier.fillMaxSize().then(behindCrop),
        ) { page ->
            val entry = entries[page]
            val zoom = pages.getOrPut(entry.path) { ZoomState() }
            // 목록이 바뀌어도 이 장의 인식기는 그대로 산다(키가 경로다) — 자리와 확대 상태를 새로 읽게 한다.
            val pageNow by rememberUpdatedState(page)
            val zoomNow by rememberUpdatedState(zoom)
            Box(
                Modifier
                    .fillMaxSize()
                    .dragToDismiss(
                        state = dismiss,
                        enabled = {
                            DismissMath.canStart(
                                settledHere = pageNow == pagerState.settledPage,
                                pagerScrolling = pagerState.isScrollInProgress,
                                zoomed = zoomNow.isZoomed,
                            )
                        },
                        onDismiss = { closeNow() },
                    ),
            ) {
                // **끌리는 것은 안쪽이다.** 인식기를 단 상자를 옮기면 포인터 좌표가 함께 내려가 이동이
                // 0 으로 읽힌다(`dragToDismiss` 주석). 이웃 장에도 같은 값이 걸리지만 화면 밖이다.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = dismiss.offsetY
                            alpha = dismiss.contentAlpha
                        },
                ) {
                    ImagePage(
                        path = entry.path,
                        generation = generation,
                        extraRotation = displayRotation[entry.path] ?: 0,
                        zoomState = zoom,
                        budget = budget,
                        active = page == pagerState.settledPage,
                        onTap = { chromeVisible = !chromeVisible },
                        onStillReason = { reason ->
                            if (reason == null) stillReasons.remove(entry.path) else stillReasons[entry.path] = reason
                        },
                        onFailure = { failure ->
                            if (failure == null) pageFailures.remove(entry.path) else pageFailures[entry.path] = failure
                        },
                        onCroppable = { probe ->
                            if (probe == null) croppable.remove(entry.path) else croppable[entry.path] = probe
                        },
                        // 못 연 장의 단추는 **그 장의** 파일을 넘긴다(지금 장이 아니라). 넘기는 도중에도 옆 장이 걸려 있다.
                        onOpenWith = { openWith(entry.path) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 끌기 시작하면 막대가 먼저 사라진다 — 그림만 남아야 '닫힌다' 가 읽힌다.
                .graphicsLayer { alpha = dismiss.chromeAlpha }
                .then(behindCrop),
        ) {
            val current = entries.getOrNull(pagerState.currentPage)
            TopAppBar(
                // 시스템 바가 숨어도 변하지 않는 인셋 — 막대가 사라지는 동안 바가 함께 숨어 제목이 위로 미끄러지지 않게.
                windowInsets = WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.image_close))
                    }
                },
                title = {
                    Text(
                        text = current?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, stringResource(R.string.image_info))
                    }
                    IconButton(onClick = { current?.let(onShare) }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.image_share))
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { current?.let(onDelete) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.image_delete))
                        }
                    }
                    // **⋮ 단추와 메뉴를 한 상자에 담는다.** Compose 의 Popup 은 자기를 감싼 **부모
                    // 레이아웃 노드**를 앵커로 삼는다. `actions` 에 형제로 두면 앵커가 아이콘 줄
                    // 전체가 되어 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(사용자가 지적했다).
                    // `offset` 으로 보정하지 마라 — 화면 폭·글꼴 배율에서 다시 어긋난다.
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.image_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (onRotate != null) DropdownMenuItem(
                                leadingIcon = {
                                    Icon(RotateRightIcon, null)
                                },
                                text = { Text(stringResource(R.string.image_rotate)) },
                                // SVG 장은 WebView 가 그려 화면에서도 돌지 않는다. 켜 두면 '저장할 수 없다' 만 떠서 화면에서는
                                // 돈 것처럼 읽힌다.
                                enabled = current?.let { !SvgSupport.isSvgName(it.name) } != false,
                                onClick = {
                                    menuOpen = false
                                    current?.let { e ->
                                        // 화면은 **언제나** 즉시 돈다. 저장은 되는 형식에서만.
                                        displayRotation[e.path] = ((displayRotation[e.path] ?: 0) + 90) % 360
                                        if (ExifReader.canRotate(File(e.path))) {
                                            onRotate(e, 90)
                                        } else {
                                            // 파일에 못 남긴다는 사실을 숨기지 않는다. 숨기면
                                            // 사용자는 돌려 놓고 앱을 껐다 켠 뒤 원래대로인
                                            // 사진을 보며 앱이 고장났다고 생각한다.
                                            rotateNotice = true
                                        }
                                    }
                                },
                            )
                            if (onCrop != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.image_crop)) },
                                // 정지 그림으로 열린 장만. 회전을 저장하는 중이면 끈다(머리말 — 고른 자리가 파일과 어긋난다).
                                enabled = editableProbe(current) != null,
                                onClick = {
                                    menuOpen = false
                                    current?.let { e -> editableProbe(e)?.let { probe -> cropping = e to probe } }
                                },
                            )
                            // 잘라서 저장과 같은 조건이다 — 그린 자리도 저장될 때의 파일과 맞아야 한다.
                            if (onMarkup != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.image_markup)) },
                                enabled = editableProbe(current) != null,
                                onClick = {
                                    menuOpen = false
                                    current?.let { e -> editableProbe(e)?.let { probe -> markupVm.open(e.path, probe) } }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.image_share_sanitized)) },
                                // 되는 형식에서만 켠다. HEIC·AVIF·RAW 는 라이브러리가 쓰기를
                                // 지원하지 않고, 재인코딩은 사용자의 HEIC 를 JPEG 으로 바꾸는 일이다.
                                enabled = current?.let { ExifReader.canSanitize(it.name) } == true,
                                onClick = {
                                    menuOpen = false
                                    current?.let { e ->
                                        onShareSanitized(e) { scope.launch { snackbar.showSnackbar(sanitizeFailed) } }
                                    }
                                },
                            )
                            // 지금 장을 다른 앱으로. 이 뷰어가 **열어 보인** 그림에도 둔다 — 편집기·다른 갤러리로
                            // 넘기려는 사람이 있다. 못 연 장에는 그 장 안에 단추가 따로 있다. 파일이 없어진 장에는 두지
                            // 않는다 — 받는 앱도 그 파일에 닿지 못한다.
                            ImageOpenWith.menuTarget(current, current?.let { pageFailures[it.path] })?.let { target ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)) },
                                    onClick = {
                                        menuOpen = false
                                        openWith(target)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }

        // 아래 막대와 '첫 장면만' 고지를 **한 칸에 쌓는다.** 고지는 막대 바로 위에 서고, 막대가 사라지면
        // 그 자리로 내려간다 — 막대의 높이(글꼴 배율·내비게이션 바 인셋에 따라 다르다)를 상수 dp 로
        // 비켜 세우지 않는다(함정 표). 고지는 막대를 감춰도 남는다: 막대를 치운 채 넘겨 온 APNG 가
        // 아무 말 없이 멈춰 있으면 6단계 감사가 찾은 그 결함 그대로다.
        val barState = remember { MutableTransitionState(true) }
        // 구성이 확정된 뒤에 바꾼다 — 구성 도중에 상태를 쓰면 버려질 구성이 애니메이션을 건드린다.
        SideEffect { barState.targetState = chromeVisible }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = dismiss.chromeAlpha }
                .then(behindCrop),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val reason = entries.getOrNull(pagerState.currentPage)?.let { stillReasons[it.path] }
            if (reason != null) {
                // 막대가 완전히 걷힌 뒤에는 고지가 내비게이션 바를 스스로 비킨다.
                val barGone = !barState.currentState && !barState.targetState
                StillNotice(
                    reason = reason,
                    modifier = if (barGone) Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility) else Modifier,
                )
            }
            AnimatedVisibility(
                visibleState = barState,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            R.string.image_position,
                            pagerState.currentPage + 1,
                            entries.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }

        // 고르는 화면은 뷰어를 통째로 덮는다(아래 페이저에 손가락이 닿지 않게 그 화면이 막는다). 스낵바는 그 위다.
        cropping?.let { (entry, probe) ->
            CropScreen(
                path = entry.path,
                probe = probe,
                onCancel = { cropping = null },
                onSave = { rect ->
                    onCrop?.invoke(entry, rect, probe.displayWidth, probe.displayHeight)
                    cropping = null
                },
            )
        }

        // 그리는 화면도 같은 꼴로 덮는다. 저장하면 그린 것을 실은 요청이 큐로 가고 화면은 닫힌다 — 결과는 이 화면의 스낵바가 말한다.
        if (onMarkup != null) {
            markup?.let { s ->
                MarkupScreen(
                    vm = markupVm,
                    session = s,
                    budget = budget,
                    onSave = { overlay ->
                        onMarkup(s.path, overlay, s.width, s.height)
                        markupVm.close()
                        markupPath = null
                    },
                    onClose = {
                        markupVm.close()
                        markupPath = null
                    },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }

    // 저장할 수 없는 형식을 돌렸을 때의 고지. 스낵바는 공용이라 여기서 띄우면
    // 어느 화면으로 가든 한 번은 보인다.
    val rotateUnsupported = stringResource(R.string.image_rotate_unsupported)
    LaunchedEffect(rotateNotice) {
        if (rotateNotice) {
            snackbar.showSnackbar(rotateUnsupported)
            rotateNotice = false
        }
    }

    if (showInfo) {
        entries.getOrNull(pagerState.currentPage)?.let { entry ->
            ExifSheet(file = File(entry.path), onDismiss = { showInfo = false })
        }
    }
}

/**
 * 움직이는 그림을 첫 장면만 보여 준다는 고지. 문구는 까닭마다 다르고, 까닭을 만드는 타입 곁(`core:ui` 의
 * `StillReasonText`)에 있다 — 만화 뷰어와 같은 사실을 한 벌로 말한다.
 */
@Composable
private fun StillNotice(reason: AnimationPlan.StillReason, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(reason.messageRes()),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 한 장. 디코딩과 상태는 여기 갇힌다 — 페이저는 어느 장이 떠 있는지만 안다. */
@Composable
private fun ImagePage(
    path: String,
    generation: Int,
    extraRotation: Int,
    zoomState: ZoomState,
    budget: ImageLimits.Budget,
    /** 자리 잡은 장인가. 움직이는 그림은 이때만 튼다. */
    active: Boolean,
    onTap: () -> Unit,
    /** 첫 장면만 보여 주게 됐으면 그 까닭을, 아니면 null 을 알린다. */
    onStillReason: (AnimationPlan.StillReason?) -> Unit,
    /** 못 열었으면 그 까닭을, 열었거나 여는 중이면 null 을 알린다. */
    onFailure: (ImageOpenWith.PageFailure?) -> Unit,
    /** 이 장의 파일을 다른 앱으로 연다. 못 연 장의 단추가 부른다. */
    onOpenWith: () -> Unit,
    /** 잘라 저장할 수 있으면 원본 치수를, 아니면 null 을 알린다(정지 그림으로 열렸고 치수를 쟀을 때만). */
    onCroppable: (ImageProbe?) -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val targetLongest = remember(config) {
        with(density) {
            maxOf(config.screenWidthDp.dp.toPx(), config.screenHeightDp.dp.toPx()).toInt()
        }
    }

    // 세 상태다 — 여는 중 / 열림 / 못 엶. 셋을 가르지 않으면 SVG·TIFF 처럼 우리가
    // 디코딩하지 못하는 파일에서 **영원히 도는 동그라미**가 남는다.
    //
    // **원본 치수를 함께 잰다**(`probe`). 최대 배율이 원본의 2배이고, 바닥층은 화면에
    // 맞춰 줄여 뜬 것이라 원본이 아니다. `Ready` 전에 재 두어 첫 프레임부터 상한이 옳다.
    val opened by produceState<PageState>(PageState.Loading, path, generation) {
        value = openPage(path, targetLongest, budget)
    }
    // SVG 를 그리던 엔진(WebView 의 렌더러 프로세스)이 죽었다 — 그 장은 못 연 장으로 보인다. 파일은 멀쩡하니 다른 앱으로 열 수 있다.
    var renderGone by remember(path, generation) { mutableStateOf(false) }
    val state = if (renderGone && opened is PageState.Vector) PageState.Failed(ImageOpenWith.PageFailure.UNDECODABLE) else opened
    val reportNow by rememberUpdatedState(onStillReason)
    LaunchedEffect(state) { reportNow((state as? PageState.Still)?.reason) }
    val reportFailure by rememberUpdatedState(onFailure)
    LaunchedEffect(state) { reportFailure((state as? PageState.Failed)?.failure) }
    val reportCroppable by rememberUpdatedState(onCroppable)
    LaunchedEffect(state) {
        // 움직이는 그림을 첫 장면만 보이는 장(APNG·너무 큰 GIF)은 자르지 않는다 — 첫 장면만 남는 것을 잘라 낸 그림이라 할 수 없다.
        reportCroppable((state as? PageState.Still)?.takeIf { it.reason == null }?.probe?.takeIf { !it.isAnimated })
    }

    // 디버그 빌드에서만 남는다. 제스처는 눈으로만 확인할 수 있는 것이 많아서, 배율만은
    // 기계가 읽을 수 있게 찍어 둔다 — 'pan 인가 page 인가' 를 화면 캡처로 판정하려 들면
    // 판정이 흔들린다.
    LaunchedEffect(zoomState.scale) {
        Iro.d { "배율 ${zoomState.scale} · canPan=${zoomState.canPan}" }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val st = state) {
            is PageState.Loading -> CircularProgressIndicator(color = Color.White)
            is PageState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = stringResource(R.string.image_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // 이 기기가 못 푸는 그림은 다른 앱이 열 수 있다. 파일이 사라진 장에는 달지 않는다 — 넘겨도 열리지 않는다.
                if (ImageOpenWith.offersOnFailure(st.failure)) {
                    Button(onClick = onOpenWith, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with))
                    }
                }
            }
            is PageState.Vector -> SvgPage(
                prepared = st.prepared,
                generation = generation,
                onTap = onTap,
                onRenderGone = { renderGone = true },
                modifier = Modifier.fillMaxSize(),
            )
            is PageState.Moving -> {
                val painter = rememberAnimatedPainter(st.drawable, playing = active)
                LaunchedEffect(active) {
                    Iro.d {
                        "움직이는 그림 ${if (active) "튼다" else "멈춘다"} · 프레임 " +
                            "${st.drawable.intrinsicWidth}x${st.drawable.intrinsicHeight} · 원본 ${st.probe.displayWidth}"
                    }
                }
                ZoomablePainter(
                    painter = painter,
                    state = zoomState,
                    // 저장이 끝나기 전까지의 **화면상 회전**(GIF 는 저장하지 못한다 — 고지가 따로 뜬다).
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = extraRotation.toFloat() },
                    onTap = onTap,
                    // 프레임은 예산에 맞춰 줄여 떴을 수 있다. 최대 배율은 원본으로 잰다.
                    originalWidth = st.probe.displayWidth,
                )
            }
            is PageState.Still -> {
                val probe = st.probe
                // 조각을 어느 방향으로 돌려 얹을지 — 한 장에 한 번만 잰다. 180°·거울상·정사각은 확대해서
                // 조각이 처음 필요해질 때 화소로 재고, 가를 수 없으면 흐린 채로 확대된다(`RegionOrientation`).
                val orientation = remember(path, generation, probe) { probe?.let { RegionOrientation(path, it) } }
                val detail = rememberRasterDetail(
                    key = path to generation,
                    state = zoomState,
                    baseWidth = st.bitmap.width,
                    originalWidth = probe?.displayWidth ?: 0,
                    originalHeight = probe?.displayHeight ?: 0,
                    capBytes = budget.detailCap,
                ) { want ->
                    val p = probe ?: return@rememberRasterDetail null
                    val o = orientation?.tileOrientation() ?: return@rememberRasterDetail null
                    val stored = RegionMath.toStored(
                        o, p.storedWidth, p.storedHeight,
                        intArrayOf(want.left, want.top, want.right, want.bottom),
                    )
                    Iro.d { "조각 방향 $o · EXIF ${p.orientation} · 치수 판정 ${p.decoderAppliesOrientation}" }
                    ImageIo.decodeRegionOriented(path, stored, want.sample, o)
                }
                LaunchedEffect(detail) {
                    detail?.let {
                        Iro.d {
                            "조각 ${it.image.width}x${it.image.height} @(${it.left},${it.top}) " +
                                "원본 ${probe?.displayWidth}x${probe?.displayHeight} 최대 ${zoomState.maxScale}"
                        }
                    }
                }
                ZoomableImage(
                    bitmap = st.bitmap.asImageBitmap(),
                    state = zoomState,
                    // 저장이 끝나기 전까지의 **화면상 회전**. 파일이 다시 읽히면 0 으로 돌아간다.
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = extraRotation.toFloat() },
                    onTap = onTap,
                    detail = detail,
                    // 최대 배율은 **원본의 2배**다. 원본을 모르면 바닥층으로 친다.
                    originalWidth = probe?.displayWidth ?: st.bitmap.width,
                )
            }
        }
    }
}

/**
 * 한 장을 연다 — 틀 것인가, 첫 장면만 보여 줄 것인가, 못 여는가.
 *
 * 판단은 `AnimationPlan` 이 하고(순수 함수, JVM 시험), 여기는 그 답대로 디코딩만 한다. 틀기로 했는데
 * 움직이는 그림으로 열지 못하면 첫 장면으로 물러나되 **그렇게 말한다**(`DECODE_FAILED`).
 */
private suspend fun openPage(path: String, targetLongest: Int, budget: ImageLimits.Budget): PageState {
    // **SVG 는 플랫폼 디코더가 없다** — 잠긴 WebView 로 그린다(`SvgPage`). 뿌리 `<svg>` 를 확인하고 `svgz` 를 푸는 일은 입출력과
    // 압축 풀기다. **뿌리가 svg 가 아니면 아래 비트맵 길로 간다** — 내용이 PNG·JPEG 인 `.svg` 를 디코더는 내용으로 연다(`MimeResolver`
    // 머리말의 '내용이 이긴다'. 처음 판은 그런 파일을 '열 수 없다' 로 끝냈다). 비트맵도 아니면 거기서 '열 수 없다' 다.
    if (SvgSupport.isSvgName(path)) {
        withContext(IroDispatchers.parsing) { SvgSupport.prepare(File(path)) }?.let { return PageState.Vector(it) }
    }
    val probe = ImageIo.probe(path)
    // APNG 은 플랫폼이 정지 PNG 으로 준다 — 청크를 우리가 훑어야 안다. PNG 일 때만 연다(IO).
    val apng = probe != null && !probe.isAnimated && probe.mimeType == "image/png" &&
        withContext(IroDispatchers.io) { ImageFormats.isApng(File(path)) }
    val plan = if (probe == null) {
        AnimationPlan.Plan.Still(null)
    } else {
        AnimationPlan.plan(probe.isAnimated, apng, probe.displayWidth, probe.displayHeight, targetLongest, budget)
    }
    Iro.d { "그림 계획 $plan · ${probe?.mimeType}" }

    var reason = (plan as? AnimationPlan.Plan.Still)?.reason
    if (plan is AnimationPlan.Plan.Animate && probe != null) {
        when (val drawable = decodeAnimated(path, targetLongest, minSample = plan.sample)) {
            is AnimatedImageDrawable -> return PageState.Moving(drawable, probe)
            // 헤더는 움직인다고 했는데 한 장짜리로 열렸다 — 말할 것이 없는 정지 그림이다.
            null -> reason = AnimationPlan.StillReason.DECODE_FAILED
            else -> reason = null
        }
    }
    val bitmap = ImageIo.decodeFitted(path, targetLongest) ?: return PageState.Failed(failureOf(path))
    return PageState.Still(bitmap, probe, reason)
}

/**
 * 디코딩이 실패한 장을 한 번 더 본다 — 파일이 **있는데** 못 푼 것인가, 파일이 **없어진**(열리지 않는) 것인가. `decodeFitted` 는
 * 둘 다 null 이라 이것 없이는 가를 수 없다([ImageOpenWith.failureOf]). 여는 탐침이라 입출력 디스패처에서 돈다.
 */
private suspend fun failureOf(path: String): ImageOpenWith.PageFailure = withContext(IroDispatchers.io) {
    val file = File(path)
    ImageOpenWith.failureOf(isFile = file.isFile, opens = ImageOpenWith.opensForReading(file))
}

/** 한 장의 상태. */
private sealed interface PageState {
    data object Loading : PageState

    /** 못 열었다. 까닭에 따라 '다른 앱으로 열기' 단추를 단다([ImageOpenWith.offersOnFailure]). */
    data class Failed(val failure: ImageOpenWith.PageFailure) : PageState

    data class Still(
        val bitmap: android.graphics.Bitmap,
        /** 원본 치수와 방향. 재지 못했으면 null — 그때는 바닥층 크기로 친다. */
        val probe: ImageProbe?,
        /** 움직이는 그림을 첫 장면만 보여 주는 까닭. 처음부터 정지 그림이면 null. */
        val reason: AnimationPlan.StillReason?,
    ) : PageState

    /** SVG. WebView 가 그리고 확대한다([SvgPage]). */
    data class Vector(val prepared: SvgSupport.Prepared) : PageState

    /** 움직이는 그림. 프레임 버퍼는 `AnimationPlan` 이 예산에 맞춘 표본으로 떴다. */
    data class Moving(
        val drawable: AnimatedImageDrawable,
        val probe: ImageProbe,
    ) : PageState
}
