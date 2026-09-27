package io.github.donggi.iroiroviewer.image

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.ui.icon.RotateRightIcon
import io.github.donggi.iroiroviewer.ui.image.ExifReader
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
 * | 한 번 탭 | 상·하단 막대 감추기/보이기 | 사진만 보고 싶을 때 즉시 치운다 |
 * | 더블탭 | 짚은 자리를 중심으로 확대/원래대로 | 두 손가락을 쓸 수 없는 한 손 조작 |
 * | 핀치 | 확대·축소 | |
 * | 끌기(확대 상태) | 사진 안에서 이동 | |
 * | 좌우로 밀기(배율 1) | 다음/이전 사진 | **폴더 목록 그대로**를 순서로 쓴다 |
 * | 뒤로가기 | 목록으로 | 아래로 끌어 닫기와 **같은 곳**으로 간다 |
 *
 * ## 이 화면이 바깥 `Scaffold` 밖에 있는 이유
 *
 * `Scaffold` 의 기본 인셋은 시스템 바를 따라가고, 그 값은 막대가 숨겨지고 나타나는
 * **애니메이션 내내 프레임마다 변한다.** 그 패딩을 받으면 확대·이동의 기준 상자가
 * 프레임마다 움직여 경계 계산이 어긋난다. 사진은 인셋 0 으로 꽉 채우고, 겹치는 막대만
 * 자기 인셋을 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    entries: List<FileEntry>,
    startIndex: Int,
    snackbar: SnackbarHostState,
    /** 파일 내용이 바뀐 횟수. 회전은 수정시각을 보존하므로 이것 말고는 단서가 없다. */
    generation: Int,
    onClose: () -> Unit,
    onShare: (FileEntry) -> Unit,
    onShareSanitized: (FileEntry) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onRotate: (FileEntry, Int) -> Unit,
    modifier: Modifier = Modifier,
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
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var rotateNotice by remember { mutableStateOf(false) }

    // 화면에서 먼저 돌리고, 파일 쓰기는 뒤에서 한다. 저장이 끝나면 [generation] 이 올라
    // 다시 디코딩되므로 이 값을 0 으로 되돌린다 — 그러지 않으면 **두 번 돌아간다.**
    val displayRotation = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(generation) { displayRotation.clear() }

    BackHandler { onClose() }

    // 화면에서 벗어난 장의 확대를 되돌린다. 되돌리지 않으면 나중에 그 장으로 돌아왔을 때
    // 전에 확대해 둔 자리에서 시작해 '내가 뭘 한 거지' 가 된다.
    val pages = remember(entries) { HashMap<String, io.github.donggi.iroiroviewer.ui.gesture.ZoomState>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            pages.forEach { (path, st) -> if (path != entries.getOrNull(settled)?.path) st.reset() }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            // 앞뒤 한 장씩만 살려 둔다. 넘기는 동안 두 장이 화면에 걸치므로 최악은 넷이다.
            // 이보다 줄이면 되돌아 밀 때마다 다시 디코딩해 체감이 그만큼 나빠진다.
            beyondViewportPageCount = 1,
            key = { entries[it].path },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries[page]
            ImagePage(
                path = entry.path,
                generation = generation,
                extraRotation = displayRotation[entry.path] ?: 0,
                zoomState = pages.getOrPut(entry.path) {
                    io.github.donggi.iroiroviewer.ui.gesture.ZoomState()
                },
                onTap = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val current = entries.getOrNull(pagerState.currentPage)
            TopAppBar(
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
                    IconButton(onClick = { current?.let(onDelete) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.image_delete))
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
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(RotateRightIcon, null)
                                },
                                text = { Text(stringResource(R.string.image_rotate)) },
                                onClick = {
                                    menuOpen = false
                                    current?.let { e ->
                                        // 화면은 **언제나** 즉시 돈다. 저장은 되는 형식에서만.
                                        displayRotation[e.path] = ((displayRotation[e.path] ?: 0) + 90) % 360
                                        if (ExifReader.canRotate(java.io.File(e.path))) {
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.image_share_sanitized)) },
                                // 되는 형식에서만 켠다. HEIC·AVIF·RAW 는 라이브러리가 쓰기를
                                // 지원하지 않고, 재인코딩은 사용자의 HEIC 를 JPEG 으로 바꾸는 일이다.
                                enabled = current?.let { ExifReader.canSanitize(it.name) } == true,
                                onClick = {
                                    menuOpen = false
                                    current?.let(onShareSanitized)
                                },
                            )
                        }
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
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

