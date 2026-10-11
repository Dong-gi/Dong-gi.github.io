package io.github.donggi.iroirobrowser.download

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.DataUrl
import io.github.donggi.iroirobrowser.core.DownloadNames
import io.github.donggi.iroirobrowser.core.LinkRenewal
import io.github.donggi.iroirobrowser.core.SafeFileName
import io.github.donggi.iroirobrowser.core.Streams
import io.github.donggi.iroirobrowser.core.media.DashManifest
import io.github.donggi.iroirobrowser.core.media.HlsCrypto
import io.github.donggi.iroirobrowser.core.media.HlsPlaylist
import io.github.donggi.iroirobrowser.core.media.Id3Timestamp
import io.github.donggi.iroirobrowser.core.sites.GalleryCodec
import io.github.donggi.iroirobrowser.core.sites.GalleryFile
import io.github.donggi.iroirobrowser.core.sites.GalleryPlan
import io.github.donggi.iroirobrowser.core.sites.GalleryResolver
import io.github.donggi.iroirobrowser.core.sites.GalleryUnit
import io.github.donggi.iroirobrowser.core.sites.Site
import io.github.donggi.iroirobrowser.core.sites.SiteException
import io.github.donggi.iroirobrowser.core.sites.SiteFetch
import io.github.donggi.iroirobrowser.core.sites.SiteHttpException
import io.github.donggi.iroirobrowser.core.sites.SiteTarget
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.proxy.NetErrors
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 내려받기 — 보통 파일·HLS·DASH, 그리고 쪽 안에서 넘겨받는 것(`blob:`)과 가로채기·녹화의 마무리. **WebView 가 아니라 앱의 HTTP 로 다시
 * 받는다** — 길은 [NetworkPath.httpRoute](중계, 예외 규칙에 든 사설망 주소는 WebView 처럼 바로 — 넘겨주기의 홉마다 고른다)이고, 프록시가
 * 켜져 있는데 중계가 없으면 받지 않는다(직접 나가지 않는다). 쪽과 같게 보이도록
 * WebView 의 User-Agent·그 주소의 쿠키·Referer 를 싣는다(쿠키는 넘겨주기의 홉마다 다시 고르고, 넘겨주기가 준 쿠키는 WebView 에 넣는다).
 *
 * **다시 해 보기와 이어받기**(사용자 결정, 2026-10-05 — '한 조각만 실패해도 처음부터 받아야 한다면, 자동 다시시도(3회 실패 시 사용자가
 * 명시적인 이어받기)'). 보통 파일은 끊기면 받은 데서부터 `Range`(+`If-Range` — 그 사이 파일이 바뀌었으면 처음부터)로 이어 받고, 나아감 없이
 * 세 번 실패하면 **멈춤**(받은 것은 감춘 채 남긴다)으로 두어 사용자가 '이어받기' 를 누르게 한다. HLS·DASH 는 조각마다 세 번까지 다시 받고, 그래도
 * 안 되면 다 받은 조각까지 남긴 채 멈춘다. 서버가 4xx(408·429 밖)로 거절하면 다시 해 보지 않고 바로 멈춘다. 목록은 디스크에 두어
 * ([DownloadStore]) 앱이 다시 떠도 멈춘 것을 이어받는다(받던 중에 앱이 죽었으면 멈춤으로 돌아온다).
 *
 * **HLS·DASH 의 따로 된 소리는 영상과 한 파일로 합친다**(사용자 결정, 2026-10-05 — media3, [Remux]). 줄기마다 앱 안의 임시 파일로 받고 다
 * 받으면 그릇만 바꿔 담는다(H.264·AAC 는 MP4, VP9·Opus 는 WebM). 담지 못하는 조합이면 앞 판처럼 따로 된 파일로 남긴다. 줄기가 하나면
 * '내려받기' 폴더에 곧바로 잇는다 — TS 는 `.ts`, fMP4 는 `.mp4`(소리만 `.m4a`), 묶음 AAC 는 `.aac`.
 *
 * 잠긴 것: **AES-128 은 풀어서 받는다**(사용자 결정, 2026-10-05 — [HlsCrypto]), `SAMPLE-AES`·DRM(키 형식이 identity 가 아닌 것·
 * `ContentProtection`)은 받지 않는다. 실시간 방송은 받지 않는다. POST 로 받은 내려받기는 GET 으로 다시 받으므로 실패할 수 있다(알려진 한계).
 *
 * 조각은 **받으면서 곧바로 파일에 쓴다**(잠긴 것은 흘려 보내며 푼다) — 통째로 메모리에 올리지 않는다(DASH 의 파일 하나짜리 화질은 수백 MB 다).
 * 시도마다 길을 다시 얻는다([NetworkPath.httpRoute]) — 설정을 바꾸거나 스위치를 눌러 중계가 새 포트로 서면 옛 포트는 닫힌다.
 *
 * **주소가 만료됐거나 막혔으면**(401·403·404·410 — 서명된 CDN 주소) 같은 주소로 이어받아도 같으므로 까닭을 바로 적고([Item.expired]) 원래
 * 페이지([Item.pageUrl])를 다시 열게 한다. 같은 페이지에서 같은 미디어를 다시 고르면 멈춘 항목에 새 주소를 붙여 받은 데서부터 잇는다(**링크
 * 갱신** — [LinkRenewal]).
 *
 * 일꾼: 받기는 동시에 둘. 목록 살피기([inspectHls]·[inspectDash])와 가로채기·녹화의 마무리, 취소·지우기의 뒷정리는 받기 수와 따로 돈다 —
 * 받는 것이 둘이어도 화질 창·저장이 그 뒤에 줄 서지 않게.
 *
 * **갤러리 받기**([Kind.SET] — 사용자 결정, 2026-10-11: 'pixiv, hitomi 기능을 브라우저에 통합'). 계획 하나(작품의 쪽들·작가의 작품들·갤러리의
 * 장들 — `:core` 의 sites 가 읽는다, [planGallery])가 항목 하나다. 사이트에 묻는 것도 파일을 받는 것도 **이 클래스의 같은 길**([fetcher] — 세 번째
 * '바로 나감' 은 없다)이고 pixiv 는 WebView 의 로그인 쿠키로 묻는다(우리는 사용자 대신 로그인하지 않는다). 파일은 '내려받기/<사이트>/<작가>/…' 에
 * 하나씩 드러내고 곁의 기록([DownloadSets])에 적는다. 한 번에 하나, 보통 받기와 따로 된 일꾼에서([runSet]). 그만두면 받던 파일만 지우고 받은
 * 파일은 남긴다(사용자의 파일이다).
 */
@OptIn(UnstableApi::class)
class DownloadCenter(context: Context, private val network: NetworkPath) {

    enum class Status { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

    /**
     * FILE — 주소 하나, MEDIA — HLS·DASH(줄기 하나 또는 둘), PUSHED — 쪽이 넘겨주는 바이트(blob), REMUX — 가로채기·녹화의 마무리,
     * SET — 갤러리 받기(여러 파일을 한 항목으로 — [Gallery]).
     */
    enum class Kind { FILE, MEDIA, PUSHED, REMUX, SET }

    /**
     * 갤러리 받기 하나([Kind.SET] — pixiv 작품·작가, hitomi 갤러리)의 목록에 보일 것과 이어받을 자리. 계획 전체(단위들)는 곁의 파일([DownloadSets])
     * 에 있다. [fileCount] 는 알면 파일 수(작품의 쪽·갤러리의 장), 작가 받기는 null(작품을 풀 때 안다). [unitsDone] 개의 단위를 받았고, 지금 단위는
     * 그것을 푼 파일들의 [fileIndex] 번째부터 받는다.
     */
    class Gallery internal constructor(
        val site: Site,
        /** 같은 것을 두 번 받지 않으려는 열쇠([SiteTarget.key] — `pixiv-user-123`). */
        val key: String,
        val title: String,
        val creator: String?,
        /** 공용 '내려받기' 아래 폴더 성분 — 받을 때 한 번 더 거른다([GalleryRules.relativePath]). */
        val folder: List<String>,
        units: Int,
        val fileCount: Int?,
    ) {
        @Volatile var units: Int = units
            internal set

        @Volatile var unitsDone: Int = 0
            internal set

        @Volatile var fileIndex: Int = 0
            internal set

        @Volatile var filesSaved: Int = 0
            internal set

        @Volatile var skipped: Int = 0
            internal set

        /** 건너뛴 것 가운데 로그인이 필요해서(pixiv) — 로그인하면 받을 수 있다. */
        @Volatile var skippedLogin: Int = 0
            internal set
    }

    /**
     * 미디어 받기의 한 줄기 — 영상 또는 따로 된 소리. HLS 는 재생목록 주소(이어받을 때 다시 읽는다 — 서명된 주소가 바뀌었을 수 있다), DASH 는
     * 미리 정한 조각 차례. 둘 다 링크 갱신이 새 것으로 바꾼다.
     */
    class Track internal constructor(
        playlistUrl: String?,
        plan: List<Part>?,
        val mimeType: String?,
        val audio: Boolean,
    ) {
        @Volatile var playlistUrl: String? = playlistUrl
            internal set

        @Volatile internal var plan: List<Part>? = plan

        @Volatile var parts: Int = 0
        @Volatile var partsDone: Int = 0

        /** 다 받은 조각까지 쓴 바이트 — 이어 쓸 자리. */
        @Volatile var bytes: Long = 0
        @Volatile var extension: String? = null
        @Volatile var container: Remux.Container? = null

        /** 묶음 AAC 의 첫 시각(ID3) — 합칠 때 영상 TS 의 PTS 와 맞춘다. */
        @Volatile var offsetUs: Long = 0

        /**
         * 받은 조각마다의 바이트 수 — 합칠 때 TS 를 조각마다 따로 읽는다. 따로 만든 조각은 연속성 카운터가 조각마다 처음부터일 수 있고, 이어 붙인
         * 채 읽으면 추출기가 그 끊김에서 읽던 PES 를 버려 조각마다 끝 프레임을 잃었다(실험실 merge-ts — 168 중 158).
         */
        internal val partSizes: MutableList<Long> = java.util.Collections.synchronizedList(ArrayList())
    }

    class Item internal constructor(
        val id: Long,
        val kind: Kind,
        url: String,
        /** 보통 파일은 확장자까지, 미디어·가로채기는 확장자 없이(받으면서 그릇을 보고 붙인다). */
        val fileName: String,
        val mimeType: String?,
        userAgent: String,
        referer: String?,
        internal val tracks: List<Track> = emptyList(),
        pageUrl: String? = null,
        pageTitle: String? = null,
        /** [Kind.SET] 이면 그 갤러리 받기, 아니면 null. */
        val gallery: Gallery? = null,
    ) {
        /** 받는 주소(미디어는 영상 줄기의 재생목록 또는 첫 조각) — 링크 갱신이 바꾼다. */
        @Volatile var url: String = url
            internal set

        @Volatile var userAgent: String = userAgent
            internal set

        @Volatile var referer: String? = referer
            internal set

        /**
         * 이것을 고른 페이지의 주소와 제목 — 주소가 만료되면 '원래 페이지 열기' 로 다시 열고, 같은 페이지에서 같은 미디어를 다시 고르면 이
         * 항목에 새 주소를 붙여 잇는다(링크 갱신). 모르면 null(앞 판이 남긴 항목·blob·가로채기).
         */
        @Volatile var pageUrl: String? = pageUrl
            internal set

        @Volatile var pageTitle: String? = pageTitle
            internal set

        /** 주소가 만료됐거나 막혔다(401·403·404·410) — 같은 주소로 이어받아도 같다. 원래 페이지를 다시 열어 고르게 한다. */
        @Volatile var expired: Boolean = false
            internal set

        /** 마무리([merging]) 중에 담는 것 — 목록·알림의 글('영상과 소리를 한 파일로' 또는 '소리를 파일로'). */
        @Volatile var finishing: Finishing = Finishing.AV
            internal set

        @Volatile var status: Status = Status.QUEUED
            internal set

        @Volatile var received: Long = 0
            internal set

        @Volatile var total: Long = -1
            internal set

        /** 조각 수와 받은 조각 수(미디어 — 줄기들을 합친 것). 보통 파일은 0. */
        val parts: Int get() = tracks.sumOf { it.parts }
        val partsDone: Int get() = tracks.sumOf { it.partsDone }

        @Volatile var savedUri: Uri? = null
            internal set

        @Volatile var savedName: String = fileName
            internal set

        @Volatile var savedMime: String? = mimeType
            internal set

        @Volatile var error: String? = null
            internal set

        /** 끝난 뒤의 덧붙임 — '합치지 못해 따로 저장' 등. */
        @Volatile var note: String? = null
            internal set

        /** 다 받고 그릇을 바꿔 담는 중. */
        @Volatile var merging: Boolean = false
            internal set

        /** 지금 다시 해 보는 중이면 몇 번째인가(0 — 아님). */
        @Volatile var retrying: Int = 0
            internal set

        @Volatile internal var cancelled = false
        @Volatile internal var pauseRequested = false

        /** 받는 중인(감춘) '내려받기' 항목 — 이어 쓸 곳. */
        @Volatile internal var pendingUri: Uri? = null
        @Volatile internal var etag: String? = null
        @Volatile internal var lastModified: String? = null

        /** REMUX — 무엇을 담을지(일꾼에서 부른다 — 훑기가 오래 걸릴 수 있다). 디스크에 두지 않는다. */
        @Volatile internal var remux: (() -> RemuxJob)? = null

        /** SET — 계획(단위들). 받는 동안 메모리에 두고, 앱이 다시 뜨면 곁의 파일([DownloadSets])에서 읽는다. downloads.json 에 두지 않는다. */
        @Volatile internal var plan: GalleryPlan? = null

        val isActive: Boolean get() = status == Status.QUEUED || status == Status.RUNNING
        val canResume: Boolean get() = status == Status.PAUSED

        /** 멈췄다 이을 수 있는 것(보통 파일·HLS·DASH·갤러리) — 넘겨받는 것(blob)·마무리는 다시 할 수 없다. */
        val pausable: Boolean get() = kind == Kind.FILE || kind == Kind.MEDIA || kind == Kind.SET
    }

