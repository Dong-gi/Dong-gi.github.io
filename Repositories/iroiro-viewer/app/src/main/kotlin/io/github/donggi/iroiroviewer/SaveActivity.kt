package io.github.donggi.iroiroviewer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileOpText
import io.github.donggi.iroiroviewer.io.IncomingSaves
import io.github.donggi.iroiroviewer.io.StorageAccess
import io.github.donggi.iroiroviewer.io.VolumeRegistry
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.model.PathNames
import io.github.donggi.iroiroviewer.ui.IroProgress
import io.github.donggi.iroiroviewer.ui.IroiroTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * **'Files에 저장'** — 다른 앱의 공유(`ACTION_SEND`·`ACTION_SEND_MULTIPLE`)로 온 파일을 사용자가 고른 폴더에 복사해 둔다. 보는 화면
 * ([OpenActivity] — 문서·압축·만화만 받는다)과 **따로 된 공유 대상**이다: 사진·영상·소리를 포함한 거의 모든 형식을 받되, 받은 것을 열지
 * 않는다. 받으면 폴더를 고르고(처음은 마지막으로 저장한 폴더, 없으면 내려받기) → 파일 작업 큐로 저장 → 결과를 알리고 닫는다.
 *
 * **저장이 끝날 때까지 끝나지 않는다.** 공유의 읽기 권한은 시스템이 이 화면(액티비티)에 준 것이라 화면이 끝나면 거둔다 — 저장 중의 뒤로
 * 가기는 태스크를 뒤로 보낼 뿐이다(`moveTaskToBack`). 끝나면 알림(토스트)을 띄우고 스스로 닫는다.
 *
 * **글만 실은 공유(`EXTRA_TEXT`)는 받지 않는다** — 필터에 `text/plain` 이 없다. 넣으면 브라우저의 주소 공유마다 이 줄이 선다. 그 대가로
 * `text/plain` 으로 오는 `.txt` 파일도 이 줄에 서지 않는다(CLAUDE.md 'Files에 저장').
 *
 * **내보낸 액티비티라 인텐트의 모든 것이 남이 적은 값이다.** 받는 것은 공유의 스트림(URI)뿐이고, extras 를 ViewModel 의 저장 상태로
 * 넘기지 않는다([withoutIntentArgs]). 꺼내다 던지는 꾸러미는 `ClipData` 로 넘어간다([SaveIntent]).
 */
class SaveActivity : ComponentActivity() {

