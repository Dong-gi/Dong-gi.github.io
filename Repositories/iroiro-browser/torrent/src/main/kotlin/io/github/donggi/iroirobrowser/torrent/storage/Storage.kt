package io.github.donggi.iroirobrowser.torrent.storage

import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * 조각 ↔ 파일. **디스크 일꾼 스레드 하나에서만** 부른다(스레드 안전하지 않다).
 *
 * - 파일은 [dataDir] 아래 정리한 경로([io.github.donggi.iroirobrowser.torrent.meta.TorrentPaths])에 만든다. 만들 때마다 캐노니컬 경로가
 *   [dataDir] 안인지 다시 본다(정리가 놓친 것이 있어도 밖에 쓰지 않는다).
 * - **패드 파일(BEP 47)은 쓰지 않는다** — 읽을 때 0 을 준다.
 * - **만들지 않는 파일**(고르지 않았거나 앱이 이미 옮긴 파일)에 걸친 조각의 그 부분은 [shadowDir] 의 그림자 파일에 쓴다 — 조각을 해시로
 *   확인하려면 조각 전체가 있어야 하는데, 고르지 않은 파일을 사용자의 받기 폴더에 만들 수는 없다. 나중에 그 파일을 고르면 확인된 조각의
 *   그 부분을 진짜 파일로 옮긴다([materialize]).
 * - 열린 파일은 [MAX_OPEN] 개까지(파일이 10만 개인 토렌트도 있다).
 */
