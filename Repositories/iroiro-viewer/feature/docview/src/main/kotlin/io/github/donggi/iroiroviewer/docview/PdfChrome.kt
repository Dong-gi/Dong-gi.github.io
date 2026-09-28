package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch

/**
 * **쪽 목록** — 작은 그림을 격자로 늘어놓고 눌러 그 쪽으로 간다.
 *
 * 작은 그림의 예산은 선명화층의 몫이다([PdfPageStore.openGrid] 의 주석). 격자가 열려 있는 동안만 들고, 닫으면 놓는다 —
 * 그래서 이 판이 컴포지션에 있는 동안을 `DisposableEffect` 로 묶는다.
 *
 * 격자 칸은 **3:4 로 고정**한다. 쪽마다 비율이 다르지만(가로 쪽이 섞인다) 칸의 크기를 재려면 모든 쪽의 크기를 먼저 재야
 * 하고, 2,000쪽 문서에서 그것은 격자를 여는 값이 된다. 그림은 칸 안에 맞춰 들어간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfGridSheet(
    store: PdfPageStore,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DisposableEffect(store) {
        store.openGrid()
        onDispose { store.closeGrid() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            stringResource(R.string.doc_grid),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            state = gridState,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = store.pageCount, key = { it }) { page ->
                GridCell(store, page, selected = page == current, onClick = { onPick(page) })
            }
        }
    }
}

@Composable
private fun GridCell(store: PdfPageStore, page: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THUMB_ASPECT)
                .background(GRID_BACKDROP)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier,
                ),
        ) {
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            // 칸이 화면을 벗어나면 이 코루틴이 취소된다 — 줄 선 그리기가 그리기 전에 돌아선다(렌더 디스패처).
            val image by produceState<ImageBitmap?>(null, store, page, w, h) {
                value = store.thumbnail(page, w, h)
            }
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            stringResource(R.string.doc_grid_page, page + 1),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * PDF 찾기 막대(API 35 이상에서만 뜬다 — `PdfSearch` 의 주석). 위쪽 막대 자리에 선다.
 *
 * 입력칸은 **저장하지 않는 상태**로 든다(`remember`) — 찾을 글은 암호가 아니지만, 회전해도 남길 까닭도 없다. 결과는 VM 이
 * 들고 있어 회전을 견딘다.
 */
@Composable
internal fun PdfSearchBar(
    state: PdfSearch.State,
    onSearch: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(state.query) }
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(PdfSearch.MAX_QUERY) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.doc_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                cursorColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = searchCaption(state),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(enabled = state.matches.isNotEmpty(), onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.doc_search_previous), tint = Color.White)
        }
        IconButton(enabled = state.matches.isNotEmpty(), onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.doc_search_next), tint = Color.White)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.doc_search_close), tint = Color.White)
        }
    }
}

/** 찾기 막대의 글 — 몇 번째인가, 찾는 중인가, 없는가. */
@Composable
private fun searchCaption(state: PdfSearch.State): String = when {
    !state.active -> ""
    state.matches.isNotEmpty() && state.capped ->
        stringResource(R.string.doc_search_count_capped, state.current + 1, state.matches.size)
    state.matches.isNotEmpty() -> stringResource(R.string.doc_search_count, state.current + 1, state.matches.size)
    state.running -> stringResource(R.string.doc_search_running)
    else -> stringResource(R.string.doc_search_none)
}

/** 격자 칸의 비율(폭 : 높이). A4 세로(0.707)에 가깝게. */
private const val THUMB_ASPECT = 0.75f

/** 격자 칸의 바탕 — 쪽이 그려지기 전과 쪽이 칸을 다 채우지 못한 자리. */
private val GRID_BACKDROP = Color(0xFF3A3A3A)
