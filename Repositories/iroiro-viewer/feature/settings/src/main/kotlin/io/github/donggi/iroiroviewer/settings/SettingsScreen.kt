package io.github.donggi.iroiroviewer.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.io.Format
import io.github.donggi.iroiroviewer.io.ShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** 떠 있는 대화상자. 이름으로 저장된다(`rememberSaveable` 이 enum 을 받는다). */
private enum class SettingsDialog {
    COMIC_DIRECTION, READER_MARGIN, READER_THEME,
    CLEAR_PLAYBACK, CLEAR_READING, CLEAR_CRASHES,
}

/**
 * 설정(14단계).
 *
 * 7·9·11단계가 '설정 화면을 만들 때 한꺼번에 한다' 로 미뤄 둔 뷰어의 기본값과, 기록 지우기·크래시 기록·고지로 가는 길을
 * 한 화면에 모은다. 값은 전부 `AppPreferences` 의 흐름이라 **바꾸는 즉시** 그 값을 보는 화면에 걸린다([SettingsViewModel]).
 *
 * **알림은 [snackbar] — Root 가 가진 공용 스낵바 — 에 띄운다.** Root 가 그릴 수 있는 모든 화면은 그것에 붙은
 * `SnackbarHost` 를 하나씩 가져야 한다(붙지 않은 화면으로 넘어가면 파일 작업 결과의 `showSnackbar` 가 돌아오지 않는다).
 * 그래서 이 화면의 `Scaffold` 가 하나를 든다 — `Scaffold` 가 아래쪽 정렬을 맡으므로 '칸 옆의 스낵바는 맨 위' 함정도 없다.
 *
 * 떠 있는 대화상자는 `rememberSaveable` 이다. **이 화면이 컴포지션에서 빠지면(고지·진단으로 가면) 사라지는데** 그것이
 * 맞다 — 돌아왔을 때 '지울까요?' 가 다시 떠 있으면 사용자는 무엇을 물었는지 모른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /** 앱의 판 이름(`1.0.0 (2)` 꼴). `BuildConfig` 는 `app` 에 있어 넘겨받는다. */
    appVersion: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenNotice: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val values by vm.values.collectAsStateWithLifecycle()
    val crash by vm.crash.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    // **고지·진단에 다녀오면 이 화면은 컴포지션에서 빠졌다가 새로 만들어진다** — `rememberLazyListState` 의 자리도 함께
    // 사라져 맨 위로 튄다(함정 표의 `rememberSaveable`). 떠나기 직전의 자리를 ViewModel 에 맡겨 두고 돌아올 때 한 번 꺼낸다.
    // 뒤로 나갈 때는 맡기지 않으므로 다음에 설정을 열면 맨 위다.
    val initialScroll = remember { vm.takeScroll() }
    val listState = rememberLazyListState(initialScroll.first, initialScroll.second)
    val openAway: (() -> Unit) -> Unit = { go ->
        vm.rememberScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        go()
    }

    // 기록은 앞선 프로세스가 죽으면서 남긴다 — 화면에 돌아올 때마다 다시 센다.
    LifecycleResumeEffect(Unit) {
        vm.refreshCrashes()
        onPauseOrDispose { }
    }

    // 코루틴 안에서는 `stringResource` 를 부를 수 없어 먼저 받아 둔다(`context.getString` 은 구성 변경을 따라가지 않는다).
    val saveFailed = stringResource(R.string.settings_save_failed)
    val clearFailed = stringResource(R.string.settings_clear_failed)
    val clearedPlayback = stringResource(R.string.settings_cleared_playback)
    val clearedReading = stringResource(R.string.settings_cleared_reading)
    val crashCleared = stringResource(R.string.settings_crash_cleared)
    val shareTitle = stringResource(R.string.settings_crash_share_title)
    val shareFailed = stringResource(R.string.settings_crash_share_failed)

    /** 값을 바꾸는 쓰기 — 되면 말없이, 안 되면 알린다. */
    val write: (Deferred<Boolean>) -> Unit = { result -> scope.report(snackbar, result, ok = null, failed = saveFailed) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { inner ->
        val v = values
        if (v == null) {
            // 저장소를 처음 읽는 몇 ms. 기본값을 먼저 그렸다가 저장된 값으로 튀게 하지 않는다.
            Box(Modifier.padding(inner).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(Modifier.padding(inner).fillMaxSize(), state = listState) {
            // ---- 파일 목록 ----
            item(key = "h-files") { SectionHeader(R.string.settings_section_files) }
            item(key = "hidden") {
                SwitchRow(R.string.settings_show_hidden, R.string.settings_show_hidden_desc, v.showHidden) {
                    write(vm.setShowHidden(it))
                }
            }
            item(key = "folders-first") {
                SwitchRow(R.string.settings_folders_first, R.string.settings_folders_first_desc, v.foldersFirst) {
                    write(vm.setFoldersFirst(it))
                }
            }

            // ---- 텍스트 뷰어 ----
            item(key = "h-text") { SectionHeader(R.string.settings_section_text) }
            item(key = "text-wrap") {
                SwitchRow(R.string.settings_text_wrap, R.string.settings_text_wrap_desc, v.text.wrap) {
                    write(vm.setTextWrap(it))
                }
            }
            item(key = "text-lines") {
                SwitchRow(R.string.settings_text_line_numbers, null, v.text.lineNumbers) {
                    write(vm.setTextLineNumbers(it))
                }
            }
            item(key = "text-font") {
                SliderRow(
                    title = R.string.settings_text_font,
                    value = v.text.fontSp,
                    min = TextViewerDefaults.MIN_FONT_SP,
                    max = TextViewerDefaults.MAX_FONT_SP,
                    step = 1,
                    label = { stringResource(R.string.settings_text_font_value, it) },
                    onCommit = vm::setTextFontSp,
                    onFailed = { scope.launch { snackbar.showSnackbar(saveFailed) } },
                )
            }
            item(key = "text-ends") {
                SwitchRow(R.string.settings_text_line_ends, R.string.settings_text_line_ends_desc, v.text.showLineEnds) {
                    write(vm.setTextLineEnds(it))
                }
            }

            // ---- 만화 ----
            item(key = "h-comic") { SectionHeader(R.string.settings_section_comic) }
            item(key = "comic-direction") {
                ChoiceRow(
                    title = R.string.settings_comic_direction,
                    value = SettingsOptions.labelOf(SettingsOptions.comicDirections, v.comicDirection),
                    note = R.string.settings_comic_direction_note,
                ) { dialog = SettingsDialog.COMIC_DIRECTION }
            }

            // ---- 문서 보기 ----
            item(key = "h-reader") { SectionHeader(R.string.settings_section_reader, R.string.settings_reader_note) }
            item(key = "reader-font") {
                SliderRow(
                    title = R.string.settings_reader_font,
                    value = v.reader.fontPercent,
                    min = ReaderAppearance.MIN_FONT_PERCENT,
                    max = ReaderAppearance.MAX_FONT_PERCENT,
                    step = SettingsOptions.READER_FONT_STEP,
                    label = { stringResource(R.string.settings_reader_font_value, it) },
                    onCommit = vm::setReaderFontPercent,
                    onFailed = { scope.launch { snackbar.showSnackbar(saveFailed) } },
                )
            }
            item(key = "reader-margin") {
                ChoiceRow(
                    title = R.string.settings_reader_margin,
                    value = SettingsOptions.labelOf(SettingsOptions.readerMargins, v.reader.margin),
                ) { dialog = SettingsDialog.READER_MARGIN }
            }
            item(key = "reader-theme") {
                ChoiceRow(
                    title = R.string.settings_reader_theme,
                    value = SettingsOptions.labelOf(SettingsOptions.readerThemes, v.reader.theme),
                ) { dialog = SettingsDialog.READER_THEME }
            }

            // ---- 기록 ----
            item(key = "h-history") { SectionHeader(R.string.settings_section_history) }
            item(key = "clear-playback") {
                ActionRow(R.string.settings_clear_playback, R.string.settings_clear_playback_desc) {
                    dialog = SettingsDialog.CLEAR_PLAYBACK
                }
            }
            item(key = "clear-reading") {
                ActionRow(R.string.settings_clear_reading, R.string.settings_clear_reading_desc) {
                    dialog = SettingsDialog.CLEAR_READING
                }
            }

            // ---- 크래시 기록 ----
            item(key = "h-crash") { SectionHeader(R.string.settings_section_crash) }
            item(key = "crash") {
                val summary = crash
                val count = summary?.count ?: 0
                // 사본을 만드는 동안 단추를 막는다 — 두 번 누르면 고르는 창이 둘 뜬다.
                var sharing by remember { mutableStateOf(false) }
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (count == 0) stringResource(R.string.settings_crash_none)
                                else stringResource(
                                    R.string.settings_crash_summary,
                                    count,
                                    Format.timestamp(summary?.lastAt ?: 0L),
                                )
                            )
                        },
                        supportingContent = { Text(stringResource(R.string.settings_crash_desc)) },
                    )
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            enabled = count > 0 && !sharing,
                            onClick = {
                                sharing = true
                                scope.launch {
                                    // 사본을 캐시의 공유 폴더에 만들고 보낸다. 원본(`filesDir`)은 `FileProvider` 가 열지 않는다.
                                    val sent = try {
                                        val file = vm.exportCrashes()
                                        file != null && ShareHelper.share(context, listOf(file.path), shareTitle)
                                    } finally {
                                        sharing = false
                                    }
                                    if (!sent) snackbar.showSnackbar(shareFailed)
                                }
                            },
                        ) { Text(stringResource(R.string.settings_crash_share)) }
                        OutlinedButton(
                            enabled = count > 0,
                            onClick = { dialog = SettingsDialog.CLEAR_CRASHES },
                        ) { Text(stringResource(R.string.settings_crash_clear)) }
                    }
                }
            }

            // ---- 정보 ----
            item(key = "h-about") { SectionHeader(R.string.settings_section_about) }
            item(key = "version") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(appVersion) },
                )
            }
            item(key = "notice") {
                ActionRow(R.string.settings_notice, R.string.settings_notice_desc) { openAway(onOpenNotice) }
            }
            item(key = "diag") {
                ActionRow(R.string.settings_diag, R.string.settings_diag_desc) { openAway(onOpenDiagnostics) }
            }
        }
    }

    val v = values ?: return
    when (dialog) {
        null -> Unit
        SettingsDialog.COMIC_DIRECTION -> ChoiceDialog(
            title = R.string.settings_comic_direction,
            options = SettingsOptions.comicDirections,
            selected = v.comicDirection,
            onSelect = { dialog = null; write(vm.setComicDirection(it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.READER_MARGIN -> ChoiceDialog(
            title = R.string.settings_reader_margin,
            options = SettingsOptions.readerMargins,
            selected = v.reader.margin,
            onSelect = { dialog = null; write(vm.setReaderMargin(it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.READER_THEME -> ChoiceDialog(
            title = R.string.settings_reader_theme,
            options = SettingsOptions.readerThemes,
            selected = v.reader.theme,
            onSelect = { dialog = null; write(vm.setReaderTheme(it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR_PLAYBACK -> ConfirmClearDialog(
            title = R.string.settings_confirm_playback_title,
            body = stringResource(R.string.settings_confirm_playback_body),
            onConfirm = {
                dialog = null
                scope.report(snackbar, vm.clearHistory(HistoryKind.PLAYBACK), clearedPlayback, clearFailed)
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR_READING -> ConfirmClearDialog(
            title = R.string.settings_confirm_reading_title,
            body = stringResource(R.string.settings_confirm_reading_body),
            onConfirm = {
                dialog = null
                scope.report(snackbar, vm.clearHistory(HistoryKind.READING), clearedReading, clearFailed)
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR_CRASHES -> ConfirmClearDialog(
            title = R.string.settings_confirm_crash_title,
            body = stringResource(R.string.settings_confirm_crash_body, crash?.count ?: 0),
            onConfirm = {
                dialog = null
                scope.report(snackbar, vm.clearCrashes(), crashCleared, clearFailed)
            },
            onDismiss = { dialog = null },
        )
    }
}

/**
 * 쓰기의 결과를 기다려 알린다. **기다리는 것은 이 화면의 스코프**라 화면이 사라지면 알림도 없다 — 쓰기는 ViewModel 의
 * 스코프에서 끝까지 간다([SettingsViewModel] 주석).
 */
private fun CoroutineScope.report(snackbar: SnackbarHostState, result: Deferred<Boolean>, ok: String?, failed: String) {
    launch {
        val message = if (result.await()) ok else failed
        if (message != null) snackbar.showSnackbar(message)
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes note: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            // 긴 목록이라 화면 낭독기가 절 사이를 건너뛸 수 있게 제목으로 표시한다.
            modifier = Modifier.semantics { heading() },
        )
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 켜고 끄는 줄. **줄 전체가 스위치다**(`toggleable`) — 작은 손잡이만 눌리면 화면 낭독기와 손가락이 모두 헤맨다.
 * 스위치 자체는 `onCheckedChange = null` 로 두어 탭을 두 번 받지 않는다.
 */
@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes desc: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = if (desc != null) {
            { Text(stringResource(desc)) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/**
 * 눈금 슬라이더. **끄는 동안은 화면의 값만 움직이고, 손을 뗄 때 한 번 쓴다.**
 *
 * 끄는 동안 칸마다 쓰면 저장소의 흐름이 한 박자 늦게 돌아와 손잡이가 뒤로 튄다(화면 상태와 저장소를 양방향으로 잇지
 * 말라는 함정과 같은 뿌리다). 그래서 [pending] 이 손가락의 값을 들고, 저장된 값이 그것을 따라잡거나(성공) 쓰기가
 * 실패하면 놓는다. 끝낸 자리가 원래 값이면 쓰지 않는다.
 */
@Composable
private fun SliderRow(
    @StringRes title: Int,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    label: @Composable (Int) -> String,
    onCommit: (Int) -> Deferred<Boolean>,
    onFailed: () -> Unit,
) {
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(value, pending) {
        if (pending == value) pending = null
    }
    val scope = rememberCoroutineScope()
    val shown = pending ?: value
    val titleText = stringResource(title)
    val shownText = label(shown)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titleText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(shownText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            // 화면 낭독기는 슬라이더를 **범위의 백분율**로 읽는다(글자 크기 13sp 가 '30%'). 무엇을 고르는지와 지금 값을
            // 우리 말로 준다 — 위 줄의 글과 같은 것.
            modifier = Modifier.semantics {
                contentDescription = titleText
                stateDescription = shownText
            },
            value = shown.toFloat(),
            onValueChange = { pending = SettingsOptions.snap(it, min, max, step) },
            onValueChangeFinished = {
                val target = pending
                if (target != null) {
                    if (target == value) {
                        pending = null
                    } else {
                        val result = onCommit(target)
                        scope.launch {
                            if (!result.await()) {
                                pending = null
                                onFailed()
                            }
                        }
                    }
                }
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = SettingsOptions.sliderSteps(min, max, step),
        )
    }
}

/** 고른 값을 보이고, 누르면 고르는 대화상자를 여는 줄. */
@Composable
private fun ChoiceRow(@StringRes title: Int, @StringRes value: Int, @StringRes note: Int? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Column {
                Text(stringResource(value), color = MaterialTheme.colorScheme.primary)
                if (note != null) {
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActionRow(@StringRes title: Int, @StringRes desc: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(desc)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** 하나를 고르는 대화상자. 고르면 곧바로 닫히고 쓴다 — '확인' 을 한 번 더 누르게 하지 않는다(안드로이드 설정의 관행). */
@Composable
private fun ChoiceDialog(
    @StringRes title: Int,
    options: List<SettingsOptions.Option>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (o in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = o.code == selected, role = Role.RadioButton, onClick = { onSelect(o.code) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o.code == selected, onClick = null)
                        Text(stringResource(o.label), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/**
 * 지우기 확인. 되돌릴 수 없는 조작이라 **반드시 묻는다**(3·4단계 검토가 영구 삭제에서 세운 규칙). 모양은 휴지통의
 * 영구 삭제 확인과 같다 — 경고 아이콘, 붉은 '지우기', 왼쪽의 '취소'.
 */
@Composable
private fun ConfirmClearDialog(@StringRes title: Int, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_confirm_clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
