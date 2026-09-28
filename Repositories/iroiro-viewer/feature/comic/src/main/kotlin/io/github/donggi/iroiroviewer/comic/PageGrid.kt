package io.github.donggi.iroiroviewer.comic

import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 쪽 목록 — 작은 썸네일 격자에서 쪽을 골라 뛴다(14단계).
 *
 * **책 위에 겹치는 층이다**(화면 전환이 아니다). 그래서 읽던 페이저가 그대로 살아 있고, 고르면 층을 걷어 내고 그 쪽으로
 * 옮긴다. 겹치는 층에는 탭을 삼키는 수정자를 단다 — 형제 히트 테스트는 포인터 노드가 없는 층을 지나쳐 아래의 페이저를
 * 누른다(CLAUDE.md 함정 표).
 *
 * 썸네일의 규칙(무엇을 받는가·메모리·solid 의 훑기)은 [PageThumbs] 에 있다. 여기는 그것을 화면에 잇는 것뿐이다:
 * 보이는 칸의 범위를 알리고, 층이 사라지면 **받는 일을 취소하고 든 것을 전부 놓는다.**
 */
@Composable
internal fun PageGrid(
    book: ComicViewModel.Book,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val store = book.store
    val thumbs = remember(store) {
        PageThumbs<ImageBitmap>(store.pageCount, store.thumbCap) { it.asAndroidBitmap().allocationByteCount.toLong() }
    }
    DisposableEffect(thumbs) {
        // 띠 캐시가 쓰던 몫을 격자가 빌린다([ComicPageStore.thumbCap]).
        store.clearBands()
        onDispose { thumbs.clear() }
    }

    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = current.coerceIn(0, (book.pageCount - 1).coerceAtLeast(0)))
    val density = LocalDensity.current
    // 칸의 실제 크기. 재기 전에는 최소 칸 크기로 어림한다. 디코딩은 해제 스레드에서 이 값을 읽는다.
    val box = remember {
        AtomicReference(
            with(density) { IntSize(CELL_MIN.roundToPx(), (CELL_MIN.toPx() * CELL_ASPECT).toInt()) },
        )
    }

    // 보이는 칸 → 원하는 범위. **앞뒤로 보이는 칸 수의 절반씩** 여유를 둔다 — 조금 밀어도 빈칸이 보이지 않을 만큼이고,
    // 그 이상은 보지도 않을 쪽을 푸는 일이다.
    LaunchedEffect(gridState, thumbs) {
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()
            if (first == null) {
                null
            } else {
                Triple(first.index, visible.last().index, first.size)
            }
        }
            .distinctUntilChanged()
            .collect { v ->
                if (v == null) return@collect
                val (first, last, size) = v
                if (size.width > 0 && size.height > 0) box.set(size)
                val margin = (last - first + 1) / 2
                thumbs.setWanted((first - margin)..(last + margin), shown = first..last)
            }
    }
    // 받는 일. 격자가 사라지면 이 효과가 취소되고, 취소는 해제 루프에 인터럽트로 닿는다.
    LaunchedEffect(thumbs) {
        thumbs.run(store::scan) { bytes ->
            val b = box.get()
            decodeThumbnail(bytes, (b.width * THUMB_SCALE).toInt(), (b.height * THUMB_SCALE).toInt())
        }
    }

    val version by thumbs.version.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // **탭을 삼키는 바닥.** 아래의 페이저가 눌리면 격자 뒤에서 쪽이 넘어간다(형제 히트 테스트 함정).
        // 층 전체(부모)가 아니라 **내용 아래의 형제**에 단다 — `clickable` 은 자손의 의미를 합친다
        // (`AbstractClickableNode.shouldMergeDescendantSemantics` 가 참이다, 바이트코드로 확인). 부모에 달면 화면 낭독기가
        // 제목·안내와 격자의 스크롤까지 '눌러도 아무 일 없는 단추' 하나로 읽는다. 여기서는 의미를 지워 낭독기가 보지 않는다.
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .clickable(interactionSource = null, indication = null) {},
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, stringResource(R.string.comic_grid_close), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.comic_grid),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.comic_page_of, current + 1, book.pageCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            // 무엇을 기다리는지 숨기지 않는다 — 힙이 작아 그림을 뜨지 않는 기기, 차례대로만 풀리는 압축.
            val hint = when {
                store.thumbCap <= 0 -> R.string.comic_grid_no_thumbs
                book.solid -> R.string.comic_grid_solid_hint
                else -> null
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CELL_MIN),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(book.pageCount, key = { it }) { ordinal ->
                    GridCell(
                        ordinal = ordinal,
                        // [version] 을 읽어야 칸이 새 썸네일을 받는다 — [PageThumbs] 는 스냅숏 상태가 아니다.
                        thumb = version.let { thumbs.get(ordinal) },
                        failed = thumbs.isFailed(ordinal),
                        selected = ordinal == current,
                        onClick = { onPick(ordinal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    ordinal: Int,
    thumb: ImageBitmap?,
    failed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.comic_grid_page, ordinal + 1)
    Box(
        Modifier
            .aspectRatio(1f / CELL_ASPECT)
            .background(Color.White.copy(alpha = 0.08f))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed) {
            Text(
                text = stringResource(R.string.comic_page_failed),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
            )
        }
        Text(
            text = (ordinal + 1).toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * 쪽 원본 → 썸네일. **해제 스레드에서 블로킹으로** 돈다(`PageThumbs.run`).
 *
 * `ImageIo.decodeFitted` 를 쓰지 않는다 — 그것은 표본을 2의 거듭제곱으로 끊어 결과가 목표의 최대 두 배(넓이 네 배)로
 * 뜨고, 격자는 그런 장을 수십 장 든다. 여기서는 `setTargetSize` 로 **칸 크기 그대로** 뜨고, 불투명한 쪽은 한 화소
 * 2바이트(`MEMORY_POLICY_LOW_RAM`)로 받는다. 확대할 일이 없어 선명화도 필요 없다.
 *
 * `createSource(byte[], int, int)` 는 API 31 부터다(minSdk 31). 실패는 전부 null — 칸에 쪽 번호가 남는다.
 */
internal fun decodeThumbnail(bytes: ByteArray, boxWidth: Int, boxHeight: Int): ImageBitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes, 0, bytes.size)) { decoder, info, _ ->
        val size = thumbSize(info.size.width, info.size.height, boxWidth, boxHeight)
            ?: throw IOException("크기를 모른다")
        decoder.setTargetSize(size[0], size[1])
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
    }.asImageBitmap()
} catch (e: IOException) {
    null
} catch (e: RuntimeException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** 칸의 최소 폭. 폰 세로에서 네 줄, 태블릿 가로에서 열 줄 남짓이다. */
private val CELL_MIN = 96.dp

/** 칸의 높이 / 폭. 만화 쪽의 흔한 비(2:3)다. */
private const val CELL_ASPECT = 1.5f

/**
 * 썸네일을 칸의 몇 배로 뜨는가. 칸 그대로 뜨면 폰 한 칸이 400 KB 를 넘어 선명화 몫(수 MB)에 스무 칸도 들지 않는다.
 * 0.75 면 한 칸이 그 절반 남짓이라 한 화면과 앞뒤 여유가 든다. 격자는 쪽을 **알아보는** 자리라 조금 무른 것이 낫다.
 */
private const val THUMB_SCALE = 0.75f
