package io.github.donggi.iroiroviewer.text

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileOpText
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.ui.IroProgress
import io.github.donggi.iroiroviewer.ui.icon.RedoIcon
import io.github.donggi.iroiroviewer.ui.icon.UndoIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 글·코드 파일 **편집기**(사용자 요청 '글·코드 파일 편집과 새 글 파일 만들기'). 텍스트 뷰어의 '편집' 이나 폴더 화면의
 * '새 글 파일' 이 연다.
 *
 * ## 차용한 관행
 *
 * | 무엇 | 어떻게 | 왜 |
 * |---|---|---|
 * | 저장 | 위 막대의 '저장'(글 단추). 고친 것이 없으면 흐리다 | 저장이 이 화면의 일이다 — 메뉴에 숨기지 않는다. 플로피 아이콘은 코어 세트에 없고, 체크 표시는 '편집 끝' 으로 읽힌다 |
 * | 되돌리기·다시 하기 | 위 막대의 아이콘 둘 | 모바일 편집기들의 자리다. 하드웨어 자판의 Ctrl+Z·Ctrl+Shift+Z 는 글 칸이 스스로 받는다 |
 * | 찾기·바꾸기 | ⋮ → 막대 아래에 두 줄(찾을 말·바꿀 말). 이전·다음·바꾸기·모두 바꾸기, 대소문자 구분 | 결과 목록을 띄우면 본문을 잃는다(뷰어와 같은 판단). 찾은 것은 골라서 보인다 |
 * | 닫기 | 고친 것이 있으면 '저장 / 저장하지 않음 / 취소' | 말없이 버리지도, 말없이 저장하지도 않는다 |
 * | 꼴 | 제목 아래 한 줄 — 인코딩 · 줄 끝(· BOM) | 무엇으로 저장되는지 늘 보인다(뷰어의 아래 막대와 같은 판단) |
 *
 * 글자 크기·줄 접기·줄 번호는 텍스트 뷰어의 그 갈래 설정(글 파일/코드 파일)을 따르고, ⋮ 에서 바꾸면 뷰어에도 남는다. 글꼴은 뷰어처럼
 * 고정폭이다. 코드 파일은 자판의 자동 고침을 끈다(따옴표·괄호를 바꿔 놓는다).
 *
 * ## 줄을 접지 않을 때
 *
 * 글 칸을 가로로 미는 상자 안에 둔다 — 칸은 가로 제약이 없어 줄을 접지 않고, 커서가 오른쪽 밖으로 가면 칸이 스스로 '보이게 해 달라'
 * 를 위로 올려 상자가 따라 민다(compose-foundation 1.12.1 의 `TextFieldCoreModifier` 가 커서 자리를 `bringIntoView` 한다).
 *
 * ## 줄 번호
 *
 * 글 칸 왼쪽에 따로 그린다 — 칸의 배치 결과(`onTextLayout`)와 세로 스크롤을 읽어 **보이는 줄만** 그린다. 접은 줄의 이어지는 행은
 * 비운다(뷰어·`less -N` 과 같다). 번호의 셈은 배치가 바뀔 때 한 번이다(글 길이에 비례 — 배치 자체보다 훨씬 싸다).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TextEditScreen(
    path: String,
    /** 뷰어에서 손으로 고른 인코딩(`TextEncoding.name`). null 이면 판정한다. */
    encoding: String?,
    /** 공용 스낵바 — Root 가 그릴 수 있는 모든 화면은 이것에 붙은 [SnackbarHost] 를 하나씩 가진다(텍스트 뷰어의 같은 인자). */
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: TextEditViewModel = viewModel {
        TextEditViewModel(context.applicationContext as Application, AppPreferencesTextStore(context.applicationContext))
    }
    // **편집 세션의 표.** 회전·프로세스 재생성에는 같은 값이고, 화면을 떠났다 돌아오면 새 값이다 — 뷰모델이 이것으로 '같은 세션을 이어
    // 간다'(초안을 묻지 않고 잇는다)와 '새로 열었다' 를 가른다.
    val session = rememberSaveable { System.nanoTime() }
    LaunchedEffect(path, encoding, session) { vm.open(path, encoding, session) }
    val activity = LocalActivity.current
    DisposableEffect(vm) {
        onDispose { if (leavesScreen(activity?.isChangingConfigurations)) vm.onScreenLeft() }
    }
    // 앱이 뒤로 가면 곧바로 초안을 적는다 — 그 뒤에는 언제든 프로세스가 죽을 수 있다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushDraft() }

    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val question by vm.question.collectAsStateWithLifecycle()
    val find by vm.find.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    val running by FileOpManager.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    val latestClose by rememberUpdatedState(onClose)
    val close = { if (vm.requestClose()) latestClose() }
    BackHandler {
        if (find.open) vm.closeFind() else close()
    }

    // 사건 — 앞에 있을 때만 꺼낸다(가려진 화면이 꺼내면 보이지 않는 스낵바에 떴다 사라진다).
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                if (event == TextEditViewModel.Event.Close) {
                    latestClose()
                } else {
                    val message = eventMessage(context, resources, event)
                    scope.launch { snackbar.showSnackbar(message) }
                }
            }
        }
    }

    val ready = screen as? TextEditViewModel.Screen.Ready
    Scaffold(
        // 자판이 올라오면 화면 전체(알림 줄 포함)가 그 위로 줄어든다 — 바깥 Scaffold 가 시스템 바를 소비했으므로 자판의 몫만 더해진다.
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.text_edit_close))
                    }
                },
                title = {
                    Column {
                        Text(
                            text = ready?.name ?: TextTitle.fileNameOf(path),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        if (ready != null) {
                            Text(
                                text = statusLine(ready.format, dirty),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    if (ready != null) {
                        val undo = ready.state.undoState
                        IconButton(onClick = { undo.undo() }, enabled = undo.canUndo) {
                            Icon(UndoIcon, stringResource(R.string.text_edit_undo))
                        }
                        IconButton(onClick = { undo.redo() }, enabled = undo.canRedo) {
                            Icon(RedoIcon, stringResource(R.string.text_edit_redo))
                        }
                        TextButton(onClick = { vm.save() }, enabled = dirty && saving == null && question == null) {
                            Text(stringResource(R.string.text_edit_save))
                        }
                        // ⋮ 단추와 메뉴를 한 상자에 담는다(Popup 의 앵커 — 함정 표).
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.text_more))
                            }
                            EditMenu(
                                expanded = menuOpen,
                                settings = settings,
                                onDismiss = { menuOpen = false },
                                onFind = { menuOpen = false; vm.openFind() },
                                onWrap = { vm.updateSettings { it.copy(wrap = !it.wrap) } },
                                onLineNumbers = { vm.updateSettings { it.copy(lineNumbers = !it.lineNumbers) } },
                                onFontBigger = { vm.updateSettings { it.copy(fontSp = it.fontSp + 1) } },
                                onFontSmaller = { vm.updateSettings { it.copy(fontSp = it.fontSp - 1) } },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            val current = saving
            if (current != null && question == null) {
                // 큐가 다른 작업(목록이 시킨 큰 복사)을 돌리는 동안은 그 뒤에 줄 서 있다 — 왜 끝나지 않는지 말한다.
                val waiting = current.id != null && (running as? FileOpManager.State.Running)?.id.let { it != null && it != current.id }
                SaveProgress(waiting)
            }
            if (find.open && ready != null) {
                FindPanel(
                    find = find,
                    onQuery = vm::setQuery,
                    onReplacement = vm::setReplacement,
                    onToggleCase = vm::toggleIgnoreCase,
                    onPrevious = vm::findPrevious,
                    onNext = vm::findNext,
                    onReplace = vm::replaceOne,
                    onReplaceAll = vm::replaceAll,
                    onClose = vm::closeFind,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = screen) {
                    TextEditViewModel.Screen.Loading -> CenteredBox { CircularProgressIndicator() }
                    is TextEditViewModel.Screen.Refused -> RefusedMessage(s, onClose = latestClose)
                    is TextEditViewModel.Screen.Ready -> {
                        val shown = settings
                        if (shown == null) {
                            // 저장된 설정을 읽는 중이다(한두 프레임). 기본값으로 한 번 그렸다가 바뀌지 않게 기다린다.
                            CenteredBox { CircularProgressIndicator() }
                        } else {
                            EditorArea(
                                state = s.state,
                                kind = s.kind,
                                settings = shown,
                                lines = lines,
                                reveal = reveal,
                                onRevealed = vm::consumeReveal,
                                onSave = { if (vm.dirty.value) vm.save() },
                            )
                        }
                    }
                }
            }
        }
    }

    question?.let { q -> QuestionDialog(q, vm) }
}

// ---- 본문 -----------------------------------------------------------------------------------

/**
 * 글 칸의 배치 결과를 읽는 길 — 줄 번호와 '이 자리로 옮기기' 가 쓴다. 칸 하나에 처음 한 번만 적는다 — 그 뒤로 오는 길도 같은 칸의
 * 배치 상태를 읽는다(배치가 바뀌면 그것을 읽는 그리기가 다시 돈다). 칸이 새로 서면 이것도 새로 만든다.
 */
private class LayoutHolder {
    var getter: (() -> TextLayoutResult?)? by mutableStateOf(null)
}

/** 글 칸 안쪽 여백. 줄 번호가 같은 값으로 자리를 맞춘다. */
private val EDITOR_PAD_HORIZONTAL = 8.dp
private val EDITOR_PAD_VERTICAL = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorArea(
    state: TextFieldState,
    kind: TextViewerKind,
    settings: TextViewerDefaults,
    lines: Int,
    reveal: Int?,
    onRevealed: () -> Unit,
    onSave: () -> Unit,
) {
    val fontSp = settings.fontSp
    val colors = MaterialTheme.colorScheme
    val style = remember(fontSp, colors.onSurface) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSp.sp,
            lineHeight = (fontSp * 1.45f).sp,
            color = colors.onSurface,
        )
    }
    // 세로 스크롤은 글 칸의 것이다 — 우리가 들고 넘겨야 줄 번호가 같은 값을 읽는다.
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val wrap = settings.wrap
    // 칸이 새로 서면(줄 접기를 바꿨다·다른 세션) 그 칸의 배치를 새로 읽는다 — 옛 칸의 길을 쥐면 줄 번호가 옛 배치를 그린다.
    val layout = remember(wrap, state) { LayoutHolder() }
    val focus = remember { FocusRequester() }
    val latestSave by rememberUpdatedState(onSave)

    // 빈 파일(방금 만든 새 글 파일)이면 곧바로 친다 — 자판을 올린다. 글이 있으면 사용자가 고칠 자리를 누른다.
    LaunchedEffect(state) {
        if (state.text.isEmpty()) focus.requestFocus()
    }

    Row(Modifier.fillMaxSize()) {
        if (settings.lineNumbers) {
            LineGutter(layout = layout, scroll = scroll, lines = lines, fontSp = fontSp, color = colors.outline)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewportWidth = maxWidth
            val field: @Composable (Modifier) -> Unit = { m ->
                BasicTextField(
                    state = state,
                    modifier = m
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            // 하드웨어 자판의 Ctrl+S — 데스크톱 편집기의 관행.
                            if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.S) {
                                latestSave()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = style,
                    inputTransformation = LineFeedOnly,
                    keyboardOptions = if (kind == TextViewerKind.CODE) CODE_KEYBOARD else KeyboardOptions.Default,
                    cursorBrush = SolidColor(colors.primary),
                    onTextLayout = { getResult -> if (layout.getter == null) layout.getter = getResult },
                    scrollState = scroll,
                    decorator = { inner ->
                        Box(Modifier.padding(horizontal = EDITOR_PAD_HORIZONTAL, vertical = EDITOR_PAD_VERTICAL)) { inner() }
                    },
                )
            }
            if (wrap) {
                field(Modifier.fillMaxSize())
            } else {
                // 줄을 접지 않는다 — 가로로 미는 상자 안의 칸은 가로 제약이 없다(머리말).
                Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                    field(Modifier.fillMaxHeight().widthIn(min = viewportWidth))
                }
            }
        }
    }

    val density = LocalDensity.current
    LaunchedEffect(reveal) {
        val offset = reveal ?: return@LaunchedEffect
        // 고친 뒤의 배치를 기다린다 — 바꾸기 뒤의 새 자리는 다음 장면에야 있다.
        withFrameNanos { }
        val result = layout.getter?.invoke()
        if (result != null) {
            val at = offset.coerceIn(0, result.layoutInput.text.length)
            val line = result.getLineForOffset(at)
            val top = result.getLineTop(line)
            val bottom = result.getLineBottom(line)
            val viewport = scroll.viewportSize
            if (viewport > 0 && (top < scroll.value || bottom > scroll.value + viewport)) {
                // 찾은 줄을 화면 위쪽 1/3 쯤에 둔다 — 그 아래의 맥락이 보인다.
                scroll.animateScrollTo((top - viewport / 3f).roundToInt().coerceAtLeast(0))
            }
            if (!wrap) {
                val padStart = with(density) { EDITOR_PAD_HORIZONTAL.toPx() }
                val x = padStart + result.getHorizontalPosition(at, usePrimaryDirection = true)
                val width = hScroll.viewportSize
                if (width > 0 && (x < hScroll.value || x > hScroll.value + width * 0.9f)) {
                    hScroll.animateScrollTo((x - width / 3f).roundToInt().coerceAtLeast(0))
                }
            }
        }
        onRevealed()
    }
}

