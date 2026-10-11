package io.github.donggi.iroirobrowser.browser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 열린 탭의 주소·제목과 고른 탭 — 앱이 다시 뜰 때 되살린다(세션 되살리기). 앱의 사적 저장소의 파일 하나(`session.json`, 백업·이전에서
 * 빠진다). **뒤로·앞으로 기록은 두지 않는다** — WebView 의 상태 묶음(`saveState`)은 `Parcel` 이라 디스크에 두는 꼴이 아니다(플랫폼 판이
 * 바뀌면 못 읽는다). 앱이 사는 동안 접어 둔 탭의 기록은 메모리에만 있다([Tab.frozen]).
 */
class SessionStore(context: Context) {

    /** 탭 하나 — [desktop] 은 '데스크톱 사이트' 를 켠 탭(없으면 꺼짐 — 앞 판의 파일도 그대로 읽는다). */
    class Saved(val url: String, val title: String, val desktop: Boolean = false)

    class Session(val tabs: List<Saved>, val selected: Int)

    private val file = AtomicFile(File(context.filesDir, "session.json"))

    fun load(): Session? = try {
        val json = JSONObject(file.readFully().decodeToString())
        val array = json.getJSONArray("tabs")
        val tabs = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Saved(o.optString("url"), o.optString("title"), o.optBoolean("desktop"))
        }
        Session(tabs, json.optInt("selected"))
    } catch (e: IOException) {
        null // 아직 없다
    } catch (e: JSONException) {
        null // 깨졌다 — 새로 시작한다
    }

    fun save(session: Session) {
        val array = JSONArray()
        session.tabs.forEach {
            val o = JSONObject().put("url", it.url).put("title", it.title)
            if (it.desktop) o.put("desktop", true)
            array.put(o)
        }
        val bytes = JSONObject().put("tabs", array).put("selected", session.selected).toString().toByteArray()
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
}