    private val vm: SaveViewModel by viewModels()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = super.defaultViewModelCreationExtras.withoutIntentArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.start(SaveIntent.urisOf(intent))
        // 끝났다 — 알리고 닫는다. 화면(컴포지션)이 아니라 여기서 기다린다: 저장 중에 뒤로 보냈으면 컴포지션은 멈춰 있다.
        lifecycleScope.launch {
            val message = vm.exit.filterNotNull().first()
            if (message.isNotEmpty()) Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
        setContent {
            IroiroTheme {
                SaveScreen(vm, onBackground = { moveTaskToBack(true) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 권한 설정에서 돌아왔을 수 있다.
        vm.recheckPermission()
    }

    /**
     * 이 화면이 맨 위에 있을 때 다시 공유했다(`singleTop` — [OpenActivity.onNewIntent] 와 같은 까닭). 고르는 중이면 새로 받은 것으로 바꾸고,
     * 저장 중이면 그대로 둔다(앞의 저장이 이 화면의 권한으로 읽고 있다).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (vm.restart(SaveIntent.urisOf(intent))) {
            setIntent(intent)
        } else {
            Toast.makeText(applicationContext, R.string.save_busy, Toast.LENGTH_SHORT).show()
        }
    }
}

/** 공유가 실은 URI 들. 판단은 [IncomingSaves.distinctCapped](JVM 시험). */
internal object SaveIntent {

    /** 받을 것 — 겹친 것을 빼고 받은 차례대로. 상한을 넘으면 null. 공유가 아니면 빈 목록. */
    fun urisOf(intent: Intent?): List<Uri>? {
        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
        val found = ArrayList<Uri>()
        // **보낸 쪽의 꾸러미를 믿지 않는다** — 모르는 클래스가 extras 에 들어 있으면 꺼내는 순간 `BadParcelableException`·`ClassCastException`
        // 이다. 그때는 `ClipData`(시스템이 공유의 URI 를 옮겨 담고 읽기 권한을 함께 싣는다)만 본다.
        try {
            if (action == Intent.ACTION_SEND) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(found::add)
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.filterNotNull()?.let(found::addAll)
            }
        } catch (e: RuntimeException) {
            // 그대로 ClipData 로.
        }
        try {
            intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let(found::add) }
        } catch (e: RuntimeException) {
            // 같은 까닭.
        }
        return IncomingSaves.distinctCapped(found, { it.toString() })
    }
}

/** 폴더 고르기와 저장. 액티비티에 묶여 회전을 건넌다(프로세스가 죽으면 처음부터 고른다 — 저장 상태에 아무것도 두지 않는다). */
internal class SaveViewModel(app: Application) : AndroidViewModel(app) {

    /** 고르는 화면의 폴더 줄. */
    data class Folder(val name: String, val path: String)

    /** 볼륨 단추. */
    data class Root(val label: String, val path: String)

    sealed interface Phase {
        data object Loading : Phase

        /** 모든 파일 접근 권한이 없다 — 사용자 폴더에 쓸 수 없다. */
        data object NoPermission : Phase

        data class Picking(
            val dir: String,
            /** 화면에 보일 자리 — '내부 저장소/Download'. */
            val where: String,
            /** 결과 문구의 폴더 이름 — 볼륨 맨 위면 볼륨 이름. */
            val label: String,
            val up: String?,
            val folders: List<Folder>,
            val roots: List<Root>,
        ) : Phase

        data class Saving(val id: String, val label: String) : Phase
    }

    private val prefs = AppPreferences(app)

    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** 화면 위쪽의 '사진.jpg 외 2개'. */
    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    /** 끝낼 때 알릴 한 줄(빈 글이면 말없이). 액티비티가 이것을 보고 닫는다. */
    private val _exit = MutableStateFlow<String?>(null)
    val exit: StateFlow<String?> = _exit.asStateFlow()

    private var uris: List<Uri> = emptyList()
    private var started = false

    /** 받기(이름·첫 폴더)를 하는 잡. 새 공유가 오면 앞의 것을 끊는다 — 두 공유가 섞이지 않게. */
    private var loadJob: Job? = null

    /** 폴더를 여는 잡. 저장을 누르면 끊는다 — 늦게 끝난 열기가 '저장 중' 을 고르는 화면으로 덮지 않게. */
    private var openJob: Job? = null

    /** 폴더를 여는 중이다 — 화면이 '여기에 저장' 을 막는다(지금 보이는 폴더와 곧 보일 폴더가 다르다). */
    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    @Volatile
    private var cleared = false

    /** 큐가 결과를 이 화면에 곧바로 준다. 화면이 사라졌으면 받지 않는다 — 그러면 파일 목록의 결과 줄이 말한다. */
    private val sink = FileOpManager.ResultSink { finished ->
        if (cleared) {
            false
        } else {
            _exit.value = FileOpText.receivedMessage(getApplication(), finished).orEmpty()
            true
        }
    }

    /** 상태를 바꾼다 — **저장 중이면 그대로 둔다.** 늦게 끝난 받기·열기·권한 확인이 저장 화면을 덮지 않게 하는 한 자리다. */
    private fun setPhase(next: Phase) {
        _phase.update { if (it is Phase.Saving) it else next }
    }

    fun start(received: List<Uri>?) {
        if (started) return
        started = true
        load(received, keepDir = null)
    }

    /** 새 공유가 왔다. 저장 중이면 거절한다(false). */
    fun restart(received: List<Uri>?): Boolean {
        if (_phase.value is Phase.Saving) return false
        load(received, keepDir = (_phase.value as? Phase.Picking)?.dir)
        return true
    }

    private fun load(received: List<Uri>?, keepDir: String?) {
        val app = getApplication<Application>()
        loadJob?.cancel()
        openJob?.cancel()
        _opening.value = false
        if (received == null) {
            _exit.value = app.getString(R.string.save_too_many, IncomingSaves.MAX_ITEMS)
            return
        }
        loadJob = viewModelScope.launch {
            val accepted = withContext(IroDispatchers.io) { received.filter { IncomingSaves.accepts(app, it) } }
            if (accepted.isEmpty()) {
                _exit.value = app.getString(R.string.save_nothing)
                return@launch
            }
            val first = withContext(IroDispatchers.io) { IncomingSaves.displayNameOf(app, accepted.first()) }
            // 저장 중에는 받은 목록을 바꾸지 않는다(그 사이 새 공유는 `restart` 가 거절했다 — 겹친 것은 버린다).
            if (_phase.value is Phase.Saving) return@launch
            uris = accepted
            _summary.value = if (accepted.size == 1) first else app.getString(R.string.save_summary_many, first, accepted.size - 1)
            if (!StorageAccess.isGranted()) {
                setPhase(Phase.NoPermission)
                return@launch
            }
            val start = keepDir ?: withContext(IroDispatchers.io) {
                val last = prefs.lastSaveDir.first()
                IncomingSaves.startDir(last, downloadDir(app)) { usable(app, it) }
            }
            open(start)
        }
    }

    /** 권한 설정에서 돌아왔다. */
    fun recheckPermission() {
        if (_phase.value is Phase.NoPermission && StorageAccess.isGranted()) {
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                val app = getApplication<Application>()
                val start = withContext(IroDispatchers.io) {
                    IncomingSaves.startDir(prefs.lastSaveDir.first(), downloadDir(app)) { usable(app, it) }
                }
                open(start)
            }
        }
    }

    /** [dir] 로 간다. 쓸 수 없는 자리면(그사이 지워졌다) 내려받기로. 앞의 열기는 끊는다(빨리 두 번 누르면 나중 것이 이긴다). */
    fun open(dir: String) {
        if (_phase.value is Phase.Saving) return
        val app = getApplication<Application>()
        openJob?.cancel()
        _opening.value = true
        openJob = viewModelScope.launch {
            try {
                val next = withContext(IroDispatchers.io) {
                    val target = if (usable(app, dir)) dir else downloadDir(app)
                    picking(app, target)
                }
                setPhase(next)
            } finally {
                // 끊겨도 끈다 — 다만 뒤에 시작한 열기가 있으면 그것의 몫이다.
                if (openJob === coroutineContext[Job]) _opening.value = false
            }
        }
    }

    /** 고른 폴더에 저장한다 — 여는 중이면 받지 않는다. 큐에 넣고, 그 폴더를 기억한다. */
    fun save() {
        if (_opening.value) return
        val picking = _phase.value as? Phase.Picking ?: return
        openJob?.cancel()
        loadJob?.cancel()
        val id = UUID.randomUUID().toString()
        _phase.value = Phase.Saving(id, picking.label)
        viewModelScope.launch { withContext(IroDispatchers.io) { prefs.setLastSaveDir(picking.dir) } }
        FileOpManager.enqueue(
            getApplication(),
            FileOpManager.Request.SaveIncoming(id, uris, picking.dir, picking.label, sink),
        )
    }

    fun cancelSave() {
        (_phase.value as? Phase.Saving)?.let { FileOpManager.cancel(it.id) }
    }

    /** 고르는 중에 닫는다 — 아무것도 저장하지 않는다. 저장 중에는 닫지 않는다(공유의 읽기 권한이 이 화면과 함께 사라진다). */
    fun dismiss() {
        if (_phase.value is Phase.Saving) return
        _exit.value = ""
    }

    /**
     * 화면이 끝났다. 줄 선 채 아직 돌지 않은 저장은 거둔다(`withdraw`) — 읽기 권한이 이 화면과 함께 사라져 돌아도 모두 '못 읽음' 이다.
     * 이미 도는 것은 끊지 않는다(놓은 것은 남고, 남은 항목은 '못 읽음' 으로 끝난다).
     */
    override fun onCleared() {
        cleared = true
        (_phase.value as? Phase.Saving)?.let { FileOpManager.withdraw(it.id) }
    }

    private fun picking(app: Application, dir: String): Phase.Picking {
        val volumes = VolumeRegistry.volumes(app)
        val roots = volumes.map { it.path }
        val volume = VolumeRegistry.volumeOf(volumes, dir)
        val atRoot = volume != null && volume.path == dir
        val label = if (volume != null && atRoot) volume.label else File(dir).name
        val where = if (volume == null) dir else volume.label + dir.removePrefix(volume.path)
        val up = if (volume == null || atRoot) null else File(dir).parent?.takeIf { IncomingSaves.isAllowedDestination(it, roots) }
        val folders = (File(dir).listFiles() ?: emptyArray())
            .filter { f ->
                !f.name.startsWith('.') && f.isDirectory && IncomingSaves.isAllowedDestination(f.path, roots)
            }
            .map { Folder(PathNames.forDisplay(it.name), it.path) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return Phase.Picking(
            dir = dir,
            where = where,
            label = label,
            up = up,
            folders = folders,
            roots = volumes.map { Root(it.label, it.path) },
        )
    }

    private fun downloadDir(app: Application): String {
        val primary = VolumeRegistry.volumes(app).firstOrNull { it.isPrimary }?.path ?: "/storage/emulated/0"
        return "$primary/${android.os.Environment.DIRECTORY_DOWNLOADS}"
    }

    /** 쓸 수 있는 폴더인가 — 정규화한 자리가 같고(링크를 건너지 않는다) 폴더이며 받는 자리다. 디스크를 읽는다. */
    private fun usable(app: Application, dir: String): Boolean {
        val file = File(dir)
        val canonical = try {
            file.canonicalPath
        } catch (e: IOException) {
            return false
        }
        return canonical == dir && file.isDirectory &&
            IncomingSaves.isAllowedDestination(canonical, VolumeRegistry.volumes(app).map { it.path })
    }
}

@Composable
private fun SaveScreen(vm: SaveViewModel, onBackground: () -> Unit) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    BackHandler {
        when (val p = phase) {
            // 저장 중에 끝내면 권한을 거둔다 — 뒤로 보낼 뿐이다.
            is SaveViewModel.Phase.Saving -> onBackground()
            is SaveViewModel.Phase.Picking -> if (p.up != null) vm.open(p.up) else vm.dismiss()
            else -> vm.dismiss()
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Text(stringResource(R.string.save_label), style = MaterialTheme.typography.titleLarge)
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.padding(top = 12.dp))
            when (val p = phase) {
                SaveViewModel.Phase.Loading -> Unit
                SaveViewModel.Phase.NoPermission -> NoPermission(onDismiss = vm::dismiss)
                is SaveViewModel.Phase.Picking -> Picking(p, vm)
                is SaveViewModel.Phase.Saving -> Saving(p, vm)
            }
        }
    }
}

@Composable
private fun NoPermission(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.save_no_permission), style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_cancel)) }
        Button(onClick = {
            for (intent in StorageAccess.requestIntents(context)) {
                if (runCatching { context.startActivity(intent) }.isSuccess) break
            }
        }) { Text(stringResource(R.string.save_grant)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Picking(p: SaveViewModel.Phase.Picking, vm: SaveViewModel) {
    if (p.roots.size > 1) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (root in p.roots) TextButton(onClick = { vm.open(root.path) }) { Text(root.label) }
        }
    }
    Text(
        PathNames.forDisplay(p.where),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    HorizontalDivider(Modifier.padding(top = 8.dp))
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        p.up?.let { up ->
            item(key = "..") { FolderRow(stringResource(R.string.save_up), onClick = { vm.open(up) }) }
        }
        items(p.folders, key = { it.path }) { f -> FolderRow(f.name, onClick = { vm.open(f.path) }) }
        if (p.folders.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.save_no_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.save_cancel)) }
        // 여는 중에는 막는다 — 보이는 폴더와 곧 열릴 폴더가 다르다.
        val opening by vm.opening.collectAsStateWithLifecycle()
        Button(onClick = vm::save, enabled = !opening) { Text(stringResource(R.string.save_here)) }
    }
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Text(
        name,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

@Composable
private fun Saving(p: SaveViewModel.Phase.Saving, vm: SaveViewModel) {
    val state by FileOpManager.state.collectAsStateWithLifecycle()
    val running = (state as? FileOpManager.State.Running)?.takeIf { it.id == p.id }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (running == null) {
            // 앞의 파일 작업(복사 등)이 끝나기를 기다린다 — 큐는 한 번에 하나다.
            Text(stringResource(R.string.save_waiting), style = MaterialTheme.typography.bodyLarge)
            IroProgress(progress = null, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        } else {
            val pr = running.progress
            val context = LocalContext.current
            Text(stringResource(R.string.save_saving_to, p.label), style = MaterialTheme.typography.bodyLarge)
            // 제공자가 크기를 말하지 않았으면(0) 정하지 않은 막대 — 받은 바이트를 글로 보인다.
            val known = pr.bytesTotal > 0 && pr.filesTotal > 0
            IroProgress(
                progress = if (known) {
                    {
                        val inFile = (pr.bytesDone.toFloat() / pr.bytesTotal).coerceIn(0f, 1f)
                        (pr.filesDone + inFile) / pr.filesTotal
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
            Text(
                stringResource(R.string.save_progress, (pr.filesDone + 1).coerceAtMost(pr.filesTotal), pr.filesTotal) +
                    " · " + PathNames.forDisplay(pr.currentName) +
                    (if (!known && pr.bytesDone > 0) " · " + android.text.format.Formatter.formatShortFileSize(context, pr.bytesDone) else ""),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.save_closes_when_done),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        TextButton(onClick = vm::cancelSave, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.save_cancel)) }
    }
}
