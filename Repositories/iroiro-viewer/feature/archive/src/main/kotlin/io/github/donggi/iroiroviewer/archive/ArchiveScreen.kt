package io.github.donggi.iroiroviewer.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.format.archive.ArchiveTree
import io.github.donggi.iroiroviewer.format.archive.ComicPages
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.ui.KindBadge

/**
 * 압축 파일 안을 보는 화면. **읽기 전용이다** — 여기서 지우거나 이름을 바꾸지 않는다.
 *
 * ## 차용한 관행
 *
 * | 무엇 | 어떻게 | 왜 |
 * |---|---|---|
 * | 폴더 트리 | 아카이브 안의 경로를 폴더로 그린다 | `폴더N/그림.jpg` 1만 개를 평평하게 그리면 1만 줄이다 |
 * | 뒤로가기 | 찾기 → 아카이브 안 폴더 → 화면 닫기 | 순서를 **이 화면 하나가** 정한다 |
 * | 풀기 | 기본이 '새 폴더', 흩어지면 숫자로 말한다 | 같은 폴더에 스무 개가 쏟아지는 것은 되돌리기 어렵다 |
 * | 썸네일 | **만들지 않는다** | 7z·RAR 은 엔트리 하나를 꺼내는 데 앞엣것을 다 푼다 |
 *
 * ## 왜 목록에 썸네일이 없는가
 *
 * `randomAccess = false` 인 포맷에서 격자 썸네일 12장은 해제를 12번 되풀이하는 일이다
 * (실측: 7z 는 엔트리마다 여는 비용이 **제곱**으로 는다). 포맷마다 화면이 달라 보이는
 * 것은 더 나쁘므로 세 포맷 모두 만들지 않는다. 만화 뷰어(9단계)가 '열 때 한 번에 다
 * 풀어 캐시' 를 세울 때 함께 정한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    path: String,
    /** 공용 스낵바. Root 가 그리는 모든 화면이 하나씩 가져야 한다는 불변식을 지킨다. */
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    /** 풀기를 시켜 달라는 요청. 실제 실행은 파일 작업 큐가 한다. */
    onExtract: (ArchiveViewModel.ExtractPlan, newFolder: Boolean, FileOpEngine.Conflict) -> Unit,
    onOpenNotice: () -> Unit,
    /**
     * 아카이브 안의 **그림 항목**을 만화 뷰어로 연다(엔트리 번호를 준다).
     *
     * 8단계가 '아카이브 안의 파일 미리보기' 를 9단계로 미루면서 든 이유가 "엔트리를
     * 어딘가에 뽑아야 하는데 그 캐시의 자리·수명·프라이버시가 만화 뷰어의 문제와 같다"
     * 였다. 만화 뷰어가 **뽑지 않기로**(힙에만 둔다) 결론을 내렸으므로, 그림 미리보기는
     * 그 뷰어를 그 쪽에서 여는 것으로 끝난다 — 캐시가 아예 없다.
     */
    onOpenImageEntry: (entryIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ArchiveViewModel = viewModel()
    LaunchedEffect(path) { vm.open(path) }

    val state by vm.state.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()

    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<ArchiveTree.Node?>(null) }

    // **뒤로가기의 주인은 이 화면 하나다.** app 쪽에 또 두면 어느 것이 먼저 먹는지가
    // 등록 순서에 달리고, 그것은 아무도 읽을 수 없는 규칙이 된다.
    BackHandler {
        when {
            searchOpen -> {
                searchOpen = false
                vm.setFilter("")
            }
            vm.up() -> Unit
            else -> onClose()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searchOpen) {
                SearchBar(
                    query = filter,
                    onQuery = vm::setFilter,
                    onClose = {
                        searchOpen = false
                        vm.setFilter("")
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (!vm.up()) onClose() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.archive_close))
                        }
                    },
                    title = {
                        Text(
                            text = folder.substringAfterLast('/').ifEmpty {
                                (state as? ArchiveViewModel.State.Ready)?.doc?.name.orEmpty()
                            },
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { searchOpen = true },
                            enabled = state is ArchiveViewModel.State.Ready,
                        ) {
                            Icon(Icons.Filled.Search, stringResource(R.string.archive_search))
                        }
                        // **⋮ 단추와 메뉴를 한 상자에 담는다.** Compose 의 Popup 은 자기를 감싼 **부모
                        // 레이아웃 노드**를 앵커로 삼는다. `actions` 에 형제로 두면 앵커가 아이콘 줄
                        // 전체가 되어 메뉴가 맨 왼쪽 아이콘 아래에서 시작한다(사용자가 지적했다).
                        // `offset` 으로 보정하지 마라 — 화면 폭·글꼴 배율에서 다시 어긋난다.
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.archive_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive_extract_all)) },
                                    enabled = state is ArchiveViewModel.State.Ready,
                                    onClick = {
                                        menuOpen = false
                                        vm.preparePlan(null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive_extract_folder)) },
                                    enabled = state is ArchiveViewModel.State.Ready && folder.isNotEmpty(),
                                    onClick = {
                                        menuOpen = false
                                        val doc = (state as? ArchiveViewModel.State.Ready)?.doc
                                        vm.preparePlan(doc?.tree?.folderAt(folder))
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive_notice)) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenNotice()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            (state as? ArchiveViewModel.State.Ready)?.doc?.let { StatusBar(it) }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (val s = state) {
                is ArchiveViewModel.State.Loading -> Centered { CircularProgressIndicator() }

                is ArchiveViewModel.State.Failed -> Centered {
                    Text(
                        text = stringResource(
                            when (s.kind) {
                                ArchiveViewModel.State.Failed.Kind.UNREADABLE -> R.string.archive_failed_unreadable
                                ArchiveViewModel.State.Failed.Kind.CORRUPT -> R.string.archive_failed_corrupt
                                ArchiveViewModel.State.Failed.Kind.UNSUPPORTED -> R.string.archive_failed_unsupported
                                ArchiveViewModel.State.Failed.Kind.TOO_LARGE -> R.string.archive_failed_too_large
                                ArchiveViewModel.State.Failed.Kind.ENCRYPTED -> R.string.archive_failed_encrypted
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                is ArchiveViewModel.State.Ready -> {
                    val rows = remember(s.doc, folder, filter) { vm.rows() }
                    if (rows.isEmpty()) {
                        Centered {
                            Text(
                                stringResource(
                                    if (filter.isBlank()) R.string.archive_empty
                                    else R.string.archive_empty_filtered,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        EntryList(
                            rows = rows,
                            showPath = filter.isNotBlank(),
                            onOpenFolder = vm::enter,
                            onTapFile = { node ->
                                // 그림은 만화 뷰어로. 나머지는 무엇을 못 하는지 말한다.
                                if (canPreview(node)) onOpenImageEntry(node.entryIndex) else notice = node
                            },
                        )
                    }
                }
            }
        }
    }

    plan?.let { p ->
        ExtractDialog(
            plan = p,
            onDismiss = vm::dismissPlan,
            onGo = { newFolder, conflict ->
                vm.dismissPlan()
                onExtract(p, newFolder, conflict)
            },
        )
    }

    notice?.let { node ->
        // 그림이 아닌 항목은 **열지 않는다.** 글·문서·영상 미리보기는 각 뷰어가 경로를
        // 요구하는데, 그것을 주려면 엔트리를 디스크로 뽑아야 한다 — 만화 뷰어가 하지
        // 않기로 한 바로 그 일이다. 무엇을 못 하는지 말하는 것이 아무 반응도 없는 것보다 낫다.
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(node.name, maxLines = 2, overflow = TextOverflow.MiddleEllipsis) },
            text = {
                Text(
                    stringResource(
                        when {
                            node.unsafe -> R.string.archive_badge_unsafe
                            node.isEncrypted -> R.string.archive_failed_encrypted
                            // **만화 확장자도 중첩 아카이브다.** 9단계가 cbz·cbr·cb7·cbt 를
                            // ARCHIVE 에서 COMIC 으로 옮기면서 이 검사가 그 넷을 놓쳤고,
                            // 권별 cbz 를 zip 하나에 모아 둔 흔한 구성에서 '이 항목을 열 수
                            // 없습니다' 만 나왔다 — 사용자는 파일이 깨진 줄로 읽는다.
                            MimeResolver.kindOf(node.name, false) in NESTED_KINDS ->
                                R.string.archive_preview_nested
                            else -> R.string.archive_preview_failed
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { notice = null }) { Text(stringResource(R.string.archive_close)) }
            },
        )
    }
}

/**
 * 압축 안에 또 압축인 것 — 이름으로 판정한다. 만화 확장자도 여기 든다.
 *
 * 재귀를 막는 것이 [io.github.donggi.iroiroviewer.safety.ParseLimits.maxContainerDepth]
 * 라는 상수가 아니라 **구조**라는 점이 중요하다: 엔트리에는 경로가 없어 어느 뷰어에도
 * 넘길 수 없고, 이 목록은 엔트리를 디스크로 뽑지 않는다. 상한 값은 그 사실을 적어 둔
 * 것일 뿐 어디에서도 읽히지 않는다.
 */
private val NESTED_KINDS = setOf(FileKind.ARCHIVE, FileKind.COMIC)

/**
 * 이 항목을 지금 열 수 있는가.
 *
 * 그림이면서 읽을 수 있는 것만이다. `unsafe`·암호·링크는 바이트를 꺼낼 수 없고,
 * 중첩 아카이브는 [NESTED_KINDS] 로 갈라 '풀어서 여세요' 로 끝낸다.
 */
private fun canPreview(node: ArchiveTree.Node): Boolean =
    !node.isDirectory && !node.unsafe && !node.isEncrypted && !node.isLink &&
        node.entryIndex >= 0 && ComicPages.isPage(node.name)

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// ---- 목록 -----------------------------------------------------------------------

@Composable
private fun EntryList(
    rows: List<ArchiveTree.Node>,
    showPath: Boolean,
    onOpenFolder: (String) -> Unit,
    onTapFile: (ArchiveTree.Node) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(
            count = rows.size,
            // **키는 이름이 아니다.** 같은 이름의 엔트리가 실재하고, 중복 키는 Compose 가
            // 예외로 끝낸다. 파일은 엔트리 번호, 폴더는 경로로 가른다.
            key = { i -> rows[i].let { if (it.isDirectory) "d:${it.path}" else "f:${it.entryIndex}" } },
            contentType = { i -> rows[i].isDirectory },
        ) { i ->
            EntryRow(rows[i], showPath, onOpenFolder, onTapFile)
        }
    }
}

@Composable
private fun EntryRow(
    node: ArchiveTree.Node,
    showPath: Boolean,
    onOpenFolder: (String) -> Unit,
    onTapFile: (ArchiveTree.Node) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (node.isDirectory) onOpenFolder(node.path) else onTapFile(node) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindBadge(
            kind = if (node.isDirectory) FileKind.FOLDER else MimeResolver.kindOf(node.name, false),
            extension = node.name.substringAfterLast('.', "").take(4),
            size = 40.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (showPath) node.path else node.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = secondLine(node),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (node.isEncrypted) {
            Icon(
                Icons.Filled.Lock,
                stringResource(R.string.archive_badge_encrypted),
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (node.unsafe || node.isLink) {
            Icon(
                Icons.Filled.Warning,
                stringResource(
                    if (node.unsafe) R.string.archive_badge_unsafe else R.string.archive_badge_link,
                ),
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun secondLine(node: ArchiveTree.Node): String = when {
    node.isDirectory -> stringResource(
        R.string.archive_folder_summary,
        node.fileCount,
        formatBytes(node.totalDeclaredSize),
    )
    else -> formatBytes(node.declaredSize)
}

// ---- 막대와 대화상자 --------------------------------------------------------------

@Composable
private fun StatusBar(doc: ArchiveViewModel.Doc) {
    Surface(tonalElevation = 3.dp) {
        Text(
            text = stringResource(
                R.string.archive_summary_header,
                doc.formatId.label,
                doc.entries.size,
                formatBytes(doc.fileBytes),
            ) + if (doc.solid) " · " + stringResource(R.string.archive_summary_solid) else "",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.archive_search_close))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.archive_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

/**
 * 풀기 전에 묻는 것.
 *
 * **기본값이 '새 폴더' 인 이유를 숫자로 말한다.** '여기에 풀기' 가 무엇을 흩뜨리는지
 * 모르면 사용자는 되돌릴 수 없는 선택을 눈감고 하게 된다.
 */
@Composable
private fun ExtractDialog(
    plan: ArchiveViewModel.ExtractPlan,
    onDismiss: () -> Unit,
    onGo: (newFolder: Boolean, FileOpEngine.Conflict) -> Unit,
) {
    var newFolder by remember { mutableStateOf(plan.preferNewFolder) }
    var conflict by remember { mutableStateOf(FileOpEngine.Conflict.KEEP_BOTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_extract_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.archive_extract_count, plan.fileCount, formatBytes(plan.declaredBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Choice(
                    label = stringResource(R.string.archive_extract_new_folder) + " · " + plan.folderName,
                    selected = newFolder,
                    onClick = { newFolder = true },
                )
                Choice(
                    label = stringResource(R.string.archive_extract_here),
                    selected = !newFolder,
                    onClick = { newFolder = false },
                )
                Text(
                    stringResource(R.string.archive_extract_dest, plan.destParent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                if (!newFolder) {
                    if (plan.topLevelCount > 1) {
                        Warn(stringResource(R.string.archive_extract_scatter, plan.topLevelCount))
                    }
                    plan.existingTopLevel.firstOrNull()?.let {
                        Warn(stringResource(R.string.archive_extract_merge, it))
                    }
                    if (plan.conflicts > 0) {
                        Warn(stringResource(R.string.archive_extract_conflicts, plan.conflicts))
                        for (name in plan.conflictSamples) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                        HorizontalDivider()
                        // 충돌이 있을 때만 정책을 묻는다. **덮어쓰기가 맨 아래**이고
                        // 오류 색이다 — 브라우저의 붙여넣기 대화상자와 같은 배치다.
                        Choice(
                            stringResource(R.string.archive_conflict_keep_both),
                            conflict == FileOpEngine.Conflict.KEEP_BOTH,
                        ) { conflict = FileOpEngine.Conflict.KEEP_BOTH }
                        Choice(
                            stringResource(R.string.archive_conflict_skip),
                            conflict == FileOpEngine.Conflict.SKIP,
                        ) { conflict = FileOpEngine.Conflict.SKIP }
                        Choice(
                            stringResource(R.string.archive_conflict_overwrite),
                            conflict == FileOpEngine.Conflict.OVERWRITE,
                            danger = true,
                        ) { conflict = FileOpEngine.Conflict.OVERWRITE }
                    }
                }

                if (plan.refused > 0) {
                    Warn(stringResource(R.string.archive_extract_refused, plan.refused))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGo(newFolder, if (newFolder) FileOpEngine.Conflict.KEEP_BOTH else conflict) }) {
                Text(stringResource(R.string.archive_extract_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.archive_cancel)) }
        },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = (if (selected) "● " else "○ ") + label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun Warn(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** 사람이 읽는 크기. 목록과 대화상자가 같은 말을 쓰게 한 곳에 둔다. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(java.util.Locale.KOREAN, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.KOREAN, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(java.util.Locale.KOREAN, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
