package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.core.SafeFileName
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.torrent.meta.TorrentFile
import java.util.Locale

/**
 * 토렌트 엔진(`:torrent`)을 앱에 잇는 **판단** — 화면·서비스·MediaStore 에 묶이지 않은 것만 모았다(기기 없이 시험한다 — `TorrentRulesTest`).
 * 무엇이 토렌트인가, 지금 망에서 받아도 되는가, 서비스·CPU 를 붙잡을지, 다 받은 파일을 '내려받기' 의 어디에 둘지, 앱이 죽은 뒤 무엇을
 * 마저 할지, 엔진을 닫아도 되는지.
 */
internal object TorrentRules {

    /** 공용 '내려받기' 폴더의 이름(`Environment.DIRECTORY_DOWNLOADS` 와 같은 값 — 그것은 시험의 껍데기 android.jar 에서 읽히지 않는다). */
    const val DOWNLOAD_DIR = "Download"

    /** 받는 바이트가 이만큼 늘지 않으면 화면이 꺼진 동안 CPU 를 놓는다([StallWatch]). */
    const val STALL_MS = 10 * 60_000L

    // ── 무엇이 토렌트인가 ───────────────────────────────────────────────────────────────────────────────────

    /** 마그넷 주소인가 — 스킴만 본다(읽을 수 있는지는 엔진의 `MagnetLink` 가 가리고 까닭을 말한다). */
    fun isMagnet(url: String): Boolean = url.trimStart().regionMatches(0, "magnet:", 0, 7, ignoreCase = true)

    /**
     * 내려받기가 `.torrent` 인가 — 형식(`application/x-bittorrent`), 고른 이름, http(s) 주소의 경로 끝 가운데 하나라도. 사이트가 형식을
     * `application/octet-stream` 으로 주는 일이 흔해 이름과 주소도 본다(이름은 Content-Disposition 에서 왔을 수 있다).
     */
    fun isTorrentFile(mimeType: String?, fileName: String?, url: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (mime == "application/x-bittorrent") return true
        if (fileName?.trim()?.lowercase(Locale.ROOT)?.endsWith(".torrent") == true) return true
        val path = url?.substringBefore('#')?.substringBefore('?')?.lowercase(Locale.ROOT) ?: return false
        return (path.startsWith("http://") || path.startsWith("https://")) && path.endsWith(".torrent")
    }

    // ── 망 ───────────────────────────────────────────────────────────────────────────────────────────────

    /** 지금 망 — `ConnectivityManager` 의 기본 망에서 읽은 것. */
    class Net(val internet: Boolean, val notMetered: Boolean, val wifi: Boolean, val ethernet: Boolean)

    /** 토렌트가 망을 기다리는 까닭. */
    enum class NetWait {
        /** 연결된 망이 없다(비행기 모드·망 바꾸는 중). */
        NO_NETWORK,

        /** 'Wi-Fi 에서만' 을 켰는데 지금 망이 Wi-Fi(요금제가 아닌 망)가 아니다. */
        NOT_WIFI,
    }

    /**
     * 지금 망에서 받아도 되면 null, 아니면 기다리는 까닭. 엔진의 망 조건 훅([io.github.donggi.iroirobrowser.torrent.TorrentEnvironment.networkAllowed])
     * 이 이것으로 답한다.
     *
     * - 'Wi-Fi 에서만'(사용자 결정 — 기본 끔)은 **요금제가 아닌 Wi-Fi(또는 유선)** 만 받는다. 휴대폰 핫스팟처럼 요금제로 표시된 Wi-Fi 는 결국
     *   모바일 데이터를 쓰므로 빼고, 통신사가 요금제가 아니라고 표시한 모바일 망도 'Wi-Fi 에서만' 의 뜻이 아니라 뺀다.
     * - **망이 없으면 늘 기다린다**(명세에 더한 것) — 없는 망으로 피어에 붙으려 애쓰며 CPU 를 붙잡지 않게. 그동안 화면은 '망 조건 기다림' 이다.
     */
    fun netWait(wifiOnly: Boolean, net: Net?): NetWait? = when {
        net == null || !net.internet -> NetWait.NO_NETWORK
        wifiOnly && !(net.notMetered && (net.wifi || net.ethernet)) -> NetWait.NOT_WIFI
        else -> null
    }

