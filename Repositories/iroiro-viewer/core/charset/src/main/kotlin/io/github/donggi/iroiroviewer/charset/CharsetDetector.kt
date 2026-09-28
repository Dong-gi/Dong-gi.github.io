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
 * ## 판정은 하나다 — 글 파일과 압축 파일의 이름이 같은 함수를 지난다
 *
 * 7단계부터 판정이 두 벌이었다(이것과 `format:archive` 의 `EntryNameDecoder`). 한쪽만 고치면 같은 바이트가 글
 * 뷰어와 압축 목록에서 다르게 읽힌다. 합치면서 두 벌의 답을 먼저 회귀 표본으로 박았고(`EntryNameRegressionTest`),
 * 이 함수가 **짧은 입력**(파일 이름 한 줄)도 긴 글만큼 옳게 답하도록 규칙을 셋 고쳤다 — 아래 ⑤·⑥ 과 ⑦.
 *
 * ## 판정의 뼈대
 *
 * 1. **BOM.** 있으면 끝. 근거가 파일 안에 적혀 있다.
 * 2. **첫 비ASCII 바이트를 찾는다.** 없으면 ASCII 이고, 어느 후보로 읽어도 같다.
 * 3. **그 자리부터 창을 뜬다.** 앞이 전부 ASCII 이므로 그 자리는 **모든 바이트지향
 *    후보에서 문자 경계**다 — 위상이 어긋나지 않는다.
 * 4. 후보마다 **엄격 디코드**(REPORT)를 해 본다. 실패하면 탈락 — 이것이 가장 센 신호다.
 * 5. UTF-8 이 살아남았으면 거의 그것이다(한 가지 예외 — 아래).
 * 6. 레거시끼리는 **그 언어에만 있는 글자**, 그 다음 **본토 글자 비율**로 가른다.
 * 7. 아무도 엄격 디코드를 통과하지 못했으면 **한글이 또렷한가**를 너그럽게 한 번 더 본다.
 *
 * ## 이 판정기가 못 하는 것 — 적고 지킨다
 *
 * - 앞 1 MiB 가 전부 ASCII 이고 그 뒤에 한글이 나오는 파일은 ASCII 로 판정된다.
 *   (UTF-8 로 읽히므로 CP949 부분만 깨진다. 사용자가 인코딩을 손으로 바꿀 수 있다.)
 * - CP949 와 GB18030 은 바이트 범위가 크게 겹쳐, **한글도 가나도 없는 짧은 한자 문서**는
 *   갈리지 않는다. 그때는 [Detection.Confidence.LOW] 로 내놓고 화면이 그렇게 말한다.
 * - **짧은 입력(한글로 읽어 16자 미만)은 한국어 쪽으로 기운다.** 받침 검사(⑥)가 뜻을 가질 만큼 글자가 없고,
 *   GB2312 한자 바이트의 60% 가 CP949 로는 한글 음절이 된다(`B0`~`C8` 앞 바이트가 겹친다). 짧은 중국어 파일·이름은
 *   한글 찌꺼기로 읽힐 수 있다 — 이 앱의 사용자와 그 파일에서 더 흔한 쪽을 고른 것이다. 반각 가나만으로 된 짧은
 *   Shift_JIS 도 같은 까닭으로 한글로 읽힐 수 있다.
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
     * @param head 파일 앞머리. [DETECT_SCAN_MAX] 만큼 주면 가장 정확하다. 파일 이름이면 이름의 바이트 전부다.
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

        if (survivors.isEmpty()) return lenient(head, first, windowLen)

        // CP949 로 읽은 한글이 진짜인가. ⑤·⑥ 이 같은 판단을 쓴다 — 한쪽에서 가짜라 해 놓고 다른 쪽에서 제 글자로
        // 세면 두 계산이 서로 다른 말을 한다(중국어를 CP949 로 읽은 것이 만점을 받아 이긴 적이 있다).
        val korean = survivors.firstOrNull { it.first == TextEncoding.CP949 }?.second
        val verdict = if (korean != null) koreanVerdict(head, first, windowLen, korean) else Korean.NONE

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
            // 짧은 한국어 문서·이름이 그렇게 걸리면 라틴 기호 더미가 된다. UTF-8 결과에 CJK 가
            // 하나도 없고 한글로 읽으면 또렷하면 한글을 고른다 — zip 파일명에서 배운 규칙이다.
            //
            // **반대 방향의 함정도 있다** — UTF-8 로 적은 `café` 의 `é`(`C3 A9`)는 CP949 로 `챕` 이다. 라틴 문자에
            // 악센트가 붙은 글은 전부 한글로 읽혔다(합치기 전 두 판정 모두). UTF-8 로 읽은 글자가 **라틴 낱말의
            // 일부**(ASCII 글자 바로 옆의 서유럽 글자)면 UTF-8 을 지킨다. 한글로 읽히는 CP949 가 UTF-8 로는 `¡¢£` 같은
            // **기호**가 되거나 비ASCII 글자끼리만 붙는 것(`캡처` → `ĸó`)과 가른다([looksLikeLatinWords]).
            //
            // 여기서는 **바이트의 자리**(확장 완성형이 절반을 넘는가)만 본다. 받침 검사는 쓰지 않는다 — UTF-8 로도
            // 읽히는 CP949 는 앞 바이트가 `C2`~`C8` 로 좁아 받침 있는 글자에 몰리고, 그것을 가짜로 몰면 이 예외가
            // 지키려던 짧은 한국어 문서가 다시 기호 더미가 된다.
            val ratio = korean?.let { hangulRatio(it) } ?: 0
            val placement = if (korean != null) koreanVerdict(head, first, windowLen, korean, batchim = false) else Korean.NONE
            // 창은 첫 비ASCII 바이트에서 시작하므로 그 앞 글자(`caf` + `é`)는 창 밖이다 — 바로 앞 한 글자를 함께 넘긴다.
            val before = if (first > 0) (head[first - 1].toInt() and 0x7F).toChar() else null
            if (ratio >= KOREAN_OVERRIDE_RATIO && placement != Korean.FOREIGN && !looksLikeLatinWords(utf8, before)) {
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
            .map { (enc, text) -> Triple(enc, text, exclusiveRatio(enc, text, verdict)) }
            .filter { it.third >= EXCLUSIVE_MIN_RATIO }

        val pool = if (marked.isNotEmpty()) marked else
            survivors.map { (enc, text) -> Triple(enc, text, 0) }

        // 동점이면 **후보 목록의 차례**가 이긴다(정렬이 안정하다) — CP949 가 Shift_JIS·중국어보다 앞이다. 어느 언어의
        // 글자도 없는 글(기호만 든 짧은 이름)은 이 앱의 사용자에게 가장 흔한 쪽으로 읽는다.
        val scored = pool
            .map { (enc, text, marker) -> ScoredCandidate(enc, marker, homeRatio(enc, text, verdict)) }
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

    /**
     * ⑦ **아무 후보도 엄격하게 읽지 못했다.**
     *
     * 예전 글 판정은 여기서 곧바로 Latin-1 을 골랐다. 그러면 CP949 한국어 문서에 **깨진 바이트가 하나만** 있어도
     * 문서 전체가 `ÇÑ±Û` 로 나왔다 — 64 KiB 창의 엄격 디코드는 한 바이트로 탈락하기 때문이다. 옛 파일명 판정은
     * 반대로 언제나 CP949 를 골라 서유럽 이름(`café` 를 Latin-1 로 적은 것)을 `caf�` 로 만들었다.
     *
     * 합친 규칙: CP949 로 **너그럽게** 읽어(깨진 자리는 대체 문자) 한글이 또렷하고 깨진 자리가 드물면 CP949,
     * 아니면 Latin-1 이다. 둘 다 [Detection.Confidence.LOW] 다 — 화면이 '확실하지 않다' 를 말한다.
     */
    private fun lenient(head: ByteArray, first: Int, windowLen: Int): Detection {
        val cs = TextEncoding.CP949.charset
        if (cs != null) {
            val text = String(head, first, windowLen, cs)
            var nonAscii = 0
            var hangul = 0
            var broken = 0
            for (c in text) {
                if (c.code < 0x80) continue
                nonAscii++
                if (c == '�') broken++ else if (isHangul(c)) hangul++
            }
            val readable = nonAscii - broken
            // 깨진 자리는 비ASCII 의 10% 까지, 짧은 입력(파일 이름)이면 한 자리까지 받는다.
            if (readable > 0 && hangul * 100 >= readable * KOREAN_OVERRIDE_RATIO && broken <= maxOf(1, nonAscii / 10) &&
                koreanVerdict(head, first, windowLen, text) != Korean.FOREIGN
            ) {
                return Detection(
                    TextEncoding.CP949,
                    Detection.Confidence.LOW,
                    Evidence.ByScript((hangul * 100) / readable, 0),
                    TextEncoding.LATIN1,
                )
            }
        }
        // 최후 수단으로 Latin-1 — 어떤 바이트열이든 받는다.
        return Detection(TextEncoding.LATIN1, Detection.Confidence.LOW, Evidence.OnlyCandidate, null)
    }

    private data class ScoredCandidate(val encoding: TextEncoding, val marker: Int, val home: Int)

    /** CP949 로 읽은 한글에 대한 판단. */
    private enum class Korean {
        /** 한글 음절이 없다. */
        NONE,

        /** 한국어로 보인다 — 또는 가짜라고 할 근거가 없다(짧은 입력). */
        REAL,

        /** 다른 언어의 바이트를 CP949 로 잘못 읽은 찌꺼기다. */
        FOREIGN,
    }

    /**
     * 이 한글이 **진짜 한국어인가, 아니면 다른 언어를 잘못 읽은 찌꺼기인가.** 근거가 둘이다.
     *
     * ## 1. 확장 완성형이 절반을 넘는가 — 바이트의 자리로
     *
     * CP949 의 한글 음절은 두 곳에 있다. KS X 1001 완성형 2,350자(앞 바이트 `B0`~`C8`, 뒤 바이트 `A1`~`FE`)와,
     * 나머지 8,822자를 담은 **확장 완성형**(앞 `81`~`C6`, 뒤에 ASCII 글자 범위까지 쓴다). 실제 한국어는 거의 전부
     * 완성형이다 — 흔한 글자를 먼저 모은 것이 완성형이기 때문이다. 반면 **Shift_JIS 를 CP949 로 읽으면 확장
     * 완성형이 쏟아진다**(가나의 앞 바이트 `82`·`83` 이 그 자리다). `テスト` 는 CP949 로 확장 완성형 셋이다.
     * 표 없이 바이트의 자리만으로 가른다.
     *
     * ## 2. 받침 없는 글자가 드문가 — 코드포인트 배열로
     *
     * 한글 음절은 `0xAC00 + (초성×21 + 중성)×28 + 종성` 으로 배열되어 있고, 종성이 0 이면
     * 받침이 없다. 실제 한국어 문장에서는 받침 없는 글자가 **절반을 넘는다**. 직접 재 보았다 — 한국어 문장 96자에서
     * **54.2%**, 이 저장소 CLAUDE.md 의 제목 104줄에서 53.6%, 문자열 자원 418개에서 53.9%. 반면 중국어를 CP949 로
     * 잘못 읽은 결과는 GB2312 1급 한자 3,755자를 고루 읽으면 **14.9%**, 중국어 문장 다섯에서 6~30% 였다.
     *
     * **짧으면 이 검사를 믿지 않는다.** 예전 규칙(8자 이상에서 25% 미만이면 가짜)은 한국어 제목 209개 가운데
     * 이 앱의 문구 하나('압축 파일을 읽는 중' — 8자에 1자)를 가짜로 몰았고, 파일 이름은 대개 그 길이다
     * (`월간 경영 전략 분석 결과` 는 10자에 1자다). 그래서 **16자 이상이거나 받침 없는 글자가 하나도 없을 때만**
     * 가짜로 본다. 대가는 8~15자짜리 중국어가 한글로 읽힐 수 있다는 것이다(위 클래스 주석의 '못 하는 것').
     *
     * 빈도표를 쓰지 않는 이유는 크기가 아니라 **근거**다. 빈도표는 어느 말뭉치로 뽑았는지에
     * 따라 값이 달라지고 그 말뭉치를 저장소에 넣어야 하지만, 두 규칙은 **CP949 의 바이트 배치와 한글 코드포인트
     * 배열 자체**에서 나온다.
     */
    private fun koreanVerdict(
        head: ByteArray,
        first: Int,
        windowLen: Int,
        text: String,
        /** 거짓이면 1(바이트의 자리)만 본다 — ⑤ 의 주석. */
        batchim: Boolean = true,
    ): Korean {
        var ks = 0
        var extended = 0
        var i = first
        val end = first + windowLen
        while (i < end) {
            val lead = head[i].toInt() and 0xFF
            if (lead < 0x80) {
                i++
                continue
            }
            if (i + 1 >= end) break
            val trail = head[i + 1].toInt() and 0xFF
            when {
                lead in 0xB0..0xC8 && trail in 0xA1..0xFE -> ks++
                isExtendedHangul(lead, trail) -> extended++
            }
            i += 2
        }
        val syllables = ks + extended
        if (syllables == 0) return Korean.NONE
        if (extended * 2 > syllables) return Korean.FOREIGN
        if (!batchim) return Korean.REAL

        var hangul = 0
        var noJong = 0
        for (c in text) {
            if (c !in '가'..'힣') continue
            hangul++
            if ((c.code - 0xAC00) % 28 == 0) noJong++
        }
        if (hangul >= MIN_HANGUL_SAMPLE && noJong * 100 < hangul * NO_JONGSEONG_MIN_PERCENT &&
            (hangul >= SURE_HANGUL_SAMPLE || noJong == 0)
        ) {
            return Korean.FOREIGN
        }
        return Korean.REAL
    }

    /** CP949 확장 완성형 한글의 바이트 자리(UHC). 뒤 바이트에 ASCII 글자 범위가 들어간다. */
    private fun isExtendedHangul(lead: Int, trail: Int): Boolean {
        val asciiLetter = trail in 0x41..0x5A || trail in 0x61..0x7A
        return when (lead) {
            in 0x81..0xA0 -> asciiLetter || trail in 0x81..0xFE
            in 0xA1..0xC6 -> asciiLetter || trail in 0x81..0xA0
            else -> false
        }
    }

    /**
     * **그 언어에만 있는 글자**의 비율(0~100).
     *
     * 한글은 한국어 인코딩에만, 가나는 일본어 인코딩에만 있다. 한 글자만 보여도 언어가
     * 갈린다. 중국어 인코딩에는 그런 글자가 없으므로 0 이고, 그래서 한글·가나가 보이는
     * 후보가 있으면 중국어는 겨루기에서 빠진다.
     *
     * **반각 가나(`ｱ`~`ﾝ`)는 세지 않는다.** CP949 완성형 한글의 두 바이트는 대개 Shift_JIS 로 반각 가나 둘이 된다
     * (`한글` = `C7 D1 B1 DB` = `ｷﾑｱﾛ`). 그것을 일본어의 표지로 세면 짧은 한국어 이름·글이 반각 가나로 읽혔다
     * (합치기 전 글 판정이 `한글이름` 8바이트를 그렇게 읽었다). 반각 가나는 본토 글자([homeRatio])로는 센다.
     */
    private fun exclusiveRatio(enc: TextEncoding, text: String, verdict: Korean): Int {
        var nonAscii = 0
        var mark = 0
        for (c in text) {
            if (c.code < 0x80) continue
            nonAscii++
            when (enc) {
                TextEncoding.CP949 -> if (isHangul(c)) mark++
                TextEncoding.SHIFT_JIS -> if (isFullWidthKana(c)) mark++
                else -> Unit
            }
        }
        if (nonAscii == 0) return 0
        // **한글이 나온다고 한국어인 것은 아니다.** CP949 의 2바이트 영역이 한글로
        // 빽빽해서, 중국어 GB18030 바이트를 CP949 로 읽으면 한글이 잔뜩 나온다.
        // [koreanVerdict] 가 그 둘을 가른다.
        if (enc == TextEncoding.CP949 && verdict != Korean.REAL) return 0
        return (mark * 100) / nonAscii
    }

    /**
     * 이 인코딩이 쓰이는 언어의 글자 비율(0~100). 사용자 영역으로 읽힌 것은 감점한다.
     *
     * **[koreanVerdict] 가 '가짜 한글' 이라고 판정했으면 그 한글은 감점 대상이다.** 한쪽에서는
     * 노이즈라 해 놓고 다른 쪽에서 제 글자로 세면 두 계산이 서로 다른 말을 하게 되고,
     * 중국어를 CP949 로 읽은 결과가 '한자 92% + 한글' 로 만점을 받아 이긴다(시험이 잡았다).
     */
    private fun homeRatio(enc: TextEncoding, text: String, verdict: Korean): Int {
        val fakeHangul = enc == TextEncoding.CP949 && verdict == Korean.FOREIGN
        var nonAscii = 0
        var home = 0
        var alien = 0
        for (c in text) {
            if (c.code < 0x80) continue
            nonAscii++
            val isHome = when (enc) {
                TextEncoding.CP949 -> (isHangul(c) && !fakeHangul) || isHanja(c)
                // 반각 **문장 부호**(`｡｢｣､･`)는 글자가 아니다. CP949 의 기호 줄(`A1`)이 Shift_JIS 로는 그것이 된다 —
                // `·`(A1 A4)가 `｡､` 로, 기호만 든 한국어 이름(`DCIM · Pictures`)이 일본어로 이겼다(검토가 잡았다).
                TextEncoding.SHIFT_JIS -> (isKana(c) && c !in '｡'..'･') || isHanja(c)
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

    /**
     * UTF-8 로 읽은 글이 **라틴 낱말**로 보이는가 — 비ASCII 글자의 **절반을 넘는 것**이 서유럽 악센트 글자이고
     * 바로 옆에 **ASCII 글자**가 붙어 있다(`café`·`naïve`·`Größe`).
     *
     * 한글로 읽히는 CP949 가 UTF-8 로 통과하면 `¡¢£` 같은 **기호**거나(앞 바이트 `C2`), 비ASCII 글자끼리만 붙어 있다.
     * 두 조건이 그 둘을 가른다 — 둘 다 검토가 잡은 회귀에서 나왔다.
     *
     * - **옆이 ASCII 글자여야 한다.** 비ASCII 글자끼리 붙은 것까지 낱말로 세면 흔한 한국어 이름이 통째로 라틴 글자가
     *   된다: `캡처`(C4 B8 C3 B3)가 `ĸó`, `체크` 가 `üũ`, `치킨` 이 `ġŲ`, `호환` 이 `ȣȯ` 다(옛 이름 판정은 넷 다 한글로
     *   읽었다). 진짜 라틴 낱말은 거의 언제나 ASCII 글자를 품는다.
     * - **서유럽 글자(U+00C0~U+00FF)만 센다.** 라틴 확장(U+0100~)은 CP949 의 `C4`~`C6` 줄 한글(`카`·`크`·`키`·`표`)이
     *   UTF-8 로 떨어지는 자리라 흔한 글자와 겹친다. 중부 유럽 글자(`Š`·`ł`)의 이름은 예전처럼 한글로 읽힐 수 있다.
     *
     * 남는 틀림: `Excel처` 처럼 **ASCII 낱말 끝에 붙은 한 글자**는 라틴 낱말 `Exceló` 와 바이트가 같다 — 가를
     * 근거가 없어 라틴으로 읽는다(`EntryNameRegressionTest` 가 적어 둔다).
     */
    private fun looksLikeLatinWords(text: String, before: Char?): Boolean {
        var nonAscii = 0
        var inWords = 0
        for (i in text.indices) {
            val c = text[i]
            if (c.code < 0x80) continue
            nonAscii++
            if (!isWesternLetter(c)) continue
            val prev = if (i == 0) before else text[i - 1]
            val next = text.getOrNull(i + 1)
            if (prev?.isAsciiLetter() == true || next?.isAsciiLetter() == true) inWords++
        }
        return nonAscii > 0 && inWords * 2 > nonAscii
    }

    /** 서유럽 악센트 글자(Latin-1 보충, U+00C0~U+00FF 가운데 곱셈·나눗셈 기호를 뺀 것). */
    private fun isWesternLetter(c: Char): Boolean = c.code in 0xC0..0xFF && c != '×' && c != '÷'

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    /** 전용 글자가 이만큼은 있어야 '뚜렷하다' 고 본다. */
    private const val EXCLUSIVE_MIN_RATIO = 10

    /** 받침 판정을 하려면 한글이 이만큼은 있어야 한다. 몇 글자로는 비율이 뜻이 없다. */
    private const val MIN_HANGUL_SAMPLE = 8

    /**
     * 받침 없는 글자가 **있어도** 가짜로 볼 수 있을 만큼의 한글 수. 그보다 짧으면 받침 없는 글자가 하나도 없을
     * 때만 가짜로 본다([koreanVerdict] 의 2).
     */
    private const val SURE_HANGUL_SAMPLE = 16

    /**
     * 받침 없는 글자가 이 비율은 되어야 진짜 한국어로 본다.
     *
     * 실측 — 한국어 54%(문장·제목·문구 모두), 중국어를 CP949 로 오독 6~30%(고르게 읽으면 15%).
     * 25% 는 그 사이에 놓인다.
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

    /** 전각 가나(히라가나·가타카나). 반각은 [exclusiveRatio] 의 주석 참고. */
    private fun isFullWidthKana(c: Char): Boolean = c in '぀'..'ヿ'

    private fun isHanja(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '\uF900'..'\uFAFF'

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
