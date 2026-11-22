package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.io.ByteArrayOutputStream

/**
 * 암호 PDF 를 **평문 PDF 로 다시 쓴다.** pdfium 은 그 결과를 연다.
 *
 * ## 왜 '다시 쓰기' 인가
 *
 * pdfium 에 복호화된 바이트를 넘기려면 PDF 한 벌이 있어야 한다 — pdfium 은 우리 복호화 함수를
 * 부를 방법이 없다. 그래서 객체를 하나씩 읽어 **문자열과 스트림만 풀고** 나머지는 글자
 * 그대로 옮긴 뒤, `/Encrypt` 를 빼고 xref 를 새로 쓴다.
 *
 * 쓰는 곳은 디스크가 아니다. 호출자가 주는 [PdfSink] 는 이름 없는 메모리 파일(memfd)이다 —
 * 사용자 문서의 **평문 사본을 저장소에 남기지 않는다**는 이 앱의 규칙이 그대로 선다.
 *
 * ## 무엇을 풀고 무엇을 두는가(명세 7.6.1)
 *
 * | 무엇 | 푸는가 |
 * |---|---|
 * | 객체 안의 문자열 | 푼다(`/StrF`) |
 * | 스트림 데이터 | 푼다(`/StmF`). 스트림이 `/Filter /Crypt` 로 이름을 대면 그 필터로 |
 * | 첨부 파일 스트림(`/Type /EmbeddedFile`) | `/EFF` 로 |
 * | 메타데이터 스트림 | `/EncryptMetadata false` 면 **두지 않고 그대로** |
 * | `/Encrypt` 사전 · 트레일러 `/ID` | 암호화되지 않았다. 사전은 빼고 `/ID` 는 둔다 |
 * | xref 스트림 | 암호화되지 않았다. **옮기지 않는다** — 오프셋이 전부 틀려진다 |
 * | 객체 스트림 안의 객체 | 따로 암호화되지 않았다. 스트림을 풀면 함께 풀린다 |
 * | 서명 사전의 `/Contents` | 암호화되지 않았다(서명 바이트 자체다) |
 *
 * ## 압축은 풀지 않는다
 *
 * 내용 스트림이 `/FlateDecode` 면 복호화한 뒤에도 **압축된 채로** 쓴다. 풀면 파일이 몇 배로
 * 부풀어 메모리 파일이 커지고, pdfium 이 어차피 다시 푼다.
 */
internal object PdfDecryptor {

    sealed interface Result {
        /** 평문 PDF 를 [PdfSink] 에 다 썼다. */
        data object Ok : Result

        /** 암호가 맞지 않다. */
        data object WrongPassword : Result

        /** 애초에 암호가 걸려 있지 않다. 호출자는 원본을 그대로 열면 된다. */
        data object NotEncrypted : Result

        /** 이 앱이 풀지 않는 방식. [reason] 은 진단용이다 — 화면에 내보내지 않는다. */
        data class Unsupported(val reason: String) : Result
    }

    /** 결과가 상한을 넘었다. */
    class TooLarge : Exception("복호화 결과가 상한을 넘었다")

    /**
     * 암호 없이 본 문서의 사정. [inspect] 가 돌려준다.
     *
     * pdfium 이 `SecurityException` 하나로 말하는 것을 우리가 셋으로 가른다 — 암호를 물을
     * 것인가, 물어도 소용없는가, 암호 없이 우리가 풀 수 있는가.
     */
    sealed interface Inspection {
        data object NotEncrypted : Inspection

        /**
         * 빈 사용자 암호로 풀린다(소유자 암호만 걸린 문서). pdfium 은 보통 이것을 스스로
         * 여는데, 그러지 못했다면 우리가 빈 암호로 풀어 넘긴다.
         */
        data object OpenWithoutPassword : Inspection

        /** 표준 보안 처리기이고, 사용자 암호가 필요하다. */
        data object NeedsPassword : Inspection

