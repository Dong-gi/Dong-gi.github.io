package io.github.donggi.iroiroviewer.image

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import io.github.donggi.iroiroviewer.ui.image.DetailLayer
import io.github.donggi.iroiroviewer.ui.image.ImageIo
import io.github.donggi.iroiroviewer.ui.image.RegionMath
import io.github.donggi.iroiroviewer.ui.image.RegionOrientation
import io.github.donggi.iroiroviewer.ui.image.rememberRasterDetail

/**
 * '그리기·메모' 화면 — 그림 위에 손으로 그리고 글 메모를 달아 **원본 곁의 새 파일**로 저장한다(사용자 요청 'pdf가 아니라,
 * 이미지 파일에 대해 할 수 있게'). 구글 포토의 '마크업', 삼성 갤러리의 '그리기' 의 관행을 따른다.
 *
 * ## 도구
 *
 * | 도구 | 한 손가락 | 까닭 |
 * |---|---|---|
 * | 펜 | 긋는 대로 — 손글씨 | 빛깔·굵기 세 단계 |
 * | 형광펜 | 반투명·굵게(기본 노랑) | **한 획을 한 경로로 한 번에 그린다** — 제 위를 겹쳐 지나가도 진해지지 않는다(`MarkupPainter`) |
 * | 직선 | 끌어서 곧은 선(밑줄). 가로·세로에서 6° 안이면 붙는다 | 손으로 그은 밑줄은 조금씩 기운다(`MarkupMath.snapLine`) |
 * | 글 | 빈 곳을 누르면 메모 칸, 메모를 누르면 고치기, 끌면 옮기기 | 칸은 대화상자다 — 자판·화면 낭독기가 제 일을 하고, 확대한 그림 위에 입력 칸을 띄우는 셈이 없다 |
 * | 지우개 | 닿은 획·메모를 **통째로** 지운다 | 화소 지우개가 아니다 — 지울 것이 '그은 것' 이지 그림이 아니다 |
 *
 * **두 손가락은 언제나 확대·이동이다**(도구와 상관없이). 그리던 획은 두 번째 손가락이 닿는 순간 버린다 — 확대하려던 손이 남긴 점·선이
 * 남지 않게. 두드려 확대하는 몸짓은 없다(펜으로 찍은 점과 겹친다).
 *
 * ## 확대한 채 그린 것의 굵기
 *
 * 굵기·글자 크기는 **화면에 보이는 크기**를 그림 좌표로 옮겨 든다(`MarkupView.toImage`). 네 배로 확대해 '보통' 펜으로 쓴 글씨는 그림에서
 * 네 배 가늘다 — 그린 순간에 본 그대로 남는다(관행). 화면의 변환은 뷰어의 확대·이동([ZoomState])과 같고, 확대하면 원본에서 보이는
 * 자리를 다시 떠 얹는다(뷰어와 같은 `rememberRasterDetail`) — 작은 글씨를 쓰려 확대한 자리가 흐리지 않게.
 *
 * ## 화면을 덮는 층
 *
 * 잘라 저장의 [CropScreen] 과 같은 꼴이다 — 뷰어를 통째로 덮고, 아래 깔린 페이저에 손가락이 닿지 않게 빈 인식기를 단다(의미를 합치는
 * `clickable` 은 쓰지 않는다 — 함정 표). 뒤의 뷰어는 뷰어가 화면 낭독기에서 감춘다.
 *
 * ## 상태
 *
 * 그린 것은 [MarkupViewModel] 이 든다 — 저장 상태 번들에 싣지 않는다(크기). 밤 모드처럼 화면이 다시 서도 남고, 프로세스가 죽으면 앱이
 * 화면에서 사라질 때 적어 둔 초안에서 되살린다(`MarkupDraft`). 손가락이 그리는 중인 획·지우개가 지나가는 것·끄는 메모는 이 화면의
 * 것이다(놓는 순간 ViewModel 로 간다).
 */
