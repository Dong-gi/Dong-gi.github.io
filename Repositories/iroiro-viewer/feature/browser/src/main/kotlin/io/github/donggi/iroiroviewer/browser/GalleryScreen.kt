package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.io.GalleryScanner
import io.github.donggi.iroiroviewer.model.DayBucket
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.ui.ThumbnailStore
import java.time.ZoneId

/**
 * 갤러리. 잘 알려진 사진 폴더를 훑어 격자로 보여 준다.
 *
 * ## 왜 격자가 폴더 목록과 따로 있는가
 *
 * 파일 관리자의 격자는 '이 폴더 안' 을 보여 주지만, 사진을 찾을 때 사용자는 폴더를
 * 생각하지 않는다. DCIM 아래에 카메라·스크린샷·다운로드가 흩어져 있어도 머릿속에서는
 * 하나의 '사진' 이다. 그래서 여러 뿌리를 한 격자에 시각 순으로 합친다.
 *
 * 썸네일은 [ThumbnailStore] 가 만들고 다시 쓴다. 원본이 사라지면 썸네일도 사라진다
 * (`BrowserViewModel` 의 갤러리 정리 참고).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: BrowserViewModel,
    snackbar: SnackbarHostState,
    onOpen: (List<FileEntry>, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scan by vm.gallery.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // **'오늘' 의 기준을 훑기마다 한 번만 잡는다.** 머리글마다 `currentTimeMillis` 를
    // 부르면 자정을 지나는 순간 화면의 절반은 '오늘', 절반은 '어제' 가 될 수 있다.
    // 다시 훑으면 다시 잡히므로 오래 켜 두어도 결국 따라온다.
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(scan, zone) { DayBucket.dayOf(System.currentTimeMillis(), zone) }
    // 어제의 마지막 밀리초. 하루를 빼면 서머타임이 있는 시간대에서 어긋난다.
    val yesterday = remember(today, zone) { DayBucket.dayOf(today.startMillis - 1, zone) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_up))
                    }
                },
                title = {
                    Text(
                        when (val s = scan) {
                            is GalleryScanner.Scan.Scanning -> stringResource(R.string.browser_gallery)
                            is GalleryScanner.Scan.Ready ->
                                stringResource(R.string.browser_gallery_count, s.images.size)
                        }
                    )
                },
            )
        },
    ) { inner ->
        when (val s = scan) {
            is GalleryScanner.Scan.Scanning -> Box(
                Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.browser_gallery_scanning, s.found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is GalleryScanner.Scan.Ready -> {
                if (s.images.isEmpty()) {
                    Box(
                        Modifier.padding(inner).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                R.string.browser_gallery_empty,
                                GalleryScanner.DEFAULT_FOLDERS.joinToString(" · "),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        // 칸 수를 고정하지 않는다. 태블릿 가로에서 3열이면 칸 하나가
                        // 손바닥만 해진다.
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        state = gridState,
                        modifier = Modifier.padding(inner).fillMaxSize(),
                    ) {
                        for (section in s.sections) {
                            item(
                                key = "day:${section.day.startMillis}",
                                // 머리글은 한 줄을 통째로 쓴다. 칸 하나로 두면 사진
                                // 사이에 글자가 끼어 어느 날의 것인지 읽히지 않는다.
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                DayHeader(section.day, today, yesterday)
                            }
                            itemsIndexed(
                                items = s.images.subList(
                                    section.firstIndex,
                                    section.firstIndex + section.count,
                                ),
                                key = { _, e -> e.path },
                            ) { i, entry ->
                                // **구간 안의 자리를 원래 목록의 자리로 되돌린다.**
                                // 이것을 빠뜨리면 두 번째 날짜부터 누른 것과 다른
                                // 사진이 열린다 — 화면에는 아무 오류도 나지 않는다.
                                val flat = section.firstIndex + i
                                GalleryCell(entry = entry, onClick = { onOpen(s.images, flat) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 날짜 머리글.
 *
 * 오늘·어제는 **이름으로 부른다** — 숫자보다 빨리 읽히고, 갤러리에서 가장 자주 보는
 * 두 줄이다. 올해 안이면 해를 적지 않는다. 머리글마다 같은 네 자리가 되풀이될 뿐이고,
 * 해가 다른 줄에서만 그것이 정보가 된다.
 */
@Composable
private fun DayHeader(day: DayBucket.Day, today: DayBucket.Day, yesterday: DayBucket.Day) {
    val text = when {
        day.startMillis == today.startMillis -> stringResource(R.string.browser_gallery_date_today)
        day.startMillis == yesterday.startMillis ->
            stringResource(R.string.browser_gallery_date_yesterday)
        day.year == today.year ->
            stringResource(R.string.browser_gallery_date_md, day.month, day.dayOfMonth)
        else ->
            stringResource(R.string.browser_gallery_date_ymd, day.year, day.month, day.dayOfMonth)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * 격자 한 칸.
 *
 * 썸네일을 `produceState` 로 읽는다 — 칸이 화면에 들어올 때 시작하고 나가면 취소된다.
 * 목록 전체를 미리 디코딩하면 사진 3,000장짜리 기기에서 메모리가 먼저 끝난다.
 */
@Composable
private fun GalleryCell(entry: FileEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val key = remember(entry.path, entry.size, entry.lastModified) {
        ThumbnailStore.keyOf(entry.path, entry.size, entry.lastModified)
    }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key) {
        value = ThumbnailStore.get(context, entry.path, key)
    }

    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                // 격자에서 파일 이름을 하나하나 읽어 주면 낭독기가 쓸 수 없게 된다.
                // 칸의 이름은 파일 이름 하나로 충분하다.
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
