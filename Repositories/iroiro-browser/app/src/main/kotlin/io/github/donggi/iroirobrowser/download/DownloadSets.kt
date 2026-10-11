package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.download.GalleryRules.Position
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 갤러리 받기 항목([DownloadCenter.Kind.SET]) 곁의 파일 — `noBackupFilesDir/download-sets/` 에 항목 번호로 둘:
 *
 * - `<번호>.json` — **계획 전체**(`GalleryCodec` 의 글 — 단위들). 작가 받기는 단위가 수천이라 `downloads.json` 에 싣지 않는다. 다 받았거나 그만두면
 *   버린다(다시 받지 않는다).
 * - `<번호>.saved` — **덧붙이기만 하는 기록**, 한 줄에 한 일: 드러낸 파일 `F 단위 차례 URI`, 건너뛴 것 `S 단위 차례 로그인(0·1)`(차례 -1 은 그
 *   단위를 통째로). 받는 동안 목록(`downloads.json` — 1.5초 모아 적는다)보다 먼저 적히므로 **이어받을 자리와 수의 원본**이다([GalleryRules.resumeAt]).
 *   '받은 파일까지 지우기' 가 그 URI 들을 지운다. 파일이 수만 개여도 기록 전체를 다시 쓰지 않게 덧붙이기다([TorrentRecords] 와 같은 판단). 줄마다
 *   fsync 하지 않는다 — 전원이 끊기면 끝의 몇 줄을 잃을 수 있고, 그때는 그 파일을 한 번 더 받아 ' (1)' 사본이 생길 수 있다. 끝이 잘린 줄은 버린다.
 *
 * 백업·이전에서 빠진다(`noBackupFilesDir`). 어느 스레드에서나 부른다(디스크를 만지므로 메인 스레드는 피한다). 이름은 번호뿐이다.
 */
internal class DownloadSets(private val dir: File) {

    /** 기록을 읽은 것 — 드러낸 파일의 URI(받은 차례), 건너뛴 수(그 가운데 로그인이 필요해서), 마지막 자리. */
    class Journal(val savedUris: List<String>, val skipped: Int, val skippedLogin: Int, val last: Position?) {
        val saved: Int get() = savedUris.size
    }

    // ── 계획 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** 계획을 적는다 — 임시 파일에 다 쓰고 디스크까지 내린 뒤 이름을 바꾼다(반쯤 쓴 계획이 남지 않게). 못 적으면 false. */
    @Synchronized
    fun writePlan(id: Long, text: String): Boolean {
        if (id <= 0) return false
        val target = File(dir, "$id$PLAN")
        val temp = File(dir, "$id$PLAN$TEMP")
        return try {
            dir.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            // 안드로이드(POSIX)의 rename 은 있는 것을 갈아 끼운다. 그렇지 않은 곳(시험의 윈도)은 지우고 다시.
            temp.renameTo(target) || (target.delete() && temp.renameTo(target))
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    /** 적어 둔 계획의 글 — 없거나 읽지 못하면 null. */
    @Synchronized
    fun readPlan(id: Long): String? = try {
        File(dir, "$id$PLAN").takeIf { it.isFile }?.readBytes()?.toString(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }

    /** 계획만 버린다(다 받았거나 그만뒀다 — 다시 받지 않는다). 기록은 남긴다('받은 파일까지 지우기' 가 쓴다). */
    @Synchronized
    fun dropPlan(id: Long) {
        File(dir, "$id$PLAN").delete()
        File(dir, "$id$PLAN$TEMP").delete()
    }

    // ── 기록 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** 파일 하나를 드러냈다. */
    fun appendSaved(id: Long, unit: Int, file: Int, uri: String) {
        // URI 는 우리가 만든 MediaStore 의 것이다 — 그래도 줄의 꼴을 깨뜨리는 글자가 들었으면 적지 않는다.
        if (uri.isEmpty() || uri.any { it == '\t' || it == '\n' || it == '\r' }) return
        append(id, "F\t$unit\t$file\t$uri")
    }

    /** 건너뛰었다 — [file] 이 -1 이면 그 단위(작품)를 통째로. */
    fun appendSkipped(id: Long, unit: Int, file: Int, login: Boolean) = append(id, "S\t$unit\t$file\t${if (login) 1 else 0}")

    @Synchronized
    fun journal(id: Long): Journal {
        val text = try {
            File(dir, "$id$SAVED").takeIf { it.isFile }?.readBytes()?.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            null
        }
        return parse(text.orEmpty())
    }

    @Synchronized
    private fun append(id: Long, line: String) {
        if (id <= 0) return
        try {
            dir.mkdirs()
            FileOutputStream(File(dir, "$id$SAVED"), true).use { it.write("$line\n".toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            // 못 적어도 받기는 이어 간다 — 목록(downloads.json)의 자리와 수가 남는다. 그 파일은 '받은 파일까지 지우기' 에서 빠진다.
        }
    }

    // ── 지우기 ───────────────────────────────────────────────────────────────────────────────────────────

    /** 항목을 목록에서 뺐다 — 계획과 기록을 버린다(받은 파일은 그대로 — 사용자의 파일이다). */
    @Synchronized
    fun forget(id: Long) {
        File(dir, "$id$PLAN").delete()
        File(dir, "$id$PLAN$TEMP").delete()
        File(dir, "$id$SAVED").delete()
    }

    /** 목록에 없는 항목의 찌꺼기를 치운다 — [upTo] 번 이하만(그 뒤 번호는 이 프로세스가 막 넣은 것일 수 있다). */
    @Synchronized
    fun sweep(keep: Set<Long>, upTo: Long) {
        val names = dir.list()?.toList().orEmpty()
        for (name in stale(names, keep, upTo)) File(dir, name).delete()
    }

    companion object {
        private const val PLAN = ".json"
        private const val SAVED = ".saved"
        private const val TEMP = ".tmp"
        private val NAME = Regex("""(\d{1,18})\.(json|json\.tmp|saved)""")

        /** 기록의 글을 읽는다 — 줄바꿈으로 끝난 줄만(쓰다가 죽은 끝 줄은 버린다), 꼴이 다른 줄은 건너뛴다. 마지막 자리는 가장 앞선 자리다. */
        fun parse(text: String): Journal {
            val complete = text.substringBeforeLast('\n', "")
            val uris = ArrayList<String>()
            var skipped = 0
            var login = 0
            var last: Position? = null
            if (complete.isEmpty()) return Journal(uris, 0, 0, null)
            for (line in complete.split('\n')) {
                val parts = line.split('\t')
                val unit = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val file = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it >= -1 } ?: continue
                when (parts[0]) {
                    "F" -> {
                        val uri = parts.getOrNull(3)?.takeIf { it.isNotEmpty() && file >= 0 } ?: continue
                        uris += uri
                    }
                    "S" -> {
                        skipped++
                        if (parts.getOrNull(3) == "1") login++
                    }
                    else -> continue
                }
                val at = Position(unit, file)
                if (last == null || at.after(last)) last = at
            }
            return Journal(uris, skipped, login, last)
        }

        /** 치울 이름 — 우리 꼴의 이름 가운데 번호가 [upTo] 이하이고 [keep] 에 없는 것. 다른 이름은 건드리지 않는다. */
        fun stale(names: List<String>, keep: Set<Long>, upTo: Long): List<String> = names.filter { name ->
            val id = NAME.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull() ?: return@filter false
            id <= upTo && id !in keep
        }
    }
}
