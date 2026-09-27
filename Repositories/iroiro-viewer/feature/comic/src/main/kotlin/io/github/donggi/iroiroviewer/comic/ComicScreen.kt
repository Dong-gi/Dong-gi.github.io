package io.github.donggi.iroiroviewer.comic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.ui.PasswordDialog
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import io.github.donggi.iroiroviewer.ui.image.ZoomableImage
import io.github.donggi.iroiroviewer.ui.image.rememberAnimatedPainter
import io.github.donggi.iroiroviewer.ui.image.rememberRasterDetail

/**
 * 만화 한 권을 읽는 화면.
 *
 * ## 이미지 뷰어와 무엇이 다른가
 *
 * 겉모습은 닮았지만 세 가지가 다르고, 그 셋이 이 화면이 따로 있는 이유다.
 *
 * | | 이미지 뷰어(6단계) | 만화 뷰어 |
 * |---|---|---|
 * | 쪽의 출처 | 디스크 위의 파일 | **아카이브 안의 바이트**(경로가 없다) |
 * | 차례 | 폴더 목록 그대로 | **이름의 자연 정렬**(`ComicPages`) |
 * | 방향 | 왼→오 고정 | 왼→오 · **오→왼** · 세로 스크롤 |
 *
 * ## 바깥 `Scaffold` 를 씌우지 않는다
 *
 * 6단계와 같은 이유다 — `Scaffold` 의 기본 인셋은 막대가 숨고 나타나는 애니메이션
 * 내내 프레임마다 변하고, 그 패딩을 받으면 확대·이동의 기준 상자가 함께 움직여 경계
 * 계산이 어긋난다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicScreen(
    path: String,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 압축 목록에서 그림 항목을 탭해 들어온 경우의 엔트리 번호. -1 이면 이어보기가 정한다. */
    startEntryIndex: Int = -1,
) {
    val vm: ComicViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val direction by vm.direction.collectAsStateWithLifecycle()
    val asking by vm.asking.collectAsStateWithLifecycle()

    LaunchedEffect(path, startEntryIndex) { vm.open(path, startEntryIndex) }

    var chromeVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(0 to 0) }

    BackHandler {
        vm.close()
        onClose()
    }

    // 이어보기를 알린다. **'처음부터' 를 되돌리기처럼 붙인다** — 5단계 재생이
    // 이어보기에 쓴 것과 같은 모양이라 사용자가 새로 배울 것이 없다.
    val resumedFormat = stringResource(R.string.comic_resumed)
    val restartLabel = stringResource(R.string.comic_restart)
    LaunchedEffect(vm) {
        vm.eventFlow.collect { event ->
            when (event) {
                is ComicViewModel.Event.Resumed -> {
                    val result = snackbar.showSnackbar(
                        message = String.format(resumedFormat, event.page + 1),
                        actionLabel = restartLabel,
                        // **기간을 손으로 적는다.** 생략하면 Material 3 이
                        // `actionLabel != null` 일 때 `Indefinite` 를 넣어, 누르거나
                        // 밀기 전에는 사라지지 않는다. 재생 쪽에서 사용자가 "제한시간
                        // 없이 계속 노출된다" 고 지적한 것이 바로 그것이었다.
                        // `Long`(10초)은 단추를 누를 시간은 되면서 쪽을 넘기는 동안
                        // 화면에 눌어붙지는 않는 길이다.
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restart()
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                if (it.width > 0 && it.height > 0) {
                    viewport = it.width to it.height
                    vm.onViewport(it.width, it.height)
                }
            },
    ) {
        when (val s = state) {
            is ComicViewModel.State.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Color.White) }

            is ComicViewModel.State.Failed -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(failureText(s.kind)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // 암호가 필요하면 창을 닫아도 다시 열 길을 남긴다.
                    if (s.kind == ComicOpen.Kind.NEEDS_PASSWORD) {
                        Button(onClick = vm::requestPassword, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.comic_password_enter))
                        }
                    }
                }
            }

            is ComicViewModel.State.Ready -> {
                if (viewport.first > 0) {
                    if (direction == ComicViewModel.Direction.VERTICAL) {
                        VerticalReader(
                            book = s.book,
                            vm = vm,
                            viewportWidth = viewport.first,
                            viewportHeight = viewport.second,
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    } else {
                        PagedReader(
                            book = s.book,
                            vm = vm,
                            rightToLeft = direction == ComicViewModel.Direction.RTL,
                            viewportLongest = maxOf(viewport.first, viewport.second),
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                }
            }
        }

        val book = (state as? ComicViewModel.State.Ready)?.book
        val current by vm.page.collectAsStateWithLifecycle()

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = { vm.close(); onClose() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.comic_close),
                        )
                    }
                },
                title = {
                    Text(
                        text = book?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.comic_settings))
                    }
                },
            )
        }

        if (book != null) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    page = current,
                    pageCount = book.pageCount,
                    rightToLeft = direction == ComicViewModel.Direction.RTL,
                    onSeek = { vm.onPageChanged(it) },
                    onJump = { showJump = true },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            DirectionPicker(
                current = direction,
                onPick = { vm.setDirection(it) },
                solid = (state as? ComicViewModel.State.Ready)?.book?.solid == true,
            )
        }
    }

    if (showJump) {
        val count = (state as? ComicViewModel.State.Ready)?.book?.pageCount ?: 0
        JumpDialog(
            pageCount = count,
            onDismiss = { showJump = false },
            onGo = {
                showJump = false
                vm.onPageChanged(it)
            },
        )
    }

    asking?.let { wrong ->
        PasswordDialog(
            title = stringResource(R.string.comic_password_title),
            message = stringResource(R.string.comic_password_body),
            wrong = wrong,
            onDismiss = vm::dismissPassword,
            onSubmit = vm::submitPassword,
        )
    }
}

