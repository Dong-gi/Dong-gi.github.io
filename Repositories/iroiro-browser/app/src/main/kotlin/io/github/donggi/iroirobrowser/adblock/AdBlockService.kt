package io.github.donggi.iroirobrowser.adblock

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import io.github.donggi.iroirobrowser.AppUserAgent
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.core.adblock.AdBlocker
import io.github.donggi.iroirobrowser.core.adblock.Domains
import io.github.donggi.iroirobrowser.core.adblock.FilterParser
import io.github.donggi.iroirobrowser.core.adblock.ParsedList
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.proxy.NetErrors
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * 광고 차단 — 목록·엔진·사이트별 허용·갱신.
 *
 * - 목록은 **실행 중에 내려받아 이 기기에서 엔진을 짓는다**(저장소에 넣지 않는다). 받기는 앱의 HTTP 로 — 길은 [NetworkPath.httpRoute](중계 —
 *   프록시를 켰으면 프록시를 거친다. 사용자가 더한 목록의 주소가 예외 규칙에 든 사설망이면 WebView 처럼 바로).
 * - **앱을 켤 때 받지 않는다**(켤 때마다 받던 브라우저 탓에 EasyList 의 트래픽이 크게 뛴 적이 있다). 하루 한 번 깨어([AdBlockUpdateJob])
 *   만료(목록의 `! Expires:` — 하루~일주일로 묶는다)가 지난 것만 `If-None-Match`·`If-Modified-Since` 로 묻는다. 처음 켠 목록과 '지금 갱신' 만
 *   바로 받는다.
 * - 엔진([AdBlocker])은 만든 뒤 바뀌지 않는다 — 다시 지으면 통째로 바꿔 끼운다(요청마다 다른 스레드가 읽는다).
 * - 사이트별 허용은 **등록 도메인**(어림) 단위다 — `www.example.com` 에서 끄면 `m.example.com` 에서도 꺼진다.
 *
 * 디스크·네트워크 일은 일꾼 스레드 하나가 차례로 한다. 화면에는 [Listener] 로 알린다(메인 스레드).
 */
class AdBlockService(context: Context, private val network: NetworkPath) {

    /** 목록 하나. 상태는 일꾼 스레드가 바꾸고 화면이 읽는다. */
    class FilterList(val id: String, val title: String, val url: String, val builtin: Boolean) {
        @Volatile var enabled: Boolean = false
        @Volatile var lastSuccess: Long = 0
        @Volatile var lastCheck: Long = 0
        @Volatile var etag: String? = null
        @Volatile var lastModified: String? = null
        @Volatile var expiresMs: Long = DEFAULT_EXPIRES_MS
        @Volatile var ruleCount: Int = 0
        @Volatile var error: String? = null
    }

    fun interface Listener {
        fun onChanged()
    }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("adblock", Context.MODE_PRIVATE)
    private val dir = File(app.filesDir, "adblock")
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-adblock").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** 지금의 엔진. 아직 짓지 않았거나 켠 목록이 없으면 null. */
    @Volatile
    var engine: AdBlocker? = null
        private set

    /** 일꾼이 목록을 받거나 엔진을 짓는 중. */
    @Volatile
    var busy: Boolean = false
        private set

    @Volatile
    var enabled: Boolean = prefs.getBoolean(K_ENABLED, true)
        private set

    var wifiOnly: Boolean = prefs.getBoolean(K_WIFI_ONLY, false)
        private set

    /** 메인 스레드(화면)와 일꾼이 함께 본다 — 고칠 때마다 베끼는 목록. */
    private val listStore = CopyOnWriteArrayList(loadLists())
    val lists: List<FilterList> get() = listStore

    @Volatile
    private var allowed: Set<String> = prefs.getStringSet(K_ALLOWED, emptySet())!!.toSet()

    init {
        if (!prefs.getBoolean(K_DEFAULTS_V2, false)) {
            // loadLists 가 기본으로 켜는 목록을 켰다 — 바로 적어 두어야 다음에 옛 값(꺼짐)으로 돌아가지 않는다.
            saveLists()
            prefs.edit().putBoolean(K_DEFAULTS_V2, true).apply()
        }
        worker.execute {
            rebuild()
            // 켠 목록을 아직 한 번도 받지 않았으면(처음 깔았다) 받는다 — 켤 때마다 받지는 않는다.
            if (lists.any { it.enabled && !file(it).exists() }) update(force = false, onlyMissing = true)
        }
        schedule()
        // 처음 깔았을 때는 프록시를 아직 정하지 않아 첫 받기가 실패한다 — 길이 쓸 만해지면(출구 확인이 됐거나 직접) 못 받은 목록만 다시 받는다.
        network.addListener { s -> if (s.phase == NetworkPath.Phase.OK || s.phase == NetworkPath.Phase.OFF) retryMissing() }
    }

