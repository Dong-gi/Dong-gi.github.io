package io.github.donggi.iroirobrowser.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.torrent.TorrentConfig
import io.github.donggi.iroirobrowser.torrent.TorrentEngine
import io.github.donggi.iroirobrowser.torrent.TorrentEnvironment
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentException
import io.github.donggi.iroirobrowser.torrent.TorrentInfo
import io.github.donggi.iroirobrowser.torrent.TorrentListener
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/**
 * 토렌트 — **프로세스에 엔진 하나**(`:torrent` 의 [TorrentEngine], 루트 `noBackupFilesDir/torrent` — 백업·이전에서 빠진다). 내려받기의
 * [DownloadCenter] 와 같은 자리(앱 하나에 하나, [WebApp.torrents])에 두고, 포그라운드 서비스([TorrentService])는 받는 동안 프로세스를 붙잡고
 * CPU·Wi-Fi 를 붙잡는 일만 한다. 명세는 '서비스 하나에 엔진 하나' 였는데 엔진을 서비스 밖에 둔 까닭: 목록 화면은 받지 않는 동안(멈춘 것·다 받은
 * 것)에도 토렌트를 보여야 하고 `.torrent` 를 읽어야 하는데, 서비스가 엔진을 쥐면 그때마다 서비스를 띄워야 한다 — 그러면 받을 것이 없을 때 서비스를
 * 내린다는 약속과 부딪친다. 엔진은 **쓸 때 열고, 쓰지 않으면 닫는다** — 목록 화면이 없고 받으려는 토렌트·옮길 파일이 없으면 [IDLE_MS] 뒤에 닫는다
 * (엔진은 멈춘 토렌트도 1초마다 상태를 짓는다 — 쓰지 않는 동안 그것을 돌리지 않게).
 *
 * - 엔진의 공개 함수는 루프에 넘겨 결과를 기다린다(막힌다) — 모두 일꾼 하나([ops])에서 차례로 부르고 답은 메인 스레드로 준다.
 * - **되살린 것은 멈춤**(엔진의 `start()` — 사용자 결정 ⑰: 앱이 죽은 뒤엔 사용자가 이어받는다). 그 확인(크기가 어긋난 파일의 해시)이 끝나면
 *   토렌트마다 마저 할 일을 한다([reconcileOne] — 옮기다 끊긴 파일).
 * - 다 받은 파일은 [TorrentMover] 가 공용 '내려받기' 로 옮긴다.
 * - 마그넷은 **파일을 고르지 않은 채** 넣는다 — 메타데이터가 오면 파일이 하나면 그것을 받고, 여럿이면 멈추고 사용자에게 묻는다(늘 순차로 받으니
 *   묻지 않으면 첫 파일부터 받는다 — `.torrent` 는 넣기 전에 묻는다).
 * - 망: 엔진의 망 조건 훅이 'Wi-Fi 에서만'(설정, 기본 끔)과 망이 있는지를 묻고([TorrentRules.netWait]), 기본 망이 바뀌면 엔진에 알린다.
 * - 엔진의 로그에는 피어 IP 가 실린다 — **디버그 빌드의 logcat 에만**, 파일로 남기지 않는다(릴리스는 버린다).
 */
class TorrentCenter(context: Context) {

    fun interface Listener {
        fun onChanged()
    }

    /** [inspect] 의 답. */
    sealed class Inspected {
        class Ok(val info: TorrentInfo) : Inspected()
        class Refused(val error: TorrentError, val detail: String?) : Inspected()
    }

    /** [add]·[addMagnet] 의 답. */
    sealed class Added {
        class Ok(val id: String) : Added()
        class Refused(val error: TorrentError, val detail: String?) : Added()
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val root = File(app.noBackupFilesDir, "torrent")
    internal val records = TorrentRecords(File(app.noBackupFilesDir, "torrent-app"))
    private val ops = Executors.newSingleThreadExecutor { r -> Thread(r, "web-torrent").apply { isDaemon = true } }
    private val mover = TorrentMover(app, this)
    private val notices = TorrentNotices(app)

    @Volatile
    private var engine: TorrentEngine? = null

    /** 엔진을 열 때마다 오른다 — 닫은 엔진의 늦은 소식을 버린다. */
    private val generation = AtomicInteger()
    private val statuses = ConcurrentHashMap<String, TorrentStatus>()

