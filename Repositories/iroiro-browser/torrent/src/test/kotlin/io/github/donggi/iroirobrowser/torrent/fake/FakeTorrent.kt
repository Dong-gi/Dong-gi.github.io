package io.github.donggi.iroirobrowser.torrent.fake

/** 토렌트에 담을 파일 하나. [path] 는 경로 성분들(여러 파일 꼴의 "path"). */
class FakeFile(val path: List<String>, val data: ByteArray)

/** info 의 차례대로 놓인 파일 하나의 자리. [offset] 은 토렌트 흐름(모든 파일을 이은 것) 기준. */
class FakeLayoutEntry(val path: List<String>, val offset: Long, val length: Long, val isPad: Boolean) {
    override fun toString(): String = "FakeLayoutEntry(${path.joinToString("/")}, offset=$offset, length=$length${if (isPad) ", pad" else ""})"
}

/**
 * 시험용 토렌트(BEP 3 메타정보). 만들 때 info 사전·조각 해시·.torrent 바이트를 모두 계산한다.
 *
 * - [singleFile] 이면 files 는 하나여야 하고 info 에 "length" 를 쓴다(파일 이름은 [name] — FakeFile.path 는 쓰지 않는다).
 * - [padToPieces] 면 마지막 파일을 빼고 각 파일 뒤에 다음 조각 경계까지 패드 파일을 넣는다
 *   (BEP 47: "attr": "p", "path": [".pad", "<길이>"], 내용은 0).
 * - [extraInfo] 는 info 에, [extraTorrent] 는 맨 위 사전에 더하거나 덮어쓴다(같은 키면 이쪽이 이긴다).
 *   extraTorrent 의 "info" 는 무시한다 — info 는 늘 [infoBytes] 그대로 들어간다.
 * - 바이트 배열 속성은 만들 때 한 번 복사해 둔 것이다. 시험이 고쳐도 가짜 서버가 내보내는 것은 바뀌지 않는다.
 */