/** 코드 파일의 자판 — 자동 고침을 끈다(따옴표·괄호·낱말을 바꿔 놓는다). */
private val CODE_KEYBOARD = KeyboardOptions(autoCorrectEnabled = false)

/**
 * 편집기의 줄 끝은 `\n` 하나다([LineEndings]). 붙여 넣은 글에 `\r\n`·`\r` 이 있으면 `\n` 으로 바꾼다 — 남기면 보이지 않는 글자가
 * 생기고, 저장할 때 CRLF 파일에서 `\r\r\n` 이 된다. **바뀐 자리만** 본다(글 전체를 훑으면 글자 하나마다 파일 크기만큼 돈다).
 * 뒤의 바뀐 자리부터 고친다 — 앞의 자리가 밀리지 않는다.
 */
@OptIn(ExperimentalFoundationApi::class)
private object LineFeedOnly : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val ranges = (0 until changes.changeCount).map { changes.getRange(it) }
        for (range in ranges.asReversed()) {
            var i = minOf(range.max, length) - 1
            while (i >= range.min) {
                if (charAt(i) == '\r') {
                    if (i + 1 < length && charAt(i + 1) == '\n') replace(i, i + 1, "") else replace(i, i + 1, "\n")
                }
                i--
            }
        }
    }
}