@Composable
internal fun MarkupScreen(
    vm: MarkupViewModel,
    session: MarkupSession,
    budget: ImageLimits.Budget,
    onSave: (MarkupOverlay) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style by vm.style.collectAsStateWithLifecycle()
    val noteEdit by vm.noteEdit.collectAsStateWithLifecycle()
    val marks = session.history.marks
    var askExit by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    // 그린 것이 없으면 묻지 않고 닫는다 — 버릴 것이 없다.
    val requestClose: () -> Unit = { if (marks.isEmpty()) onClose() else askExit = true }
    BackHandler { requestClose() }
    // 앱이 화면에서 사라질 때 초안을 적는다 — 시스템은 그 뒤에만 프로세스를 거둔다(`MarkupViewModel.writeDraft`).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.writeDraft() }

    val density = LocalDensity.current
    val metrics = remember(density) { InputMetrics.of(density) }
    val painter = remember { MarkupPainter() }
    val zoom = remember(session.path) { ZoomState() }
    val live = remember { LiveStroke() }
    val input = remember(vm, painter, zoom, live) { MarkupInput(vm, painter, zoom, live) }
    SideEffect {
        input.style = style
        input.marks = marks
        input.aspect = session.aspect
        input.metrics = metrics
    }
    // 놓은 것이 그린 것에 들어온 뒤에야 손가락의 것을 걷는다 — 걷는 프레임과 들어오는 프레임이 갈리면 한 프레임 비어 깜빡인다.
    LaunchedEffect(marks) { input.settle(marks) }

    // 바닥층 — 뷰어와 같은 디코더로 화면에 맞게 뜬다(보이는 방향이 뷰어와 같다).
    val window = LocalWindowInfo.current.containerSize
    val targetLongest = maxOf(window.width, window.height)
    val preview by produceState<Bitmap?>(null, session.path, targetLongest) {
        value = ImageIo.decodeFitted(session.path, targetLongest)
    }
    val previewImage = remember(preview) { preview?.asImageBitmap() }
    // 확대하면 원본에서 보이는 자리를 다시 떠 얹는다 — 뷰어의 한 장(`ImagePage`)과 같은 길이다.
    val probe = session.probe
    val orientation = remember(session.path, probe) { RegionOrientation(session.path, probe) }
    val detail = rememberRasterDetail(
        key = session.path,
        state = zoom,
        baseWidth = preview?.width ?: 0,
        originalWidth = probe.displayWidth,
        originalHeight = probe.displayHeight,
        capBytes = budget.detailCap,
    ) { want ->
        val o = orientation.tileOrientation() ?: return@rememberRasterDetail null
        val stored = RegionMath.toStored(
            o, probe.storedWidth, probe.storedHeight,
            intArrayOf(want.left, want.top, want.right, want.bottom),
        )
        ImageIo.decodeRegionOriented(session.path, stored, want.sample, o)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // 아래 깔린 뷰어(페이저·막대)에 손가락이 닿지 않게 한다 — 포인터 노드가 없으면 히트 테스트가 형제를 지나 아래로 간다
            // (함정 표). 의미를 합치는 `clickable` 대신 빈 인식기를 단다 — 이 화면의 단추들이 낭독기에 따로 읽혀야 한다.
            .pointerInput(Unit) {}
            .safeDrawingPadding(),
    ) {
        TopBar(
            canUndo = session.history.canUndo,
            canRedo = session.history.canRedo,
            canSave = marks.isNotEmpty(),
            onClose = requestClose,
            onUndo = vm::undo,
            onRedo = vm::redo,
            onSave = { onSave(MarkupOverlay(marks)) },
        )

        DrawingArea(
            vm = vm,
            input = input,
            zoom = zoom,
            painter = painter,
            live = live,
            marks = marks,
            imageWidth = session.width,
            imageHeight = session.height,
            preview = previewImage,
            detail = detail,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Text(
            text = stringResource(hintOf(style.tool)),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        )
        ToolRow(selected = style.tool, onSelect = vm::setTool)
        OptionsRow(
            style = style,
            canClear = marks.isNotEmpty(),
            onLevel = { vm.setLevel(style.tool, it) },
            onBoxed = vm::setBoxed,
            onClear = { askClear = true },
        )
        // 빛깔 줄 — 지우개에는 빛깔이 없지만 줄의 높이는 남긴다(도구를 바꿀 때마다 그리는 자리가 늘었다 줄면 그림이 튄다).
        Box(Modifier.fillMaxWidth().height(OPTION_ROW_HEIGHT), contentAlignment = Alignment.Center) {
            if (style.tool != MarkupTool.ERASER) {
                ColorPicker(
                    selected = style.colorOf(style.tool),
                    ring = Color.White,
                    onPick = { vm.setColor(style.tool, it) },
                )
            }
        }
    }

    noteEdit?.let { edit ->
        NoteDialog(
            edit = edit,
            onChange = vm::updateNote,
            onConfirm = {
                commitNote(vm, input, painter, metrics, session.aspect, edit)
            },
            onDelete = edit.id?.let { id ->
                {
                    vm.remove(setOf(id))
                    vm.closeNote()
                }
            },
            onDismiss = { vm.closeNote() },
        )
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.image_markup_clear_title)) },
            text = { Text(stringResource(R.string.image_markup_clear_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askClear = false
                        vm.clear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.image_markup_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.image_markup_cancel)) }
            },
        )
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text(stringResource(R.string.image_markup_exit_title)) },
            text = { Text(stringResource(R.string.image_markup_exit_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    onSave(MarkupOverlay(marks))
                }) { Text(stringResource(R.string.image_markup_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            askExit = false
                            onClose()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.image_markup_discard)) }
                    TextButton(onClick = { askExit = false }) { Text(stringResource(R.string.image_markup_cancel)) }
                }
            },
        )
    }
}