    /** 마무리에서 담는 것. */
    enum class Finishing { AV, VIDEO, AUDIO }

    /** 목록 살피기 하나(화질을 고르기 전) — [cancel] 하면 답을 부르지 않는다. */
    class Inspection internal constructor() {
        @Volatile var cancelled: Boolean = false
            private set

        /** 그만둔다 — 답을 부르지 않고 다시 해 보지 않는다(받던 요청은 살피기의 짧은 시간 제한 안에 끝난다). 어느 스레드에서나. */
        fun cancel() {
            cancelled = true
        }
    }

    /**
     * 잇는 조각 하나 — 주소, 바이트 범위, (HLS 의 잠금이면) 열쇠와 조각 번호. 초기 조각의 번호는 0 이다 — 잠긴 초기 조각은 RFC 8216 이 IV 를
     * 적으라고 하고, 적지 않은 것은 hls.js 처럼 0 으로 푼다.
     */
    class Part internal constructor(val url: String, val range: HlsPlaylist.ByteRange?, val key: HlsPlaylist.Key?, val sequence: Long)

    /** 가로채기·녹화의 마무리 — 담을 줄기들, 담지 못하면 대신 남길 파일들(소리면 이름에 '(소리)'), 끝나면 지울 폴더. */
    class RemuxJob(val sources: List<Remux.Source>, val fallback: List<Fallback>, val cleanup: File?) {
        class Fallback(val file: File, val extension: String, val mimeType: String, val audio: Boolean)
    }

    /** [inspectDash] 의 답. */
    sealed class DashInfo {
        class Ready(val manifest: DashManifest) : DashInfo()
        class Failed(val message: String) : DashInfo()
    }

    /** [inspectHls] 의 답. */
    sealed class HlsInfo {
        class Choices(val master: HlsPlaylist.Master) : HlsInfo()
        class Single(val media: HlsPlaylist.Media) : HlsInfo()
        class Failed(val message: String) : HlsInfo()
    }

    /** [fetchSmall] 의 답. */
    sealed class SmallFile {
        class Ready(val bytes: ByteArray) : SmallFile()
        class Failed(val message: String) : SmallFile()
    }

    /** [planGallery] 의 답 — 계획, 또는 받을 수 없는 까닭(우리 문장). */
    sealed class GalleryResult {
        class Ready(val plan: GalleryPlan) : GalleryResult()
        class Failed(val message: String) : GalleryResult()
    }

    fun interface Listener {
        fun onChanged(item: Item?)
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 받기 — 동시에 둘. */
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "web-download").apply { isDaemon = true } }

    /** 목록 살피기 — 받기와 따로(받는 것이 둘이어도 화질 창이 바로 뜨게). */
    private val inspector = Executors.newFixedThreadPool(2) { r -> Thread(r, "web-download-inspect").apply { isDaemon = true } }

    /** 가로채기·녹화의 마무리 — 받기와 따로, 하나씩(담는 동안 MediaMuxer 가 메모리를 쓴다). */
    private val finisher = Executors.newSingleThreadExecutor { r -> Thread(r, "web-download-finish").apply { isDaemon = true } }

    /**
     * 갤러리 받기 — 한 번에 하나, 보통 받기 줄과 따로. 작가 받기는 몇 시간이 걸릴 수 있어 그것이 보통 파일을 막지 않게, 그리고 같은 로그인으로
     * 사이트에 몰아 묻지 않게(둘째는 앞의 것이 끝날 때까지 '기다리는 중').
     */
    private val galleryWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-download-gallery").apply { isDaemon = true } }