        /** 풀 수 없는 방식. [reason] 은 진단용이다 — 화면에 내보내지 않는다. */
        data class Unsupported(val reason: String) : Inspection
    }

    /**
     * 암호 없이 들여다본다. **xref 와 `/Encrypt` 만 읽는다** — 객체를 다 훑지 않으므로
     * 수백 MB 문서에서도 싸다. xref 가 무너져 다시 세워야 하면 파일을 훑는데, 그 동안
     * [checkCancelled] 를 부른다.
     *
     * **PDF 머리가 없으면 곧바로 '잠기지 않았다' 다.** 플랫폼이 '깨졌다' 고 한 파일마다 여기로
     * 오므로(확장자만 `.pdf` 인 동영상도), 그런 파일을 통째로 훑으면 '깨졌다' 가 몇십 초 늦게
     * 나올 뿐이다. 명세는 머리를 첫 1024 바이트 안에 두게 한다.
     *
     * @throws PdfSyntaxException 파일이 PDF 로 읽히지 않는다.
     */
    fun inspect(src: PdfSource, checkCancelled: () -> Unit = {}): Inspection {
        if (!hasPdfHeader(src)) return Inspection.NotEncrypted
        val file = PdfFile.open(src, checkCancelled)
        val encrypt = Resolver(file).resolve(file.trailer["Encrypt"]) as? PdfDict
            ?: return Inspection.NotEncrypted
        val security = try {
            PdfSecurity.open(encrypt, idOf(file.trailer), CharArray(0))
        } catch (e: UnsupportedEncryption) {
            return Inspection.Unsupported(e.message ?: "")
        } ?: return Inspection.NeedsPassword
        security.wipe()
        return Inspection.OpenWithoutPassword
    }

    private fun hasPdfHeader(src: PdfSource): Boolean {
        val head = ByteArray(minOf(1024L, src.size).toInt())
        var done = 0
        while (done < head.size) {
            val n = src.read(done.toLong(), head, done, head.size - done)
            if (n <= 0) break
            done += n
        }
        return String(head, 0, done, Charsets.ISO_8859_1).contains("%PDF-")
    }

    /**
     * 트레일러 `/ID` 의 첫 문자열. 키를 만드는 데 들어간다(R2~R4). 없으면 빈 값 —
     * 명세는 암호 문서에 `/ID` 를 요구하지만, 없는 파일도 빈 값으로 잠근 도구가 있다.
     */
    private fun idOf(trailer: PdfDict): ByteArray =
        ((trailer["ID"] as? PdfArray)?.items?.firstOrNull() as? PdfStr)?.bytes ?: ByteArray(0)

    /**
     * @param maxOutput 결과의 바이트 상한. 넘으면 [TooLarge].
     * @param checkCancelled 객체 하나마다 부른다. 취소면 여기서 던져라.
     * @throws PdfSyntaxException 파일이 PDF 로 읽히지 않는다.
     */
    fun decrypt(
        src: PdfSource,
        password: CharArray,
        sink: PdfSink,
        maxOutput: Long,
        checkCancelled: () -> Unit = {},
    ): Result {
        val file = PdfFile.open(src, checkCancelled)
        val trailer = file.trailer
        val encryptRef = trailer["Encrypt"]
        val resolver = Resolver(file)
        val encrypt = resolver.resolve(encryptRef) as? PdfDict ?: return Result.NotEncrypted
        val security = try {
            PdfSecurity.open(encrypt, idOf(trailer), password)
        } catch (e: UnsupportedEncryption) {
            return Result.Unsupported(e.message ?: "")
        } ?: return Result.WrongPassword

        try {
            resolver.security = security
            Writer(file, resolver, security, sink, maxOutput, checkCancelled)
                .write((encryptRef as? PdfRef)?.num)
            return Result.Ok
        } finally {
            security.wipe()
        }
    }

