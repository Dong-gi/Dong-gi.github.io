package io.github.donggi.iroiroviewer.charset

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 이 바이트열이 어느 인코딩인가.
 *
 * ## 왜 직접 쓰는가
 *
 * ICU4J 와 juniversalchardet 은 이 프로젝트가 **채택하지 않기로 명시한** 것들이다.
 * 그래서 판정을 우리가 쓴다. 다행히 우리가 답해야 하는 질문은 훨씬 좁다 —
 * 요구사항의 넷(UTF-8·CP949/EUC-KR·Shift_JIS)에 중국어 둘을 더한 다섯이다.
 *
 * ## 판정의 뼈대
 *
 * 1. **BOM.** 있으면 끝. 근거가 파일 안에 적혀 있다.
 * 2. **첫 비ASCII 바이트를 찾는다.** 없으면 ASCII 이고, 어느 후보로 읽어도 같다.
 * 3. **그 자리부터 창을 뜬다.** 앞이 전부 ASCII 이므로 그 자리는 **모든 바이트지향
 *    후보에서 문자 경계**다 — 위상이 어긋나지 않는다.
 * 4. 후보마다 **엄격 디코드**(REPORT)를 해 본다. 실패하면 탈락 — 이것이 가장 센 신호다.
 * 5. 살아남은 것들을 **그 인코딩의 '본토 글자' 비율**로 점수 매긴다.
 *
 * ## 이 판정기가 못 하는 것 — 적고 지킨다
 *
 * - 앞 1 MiB 가 전부 ASCII 이고 그 뒤에 한글이 나오는 파일은 ASCII 로 판정된다.
 *   (UTF-8 로 읽히므로 CP949 부분만 깨진다. 사용자가 인코딩을 손으로 바꿀 수 있다.)
 * - CP949 와 GB18030 은 바이트 범위가 크게 겹쳐, **한글도 가나도 없는 짧은 한자 문서**는
 *   갈리지 않는다. 그때는 [Detection.Confidence.LOW] 로 내놓고 화면이 그렇게 말한다.
 * - 인코딩이 섞인 파일은 다루지 않는다.
 */
object CharsetDetector {

    /** 판정에 쓰는 창. 한글 표본이 많을수록 정확해지므로 넉넉히 본다. */
    const val DETECT_BYTES = 64 * 1024

    /** 첫 비ASCII 바이트를 여기까지 찾는다. 못 찾으면 ASCII 로 본다. */
    const val DETECT_SCAN_MAX = 1024 * 1024

    data class Detection(
        val encoding: TextEncoding,
        val confidence: Confidence,
        /** 왜 그렇게 골랐는가. **값이지 문장이 아니다** — 문장은 화면이 만든다. */
        val evidence: Evidence,
        /** 아깝게 진 후보. 화면이 '다르게 읽기' 로 먼저 보여 준다. */
        val runnerUp: TextEncoding?,
    ) {
        enum class Confidence { CERTAIN, HIGH, LOW }
    }

    sealed interface Evidence {
        /** BOM 이 적혀 있었다. */
        data class ByBom(val bom: Bom) : Evidence

        /** 비ASCII 바이트가 하나도 없었다. */
        data object AsciiOnly : Evidence

        /** 엄격 디코드를 통과한 유일한 후보였다. */
        data object OnlyCandidate : Evidence

        /** 본토 글자 비율로 이겼다. [score] 는 0~100. */
        data class ByScript(val score: Int, val runnerUpScore: Int) : Evidence

        /** UTF-8 로도 읽히지만 **한글로 읽는 편이 그럴듯했다.** */
        data class KoreanOverUtf8(val hangulRatio: Int) : Evidence

        /** 우리가 다루지 못하는 인코딩이다. */
        data class Unsupported(val bom: Bom) : Evidence
    }