    /** 지운 토렌트 — 엔진의 늦은 상태 소식이 목록에 되살리지 않게. */
    private val removed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 되살린 뒤 확인(해시)을 기다리는 토렌트 — 끝나면 마저 할 일을 한다. */
    private val awaitingCheck: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 이 프로세스에서 받으려 했던 토렌트 — 오류·다 받음 알림은 이것만(되살린 것이 열 때마다 알림을 내지 않게). */
    private val runningHere: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val observers = AtomicInteger()
    private val inFlight = AtomicInteger()
    private val changePending = AtomicBoolean()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** 엔진을 닫는 시계를 걸어 두었다(메인 스레드에서만). */
    private var idlePosted = false
    private val idleCheck = Runnable { checkIdle() }

    // ── 보기(어느 스레드에서나) ──────────────────────────────────────────────────────────────────────────

    fun addListener(l: Listener) {
        listeners += l
    }

    fun removeListener(l: Listener) {
        listeners -= l
    }

    /** 목록 — 나중에 더한 것이 위. 엔진이 닫혀 있으면 비었다(목록 화면이 [attach] 로 연다). */
    fun statuses(): List<TorrentStatus> = statuses.values.sortedByDescending { it.addedAtMs }

    fun status(id: String): TorrentStatus? = statuses[id]

    /** 메타데이터(파일 목록) — 엔진이 열려 있을 때만. */
    fun info(id: String): TorrentInfo? = engine?.info(id)

    /** 엔진 일이 남았거나 옮길 파일이 있다 — 서비스를 내리지 않고 엔진을 닫지 않는다. */
    val busy: Boolean get() = inFlight.get() > 0 || mover.busy

    /** 옮길 파일이 있다 — 그동안은 CPU 를 붙잡는다(화면이 꺼진 채 큰 파일을 옮긴다). */
    val moving: Boolean get() = mover.busy

    /** 엔진의 상태 밖에서 앱이 아는 것(글을 고를 때). 기록은 엔진을 열 때 읽어 두었다. */
    internal fun extra(s: TorrentStatus): TorrentTexts.Extra = TorrentTexts.Extra(
        choiceNeeded = records.needsChoice(s.id),
        moving = mover.countFor(s.id),
        moveFailed = records.failedOf(s.id).size,
        netWait = if (s.state == TorrentState.NETWORK_WAIT) netWait() else null,
    )

    fun movedCount(id: String): Int = records.movedOf(id).size

    fun failedMoves(id: String): Map<Int, String> = records.failedOf(id)

    fun needsChoice(id: String): Boolean = records.needsChoice(id)

    /** 지금 망에서 받아도 되면 null, 아니면 기다리는 까닭 — 엔진의 망 조건 훅. */
    internal fun netWait(): TorrentRules.NetWait? = TorrentRules.netWait(WebApp.of(app).settings.torrentWifiOnly, currentNet())

    /** 지금 기본 망이 Wi-Fi 인가 — 서비스가 Wi-Fi 잠금을 걸지 고른다. */
    fun onWifi(): Boolean = currentNet()?.wifi == true

    // ── 목록 화면 ────────────────────────────────────────────────────────────────────────────────────────

    /** 목록 화면이 보인다 — 저장된 토렌트가 있으면 엔진을 연다(되살린 것은 멈춤). 메인 스레드. */
    fun attach() {
        observers.incrementAndGet()
        cancelIdle()
        submit { if (engine != null || hasSaved()) ensureEngine() }
    }

    /** 목록 화면이 가려졌다. 메인 스레드. */
    fun detach() {
        observers.decrementAndGet()
        considerIdle()
    }

    // ── 넣기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** `.torrent` 를 넣지 않고 읽는다(묻기 화면 — 이름·파일 목록·크기). 받지 않는 것(순수 v2·private·깨진 것)은 그 까닭. 답은 메인 스레드. */
    fun inspect(bytes: ByteArray, done: (Inspected) -> Unit) = submit {
        val result = try {
            Inspected.Ok(ensureEngine().inspect(bytes))
        } catch (e: TorrentException) {
            Inspected.Refused(e.error, e.detail)
        } catch (e: RuntimeException) {
            Inspected.Refused(TorrentError.ENGINE_CLOSED, e.toString())
        }
        main.post { done(result) }
    }

