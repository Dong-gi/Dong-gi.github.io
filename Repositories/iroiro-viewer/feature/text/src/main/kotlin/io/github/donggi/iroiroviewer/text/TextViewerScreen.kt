package io.github.donggi.iroiroviewer.text

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.ProcessTextKey
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.ui.IroProgress
import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.format.text.PlainHighlighter
import io.github.donggi.iroiroviewer.format.text.RowHighlighter
import io.github.donggi.iroiroviewer.format.text.TextLanguage
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 텍스트·코드 뷰어. **보기만 한다** — 고치는 것은 위 막대의 '편집'(연필)이 여는 편집 화면이다([TextEditScreen]).
 *
 * ## 차용한 관행
 *
 * `less`·`bat` 과 모바일 편집기들이 공통으로 하는 것만 가져왔다.
 *
 * | 무엇 | 어떻게 | 왜 |
 * |---|---|---|
 * | 줄 번호 | 왼쪽 고정, 본문보다 흐리게 | 가로로 밀어도 번호는 제자리에 있어야 쓸모가 있다 |
 * | 긴 줄 | **접지 않는 것이 기본**, 가로 스크롤 | 코드는 들여쓰기가 뜻을 가진다. 접으면 그것이 무너진다 |
 * | 이어지는 행 | 줄 번호를 비운다 | `less -N` 과 같다 |
 * | 찾기 | 행 번호 목록 + 위아래 단추 | 결과 목록을 따로 띄우면 본문을 잃는다 |
 * | 인코딩 | 아래 막대에 항상 보이고, 눌러서 바꾼다 | 잘못 읽힌 것을 **한 번에** 고칠 수 있어야 한다 |
 *
 * ## 글꼴
 *
 * [FontFamily.Monospace] 를 쓴다. 안드로이드의 고정폭 글꼴에는 한글·한자가 없지만,
 * 시스템 폴백 사슬이 `NotoSansCJK` 로 이어 주므로 **글자가 빠지지 않는다.** 저장소에
 * 글꼴을 넣지 않기로 한 것이 이 결정이다 — 이 저장소는 통째로 웹에 올라간다.
 * 대가는 한글의 글자폭이 라틴 문자의 정확히 두 배가 아닐 수 있다는 것이고, 그때
 * 한글이 섞인 줄의 세로 정렬이 어긋난다. 코드에서 한글은 대개 주석과 문자열에 있어
 * 실제로 걸리는 자리가 드물다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    path: String,
    /**
     * 공용 스낵바.
     *
     * **Root 가 그릴 수 있는 모든 화면은 여기에 붙은 [SnackbarHost] 를 하나씩 가져야
     * 한다.** 붙지 않은 화면으로 넘어가면 파일 작업 결과를 보여 주는 `showSnackbar` 가
     * 돌아오지 않아 그 뒤 결과가 전부 막힌다(3·4단계 검토가 고친 실패 형태).
     */
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 편집 화면을 연다. 인자는 뷰어에서 손으로 고른 인코딩(`TextEncoding.name`, 판정이면 null) — 편집기가 같은 글을 본다.
     * null 이면 단추가 없다 — 다른 앱이 넘긴 읽기 전용 사본(`app` 의 `readOnly`).
     */
    onEdit: ((encoding: String?) -> Unit)? = null,
    /**
     * CSV 를 편집기(`feature:editor`)의 표 편집기로 연다 — 인자는 편집기의 형식 글자. 편집기가 CSV 를 다룰 때만 ⋮ 에 줄이
     * 있다. 글 파일은 이 앱의 편집기가 더 낫다(인코딩·줄 끝을 바이트까지 지킨다) — 넘기지 않는다. 읽기 전용 사본이면 null.
     */
    onEditor: ((String) -> Unit)? = null,
    /** 바뀌면 같은 경로를 다시 읽는다 — 편집기(`feature:editor`)에서 돌아와 저장한 것을 보인다(`app` 이 [TextViewer.changedElsewhere] 와 함께 올린다). */
    reopen: Int = 0,
) {
    val context = LocalContext.current
    val vm: TextViewModel = viewModel { TextViewModel(AppPreferencesTextStore(context.applicationContext)) }
    // **입장 표.** 회전·프로세스 재생성에는 같은 값이 살아남고, 화면을 떠났다 돌아오면 새 값이 된다 — 뷰모델이 이것으로
    // '새로 열었다(저장된 설정을 다시 읽는다)' 와 '돌렸다(지금 설정 그대로)' 를 가른다(`ViewerSettings`).
    val entry = rememberSaveable { System.nanoTime() }
    LaunchedEffect(path, entry, reopen) { vm.open(path, entry) }
    // 편집기(`feature:editor`)의 표 편집기로 열 수 있는가 — CSV 만, 꾸러미 관리자에 묻는다(경로가 바뀔 때만).
    val sheetEditorKind = remember(path, onEditor != null) {
        if (onEditor != null && path.endsWith(".csv", ignoreCase = true)) EditorApp.kindFor(context, path) else null
    }
    // 화면을 떠나면 미리보기를 놓는다 — 뷰모델은 액티비티에 묶여 살아 있다([TextViewModel.onScreenLeft]). 회전도 컴포지션을
    // 버리므로 그것과 가른다([leavesScreen]).
    val activity = LocalActivity.current
    DisposableEffect(vm) {
        onDispose { if (leavesScreen(activity?.isChangingConfigurations)) vm.onScreenLeft() }
    }
    // '다른 앱으로 열기'. 넘기는 것은 언제나 화면이 받은 원문 파일이다 — 미리보기 중에도([TextOpenWith]).
    val openWith = rememberOpenWith(path, snackbar)
    // '브라우저로 열기'(HTML 만) — 같은 파일을 웹 쪽(`text/html`)으로 넘긴다. 이 앱은 인터넷에 닿지 않으므로 바깥의 그림·
    // 글꼴·스타일까지 보려면 브라우저로 연다(HTML 미리보기에서 인터넷 허용을 걷어 낸 자리).
    val isHtml = remember(path) { PreviewKind.of(path.substringAfterLast('/')) == PreviewKind.HTML }
    val openInBrowser = rememberOpenWith(path, snackbar, ExternalOpen.Mode.WEB_PAGE)
    val scope = rememberCoroutineScope()
    val copyMessage = rememberCopyMessages()
    val sendMessage = rememberSendMessages()

    val state by vm.state.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val scrollTo by vm.scrollTo.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewOn = preview !is TextViewModel.Preview.Off
    val editGate by vm.editGate.collectAsStateWithLifecycle()
    val tooLargeToEdit = (editGate as? TextEditGate.TooLarge)?.let {
        stringResource(R.string.text_edit_too_large, io.github.donggi.iroiroviewer.io.Format.size(it.limitBytes))
    }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var goToLine by remember { mutableStateOf(false) }

    BackHandler {
        if (searchOpen) {
            searchOpen = false
            vm.clearSearch()
        } else {
            onClose()
        }
    }

    val listState = rememberLazyListState()
    // 가로 스크롤은 **모든 행이 함께** 움직여야 한다. 행마다 따로 두면 한 줄만 밀린다.
    val hScroll = rememberScrollState()
    val colors = rememberCodeColors()
    val lineEndMarks = rememberLineEndMarks()

    // 보이는 구간을 뷰모델에 알려 창을 옮기게 한다.
    LaunchedEffect(listState, state) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.size }
            .collect { (first, visible) -> vm.onVisible(first, maxOf(visible, 1)) }
    }

    LaunchedEffect(scrollTo) {
        scrollTo?.let { row ->
            // **먼저 창을 그 자리로 옮긴다.** 옮기지 않고 스크롤만 하면 목표 행이 아직
            // 안 읽힌 자리라 빈 줄이 뜨고, 사용자는 찾기가 실패한 줄 안다.
            vm.onVisible(row, 1)
            listState.scrollToItem(row.coerceAtLeast(0))
            vm.consumeScrollTo()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searchOpen) {
                SearchBar(
                    query = search.query,
                    ignoreCase = search.ignoreCase,
                    onQuery = vm::setQuery,
                    onSubmit = vm::runSearch,
                    onToggleCase = vm::toggleIgnoreCase,
                    onClose = {
                        searchOpen = false
                        vm.clearSearch()
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.text_close))
                        }
                    },
                    title = {
                        Text(
                            // 다 읽기 전·실패 화면에도 무엇을 열었는지 적는다(파일 이름만 — [TextTitle]).
                            text = TextTitle.of(state, path),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    },
                    actions = {
                        // 마크다운·HTML 이면 원문 ↔ 미리보기. 메뉴 속에 숨기지 않는다 — 이 파일을 연 사람이 가장 먼저 찾는 것이다.
                        if ((state as? TextViewModel.State.Ready)?.doc?.previewKind != null) {
                            TextButton(onClick = vm::togglePreview) {
                                Text(
                                    stringResource(
                                        if (previewOn) R.string.text_preview_source else R.string.text_preview,
                                    ),
                                )
                            }
                        }
                        // 편집. 고칠 수 없는 자리·인코딩이면 없다([TextEditGate]). 크기 때문이면 두고 누를 때 까닭을 말한다 —
                        // 숨기면 이 파일만 왜 고칠 수 없는지 모른다. 편집기는 뷰어가 손으로 고른 인코딩으로 연다.
                        if (onEdit != null && editGate != TextEditGate.Hidden) {
                            IconButton(onClick = {
                                val gate = editGate
                                if (gate is TextEditGate.Open) {
                                    val doc = (state as? TextViewModel.State.Ready)?.doc
                                    onEdit(doc?.takeIf { it.encodingChosen }?.encoding?.name)
                                } else if (tooLargeToEdit != null) {
                                    scope.launch { snackbar.showSnackbar(tooLargeToEdit) }
                                }
                            }) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.text_edit))
                            }
                        }
                        // 찾기는 원문의 행을 훑는다. 미리보기에서는 찾은 자리를 보여 줄 곳이 없다.
                        IconButton(
                            onClick = { searchOpen = true },
                            enabled = state is TextViewModel.State.Ready && !previewOn,
                        ) {
                            Icon(Icons.Filled.Search, stringResource(R.string.text_search))
                        }
                        // **⋮ 단추와 메뉴를 한 상자에 담는다.** Compose 의 Popup 은 자기를 감싼 **부모
                        // 레이아웃 노드**를 앵커로 삼는다. `actions` 에 형제로 두면 앵커가 아이콘 줄
                        // 전체가 되어 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(사용자가 지적했다).
                        // `offset` 으로 보정하지 마라 — 화면 폭·글꼴 배율에서 다시 어긋난다.
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.text_more))
                            }
                            ViewerMenu(
                                expanded = menuOpen,
                                settings = settings,
                                canGoToLine = !previewOn,
                                onDismiss = { menuOpen = false },
                                onWrap = { vm.updateSettings { it.copy(wrap = !it.wrap) } },
                                onLineNumbers = { vm.updateSettings { it.copy(lineNumbers = !it.lineNumbers) } },
                                onLineEnds = { vm.updateSettings { it.copy(showLineEnds = !it.showLineEnds) } },
                                onFontBigger = { vm.updateSettings { it.copy(fontSp = it.fontSp + 1) } },
                                onFontSmaller = { vm.updateSettings { it.copy(fontSp = it.fontSp - 1) } },
                                onEncoding = { sheet = Sheet.ENCODING },
                                onLanguage = { sheet = Sheet.LANGUAGE },
                                onGoToLine = { goToLine = true },
                                canOpenWith = TextOpenWith.inMenu(state),
                                onOpenWith = openWith,
                                onOpenInBrowser = if (isHtml) openInBrowser else null,
                                onSheetEditor = if (sheetEditorKind != null && onEditor != null) {
                                    { onEditor(sheetEditorKind) }
                                } else {
                                    null
                                },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            when {
                searchOpen -> SearchFooter(
                    search = search,
                    onStep = vm::stepHit,
                )
                state is TextViewModel.State.Ready -> StatusBar(
                    doc = (state as TextViewModel.State.Ready).doc,
                    onEncoding = { sheet = Sheet.ENCODING },
                    onLanguage = { sheet = Sheet.LANGUAGE },
                )
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (val s = state) {
                is TextViewModel.State.Loading -> Centered { CircularProgressIndicator() }

                is TextViewModel.State.Indexing -> Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.text_indexing, s.rows),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.total > 0) {
                            IroProgress(
                                progress = { (s.scanned.toFloat() / s.total).coerceIn(0f, 1f) },
                                modifier = Modifier.width(220.dp),
                            )
                        }
                    }
                }

                is TextViewModel.State.Failed -> Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            text = stringResource(
                                when (s.kind) {
                                    TextViewModel.State.Failed.Kind.UNREADABLE -> R.string.text_failed_unreadable
                                    TextViewModel.State.Failed.Kind.BINARY -> R.string.text_failed_binary
                                    TextViewModel.State.Failed.Kind.UNSUPPORTED_ENCODING ->
                                        R.string.text_failed_encoding
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        // 글이 아니거나 우리가 못 읽는 인코딩이다 — 파일은 멀쩡하니 그것을 여는 앱으로 넘길 길을 먼저 둔다.
                        // 파일에 닿지 못한 것에는 없다(받는 앱도 닿지 못한다 — [TextOpenWith.onFailure]).
                        if (TextOpenWith.onFailure(s.kind)) {
                            Button(onClick = openWith) {
                                Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with))
                            }
                        }
                        // **바이너리라도 억지로 열 수 있게 둔다.** 우리 판정이 틀릴 수 있고,
                        // 틀렸을 때 사용자에게 길이 없으면 그것이 더 나쁘다.
                        if (s.kind != TextViewModel.State.Failed.Kind.UNREADABLE) {
                            TextButton(onClick = { sheet = Sheet.ENCODING }) {
                                Text(stringResource(R.string.text_open_anyway))
                            }
                        }
                    }
                }

                is TextViewModel.State.Ready -> {
                    val current = settings
                    when {
                        // 저장된 설정을 읽는 중이다(한두 프레임). 기본값으로 한 번 그렸다가 바뀌지 않게 기다린다.
                        current == null -> Centered { CircularProgressIndicator() }
                        previewOn -> PreviewArea(
                            preview = preview,
                            fontSp = settings?.fontSp ?: TextViewerDefaults.DEFAULT_FONT_SP,
                            html = s.doc.previewKind == PreviewKind.HTML,
                            onShowSource = vm::togglePreview,
                            onRunScripts = vm::runScripts,
                            onStopScripts = vm::stopScripts,
                            onOpenInBrowser = openInBrowser,
                        )
                        else -> RowList(
                            doc = s.doc,
                            window = window,
                            listState = listState,
                            hScroll = hScroll,
                            wrap = current.wrap,
                            showLineNumbers = current.lineNumbers,
                            fontSize = current.fontSp,
                            colors = colors,
                            query = if (searchOpen) search.query else "",
                            ignoreCase = search.ignoreCase,
                            currentHitRow = search.currentRow,
                            marks = if (current.showLineEnds) lineEndMarks else null,
                            onCopyOutcome = { outcome ->
                                copyMessage(outcome)?.let { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                            },
                            onSendOutcome = { outcome ->
                                scope.launch { snackbar.showSnackbar(sendMessage(outcome)) }
                            },
                            // 두 손가락으로 바꾼 크기는 ⋮ 의 '글자 키우기' 와 같은 설정에 남는다([pinchFontSize]).
                            onFontSize = { sp -> vm.updateSettings { it.copy(fontSp = sp) } },
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        Sheet.ENCODING -> {
            val previews by vm.previews.collectAsStateWithLifecycle()
            EncodingSheet(
                current = (state as? TextViewModel.State.Ready)?.doc?.encoding,
                detection = (state as? TextViewModel.State.Ready)?.doc?.detection,
                previews = previews,
                onPick = {
                    sheet = null
                    vm.chooseEncoding(it)
                },
                onDismiss = { sheet = null },
            )
        }
        Sheet.LANGUAGE -> LanguageSheet(
            current = (state as? TextViewModel.State.Ready)?.doc?.language,
            onPick = {
                sheet = null
                vm.chooseLanguage(it)
            },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    if (goToLine) {
        val doc = (state as? TextViewModel.State.Ready)?.doc
        GoToLineDialog(
            lineCount = doc?.index?.lineCount ?: 0,
            onGo = {
                goToLine = false
                vm.goToLine(it)
            },
            onDismiss = { goToLine = false },
        )
    }
}

private enum class Sheet { ENCODING, LANGUAGE }

/**
 * 화면이 컴포지션에서 빠지는 것이 **떠나는 것**인가. 회전(구성 변경)이면 아니다 — 컴포지션을 버리고 다시 만들 뿐 같은 화면이고, 돌린
 * 뒤에도 같은 쪽이 보여야 한다. 액티비티를 모르면(미리보기 도구 따위) 떠난 것으로 본다 — 쥐고 있는 쪽이 틀리는 것보다 낫다.
 */
internal fun leavesScreen(changingConfigurations: Boolean?): Boolean = changingConfigurations != true

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// ---- 본문 -----------------------------------------------------------------------

@Composable
private fun RowList(
    doc: TextViewModel.Doc,
    window: TextViewModel.Window,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hScroll: androidx.compose.foundation.ScrollState,
    wrap: Boolean,
    showLineNumbers: Boolean,
    fontSize: Int,
    colors: CodeColors,
    query: String,
    ignoreCase: Boolean,
    currentHitRow: Int?,
    /** 줄 끝 표시. null 이면 그리지 않는다. */
    marks: LineEndMarks?,
    /** 고른 글을 클립보드에 넣은 결과. 화면이 알림으로 말한다. */
    onCopyOutcome: (RowCopy.Outcome) -> Unit,
    /** 고른 글을 다른 앱으로 보낸 결과 가운데 말할 것([RowSend]). */
    onSendOutcome: (RowSend.Outcome) -> Unit,
    /** 두 손가락으로 글자 크기를 바꿨다([pinchFontSize]). */
    onFontSize: (Int) -> Unit,
) {
    val lineDigits = remember(doc.index.lineCount) {
        maxOf(2, doc.index.lineCount.toString().length)
    }
    val gutterWidth = (lineDigits * (fontSize * 0.62f) + 12f).dp
    val rowHeight = (fontSize * 1.45f).dp

    // **글 고르기**. 길게 누르면 낱말이 골라지고 손잡이로 넓힌다 — 안드로이드의 글 고르기 그대로다.
    // compose-foundation 1.12 의 `SelectionContainer` 는 고른 행을 게으른 목록 밖으로 밀려나도 **붙든다**(PinnableContainer)
    // 그리고 손잡이를 끄는 동안 목록을 민다 — 그래서 화면보다 긴 범위도 고를 수 있다.
    // **저장되지 않는 상태로 든다.** `rememberSelectionState()` 는 `rememberSaveable` 이라 고른 글(강조의 색까지)을 저장 상태 번들에
    // 싣는다 — 수백 줄을 고른 채 홈으로 나가면 번들이 바인더 한도를 넘어 앱이 죽고(`TransactionTooLargeException`), 고른 파일의 글이
    // 시스템이 디스크에 쓸 수 있는 번들에 남는다(함정 표의 `rememberTextFieldState` 와 같은 까닭). 회전하면 고른 것이 풀리는 것이 대가다.
    val selection = remember { SelectionState() }
    val realClipboard = LocalClipboard.current
    val latestOnCopy by rememberUpdatedState(onCopyOutcome)
    val clipboard = remember(realClipboard, selection) {
        RowClipboard(realClipboard, selection) { latestOnCopy(it) }
    }
    SelectionBackHandler(selection)
    val context = LocalContext.current
    val latestOnSend by rememberUpdatedState(onSendOutcome)
    val latestFontSize by rememberUpdatedState(fontSize)
    val latestOnFontSize by rememberUpdatedState(onFontSize)

    // '모두 선택' 을 뺀다. 이 목록은 창(400행)만 읽어 두므로 그것이 고르는 것은 **지금 그려진 행뿐**이다 — '모두' 라고 적힌
    // 단추가 파일의 일부만 고르는 것은 거짓말이다.
    // 다른 앱으로 보내기(번역 등 — `PROCESS_TEXT`)도 Compose 의 것을 빼고 우리 것을 단다 — Compose 의 것은 날것의 선택을 상한 없이
    // 실어 큰 선택에서 앱이 죽었다([RowSend]).
    Box(
        Modifier
            .fillMaxSize()
            .filterTextContextMenuComponents { it.key != TextContextMenuKeys.SelectAllKey && it.key !is ProcessTextKey }
            .appendTextContextMenuComponents { sendItems(context, selection) { latestOnSend(it) } }
            .pinchFontSize(fontSp = { latestFontSize }, onFontSp = { latestOnFontSize(it) }),
    ) {
        CompositionLocalProvider(LocalClipboard provides clipboard) {
            SelectionContainer(state = selection, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(doc.index.rowCount) { rowNo ->
                        // **고른 행은 창이 옮겨 가도 글을 잃지 않아야 한다.** 붙들린 행이 창 밖이 되면 `rowAt` 이 null 이 되어
                        // 빈 칸으로 바뀌고, 그 행의 글이 선택에서 조용히 빠진다. 이 칸이 마지막으로 그린 행을 들고 있는다 —
                        // 문서가 바뀌면(인코딩·언어) 새로 시작한다.
                        val held = remember(doc) { HeldRow() }
                        val row = window.rowAt(rowNo)?.also { held.row = it } ?: held.row
                        Row(Modifier.fillMaxWidth()) {
                            if (showLineNumbers) {
                                // 줄 번호는 고르지 않는다 — 복사한 코드 앞에 번호가 붙으면 다시 지워야 한다.
                                DisableSelection {
                                    Text(
                                        // 이어지는 행에는 번호를 비운다 — `less -N` 과 같다. 번호를 다시
                                        // 찍으면 4,096자마다 줄이 하나씩 늘어난 것처럼 보인다.
                                        text = if (row == null || row.continuation) "" else (row.line + 1).toString(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.45f).sp,
                                        color = colors.gutter,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        modifier = Modifier.width(gutterWidth).padding(end = 6.dp),
                                    )
                                }
                            }
                            if (row == null) {
                                // 아직 안 읽힌 행. 높이만 맞춰 두면 스크롤 막대가 튀지 않는다.
                                Box(Modifier.height(rowHeight).fillMaxWidth())
                            } else {
                                val text = remember(row, query, ignoreCase, currentHitRow, rowNo, colors, marks) {
                                    annotateRow(row, colors, query, ignoreCase, rowNo == currentHitRow, marks)
                                }
                                Text(
                                    text = text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.45f).sp,
                                    color = colors.foreground,
                                    softWrap = wrap,
                                    modifier = if (wrap) {
                                        Modifier.fillMaxWidth().padding(end = 8.dp)
                                    } else {
                                        // 접지 않을 때만 가로로 민다. 접을 때 함께 걸면 가로 스크롤이
                                        // 아무 데도 가지 않는 상태로 남아 세로 스크롤을 먹는다.
                                        Modifier.horizontalScroll(hScroll).padding(end = 8.dp)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 목록의 한 칸이 마지막으로 그린 행. 컴포지션의 상태가 아니라 캐시다 — 같은 값을 다시 적어도 해가 없다. */
private class HeldRow {
    var row: io.github.donggi.iroiroviewer.format.text.TextRow? = null
}

/**
 * 고른 글이 있으면 뒤로 가기가 그것부터 푼다(화면을 닫기 전에). 따로 둔 까닭은 재구성의 범위다 — 선택은 손잡이를 끄는
 * 동안 계속 바뀌는데, 그것을 목록이 읽으면 그때마다 목록 전체가 다시 그려진다.
 */
@Composable
private fun SelectionBackHandler(selection: SelectionState) {
    val active by remember(selection) { derivedStateOf { selection.selectedTexts.isNotEmpty() } }
    BackHandler(enabled = active) { selection.clear() }
}

/** 다른 앱으로 보낸 결과([RowSend.Outcome])의 알림 문장. */
@Composable
private fun rememberSendMessages(): (RowSend.Outcome) -> String {
    val truncated = stringResource(R.string.text_send_truncated, "%,d".format(Locale.ROOT, RowSend.MAX_CHARS))
    val failed = stringResource(R.string.text_send_failed)
    return remember(truncated, failed) {
        { outcome ->
            when (outcome) {
                is RowSend.Outcome.Truncated -> truncated
                RowSend.Outcome.Failed -> failed
            }
        }
    }
}

/**
 * 복사의 결과를 알림 문장으로. null 이면 말하지 않는다.
 *
 * **안드로이드 13(API 33)부터는 '복사했다' 를 말하지 않는다** — 시스템이 클립보드에 넣는 순간 스스로 보여 주고, 공식 안내가
 * 앱이 겹쳐 알리지 말라고 한다. 그 아래(사용자의 기기 Android 12)에서는 아무것도 보이지 않으므로 우리가 말한다.
 */
@Composable
private fun rememberCopyMessages(): (RowCopy.Outcome) -> String? {
    val copied = stringResource(R.string.text_copied)
    val tooLong = stringResource(R.string.text_copy_too_long, "%,d".format(Locale.ROOT, RowCopy.MAX_CHARS))
    val failed = stringResource(R.string.text_copy_failed)
    return remember(copied, tooLong, failed) {
        { outcome ->
            when (outcome) {
                is RowCopy.Outcome.Copied -> copied.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU }
                RowCopy.Outcome.TooLong -> tooLong
                RowCopy.Outcome.Failed -> failed
            }
        }
    }
}

// ---- 막대 -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    ignoreCase: Boolean,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleCase: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.text_search_close))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.text_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            IconButton(onClick = onToggleCase) {
                Text(
                    text = stringResource(R.string.text_search_case),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ignoreCase) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun SearchFooter(search: TextViewModel.SearchState, onStep: (Int) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Box {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        search.running -> stringResource(R.string.text_search_progress, search.percent)
                        search.query.isEmpty() -> ""
                        search.hits.isEmpty() -> stringResource(R.string.text_search_none)
                        else -> stringResource(
                            R.string.text_search_count,
                            search.cursor + 1,
                            search.hits.size,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onStep(-1) }, enabled = search.hits.isNotEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.text_search_prev))
                }
                IconButton(onClick = { onStep(1) }, enabled = search.hits.isNotEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.text_search_next))
                }
            }
            // 파일 전체를 훑는 동안 얼마나 왔는지 보인다. 멈추는 길은 위 막대의 닫기(찾기 취소)다.
            // **막대 위에 겹친다.** 칸으로 두면 찾기가 시작하고 끝날 때마다 아래 막대가 4dp 늘었다 줄어 본문이 위아래로 흔들린다.
            if (search.running) {
                IroProgress(
                    progress = { search.percent / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * 아래 막대. **인코딩을 언제나 보여 준다.**
 *
 * 자동 판정이 틀렸을 때 사용자가 가장 먼저 보는 곳이 여기여야 한다. 메뉴 속에 숨기면
 * 깨진 글자를 보면서 "이 앱은 한글을 못 읽는다" 로 끝난다.
 */
@Composable
private fun StatusBar(
    doc: TextViewModel.Doc,
    onEncoding: () -> Unit,
    onLanguage: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onEncoding) {
                Text(
                    text = doc.encoding.label + confidenceMark(doc.detection),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onLanguage) {
                Text(
                    text = if (doc.highlightOff) {
                        stringResource(R.string.text_highlight_off, doc.language.label)
                    } else {
                        doc.language.label
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = stringResource(R.string.text_lines, doc.index.lineCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 판정이 자신 없으면 물음표를 붙인다. 아닌 척하지 않는다. */
@Composable
private fun confidenceMark(d: CharsetDetector.Detection?): String = when (d?.confidence) {
    CharsetDetector.Detection.Confidence.LOW -> " ?"
    else -> ""
}

/**
 * 보기 메뉴. **여기서 바꾼 것은 저장된다** — 다음에 여는 파일도 이 모양이다(14단계). 설정을 읽는 동안([settings] 가
 * null)은 고를 것을 흐리게 둔다.
 */
@Composable
private fun ViewerMenu(
    expanded: Boolean,
    settings: TextViewerDefaults?,
    canGoToLine: Boolean,
    onDismiss: () -> Unit,
    onWrap: () -> Unit,
    onLineNumbers: () -> Unit,
    onLineEnds: () -> Unit,
    onFontBigger: () -> Unit,
    onFontSmaller: () -> Unit,
    onEncoding: () -> Unit,
    onLanguage: () -> Unit,
    onGoToLine: () -> Unit,
    /** 파일에 닿을 수 있는가([TextOpenWith.inMenu]). 거짓이면 '다른 앱으로 열기'·'브라우저로 열기' 를 흐리게 둔다. */
    canOpenWith: Boolean,
    onOpenWith: () -> Unit,
    /** HTML 을 브라우저로(HTML 이 아니면 null — 줄이 없다). */
    onOpenInBrowser: (() -> Unit)? = null,
    /** CSV 를 편집기(`feature:editor`)의 표 편집기로(없으면 줄이 없다). */
    onSheetEditor: (() -> Unit)? = null,
) {
    val ready = settings != null
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CheckItem(stringResource(R.string.text_wrap), settings?.wrap == true, ready) { onWrap() }
        CheckItem(stringResource(R.string.text_line_numbers), settings?.lineNumbers == true, ready) { onLineNumbers() }
        CheckItem(
            label = stringResource(R.string.text_line_ends),
            checked = settings?.showLineEnds == true,
            enabled = ready,
            supporting = stringResource(R.string.text_line_ends_legend),
        ) { onLineEnds() }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_font_bigger)) },
            onClick = onFontBigger,
            enabled = settings != null && settings.fontSp < TextViewerDefaults.MAX_FONT_SP,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_font_smaller)) },
            onClick = onFontSmaller,
            enabled = settings != null && settings.fontSp > TextViewerDefaults.MIN_FONT_SP,
        )
        HorizontalDivider()
        if (canGoToLine) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_goto_line)) },
                onClick = { onDismiss(); onGoToLine() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_encoding)) },
            onClick = { onDismiss(); onEncoding() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_language)) },
            onClick = { onDismiss(); onLanguage() },
        )
        // 보기 설정과 갈라 맨 아래에 둔다 — 이것만 이 화면을 떠난다. 문구는 `core:io` 의 것 한 벌이다.
        HorizontalDivider()
        if (onSheetEditor != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.text_open_sheet_editor)) },
                onClick = { onDismiss(); onSheetEditor() },
                enabled = canOpenWith,
            )
        }
        if (onOpenInBrowser != null) {
            DropdownMenuItem(
                text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_in_browser)) },
                onClick = { onDismiss(); onOpenInBrowser() },
                enabled = canOpenWith,
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)) },
            onClick = { onDismiss(); onOpenWith() },
            enabled = canOpenWith,
        )
    }
}

