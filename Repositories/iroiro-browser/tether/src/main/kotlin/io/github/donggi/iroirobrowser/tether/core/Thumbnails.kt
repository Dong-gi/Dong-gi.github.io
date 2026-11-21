package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 섬네일의 형식 보기와 디스크 캐시.
 *
 * 섬네일은 [SharedTree.thumbnail] 이 짓는다(앱 — 제공자가 준 바이트·미디어 서버가 푼 장면). 파일 서버는 받은 바이트를 **머리로** 보아
 * JPEG·PNG·WebP·GIF 만 그 형식으로 내보낸다([sniff]) — 확장자나 제공자의 말을 믿지 않는다(그림이 아닌 것을 그림인 척 내보내지 않게, 그리고
 * nosniff 와 함께 브라우저가 다른 것으로 읽지 않게). 크기는 [MAX_BYTES] 까지.
 */
object Thumbnails {

    /** 섬네일 하나의 바이트 상한 — 넘으면 내보내지 않고 화면이 아이콘을 그린다. */
    const val MAX_BYTES = 512 * 1024

    /** 받는 한 변 크기 — 목록(작은 것)과 격자(큰 것) 둘. 그 밖의 값은 가까운 쪽으로. */
    const val SMALL = 160
    const val LARGE = 320

    fun clampSize(requested: Int?): Int = if (requested != null && requested > SMALL) LARGE else SMALL

    /** 머리 바이트로 본 MIME. 모르면 null. */
    fun sniff(bytes: ByteArray): String? {
        fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 && at(4) == 0x0D && at(5) == 0x0A -> "image/png"
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == '8'.code -> "image/gif"
            at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
                at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> "image/webp"
            else -> null
        }
    }
}

/**
 * 섬네일의 디스크 캐시 — 앱은 `:tether` 프로세스의 cacheDir 아래 폴더를 준다(시스템이 모자라면 지울 수 있는 자리). 파일 이름은 열쇠(문서의
 * 열쇠·바뀐 때·크기·요청 크기)의 SHA-256 16진이라 **손님이 보낸 글이 경로에 들어가지 않는다**(앱 자료 폴더의 파일 읽기·쓰기 — CLAUDE.md
 * '구조' 의 UID 경계). 상한은 개수 [maxFiles]·합 [maxBytes] — 넘으면 오래 쓰지 않은 것부터 지운다(쓸 때마다 고친 시각으로 LRU).
 */
class ThumbnailCache(
    private val dir: File,
    private val maxFiles: Int = 3000,
    private val maxBytes: Long = 48L * 1024 * 1024,
) {
    private val writesSinceTrim = AtomicInteger(TRIM_EVERY) // 처음 쓰기에서 한 번 다듬는다

    /** 그 항목·크기의 열쇠. */
    fun key(entry: SharedEntry, sizePx: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val raw = "${entry.ref}\u0000${entry.modified}\u0000${entry.size}\u0000$sizePx"
        val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return buildString { for (b in hash) append(HEX[(b.toInt() shr 4) and 0x0F]).append(HEX[b.toInt() and 0x0F]) }
    }

    fun get(key: String): ByteArray? {
        val file = File(dir, key)
        return try {
            if (!file.isFile) return null
            val bytes = file.readBytes()
            file.setLastModified(System.currentTimeMillis())
            bytes
        } catch (e: Exception) {
            null
        }
    }

    fun put(key: String, bytes: ByteArray) {
        if (bytes.size > Thumbnails.MAX_BYTES) return
        try {
            if (!dir.isDirectory && !dir.mkdirs()) return
            // 다 쓴 뒤에 이름을 바꾼다 — 같은 열쇠를 함께 쓰는 요청이 반쯤 쓴 파일을 읽지 않게.
            val temp = File(dir, "$key.${Thread.currentThread().id}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(File(dir, key))) temp.delete()
        } catch (e: Exception) {
            return
        }
        if (writesSinceTrim.incrementAndGet() >= TRIM_EVERY) {
            writesSinceTrim.set(0)
            trim()
        }
    }

    /** 상한을 넘었으면 오래 쓰지 않은 것부터 지운다. 남은 임시 파일도 지운다. */
    @Synchronized
    fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        val kept = files.filterNot { it.name.endsWith(".tmp") }.sortedByDescending { it.lastModified() }
        var total = 0L
        for ((index, file) in kept.withIndex()) {
            total += file.length()
            if (index >= maxFiles || total > maxBytes) file.delete()
        }
    }

    private companion object {
        const val TRIM_EVERY = 64
        const val HEX = "0123456789abcdef"
    }
}