    // ── 서비스와 CPU ─────────────────────────────────────────────────────────────────────────────────────

    /** 받으려 하는 상태 — 포그라운드 서비스를 세워 둔다. 망을 기다리는 것도 넣는다(프로세스가 살아 있어야 망이 돌아올 때 이어 받는다). */
    private val RUNNING = setOf(
        TorrentState.METADATA,
        TorrentState.CHECKING,
        TorrentState.DOWNLOADING,
        TorrentState.WAITING,
        TorrentState.QUEUED,
        TorrentState.NETWORK_WAIT,
    )

    /**
     * 화면이 꺼져도 CPU 를 붙잡을 상태 — 실제로 피어와 주고받거나(메타데이터·받기·조각 기다림) 디스크에서 확인하는 중. 차례를 기다리는 것(다른
     * 토렌트가 받는 중이다 — 그쪽이 붙잡는다)과 망을 기다리는 것은 붙잡지 않는다.
     */
    private val CPU = setOf(TorrentState.METADATA, TorrentState.CHECKING, TorrentState.DOWNLOADING, TorrentState.WAITING)

    fun isRunning(state: TorrentState): Boolean = state in RUNNING

    /** 포그라운드 서비스를 세워 둘까 — 받으려는 토렌트가 있거나, 옮기기·엔진 일이 남았다([busy]). */
    fun keepsForeground(states: Collection<TorrentState>, busy: Boolean): Boolean = busy || states.any { it in RUNNING }

    /**
     * CPU 를 붙잡을지(부분 wake lock — 사용자 결정: 화면이 꺼져도 받는다) — **받는 동안만**. 받는 상태가 있어도 [STALL_MS] 동안 나아간 것이 없으면
     * 놓는다: 피어가 없어 하염없이 기다리는 토렌트(조각이 모자라 기다림·메타데이터를 줄 피어가 없음·모두 choke)가 배터리를 다 쓰지 않게(명세에 더한
     * 것). 나아감은 받은 바이트의 합이 늘었거나 받는 토렌트의 차림(어느 토렌트가 어느 상태인가)이 바뀐 것이다. 놓은 뒤에도 화면이 켜지거나 다른
     * 까닭으로 CPU 가 깨면 엔진은 계속 돌고, 바이트가 다시 늘면 곧바로 다시 붙잡는다. 확인(해시)과 파일 옮기기는 끝이 있는 디스크 일이라 바이트를
     * 보지 않고 늘 붙잡는다. 메인 스레드에서만(서비스가 상태를 받을 때마다).
     */
    class StallWatch(private val graceMs: Long = STALL_MS) {
        private var lastKey: String? = null
        private var lastBytes = -1L
        private var lastProgressAt = 0L

        fun needsCpu(nowMs: Long, statuses: List<TorrentStatus>, moving: Boolean): Boolean {
            val busy = statuses.filter { it.state in CPU }
            if (moving || busy.any { it.state == TorrentState.CHECKING }) {
                lastKey = null
                return true
            }
            if (busy.isEmpty()) {
                lastKey = null
                lastBytes = -1L
                return false
            }
            val key = busy.map { "${it.id}:${it.state}" }.sorted().joinToString(",")
            val bytes = busy.sumOf { it.downloadedBytes }
            if (key != lastKey || bytes > lastBytes) {
                lastKey = key
                lastBytes = bytes
                lastProgressAt = nowMs
            }
            return nowMs - lastProgressAt < graceMs
        }
    }

