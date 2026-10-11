package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import java.text.Normalizer
import java.util.Locale

/**
 * 사이트가 준 글(제목·작가·파일 이름)을 저장 이름과 화면 글로. 저장 이름은 앱 공용 [SafeFileName] 이 마지막에 다듬고, 그 앞에서 둘을 더 한다 —
 * **홀로 선 서로게이트**(JSON 의 `\ud800` 이스케이프로 들어올 수 있다 — UTF-8 로 쓰면 `?` 가 되고 파일 시스템마다 다르게 다룬다)를 `_` 로,
 * 그리고 NFC(같은 글자가 두 꼴로 와서 이름이 둘이 되지 않게 — 토렌트의 경로 정리와 같다).
 */
internal object SiteNames {

    /** 화면에 보일 제목·작가의 글자(코드 포인트) 상한 — 목록과 확인 창의 한 줄. */
    const val MAX_DISPLAY = 300

    /** 저장 이름 하나의 바이트 상한 — [SafeFileName.MAX_BYTES](200)에 겹침 꼬리 ` (20000)` 이 붙어도 ext4 의 255 안이다. */
    const val MAX_NAME_BYTES = 255

    /** [SafeFileName] 과 같은 방향 제어 문자들. */
    private val BIDI_CONTROLS = setOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069)

    /** 폴더 성분·파일 이름. 비면 [SafeFileName.FALLBACK]. */
    fun clean(raw: String): String = SafeFileName.clean(prepare(raw))

    /** 저장 이름으로 다듬기 전의 공통 손질 — 서로게이트와 NFC. 길이를 자르기 전에 부른다(자른 뒤 NFC 가 바이트를 바꾸지 않게). */
    fun prepare(raw: String): String = Normalizer.normalize(replaceLoneSurrogates(raw, '_'), Normalizer.Form.NFC)

    /**
     * 화면 글 — 제어 문자(줄바꿈 포함)를 빈칸으로, 홀로 선 서로게이트를 U+FFFD 로, 빈칸을 하나로 접고 앞뒤를 떼고 [max] 글자까지. 비면 null.
     * **방향 제어 문자**(U+202E 등)는 뺀다 — 남이 지은 제목이 확인 창의 뒤따르는 글(작가·파일 수)의 방향을 뒤집지 못하게.
     */
    fun display(raw: String?, max: Int = MAX_DISPLAY): String? {
        if (raw == null) return null
        val fixed = replaceLoneSurrogates(raw, '�')
        val out = StringBuilder(minOf(fixed.length, max * 2))
        var space = false
        for (c in fixed) {
            if (c.code in BIDI_CONTROLS) continue
            val blank = c.code < 0x20 || c.code == 0x7F || c == ' ' || c == ' '
            if (blank) {
                space = out.isNotEmpty()
            } else {
                if (space) out.append(' ')
                space = false
                out.append(c)
            }
        }
        val text = Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
        return takeCodePoints(text, max).takeIf { it.isNotEmpty() }
    }

    /** 앞에서 [count] 코드 포인트 — 서로게이트 쌍을 가르지 않는다(데스크톱의 `[:80]` 은 파이썬이라 코드 포인트로 잘랐다). */
    fun takeCodePoints(text: String, count: Int): String {
        var i = 0
        var taken = 0
        while (i < text.length && taken < count) {
            i += Character.charCount(text.codePointAt(i))
            taken++
        }
        return text.substring(0, i)
    }

    /** 앞에서 UTF-8 로 [maxBytes] 바이트까지(코드 포인트 경계에서). */
    fun limitUtf8(text: String, maxBytes: Int): String {
        var i = 0
        var used = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = utf8Length(cp)
            if (used + n > maxBytes) break
            used += n
            i += Character.charCount(cp)
        }
        return text.substring(0, i)
    }

    fun utf8Length(text: String): Int {
        var i = 0
        var used = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            used += utf8Length(cp)
            i += Character.charCount(cp)
        }
        return used
    }

    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }

    /**
     * 같은 폴더에 놓일 이름들을 겹치지 않게 — 대소문자와 NFC 를 무시하고 견준다(안드로이드의 공용 저장소는 대소문자를 가리지 않는다).
     * 겹치면 확장자 앞에 ` (2)`·` (3)`. 꼬리를 붙여도 [SafeFileName.MAX_BYTES] 안이 되게 앞부분을 줄인다 — 그래서 결과는 다시 다듬어도
     * 그대로다([isClean]). 같은 이름이 수만 번 와도 이름마다 번호를 이어서 세어 제곱이 되지 않는다.
     */
    fun unique(names: List<String>): List<String> {
        val used = HashSet<String>(names.size * 2)
        val nextNumber = HashMap<String, Int>()
        return names.map { name ->
            val key = keyOf(name)
            if (used.add(key)) return@map name
            var n = nextNumber[key] ?: 2
            var candidate: String
            do {
                candidate = numbered(name, n++)
            } while (!used.add(keyOf(candidate)))
            nextNumber[key] = n
            candidate
        }
    }

    private fun keyOf(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun numbered(name: String, n: Int): String {
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 16) name.substring(dot) else ""
        val suffix = " ($n)"
        val budget = SafeFileName.MAX_BYTES - utf8Length(suffix + ext)
        val base = limitUtf8(name.substring(0, name.length - ext.length), budget).trimEnd('.', ' ')
        return SafeFileName.clean(base + suffix + ext)
    }

    /**
     * 우리가 지은 이름인가 — 비어 있지 않고, 홀로 선 서로게이트가 없고, [SafeFileName] 이 다시 다듬어도 그대로다(그래서 `..`·`/`·제어 문자·
     * 200 바이트 넘는 이름이 아니다). 계획 글([GalleryCodec])을 다시 읽을 때 본다 — 폴더 성분이 경로를 벗어나지 못하게.
     */
    fun isClean(name: String): Boolean =
        name.isNotEmpty() && name.length <= MAX_NAME_BYTES && !hasLoneSurrogate(name) && SafeFileName.clean(name) == name

    fun hasLoneSurrogate(text: String): Boolean = replaceLoneSurrogates(text, '_') !== text

    private fun replaceLoneSurrogates(text: String, replacement: Char): String {
        var out: StringBuilder? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                out?.append(c)?.append(text[i + 1])
                i += 2
                continue
            }
            if (Character.isSurrogate(c)) {
                val builder = out ?: StringBuilder(text.length).append(text, 0, i).also { out = it }
                builder.append(replacement)
            } else {
                out?.append(c)
            }
            i++
        }
        return out?.toString() ?: text
    }
}
