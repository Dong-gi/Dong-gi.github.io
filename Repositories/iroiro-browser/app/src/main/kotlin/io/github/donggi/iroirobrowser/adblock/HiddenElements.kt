package io.github.donggi.iroirobrowser.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import io.github.donggi.iroirobrowser.core.adblock.HideRule
import io.github.donggi.iroirobrowser.core.adblock.UserHides
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * 사용자가 숨긴 요소(페이지의 메뉴 '요소 숨기기' — 사용자 요청 2026-10-08)의 저장. 규칙은 광고 목록의 요소 숨김과 같은 꼴(`사이트##선택자`,
 * [UserHides])이고 범위는 그 사이트(등록 도메인 어림)다.
 *
 * - **광고 목록과 따로 둔다** — 목록 파일(`files/adblock/`)은 갱신·빼기가 다시 받고 지우지만 이 파일(`files/hidden-elements.txt`)은 사용자만
 *   고친다. 백업·기기 이전에서는 다른 앱 파일처럼 빠진다(`data_extraction_rules`).
 * - **늘 건다** — 광고 차단의 켜기·사이트별 끄기·목록의 예외(`$elemhide`·`#@#`)와 상관없다(사용자가 직접 고른 것이라). 거는 길은 광고 차단의
 *   요소 숨김과 같다(브라우저 화면이 페이지를 시작할 때 그 사이트의 CSS 를 함께 넣는다 — `BrowserActivity.adStart`).
 *
 * 메인 스레드에서 읽고 고친다. 처음 쓸 때 파일을 읽고(작다 — 규칙 하나가 수십 바이트, 첫 페이지를 열기 전에 규칙이 있어야 한다), 고칠
 * 때마다 일꾼 스레드가 통째로 다시 쓴다(AtomicFile — 쓰다 죽어도 앞의 것이 남는다).
 */
class HiddenElements(context: Context) {

    fun interface Listener {
        fun onChanged()
    }

    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE))
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-hidden").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** 지금의 규칙(바뀌지 않는 것 — 고치면 통째로 바꿔 끼운다). */
    @Volatile
    var rules: UserHides = load()
        private set

    fun addListener(l: Listener) {
        listeners += l
    }

    fun removeListener(l: Listener) {
        listeners -= l
    }

    /** [host] 의 페이지에 넣을 CSS — 그 사이트의 규칙. 없으면 빈 글. */
    fun cssFor(host: String): String = rules.cssFor(host)

    /** 규칙의 범위(등록 도메인) — 알림·메뉴에 쓴다. */
    fun siteOf(host: String): String = UserHides.siteOf(host)

    /** [host] 의 사이트에 숨긴 요소의 수. */
    fun countFor(host: String): Int = if (host.isEmpty()) 0 else rules.rulesOf(siteOf(host)).size

    fun rulesOf(site: String): List<HideRule> = rules.rulesOf(site)

    fun sites(): List<String> = rules.sites()

    /** [host] 의 사이트에 [selector] 를 숨기는 규칙 — 쓸 수 없는 사이트·선택자면 null. 더하지는 않는다. */
    fun ruleFor(host: String, selector: String): HideRule? = UserHides.rule(host, selector)

    /** 규칙을 더한다 — 이미 있으면 false. */
    fun add(rule: HideRule): Boolean {
        if (rule in rules) return false
        update(rules.plus(rule))
        return true
    }

    fun remove(rule: HideRule) {
        if (rule !in rules) return
        update(rules.minus(rule))
    }

    /** 사이트의 규칙을 모두 지운다 — 지운 것(되돌리기에 쓴다). */
    fun removeSite(site: String): List<HideRule> {
        val gone = rules.rulesOf(site)
        if (gone.isNotEmpty()) update(rules.minusSite(site))
        return gone
    }

    /** 지운 규칙을 되살린다(되돌리기) — 이미 있는 것은 그대로. */
    fun restore(list: List<HideRule>) {
        val missing = list.filter { it !in rules }
        if (missing.isNotEmpty()) update(rules.plus(missing))
    }

    private fun update(next: UserHides) {
        rules = next
        val bytes = next.format().toByteArray(Charsets.UTF_8)
        worker.execute { write(bytes) }
        main.post { listeners.forEach { it.onChanged() } }
    }

    /** 일꾼 스레드에서 — 차례로 쓰므로 마지막에 쓴 것이 마지막에 고친 것이다. */
    private fun write(bytes: ByteArray) {
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            return
        }
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }

    /** 파일이 없으면(처음 — 쓰다 죽었으면 AtomicFile 이 앞의 것을 되살린다) 빈 규칙. */
    private fun load(): UserHides = try {
        UserHides.parse(String(file.readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        UserHides(emptyList())
    }

    private companion object {
        const val FILE = "hidden-elements.txt"
    }
}