@Composable
private fun CheckItem(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            if (supporting == null) {
                Text(label)
            } else {
                Column {
                    Text(label)
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        trailingIcon = {
            if (checked) Icon(Icons.Filled.Check, null)
        },
        onClick = onClick,
        enabled = enabled,
    )
}

/** 줄 끝 표시의 글자. 문구처럼 `strings.xml` 에서 온다. */
@Composable
private fun rememberLineEndMarks(): LineEndMarks {
    val lf = stringResource(R.string.text_eol_lf)
    val crlf = stringResource(R.string.text_eol_crlf)
    val cr = stringResource(R.string.text_eol_cr)
    return remember(lf, crlf, cr) { LineEndMarks(lf = lf, crlf = crlf, cr = cr) }
}

// ---- 미리보기 ------------------------------------------------------------------

/**
 * 미리보기(마크다운·HTML)의 자리. 만드는 중·너무 큼·실패는 **말로** 알리고 원문으로 돌아가는 단추를 둔다 — 미리보기가
 * 안 되는 파일에서 사용자가 갇히지 않게. 줄인 것(뒤를 버렸다)과 싣지 않은 것(스크립트·프레임·바깥 자원)도 한 줄씩 말한다 —
 * 스크립트로 그리는 쪽은 비어 보이는데, 말하지 않으면 사용자는 뷰어가 고장 난 줄 안다.
 *
 * 스크립트를 버린 HTML 쪽에는 그 줄에 **'스크립트 실행'** 을 단다 — 보안 메일처럼 스크립트가 곧 내용인 쪽이 있다. 실행 중에는 그
 * 사실과 '멈추기' 를 같은 자리에 둔다(사용자가 지금 무엇을 보고 있는지 늘 보이게). 고른 것은 저장하지 않는다. 위생을 거친 쪽이 너무
 * 크거나 만들어지지 못한 HTML 에도 그 단추를 둔다 — 스크립트를 실행하는 쪽은 파일을 그대로 흘려 내주므로 더 큰 파일도 연다.
 *
 * **이 앱은 인터넷에 닿지 않는다**(사용자가 이 앱의 네트워크 기능을 걷었다). 바깥의 그림·글꼴·스타일까지 보려면 같은
 * 파일을 브라우저로 연다 — HTML 의 알림 줄과 안내에 **'브라우저로 열기'**([onOpenInBrowser])를 단다. 바깥 자원을 버린 쪽에만 줄이
 * 서므로 버린 것이 없는 쪽은 ⋮ 의 같은 줄로 간다.
 */
@Composable
private fun PreviewArea(
    preview: TextViewModel.Preview,
    fontSp: Int,
    html: Boolean,
    onShowSource: () -> Unit,
    onRunScripts: () -> Unit,
    onStopScripts: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val runScripts = stringResource(R.string.text_preview_run_scripts)
    val stopScripts = stringResource(R.string.text_preview_live_stop)
    val browser = stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_in_browser)
    when (preview) {
        TextViewModel.Preview.Off -> Unit
        TextViewModel.Preview.Building -> Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.text_preview_building), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is TextViewModel.Preview.TooLarge -> PreviewMessage(
            text = stringResource(
                R.string.text_preview_too_large,
                (preview.limitBytes / (1024 * 1024)).toInt(),
            ),
            onShowSource = onShowSource,
            actions = listOfNotNull(
                (runScripts to onRunScripts).takeIf { html && !preview.live },
                (browser to onOpenInBrowser).takeIf { html },
            ),
        )
        is TextViewModel.Preview.Failed -> PreviewMessage(
            text = stringResource(R.string.text_preview_failed),
            onShowSource = onShowSource,
            actions = listOfNotNull(
                (runScripts to onRunScripts).takeIf { html && !preview.live },
                (browser to onOpenInBrowser).takeIf { html },
            ),
        )
        is TextViewModel.Preview.Ready -> Column(Modifier.fillMaxSize()) {
            if (preview.truncated) PreviewNote(stringResource(R.string.text_preview_truncated))
            when {
                preview.live != null -> PreviewNote(
                    text = stringResource(R.string.text_preview_live),
                    actions = listOf(stopScripts to onStopScripts, browser to onOpenInBrowser),
                )
                // 위생을 거친 쪽이 무엇을 버렸다. HTML 이면 '브라우저로 열기' 도 단다(마크다운은 우리가 만든 쪽이라 바깥이 없다).
                preview.withheld -> PreviewNote(
                    text = stringResource(R.string.text_preview_withheld),
                    actions = listOfNotNull(
                        (runScripts to onRunScripts).takeIf { preview.scripted },
                        (browser to onOpenInBrowser).takeIf { preview.kind == PreviewKind.HTML },
                    ),
                )
            }
            PreviewPane(preview, textZoom = fontSp * 100 / TextViewerDefaults.DEFAULT_FONT_SP, modifier = Modifier.weight(1f))
        }
    }
}

