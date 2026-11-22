package io.github.donggi.iroiroviewer.docview.pdf.crypt

/**
 * 암호 PDF 를 평문으로 옮겨 쓰는 **규칙** — 어느 스트림을 어떤 방식으로 푸는가, 무엇을 풀지 않고 두는가(명세 7.6).
 *
 * 복호기([PdfDecryptor] — 문서 전체를 메모리 파일에 평문으로)와 쪽 뽑기·합치기(`pdf/edit` — 고른 쪽만 새 파일에)가 함께 쓴다.
 * 두 벌로 두면 한쪽만 고쳐지는 날 그쪽 결과만 틀린 바이트가 된다.
 */
internal object PdfCryptRules {

    /** 이 스트림에 쓰는 암호 방식. [PdfDecryptor] 머리말의 표 그대로다. */
    fun streamMethod(sec: PdfSecurity, dict: PdfDict): PdfSecurity.Method {
        val filters = filterNames(dict)
        val cryptAt = filters.indexOf("Crypt")
        if (cryptAt >= 0) {
            val parms = parmsAt(dict, cryptAt)
            val name = parms?.name("Name") ?: "Identity"
            return sec.methodByName(name) ?: PdfSecurity.Method.IDENTITY
        }
        if (dict.name("Type") == "Metadata" && !sec.encryptMetadata && sec.revision >= 4) {
            return PdfSecurity.Method.IDENTITY
        }
        if (dict.name("Type") == "EmbeddedFile") return sec.methodFor(PdfSecurity.Kind.EMBEDDED_FILE)
        return sec.methodFor(PdfSecurity.Kind.STREAM)
    }

    /** `/Filter` 목록에서 `/Crypt` 를 뺀다(제자리에서 고친다). 우리가 이미 풀었으므로 읽는 쪽이 다시 보면 안 된다. */
    fun stripCryptFilter(dict: PdfDict) {
        val filters = filterNames(dict)
        val at = filters.indexOf("Crypt")
        if (at < 0) return
        when (val f = dict["Filter"]) {
            is PdfName -> {
                dict.map.remove("Filter")
                dict.map.remove("DecodeParms")
            }
            is PdfArray -> {
                f.items.removeAt(at)
                (dict["DecodeParms"] as? PdfArray)?.items?.let { if (at < it.size) it.removeAt(at) }
                if (f.items.isEmpty()) {
                    dict.map.remove("Filter")
                    dict.map.remove("DecodeParms")
                }
            }
            else -> Unit
        }
    }

    /**
     * 값 안의 문자열을 전부 푼다. **새 값**을 돌려준다(원본은 건드리지 않는다). 서명 사전의 `/Contents` 는 두다(명세 — 서명 바이트는
     * 암호화하지 않는다).
     *
     * @param lenient 참이면 풀지 못한 문자열(잘린 AES 블록)을 빈 문자열로 — 그 문자열만 버린다. 거짓이면 던진다(복호기 — 그쪽은 문서
     *   전체를 그리려는 것이라 실패를 위로 올린다).
     */
    fun decryptStrings(sec: PdfSecurity, v: PdfObj, num: Int, gen: Int, lenient: Boolean = false, depth: Int = 0): PdfObj {
        if (depth > PdfParser.MAX_DEPTH) return v
        return when (v) {
            is PdfStr -> try {
                PdfStr(sec.decrypt(num, gen, sec.methodFor(PdfSecurity.Kind.STRING), v.bytes))
            } catch (e: RuntimeException) {
                if (!lenient) throw e
                PdfStr(ByteArray(0))
            }
            is PdfArray -> PdfArray(v.items.mapTo(ArrayList(v.items.size)) { decryptStrings(sec, it, num, gen, lenient, depth + 1) })
            is PdfDict -> {
                val isSig = v.name("Type") == "Sig" || v.name("Type") == "DocTimeStamp" ||
                    (v["ByteRange"] != null && v["Contents"] is PdfStr)
                val out = LinkedHashMap<String, PdfObj>(v.map.size)
                for ((k, x) in v.map) {
                    out[k] = if (isSig && k == "Contents") x else decryptStrings(sec, x, num, gen, lenient, depth + 1)
                }
                PdfDict(out)
            }
            else -> v
        }
    }

    private fun filterNames(dict: PdfDict): List<String> = when (val f = dict["Filter"]) {
        is PdfName -> listOf(f.name)
        is PdfArray -> f.items.map { (it as? PdfName)?.name ?: "" }
        else -> emptyList()
    }

    private fun parmsAt(dict: PdfDict, i: Int): PdfDict? = when (val p = dict["DecodeParms"]) {
        is PdfDict -> if (i == 0 || filterNames(dict).size == 1) p else null
        is PdfArray -> p.items.getOrNull(i) as? PdfDict
        else -> null
    }
}