/**
 * 좌우로 넘기는 읽기.
 *
 * **오른쪽에서 왼쪽은 `reverseLayout` 하나로 끝난다.** 목록을 뒤집는 길도 있지만,
 * 그러면 쪽 번호가 목록의 자리와 어긋나 이어보기가 다른 쪽을 가리킨다 — 방향은
 * 보이는 순서의 문제이지 쪽 번호의 문제가 아니다.
 *
 * 뒤집으면 [BottomBar] 의 슬라이더도 함께 뒤집어야 한다. 손가락이 가는 방향과 쪽이
 * 나아가는 방향이 어긋나면 슬라이더가 거꾸로 도는 것처럼 느껴진다.
 */
@Composable
private fun PagedReader(
    book: ComicViewModel.Book,
    vm: ComicViewModel,
    rightToLeft: Boolean,
    viewportLongest: Int,
    onTap: () -> Unit,
) {
    val current by vm.page.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = current.coerceIn(0, (book.pageCount - 1).coerceAtLeast(0)),
        pageCount = { book.pageCount },
    )

    // 페이저 → VM. 사용자가 민 결과다.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { vm.onPageChanged(it) }
    }
    // VM → 페이저. 슬라이더·쪽으로 가기·'처음부터' 가 여기로 들어온다.
    LaunchedEffect(current) {
        if (current != pagerState.currentPage) pagerState.scrollToPage(current)
    }

    // 화면에서 벗어난 쪽의 확대를 되돌린다. 6단계와 같은 이유다 — 되돌리지 않으면
    // 나중에 그 쪽으로 돌아왔을 때 전에 확대해 둔 자리에서 시작한다.
    val zooms = remember(book.path) { HashMap<Int, ZoomState>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            zooms.forEach { (ordinal, st) -> if (ordinal != settled) st.reset() }
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = rightToLeft,
        // 앞뒤 한 장씩. 넘기는 동안 두 장이 걸치므로 최악은 넷이고, `ImageLimits` 의
        // 예산이 정확히 그 넷을 기준으로 계산된다.
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { ordinal ->
        ComicPageView(
            store = book.store,
            ordinal = ordinal,
            targetLongest = viewportLongest,
            zoomState = zooms.getOrPut(ordinal) { ZoomState() },
            onTap = onTap,
        )
    }
}