/** 미리보기 위의 알림 한 줄. [actions] 가 있으면 줄 끝에 단추들(글, 누르면 할 일). */
@Composable
private fun PreviewNote(text: String, actions: List<Pair<String, () -> Unit>> = emptyList()) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = if (actions.isEmpty()) 16.dp else 4.dp, top = 6.dp, bottom = 6.dp),
            )
            for ((label, onClick) in actions) {
                TextButton(onClick = onClick, modifier = Modifier.padding(end = 4.dp)) { Text(label) }
            }
        }
    }
}

/**
 * 미리보기를 만들 수 없을 때의 안내. [actions] 의 첫 단추만 채운 모양이다 — 그것이 이 자리의 본 일이다(스크립트 실행). 나머지는
 * 테두리만(브라우저로 열기), 원문으로 돌아가는 단추는 글자만.
 */
@Composable
private fun PreviewMessage(text: String, onShowSource: () -> Unit, actions: List<Pair<String, () -> Unit>> = emptyList()) {
    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            actions.forEachIndexed { i, (label, onClick) ->
                if (i == 0) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }
            }
            TextButton(onClick = onShowSource) { Text(stringResource(R.string.text_preview_show_source)) }
        }
    }
}

// ---- 시트와 대화상자 ---------------------------------------------------------------

