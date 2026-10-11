package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.dht.Dht
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.Clock
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import io.github.donggi.iroirobrowser.torrent.tracker.TrackerStatus
import java.io.File
import java.security.SecureRandom
import java.util.Random
import java.util.concurrent.Executor
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

/** 엔진의 한도와 고르기. 기본값은 사용자 결정 ⑭ 그대로. */
data class TorrentConfig(
    /** 동시에 받는 토렌트 — 넘는 것은 [TorrentState.QUEUED]. */
    val maxActiveTorrents: Int = 2,
    val maxConnectionsPerTorrent: Int = 30,
    val maxConnections: Int = 60,
    /** 새 연결 시도 — 초당. */
    val newConnectionsPerSecond: Int = 5,
    /** 연결 중(응답을 기다리는) 시도의 동시 상한. */
    val maxHalfOpen: Int = 8,
    /** 나가는 연결은 MSE 를 먼저 청하고, 상대가 받지 않으면 평문으로 다시 붙는다. */
    val encryption: Boolean = true,
    /** DHT(읽기 전용)로 피어를 찾는다. */
    val dht: Boolean = true,
    /** DHT 부트스트랩 노드(이름, 포트). */
    val dhtBootstrap: List<Pair<String, Int>> = Dht.BOOTSTRAP,
    /** 트래커에 청하는 피어 수. */
    val numWant: Int = 50,
    /** 이어받기 자료를 적는 간격(바뀐 것이 있을 때). */
    val resumeSaveIntervalMs: Long = 30_000,
    /** 트래커·웹 시드에 보내는 User-Agent. */
    val userAgent: String = "Web/0.1",
    /** 피어 id 의 머리(BEP 20, Azureus 꼴 8 글자) — 기존 클라이언트를 흉내 내지 않는 우리 것. */
    val peerIdPrefix: String = "-IW0100-",
    /** 받기 시작 전에 남겨 둘 여유 공간(바이트). */
    val reserveBytes: Long = 64L * 1024 * 1024,
) {
    init {
        require(peerIdPrefix.toByteArray(Charsets.US_ASCII).size == 8)
        require(maxActiveTorrents >= 1 && maxConnections >= 1 && maxConnectionsPerTorrent >= 1)
    }
}

/**
 * 엔진이 바깥과 닿는 모든 것 — 시험이 결정적이게 바꿔 낄 수 있다(가상 시계·고정 씨앗·가짜 소켓·루프백을 허락하는 거르개).
 */
class TorrentEnvironment(
    val clock: Clock = Clock.SYSTEM,
    /** 피어 id·DH 개인 키·패딩·DHT id·transaction id. 앱은 기본(SecureRandom)을 쓴다. */
    val random: Random = SecureRandom(),
    /** 소켓 공장 — **늘 프록시 없이**(`Proxy.NO_PROXY`). */
    val transport: Transport = Transport.DIRECT,
    val resolver: Resolver = Resolver.SYSTEM,
    /** 남이 준 주소의 거르개 — 앱은 늘 [AddressFilter.PUBLIC]. */
    val addressFilter: AddressFilter = AddressFilter.PUBLIC,
    /** HTTPS 트래커·웹 시드의 TLS. 안드로이드는 플랫폼의 기본. */
    val tls: SSLSocketFactory? = SSLSocketFactory.getDefault() as? SSLSocketFactory,
    /** 이름을 한 번 더 확인한다(안드로이드: `HttpsURLConnection.getDefaultHostnameVerifier()`). */
    val hostnameVerifier: HostnameVerifier? = null,
    /**
     * 망 조건 훅 — 거짓이면 엔진은 모든 토렌트의 연결을 끊고 기다린다([TorrentState.NETWORK_WAIT]). 'Wi-Fi 에서만'(사용자 결정, 기본 끔)은
     * 앱이 정한다. 망이 바뀌면 앱이 [TorrentEngine.networkChanged] 를 부른다. 엔진 스레드에서 불리니 빨리 답해야 한다.
     */
    val networkAllowed: () -> Boolean = { true },
    /** 듣개를 부를 곳. null 이면 엔진의 알림 스레드 하나. */
    val callbackExecutor: Executor? = null,
    val log: (String) -> Unit = {},
)

enum class TorrentState {
    /** 마그넷 — 피어에게서 메타데이터(info 사전)를 받는 중. */
    METADATA,

    /** 받아 둔 것을 해시로 확인하는 중(이어받을 때 크기가 어긋난 파일). */
    CHECKING,

    DOWNLOADING,

