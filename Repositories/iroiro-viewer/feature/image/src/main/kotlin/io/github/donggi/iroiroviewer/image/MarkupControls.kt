package io.github.donggi.iroiroviewer.image

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 그리는 화면의 **도구 줄과 메모 칸** — 도구 다섯, 굵기·글자 크기·바탕 상자·모두 지우기, 빛깔, 메모를 쓰는 대화상자. 화면은
 * `MarkupScreen`.
 */

/** 도구 다섯 — 아이콘과 이름. 하나만 고른다(낭독기에 '라디오 단추' 로 읽힌다). */
@Composable
internal fun ToolRow(selected: MarkupTool, onSelect: (MarkupTool) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp, vertical = 2.dp)) {
        for (t in MarkupTool.entries) {
            val on = t == selected
            val tint = if (on) Color.White else Color.White.copy(alpha = 0.7f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(t) })
                    .padding(vertical = 6.dp),
            ) {
                Icon(iconOf(t), contentDescription = null, tint = tint)
                Text(
                    text = stringResource(labelOf(t)),
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 도구의 설정 줄 — 굵기(펜·형광펜·직선), 글자 크기와 바탕 상자(글), '모두 지우기'(지우개). 높이는 도구와 상관없이 같다(그리는 자리가
 * 출렁이지 않게).
 */
@Composable
internal fun OptionsRow(
    style: MarkupStyle,
    canClear: Boolean,
    onLevel: (Int) -> Unit,
    onBoxed: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(OPTION_ROW_HEIGHT), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            when (val t = style.tool) {
                MarkupTool.PEN, MarkupTool.HIGHLIGHTER, MarkupTool.LINE -> LevelPicker(
                    selected = style.levelOf(t),
                    labels = WIDTH_LABELS,
                    onPick = onLevel,
                ) { level, on -> WidthDot(level, on) }
                MarkupTool.TEXT -> {
                    LevelPicker(selected = style.levelOf(t), labels = SIZE_LABELS, onPick = onLevel) { level, on -> SizeGlyph(level, on) }
                    BoxChip(boxed = style.boxed, onBoxed = onBoxed, dark = true)
                }
                MarkupTool.ERASER -> TextButton(onClick = onClear, enabled = canClear) {
                    Text(
                        text = stringResource(R.string.image_markup_clear),
                        color = if (canClear) Color.White else Color.White.copy(alpha = DISABLED_ALPHA),
                    )
                }
            }
        }
    }
}

/** 세 단계 고르기 — 굵기나 글자 크기. 단계마다 이름([labels])을 낭독기에 준다. */
@Composable
private fun LevelPicker(
    selected: Int,
    labels: IntArray,
    onPick: (Int) -> Unit,
    glyph: @Composable (level: Int, selected: Boolean) -> Unit,
) {
    Row(Modifier.selectableGroup()) {
        for (level in labels.indices) {
            val on = level == selected
            val name = stringResource(labels[level])
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(SWATCH_TOUCH)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(level) })
                    .semantics { contentDescription = name },
            ) {
                if (on) Box(Modifier.size(SWATCH_RING).border(2.dp, Color.White, CircleShape))
                glyph(level, on)
            }
        }
    }
}

@Composable
private fun WidthDot(level: Int, selected: Boolean) {
    val d = when (level) {
        0 -> 5.dp
        1 -> 9.dp
        else -> 15.dp
    }
    Box(Modifier.size(d).clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = 0.7f)))
}

@Composable
private fun SizeGlyph(level: Int, selected: Boolean) {
    Text(
        text = stringResource(R.string.image_markup_size_glyph),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
        fontSize = when (level) {
            0 -> 12.sp
            1 -> 16.sp
            else -> 21.sp
        },
        fontWeight = FontWeight.Bold,
    )
}

/** 메모의 바탕 상자 켜고 끄기. 낭독기에 켜짐·꺼짐이 읽힌다(고른 칩). */
@Composable
private fun BoxChip(boxed: Boolean, onBoxed: (Boolean) -> Unit, dark: Boolean) {
    FilterChip(
        selected = boxed,
        onClick = { onBoxed(!boxed) },
        label = { Text(stringResource(R.string.image_markup_box)) },
        leadingIcon = if (boxed) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        colors = if (dark) {
            FilterChipDefaults.filterChipColors(labelColor = Color.White)
        } else {
            FilterChipDefaults.filterChipColors()
        },
    )
}

/** 빛깔 고르기 — 고정 팔레트. 빛깔마다 이름을 낭독기에 준다(빛깔만으로 가르지 않는다). [ring] 은 고른 것의 테두리 빛깔이다. */
@Composable
internal fun ColorPicker(selected: Int, ring: Color, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 4.dp)) {
        for (c in MarkupColor.entries) {
            val on = c.argb == selected
            val name = stringResource(c.label)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(SWATCH_TOUCH)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(c.argb) })
                    .semantics { contentDescription = name },
            ) {
                if (on) Box(Modifier.size(SWATCH_RING).border(2.dp, ring, CircleShape))
                Box(
                    Modifier
                        .size(SWATCH)
                        .clip(CircleShape)
                        .background(Color(c.argb))
                        .border(1.dp, Color.Gray, CircleShape),
                )
            }
        }
    }
}