/** 쪽 하나. 정지·움직임·실패 셋으로 갈린다. */
@Composable
private fun ComicPageView(
    store: ComicPageStore,
    ordinal: Int,
    targetLongest: Int,
    zoomState: ZoomState,
    onTap: () -> Unit,
) {
    val state by produceState<PageState>(PageState.Loading, store, ordinal, targetLongest) {
        val info = store.info(ordinal)
        value = when {
            info == null -> PageState.Failed
            info.animated -> {
                val drawable = store.moving(ordinal, targetLongest)
                if (drawable != null) PageState.Moving(drawable, info) else PageState.Failed
            }
            else -> {
                val bitmap = store.still(ordinal, targetLongest)
                if (bitmap != null) PageState.Still(bitmap, info) else PageState.Failed
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is PageState.Loading -> CircularProgressIndicator(color = Color.White)

            is PageState.Failed -> Text(
                text = stringResource(R.string.comic_page_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(32.dp),
            )

            is PageState.Still -> {
                // 확대하면 원본의 보이는 자리를 다시 떠 얹는다. 최대 배율이 원본의 2배라
                // 바닥층(화면에 맞춰 줄여 뜬 것)만으로는 흐리다.
                val detail = rememberRasterDetail(
                    key = store to ordinal,
                    state = zoomState,
                    baseWidth = s.bitmap.width,
                    originalWidth = if (s.info.canUseRegionDecoder) s.info.width else 0,
                    originalHeight = if (s.info.canUseRegionDecoder) s.info.height else 0,
                    capBytes = store.detailCap,
                ) { want ->
                    store.region(ordinal, intArrayOf(want.left, want.top, want.right, want.bottom), want.sample)
                }
                ZoomableImage(
                    bitmap = s.bitmap,
                    state = zoomState,
                    modifier = Modifier.fillMaxSize(),
                    onTap = onTap,
                    detail = detail,
                    originalWidth = s.info.displayWidth,
                )
                if (s.info.apng) ApngNotice()
            }

            is PageState.Moving -> {
                // **움직이는 쪽은 확대하지 않는다.** `AnimatedImageDrawable` 은 프레임을
                // 스스로 그리므로 확대·이동 변환을 층으로 얹으면 프레임마다 다시 합성해야
                // 하고, 그 비용이 재생을 끊는다. 만화책 안의 GIF 는 드물어 이 제약이
                // 실제로 걸리는 자리가 거의 없다 — 걸리면 그때 고친다.
                Image(
                    painter = rememberAnimatedPainter(s.drawable),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
                )
            }
        }
    }
}

/** 움직이지만 첫 장면만 보여 준다는 고지. 숨기면 앱이 고장난 것처럼 보인다. */
@Composable
private fun ApngNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = stringResource(R.string.comic_apng_notice),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(bottom = 120.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Still(
        val bitmap: androidx.compose.ui.graphics.ImageBitmap,
        val info: PageInfo,
    ) : PageState

    data class Moving(
        val drawable: android.graphics.drawable.Drawable,
        val info: PageInfo,
    ) : PageState
}

/**
 * 세로 스크롤(웹툰) 읽기.
 *
 * ## 왜 쪽이 아니라 **띠**가 항목인가
 *
 * 웹툰 한 회는 폭 800 에 높이 10,000 을 넘는 것이 흔하다. 뷰포트 폭에 맞춰 통짜로 뜨면
 * 1080×13,500 = **55 MiB** 한 장이고, 스크롤 중에는 그런 것이 둘씩 걸린다. 표본을 키워
 * 흐리게 만드는 길도 있지만 웹툰은 **글자를 읽는 그림**이라 그것은 답이 아니다.
 *
 * 그래서 긴 쪽은 화면 한 장 분량의 띠로 잘라 `LazyColumn` 의 항목으로 낸다
 * (`ComicLimits.isTall`·`bandHeight`). 재활용은 `LazyColumn` 이 이미 하는 일이다.
 *
 * ## 치수를 모르는 쪽
 *
 * 쪽을 재려면 바이트를 읽어야 하고, solid 아카이브에서 그것은 싸지 않다. 그래서 미리
 * 전부 재지 않고 **보이는 것부터** 잰다 — 아직 모르는 쪽은 화면 한 장 크기의 자리만
 * 잡아 두고, 재고 나면 그 자리가 실제 높이로 바뀐다.
 */
@Composable
private fun VerticalReader(
    book: ComicViewModel.Book,
    vm: ComicViewModel,
    viewportWidth: Int,
    viewportHeight: Int,
    onTap: () -> Unit,
) {
    val store = book.store
    val infos = remember(book.path) { mutableStateMapOf<Int, PageInfo>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val current by vm.page.collectAsStateWithLifecycle()

    val items = remember(infos.size, viewportWidth, viewportHeight, book.pageCount) {
        buildVerticalItems(book.pageCount, infos, store, viewportWidth, viewportHeight)
    }

    // 보이는 첫 항목이 어느 쪽인가. 그것이 '지금 쪽' 이고 이어보기가 저장하는 값이다.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            items.getOrNull(index)?.let { vm.onPageChanged(it.page) }
        }
    }
    // 슬라이더·쪽으로 가기에서 들어오는 이동.
    LaunchedEffect(current) {
        val index = items.indexOfFirst { it.page == current && it.band == 0 }
        if (index >= 0 && index != listState.firstVisibleItemIndex) {
            listState.scrollToItem(index)
        }
    }

    val density = LocalDensity.current
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        items(items.size, key = { items[it].key }) { i ->
            val item = items[i]
            val heightDp = with(density) { item.heightPx.toDp() }
            Box(
                Modifier.fillMaxWidth().height(heightDp),
                contentAlignment = Alignment.Center,
            ) {
                VerticalPiece(
                    store = store,
                    item = item,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    onInfo = { infos[item.page] = it },
                )
            }
        }
    }
}

/** 세로 모드의 항목 하나 — 통짜 쪽이거나 긴 쪽의 띠 하나다. */
private data class VItem(
    val page: Int,
    val band: Int,
    val heightPx: Int,
    /** 치수를 아직 재지 못한 자리인가. */
    val placeholder: Boolean,
) {
    val key: Long get() = page.toLong() * 4096 + band
}