    /** `.torrent` 를 넣는다 — [selection] 의 파일만 받는다. 메인 스레드(앞에 있을 때 — 서비스를 곧바로 세운다). */
    fun add(bytes: ByteArray, selection: Set<Int>, done: (Added) -> Unit) {
        TorrentService.start(app)
        submit {
            val result = try {
                val id = ensureEngine().add(bytes, selection, paused = false)
                removed.remove(id)
                runningHere += id
                trace("torrent added $id files=${selection.size}")
                Added.Ok(id)
            } catch (e: TorrentException) {
                Added.Refused(e.error, e.detail)
            } catch (e: RuntimeException) {
                Added.Refused(TorrentError.ENGINE_CLOSED, e.toString())
            }
            main.post { done(result) }
        }
    }

    /**
     * 마그넷을 넣는다 — 파일을 고르지 않은 채 메타데이터부터 받는다(그 차례가 바뀌지 않게 멈춘 채 넣고 고르기를 비운 뒤 이어받는다). 메타데이터가
     * 오면 [afterMetadata]. 메인 스레드.
     */
    fun addMagnet(uri: String, done: (Added) -> Unit) {
        TorrentService.start(app)
        submit {
            val result = try {
                val e = ensureEngine()
                val id = e.addMagnet(uri, paused = true)
                removed.remove(id)
                records.setAwaiting(id, true)
                e.selectFiles(id, emptySet())
                e.resume(id)
                runningHere += id
                trace("torrent magnet added $id")
                Added.Ok(id)
            } catch (e: TorrentException) {
                Added.Refused(e.error, e.detail)
            } catch (e: RuntimeException) {
                Added.Refused(TorrentError.ENGINE_CLOSED, e.toString())
            }
            main.post { done(result) }
        }
    }

    // ── 다루기 ───────────────────────────────────────────────────────────────────────────────────────────

    /** 멈춘다. [notice] 가 있으면(알림의 단추·시간 제한 — 사용자가 화면을 보지 않는다) 그 글로 '이어받기' 알림을 남긴다. */
    fun pause(id: String, notice: String? = null) = submit {
        val e = engine ?: return@submit
        e.pause(id)
        if (notice != null) notices.paused(id, titleOf(id), notice)
    }

    /** 받으려는 것을 모두 멈춘다(알림의 '모두 일시 정지'·dataSync 시간 제한). */
    fun pauseAll(notice: String? = null) = submit {
        val e = engine ?: return@submit
        for (s in e.statuses()) {
            if (!TorrentRules.isRunning(s.state)) continue
            runCatching { e.pause(s.id) }
            if (notice != null) notices.paused(s.id, titleOf(s.id), notice)
        }
    }

    /** 이어받는다(엔진이 닫혀 있으면 연다). 파일을 골라야 하는 마그넷은 이어받지 않는다 — 화면이 고르는 창을 띄운다. 메인 스레드. */
    fun resume(id: String) {
        TorrentService.start(app)
        submit {
            val e = ensureEngine()
            if (records.needsChoice(id)) return@submit
            e.resume(id)
            runningHere += id
            notices.cancel(id)
        }
    }

    /** 받을 파일을 고른다. [resume] 이면 이어받는다(마그넷의 고르기를 마쳤다). 메인 스레드. */
    fun selectFiles(id: String, files: Set<Int>, resume: Boolean) {
        if (resume) TorrentService.start(app)
        submit {
            val e = ensureEngine()
            e.selectFiles(id, files)
            if (records.needsChoice(id)) records.setChoice(id, false)
            if (resume) {
                e.resume(id)
                runningHere += id
                notices.cancel(id)
            }
        }
    }

