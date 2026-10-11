package io.github.donggi.iroiroviewer.docview

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import io.github.donggi.iroiroviewer.ui.IroSlider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch
import io.github.donggi.iroiroviewer.docview.pdf.PdfSpreads
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.safety.PdfLimits
import io.github.donggi.iroiroviewer.ui.PasswordDialog
import io.github.donggi.iroiroviewer.ui.SystemBarsHidden
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import io.github.donggi.iroiroviewer.ui.image.DetailLayer
import io.github.donggi.iroiroviewer.ui.image.ZoomableImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 문서 한 편을 읽는 화면 — PDF(쪽)·EPUB(장). 아래 구조 설명은 PDF 의 것이고, EPUB 은 WebView 를 칸으로 나눠 띄운다(아래
 * `Doc.Epub` 가지의 주석). 오피스·한글 문서(docx·xlsx·pptx·HWPX·HWP)는 이 화면이 아니라 편집기의 보기 모드가 연다 —
 * 쪽을 PC 처럼 그린다(`app` 이 가른다).
 *
 * ## PDF 의 보기는 둘이다
 *
 * **기본은 세로로 이어 보기**([ScrollDocument]) — 쪽을 화면 폭에 맞춰 이어 붙이고 배율이 문서 전체에 걸린다.
 * 예전의 **한 쪽씩 넘기기**([PagedDocument])는 위쪽 막대 ⋮ 의 보기 방식으로 남고, 고른
 * 것은 설정에 남는다(`AppPreferences.pdfContinuous`). 두 보기가 VM 의 같은 **쪽**을 세므로 쪽 번호·슬라이더·이어보기·쪽 목록·쪽으로
 * 가기·찾기가 한 벌이다.
 *
 * ## 한 쪽씩 넘기기의 구조는 만화의 `PagedReader` 와 같다
 *
 * `HorizontalPager(beyondViewportPageCount = 1, key = { it })`, 쪽마다 [ZoomState] 를
 * 두고 정착하지 않은 쪽의 확대를 되돌린다, **바깥 `Scaffold` 를 씌우지 않는다**.
 * 마지막 것이 6·9단계가 같은 이유로 정한 규칙이다 — 막대를 감추면 시스템 바도 함께 숨는데
 * (`SystemBarsHidden` — 거는 것은 액티비티다) `Scaffold` 의 기본 인셋은 바가 숨고 나타나는 애니메이션 내내 프레임마다
 * 변하고, 그 패딩을 받으면 확대·이동의 기준 상자가 함께 움직여 경계 계산이 어긋난다. 겹치는 막대는 바가 숨어도 변하지
 * 않는 인셋(`…IgnoringVisibility`)을 쓴다. 확대한 채 가장자리에 닿은 뒤 새로 밀면 다음 쪽이다(`ZoomState.claimsDrag`).
 *
 * ## 만화와 다른 것 셋
 *
 * | | 만화 뷰어(9단계) | 문서 뷰어 |
 * |---|---|---|
 * | 쪽의 출처 | 아카이브 안의 **바이트** | 파일 하나 안의 **쪽 번호**(무작위 접근이 싸다) |
 * | 방향 | 왼→오 · 오→왼 · 세로 | **왼→오 하나뿐**(PDF 에 읽는 방향이 없다) |
 * | 확대 | 흐려진 채로 둔다 | **그 자리를 다시 그린다**([DetailLayer]) |
 *
 * 마지막 줄이 PDF 가 벡터라서 얻는 이득이다. 만화 쪽은 원본이 래스터라 확대하면 원본
 * 화소를 다시 읽을 수밖에 없지만(`BitmapRegionDecoder`), PDF 는 **더 큰 배율로 다시
 * 그리면** 된다 — 원본이라는 것이 애초에 없다.
 *
 * ## 배경은 어두운 회색이고 쪽은 희다
 *
 * 만화 뷰어는 검은 배경에 그림을 얹지만 문서는 **흰 종이**다. 검정 위의 흰 종이는 대비가
 * 너무 세고, 어두운 모드라고 쪽을 반전하면 그림과 표의 색이 깨진다. 배경만 회색으로 낮춘다.
 *
 * ## 14단계가 더한 것
 *
 * * **읽는 모양**('보기' 판 — [AppearanceSheet]). EPUB 에만 있고, 설정 화면과 같은 값을 쓴다.
 * * **장 안의 자리**. 스크롤의 비율로 기억하고 되살린다([DocLocator]·`LockedWebView`). 장 번호의 이어보기 안내는 그대로다.
 * * **PDF 두 쪽 보기**(가로 화면에서만, `PdfSpreads`)와 **쪽 목록**([PdfGridSheet]).
 * * **PDF 찾기**(안드로이드 15 이상에서만 — `PdfSearch`). 그 아래 기기에는 단추가 없다.
 *
 * ## 장을 손가락으로 넘긴다
 *
 * EPUB 은 아래 막대의 화살표 말고도 넘긴다 — 흐르는 글은 장의 끝에서 한 번 더 당겨(처음에서 거꾸로 당기면 앞 장의 끝으로),
 * 고정 레이아웃 쪽은 좌우로 밀거나 가장자리를 두드려(`ReadingTurn`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocViewScreen(
    path: String,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * ⋮ 의 '편집' — 편집기(`feature:editor`)로 넘긴다. 인자는 편집기의 형식 글자(지금은 PDF — 편집기가 고칠 수 있는
     * 문서로 바꿔 열고, 저장하면 원본 곁에 새 파일). 읽기 전용 사본이면 null — 줄이 없다(`DocEdit.kt`).
     */
    onEditor: ((String) -> Unit)? = null,
    /** 바뀌면 문서를 다시 연다 — 편집기에서 돌아와 같은 경로를 새로 읽을 때(`app` 이 올린다). */
    reopen: Int = 0,
) {
    val vm: DocViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    // PDF 글 고르기의 알림(복사했다·고를 글이 없다)을 띄운다.
    val messageScope = rememberCoroutineScope()

    LaunchedEffect(path, reopen) { vm.open(path) }

    // '다른 앱으로 열기'. 막대의 ⋮ 에는 파일에 닿은 뒤로 언제나(여는 중·암호를 묻는 중 포함 — 읽지 못한 실패는 빼고), 실패
    // 화면에는 이 앱이 보여 줄 수 없는 갈래에만 둔다([DocOpenWith]). 여는 것은 언제나 화면이 받은 경로다 — 실패했으면 문서
    // (`Doc`)가 없다.
    val openWith = rememberOpenWith(path, snackbar)
    val menuOpenWith = if (DocOpenWith.inMenu(state)) openWith else null
    // '편집' — 막대가 있는 모든 상태에서(열기 전·실패여도 파일의 종류로 정한다 — 편집기가 다루는 형식이면).
    val edit = rememberEditAction(path, closeViewer = { vm.close() }, onEditor = onEditor)
    // 편집기가 이 형식을 다루는가 — 이 뷰어가 열지 못한 문서의 실패 화면에 '편집기로 열기' 를 둔다.
    val editorKind = rememberEditorKind(path, onEditor)

    var chromeVisible by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    // 문서가 바뀌면 닫는다 — 앞 문서의 찾기 막대·쪽 목록이 새 문서 위에 남지 않게.
    var showGrid by remember(path) { mutableStateOf(false) }
    // 찾기 막대는 **VM 의 찾기가 살아 있으면 열린 채로** 선다. 액티비티가 다시 만들어지면(밤 모드를 바꿨다 — 회전은
    // `configChanges` 라 다시 만들지 않는다) 결과의 칠은 VM 에 남는데 막대만 사라져, 칠을 걷을 길도 뒤로가 막대를 닫는
    // 처리기도 없어졌다.
    var searching by remember(path) { mutableStateOf(vm.isSearching(path)) }
    var viewport by remember { mutableStateOf(0 to 0) }
    // 링크·목차가 가리킨 자리(EPUB 의 `#note3`). **여기 둔다** — '처음부터' 는 위의
    // 사건 수집에서 오므로, 가지 안에 두면 비울 손이 닿지 않아 옛 자리로 떨어졌다.
    var linkAnchor by remember(path) { mutableStateOf<String?>(null) }

    BackHandler {
        vm.close()
        onClose()
    }
    // **찾기 막대가 열려 있으면 뒤로가 그것부터 닫는다.** 나중에 둔 처리기가 먼저 받는다.
    BackHandler(enabled = searching) {
        searching = false
        vm.clearSearch()
    }

    val resumedFormat = stringResource(R.string.doc_resumed)
    val resumedChapterFormat = stringResource(R.string.doc_resumed_chapter)
    val restartLabel = stringResource(R.string.doc_restart)
    val resumedHere = stringResource(R.string.doc_resumed_here)
    val fixedLayoutNotice = stringResource(R.string.doc_fixed_layout_notice)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DocViewModel.Event.Resumed -> {
                    val result = snackbar.showSnackbar(
                        // 첫 장 **안에서** 잇는 것이면 '1장부터' 가 아니라 '읽던 자리부터' 다.
                        message = if (event.inPlace) resumedHere else String.format(
                            when (event.unit) {
                                DocViewModel.ResumeUnit.PAGE -> resumedFormat
                                DocViewModel.ResumeUnit.CHAPTER -> resumedChapterFormat
                            },
                            event.page + 1,
                        ),
                        actionLabel = restartLabel,
                        // **기간을 손으로 적는다.** 생략하면 Material 3 이
                        // `actionLabel != null` 일 때 `Indefinite` 를 넣어, 누르거나
                        // 밀기 전에는 사라지지 않는다(함정 표).
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        linkAnchor = null
                        vm.restart()
                    }
                }

                is DocViewModel.Event.FixedLayoutEntry -> snackbar.showSnackbar(
                    message = fixedLayoutNotice,
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    val doc = (state as? DocViewModel.State.Ready)?.doc
    // PDF 의 막대를 감추면 시스템 바도 감춘다(거는 것은 액티비티다). EPUB 은 막대가 늘 보인다 — 칸으로 나눈 화면이다.
    SystemBarsHidden(hidden = doc is DocViewModel.Doc.Pdf && !DocOpenWith.barVisible(chromeVisible, state))
    val current by vm.page.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val jump by vm.jump.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()

    // **EPUB 은 겹치지 않고 칸으로 나눈다.** 나머지 뷰어는 조작부를 그림 위에 겹치지만
    // (10단계가 '조작부가 나타날 때마다 영상이 줄었다' 로 고친 그 판단), 글을 읽는
    // 화면에서는 반대다 — 겹치면 **글자가 막대 밑으로 들어가 읽히지 않는다.**
    // 그리고 WebView 는 탭을 스스로 쓰므로(글자 선택·링크) 두드림으로 조작부를 여닫는
    // 길이 없다. 칸으로 나누고 늘 보이게 두는 편이 정직하다.
    if (doc is DocViewModel.Doc.Epub) {
        val controls = ReaderLooks.controlsForEpub(doc.book.allFixedLayout)
        // 막대 뒤의 바탕도 종이 색으로 — 반투명 막대가 어두운 바탕 위에서 흰 띠로 뜨지 않게.
        val paper = appearance?.let {
            ReaderLooks.forEpub(doc.book.allFixedLayout, it, systemDark, 0.0, 0.0).background
        } ?: WHITE_PAPER
        // **알림은 아래 막대 위에 뜬다.** 칸으로 나눈 화면은 `Column` 이라 `SnackbarHost` 를 그 옆에
        // 두면 부모가 정렬을 모르고 **맨 위**에 그려져 제목 막대를 가렸다(기기에서
        // 확인했다). 상자로 감싸 아래에 붙인다.
        Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(paper))) {
            DocTopBar(
                title = doc.name,
                onClose = { vm.close(); onClose() },
                // **버릴 것이 있을 때만 단추가 뜬다.** 늘 띄우면 '무언가 잘못됐다' 는
                // 인상만 남기고, 실제로 누를 이유가 있는 순간과 구별되지 않는다.
                notice = !notice.isEmpty,
                onNotice = { showNotice = true },
                // 고칠 것이 없으면(모든 쪽이 고정 레이아웃) '보기' 단추를 띄우지 않는다.
                onAppearance = if (controls.isEmpty()) null else ({ showAppearance = true }),
                onOpenWith = menuOpenWith,
                onEdit = edit,
                modifier = Modifier.fillMaxWidth(),
            )
            val look = appearance
            if (look == null) {
                // 읽는 모양을 아직 읽지 못했다(설정 파일은 곧 읽힌다). 기본 모양으로 한 번 그리고 다시 그리지 않는다.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                EpubReader(
                    doc = doc,
                    chapter = current,
                    anchor = linkAnchor,
                    onChapter = { chapter, anchor ->
                        linkAnchor = anchor
                        vm.onPageChanged(chapter)
                    },
                    onRead = { vm.noteRead() },
                    appearance = look,
                    systemDark = systemDark,
                    startFraction = vm::fractionNow,
                    jump = jump,
                    onFraction = vm::onFraction,
                    onSeen = vm::onSeen,
                    onTurn = { target, fromEnd ->
                        // 손가락으로 넘겼다. 앞 장으로 당겨 갔으면 그 장의 끝에서 연다(읽던 흐름이 이어지게).
                        linkAnchor = null
                        if (fromEnd) vm.openAtEnd(target) else vm.onPageChanged(target)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            EpubBottomBar(
                chapter = current,
                count = doc.count,
                onChapter = {
                    linkAnchor = null
                    vm.onPageChanged(it)
                },
                onToc = { showToc = true },
                // 모든 장이 고정 레이아웃인 책(그림책·만화)은 장 하나가 쪽 하나다 — '쪽' 으로 센다.
                fixedPages = doc.book.allFixedLayout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BOTTOM_BAR_CLEARANCE),
        )
        }
        TocSheet(state, showToc, { showToc = false }, vm) { linkAnchor = null }
        NoticeSheet(notice, showNotice) { showNotice = false }
        val look = appearance
        if (showAppearance && look != null) {
            AppearanceSheet(look, controls, vm::setAppearance) { showAppearance = false }
        }
        return
    }

    val spreadWanted by vm.spread.collectAsStateWithLifecycle()
    val continuous by vm.continuous.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val landscape = viewport.first > viewport.second
    val pdfSpread = spreadWanted && landscape
    Box(
        modifier
            .fillMaxSize()
            .background(PAPER_BACKDROP)
            .onSizeChanged {
                if (it.width > 0 && it.height > 0) {
                    viewport = it.width to it.height
                    vm.onViewport(it.width, it.height)
                }
            },
    ) {
        when (val s = state) {
            is DocViewModel.State.Loading -> Centered {
                CircularProgressIndicator(color = Color.White)
            }

            is DocViewModel.State.Failed -> {
                val locked = s.failure as? OpenFailure.PasswordRequired
                // **실패할 때마다 다시 묻는다.** 넣은 뒤에는 `Loading` 을 지나므로 이 가지가
                // 컴포지션에서 빠졌다 들어오고, 그때 값이 처음부터 다시 선다. 취소를 누른
                // 뒤에는 닫힌 채로 두되, 회전해도 다시 튀어나오지 않게 저장해 둔다
                // (저장하는 것은 '물을 것인가' 하나다 — 암호가 아니다).
                var asking by rememberSaveable(s.failure) { mutableStateOf(locked != null) }
                Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            // 파일에 닿지 못했으면 종류(`Io`)의 문장이 아니라 '읽을 수 없다' 다(`State.Failed.unreachable`).
                            text = stringResource(
                                if (s.unreachable) R.string.doc_failed_unreadable else failureText(s.failure),
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (locked != null) {
                            // 채운 단추로 둔다. 글자 단추(파랑)는 어두운 회색 바탕에서 흐려
                            // 보인다(에뮬레이터 캡처로 확인했다) — 이 화면에서 누를 것은 이것 하나다.
                            Button(
                                onClick = { asking = true },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.doc_password_enter))
                            }
                        }
                        // **이 앱이 보여 줄 수 없는 갈래에만** — 다루지 않는 형식·풀 수 없는 잠금·깨짐·상한.
                        // 암호(위 단추)와 파일에 닿지 못한 실패에는 없다([DocOpenWith.onFailure]). 암호 단추와 함께 뜨는
                        // 갈래는 없다. 채운 단추로 둔다(글자 단추는 어두운 바탕에서 흐리다). 암호 화면에서 다른 앱으로 넘기는
                        // 길은 막대의 ⋮ 다(암호 창을 닫으면 닿는다).
                        if (DocOpenWith.onFailure(s)) {
                            // '편집기로 열기'(⋮ 의 '편집' 과 같다)는 편집기라면 그 파일을 볼 수 있는 갈래에만 — PDF 는 깨진 파일뿐이고 그때도
                            // '다른 앱으로 열기' 가 첫 단추다. 오피스·한글은 편집기가 먼저다([DocOpenWith.editorOnFailure]·[DocOpenWith.editorFirst]).
                            val toEditor = editorKind?.takeIf { DocOpenWith.editorOnFailure(s.failure, it) }?.let { edit }
                            val editorFirst = toEditor != null && editorKind != null && DocOpenWith.editorFirst(editorKind)
                            val openWithLabel = stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)
                            val editorLabel = stringResource(R.string.doc_open_in_editor)
                            // 첫 단추는 채운 단추, 둘째는 옅은 단추다.
                            val first = if (toEditor != null && editorFirst) editorLabel to toEditor else openWithLabel to openWith
                            val second = when {
                                toEditor == null -> null
                                editorFirst -> openWithLabel to openWith
                                else -> editorLabel to toEditor
                            }
                            Button(
                                onClick = first.second,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(first.first)
                            }
                            if (second != null) {
                                FilledTonalButton(
                                    onClick = second.second,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text(second.first)
                                }
                            }
                        }
                    }
                }
                if (locked != null && asking) {
                    PasswordDialog(
                        title = stringResource(R.string.doc_password_title),
                        message = stringResource(R.string.doc_password_body),
                        wrong = locked.wrongPassword,
                        onDismiss = { asking = false },
                        onSubmit = { vm.submitPassword(it) },
                    )
                }
            }

            is DocViewModel.State.Ready -> {
                val pdf = s.doc as? DocViewModel.Doc.Pdf
                if (pdf != null && viewport.first > 0) {
                    // 새 모양(한 쪽 ↔ 두 쪽, 이어 보기 ↔ 넘기기)이면 새로 세운다 — 칸의 수와 뜻이 바뀐다. 보던 쪽은 VM 이 든다.
                    key(pdfSpread, continuous) {
                        if (continuous) {
                            ScrollDocument(
                                doc = pdf,
                                vm = vm,
                                spread = pdfSpread,
                                search = search,
                                viewportWidth = viewport.first,
                                viewportHeight = viewport.second,
                                onTap = { chromeVisible = !chromeVisible },
                                onMessage = { msg -> messageScope.launch { snackbar.showSnackbar(msg) } },
                            )
                        } else {
                            PagedDocument(
                                doc = pdf,
                                vm = vm,
                                spread = pdfSpread,
                                search = search,
                                viewportWidth = viewport.first,
                                viewportHeight = viewport.second,
                                onTap = { chromeVisible = !chromeVisible },
                                onMessage = { msg -> messageScope.launch { snackbar.showSnackbar(msg) } },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            // 여는 중·실패 화면에서는 두드림으로 숨긴 값이 남아 있어도 그린다 — 되살릴 두드림이 없다([DocOpenWith.barVisible]).
            visible = DocOpenWith.barVisible(chromeVisible, state),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (searching && doc is DocViewModel.Doc.Pdf) {
                PdfSearchBar(
                    state = search,
                    onSearch = { vm.search(it) },
                    onPrevious = { vm.previousMatch() },
                    onNext = { vm.nextMatch() },
                    onClose = {
                        searching = false
                        vm.clearSearch()
                    },
                )
            } else {
                // **PDF 에는 알림 단추가 없다.** pdfium 이 무엇을 못 그렸는지 알려 주지
                // 않으므로 셀 것이 없다(`PdfDocument` 의 주석). 0 을 보여 주면 그 0 이
                // 거짓말을 한다.
                DocTopBar(
                    // 열린 PDF 는 그 이름, 여는 중·실패 화면은 화면이 받은 경로의 파일 이름이다([DocTitle]).
                    title = DocTitle.of(state, path),
                    onClose = { vm.close(); onClose() },
                    // 찾기는 안드로이드 15 이상에서만 — 그 아래에서는 단추가 없다(`PdfSearch` 의 주석).
                    onSearch = if (doc is DocViewModel.Doc.Pdf && vm.canSearchPdf) ({ searching = true }) else null,
                    onOpenWith = menuOpenWith,
                    onEdit = edit,
                    // 보기 방식 — 지금 모양이 아니라 **누르면 될 모양**을 적는다(두 쪽 단추와 같다).
                    viewMode = if (doc is DocViewModel.Doc.Pdf) {
                        if (continuous) R.string.doc_view_paged else R.string.doc_view_continuous
                    } else {
                        null
                    },
                    onViewMode = { vm.setContinuous(!continuous) },
                )
            }
        }

        if (doc is DocViewModel.Doc.Pdf) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    page = current,
                    pageCount = doc.count,
                    spread = pdfSpread,
                    // 두 쪽 보기는 가로 화면에서만 뜻이 있다. 세로로 돌리면 단추도 사라지고 한 쪽으로 보인다.
                    canSpread = landscape && doc.count > 1,
                    onSeek = { vm.onPageChanged(it) },
                    onJump = { showJump = true },
                    onGrid = { showGrid = true },
                    onSpread = { vm.setSpread(!spreadWanted) },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    val pdfDoc = doc as? DocViewModel.Doc.Pdf
    if (showGrid && pdfDoc != null) {
        PdfGridSheet(
            store = pdfDoc.store,
            current = current,
            onPick = {
                showGrid = false
                vm.onPageChanged(it)
            },
            onDismiss = { showGrid = false },
        )
    }

    if (showJump) {
        val count = (state as? DocViewModel.State.Ready)?.doc?.count ?: 0
        JumpDialog(
            pageCount = count,
            onDismiss = { showJump = false },
            onGo = {
                showJump = false
                vm.onPageChanged(it)
            },
        )
    }
}

/**
 * 위쪽 막대. 두 갈래(PDF 겹치기 · EPUB 칸)가 같은 것을 쓴다.
 *
 * **색을 반투명 검정으로 고정한다.** PDF 는 회색 바탕 위에, EPUB 은 흰 종이 위에 놓이는데
 * 둘 다 이 색에서 글자가 읽힌다. 테마를 따르게 하면 다이나믹 배색에서 `surface` 와
 * 배경이 같은 색이 되어 막대가 보이지 않는 판이 생긴다(10단계가 겪었다).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DocTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    notice: Boolean = false,
    onNotice: () -> Unit = {},
    /** '보기'(읽는 모양). null 이면 단추가 없다. */
    onAppearance: (() -> Unit)? = null,
    /** 찾기(PDF, API 35 이상). null 이면 단추가 없다. */
    onSearch: (() -> Unit)? = null,
    /** '다른 앱으로 열기'(⋮ 안). null 이고 [viewMode]·[onEdit] 도 null 이면 ⋮ 도 없다. */
    onOpenWith: (() -> Unit)? = null,
    /** PDF 의 보기 방식(⋮ 안). 누르면 될 모양의 이름이고, null 이면 그 줄이 없다. */
    @StringRes viewMode: Int? = null,
    onViewMode: () -> Unit = {},
    /** '편집'(⋮ 의 첫 줄 — 편집기로 넘긴다). null 이면 그 줄이 없다(`DocEdit.kt`). */
    onEdit: (() -> Unit)? = null,
) {
    TopAppBar(
        modifier = modifier,
        // 시스템 바가 숨어도 변하지 않는 인셋 — PDF 의 막대가 사라지는 동안 바가 함께 숨어 제목이 위로 미끄러지지 않게.
        windowInsets = WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.doc_close),
                )
            }
        },
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, stringResource(R.string.doc_search))
                }
            }
            if (onAppearance != null) {
                // 글자 단추로 둔다 — 아이콘 코어 세트에 '글자 모양' 이 없고(함정 표의 49개), 확장 세트는 들이지 않는다.
                TextButton(onClick = onAppearance) {
                    Text(stringResource(R.string.doc_appearance), color = Color.White)
                }
            }
            if (notice) {
                IconButton(onClick = onNotice) {
                    Icon(Icons.Filled.Info, stringResource(R.string.doc_notice))
                }
            }
            if (onOpenWith != null || viewMode != null || onEdit != null) {
                // **⋮ 단추와 메뉴를 한 상자에 담는다.** Compose 의 Popup 은 자기를 감싼 부모 레이아웃 노드를 앵커로 삼아,
                // `actions` 에 형제로 두면 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(함정 표). 텍스트·압축 화면과 같은 모양이다.
                // 항목이 하나뿐인데도 메뉴로 두는 것은 코어 아이콘 세트(49개)에 이 뜻의 그림이 없고(`ExitToApp` 은 '나가기' 로
                // 읽힌다) 막대에 글자 단추를 하나 더 세우면 제목이 좁아져서다.
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.doc_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.doc_edit)) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                },
                            )
                        }
                        if (viewMode != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(viewMode)) },
                                onClick = {
                                    menuOpen = false
                                    onViewMode()
                                },
                            )
                        }
                        if (onOpenWith != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenWith()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 버린 것 알림. EPUB 에만 있다(위 주석). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheet(
    notice: DocViewModel.Notice,
    shown: Boolean,
    onDismiss: () -> Unit,
) {
    if (!shown || notice.isEmpty) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DocNoticeList(notice.warnings, notice.dropped)
    }
}

