package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.data.ReaderAppearance

/**
 * 문서 안의 '보기' 판 — 글자 크기·여백·바탕.
 *
 * **설정 화면과 같은 값을 쓴다**(`AppPreferences.readerAppearance`). 여기서 바꾸면 열린 문서에 곧바로 걸리고(글자 크기는
 * 다시 읽지 않고, 여백·바탕은 쪽을 다시 읽어서) 다음 문서도 그 모양으로 열린다. 문서마다 따로 기억하지 않는 것은 9단계가
 * '읽기 설정이 책마다 저장된다' 를 한계로 적어 둔 것과 반대 방향의 판단이다 — 글을 읽는 모양은 사람의 것이지 책의 것이
 * 아니다.
 *
 * 보여 주는 손잡이는 문서의 종류가 정한다([ReaderLooks.controlsForEpub]·[ReaderLooks.controlsForFlow]) — 시트에 바탕을
 * 걸면 칸의 채움 색(값의 뜻)이 지워지고, 슬라이드에 글자 크기를 걸면 상자를 넘는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(
    appearance: ReaderAppearance,
    controls: Set<ReaderLooks.Control>,
    onChange: (ReaderAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.doc_appearance_title), style = MaterialTheme.typography.titleMedium)

            if (ReaderLooks.Control.FONT in controls) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.doc_font_size),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = appearance.fontPercent > ReaderAppearance.MIN_FONT_PERCENT,
                        onClick = { onChange(ReaderLooks.smaller(appearance)) },
                    ) { Text(stringResource(R.string.doc_font_smaller)) }
                    Text(
                        stringResource(R.string.doc_font_percent, appearance.fontPercent),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                    OutlinedButton(
                        enabled = appearance.fontPercent < ReaderAppearance.MAX_FONT_PERCENT,
                        onClick = { onChange(ReaderLooks.larger(appearance)) },
                    ) { Text(stringResource(R.string.doc_font_larger)) }
                }
            }

            if (ReaderLooks.Control.MARGIN in controls) {
                Choice(
                    title = stringResource(R.string.doc_margin),
                    options = listOf(
                        ReaderAppearance.MARGIN_NARROW to stringResource(R.string.doc_margin_narrow),
                        ReaderAppearance.MARGIN_NORMAL to stringResource(R.string.doc_margin_normal),
                        ReaderAppearance.MARGIN_WIDE to stringResource(R.string.doc_margin_wide),
                    ),
                    selected = appearance.margin,
                    onSelect = { onChange(appearance.copy(margin = it)) },
                )
            }

            if (ReaderLooks.Control.THEME in controls) {
                Choice(
                    title = stringResource(R.string.doc_theme),
                    options = listOf(
                        ReaderAppearance.THEME_SYSTEM to stringResource(R.string.doc_theme_system),
                        ReaderAppearance.THEME_LIGHT to stringResource(R.string.doc_theme_light),
                        ReaderAppearance.THEME_DARK to stringResource(R.string.doc_theme_dark),
                        ReaderAppearance.THEME_SEPIA to stringResource(R.string.doc_theme_sepia),
                    ),
                    selected = appearance.theme,
                    onSelect = { onChange(appearance.copy(theme = it)) },
                )
            }

            Text(
                stringResource(R.string.doc_appearance_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 하나를 고르는 줄. 좁은 화면에서 넘치면 다음 줄로 내린다. */
@Composable
private fun Choice(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in options) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