internal class Storage(
    private val meta: Metainfo,
    private val dataDir: File,
    private val shadowDir: File,
    materializedFiles: Set<Int>,
) : Closeable {

    private val materialized = BooleanArray(meta.files.size) { it in materializedFiles }
    private val open = object : LinkedHashMap<String, FileChannel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FileChannel>?): Boolean {
            if (size > MAX_OPEN) {
                runCatching { eldest?.value?.close() }
                return true
            }
            return false
        }
    }
    private val dataRoot: String by lazy { dataDir.canonicalPath + File.separator }

    fun isMaterialized(index: Int): Boolean = materialized[index]

    /** 받기 폴더 안의 진짜 파일. */
    fun fileOf(index: Int): File {
        var f = dataDir
        for (c in meta.files[index].path) f = File(f, c)
        return f
    }

    private fun shadowOf(index: Int): File = File(shadowDir, index.toString())

    /** 조각의 [begin] 부터 쓴다. */
    fun write(piece: Int, begin: Int, data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        var pos = meta.pieceOffset(piece) + begin
        var off = offset
        var left = length
        var fi = meta.fileAt(pos)
        while (left > 0 && fi < meta.files.size) {
            val f = meta.files[fi]
            if (f.length == 0L || pos >= f.end) {
                fi++
                continue
            }
            val n = minOf(left.toLong(), f.end - pos).toInt()
            if (!f.isPad) {
                val ch = channel(fi, create = true)!!
                writeFully(ch, ByteBuffer.wrap(data, off, n), pos - f.offset)
            }
            pos += n
            off += n
            left -= n
            fi++
        }
        if (left > 0) throw IOException("write past end")
    }

    /** 조각 전체를 읽는다 — 없는 파일·짧은 파일의 자리는 0(그러면 해시가 맞지 않는다). 시험용 — 엔진은 [verify] 로 덩이씩 읽는다. */
    fun readPiece(piece: Int): ByteArray {
        val out = ByteArray(meta.pieceSize(piece))
        var at = 0
        forEachChunk(piece, ByteArray(CHUNK)) { buf, n ->
            System.arraycopy(buf, 0, out, at, n)
            at += n
        }
        return out
    }

    /**
     * 조각의 SHA-1 이 메타정보와 맞는가. [CHUNK] 씩 읽으며 해시를 갱신한다 — 조각이 수십 MiB 인 토렌트에서 조각 하나를 통째로 메모리에
     * 올리지 않는다.
     */
    fun verify(piece: Int): Boolean {
        val md = MessageDigest.getInstance("SHA-1")
        forEachChunk(piece, scratch) { buf, n -> md.update(buf, 0, n) }
        return meta.pieceHashEquals(piece, md.digest())
    }

    private val scratch = ByteArray(CHUNK)

    /** 조각을 앞에서부터 [buf] 크기의 덩이로 넘긴다. 패드·없는 파일·짧은 파일의 자리는 0 이다. */
    private inline fun forEachChunk(piece: Int, buf: ByteArray, consume: (ByteArray, Int) -> Unit) {
        val size = meta.pieceSize(piece)
        var pos = meta.pieceOffset(piece)
        var done = 0
        var fi = meta.fileAt(pos)
        while (done < size && fi < meta.files.size) {
            val f = meta.files[fi]
            if (f.length == 0L || pos >= f.end) {
                fi++
                continue
            }
            val inFile = minOf((size - done).toLong(), f.end - pos).toInt()
            val ch = if (f.isPad) null else channel(fi, create = false)
            var left = inFile
            while (left > 0) {
                val n = minOf(left, buf.size)
                java.util.Arrays.fill(buf, 0, n, 0)
                if (ch != null) readFully(ch, ByteBuffer.wrap(buf, 0, n), pos - f.offset)
                consume(buf, n)
                pos += n
                done += n
                left -= n
            }
            fi++
        }
    }

    /** 디스크의 그 파일 크기(진짜 파일). 없으면 -1. */
    fun actualSize(index: Int): Long {
        val f = fileOf(index)
        return if (f.isFile) f.length() else -1
    }

    /**
     * 그림자로 두던 파일을 진짜 파일로 — [have] 의 조각 가운데 그 파일에 걸친 부분을 그림자에서 옮기고 그림자를 지운다.
     * 이미 진짜면 아무것도 하지 않는다.
     */
    fun materialize(index: Int, have: Bitfield) {
        if (materialized[index]) return
        val f = meta.files[index]
        closeFile(index)
        materialized[index] = true
        if (f.isPad || f.length == 0L) return
        val shadow = shadowOf(index)
        if (shadow.isFile) {
            RandomAccessFile(shadow, "r").use { raf ->
                val src = raf.channel
                val dst = channel(index, create = true)!!
                val arr = ByteArray(CHUNK)
                for (p in meta.piecesOfFile(index)) {
                    if (!have[p]) continue
                    var start = maxOf(meta.pieceOffset(p), f.offset)
                    val end = minOf(meta.pieceOffset(p) + meta.pieceSize(p), f.end)
                    while (start < end) {
                        val n = minOf(CHUNK.toLong(), end - start).toInt()
                        // ByteBuffer.limit(int)·flip() 의 공변 판은 안드로이드 표에 없다 — 덩이마다 새로 감싼다.
                        java.util.Arrays.fill(arr, 0, n, 0)
                        readFully(src, ByteBuffer.wrap(arr, 0, n), start - f.offset)
                        writeFully(dst, ByteBuffer.wrap(arr, 0, n), start - f.offset)
                        start += n
                    }
                }
            }
            closeFile(index)
            shadow.delete()
        }
    }

    /** 앱이 그 파일을 옮겼다 — 다시 진짜 파일을 읽거나 쓰지 않는다(그 뒤 그 자리의 쓰기는 그림자로). */
    fun release(index: Int) {
        closeFile(index)
        materialized[index] = false
    }

    /** 다 받은 파일을 앱이 옮기기 전에 닫는다(열린 손잡이가 있으면 옮기지 못하는 파일 시스템이 있다). */
    fun closeFile(index: Int) {
        open.remove("r$index")?.let { runCatching { it.close() } }
        open.remove("s$index")?.let { runCatching { it.close() } }
    }

    /** 쓴 것을 디스크까지 내린다 — 이어받기 자료에 '확인한 조각' 을 적기 전에. */
    fun flush() {
        for (ch in open.values) ch.force(false)
    }

    override fun close() {
        for (ch in open.values) runCatching { ch.close() }
        open.clear()
    }

    /** 받은 것을 모두 지운다(우리 파일·그 뒤 빈 폴더·그림자). [dataDir] 밖은 건드리지 않는다. */
    fun deleteAll() {
        close()
        for (i in meta.files.indices) {
            val f = fileOf(i)
            if (f.isFile && f.canonicalPath.startsWith(dataRoot)) f.delete()
        }
        // 빈 폴더만 지워진다(File.delete 는 빈 폴더만 지운다) — 아래에서 위로.
        if (dataDir.isDirectory) dataDir.walkBottomUp().forEach { if (it.isDirectory) it.delete() }
        if (shadowDir.isDirectory) shadowDir.walkBottomUp().forEach { it.delete() }
    }

    private fun channel(index: Int, create: Boolean): FileChannel? {
        val real = materialized[index]
        val key = (if (real) "r" else "s") + index
        open[key]?.let { return it }
        val file = if (real) fileOf(index) else shadowOf(index)
        if (!file.isFile) {
            if (!create) return null
            val parent = file.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) throw IOException("mkdirs failed")
        }
        if (real && !(file.canonicalPath).startsWith(dataRoot)) throw IOException("path escapes data dir")
        // 늘 "rw" 로 연다 — 읽으려고 연 채널을 캐시에서 다시 꺼내 쓰기에 쓸 수 있게. 미리 제 길이로 늘이지 않는다 — 파일 크기가 '실제로 쓴
        // 데까지' 여야 이어받기의 크기 대조가 뜻이 있다(순차로 받으니 빈 곳이 거의 없다).
        val raf = RandomAccessFile(file, "rw")
        val ch = raf.channel
        open[key] = ch
        return ch
    }

    private fun writeFully(ch: FileChannel, buf: ByteBuffer, position: Long) {
        var p = position
        while (buf.hasRemaining()) p += ch.write(buf, p)
    }

    private fun readFully(ch: FileChannel, buf: ByteBuffer, position: Long) {
        var p = position
        while (buf.hasRemaining()) {
            val n = ch.read(buf, p)
            if (n < 0) break
            p += n
        }
    }

    companion object {
        const val MAX_OPEN = 16

        /** 해시·옮기기에서 한 번에 읽는 크기. */
        const val CHUNK = 256 * 1024
    }
}
