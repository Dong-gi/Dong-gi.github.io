package io.github.donggi.iroirobrowser.torrent.meta

import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentException
import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.BList
import io.github.donggi.iroirobrowser.torrent.bencode.BValue
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import io.github.donggi.iroirobrowser.torrent.util.Hex
import io.github.donggi.iroirobrowser.torrent.util.Sha1

/** 토렌트 안의 파일 하나. 경로는 [TorrentPaths] 로 정리한 것이다. */
class TorrentFile(
    val index: Int,
    /** 받기 폴더 안의 상대 경로. 여러 파일 토렌트면 맨 앞 성분이 토렌트 이름(폴더)이다. */
    val path: List<String>,
    val length: Long,
    /** 토렌트 흐름(모든 파일을 이은 것)에서의 시작 자리. */
    val offset: Long,
    /** BEP 47 패드 파일(`attr` 의 `p`) — 내용은 0 이고 디스크에 쓰지 않는다. */
    val isPad: Boolean,
    /** BEP 47 심볼릭 링크(`l`) — 만들지 않는다. */
    val isSymlink: Boolean,
    /** BitComet 의 옛 패드(`_____padding_file_…`) — 표준 패드가 아니라 받기는 하지만 기본으로 고르지 않는다. */
    val isPadLike: Boolean,
    /** 토렌트가 준 그대로의 경로 성분(UTF-8 로 푼 것, 정리하지 않음) — 웹 시드의 주소를 지을 때만 쓴다(BEP 19). */
    val urlPath: List<String>,
) {
    val end: Long get() = offset + length
    val displayPath: String get() = path.joinToString("/")

    /** 사용자가 고를 수 있는 파일인가(패드·심볼릭 링크는 아니다). */
    val isSelectable: Boolean get() = !isPad && !isSymlink

    override fun toString(): String = "#$index $displayPath ($length)"
}

/**
 * `.torrent`(BEP 3)의 info 사전과 그 둘레. **v1 만** 읽는다 — 하이브리드(BEP 52 의 v1+v2)는 v1 부분으로 받고, 순수 v2 는
 * [TorrentError.V2_ONLY], `private=1` 은 [TorrentError.PRIVATE_TORRENT] 로 거부한다.
 *
 * 상한: 파일 수 [MAX_FILES], 조각 수 [MAX_PIECES](libtorrent 의 `load_torrent_limits` 와 같다), 조각 크기 [MAX_PIECE_LENGTH],
 * 파일 길이 합의 넘침, 조각 해시 길이와 조각 수의 대조, 경로의 깊이·길이([TorrentPaths]).
 */