/** 목차. EPUB 에만 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    state: DocViewModel.State,
    shown: Boolean,
    onDismiss: () -> Unit,
    vm: DocViewModel,
    /** 장을 골랐다. 링크가 남긴 자리를 비운다 — 안 비우면 고른 장의 처음이 아니라 옛 각주 자리로 간다. */
    onPicked: () -> Unit,
) {
    val epub = (state as? DocViewModel.State.Ready)?.doc as? DocViewModel.Doc.Epub
    if (!shown || epub == null) return
    val current by vm.page.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        EpubTocList(
            book = epub.book,
            current = current,
            onPick = {
                onDismiss()
                onPicked()
                // 고른 장의 **처음**이다 — 지금 장을 골라도 그 장의 처음으로 간다.
                vm.startAt(it)
            },
        )
    }
}

/**
 * 좌우로 넘기는 읽기.
 *
 * **`reverseLayout` 이 없다.** PDF 에는 읽는 방향이 적혀 있지 않고, 만화처럼 사람이
 * 고르게 하면 세로쓰기 문서 몇 편을 위해 설정이 하나 더 는다. 필요해지면 그때 만화와
 * 같은 장치를 가져온다 — 그쪽은 책 단위로 방향을 저장하는 구조가 이미 있다.
 *
 * **두 쪽 보기면 칸 하나가 펼침 하나다**([PdfSpreads]). VM 이 세는 것은 여전히 **쪽**이다 — 펼침은 화면의 모양일 뿐이라
 * 기록·슬라이더·쪽으로 가기·찾기가 한 벌로 남는다. 칸이 바뀌어도 지금 쪽이 그 펼침 안에 있으면 쪽을 바꾸지 않는다 — 그래야
 * 한 쪽 보기로 돌아왔을 때 보던 쪽 그대로다.
 */
