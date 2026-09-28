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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.flow.distinctUntilChanged

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
 * 14단계가 셋을 더했다 — 두 쪽 보기([Spreads]), 쪽 목록([PageGrid]), 다음 권([NextVolume]).
 *
 * **이 앱이 보여 줄 수 없는 책은 다른 앱으로 넘긴다.** 실패 화면의 단추와 ⋮ 메뉴가 **책 파일**을 고르는 창으로 넘긴다
 * — 폴더 자체와 아카이브 안의 쪽은 넘기지 않는다. 폴더로 연 만화의 **못 연 쪽**은 그 쪽의 파일을 넘긴다([FailedPage]).
 * 어느 실패에 단추를 다는지는 [ComicOpenWith] 가 정한다.
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
    /**
     * 다음 권으로 넘어갔다(14단계). 조립하는 쪽이 자기 화면 상태(`AppScreen.Comic`)를 새 경로로 바꾸면 프로세스가
     * 죽었다 살아나도 새 책으로 돌아온다. **주지 않아도 된다** — 화면이 새 경로를 저장 상태에 들고 스스로 연다.
     */
    onOpenBook: ((path: String) -> Unit)? = null,
) {
    val vm: ComicViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val direction by vm.direction.collectAsStateWithLifecycle()
    val asking by vm.asking.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()

    // **지금 여는 책.** 밖에서 받은 경로에서 시작하고, 다음 권으로 넘어가면 이 화면 안에서 바뀐다. 저장 상태에 든다 —
    // 구성 변경과 프로세스 재생성을 견딘다. 이 화면이 컴포지션에서 빠지면(목록으로 돌아가면) 사라지는데, 그때는
    // 다시 들어올 때 밖에서 새 경로를 받으므로 잃을 것이 없다(함정 표의 `rememberSaveable` 항목이 경고하는 모양이 아니다).
    // 밖의 경로가 바뀌면(열쇠) 그 값에서 다시 시작한다.
    var bookPath by rememberSaveable(path, startEntryIndex) { mutableStateOf(path) }
    var bookEntry by rememberSaveable(path, startEntryIndex) { mutableIntStateOf(startEntryIndex) }
    LaunchedEffect(bookPath, bookEntry) { vm.open(bookPath, bookEntry) }

    val onOpenBookNow by rememberUpdatedState(onOpenBook)
    // **여느 책과 같은 길로 연다.** 경로만 바꾸면 위 효과가 `vm.open` 을 부르고, 그 함수가 앞 책의 마지막 쪽을 쓰고
    // 상태를 되돌린 뒤 이어보기를 되살린다 — 다음 권만의 여는 길을 따로 두지 않는다.
    val openBook: (String) -> Unit = { nextPath ->
        bookPath = nextPath
        bookEntry = -1
        onOpenBookNow?.invoke(nextPath)
    }
    // 아래의 알림 수집기는 화면이 사는 내내 한 번만 돈다. 밖의 경로가 바뀌면 위의 저장 상태가 새로 만들어지므로, 첫
    // 컴포지션의 [openBook] 을 붙들고 있으면 **버려진 상태**에 경로를 쓰게 된다 — 늘 지금의 것을 읽는다.
    val openBookNow by rememberUpdatedState(openBook)

    var chromeVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    // 격자는 회전을 견디게 둔다 — 고르던 도중 기기를 돌렸다고 닫히면 처음부터 다시 찾아야 한다.
    var showGrid by rememberSaveable { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(0 to 0) }

    val openWith = rememberOpenWith(snackbar)

    BackHandler {
        vm.close()
        onClose()
    }

    // 이어보기를 알린다. **'처음부터' 를 되돌리기처럼 붙인다** — 5단계 재생이
    // 이어보기에 쓴 것과 같은 모양이라 사용자가 새로 배울 것이 없다.
    val resumedFormat = stringResource(R.string.comic_resumed)
    val restartLabel = stringResource(R.string.comic_restart)
    val nextOfferFormat = stringResource(R.string.comic_next_offer)
    val nextOpenLabel = stringResource(R.string.comic_next_open)
    LaunchedEffect(vm) {
        vm.eventFlow.collect { event ->
            // **그 책이 열려 있는 동안만 띄운다**(14단계). 다음 권은 뷰어를 닫지 않고 책을 바꾸므로, 앞 책의 알림이 새 책
            // 위에 남으면 '처음부터' 가 새 책을 되감는다. 늦게 배달된 것(그 사이 책이 바뀌었다)은 띄우지도 않는다.
            // 기간은 `showWhileCurrent` 가 손으로 적는다 — 생략하면 Material 3 이 `actionLabel != null` 일 때
            // `Indefinite` 를 넣어, 누르거나 밀기 전에는 사라지지 않는다(재생 쪽에서 사용자가 "제한시간 없이 계속
            // 노출된다" 고 지적한 것). `Long`(10초)은 단추를 누를 시간은 되면서 쪽을 넘기는 동안 눌어붙지는 않는 길이다.
            when (event) {
                is ComicViewModel.Event.Resumed -> {
                    val result = snackbar.showWhileCurrent(
                        stillCurrent = { vm.isCurrent(event.fromPath) },
                        changes = vm.state,
                        message = String.format(resumedFormat, event.page + 1),
                        actionLabel = restartLabel,
                    )
                    // 누른 순간과 책이 바뀐 순간이 겹쳤어도 새 책을 되감지 않는다.
                    if (result == SnackbarResult.ActionPerformed && vm.isCurrent(event.fromPath)) vm.restart()
                }

                is ComicViewModel.Event.NextOffer -> {
                    // 이어보기 알림이 떠 있는 동안 이 알림은 줄을 선다.
                    val result = snackbar.showWhileCurrent(
                        stillCurrent = { vm.isCurrent(event.fromPath) },
                        changes = vm.state,
                        message = String.format(nextOfferFormat, event.name),
                        actionLabel = nextOpenLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.nextPathFrom(event.fromPath)?.let { openBookNow(it) }
                    }
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
        val (vw, vh) = viewport
        val twoUp = direction != ComicViewModel.Direction.VERTICAL && Spreads.twoUp(layout, vw, vh)

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
                    // 이 앱이 보여 줄 수 없는 책(다루지 않는 형식·깨짐·풀지 않는 잠금·상한)은 다른 앱이 열 수 있다.
                    // 책 **파일**만 넘긴다 — 폴더로 연 만화에는 서지 않는다([ComicOpenWith]).
                    ComicOpenWith.failureTarget(s.kind, s.file)?.let { target ->
                        Button(onClick = { openWith(target) }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with))
                        }
                    }
                }
            }

            is ComicViewModel.State.Ready -> {
                // 두 쪽을 펼치는가를 소스에 알린다 — solid 의 창이 뛰어든 자리에서 앞 펼침까지 담아, 앞 칸을 뜨느라 패스가
                // 한 번 더 돌지 않게 한다([Spreads.lookBehind]). 부수 효과는 적용 단계에서 곧바로 돌고 쪽을 뜨는 효과의
                // 몸은 그 뒤에 디스패치되므로, 새 페이저의 첫 요청보다 먼저 선다.
                SideEffect { s.book.store.setTwoUp(twoUp) }
                if (vw > 0) {
                    if (direction == ComicViewModel.Direction.VERTICAL) {
                        VerticalReader(
                            book = s.book,
                            vm = vm,
                            viewportWidth = vw,
                            viewportHeight = vh,
                            onTap = { chromeVisible = !chromeVisible },
                            onOpenWith = openWith,
                        )
                    } else {
                        // **두 쪽 보기가 켜지고 꺼지면 페이저를 새로 세운다.** 펼침 수와 번호의 뜻이 통째로 바뀌므로
                        // 옛 페이저의 자리(펼침 번호)를 그대로 들고 가면 엉뚱한 쪽에 선다. 새 페이저는 VM 의 **쪽**에서
                        // 제 펼침을 구해 선다 — 쪽 번호가 유일한 진실이다.
                        key(twoUp) {
                            PagedReader(
                                book = s.book,
                                vm = vm,
                                rightToLeft = direction == ComicViewModel.Direction.RTL,
                                twoUp = twoUp,
                                viewportWidth = vw,
                                viewportHeight = vh,
                                onTap = { chromeVisible = !chromeVisible },
                                onOpenWith = openWith,
                            )
                        }
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
                        text = ComicTitle.of(state, bookPath),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { showGrid = true }) {
                            Icon(ComicIcons.Grid, stringResource(R.string.comic_grid))
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.comic_settings))
                    }
                    val handoff = ComicOpenWith.menuTarget(state)
                    val nextBook = next
                    if (nextBook != null || handoff != null) {
                        MoreMenu(
                            nextName = nextBook?.name,
                            onOpenNext = { nextBook?.let { openBook(it.path) } },
                            onOpenWith = handoff?.let { target -> { openWith(target) } },
                        )
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
                    // 두 쪽 보기에서는 펼침이 담은 쪽을 함께 적는다('2–3 / 10'). 슬라이더는 그대로 **쪽**을 움직인다.
                    shown = if (twoUp) {
                        Spreads.pagesOf(Spreads.spreadOf(current, book.pageCount, true), book.pageCount, true)
                    } else {
                        listOf(current)
                    },
                    rightToLeft = direction == ComicViewModel.Direction.RTL,
                    onSeek = { vm.onPageChanged(it) },
                    onJump = { showJump = true },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))

        // 격자는 **맨 위 층**이다. 막대와 알림을 덮는다 — 고르는 동안 뒤의 막대를 누를 일이 없다.
        if (showGrid && book != null) {
            PageGrid(
                book = book,
                current = current,
                onPick = {
                    showGrid = false
                    vm.onPageChanged(it)
                },
                onClose = { showGrid = false },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReadingOptions(
                direction = direction,
                onDirection = { vm.setDirection(it) },
                layout = layout,
                onLayout = { vm.setLayout(it) },
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
 * ⋮ 메뉴 — 다음 권 열기, 다른 앱으로 열기. 둘 중 하나라도 있을 때만 선다(부르는 쪽이 가른다).
 *
 * **단추와 메뉴를 `Box` 하나에 담는다.** `actions` 에 형제로 두면 `Popup` 의 앵커가 아이콘 줄 전체가 되어 메뉴가
 * 맨 왼쪽 아이콘 아래에서 열린다(함정 표 — 사용자가 '꽤 동떨어진 자리' 로 지적했다).
 *
 * @param nextName 같은 폴더의 다음 권 이름. null 이면 그 줄이 없다.
 * @param onOpenWith 지금 책 파일을 다른 앱으로 연다. 책이 보통 파일이 아니면 null — 그 줄이 없다([ComicOpenWith.menuTarget]).
 */
@Composable
private fun MoreMenu(nextName: String?, onOpenNext: () -> Unit, onOpenWith: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.comic_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (nextName != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.comic_next_volume, nextName),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        open = false
                        onOpenNext()
                    },
                )
            }
            if (onOpenWith != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)) },
                    onClick = {
                        open = false
                        onOpenWith()
                    },
                )
            }
        }
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
 *
 * ## 두 쪽 보기(14단계)
 *
 * 페이저의 한 칸이 **펼침**이다([Spreads]). VM 은 여전히 **쪽**을 든다 — 페이저가 다른 펼침에 서면 그 펼침의 첫 쪽이
 * 새 쪽이 되고, 같은 펼침 안의 쪽이면 그대로 둔다([Spreads.pageAfterSettle]). 펼침 안의 두 쪽은 읽는 방향을 따라
 * 놓는다 — 오른쪽에서 왼쪽이면 앞 쪽이 오른쪽이다([Spreads.visualOrder]).
 */