    /**
     * 간접 참조를 값으로. `/Length 12 0 R` 과 `/Encrypt 5 0 R` 을 푸는 데 쓴다.
     *
     * 객체 스트림 안의 값도 찾는다 — 그러려면 그 스트림을 풀어야 하므로 복호화가 세워진
     * 뒤에만 된다([security]).
     */
    private class Resolver(private val file: PdfFile) {
        var security: PdfSecurity? = null
        private val objStreams = HashMap<Int, ObjStm?>()
        private var depth = 0

        fun resolve(obj: PdfObj?): PdfObj? {
            if (obj !is PdfRef) return obj
            if (depth > 16) return null // 참조가 참조를 가리키는 사슬
            depth++
            try {
                return when (val e = file.entries[obj.num]) {
                    // **머리의 번호가 참조와 다르면 없는 것이다.** xref 가 밀린 파일에서 엉뚱한
                    // 객체를 `/Encrypt` 로 읽으면 암호를 묻지 않거나 틀린 열쇠를 만든다.
                    is PdfFile.InFile -> file.readAt(e.offset)?.takeIf { it.num == obj.num }?.value
                        ?.let { resolve(it) }
                    is PdfFile.InStream -> objStream(e.stream)?.get(e.index)
                    else -> null
                }
            } finally {
                depth--
            }
        }

        fun objStream(num: Int): ObjStm? = objStreams.getOrPut(num) {
            val sec = security ?: return@getOrPut null
            val e = file.entries[num] as? PdfFile.InFile ?: return@getOrPut null
            val obj = file.readAt(e.offset) ?: return@getOrPut null
            if (!obj.isStream) return@getOrPut null
            val len = file.streamLength(obj) { resolve(it) }
            if (len > PdfFilters.MAX_DECODED) return@getOrPut null
            val raw = file.readBytes(obj.dataStart, len.toInt())
            val plain = sec.decrypt(obj.num, obj.gen, sec.methodFor(PdfSecurity.Kind.STREAM), raw)
            ObjStm.parse(obj.dict!!, plain)
        }
    }

