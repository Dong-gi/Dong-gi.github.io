package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.BinarySniffer
import io.github.donggi.iroiroviewer.charset.Bom
import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 줄 끝 한 가지. [chars] 는 파일에 쓰는 글자다. */
enum class LineEnding(val chars: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/**
 * 파일 안의 줄 끝 수. [first] 는 맨 처음 나온 줄 끝(없으면 null) — 수가 같을 때 그것을 고른다.
 */
data class LineEndingCounts(val lf: Int, val crlf: Int, val cr: Int, val first: LineEnding?) {

    /** 두 가지 넘게 섞였는가. */
    val mixed: Boolean get() = listOf(lf, crlf, cr).count { it > 0 } > 1

    /**
     * 저장할 때 맞출 줄 끝 — **가장 많은 것**, 수가 같으면 먼저 나온 것. 줄 끝이 하나도 없으면(한 줄짜리·빈 파일) LF — 새로 치는
     * 줄이 이것으로 쓰인다(안드로이드·리눅스·웹의 관행, 요즘 윈도의 메모장도 읽는다).
     */
    fun dominant(): LineEnding {
        val best = maxOf(lf, crlf, cr)
        if (best == 0) return LineEnding.LF
        val tied = buildList {
            if (lf == best) add(LineEnding.LF)
            if (crlf == best) add(LineEnding.CRLF)
            if (cr == best) add(LineEnding.CR)
        }
        return if (first != null && first in tied) first else tied.first()
    }
}

/**
 * 고치는 글의 **꼴** — 저장할 때 그대로 입힌다. 인코딩·BOM·줄 끝. 마지막 줄바꿈은 꼴이 아니라 글이다 — 편집기가 글을
 * 그대로 들고 있으므로 있던 것은 있고 없던 것은 없다(우리가 붙이거나 떼지 않는다).
 *
 * @param mixed 줄 끝이 섞여 있었으면 그 수 — 저장하면 [lineEnding] 으로 맞추므로 저장 전에 묻는다. 하나뿐이었으면 null.
 */
data class TextFormat(
    val encoding: TextEncoding,
    val bom: Boolean,
    val lineEnding: LineEnding,
    val mixed: LineEndingCounts? = null,
) {
    /** 쓸 수 없는 글자를 만나 UTF-8 로 바꿔 저장할 때의 꼴 — BOM 없이(레거시 인코딩에는 처음부터 BOM 이 없다), 줄 끝은 그대로. */
    fun asUtf8(): TextFormat = copy(encoding = TextEncoding.UTF_8, bom = false)
}

/**
 * 편집기의 줄 끝 — **편집기 안에서는 언제나 `\n` 하나다.** 파일의 `\r\n`·`\r` 는 열 때 `\n` 으로 바꾸고([normalize]) 저장할 때 파일의
 * 줄 끝으로 되돌린다([apply]). `\r` 을 글에 남기면 화면에 보이지 않는 글자가 생기고 커서가 그 자리에서 한 번 더 멈춘다.
 */
internal object LineEndings {

    fun count(text: CharSequence): LineEndingCounts {
        var lf = 0
        var crlf = 0
        var cr = 0
        var first: LineEnding? = null
        var i = 0
        val n = text.length
        while (i < n) {
            when (text[i]) {
                '\r' -> if (i + 1 < n && text[i + 1] == '\n') {
                    crlf++
                    if (first == null) first = LineEnding.CRLF
                    i++
                } else {
                    cr++
                    if (first == null) first = LineEnding.CR
                }
                '\n' -> {
                    lf++
                    if (first == null) first = LineEnding.LF
                }
            }
            i++
        }
        return LineEndingCounts(lf, crlf, cr, first)
    }

    /** `\r\n`·`\r` 을 `\n` 으로. 바꿀 것이 없으면 받은 그대로 돌려준다(큰 글을 복사하지 않는다). */
    fun normalize(text: String): String {
        if (text.indexOf('\r') < 0) return text
        val out = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\r') {
                out.append('\n')
                if (i + 1 < n && text[i + 1] == '\n') i++
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** 편집기의 `\n` 을 [ending] 으로. LF 면 받은 그대로다. */
    fun apply(text: CharSequence, ending: LineEnding): CharSequence {
        if (ending == LineEnding.LF) return text
        val out = StringBuilder(text.length + text.length / 32)
        for (c in text) if (c == '\n') out.append(ending.chars) else out.append(c)
        return out
    }
}

/**
 * 편집을 위한 디코드와 저장을 위한 인코드. **순수 함수**라 JVM 시험이 왕복을 본다(`EditCodecTest`).
 *
 * ## 열 때 — 저장해도 손대지 않은 곳이 바뀌지 않는 글만 연다
 *
 * 판정은 텍스트 뷰어와 **같은 함수, 같은 앞머리**다(`BinarySniffer.sniff`·`CharsetDetector.detect` — `TextViewModel.load`). 편집할
 * 수 있는 파일은 판정 창(1 MiB)보다 작아 늘 파일 전체를 본다 — 뷰어가 보인 인코딩과 같은 답이다. 사용자가 뷰어에서 고른 인코딩이
 * 있으면 그것이다(그때는 '글이 아니다' 를 묻지 않는다 — 뷰어에서 '인코딩을 골라 열어 보기' 를 지난 글이다).
 *
 * 그다음 두 겹을 본다. **엄격하게 읽히는가**(읽을 수 없는 바이트가 있으면 뷰어는 � 로 보이지만 편집기는 그것을 저장할 수 없다 —
 * 그 바이트가 사라진다), **다시 쓰면 같은 바이트인가**(Shift_JIS·Big5 처럼 두 바이트 열이 한 글자로 읽히는 인코딩이 있다 — 그대로
 * 저장하면 손대지 않은 줄이 바뀐다). 하나라도 어기면 열지 않고 까닭을 말한다.
 *
 * ## 저장할 때 — 쓸 수 없는 글자를 먼저 찾는다
 *
 * [encode] 는 줄 끝을 입히고 엄격하게 인코딩한다. 쓸 수 없는 글자(CP949 파일에 이모지)를 만나면 바이트 대신 그 자리와 글자를
 * 돌려준다 — 화면이 'UTF-8 로 바꿔 저장 / 취소' 를 묻고 그 글자를 보여 준다.
 */
internal object EditCodec {

    sealed interface Decoded {
        /** 열었다. [text] 의 줄 끝은 `\n` 이다([LineEndings]). */
        class Ok(val text: String, val format: TextFormat) : Decoded

        /** 열지 않는다. [encoding] 은 그때 쓰려던 인코딩(화면이 문장에 넣는다). */
        data class Refused(val why: Why, val encoding: TextEncoding?) : Decoded
    }

    enum class Why {
        /** 글이 아니다(뷰어의 '글이 아닌 파일로 보입니다'). */
        BINARY,

        /** 이 앱이 읽지 못하는 인코딩(UTF-32). */
        UNSUPPORTED_ENCODING,

        /** 이 인코딩으로 읽을 수 없는 바이트가 있다. */
        MALFORMED,

        /** 다시 쓰면 바이트가 달라진다. */
        NOT_ROUND_TRIP,
    }

    /**
     * @param bytes 파일 전체(편집 상한 이하).
     * @param forced 사용자가 뷰어에서 고른 인코딩. null 이면 판정한다.
     */
    fun decode(bytes: ByteArray, length: Int = bytes.size, forced: TextEncoding? = null): Decoded {
        if (forced == null) {
            val verdict = BinarySniffer.sniff(bytes, minOf(length, BinarySniffer.SNIFF_BYTES))
            if (verdict is BinarySniffer.Verdict.Binary) return Decoded.Refused(Why.BINARY, null)
        }
        val detection = CharsetDetector.detect(bytes, minOf(length, CharsetDetector.DETECT_SCAN_MAX))
        if (forced == null && detection.evidence is CharsetDetector.Evidence.Unsupported) {
            return Decoded.Refused(Why.UNSUPPORTED_ENCODING, null)
        }
        val encoding = forced ?: detection.encoding
        val charset = encoding.charset ?: return Decoded.Refused(Why.UNSUPPORTED_ENCODING, encoding)
        // BOM 은 그 인코딩의 것일 때만 떼고 기억한다 — 뷰어와 같은 규칙(`bomLength`).
        val bom = Bom.detect(bytes, length)
        val bomLength = if (bom?.encoding == encoding) bom.length else 0
        val raw = strictDecode(bytes, bomLength, length - bomLength, charset)
            ?: return Decoded.Refused(Why.MALFORMED, encoding)
        val back = (encodeOrPosition(raw, charset) as? Encoding.Bytes)?.bytes
            ?: return Decoded.Refused(Why.NOT_ROUND_TRIP, encoding)
        if (!sameBytes(back, bytes, bomLength, length - bomLength)) return Decoded.Refused(Why.NOT_ROUND_TRIP, encoding)
        val counts = LineEndings.count(raw)
        val format = TextFormat(encoding, bom = bomLength > 0, lineEnding = counts.dominant(), mixed = counts.takeIf { it.mixed })
        return Decoded.Ok(LineEndings.normalize(raw), format)
    }

    sealed interface Encoded {
        /** 파일에 쓸 바이트 그대로(BOM·줄 끝을 입혔다). */
        class Ok(val bytes: ByteArray) : Encoded

        /**
         * 이 인코딩으로 쓸 수 없는 첫 글자. [index] 는 편집기 글(`\n` 줄 끝)의 자리, [sample] 은 그 글자(대리 쌍이면 둘을 이은 것).
         */
        data class Unmappable(val index: Int, val sample: String) : Encoded
    }

    /** [text] 를 [format] 으로 — 줄 끝을 입히고, 엄격하게 인코딩하고, BOM 이 있던 파일이면 앞에 붙인다. */
    fun encode(text: CharSequence, format: TextFormat): Encoded {
        val charset = format.encoding.charset
            ?: return Encoded.Unmappable(0, if (text.isEmpty()) "" else text.substring(0, 1))
        val withEndings = LineEndings.apply(text, format.lineEnding)
        return when (val e = encodeOrPosition(withEndings, charset)) {
            is Encoding.Bytes -> Encoded.Ok(if (format.bom) bomOf(format.encoding) + e.bytes else e.bytes)
            is Encoding.Stuck -> {
                // 줄 끝을 입힌 글의 자리를 편집기 글의 자리로 — CRLF 는 앞의 줄마다 한 글자씩 길어졌다(CR 은 길이가 같다).
                val index = if (format.lineEnding == LineEnding.CRLF) {
                    e.position - countLineFeeds(withEndings, e.position)
                } else {
                    e.position
                }.coerceIn(0, maxOf(text.length - 1, 0))
                Encoded.Unmappable(index, sampleAt(text, index))
            }
        }
    }

    // ---- 도우미 -------------------------------------------------------------------------------

    private sealed interface Encoding {
        class Bytes(val bytes: ByteArray) : Encoding

        /** [position] 의 글자에서 멈췄다(쓸 수 없거나 짝이 없는 대리 문자). */
        class Stuck(val position: Int) : Encoding
    }

    /** 엄격하게 읽는다(읽을 수 없는 바이트가 있으면 null). */
    private fun strictDecode(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /**
     * 엄격하게 쓴다. 편의 함수(`CharsetEncoder.encode(CharBuffer)`)는 멈춘 **자리**를 주지 않아 낮은 단계의 고리를 직접 돈다 — 멈추면
     * 입력 버퍼의 자리가 그 글자다.
     */
    private fun encodeOrPosition(text: CharSequence, charset: Charset): Encoding {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = CharBuffer.wrap(text)
        var out = ByteBuffer.allocate(maxOf(16, (text.length * encoder.averageBytesPerChar()).toInt() + 16))
        while (true) {
            val result = encoder.encode(input, out, true)
            when {
                result.isError -> return Encoding.Stuck(input.position())
                result.isOverflow -> out = grown(out)
                else -> break
            }
        }
        while (encoder.flush(out).isOverflow) out = grown(out)
        out.flip()
        return Encoding.Bytes(ByteArray(out.remaining()).also { out.get(it) })
    }

    private fun grown(out: ByteBuffer): ByteBuffer {
        val bigger = ByteBuffer.allocate(maxOf(out.capacity() * 2, 64))
        out.flip()
        bigger.put(out)
        return bigger
    }

    private fun sameBytes(a: ByteArray, b: ByteArray, offset: Int, length: Int): Boolean {
        if (a.size != length) return false
        for (i in 0 until length) if (a[i] != b[offset + i]) return false
        return true
    }

    private fun bomOf(encoding: TextEncoding): ByteArray =
        Bom.entries.firstOrNull { it.encoding == encoding }?.bytes ?: ByteArray(0)

    private fun countLineFeeds(text: CharSequence, end: Int): Int {
        var n = 0
        for (i in 0 until minOf(end, text.length)) if (text[i] == '\n') n++
        return n
    }

    /** [index] 의 글자 — 대리 쌍이면 둘을 잇는다(이모지 한 자). */
    private fun sampleAt(text: CharSequence, index: Int): String {
        if (index >= text.length) return ""
        val c = text[index]
        return if (Character.isHighSurrogate(c) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1])) {
            text.substring(index, index + 2)
        } else {
            c.toString()
        }
    }
}