    /**
     * 지운다 — 앱 안에 받던 것은 늘 지운다(앱의 사적 저장소라 사용자가 꺼낼 수 없다 — 남기면 자리만 차지한다). [deleteMoved] 면 '내려받기' 로 옮긴
     * 파일도(이 앱이 만든 항목만 — 사용자가 옮기거나 다른 앱이 바꿨으면 지우지 못한다). 옮기던 일은 버린다. [done] 은 지운 '내려받기' 파일 수.
     */
    fun remove(id: String, deleteMoved: Boolean, done: (Int) -> Unit) = submit {
        // 엔진을 먼저 연다 — 열면서 되살린 상태가 목록에 들어온 뒤에 지워야 남지 않는다(여는 것은 지운 표시도 비운다).
        val e = try {
            ensureEngine()
        } catch (ex: TorrentException) {
            null
        }
        removed += id
        statuses.remove(id)
        awaitingCheck.remove(id)
        mover.cancel(id)
        // 옮기던 파일이 마무리(드러내기·기록)를 마칠 틈 — 그 뒤에 기록을 읽어야 방금 옮긴 파일도 함께 지운다.
        mover.drain(MOVER_DRAIN_MS)
        val moved = records.movedOf(id)
        try {
            e?.remove(id, deleteData = true)
        } catch (ex: TorrentException) {
            // 이미 없다 — 기록만 정리한다.
        }
        var deleted = 0
        if (deleteMoved) for (media in moved.values) if (MediaStoreWriter.deleteCounted(app, MediaStoreWriter.uriOf(media))) deleted++
        records.pendingSnapshot()[id]?.values?.forEach { MediaStoreWriter.delete(app, MediaStoreWriter.uriOf(it)) }
        records.forget(id)
        notices.cancel(id)
        trace("torrent removed $id moved=${moved.size} deleted=$deleted")
        main.post { done(deleted) }
    }

    /** 옮기지 못한 파일을 다시 옮긴다. 메인 스레드. */
    fun retryMoves(id: String) {
        TorrentService.start(app)
        submit {
            ensureEngine()
            for (index in records.failedOf(id).keys) {
                records.clearFailed(id, index)
                mover.enqueue(id, index)
            }
        }
    }

    /** 'Wi-Fi 에서만' 을 바꿨다 — 엔진이 망 조건을 곧바로 다시 묻는다. */
    fun settingsChanged() {
        engine?.networkChanged()
        changedSoon()
    }

    // ── 옮기기([TorrentMover])가 부르는 것 ──────────────────────────────────────────────────────────────

    internal fun engineOrNull(): TorrentEngine? = engine

    internal fun onMoveFailed(id: String, reason: String) {
        if (id !in removed) notices.moveFailed(id, titleOf(id), reason)
    }

    // ── 엔진 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** [ops] 에서만. 닫혀 있으면 연다 — 저장된 토렌트를 되살리고(받던 것도 멈춤), 망을 지켜보고, 옮기다 끊긴 것을 마저 한다. */
    private fun ensureEngine(): TorrentEngine {
        engine?.let { return it }
        val gen = generation.incrementAndGet()
        val e = TorrentEngine(root, TorrentConfig(), environment(), Events(gen))
        val restored = try {
            e.start()
        } catch (ex: Exception) {
            runCatching { e.close() }
            throw TorrentException(TorrentError.ENGINE_CLOSED, ex.toString())
        }
        engine = e
        removed.clear()
        statuses.clear()
        awaitingCheck.clear()
        for (s in restored) statuses[s.id] = s
        watchNetwork()
        // 옮기다 만 감춘 항목·엔진에 없는 토렌트의 기록 — 확인을 기다리지 않는다.
        for (fix in TorrentRules.leftovers(restored.mapTo(HashSet()) { it.id }, records.movedSnapshot(), records.pendingSnapshot())) apply(e, fix)
        // 토렌트마다 확인(크기가 어긋난 파일의 해시)이 끝난 뒤에 마저 한다 — 확인 전의 '다 받음' 은 아직 믿을 수 없다.
        for (s in restored) if (s.state == TorrentState.CHECKING) awaitingCheck += s.id else reconcileOne(e, s)
        trace("torrent engine open restored=${restored.size}")
        return e
    }

    private fun environment() = TorrentEnvironment(
        // 엔진의 HTTPS(트래커·웹 시드) — 엔진이 스스로 하는 이름 확인 위에 플랫폼의 확인을 한 번 더.
        hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
        networkAllowed = { netWait() == null },
        log = { message -> if (BuildConfig.DEBUG) Log.d(ENGINE_TAG, message) },
    )