    /**
     * 객체 스트림 하나를 푼 것. 머리의 `번호 오프셋` 쌍만 읽어 두고, **안의 값은 물을 때 그 자리에서** 읽는다.
     *
     * 값을 미리 다 읽으면 백만 칸짜리 스트림 하나가 객체 백만 개가 되어 메모리를 다 먹고(작은 압축 파일 하나로 — 독립 검토가 OOM 을
     * 냈다), 캐시에서 밀려났다 다시 풀 때마다 그 전부를 다시 읽는다. 읽은 값은 부르는 쪽(`PdfObjects`)의 객체 캐시가 기억한다.
     *
     * [shareValues] 면 읽은 값을 **오프셋마다** 기억해 같은 자리를 가리키는 칸에 같은 객체를 준다 — 칸 300 개가 8 MiB 문자열 한 자리를
     * 가리키는 84 KB 파일이 그 문자열을 300 벌 만들어 힙을 다 먹었다(독립 검토 2차). 기억은 [MEMO_WEIGHT] 까지이고 [weight] 에 든다.
     */
    class ObjStm private constructor(
        private val nums: IntArray,
        private val offs: IntArray,
        private val first: Int,
        private val data: ByteArray,
        shareValues: Boolean,
    ) {
        /** 칸 하나의 값([value] — 못 읽었으면 null), 그것을 읽느라 지나간 바이트([read] — 기억해 둔 자리면 0), 값의 어림 무게. */
        class Member(val value: PdfObj?, val read: Long, val weight: Long)

        private val memo: HashMap<Int, Member>? = if (shareValues) HashMap() else null
        private var memoWeight = 0L

        /** 캐시가 메모리를 재는 무게 — 푼 바이트와 번호표, 그리고 기억해 둔 값. */
        val weight: Long get() = data.size.toLong() + nums.size.toLong() * 8 + memoWeight

        fun get(index: Int): PdfObj? = member(index)?.value

        /** 칸 [index] — 없는 칸·자리가 데이터 밖이면 null. 읽다 실패한 자리도 [Member] 로 준다(값 null, 지나간 바이트는 센다). */
        fun member(index: Int): Member? {
            if (index !in nums.indices) return null
            val at = first.toLong() + offs[index]
            if (at < 0 || at >= data.size) return null
            memo?.get(at.toInt())?.let { return Member(it.value, 0, it.weight) }
            val cursor = PdfCursor(ByteArraySource(data), at)
            val value = try {
                PdfParser(cursor).parseObject()?.takeIf { it !is PdfParser.Keyword }
            } catch (e: PdfSyntaxException) {
                null
            }
            val read = (cursor.pos - at).coerceAtLeast(1L)
            val m = Member(value, read, value?.let(PdfWeights::of) ?: 0L)
            if (memo != null && memoWeight + m.weight <= MEMO_WEIGHT) {
                memo[at.toInt()] = m
                memoWeight += m.weight + MEMO_ENTRY
            }
            return m
        }

        /** 칸 [index] 의 값이 놓인 자리(데이터 안). 같은 자리면 같은 값이다. */
        fun offsetOf(index: Int): Int? {
            if (index !in nums.indices) return null
            val at = first.toLong() + offs[index]
            return if (at < 0 || at >= data.size) null else at.toInt()
        }

        fun numbers(): IntArray = nums

        companion object {
            /** 스트림 하나가 기억해 두는 값의 무게 상한. 넘으면 기억하지 않고 물을 때마다 다시 읽는다(부르는 쪽이 읽은 바이트를 센다). */
            private const val MEMO_WEIGHT = 32L * 1024 * 1024
            private const val MEMO_ENTRY = 48L

            fun parse(dict: PdfDict, plain: ByteArray, shareValues: Boolean = false): ObjStm? {
                val data = try {
                    PdfFilters.decode(dict, plain)
                } catch (e: PdfSyntaxException) {
                    return null
                }
                val n = dict.int("N") ?: return null
                val first = dict.int("First") ?: return null
                if (n < 0 || n > 1_000_000 || first < 0 || first > data.size) return null
                val p = PdfParser(PdfCursor(ByteArraySource(data), 0))
                val nums = IntArray(n)
                val offs = IntArray(n)
                try {
                    for (i in 0 until n) {
                        nums[i] = (p.parseObject() as? PdfNum)?.int ?: return null
                        offs[i] = (p.parseObject() as? PdfNum)?.int ?: return null
                    }
                } catch (e: PdfSyntaxException) {
                    return null
                }
                return ObjStm(nums, offs, first, data, shareValues)
            }
        }
    }

