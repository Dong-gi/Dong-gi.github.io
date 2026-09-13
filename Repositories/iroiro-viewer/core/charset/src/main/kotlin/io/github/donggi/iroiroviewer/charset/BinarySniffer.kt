package io.github.donggi.iroiroviewer.charset

/**
 * 이것을 텍스트로 열어도 되는가.
 *
 * ## 왜 '제어문자 비율' 이 아닌가
 *
 * 흔한 방법은 앞부분에서 제어문자 비율을 세어 임계값과 견주는 것인데, 그 임계값에
 * 근거가 없다. 5%? 30%? 어느 쪽이든 **UTF-16 텍스트가 걸린다** — 영문 UTF-16 은 바이트의
 * 절반이 `0x00` 이라 어떤 비율 기준으로도 이진으로 보인다.
 *
 * 그래서 판정 순서를 못 박는다.
 *
 * 1. **BOM 을 먼저 본다.** BOM 이 있으면 텍스트다. 끝.
 * 2. BOM 이 없으면 **UTF-16 후보**인지 본다(짝·홀 자리 한쪽에 `0x00` 이 몰려 있는가).
 * 3. 그 다음에야 `0x00` 하나로 이진을 가른다.
 *
 * 이 순서를 뒤집으면 BOM 없는 UTF-16 문서가 "이진 파일입니다" 로 거부된다.
 */
object BinarySniffer {

    /** 이진 판정에 쓰는 앞머리. 더 봐도 답이 달라지지 않는다. */
    const val SNIFF_BYTES = 8 * 1024

    sealed interface Verdict {
        /** 텍스트로 열어도 된다. [utf16Hint] 가 있으면 BOM 없는 UTF-16 으로 보인다. */
        data class Text(val utf16Hint: TextEncoding?) : Verdict

        /** 열지 않는다. */
        data object Binary : Verdict
    }

    /**
     * @param head 파일 앞머리. [SNIFF_BYTES] 만큼이면 충분하다.
     * @param length 실제로 채워진 바이트 수.
     */
    fun sniff(head: ByteArray, length: Int = head.size): Verdict {
        if (length == 0) return Verdict.Text(null)

        // ① BOM 이 있으면 텍스트다. 더 볼 것이 없다.
        //    우리가 못 읽는 인코딩(UTF-32)이어도 **이진은 아니다** — 그 구분은 판정기가
        //    하고, 화면은 "지원하지 않습니다" 로 끝낸다.
        Bom.detect(head, length)?.let { return Verdict.Text(it.encoding) }

        // ② BOM 없는 UTF-16 인가.
        //
        // **'UTF-16 으로 보이는가' 와 '어느 방향인가' 는 다른 질문이다.** 둘을 한 값으로
        // 합치면, 방향을 못 정한 진짜 UTF-16 문서가 '힌트 없음' 을 거쳐 ③에서 이진으로
        // 거부된다 — 이 클래스가 가장 피해야 하는 실패다.
        if (looksLikeUtf16(head, length)) return Verdict.Text(utf16Hint(head, length))

        // ③ 이제서야 NUL 로 가른다. 여기까지 왔으면 UTF-16 이 아니고,
        //    ASCII 호환 인코딩의 텍스트에는 NUL 이 없다.
        for (i in 0 until length) {
            if (head[i] == 0.toByte()) return Verdict.Binary
        }
        return Verdict.Text(null)
    }

    /**
     * UTF-16 으로 **볼 만한가.** 방향은 묻지 않는다.
     *
     * 조건 둘이다 — 널이 있고(없으면 UTF-16 이 아니다. [utf16Hint] 주석 참고),
     * 두 방향 가운데 적어도 하나가 **엄격하게** 읽히며 그 결과에 C0 제어문자가 없다.
     */
    private fun looksLikeUtf16(head: ByteArray, length: Int): Boolean {
        val n = length and 1.inv()
        if (n < MIN_UTF16_SAMPLE) return false
        var hasNul = false
        for (i in 0 until n) {
            if (head[i] == 0.toByte()) {
                hasNul = true
                break
            }
        }
        if (!hasNul) return false
        return decodedAsText(head, n, TextEncoding.UTF_16LE) != null ||
            decodedAsText(head, n, TextEncoding.UTF_16BE) != null
    }