    /** 토렌트 하나의 마저 할 일(확인이 끝난 상태로) — [TorrentRules.reconcile]. [ops] 에서만. */
    private fun reconcileOne(e: TorrentEngine, s: TorrentStatus) {
        if (s.id in removed) return
        val fixes = TorrentRules.reconcile(s, records.movedOf(s.id)) { index -> e.fileOf(s.id, index)?.takeIf { it.isFile }?.length() ?: -1L }
        for (fix in fixes) apply(e, fix)
        // 옮기는 동안 앱이 뒤로 가도 끝내게 — 엔진은 목록 화면(앞에 있다)이 열었거나 받는 중(서비스가 서 있다)이다.
        if (fixes.any { it is TorrentRules.Fix.Move }) main.post { TorrentService.start(app) }
    }

    private fun apply(e: TorrentEngine, fix: TorrentRules.Fix) {
        try {
            when (fix) {
                is TorrentRules.Fix.DropPending -> {
                    MediaStoreWriter.delete(app, MediaStoreWriter.uriOf(fix.mediaId))
                    records.clearPending(fix.id, fix.index)
                }
                is TorrentRules.Fix.Forget -> records.forget(fix.id)
                is TorrentRules.Fix.DeleteLeftover -> e.fileOf(fix.id, fix.index)?.delete()
                is TorrentRules.Fix.Release -> {
                    if (fix.unselect) {
                        val keep = statuses[fix.id]?.files?.filter { it.selected && it.index != fix.index }?.map { it.index }?.toSet()
                        if (keep != null) e.selectFiles(fix.id, keep)
                    }
                    e.releaseFile(fix.id, fix.index)
                    e.fileOf(fix.id, fix.index)?.delete()
                }
                is TorrentRules.Fix.Move -> mover.enqueue(fix.id, fix.index)
            }
            trace("torrent fix $fix")
        } catch (ex: TorrentException) {
            trace("torrent fix failed $fix ${ex.error}")
        }
    }

    /**
     * 마그넷의 메타데이터가 왔다. 우리가 고르기를 비워 넣은 것만([TorrentRecords.isAwaiting]) — 고를 파일이 하나면 그것을 받고, 여럿이면 멈추고
     * 묻는다(알림 — 목록 화면이 앞에 있으면 고르는 창을 띄운다).
     */
    private fun afterMetadata(id: String, info: TorrentInfo) {
        if (id in removed || !records.isAwaiting(id)) return
        val e = engine ?: return
        records.setAwaiting(id, false)
        val auto = TorrentRules.autoSelection(info.files)
        if (auto != null) {
            e.selectFiles(id, auto)
            trace("torrent metadata $id auto=${auto.size}")
            return
        }
        e.pause(id)
        records.setChoice(id, true)
        notices.chooseFiles(id, titleOf(id))
        trace("torrent metadata $id ask files=${info.files.size}")
    }

    private fun closeEngine() {
        val e = engine ?: return
        engine = null
        generation.incrementAndGet()
        unwatchNetwork()
        runCatching { e.close() }
        statuses.clear()
        awaitingCheck.clear()
        trace("torrent engine closed")
        changedSoon()
    }

    private fun hasSaved(): Boolean =
        File(root, "state").list()?.any { it.endsWith(".resume") || it.endsWith(".torrent") } == true

    /** 엔진의 소식 — 엔진의 알림 스레드에서 온다. 닫은 엔진([gen] 이 다르다)의 늦은 소식은 버린다. */
    private inner class Events(private val gen: Int) : TorrentListener {
        private val current: Boolean get() = gen == generation.get()

        override fun onStatus(status: TorrentStatus) {
            if (!current || status.id in removed) return
            statuses[status.id] = status
            if (TorrentRules.isRunning(status.state) && status.state != TorrentState.CHECKING) runningHere += status.id
            if (status.state != TorrentState.CHECKING && awaitingCheck.remove(status.id)) {
                submit { engine?.let { e -> if (e === engineOf(gen)) reconcileOne(e, status) } }
            }
            changedSoon()
        }

        override fun onMetadata(id: String, info: TorrentInfo) {
            if (current) submit { afterMetadata(id, info) }
        }

        override fun onFileCompleted(id: String, fileIndex: Int, file: File) {
            if (current && id !in removed) mover.enqueue(id, fileIndex)
        }

        override fun onFinished(id: String) {
            if (current && id !in removed && id in runningHere) notices.finished(id, titleOf(id))
        }

        override fun onError(id: String, error: TorrentError, detail: String?) {
            trace("torrent error $id $error")
            if (current && id !in removed && id in runningHere) notices.error(id, titleOf(id), app.getString(TorrentTexts.errorRes(error)))
        }
    }