/**
 * 인코딩 고르기. **미리보기를 함께 보여 준다.**
 *
 * 이름만 늘어놓으면 사용자는 `CP949` 와 `Shift_JIS` 중 무엇인지 알 수 없다. 앞부분을
 * 그 인코딩으로 디코드해 보여 주면 **읽히는 것을 고르면** 된다 — 이름을 알 필요가 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncodingSheet(
    current: TextEncoding?,
    detection: CharsetDetector.Detection?,
    previews: Map<TextEncoding, String>,
    onPick: (TextEncoding) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.text_encoding),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            detection?.let {
                Text(
                    text = evidenceText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (enc in TextEncoding.userChoices) {
                    if (enc.charset == null) continue
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(enc.label, style = MaterialTheme.typography.bodyLarge)
                                previews[enc]?.let { p ->
                                    Text(
                                        text = p,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        trailingIcon = { if (enc == current) Icon(Icons.Filled.Check, null) },
                        onClick = { onPick(enc) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun evidenceText(d: CharsetDetector.Detection): String = when (val e = d.evidence) {
    is CharsetDetector.Evidence.ByBom -> stringResource(R.string.text_evidence_bom)
    is CharsetDetector.Evidence.AsciiOnly -> stringResource(R.string.text_evidence_ascii)
    is CharsetDetector.Evidence.OnlyCandidate -> stringResource(R.string.text_evidence_only)
    is CharsetDetector.Evidence.ByScript ->
        stringResource(R.string.text_evidence_script, e.score, e.runnerUpScore)
    is CharsetDetector.Evidence.KoreanOverUtf8 ->
        stringResource(R.string.text_evidence_korean, e.hangulRatio)
    is CharsetDetector.Evidence.Unsupported -> stringResource(R.string.text_evidence_unsupported)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    current: RowHighlighter?,
    onPick: (RowHighlighter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.text_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (h in TextLanguage.userChoices) {
                    DropdownMenuItem(
                        text = { Text(if (h === PlainHighlighter) stringResource(R.string.text_no_highlight) else h.label) },
                        trailingIcon = { if (h === current) Icon(Icons.Filled.Check, null) },
                        onClick = { onPick(h) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoToLineDialog(lineCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val n = value.toIntOrNull()
    val valid = n != null && n >= 1 && n <= lineCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_goto_line)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { s -> value = s.filter { it.isDigit() }.take(10) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.text_goto_range, lineCount)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { n?.let(onGo) }, enabled = valid) {
                Text(stringResource(R.string.text_goto_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.text_cancel)) }
        },
    )
}

