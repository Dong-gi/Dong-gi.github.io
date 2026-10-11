package io.github.donggi.iroirobrowser.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import io.github.donggi.iroirobrowser.core.Suggester
import java.util.concurrent.Executors

/**
 * 북마크와 방문 기록 — 플랫폼 SQLite(`browser.db`, 새 의존 없이). 앱의 사적 저장소에 있고 백업·이전에서 빠진다(`data_extraction_rules`).
 *
 * 방문 기록은 **주소마다 한 줄**(마지막으로 간 때·간 횟수·주소창에서 친 횟수·제목)이고 최근 [MAX_HISTORY] 곳까지 둔다. 디스크 일은 일꾼
 * 스레드 하나가 차례로 하고 읽은 것은 메인 스레드로 돌려준다. 북마크인 주소는 메모리에도 두어 메뉴가 바로 묻는다([isBookmarked]).
 * 주소창의 제안([suggester])은 기록 전체를 메모리에 한 벌 지어 두고 쓰기가 있으면 다시 짓는다.
 */
class Library(context: Context) {

    class Entry(val url: String, val title: String, val time: Long, val visits: Int)

    private val helper = Helper(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-library").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var bookmarked: Set<String> = emptySet()
    private var visitsSinceTrim = 0

    /** 주소창의 제안을 지은 것 — 쓰기가 있으면 버린다(일꾼 스레드만 만진다). */
    private var suggestCache: Suggester? = null

    init {
        worker.execute { reloadBookmarkSet() }
    }

    // ── 방문 기록 ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 주 문서를 다 열었다(http·https 만 — 부르는 쪽이 가린다). [typed] 는 주소창에서 쳐서(또는 제안을 골라) 간 것 — 인라인 완성은 그런 곳을
     * 먼저 채운다([Suggester]).
     */
    fun visit(url: String, title: String, typed: Boolean = false) = worker.execute {
        suggestCache = null
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        val typedAdd = if (typed) 1L else 0L
        db.beginTransaction()
        try {
            val updated = db.compileStatement(
                "UPDATE history SET last_visit = ?, visits = visits + 1, typed = typed + ?, title = CASE WHEN ? = '' THEN title ELSE ? END WHERE url = ?",
            ).use { st ->
                st.bindLong(1, now)
                st.bindLong(2, typedAdd)
                st.bindString(3, title)
                st.bindString(4, title)
                st.bindString(5, url)
                st.executeUpdateDelete()
            }
            if (updated == 0) {
                db.insert("history", null, ContentValues().apply {
                    put("url", url)
                    put("title", title)
                    put("last_visit", now)
                    put("visits", 1)
                    put("typed", typedAdd)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (++visitsSinceTrim >= TRIM_EVERY) {
            visitsSinceTrim = 0
            db.execSQL("DELETE FROM history WHERE url IN (SELECT url FROM history ORDER BY last_visit DESC LIMIT -1 OFFSET $MAX_HISTORY)")
        }
    }

    /** 쪽의 제목이 왔다 — 그 주소의 줄이 있으면 제목만 바꾼다(없으면 [visit] 이 제목과 함께 만든다). */
    fun retitle(url: String, title: String) {
        if (title.isBlank()) return
        worker.execute {
            suggestCache = null
            helper.writableDatabase.update("history", ContentValues().apply { put("title", title) }, "url = ?", arrayOf(url))
        }
    }

    /**
     * 주소창의 제안 — 방문 기록 전체와 북마크를 주소로 합쳐 지은 것(많아야 [MAX_HISTORY] 줄 + 북마크). 지어 둔 것이 있으면 그것을, 쓰기가
     * 있었으면 다시 지어 메인 스레드로 준다.
     */
    fun suggester(done: (Suggester) -> Unit) = read(done) {
        suggestCache ?: buildSuggester().also { suggestCache = it }
    }

    private fun buildSuggester(): Suggester {
        val byUrl = LinkedHashMap<String, Suggester.Entry>()
        helper.readableDatabase.rawQuery("SELECT url, title, last_visit, visits, typed FROM history", null).use { c ->
            while (c.moveToNext()) {
                val url = c.getString(0)
                byUrl[url] = Suggester.Entry(url, c.getString(1), c.getInt(3), c.getInt(4), c.getLong(2), url in bookmarked)
            }
        }
        helper.readableDatabase.rawQuery("SELECT url, title, created FROM bookmarks", null).use { c ->
            while (c.moveToNext()) {
                val url = c.getString(0)
                val seen = byUrl[url]
                // 북마크의 이름은 사용자가 지은 것이라 기록의 제목보다 앞선다.
                byUrl[url] = if (seen != null) {
                    Suggester.Entry(url, c.getString(1).ifBlank { seen.title }, seen.visits, seen.typed, seen.lastVisit, true)
                } else {
                    Suggester.Entry(url, c.getString(1), 0, 0, c.getLong(2), true)
                }
            }
        }
        return Suggester(byUrl.values)
    }

    /** 최근에 간 곳부터. [query] 가 있으면 제목·주소에 그 글이 든 것만. */
    fun history(query: String, limit: Int = 500, done: (List<Entry>) -> Unit) = read(done) {
        select("SELECT url, title, last_visit, visits FROM history", query, "ORDER BY last_visit DESC LIMIT $limit")
    }

    /** 자주 간 곳 — 간 횟수, 그다음 최근. */
    fun frequent(limit: Int, done: (List<Entry>) -> Unit) = read(done) {
        select("SELECT url, title, last_visit, visits FROM history", "", "ORDER BY visits DESC, last_visit DESC LIMIT $limit")
    }

    fun deleteHistory(url: String, done: () -> Unit = {}) = write(done) {
        suggestCache = null
        helper.writableDatabase.delete("history", "url = ?", arrayOf(url))
    }

    fun clearHistory(done: () -> Unit = {}) = write(done) {
        suggestCache = null
        helper.writableDatabase.delete("history", null, null)
    }

    // ── 북마크 ────────────────────────────────────────────────────────────────────────────────────────────────

    fun isBookmarked(url: String): Boolean = url in bookmarked

    /** 최근에 넣은 것부터. */
    fun bookmarks(query: String, done: (List<Entry>) -> Unit) = read(done) {
        select("SELECT url, title, created, 0 FROM bookmarks", query, "ORDER BY created DESC")
    }

    fun addBookmark(url: String, title: String, done: () -> Unit = {}) = write(done) {
        helper.writableDatabase.insertWithOnConflict("bookmarks", null, ContentValues().apply {
            put("url", url)
            put("title", title.ifBlank { url })
            put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        reloadBookmarkSet()
    }

    fun removeBookmark(url: String, done: () -> Unit = {}) = write(done) {
        helper.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
        reloadBookmarkSet()
    }

    fun renameBookmark(url: String, title: String, done: () -> Unit = {}) = write(done) {
        helper.writableDatabase.update("bookmarks", ContentValues().apply { put("title", title.ifBlank { url }) }, "url = ?", arrayOf(url))
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────────────────────────────────────────

    private fun reloadBookmarkSet() {
        suggestCache = null
        val urls = HashSet<String>()
        helper.readableDatabase.rawQuery("SELECT url FROM bookmarks", null).use { c ->
            while (c.moveToNext()) urls += c.getString(0)
        }
        bookmarked = urls
    }

    /** [query] 를 제목·주소의 부분 글로 찾는다 — `%`·`_` 는 글자 그대로. */
    private fun select(head: String, query: String, tail: String): List<Entry> {
        val q = query.trim()
        val (where, args) = if (q.isEmpty()) {
            "" to emptyArray()
        } else {
            val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            "WHERE title LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\'" to arrayOf(like, like)
        }
        val out = ArrayList<Entry>()
        helper.readableDatabase.rawQuery("$head $where $tail", args).use { c ->
            while (c.moveToNext()) out += Entry(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3))
        }
        return out
    }

    private fun <T> read(done: (T) -> Unit, block: () -> T) = worker.execute {
        val result = block()
        main.post { done(result) }
    }

    private fun write(done: () -> Unit, block: () -> Unit) = worker.execute {
        block()
        main.post(done)
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "browser.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE bookmarks (url TEXT PRIMARY KEY, title TEXT NOT NULL, created INTEGER NOT NULL)")
            db.execSQL(
                "CREATE TABLE history (url TEXT PRIMARY KEY, title TEXT NOT NULL, last_visit INTEGER NOT NULL, visits INTEGER NOT NULL, " +
                    "typed INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL("CREATE INDEX history_last_visit ON history(last_visit)")
        }

        /** 2: 주소창에서 친 횟수(`typed`) — 앞서 적힌 줄은 0 에서 시작한다(인라인 완성은 두 번 넘게 간 곳도 채운다). */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE history ADD COLUMN typed INTEGER NOT NULL DEFAULT 0")
        }
    }

    private companion object {
        const val MAX_HISTORY = 10_000
        const val TRIM_EVERY = 100
    }
}
