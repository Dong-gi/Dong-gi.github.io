package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDecryptor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFilters
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSecurity
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
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
) {
    /** 풀어 둔 객체 스트림 — **못 푼 것도**(null) 기억한다. 깨진 64 MiB 스트림을 참조마다 다시 풀지 않게. */
    private val objStreams = object : LinkedHashMap<Int, PdfDecryptor.ObjStm?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfDecryptor.ObjStm?>?): Boolean =
            size > CACHE_OBJSTMS
    }

    /**
     * 지금 푸는 중인 번호들 — **되돌아오는 참조를 끊는다.** 객체 스트림의 `/Length` 가 그 스트림 안의 객체를 가리키면
     * `objStream → resolve → load → objStream` 이 끝없이 돌아 스택이 넘쳤다(복호기의 해석기에는 깊이 16 의
     * 문턱이 있었고 여기에는 없었다). 이미 푸는 중인 것을 다시 만나면 '없다' 로 친다.
     */
    private val loading = HashSet<Int>()
    private val loadingStreams = HashSet<Int>()

    /** 없는 것으로 판명된 번호. 같은 빈 참조를 쪽마다 다시 찾지 않는다. */
    private val missing = HashSet<Int>()

    /** 풀어 둔 간접 객체. 사전 수천 개면 충분하다 — 쪽마다 같은 자원을 가리킨다. */
    private val cache = object : LinkedHashMap<Int, PdfObj>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfObj>?): Boolean = size > CACHE_OBJECTS
    }

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
        cache[ref.num]?.let { return it }
        if (ref.num in missing) return null
        if (loading.size >= MAX_NESTING || !loading.add(ref.num)) return null
        val value = try {
            when (val e = file.entries[ref.num] ?: discoveredEntry(ref.num)) {
                // 머리의 번호가 참조와 다르면 없는 것이다(복호기와 같은 판단 — xref 가 밀린 파일).
                is PdfFile.InFile -> try {
                    file.readAt(e.offset)?.takeIf { it.num == ref.num }?.value
                } catch (x: PdfSyntaxException) {
                    null
                }
                is PdfFile.InStream -> objStream(e.stream)?.get(e.index)
                else -> null
            }
        } finally {
            loading.remove(ref.num)
        }
        if (value == null) {
            if (missing.size < MAX_MISSING) missing += ref.num
            return null
        }
        cache[ref.num] = value
        return value
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
        return parsed
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
        return PdfDecryptor.ObjStm.parse(s.dict, raw)
    }

    /**
     * xref 를 다시 세운 파일은 객체 스트림 속 번호를 모른다(복호기는 쓰면서 알아 간다). 처음 빈 번호를 만나면 객체 스트림을
     * 한 번 다 훑어 표를 만든다.
     */
    private fun discoveredEntry(num: Int): PdfFile.Entry? {
        if (!file.rebuilt) return null
        val table = discovered ?: HashMap<Int, PdfFile.InStream>().also { t ->
            discovered = t
            for ((n, e) in file.entries) {
                checkCancelled()
                if (e !is PdfFile.InFile) continue
                val s = stream(PdfRef(n, 0)) ?: continue
                if ((s.dict["Type"] as? PdfName)?.name != "ObjStm") continue
                val objs = objStream(n) ?: continue
                objs.numbers().forEachIndexed { i, member -> t.putIfAbsent(member, PdfFile.InStream(n, i)) }
            }
        }
        return table[num]
    }

    companion object {
        private const val MAX_CHAIN = 16
        private const val CACHE_OBJECTS = 4096
        private const val CACHE_OBJSTMS = 64

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
