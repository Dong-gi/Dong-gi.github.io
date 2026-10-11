package io.github.donggi.iroirobrowser.download

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 내려받기 목록을 디스크에 둔다(`downloads.json` — `AtomicFile`). 앱이 다시 떠도 목록과 **멈춘 받기**(이어받을 자리 — 받은 바이트·조각 수·
 * 감춘 '내려받기' 항목·`ETag`)가 남는다. 백업·이전에서 빠진다(`data_extraction_rules` 의 files).
 */
internal class DownloadStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "downloads.json"))

    fun load(): JSONArray = loadOrNull() ?: JSONArray()

    /**
     * [load] 와 같되 파일을 읽지 못했거나 깨졌으면 null(없으면 빈 목록) — 기록 없는 감춘 항목을 지우는 쪽이 깨진 목록을 '기록 없음' 으로 읽어
     * 이어받을 것까지 지우지 않게.
     */
    fun loadOrNull(): JSONArray? = try {
        JSONArray(file.readFully().decodeToString())
    } catch (e: FileNotFoundException) {
        JSONArray()
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    fun save(items: JSONArray) {
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            return
        }
        try {
            out.write(items.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }
}