/** 메모 칸의 확인 — 글자 크기를 그림 좌표로 옮기고, 상자가 그림 안에 들게 자리를 맞춰 그린 것에 넣는다. */
private fun commitNote(
    vm: MarkupViewModel,
    input: MarkupInput,
    painter: MarkupPainter,
    metrics: InputMetrics,
    aspect: Float,
    edit: NoteEdit,
) {
    val text = edit.text.trimEnd()
    if (text.isEmpty()) return
    val view = input.view()
    // 고른 단계는 **지금 보이는 크기**로 옮긴다(확대한 채 달면 그림에서 그만큼 작다 — 굵기와 같은 규칙). 단계를 고르지 않았으면 그
    // 메모의 크기 그대로다.
    val size = when {
        edit.level != null && view != null -> view.toImage(metrics.textSizes[edit.level.coerceIn(0, metrics.textSizes.lastIndex)])
        edit.size != null -> edit.size
        else -> FALLBACK_NOTE_SIZE
    }
    val base = Note(edit.id ?: vm.newId(), edit.x, edit.y, text, edit.color, size, edit.boxed)
    val box = painter.noteSize(base)
    val at = MarkupMath.clampBox(base.x, base.y, box[0], box[1], aspect)
    val note = base.copy(x = at[0], y = at[1])
    if (edit.id == null) vm.add(note) else vm.replace(note)
    vm.closeNote(keepStyle = edit)
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canSave: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = DISABLED_ALPHA),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onClose, colors = iconColors) {
            Icon(Icons.Filled.Close, stringResource(R.string.image_markup_close))
        }
        Text(
            text = stringResource(R.string.image_markup_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUndo, enabled = canUndo, colors = iconColors) {
            Icon(UndoIcon, stringResource(R.string.image_markup_undo))
        }
        IconButton(onClick = onRedo, enabled = canRedo, colors = iconColors) {
            Icon(RedoIcon, stringResource(R.string.image_markup_redo))
        }
        TextButton(onClick = onSave, enabled = canSave) {
            Text(
                text = stringResource(R.string.image_markup_save),
                color = if (canSave) MaterialTheme.colorScheme.inversePrimary else Color.White.copy(alpha = DISABLED_ALPHA),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 그리는 자리 — 바닥층(+ 확대 조각)과 그린 것을 한 층에, 손가락이 그리는 중인 것을 다른 층에 그린다. 손가락이 움직일 때마다 다시
 * 그리는 것은 뒤의 층뿐이다(그린 것이 수백 개여도 그 층의 기록은 그대로 쓴다).
 */
@Composable
private fun DrawingArea(
    vm: MarkupViewModel,
    input: MarkupInput,
    zoom: ZoomState,
    painter: MarkupPainter,
    live: LiveStroke,
    marks: List<Mark>,
    imageWidth: Int,
    imageHeight: Int,
    preview: ImageBitmap?,
    detail: DetailLayer?,
    modifier: Modifier = Modifier,
) {
    val areaDescription = stringResource(R.string.image_markup_area)
    val strokeCount = marks.count { it is Stroke }
    val notes = marks.filterIsInstance<Note>()
    val counts = stringResource(R.string.image_markup_area_state, strokeCount, notes.size)
    val addNoteLabel = stringResource(R.string.image_markup_a11y_add_note)
    // 손가락으로 긋는 일은 화면 낭독기로 할 수 없다 — 메모만은 낭독기로도 달고 고칠 수 있게 동작을 단다(마지막에 단 것부터 몇 개).
    val noteActions = notes.takeLast(MAX_A11Y_NOTES).map { n ->
        n to stringResource(R.string.image_markup_a11y_edit_note, noteLabelOf(n.text))
    }

    // 그리는 자리의 크기. 그림(변환)이 바뀌어도 크기가 그대로면 `onSizeChanged` 가 오지 않는다 — 구성마다 한 번 더 알린다(같은 값이면
    // `ZoomState.onLayout` 이 곧바로 돌아온다. `ZoomableImage` 와 같은 까닭).
    var areaSize by remember { mutableStateOf(Size.Zero) }
    SideEffect { if (areaSize.width > 0f) zoom.onLayout(areaSize, imageWidth, imageHeight, imageWidth) }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged {
                areaSize = Size(it.width.toFloat(), it.height.toFloat())
                zoom.onLayout(areaSize, imageWidth, imageHeight, imageWidth)
            }
            .semantics {
                contentDescription = areaDescription
                stateDescription = counts
                customActions = buildList {
                    add(CustomAccessibilityAction(addNoteLabel) { input.startNoteAtCenter() })
                    for ((n, label) in noteActions) {
                        add(
                            CustomAccessibilityAction(label) {
                                vm.editNote(n)
                                true
                            },
                        )
                    }
                }
            }
            .pointerInput(input) { markupGestures(input) },
    ) {
        // 그린 것 — 바닥층·확대 조각과 함께. 지우개가 지나가는 것과 끄는 메모는 빼고 그린다.
        Canvas(Modifier.fillMaxSize()) {
            withImage(zoom) { fw, fh ->
                if (preview != null) {
                    scale(fw / preview.width, fh / preview.height, pivot = Offset.Zero) { drawImage(preview) }
                }
                if (detail != null && detail.image.width > 0 && detail.image.height > 0) {
                    translate(detail.left * fw, detail.top * fh) {
                        scale(detail.width * fw / detail.image.width, detail.height * fh / detail.image.height, pivot = Offset.Zero) {
                            drawImage(detail.image)
                        }
                    }
                }
                val moving = input.moving
                val skip = if (moving == null) input.hidden else input.hidden + moving.id
                // 그림 밖은 잘라 그린다 — 저장한 그림에 없는 것을 화면에 보이지 않는다.
                clipRect(0f, 0f, fw, fh) {
                    drawIntoCanvas { painter.draw(it.nativeCanvas, marks, fw, skip) }
                }
            }
        }
        // 손가락이 그리는 중인 것 — 긋는 획, 끄는 메모, 지우개의 동그라미.
        Canvas(Modifier.fillMaxSize()) {
            // 버전을 읽어 두어야 점이 늘 때 이 층이 다시 그려진다.
            live.version
            val moving = input.moving
            withImage(zoom) { fw, fh ->
                clipRect(0f, 0f, fw, fh) {
                    drawIntoCanvas { c ->
                        if (live.count > 0) {
                            painter.drawLive(c.nativeCanvas, live.kind, live.color, live.width, live.points, live.count, fw)
                        }
                        if (moving != null) {
                            (marks.firstOrNull { it.id == moving.id } as? Note)?.let { n ->
                                painter.drawNoteMoved(c.nativeCanvas, n, moving.x, moving.y, fw)
                            }
                        }
                    }
                }
            }
            input.eraserAt?.let { at ->
                drawCircle(Color.White.copy(alpha = 0.85f), radius = input.metrics.eraserRadius, center = at, style = DrawStroke(1.5.dp.toPx()))
            }
        }
        if (preview == null) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

/** 낭독기 동작의 메모 이름 — 첫 줄의 앞 몇 글자. */
private fun noteLabelOf(text: String): String {
    val line = text.lineSequence().firstOrNull().orEmpty()
    return if (line.length > A11Y_NOTE_CHARS) line.take(A11Y_NOTE_CHARS) + "…" else line
}

/**
 * 그림을 놓는 변환 — 맞춤 크기로 가운데 두고, 뷰포트 가운데를 중심으로 키우고, 민다. 뷰어의 `ZoomableImage`(`drawFitted`)와 같은 식이고
 * 손가락의 셈([MarkupView])이 이것의 역이다. [block] 의 원점이 그림의 왼쪽 위다(맞춤 크기 `fw`×`fh`).
 */
private inline fun DrawScope.withImage(zoom: ZoomState, block: DrawScope.(fw: Float, fh: Float) -> Unit) {
    val fw = zoom.fitted.width
    val fh = zoom.fitted.height
    if (fw <= 0f || fh <= 0f) return
    translate(zoom.offset.x, zoom.offset.y) {
        scale(zoom.scale, pivot = center) {
            translate((size.width - fw) / 2f, (size.height - fh) / 2f) { block(fw, fh) }
        }
    }
}

/** 낭독기 동작에 올리는 메모의 수와 이름의 길이. */
private const val MAX_A11Y_NOTES = 20
private const val A11Y_NOTE_CHARS = 20

/** 재지 않은 채 단 메모의 크기(그림 폭의 몫) — 올 일이 없다(칸은 잰 뒤에만 열린다). */
private const val FALLBACK_NOTE_SIZE = 0.05f