    /** 고른 파일 가운데 지금 군집에서 다 구할 수 있는 것이 없어 기다린다(조각이 모자라). */
    WAITING,

    /** 동시 토렌트 수를 넘어 차례를 기다린다. */
    QUEUED,

    /** 망 조건(앱의 훅)이 거짓이라 기다린다. */
    NETWORK_WAIT,

    PAUSED,

    /** 고른 파일을 모두 받았다 — 연결을 모두 끊었다(시드로 남지 않는다). */
    COMPLETED,

    ERROR,
}

/** 파일 하나의 진행. */
class FileStatus(
    val index: Int,
    val path: String,
    val length: Long,
    /** 확인된 조각이 덮는 바이트(이 파일 안에서). */
    val doneBytes: Long,
    val selected: Boolean,
    /** 'done'·'active'(받는 중)·'waiting'(구할 수 없어 미룸)·'queued'(차례를 기다림)·'skipped'(고르지 않음)·'pad'. */
    val state: String,
    /** 앱이 옮겨 갔다. */
    val released: Boolean,
)

/** 토렌트 하나의 지금 상태 — 바뀌지 않는 값(어느 스레드에서 읽어도 된다). */
class TorrentStatus(
    /** info-hash 의 16진 글(소문자) — 토렌트의 id. */
    val id: String,
    val name: String,
    val state: TorrentState,
    val error: TorrentError?,
    val errorDetail: String?,
    /** 고른 파일의 바이트 합. 메타데이터 전이면 -1. */
    val wantedBytes: Long,
    /** 고른 파일 가운데 확인된 바이트. */
    val doneBytes: Long,
    /** 받은 바이트(해시를 거치기 전의 블록도 — 버린 것 포함). */
    val downloadedBytes: Long,
    /** 받는 속도(바이트/초, 최근 5초). */
    val downloadRate: Long,
    val connectedPeers: Int,
    val knownPeers: Int,
    val seeds: Int,
    val webSeeds: Int,
    /** 지금 받는 파일(-1 이면 없다). */
    val activeFile: Int,
    val files: List<FileStatus>,
    val trackers: List<TrackerStatus>,
    /** 피어를 얻은 곳마다의 수(tracker·dht·magnet). */
    val peerSources: Map<String, Int>,
    /** MSE 를 시도한 연결·핸드셰이크까지 간 MSE 연결·평문으로 다시 붙어 된 연결. */
    val mseAttempts: Int,
    val mseSucceeded: Int,
    val plainFallbacks: Int,
    /** 해시가 틀린 조각 수. */
    val hashFailures: Int,
    val addedAtMs: Long,
)

/** 메타데이터(파일 목록) — 앱이 '묻기' 화면에 보인다. */
class TorrentInfo internal constructor(val meta: Metainfo) {
    val id: String get() = meta.infoHashHex
    val name: String get() = meta.name
    val totalLength: Long get() = meta.totalLength
    val files get() = meta.files
    val trackers: List<List<String>> get() = meta.trackers
    val webSeeds: List<String> get() = meta.webSeeds
    val isHybrid: Boolean get() = meta.isHybrid
    val defaultSelection: Set<Int> get() = meta.defaultSelection()
}

/**
 * 엔진의 소식. **알림 스레드**([TorrentEnvironment.callbackExecutor])에서 불린다 — 네트워크 스레드를 막지 않는다.
 */
interface TorrentListener {
    /** 상태가 바뀌었다(받는 동안은 1초에 한 번까지). */
    fun onStatus(status: TorrentStatus) {}

    /** 마그넷의 메타데이터를 받았다. */
    fun onMetadata(id: String, info: TorrentInfo) {}

    /**
     * 파일 하나를 다 받았다(해시를 확인하고 디스크까지 내렸다). 앱은 이것을 공용 '내려받기' 로 옮기고 [TorrentEngine.releaseFile] 을 부른다.
     */
    fun onFileCompleted(id: String, fileIndex: Int, file: File) {}

    /**
     * 고른 파일을 모두 받았다 — 엔진은 트래커에 알리고 모든 연결을 끊었다. **마지막 파일의 [onFileCompleted] 보다 먼저 올 수 있다** — 파일의 알림은
     * 디스크에 내린 뒤에 나가고 이것은 마지막 조각을 확인한 자리에서 곧바로 나간다(상태도 그때 '다 받음' 이다). 옮기기를 이것에 걸지 마라.
     */
    fun onFinished(id: String) {}

    /** 받기를 멈춘 오류. */
    fun onError(id: String, error: TorrentError, detail: String?) {}

    companion object {
        val NONE = object : TorrentListener {}
    }
}