    /**
     * @param head 파일 앞머리. [DETECT_SCAN_MAX] 만큼 주면 가장 정확하다.
     * @param length 실제로 채워진 바이트 수.
     */
    fun detect(head: ByteArray, length: Int = head.size): Detection {
        // ① BOM.
        Bom.detect(head, length)?.let { bom ->
            val enc = bom.encoding
            return if (enc == null) {
                // UTF-32 처럼 우리가 못 읽는 것. **읽는 척하지 않는다.**
                Detection(TextEncoding.UTF_8, Detection.Confidence.LOW, Evidence.Unsupported(bom), null)
            } else {
                Detection(enc, Detection.Confidence.CERTAIN, Evidence.ByBom(bom), null)
            }
        }

        // ② 첫 비ASCII 바이트.
        val scanEnd = minOf(length, DETECT_SCAN_MAX)
        var first = -1
        for (i in 0 until scanEnd) {
            if (head[i] < 0) {
                first = i
                break
            }
        }
        if (first < 0) {
            // 비ASCII 가 없다. UTF-8 로 읽으면 되고 어느 후보와도 같은 결과다.
            return Detection(TextEncoding.UTF_8, Detection.Confidence.CERTAIN, Evidence.AsciiOnly, null)
        }

        // ③ 그 자리부터 창. 앞이 전부 ASCII 라 여기는 모든 후보에서 문자 경계다.
        val end = minOf(length, first + DETECT_BYTES)
        val windowLen = end - first

        // ④ 엄격 디코드로 후보를 거른다.
        val survivors = ArrayList<Pair<TextEncoding, String>>(TextEncoding.autoCandidates.size)
        for (enc in TextEncoding.autoCandidates) {
            val text = strictDecodeWindow(head, first, windowLen, enc) ?: continue
            survivors += enc to text
        }

        if (survivors.isEmpty()) {
            // 아무 후보도 못 읽는다. 최후 수단으로 Latin-1 — 어떤 바이트열이든 받는다.
            return Detection(TextEncoding.LATIN1, Detection.Confidence.LOW, Evidence.OnlyCandidate, null)
        }

        // ⑤ **UTF-8 이 읽히면 그것이 답이다.**
        //
        // 레거시 바이트열 64 KiB 가 우연히 전부 유효한 UTF-8 일 확률은 사실상 0 이다 —
        // UTF-8 은 선행/후행 바이트 범위가 겹치지 않고 과잉 인코딩·서러게이트를 금지해서,
        // 한 글자라도 어긋나면 즉시 탈락한다. 그래서 통과 자체가 아주 센 근거다.
        val utf8 = survivors.firstOrNull { it.first == TextEncoding.UTF_8 }?.second
        if (utf8 != null) {
            if (hasCjk(utf8)) {
                return Detection(TextEncoding.UTF_8, Detection.Confidence.HIGH, Evidence.OnlyCandidate, null)
            }
            // **예외 하나.** UTF-8 로도 읽히는 CP949 바이트열이 실재한다(2바이트 조합의 약 6%).
            // 짧은 한국어 문서가 그렇게 걸리면 라틴 기호 더미가 된다. UTF-8 결과에 CJK 가
            // 하나도 없고 한글로 읽으면 또렷하면 한글을 고른다 — zip 파일명에서 배운 규칙이다.
            val korean = survivors.firstOrNull { it.first == TextEncoding.CP949 }?.second
            val ratio = korean?.let { hangulRatio(it) } ?: 0
            if (ratio >= KOREAN_OVERRIDE_RATIO) {
                return Detection(
                    TextEncoding.CP949,
                    Detection.Confidence.HIGH,
                    Evidence.KoreanOverUtf8(ratio),
                    TextEncoding.UTF_8,
                )
            }
            return Detection(TextEncoding.UTF_8, Detection.Confidence.HIGH, Evidence.OnlyCandidate, null)
        }

        if (survivors.size == 1) {
            return Detection(survivors[0].first, Detection.Confidence.HIGH, Evidence.OnlyCandidate, null)
        }

        // ⑥ 레거시끼리는 **그 언어에만 있는 글자**로 가른다.
        //
        // 이 판단은 **후보를 따로 볼 수 없다.** 일본어 Shift_JIS 를 중국어로 읽으면 전부
        // 한자가 되어 '한자 비율' 로는 만점이 나온다. 반대로 진짜 Shift_JIS 읽기는
        // 가나와 한자가 섞여 비율이 낮다. 후보 하나만 보고 점수를 매기면 **중국어가 이긴다**
        // (시험이 잡았다). 그래서 먼저 "전용 글자를 뚜렷하게 내놓은 후보가 있는가" 를
        // 가로로 묻고, 있으면 그들끼리만 겨루게 한다.
        val marked = survivors
            .map { (enc, text) -> Triple(enc, text, exclusiveRatio(enc, text)) }
            .filter { it.third >= EXCLUSIVE_MIN_RATIO }

        val pool = if (marked.isNotEmpty()) marked else
            survivors.map { (enc, text) -> Triple(enc, text, 0) }

        val scored = pool
            .map { (enc, text, marker) -> ScoredCandidate(enc, marker, homeRatio(enc, text)) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.marker }.thenByDescending { it.home })