/** 한 장. 디코딩과 상태는 여기 갇힌다 — 페이저는 어느 장이 떠 있는지만 안다. */
@Composable
private fun ImagePage(
    path: String,
    generation: Int,
    extraRotation: Int,
    zoomState: io.github.donggi.iroiroviewer.ui.gesture.ZoomState,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val targetLongest = remember(config) {
        with(density) {
            maxOf(config.screenWidthDp.dp.toPx(), config.screenHeightDp.dp.toPx()).toInt()
        }
    }

    // 선명화 조각 한 장의 상한. 만화·PDF 와 같은 예산표에서 나온다(화면 크기 + 힙 등급).
    val detailCap = remember(config) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val m = context.resources.displayMetrics
        io.github.donggi.iroiroviewer.safety.ImageLimits
            .budgetOf(m.widthPixels, m.heightPixels, am.memoryClass).detailCap
    }

    // 세 상태다 — 여는 중 / 열림 / 못 엶. 셋을 가르지 않으면 SVG·TIFF 처럼 우리가
    // 디코딩하지 못하는 파일에서 **영원히 도는 동그라미**가 남는다.
    //
    // **원본 치수를 함께 잰다**(`probe`). 최대 배율이 원본의 2배이고, 바닥층은 화면에
    // 맞춰 줄여 뜬 것이라 원본이 아니다. `Ready` 전에 재 두어 첫 프레임부터 상한이 옳다.
    val state by androidx.compose.runtime.produceState<PageState>(PageState.Loading, path, generation) {
        val probe = io.github.donggi.iroiroviewer.ui.image.ImageIo.probe(path)
        val bmp = io.github.donggi.iroiroviewer.ui.image.ImageIo.decodeFitted(path, targetLongest)
        value = if (bmp != null) PageState.Ready(bmp, probe) else PageState.Failed
    }

    // 디버그 빌드에서만 남는다. 제스처는 눈으로만 확인할 수 있는 것이 많아서, 배율만은
    // 기계가 읽을 수 있게 찍어 둔다 — 'pan 인가 page 인가' 를 화면 캡처로 판정하려 들면
    // 판정이 흔들린다.
    LaunchedEffect(zoomState.scale) {
        io.github.donggi.iroiroviewer.io.Iro.d { "배율 ${zoomState.scale} · canPan=${zoomState.canPan}" }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val st = state) {
            is PageState.Loading -> CircularProgressIndicator(color = Color.White)
            is PageState.Failed -> Text(
                text = stringResource(R.string.image_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(32.dp),
            )
            is PageState.Ready -> {
                val probe = st.probe
                // **방향을 확실히 아는 파일만 영역 디코딩한다**(`canUseRegionDecoder`). 180°·
                // 거울상은 디코더가 방향을 적용했는지 잴 수 없어, 조각이 뒤집혀 얹힐 수 있다 —
                // 그 파일은 흐린 채로 확대된다.
                val regional = probe?.takeIf { it.canUseRegionDecoder }
                // 바닥층이 방향을 적용해 떠 있을 때만 조각을 돌린다. 적용하지 않았다면 바닥층도
                // 저장 방향 그대로라 조각도 그대로여야 한다.
                val orientation = if (regional?.decoderAppliesOrientation == true) {
                    regional.orientation
                } else {
                    io.github.donggi.iroiroviewer.ui.image.RegionMath.NORMAL
                }
                val detail = io.github.donggi.iroiroviewer.ui.image.rememberRasterDetail(
                    key = path to generation,
                    state = zoomState,
                    baseWidth = st.bitmap.width,
                    originalWidth = regional?.displayWidth ?: 0,
                    originalHeight = regional?.displayHeight ?: 0,
                    capBytes = detailCap,
                ) { want ->
                    val p = regional ?: return@rememberRasterDetail null
                    val stored = io.github.donggi.iroiroviewer.ui.image.RegionMath.toStored(
                        orientation, p.storedWidth, p.storedHeight,
                        intArrayOf(want.left, want.top, want.right, want.bottom),
                    )
                    io.github.donggi.iroiroviewer.ui.image.ImageIo.decodeRegion(
                        path, stored, want.sample,
                        io.github.donggi.iroiroviewer.ui.image.RegionMath.rotationDegrees(orientation),
                    )
                }
                LaunchedEffect(detail) {
                    detail?.let {
                        io.github.donggi.iroiroviewer.io.Iro.d {
                            "조각 ${it.image.width}x${it.image.height} @(${it.left},${it.top}) " +
                                "원본 ${probe?.displayWidth}x${probe?.displayHeight} 최대 ${zoomState.maxScale}"
                        }
                    }
                }
                io.github.donggi.iroiroviewer.ui.image.ZoomableImage(
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

/** 한 장의 상태. */
private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Ready(
        val bitmap: android.graphics.Bitmap,
        /** 원본 치수와 방향. 재지 못했으면 null — 그때는 바닥층 크기로 친다. */
        val probe: io.github.donggi.iroiroviewer.ui.image.ImageProbe?,
    ) : PageState
}
