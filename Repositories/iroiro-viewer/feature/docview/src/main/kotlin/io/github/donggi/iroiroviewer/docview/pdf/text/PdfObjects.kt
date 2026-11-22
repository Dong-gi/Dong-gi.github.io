package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDecryptor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFilters
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfParser
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfStr
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSecurity
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfWeights
import java.io.ByteArrayOutputStream

/**
 * 글 꺼내기가 객체를 읽는 길 — 간접 참조를 풀고, 스트림을 **풀어**(암호·압축) 준다.
 *
 * 복호기(`PdfDecryptor`)의 해석기는 그쪽의 쓰임(`/Encrypt`·`/Length` 찾기, 객체 스트림)에 맞춰 줄여 둔 것이라 여기서 따로
 * 둔다. 다른 것은 셋이다 — 스트림 데이터를 **필터까지** 풀어 준다(내용 스트림은 LZW·ASCII85 로도 온다), 객체를 기억해 둔다
 * (쪽마다 같은 글꼴 사전을 다시 읽는다), 그리고 암호가 걸린 문서도 **빈 사용자 암호로 풀리면** 읽는다(소유자 암호만 걸린
 * 문서는 pdfium 이 스스로 여는데, 그 파일은 여전히 암호문이다).
 */
internal class PdfObjects(
    private val file: PdfFile,
    private val security: PdfSecurity?,
    /** 오래 도는 자리(스트림 풀기·객체 스트림 훑기)마다 부른다. 부르는 쪽이 일마다 바꿔 끼운다([PdfTextEngine.page]). */
    var checkCancelled: () -> Unit,
    /**
     * 읽는 일의 예산 — 객체 스트림을 푼 바이트, 그 안의 값을 읽느라 지나간 바이트, 캐시에 두지 못한 큰 값의 무게의 합(다시 한 것도 센다).
     * 넘으면 [BudgetExceeded] — 쪽 뽑기·합치기가 준다. 글 꺼내기는 시간 상한이 따로 있어 두지 않는다.
     */
    private val objStmBudget: Long = Long.MAX_VALUE,
) {
    /**
     * 풀어 둔 객체 스트림 — **못 푼 것도**(null) 기억한다. 깨진 64 MiB 스트림을 참조마다 다시 풀지 않게.
     *
     * **수가 아니라 무게로 묶는다**([CACHE_OBJSTM_WEIGHT]). 수로 묶으면(옛 판은 64개) 65개를 번갈아 가리키는 문서가 참조마다 스트림
     * 하나를 통째로 다시 풀어 제곱 시간이 되고(900칸 65개에 199초 — 독립 검토), 큰 스트림 64개는 메모리를 묶지 못한다.
     */
    private val objStreams = LinkedHashMap<Int, PdfDecryptor.ObjStm?>(16, 0.75f, true)
    private var objStreamsWeight = 0L
    private var objStmWork = 0L

    /** 읽는 예산([objStmBudget])을 넘었다. */
    class BudgetExceeded : RuntimeException()

    /**
     * 지금 푸는 중인 번호들 — **되돌아오는 참조를 끊는다.** 객체 스트림의 `/Length` 가 그 스트림 안의 객체를 가리키면
     * `objStream → resolve → load → objStream` 이 끝없이 돌아 스택이 넘친다. 이미 푸는 중인 것을 다시 만나면 '없다' 로 친다.
     */
    private val loading = HashSet<Int>()
    private val loadingStreams = HashSet<Int>()

    /** 없는 것으로 판명된 번호. 같은 빈 참조를 쪽마다 다시 찾지 않는다. */
    private val missing = HashSet<Int>()

    /**
     * 풀어 둔 간접 객체. 사전 수천 개면 충분하다 — 쪽마다 같은 자원을 가리킨다. **수와 무게 둘로 묶는다**([CACHE_OBJECTS]·[CACHE_WEIGHT]) —
     * 수로만 묶으면 큰 값 4,096 개가 힙을 다 먹는다(독립 검토 2차). [CACHE_VALUE_MAX] 를 넘는 값은 두지 않는다.
     */
    private class Cached(val value: PdfObj, val weight: Long)

    private val cache = LinkedHashMap<Int, Cached>(256, 0.75f, true)
    private var cacheWeight = 0L

    /** xref 를 다시 세운 파일에서 객체 스트림 속 번호를 찾아 둔 표. 처음 빈 번호를 만나면 한 번 만든다. */
    private var discovered: HashMap<Int, PdfFile.InStream>? = null

    /** 스트림 하나 — 사전과 데이터의 자리. */
    class Stream(val dict: PdfDict, val num: Int, val gen: Int, val dataStart: Long)

    /** 참조면 따라가고 아니면 그대로. 사슬은 [MAX_CHAIN] 에서 끊는다. */
    fun resolve(obj: PdfObj?): PdfObj? {
        var cur = obj
        var hops = 0
        while (cur is PdfRef) {
            if (++hops > MAX_CHAIN) return null
            cur = load(cur)
        }
        return cur
    }

    fun dict(obj: PdfObj?): PdfDict? = resolve(obj) as? PdfDict

    fun array(obj: PdfObj?): PdfArray? = resolve(obj) as? PdfArray

    fun number(obj: PdfObj?): Double? = (resolve(obj) as? PdfNum)?.let { it.text.toDoubleOrNull() }

    fun name(obj: PdfObj?): String? = (resolve(obj) as? PdfName)?.name

    private fun load(ref: PdfRef): PdfObj? {
        cache[ref.num]?.let { return it.value }
        if (ref.num in missing) return null
        if (loading.size >= MAX_NESTING || !loading.add(ref.num)) return null
        var weight = -1L
        val value = try {
            when (val e = file.entries[ref.num] ?: discoveredEntry(ref.num)) {
                // 머리의 번호가 참조와 다르면 없는 것이다(복호기와 같은 판단 — xref 가 밀린 파일).
                is PdfFile.InFile -> try {
                    file.readAt(e.offset)?.takeIf { it.num == ref.num }?.let { plainStrings(it.value, it.num, it.gen, 0) }
                } catch (x: PdfSyntaxException) {
                    null
                }
                is PdfFile.InStream -> member(e.stream, e.index)?.let { m ->
                    weight = m.weight
                    m.value
                }
                else -> null
            }
        } finally {
            loading.remove(ref.num)
        }
        if (value == null) {
            if (missing.size < MAX_MISSING) missing += ref.num
            return null
        }
        if (weight < 0) weight = PdfWeights.of(value)
        remember(ref.num, value, weight)
        return value
    }

    /** 객체 캐시에 넣는다 — 너무 큰 값은 두지 않고 그 무게를 예산에 센다(다시 읽게 되므로). */
    private fun remember(num: Int, value: PdfObj, weight: Long) {
        if (weight > CACHE_VALUE_MAX) {
            spend(weight)
            return
        }
        cache.put(num, Cached(value, weight))?.let { cacheWeight -= it.weight }
        cacheWeight += weight
        val it = cache.entries.iterator()
        while ((cacheWeight > CACHE_WEIGHT || cache.size > CACHE_OBJECTS) && cache.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key == num) continue
            cacheWeight -= e.value.weight
            it.remove()
        }
    }

    private fun spend(bytes: Long) {
        objStmWork += bytes
        if (objStmWork > objStmBudget) throw BudgetExceeded()
    }

    /** 객체 스트림 [stream] 의 칸 [index]. 읽느라 지나간 바이트를 예산에 세고, 스트림이 값을 기억해 무거워진 만큼 캐시 무게에 더한다. */
    private fun member(stream: Int, index: Int): PdfDecryptor.ObjStm.Member? {
        val stm = objStream(stream) ?: return null
        val before = stm.weight
        val m = stm.member(index)
        val grew = stm.weight - before
        if (grew != 0L && objStreams[stream] === stm) {
            objStreamsWeight += grew
            trimObjStreams(stream)
        }
        if (m != null) spend(m.read)
        return m
    }

    /**
     * [ref] 가 객체 스트림 속 객체면 그 값이 놓인 자리의 열쇠(스트림 번호·스트림 안 자리). 같은 열쇠면 같은 값이다 — 쪽 뽑기가 같은 자리를
     * 가리키는 번호들을 결과에서 한 객체로 쓴다. 파일에 바로 놓인 객체·없는 번호는 null.
     */
    fun sharedKey(ref: PdfRef): Long? {
        val e = (file.entries[ref.num] ?: discoveredEntry(ref.num)) as? PdfFile.InStream ?: return null
        val at = objStream(e.stream)?.offsetOf(e.index) ?: return null
        return (e.stream.toLong() shl 32) or at.toLong()
    }

    /**
     * 파일에 바로 놓인 객체 안의 **문자열을 푼다**(암호가 걸린 문서만 — 명세 7.6.2). 목차의 제목·이름 목적지의 열쇠가 문자열이다
     * (글 꺼내기는 문자열을 스트림 안에서만 읽어 이것 없이도 돌았다). 객체 스트림 속 객체는 스트림째 풀었으므로 따로 풀지 않는다.
     * 서명의 `/Contents` 는 두다(복호기의 `decryptValue` 와 같은 규칙). 못 푼 문자열은 빈 문자열이다 — 암호문을 글자로 읽히지 않게.
     */
    private fun plainStrings(v: PdfObj, num: Int, gen: Int, depth: Int): PdfObj {
        val sec = security ?: return v
        if (depth > PdfParser.MAX_DEPTH) return v
        return when (v) {
            is PdfStr -> try {
                PdfStr(sec.decrypt(num, gen, sec.methodFor(PdfSecurity.Kind.STRING), v.bytes))
            } catch (e: Exception) {
                // 암호기의 실패(잘린 AES 블록 등). 이 문자열만 버린다.
                PdfStr(ByteArray(0))
            }
            is PdfArray -> PdfArray(v.items.mapTo(ArrayList(v.items.size)) { plainStrings(it, num, gen, depth + 1) })
            is PdfDict -> {
                val isSig = v.name("Type") == "Sig" || v.name("Type") == "DocTimeStamp" ||
                    (v["ByteRange"] != null && v["Contents"] is PdfStr)
                val out = LinkedHashMap<String, PdfObj>(v.map.size)
                for ((k, x) in v.map) out[k] = if (isSig && k == "Contents") x else plainStrings(x, num, gen, depth + 1)
                PdfDict(out)
            }
            else -> v
        }
    }

    /** [ref] 가 가리키는 것이 스트림이면 그 자리. */
    fun stream(ref: PdfObj?): Stream? {
        val r = ref as? PdfRef ?: return null
        val e = file.entries[r.num] as? PdfFile.InFile ?: return null
        val obj = try {
            file.readAt(e.offset)
        } catch (x: PdfSyntaxException) {
            null
        } ?: return null
        if (obj.num != r.num || !obj.isStream) return null
        return Stream(obj.dict ?: return null, obj.num, obj.gen, obj.dataStart)
    }

    /**
     * 스트림을 푼다 — 암호를 벗기고 필터를 거꾸로 돈다. 풀린 크기가 [limit] 을 넘으면 거기서 자른다(내용 스트림 하나가
     * 수백 MB 로 풀리는 폭탄을 막는다 — 자른 앞부분의 글은 남는다).
     *
     * @return 풀 수 없으면(모르는 필터·깨진 데이터) null.
     */
    fun data(s: Stream, limit: Int = MAX_STREAM): ByteArray? {
        checkCancelled()
        val len = file.streamLength(PdfFile.Indirect(s.num, s.gen, s.dict, s.dataStart)) { resolve(it) }
        if (len < 0 || len > MAX_RAW) return null
        var bytes = file.readBytes(s.dataStart, len.toInt())
        if (security != null && !identityCrypt(s.dict)) {
            bytes = try {
                security.decrypt(s.num, s.gen, security.methodFor(PdfSecurity.Kind.STREAM), bytes)
            } catch (e: RuntimeException) {
                return null
            }
        }
        return try {
            decode(s.dict, bytes, limit)
        } catch (e: PdfSyntaxException) {
            null
        }
    }

    /** `/Filter /Crypt` 가 `/Identity` 를 이름 대면 그 스트림은 암호화되지 않았다(명세 7.6.5). */
    private fun identityCrypt(dict: PdfDict): Boolean {
        val filters = filterNames(dict)
        val i = filters.indexOf("Crypt")
        if (i < 0) return false
        val parms = parmsAt(dict, i)
        return (parms?.get("Name") as? PdfName)?.name?.let { it == "Identity" } ?: true
    }

    private fun filterNames(dict: PdfDict): List<String> = when (val f = resolve(dict["Filter"])) {
        is PdfName -> listOf(f.name)
        is PdfArray -> f.items.mapNotNull { (resolve(it) as? PdfName)?.name }
        else -> emptyList()
    }

    private fun parmsAt(dict: PdfDict, i: Int): PdfDict? = when (val p = resolve(dict["DecodeParms"] ?: dict["DP"])) {
        is PdfDict -> if (i == 0) p else null
        is PdfArray -> resolve(p.items.getOrNull(i)) as? PdfDict
        else -> null
    }

    private fun decode(dict: PdfDict, input: ByteArray, limit: Int): ByteArray {
        var out = input
        filterNames(dict).forEachIndexed { i, name ->
            checkCancelled()
            out = when (name) {
                "FlateDecode", "Fl" -> PdfFilters.predict(inflate(out, limit), parmsAt(dict, i))
                "LZWDecode", "LZW" -> PdfFilters.predict(Lzw.decode(out, parmsAt(dict, i)?.int("EarlyChange") ?: 1, limit), parmsAt(dict, i))
                "ASCII85Decode", "A85" -> ascii85(out)
                "ASCIIHexDecode", "AHx" -> asciiHex(out)
                "RunLengthDecode", "RL" -> runLength(out, limit)
                "Crypt" -> out
                // 그림의 필터(DCT·JPX·CCITT·JBIG2)는 글이 아니다. 내용 스트림에 오면 읽을 수 없다.
                else -> throw PdfSyntaxException("글을 꺼내지 않는 필터")
            }
            if (out.size > limit) out = out.copyOf(limit)
        }
        return out
    }

    /** 상한을 넘으면 거기까지만. 끝이 잘린 zlib 도 나온 데까지 쓴다(복호기의 [PdfFilters.inflate] 와 같은 판단). */
    private fun inflate(data: ByteArray, limit: Int): ByteArray {
        val inf = java.util.zip.Inflater()
        try {
            inf.setInput(data)
            val out = ByteArrayOutputStream(minOf(data.size * 4, 1 shl 20))
            val buf = ByteArray(64 * 1024)
            while (!inf.finished() && out.size() < limit) {
                val n = try {
                    inf.inflate(buf)
                } catch (e: java.util.zip.DataFormatException) {
                    break
                }
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inf.end()
        }
    }

    private fun ascii85(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var acc = 0L
        var n = 0
        var i = 0
        // `<~` 로 시작할 수 있다(PostScript 꼴).
        if (data.size >= 2 && data[0] == '<'.code.toByte() && data[1] == '~'.code.toByte()) i = 2
        while (i < data.size) {
            val c = data[i++].toInt() and 0xFF
            if (c == '~'.code) break
            if (c <= 32) continue
            if (c == 'z'.code && n == 0) {
                repeat(4) { out.write(0) }
                continue
            }
            if (c < 33 || c > 117) continue
            acc = acc * 85 + (c - 33)
            if (++n == 5) {
                for (s in 3 downTo 0) out.write(((acc shr (8 * s)) and 0xFF).toInt())
                acc = 0
                n = 0
            }
        }
        if (n > 1) {
            repeat(5 - n) { acc = acc * 85 + 84 }
            for (s in 3 downTo 4 - (n - 1)) out.write(((acc shr (8 * s)) and 0xFF).toInt())
        }
        return out.toByteArray()
    }

    private fun asciiHex(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size / 2)
        var hi = -1
        for (b in data) {
            val c = b.toInt() and 0xFF
            if (c == '>'.code) break
            val v = Character.digit(c, 16)
            if (v < 0) continue
            if (hi < 0) hi = v else {
                out.write(hi * 16 + v)
                hi = -1
            }
        }
        if (hi >= 0) out.write(hi * 16)
        return out.toByteArray()
    }

    private fun runLength(data: ByteArray, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size && out.size() < limit) {
            val len = data[i++].toInt() and 0xFF
            when {
                len == 128 -> break
                len < 128 -> {
                    val n = minOf(len + 1, data.size - i)
                    out.write(data, i, n)
                    i += n
                }
                else -> {
                    if (i >= data.size) break
                    val b = data[i++].toInt()
                    repeat(257 - len) { out.write(b) }
                }
            }
        }
        return out.toByteArray()
    }

    private fun objStream(num: Int): PdfDecryptor.ObjStm? {
        if (objStreams.containsKey(num)) return objStreams[num]
        // 이 스트림을 푸는 중에 다시 왔다(`/Length` 가 제 안을 가리킨다) — 없는 것으로 친다.
        if (!loadingStreams.add(num)) return null
        val parsed = try {
            parseObjStream(num)
        } finally {
            loadingStreams.remove(num)
        }
        objStreams[num] = parsed
        objStreamsWeight += parsed?.weight ?: 0L
        trimObjStreams(num)
        return parsed
    }

    /** 무게를 넘으면 오래된 것부터 내보낸다([keep] 하나는 남긴다). 못 푼 것(null)은 무게가 없어 수로 묶는다. */
    private fun trimObjStreams(keep: Int) {
        val it = objStreams.entries.iterator()
        while ((objStreamsWeight > CACHE_OBJSTM_WEIGHT || objStreams.size > CACHE_OBJSTMS) && objStreams.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key == keep) continue
            objStreamsWeight -= e.value?.weight ?: 0L
            it.remove()
        }
    }

    private fun parseObjStream(num: Int): PdfDecryptor.ObjStm? {
        checkCancelled()
        val s = stream(PdfRef(num, 0)) ?: return null
        val len = file.streamLength(PdfFile.Indirect(s.num, s.gen, s.dict, s.dataStart)) { resolve(it) }
        if (len < 0 || len > PdfFilters.MAX_DECODED) return null
        var raw = file.readBytes(s.dataStart, len.toInt())
        if (security != null) {
            raw = try {
                security.decrypt(s.num, s.gen, security.methodFor(PdfSecurity.Kind.STREAM), raw)
            } catch (e: RuntimeException) {
                return null
            }
        }
        return PdfDecryptor.ObjStm.parse(s.dict, raw, shareValues = true)?.also { spend(it.weight) }
    }

    /**
     * xref 를 다시 세운 파일은 객체 스트림 속 번호를 모른다(복호기는 쓰면서 알아 간다). 처음 빈 번호를 만나면 객체 스트림을
     * 한 번 다 훑어 표를 만든다.
     */
    private fun discoveredEntry(num: Int): PdfFile.Entry? {
        if (!file.rebuilt) return null
        // **다 훑은 뒤에만 적는다** — 훑다가 취소되면(목차 10초·두드림 2초) 반쯤 찬 표가 남아 나머지 객체가 이 문서에서 영영 '없다' 가 된다.
        val table = discovered ?: HashMap<Int, PdfFile.InStream>().also { t ->
            for ((n, e) in file.entries) {
                checkCancelled()
                if (e !is PdfFile.InFile) continue
                val s = stream(PdfRef(n, 0)) ?: continue
                if ((s.dict["Type"] as? PdfName)?.name != "ObjStm") continue
                val objs = objStream(n) ?: continue
                objs.numbers().forEachIndexed { i, member -> t.putIfAbsent(member, PdfFile.InStream(n, i)) }
            }
            discovered = t
        }
        return table[num]
    }

    companion object {
        private const val MAX_CHAIN = 16
        private const val CACHE_OBJECTS = 4096

        /** 객체 캐시의 무게(어림 — [PdfWeights]) 상한과, 캐시에 두는 값 하나의 상한. */
        private const val CACHE_WEIGHT = 48L * 1024 * 1024
        private const val CACHE_VALUE_MAX = 16L * 1024 * 1024
        /** 못 푼 것까지 센 객체 스트림 수의 상한. */
        private const val CACHE_OBJSTMS = 4096

        /** 기억해 두는 객체 스트림의 무게(푼 바이트 + 번호표) 상한. */
        private const val CACHE_OBJSTM_WEIGHT = 48L * 1024 * 1024

        /** 객체를 푸는 중에 다른 객체를 푸는 깊이의 상한(참조 사슬·객체 스트림의 `/Length`). */
        private const val MAX_NESTING = 32
        private const val MAX_MISSING = 65_536

        /** 내용 스트림 하나를 푸는 상한. 넘으면 앞부분만 읽는다. */
        const val MAX_STREAM = 32 * 1024 * 1024

        /** 원본(압축된) 스트림을 읽는 상한. */
        private const val MAX_RAW = 64L * 1024 * 1024
    }
}

