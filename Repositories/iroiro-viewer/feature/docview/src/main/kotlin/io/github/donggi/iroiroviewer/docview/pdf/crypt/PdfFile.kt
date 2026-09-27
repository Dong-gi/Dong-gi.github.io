package io.github.donggi.iroiroviewer.docview.pdf.crypt

/**
 * PDF 파일의 뼈대 — xref(어느 객체가 어디 있는가)와 트레일러, 그리고 객체 하나를 읽는 법.
 *
 * ## xref 를 믿되, 무너지면 다시 세운다
 *
 * 정상 파일은 `startxref` → xref 표(또는 xref 스트림) → `/Prev` 로 이전 판을 잇는다. 그런데
 * 실물에는 오프셋이 몇 바이트씩 밀린 파일이 흔하다(줄 끝을 CRLF 로 바꿔 저장한 도구가 있다).
 * pdfium 은 그런 파일을 **처음부터 훑어 객체를 다시 찾아** 연다. 우리도 복호화하려면 같은 일을
 * 해야 한다 — 우리가 못 열면 pdfium 이 열 수 있는 파일도 사용자는 못 본다.
 *
 * ## 막는 것
 *
 * `/Prev` 가 자기 자신을 가리키는 파일(무한 루프), xref 구역이 수만 개인 파일, `/Size` 가
 * 수십억인 파일. 각각 방문 집합·구역 상한·객체 수 상한으로 끊는다.
 */
