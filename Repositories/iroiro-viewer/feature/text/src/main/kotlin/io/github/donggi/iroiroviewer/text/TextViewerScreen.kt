package io.github.donggi.iroiroviewer.text

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.format.text.PlainHighlighter
import io.github.donggi.iroiroviewer.format.text.RowHighlighter
import io.github.donggi.iroiroviewer.format.text.TextLanguage
import io.github.donggi.iroiroviewer.format.text.TextRow

/**
 * 텍스트·코드 뷰어. **읽기 전용이다.**
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
) {
    val vm: TextViewModel = viewModel()
    LaunchedEffect(path) { vm.open(path) }

    val state by vm.state.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val scrollTo by vm.scrollTo.collectAsStateWithLifecycle()

    var wrap by rememberSaveable { mutableStateOf(false) }
    var showLineNumbers by rememberSaveable { mutableStateOf(true) }
    var fontSize by rememberSaveable { mutableIntStateOf(DEFAULT_FONT_SP) }
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
                            text = (state as? TextViewModel.State.Ready)?.doc?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { searchOpen = true },
                            enabled = state is TextViewModel.State.Ready,
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
                                wrap = wrap,
                                showLineNumbers = showLineNumbers,
                                onDismiss = { menuOpen = false },
                                onWrap = { wrap = !wrap },
                                onLineNumbers = { showLineNumbers = !showLineNumbers },
                                onFontBigger = { fontSize = (fontSize + 1).coerceAtMost(MAX_FONT_SP) },
                                onFontSmaller = { fontSize = (fontSize - 1).coerceAtLeast(MIN_FONT_SP) },
                                onEncoding = { sheet = Sheet.ENCODING },
                                onLanguage = { sheet = Sheet.LANGUAGE },
                                onGoToLine = { goToLine = true },
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
                            LinearProgressIndicator(
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
                        // **바이너리라도 억지로 열 수 있게 둔다.** 우리 판정이 틀릴 수 있고,
                        // 틀렸을 때 사용자에게 길이 없으면 그것이 더 나쁘다.
                        if (s.kind != TextViewModel.State.Failed.Kind.UNREADABLE) {
                            TextButton(onClick = { sheet = Sheet.ENCODING }) {
                                Text(stringResource(R.string.text_open_anyway))
                            }
                        }
                    }
                }

                is TextViewModel.State.Ready -> RowList(
                    doc = s.doc,
                    window = window,
                    listState = listState,
                    hScroll = hScroll,
                    wrap = wrap,
                    showLineNumbers = showLineNumbers,
                    fontSize = fontSize,
                    colors = colors,
                    query = if (searchOpen) search.query else "",
                    ignoreCase = search.ignoreCase,
                    currentHitRow = search.currentRow,
                )
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
) {
    val lineDigits = remember(doc.index.lineCount) {
        maxOf(2, doc.index.lineCount.toString().length)
    }
    val gutterWidth = (lineDigits * (fontSize * 0.62f) + 12f).dp
    val rowHeight = (fontSize * 1.45f).dp

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(doc.index.rowCount) { rowNo ->
            val row = window.rowAt(rowNo)
            Row(Modifier.fillMaxWidth()) {
                if (showLineNumbers) {
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
                if (row == null) {
                    // 아직 안 읽힌 행. 높이만 맞춰 두면 스크롤 막대가 튀지 않는다.
                    Box(Modifier.height(rowHeight).fillMaxWidth())
                } else {
                    val text = remember(row, query, ignoreCase, currentHitRow, rowNo, colors) {
                        annotate(row, colors, query, ignoreCase, rowNo == currentHitRow)
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

/** 조각과 찾은 자리를 하나의 [AnnotatedString] 으로 합친다. */
private fun annotate(
    row: TextRow,
    colors: CodeColors,
    query: String,
    ignoreCase: Boolean,
    isCurrentHit: Boolean,
): AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    append(row.text)
    for (s in row.spans) {
        // 조각이 행 길이를 넘는 일은 없어야 하지만, 넘으면 예외가 나므로 잘라 넣는다.
        val end = minOf(s.end, row.text.length)
        if (s.start >= end) continue
        addStyle(SpanStyle(color = colors.of(s.kind)), s.start, end)
    }
    if (query.isNotEmpty()) {
        var at = row.text.indexOf(query, 0, ignoreCase)
        while (at >= 0) {
            addStyle(
                SpanStyle(background = if (isCurrentHit) colors.currentHit else colors.hit),
                at,
                at + query.length,
            )
            at = row.text.indexOf(query, at + query.length, ignoreCase)
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    search.running -> stringResource(R.string.text_search_running)
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

@Composable
private fun ViewerMenu(
    expanded: Boolean,
    wrap: Boolean,
    showLineNumbers: Boolean,
    onDismiss: () -> Unit,
    onWrap: () -> Unit,
    onLineNumbers: () -> Unit,
    onFontBigger: () -> Unit,
    onFontSmaller: () -> Unit,
    onEncoding: () -> Unit,
    onLanguage: () -> Unit,
    onGoToLine: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CheckItem(stringResource(R.string.text_wrap), wrap) { onWrap() }
        CheckItem(stringResource(R.string.text_line_numbers), showLineNumbers) { onLineNumbers() }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_font_bigger)) },
            onClick = onFontBigger,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_font_smaller)) },
            onClick = onFontSmaller,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_goto_line)) },
            onClick = { onDismiss(); onGoToLine() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_encoding)) },
            onClick = { onDismiss(); onEncoding() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_language)) },
            onClick = { onDismiss(); onLanguage() },
        )
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (checked) Icon(Icons.Filled.Check, null)
        },
        onClick = onClick,
    )
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

private const val DEFAULT_FONT_SP = 13
private const val MIN_FONT_SP = 9
private const val MAX_FONT_SP = 22