@Composable
private fun PagedReader(
    book: ComicViewModel.Book,
    vm: ComicViewModel,
    rightToLeft: Boolean,
    twoUp: Boolean,
    viewportWidth: Int,
    viewportHeight: Int,
    onTap: () -> Unit,
    /** 못 연 쪽의 파일을 다른 앱으로 연다([FailedPage]). */
    onOpenWith: (String) -> Unit,
) {
    val current by vm.page.collectAsStateWithLifecycle()
    val pageCount = book.pageCount
    val spreadCount = Spreads.count(pageCount, twoUp)
    val pagerState = rememberPagerState(
        initialPage = Spreads.spreadOf(current, pageCount, twoUp).coerceIn(0, (spreadCount - 1).coerceAtLeast(0)),
        pageCount = { spreadCount },
    )

    // 페이저 → VM. 사용자가 민 결과다. 펼침 번호가 아니라 **쪽**으로 옮겨 적는다.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { spread ->
            vm.onPageChanged(Spreads.pageAfterSettle(vm.page.value, spread, pageCount, twoUp))
        }
    }
    // VM → 페이저. 슬라이더·쪽으로 가기·쪽 목록·'처음부터' 가 여기로 들어온다.
    LaunchedEffect(current) {
        val target = Spreads.spreadOf(current, pageCount, twoUp)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    // 화면에서 벗어난 쪽의 확대를 되돌린다. 6단계와 같은 이유다 — 되돌리지 않으면
    // 나중에 그 쪽으로 돌아왔을 때 전에 확대해 둔 자리에서 시작한다.
    val zooms = remember(book.path) { HashMap<Int, ZoomState>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            zooms.forEach { (spread, st) -> if (spread != settled) st.reset() }
            // 마지막 쪽이 든 펼침에 **멈췄으면** 끝에 닿은 것이다. 넘기는 도중(`currentPage`)으로 재지 않는다 —
            // 끝에서 한 장 앞으로 살짝 밀었다 놓아도 다음 권을 권하게 된다.
            if (spreadCount > 0 && settled == spreadCount - 1) vm.onReachedEnd()
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = rightToLeft,
        // 앞뒤 한 장씩. 넘기는 동안 두 장이 걸치므로 최악은 넷이고, `ImageLimits` 의
        // 예산이 정확히 그 넷을 기준으로 계산된다. 두 쪽 보기면 넷이 펼침 넷(반쪽 여덟)이고,
        // 반쪽 하나가 한 장 몫의 절반이라 합이 같다(`ComicPageStore.spreadPageCap`).
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { spread ->
        val pages = Spreads.pagesOf(spread, pageCount, twoUp)
        val zoom = zooms.getOrPut(spread) { ZoomState() }
        if (pages.size == 2) {
            val (left, right) = Spreads.visualOrder(pages, rightToLeft)
            SpreadView(
                store = book.store,
                left = left,
                right = right,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                zoomState = zoom,
                onTap = onTap,
                onOpenWith = onOpenWith,
            )
        } else if (pages.size == 1) {
            // 한 쪽 보기, 또는 두 쪽 보기의 표지·혼자 남은 마지막 쪽. 화면 전체를 쓴다.
            ComicPageView(
                store = book.store,
                ordinal = pages[0],
                targetLongest = maxOf(viewportWidth, viewportHeight),
                zoomState = zoom,
                onTap = onTap,
                onOpenWith = onOpenWith,
            )
        }
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
    onOpenWith: (String) -> Unit,
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

            is PageState.Failed -> FailedPage(store, ordinal, onOpenWith, Modifier.padding(32.dp))

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

/**
 * '이 쪽을 열 수 없습니다'. 폴더로 연 만화의 쪽이면 곁에 **그 쪽의 파일**을 다른 앱으로 여는 단추를 단다 — 그 쪽은 디스크 위의
 * 그림 파일이라 이 기기가 못 푸는 것(HEIC·AVIF·깨진 JPEG)을 다른 앱은 열 수 있다. 아카이브 안의 쪽에는 경로가 없어 달지
 * 않고, 파일이 그새 사라졌거나 열리지 않아도 달지 않는다([ComicOpenWith.pageFailureTarget]).
 *
 * 대상은 실패가 **선 뒤에** 한 번 찾는다(여는 탐침이라 입출력이다 — [ComicPageStore.failedPageTarget]). 찾는 동안에는 글만
 * 보이고 단추는 찾은 뒤에 선다.
 *
 * @param offerHandoff 단추를 달 자리인가. 세로 모드의 긴 쪽은 띠 여럿으로 갈리므로 첫 띠에만 단다.
 */
@Composable
internal fun FailedPage(
    store: ComicPageStore,
    ordinal: Int,
    onOpenWith: (String) -> Unit,
    modifier: Modifier = Modifier,
    offerHandoff: Boolean = true,
) {
    val target by produceState<String?>(null, store, ordinal, offerHandoff) {
        value = if (offerHandoff) store.failedPageTarget(ordinal) else null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = stringResource(R.string.comic_page_failed),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        target?.let { path ->
            Button(onClick = { onOpenWith(path) }, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with))
            }
        }
    }
}

/** 움직이지만 첫 장면만 보여 준다는 고지. 숨기면 앱이 고장난 것처럼 보인다. */
@Composable
internal fun ApngNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = stringResource(io.github.donggi.iroiroviewer.ui.R.string.ui_still_apng),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(bottom = 120.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

internal sealed interface PageState {
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
 *
 * 두 쪽 보기는 이 모드에 걸리지 않는다 — 웹툰은 폭 맞춤으로 읽는 것이다.
 */
@Composable
private fun VerticalReader(
    book: ComicViewModel.Book,
    vm: ComicViewModel,
    viewportWidth: Int,
    viewportHeight: Int,
    onTap: () -> Unit,
    onOpenWith: (String) -> Unit,
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
    // 끝에 닿았는가 — **마지막 항목이 보이고 더 내릴 곳이 없을 때.** 마지막 쪽이 짧으면 그것이 '지금 쪽'(보이는 첫
    // 항목)이 되는 일이 없으므로 쪽 번호로는 잴 수 없다. 첫 배치 전의 빈 값에 속지 않게 항목이 보일 때만 본다.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount > 0 && last != null && last.index == info.totalItemsCount - 1 &&
                !listState.canScrollForward
        }
            .distinctUntilChanged()
            .collect { atEnd -> if (atEnd) vm.onReachedEnd() }
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
                    onOpenWith = onOpenWith,
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
    onOpenWith: (String) -> Unit,
) {
    val piece by produceState<VPiece>(VPiece.Loading, store, item.page, item.band, viewportWidth) {
        val info = store.info(item.page)
        if (info == null) {
            value = VPiece.Failed
            return@produceState
        }
        onInfo(info)
        val image = if (store.bandCount(info, viewportWidth, viewportHeight) <= 1) {
            store.still(item.page, targetLongestFor(info.width, info.height, viewportWidth))
        } else {
            store.band(item.page, item.band, viewportWidth, viewportHeight)
        }
        value = if (image != null) VPiece.Shown(image) else VPiece.Failed
    }

    when (val p = piece) {
        VPiece.Loading -> CircularProgressIndicator(color = Color.White)
        // **못 연 것을 여는 중으로 보이지 않는다.** 예전에는 둘 다 null 이라 못 연 쪽에서 동그라미가 영원히 돌았다 — 이미지
        // 뷰어가 6단계에 세 상태(여는 중·열림·못 엶)로 가른 그 결함이다. 좌우 읽기와 같은 문구를 쓴다.
        VPiece.Failed -> FailedPage(store, item.page, onOpenWith, Modifier.padding(32.dp), offerHandoff = item.band == 0)
        is VPiece.Shown -> Image(
            bitmap = p.image,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 세로 모드 항목 하나의 상태 — 여는 중 / 못 엶 / 열림. */
private sealed interface VPiece {
    data object Loading : VPiece
    data object Failed : VPiece
    data class Shown(val image: androidx.compose.ui.graphics.ImageBitmap) : VPiece
}

@Composable
private fun BottomBar(
    page: Int,
    pageCount: Int,
    /** 지금 화면에 보이는 쪽들(두 쪽 보기면 둘). 글자로만 쓴다. */
    shown: List<Int>,
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
                text = if (shown.size == 2) {
                    stringResource(R.string.comic_page_range, shown[0] + 1, shown[1] + 1, pageCount)
                } else {
                    stringResource(R.string.comic_page_of, page + 1, pageCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            TextButton(onClick = onJump) { Text(stringResource(R.string.comic_jump)) }
        }
    }
}

/** 읽기 설정 — 읽는 방향과 한 화면에 놓는 쪽 수. */
@Composable
private fun ReadingOptions(
    direction: ComicViewModel.Direction,
    onDirection: (ComicViewModel.Direction) -> Unit,
    layout: PageLayout,
    onLayout: (PageLayout) -> Unit,
    solid: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.comic_direction), style = MaterialTheme.typography.titleMedium)
        val directions = listOf(
            ComicViewModel.Direction.LTR to R.string.comic_direction_ltr,
            ComicViewModel.Direction.RTL to R.string.comic_direction_rtl,
            ComicViewModel.Direction.VERTICAL to R.string.comic_direction_vertical,
        )
        for ((value, label) in directions) {
            OptionRow(selected = direction == value, enabled = true, label = label) { onDirection(value) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 세로 스크롤에서는 두 쪽 보기가 걸리지 않는다. 고른 값은 남겨 두고 고를 수만 없게 한다 — 좌우 넘기기로 돌아오면
        // 그 값이 다시 산다.
        val paged = direction != ComicViewModel.Direction.VERTICAL
        Text(stringResource(R.string.comic_layout), style = MaterialTheme.typography.titleMedium)
        val layouts = listOf(
            PageLayout.SINGLE to R.string.comic_layout_single,
            PageLayout.DOUBLE to R.string.comic_layout_double,
            PageLayout.AUTO to R.string.comic_layout_auto,
        )
        for ((value, label) in layouts) {
            OptionRow(selected = layout == value, enabled = paged, label = label) { onLayout(value) }
        }
        Text(
            text = stringResource(if (paged) R.string.comic_layout_cover_hint else R.string.comic_layout_vertical_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

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
private fun OptionRow(selected: Boolean, enabled: Boolean, label: Int, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pointerInput(label) { detectTapGestures(onTap = { onPick() }) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = if (enabled) onPick else null, enabled = enabled)
        Text(
            text = stringResource(label),
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
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