    /**
     * BOM 없는 UTF-16 인가. **바이트 무늬를 보지 않고 실제로 디코드해 본다.**
     *
     * ## 널 바이트의 자리로 판정하려다 틀린 기록
     *
     * 처음에는 '널이 짝수 자리에 몰려 있으면 BE, 홀수면 LE' 로 짰다. 그것이 영문 UTF-16
     * 에서는 맞지만 **한국어에서 무너진다** — `가` 는 `U+AC00` 이라 낮은 바이트가 `00`
     * 이고, 그래서 LE 에서 널이 *짝수* 자리에 나온다. 공백(`U+0020`)이 섞이면 널이 양쪽
     * 자리에 모두 생겨 어느 쪽으로도 판정되지 않고, 결국 한글 UTF-16 문서가
     * **"이진 파일입니다" 로 거부**됐다. 단위 시험이 그것을 잡았다.
     *
     * ## 지금 쓰는 판정
     *
     * **엄격 디코드가 되는가**를 본다. 무작위 바이트를 UTF-16 으로 읽으면 짝 없는
     * 서러게이트(`D800..DFFF`)를 만날 확률이 단위당 1/32 이라, 수천 단위를 읽는 동안
     * 거의 확실히 걸린다. 그리고 진짜 텍스트에는 C0 제어문자가 사실상 없다.
     *
     * 이 둘을 함께 보면 바이트 무늬 추측보다 **근거가 있다** — 추측이 아니라 그 인코딩의
     * 규칙을 실제로 적용해 보는 것이기 때문이다.
     */
    private fun utf16Hint(head: ByteArray, length: Int): TextEncoding? {
        if (length < MIN_UTF16_SAMPLE) return null
        // 짝수 길이로 맞춘다. 홀수면 마지막 한 바이트는 잘린 단위라 판정에서 뺀다.
        val n = length and 1.inv()
        if (n < MIN_UTF16_SAMPLE) return null

        // ★ **널이 없으면 UTF-16 이 아니다.** 이것이 이 함수의 첫 관문이어야 한다.
        //
        // 처음에는 이 관문 없이 '엄격 디코드가 되는가' 만 물었는데, 그러면
        // **ASCII 소스·UTF-8 한국어·Shift_JIS·GB18030 이 전부 UTF-16LE 로 판정된다** —
        // 그 바이트열을 16비트 단위로 읽어도 한자·기호가 될 뿐 규칙을 어기지 않기 때문이다.
        // 판정(텍스트/이진)은 맞았지만 힌트가 틀렸고, 그것을 믿는 쪽은 UTF-8 파일을
        // UTF-16 으로 읽는다. 검토가 재현해 왔고 회귀 시험으로 박았다.
        //
        // 실제 UTF-16 텍스트에는 널이 반드시 있다 — 줄바꿈(`U+000A`) 하나, 공백 하나,
        // ASCII 글자 하나면 충분하다. 널이 하나도 없는 UTF-16 파일은 줄바꿈도 공백도
        // ASCII 도 없는 순수 CJK 한 줄뿐이고, 그것은 판정기가 다룬다.
        var evenNul = 0
        var oddNul = 0
        for (i in 0 until n) {
            if (head[i] == 0.toByte()) {
                if (i % 2 == 0) evenNul++ else oddNul++
            }
        }
        if (evenNul == 0 && oddNul == 0) return null

        val le = decodedAsText(head, n, TextEncoding.UTF_16LE)
        val be = decodedAsText(head, n, TextEncoding.UTF_16BE)
        if (le == null && be == null) return null
        if (le == null) return TextEncoding.UTF_16BE
        if (be == null) return TextEncoding.UTF_16LE

        // **둘 다 깨끗하게 읽힌다.** 방향을 가릴 근거가 둘 있다.
        //
        // ① 줄바꿈을 담는 쪽. 사람이 쓴 텍스트에는 줄바꿈이 있고, 반대로 읽으면 그것이
        //    `U+0A00`(구르무키 영역) 같은 엉뚱한 글자가 된다.
        val leNl = le.contains('\n')
        val beNl = be.contains('\n')
        if (leNl != beNl) return if (leNl) TextEncoding.UTF_16LE else TextEncoding.UTF_16BE

        // ② 널의 자리. ASCII 가 섞인 글에서 널은 LE 면 홀수, BE 면 짝수 자리에 놓인다.
        //    **다만 순수 한글에서는 뒤집힌다** — `가`(U+AC00)는 LE 에서 낮은 바이트가 0 이라
        //    널이 짝수 자리에 온다. 그래서 이 근거는 차이가 뚜렷할 때만 쓴다.
        return when {
            oddNul >= evenNul * 3 -> TextEncoding.UTF_16LE
            evenNul >= oddNul * 3 -> TextEncoding.UTF_16BE
            // 근거가 모자라다. **말하지 않는다** — 여기서 할 일은 이진으로 거부하지 않는
            // 것이고(그 판단은 이미 끝났다), 방향은 판정기가 본문 전체를 보고 정한다.
            else -> null
        }
    }

    /** 이 방향으로 엄격하게 읽히면 그 결과를, 아니면 null 을. */
    private fun decodedAsText(head: ByteArray, n: Int, enc: TextEncoding): String? {
        val cs = enc.charset ?: return null
        val text = strictDecode(head, n, cs) ?: return null
        return if (text.isNotEmpty() && looksLikeText(text)) text else null
    }

    /** 엄격 디코드. 규칙에 어긋나는 바이트가 하나라도 있으면 null. */
    private fun strictDecode(bytes: ByteArray, length: Int, cs: java.nio.charset.Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes, 0, length))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }

    /**
     * 사람이 읽는 글로 보이는가.
     *
     * 기준은 **C0 제어문자가 없는 것** 하나다(탭·줄바꿈·캐리지리턴은 뺀다).
     * '인쇄 가능한 글자의 비율' 을 쓰지 않는 이유는, 무작위 바이트를 UTF-16 으로 읽으면
     * 대부분 한자·사용자 영역 글자가 되어 '인쇄 가능' 으로 세어지기 때문이다.
     */
    private fun looksLikeText(text: String): Boolean {
        for (c in text) {
            if (c == '\t' || c == '\n' || c == '\r') continue
            if (c.code < 0x20 || c.code == 0x7F) return false
        }
        return true
    }

    private const val MIN_UTF16_SAMPLE = 16
}