    /** 취소·지우기의 뒷정리 — 받기 줄에 서지 않게(앞 판은 받는 것이 둘이면 기다리는 항목의 취소도 그 뒤에 섰다). */
    private val chores = Executors.newSingleThreadExecutor { r -> Thread(r, "web-download-chores").apply { isDaemon = true } }
    private val pushExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "web-download-push").apply { isDaemon = true } }
    private val storeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "web-download-store").apply { isDaemon = true } }
    private val store = DownloadStore(app)
    private val ids = AtomicLong()
    private val items = CopyOnWriteArrayList<Item>()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val savePending = AtomicBoolean(false)
    private val workRoot = File(app.noBackupFilesDir, "downloads")

    /** 갤러리 받기 곁의 계획·기록 — [restore] 가 찌꺼기를 치우므로 그보다 먼저 세운다. */
    private val sets = DownloadSets(File(app.noBackupFilesDir, "download-sets"))

    val names = DownloadNames(
        extensionForMime = { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) },
        mimeForExtension = { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) },
    )

    init {
        restore()
    }

    fun items(): List<Item> = items.toList()

    fun activeCount(): Int = items.count { it.isActive }

    fun find(id: Long): Item? = items.firstOrNull { it.id == id }

    fun addListener(l: Listener) {
        listeners += l
    }

    fun removeListener(l: Listener) {
        listeners -= l
    }

    // ── 줄 세우기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 보통 파일(또는 `data:`). 메인 스레드. [pageUrl]·[pageTitle] 은 이것을 고른 페이지 — 주소가 만료되면 다시 열 곳이고, 그 페이지에서 같은
     * 파일을 다시 받으면 멈춘 항목에 새 주소를 붙여 잇는다(링크 갱신 — 그때는 그 항목을 돌려준다).
     */
    fun enqueue(
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String,
        referer: String?,
        pageUrl: String? = null,
        pageTitle: String? = null,
    ): Item {
        renewable(pageUrl, hls = false, dash = false, listOf(url))?.let { old ->
            return renew(old, url, userAgent, referer, pageTitle) {}
        }
        return start(Item(ids.incrementAndGet(), Kind.FILE, url, fileName, mimeType, userAgent, referer, pageUrl = pageUrl, pageTitle = pageTitle))
    }

    /**
     * HLS — [playlistUrl] 은 영상의 미디어 재생목록(화질을 고른 뒤의 것), [audioUrl] 은 따로 된 소리의 재생목록(있으면 한 파일로 합친다).
     * [baseName] 에 확장자는 받으면서 붙인다. [pageUrl]·[pageTitle] 은 [enqueue] 와 같다(같은 화질·같은 소리면 멈춘 항목을 잇는다).
     * [audioOnly] 면 [playlistUrl] 이 소리의 재생목록이다 — fMP4 소리가 `.mp4` 가 아니라 `.m4a` 로 나간다(화질 창의 '소리만').
     */
    fun enqueueHls(
        playlistUrl: String,
        audioUrl: String?,
        baseName: String,
        userAgent: String,
        referer: String?,
        pageUrl: String? = null,
        pageTitle: String? = null,
        audioOnly: Boolean = false,
    ): Item {
        val urls = listOfNotNull(playlistUrl, audioUrl)
        renewable(pageUrl, hls = true, dash = false, urls)?.let { old ->
            return renew(old, playlistUrl, userAgent, referer, pageTitle) { old.tracks.forEachIndexed { i, t -> t.playlistUrl = urls[i] } }
        }
        val tracks = listOfNotNull(Track(playlistUrl, null, null, audio = audioOnly), audioUrl?.let { Track(it, null, null, audio = true) })
        return start(
            Item(ids.incrementAndGet(), Kind.MEDIA, playlistUrl, SafeFileName.clean(baseName), null, userAgent, referer, tracks, pageUrl, pageTitle),
        )
    }

    /** DASH — 영상 화질과 소리(둘 중 하나는 없어도 된다)를 받아 한 파일로. [pageUrl]·[pageTitle] 은 [enqueue] 와 같다(같은 화질·같은 조각 수면 잇는다). */
    fun enqueueDash(
        video: DashManifest.Representation?,
        audio: DashManifest.Representation?,
        baseName: String,
        userAgent: String,
        referer: String?,
        pageUrl: String? = null,
        pageTitle: String? = null,
    ): Item {
        val tracks = listOfNotNull(video?.let { dashTrack(it, audio = false) }, audio?.let { dashTrack(it, audio = true) })
        require(tracks.isNotEmpty())
        val first = (video ?: audio)!!.segments.firstOrNull()?.url ?: ""
        val plans = tracks.map { it.plan.orEmpty() }
        renewable(pageUrl, hls = false, dash = true, plans.map { it.firstOrNull()?.url.orEmpty() })
            ?.takeIf { old -> old.tracks.map { it.plan?.size } == plans.map { it.size } }
            ?.let { old -> return renew(old, first, userAgent, referer, pageTitle) { old.tracks.forEachIndexed { i, t -> t.plan = plans[i] } } }
        return start(Item(ids.incrementAndGet(), Kind.MEDIA, first, SafeFileName.clean(baseName), null, userAgent, referer, tracks, pageUrl, pageTitle))
    }

    private fun dashTrack(rep: DashManifest.Representation, audio: Boolean): Track {
        val parts = (listOfNotNull(rep.init) + rep.segments).map { p ->
            val offset = p.offset
            val length = p.length
            Part(p.url, if (offset != null && length != null) HlsPlaylist.ByteRange(length, offset) else null, null, 0)
        }
        return Track(null, parts, rep.mimeType ?: if (audio) "audio/mp4" else "video/mp4", audio)
    }

    /**
     * 링크 갱신할 멈춘 항목 — 같은 페이지([LinkRenewal.samePage])에서 고른 같은 종류이고, 줄기마다 같은 자원([LinkRenewal.sameTracks] — 질의만
     * 다른 서명된 주소)인 것 가운데 가장 최근 것. 줄기는 보통 파일이면 그 주소, HLS 면 재생목록(영상·소리), DASH 면 줄기의 첫 조각.
     */
    private fun renewable(pageUrl: String?, hls: Boolean, dash: Boolean, urls: List<String>): Item? {
        if (pageUrl == null) return null
        return items.firstOrNull { old ->
            old.status == Status.PAUSED && LinkRenewal.samePage(old.pageUrl, pageUrl) && when {
                hls -> old.kind == Kind.MEDIA && old.tracks.all { it.playlistUrl != null } &&
                    LinkRenewal.sameTracks(old.tracks.map { it.playlistUrl!! }, urls)
                dash -> old.kind == Kind.MEDIA && old.tracks.all { it.plan != null } &&
                    LinkRenewal.sameTracks(old.tracks.map { it.plan?.firstOrNull()?.url.orEmpty() }, urls)
                else -> old.kind == Kind.FILE && LinkRenewal.sameTracks(listOf(old.url), urls)
            }
        }
    }

    /** 링크 갱신 — 멈춘 항목에 새 주소([change] 가 줄기를 바꾼다)를 붙여 받은 데서부터 잇는다. 메인 스레드(그 항목은 멈춰 있다). */
    private fun renew(item: Item, url: String, userAgent: String, referer: String?, pageTitle: String?, change: () -> Unit): Item {
        item.url = url
        item.userAgent = userAgent
        item.referer = referer
        pageTitle?.takeIf { it.isNotBlank() }?.let { item.pageTitle = it }
        change()
        debug("download renewed ${item.savedName}")
        resume(item)
        return item
    }

    /** 가로채기·녹화를 마무리한다 — [job] 은 일꾼에서 부른다. [baseName] 에 확장자는 담을 그릇을 보고 붙인다. */
    fun enqueueRemux(baseName: String, job: () -> RemuxJob): Item {
        val item = Item(ids.incrementAndGet(), Kind.REMUX, "", SafeFileName.clean(baseName), null, "", null)
        item.remux = job
        return start(item)
    }

    /**
     * 갤러리 받기를 줄 세운다 — [plan] 은 [planGallery] 가 읽은 것(사용자가 확인 창에서 받기를 눌렀다). 메인 스레드. 계획 전체는 곁의 파일에
     * 적고(뒷정리 일꾼 — 디스크라, 차례를 기다리는 동안 앱이 죽어도 남게), 목록(downloads.json)에는 보일 것과 이어받을 자리만 둔다. [userAgent] 는
     * 그 탭의 WebView 의 것이다(쿠키는 받을 때 주소마다 WebView 에서 — [headers]). 원래 페이지는 대상의 정규 주소다.
     */
    fun enqueueGallery(plan: GalleryPlan, userAgent: String): Item {
        val target = plan.target
        val title = plan.title.ifBlank { target.key }
        val gallery = Gallery(target.site, target.key, title, plan.creator?.takeIf { it.isNotBlank() }, plan.folder, plan.units.size, plan.fileCount)
        val item = Item(
            ids.incrementAndGet(), Kind.SET, target.canonicalUrl, title, null, userAgent, null,
            pageUrl = target.canonicalUrl, pageTitle = title, gallery = gallery,
        )
        item.plan = plan
        val id = item.id
        // 새 번호에 남은 곁의 파일은 찌꺼기다(목록이 깨져 번호가 처음부터 다시 셌다 등) — 남의 기록을 이어받을 자리로 읽지 않게 받기를 줄 세우기
        // 전에 비운다(없는 파일 셋을 지우는 일이라 곧바로).
        sets.forget(id)
        chores.execute { writePlan(id, plan) }
        return start(item)
    }

    /** 계획을 곁의 파일에 — 못 적으면 이 프로세스 안에서는 메모리의 것으로 받는다(앱이 다시 뜨면 '목록을 읽지 못했다' 로 끝난다). */
    private fun writePlan(id: Long, plan: GalleryPlan) {
        val text = try {
            GalleryCodec.encode(plan)
        } catch (e: RuntimeException) {
            debug("gallery plan not encoded ${e.javaClass.simpleName}")
            return
        }
        if (!sets.writePlan(id, text)) debug("gallery plan not written $id")
    }

    /** 같은 대상([SiteTarget.key])의 갤러리 받기 항목들 — 다시 받으려 할 때 묻는다([GalleryPrompt]). */
    fun galleryItems(key: String): List<Item> = items.filter { it.kind == Kind.SET && it.gallery?.key == key }

    /**
     * 쪽이 넘겨주는 바이트(`blob:`)를 받는다 — 바이트는 [Pushed.write] 로 차례로 온다. 메인 스레드. 다시 받을 수 없으므로 끊기면 실패다.
     */
    fun startPushed(fileName: String, mimeType: String?, size: Long): Pushed {
        val item = Item(ids.incrementAndGet(), Kind.PUSHED, "blob:", fileName, mimeType, "", null)
        item.total = size
        item.status = Status.RUNNING
        items.add(0, item)
        changed(item)
        saveSoon()
        DownloadService.ensureRunning(app)
        return Pushed(item)
    }

    private fun start(item: Item): Item {
        items.add(0, item)
        trim()
        changed(item)
        saveSoon()
        DownloadService.ensureRunning(app)
        workerFor(item).execute { run(item) }
        return item
    }

    /** 마무리(가로채기·녹화)는 받기 줄에 서지 않는다 — 받는 것이 둘이어도 바로 담는다. 갤러리 받기는 제 줄에서 하나씩([galleryWorker]). */
    private fun workerFor(item: Item) = when (item.kind) {
        Kind.REMUX -> finisher
        Kind.SET -> galleryWorker
        else -> executor
    }

    /**
     * DASH 목록을 읽어 본다 — 살피기 일꾼에서(받기 수와 따로), 답은 메인 스레드로. 살피기는 짧게 해 본다(두 번·짧은 시간 제한 — 그동안 사용자가
     * 화면 앞에서 기다린다). 돌려준 것으로 그만둘 수 있다(그러면 [done] 을 부르지 않는다).
     */
    fun inspectDash(url: String, userAgent: String, referer: String?, done: (DashInfo) -> Unit): Inspection {
        val job = Inspection()
        inspector.execute {
            val info = try {
                val bytes = getBytes(url, userAgent, referer, null, INSPECT, MAX_PLAYLIST_BYTES) { job.cancelled }
                DashInfo.Ready(DashManifest.parse(bytes.decodeToString(), url))
            } catch (e: Cancelled) {
                null
            } catch (e: IOException) {
                DashInfo.Failed(NetErrors.describe(e))
            } catch (e: DashManifest.Invalid) {
                DashInfo.Failed(NOT_A_MANIFEST)
            }
            if (info != null) main.post { if (!job.cancelled) done(info) }
        }
        return job
    }

    /** 재생목록을 읽어 본다(화질을 고르기 전에) — [inspectDash] 와 같은 꼴. */
    fun inspectHls(url: String, userAgent: String, referer: String?, done: (HlsInfo) -> Unit): Inspection {
        val job = Inspection()
        inspector.execute {
            val info = try {
                when (val p = readPlaylist(url, userAgent, referer, null, INSPECT) { job.cancelled }) {
                    is HlsPlaylist.Master -> HlsInfo.Choices(p)
                    is HlsPlaylist.Media -> HlsInfo.Single(p)
                }
            } catch (e: Cancelled) {
                null
            } catch (e: IOException) {
                HlsInfo.Failed(NetErrors.describe(e))
            } catch (e: HlsPlaylist.Invalid) {
                HlsInfo.Failed(NOT_A_PLAYLIST)
            }
            if (info != null) main.post { if (!job.cancelled) done(info) }
        }
        return job
    }

    /**
     * 작은 파일 하나(`.torrent`)를 메모리로 받는다 — **웹 쪽 길**(앱의 HTTP, 중계 — 프록시를 켰는데 중계가 없으면 받지 않는다)로, 그 페이지의
     * 쿠키·Referer·User-Agent 를 싣는다(로그인해야 받는 토렌트 파일이 있다). 토렌트의 내용은 엔진이 기기에서 바로 받지만 이 파일은 페이지가 건넨
     * 내려받기다. [limit] 바이트를 넘으면 받지 않는다. 살피기 일꾼에서(받기 수와 따로), 답은 메인 스레드로 — [inspectHls] 와 같은 꼴.
     */
    fun fetchSmall(url: String, userAgent: String, referer: String?, limit: Int, done: (SmallFile) -> Unit): Inspection {
        val job = Inspection()
        inspector.execute {
            val result = try {
                SmallFile.Ready(getBytes(url, userAgent, referer, null, INSPECT, limit) { job.cancelled })
            } catch (e: Cancelled) {
                null
            } catch (e: Streams.TooLarge) {
                SmallFile.Failed(app.getString(R.string.torrent_file_too_big))
            } catch (e: IOException) {
                SmallFile.Failed(NetErrors.describe(e))
            }
            if (result != null) main.post { if (!job.cancelled) done(result) }
        }
        return job
    }

    /**
     * 갤러리를 읽어 계획을 짓는다(확인 창에 보일 것과 받을 것) — 살피기 일꾼에서(받기 수와 따로), 답은 메인 스레드로. [inspectHls] 와 같은 꼴이라
     * 그만둘 수 있다. 묻는 길은 내려받기와 같다([siteFetch] — [fetcher], WebView 의 쿠키·[userAgent]). 받을 단위가 없으면 '받을 파일이 없다'.
     * 해석기가 무엇을 던지든 앱을 내리지 않고 까닭 한 줄로 끝낸다(사이트의 이상한 답 하나가 일꾼 스레드를 죽이면 앱이 죽는다).
     */
    fun planGallery(target: SiteTarget, userAgent: String, done: (GalleryResult) -> Unit): Inspection {
        val job = Inspection()
        inspector.execute {
            val result = try {
                val plan = GalleryResolver(siteFetch(userAgent, null, INSPECT) { job.cancelled }).plan(target) { job.cancelled }
                if (plan.units.isEmpty()) {
                    GalleryResult.Failed(text(GalleryRules.reason(SiteException(SiteException.Reason.EMPTY), target.site)))
                } else {
                    GalleryResult.Ready(plan)
                }
            } catch (e: Cancelled) {
                null
            } catch (e: Throwable) {
                if (job.cancelled) {
                    null
                } else {
                    debug("gallery plan failed ${target.key} ${e.javaClass.simpleName}")
                    GalleryResult.Failed(text(GalleryRules.reason(e, target.site)))
                }
            }
            if (result != null) main.post { if (!job.cancelled) done(result) }
        }
        return job
    }

    // ── 사용자의 손 ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 취소 — 받은 것을 지운다. 기다리는·멈춘 것은 곧바로(뒷정리 일꾼 — 받기 줄에 서지 않는다), 받는 중이면 받던 일꾼이 멈추며 지운다. 갤러리는
     * **받던 파일(감춘 것)만** 지우고 이미 드러낸 파일은 남긴다('그만 받기' — 사용자의 파일이다. 그것까지는 [deleteSetFiles]).
     */
    fun cancel(item: Item) {
        item.cancelled = true
        if (item.status == Status.QUEUED || item.status == Status.PAUSED) {
            chores.execute {
                cleanup(item)
                finish(item, Status.CANCELLED, null)
            }
        }
    }

    /** 일시 정지 — 받은 것은 남긴다(보통 파일·HLS·DASH·갤러리만). */
    fun pause(item: Item) {
        if (!item.pausable) return
        item.pauseRequested = true
    }

    /** 이어받기 — 멈춘 데서부터. */
    fun resume(item: Item) {
        if (item.status != Status.PAUSED) return
        item.pauseRequested = false
        item.cancelled = false
        item.error = null
        item.expired = false
        item.status = Status.QUEUED
        changed(item)
        saveSoon()
        DownloadService.ensureRunning(app)
        workerFor(item).execute { run(item) }
    }

    /** 끝난 것을 목록에서 뺀다 — 받은 파일은 그대로, 멈춘 것은 받던 것을 지운다(뒷정리 일꾼에서). 갤러리는 곁의 계획·기록도 버린다. */
    fun remove(item: Item) {
        if (item.isActive) return
        items.remove(item)
        if (item.status == Status.PAUSED) chores.execute { cleanup(item) }
        if (item.kind == Kind.SET) chores.execute { sets.forget(item.id) }
        changed(null)
        saveSoon()
    }

    /**
     * 갤러리 받기를 **받은 파일까지** 지우고 목록에서 뺀다 — 곁의 기록에 적은 URI 들(이 앱이 만든 '내려받기' 항목만 지울 수 있다 — 사용자가 옮겼거나
     * 다른 앱이 바꿨으면 못 지운다)과 받던 파일. 비게 된 폴더는 남는다(이 앱의 권한으로는 폴더를 지울 수 없다 — 토렌트와 같다). [done] 은 메인
     * 스레드에서 — 지운 파일 수.
     */
    fun deleteSetFiles(item: Item, done: (Int) -> Unit) {
        if (item.isActive || item.kind != Kind.SET) return
        items.remove(item)
        changed(null)
        saveSoon()
        chores.execute {
            var deleted = 0
            for (uri in sets.journal(item.id).savedUris) {
                if (MediaStoreWriter.deleteCounted(app, Uri.parse(uri))) deleted++
            }
            cleanup(item)
            sets.forget(item.id)
            debug("gallery files deleted $deleted")
            main.post { done(deleted) }
        }
    }

    /**
     * 다 받은 것을 **파일까지** 지우고 목록에서 뺀다 — 이 앱이 만든 '내려받기' 항목만 지울 수 있다(사용자가 옮기거나 다른 앱이 바꿨으면 지우지
     * 못한다). [done] 은 메인 스레드에서 — 지웠는가.
     */
    fun deleteFile(item: Item, done: (Boolean) -> Unit) {
        if (item.isActive) return
        val uri = item.savedUri
        items.remove(item)
        changed(null)
        saveSoon()
        chores.execute {
            val deleted = uri != null && MediaStoreWriter.deleteCounted(app, uri)
            if (item.status == Status.PAUSED) cleanup(item)
            main.post { done(deleted) }
        }
    }

    /** 실패·취소한 것을 처음부터 다시. */
    fun retry(item: Item): Item? {
        if (item.isActive || item.kind == Kind.PUSHED || item.kind == Kind.REMUX) return null
        if (item.status == Status.PAUSED) {
            resume(item)
            return item
        }
        // 갤러리를 처음부터 다시 받으면 받은 파일이 겹친다(' (1)' 사본) — 원래 페이지에서 다시 받는다(그때 한 번 묻는다).
        if (item.kind == Kind.SET) return null
        items.remove(item)
        val tracks = item.tracks.map { Track(it.playlistUrl, it.plan, it.mimeType, it.audio) }
        return start(
            Item(ids.incrementAndGet(), item.kind, item.url, item.fileName, item.mimeType, item.userAgent, item.referer, tracks, item.pageUrl, item.pageTitle),
        )
    }

    /**
     * 받은 파일을 연다 — **기본 앱으로**(정해 둔 앱이 없으면 시스템이 고르는 창을 띄운다 — '항상' 을 고를 수 있다). 앞 판은 열 때마다 앱 고르기
     * 창(chooser)을 띄웠다.
     */
    fun openIntent(item: Item): Intent? {
        val uri = item.savedUri ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.savedMime ?: "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 받은 파일을 다른 앱에 건넨다(공유 — 고르는 창으로). */
    fun shareIntent(item: Item): Intent? {
        val uri = item.savedUri ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.savedMime ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, item.savedName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ── 받기 ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun run(item: Item) {
        if (item.cancelled) {
            cleanup(item)
            finish(item, Status.CANCELLED, null)
            return
        }
        if (item.pauseRequested) {
            finish(item, Status.PAUSED, PAUSED_BY_USER)
            return
        }
        item.status = Status.RUNNING
        item.error = null
        item.retrying = 0
        changed(item)
        saveSoon()
        try {
            when (item.kind) {
                Kind.FILE -> if (item.url.startsWith("data:", ignoreCase = true)) saveDataUrl(item) else runFile(item)
                Kind.MEDIA -> runMedia(item)
                Kind.REMUX -> runRemux(item)
                Kind.SET -> runSet(item)
                Kind.PUSHED -> return
            }
            finish(item, Status.DONE, null)
        } catch (e: Throwable) {
            fail(item, e)
        }
    }

    /**
     * 끝나지 못한 까닭에 따라 — 취소·정지·거절(다시 해도 안 될 것)·저장 실패·망(이어받을 수 있다). 주소가 만료됐거나 막혔으면(401·403·404·410
     * — 목적지의 답) 받은 것은 남기고 멈추되 그 까닭을 바로 적는다 — 같은 주소로 이어받아도 같으니 원래 페이지를 다시 열어 고르게 한다(그러면
     * 링크 갱신으로 잇는다).
     */
    private fun fail(item: Item, e: Throwable) {
        when {
            item.cancelled -> {
                cleanup(item)
                finish(item, Status.CANCELLED, null)
            }
            item.pauseRequested -> finish(item, Status.PAUSED, PAUSED_BY_USER)
            item.kind == Kind.SET -> failSet(item, e)
            e is HttpStatus && !e.byProxy && e.code in EXPIRED_CODES && item.pausable -> {
                item.expired = true
                val text = if (item.kind == Kind.MEDIA) R.string.download_expired_media else R.string.download_expired_file
                finish(item, Status.PAUSED, app.getString(text, e.code))
            }
            e is Refused -> {
                cleanup(item)
                finish(item, Status.FAILED, e.message)
            }
            e is HlsPlaylist.Invalid -> {
                cleanup(item)
                finish(item, Status.FAILED, NOT_A_PLAYLIST)
            }
            e is SecurityException || e is StorageFailure -> {
                cleanup(item)
                finish(item, Status.FAILED, SAVE_FAILED)
            }
            e is IOException && (item.kind == Kind.FILE || item.kind == Kind.MEDIA) -> finish(item, Status.PAUSED, NetErrors.describe(e))
            e is IOException -> {
                cleanup(item)
                finish(item, Status.FAILED, NetErrors.describe(e))
            }
            else -> {
                cleanup(item)
                finish(item, Status.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * 갤러리 받기가 멈췄다 — 받던 파일(감춘 것)은 버린다(이어받을 때 그 파일은 처음부터다). 다시 해도 같을 것(막힘·꼴이 다름)도, 이어 실패한 망도,
     * 저장 실패도 **멈춤**으로 둔다 — 사용자가 로그인하거나 공간을 비운 뒤 이어받는다. 계획을 잃은 것만 실패다(이어받을 것이 없다).
     */
    private fun failSet(item: Item, e: Throwable) {
        dropPending(item)
        when (e) {
            is Refused -> finish(item, Status.FAILED, e.message)
            is SetStop -> finish(item, Status.PAUSED, e.message)
            is StorageFailure, is SecurityException -> finish(item, Status.PAUSED, app.getString(R.string.gallery_err_save))
            else -> {
                debug("gallery stopped ${e.javaClass.simpleName}")
                finish(item, Status.PAUSED, text(GalleryRules.reason(e, item.gallery?.site)))
            }
        }
    }

    /** 받던(감춘) 항목을 지운다 — 갤러리는 이어받을 때 받던 파일을 처음부터 받는다. */
    private fun dropPending(item: Item) {
        item.pendingUri?.let { MediaStoreWriter.delete(app, it) }
        item.pendingUri = null
    }

    /** 요청 머리 — WebView 와 같은 User-Agent, 쪽의 Referer, 그 주소의 쿠키(넘겨주기의 홉마다 다시 고른다). */
    private fun headers(userAgent: String, referer: String?, url: String, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            referer?.let { put("Referer", it) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
            putAll(extra)
        }

    private val storeCookies: (String, List<String>) -> Unit = { url, values -> values.forEach { CookieManager.getInstance().setCookie(url, it) } }

    /**
     * 앱의 HTTP — **부를 때마다 지금 길로**(중계의 지금 포트). 설정을 바꾸거나 스위치를 누르면 중계가 새 포트로 서고 옛 포트는 닫힌다 — 앞 판의
     * HLS·DASH 받기는 처음에 한 번 얻은 것을 끝까지 써서 '연결하지 못했습니다' 로 멈췄다. 길([NetworkPath.httpRoute])은 넘겨주기의 **홉마다**
     * 예외 규칙을 보고 바로 또는 중계로 고른다(사설망의 공유기·NAS 에서 받기). 길이 아직 없으면 연결을 열지 않고 [HttpFetcher.NoRoute].
     */
    private fun fetcher(patience: Patience = DOWNLOAD): HttpFetcher = HttpFetcher(
        network.httpRoute(),
        connectTimeoutMs = patience.connectMs,
        readTimeoutMs = patience.readMs,
    )

    /** 2xx 밖의 답 — 프록시(중계·Squid)가 만든 답인지도 적는다(그것은 목적지의 '만료' 가 아니다). */
    private fun httpStatus(r: HttpFetcher.Response) = HttpStatus(r.code, NetErrors.describeStatus(r), NetErrors.isProxyAnswer(r))

    // ── 보통 파일 ──────────────────────────────────────────────────────────────────────────────────────────

    /** 끊기면 받은 데서부터 이어 받는다 — 나아감 없이 [RETRIES] 번 실패하면 멈춘다. */
    private fun runFile(item: Item) {
        var failures = 0
        while (true) {
            if (item.cancelled) throw Cancelled()
            if (item.pauseRequested) throw Cancelled()
            val before = item.received
            try {
                fetchFileOnce(item)
                item.retrying = 0
                return
            } catch (e: Cancelled) {
                throw e
            } catch (e: Refused) {
                throw e
            } catch (e: StorageFailure) {
                throw e
            } catch (e: IOException) {
                if (item.received > before) failures = 0
                failures++
                if (!retryable(e) || failures >= RETRIES) throw e
                item.retrying = failures
                item.error = NetErrors.describe(e)
                debug("download retry ${item.fileName} $failures ${item.error} at=${item.received}")
                changed(item)
                saveSoon()
                pauseFor(item, BACKOFF_MS[failures - 1])
            }
        }
    }

    private fun fetchFileOnce(item: Item) {
        val existing = item.pendingUri?.takeIf { MediaStoreWriter.exists(app, it) }
        if (existing == null) {
            item.pendingUri = null
            item.received = 0
        }
        val resumeAt = if (existing != null) item.received else 0L
        val extra = if (resumeAt > 0) {
            buildMap {
                put("Range", "bytes=$resumeAt-")
                // 그 사이 파일이 바뀌었으면 서버가 통째로(200) 준다 — 처음부터 다시 쓴다.
                (item.etag?.takeUnless { it.startsWith("W/") } ?: item.lastModified)?.let { put("If-Range", it) }
            }
        } else {
            emptyMap()
        }
        fetcher().get(item.url, headersFor = { u -> headers(item.userAgent, item.referer, u, extra) }, onSetCookie = storeCookies).use { r ->
            val range = if (r.code == 206) ContentRange.parse(r.header("Content-Range")) else null
            val append = when {
                resumeAt > 0 && r.code == 206 && range?.start == resumeAt -> true
                r.code == 206 && range != null && range.start != 0L -> {
                    // 묻지 않은 자리부터 왔다 — 다음 시도는 처음부터.
                    item.received = 0
                    throw IOException("범위가 맞지 않습니다")
                }
                r.code in 200..299 -> false
                r.code == 416 && resumeAt > 0 && item.total == resumeAt -> {
                    publish(item, existing!!)
                    return
                }
                else -> throw httpStatus(r)
            }
            if (!append) {
                item.received = 0
                item.total = range?.total ?: r.contentLength
                item.etag = r.header("ETag")
                item.lastModified = r.header("Last-Modified")
                item.savedMime = item.mimeType ?: r.contentType?.substringBefore(';')?.trim()
            } else if (range?.total != null) {
                item.total = range.total
            }
            val uri = existing ?: try {
                MediaStoreWriter.create(app, item.fileName, item.savedMime ?: "application/octet-stream")
            } catch (e: IOException) {
                throw StorageFailure()
            }
            item.pendingUri = uri
            saveSoon()
            openAt(uri, item.received).use { out -> copy(item, r.body, out) }
            if (item.total > 0 && item.received < item.total) throw IOException("받다가 끊겼습니다")
            publish(item, uri)
        }
    }

    private fun copy(item: Item, input: java.io.InputStream, out: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var lastNotice = 0L
        while (true) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            val n = input.read(buffer)
            if (n < 0) break
            try {
                out.write(buffer, 0, n)
            } catch (e: IOException) {
                throw StorageFailure()
            }
            item.received += n
            val now = System.nanoTime()
            if (now - lastNotice > NOTICE_NS) {
                lastNotice = now
                changed(item)
                saveSoon()
            }
        }
    }

    private fun publish(item: Item, uri: Uri) {
        item.savedName = MediaStoreWriter.publish(app, uri) ?: item.fileName
        item.savedUri = uri
        item.pendingUri = null
    }

    private fun openAt(uri: Uri, position: Long): FileOutputStream = try {
        MediaStoreWriter.openAt(app, uri, position)
    } catch (e: IOException) {
        throw StorageFailure()
    } catch (e: SecurityException) {
        throw StorageFailure()
    }

    private fun saveDataUrl(item: Item) {
        val data = DataUrl.parse(item.url) ?: throw Refused("data: 주소를 읽지 못했습니다")
        item.total = data.bytes.size.toLong()
        item.savedMime = item.mimeType ?: data.mimeType
        val uri = MediaStoreWriter.create(app, item.fileName, item.savedMime ?: data.mimeType)
        item.pendingUri = uri
        openAt(uri, 0).use { it.write(data.bytes) }
        item.received = data.bytes.size.toLong()
        publish(item, uri)
    }

    // ── 갤러리(pixiv·hitomi) ───────────────────────────────────────────────────────────────────────────────

    /**
     * 갤러리 받기 — 단위를 [Gallery.unitsDone] 부터 차례로 파일로 풀고([GalleryResolver.expand] — 받기 한 번마다 해석기를 새로 지어 hitomi 의
     * `gg.js` 도 새로 읽는다), 그 파일들을 하나씩 흘려 받아 '내려받기/<폴더>/' 에 드러낸다([saveSetFile]). 드러내거나 건너뛸 때마다 곁의 기록에
     * 적는다([DownloadSets] — 이어받을 자리와 '받은 파일까지 지우기' 의 원본). pixiv 작가 받기는 작품과 작품 사이에 [GalleryResolver.PIXIV_GAP_MS]
     * 쉬고, 지워졌거나 로그인이 필요한 작품은 건너뛰고 센다. hitomi 그림이 없으면(404·410) `gg.js` 를 버리고 그 그림을 한 번 더 셈해 본 뒤에야
     * 건너뛴다. 그 밖의 실패는 멈춘다([SetStop] — 이어받기는 지금 단위·지금 파일부터, 파일은 처음부터). 다 받으면 건너뛴 수를 [Item.note] 에.
     */
    private fun runSet(item: Item) {
        val g = item.gallery ?: throw Refused(app.getString(R.string.gallery_err_plan_lost))
        val plan = planOf(item, g)
        // 이어받을 자리 — 덧붙이기 기록이 목록(1.5초 모아 적는다)보다 앞서 있을 수 있다(앱이 그 사이에 죽었다). 수도 기록이 원본이다.
        val journal = sets.journal(item.id)
        val at = GalleryRules.resumeAt(GalleryRules.Position(g.unitsDone, g.fileIndex), journal.last)
        // 차례(파일 → 단위)를 지킨다 — 목록을 적는 일꾼이 둘 사이를 읽어도 그 자리가 실제보다 앞서지 않게(앞서면 이어받을 때 파일을 건너뛴다).
        g.fileIndex = at.file
        g.unitsDone = at.unit
        g.filesSaved = maxOf(g.filesSaved, journal.saved)
        g.skipped = maxOf(g.skipped, journal.skipped)
        g.skippedLogin = maxOf(g.skippedLogin, journal.skippedLogin)
        // 받던 파일은 처음부터 — 앞 시도가 남긴 감춘 항목을 버린다.
        dropPending(item)
        changed(item)
        saveSoon()
        val stop = { item.cancelled || item.pauseRequested }
        val resolver = GalleryResolver(siteFetch(item.userAgent, item, DOWNLOAD, stop))
        var lastWasWork = false
        while (g.unitsDone < plan.units.size) {
            if (stop()) throw Cancelled()
            val index = g.unitsDone
            val unit = plan.units[index]
            val kind = GalleryRules.kindOf(unit)
            // pixiv 의 작품을 잇달아 묻지 않는다(데스크톱은 0.3초) — 같은 로그인으로 몰아 물으면 계정이 제한될 수 있다.
            if (kind == GalleryRules.UnitKind.PIXIV_WORK && lastWasWork) pauseFor(item, GalleryResolver.PIXIV_GAP_MS)
            lastWasWork = kind == GalleryRules.UnitKind.PIXIV_WORK
            var files = expandUnit(item, g, resolver, unit, kind, index)
            var reResolved = false
            while (files != null && g.fileIndex < files.size) {
                val position = g.fileIndex
                try {
                    saveSetFile(item, g, files[position], index, position)
                    g.fileIndex = position + 1
                } catch (e: Cancelled) {
                    throw e
                } catch (e: StorageFailure) {
                    throw e
                } catch (e: IOException) {
                    when (val step = GalleryRules.onFileFailure(e, kind, reResolved, stop(), g.site)) {
                        GalleryRules.FileStep.Stop -> throw Cancelled()
                        GalleryRules.FileStep.ReResolve -> {
                            // 그 사이 `gg.js` 가 바뀌었을 수 있다 — 버리고 그 그림의 주소를 다시 셈해 같은 파일을 한 번 더.
                            debug("gallery re-resolve ${g.key} #$index")
                            resolver.invalidate()
                            reResolved = true
                            files = expandUnit(item, g, resolver, unit, kind, index)
                        }
                        GalleryRules.FileStep.Skip -> {
                            dropPending(item)
                            sets.appendSkipped(item.id, index, position, login = false)
                            g.skipped++
                            g.fileIndex = position + 1
                            debug("gallery file skipped ${g.key} #$index/$position")
                        }
                        is GalleryRules.FileStep.Pause -> throw SetStop(text(step.reason))
                    }
                }
                changed(item)
                saveSoon()
            }
            // 파일 차례를 먼저 — 목록을 적는 일꾼은 단위를 먼저 읽는다([toJson]). 그 사이를 읽으면 (이 단위, 0) — 실제보다 뒤라 기록이 바로잡는다.
            g.fileIndex = 0
            g.unitsDone = index + 1
            changed(item)
            saveSoon()
        }
        item.note = GalleryRules.note(g.skipped, g.skippedLogin)?.let { text(it) }
        debug("gallery done ${g.key} files=${g.filesSaved} skipped=${g.skipped}")
    }

    /**
     * 계획 — 메모리에 있으면 그것을, 없으면(앱이 다시 떴다) 곁의 파일에서. 없거나 깨졌거나 다른 대상의 것이면 실패다([Refused] — 이어받을 것이
     * 없다, 원래 페이지에서 다시 받는다).
     */
    private fun planOf(item: Item, g: Gallery): GalleryPlan {
        item.plan?.let { return it }
        val lost = { Refused(app.getString(R.string.gallery_err_plan_lost)) }
        val text = sets.readPlan(item.id) ?: throw lost()
        val plan = try {
            GalleryCodec.decode(text)
        } catch (e: SiteException) {
            throw lost()
        } catch (e: RuntimeException) {
            throw lost()
        }
        if (plan.target.key != g.key) throw lost()
        g.units = plan.units.size
        item.plan = plan
        return plan
    }

    /**
     * 단위를 받을 파일들로 — 건너뛸 단위(작가 받기의 지워진·로그인이 필요한·쪽이 없는 작품)면 기록하고 세어 null. 멈출 것이면 던진다
     * ([GalleryRules.onExpandFailure]). 해석기의 요청은 [siteFetch] 가 망·5xx 를 세 번까지 해 본 뒤의 실패다.
     */
    private fun expandUnit(
        item: Item,
        g: Gallery,
        resolver: GalleryResolver,
        unit: GalleryUnit,
        kind: GalleryRules.UnitKind,
        index: Int,
    ): List<GalleryFile>? {
        val stop = { item.cancelled || item.pauseRequested }
        val files = try {
            resolver.expand(unit, stop)
        } catch (e: Cancelled) {
            throw e
        } catch (e: IOException) {
            when (val step = GalleryRules.onExpandFailure(e, kind, stop(), g.site)) {
                GalleryRules.UnitStep.Stop -> throw Cancelled()
                is GalleryRules.UnitStep.Skip -> {
                    skipUnit(item, g, index, step.login)
                    return null
                }
                is GalleryRules.UnitStep.Pause -> throw SetStop(text(step.reason))
            }
        }
        // 해석기의 요청이 다시 해 보다 풀렸다 — '다시 해 보는 중' 을 걷는다.
        item.retrying = 0
        if (files.isEmpty()) {
            skipUnit(item, g, index, login = false)
            return null
        }
        return files
    }

    private fun skipUnit(item: Item, g: Gallery, index: Int, login: Boolean) {
        sets.appendSkipped(item.id, index, -1, login)
        g.skipped++
        if (login) g.skippedLogin++
        debug("gallery unit skipped ${g.key} #$index login=$login")
    }

    /**
     * 갤러리의 파일 하나 — 감춘 항목('내려받기/<폴더>/')에 흘려 받아 드러내고 곁의 기록에 적는다. 끊기면 처음부터 [RETRIES] 번까지 해 본다(뒤로
     * 물러서며 — 4xx 는 한 번, [retryable]). 받는 동안 감춘 항목의 URI 를 [Item.pendingUri] 로 목록에 적는다 — 그 사이 앱이 죽어도 고아 정리
     * ([sweepOrphans])가 지우지 않게(이어받을 때 이 파일은 처음부터 받으며 지운다).
     */
    private fun saveSetFile(item: Item, g: Gallery, file: GalleryFile, unit: Int, index: Int) {
        var last: IOException? = null
        for (attempt in 0 until RETRIES) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            val before = item.received
            try {
                saveSetFileOnce(item, g, file, unit, index)
                item.retrying = 0
                return
            } catch (e: IOException) {
                // 받던 파일은 처음부터 다시 받는다 — 받은 크기도 드러낸 파일까지로 되돌린다.
                item.received = before
                if (e is Cancelled || e is StorageFailure) throw e
                last = e
                if (!retryable(e)) break
                if (attempt < RETRIES - 1) {
                    item.retrying = attempt + 1
                    item.error = NetErrors.describe(e)
                    changed(item)
                    pauseFor(item, BACKOFF_MS[attempt])
                }
            }
        }
        item.retrying = 0
        throw last ?: IOException(NOT_RECEIVED)
    }

    private fun saveSetFileOnce(item: Item, g: Gallery, file: GalleryFile, unit: Int, index: Int) {
        fetcher().get(file.url, headersFor = { u -> headers(item.userAgent, file.referer, u) }, onSetCookie = storeCookies).use { r ->
            if (r.code !in 200..299) throw SiteHttpException(r.code, NetErrors.describeStatus(r), NetErrors.isProxyAnswer(r))
            // 이름은 사이트가 지은 것이라 한 번 더 거른다(같은 것을 두 번 걸러도 같다). 형식은 확장자로 — 모르면 octet-stream(이름이 그대로 남는다).
            val name = SafeFileName.clean(file.fileName)
            val mime = TorrentRules.mimeOf(name) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            val uri = item.pendingUri ?: try {
                MediaStoreWriter.create(app, name, mime, g.folder)
            } catch (e: IOException) {
                throw StorageFailure()
            } catch (e: SecurityException) {
                throw StorageFailure()
            }
            item.pendingUri = uri
            saveSoon()
            val start = item.received
            openAt(uri, 0).use { out -> copy(item, r.body, out) }
            val length = r.contentLength
            if (length > 0 && item.received - start < length) throw IOException("받다가 끊겼습니다")
            MediaStoreWriter.publish(app, uri)
            item.pendingUri = null
            sets.appendSaved(item.id, unit, index, uri.toString())
            g.filesSaved++
            if (item.savedUri == null) {
                item.savedUri = uri
                item.savedMime = mime
            }
        }
    }

    /**
     * 사이트에서 작은 덩이(JSON·JS)를 읽는 길 — `:core` 의 해석기에 준다. **내려받기와 같은 길**이다([getBytes] — [fetcher], WebView 의 쿠키·
     * [userAgent], [patience] 만큼 다시 해 보기 — [item] 이 있으면 그 항목에 '다시 해 보는 중' 을 보인다). 2xx 밖은 [SiteHttpException](프록시가 만든
     * 답인지도 싣는다). [stop] 이 참이면 그만둔다.
     */
    private fun siteFetch(userAgent: String, item: Item?, patience: Patience, stop: () -> Boolean) = SiteFetch { url, referer, limit ->
        try {
            getBytes(url, userAgent, referer, item, patience, limit, stop)
        } catch (e: HttpStatus) {
            throw SiteHttpException(e.code, e.message ?: "HTTP ${e.code}", e.byProxy)
        }
    }

    private fun text(part: TorrentTexts.Part): String = TorrentTexts.text(app, part)

    // ── HLS·DASH ─────────────────────────────────────────────────────────────────────────────────────────

    /** 줄기 하나의 조각 차례와 그릇. */
    private class Plan(val parts: List<Part>, val extension: String, val mimeType: String, val container: Remux.Container)

    private fun runMedia(item: Item) {
        val plans = item.tracks.map { planFor(item, it) }
        item.tracks.forEachIndexed { i, t ->
            if (t.parts != plans[i].parts.size) {
                // 처음이거나 재생목록이 바뀌었다 — 그 줄기는 처음부터.
                t.parts = plans[i].parts.size
                t.partsDone = 0
                t.bytes = 0
                t.partSizes.clear()
            }
            // 이어받기 — 기록한 조각 크기가 받은 조각 수보다 많으면(앞 시도가 다 적지 못했다) 자른다.
            synchronized(t.partSizes) { while (t.partSizes.size > t.partsDone) t.partSizes.removeAt(t.partSizes.size - 1) }
            t.extension = plans[i].extension
            t.container = plans[i].container
        }
        item.received = item.tracks.sumOf { it.bytes }
        changed(item)
        saveSoon()
        if (item.tracks.size == 1) {
            val t = item.tracks[0]
            val plan = plans[0]
            var uri = item.pendingUri?.takeIf { MediaStoreWriter.exists(app, it) }
            if (uri == null) {
                t.partsDone = 0
                t.bytes = 0
                t.partSizes.clear()
                item.received = 0
                uri = try {
                    MediaStoreWriter.create(app, "${item.fileName}.${plan.extension}", plan.mimeType)
                } catch (e: IOException) {
                    throw StorageFailure()
                }
                item.pendingUri = uri
                saveSoon()
            }
            item.savedMime = plan.mimeType
            openAt(uri, t.bytes).use { out -> fetchParts(item, t, plan, out) }
            publish(item, uri)
            return
        }
        val dir = workDir(item)
        item.tracks.forEachIndexed { i, t ->
            val f = File(dir, "track$i.part")
            if (!f.exists()) {
                t.partsDone = 0
                t.bytes = 0
                t.partSizes.clear()
            }
            // 앞 시도가 반쯤 쓴 조각을 잘라 내고 이어 쓴다.
            RandomAccessFile(f, "rw").use { it.setLength(t.bytes) }
            FileOutputStream(f, true).use { out -> fetchParts(item, t, plans[i], out) }
        }
        merge(item, dir)
    }

    /** 줄기의 조각 차례 — HLS 는 재생목록을 (다시) 읽고, DASH 는 미리 정한 것. 실시간·DRM 은 거절. */
    private fun planFor(item: Item, track: Track): Plan {
        track.plan?.let { parts ->
            val mime = track.mimeType ?: "video/mp4"
            return when (mime) {
                "video/webm", "audio/webm" -> Plan(parts, "webm", mime, Remux.Container.WEBM)
                "audio/mp4" -> Plan(parts, "m4a", mime, Remux.Container.FMP4)
                else -> Plan(parts, "mp4", mime, Remux.Container.FMP4)
            }
        }
        val url = track.playlistUrl ?: throw Refused(NOT_A_PLAYLIST)
        val media = when (val p = readPlaylist(url, item.userAgent, item.referer, item)) {
            is HlsPlaylist.Media -> p
            // 마스터를 곧바로 받으라 했으면 가장 높은 화질로.
            is HlsPlaylist.Master -> readPlaylist(p.variants.first().uri, item.userAgent, item.referer, item) as? HlsPlaylist.Media
                ?: throw Refused(NOT_A_PLAYLIST)
        }
        if (!media.ended) throw Refused(HLS_LIVE)
        if (media.segments.isEmpty()) throw Refused("조각이 없습니다")
        if (media.segments.any { it.key?.isAes128 == false }) throw Refused(HLS_DRM)
        val (ext, mime, container) = when {
            media.fragmentedMp4 && track.audio -> Triple("m4a", "audio/mp4", Remux.Container.FMP4)
            media.fragmentedMp4 -> Triple("mp4", "video/mp4", Remux.Container.FMP4)
            media.segments.all { it.uri.substringBefore('?').endsWith(".aac", ignoreCase = true) } -> Triple("aac", "audio/aac", Remux.Container.ADTS)
            else -> Triple("ts", "video/mp2t", Remux.Container.TS)
        }
        // 조각 차례 — 초기 조각(fMP4)은 그것을 쓰는 첫 조각 앞에, 바뀔 때마다 한 번. 초기 조각도 잠겼으면 그 MAP 이 나온 자리의 잠금으로 푼다
        // (RFC 8216 4.3.2.4 — 앞 판은 늘 평문으로 보아 잠긴 초기 조각이면 moov 가 깨졌다). IV 는 RFC 가 적으라 하고, 없으면 hls.js 처럼 0.
        val parts = ArrayList<Part>()
        var lastMap: String? = null
        for (seg in media.segments) {
            seg.map?.let { map ->
                val id = map.uri + "@" + (map.byteRange?.offset ?: -1)
                if (id != lastMap) {
                    parts += Part(map.uri, map.byteRange, map.key?.takeIf { it.isAes128 }, 0)
                    lastMap = id
                }
            }
            parts += Part(seg.uri, seg.byteRange, seg.key, seg.sequence)
        }
        return Plan(parts, ext, mime, container)
    }

    /**
     * 조각들을 차례로 받아 [out] 에 잇는다(이어받기면 [Track.partsDone] 부터). 잠긴 조각(AES-128)은 흘려 보내며 푼다. 조각은 통째로 메모리에
     * 올리지 않고 받으면서 쓴다([fetchPart]).
     */
    private fun fetchParts(item: Item, track: Track, plan: Plan, out: FileOutputStream) {
        val keys = HashMap<String, ByteArray>()
        val ticker = Ticker(item)
        for (k in track.partsDone until plan.parts.size) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            val part = plan.parts[k]
            val secret = part.key?.let { key ->
                val keyUri = key.uri ?: throw Refused(HLS_DRM)
                keys.getOrPut(keyUri) {
                    try {
                        getBytes(keyUri, item.userAgent, item.referer, item, DOWNLOAD, MAX_KEY_BYTES)
                    } catch (e: Streams.TooLarge) {
                        // 열쇠 대신 페이지 따위가 왔다.
                        throw Refused(app.getString(R.string.download_key_invalid))
                    }
                }.also { if (it.size != 16) throw Refused(app.getString(R.string.download_key_invalid)) }
            }
            // 묶음 AAC 의 첫 조각은 앞머리(ID3 의 시각)를 함께 쥔다 — 합칠 때 영상 TS 의 PTS 와 맞춘다.
            val head = if (k == 0 && plan.container == Remux.Container.ADTS) HeadCapture(ID3_HEAD_BYTES) else null
            val at = track.bytes
            val before = item.received
            val written = fetchPart(item, part, secret, out, at, head) { n ->
                item.received = before + n
                ticker.tick()
            }
            head?.let { track.offsetUs = Id3Timestamp.firstTimestampUs(it.bytes()) ?: 0 }
            track.bytes = at + written
            track.partSizes += written
            track.partsDone++
            item.received = before + written
            item.retrying = 0
            ticker.tick()
            saveSoon()
        }
        changed(item)
        saveSoon()
    }

    /**
     * 조각 하나를 받아 [out] 의 [at] 자리부터 곧바로 쓴다 — 통째로 메모리에 올리지 않는다(DASH 의 파일 하나짜리 화질은 수백 MB 다). 바이트 범위면
     * `Range` 로 묻고, 서버가 통째로(200) 주면 그 범위만 읽는다([Streams.slice] — Long 셈). 끊기면 [at] 까지 잘라 내고 [RETRIES] 번까지 다시
     * 받는다(4xx 는 한 번) — 시도마다 길을 다시 얻는다([fetcher]). [progress] 는 이 조각에서 쓴 바이트 수. 쓴 바이트 수를 돌려준다.
     */
    private fun fetchPart(
        item: Item,
        part: Part,
        secret: ByteArray?,
        out: FileOutputStream,
        at: Long,
        head: HeadCapture?,
        progress: (Long) -> Unit,
    ): Long {
        var last: IOException? = null
        for (attempt in 0 until RETRIES) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            try {
                rewind(out, at)
                head?.reset()
                progress(0)
                val range = part.range
                val extra = range?.let { mapOf("Range" to "bytes=${it.offset}-${it.offset + it.length - 1}") } ?: emptyMap()
                fetcher().get(part.url, headersFor = { u -> headers(item.userAgent, item.referer, u, extra) }, onSetCookie = storeCookies).use { r ->
                    if (r.code != 200 && r.code != 206) throw httpStatus(r)
                    val body = if (range != null && r.code == 200) Streams.slice(r.body, range.offset, range.length) else r.body
                    return pump(item, part, secret, body, out, head, progress)
                }
            } catch (e: Cancelled) {
                throw e
            } catch (e: Refused) {
                throw e
            } catch (e: StorageFailure) {
                throw e
            } catch (e: IOException) {
                last = e
                if (!retryable(e)) break
                if (attempt < RETRIES - 1) {
                    item.retrying = attempt + 1
                    item.error = NetErrors.describe(e)
                    changed(item)
                    pauseFor(item, BACKOFF_MS[attempt])
                }
            }
        }
        throw last ?: IOException(NOT_RECEIVED)
    }

    /** 몸을 [out] 에 흘려 쓴다 — 잠겼으면 풀며(CBC 를 덩이마다 이어 푼다 — 끝의 채움은 마지막에 확인한다). 쓰기가 실패하면 [StorageFailure]. */
    private fun pump(
        item: Item,
        part: Part,
        secret: ByteArray?,
        body: InputStream,
        out: FileOutputStream,
        head: HeadCapture?,
        progress: (Long) -> Unit,
    ): Long {
        val cipher = secret?.let { s ->
            try {
                Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(s, "AES"), IvParameterSpec(part.key?.iv ?: HlsCrypto.ivFor(part.sequence)))
                }
            } catch (e: GeneralSecurityException) {
                throw Refused(DECRYPT_FAILED)
            }
        }
        var written = 0L
        fun emit(bytes: ByteArray, length: Int) {
            if (length <= 0) return
            try {
                out.write(bytes, 0, length)
            } catch (e: IOException) {
                throw StorageFailure()
            }
            head?.add(bytes, length)
            written += length
        }
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            val n = body.read(buffer)
            if (n < 0) break
            if (cipher == null) emit(buffer, n) else cipher.update(buffer, 0, n)?.let { emit(it, it.size) }
            progress(written)
        }
        if (cipher != null) {
            val tail = try {
                cipher.doFinal()
            } catch (e: GeneralSecurityException) {
                // 채움이 맞지 않는다 — 열쇠나 IV 가 틀렸다(다시 받아도 같다).
                throw Refused(DECRYPT_FAILED)
            }
            emit(tail, tail.size)
        }
        progress(written)
        return written
    }

    /** 앞 시도가 반쯤 쓴 것을 잘라 내고 [at] 에서 다시 쓴다. */
    private fun rewind(out: FileOutputStream, at: Long) {
        try {
            val channel = out.channel
            if (channel.size() > at) channel.truncate(at)
            channel.position(at)
        } catch (e: IOException) {
            throw StorageFailure()
        }
    }

    /** 앞머리 [limit] 바이트를 쥐어 둔다(묶음 AAC 의 ID3 시각을 읽으려고). */
    private class HeadCapture(private val limit: Int) {
        private val bytes = ByteArrayOutputStream()

        fun reset() = bytes.reset()

        fun add(data: ByteArray, length: Int) {
            val take = minOf(length, limit - bytes.size())
            if (take > 0) bytes.write(data, 0, take)
        }

        fun bytes(): ByteArray = bytes.toByteArray()
    }

    /** 진행 알림을 [NOTICE_NS] 에 한 번으로 모은다(목록·알림을 쉼 없이 다시 그리지 않게). */
    private inner class Ticker(private val item: Item) {
        private var last = 0L

        fun tick() {
            val now = System.nanoTime()
            if (now - last <= NOTICE_NS) return
            last = now
            changed(item)
        }
    }

    /** 다 받은 줄기들을 한 파일로 — 담지 못하면 따로 된 파일로 남긴다. */
    private fun merge(item: Item, dir: File) {
        item.finishing = finishingOf(item.tracks.map { it.audio })
        item.merging = true
        changed(item)
        val sources = item.tracks.mapIndexed { i, t ->
            val file = File(dir, "track$i.part")
            val container = t.container ?: Remux.Container.TS
            val sizes = synchronized(t.partSizes) { t.partSizes.toList() }
            val runs = if (container == Remux.Container.TS && sizes.size == t.partsDone && sizes.sum() == file.length()) {
                // TS 는 조각마다 따로(위 partSizes).
                var at = 0L
                sizes.map { n -> Remux.Run(file, container, at, n).also { at += n } }
            } else {
                listOf(Remux.Run(file, container, offsetUs = t.offsetUs))
            }
            Remux.Source(runs, if (t.audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_VIDEO)
        }
        try {
            remuxInto(item, sources)
            debug("download merged ${item.savedName} tracks=${sources.size} bytes=${item.received}")
        } catch (e: Remux.Encrypted) {
            throw Refused(HLS_DRM)
        } catch (e: Remux.Failure) {
            // 앞 판처럼 따로 된 파일로 남긴다.
            val saved = item.tracks.mapIndexed { i, t ->
                val name = if (t.audio) app.getString(R.string.media_audio_name, item.fileName) else item.fileName
                MediaStoreWriter.copyIn(app, File(dir, "track$i.part"), "$name.${t.extension}", mimeFor(t))
            }
            item.savedUri = saved.first().first
            item.savedName = saved.first().second
            item.savedMime = mimeFor(item.tracks.first())
            item.note = app.getString(R.string.download_merge_failed, e.message ?: "")
            debug("download merge-failed ${e.message}")
        } finally {
            item.merging = false
        }
        dir.deleteRecursively()
    }

    /** 담는 것 — 줄기들이 소리인지([audio])로. 녹화의 '소리만' 이 '영상과 소리를 한 파일로' 로 나오던 것을 가른다. */
    private fun finishingOf(audio: List<Boolean>): Finishing = when {
        audio.any { it } && audio.any { !it } -> Finishing.AV
        audio.isNotEmpty() && audio.all { it } -> Finishing.AUDIO
        else -> Finishing.VIDEO
    }

    private fun mimeFor(t: Track): String = when (t.extension) {
        "ts" -> "video/mp2t"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "webm" -> if (t.audio) "audio/webm" else "video/webm"
        else -> "video/mp4"
    }

    /** 담아 '내려받기' 폴더에 둔다 — 이름의 확장자는 담을 그릇이 정한다. */
    private fun remuxInto(item: Item, sources: List<Remux.Source>) {
        Remux(sources) { item.cancelled }.use { remux ->
            val plan = remux.prepare()
            // 실제로 담길 트랙을 알았다 — 영상만 녹화된 것(소리 줄기는 있었으나 트랙이 없다) 등.
            item.finishing = when {
                plan.video != null && plan.audio != null -> Finishing.AV
                plan.video != null -> Finishing.VIDEO
                else -> Finishing.AUDIO
            }
            changed(item)
            val name = "${item.fileName}.${plan.extension}"
            val uri = try {
                MediaStoreWriter.create(app, name, plan.mimeType)
            } catch (e: IOException) {
                throw StorageFailure()
            }
            try {
                val size = (app.contentResolver.openFileDescriptor(uri, "rw") ?: throw StorageFailure()).use { pfd ->
                    remux.writeTo(pfd.fileDescriptor)
                    pfd.statSize
                }
                item.savedMime = plan.mimeType
                item.received = size
                publish(item, uri)
            } catch (e: Throwable) {
                MediaStoreWriter.delete(app, uri)
                throw e
            }
        }
    }

    private fun runRemux(item: Item) {
        val job = item.remux?.invoke() ?: throw Refused("담을 것이 없습니다")
        item.finishing = finishingOf(job.sources.map { it.type == C.TRACK_TYPE_AUDIO })
        item.merging = true
        changed(item)
        try {
            remuxInto(item, job.sources)
            debug("download remuxed ${item.savedName} bytes=${item.received}")
        } catch (e: Remux.Encrypted) {
            throw Refused(HLS_DRM)
        } catch (e: Remux.Failure) {
            if (job.fallback.isEmpty()) throw Refused(e.message ?: "담지 못했습니다")
            val saved = job.fallback.map { f ->
                val name = if (f.audio) app.getString(R.string.media_audio_name, item.fileName) else item.fileName
                MediaStoreWriter.copyIn(app, f.file, "$name.${f.extension}", f.mimeType)
            }
            item.savedUri = saved.first().first
            item.savedName = saved.first().second
            item.savedMime = job.fallback.first().mimeType
            item.received = job.fallback.sumOf { it.file.length() }
            item.note = app.getString(R.string.download_merge_failed, e.message ?: "")
            debug("download remux-failed ${e.message}")
        } finally {
            item.merging = false
            job.cleanup?.deleteRecursively()
        }
    }

    private fun readPlaylist(
        url: String,
        userAgent: String,
        referer: String?,
        item: Item?,
        patience: Patience = DOWNLOAD,
        stop: () -> Boolean = { item?.cancelled == true || item?.pauseRequested == true },
    ): HlsPlaylist {
        val bytes = try {
            getBytes(url, userAgent, referer, item, patience, MAX_PLAYLIST_BYTES, stop)
        } catch (e: Streams.TooLarge) {
            throw Refused("재생목록이 너무 큽니다")
        }
        return HlsPlaylist.parse(bytes.decodeToString(), url)
    }

    /**
     * 작은 덩이(재생목록·열쇠·DASH 목록)를 받는다 — [limit] 바이트까지만([Streams.readAll] — 넘으면 끊는다. 큰 것을 통째로 쥐다 메모리를 다
     * 쓰지 않게). [patience] 만큼 해 본다(4xx 는 한 번). 시도마다 길을 다시 얻는다([fetcher]). [stop] 이 참이면 그만둔다([Cancelled]).
     * 조각은 여기서 받지 않는다 — 받으면서 쓴다([fetchPart]).
     */
    private fun getBytes(
        url: String,
        userAgent: String,
        referer: String?,
        item: Item?,
        patience: Patience = DOWNLOAD,
        limit: Int = MAX_PLAYLIST_BYTES,
        stop: () -> Boolean = { item?.cancelled == true || item?.pauseRequested == true },
    ): ByteArray {
        var last: IOException? = null
        for (attempt in 0 until patience.attempts) {
            if (stop()) throw Cancelled()
            try {
                fetcher(patience).get(url, headersFor = { u -> headers(userAgent, referer, u) }, onSetCookie = storeCookies).use { r ->
                    if (r.code != 200 && r.code != 206) throw httpStatus(r)
                    return Streams.readAll(r.body, limit)
                }
            } catch (e: Cancelled) {
                throw e
            } catch (e: IOException) {
                last = e
                if (!retryable(e)) break
                if (attempt < patience.attempts - 1) {
                    val wait = patience.backoffMs[attempt.coerceAtMost(patience.backoffMs.size - 1)]
                    if (item != null) {
                        item.retrying = attempt + 1
                        changed(item)
                        pauseFor(item, wait)
                    } else {
                        sleepUnless(stop, wait)
                    }
                }
            }
        }
        throw last ?: IOException(NOT_RECEIVED)
    }

    /** 다시 해 볼 만한가 — 끊김·시간 넘김·5xx·408·429. 그 밖의 4xx(없음·거절)와 너무 큰 몸, 사이트가 답한 까닭은 다시 해도 같다. */
    private fun retryable(e: IOException): Boolean = when (e) {
        is HttpStatus -> e.code == 408 || e.code == 429 || e.code >= 500
        is SiteHttpException -> e.code == 408 || e.code == 429 || e.code >= 500
        is SiteException -> false
        is Streams.TooLarge -> false
        else -> true
    }

    /** 기다리되 [stop] 이면 곧바로 그만둔다(살피기를 그만둔 사용자를 붙잡지 않는다). */
    private fun sleepUnless(stop: () -> Boolean, ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (stop()) throw Cancelled()
            Thread.sleep(50)
        }
    }

    /** 기다리되 취소·정지는 바로 알아챈다. */
    private fun pauseFor(item: Item, ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (item.cancelled || item.pauseRequested) throw Cancelled()
            Thread.sleep(100)
        }
    }

    // ── 쪽이 넘겨주는 것 ───────────────────────────────────────────────────────────────────────────────────

    /** 쪽이 넘겨주는 바이트를 '내려받기' 폴더의 파일 하나로 쓴다. 쓰기는 한 일꾼이 차례로 한다. */
    inner class Pushed internal constructor(val item: Item) {
        private var out: OutputStream? = null
        private var uri: Uri? = null

        val cancelled: Boolean get() = item.cancelled

        /** [length] 바이트를 쓰고, 다 쓰면 [then](메인 스레드 — 다음 덩이를 청한다). */
        fun write(data: ByteArray, offset: Int, length: Int, then: () -> Unit) = pushExecutor.execute {
            if (item.status != Status.RUNNING) return@execute
            if (item.cancelled) {
                abort(Status.CANCELLED, null)
                return@execute
            }
            try {
                val o = out ?: run {
                    val u = MediaStoreWriter.create(app, item.fileName, item.mimeType ?: "application/octet-stream")
                    uri = u
                    MediaStoreWriter.openAt(app, u, 0).also { out = it }
                }
                o.write(data, offset, length)
                item.received += length
                changed(item)
                main.post(then)
            } catch (e: IOException) {
                abort(Status.FAILED, SAVE_FAILED)
            } catch (e: SecurityException) {
                abort(Status.FAILED, SAVE_FAILED)
            }
        }

        fun finish() = pushExecutor.execute {
            if (item.status != Status.RUNNING) return@execute
            try {
                val u = uri ?: MediaStoreWriter.create(app, item.fileName, item.mimeType ?: "application/octet-stream").also { uri = it }
                out?.close()
                out = null
                item.savedMime = item.mimeType
                publish(item, u)
                finish(item, Status.DONE, null)
            } catch (e: IOException) {
                abort(Status.FAILED, SAVE_FAILED)
            }
        }

        fun fail(message: String) = pushExecutor.execute {
            if (item.status == Status.RUNNING) abort(if (item.cancelled) Status.CANCELLED else Status.FAILED, message)
        }

        private fun abort(status: Status, message: String?) {
            runCatching { out?.close() }
            out = null
            uri?.let { MediaStoreWriter.delete(app, it) }
            uri = null
            finish(item, status, message)
        }
    }

    // ── 정리·저장 ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun workDir(item: Item): File = File(workRoot, item.id.toString()).apply { mkdirs() }

    /** 받던 것을 지운다 — 감춘 항목과 임시 폴더. */
    private fun cleanup(item: Item) {
        item.pendingUri?.let { MediaStoreWriter.delete(app, it) }
        item.pendingUri = null
        File(workRoot, item.id.toString()).deleteRecursively()
        item.tracks.forEach {
            it.partsDone = 0
            it.bytes = 0
            it.partSizes.clear()
        }
    }

    private fun finish(item: Item, status: Status, error: String?) {
        item.status = status
        item.error = error
        item.retrying = 0
        if (status != Status.PAUSED) item.remux = null
        if (item.kind == Kind.SET && status != Status.PAUSED) forgetPlan(item)
        debug("download $status ${item.savedName} bytes=${item.received} parts=${item.partsDone}/${item.parts} error=${error ?: "-"}")
        changed(item)
        saveSoon()
    }

    /**
     * 갤러리 받기가 끝났다(다 받음·그만둠·실패) — 다시 받지 않으므로 계획을 버린다(메모리와 곁의 파일). 기록은 남긴다 — '받은 파일까지 지우기' 가
     * 쓴다. 뒷정리 일꾼에서(계획을 적는 일이 같은 줄에 먼저 섰다 — 늦게 와서 되살리지 않는다).
     */
    private fun forgetPlan(item: Item) {
        item.plan = null
        val id = item.id
        chores.execute { sets.dropPlan(id) }
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) android.util.Log.i("WebUI", message)
    }

    private fun changed(item: Item?) {
        main.post { listeners.forEach { it.onChanged(item) } }
    }

    /** 목록이 너무 길면 오래된 끝난 것부터 뺀다(갤러리는 곁의 계획·기록도 — 받은 파일은 그대로). */
    private fun trim() {
        while (items.size > MAX_ITEMS) {
            val old = items.lastOrNull { !it.isActive && it.status != Status.PAUSED } ?: break
            items.remove(old)
            if (old.kind == Kind.SET) chores.execute { sets.forget(old.id) }
        }
    }

    /** 곧 적는다(잦은 진행은 한 번으로 모은다). */
    private fun saveSoon() {
        if (!savePending.compareAndSet(false, true)) return
        storeExecutor.execute {
            Thread.sleep(SAVE_DELAY_MS)
            savePending.set(false)
            store.save(snapshot())
        }
    }

    private fun snapshot(): JSONArray = JSONArray().apply {
        for (it in items) {
            // 넘겨받는 것과 마무리는 다시 할 수 없다 — 끝난 것만 남긴다.
            if ((it.kind == Kind.PUSHED || it.kind == Kind.REMUX) && it.isActive) continue
            put(toJson(it))
        }
    }

    private fun toJson(i: Item): JSONObject = JSONObject()
        .put("id", i.id).put("kind", i.kind.name).put("url", i.url).put("fileName", i.fileName).put("mimeType", i.mimeType ?: "")
        .put("userAgent", i.userAgent).put("referer", i.referer ?: "").put("status", i.status.name).put("received", i.received)
        .put("total", i.total).put("error", i.error ?: "").put("note", i.note ?: "").put("savedUri", i.savedUri?.toString() ?: "")
        .put("savedName", i.savedName).put("savedMime", i.savedMime ?: "").put("pendingUri", i.pendingUri?.toString() ?: "")
        .put("etag", i.etag ?: "").put("lastModified", i.lastModified ?: "")
        .put("pageUrl", i.pageUrl ?: "").put("pageTitle", i.pageTitle ?: "").put("expired", i.expired)
        .put("tracks", JSONArray().apply {
            for (t in i.tracks) {
                put(JSONObject()
                    .put("playlistUrl", t.playlistUrl ?: "").put("mimeType", t.mimeType ?: "").put("audio", t.audio)
                    .put("parts", t.parts).put("partsDone", t.partsDone).put("bytes", t.bytes).put("extension", t.extension ?: "")
                    .put("container", t.container?.name ?: "").put("offsetUs", t.offsetUs)
                    .put("partSizes", JSONArray().apply { synchronized(t.partSizes) { t.partSizes.forEach { put(it) } } })
                    .put("plan", t.plan?.let { plan ->
                        JSONArray().apply { plan.forEach { p -> put(JSONArray().put(p.url).put(p.range?.offset ?: -1).put(p.range?.length ?: -1)) } }
                    } ?: JSONArray()))
            }
        })
        .also { o -> i.gallery?.let { o.put("set", toJson(it)) } }

    /**
     * 갤러리 받기의 목록에 보일 것과 이어받을 자리 — 계획 전체는 곁의 파일에([DownloadSets]). 받는 일꾼이 쓰는 동안 읽는다 — **단위를 파일 차례보다
     * 먼저 읽는다**(받는 쪽은 파일 차례를 먼저 쓴다 — [runSet]). 그래서 적힌 자리는 실제보다 앞서지 않는다.
     */
    private fun toJson(g: Gallery): JSONObject = JSONObject()
        .put("site", g.site.name).put("key", g.key).put("title", g.title).put("creator", g.creator ?: "")
        .put("folder", JSONArray(g.folder)).put("units", g.units).put("fileCount", g.fileCount ?: -1)
        .put("unitsDone", g.unitsDone).put("fileIndex", g.fileIndex).put("filesSaved", g.filesSaved)
        .put("skipped", g.skipped).put("skippedLogin", g.skippedLogin)

    /** [toJson] 의 거꾸로 — 모르는 사이트면 [IllegalArgumentException](그 항목을 버린다 — 뒤 판이 남긴 것). */
    private fun galleryFromJson(s: JSONObject): Gallery {
        val folderArray = s.optJSONArray("folder") ?: JSONArray()
        val folder = (0 until folderArray.length()).map { folderArray.getString(it) }
        return Gallery(
            Site.valueOf(s.getString("site")), s.getString("key"), s.optString("title"), s.optString("creator").ifEmpty { null }, folder,
            s.optInt("units"), s.optInt("fileCount", -1).takeIf { it >= 0 },
        ).apply {
            unitsDone = s.optInt("unitsDone")
            fileIndex = s.optInt("fileIndex")
            filesSaved = s.optInt("filesSaved")
            skipped = s.optInt("skipped")
            skippedLogin = s.optInt("skippedLogin")
        }
    }

    /** 앞서 적어 둔 목록 — 받던 중에 앱이 죽은 것은 멈춤(보통 파일·미디어·갤러리 — 이어받는다)이나 실패(넘겨받는 것)로. */
    private fun restore() {
        val loaded = store.loadOrNull()
        val readable = loaded != null
        val array = loaded ?: JSONArray()
        val keep = HashSet<String>()
        for (k in 0 until array.length()) {
            val item = try {
                fromJson(array.getJSONObject(k))
            } catch (e: JSONException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } ?: continue
            if (item.status == Status.RUNNING || item.status == Status.QUEUED) {
                if (item.pausable) {
                    item.status = Status.PAUSED
                    item.error = app.getString(R.string.download_interrupted)
                } else {
                    item.status = Status.FAILED
                    item.error = app.getString(R.string.download_interrupted)
                }
            }
            if (item.status == Status.PAUSED) keep += item.id.toString()
            items.add(item)
            if (item.id > ids.get()) ids.set(item.id)
        }
        // 목록에 없는 임시 폴더(앞서 지운 것의 찌꺼기)와 갤러리 곁의 파일(읽지 못해 버린 항목·지우다 끊긴 것)을 치운다. 곁의 파일은 목록을 제대로
        // 읽었을 때만(깨졌으면 아무것도 지우지 않는다 — 고아 정리와 같은 판단), 그리고 지금까지의 번호만(뒤 번호는 이 프로세스가 곧 넣는다).
        val setIds = items.filter { it.kind == Kind.SET }.map { it.id }.toSet()
        val restoredUpTo = ids.get()
        storeExecutor.execute {
            workRoot.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
            if (readable) sets.sweep(setIds, restoredUpTo)
        }
    }

    private fun fromJson(o: JSONObject): Item {
        val tracks = ArrayList<Track>()
        val ta = o.optJSONArray("tracks") ?: JSONArray()
        for (k in 0 until ta.length()) {
            val t = ta.getJSONObject(k)
            val planArray = t.optJSONArray("plan")
            val plan = if (planArray != null && planArray.length() > 0) {
                (0 until planArray.length()).map { n ->
                    val p = planArray.getJSONArray(n)
                    val offset = p.getLong(1)
                    val length = p.getLong(2)
                    Part(p.getString(0), if (offset >= 0 && length >= 0) HlsPlaylist.ByteRange(length, offset) else null, null, 0)
                }
            } else {
                null
            }
            tracks += Track(t.optString("playlistUrl").ifEmpty { null }, plan, t.optString("mimeType").ifEmpty { null }, t.optBoolean("audio")).apply {
                parts = t.optInt("parts")
                partsDone = t.optInt("partsDone")
                bytes = t.optLong("bytes")
                extension = t.optString("extension").ifEmpty { null }
                container = t.optString("container").ifEmpty { null }?.let { runCatching { Remux.Container.valueOf(it) }.getOrNull() }
                offsetUs = t.optLong("offsetUs")
                t.optJSONArray("partSizes")?.let { a -> for (n in 0 until a.length()) partSizes += a.getLong(n) }
            }
        }
        // 페이지 주소·제목·만료는 0.9.x 뒤에 더했다 — 앞 판의 파일에는 없다(없으면 null·false). 갤러리(set)는 2026-10-11 에 더했다.
        val kind = Kind.valueOf(o.getString("kind"))
        val gallery = if (kind == Kind.SET) galleryFromJson(o.getJSONObject("set")) else null
        return Item(
            o.getLong("id"), kind, o.getString("url"), o.getString("fileName"),
            o.optString("mimeType").ifEmpty { null }, o.optString("userAgent"), o.optString("referer").ifEmpty { null }, tracks,
            o.optString("pageUrl").ifEmpty { null }, o.optString("pageTitle").ifEmpty { null }, gallery,
        ).apply {
            expired = o.optBoolean("expired")
            status = Status.valueOf(o.getString("status"))
            received = o.optLong("received")
            total = o.optLong("total", -1)
            error = o.optString("error").ifEmpty { null }
            note = o.optString("note").ifEmpty { null }
            savedUri = o.optString("savedUri").ifEmpty { null }?.let(Uri::parse)
            savedName = o.optString("savedName").ifEmpty { fileName }
            savedMime = o.optString("savedMime").ifEmpty { null }
            pendingUri = o.optString("pendingUri").ifEmpty { null }?.let(Uri::parse)
            etag = o.optString("etag").ifEmpty { null }
            lastModified = o.optString("lastModified").ifEmpty { null }
        }
    }

    /** 취소·정지 — 받던 일을 멈춘다(어느 쪽인지는 [Item.cancelled]·[Item.pauseRequested] 가 정한다). */
    private class Cancelled : IOException("cancelled")

    /** 다시 해도 안 될 것 — 실시간·DRM·재생목록이 아님 등. 받던 것을 지운다. */
    private class Refused(message: String) : IOException(message)

    /** '내려받기' 폴더에 쓰지 못했다. */
    private class StorageFailure : IOException("storage")

    /** 갤러리 받기를 멈춘다 — 다시 해도 같을 것이거나 망이 이어 실패했다([message] 는 우리 문장). 받은 파일은 남고 이어받는다. */
    private class SetStop(message: String) : IOException(message)

    /** 서버가 2xx 밖으로 답했다. [byProxy] — 목적지가 아니라 프록시(중계·Squid)가 만든 답이다(그것은 주소의 '만료' 가 아니다). */
    private class HttpStatus(val code: Int, message: String, val byProxy: Boolean = false) : IOException(message)

    /**
     * 한 덩이 받기의 끈기 — 받기는 세 번·넉넉한 시간 제한, 살피기(화질을 고르기 전)는 두 번·짧은 시간 제한(그동안 사용자가 화면 앞에서
     * 기다린다 — 앞 판은 받기와 같아서 느린 서버면 1분 반 남짓 아무 표시 없이 기다렸다).
     */
    private class Patience(val attempts: Int, val backoffMs: LongArray, val connectMs: Int, val readMs: Int)

    /** `Content-Range: bytes 100-199/1000`. */
    private class ContentRange(val start: Long, val total: Long?) {
        companion object {
            fun parse(value: String?): ContentRange? {
                val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").find(value ?: return null) ?: return null
                return ContentRange(m.groupValues[1].toLong(), m.groupValues[3].toLongOrNull())
            }
        }
    }

    companion object {
        private const val RETRIES = 3

        /** 다시 해 보기 전에 기다리는 시간(첫째·둘째). */
        private val BACKOFF_MS = longArrayOf(1_500L, 4_000L, 8_000L)

        /** 받기의 끈기 — 세 번, 연결 20초·읽기 30초(셀룰러로 해외 프록시를 지난다). */
        private val DOWNLOAD = Patience(RETRIES, BACKOFF_MS, 20_000, 30_000)

        /** 살피기의 끈기 — 두 번, 연결 8초·읽기 10초(가장 길어도 20초 남짓). */
        private val INSPECT = Patience(2, longArrayOf(1_000L), 8_000, 10_000)
        private const val NOTICE_NS = 400_000_000L
        private const val SAVE_DELAY_MS = 1_500L
        private const val MAX_ITEMS = 200
        private const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024

        /** 열쇠의 상한 — 열쇠는 16 바이트다. 로그인 페이지 따위가 와도 이만큼만 읽는다. */
        private const val MAX_KEY_BYTES = 64 * 1024

        /** 묶음 AAC 의 앞머리에서 ID3 시각을 찾을 만큼. */
        private const val ID3_HEAD_BYTES = 64 * 1024

        /** 주소가 만료됐거나 막혔다고 보는 답 — 서명된 CDN 주소가 끝나면 이렇게 답한다(같은 주소로 다시 해도 같다). */
        private val EXPIRED_CODES = setOf(401, 403, 404, 410)
        private const val DECRYPT_FAILED = "조각을 풀지 못했습니다"
        private const val NOT_RECEIVED = "받지 못했습니다"

        /**
         * 내려받기 목록의 '원래 페이지 열기' 가 브라우저 화면에 넘기는 주소. **프로세스 안에서만** 오간다 — 브라우저 화면은 런처로 열리는(내보낸)
         * 화면이라 인텐트의 덧붙임으로 주소를 받으면 다른 앱이 아무 주소나 열게 할 수 있다(북마크 화면이 결과로만 돌려주는 까닭과 같다).
         */
        private val pageToOpen = AtomicReference<String?>()

        /** 원래 페이지를 열어 달라고 맡긴다 — 브라우저 화면이 다음에 보일 때 [takePageToOpen] 으로 가져가 새 탭에서 연다. */
        fun openPageLater(url: String) {
            pageToOpen.set(url)
        }

        /** 맡겨 둔 페이지(없으면 null) — 가져가면 비운다. 브라우저 화면이 보일 때(onResume) 부른다. */
        fun takePageToOpen(): String? = pageToOpen.getAndSet(null)

        /**
         * 지난 프로세스가 남긴 **감춘 '내려받기' 항목**(IS_PENDING)을 지운다 — 목록(downloads.json)에 기록이 없는 것만. 마무리(가로채기·녹화)와
         * blob 받기는 다시 할 수 없어 받는 동안 목록에 적지 않으므로, 그 도중에 앱이 죽으면 항목이 남아 사용자에게 보이지도 않고 이어받을 수도
         * 없었다(시스템은 이레 뒤에야 지운다). 이 프로세스가 뜬 뒤에 생긴 것은 건드리지 않는다(지금 받는 것). 목록을 읽지 못하면(깨졌다) 아무것도
         * 지우지 않는다. 디스크·MediaStore 를 보므로 일꾼에서 부른다.
         */
        fun sweepOrphans(context: Context) {
            val app = context.applicationContext
            val known = DownloadStore(app).loadOrNull()?.let(::pendingIds) ?: return
            val startedAt = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
            val orphans = orphansOf(MediaStoreWriter.pending(app), known, startedAt)
            orphans.forEach { MediaStoreWriter.delete(app, MediaStoreWriter.uriOf(it.id)) }
            if (BuildConfig.DEBUG && orphans.isNotEmpty()) android.util.Log.i("WebUI", "download orphans removed ${orphans.size}")
        }

        /**
         * 지울 감춘 항목 — 목록에 기록([known])이 없고 이 프로세스가 뜨기([startedAtMs]) 전에 생긴 것. MediaStore 의 만든 때는 초 단위로 내려
         * 적히므로 1초를 비켜 센다(이 프로세스가 막 만든 것을 지우지 않게 — 그 경계의 앞 프로세스 것은 남아도 시스템이 이레 뒤에 지운다).
         */
        internal fun orphansOf(
            entries: List<MediaStoreWriter.PendingEntry>,
            known: Set<Long>,
            startedAtMs: Long,
        ): List<MediaStoreWriter.PendingEntry> = entries.filter { it.id !in known && it.addedSeconds * 1000 < startedAtMs - 1000 }

        /** 목록이 쥐고 있는 감춘 항목(이어받을 자리)의 번호. */
        private fun pendingIds(array: JSONArray): Set<Long> = buildSet {
            for (k in 0 until array.length()) {
                val uri = array.optJSONObject(k)?.optString("pendingUri").orEmpty()
                if (uri.isEmpty()) continue
                runCatching { ContentUris.parseId(Uri.parse(uri)) }.getOrNull()?.takeIf { it >= 0 }?.let { add(it) }
            }
        }
        const val NOT_A_PLAYLIST = "재생목록이 아닙니다"
        const val NOT_A_MANIFEST = "DASH 목록이 아닙니다"
        const val HLS_LIVE = "실시간 방송은 받지 않습니다"
        const val HLS_DRM = "DRM 이 걸린 영상은 받지 않습니다"
        const val SAVE_FAILED = "저장하지 못했습니다"
        const val PAUSED_BY_USER = "멈췄습니다"
    }
}
