// tools/torrent_vectors.py 가 지었다(python -I tools/torrent_vectors.py) — 손으로 고치지 않는다.
package io.github.donggi.iroirobrowser.torrent

/**
 * 토렌트 엔진의 파서·암호를 바깥에서 대조할 정답 벡터 — PC 의 파이썬(표준 라이브러리만)이 엔진의 코드를 보지 않고 지었다.
 * 무작위를 쓰지 않아 다시 지어도 같고, 지은 쪽이 스스로 다시 풀어 대조한 뒤에 쓴다(tools/torrent_vectors.py 의 머리말).
 * 사례마다 무엇을 보이는지는 그 앞의 주석에 있다. 인터넷 주소는 문서용(example.org·192.0.2.0/24·198.51.100.0/24·
 * 203.0.113.0/24·2001:db8::/32)이다.
 */
internal object PythonVectors {
    /** 결정적 바이트: block(c) = SHA1(("iroiro-" + seed + "-" + c) 의 UTF-8), c = 0,1,2,… 를 이어 앞 length 바이트. 패드 파일은 0. */
    class FileSpec(val path: List<String>, val length: Long, val seed: Int, val pad: Boolean, val attr: String)

    class TorrentCase(
        val id: String,
        val torrentBase64: String,       // .torrent 전체
        val expect: String,              // "ok" | "hybrid"(v1 로 읽어야 한다) | "v2-only"(거부) | "private"(거부)
        val infoHashV1Hex: String,       // SHA-1(info 사전의 **원래 바이트**), 소문자
        val infoHashV2Hex: String?,      // v2·하이브리드면 SHA-256(info 원래 바이트)
        val name: String,
        val pieceLength: Int,
        val totalLength: Long,           // v1 꼴 파일 길이 합(패드 포함), 순수 v2 는 파일 길이 합
        val pieceHashesHex: String,      // 20바이트씩 이은 hex(순수 v2 는 "")
        val files: List<FileSpec>,       // v1 info 의 차례(패드 포함). 한 파일 꼴이면 path = listOf(name)
        val trackers: List<List<String>>,// announce-list 의 층(없으면 announce 하나를 한 층으로, 그것도 없으면 빈 목록)
        val webSeeds: List<String>,      // url-list
    )

    /**
     * 토렌트 9 — single-file, multi-file, pad-files, hybrid-v1-v2, v2-only, private, unsorted-info-keys,
     * utf8-keys, tracker-forms. 무엇을 보이는지는 각 함수의 주석에 있다. 파일 내용은 [FileSpec] 의 규칙으로 지으면 조각 해시까지 견줄 수 있다.
     */
    val torrents: List<TorrentCase> = listOf(torrent1(), torrent2(), torrent3(), torrent4(), torrent5(), torrent6(), torrent7(), torrent8(), torrent9())

    /** expect 가 "ok" 가 아니면 infoHashHex·name 은 null, 목록은 빈 것이다. peers 의 IPv6 는 "[주소]:포트" 꼴. */
    class MagnetCase(val uri: String, val expect: String /* "ok" | "v2-only" | "invalid" */, val infoHashHex: String?, val name: String?,
        val trackers: List<String>, val webSeeds: List<String>, val peers: List<String>)

    /** 마그넷 13 — 사례마다의 뜻은 그 줄 위의 주석. */
    val magnets: List<MagnetCase> = magnetCases()

    /** 키 흐름의 앞 drop 바이트를 버린 뒤 평문과 xor 한 것이 암호문이다. */
    class Rc4Case(val keyHex: String, val drop: Int, val plaintextHex: String, val ciphertextHex: String)

    /** RC4 7 — 위키백과의 시험 벡터 셋, RFC 6229 의 키 0102030405 둘(오프셋 0·1024), SHA 로 지은 키 둘(drop 1024). */
    val rc4: List<Rc4Case> = rc4Cases()

    class MseCase(
        val infoHashHex: String, val xaHex: String, val xbHex: String, val padAHex: String, val padBHex: String,
        val cryptoProvide: Int, val cryptoSelect: Int, val padCHex: String, val padDHex: String, val iaHex: String,
        val yaHex: String, val ybHex: String, val sHex: String,            // 셋 다 96바이트 big-endian(앞을 0 으로 채움)
        val req1Hex: String, val req2xor3Hex: String, val keyAHex: String, val keyBHex: String,
        val message1Hex: String,     // Ya ‖ PadA
        val message2Hex: String,     // Yb ‖ PadB
        val message3Hex: String,     // SHA1('req1'‖S) ‖ (SHA1('req2'‖SKEY) xor SHA1('req3'‖S)) ‖ ENC_A(VC ‖ crypto_provide ‖ len(PadC) ‖ PadC ‖ len(IA)) ‖ ENC_A(IA)
        val message4Hex: String,     // ENC_B(VC ‖ crypto_select ‖ len(PadD) ‖ PadD)
        val afterAPlainHex: String, val afterACipherHex: String,   // 그 뒤 A 가 보내는 바이트와 실제로 선 위에 나가는 바이트(select 가 RC4 면 RC4_A 를 이어서, 평문이면 그대로)
        val afterBPlainHex: String, val afterBCipherHex: String,   // B 쪽도 같게
    )

    /**
     * MSE 둘. P 는 명세의 768 비트 소수, G = 2, SKEY = info-hash, keyA·keyB 의 RC4 는 키 흐름 앞 1024 바이트를 버리고 한 쪽의
     * ENCRYPT 는 한 흐름으로 이어진다. Xa·Xb 는 SHA-1 로 지은 160 비트 값 가운데 큰 정수의 바이트 꼴을 시험하도록 골랐다(각 사례의 주석). B 의 자리에서
     * req1 으로, A 의 자리에서 ENCRYPT(VC) 로 동기화해 풀어 보는 것까지 대조했다.
     */
    val mse: List<MseCase> = listOf(mse1(), mse2())

    class BencodeCase(val inputHex: String, val valid: Boolean, val note: String)

    /** bencode 46 — 받을 것 14, 거부할 것 32. 정렬되지 않은 키는 받는다(토렌트 unsorted-info-keys 와 같은 까닭). */
    val bencode: List<BencodeCase> = bencodeCases()

    class KrpcCase(val id: String, val bytesHex: String, val note: String)
    // 풀어 본 결과를 시험이 대조할 수 있게, 응답 사례는 note 에 사람이 읽는 기대값을 적고, 따로 아래 둘을 둔다:
    class KrpcPeersCase(val bytesHex: String, val nodeIdsHex: List<String>, val nodeAddrs: List<String> /* "ip:port" */, val peers: List<String> /* "ip:port" */)

    /** KRPC 5 — 질의 둘(키는 바이트 차례, 맨 위 ro = 1), 응답 둘, 오류 하나. */
    val krpc: List<KrpcCase> = krpcCases()

    /** 위 응답 둘(get_peers·find_node)을 풀어 낸 기대값 — nodes 는 26 바이트씩(id 20 + IPv4 4 + 포트 2), values 는 6 바이트씩. */
    val krpcResponses: List<KrpcPeersCase> = krpcResponseCases()

    class UdpCase(val id: String, val bytesHex: String, val note: String)

    /** UDP 트래커 5 — 모든 수는 big-endian. */
    val udp: List<UdpCase> = udpCases()