@Composable
private fun PagedDocument(
    doc: DocViewModel.Doc.Pdf,
    vm: DocViewModel,
    spread: Boolean,
    search: PdfSearch.State,
    viewportWidth: Int,
    viewportHeight: Int,
    onTap: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val current by vm.page.collectAsStateWithLifecycle()
    // 고른 글. 쪽 하나에 매인다 — 칸이 바뀌면 푼다(아래). 뒤로 가기는 고른 것부터 푼다.
    val selection = remember(doc.path) { PdfSelection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    val slots = if (spread) PdfSpreads.count(doc.count) else doc.count
    fun slotOf(page: Int): Int =
        if (spread) PdfSpreads.spreadOf(page, doc.count) else page.coerceIn(0, (doc.count - 1).coerceAtLeast(0))
    fun viewOf(slot: Int): PdfSpreads.View =
        if (spread) PdfSpreads.View(PdfSpreads.pages(slot, doc.count)) else PdfSpreads.View.single(slot)
    val pagerState = rememberPagerState(
        initialPage = slotOf(current),
        pageCount = { slots },
    )
    val latestCurrent by rememberUpdatedState(current)

    // 페이저 → VM. 사용자가 민 결과다.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { slot ->
            val pages = viewOf(slot).pages
            if (latestCurrent !in pages) vm.onPageChanged(pages.first())
        }
    }
    // VM → 페이저. 슬라이더·쪽으로 가기·'처음부터'·찾기가 여기로 들어온다.
    LaunchedEffect(current) {
        val target = slotOf(current)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    // 화면에서 벗어난 쪽의 확대를 되돌린다. 되돌리지 않으면 나중에 그 쪽으로 돌아왔을 때
    // 전에 확대해 둔 자리에서 시작하고, **그 쪽의 상세 타일도 그대로 살아 있다** —
    // 보이지도 않는 타일이 예산을 먹는다.
    val zooms = remember(doc.path) { HashMap<Int, ZoomState>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            zooms.forEach { (ordinal, st) -> if (ordinal != settled) st.reset() }
            if (selection.page >= 0 && selection.page !in viewOf(settled).pages) selection.clear()
        }
    }

    HorizontalPager(
        state = pagerState,
        // 앞뒤 한 장씩. 만화와 같은 값이고 `ImageLimits` 의 예산이 그 넷을 기준으로 잰다.
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { slot ->
        val view = viewOf(slot)
        DocPageView(
            store = doc.store,
            view = view,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            zoomState = zooms.getOrPut(slot) { ZoomState() },
            onTap = onTap,
            highlights = search.on(view.pages),
            currentMatch = search.currentMatch,
            selection = selection,
            onMessage = onMessage,
            // 글을 꺼내는 사이 쪽을 넘겼으면 그 쪽의 선택을 세우지 않는다(보이지 않는 선택이 뒤로 가기를 먹었다).
            isCurrent = { pagerState.settledPage == slot },
        )
    }
}