/**
 * 줄 번호 — 글 칸의 배치와 세로 스크롤을 읽어 **보이는 줄만** 그린다. 화면 낭독기에는 내놓지 않는다(글 칸이 줄을 읽는다).
 */
@Composable
private fun LineGutter(layout: LayoutHolder, scroll: ScrollState, lines: Int, fontSp: Int, color: Color) {
    // 칸의 폭은 뷰어의 셈과 같다(자릿수 × 글자폭 + 여백).
    val digits = maxOf(2, lines.toString().length)
    val width = (digits * (fontSp * 0.62f) + 12f).dp
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val style = remember(fontSp, color) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSp.sp, color = color, textAlign = TextAlign.End)
    }
    val numbers = remember { GutterNumbers() }
    Canvas(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clipToBounds()
            .clearAndSetSemantics { },
    ) {
        val result = layout.getter?.invoke() ?: return@Canvas
        if (result.lineCount == 0) return@Canvas
        val padTop = EDITOR_PAD_VERTICAL.toPx()
        val padEnd = 6.dp.toPx()
        val y0 = scroll.value.toFloat()
        val visible = (size.height - 2 * padTop).coerceAtLeast(0f)
        val first = result.getLineForVerticalPosition(y0)
        val last = result.getLineForVerticalPosition(y0 + visible)
        val map = numbers.of(result)
        // 글 칸이 안쪽 여백 밖을 자르는 것과 같은 자리를 자른다 — 번호만 여백에 걸쳐 보이지 않게.
        clipRect(top = padTop, bottom = size.height - padTop) {
            for (v in first..last) {
                val n = map.getOrElse(v) { 0 }
                if (n == 0) continue
                val label = measurer.measure(n.toString(), style)
                val baseline = padTop + result.getLineBaseline(v) - y0
                drawText(label, topLeft = Offset(size.width - padEnd - label.size.width, baseline - label.firstBaseline))
            }
        }
    }
}