    /** 엔진을 닫아도 되는가 — 목록을 보는 화면이 없고, 받으려는 토렌트가 없고, 하던 일(옮기기·엔진 일)이 없다. */
    fun idle(observers: Int, states: Collection<TorrentState>, busy: Boolean): Boolean = observers <= 0 && !keepsForeground(states, busy)

    // ── 다 받은 파일의 자리 ──────────────────────────────────────────────────────────────────────────────

    /** 공용 '내려받기' 에서의 자리 — MediaStore 의 `RELATIVE_PATH`(끝에 `/`)와 파일 이름. */
    class Placement(val relativePath: String, val displayName: String)

    /**
     * 엔진이 정리한 경로([TorrentFile.path])를 '내려받기' 아래로. 여러 파일 토렌트는 맨 앞 성분이 토렌트 이름(폴더)이라 `Download/<이름>/<하위 경로>/`
     * 에 두고(사용자 결정 ⑮ — 공용 '내려받기/<이름>/'), **한 파일 토렌트는 `Download/` 에 곧바로** 둔다 — 그 이름의 폴더 안에 같은 이름의 파일 하나는
     * 군더더기다(명세와 다르게 정한 것 — CLAUDE.md). 성분은 엔진이 이미 정리했지만(`TorrentPaths`) MediaStore 에 넘기기 전에 한 번 더 거른다 —
     * 남이 지은 경로라, 정리를 빠져나온 `..`·구분자가 공용 폴더 밖을 가리키지 않게(`SafeFileName.clean` 은 같은 것을 두 번 걸러도 같다).
     */
    fun placement(path: List<String>, multiFile: Boolean): Placement {
        val clean = path.map { SafeFileName.clean(it) }
        val name = clean.lastOrNull() ?: SafeFileName.FALLBACK
        val dirs = if (multiFile) clean.dropLast(1) else emptyList()
        return Placement((listOf(DOWNLOAD_DIR) + dirs).joinToString("/") + "/", name)
    }

    /** 파일의 형식 — 확장자로([lookup] 은 `MimeTypeMap`), 모르면 `application/octet-stream`. */
    fun mimeOf(fileName: String, lookup: (String) -> String?): String {
        val dot = fileName.lastIndexOf('.')
        val ext = if (dot > 0 && dot < fileName.length - 1) fileName.substring(dot + 1).lowercase(Locale.ROOT) else ""
        return ext.takeIf { it.isNotEmpty() }?.let(lookup)?.takeIf { it.contains('/') } ?: "application/octet-stream"
    }

    // ── 마그넷의 파일 고르기 ─────────────────────────────────────────────────────────────────────────────

    /**
     * 마그넷의 메타데이터가 왔다 — 고를 만한 파일이 하나뿐이면 그것을(물을 것이 없다), 여럿이면 null(멈추고 사용자에게 묻는다 — 늘 순차로 받으니
     * 묻지 않으면 토렌트의 첫 파일부터 받기 시작한다). 고를 만한 파일은 패드·심볼릭 링크·옛 채움 파일(BitComet)이 아닌 것. 그런 것이 하나도 없는
     * 이상한 토렌트면 고를 수 있는 것을 모두.
     */
    fun autoSelection(files: List<TorrentFile>): Set<Int>? {
        val choosable = files.filter { it.isSelectable && !it.isPadLike }
        return when {
            choosable.size == 1 -> setOf(choosable[0].index)
            choosable.isEmpty() -> files.filter { it.isSelectable }.map { it.index }.toSet()
            else -> null
        }
    }

    // ── 앱이 죽은 뒤 마저 할 것 ──────────────────────────────────────────────────────────────────────────

    /** 엔진을 열었을 때(되살린 뒤) 마저 할 일. */
    sealed class Fix {
        /**
         * 앞서 '내려받기' 로 옮겼는데 엔진이 '옮겨 감' 을 적기 전에 앱이 죽었다 — 엔진에 알리고 앱 안에 남은 것을 지운다. [unselect] 면 엔진이 그
         * 파일의 조각을 잃은 것으로 본다(옮긴 뒤 지운 파일의 크기가 어긋나 다시 해시했다) — 고르기에서 빼야 이미 옮긴 파일을 다시 받지 않는다.
         */
        data class Release(val id: String, val index: Int, val unselect: Boolean) : Fix()