/**
 * 쪽 하나. 층이 둘이다 — 뷰포트에 맞춘 **흐린 바닥**과, 확대한 자리를 다시 그린 **타일**.
 *
 * 타일이 도착하기 전에도 바닥이 보인다. 그래서 확대하면 먼저 흐려지고 잠시 뒤 선명해진다 —
 * 비어 있는 흰 자리를 보여 주는 것보다 낫다.
 */
@Composable
private fun DocPageView(
    store: PdfPageStore,
    view: PdfSpreads.View,
    viewportWidth: Int,
    viewportHeight: Int,
    zoomState: ZoomState,
    onTap: () -> Unit,
    /** 이 칸의 쪽들 위의 찾기 결과. */
    highlights: List<PdfSearch.Match> = emptyList(),
    /** 지금 보고 있는 결과. 더 짙게 칠한다. */
    currentMatch: PdfSearch.Match? = null,
    /** 고른 글(문서 전체에 하나 — 이 칸의 쪽에 있을 때만 그린다). */
    selection: PdfSelection? = null,
    onMessage: (String) -> Unit = {},
    /** 이 칸이 지금 자리 잡은 칸인가. */
    isCurrent: () -> Boolean = { true },
) {
    // ---- 글 고르기 ----
    val selectScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val unavailable = stringResource(R.string.doc_text_unavailable)
    val noText = stringResource(R.string.doc_text_none)
    val fingerPx = with(LocalDensity.current) { FINGER_SLOP.toPx() }

    /**
     * 길게 누른 자리의 낱말을 고른다. 쪽의 글은 그때 처음 꺼낸다(쪽마다 수십~수백 ms — 입출력 스레드에서). 이 문서에서 글을
     * 고를 수 없거나 그 쪽에 글이 하나도 없으면 말한다. 글이 있는 쪽의 빈 자리는 조용히 넘어간다.
     */
    fun selectWordAt(p: Offset, image: ImageBitmap, layout: PdfSpreads.Layout, zoom: ZoomState, sel: PdfSelection) {
        if (!store.canSelectText) {
            onMessage(unavailable)
            return
        }
        val frac = PdfSelectionMath.toFraction(p.x, p.y, image.width, image.height, zoom.viewport, zoom.scale, zoom.offset)
            ?: return
        val (page, local) = PdfSelectionMath.fractionToPage(layout, frac.x, frac.y) ?: return
        if (local.x !in -0.02f..1.02f || local.y !in -0.02f..1.02f) return
        val (fw, _) = ZoomMath.fittedSize(image.width, image.height, zoom.viewport.width, zoom.viewport.height)
        // 손가락의 반지름을 쪽 포인트로 — 화면 화소 하나가 쪽의 몇 포인트인가.
        val ptPerPx = if (fw > 0f && zoom.scale > 0f) layout.width / (fw * zoom.scale) else 1f
        selectScope.launch {
            // 이어 보기와 같은 길로 고른다(`pickWord`).
            when (pickWord(store, sel, page, local, fingerPx * ptPerPx, isCurrent)) {
                WordPick.SELECTED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                WordPick.UNAVAILABLE -> onMessage(unavailable)
                WordPick.NO_TEXT -> onMessage(noText)
                WordPick.MISSED -> Unit
            }
        }
    }

    val fit by produceState<Fit>(Fit.Loading, store, view, viewportWidth, viewportHeight) {
        val image = store.fitted(view, viewportWidth, viewportHeight)
        value = if (image == null) Fit.Failed else Fit.Ready(image)
    }

    var detail by remember(store, view) { mutableStateOf<DetailLayer?>(null) }
    // 구성(Configuration)에서 읽는다 — `LocalContext.current.resources` 는 구성이 바뀌어도
    // 다시 읽히지 않는다(lint 의 LocalContextResourcesRead).
    val densityDpi = LocalConfiguration.current.densityDpi
    // 쪽 목록이 열린 동안은 조각을 쥐지 않는다 — 격자가 그 예산을 빌려 쓴다(`PdfPageStore.gridOpen`). 닫히면 효과가 다시
    // 돌아 지금 확대에 맞는 조각을 새로 청한다.
    val gridOpen by store.gridOpen.collectAsStateWithLifecycle()

    LaunchedEffect(store, view, viewportWidth, viewportHeight, fit, zoomState, gridOpen) {
        if (gridOpen) {
            detail = null
            return@LaunchedEffect
        }
        if (fit !is Fit.Ready) return@LaunchedEffect
        // 쪽 크기는 맞춤 층을 뜨면서 이미 쟀다. 여기서 문서를 다시 만지지 않는다.
        val layout = store.knownLayout(view) ?: return@LaunchedEffect
        val fitScale = PdfLimits.fitScale(layout.width, layout.height, viewportWidth, viewportHeight)

        snapshotFlow {
            DocDetail.want(
                fitScale = fitScale,
                zoom = zoomState.scale,
                viewportWidth = zoomState.viewport.width,
                viewportHeight = zoomState.viewport.height,
                fittedWidth = zoomState.fitted.width,
                fittedHeight = zoomState.fitted.height,
                offsetX = zoomState.offset.x,
                offsetY = zoomState.offset.y,
            )
        }
            .distinctUntilChanged()
            .collectLatest { want ->
                if (want == null) {
                    detail = null
                    return@collectLatest
                }
                // **손가락이 멎기를 기다린다.** pdfium 의 잠금은 프로세스 전역이라
                // 끄는 동안 프레임마다 타일을 뜨면 줄이 서고, 그 줄이 밀리는 만큼 화면이
                // 늦게 따라온다. `collectLatest` 가 새 요청에서 앞의 것을 끊는다.
                delay(DETAIL_DELAY_MS)
                val tile = store.detail(
                    view = view,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    scale = want.scale,
                    focusX = want.focusX,
                    focusY = want.focusY,
                    // 화면이 허락하는 최대 확대(원본의 2배)까지만 선명하게 그린다.
                    maxScale = zoomState.maxScale * fitScale,
                )
                detail = tile?.let {
                    val at = DocDetail.fractionOf(it.tile)
                    DetailLayer(it.image, at[0], at[1], at[2], at[3])
                }
            }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val f = fit) {
            is Fit.Loading -> CircularProgressIndicator(color = Color.White)

            is Fit.Failed -> Text(
                text = stringResource(R.string.doc_page_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(32.dp),
            )

            is Fit.Ready -> {
                val layout = store.knownLayout(view)
                ZoomableImage(
                    bitmap = f.image,
                    state = zoomState,
                    modifier = Modifier.fillMaxSize(),
                    // 고른 글이 이 칸에 있으면 두드림은 그것을 푼다(막대를 여닫지 않는다 — 글 고르기의 관행이다).
                    onTap = {
                        if (selection != null && selection.active && selection.page in view.pages) selection.clear() else onTap()
                    },
                    onLongPressAt = if (selection != null && layout != null) {
                        { p -> selectWordAt(p, f.image, layout, zoomState, selection) }
                    } else {
                        null
                    },
                    detail = detail,
                    // **원본의 2배까지.** PDF 의 원본은 종이의 실제 크기다(`PdfLimits.originalWidthPx`).
                    // 맞춤 층을 뜨면서 쪽 크기를 이미 쟀으므로 여기서 문서를 다시 만지지 않는다.
                    originalWidth = layout
                        ?.let { PdfLimits.originalWidthPx(it.width, densityDpi) }
                        ?: f.image.width,
                )
                if (layout != null && highlights.isNotEmpty()) {
                    SearchHighlights(f.image, layout, highlights, currentMatch, zoomState)
                }
                if (layout != null && selection != null) {
                    val frame = remember(f.image, zoomState) { ZoomFrame(f.image, zoomState) }
                    PdfSelectionOverlay(
                        layout = layout,
                        selection = selection,
                        frame = frame,
                        // 확대·이동이 바뀌면 떠 있는 도구 막대를 새 자리로 옮긴다.
                        frameKey = zoomState.scale to zoomState.offset,
                        onMessage = onMessage,
                    )
                }
            }
        }
    }
}