class Metainfo private constructor(
    val infoHash: ByteArray,
    /** info 사전의 원래 바이트 — 마그넷으로 받은 것을 저장할 때 쓴다. */
    val infoBytes: ByteArray,
    val name: String,
    /** 토렌트가 준 그대로의 이름(정리하지 않음) — 웹 시드 주소용. */
    val urlName: String,
    val pieceLength: Int,
    private val pieceHashes: ByteArray,
    val files: List<TorrentFile>,
    val totalLength: Long,
    /** BEP 12 의 층. 층마다 http·https·udp 주소. */
    val trackers: List<List<String>>,
    /** BEP 19 `url-list`(http·https). */
    val webSeeds: List<String>,
    /** BEP 17 `httpseeds`. */
    val httpSeeds: List<String>,
    val isMultiFile: Boolean,
    val isHybrid: Boolean,
    val comment: String?,
    val createdBy: String?,
) {
    val infoHashHex: String get() = Hex.encode(infoHash)
    val pieceCount: Int = (pieceHashes.size / 20)

    fun pieceHash(index: Int): ByteArray = pieceHashes.copyOfRange(index * 20, index * 20 + 20)

    fun pieceHashEquals(index: Int, digest: ByteArray): Boolean {
        if (digest.size != 20) return false
        for (i in 0 until 20) if (pieceHashes[index * 20 + i] != digest[i]) return false
        return true
    }

    fun pieceOffset(index: Int): Long = index.toLong() * pieceLength

    fun pieceSize(index: Int): Int =
        if (index == pieceCount - 1) (totalLength - pieceOffset(index)).toInt() else pieceLength

    /** 그 조각이 걸친 파일들(길이 0 인 파일은 빼고). */
    fun filesOfPiece(index: Int): IntRange {
        val start = pieceOffset(index)
        val end = start + pieceSize(index)
        var lo = fileAt(start)
        while (lo < files.size && files[lo].length == 0L) lo++
        var hi = lo
        while (hi + 1 < files.size && files[hi + 1].offset < end) hi++
        while (hi > lo && files[hi].length == 0L) hi--
        return lo..hi
    }

    /** 파일이 걸친 조각들. 길이 0 이면 빈 범위. */
    fun piecesOfFile(index: Int): IntRange {
        val f = files[index]
        if (f.length == 0L) return IntRange.EMPTY
        return (f.offset / pieceLength).toInt()..((f.end - 1) / pieceLength).toInt()
    }

    /** 흐름의 [position] 바이트를 가진 파일(길이 0 인 파일은 건너뛴 첫 파일). */
    fun fileAt(position: Long): Int {
        var lo = 0
        var hi = files.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (files[mid].offset <= position) lo = mid else hi = mid - 1
        }
        // 같은 offset 에 길이 0 인 파일들이 앞에 있으면 데이터가 있는 파일로 간다.
        var i = lo
        while (i > 0 && files[i - 1].offset == files[i].offset) i--
        while (i < files.size - 1 && files[i].length == 0L && files[i].offset <= position) i++
        return i
    }

    /** 처음에 고를 파일 — 패드·심볼릭 링크·옛 패드는 뺀다. */
    fun defaultSelection(): Set<Int> = files.filter { it.isSelectable && !it.isPadLike }.map { it.index }.toSet()

    companion object {
        const val MAX_FILES = 100_000
        const val MAX_PIECES = 0x200000
        const val MAX_PIECE_LENGTH = 64 * 1024 * 1024
        const val MAX_TRACKER_TIERS = 50
        const val MAX_TRACKERS = 200
        const val MAX_WEB_SEEDS = 50
        const val MAX_URL_LENGTH = 2048

        /** `.torrent` 파일 전체. */
        fun parse(torrent: ByteArray): Metainfo {
            val root = decodeDict(torrent)
            val info = root["info"] as? BDict ?: invalid("no info dict")
            val infoBytes = torrent.copyOfRange(info.start, info.end)
            return build(info, infoBytes, trackersOf(root), webSeedsOf(root), httpSeedsOf(root), root.string("comment"), root.string("created by"))
        }

        /** 마그넷으로 받은 info 사전(해시를 이미 맞춘 것) + 마그넷의 트래커·웹 시드. */
        fun fromInfo(infoBytes: ByteArray, trackers: List<List<String>>, webSeeds: List<String>): Metainfo {
            val info = decodeDict(infoBytes)
            return build(info, infoBytes, trackers, webSeeds.filter(::isWebSeedUrl).take(MAX_WEB_SEEDS), emptyList(), null, null)
        }

        private fun decodeDict(bytes: ByteArray): BDict {
            val v = try {
                Bencode.decode(bytes, Bencode.Limits.TORRENT)
            } catch (e: BencodeException) {
                invalid("bencode: ${e.message}")
            }
            return v as? BDict ?: invalid("not a dict")
        }

        private fun build(
            info: BDict,
            infoBytes: ByteArray,
            trackers: List<List<String>>,
            webSeeds: List<String>,
            httpSeeds: List<String>,
            comment: String?,
            createdBy: String?,
        ): Metainfo {
            val infoHash = Sha1.digest(infoBytes)
            val metaVersion = info.long("meta version")
            val hasPieces = info["pieces"] != null
            if (metaVersion != null && metaVersion >= 2 && !hasPieces) throw TorrentException(TorrentError.V2_ONLY)
            val isHybrid = metaVersion != null && metaVersion >= 2
            // private 는 정수 1 이 규격이지만 다른 꼴의 참 값도 거부 쪽으로 읽는다.
            when (val p = info["private"]) {
                is BInt -> if (p.value != 0L) throw TorrentException(TorrentError.PRIVATE_TORRENT)
                is BBytes -> if (p.latin1() == "1") throw TorrentException(TorrentError.PRIVATE_TORRENT)
                else -> Unit
            }

            val pieceLength = info.long("piece length") ?: invalid("no piece length")
            if (pieceLength <= 0 || pieceLength > MAX_PIECE_LENGTH) invalid("piece length $pieceLength")
            val pieces = info.bytes("pieces") ?: invalid("no pieces")
            if (pieces.isEmpty() || pieces.size % 20 != 0) invalid("pieces length ${pieces.size}")

            val rawName = (info["name.utf-8"] as? BBytes ?: info["name"] as? BBytes)?.utf8() ?: invalid("no name")
            val name = TorrentPaths.name(rawName, Hex.encode(infoHash))

            val filesList = info["files"]
            val singleLength = info["length"]
            if (filesList != null && singleLength != null) invalid("both length and files")
            val entries = ArrayList<RawFile>()
            val isMulti: Boolean
            if (filesList != null) {
                isMulti = true
                val list = filesList as? BList ?: invalid("files is not a list")
                if (list.items.isEmpty()) invalid("empty files")
                if (list.items.size > MAX_FILES) throw TorrentException(TorrentError.TOO_LARGE, "files ${list.items.size}")
                for (item in list.items) entries.add(rawFile(item as? BDict ?: invalid("file entry is not a dict")))
            } else {
                isMulti = false
                val length = (singleLength as? BInt)?.value ?: invalid("no length")
                if (length < 0) invalid("negative length")
                entries.add(RawFile(length, listOf(name), attr = "", urlComponents = emptyList()))
            }

            var total = 0L
            val offsets = LongArray(entries.size)
            for ((i, e) in entries.withIndex()) {
                offsets[i] = total
                total = try {
                    Math.addExact(total, e.length)
                } catch (_: ArithmeticException) {
                    invalid("total length overflow")
                }
            }
            if (total <= 0) invalid("no data")
            val pieceCount = (total - 1) / pieceLength + 1
            if (pieceCount > MAX_PIECES) throw TorrentException(TorrentError.TOO_LARGE, "pieces $pieceCount")
            if (pieces.size.toLong() != pieceCount * 20) invalid("pieces ${pieces.size / 20} != $pieceCount")

            val rawPaths = entries.map { e -> if (isMulti) listOf(name) + e.components else e.components }
            for (p in rawPaths) {
                if (p.size > TorrentPaths.MAX_DEPTH + 1) throw TorrentException(TorrentError.TOO_LARGE, "path depth ${p.size}")
                if (TorrentPaths.byteLength(p) > TorrentPaths.MAX_PATH_BYTES) throw TorrentException(TorrentError.TOO_LARGE, "path bytes")
            }
            val isPad = BooleanArray(entries.size) { isMulti && entries[it].isPad }
            val isLink = BooleanArray(entries.size) { 'l' in entries[it].attr }
            val paths = TorrentPaths.resolveCollisions(rawPaths) { isPad[it] || isLink[it] }
            val files = entries.mapIndexed { i, e ->
                TorrentFile(
                    index = i,
                    path = paths[i],
                    length = e.length,
                    offset = offsets[i],
                    isPad = isPad[i],
                    isSymlink = isLink[i],
                    isPadLike = !isPad[i] && paths[i].last().startsWith("_____padding_file_"),
                    urlPath = e.urlComponents,
                )
            }
            return Metainfo(
                infoHash = infoHash,
                infoBytes = infoBytes,
                name = name,
                urlName = rawName,
                pieceLength = pieceLength.toInt(),
                pieceHashes = pieces,
                files = files,
                totalLength = total,
                trackers = trackers,
                webSeeds = webSeeds,
                httpSeeds = httpSeeds,
                isMultiFile = isMulti,
                isHybrid = isHybrid,
                comment = comment,
                createdBy = createdBy,
            )
        }

        private class RawFile(val length: Long, val components: List<String>, val attr: String, val urlComponents: List<String>) {
            /** BEP 47 의 `p`, 또는 libtorrent 가 쓰는 `.pad/` 아래 경로. */
            val isPad: Boolean get() = 'p' in attr
        }

        private fun rawFile(d: BDict): RawFile {
            val length = d.long("length") ?: invalid("file without length")
            if (length < 0) invalid("negative file length")
            val pathValue = d.list("path.utf-8") ?: d.list("path") ?: invalid("file without path")
            if (pathValue.items.isEmpty()) invalid("empty path")
            if (pathValue.items.size > TorrentPaths.MAX_DEPTH) throw TorrentException(TorrentError.TOO_LARGE, "path depth")
            val rawComponents = pathValue.items.map { (it as? BBytes)?.bytes ?: invalid("path component is not a string") }
            val attr = d.bytes("attr")?.toString(Charsets.US_ASCII) ?: ""
            val isPadPath = rawComponents.size >= 2 && rawComponents[0].contentEquals(".pad".toByteArray())
            val components = rawComponents.mapNotNull(TorrentPaths::component).ifEmpty { listOf("_") }
            return RawFile(length, components, if (isPadPath && 'p' !in attr) attr + "p" else attr, rawComponents.map { it.toString(Charsets.UTF_8) })
        }

        private fun trackersOf(root: BDict): List<List<String>> {
            val seen = HashSet<String>()
            val tiers = ArrayList<List<String>>()
            var count = 0
            val announceList = root.list("announce-list")
            if (announceList != null) {
                for (tier in announceList.items) {
                    if (tiers.size >= MAX_TRACKER_TIERS) break
                    val urls = (tier as? BList)?.items.orEmpty().mapNotNull { (it as? BBytes)?.utf8()?.trim() }
                        .filter { isTrackerUrl(it) && seen.add(it) }
                    val room = MAX_TRACKERS - count
                    if (room <= 0) break
                    val kept = urls.take(room)
                    if (kept.isNotEmpty()) {
                        tiers.add(kept)
                        count += kept.size
                    }
                }
            }
            if (tiers.isEmpty()) {
                val announce = root.string("announce")?.trim()
                if (announce != null && isTrackerUrl(announce)) tiers.add(listOf(announce))
            }
            return tiers
        }

        private fun webSeedsOf(root: BDict): List<String> = urlsOf(root["url-list"]).filter(::isWebSeedUrl).distinct().take(MAX_WEB_SEEDS)

        private fun httpSeedsOf(root: BDict): List<String> = urlsOf(root["httpseeds"]).filter(::isWebSeedUrl).distinct().take(MAX_WEB_SEEDS)

        private fun urlsOf(v: BValue?): List<String> = when (v) {
            is BBytes -> listOf(v.utf8().trim())
            is BList -> v.items.mapNotNull { (it as? BBytes)?.utf8()?.trim() }
            else -> emptyList()
        }

        fun isTrackerUrl(url: String): Boolean {
            if (url.length > MAX_URL_LENGTH) return false
            val lower = url.lowercase()
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("udp://")
        }

        fun isWebSeedUrl(url: String): Boolean {
            if (url.length > MAX_URL_LENGTH) return false
            val lower = url.lowercase()
            return lower.startsWith("http://") || lower.startsWith("https://")
        }

        private fun invalid(detail: String): Nothing = throw TorrentException(TorrentError.INVALID_TORRENT, detail)
    }
}