class FakeTorrent(
    name: String,
    files: List<FakeFile>,
    pieceLength: Int,
    singleFile: Boolean = false,
    announce: String? = null,
    announceList: List<List<String>>? = null,
    urlList: List<String>? = null,
    httpSeeds: List<String>? = null,
    isPrivate: Boolean = false,
    padToPieces: Boolean = false,
    extraInfo: Map<String, Any> = emptyMap(),
    extraTorrent: Map<String, Any> = emptyMap(),
) {
    val name: String = name
    val pieceLength: Int = pieceLength

    /** 한 파일 꼴(info 에 "length")인가. */
    val singleFile: Boolean = singleFile

    /** info 의 차례(패드 포함). */
    val layout: List<FakeLayoutEntry>

    /** 패드 포함. */
    val totalLength: Long
    val pieceCount: Int

    // 가짜 서버들이 쓰는 원본. 공개 속성은 이것의 사본이다.
    internal val payloadInternal: ByteArray
    internal val infoBytesInternal: ByteArray
    internal val infoHashInternal: ByteArray
    private val hashes: List<ByteArray>

    val payload: ByteArray
    val infoBytes: ByteArray
    val torrentBytes: ByteArray
    val infoHash: ByteArray
    val infoHashHex: String

    init {
        require(pieceLength > 0) { "pieceLength 는 양수여야 한다" }
        require(files.isNotEmpty()) { "파일이 하나는 있어야 한다" }
        if (singleFile) require(files.size == 1) { "한 파일 꼴은 files 가 하나여야 한다" }

        val entries = ArrayList<FakeLayoutEntry>()
        val contents = ArrayList<ByteArray?>()
        var offset = 0L
        files.forEachIndexed { i, f ->
            entries += FakeLayoutEntry(if (singleFile) listOf(name) else f.path.toList(), offset, f.data.size.toLong(), false)
            contents += f.data
            offset += f.data.size
            val remainder = offset % pieceLength
            if (padToPieces && !singleFile && i < files.lastIndex && remainder != 0L) {
                val padLength = pieceLength - remainder
                entries += FakeLayoutEntry(listOf(".pad", padLength.toString()), offset, padLength, true)
                contents += null
                offset += padLength
            }
        }
        require(offset <= Int.MAX_VALUE - 64) { "시험용 토렌트가 너무 크다" }
        layout = entries
        totalLength = offset

        payloadInternal = ByteArray(offset.toInt())
        entries.forEachIndexed { i, e ->
            contents[i]?.copyInto(payloadInternal, e.offset.toInt())
        }
        pieceCount = ((totalLength + pieceLength - 1) / pieceLength).toInt()
        hashes = (0 until pieceCount).map { fakeSha1(pieceSlice(it)) }

        val info = LinkedHashMap<String, Any>()
        info["name"] = name.toByteArray(Charsets.UTF_8)
        info["piece length"] = pieceLength.toLong()
        info["pieces"] = java.io.ByteArrayOutputStream(hashes.size * 20).also { out -> hashes.forEach { out.write(it) } }.toByteArray()
        if (singleFile) {
            info["length"] = files[0].data.size.toLong()
        } else {
            info["files"] = entries.map { e ->
                val m = LinkedHashMap<String, Any>()
                m["length"] = e.length
                m["path"] = e.path.map { it.toByteArray(Charsets.UTF_8) }
                if (e.isPad) m["attr"] = "p".toByteArray(Charsets.US_ASCII)
                m
            }
        }
        if (isPrivate) info["private"] = 1L
        info.putAll(extraInfo)
        infoBytesInternal = FakeBencode.encode(info)
        infoHashInternal = fakeSha1(infoBytesInternal)

        val top = LinkedHashMap<String, Any>()
        announce?.let { top["announce"] = it.toByteArray(Charsets.UTF_8) }
        announceList?.let { tiers -> top["announce-list"] = tiers.map { tier -> tier.map { it.toByteArray(Charsets.UTF_8) } } }
        urlList?.let { urls -> top["url-list"] = urls.map { it.toByteArray(Charsets.UTF_8) } }
        httpSeeds?.let { urls -> top["httpseeds"] = urls.map { it.toByteArray(Charsets.UTF_8) } }
        top.putAll(extraTorrent.filterKeys { it != "info" })
        top["info"] = info
        torrentBytes = FakeBencode.encode(top)
        // 인코더가 결정적이므로 .torrent 안의 info 는 infoBytes 와 같다. 어긋나면 가짜 자신의 버그다.
        check(containsAt(torrentBytes, infoBytesInternal)) { "torrentBytes 안의 info 가 infoBytes 와 다르다" }

        payload = payloadInternal.copyOf()
        infoBytes = infoBytesInternal.copyOf()
        infoHash = infoHashInternal.copyOf()
        infoHashHex = infoHashInternal.fakeHex()
    }

    /** 조각 [i] 의 실제 길이(마지막 조각은 짧을 수 있다). */
    fun pieceSize(i: Int): Int {
        require(i in 0 until pieceCount) { "조각 번호가 범위 밖: $i" }
        return minOf(pieceLength.toLong(), totalLength - i.toLong() * pieceLength).toInt()
    }

    fun piece(i: Int): ByteArray = pieceSlice(i)

    fun pieceHash(i: Int): ByteArray {
        require(i in 0 until pieceCount) { "조각 번호가 범위 밖: $i" }
        return hashes[i].copyOf()
    }

    /**
     * 마그넷 링크(BEP 9). xt=urn:btih:<hex 소문자>, dn, tr, ws, x.pe 의 값은 퍼센트 인코딩한다
     * (예약되지 않은 글자만 그대로 — 그래서 ':' 와 '/' 도 %3A·%2F 가 된다).
     */
    fun magnet(
        trackers: List<String> = emptyList(),
        webSeeds: List<String> = emptyList(),
        peers: List<String> = emptyList(),
        displayName: String? = name,
    ): String {
        val sb = StringBuilder("magnet:?xt=urn:btih:").append(infoHashHex)
        displayName?.let { sb.append("&dn=").append(fakePercentEncode(it)) }
        for (t in trackers) sb.append("&tr=").append(fakePercentEncode(t))
        for (w in webSeeds) sb.append("&ws=").append(fakePercentEncode(w))
        for (p in peers) sb.append("&x.pe=").append(fakePercentEncode(p))
        return sb.toString()
    }

    /** 토렌트 흐름 [from, from+length) 의 사본. 범위를 넘으면 예외. */
    internal fun slice(from: Long, length: Int): ByteArray {
        require(from >= 0 && length >= 0 && from + length <= totalLength) { "범위 밖: $from+$length" }
        return payloadInternal.copyOfRange(from.toInt(), (from + length).toInt())
    }

    private fun pieceSlice(i: Int): ByteArray {
        val from = i.toLong() * pieceLength
        return slice(from, pieceSize(i))
    }

    private fun containsAt(haystack: ByteArray, needle: ByteArray): Boolean {
        val marker = "4:info".toByteArray(Charsets.US_ASCII)
        var i = 0
        while (i <= haystack.size - marker.size - needle.size) {
            if (haystack.copyOfRange(i, i + marker.size).contentEquals(marker) &&
                haystack.copyOfRange(i + marker.size, i + marker.size + needle.size).contentEquals(needle)
            ) {
                return true
            }
            i++
        }
        return false
    }

    companion object {
        /** 시드에서 정해지는 바이트(java.util.Random 의 정해진 수열). 시험 자료용. */
        fun data(seed: Int, length: Int): ByteArray = ByteArray(length).also { java.util.Random(seed.toLong()).nextBytes(it) }
    }
}