/**
 * 찾기 결과를 쪽 그림 위에 칠한다.
 *
 * **`ZoomableImage` 와 같은 변환을 `ZoomState` 한 곳에서 읽는다** — 그쪽 주석이 적은 대로, 층마다 자기 변환을 들면 반드시
 * 어긋난다. 맞춘 크기는 `ZoomMath.fittedSize` 로 같은 식을 쓴다. **포인터를 받지 않는 층이다** — 그리기 수정자만 있어 탭·핀치가
 * 아래의 그림으로 내려간다(함정 표의 '형제 히트 테스트' 가 여기서는 원하는 쪽으로 작동한다).
 */
@Composable
private fun SearchHighlights(
    image: ImageBitmap,
    layout: PdfSpreads.Layout,
    matches: List<PdfSearch.Match>,
    current: PdfSearch.Match?,
    zoom: ZoomState,
) {
    Canvas(Modifier.fillMaxSize()) {
        val (fw, fh) = ZoomMath.fittedSize(image.width, image.height, size.width, size.height)
        if (fw <= 0f || fh <= 0f) return@Canvas
        translate(zoom.offset.x, zoom.offset.y) {
            scale(zoom.scale, pivot = center) {
                val left = (size.width - fw) / 2f
                val top = (size.height - fh) / 2f
                for (m in matches) {
                    val color = if (m == current) HIGHLIGHT_CURRENT else HIGHLIGHT
                    for (box in m.boxes) {
                        val r = PdfSearch.boxIn(layout, m.page, box) ?: continue
                        drawRect(
                            color = color,
                            topLeft = Offset(left + r[0] * fw, top + r[1] * fh),
                            size = Size((r[2] - r[0]) * fw, (r[3] - r[1]) * fh),
                        )
                    }
                }
            }
        }
    }
}