/**
 * LZW(명세 7.4.4). 옛 PDF 의 내용 스트림이 이것으로 온다. 코드 폭은 9~12 비트, 256 은 표 비우기, 257 은 끝.
 * `EarlyChange` 가 1(기본)이면 폭을 한 코드 일찍 넓힌다.
 */
internal object Lzw {
    fun decode(data: ByteArray, earlyChange: Int, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(data.size * 2)
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val length = IntArray(4096)
        for (i in 0 until 256) {
            suffix[i] = i.toByte()
            length[i] = 1
        }
        var next = 258
        var width = 9
        var prev = -1
        var bitBuf = 0L
        var bits = 0
        var pos = 0
        val stack = ByteArray(4096)

        fun emit(code: Int): Byte {
            var c = code
            var n = 0
            while (c >= 0 && n < stack.size) {
                stack[n++] = suffix[c]
                c = if (length[c] > 1) prefix[c] else -1
            }
            for (i in n - 1 downTo 0) out.write(stack[i].toInt())
            return stack[n - 1]
        }

        while (out.size() < limit) {
            while (bits < width && pos < data.size) {
                bitBuf = (bitBuf shl 8) or (data[pos++].toLong() and 0xFF)
                bits += 8
            }
            if (bits < width) break
            val code = ((bitBuf shr (bits - width)) and ((1L shl width) - 1)).toInt()
            bits -= width
            when {
                code == 256 -> {
                    next = 258
                    width = 9
                    prev = -1
                    continue
                }
                code == 257 -> break
                prev < 0 -> {
                    if (code > 255) break
                    out.write(code)
                    prev = code
                    continue
                }
            }
            val first: Byte
            if (code < next) {
                first = emit(code)
            } else if (code == next) {
                val f = emit(prev)
                out.write(f.toInt())
                first = f
            } else {
                break // 깨진 데이터
            }
            if (next < 4096) {
                prefix[next] = prev
                suffix[next] = first
                length[next] = length[prev] + 1
                next++
            }
            prev = code
            val threshold = next + earlyChange
            width = when {
                threshold >= 2048 -> 12
                threshold >= 1024 -> 11
                threshold >= 512 -> 10
                else -> 9
            }
        }
        return out.toByteArray()
    }
}
