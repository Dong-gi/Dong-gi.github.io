package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.FileStatus
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentException
import io.github.donggi.iroirobrowser.torrent.TorrentInfo
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.torrent.dht.Dht
import io.github.donggi.iroirobrowser.torrent.meta.MagnetLink
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.mse.MseInitiator
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import io.github.donggi.iroirobrowser.torrent.piece.PiecePicker
import io.github.donggi.iroirobrowser.torrent.storage.AtomicFiles
import io.github.donggi.iroirobrowser.torrent.storage.ResumeData
import io.github.donggi.iroirobrowser.torrent.storage.Storage
import io.github.donggi.iroirobrowser.torrent.tracker.AnnounceEvent
import io.github.donggi.iroirobrowser.torrent.tracker.AnnounceRequest
import io.github.donggi.iroirobrowser.torrent.tracker.Announcer
import io.github.donggi.iroirobrowser.torrent.util.Hex
import io.github.donggi.iroirobrowser.torrent.wire.ExtensionHandshake
import io.github.donggi.iroirobrowser.torrent.wire.Inbound
import io.github.donggi.iroirobrowser.torrent.wire.MetadataMessage
import io.github.donggi.iroirobrowser.torrent.wire.Outbound
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.RejectedExecutionException

/**
 * 토렌트 하나 — 이벤트 루프에서만 쓴다(디스크 일은 [EngineCore.disk] 에 넘기고 결과를 루프로 받는다).
 *
 * **올리기 0(불변식).** 우리가 보내는 것은 [Outbound] 뿐이다: 해시를 확인한 조각만 bitfield·HAVE 로 알리고, 모든 피어를 choke 로 둔다
 * (unchoke 를 보내는 길이 없다), Fast 확장이면 들어온 request 를 reject 로 답한다, ut_metadata 요청은 거절하고 metadata_size 를 알리지
 * 않는다, announce 의 uploaded 는 늘 0, 다 받으면 completed(토렌트 전체를 받았을 때)·stopped 를 보내고 모든 연결을 끊는다.
 */