/**
 * 메모 칸 — 글, 크기, 바탕 상자, 빛깔. 대화상자로 띄운다(자판이 칸을 가리지 않고, 화면 낭독기가 제 일을 한다). 글은 ViewModel 이 든다
 * ([NoteEdit] — 저장 상태 번들에 싣지 않는다).
 */
@Composable
internal fun NoteDialog(
    edit: NoteEdit,
    onChange: ((NoteEdit) -> NoteEdit) -> Unit,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (edit.id == null) R.string.image_markup_note_new else R.string.image_markup_note_edit)) },
        text = {
            // 칸은 대화상자의 창에서 구성된다 — 그 안에서 초점을 청해야 붙은 뒤에 청한다(밖에서 청하면 아직 붙지 않은 칸이라 던진다).
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focus.requestFocus()
                keyboard?.show()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = edit.text,
                    // 상한을 넘는 입력은 받지 않는다 — 잘라 받으면 두 글자로 된 문자(이모지)가 반으로 갈릴 수 있다.
                    onValueChange = { s -> if (s.length <= MarkupDraft.MAX_NOTE_CHARS) onChange { it.copy(text = s) } },
                    label = { Text(stringResource(R.string.image_markup_note_label)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (level in SIZE_LABELS.indices) {
                        FilterChip(
                            selected = edit.level == level,
                            onClick = { onChange { it.copy(level = level) } },
                            label = { Text(stringResource(SIZE_LABELS[level])) },
                        )
                    }
                }
                BoxChip(boxed = edit.boxed, onBoxed = { b -> onChange { it.copy(boxed = b) } }, dark = false)
                ColorPicker(
                    selected = edit.color,
                    ring = MaterialTheme.colorScheme.onSurface,
                    onPick = { c -> onChange { it.copy(color = c) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = edit.text.isNotBlank()) {
                Text(stringResource(R.string.image_markup_note_ok))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.image_markup_note_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.image_markup_cancel)) }
            }
        },
    )
}

/** 고정 팔레트 — 표시에 흔히 쓰는 빛깔과 검정·흰색. [label] 은 빛깔의 이름(문자열 자원) — 낭독기가 읽는다. */
internal enum class MarkupColor(val argb: Int, val label: Int) {
    RED(0xFFE53935.toInt(), R.string.image_markup_color_red),
    ORANGE(0xFFFB8C00.toInt(), R.string.image_markup_color_orange),
    YELLOW(0xFFFDD835.toInt(), R.string.image_markup_color_yellow),
    GREEN(0xFF43A047.toInt(), R.string.image_markup_color_green),
    BLUE(0xFF1E88E5.toInt(), R.string.image_markup_color_blue),
    PURPLE(0xFF8E24AA.toInt(), R.string.image_markup_color_purple),
    BLACK(0xFF000000.toInt(), R.string.image_markup_color_black),
    WHITE(0xFFFFFFFF.toInt(), R.string.image_markup_color_white),
}

private fun iconOf(tool: MarkupTool): ImageVector = when (tool) {
    MarkupTool.PEN -> Icons.Filled.Edit
    MarkupTool.HIGHLIGHTER -> HighlighterIcon
    MarkupTool.LINE -> LineIcon
    MarkupTool.TEXT -> TextIcon
    MarkupTool.ERASER -> EraserIcon
}

@StringRes
private fun labelOf(tool: MarkupTool): Int = when (tool) {
    MarkupTool.PEN -> R.string.image_markup_tool_pen
    MarkupTool.HIGHLIGHTER -> R.string.image_markup_tool_highlighter
    MarkupTool.LINE -> R.string.image_markup_tool_line
    MarkupTool.TEXT -> R.string.image_markup_tool_text
    MarkupTool.ERASER -> R.string.image_markup_tool_eraser
}

@StringRes
internal fun hintOf(tool: MarkupTool): Int = when (tool) {
    MarkupTool.PEN, MarkupTool.HIGHLIGHTER -> R.string.image_markup_hint_draw
    MarkupTool.LINE -> R.string.image_markup_hint_line
    MarkupTool.TEXT -> R.string.image_markup_hint_text
    MarkupTool.ERASER -> R.string.image_markup_hint_eraser
}

private val WIDTH_LABELS = intArrayOf(
    R.string.image_markup_width_thin,
    R.string.image_markup_width_medium,
    R.string.image_markup_width_thick,
)
private val SIZE_LABELS = intArrayOf(
    R.string.image_markup_size_small,
    R.string.image_markup_size_medium,
    R.string.image_markup_size_large,
)

/** 설정 줄·빛깔 줄의 높이 — 도구와 상관없이 같다. */
internal val OPTION_ROW_HEIGHT = 48.dp
private val SWATCH_TOUCH = 44.dp
private val SWATCH_RING = 36.dp
private val SWATCH = 26.dp
internal const val DISABLED_ALPHA = 0.38f