    /**
     * single-file: 한 파일(BEP 3 의 length 꼴). 길이 100,000 은 조각 16,384 의 배수가 아니다 — 조각 일곱, 마지막은 1,696 바이트.
     * announce 하나.
     */
    private fun torrent1() = TorrentCase(
        id = "single-file",
        torrentBase64 = "ZDg6YW5ub3VuY2U0MDpodHRwOi8vdHJhY2tlci5leGFtcGxlLm9yZzo2OTY5L2Fubm91bmNlNDppbmZvZDY6bGVuZ3RoaTEwMDAwMGU0Om5hbWUxMDpzaW5nbGUuYmluMTI6cGllY2UgbGVuZ3RoaTE2Mzg0ZTY6cGllY2VzMTQwOrg9ugJMCpacR3euyHt6iUpIqM+gLCwjAiiog8RwJGGMnAVfKxz7ZHNT15Pq8TO6OcJP7HDp7nrIO8qIsqeNdFWINdct+yw4WHyCzsuXG2sn3h35u9kcc+uSIRYzF/9U/oMIwDultnSFE7tsG9URyVsR/kaXqHG/U4ecfRyronA1uj5w7qUZOpOnyXcNZWU=",
        expect = "ok",
        infoHashV1Hex = "5edef1cea7b9379514fc49ff9a989e5a359f8541",
        infoHashV2Hex = null,
        name = "single.bin",
        pieceLength = 16384,
        totalLength = 100000L,
        pieceHashesHex = "b83dba024c0a969c4777aec87b7a894a48a8cfa02c2c230228a883c47024618c9c055f2b1cfb647353d793eaf133ba39c24fec70e9ee7ac83bca88b2a78d74558835d72dfb2c38587c82cecb971b6b27de1df9bbd91c73eb9221163317ff54fe8308c03ba5b6748513bb6c1bd511c95b11fe4697a871bf53879c7d1caba27035ba3e70eea5193a93a7c9770d",
        files = listOf(
            FileSpec(path = listOf("single.bin"), length = 100000L, seed = 11, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("http://tracker.example.org:6969/announce")),
        webSeeds = emptyList(),
    )

    /**
     * multi-file: 여러 파일 넷 — 중첩 경로(sub/deep/b.bin)와 길이 0 인 파일(empty.dat)을 끼고, files 의 차례는 정렬하지 않은 그대로다.
     * announce + announce-list(층 둘, announce 는 첫 층에도 있다) + url-list(둘), 읽지 않아도 되는 키(created by·creation
     * date).
     */
    private fun torrent2() = TorrentCase(
        id = "multi-file",
        torrentBase64 = "ZDg6YW5ub3VuY2U0MDpodHRwOi8vdHJhY2tlci5leGFtcGxlLm9yZzo2OTY5L2Fubm91bmNlMTM6YW5ub3VuY2UtbGlzdGxsNDA6aHR0cDovL3RyYWNrZXIuZXhhbXBsZS5vcmc6Njk2OS9hbm5vdW5jZTM5OnVkcDovL3RyYWNrZXIuZXhhbXBsZS5vcmc6Njk2OS9hbm5vdW5jZWVsMzU6aHR0cHM6Ly9iYWNrdXAuZXhhbXBsZS5uZXQvYW5ub3VuY2VlZTEwOmNyZWF0ZWQgYnkzMTppcm9pcm8gdG9vbHMvdG9ycmVudF92ZWN0b3JzLnB5MTM6Y3JlYXRpb24gZGF0ZWkxNzYwMDAwMDAwZTQ6aW5mb2Q1OmZpbGVzbGQ2Omxlbmd0aGk0MDAwMGU0OnBhdGhsNTphLnR4dGVlZDY6bGVuZ3RoaTcwMDAwZTQ6cGF0aGwzOnN1YjQ6ZGVlcDU6Yi5iaW5lZWQ2Omxlbmd0aGkwZTQ6cGF0aGw5OmVtcHR5LmRhdGVlZDY6bGVuZ3RoaTI1MDAwZTQ6cGF0aGw1OmMuYmluZWVlNDpuYW1lNTptdWx0aTEyOnBpZWNlIGxlbmd0aGkzMjc2OGU2OnBpZWNlczEwMDoLIdX9S8I0q8Q+JpkfmK5efcXwuMAbtw3nNB0YfjA1jVqkuYva+XLrQNUoDEc7J0mRcGBAWbygwW6QCRzCszb69q0BGfipb6f3y+lVB0hrCstMXEUfnwAUqPglpZkHkggnb5tcZTg6dXJsLWxpc3RsMjk6aHR0cHM6Ly9zZWVkLmV4YW1wbGUuY29tL3B1Yi8zMjpodHRwOi8vbWlycm9yLmV4YW1wbGUubmV0L2ZpbGVzL2Vl",
        expect = "ok",
        infoHashV1Hex = "d306734f7cec28875278db20f9d77b0db89e5734",
        infoHashV2Hex = null,
        name = "multi",
        pieceLength = 32768,
        totalLength = 135000L,
        pieceHashesHex = "0b21d5fd4bc234abc43e26991f98ae5e7dc5f0b8c01bb70de7341d187e30358d5aa4b98bdaf972eb40d5280c473b27499170604059bca0c16e90091cc2b336faf6ad0119f8a96fa7f7cbe95507486b0acb4c5c451f9f0014a8f825a599079208276f9b5c",
        files = listOf(
            FileSpec(path = listOf("a.txt"), length = 40000L, seed = 21, pad = false, attr = ""),
            FileSpec(path = listOf("sub", "deep", "b.bin"), length = 70000L, seed = 22, pad = false, attr = ""),
            FileSpec(path = listOf("empty.dat"), length = 0L, seed = 23, pad = false, attr = ""),
            FileSpec(path = listOf("c.bin"), length = 25000L, seed = 24, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("http://tracker.example.org:6969/announce", "udp://tracker.example.org:6969/announce"), listOf("https://backup.example.net/announce")),
        webSeeds = listOf("https://seed.example.com/pub/", "http://mirror.example.net/files/"),
    )

    /**
     * pad-files: BEP 47 패드 — one.bin 20,000 뒤에 .pad/12768, two.bin 32,868 뒤에 .pad/16284 (내용은 0, 조각 해시에
     * 들어간다). 마지막 tools/run.sh 는 attr 'xh'(실행·숨김)와 파일 sha1 을 달았고 뒤에 패드가 없다. announce 는 udp 하나.
     */
    private fun torrent3() = TorrentCase(
        id = "pad-files",
        torrentBase64 = "ZDg6YW5ub3VuY2UzOTp1ZHA6Ly90cmFja2VyLmV4YW1wbGUub3JnOjY5NjkvYW5ub3VuY2U0OmluZm9kNTpmaWxlc2xkNjpsZW5ndGhpMjAwMDBlNDpwYXRobDc6b25lLmJpbmVlZDQ6YXR0cjE6cDY6bGVuZ3RoaTEyNzY4ZTQ6cGF0aGw0Oi5wYWQ1OjEyNzY4ZWVkNjpsZW5ndGhpMzI4NjhlNDpwYXRobDc6dHdvLmJpbmVlZDQ6YXR0cjE6cDY6bGVuZ3RoaTE2Mjg0ZTQ6cGF0aGw0Oi5wYWQ1OjE2Mjg0ZWVkNDphdHRyMjp4aDY6bGVuZ3RoaTUwMDBlNDpwYXRobDU6dG9vbHM2OnJ1bi5zaGU0OnNoYTEyMDrrqGbowZTWH9mENTwb1/fPbzGdQmVlNDpuYW1lNjpwYWRkZWQxMjpwaWVjZSBsZW5ndGhpMTYzODRlNjpwaWVjZXMxMjA6E2GEocSJpsTGm50gnhtmQJ8/DJp3VW42EYiYlYu9jdest/DQQ20JvRn9kegTdIK/e9ZBU3ZTPOKz9Ls0QNh47q2KnJsN1D6lWTKBwFEfaVKslW1IMQkLEq9/c0ERZD7CHVFRUuuoZujBlNYf2YQ1PBvX989vMZ1CZWU=",
        expect = "ok",
        infoHashV1Hex = "9af967c06591f0d1aac82af4b9e1e356b9202d15",
        infoHashV2Hex = null,
        name = "padded",
        pieceLength = 16384,
        totalLength = 86920L,
        pieceHashesHex = "136184a1c489a6c4c69b9d209e1b66409f3f0c9a77556e36118898958bbd8dd7acb7f0d0436d09bd19fd91e8137482bf7bd6415376533ce2b3f4bb3440d878eead8a9c9b0dd43ea5593281c0511f6952ac956d4831090b12af7f734111643ec21d515152eba866e8c194d61fd984353c1bd7f7cf6f319d42",
        files = listOf(
            FileSpec(path = listOf("one.bin"), length = 20000L, seed = 31, pad = false, attr = ""),
            FileSpec(path = listOf(".pad", "12768"), length = 12768L, seed = 0, pad = true, attr = "p"),
            FileSpec(path = listOf("two.bin"), length = 32868L, seed = 32, pad = false, attr = ""),
            FileSpec(path = listOf(".pad", "16284"), length = 16284L, seed = 0, pad = true, attr = "p"),
            FileSpec(path = listOf("tools", "run.sh"), length = 5000L, seed = 33, pad = false, attr = "xh"),
        ),
        trackers = listOf(listOf("udp://tracker.example.org:6969/announce")),
        webSeeds = emptyList(),
    )

    /**
     * hybrid-v1-v2: 하이브리드 v1+v2(BEP 52) — v1 으로 읽는다. info 에 meta version 2·file tree 와 v1 의 files·pieces 가
     * 함께 있고 맨 위에 piece layers. v1 files 는 file tree 를 걷는 차례(키의 바이트 차례)이고 마지막이 아닌 파일마다 패드로 조각 경계에 맞췄다(마지막
     * 파일 뒤에는 패드가 없다). 두 info-hash 는 같은 info 바이트의 SHA-1·SHA-256. 머클: 16 KiB 잎, 조각 32 KiB 라 조각 층은 잎 한 층 위 —
     * gamma.bin(잎 5→8)의 셋째 마디는 H(다섯째 잎 ‖ 0), 넷째(파일 끝 너머)는 층에서 빠진다. delta.txt 는 조각 하나라 piece layers 에 없다.
     */
    private fun torrent4() = TorrentCase(
        id = "hybrid-v1-v2",
        torrentBase64 = "ZDg6YW5ub3VuY2UzOTp1ZHA6Ly90cmFja2VyLmV4YW1wbGUub3JnOjY5NjkvYW5ub3VuY2U0OmluZm9kOTpmaWxlIHRyZWVkOTphbHBoYS5iaW5kMDpkNjpsZW5ndGhpNTAwMDBlMTE6cGllY2VzIHJvb3QzMjooUvEUPjPDiPQvF1BxDHonuceVtoyz4tMKPSSZkxZt3WVlNDpiZXRhZDk6Z2FtbWEuYmluZDA6ZDY6bGVuZ3RoaTcwMDAwZTExOnBpZWNlcyByb290MzI6284YWjY/JPGV9KB1eaIHXez5KOej/Po8aITvU6qAYHhlZWU5OmRlbHRhLnR4dGQwOmQ2Omxlbmd0aGkyMDAwMGUxMTpwaWVjZXMgcm9vdDMyOuj3DkN9MDdHMCUnhD2jFDfcTMF0POIpgWGrZqbOGa45ZWVlNTpmaWxlc2xkNjpsZW5ndGhpNTAwMDBlNDpwYXRobDk6YWxwaGEuYmluZWVkNDphdHRyMTpwNjpsZW5ndGhpMTU1MzZlNDpwYXRobDQ6LnBhZDU6MTU1MzZlZWQ2Omxlbmd0aGk3MDAwMGU0OnBhdGhsNDpiZXRhOTpnYW1tYS5iaW5lZWQ0OmF0dHIxOnA2Omxlbmd0aGkyODMwNGU0OnBhdGhsNDoucGFkNToyODMwNGVlZDY6bGVuZ3RoaTIwMDAwZTQ6cGF0aGw5OmRlbHRhLnR4dGVlZTEyOm1ldGEgdmVyc2lvbmkyZTQ6bmFtZTY6aHlicmlkMTI6cGllY2UgbGVuZ3RoaTMyNzY4ZTY6cGllY2VzMTIwOod6/06h7sOENoQzkkv2O9eD7EArZ5jVrneyHTzGhuitxsMPaEfqcABd0sry+CRyK2kyAEyQcr9DgudZRf2nKrbDovyI3PKKL+DGMbVYh2XjWZPL+5Y3IlB9+saldHiLwvFJeA7KvmevvaSW2s8WcXsmuGIjNfuaemUxMjpwaWVjZSBsYXllcnNkMzI6KFLxFD4zw4j0LxdQcQx6J7nHlbaMs+LTCj0kmZMWbd02NDoXvqZAjLGVBm8NeRYnJ5ce2RKotWzB6XXieJzTv/7w3F/qv46Hr2vmmLpo2kFLaWtsXGk7zPP0MWYbu2wEJgy5MzI6284YWjY/JPGV9KB1eaIHXez5KOej/Po8aITvU6qAYHg5Njo3+h8B+VD70rMvIjbxOI5bMDtIG/1pqDzH1tVUMDD518+6tHqmmU20PpTYFbL5NmPK+x46FqBN4yG3aWo/5vA0XSBfoH5sbx1w4sll9h8NNx8zB6GbZQg/o/Z6hB9Y8P5lZQ==",
        expect = "hybrid",
        infoHashV1Hex = "d834768f5359c972a1c9c4cbfedd84c815a8d19f",
        infoHashV2Hex = "b0138207ef952dc53d9a11da7fb55df703c7c5d44a5d13f43dd64efd5549a231",
        name = "hybrid",
        pieceLength = 32768,
        totalLength = 183840L,
        pieceHashesHex = "877aff4ea1eec384368433924bf63bd783ec402b6798d5ae77b21d3cc686e8adc6c30f6847ea70005dd2caf2f824722b6932004c9072bf4382e75945fda72ab6c3a2fc88dcf28a2fe0c631b5588765e35993cbfb963722507dfac6a574788bc2f149780ecabe67afbda496dacf16717b26b8622335fb9a7a",
        files = listOf(
            FileSpec(path = listOf("alpha.bin"), length = 50000L, seed = 41, pad = false, attr = ""),
            FileSpec(path = listOf(".pad", "15536"), length = 15536L, seed = 0, pad = true, attr = "p"),
            FileSpec(path = listOf("beta", "gamma.bin"), length = 70000L, seed = 42, pad = false, attr = ""),
            FileSpec(path = listOf(".pad", "28304"), length = 28304L, seed = 0, pad = true, attr = "p"),
            FileSpec(path = listOf("delta.txt"), length = 20000L, seed = 43, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("udp://tracker.example.org:6969/announce")),
        webSeeds = emptyList(),
    )

    /**
     * v2-only: 순수 v2(BEP 52 — pieces·files·length 없음) — 거부한다. infoHashV1Hex 는 정의대로 info 바이트의 SHA-1 을
     * 적었다(v2 군집은 그 값이 아니라 SHA-256 의 앞 20 바이트를 쓴다). files 는 file tree 의 차례, 패드 없음. 조각 64 KiB 라 조각 층은 잎 두 층
     * 위 — x.bin(잎 10→16)의 조각 층은 마디 셋(넷째는 merkle_pad 라 빠진다).
     */
    private fun torrent5() = TorrentCase(
        id = "v2-only",
        torrentBase64 = "ZDQ6aW5mb2Q5OmZpbGUgdHJlZWQ1OnguYmluZDA6ZDY6bGVuZ3RoaTE1MDAwMGUxMTpwaWVjZXMgcm9vdDMyOnLk56VQd/+99wr9XpxxUCJW/lwvSK7PmFgzu4GMlzWvZWUxOnlkNTp6LmJpbmQwOmQ2Omxlbmd0aGk2MDAwMGUxMTpwaWVjZXMgcm9vdDMyOpPitUdzTL1uL2g77q6d0dXuPSMZpI9bfZAYZfj7E1RKZWVlZTEyOm1ldGEgdmVyc2lvbmkyZTQ6bmFtZTY6djJvbmx5MTI6cGllY2UgbGVuZ3RoaTY1NTM2ZWUxMjpwaWVjZSBsYXllcnNkMzI6cuTnpVB3/733Cv1enHFQIlb+XC9Irs+YWDO7gYyXNa85NjoLxMMDcN1YohZOlNunz0bpiyLNKa+zQdJsybXD1iqoIaf2oJuWJdj1qv04gsyoYPqamoM2v9IYMYHqtHc+TYH6E3j2mrwbquGvWqjjIvkE+AmSljOux1Atsw0lb33JHGJlZQ==",
        expect = "v2-only",
        infoHashV1Hex = "131a92bb1ef8fec683ac1caefe7f7f3d9e378c17",
        infoHashV2Hex = "e887b838da1a61f4b3cc0c91cbdcb6cc007162319875d1f2e8f0b6bb4b406739",
        name = "v2only",
        pieceLength = 65536,
        totalLength = 210000L,
        pieceHashesHex = "",
        files = listOf(
            FileSpec(path = listOf("x.bin"), length = 150000L, seed = 51, pad = false, attr = ""),
            FileSpec(path = listOf("y", "z.bin"), length = 60000L, seed = 52, pad = false, attr = ""),
        ),
        trackers = emptyList(),
        webSeeds = emptyList(),
    )

    /** private: info 의 private = 1 — 거부한다(초대제 트래커). 그런 토렌트에 흔한 source 키도 info 에 있다. */
    private fun torrent6() = TorrentCase(
        id = "private",
        torrentBase64 = "ZDg6YW5ub3VuY2UzNjpodHRwczovL3ByaXZhdGUuZXhhbXBsZS5vcmcvYW5ub3VuY2U0OmluZm9kNjpsZW5ndGhpMzAwMDBlNDpuYW1lMTE6cHJpdmF0ZS5iaW4xMjpwaWVjZSBsZW5ndGhpMTYzODRlNjpwaWVjZXM0MDrq0/v5NAwheOmk7sAHFwpyyCMyzPZg07HOKpf4qtIfj35d5pgrfDVLNzpwcml2YXRlaTFlNjpzb3VyY2UxMTpJUk9JUk8tVEVTVGVl",
        expect = "private",
        infoHashV1Hex = "55411c0df9259f4598cc68dd469f7c5ed0c3095f",
        infoHashV2Hex = null,
        name = "private.bin",
        pieceLength = 16384,
        totalLength = 30000L,
        pieceHashesHex = "ead3fbf9340c2178e9a4eec007170a72c82332ccf660d3b1ce2a97f8aad21f8f7e5de6982b7c354b",
        files = listOf(
            FileSpec(path = listOf("private.bin"), length = 30000L, seed = 61, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("https://private.example.org/announce")),
        webSeeds = emptyList(),
    )

    /**
     * unsorted-info-keys: info 사전의 키가 정렬되지 않았다(name, piece length, pieces, length 차례 — 손으로 짠 바이트).
     * info-hash 는 원래 바이트의 SHA-1 이다 — 정렬해 다시 쓴 info 의 SHA-1(57ddff7052d3032a7568e128cf9a29c1b432ba80)이 아니다.
     * 트래커 없음(빈 목록).
     */
    private fun torrent7() = TorrentCase(
        id = "unsorted-info-keys",
        torrentBase64 = "ZDc6Y29tbWVudDI0OmluZm8ga2V5cyBhcmUgbm90IHNvcnRlZDQ6aW5mb2Q0Om5hbWUxMjp1bnNvcnRlZC5iaW4xMjpwaWVjZSBsZW5ndGhpMTYzODRlNjpwaWVjZXM0MDqCmyVVTCAYoDkDWqbg/0hCtiu8tuK2H7gnWyupp3ieVajwm7K1yBHUNjpsZW5ndGhpMjAwMDBlZWU=",
        expect = "ok",
        infoHashV1Hex = "6f42da8a78b2fd60775d2c03fca611ec0000ce23",
        infoHashV2Hex = null,
        name = "unsorted.bin",
        pieceLength = 16384,
        totalLength = 20000L,
        pieceHashesHex = "829b25554c2018a039035aa6e0ff4842b62bbcb6e2b61fb8275b2ba9a7789e55a8f09bb2b5c811d4",
        files = listOf(
            FileSpec(path = listOf("unsorted.bin"), length = 20000L, seed = 71, pad = false, attr = ""),
        ),
        trackers = emptyList(),
        webSeeds = emptyList(),
    )

    /**
     * utf8-keys: name·path 에는 CP949 바이트(UTF-8 로 읽히지 않는다), name.utf-8·path.utf-8 에는 UTF-8 — 기대값은 .utf-8
     * 쪽이다. 옛 한국어 클라이언트가 이렇게 썼다. 빈칸 든 이름(노래 01.bin)도 있다.
     */
    private fun torrent8() = TorrentCase(
        id = "utf8-keys",
        torrentBase64 = "ZDg6YW5ub3VuY2U0MDpodHRwOi8vdHJhY2tlci5leGFtcGxlLm9yZzo2OTY5L2Fubm91bmNlNDppbmZvZDU6ZmlsZXNsZDY6bGVuZ3RoaTMwMDAwZTQ6cGF0aGw0OrmuvK04Or7Is7sudHh0ZTEwOnBhdGgudXRmLThsNjrrrLjshJwxMDrslYjrgrQudHh0ZWVkNjpsZW5ndGhpNDUwMDBlNDpwYXRobDg6u+fB+C5iaW5lMTA6cGF0aC51dGYtOGwxMDrsgqzsp4QuYmluZWVkNjpsZW5ndGhpMTIzNDVlNDpwYXRobDQ6wL2+xzExOrPrt6EgMDEuYmluZTEwOnBhdGgudXRmLThsNjrsnYzslYUxMzrrhbjrnpggMDEuYmluZWVlNDpuYW1lMTE6x9Gxub7uIMDat+ExMDpuYW1lLnV0Zi04MTY67ZWc6rWt7Ja0IOyekOujjDEyOnBpZWNlIGxlbmd0aGkxNjM4NGU2OnBpZWNlczEyMDq1U9pV1OWDk56Ot7IE525LwqJ3bcEUyahBu4mCqdiWRkIeo6zeza8d70OovlzodA0ZbmPpvG8l0Az+DTwQP1snFce1/aJbIY7nb39JnDF1vbJ59EZaSHX6JrTunw/FsHI5jNFEaItOGd37H8LHbdrxFs5y0xMyzodlZQ==",
        expect = "ok",
        infoHashV1Hex = "62a151970df5d5b9bfed8651b3f52b04911c75ef",
        infoHashV2Hex = null,
        name = "한국어 자료",
        pieceLength = 16384,
        totalLength = 87345L,
        pieceHashesHex = "b553da55d4e583939e8eb7b204e76e4bc2a2776dc114c9a841bb8982a9d89646421ea3acdecdaf1def43a8be5ce8740d196e63e9bc6f25d00cfe0d3c103f5b2715c7b5fda25b218ee76f7f499c3175bdb279f4465a4875fa26b4ee9f0fc5b072398cd144688b4e19ddfb1fc2c76ddaf116ce72d31332ce87",
        files = listOf(
            FileSpec(path = listOf("문서", "안내.txt"), length = 30000L, seed = 81, pad = false, attr = ""),
            FileSpec(path = listOf("사진.bin"), length = 45000L, seed = 82, pad = false, attr = ""),
            FileSpec(path = listOf("음악", "노래 01.bin"), length = 12345L, seed = 83, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("http://tracker.example.org:6969/announce")),
        webSeeds = emptyList(),
    )

    /**
     * tracker-forms: 트래커의 꼴 — announce-list 가 있으면 announce 는 쓰지 않는다(BEP 12 — 여기의 announce 는 목록에 없는 주소다).
     * url-list 가 목록이 아니라 문자열 하나다. 길이가 조각의 꼭 두 배라 마지막 조각도 꽉 찬다.
     */
    private fun torrent9() = TorrentCase(
        id = "tracker-forms",
        torrentBase64 = "ZDg6YW5ub3VuY2UzMTpodHRwOi8vb2xkLmV4YW1wbGUub3JnL2Fubm91bmNlMTM6YW5ub3VuY2UtbGlzdGxsMzU6dWRwOi8vbmV3LmV4YW1wbGUub3JnOjY5NjkvYW5ub3VuY2UzMTpodHRwOi8vbmV3LmV4YW1wbGUub3JnL2Fubm91bmNlZWwzMjpodHRwczovL25ldy5leGFtcGxlLm5ldC9hbm5vdW5jZWVlNDppbmZvZDY6bGVuZ3RoaTMyNzY4ZTQ6bmFtZTg6ZWRnZS5iaW4xMjpwaWVjZSBsZW5ndGhpMTYzODRlNjpwaWVjZXM0MDpuZcwvM7KB5o9Lh0gJL2c5hIcRRDv86TXrm6TYwfUgfGl4Ti/n6LahZTg6dXJsLWxpc3QzMzpodHRwczovL3NlZWQuZXhhbXBsZS5vcmcvZWRnZS5iaW5l",
        expect = "ok",
        infoHashV1Hex = "0ab8a2e03aec761e1693478d32b15fa8fc0b3be1",
        infoHashV2Hex = null,
        name = "edge.bin",
        pieceLength = 16384,
        totalLength = 32768L,
        pieceHashesHex = "6e65cc2f33b281e68f4b8748092f6739848711443bfce935eb9ba4d8c1f5207c69784e2fe7e8b6a1",
        files = listOf(
            FileSpec(path = listOf("edge.bin"), length = 32768L, seed = 91, pad = false, attr = ""),
        ),
        trackers = listOf(listOf("udp://new.example.org:6969/announce", "http://new.example.org/announce"), listOf("https://new.example.net/announce")),
        webSeeds = listOf("https://seed.example.org/edge.bin"),
    )

    private fun magnetCases() = listOf(
        // btih 를 hex 40 자, 대소문자를 섞어 — dn 없음(name null)
        MagnetCase("magnet:?xt=urn:btih:D306734f7CeC28875278dB20f9D77b0Db89E5734", "ok", "d306734f7cec28875278db20f9d77b0db89e5734", null, emptyList(), emptyList(), emptyList()),
        // btih 를 base32 32 자(RFC 4648, 대문자) + dn
        MagnetCase("magnet:?xt=urn:btih:L3PPDTVHXE3ZKFH4JH7ZVGE6LI2Z7BKB&dn=single.bin", "ok", "5edef1cea7b9379514fc49ff9a989e5a359f8541", "single.bin", emptyList(), emptyList(), emptyList()),
        // dn 을 UTF-8 퍼센트 인코딩(한국어와 빈칸 %20 — '+' 는 쓰지 않았다)
        MagnetCase("magnet:?xt=urn:btih:62a151970df5d5b9bfed8651b3f52b04911c75ef&dn=%ED%95%9C%EA%B5%AD%EC%96%B4%20%EC%9E%90%EB%A3%8C", "ok", "62a151970df5d5b9bfed8651b3f52b04911c75ef", "한국어 자료", emptyList(), emptyList(), emptyList()),
        // tr 셋(udp·http·https) — 첫째·셋째는 인코딩, 둘째는 날것(질의에 ':'·'/' 는 그대로 써도 된다). 셋째는 안에 '?'·'&' 가 %3F·%26 으로 들어
        // 있다 — '&' 로 먼저 가른 뒤 한 번만 푼다
        MagnetCase("magnet:?xt=urn:btih:0ab8a2e03aec761e1693478d32b15fa8fc0b3be1&tr=udp%3A%2F%2Ftracker.example.org%3A6969%2Fannounce&tr=http://tracker.example.org:6969/announce&tr=https%3A%2F%2Ftracker.example.com%2Fannounce%3Fpasskey%3Dabc%26x%3D1", "ok", "0ab8a2e03aec761e1693478d32b15fa8fc0b3be1", null, listOf("udp://tracker.example.org:6969/announce", "http://tracker.example.org:6969/announce", "https://tracker.example.com/announce?passkey=abc&x=1"), emptyList(), emptyList()),
        // ws 둘(BEP 19 의 웹 시드)
        MagnetCase("magnet:?xt=urn:btih:5edef1cea7b9379514fc49ff9a989e5a359f8541&ws=https%3A%2F%2Fseed.example.org%2Fsingle.bin&ws=http%3A%2F%2Fmirror.example.net%2Ffiles%2Fsingle.bin", "ok", "5edef1cea7b9379514fc49ff9a989e5a359f8541", null, emptyList(), listOf("https://seed.example.org/single.bin", "http://mirror.example.net/files/single.bin"), emptyList()),
        // x.pe 둘 — IPv4:port(날것)와 [IPv6]:port(인코딩, 기대값은 대괄호 꼴)
        MagnetCase("magnet:?xt=urn:btih:d306734f7cec28875278db20f9d77b0db89e5734&x.pe=203.0.113.5:6881&x.pe=%5B2001%3Adb8%3A%3A1%5D%3A51413", "ok", "d306734f7cec28875278db20f9d77b0db89e5734", null, emptyList(), emptyList(), listOf("203.0.113.5:6881", "[2001:db8::1]:51413")),
        // btmh 만(순수 v2) — 받지 않는다
        MagnetCase("magnet:?xt=urn:btmh:1220e887b838da1a61f4b3cc0c91cbdcb6cc007162319875d1f2e8f0b6bb4b406739&dn=v2only", "v2-only", null, null, emptyList(), emptyList(), emptyList()),
        // btih 와 btmh 둘 다(하이브리드) — btih 를 쓴다
        MagnetCase("magnet:?xt=urn:btih:d834768f5359c972a1c9c4cbfedd84c815a8d19f&xt=urn:btmh:1220b0138207ef952dc53d9a11da7fb55df703c7c5d44a5d13f43dd64efd5549a231&dn=hybrid", "ok", "d834768f5359c972a1c9c4cbfedd84c815a8d19f", "hybrid", emptyList(), emptyList(), emptyList()),
        // btmh 가 앞, btih 가 뒤 — 그래도 btih 를 쓴다(첫 xt 만 보면 틀린다)
        MagnetCase("magnet:?xt=urn:btmh:1220b0138207ef952dc53d9a11da7fb55df703c7c5d44a5d13f43dd64efd5549a231&xt=urn:btih:d834768f5359c972a1c9c4cbfedd84c815a8d19f&dn=hybrid", "ok", "d834768f5359c972a1c9c4cbfedd84c815a8d19f", "hybrid", emptyList(), emptyList(), emptyList()),
        // xt 가 없다
        MagnetCase("magnet:?dn=no-xt&tr=http%3A%2F%2Ftracker.example.org%3A6969%2Fannounce", "invalid", null, null, emptyList(), emptyList(), emptyList()),
        // btih 의 hex 가 39 자
        MagnetCase("magnet:?xt=urn:btih:d306734f7cec28875278db20f9d77b0db89e573", "invalid", null, null, emptyList(), emptyList(), emptyList()),
        // btih 가 40 자인데 hex 가 아닌 글자(g)
        MagnetCase("magnet:?xt=urn:btih:d306734f7cec28875278db20f9d77b0db89e573g", "invalid", null, null, emptyList(), emptyList(), emptyList()),
        // 모두 — 소문자 hex, dn 의 퍼센트 인코딩이 소문자 hex(%ed…), 모르는 키 xl(정확한 길이)은 지나간다
        MagnetCase("magnet:?xt=urn:btih:62a151970df5d5b9bfed8651b3f52b04911c75ef&dn=%ed%95%9c%ea%b5%ad%ec%96%b4%20%ec%9e%90%eb%a3%8c&xl=87345&tr=udp%3A%2F%2Ftracker.example.org%3A6969%2Fannounce&ws=https%3A%2F%2Fseed.example.org%2Fsingle.bin&x.pe=198.51.100.20:51413", "ok", "62a151970df5d5b9bfed8651b3f52b04911c75ef", "한국어 자료", listOf("udp://tracker.example.org:6969/announce"), listOf("https://seed.example.org/single.bin"), listOf("198.51.100.20:51413")),
    )

    private fun rc4Cases() = listOf(
        // 위키백과의 시험 벡터 — 키 'Key', 평문 'Plaintext'
        Rc4Case("4b6579", 0, "506c61696e74657874", "bbf316e8d940af0ad3"),
        // 위키백과의 시험 벡터 — 키 'Wiki', 평문 'pedia'
        Rc4Case("57696b69", 0, "7065646961", "1021bf0420"),
        // 위키백과의 시험 벡터 — 키 'Secret', 평문 'Attack at dawn'
        Rc4Case("536563726574", 0, "41747461636b206174206461776e", "45a01f645fc35b383552544b9bf5"),
        // RFC 6229 의 40 비트 키 0102030405 — 평문 0 이라 암호문이 곧 키 흐름(오프셋 0)
        Rc4Case("0102030405", 0, "0000000000000000000000000000000000000000000000000000000000000000", "b2396305f03dc027ccc3524a0a1118a86982944f18fc82d589c403a47a0d0919"),
        // RFC 6229 의 40 비트 키 0102030405 — 평문 0 이라 암호문이 곧 키 흐름(오프셋 1024)
        Rc4Case("0102030405", 1024, "00000000000000000000000000000000", "30abbcc7c20b01609f23ee2d5f6bb7df"),
        // SHA 로 지은 20 바이트 키, MSE 처럼 앞 1024 바이트를 버린다
        Rc4Case("cbb8aa07af02ce96b45a21432b14cfbe2bebcbfb", 1024, "9fe909f3b80861e52be05dcd4123b6366605048f22c6da1fbf10021f3acd091fd24595984a22255ddb4389ba399e3cdc8f5ec6bafdfc7feeaca09642fe941fa4d9b4f5fef9735ee0327cda8cab1785a55040fc7b3f3271d7f64a0ef6d1283c6fa3946ec2", "47a8c5c9432314026802e7f5060eec8f6c4dd49809d77aaf412974c648ba8704b5273c59eca70afb14ecf7981644bbb5ea5855eed2f019ad0d47eb5f40b6b441d78f1a8f16af24a6bedb5cacdd3c4c8aad31c67c8d156ff13bb6bc51fbf0e0674ee1dea7"),
        // SHA 로 지은 7 바이트 키, MSE 처럼 앞 1024 바이트를 버린다
        Rc4Case("5237cc14182eda", 1024, "89c8682b6e9d04fb72966135ab9a678013495a4d22a346f28aae1ed54afdbf7c73bcd6ab1a65cd32c8e037947285c51b186c94c2f374ce38bd614d165fa73edc9d5d99083d02f9f99b80d348a01cbf59d8f6f9f43c1c0a15e39335b740789793572ed3a2", "2847d73df3474c03786781e76cf0dfccd6cc9b3f42d267bfe047e28cc5ef14a60cd1ad8e383f6a1c6e4f65dede5179d37be1842c98db8c631bdacfbbb340768c54ce3b06dd6836c51822bda332eda16ecb6f3f54565857d119653744e44ddf1b33d4f113"),
    )

    /**
     * provide 0x02·select 0x02(RC4). PadA 37·PadB 0·PadC 0·PadD 0. IA 는 A 의 핸드셰이크 68 바이트(reserved[5] =
     * 0x10, reserved[7] = 0x04, peer id '-XX0000-' + 12). 그 뒤 A 는 have_none·interested·request(0, 0,
     * 16384), B 는 제 핸드셰이크·have_all·unchoke 를 RC4 를 이어 보낸다. Ya 의 맨 앞 바이트가 0 이고(96 바이트를 채우지 않으면 95), S 의 맨 앞
     * 비트가 1 이다(BigInteger.toByteArray 는 부호 바이트를 붙여 97 바이트를 낸다).
     */
    private fun mse1() = MseCase(
        infoHashHex = "d306734f7cec28875278db20f9d77b0db89e5734",
        xaHex = "6135f7745adbbbc590a0f6b41abcbcff5945003d",
        xbHex = "cfe76482c5a87bdba031cca7397a8974a8b3868d",
        padAHex = "57d3e5e3a0b4ac0ae78eb7425fefbc666a44c78c40a0772909f20a9bd4f3c5c476fa3c5c29",
        padBHex = "",
        cryptoProvide = 2,
        cryptoSelect = 2,
        padCHex = "",
        padDHex = "",
        iaHex = "13426974546f7272656e742070726f746f636f6c0000000000100004d306734f7cec28875278db20f9d77b0db89e57342d5858303030302d363339643765326338373531",
        yaHex = "0075e7a5ec0d5c36cc12d23a3b58a340dec3cf1139ad64da9e18797e9386c7fc00225ef53a51a740b3366f57ca71515f6ba5350151ff51bde6180e75637f874cacf90b89baaf2e6e16b68120d0eaadc08078979c1f4d34c6516cdabeb6e6e7df",
        ybHex = "35031a89ac85d1c011e7fcf893b30cda714e765b20fec0f43c0403c40cdb757faa8ad81495880af738406e2cbd555ec711f11ec110673e115594f8c807bd93631a541c8b86ed377f793489f798b7876dd62171320504297674e474a346d117a4",
        sHex = "c1f6b935a040ad9c894d6c519cc27ee3e0c0669744730e74cb72d088e935926b3faf9519033fd3740edc55b63bd38631e64a86ab54fe7ed0f96e96768dd44a54c3928049e9439164f4ca72cd0118cb06d4f7c7eebc15184f2b58b0869e784752",
        req1Hex = "1be4ec50357ecdb632603f49a314177e855cdfc8",
        req2xor3Hex = "3a835f13c36a733d1953c1e85b78ea51d5b49437",
        keyAHex = "1a7d0933b6f0fdff951fa1f3057d35c861cde62c",
        keyBHex = "f43f12d6447a62dde51976f6483e88b067e03aab",
        message1Hex = "0075e7a5ec0d5c36cc12d23a3b58a340dec3cf1139ad64da9e18797e9386c7fc00225ef53a51a740b3366f57ca71515f6ba5350151ff51bde6180e75637f874cacf90b89baaf2e6e16b68120d0eaadc08078979c1f4d34c6516cdabeb6e6e7df57d3e5e3a0b4ac0ae78eb7425fefbc666a44c78c40a0772909f20a9bd4f3c5c476fa3c5c29",
        message2Hex = "35031a89ac85d1c011e7fcf893b30cda714e765b20fec0f43c0403c40cdb757faa8ad81495880af738406e2cbd555ec711f11ec110673e115594f8c807bd93631a541c8b86ed377f793489f798b7876dd62171320504297674e474a346d117a4",
        message3Hex = "1be4ec50357ecdb632603f49a314177e855cdfc83a835f13c36a733d1953c1e85b78ea51d5b49437e627e97c6d4b99fbdd4fe1a60399881a3aba4e7be0ddcb237f7003a40eeb561ce1013dcc6b712e3ba5a82bce26d52de1e6b0aea045eeefac53897969f7f966243e0e27f080696ffcc9aaebbea85d9416f343f775",
        message4Hex = "0dbb6be82b6a85471395ec962da2",
        afterAPlainHex = "000000010f00000001020000000d06000000000000000000004000",
        afterACipherHex = "4fed61d680e13152bee4b436dbd0a6dccec0652354500c4b8e5579",
        afterBPlainHex = "13426974546f7272656e742070726f746f636f6c0000000000100005d306734f7cec28875278db20f9d77b0db89e57342d5a5a303130302d646165373464656465613537000000010e0000000101",
        afterBCipherHex = "6ab8ba87acb4dfb20cfdf65488c49eab4b921825d078fcca9279068c7611af400d1fdec7ef48b323f9998452c00a9bafdcd042a535ae4de58421ea6decec9b1d3e3f4ac82e06fab1555c99a14d46",
    )

    /**
     * provide 0x03·select 0x01(평문). PadA 512·PadB 300·PadC 4(0)·PadD 0, IA 없음. 그 뒤의 바이트는 암호화하지 않는다 — A 는 제
     * 핸드셰이크, B 는 제 핸드셰이크·bitfield(조각 7 모두)·unchoke. S 의 맨 앞 바이트가 0 이다.
     */
    private fun mse2() = MseCase(
        infoHashHex = "5edef1cea7b9379514fc49ff9a989e5a359f8541",
        xaHex = "f817eb8012553b98c837007ebe0b0acec8543a8d",
        xbHex = "230b8601c819314759ef773720347cec3b2004af",
        padAHex = "6751363f5489726138fc44ad48a20a44c0bcadd8bed8d55fa43e06c01ccefbab0ab3878dc6118a17d96d2fe5ea40778c924793510a066ee5592889067bdda52fe4a8026621d976e98394574f8a17862b3a3f907612a219dbeb8a5efa17a0f7da6cb5b27e3233fd41560d0ee95d448ae53c72a5307edc9d27728d9ed8099c101cf66d04b65b979f41586dd24db03c6033092665e3a783effe111213b213472824e33c56fdc5afd1b7f573357825e713a32342aa3d624117819d44dee53f486d3dab1b1c2a7fa9039e18e7588d07d74a834d03dbee9246947b3fc4581a5d7db8eada673aae09531557309fa83e59a57479cc8b2b2466302d9f253ced4eb715c49dd3aebaf94171f0e701f8b23619443c29f94a4761052cf39d90e82457a7621101832efd2a879c15136b445b3982163af2fb4c0405aa450eda9d1cab4557878c14ed08b837ed3268919dd5345998f7141164c76bf8c82fd08d349f49ae933384db89e9a284a737f7d06c2e58653fca8291a6c746da90b9df232c4b80a3bdfb8de4b11e44684aed858216c5e774ab29857b73c8745930664cf801e816082c6091d839caf69dbf82f719d7daa3189caf1591b5d01166604cdaa1034d335a31c95c34b081a1dd6d8bdc3bcb5006a84257f1287d00e44ee11294abe76439f9c1b975ad9dcd7e26d9335a2e6d63a514f99a9da8d768d79a18b5eedb349a0f6098dbeddf",
        padBHex = "a0ce54854a87e6c830e17153da1f473d0f414fbd0963a2353395481a53e8f63dc86301f47a0475d8a845b86a096fac815e32d6ec7cc1e5771496ee99413b47a1a5b185a19f0b2313126b06e1217bd9a3a77d2ccca3e8b3c65b2d0c0c70a60c6e03afecae73f2fa12091a305c1bdb1aee8c521dfd33582ad06f939e3fa870a6a3b07ea851fcd6bbfb11aaf0b6322c400d15ab571779e47142108c6258effebaa9b49518157338db391aeceb08d7ed661685a5eae429439769d8714dfae191f27c42dd948b6ad6510bc68e461ec8919d0a5b080064bd375aee2b4fc9c892b3a958cc67972ca6aba41555622ac6236d63490436e8267f84fde03756939615a8967bf9da097b08f6a4c07b157ca8862c90839e13ccbc49b3bf6bf304139b8103310fa8edcc29ccff4ecc77b5e866",
        cryptoProvide = 3,
        cryptoSelect = 1,
        padCHex = "00000000",
        padDHex = "",
        iaHex = "",
        yaHex = "4968a2df094e774cddfbf7ec8f1af5bb99cdc3953e45401841897c693efa07c256bc7f63590562d1266878313bdac4a2a26cee43352ef858f1b5320a7d53feb2a7f90d70a15ec50dcc738922c86146c6865f8fff19a52d24f6a6223d8307849a",
        ybHex = "b253dd05b48e9ae49787e165e33cd7267b177a01f271a0d83ef1eac3774f8821b842818440874684944de9956fd0c94b54a6067d261d95ab220c6416289383d5021a2eeb52e429056fca4378c16bbf958a59093c87ff8183ab565ca123335ac7",
        sHex = "006028af9d9706dc1e220574c63425cfa860ce3abc49f35bb83b5c9d0f00dc2ac1775b50bf87ea60f8bf1404cee2659582cff1c4389598a01392a3fefaa1e2efab23a7d51bda61ca587a8908ebae22dcdd03a9dfd028891cf38e04461fba0d75",
        req1Hex = "c9e2b0cb2552852ccc3719283d10761366a2251f",
        req2xor3Hex = "31c1601884bc1b9f0a636e7e43d096bca84facd2",
        keyAHex = "7b5c1dc18258616db02852da95a1409c29207be0",
        keyBHex = "e1e08b2cc219fbf526c3bdccab2a42ab696c0370",
        message1Hex = "4968a2df094e774cddfbf7ec8f1af5bb99cdc3953e45401841897c693efa07c256bc7f63590562d1266878313bdac4a2a26cee43352ef858f1b5320a7d53feb2a7f90d70a15ec50dcc738922c86146c6865f8fff19a52d24f6a6223d8307849a6751363f5489726138fc44ad48a20a44c0bcadd8bed8d55fa43e06c01ccefbab0ab3878dc6118a17d96d2fe5ea40778c924793510a066ee5592889067bdda52fe4a8026621d976e98394574f8a17862b3a3f907612a219dbeb8a5efa17a0f7da6cb5b27e3233fd41560d0ee95d448ae53c72a5307edc9d27728d9ed8099c101cf66d04b65b979f41586dd24db03c6033092665e3a783effe111213b213472824e33c56fdc5afd1b7f573357825e713a32342aa3d624117819d44dee53f486d3dab1b1c2a7fa9039e18e7588d07d74a834d03dbee9246947b3fc4581a5d7db8eada673aae09531557309fa83e59a57479cc8b2b2466302d9f253ced4eb715c49dd3aebaf94171f0e701f8b23619443c29f94a4761052cf39d90e82457a7621101832efd2a879c15136b445b3982163af2fb4c0405aa450eda9d1cab4557878c14ed08b837ed3268919dd5345998f7141164c76bf8c82fd08d349f49ae933384db89e9a284a737f7d06c2e58653fca8291a6c746da90b9df232c4b80a3bdfb8de4b11e44684aed858216c5e774ab29857b73c8745930664cf801e816082c6091d839caf69dbf82f719d7daa3189caf1591b5d01166604cdaa1034d335a31c95c34b081a1dd6d8bdc3bcb5006a84257f1287d00e44ee11294abe76439f9c1b975ad9dcd7e26d9335a2e6d63a514f99a9da8d768d79a18b5eedb349a0f6098dbeddf",
        message2Hex = "b253dd05b48e9ae49787e165e33cd7267b177a01f271a0d83ef1eac3774f8821b842818440874684944de9956fd0c94b54a6067d261d95ab220c6416289383d5021a2eeb52e429056fca4378c16bbf958a59093c87ff8183ab565ca123335ac7a0ce54854a87e6c830e17153da1f473d0f414fbd0963a2353395481a53e8f63dc86301f47a0475d8a845b86a096fac815e32d6ec7cc1e5771496ee99413b47a1a5b185a19f0b2313126b06e1217bd9a3a77d2ccca3e8b3c65b2d0c0c70a60c6e03afecae73f2fa12091a305c1bdb1aee8c521dfd33582ad06f939e3fa870a6a3b07ea851fcd6bbfb11aaf0b6322c400d15ab571779e47142108c6258effebaa9b49518157338db391aeceb08d7ed661685a5eae429439769d8714dfae191f27c42dd948b6ad6510bc68e461ec8919d0a5b080064bd375aee2b4fc9c892b3a958cc67972ca6aba41555622ac6236d63490436e8267f84fde03756939615a8967bf9da097b08f6a4c07b157ca8862c90839e13ccbc49b3bf6bf304139b8103310fa8edcc29ccff4ecc77b5e866",
        message3Hex = "c9e2b0cb2552852ccc3719283d10761366a2251f31c1601884bc1b9f0a636e7e43d096bca84facd24929bdaa4118cc68c38b289cfe4bdb691e85197e",
        message4Hex = "4ebc7d51c43538443f45ad7cbafc",
        afterAPlainHex = "13426974546f7272656e742070726f746f636f6c00000000001000045edef1cea7b9379514fc49ff9a989e5a359f85412d5858303030302d363339643765326338373531",
        afterACipherHex = "13426974546f7272656e742070726f746f636f6c00000000001000045edef1cea7b9379514fc49ff9a989e5a359f85412d5858303030302d363339643765326338373531",
        afterBPlainHex = "13426974546f7272656e742070726f746f636f6c00000000001000055edef1cea7b9379514fc49ff9a989e5a359f85412d5a5a303130302d6461653734646564656135370000000205fe0000000101",
        afterBCipherHex = "13426974546f7272656e742070726f746f636f6c00000000001000055edef1cea7b9379514fc49ff9a989e5a359f85412d5a5a303130302d6461653734646564656135370000000205fe0000000101",
    )

    private fun bencodeCases() = listOf(
        BencodeCase("693065", true, "정수 0"),
        BencodeCase("692d3565", true, "음수"),
        BencodeCase("693932323333373230333638353437373538303765", true, "Long.MAX_VALUE(19자리)"),
        BencodeCase("692d3932323333373230333638353437373538303865", true, "Long.MIN_VALUE"),
        BencodeCase("303a", true, "빈 문자열"),
        BencodeCase("6c65", true, "빈 목록"),
        BencodeCase("6465", true, "빈 사전"),
        BencodeCase("64343a6469637464333a6b65796c313a616931656565343a6c6973746c343a7370616d693432656c6564656565", true, "중첩 — 사전 안의 사전·목록, 목록 안의 빈 목록·빈 사전"),
        BencodeCase("31303a30313233343536373839", true, "길이가 두 자리인 문자열"),
        BencodeCase("333a00ff80", true, "이진 바이트 문자열(UTF-8 이 아니다)"),
        BencodeCase("64303a69316565", true, "빈 키 — v2 의 file tree 가 쓴다"),
        BencodeCase("64313a5a693165313a6169326565", true, "키는 원래 바이트로 정렬된다 — 'Z'(0x5A) 가 'a'(0x61) 앞"),
        BencodeCase("64313a62693165313a6169326565", true, "정렬되지 않은 키 — 받아들인다(실제 토렌트에 있다, 토렌트 unsorted-info-keys)"),
        BencodeCase("6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6565656565656565656565656565656565656565656565656565656565656565", true, "목록 32 겹"),
        BencodeCase("69303365", false, "정수의 선행 0"),
        BencodeCase("30333a616263", false, "길이의 선행 0"),
        BencodeCase("692d3065", false, "-0"),
        BencodeCase("6965", false, "빈 정수"),
        BencodeCase("692d65", false, "부호뿐인 정수"),
        BencodeCase("69313030303030303030303030303030303030303065", false, "20자리 정수(19자리를 넘는다)"),
        BencodeCase("693131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313131313165", false, "64자리 정수"),
        BencodeCase("693932323333373230333638353437373538303865", false, "Long 넘침(MAX + 1)"),
        BencodeCase("692d3932323333373230333638353437373538303965", false, "Long 넘침(MIN - 1)"),
        BencodeCase("692b3165", false, "'+' 붙은 정수"),
        BencodeCase("69203165", false, "앞에 공백 붙은 정수"),
        BencodeCase("69312065", false, "뒤에 공백 붙은 정수"),
        BencodeCase("69312e3565", false, "소수"),
        BencodeCase("693432", false, "닫히지 않은 정수"),
        BencodeCase("64313a61693165313a6169326565", false, "중복 키(이웃)"),
        BencodeCase("64313a61693165313a62693265313a6169336565", false, "중복 키(떨어져 있고 정렬도 안 됐다 — 바로 앞 키와만 견주면 놓친다)"),
        BencodeCase("693165693265", false, "끝에 남는 바이트(값 뒤에 값)"),
        BencodeCase("646578", false, "끝에 남는 바이트"),
        BencodeCase("353a616263", false, "길이가 남은 바이트보다 긴 문자열"),
        BencodeCase("393939393939393939393a61", false, "길이가 Int 를 넘는 문자열"),
        BencodeCase("6c313a61", false, "닫히지 않은 목록"),
        BencodeCase("64313a61693165", false, "닫히지 않은 사전"),
        BencodeCase("6469316569326565", false, "사전 키가 정수"),
        BencodeCase("646c6569316565", false, "사전 키가 목록"),
        BencodeCase("64313a6165", false, "값 없는 키"),
        BencodeCase("2d313a61", false, "음수 길이"),
        BencodeCase("2b313a61", false, "'+' 붙은 길이"),
        BencodeCase("33616263", false, "길이 뒤에 ':' 가 없다"),
        BencodeCase("", false, "빈 입력"),
        BencodeCase("78", false, "모르는 바이트"),
        BencodeCase("65", false, "짝 없는 'e'"),
        BencodeCase("6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c65656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565656565", false, "목록 1000 겹 — 깊이 상한(검토가 권한 64)을 넘는다"),
    )

    private fun krpcCases() = listOf(
        KrpcCase("get_peers-query", "64313a6164323a696432303a13fdac7683883ddd8029bd595abb00303acb7996393a696e666f5f6861736832303ad306734f7cec28875278db20f9d77b0db89e573465313a71393a6765745f7065657273323a726f693165313a74323a6161313a79313a7165", "get_peers 질의 — t 'aa', y 'q', q 'get_peers', a = {id: SHA1('iroiro-dht-self') = 13fdac7683883ddd8029bd595abb00303acb7996, info_hash: 토렌트 multi-file 의 v1 info-hash}, 맨 위 ro = 1(BEP 43 — 읽기 전용). 키는 바이트 차례."),
        KrpcCase("find_node-query", "64313a6164323a696432303a13fdac7683883ddd8029bd595abb00303acb7996363a74617267657432303ad5ff82252f36470270e9eced8133f06d7567169665313a71393a66696e645f6e6f6465323a726f693165313a74323a6262313a79313a7165", "find_node 질의 — t 'bb', a = {id: 위와 같다, target: SHA1('iroiro-dht-target') = d5ff82252f36470270e9eced8133f06d75671696}, ro = 1."),
        KrpcCase("get_peers-response", "64323a6970363ac63364071ae1313a7264323a696432303aa2366aebc97d6021f11f0ea13637d43b4f7bdb69353a6e6f64657335323a9ce6770cee65f46f63c91b2937632d82d3e88e35c00002111ae139d3c808ea7924886646df6d210d7f5dba11da7dc63364c8c8d5353a746f6b656e383a79a241ff177adbda363a76616c7565736c363acb0071051ae1363acb0071faffff6565313a74323a6161313a79313a7265", "get_peers 응답(t 'aa') — r.id a2366aebc97d6021f11f0ea13637d43b4f7bdb69, r.token 79a241ff177adbda, r.nodes 둘(9ce6770c… @ 192.0.2.17:6881, 39d3c808… @ 198.51.100.200:51413), r.values 둘(203.0.113.5:6881, 203.0.113.250:65535). 맨 위 ip 는 BEP 42 의 '네 주소'(198.51.100.7:6881) — 읽지 않아도 된다."),
        KrpcCase("find_node-response", "64313a7264323a696432303aea8c2f27a563ca3a53ee91a56944f622b8bbe55f353a6e6f64657337383a6a0bbc93f38b279ecaedc4be63d60f5938fb544dcb00716380008b5185b892bc1562a0b076dcf92107305ac7702ac00002010001a51fa26c65f3e2087ade7d2e92fcec11711c5523c63364ffffff65313a74323a6262313a76343a4c540102313a79313a7265", "find_node 응답(t 'bb') — r.id ea8c2f27a563ca3a53ee91a56944f622b8bbe55f, r.nodes 셋(6a0bbc93… @ 203.0.113.99:32768, 8b5185b8… @ 192.0.2.1:1, a51fa26c… @ 198.51.100.255:65535), values 없음, 맨 위 v 'LT\\x01\\x02'(상대의 판) — 읽지 않아도 된다. 포트 32768·1·65535 와 바이트 255 는 부호 없는 셈을 본다."),
        KrpcCase("error-response", "64313a656c693230316532333a412047656e65726963204572726f72204f63757272656465313a74323a6161313a79313a6565", "오류 응답 — e = [201, 'A Generic Error Ocurred'](BEP 5 의 예, 철자 그대로), t 'aa', y 'e'."),
    )

    private fun krpcResponseCases() = listOf(
        KrpcPeersCase("64323a6970363ac63364071ae1313a7264323a696432303aa2366aebc97d6021f11f0ea13637d43b4f7bdb69353a6e6f64657335323a9ce6770cee65f46f63c91b2937632d82d3e88e35c00002111ae139d3c808ea7924886646df6d210d7f5dba11da7dc63364c8c8d5353a746f6b656e383a79a241ff177adbda363a76616c7565736c363acb0071051ae1363acb0071faffff6565313a74323a6161313a79313a7265", listOf("9ce6770cee65f46f63c91b2937632d82d3e88e35", "39d3c808ea7924886646df6d210d7f5dba11da7d"), listOf("192.0.2.17:6881", "198.51.100.200:51413"), listOf("203.0.113.5:6881", "203.0.113.250:65535")),
        KrpcPeersCase("64313a7264323a696432303aea8c2f27a563ca3a53ee91a56944f622b8bbe55f353a6e6f64657337383a6a0bbc93f38b279ecaedc4be63d60f5938fb544dcb00716380008b5185b892bc1562a0b076dcf92107305ac7702ac00002010001a51fa26c65f3e2087ade7d2e92fcec11711c5523c63364ffffff65313a74323a6262313a76343a4c540102313a79313a7265", listOf("6a0bbc93f38b279ecaedc4be63d60f5938fb544d", "8b5185b892bc1562a0b076dcf92107305ac7702a", "a51fa26c65f3e2087ade7d2e92fcec11711c5523"), listOf("203.0.113.99:32768", "192.0.2.1:1", "198.51.100.255:65535"), emptyList()),
    )

    private fun udpCases() = listOf(
        UdpCase("connect-request", "00000417271019800000000001020304", "connect 요청(16 바이트) — protocol_id 0x41727101980, action 0, transaction_id 0x01020304."),
        UdpCase("connect-response", "00000000010203048877665544332211", "connect 응답(16 바이트) — action 0, transaction_id 0x01020304, connection_id 0x8877665544332211(맨 앞 비트가 1)."),
        UdpCase("announce-request", "8877665544332211000000010a0b0c0dd306734f7cec28875278db20f9d77b0db89e57342d5858303030302d36333964376532633837353100000000000000000000000000020f58000000000000000000000002000000009e3779b9ffffffff1ae1", "announce 요청(98 바이트) — connection_id 0x8877665544332211, action 1, transaction_id 0x0A0B0C0D, info_hash = 토렌트 multi-file 의 v1(d306734f7cec28875278db20f9d77b0db89e5734), peer_id '-XX0000-639d7e2c8751', downloaded 0, left 135000, uploaded 0, event 2(started), IP 0(기본), key 0x9E3779B9, num_want -1(0xFFFFFFFF), port 6881."),
        UdpCase("announce-response", "000000010a0b0c0d000007080000000300000007cb00710a1ae1c6336414c8d5", "announce 응답 — action 1, transaction_id 0x0A0B0C0D, interval 1800, leechers 3, seeders 7, 피어 둘(203.0.113.10:6881, 198.51.100.20:51413)."),
        UdpCase("error-response", "000000030a0b0c0d746f7272656e74206e6f742072656769737465726564", "오류 응답 — action 3, transaction_id 0x0A0B0C0D, 메시지 'torrent not registered'(남은 바이트 전부, ASCII)."),
    )
}