/** 길게 누른 손가락이 닿은 것으로 치는 거리. 글자는 손가락보다 작다. */
private val FINGER_SLOP = 14.dp

private sealed interface Fit {
    data object Loading : Fit
    data object Failed : Fit
    data class Ready(val image: ImageBitmap) : Fit
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomBar(
    page: Int,
    pageCount: Int,
    spread: Boolean,
    canSpread: Boolean,
    onSeek: (Int) -> Unit,
    onJump: () -> Unit,
    onGrid: () -> Unit,
    onSpread: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            // 시스템 바가 숨어도 변하지 않는 인셋 — 막대가 나타나는 동안 바가 함께 나타나 글이 위로 밀려 오르지 않게.
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pageCount > 1) {
            // 화면 낭독기는 슬라이더를 범위의 백분율로 읽는다(함정 표) — 무엇을 고르는지와 지금 쪽을 우리 말로 준다.
            val sliderName = stringResource(R.string.doc_slider)
            val sliderValue = stringResource(R.string.doc_slider_value, page + 1, pageCount)
            // 재생 막대와 같은 모양(`IroSlider` — 얇은 트랙 + 둥근 점).
            IroSlider(
                modifier = Modifier.semantics {
                    contentDescription = sliderName
                    stateDescription = sliderValue
                },
                value = page.toFloat(),
                onValueChange = { onSeek(it.toInt().coerceIn(0, pageCount - 1)) },
                valueRange = 0f..(pageCount - 1).toFloat(),
                // **눈금을 두지 않는다.** 만화는 쪽이 수십이지만 문서는 2,000쪽이 드물지
                // 않고, 그만큼의 눈금 표시는 트랙을 회색 띠로 만든다.
                steps = 0,
                inactiveColor = Color.White.copy(alpha = 0.28f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shown = if (spread) PdfSpreads.pages(PdfSpreads.spreadOf(page, pageCount), pageCount) else listOf(page)
            Text(
                text = if (shown.size > 1) {
                    stringResource(R.string.doc_spread_of, shown[0] + 1, shown[1] + 1, pageCount)
                } else {
                    stringResource(R.string.doc_page_of, page + 1, pageCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            TextButton(onClick = onJump) { Text(stringResource(R.string.doc_jump)) }
            TextButton(onClick = onGrid) { Text(stringResource(R.string.doc_grid)) }
            if (canSpread) {
                // 지금 모양이 아니라 **누르면 될 모양**을 적는다.
                TextButton(onClick = onSpread) {
                    Text(stringResource(if (spread) R.string.doc_spread_off else R.string.doc_spread_on))
                }
            }
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
        title = { Text(stringResource(R.string.doc_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.doc_jump_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onGo(number!! - 1) }) {
                Text(stringResource(R.string.doc_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_cancel)) }
        },
    )
}

/**
 * 실패 문구. **[OpenFailure.detail] 을 쓰지 않는다** — 절대경로와 공격자가 심은 문자열이
 * 거기 들어 있다(저장소 규칙).
 */
private fun failureText(failure: OpenFailure): Int = when (failure) {
    is OpenFailure.Corrupt -> R.string.doc_failed_corrupt
    is OpenFailure.PasswordRequired -> R.string.doc_failed_password
    is OpenFailure.Encrypted -> R.string.doc_failed_encrypted
    is OpenFailure.NoPermission -> R.string.doc_failed_no_permission
    is OpenFailure.TooLarge -> R.string.doc_failed_too_large
    is OpenFailure.Timeout -> R.string.doc_failed_timeout
    is OpenFailure.Unsupported -> R.string.doc_failed_unsupported
    is OpenFailure.Io -> R.string.doc_failed_io
}

/** 종이를 얹는 바닥. 검정은 흰 쪽과 대비가 너무 세다. */
private val PAPER_BACKDROP = Color(0xFF303030)

/** 읽는 모양을 아직 모를 때의 종이 색(흰 종이). */
private const val WHITE_PAPER = 0xFFFFFFFF.toInt()

/** 확대가 멎었다고 보는 시간. */
private const val DETAIL_DELAY_MS = 180L

/** 찾기 결과의 칠. 글이 비쳐 보이게 반투명으로. 지금 보는 것은 더 짙게. */
private val HIGHLIGHT = Color(0x66FFD54F)
private val HIGHLIGHT_CURRENT = Color(0x99FF8F00)

/** 칸으로 나눈 화면에서 알림이 아래 막대(단추 48dp + 여백)를 비켜서는 거리. */
private val BOTTOM_BAR_CLEARANCE = 64.dp
