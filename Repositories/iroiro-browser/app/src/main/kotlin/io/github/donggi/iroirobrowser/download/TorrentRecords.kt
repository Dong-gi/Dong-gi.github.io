package io.github.donggi.iroirobrowser.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 앱이 토렌트마다 적어 두는 것 — 엔진 밖의 일. '내려받기' 로 옮긴 파일(MediaStore 번호 — 토렌트를 지울 때 함께 지울 수 있게, 그리고 엔진이
 * '옮겨 감' 을 적기 전에 앱이 죽었으면 다시 옮기지 않게), 옮기던 감춘 항목(앱이 죽으면 지운다), 옮기지 못한 까닭, 마그넷이 파일 목록을 기다리는지·
 * 사용자의 고르기를 기다리는지.
 *
 * 토렌트마다 **덧붙이기만 하는 기록**(`<info-hash>.log` — 한 줄에 한 일)이다. 파일이 10만 개인 토렌트에서 파일을 옮길 때마다 기록 전체를 다시
 * 쓰면 일이 파일 수의 제곱으로 는다. 앱이 죽어도 쓴 줄은 남는다(커널이 쥐고 있다) — 전원이 끊기면 끝의 몇 줄을 잃을 수 있고, 그때는 그 파일을
 * 한 번 더 옮겨 ' (1)' 이 붙은 사본이 생길 수 있다(줄마다 fsync 하지 않았다). 끝이 잘린 마지막 줄은 버린다. 백업·이전에서 빠진다
 * (`noBackupFilesDir`). 어느 스레드에서나 부른다(디스크를 만지므로 메인 스레드는 피한다).
 */
internal class TorrentRecords(private val dir: File) {

    private val lock = Any()
    private var loaded = false
    private val moved = HashMap<String, HashMap<Int, Long>>()
    private val pending = HashMap<String, HashMap<Int, Long>>()
    private val failed = HashMap<String, HashMap<Int, String>>()
    private val awaiting = HashSet<String>()
    private val choice = HashSet<String>()

    // ── 읽기 ─────────────────────────────────────────────────────────────────────────────────────────────

    fun movedOf(id: String): Map<Int, Long> = synchronized(lock) {
        ensureLoaded()
        moved[id]?.toMap().orEmpty()
    }

    fun movedSnapshot(): Map<String, Map<Int, Long>> = synchronized(lock) {
        ensureLoaded()
        moved.mapValues { it.value.toMap() }
    }

    fun pendingSnapshot(): Map<String, Map<Int, Long>> = synchronized(lock) {
        ensureLoaded()
        pending.mapValues { it.value.toMap() }
    }

    fun failedOf(id: String): Map<Int, String> = synchronized(lock) {
        ensureLoaded()
        failed[id]?.toMap().orEmpty()
    }

    fun isAwaiting(id: String): Boolean = synchronized(lock) {
        ensureLoaded()
        id in awaiting
    }

    fun needsChoice(id: String): Boolean = synchronized(lock) {
        ensureLoaded()
        id in choice
    }

    // ── 적기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** 옮기기 시작 — 감춘 '내려받기' 항목을 만들었다. */
    fun markPending(id: String, index: Int, mediaId: Long) = append(id, "P\t$index\t$mediaId")

    fun clearPending(id: String, index: Int) = append(id, "p\t$index")

    /** 옮겼다(드러냈다) — 옮기던 것·실패는 지워진다. */
    fun markMoved(id: String, index: Int, mediaId: Long) = append(id, "M\t$index\t$mediaId")

    /** 옮기지 못했다 — 까닭은 화면에 보일 글(한 줄로 고친다). */
    fun markFailed(id: String, index: Int, reason: String) =
        append(id, "F\t$index\t" + reason.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(MAX_REASON))

    fun clearFailed(id: String, index: Int) = append(id, "f\t$index")

    /** 마그넷을 파일 고르기 없이 넣었다 — 메타데이터가 오면 고른다([TorrentCenter]). */
    fun setAwaiting(id: String, on: Boolean) = append(id, if (on) "A" else "a")

    /** 메타데이터가 와서 사용자의 고르기를 기다린다(멈춰 둔 채). */
    fun setChoice(id: String, on: Boolean) = append(id, if (on) "C" else "c")

    /** 토렌트를 지웠다 — 기록째 버린다. */
    fun forget(id: String) {
        if (!ID.matches(id)) return
        synchronized(lock) {
            ensureLoaded()
            moved.remove(id)
            pending.remove(id)
            failed.remove(id)
            awaiting.remove(id)
            choice.remove(id)
            File(dir, "$id.log").delete()
        }
    }

    // ── 안 ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun append(id: String, line: String) {
        // 이름은 info-hash 16진뿐이다 — 다른 글이면 기록 폴더 밖을 가리킬 수 있다.
        if (!ID.matches(id)) return
        synchronized(lock) {
            ensureLoaded()
            apply(id, line)
            try {
                dir.mkdirs()
                FileOutputStream(File(dir, "$id.log"), true).use { it.write("$line\n".toByteArray(Charsets.UTF_8)) }
            } catch (e: IOException) {
                // 기록을 못 남겨도 메모리에는 있다 — 앱이 죽기 전까지는 그대로 돈다. 다음에 뜨면 엔진의 상태로 마저 할 일을 다시 고른다.
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val files = dir.listFiles().orEmpty()
        for (f in files) {
            val id = f.name.removeSuffix(".log")
            if (!f.name.endsWith(".log") || !ID.matches(id)) continue
            val text = try {
                f.readBytes().toString(Charsets.UTF_8)
            } catch (e: IOException) {
                continue
            }
            // 끝이 잘린 줄(쓰다가 죽었다)은 버린다 — 줄바꿈으로 끝난 줄만 읽는다.
            val complete = text.substringBeforeLast('\n', "")
            if (complete.isEmpty()) continue
            for (line in complete.split('\n')) apply(id, line)
        }
    }

    private fun apply(id: String, line: String) {
        val parts = line.split('\t')
        val index = parts.getOrNull(1)?.toIntOrNull()
        when (parts[0]) {
            "P" -> if (index != null) parts.getOrNull(2)?.toLongOrNull()?.let { pending.getOrPut(id) { HashMap() }[index] = it }
            "p" -> if (index != null) pending[id]?.remove(index)
            "M" -> if (index != null) {
                parts.getOrNull(2)?.toLongOrNull()?.let { moved.getOrPut(id) { HashMap() }[index] = it }
                pending[id]?.remove(index)
                failed[id]?.remove(index)
            }
            "F" -> if (index != null) failed.getOrPut(id) { HashMap() }[index] = parts.getOrNull(2).orEmpty()
            "f" -> if (index != null) failed[id]?.remove(index)
            "A" -> awaiting += id
            "a" -> awaiting -= id
            "C" -> choice += id
            "c" -> choice -= id
        }
    }

    private companion object {
        val ID = Regex("[0-9a-f]{40}")
        const val MAX_REASON = 300
    }
}