        /** 다 받았는데 옮기지 않았다(옮기기 전·옮기는 중에 앱이 죽었다, 앞서 옮기지 못했다) — 옮긴다. */
        data class Move(val id: String, val index: Int) : Fix()

        /** 옮겨 간 파일이 앱 안에 남았다(지우기 전에 앱이 죽었다) — 지운다. 엔진은 옮겨 간 파일을 다시 읽거나 쓰지 않는다. */
        data class DeleteLeftover(val id: String, val index: Int) : Fix()

        /** 옮기다 만 '내려받기' 항목(감춘 것) — 지운다. 다시 옮길 때는 새 항목에 처음부터 쓴다. */
        data class DropPending(val id: String, val index: Int, val mediaId: Long) : Fix()

        /** 엔진에 없는 토렌트의 기록 — 버린다(옮긴 파일은 그대로 둔다 — 사용자의 파일이다). */
        data class Forget(val id: String) : Fix()
    }

    /**
     * 엔진을 열자마자 버릴 것 — 옮기다 만 감춘 항목([pending] — 늘 버리고 다시 옮긴다: 반쯤 쓴 것을 이어 쓰지 않는다, 시스템이 이레 뒤에 지우는
     * 항목이라 이을 수 없을 때가 있다)과 엔진에 없는 토렌트([known] 밖)의 기록. 옮기던 것을 먼저 지운 뒤 기록을 버린다(차례가 이 목록의 차례다).
     */
    fun leftovers(known: Set<String>, moved: Map<String, Map<Int, Long>>, pending: Map<String, Map<Int, Long>>): List<Fix> {
        val out = ArrayList<Fix>()
        for ((id, files) in pending) for ((index, media) in files) out += Fix.DropPending(id, index, media)
        for (id in (moved.keys + pending.keys).toSortedSet()) if (id !in known) out += Fix.Forget(id)
        return out
    }

    /**
     * 되살린 토렌트 하나([s] — **엔진의 확인(크기가 어긋난 파일의 해시)이 끝난 상태**여야 한다: 그 전의 '다 받음' 은 이어받기 자료의 말일 뿐이고,
     * 확인이 아직 읽지 않은 파일을 앱이 먼저 지우면 확인이 그 조각들을 잃은 것으로 친다)와 앱의 기록([moved] — 파일 번호 → MediaStore 번호)을 견줘
     * 마저 할 일을 고른다. [inAppSize] 는 그 파일이 앱 안에 있으면 그 크기, 없으면 -1.
     */
    fun reconcile(s: TorrentStatus, moved: Map<Int, Long>, inAppSize: (Int) -> Long): List<Fix> {
        val out = ArrayList<Fix>()
        for (f in s.files) {
            when {
                f.released -> if (inAppSize(f.index) >= 0) out += Fix.DeleteLeftover(s.id, f.index)
                f.index in moved -> out += Fix.Release(s.id, f.index, unselect = f.selected && f.state != "done")
                f.selected && f.state == "done" -> out += Fix.Move(s.id, f.index)
            }
        }
        return out
    }

    // ── 알림 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 토렌트마다의 알림 번호 — 내려받기의 알림(1, 1000 + 번호)·녹화(2)·토렌트 받는 중(3)과 겹치지 않는 자리에 info-hash 로 고르게 흩는다(같은
     * 토렌트는 늘 같은 번호 — 다 받음·오류·멈춤이 앞 알림을 갈아 끼운다).
     */
    fun noticeId(id: String): Int = NOTICE_BASE + (id.hashCode() and 0xFFFFFF)

    private const val NOTICE_BASE = 0x10000000
}