/**
 * 배치의 행마다 줄 번호(그 행이 줄의 처음이 아니면 0). 배치가 바뀔 때 한 번 센다 — 글을 한 번 훑는다.
 */
private class GutterNumbers {
    private var source: TextLayoutResult? = null
    private var numbers = IntArray(0)

    fun of(result: TextLayoutResult): IntArray {
        if (result === source) return numbers
        val text = result.layoutInput.text.text
        val count = result.lineCount
        val out = IntArray(count)
        var logical = 0
        var pos = 0
        for (v in 0 until count) {
            val start = result.getLineStart(v).coerceIn(0, text.length)
            while (pos < start) {
                if (text[pos] == '\n') logical++
                pos++
            }
            out[v] = if (start == 0 || text[start - 1] == '\n') logical + 1 else 0
        }
        source = result
        numbers = out
        return out
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SaveProgress(waiting: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        IroProgress(progress = null, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(if (waiting) R.string.text_edit_waiting else R.string.text_edit_saving),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** 고칠 수 없다 — 까닭을 말하고 닫는 단추를 둔다. */
@Composable
private fun RefusedMessage(s: TextEditViewModel.Screen.Refused, onClose: () -> Unit) {
    val label = s.encoding?.label.orEmpty()
    val text = when (s.why) {
        TextEditViewModel.Refusal.PLACE -> stringResource(R.string.text_edit_refused_place)
        TextEditViewModel.Refusal.READ_ONLY -> stringResource(R.string.text_edit_refused_read_only)
        TextEditViewModel.Refusal.TOO_LARGE -> stringResource(R.string.text_edit_too_large, Format.size(s.limitBytes))
        TextEditViewModel.Refusal.UNREADABLE -> stringResource(R.string.text_failed_unreadable)
        TextEditViewModel.Refusal.BINARY -> stringResource(R.string.text_failed_binary)
        TextEditViewModel.Refusal.UNSUPPORTED_ENCODING -> stringResource(R.string.text_failed_encoding)
        TextEditViewModel.Refusal.MALFORMED -> stringResource(R.string.text_edit_refused_malformed, label)
        TextEditViewModel.Refusal.NOT_ROUND_TRIP -> stringResource(R.string.text_edit_refused_round_trip, label)
    }
    CenteredBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            TextButton(onClick = onClose) { Text(stringResource(R.string.text_edit_close)) }
        }
    }
}

// ---- 막대·메뉴 ---------------------------------------------------------------------------------

/** 제목 아래 한 줄 — 저장할 때 입힐 꼴과, 저장하지 않은 고침이 있는가. */
@Composable
private fun statusLine(format: TextFormat, dirty: Boolean): String {
    val eol = stringResource(
        when (format.lineEnding) {
            LineEnding.LF -> R.string.text_edit_eol_lf
            LineEnding.CRLF -> R.string.text_edit_eol_crlf
            LineEnding.CR -> R.string.text_edit_eol_cr
        },
    )
    val base = stringResource(R.string.text_edit_status, format.encoding.label, eol)
    val bom = if (format.bom) stringResource(R.string.text_edit_status_bom) else ""
    val unsaved = if (dirty) stringResource(R.string.text_edit_status_dirty) else ""
    return base + bom + unsaved
}

@Composable
private fun EditMenu(
    expanded: Boolean,
    settings: TextViewerDefaults?,
    onDismiss: () -> Unit,
    onFind: () -> Unit,
    onWrap: () -> Unit,
    onLineNumbers: () -> Unit,
    onFontBigger: () -> Unit,
    onFontSmaller: () -> Unit,
) {
    val ready = settings != null
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.text_edit_find)) }, onClick = onFind)
        HorizontalDivider()
        ToggleItem(stringResource(R.string.text_wrap), settings?.wrap == true, ready, onWrap)
        ToggleItem(stringResource(R.string.text_line_numbers), settings?.lineNumbers == true, ready, onLineNumbers)
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
    }
}