    private class Writer(
        private val file: PdfFile,
        private val resolver: Resolver,
        private val sec: PdfSecurity,
        private val sink: PdfSink,
        private val maxOutput: Long,
        private val checkCancelled: () -> Unit,
    ) {
        private val offsets = HashMap<Int, Pair<Long, Int>>()
        /** xref 에 없었는데 객체 스트림을 풀다가 알게 된 번호. 다시 세운 xref 에서 쓴다. */
        private val discovered = HashMap<Int, PdfFile.InStream>()

        fun write(encryptNum: Int?) {
            emit("%PDF-1.7\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))

            val inFile = file.entries.entries
                .filter { it.value is PdfFile.InFile }
                .sortedBy { (it.value as PdfFile.InFile).offset }
            for ((num, entry) in inFile) {
                checkCancelled()
                if (num == encryptNum) continue
                val e = entry as PdfFile.InFile
                val obj = try {
                    file.readAt(e.offset)
                } catch (x: PdfSyntaxException) {
                    null
                } ?: continue
                // 헤더의 번호가 xref 와 다르면 xref 가 틀린 것이다. **키는 헤더의 번호로 만든다**
                // — 암호화한 쪽이 쓴 번호가 그것이다.
                if (obj.num != num && file.entries[obj.num] != null && !file.rebuilt) continue
                if (offsets.containsKey(obj.num)) continue
                val dict = obj.dict
                if (dict?.name("Type") == "XRef") continue // 옛 xref 스트림은 옮기지 않는다
                writeObject(obj)
            }

            val streamEntries = HashMap<Int, PdfFile.InStream>()
            for ((num, e) in file.entries) if (e is PdfFile.InStream) streamEntries[num] = e
            for ((num, e) in discovered) streamEntries.putIfAbsent(num, e)
            // 객체 스트림 안의 번호가 그 스트림을 우리가 실제로 썼을 때만 유효하다.
            streamEntries.entries.removeAll { (num, e) -> !offsets.containsKey(e.stream) || offsets.containsKey(num) }

            writeXref(streamEntries)
        }

        private fun writeObject(obj: PdfFile.Indirect) {
            val start = sink.position
            val header = ByteArrayOutputStream()
            header.writeAscii("${obj.num} ${obj.gen} obj\n")

            if (!obj.isStream) {
                val value = decryptValue(obj.value, obj.num, obj.gen)
                PdfWriter.write(value, header)
                header.writeAscii("\nendobj\n")
                emit(header.toByteArray())
                offsets[obj.num] = start to obj.gen
                return
            }

            val dict = obj.dict!!
            val rawLen = file.streamLength(obj) { resolver.resolve(it) }
            val method = streamMethod(dict)
            val outDict = decryptValue(dict, obj.num, obj.gen) as PdfDict
            stripCryptFilter(outDict)
            // `/Length` 는 **자리 채움**으로 쓰고 나중에 고친다. AES 는 채움을 떼기 전까지
            // 평문 길이를 모른다. 앞자리 0 은 명세가 허락하는 정수 표기다.
            //
            // **사전의 맨 끝에 둔다.** 그 자리는 아래에서 '마지막으로 나타난 자리 채움' 으로
            // 찾는데, 사전 안의 16진 문자열이 0 바이트를 다섯 개 품으면 같은 글자
            // `0000000000` 이 된다 — `/Length` 가 가운데 있으면 엉뚱한 문자열을 고쳐 쓴다.
            outDict.map.remove("Length")
            outDict.map["Length"] = PdfNum(LENGTH_PLACEHOLDER)
            PdfWriter.write(outDict, header)
            val bytes = header.toByteArray()
            val placeholderAt = start + lastIndexOf(bytes, LENGTH_PLACEHOLDER.toByteArray(Charsets.ISO_8859_1))
            emit(bytes)
            emit("\nstream\n".toByteArray(Charsets.ISO_8859_1))

            val isObjStm = dict.name("Type") == "ObjStm"
            val keep = if (isObjStm) ByteArrayOutputStream() else null
            val key = sec.objectKey(obj.num, obj.gen, method)
            val d = sec.decryptor(method, key)
            var written = 0L
            val buf = ByteArray(64 * 1024)
            var pos = obj.dataStart
            val end = obj.dataStart + rawLen
            while (pos < end) {
                checkCancelled()
                val want = minOf(buf.size.toLong(), end - pos).toInt()
                val got = file.readBytes(pos, want)
                if (got.isEmpty()) break
                pos += got.size
                val plain = d.update(got, 0, got.size)
                if (plain.isNotEmpty()) {
                    emit(plain)
                    keep?.write(plain)
                    written += plain.size
                }
            }
            val tail = d.finish()
            if (tail.isNotEmpty()) {
                emit(tail)
                keep?.write(tail)
                written += tail.size
            }
            key.takeIf { method != PdfSecurity.Method.AES256 }?.fill(0)
            emit("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
            if (placeholderAt >= start) {
                sink.patch(placeholderAt, written.toString().padStart(LENGTH_PLACEHOLDER.length, '0')
                    .toByteArray(Charsets.ISO_8859_1))
            }
            offsets[obj.num] = start to obj.gen

            // 객체 스트림이면 안의 번호를 챙긴다 — xref 가 무너져 다시 세운 경우 그 번호들은
            // xref 어디에도 없다. **xref 가 아는 번호는 건드리지 않는다** — 새 판이 지운(Free)
            // 번호를 옛 객체 스트림에서 되살리면 지운 주석·양식이 다시 살아난다.
            if (keep != null) {
                val stm = ObjStm.parse(dict, keep.toByteArray())
                stm?.numbers()?.forEachIndexed { i, n ->
                    if (n > 0 && file.entries[n] == null) {
                        discovered.putIfAbsent(n, PdfFile.InStream(obj.num, i))
                    }
                }
            }
        }

        // 어느 스트림을 어떤 방식으로 푸는가·`/Crypt` 떼기·문자열 풀기는 쪽 뽑기·합치기와 한 벌이다([PdfCryptRules]).
        private fun streamMethod(dict: PdfDict): PdfSecurity.Method = PdfCryptRules.streamMethod(sec, dict)

        private fun stripCryptFilter(dict: PdfDict) = PdfCryptRules.stripCryptFilter(dict)

        private fun decryptValue(v: PdfObj, num: Int, gen: Int): PdfObj = PdfCryptRules.decryptStrings(sec, v, num, gen)

        /**
         * xref 를 쓴다. **쓴 번호만 소구역으로 적는다** — 0 부터 가장 큰 번호까지 빽빽하게 쓰면
         * 번호 하나를 8,388,600 으로 적은 몇 KB 짜리 파일이 168 MB 의 표를 메모리 파일에
         * 만든다. 빈 번호는 적지 않으면 읽는 쪽이 '없는 객체' 로 본다
         * (명세 7.5.4 — 소구역 사이의 번호는 정의되지 않은 객체다). `/Size` 는 숫자일 뿐이다.
         */
        private fun writeXref(streamEntries: Map<Int, PdfFile.InStream>) {
            val maxNum = maxOf(offsets.keys.maxOrNull() ?: 0, streamEntries.keys.maxOrNull() ?: 0)
            val trailer = LinkedHashMap<String, PdfObj>()
            file.trailer["Root"]?.let { trailer["Root"] = it }
            file.trailer["Info"]?.let { trailer["Info"] = it }
            file.trailer["ID"]?.let { trailer["ID"] = it }

            if (streamEntries.isEmpty()) {
                // 고전 xref 표. 가장 널리 읽힌다.
                val size = maxNum + 1
                trailer["Size"] = PdfNum(size.toString())
                val xrefAt = sink.position
                val out = ByteArrayOutputStream()
                out.writeAscii("xref\n0 1\n0000000000 65535 f \n")
                for (run in runsOf(offsets.keys)) {
                    checkCancelled()
                    out.writeAscii("${run.first} ${run.last - run.first + 1}\n")
                    for (n in run) {
                        val o = offsets.getValue(n)
                        // 로캘을 박는다 — 기기의 로캘을 따르면 아랍어·페르시아어 등에서 `%d` 가 그 문자의 숫자를 써서
                        // xref 가 깨진다(lint `DefaultLocale`).
                        out.writeAscii(String.format(java.util.Locale.ROOT, "%010d %05d n \n", o.first, o.second))
                    }
                    if (out.size() > 1 shl 20) {
                        emit(out.toByteArray())
                        out.reset()
                    }
                }
                out.writeAscii("trailer\n")
                PdfWriter.write(PdfDict(trailer), out)
                out.writeAscii("\nstartxref\n$xrefAt\n%%EOF\n")
                emit(out.toByteArray())
                return
            }

            // 객체 스트림이 있으면 xref **스트림**이어야 한다 — 표로는 '스트림 안의 몇 번째' 를
            // 적을 수 없다.
            val xrefNum = maxNum + 1
            val size = xrefNum + 1
            val xrefAt = sink.position
            val offW = if (xrefAt + (1 shl 20) > 0xFFFFFFFFL) 8 else 4
            val numbers = HashSet<Int>(offsets.size + streamEntries.size + 2)
            numbers.add(0)
            numbers.addAll(offsets.keys)
            numbers.addAll(streamEntries.keys)
            numbers.add(xrefNum)
            val runs = runsOf(numbers)
            val rec = ByteArrayOutputStream(numbers.size * (1 + offW + 2))
            val index = ArrayList<PdfObj>(runs.size * 2)
            for (run in runs) {
                checkCancelled()
                index.add(PdfNum(run.first.toString()))
                index.add(PdfNum((run.last - run.first + 1).toString()))
                for (n in run) {
                    val o = offsets[n]
                    val s = streamEntries[n]
                    when {
                        n == xrefNum -> writeRec(rec, 1, xrefAt, offW, 0)
                        o != null -> writeRec(rec, 1, o.first, offW, o.second)
                        s != null -> writeRec(rec, 2, s.stream.toLong(), offW, s.index)
                        else -> writeRec(rec, 0, 0, offW, 65535) // 0 번뿐이다
                    }
                }
            }
            val data = rec.toByteArray()
            trailer["Type"] = PdfName("XRef")
            trailer["Size"] = PdfNum(size.toString())
            trailer["Index"] = PdfArray(index)
            trailer["W"] = PdfArray(mutableListOf(PdfNum("1"), PdfNum(offW.toString()), PdfNum("2")))
            trailer["Length"] = PdfNum(data.size.toString())
            val out = ByteArrayOutputStream()
            out.writeAscii("$xrefNum 0 obj\n")
            PdfWriter.write(PdfDict(trailer), out)
            out.writeAscii("\nstream\n")
            emit(out.toByteArray())
            emit(data)
            emit("\nendstream\nendobj\nstartxref\n$xrefAt\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        }

        /** 번호들을 이어진 구간으로 묶는다. 1,2,3,7,8 → [1..3, 7..8]. */
        private fun runsOf(numbers: Collection<Int>): List<IntRange> {
            val sorted = numbers.sorted()
            val out = ArrayList<IntRange>()
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
                out.add(sorted[i]..sorted[j])
                i = j + 1
            }
            return out
        }

        private fun writeRec(out: ByteArrayOutputStream, type: Int, f2: Long, w2: Int, f3: Int) {
            out.write(type)
            for (i in w2 - 1 downTo 0) out.write(((f2 shr (8 * i)) and 0xFF).toInt())
            out.write((f3 shr 8) and 0xFF)
            out.write(f3 and 0xFF)
        }

        private fun emit(b: ByteArray) {
            if (sink.position + b.size > maxOutput) throw TooLarge()
            sink.write(b, 0, b.size)
        }

        private fun lastIndexOf(hay: ByteArray, needle: ByteArray): Int {
            outer@ for (i in hay.size - needle.size downTo 0) {
                for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))

    /** 열 자리 — 10 GB 까지 담는다. */
    private const val LENGTH_PLACEHOLDER = "0000000000"
}

/** 복호화 결과를 받는 곳. 길이 자리를 나중에 고쳐 써야 해서 [patch] 가 있다. */
internal interface PdfSink {
    val position: Long
    fun write(b: ByteArray, off: Int, len: Int)
    fun patch(at: Long, b: ByteArray)
}

/** 시험용. 실제 앱은 memfd 에 쓴다(`MemfdSink`). */
internal class MemorySink : PdfSink {
    private var buf = ByteArray(64 * 1024)
    private var len = 0
    override val position: Long get() = len.toLong()

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(this.len + len)
        System.arraycopy(b, off, buf, this.len, len)
        this.len += len
    }

    override fun patch(at: Long, b: ByteArray) {
        System.arraycopy(b, 0, buf, at.toInt(), b.size)
    }

    fun toByteArray(): ByteArray = buf.copyOf(len)

    private fun ensure(n: Int) {
        if (n <= buf.size) return
        var cap = buf.size
        while (cap < n) cap *= 2
        buf = buf.copyOf(cap)
    }
}