    private fun engineOf(gen: Int): TorrentEngine? = if (gen == generation.get()) engine else null

    // ── 망 ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun currentNet(): TorrentRules.Net? {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        return TorrentRules.Net(
            internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        )
    }

    /** 기본 망이 바뀌면 엔진이 망 조건을 곧바로 다시 묻게(엔진도 5초마다 묻는다 — 화면이 꺼져 이 소식이 늦어도). [ops] 에서. */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = poke()

            override fun onLost(network: Network) = poke()

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = poke()
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: RuntimeException) {
            // 듣개가 너무 많다(TooManyRequestsException) — 엔진이 5초마다 스스로 묻는다.
        }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private fun poke() {
        engine?.networkChanged()
        changedSoon()
    }

    // ── 차례·알림 ────────────────────────────────────────────────────────────────────────────────────────

    /** 엔진 일을 [ops] 에 줄 세운다. 실패는 기록만 하고(앱을 죽이지 않는다), 끝나면 화면에 알린다. */
    private fun submit(block: () -> Unit) {
        inFlight.incrementAndGet()
        try {
            ops.execute {
                try {
                    block()
                } catch (e: TorrentException) {
                    trace("torrent op failed ${e.error} ${e.detail ?: ""}")
                } catch (e: RuntimeException) {
                    trace("torrent op failed $e")
                } finally {
                    inFlight.decrementAndGet()
                    changedSoon()
                }
            }
        } catch (e: RejectedExecutionException) {
            inFlight.decrementAndGet()
        }
    }

    /** 화면·서비스에 알린다 — 엔진은 토렌트마다 1초에 한 번 상태를 내므로 모아서 [CHANGE_MS] 에 한 번. */
    internal fun changedSoon() {
        if (!changePending.compareAndSet(false, true)) return
        main.postDelayed({
            changePending.set(false)
            listeners.forEach { it.onChanged() }
            considerIdle()
        }, CHANGE_MS)
    }

    /** 쓰지 않으면 [IDLE_MS] 뒤에 엔진을 닫는다. 메인 스레드. */
    private fun considerIdle() {
        if (engine == null) return
        if (TorrentRules.idle(observers.get(), statuses.values.map { it.state }, busy)) {
            if (!idlePosted) {
                idlePosted = true
                main.postDelayed(idleCheck, IDLE_MS)
            }
        } else {
            cancelIdle()
        }
    }

    private fun cancelIdle() {
        idlePosted = false
        main.removeCallbacks(idleCheck)
    }

    private fun checkIdle() {
        idlePosted = false
        if (!TorrentRules.idle(observers.get(), statuses.values.map { it.state }, busy)) return
        try {
            ops.execute {
                // 줄을 서는 사이 화면이 열렸거나 일이 생겼으면 그대로 둔다(닫은 뒤에 온 일은 엔진을 다시 연다).
                if (TorrentRules.idle(observers.get(), statuses.values.map { it.state }, mover.busy || inFlight.get() > 0)) closeEngine()
            }
        } catch (e: RejectedExecutionException) {
            // 닫히는 중
        }
    }

    private fun titleOf(id: String): String =
        statuses[id]?.let { TorrentTexts.text(app, TorrentTexts.title(it)) } ?: app.getString(R.string.torrent_unnamed, id.take(8))

    internal fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.i("WebUI", message)
    }

    private companion object {
        /** 쓰지 않는 엔진을 닫기까지. */
        const val IDLE_MS = 60_000L

        /** 화면·서비스에 상태를 알리는 간격(모은다). */
        const val CHANGE_MS = 500L

        /** 지울 때 옮기던 파일의 마무리를 기다리는 한도. */
        const val MOVER_DRAIN_MS = 5_000L

        /** 엔진의 로그(피어 IP 가 실린다 — 디버그의 logcat 에만). */
        const val ENGINE_TAG = "WebTorrent"
    }
}