/** 켜고 끄는 메뉴 줄 — 화면 낭독기가 '켜짐·꺼짐' 을 읽는다. */
@Composable
private fun ToggleItem(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (checked) R.string.text_edit_on else R.string.text_edit_off)
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, null) },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = state
        },
    )
}

// ---- 찾기·바꾸기 ------------------------------------------------------------------------------

@Composable
private fun FindPanel(
    find: TextEditViewModel.FindState,
    onQuery: (String) -> Unit,
    onReplacement: (String) -> Unit,
    onToggleCase: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )
    val caseState = stringResource(if (find.ignoreCase) R.string.text_edit_off else R.string.text_edit_on)
    val caseLabel = stringResource(R.string.text_edit_case)
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = find.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.text_edit_find_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Text(
                    text = when {
                        find.query.isEmpty() -> ""
                        find.total == 0 -> stringResource(R.string.text_search_none)
                        else -> stringResource(R.string.text_search_count, find.current, find.total)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                IconButton(onClick = onPrevious, enabled = find.total > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.text_edit_find_prev))
                }
                IconButton(onClick = onNext, enabled = find.total > 0) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.text_edit_find_next))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, stringResource(R.string.text_edit_find_close))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = find.replacement,
                    onValueChange = onReplacement,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.text_edit_replace_hint)) },
                    colors = fieldColors,
                    modifier = Modifier.weight(1f),
                )
                // 대소문자 구분 — 켜면 강조색. 화면 낭독기에는 이름과 '켜짐·꺼짐' 을 준다(글자 'Aa' 는 읽어도 뜻이 없어 감춘다).
                IconButton(
                    onClick = onToggleCase,
                    modifier = Modifier.semantics {
                        contentDescription = caseLabel
                        stateDescription = caseState
                        role = Role.Switch
                    },
                ) {
                    Text(
                        text = stringResource(R.string.text_search_case),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (find.ignoreCase) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                TextButton(onClick = onReplace, enabled = find.total > 0) {
                    Text(stringResource(R.string.text_edit_replace))
                }
                TextButton(onClick = onReplaceAll, enabled = find.total > 0) {
                    Text(stringResource(R.string.text_edit_replace_all))
                }
            }
        }
    }
}

