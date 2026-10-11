package io.github.donggi.iroirobrowser.torrent.storage

import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 이어받기 자료 — 토렌트마다 `<root>/state/<info-hash>.resume`(bencode). [pieces] 는 **해시를 확인하고 디스크까지 내린(force) 조각만**이다
 * (쓰기를 내리기 전에 '받았다' 고 적으면, 그 사이 앱이 죽었을 때 없는 조각을 가진 것으로 되살린다). 파일마다 그때의 디스크 크기를 적어 두고,
 * 되살릴 때 크기가 어긋나는 파일의 조각만 다시 해시한다.
 */
class ResumeData(
    val infoHash: ByteArray,
    val name: String,
    /** 메타데이터를 받기 전(마그넷)이면 그 주소. */
    val magnet: String?,
    /** 확인한 조각(와이어의 bitfield 꼴). 메타데이터 전이면 null. */
    val pieces: ByteArray?,
    /** 파일마다 적을 때의 디스크 크기(-1 = 없음). */
    val fileSizes: LongArray?,
    /** 사용자가 고른 파일. */
    val selected: Set<Int>?,
    /** 진짜 파일로 만든 파일(나머지는 그림자). */
    val materialized: Set<Int>,
    /** 앱이 옮겨 간 파일. */
    val released: Set<Int>,
    /** '다 받음' 을 알린 파일. */
    val completedFiles: Set<Int>,
    /** "paused"·"completed"·"error" — 되살릴 때 받던 것도 멈춤으로 둔다. */
    val state: String,
    val error: String?,
    val addedAtMs: Long,
    val downloaded: Long,
) {
    fun encode(): ByteArray {
        val m = LinkedHashMap<String, Any>()
        m["v"] = VERSION.toLong()
        m["info-hash"] = infoHash
        m["name"] = name
        magnet?.let { m["magnet"] = it }
        pieces?.let { m["pieces"] = it }
        fileSizes?.let { sizes -> m["file-sizes"] = sizes.map { it } }
        selected?.let { s -> m["selected"] = s.sorted().map { it.toLong() } }
        m["materialized"] = materialized.sorted().map { it.toLong() }
        m["released"] = released.sorted().map { it.toLong() }
        m["completed-files"] = completedFiles.sorted().map { it.toLong() }
        m["state"] = state
        error?.let { m["error"] = it }
        m["added"] = addedAtMs
        m["downloaded"] = downloaded
        return Bencode.encode(m)
    }

    companion object {
        const val VERSION = 1

        /** 깨졌거나 판이 다르면 null — 그때는 처음부터 다시 해시한다. */
        fun decode(bytes: ByteArray): ResumeData? {
            val d = try {
                Bencode.decode(bytes, Bencode.Limits.TORRENT) as? BDict
            } catch (_: BencodeException) {
                null
            } ?: return null
            if (d.long("v") != VERSION.toLong()) return null
            val infoHash = d.bytes("info-hash")?.takeIf { it.size == 20 } ?: return null
            fun ints(key: String): Set<Int>? = d.list(key)?.items?.mapNotNull { (it as? BInt)?.value?.toInt()?.takeIf { v -> v >= 0 } }?.toSet()
            return ResumeData(
                infoHash = infoHash,
                name = d.string("name") ?: "",
                magnet = d.string("magnet"),
                pieces = d.bytes("pieces"),
                fileSizes = d.list("file-sizes")?.items?.map { (it as? BInt)?.value ?: -1L }?.toLongArray(),
                selected = ints("selected"),
                materialized = ints("materialized").orEmpty(),
                released = ints("released").orEmpty(),
                completedFiles = ints("completed-files").orEmpty(),
                state = d.string("state") ?: "paused",
                error = d.string("error"),
                addedAtMs = d.long("added") ?: 0L,
                downloaded = d.long("downloaded") ?: 0L,
            ).takeIf { (d["pieces"] == null || d["pieces"] is BBytes) }
        }
    }
}

/** 원자적 쓰기 — 임시 파일에 쓰고 디스크까지 내린 뒤 이름을 바꾼다. 앱이 쓰다 죽어도 앞 판이나 새 판 둘 중 하나가 남는다. */
object AtomicFiles {
    fun write(file: File, bytes: ByteArray) {
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw java.io.IOException("mkdirs failed: $dir")
        val tmp = File(dir, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun read(file: File, maxBytes: Long = 16L * 1024 * 1024): ByteArray? {
        if (!file.isFile || file.length() > maxBytes) return null
        return runCatching { file.readBytes() }.getOrNull()
    }
}