internal class TorrentSession(
    private val core: EngineCore,
    override val infoHash: ByteArray,
    private var magnet: MagnetLink?,
    meta: Metainfo?,
    resume: ResumeData?,
    initialSelection: Set<Int>?,
    startPaused: Boolean,
    val addedAtMs: Long,
) : PeerConnection.Host {

    val id: String = Hex.encode(infoHash)
    private val loop = core.loop
    private val env = core.env
    private val config = core.config

    /** 다른 스레드(엔진의 공개 함수)도 읽는다 — Metainfo 는 바뀌지 않는 값이라 volatile 로 넘긴다. */
    @Volatile
    var meta: Metainfo? = meta
        private set
    private var storage: Storage? = null
    private var picker: PiecePicker? = null
    private var wanted = BooleanArray(0)
    private var wantedTotal = 0
    private var fileDoneBytes = LongArray(0)

    private var selection: Set<Int>? = initialSelection ?: resume?.selected
    private val materialized = HashSet<Int>()
    private val released = HashSet<Int>()
    private val completedFiles = HashSet<Int>()

    // 상태
    var userPaused = startPaused || (resume != null && resume.state != "completed")
        private set
    private var completed = resume?.state == "completed"
    private var error: TorrentError? = null
    private var errorDetail: String? = null
    private var checking = false
    private var admitted = false
    private var networkAllowed = true
    private var networking = false
    private var removed = false
    private var dirty = false
    private var lastSaveAt = 0L
    private var lastStatusAt = 0L
    private var tickTimer: EventLoop.Timer? = null

    // 피어
    private val connections = LinkedHashMap<InetSocketAddress, PeerConnection>()
    private val connectedIps = HashSet<InetAddress>()
    private val book = PeerBook()
    private var peerIdBytes: ByteArray = newPeerId()
    override val ourPeerId: ByteArray get() = peerIdBytes
    private var announcer: Announcer? = null
    private var lookup: Dht.Lookup? = null
    private var nextDhtAt = 0L
    private var dhtEmptyRounds = 0
    private var webSeeds: List<WebSeed> = emptyList()
    private var metadataExchange: MetadataExchange? = if (meta == null) MetadataExchange(infoHash) else null
    private val pendingWrites = HashMap<Int, Int>()
    private val verifying = HashSet<Int>()
    private var resumeData: ResumeData? = resume

    // 통계
    private var downloadedBytes = resume?.downloaded ?: 0L
    private val rate = RateMeter()
    private var hashFailures = 0
    private var mseAttempts = 0
    private var mseSucceeded = 0
    private var plainFallbacks = 0
    private val sources = HashMap<String, Int>()

    @Volatile
    var status: TorrentStatus = buildStatus()
        private set

    override val pieceCount: Int get() = meta?.pieceCount ?: 0

    // ── 시작과 멈춤 ─────────────────────────────────────────────────────────

    /** 엔진이 세션을 만든 직후(루프에서). 메타데이터가 있으면 저장소를 세우고 이어받기 자료를 확인한다. */
    fun open() {
        tickTimer = loop.schedule(TICK_MS) { tick() }
        val m = meta
        if (m != null) setUpMetadata(m, resumeData, fromMagnet = false)
        publish(force = true)
    }

    /** 이 토렌트가 받으려 하는가(엔진의 차례 정하기). */
    val wantsToRun: Boolean get() = !userPaused && !completed && error == null && !removed

    fun setAdmitted(on: Boolean) {
        if (admitted == on) return
        admitted = on
        reevaluate()
    }

    fun setNetworkAllowed(on: Boolean) {
        if (networkAllowed == on) return
        networkAllowed = on
        reevaluate()
    }

    /** 네트워크를 돌릴지 다시 본다. */
    private fun reevaluate() {
        val should = admitted && wantsToRun && networkAllowed && !checking
        if (should && !networking) startNetworking() else if (!should && networking) stopNetworking(completedEvent = false)
        publish(force = true)
    }

    private fun startNetworking() {
        networking = true
        peerIdBytes = newPeerId()
        val m = meta
        val tiers = m?.trackers ?: magnet?.trackers?.map { listOf(it) }.orEmpty()
        announcer = Announcer(
            loop, core.io, core.httpTracker, core.udpTracker, env.resolver, env.addressFilter, tiers, env.random,
            requestFor = ::announceRequest, onPeers = { addPeers(it, "tracker") }, log = env.log,
        ).also { if (!it.isEmpty) it.start() }
        nextDhtAt = loop.now()
        magnet?.peers?.let(::resolveMagnetPeers)
        if (m != null) setUpWebSeeds(m)
        tick()
    }

    private fun stopNetworking(completedEvent: Boolean) {
        networking = false
        announcer?.stop(completedEvent)
        announcer = null
        lookup?.cancel()
        lookup = null
        for (ws in webSeeds) ws.cancelled = true
        for (c in connections.values.toList()) c.close("stopped", violation = false)
        connections.clear()
        connectedIps.clear()
        core.dht?.idle()
    }

    fun pause() {
        if (userPaused) return
        userPaused = true
        reevaluate()
        saveResume()
    }

    fun resume() {
        error = null
        errorDetail = null
        userPaused = false
        val p = picker
        completed = p != null && p.allSelectedDone()
        reevaluate()
        saveResume()
    }

    /** 지운다. [deleteData] 면 받은 파일도. 끝나면 [done](루프에서). */
    fun remove(deleteData: Boolean, done: () -> Unit) {
        removed = true
        if (networking) stopNetworking(completedEvent = false)
        tickTimer?.cancel()
        val s = storage
        val state = stateFiles()
        submitDisk {
            runCatching { s?.close() }
            if (deleteData) runCatching { s?.deleteAll() }
            state.forEach { it.delete() }
            loop.post(done)
        }
    }

    /** 엔진을 닫을 때 — 이어받기 자료를 적고 연결을 끊는다. */
    fun shutdown() {
        if (networking) stopNetworking(completedEvent = false)
        tickTimer?.cancel()
        saveResume()
        val s = storage
        submitDisk { runCatching { s?.close() } }
    }

    // ── 메타데이터 ──────────────────────────────────────────────────────────

    private fun setUpMetadata(m: Metainfo, resume: ResumeData?, fromMagnet: Boolean) {
        meta = m
        metadataExchange = null
        val sel = (selection ?: m.defaultSelection()).filter { it in m.files.indices && m.files[it].isSelectable }.toSet()
        selection = sel
        val have = resume?.pieces?.let { Bitfield.fromBytes(m.pieceCount, it) } ?: Bitfield(m.pieceCount)
        if (resume != null) {
            released += resume.released
            completedFiles += resume.completedFiles
        }
        materialized += sel.filter { it !in released }
        resume?.materialized?.let { materialized += it.filter { i -> i in m.files.indices && i !in released } }
        val p = PiecePicker(m, have)
        p.setSelection(sel)
        picker = p
        recomputeWanted()
        fileDoneBytes = LongArray(m.files.size)
        for (i in 0 until m.pieceCount) if (have[i]) addDoneBytes(i)
        val st = Storage(m, File(core.dataRoot, id), File(core.stateDir, "$id.shadow"), materialized)
        storage = st
        webSeeds = m.webSeeds.map { WebSeed(it, bep17 = false) } + m.httpSeeds.map { WebSeed(it, bep17 = true) }
        if (fromMagnet) {
            // 받은 info 를 .torrent 로 저장한다(다음에 마그넷 없이 되살린다).
            val torrentBytes = torrentFileBytes(m)
            submitDisk { runCatching { AtomicFiles.write(torrentFile(), torrentBytes) } }
            for (c in connections.values.toList()) applyPendingAvailability(c)
            core.notify.metadata(id, TorrentInfo(m))
            if (networking) setUpWebSeeds(m)
        }
        // 이어받기: 크기가 어긋난 파일의 조각만 다시 해시한다(자료가 없으면 있는 파일을 모두).
        val needCheck = !fromMagnet && (resume == null || resume.pieces == null || resume.fileSizes != null)
        if (needCheck) check(m, st, have, resume) else afterChecked()
    }

    private fun check(m: Metainfo, st: Storage, have: Bitfield, resume: ResumeData?) {
        checking = true
        reevaluate()
        val haveSnapshot = have.copy()
        val sizes = resume?.fileSizes
        val trust = resume?.pieces != null
        val mat = materialized.toSet()
        val rel = released.toSet()
        submitDisk {
            val bad = HashSet<Int>()
            val toVerify = sortedSetOf<Int>()
            for (f in m.files.indices) {
                val file = m.files[f]
                if (file.isPad || file.length == 0L || f in rel || f !in mat) continue
                val actual = st.actualSize(f)
                val recorded = sizes?.getOrNull(f) ?: -2L
                if (!trust) {
                    if (actual > 0) toVerify += m.piecesOfFile(f)
                } else if (actual != recorded) {
                    bad += f
                    for (p in m.piecesOfFile(f)) if (haveSnapshot[p]) toVerify += p
                }
            }
            val result = haveSnapshot.copy()
            for (p in toVerify) {
                val ok = try {
                    st.verify(p)
                } catch (_: IOException) {
                    false
                }
                if (ok) result.set(p) else result.clear(p)
            }
            loop.post { onChecked(result, toVerify.size) }
        }
    }

    private fun onChecked(result: Bitfield, verified: Int) {
        val m = meta ?: return
        val old = picker ?: return
        if (verified > 0) {
            val p = PiecePicker(m, result)
            p.setSelection(selection.orEmpty())
            picker = p
            recomputeWanted()
            fileDoneBytes = LongArray(m.files.size)
            for (i in 0 until m.pieceCount) if (result[i]) addDoneBytes(i)
            // 다 받았다고 알렸던 파일이 이제 모자라면(옮기지 않은 것) 다시 받는다.
            completedFiles.removeAll { f -> f !in released && !p.isFileDone(f) }
            dirty = true
        } else if (old.have.count != result.count) {
            dirty = true
        }
        checking = false
        afterChecked()
    }

    private fun afterChecked() {
        val p = picker ?: return
        // 고른 길이 0 인 파일 — 곧바로 끝난 파일이다.
        for (f in selection.orEmpty()) if (meta!!.files[f].length == 0L && f !in completedFiles) fileCompleted(f)
        for (f in selection.orEmpty()) if (f !in completedFiles && p.isFileDone(f)) fileCompleted(f)
        if (!completed && p.allSelectedDone() && selection.orEmpty().isNotEmpty()) {
            complete()
            return
        }
        if (completed && !p.allSelectedDone()) completed = false
        reevaluate()
    }

    private fun recomputeWanted() {
        val m = meta ?: return
        val p = picker ?: return
        wanted = BooleanArray(m.pieceCount)
        wantedTotal = 0
        for (f in selection.orEmpty()) for (i in m.piecesOfFile(f)) if (!p.have[i] && !wanted[i]) {
            wanted[i] = true
            wantedTotal++
        }
        for (c in connections.values) recomputeWantedCount(c)
    }

    private fun recomputeWantedCount(c: PeerConnection) {
        val bf = c.bitfield
        c.wantedCount = when {
            c.isSeed -> wantedTotal
            bf == null -> 0
            else -> {
                var n = 0
                for (i in wanted.indices) if (wanted[i] && bf[i]) n++
                n
            }
        }
        updateInterest(c)
    }

    private fun addDoneBytes(piece: Int) {
        val m = meta ?: return
        val start = m.pieceOffset(piece)
        val end = start + m.pieceSize(piece)
        for (f in m.filesOfPiece(piece)) {
            val file = m.files[f]
            val overlap = minOf(end, file.end) - maxOf(start, file.offset)
            if (overlap > 0) fileDoneBytes[f] += overlap
        }
    }

    // ── 고르기·옮기기 ───────────────────────────────────────────────────────

    fun selectFiles(files: Set<Int>) {
        val m = meta
        if (m == null) {
            selection = files
            return
        }
        val sel = files.filter { it in m.files.indices && m.files[it].isSelectable }.toSet()
        val p = picker ?: return
        val newly = sel - selection.orEmpty()
        selection = sel
        p.setSelection(sel)
        val st = storage
        for (f in newly) {
            if (f in materialized || f in released) continue
            materialized += f
            for (piece in m.piecesOfFile(f)) if (piece in p.inProgressPieces()) p.resetPiece(piece)
            val haveSnap = p.have.copy()
            submitDisk { st?.materialize(f, haveSnap) }
        }
        recomputeWanted()
        for (f in sel) if (f !in completedFiles && (m.files[f].length == 0L || p.isFileDone(f))) fileCompleted(f)
        if (p.allSelectedDone() && sel.isNotEmpty()) {
            if (!completed) complete()
        } else if (completed) {
            completed = false
            reevaluate()
        } else {
            fillAll()
        }
        dirty = true
        saveResume()
        publish(force = true)
    }

    /** 앱이 그 파일을 옮겼다. */
    fun releaseFile(index: Int) {
        val m = meta ?: return
        if (index !in m.files.indices) return
        released += index
        materialized -= index
        val st = storage
        submitDisk { st?.release(index) }
        saveResume()
        publish(force = true)
    }

    // ── 1초마다 ──────────────────────────────────────────────────────────────

    private fun tick() {
        if (removed) return
        tickTimer = loop.schedule(TICK_MS) { tick() }
        val now = loop.now()
        if (networking) {
            announcer?.tick()
            for (c in connections.values.toList()) c.checkTimeouts(now)
            checkRequests(now)
            dropUseless(now)
            connectMore()
            manageDht(now)
            manageWebSeeds(now)
            if (meta == null) for (c in connections.values.toList()) requestMetadata(c)
        }
        if (dirty && now - lastSaveAt >= config.resumeSaveIntervalMs) saveResume()
        publish(force = false)
    }

    private fun checkRequests(now: Long) {
        for (c in connections.values.toList()) {
            if (!c.isActive || c.requested.isEmpty()) continue
            val oldest = c.requested.values.first()[1]
            val last = maxOf(oldest, c.lastBlockAt)
            if (now - last > REQUEST_TIMEOUT_MS) {
                // 답하지 않는 피어 — 청한 것을 거두어 다른 피어에게.
                dropAllRequests(c, sendCancel = true)
                c.snubbedUntil = now + SNUB_MS
                fillAll()
            }
        }
        for (c in connections.values.toList()) fill(c)
    }

    private fun dropUseless(now: Long) {
        val m = meta ?: return
        if (picker == null) return
        for (c in connections.values.toList()) {
            if (!c.isActive) continue
            val since = c.handshakeAt
            if (since < 0) continue
            if (!isInteresting(c)) {
                if (c.uninterestingSince < 0) c.uninterestingSince = now
                if (now - c.uninterestingSince > USELESS_MS && now - since > USELESS_MS) c.close("nothing to get", violation = false)
            } else {
                c.uninterestingSince = -1
                if (c.peerChoking && c.allowedFast.isEmpty() && now - since > CHOKED_MS && book.hasFresh(now) &&
                    connections.size >= config.maxConnectionsPerTorrent
                ) {
                    c.close("choked too long", violation = false)
                }
            }
        }
        if (m.pieceCount == 0) return
    }

    private fun isInteresting(c: PeerConnection): Boolean = c.wantedCount > 0 || (c.isSeed && wantedTotal > 0)

    // ── 피어 ────────────────────────────────────────────────────────────────

    private fun addPeers(list: List<InetSocketAddress>, source: String) {
        var added = 0
        for (a in list) {
            if (!env.addressFilter.allows(a, Purpose.PEER)) continue
            if (book.add(a, source)) added++
        }
        if (added > 0) sources[source] = (sources[source] ?: 0) + added
        if (networking) connectMore()
    }

    private fun resolveMagnetPeers(peers: List<io.github.donggi.iroirobrowser.torrent.meta.HostPort>) {
        if (peers.isEmpty()) return
        try {
            core.io.execute {
                val out = ArrayList<InetSocketAddress>()
                for (hp in peers) {
                    val addrs = runCatching { env.resolver.resolve(hp.host) }.getOrNull() ?: continue
                    addrs.firstOrNull { env.addressFilter.allows(it, hp.port, Purpose.PEER) }?.let { out += InetSocketAddress(it, hp.port) }
                }
                loop.post { addPeers(out, "magnet") }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun connectMore() {
        if (!networking) return
        val now = loop.now()
        while (connections.size < config.maxConnectionsPerTorrent) {
            val cand = book.next(now, connectedIps) ?: return
            if (!core.tryTakeConnectSlot()) return
            val tryMse = config.encryption && !cand.plainOnly
            val ch = try {
                env.transport.connectTcp(cand.address)
            } catch (e: Exception) {
                book.failed(cand, now)
                continue
            }
            cand.connected = true
            cand.lastTriedAt = now
            if (tryMse) mseAttempts++
            val c = PeerConnection(this, cand.address, tryMse, ch, loop::now)
            try {
                c.register(loop)
            } catch (e: Exception) {
                runCatching { ch.close() }
                cand.connected = false
                book.failed(cand, now)
                continue
            }
            connections[cand.address] = c
            cand.address.address?.let(connectedIps::add)
        }
    }

    fun connectionCount(): Int = connections.size

    fun halfOpenCount(): Int = connections.values.count { it.stage == PeerConnection.Stage.CONNECTING }

    fun resumeReading() {
        for (c in connections.values) c.resumeReading()
    }

    override fun canRead(): Boolean = core.canRead()

    override fun newMse(handshake: ByteArray): MseInitiator =
        MseInitiator(infoHash, MseInitiator.newPrivateKey(env.random), MseInitiator.newPad(env.random), handshake)

    override fun onHandshake(c: PeerConnection) {
        val cand = book.get(c.address)
        if (c.usedMse) mseSucceeded++
        if (cand != null) {
            cand.failures = 0
            if (cand.plainOnly && !c.usedMse && cand.fellBack) plainFallbacks++
        }
        val p = picker
        if (p != null && p.have.count > 0) {
            c.send(Outbound.Bitfield(p.have.toBytes()))
        } else if (c.fast) {
            c.send(Outbound.HaveNone)
        }
        if (c.extensions) c.send(Outbound.ExtendedHandshake(ExtensionHandshake.ours(wantMetadata = meta == null)))
        c.flush()
    }

    override fun onMessage(c: PeerConnection, m: Inbound) {
        when (m) {
            is Inbound.Choke -> {
                c.peerChoking = true
                if (!c.fast) dropAllRequests(c, sendCancel = false)
            }
            is Inbound.Unchoke -> {
                c.peerChoking = false
                fill(c)
            }
            is Inbound.Interested -> {
                c.peerInterested = true // 우리는 풀지 않는다 — 늘 choke.
            }
            is Inbound.NotInterested -> {
                c.peerInterested = false
            }
            is Inbound.Have -> onHave(c, m.index)
            is Inbound.Bitfield -> onBitfield(c, m.bits)
            is Inbound.HaveAll -> {
                if (!c.fast) return violation(c, "have all without fast")
                onHaveAll(c)
            }
            is Inbound.HaveNone -> {
                if (!c.fast) return violation(c, "have none without fast")
                c.availabilitySeen?.let { return violation(c, "have none after $it") }
                c.availabilitySeen = "have none"
                if (meta != null && c.bitfield == null) c.bitfield = Bitfield(pieceCount)
            }
            is Inbound.Request -> {
                if (++c.receivedRequests > MAX_PEER_REQUESTS) return c.close("too many requests", violation = true)
                // 올리기 0 — 보내지 않는다. Fast 면 거절을 알린다(BEP 6), 아니면 choke 중이라 무시한다(BEP 3).
                if (c.fast) {
                    c.send(Outbound.RejectRequest(m.index, m.begin, m.length))
                    c.flush()
                }
            }
            is Inbound.Piece -> {
                onPiece(c, m)
            }
            is Inbound.Cancel, is Inbound.Port, is Inbound.Suggest -> Unit
            is Inbound.Reject -> {
                if (!c.fast) return violation(c, "reject without fast")
                val key = PeerConnection.blockKey(m.index, m.begin)
                if (c.requested.remove(key) != null) {
                    picker?.dropRequest(c, m.index, m.begin)
                    if (!c.peerChoking) c.snubbedUntil = loop.now() + REJECT_BACKOFF_MS
                    fillAll()
                }
            }
            is Inbound.AllowedFast -> {
                if (!c.fast) return violation(c, "allowed fast without fast")
                val n = pieceCount
                if (n > 0 && m.index in 0 until n && c.allowedFast.size < MAX_ALLOWED_FAST) {
                    c.allowedFast.add(m.index)
                    fill(c)
                }
            }
            is Inbound.Extended -> onExtended(c, m)
            is Inbound.Unknown -> Unit
        }
    }

    private fun violation(c: PeerConnection, reason: String) {
        c.close(reason, violation = true)
    }

    private fun onBitfield(c: PeerConnection, bits: ByteArray) {
        c.availabilitySeen?.let { return violation(c, "bitfield after $it") }
        c.availabilitySeen = "bitfield"
        val m = meta
        if (m == null) {
            c.pendingBitfield = bits
            return
        }
        val bf = Bitfield.fromBytes(m.pieceCount, bits) ?: return violation(c, "bad bitfield")
        setPeerBitfield(c, bf, seed = bf.isComplete)
    }

    private fun onHaveAll(c: PeerConnection) {
        c.availabilitySeen?.let { return violation(c, "have all after $it") }
        c.availabilitySeen = "have all"
        val m = meta
        if (m == null) {
            c.pendingHaveAll = true
            return
        }
        setPeerBitfield(c, Bitfield(m.pieceCount).also { it.setAll() }, seed = true)
    }

    private fun setPeerBitfield(c: PeerConnection, bf: Bitfield, seed: Boolean) {
        val p = picker ?: return
        c.bitfield = bf
        c.isSeed = seed
        p.addBitfield(bf)
        recomputeWantedCount(c)
        availabilityChanged()
    }

    private fun onHave(c: PeerConnection, index: Int) {
        if (c.availabilitySeen == null) c.availabilitySeen = "have"
        val m = meta
        if (m == null) {
            if (index < 0 || index >= Metainfo.MAX_PIECES) return violation(c, "have out of range")
            if (c.pendingHaves.size < 100_000) c.pendingHaves.add(index)
            return
        }
        if (index !in 0 until m.pieceCount) return violation(c, "have out of range")
        val p = picker ?: return
        var bf = c.bitfield
        if (bf == null) {
            bf = Bitfield(m.pieceCount)
            c.bitfield = bf
        }
        if (c.isSeed || bf[index]) return
        bf.set(index)
        p.addAvailability(index)
        if (bf.isComplete) {
            p.promoteToSeed(bf)
            c.isSeed = true
        }
        if (wanted.getOrElse(index) { false }) {
            c.wantedCount++
            updateInterest(c)
        }
        if (p.activeFile < 0 || index in m.piecesOfFile(p.activeFile)) availabilityChanged()
        fill(c)
    }

    /** 메타데이터를 받은 뒤 — 그 전에 받아 둔 bitfield·have 를 검사해 넣는다. */
    private fun applyPendingAvailability(c: PeerConnection) {
        val m = meta ?: return
        if (!c.isActive) return
        when {
            c.pendingHaveAll -> setPeerBitfield(c, Bitfield(m.pieceCount).also { it.setAll() }, seed = true)
            c.pendingBitfield != null -> {
                val bf = Bitfield.fromBytes(m.pieceCount, c.pendingBitfield!!) ?: return violation(c, "bad bitfield")
                setPeerBitfield(c, bf, seed = bf.isComplete)
            }
            else -> if (c.bitfield == null) c.bitfield = Bitfield(m.pieceCount)
        }
        c.pendingBitfield = null
        c.pendingHaveAll = false
        for (i in c.pendingHaves) {
            if (i >= m.pieceCount) return violation(c, "have out of range")
            onHave(c, i)
            if (!c.isActive) return
        }
        c.pendingHaves.clear()
    }

    private fun updateInterest(c: PeerConnection) {
        if (!c.isActive) return
        val want = isInteresting(c) && !completed
        if (want && !c.amInterested) {
            c.amInterested = true
            c.send(Outbound.Interested)
            c.flush()
        } else if (!want && c.amInterested) {
            c.amInterested = false
            c.send(Outbound.NotInterested)
            c.flush()
        }
    }

    /** 받는 파일이 바뀌었을 수 있다 — 모든 피어에게 다시 청한다. */
    private fun availabilityChanged() {
        val p = picker ?: return
        val before = p.activeFile
        val after = p.chooseActiveFile()
        if (before != after) publish(force = true)
        fillAll()
    }

    private fun fillAll() {
        for (c in connections.values.toList()) fill(c)
        if (networking) meta?.let { manageWebSeeds(loop.now()) }
    }

    private fun fill(c: PeerConnection) {
        val p = picker ?: return
        if (!networking || completed || !c.isActive) return
        val bf = c.bitfield ?: return
        if (c.peerChoking && c.allowedFast.isEmpty()) return
        val now = loop.now()
        if (now < c.snubbedUntil) return
        val room = queueTarget(c) - c.requested.size
        if (room <= 0) return
        val reqs = p.pick(c, { i -> c.isSeed || bf[i] }, room, if (c.peerChoking) c.allowedFast else null)
        if (reqs.isEmpty()) return
        for (r in reqs) {
            c.requested[PeerConnection.blockKey(r.piece, r.begin)] = longArrayOf(r.length.toLong(), now)
            c.send(Outbound.Request(r.piece, r.begin, r.length))
        }
        c.flush()
    }

    /** 피어의 속도로 줄 세울 요청 수 — 약 3초어치, 4~128(상대의 reqq 밑). */
    private fun queueTarget(c: PeerConnection): Int {
        val r = c.rate.rate(loop.now())
        val byRate = (r * 3 / Wire.BLOCK).toInt() + 4
        val cap = minOf(MAX_QUEUE, c.peerRequestQueue ?: MAX_QUEUE)
        return byRate.coerceIn(4, maxOf(4, cap))
    }

    private fun dropAllRequests(c: PeerConnection, sendCancel: Boolean) {
        picker?.dropRequests(c)
        for ((k, v) in c.requested) {
            c.canceled[k] = v[0].toInt()
            if (sendCancel) c.send(Outbound.Cancel((k ushr 32).toInt(), k.toInt(), v[0].toInt()))
        }
        c.requested.clear()
        if (c.canceled.size > MAX_CANCELED) c.canceled.clear()
        if (sendCancel) c.flush()
    }

    private fun onPiece(c: PeerConnection, m: Inbound.Piece) {
        val key = PeerConnection.blockKey(m.index, m.begin)
        val asked = c.requested.remove(key)
        val expected = asked?.get(0)?.toInt() ?: c.canceled.remove(key)
        if (expected == null) return violation(c, "unrequested block ${m.index}/${m.begin}")
        if (m.data.size != expected) return violation(c, "block length ${m.data.size} != $expected")
        val now = loop.now()
        val n = m.data.size
        c.lastBlockAt = now
        c.downloaded += n
        c.rate.add(now, n.toLong())
        downloadedBytes += n
        rate.add(now, n.toLong())
        val p = picker ?: return
        val cancelFor = ArrayList<io.github.donggi.iroirobrowser.torrent.piece.Requester>()
        when (p.onBlock(c, m.index, m.begin, n, cancelFor)) {
            PiecePicker.BlockResult.ACCEPTED -> {
                write(m.index, m.begin, m.data)
                for (o in cancelFor) if (o is PeerConnection && o !== c) cancelRequest(o, m.index, m.begin)
            }
            PiecePicker.BlockResult.DUPLICATE -> Unit
            PiecePicker.BlockResult.UNEXPECTED -> return violation(c, "bad block")
        }
        fill(c)
    }

    private fun cancelRequest(o: PeerConnection, piece: Int, begin: Int) {
        val key = PeerConnection.blockKey(piece, begin)
        val v = o.requested.remove(key) ?: return
        o.canceled[key] = v[0].toInt()
        o.send(Outbound.Cancel(piece, begin, v[0].toInt()))
        o.flush()
    }

    private fun onExtended(c: PeerConnection, m: Inbound.Extended) {
        if (m.extensionId == 0) {
            val hs = ExtensionHandshake.parse(m.payload) ?: return
            c.peerUtMetadataId = hs.utMetadataId
            c.peerMetadataSize = hs.metadataSize
            c.peerRequestQueue = hs.requestQueue
            c.client = hs.client
            metadataExchange?.onPeerSize(c, hs.metadataSize)
            requestMetadata(c)
            return
        }
        if (m.extensionId != ExtensionHandshake.OUR_UT_METADATA_ID) return
        val msg = MetadataMessage.parse(m.payload) ?: return
        when (msg.type) {
            MetadataMessage.REQUEST -> {
                // 메타데이터는 주지 않는다(올리기 0).
                if (c.peerUtMetadataId > 0) {
                    c.send(Outbound.MetadataReject(c.peerUtMetadataId, msg.piece))
                    c.flush()
                }
            }
            MetadataMessage.DATA -> {
                val ex = metadataExchange ?: return
                when (val out = ex.onData(c, msg)) {
                    is MetadataExchange.Outcome.Pending -> requestMetadata(c)
                    is MetadataExchange.Outcome.Mismatch -> {
                        for (b in out.blame) if (b is PeerConnection) b.close("metadata hash mismatch", violation = true)
                        for (o in connections.values.toList()) requestMetadata(o)
                    }
                    is MetadataExchange.Outcome.Complete -> onMetadataBytes(out.info)
                }
            }
            MetadataMessage.REJECT -> {
                metadataExchange?.onReject(c, msg.piece)
                for (o in connections.values.toList()) requestMetadata(o)
            }
        }
    }

    private fun requestMetadata(c: PeerConnection) {
        val ex = metadataExchange ?: return
        if (!c.isActive || c.peerUtMetadataId <= 0) return
        val piece = ex.nextRequest(c, loop.now()) ?: return
        c.send(Outbound.MetadataRequest(c.peerUtMetadataId, piece))
        c.flush()
    }

    private fun onMetadataBytes(info: ByteArray) {
        val mg = magnet
        val m = try {
            Metainfo.fromInfo(info, mg?.trackers?.map { listOf(it) }.orEmpty(), mg?.webSeeds.orEmpty())
        } catch (e: TorrentException) {
            fail(e.error, e.detail)
            return
        }
        setUpMetadata(m, null, fromMagnet = true)
        fillAll()
        publish(force = true)
    }

    override fun onClosed(c: PeerConnection, reason: String, violation: Boolean) {
        if (connections[c.address] === c) {
            connections.remove(c.address)
            c.address.address?.let(connectedIps::remove)
        }
        val p = picker
        if (p != null) {
            p.dropRequests(c)
            val bf = c.bitfield
            if (bf != null && (c.isSeed || bf.count > 0)) {
                if (c.isSeed) p.removeBitfield(bf, wasSeed = true) else p.removeBitfield(bf, wasSeed = false)
            }
        }
        c.bitfield = null
        metadataExchange?.onPeerGone(c)
        val cand = book.get(c.address)
        val now = loop.now()
        if (cand != null) {
            cand.connected = false
            when {
                violation -> book.ban(c.address)
                !networking -> Unit // 우리가 멈춰 끊은 것 — 실패로 세지 않는다
                c.failedDuringMse && config.encryption && !cand.plainOnly && networking -> {
                    // MSE 를 받지 않는 피어 — 평문으로 다시 붙는다.
                    cand.plainOnly = true
                    cand.fellBack = true
                    cand.nextAttemptAt = now
                }
                c.handshakeAt < 0 -> book.failed(cand, now)
                else -> cand.nextAttemptAt = now + RECONNECT_MS
            }
        }
        env.log("peer ${c.address} closed: $reason")
        if (networking && !removed) {
            availabilityChanged()
            connectMore()
        }
    }

    // ── 쓰기·확인 ───────────────────────────────────────────────────────────

    private fun write(piece: Int, begin: Int, data: ByteArray) {
        val st = storage ?: return
        pendingWrites[piece] = (pendingWrites[piece] ?: 0) + 1
        core.addPending(data.size)
        val ok = submitDisk {
            var err: Exception? = null
            try {
                st.write(piece, begin, data)
            } catch (e: Exception) {
                err = e
            }
            loop.post { onWritten(piece, data.size, err) }
        }
        if (!ok) core.releasePending(data.size)
    }

    private fun onWritten(piece: Int, size: Int, err: Exception?) {
        core.releasePending(size)
        val left = (pendingWrites[piece] ?: 1) - 1
        if (left <= 0) pendingWrites.remove(piece) else pendingWrites[piece] = left
        if (err != null) {
            diskError(err)
            return
        }
        val p = picker ?: return
        val w = p.work(piece, create = false) ?: return
        if (w.isComplete && left <= 0 && piece !in verifying) verify(piece)
    }

    private fun verify(piece: Int) {
        val st = storage ?: return
        verifying.add(piece)
        submitDisk {
            val ok = try {
                st.verify(piece)
            } catch (e: IOException) {
                false
            }
            loop.post { onVerified(piece, ok) }
        }
    }

    private fun onVerified(piece: Int, ok: Boolean) {
        verifying.remove(piece)
        val p = picker ?: return
        val m = meta ?: return
        if (removed) return
        if (!ok) {
            hashFailures++
            for (r in p.onPieceFailed(piece)) {
                when (r) {
                    is PeerConnection -> if (++r.hashFails >= MAX_HASH_FAILS) r.close("bad data", violation = true)
                    is WebSeed -> disableWebSeed(r, "bad data (hash)")
                }
            }
            fillAll()
            return
        }
        p.onPieceVerified(piece)
        addDoneBytes(piece)
        if (wanted.getOrElse(piece) { false }) {
            wanted[piece] = false
            wantedTotal--
        }
        for (c in connections.values.toList()) {
            if (!c.isActive) continue
            val bf = c.bitfield
            if (c.isSeed || (bf != null && bf[piece])) {
                if (c.wantedCount > 0) c.wantedCount--
            }
            // 해시를 확인한 조각만 알린다(정직한 HAVE).
            c.send(Outbound.Have(piece))
            c.flush()
            updateInterest(c)
        }
        dirty = true
        for (f in m.filesOfPiece(piece)) {
            if (f in completedFiles || selection?.contains(f) != true) continue
            if (p.isFileDone(f)) fileCompleted(f)
        }
        if (p.allSelectedDone()) complete() else availabilityChanged()
    }

    private fun fileCompleted(f: Int) {
        val m = meta ?: return
        if (!completedFiles.add(f)) return
        val st = storage ?: return
        val file = st.fileOf(f)
        val empty = m.files[f].length == 0L
        submitDisk {
            try {
                if (empty) {
                    file.parentFile?.mkdirs()
                    if (!file.exists()) file.createNewFile()
                }
                st.flush()
                st.closeFile(f)
            } catch (e: IOException) {
                loop.post { diskError(e) }
                return@submitDisk
            }
            loop.post {
                saveResume()
                if (f !in released) core.notify.fileCompleted(id, f, file)
            }
        }
    }

    private fun complete() {
        if (completed) return
        completed = true
        val p = picker
        val wholeTorrent = p != null && p.have.isComplete
        if (networking) stopNetworking(completedEvent = wholeTorrent)
        saveResume()
        publish(force = true)
        core.notify.finished(id)
        core.sessionStateChanged()
    }

    private fun diskError(e: Exception) {
        val usable = core.dataRoot.usableSpace
        val full = usable in 0 until 1024L * 1024
        fail(if (full) TorrentError.DISK_FULL else TorrentError.DISK_ERROR, e.message)
    }

    fun fail(err: TorrentError, detail: String?) {
        if (error != null) return
        error = err
        errorDetail = detail
        if (networking) stopNetworking(completedEvent = false)
        saveResume()
        publish(force = true)
        core.notify.error(id, err, detail)
        core.sessionStateChanged()
    }

    /** 받기 전 공간 확인. 모자라면 오류로 멈춘다. */
    fun checkSpace(): Boolean {
        val m = meta ?: return true
        val p = picker ?: return true
        val remaining = m.files.indices.filter { selection?.contains(it) == true }.sumOf { m.files[it].length - fileDoneBytes[it] }
        val usable = runCatching { core.root.usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (usable in 1 until remaining + config.reserveBytes && !p.allSelectedDone()) {
            fail(TorrentError.DISK_FULL, "need $remaining, usable $usable")
            return false
        }
        return true
    }

    // ── 웹 시드·DHT ─────────────────────────────────────────────────────────

    private fun setUpWebSeeds(m: Metainfo) {
        if (webSeeds.isEmpty()) webSeeds = m.webSeeds.map { WebSeed(it, bep17 = false) } + m.httpSeeds.map { WebSeed(it, bep17 = true) }
        for (ws in webSeeds) ws.cancelled = false
        picker?.webSeeds = webSeeds.count { !it.disabled }
    }

    private fun manageWebSeeds(now: Long) {
        val m = meta ?: return
        val p = picker ?: return
        if (!networking || completed) return
        p.webSeeds = webSeeds.count { !it.disabled }
        for (ws in webSeeds) {
            if (ws.disabled || ws.busy || now < ws.nextAt) continue
            val run = p.pickRun(ws, WEB_SEED_RUN_BYTES)
            if (run.isEmpty()) continue
            ws.busy = true
            ws.cancelled = false
            val ok = runCatching {
                core.webSeedPool.execute {
                    var failure: WebSeedFetch.Failure? = null
                    try {
                        WebSeedFetch.fetch(core.http, m, ws, run) { piece, begin, data ->
                            core.awaitRoom { ws.cancelled }
                            core.addPending(data.size)
                            loop.post { onWebSeedBlock(ws, piece, begin, data) }
                        }
                    } catch (e: WebSeedFetch.Failure) {
                        failure = e
                    } catch (e: Exception) {
                        failure = WebSeedFetch.Failure(e.toString(), permanent = false)
                    }
                    val f = failure
                    loop.post { onWebSeedDone(ws, f) }
                }
            }.isSuccess
            if (!ok) ws.busy = false
        }
    }

    private fun onWebSeedBlock(ws: WebSeed, piece: Int, begin: Int, data: ByteArray) {
        core.releasePending(data.size)
        val p = picker ?: return
        if (ws.disabled || removed) return
        val now = loop.now()
        ws.downloaded += data.size
        downloadedBytes += data.size
        rate.add(now, data.size.toLong())
        val cancelFor = ArrayList<io.github.donggi.iroirobrowser.torrent.piece.Requester>()
        when (p.onBlock(ws, piece, begin, data.size, cancelFor)) {
            PiecePicker.BlockResult.ACCEPTED -> {
                write(piece, begin, data)
                for (o in cancelFor) if (o is PeerConnection) cancelRequest(o, piece, begin)
            }
            else -> Unit
        }
    }

    private fun onWebSeedDone(ws: WebSeed, failure: WebSeedFetch.Failure?) {
        ws.busy = false
        val p = picker
        p?.dropRequests(ws)
        if (failure == null) {
            ws.failures = 0
            ws.lastError = null
        } else if (!ws.cancelled) {
            ws.failures++
            ws.lastError = failure.message
            env.log("webseed ${ws.url}: ${failure.message}")
            if (failure.permanent || ws.failures >= MAX_WEB_SEED_FAILURES) {
                disableWebSeed(ws, failure.message ?: "failed")
            } else {
                ws.nextAt = loop.now() + WEB_SEED_BACKOFF_MS * ws.failures
            }
        }
        if (networking) {
            availabilityChanged()
        }
    }

    private fun disableWebSeed(ws: WebSeed, why: String) {
        if (ws.disabled) return
        ws.disabled = true
        ws.cancelled = true
        ws.lastError = why
        picker?.dropRequests(ws)
        picker?.webSeeds = webSeeds.count { !it.disabled }
        availabilityChanged()
    }

    private fun manageDht(now: Long) {
        val dht = core.dht ?: return
        if (!config.dht || lookup != null || now < nextDhtAt || completed) return
        lookup = dht.lookup(infoHash, onPeers = { addPeers(it, "dht") }, onDone = { found ->
            lookup = null
            // 빈손이면 곧 다시(30초부터 두 배씩, 5분까지) — 처음 부트스트랩 이름 풀이가 실패한 실행에서 2분을 기다린 것을 보고 고쳤다.
            dhtEmptyRounds = if (found == 0) dhtEmptyRounds + 1 else 0
            val wait = when {
                found == 0 -> minOf(DHT_EMPTY_MAX_MS, DHT_EMPTY_RETRY_MS shl minOf(dhtEmptyRounds - 1, 4))
                connections.size < 5 -> DHT_RETRY_MS
                else -> DHT_INTERVAL_MS
            }
            nextDhtAt = loop.now() + wait
            if (!networking) core.dht.idle()
        })
    }

    // ── announce ────────────────────────────────────────────────────────────

    private fun announceRequest(event: AnnounceEvent): AnnounceRequest {
        val m = meta
        val p = picker
        val left = if (m == null || p == null) 0L else {
            var done = 0L
            for (i in 0 until m.pieceCount) if (p.have[i]) done += m.pieceSize(i)
            m.totalLength - done
        }
        return AnnounceRequest(
            infoHash = infoHash,
            peerId = ourPeerId,
            port = core.announcePort,
            downloaded = downloadedBytes,
            left = if (m == null) 1L else left, // 메타데이터 전에는 크기를 모른다 — 0 이면 시드로 읽힌다
            event = event,
            numWant = if (event == AnnounceEvent.STOPPED || event == AnnounceEvent.COMPLETED) 0 else config.numWant,
            key = keyFor(),
        )
    }

    private var announceKey = 0

    private fun keyFor(): Int {
        if (announceKey == 0) announceKey = env.random.nextInt() or 1
        return announceKey
    }

    // ── 이어받기 자료·상태 ──────────────────────────────────────────────────

    private fun stateFiles(): List<File> = listOf(torrentFile(), resumeFile(), File(core.stateDir, "$id.resume.tmp"), File(core.stateDir, "$id.torrent.tmp"))

    fun torrentFile(): File = File(core.stateDir, "$id.torrent")

    private fun resumeFile(): File = File(core.stateDir, "$id.resume")

    fun saveResume() {
        if (removed) return
        lastSaveAt = loop.now()
        dirty = false
        val m = meta
        val p = picker
        val st = storage
        val name = m?.name ?: magnet?.displayName ?: id
        val haveBytes = p?.have?.toBytes()
        val sel = selection
        val mat = materialized.toSet()
        val rel = released.toSet()
        val done = completedFiles.toSet()
        val state = when {
            error != null -> "error"
            completed -> "completed"
            else -> "paused"
        }
        val err = error?.name
        val added = addedAtMs
        val down = downloadedBytes
        val magnetUri = if (m == null) magnet?.uri else null
        submitDisk {
            try {
                st?.flush()
                val sizes = if (m != null && st != null) LongArray(m.files.size) { i -> if (i in mat) st.actualSize(i) else -1L } else null
                val data = ResumeData(infoHash, name, magnetUri, haveBytes, sizes, sel, mat, rel, done, state, err, added, down)
                AtomicFiles.write(resumeFile(), data.encode())
            } catch (e: IOException) {
                env.log("resume save failed: $e")
            }
        }
    }

    private fun publish(force: Boolean) {
        val now = loop.now()
        if (!force && now - lastStatusAt < STATUS_MS) return
        lastStatusAt = now
        val s = buildStatus()
        status = s
        core.notify.status(s)
    }

    fun currentState(): TorrentState = when {
        error != null -> TorrentState.ERROR
        checking -> TorrentState.CHECKING
        completed -> TorrentState.COMPLETED
        userPaused -> TorrentState.PAUSED
        !admitted -> TorrentState.QUEUED
        !networkAllowed -> TorrentState.NETWORK_WAIT
        meta == null -> TorrentState.METADATA
        (picker?.activeFile ?: -1) < 0 -> TorrentState.WAITING
        else -> TorrentState.DOWNLOADING
    }

    private fun buildStatus(): TorrentStatus {
        val m = meta
        val p = picker
        val now = loop.now()
        val files = if (m == null) emptyList() else m.files.map { f ->
            val sel = selection?.contains(f.index) == true
            val done = p != null && p.isFileDone(f.index)
            FileStatus(
                index = f.index,
                path = f.displayPath,
                length = f.length,
                doneBytes = if (fileDoneBytes.size > f.index) fileDoneBytes[f.index] else 0L,
                selected = sel,
                state = when {
                    f.isPad -> "pad"
                    !sel -> "skipped"
                    done -> "done"
                    p != null && p.activeFile == f.index -> "active"
                    p != null && !p.isFullyAvailable(f.index) -> "waiting"
                    else -> "queued"
                },
                released = f.index in released,
            )
        }
        val wantedBytes = if (m == null) -1L else files.filter { it.selected }.sumOf { it.length }
        val doneBytes = files.filter { it.selected }.sumOf { minOf(it.doneBytes, it.length) }
        return TorrentStatus(
            id = id,
            name = m?.name ?: magnet?.displayName ?: id,
            state = currentState(),
            error = error,
            errorDetail = errorDetail,
            wantedBytes = wantedBytes,
            doneBytes = doneBytes,
            downloadedBytes = downloadedBytes,
            downloadRate = if (networking) rate.rate(now) else 0L,
            connectedPeers = connections.values.count { it.isActive },
            knownPeers = book.size,
            seeds = connections.values.count { it.isActive && it.isSeed },
            webSeeds = webSeeds.count { !it.disabled },
            activeFile = p?.activeFile ?: -1,
            files = files,
            trackers = announcer?.statuses().orEmpty(),
            peerSources = HashMap(sources),
            mseAttempts = mseAttempts,
            mseSucceeded = mseSucceeded,
            plainFallbacks = plainFallbacks,
            hashFailures = hashFailures,
            addedAtMs = addedAtMs,
        )
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private fun submitDisk(task: () -> Unit): Boolean = try {
        core.disk.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    private fun newPeerId(): ByteArray {
        val prefix = config.peerIdPrefix.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(20)
        System.arraycopy(prefix, 0, out, 0, 8)
        for (i in 8 until 20) out[i] = PEER_ID_CHARS[env.random.nextInt(PEER_ID_CHARS.length)].code.toByte()
        return out
    }

    /** 마그넷으로 받은 info 를 담은 .torrent — info 사전의 원래 바이트를 그대로 넣는다(다시 짓지 않는다). */
    private fun torrentFileBytes(m: Metainfo): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write('d'.code)
        if (m.trackers.isNotEmpty()) {
            out.write(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode("announce-list"))
            out.write(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode(m.trackers))
        }
        out.write(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode("info"))
        out.write(m.infoBytes)
        if (m.webSeeds.isNotEmpty()) {
            out.write(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode("url-list"))
            out.write(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode(m.webSeeds))
        }
        out.write('e'.code)
        return out.toByteArray()
    }

    companion object {
        const val TICK_MS = 1_000L
        const val STATUS_MS = 1_000L
        const val REQUEST_TIMEOUT_MS = 60_000L
        const val SNUB_MS = 60_000L
        const val REJECT_BACKOFF_MS = 10_000L
        const val USELESS_MS = 30_000L
        const val CHOKED_MS = 180_000L
        const val RECONNECT_MS = 120_000L
        const val MAX_QUEUE = 128
        const val MAX_PEER_REQUESTS = 2_000
        const val MAX_ALLOWED_FAST = 64
        const val MAX_CANCELED = 4_096
        const val MAX_HASH_FAILS = 2
        const val WEB_SEED_RUN_BYTES = 4L * 1024 * 1024
        const val WEB_SEED_BACKOFF_MS = 30_000L
        const val MAX_WEB_SEED_FAILURES = 5
        const val DHT_INTERVAL_MS = 15 * 60_000L
        const val DHT_RETRY_MS = 2 * 60_000L
        const val DHT_EMPTY_RETRY_MS = 30_000L
        const val DHT_EMPTY_MAX_MS = 5 * 60_000L
        private const val PEER_ID_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    }
}

/** 피어 후보 — 트래커·DHT·마그넷이 준 주소. 이벤트 루프에서만. */
internal class PeerBook {
    class Candidate(val address: InetSocketAddress, val source: String) {
        var connected = false
        var failures = 0
        var nextAttemptAt = 0L
        var lastTriedAt = 0L
        var plainOnly = false
        var fellBack = false
    }

    private val candidates = LinkedHashMap<InetSocketAddress, Candidate>()
    private val banned = HashSet<InetAddress>()

    val size: Int get() = candidates.size

    fun get(a: InetSocketAddress): Candidate? = candidates[a]

    fun add(a: InetSocketAddress, source: String): Boolean {
        val ip = a.address ?: return false
        if (ip in banned || candidates.containsKey(a)) return false
        if (candidates.size >= MAX_CANDIDATES) {
            // 실패가 많은 것부터 버린다.
            val worst = candidates.values.filter { !it.connected }.maxByOrNull { it.failures } ?: return false
            if (worst.failures == 0) return false
            candidates.remove(worst.address)
        }
        candidates[a] = Candidate(a, source)
        return true
    }

    fun next(now: Long, connectedIps: Set<InetAddress>): Candidate? {
        var best: Candidate? = null
        for (c in candidates.values) {
            if (c.connected || now < c.nextAttemptAt) continue
            val ip = c.address.address ?: continue
            if (ip in banned || ip in connectedIps) continue
            if (best == null || c.failures < best.failures) {
                best = c
                if (c.failures == 0 && c.lastTriedAt == 0L) break
            }
        }
        return best
    }

    /** 아직 붙어 보지 않은 후보가 있는가. */
    fun hasFresh(now: Long): Boolean = candidates.values.any { !it.connected && it.lastTriedAt == 0L && now >= it.nextAttemptAt }

    fun failed(c: Candidate, now: Long) {
        c.connected = false
        c.failures++
        c.lastTriedAt = now
        if (c.failures > MAX_FAILURES) {
            candidates.remove(c.address)
            return
        }
        c.nextAttemptAt = now + minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl (c.failures - 1))
    }

    /** 규약을 어긴 피어 — 그 IP 를 이 토렌트에서 다시 쓰지 않는다. */
    fun ban(a: InetSocketAddress) {
        val ip = a.address ?: return
        banned.add(ip)
        candidates.values.removeAll { it.address.address == ip && !it.connected }
    }

    fun isBanned(ip: InetAddress): Boolean = ip in banned

    companion object {
        const val MAX_CANDIDATES = 1_000
        const val MAX_FAILURES = 5
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 30 * 60_000L
    }
}