internal class PdfFile private constructor(
    private val src: PdfSource,
    val entries: Map<Int, Entry>,
    val trailer: PdfDict,
    /** xref 를 다시 세웠는가. 진단에만 쓴다. */
    val rebuilt: Boolean,
) {

    sealed interface Entry
    data class InFile(val offset: Long, val gen: Int) : Entry
    data class InStream(val stream: Int, val index: Int) : Entry

    /** 해제된 번호. 이전 판의 같은 번호가 되살아나지 않게 자리를 막는다. */
    data object Free : Entry

    /** `N G obj … endobj` 하나. 스트림이면 데이터의 자리를 함께 싣는다. */
    class Indirect(
        val num: Int,
        val gen: Int,
        val value: PdfObj,
        /** 스트림 데이터가 시작하는 자리. 스트림이 아니면 -1. */
        val dataStart: Long,
    ) {
        val isStream: Boolean get() = dataStart >= 0
        val dict: PdfDict? get() = value as? PdfDict
    }

    val size: Long get() = src.size

    /** [offset] 에 있는 객체를 읽는다. 스트림 데이터는 읽지 않는다. */
    fun readAt(offset: Long): Indirect? {
        if (offset < 0 || offset >= src.size) return null
        val c = PdfCursor(src, offset)
        val p = PdfParser(c)
        val numTok = p.parseObject() as? PdfNum ?: return null
        val genTok = p.parseObject() as? PdfNum ?: return null
        val kw = p.parseObject() as? PdfParser.Keyword ?: return null
        if (kw.text != "obj") return null
        val num = numTok.int ?: return null
        val gen = genTok.int ?: return null
        val value = p.parseObject() ?: return null
        if (value is PdfParser.Keyword) {
            // `N G obj endobj` — 빈 객체. null 로 본다.
            return Indirect(num, gen, PdfNull, -1)
        }
        // 스트림인가?
        p.skipWhite()
        val save = c.pos
        val next = p.parseObject()
        if (value is PdfDict && next is PdfParser.Keyword && next.text == "stream") {
            // `stream` 뒤에는 CRLF 또는 LF 가 온다. CR 하나만 둔 파일도 있어 그것도 받는다.
            // **그 사이의 공백·탭을 건너뛴다**(`stream \r\n`). 명세 위반이지만 pdfium(`ToNextLine`)·
            // pdf.js·qpdf 가 다 받는다 — 건너뛰지 않으면 공백이 데이터의 첫 바이트가 되어
            // AES 의 IV 와 RC4 의 열쇠 흐름이 통째로 어긋난다(적대적 검토가 잡았다).
            var d = c.pos
            while (peekAt(d) == 32 || peekAt(d) == 9) d++
            val b1 = peekAt(d)
            if (b1 == 13) {
                d++
                if (peekAt(d) == 10) d++
            } else if (b1 == 10) {
                d++
            }
            return Indirect(num, gen, value, d)
        }
        c.seek(save)
        return Indirect(num, gen, value, -1)
    }

    /**
     * xref 가 객체 머리와 맞는가 — **번호가 가장 작은 쓰는 항목 몇 개**를 읽어 본다.
     *
     * pdfium 은 첫 항목을 확인하고 어긋나면 다시 세운다. 우리가 그러지 않으면 '표가 1 번에서
     * 시작한다' 같은 흔한 도구 결함에서 모든 항목이 한 칸씩 밀려, `/Encrypt` 가 엉뚱한 객체로
     * 풀리고(암호를 묻지 않거나) 복호기가 객체를 전부 버린다(적대적 검토가 잡았다). 셋이면
     * 밀림은 잡고, 수만 개를 다 읽는 값은 치르지 않는다.
     */
    internal fun headersMatch(): Boolean {
        val sample = entries.entries.asSequence()
            .filter { it.value is InFile }
            .sortedBy { it.key }
            .take(3)
            .toList()
        for ((num, e) in sample) {
            val obj = try {
                readAt((e as InFile).offset)
            } catch (x: PdfSyntaxException) {
                null
            } ?: return false
            if (obj.num != num) return false
        }
        return true
    }

    fun peekAt(pos: Long): Int {
        if (pos < 0 || pos >= src.size) return -1
        val b = ByteArray(1)
        return if (src.read(pos, b, 0, 1) == 1) b[0].toInt() and 0xFF else -1
    }

    fun readBytes(pos: Long, len: Int): ByteArray {
        val out = ByteArray(len)
        var done = 0
        while (done < len) {
            val n = src.read(pos + done, out, done, len - done)
            if (n <= 0) break
            done += n
        }
        return if (done == len) out else out.copyOf(done)
    }

    /**
     * 스트림 데이터의 길이. `/Length` 를 먼저 믿고, **그 자리에 `endstream` 이 없으면** 찾는다.
     *
     * `/Length` 가 틀린 파일이 흔하다 — 특히 간접 참조(`/Length 12 0 R`)를 쓰는 도구가
     * 나중에 값을 채우다 실수한다. 그 한 스트림 때문에 문서 전체를 못 여는 것보다 찾는 편이 낫다.
     */
    fun streamLength(obj: Indirect, resolve: (PdfObj?) -> PdfObj?): Long {
        val declared = (resolve(obj.dict?.get("Length")) as? PdfNum)?.long
        if (declared != null && declared >= 0 && obj.dataStart + declared <= src.size) {
            if (endstreamNear(obj.dataStart + declared)) return declared
        }
        return findEndstream(obj.dataStart)
    }

    /** 이 자리(공백 몇 개 뒤)에 `endstream` 이 있는가. */
    private fun endstreamNear(at: Long): Boolean {
        val probe = readBytes(at, 32)
        var i = 0
        while (i < probe.size && PdfChars.isWhite(probe[i].toInt() and 0xFF)) i++
        return String(probe, i, probe.size - i, Charsets.ISO_8859_1).startsWith("endstream")
    }

    /** `endstream` 을 찾아 그 앞의 줄 끝을 뗀 길이. 못 찾으면 파일 끝까지. */
    private fun findEndstream(start: Long): Long {
        val needle = "endstream".toByteArray(Charsets.ISO_8859_1)
        val buf = ByteArray(64 * 1024)
        var pos = start
        var carry = 0
        while (pos < src.size) {
            val n = src.read(pos, buf, carry, buf.size - carry)
            if (n <= 0) break
            val total = carry + n
            val idx = indexOf(buf, total, needle)
            if (idx >= 0) {
                var end = pos - carry + idx
                // 앞의 줄 끝은 데이터가 아니다(명세 7.3.8.1).
                if (end - 1 >= start && peekAt(end - 1) == 10) end--
                if (end - 1 >= start && peekAt(end - 1) == 13) end--
                return end - start
            }
            // 경계에 걸친 바늘을 놓치지 않으려고 꼬리를 남긴다.
            val keep = minOf(needle.size - 1, total)
            System.arraycopy(buf, total - keep, buf, 0, keep)
            pos += n
            carry = keep
        }
        return src.size - start
    }

    private fun indexOf(buf: ByteArray, len: Int, needle: ByteArray): Int {
        outer@ for (i in 0..len - needle.size) {
            for (j in needle.indices) if (buf[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    companion object {

        /** xref 구역 수 상한. 증분 저장을 수천 번 한 파일은 없다. */
        const val MAX_SECTIONS = 1024

        /** 객체 번호 상한(명세의 구현 한계, 부록 C). */
        const val MAX_OBJECTS = 8_388_607

        /**
         * @param checkCancelled xref 구역마다, 다시 세울 때는 1 MiB 마다 부른다. 취소면 여기서 던져라.
         *   다시 세우기는 파일 전체를 훑으므로(수백 MB 일 수 있다) 이것이 없으면 여는 시간 상한도
         *   뒤로 가기도 닿지 않는다.
         */
        fun open(src: PdfSource, checkCancelled: () -> Unit = {}): PdfFile {
            // **다시 세우기는 한 번만 한다.** 예전에는 `readXref ?: rebuild` 가 던지면 catch 에서
            // 또 `rebuild` 를 불러, 뿌리 없는 큰 파일을 두 번 통째로 훑었다.
            val viaXref = try {
                readXref(src, checkCancelled)?.takeIf { it.headersMatch() }
            } catch (e: PdfSyntaxException) {
                null
            }
            return viaXref ?: rebuild(src, checkCancelled)
        }

        // ---- 정상 경로: startxref 부터 ----------------------------------------------

        private fun readXref(src: PdfSource, checkCancelled: () -> Unit): PdfFile? {
            val start = findStartXref(src) ?: return null
            val entries = HashMap<Int, Entry>()
            val trailer = LinkedHashMap<String, PdfObj>()
            val visited = HashSet<Long>()
            var offset: Long? = start
            var sections = 0
            val probe = PdfFile(src, entries, PdfDict(trailer), false)
            while (offset != null) {
                checkCancelled()
                if (!visited.add(offset)) break // /Prev 가 되돌아왔다
                if (++sections > MAX_SECTIONS) throw PdfSyntaxException("xref 구역이 너무 많다")
                val section = readSection(src, probe, offset, entries) ?: return null
                for ((k, v) in section.map) {
                    if (k !in XREF_ONLY_KEYS) trailer.putIfAbsent(k, v)
                }
                // 혼합형(hybrid) 파일: 표 구역의 트레일러가 xref 스트림을 따로 가리킨다.
                val xrefStm = (section["XRefStm"] as? PdfNum)?.long
                if (xrefStm != null && visited.add(xrefStm)) {
                    readSection(src, probe, xrefStm, entries)
                }
                offset = (section["Prev"] as? PdfNum)?.long
            }
            if (trailer["Root"] == null) return null
            return PdfFile(src, entries, PdfDict(trailer), false)
        }

        /** 파일 끝 몇 KB 에서 `startxref` 를 찾는다. 끝에 쓰레기가 붙은 파일이 있어 넉넉히 본다. */
        private fun findStartXref(src: PdfSource): Long? {
            val tail = minOf(src.size, 8192L).toInt()
            val buf = ByteArray(tail)
            var done = 0
            while (done < tail) {
                val n = src.read(src.size - tail + done, buf, done, tail - done)
                if (n <= 0) break
                done += n
            }
            val text = String(buf, 0, done, Charsets.ISO_8859_1)
            val at = text.lastIndexOf("startxref")
            if (at < 0) return null
            val m = Regex("""startxref\s+(\d+)""").find(text, at) ?: return null
            return m.groupValues[1].toLongOrNull()?.takeIf { it in 0 until src.size }
        }

        /**
         * xref 구역 하나 — 표든 스트림이든. 들어 있던 항목을 [entries] 에 **없을 때만** 넣는다
         * (새 판부터 읽으므로 먼저 넣은 것이 이긴다).
         *
         * @return 그 구역의 트레일러(표) 또는 스트림 사전. 구역이 아니면 null.
         */
        private fun readSection(
            src: PdfSource,
            file: PdfFile,
            offset: Long,
            entries: MutableMap<Int, Entry>,
        ): PdfDict? {
            val c = PdfCursor(src, offset)
            val p = PdfParser(c)
            p.skipWhite()
            val save = c.pos
            val first = p.parseObject()
            if (first is PdfParser.Keyword && first.text == "xref") {
                return readTable(p, c, entries)
            }
            c.seek(save)
            val obj = file.readAt(offset) ?: return null
            val dict = obj.dict ?: return null
            if (dict.name("Type") != "XRef" || !obj.isStream) return null
            readXrefStream(file, obj, entries)
            return dict
        }

        private fun readTable(p: PdfParser, c: PdfCursor, entries: MutableMap<Int, Entry>): PdfDict? {
            while (true) {
                val a = p.parseObject() ?: return null
                if (a is PdfParser.Keyword) {
                    if (a.text != "trailer") return null
                    return p.parseObject() as? PdfDict
                }
                val startNum = (a as? PdfNum)?.int ?: return null
                val count = (p.parseObject() as? PdfNum)?.int ?: return null
                if (startNum < 0 || count < 0 || startNum.toLong() + count > MAX_OBJECTS) {
                    throw PdfSyntaxException("xref 소구역이 범위를 넘는다")
                }
                for (i in 0 until count) {
                    val off = (p.parseObject() as? PdfNum)?.long ?: return null
                    val gen = (p.parseObject() as? PdfNum)?.int ?: return null
                    val kind = (p.parseObject() as? PdfParser.Keyword)?.text ?: return null
                    val num = startNum + i
                    if (kind == "n") {
                        if (off > 0) entries.putIfAbsent(num, InFile(off, gen))
                    } else {
                        // 해제된 객체도 자리는 차지한다 — 이전 판의 같은 번호를 되살리면 안 된다.
                        entries.putIfAbsent(num, Free)
                    }
                }
            }
        }

        private fun readXrefStream(file: PdfFile, obj: Indirect, entries: MutableMap<Int, Entry>) {
            val dict = obj.dict ?: return
            val w = (dict["W"] as? PdfArray)?.items?.map { (it as? PdfNum)?.int ?: 0 }
                ?: throw PdfSyntaxException("xref 스트림에 W 가 없다")
            if (w.size < 3 || w.any { it < 0 || it > 8 }) throw PdfSyntaxException("W 가 이상하다")
            val size = dict.int("Size") ?: 0
            val index = (dict["Index"] as? PdfArray)?.items?.map { (it as? PdfNum)?.int ?: 0 }
                ?: listOf(0, size)
            // xref 스트림은 암호화되지 않는다(명세 7.6.1) — 그대로 푼다.
            val len = file.streamLength(obj) { it }
            if (len > PdfFilters.MAX_DECODED) throw PdfSyntaxException("xref 스트림이 너무 크다")
            val data = PdfFilters.decode(dict, file.readBytes(obj.dataStart, len.toInt()))
            val rec = w[0] + w[1] + w[2]
            if (rec == 0) return
            var p = 0
            var i = 0
            while (i + 1 < index.size) {
                val start = index[i]
                val count = index[i + 1]
                if (start < 0 || count < 0 || start.toLong() + count > MAX_OBJECTS) {
                    throw PdfSyntaxException("xref 스트림 색인이 범위를 넘는다")
                }
                for (k in 0 until count) {
                    if (p + rec > data.size) return
                    val type = if (w[0] == 0) 1L else field(data, p, w[0])
                    val f2 = field(data, p + w[0], w[1])
                    val f3 = field(data, p + w[0] + w[1], w[2])
                    p += rec
                    val num = start + k
                    when (type) {
                        0L -> entries.putIfAbsent(num, Free)
                        1L -> if (f2 > 0) entries.putIfAbsent(num, InFile(f2, f3.toInt()))
                        2L -> entries.putIfAbsent(num, InStream(f2.toInt(), f3.toInt()))
                        else -> Unit // 모르는 종류는 명세가 '무시하라' 고 한다
                    }
                }
                i += 2
            }
        }

        private fun field(data: ByteArray, at: Int, width: Int): Long {
            var v = 0L
            for (i in 0 until width) v = (v shl 8) or (data[at + i].toLong() and 0xFF)
            return v
        }

        // ---- 무너졌을 때: 처음부터 훑어 다시 세운다 ----------------------------------

        /**
         * `N G obj` 를 전부 찾아 **나중에 나온 것이 이기게** 표를 만들고, 마지막 트레일러를 쓴다.
         *
         * 객체 스트림 안의 객체는 여기서 알 수 없다(그 스트림이 암호화되어 있다). 복호기가
         * 객체 스트림을 풀면서 그 안의 번호를 나중에 채운다([PdfDecryptor]).
         */
        private fun rebuild(src: PdfSource, checkCancelled: () -> Unit): PdfFile {
            val entries = HashMap<Int, Entry>()
            val trailer = LinkedHashMap<String, PdfObj>()
            val file = PdfFile(src, entries, PdfDict(trailer), true)
            val objRe = Regex("""(\d{1,10})\s+(\d{1,5})\s+obj\b""")
            val chunk = 1 shl 20
            var pos = 0L
            val buf = ByteArray(chunk + 64)
            while (pos < src.size) {
                checkCancelled()
                val n = src.read(pos, buf, 0, buf.size)
                if (n <= 0) break
                val text = String(buf, 0, n, Charsets.ISO_8859_1)
                for (m in objRe.findAll(text)) {
                    // 겹쳐 읽은 꼬리에서 이미 본 것은 건너뛴다.
                    if (m.range.first >= chunk) continue
                    val at = pos + m.range.first
                    // 앞이 줄 끝·공백·파일 처음이어야 한다 — `123 0 obj` 가 문자열 속에 든 경우를 줄인다.
                    val before = if (at == 0L) 10 else file.peekAt(at - 1)
                    if (!PdfChars.isWhite(before) && before != '>'.code && before != ']'.code) continue
                    val num = m.groupValues[1].toLongOrNull() ?: continue
                    val gen = m.groupValues[2].toIntOrNull() ?: continue
                    if (num <= 0 || num > MAX_OBJECTS) continue
                    entries[num.toInt()] = InFile(at, gen)
                }
                val t = text.lastIndexOf("trailer")
                if (t in 0 until chunk) {
                    val c = PdfCursor(src, pos + t + "trailer".length)
                    val d = runCatching { PdfParser(c).parseObject() as? PdfDict }.getOrNull()
                    if (d != null) for ((k, v) in d.map) trailer[k] = v
                }
                pos += chunk
            }
            // xref 스트림의 사전도 트레일러 노릇을 한다(/Root·/Encrypt·/ID 가 거기 있다).
            if (trailer["Root"] == null || trailer["Encrypt"] == null) {
                for ((_, e) in entries.toList().sortedBy { (it.second as InFile).offset }) {
                    checkCancelled()
                    val obj = runCatching { file.readAt((e as InFile).offset) }.getOrNull() ?: continue
                    val d = obj.dict ?: continue
                    if (d.name("Type") == "XRef") {
                        for ((k, v) in d.map) if (k !in XREF_ONLY_KEYS) trailer[k] = v
                    }
                }
            }
            if (trailer["Root"] == null) throw PdfSyntaxException("문서 뿌리(/Root)를 찾지 못했다")
            return PdfFile(src, entries, PdfDict(trailer), true)
        }

        /** xref 스트림 사전에만 있고 트레일러로 옮기면 안 되는 키. */
        private val XREF_ONLY_KEYS = setOf(
            "Type", "W", "Index", "Length", "Filter", "DecodeParms", "Prev", "XRefStm", "Size",
        )

    }
}