private fun buildVerticalItems(
    pageCount: Int,
    infos: Map<Int, PageInfo>,
    store: ComicPageStore,
    viewportWidth: Int,
    viewportHeight: Int,
): List<VItem> {
    val out = ArrayList<VItem>(pageCount)
    for (page in 0 until pageCount) {
        val info = infos[page]
        if (info == null || info.width <= 0) {
            out += VItem(page, 0, viewportHeight, placeholder = true)
            continue
        }
        val bands = store.bandCount(info, viewportWidth, viewportHeight)
        if (bands <= 1) {
            val h = (info.height.toLong() * viewportWidth / info.width)
                .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            out += VItem(page, 0, h, placeholder = false)
            continue
        }
        val bandSrc = io.github.donggi.iroiroviewer.safety.ComicLimits
            .bandHeight(info.width, viewportWidth, viewportHeight)
        for (b in 0 until bands) {
            val srcHeight = minOf(bandSrc, info.height - b * bandSrc).coerceAtLeast(1)
            val h = (srcHeight.toLong() * viewportWidth / info.width)
                .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            out += VItem(page, b, h, placeholder = false)
        }
    }
    return out
}

@Composable
private fun VerticalPiece(
    store: ComicPageStore,
    item: VItem,
    viewportWidth: Int,
    viewportHeight: Int,
    onInfo: (PageInfo) -> Unit,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null, store, item.page, item.band, viewportWidth,
    ) {
        val info = store.info(item.page)
        if (info == null) {
            value = null
            return@produceState
        }
        onInfo(info)
        value = if (store.bandCount(info, viewportWidth, viewportHeight) <= 1) {
            store.still(item.page, targetLongestFor(info.width, info.height, viewportWidth))
        } else {
            store.band(item.page, item.band, viewportWidth, viewportHeight)
        }
    }

    val image = bitmap
    if (image == null) {
        CircularProgressIndicator(color = Color.White)
    } else {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BottomBar(
    page: Int,
    pageCount: Int,
    rightToLeft: Boolean,
    onSeek: (Int) -> Unit,
    onJump: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pageCount > 1) {
            // **오른쪽에서 왼쪽으로 읽으면 슬라이더도 뒤집는다.** 손가락이 가는 방향과
            // 쪽이 나아가는 방향이 어긋나면 슬라이더가 거꾸로 도는 것처럼 느껴진다.
            val value = if (rightToLeft) (pageCount - 1 - page) else page
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    val v = it.toInt().coerceIn(0, pageCount - 1)
                    onSeek(if (rightToLeft) pageCount - 1 - v else v)
                },
                valueRange = 0f..(pageCount - 1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.comic_page_of, page + 1, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            TextButton(onClick = onJump) { Text(stringResource(R.string.comic_jump)) }
        }
    }
}

@Composable
private fun DirectionPicker(
    current: ComicViewModel.Direction,
    onPick: (ComicViewModel.Direction) -> Unit,
    solid: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.comic_direction), style = MaterialTheme.typography.titleMedium)
        val options = listOf(
            ComicViewModel.Direction.LTR to R.string.comic_direction_ltr,
            ComicViewModel.Direction.RTL to R.string.comic_direction_rtl,
            ComicViewModel.Direction.VERTICAL to R.string.comic_direction_vertical,
        )
        for ((value, label) in options) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(value) { detectTapGestures(onTap = { onPick(value) }) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == value, onClick = { onPick(value) })
                Text(stringResource(label))
            }
        }
        if (solid) {
            // 되돌아갈 때 느린 이유를 미리 말해 둔다. 8단계 실측이 7z 의 무작위 접근을
            // 제곱 비용으로 재 두었고, 이 화면은 그 사실을 숨기지 않는다.
            Text(
                text = stringResource(R.string.comic_solid_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
        } else {
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun JumpDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val number = text.toIntOrNull()
    val valid = number != null && number in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comic_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.comic_jump_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onGo(number!! - 1) }) {
                Text(stringResource(R.string.comic_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comic_cancel)) }
        },
    )
}

private fun failureText(kind: ComicOpen.Kind): Int = when (kind) {
    ComicOpen.Kind.UNREADABLE -> R.string.comic_failed_unreadable
    ComicOpen.Kind.CORRUPT -> R.string.comic_failed_corrupt
    ComicOpen.Kind.UNSUPPORTED -> R.string.comic_failed_unsupported
    ComicOpen.Kind.TOO_LARGE -> R.string.comic_failed_too_large
    ComicOpen.Kind.NEEDS_PASSWORD -> R.string.comic_failed_password
    ComicOpen.Kind.ENCRYPTED -> R.string.comic_failed_encrypted
    ComicOpen.Kind.NO_PAGES -> R.string.comic_failed_no_pages
}