    private var lastRetry = 0L

    /**
     * 켰는데 아직 받지 못한 목록만 받는다 — 1분에 한 번까지. 길의 상태가 바뀔 때마다(스위치·설정·5분마다의 출구 확인) 메인 스레드에서 불리므로
     * 파일이 있는지는 **일꾼이 본다**(앞 판은 메인 스레드에서 목록마다 디스크를 봤다).
     */
    private fun retryMissing() {
        if (busy) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRetry < RETRY_GAP_MS) return
        lastRetry = now
        worker.execute {
            if (lists.any { it.enabled && !file(it).exists() }) update(force = false, onlyMissing = true)
        }
    }

    fun addListener(l: Listener) {
        listeners += l
    }

    fun removeListener(l: Listener) {
        listeners -= l
    }

    // ── 사이트별 허용 ──────────────────────────────────────────────────────────────────────────────────────────

    /** 이 호스트의 사이트에서 광고 차단을 껐는가. */
    fun isAllowed(host: String): Boolean = host.isNotEmpty() && Domains.registrable(host) in allowed

    fun setAllowed(host: String, allow: Boolean) {
        val site = Domains.registrable(host)
        if (site.isEmpty()) return
        allowed = if (allow) allowed + site else allowed - site
        prefs.edit().putStringSet(K_ALLOWED, allowed).apply()
        notifyChanged()
    }

    fun allowedSites(): List<String> = allowed.sorted()

    // ── 켜기·목록 ──────────────────────────────────────────────────────────────────────────────────────────────

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean(K_ENABLED, on).apply()
        notifyChanged()
    }

    fun setWifiOnly(on: Boolean) {
        wifiOnly = on
        prefs.edit().putBoolean(K_WIFI_ONLY, on).apply()
        schedule(replace = true)
        notifyChanged()
    }

    /** 목록을 켜거나 끈다. 켰는데 아직 받지 않았으면 바로 받는다. */
    fun setListEnabled(list: FilterList, on: Boolean) {
        list.enabled = on
        saveLists()
        worker.execute {
            if (on && !file(list).exists()) update(force = true, only = list) else rebuild()
        }
        notifyChanged()
    }

    /** 사용자가 주소로 더한 목록. */
    fun addList(url: String, title: String): FilterList? {
        if (lists.any { it.url == url }) return null
        val list = FilterList("custom-" + Integer.toHexString(url.hashCode()) + "-" + (System.currentTimeMillis() % 100000), title, url, builtin = false)
        list.enabled = true
        listStore += list
        saveLists()
        worker.execute { update(force = true, only = list) }
        notifyChanged()
        return list
    }

    fun removeList(list: FilterList) {
        if (list.builtin) return
        listStore -= list
        saveLists()
        worker.execute {
            file(list).delete()
            rebuild()
        }
        notifyChanged()
    }

    /** '지금 갱신' — 켠 목록을 모두 다시 묻는다(만료와 Wi-Fi 설정을 보지 않는다 — 사용자가 누른 것이다). */
    fun updateNow(done: () -> Unit = {}) = worker.execute {
        update(force = true)
        main.post(done)
    }

    /** 하루 한 번의 갱신([AdBlockUpdateJob]) — 만료가 지난 것만. Wi-Fi 에서만이면 데이터 요금제에서는 건너뛴다. */
    fun updateDue(done: () -> Unit) = worker.execute {
        if (!wifiOnly || isUnmetered()) update(force = false)
        main.post(done)
    }

    // ── 일꾼 ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** 일꾼 스레드에서. 목록을 받고(필요한 것만) 엔진을 다시 짓는다. */
    private fun update(force: Boolean, only: FilterList? = null, onlyMissing: Boolean = false) {
        busy = true
        notifyChanged()
        var changed = false
        val now = System.currentTimeMillis()
        for (list in lists) {
            if (!list.enabled || (only != null && list !== only)) continue
            val f = file(list)
            if (onlyMissing && f.exists()) continue
            if (!force && f.exists() && now - list.lastSuccess < list.expiresMs) continue
            changed = fetch(list) || changed
        }
        saveLists()
        if (changed) rebuild()
        busy = false
        notifyChanged()
    }

    /** 목록 하나를 받는다. 새 글을 받았으면 true. 길이 아직 없으면 연결을 열지 않고 '프록시가 준비되지 않았습니다'(아래 catch — NetErrors). */
    private fun fetch(list: FilterList): Boolean {
        val route = network.httpRoute()
        list.lastCheck = System.currentTimeMillis()
        val f = file(list)
        return try {
            HttpFetcher(route).get(
                list.url,
                headersFor = {
                    buildMap {
                        put("User-Agent", AppUserAgent.of(app))
                        if (f.exists()) {
                            list.etag?.let { put("If-None-Match", it) }
                            list.lastModified?.let { put("If-Modified-Since", it) }
                        }
                    }
                },
            ).use { r ->
                when (r.code) {
                    304 -> {
                        list.lastSuccess = System.currentTimeMillis()
                        list.error = null
                        debugLog("adblock list ${list.id} not modified")
                        false
                    }
                    200 -> {
                        val bytes = readAtMost(r.body, MAX_LIST_BYTES)
                        val text = bytes.decodeToString()
                        if (!looksLikeFilterList(text)) throw IOException(ERR_NOT_A_LIST)
                        dir.mkdirs()
                        val out = AtomicFile(f)
                        val stream = out.startWrite()
                        try {
                            stream.write(bytes)
                            out.finishWrite(stream)
                        } catch (e: IOException) {
                            out.failWrite(stream)
                            throw e
                        }
                        list.etag = r.header("ETag")
                        list.lastModified = r.header("Last-Modified")
                        list.expiresMs = expiresOf(text)
                        list.lastSuccess = System.currentTimeMillis()
                        list.error = null
                        debugLog("adblock list ${list.id} ok ${bytes.size} bytes")
                        true
                    }
                    else -> {
                        list.error = NetErrors.describeStatus(r)
                        debugLog("adblock list ${list.id} failed HTTP ${r.code}")
                        false
                    }
                }
            }
        } catch (e: IOException) {
            list.error = NetErrors.describe(e)
            debugLog("adblock list ${list.id} failed ${e.javaClass.simpleName}")
            false
        }
    }

    /** 일꾼 스레드에서. 켠 목록의 글로 엔진을 다시 짓는다. */
    private fun rebuild() {
        busy = true
        val parsed = ArrayList<ParsedList>()
        for (list in lists) {
            if (!list.enabled) continue
            val f = file(list)
            if (!f.exists()) continue
            val p = try {
                FilterParser.parse(f.readText())
            } catch (e: IOException) {
                list.error = e.message
                continue
            }
            list.ruleCount = p.network.size + p.cosmetic.size
            parsed += p
        }
        val built = if (parsed.isEmpty()) null else AdBlocker(parsed)
        engine = built
        busy = false
        debugLog("adblock ready ${built?.networkRuleCount ?: 0} ${built?.cosmeticRuleCount ?: 0}")
        notifyChanged()
    }

    private fun file(list: FilterList) = File(dir, "${list.id}.txt")

    private fun isUnmetered(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** 하루 한 번의 갱신 일감을 건다 — 이미 걸려 있으면 그대로 둔다(다시 걸면 주기가 처음부터 다시 센다). */
    private fun schedule(replace: Boolean = false) {
        val scheduler = app.getSystemService(JobScheduler::class.java) ?: return
        if (!replace && scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(app, AdBlockUpdateJob::class.java))
            .setPeriodic(DAY_MS)
            .setRequiredNetworkType(if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .build()
        scheduler.schedule(job)
    }

    private fun notifyChanged() {
        main.post { listeners.forEach { it.onChanged() } }
    }

    // ── 설정 파일 ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun loadLists(): List<FilterList> {
        val saved = HashMap<String, JSONObject>()
        val customs = ArrayList<FilterList>()
        try {
            val array = JSONArray(prefs.getString(K_LISTS, "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                saved[o.getString("id")] = o
                if (!o.optBoolean("builtin")) customs += FilterList(o.getString("id"), o.optString("title"), o.getString("url"), builtin = false)
            }
        } catch (e: org.json.JSONException) {
            // 깨졌다 — 기본값으로.
        }
        val all = BUILTIN.map { (id, title, url) -> FilterList(id, title, url, builtin = true) } + customs
        // 2026-10-05 에 기본으로 켜는 목록이 넷이 됐다(사용자 결정). 그 전에 깔린 앱은 EasyList 하나만 켜 두었으므로 한 번 켜 준다.
        val migrate = !prefs.getBoolean(K_DEFAULTS_V2, false)
        for (l in all) {
            val o = saved[l.id]
            l.enabled = if (migrate && l.builtin && l.id in DEFAULT_ON) true else o?.optBoolean("enabled") ?: (l.id in DEFAULT_ON)
            if (o != null) {
                l.lastSuccess = o.optLong("lastSuccess")
                l.lastCheck = o.optLong("lastCheck")
                l.etag = o.optString("etag").ifEmpty { null }
                l.lastModified = o.optString("lastModified").ifEmpty { null }
                l.expiresMs = o.optLong("expiresMs", DEFAULT_EXPIRES_MS)
                l.ruleCount = o.optInt("ruleCount")
                l.error = o.optString("error").ifEmpty { null }
            }
        }
        return all
    }

    @Synchronized
    private fun saveLists() {
        val array = JSONArray()
        for (l in lists.toList()) {
            array.put(
                JSONObject()
                    .put("id", l.id).put("title", l.title).put("url", l.url).put("builtin", l.builtin).put("enabled", l.enabled)
                    .put("lastSuccess", l.lastSuccess).put("lastCheck", l.lastCheck).put("etag", l.etag ?: "")
                    .put("lastModified", l.lastModified ?: "").put("expiresMs", l.expiresMs).put("ruleCount", l.ruleCount)
                    .put("error", l.error ?: ""),
            )
        }
        prefs.edit().putString(K_LISTS, array.toString()).apply()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.i("WebUI", message)
    }

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_WIFI_ONLY = "wifi_only"
        private const val K_LISTS = "lists"
        private const val K_ALLOWED = "allowed_sites"
        private const val K_DEFAULTS_V2 = "defaults_v2"
        private const val JOB_ID = 4201
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val DEFAULT_EXPIRES_MS = 4 * DAY_MS
        private const val MIN_EXPIRES_MS = DAY_MS
        private const val MAX_EXPIRES_MS = 7 * DAY_MS
        private const val MAX_LIST_BYTES = 16 * 1024 * 1024
        private const val RETRY_GAP_MS = 60_000L
        const val ERR_NOT_A_LIST = "광고 목록이 아닙니다"

        /**
         * 들어 있는 목록(주소는 목록이 스스로 밝힌 내려받기 주소). **넷 모두 기본으로 켠다**(사용자 결정, 2026-10-05 — '꾸준히 업데이트되는
         * 목록들이라면 YousList·EasyPrivacy·uBlock filters 도 포함' — 셋의 GitHub 커밋 기록이 2026-09-22~10-04 까지 이어지는 것을 확인했다).
         * 'Wi-Fi 에서만' 은 정하지 않아 꺼 둔다. List-KR·filterslists-KO 는 주소를 확인하지 못해 넣지 않았다(사용자가 주소로 더할 수 있다).
         */
        private val BUILTIN = listOf(
            Triple("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt"),
            Triple("easyprivacy", "EasyPrivacy(추적 막기)", "https://easylist.to/easylist/easyprivacy.txt"),
            Triple("youslist", "YousList(한국어)", "https://raw.githubusercontent.com/yous/YousList/master/youslist.txt"),
            Triple("ubo-filters", "uBlock filters", "https://ublockorigin.github.io/uAssets/filters/filters.txt"),
        )
        private val DEFAULT_ON = setOf("easylist", "easyprivacy", "youslist", "ubo-filters")

        /** 목록의 `! Expires: 4 days` / `12 hours` — 하루~일주일로 묶는다. 없으면 나흘. */
        internal fun expiresOf(text: String): Long {
            val m = Regex("""^!\s*Expires:\s*(\d+)\s*(day|hour)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                .find(text.take(4096)) ?: return DEFAULT_EXPIRES_MS
            val n = m.groupValues[1].toLongOrNull() ?: return DEFAULT_EXPIRES_MS
            val ms = if (m.groupValues[2].lowercase().startsWith("hour")) n * 60 * 60 * 1000 else n * DAY_MS
            return ms.coerceIn(MIN_EXPIRES_MS, MAX_EXPIRES_MS)
        }

        /** 받은 것이 광고 목록처럼 보이는가 — 로그인 쪽·오류 쪽 HTML 을 목록으로 쓰지 않게. */
        internal fun looksLikeFilterList(text: String): Boolean {
            val head = text.take(2048).trimStart()
            if (head.startsWith("<")) return false
            return head.startsWith("[Adblock") || head.startsWith("!") || text.lineSequence().take(200).any {
                it.startsWith("||") || it.contains("##") || it.startsWith("0.0.0.0 ") || it.startsWith("127.0.0.1 ")
            }
        }

        private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > limit) throw IOException("목록이 너무 큽니다")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