        val best = scored[0]
        val second = scored.getOrNull(1)
        val confidence = when {
            best.marker >= 30 && (second == null || best.marker - second.marker >= 20) ->
                Detection.Confidence.HIGH
            best.home >= 80 && second == null -> Detection.Confidence.HIGH
            else -> Detection.Confidence.LOW
        }
        return Detection(
            best.encoding,
            confidence,
            Evidence.ByScript(maxOf(best.marker, best.home), second?.let { maxOf(it.marker, it.home) } ?: 0),
            second?.encoding,
        )
    }

    private data class ScoredCandidate(val encoding: TextEncoding, val marker: Int, val home: Int)

    /**
     * **그 언어에만 있는 글자**의 비율(0~100).
     *
     * 한글은 한국어 인코딩에만, 가나는 일본어 인코딩에만 있다. 한 글자만 보여도 언어가
     * 갈린다. 중국어 인코딩에는 그런 글자가 없으므로 0 이고, 그래서 한글·가나가 보이는
     * 후보가 있으면 중국어는 겨루기에서 빠진다.
     */
    private fun exclusiveRatio(enc: TextEncoding, text: String): Int {
        var nonAscii = 0
        var mark = 0
        for (c in text) {
            if (c.code < 0x80) continue
            nonAscii++
            when (enc) {
                TextEncoding.CP949 -> if (isHangul(c)) mark++
                TextEncoding.SHIFT_JIS -> if (isKana(c)) mark++
                else -> Unit
            }
        }
        if (nonAscii == 0) return 0
        // **한글이 나온다고 한국어인 것은 아니다.** CP949 의 2바이트 영역이 한글로
        // 빽빽해서, 중국어 GB18030 바이트를 CP949 로 읽으면 한글이 잔뜩 나온다.
        // 아래 검사가 그 둘을 가른다.
        if (enc == TextEncoding.CP949 && !looksLikeRealKorean(text)) return 0
        return (mark * 100) / nonAscii
    }

    /**
     * 이 한글이 **진짜 한국어인가, 아니면 다른 언어를 잘못 읽은 찌꺼기인가.**
     *
     * ## 받침으로 가른다 — 빈도표 없이
     *
     * 한글 음절은 `0xAC00 + (초성×21 + 중성)×28 + 종성` 으로 배열되어 있고, 종성이 0 이면
     * 받침이 없다. **무작위 음절이라면 받침 없는 것이 1/28 = 3.6%** 여야 한다.
     * 그런데 실제 한국어 문장에서는 그것이 **절반을 넘는다**(받침 없는 글자가 훨씬 흔하다).
     *
     * 직접 재 보았다 — 한국어 문장 96자에서 **54.2%**, 같은 길이의 중국어를 CP949 로
     * 잘못 읽은 결과에서 **6.2%**(이론값 3.6% 에 가깝다). 표 하나 없이 열 배가 갈린다.
     *
     * 빈도표를 쓰지 않는 이유는 크기가 아니라 **근거**다. 빈도표는 어느 말뭉치로 뽑았는지에
     * 따라 값이 달라지고 그 말뭉치를 저장소에 넣어야 하지만, 이 규칙은 **한글 코드포인트
     * 배열 자체**에서 나온다.
     */
    private fun looksLikeRealKorean(text: String): Boolean {
        var hangul = 0
        var noJong = 0
        for (c in text) {
            if (!isHangul(c) || c !in '가'..'힣') continue
            hangul++
            if ((c.code - 0xAC00) % 28 == 0) noJong++
        }
        if (hangul < MIN_HANGUL_SAMPLE) return false
        return noJong * 100 >= hangul * NO_JONGSEONG_MIN_PERCENT
    }

    /**
     * 이 인코딩이 쓰이는 언어의 글자 비율(0~100). 사용자 영역으로 읽힌 것은 감점한다.
     *
     * **받침 검사가 '가짜 한글' 이라고 판정했으면 그 한글은 감점 대상이다.** 한쪽에서는
     * 노이즈라 해 놓고 다른 쪽에서 제 글자로 세면 두 계산이 서로 다른 말을 하게 되고,
     * 중국어를 CP949 로 읽은 결과가 '한자 92% + 한글' 로 만점을 받아 이긴다(시험이 잡았다).
     */
    private fun homeRatio(enc: TextEncoding, text: String): Int {
        val fakeHangul = enc == TextEncoding.CP949 && !looksLikeRealKorean(text)
        var nonAscii = 0
        var home = 0
        var alien = 0
        for (c in text) {
            if (c.code < 0x80) continue
            nonAscii++
            val isHome = when (enc) {
                TextEncoding.CP949 -> (isHangul(c) && !fakeHangul) || isHanja(c)
                TextEncoding.SHIFT_JIS -> isKana(c) || isHanja(c)
                TextEncoding.GB18030, TextEncoding.BIG5 -> isHanja(c)
                else -> false
            }
            when {
                isHome -> home++
                isHangul(c) && fakeHangul -> alien++
                c.code in 0xE000..0xF8FF -> alien++
                c == '�' -> alien++
            }
        }
        if (nonAscii == 0) return 0
        return (((home - alien) * 100) / nonAscii).coerceIn(0, 100)
    }

    /** 전용 글자가 이만큼은 있어야 '뚜렷하다' 고 본다. */
    private const val EXCLUSIVE_MIN_RATIO = 10

    /** 받침 판정을 하려면 한글이 이만큼은 있어야 한다. 몇 글자로는 비율이 뜻이 없다. */
    private const val MIN_HANGUL_SAMPLE = 8

    /**
     * 받침 없는 글자가 이 비율은 되어야 진짜 한국어로 본다.
     *
     * 실측 — 한국어 54.2%, 중국어를 CP949 로 오독 6.2%, 무작위 이론값 3.6%.
     * 25% 는 그 사이에 넉넉히 놓인다.
     */
    private const val NO_JONGSEONG_MIN_PERCENT = 25

    /**
     * 창을 엄격하게 디코드한다. **창 끝에서 잘린 문자는 실패로 세지 않는다.**
     *
     * `Charset.decode()` 를 쓰면 안 된다 — 그것은 `endOfInput = true` 라, 창 끝에 걸친
     * 멀티바이트 문자를 '깨진 바이트' 로 보고 **정답 인코딩을 탈락시킨다.** 판정 창을
     * 자르는 자리는 대개 문자 한가운데이므로 이것은 예외가 아니라 통상이다.
     *
     * 대신 `decode(in, out, endOfInput = false)` 로 부르고 UNDERFLOW(입력이 모자람)를
     * **성공으로** 센다. 잘린 꼬리는 그냥 안 읽으면 된다.
     */
    private fun strictDecodeWindow(
        head: ByteArray,
        offset: Int,
        length: Int,
        enc: TextEncoding,
    ): String? {
        val cs = enc.charset ?: return null
        val decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(head, offset, length)
        val output = java.nio.CharBuffer.allocate(length + 8)
        return try {
            val result = decoder.decode(input, output, false)
            // OVERFLOW 는 버퍼를 넉넉히 잡았으므로 오지 않는다. 오면 판정을 포기한다.
            if (result.isError) return null
            output.flip()
            output.toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    private fun isHangul(c: Char): Boolean =
        c in '가'..'힣' || c in '㄰'..'㆏'

    private fun isKana(c: Char): Boolean =
        c in '぀'..'ヿ' || c in '｡'..'ﾟ'

    private fun isHanja(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

    private fun hasCjk(text: String): Boolean =
        text.any { isHangul(it) || isKana(it) || isHanja(it) }

    private fun hangulRatio(text: String): Int {
        var nonAscii = 0
        var hangul = 0
        for (c in text) {
            if (c.code < 0x80) continue
            nonAscii++
            if (isHangul(c)) hangul++
        }
        return if (nonAscii == 0) 0 else (hangul * 100) / nonAscii
    }

    /** UTF-8 을 제치고 한글을 고르려면 이만큼은 한글이어야 한다. */
    private const val KOREAN_OVERRIDE_RATIO = 70
}