// ---- 물음 ---------------------------------------------------------------------------------

/**
 * 묻는 것들. **강조한 단추(확인 자리)는 잃는 것이 없는 쪽**이다 — 닫기의 '저장', 초안의 '이어서 고치기', 저장 흐름의 물음들은
 * 바꾸는 쪽이 사용자가 방금 누른 '저장' 의 뜻이라 확인 자리에 둔다. 바깥을 누르거나 뒤로 가면 취소다 — 초안의 물음만은 답을 받는다
 * (취소하면 초안이 남아 다음에도 묻는다).
 */
@Composable
private fun QuestionDialog(q: TextEditViewModel.Question, vm: TextEditViewModel) {
    when (q) {
        TextEditViewModel.Question.CloseUnsaved -> AlertDialog(
            onDismissRequest = vm::cancelClose,
            title = { Text(stringResource(R.string.text_edit_close_title)) },
            text = { Text(stringResource(R.string.text_edit_close_message)) },
            confirmButton = { Button(onClick = vm::closeSaving) { Text(stringResource(R.string.text_edit_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::closeDiscarding) { Text(stringResource(R.string.text_edit_discard)) }
                    TextButton(onClick = vm::cancelClose) { Text(stringResource(R.string.text_cancel)) }
                }
            },
        )
        is TextEditViewModel.Question.Resume -> AlertDialog(
            // 답을 받는다 — 바깥을 눌러 닫으면 초안이 어정쩡하게 남는다.
            onDismissRequest = { },
            title = { Text(stringResource(R.string.text_edit_resume_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.text_edit_resume_message))
                    if (q.changedSince) {
                        Text(stringResource(R.string.text_edit_resume_changed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { Button(onClick = vm::resumeDraft) { Text(stringResource(R.string.text_edit_resume)) } },
            dismissButton = {
                TextButton(onClick = vm::discardDraft) { Text(stringResource(R.string.text_edit_resume_discard)) }
            },
        )
        is TextEditViewModel.Question.MixedEndings -> {
            val target = stringResource(
                when (q.target) {
                    LineEnding.LF -> R.string.text_edit_eol_lf
                    LineEnding.CRLF -> R.string.text_edit_eol_crlf
                    LineEnding.CR -> R.string.text_edit_eol_cr
                },
            )
            AlertDialog(
                onDismissRequest = vm::cancelSaveQuestion,
                title = { Text(stringResource(R.string.text_edit_mixed_title)) },
                text = {
                    Text(stringResource(R.string.text_edit_mixed_message, q.counts.lf, q.counts.crlf, q.counts.cr, target))
                },
                confirmButton = { Button(onClick = vm::confirmMixedEndings) { Text(stringResource(R.string.text_edit_save)) } },
                dismissButton = { TextButton(onClick = vm::cancelSaveQuestion) { Text(stringResource(R.string.text_cancel)) } },
            )
        }
        is TextEditViewModel.Question.Unmappable -> AlertDialog(
            onDismissRequest = vm::cancelSaveQuestion,
            title = { Text(stringResource(R.string.text_edit_unmappable_title)) },
            text = { Text(stringResource(R.string.text_edit_unmappable_message, q.encoding.label, q.sample)) },
            confirmButton = {
                Button(onClick = vm::convertToUtf8) { Text(stringResource(R.string.text_edit_unmappable_confirm)) }
            },
            dismissButton = { TextButton(onClick = vm::cancelSaveQuestion) { Text(stringResource(R.string.text_cancel)) } },
        )
        TextEditViewModel.Question.ChangedElsewhere -> AlertDialog(
            onDismissRequest = vm::cancelSaveQuestion,
            title = { Text(stringResource(R.string.text_edit_changed_title)) },
            text = { Text(stringResource(R.string.text_edit_changed_message)) },
            // 남의 고침을 지우는 쪽이라 강조하지 않는다 — 기본은 '취소' 다.
            confirmButton = { Button(onClick = vm::cancelSaveQuestion) { Text(stringResource(R.string.text_cancel)) } },
            dismissButton = {
                TextButton(onClick = vm::overwriteChanged) {
                    Text(stringResource(R.string.text_edit_overwrite), color = MaterialTheme.colorScheme.error)
                }
            },
        )
        TextEditViewModel.Question.PreviousNotKept -> AlertDialog(
            onDismissRequest = vm::cancelSaveQuestion,
            title = { Text(stringResource(R.string.text_edit_previous_title)) },
            text = { Text(stringResource(R.string.text_edit_previous_message)) },
            confirmButton = { Button(onClick = vm::cancelSaveQuestion) { Text(stringResource(R.string.text_cancel)) } },
            dismissButton = {
                TextButton(onClick = vm::saveWithoutPrevious) {
                    Text(stringResource(R.string.text_edit_save_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/**
 * 사건의 알림 문장. 결과는 채널로 오므로 컴포지션 밖에서 고른다([LocalResources] — 구성이 바뀌면 따라간다). 저장의 결과는 그것을
 * 만든 `core:io` 의 문장이다(`FileOpText.textMessage` — 앱의 결과 줄과 같다).
 */
private fun eventMessage(
    context: android.content.Context,
    resources: android.content.res.Resources,
    event: TextEditViewModel.Event,
): String = when (event) {
    is TextEditViewModel.Event.Result -> {
        val line = FileOpText.textMessage(context, event.finished).orEmpty()
        if (event.converted) line + resources.getString(R.string.text_edit_converted) else line
    }
    TextEditViewModel.Event.Unencodable -> resources.getString(R.string.text_edit_unencodable)
    TextEditViewModel.Event.ClosingAfterSave -> resources.getString(R.string.text_edit_closing_after_save)
    is TextEditViewModel.Event.Replaced -> resources.getString(R.string.text_edit_replaced, event.count)
    TextEditViewModel.Event.NotFound -> resources.getString(R.string.text_edit_not_found)
    // 닫기는 알림이 아니다 — 부르는 쪽이 먼저 가른다.
    TextEditViewModel.Event.Close -> ""
}
