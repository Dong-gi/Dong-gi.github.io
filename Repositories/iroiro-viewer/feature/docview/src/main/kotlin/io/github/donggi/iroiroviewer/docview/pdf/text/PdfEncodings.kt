package io.github.donggi.iroiroviewer.docview.pdf.text

/**
 * 한 바이트 글꼴(Type1·TrueType·Type3)의 코드를 글자로 — `/ToUnicode` 가 없을 때의 길이다.
 *
 * 명세의 부록 D 에 있는 기본 인코딩 셋(WinAnsi·MacRoman·Standard)과, `/Differences` 가 쓰는 **글리프 이름**을 글자로 옮기는
 * 표를 둔다. 글리프 이름 표는 Adobe Glyph List 의 라틴 문자 부분(문서가 실제로 쓰는 이름들)과 `uniXXXX`·`uXXXX` 규칙이다 —
 * 표 전체(4천여 개)를 싣지 않았다. 표에 없는 이름은 글자를 모르는 것으로 둔다(복사에서 빠진다).
 *
 * 값은 전부 명세와 유니코드의 사실이다(이름 ↔ 코드포인트). 옮겨 적은 표가 아니라 그 사실을 적은 것이다.
 */
internal object PdfEncodings {

    /** 글리프 이름을 글자로. 모르면 null. */
    fun unicodeOf(name: String): String? {
        NAMES[name]?.let { return it }
        // 'a.sc'·'one.oldstyle' 처럼 점 뒤는 모양 변형이다(AGL 규칙).
        val base = name.substringBefore('.')
        if (base != name && base.isNotEmpty()) NAMES[base]?.let { return it }
        // 'f_f_i' 처럼 밑줄로 이은 합자.
        if ('_' in base) {
            val parts = base.split('_').map { unicodeOf(it) ?: return null }
            return parts.joinToString("")
        }
        return algorithmic(base)
    }

    /** `uniXXXX`(네 자리씩 여럿) · `uXXXX[XX]`. */
    private fun algorithmic(name: String): String? {
        if (name.startsWith("uni") && name.length >= 7 && (name.length - 3) % 4 == 0) {
            val sb = StringBuilder()
            var i = 3
            while (i < name.length) {
                val v = name.substring(i, i + 4).toIntOrNull(16) ?: return null
                if (v in 0xD800..0xDFFF) return null
                sb.append(v.toChar())
                i += 4
            }
            return sb.toString()
        }
        if (name.startsWith("u") && name.length in 5..7) {
            val v = name.substring(1).toIntOrNull(16) ?: return null
            if (v in 0xD800..0xDFFF || v > 0x10FFFF) return null
            return String(Character.toChars(v))
        }
        return null
    }

    /** 이름이 있는 인코딩. 코드 0~255 의 글자(코드포인트, 0 은 없음). */
    fun table(name: String?): IntArray? = when (name) {
        "WinAnsiEncoding" -> WIN_ANSI
        "MacRomanEncoding" -> MAC_ROMAN
        "StandardEncoding" -> STANDARD
        else -> null
    }

    // ---- 표 ------------------------------------------------------------------------

    /** WinAnsiEncoding — 윈도 1252 와 같다. 0x20~0x7E 는 ASCII. */
    val WIN_ANSI: IntArray = IntArray(256).also { t ->
        for (c in 0x20..0x7E) t[c] = c
        val high = intArrayOf(
            0x20AC, 0, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, 0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0, 0x017D, 0,
            0, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014, 0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0, 0x017E, 0x0178,
        )
        for (i in high.indices) t[0x80 + i] = high[i]
        for (c in 0xA0..0xFF) t[c] = c
        // 명세: WinAnsi 의 0xA0 은 공백, 0xAD 는 하이픈으로 그린다.
        t[0xA0] = 0x20
        t[0xAD] = 0x2D
    }

    /** MacRomanEncoding(명세 부록 D 는 이 표의 부분집합이다 — 넓게 둔다). */
    val MAC_ROMAN: IntArray = IntArray(256).also { t ->
        for (c in 0x20..0x7E) t[c] = c
        val high = intArrayOf(
            0x00C4, 0x00C5, 0x00C7, 0x00C9, 0x00D1, 0x00D6, 0x00DC, 0x00E1, 0x00E0, 0x00E2, 0x00E4, 0x00E3, 0x00E5, 0x00E7, 0x00E9, 0x00E8,
            0x00EA, 0x00EB, 0x00ED, 0x00EC, 0x00EE, 0x00EF, 0x00F1, 0x00F3, 0x00F2, 0x00F4, 0x00F6, 0x00F5, 0x00FA, 0x00F9, 0x00FB, 0x00FC,
            0x2020, 0x00B0, 0x00A2, 0x00A3, 0x00A7, 0x2022, 0x00B6, 0x00DF, 0x00AE, 0x00A9, 0x2122, 0x00B4, 0x00A8, 0x2260, 0x00C6, 0x00D8,
            0x221E, 0x00B1, 0x2264, 0x2265, 0x00A5, 0x00B5, 0x2202, 0x2211, 0x220F, 0x03C0, 0x222B, 0x00AA, 0x00BA, 0x03A9, 0x00E6, 0x00F8,
            0x00BF, 0x00A1, 0x00AC, 0x221A, 0x0192, 0x2248, 0x2206, 0x00AB, 0x00BB, 0x2026, 0x0020, 0x00C0, 0x00C3, 0x00D5, 0x0152, 0x0153,
            0x2013, 0x2014, 0x201C, 0x201D, 0x2018, 0x2019, 0x00F7, 0x25CA, 0x00FF, 0x0178, 0x2044, 0x00A4, 0x2039, 0x203A, 0xFB01, 0xFB02,
            0x2021, 0x00B7, 0x201A, 0x201E, 0x2030, 0x00C2, 0x00CA, 0x00C1, 0x00CB, 0x00C8, 0x00CD, 0x00CE, 0x00CF, 0x00CC, 0x00D3, 0x00D4,
            0, 0x00D2, 0x00DA, 0x00DB, 0x00D9, 0x0131, 0x02C6, 0x02DC, 0x00AF, 0x02D8, 0x02D9, 0x02DA, 0x00B8, 0x02DD, 0x02DB, 0x02C7,
        )
        for (i in high.indices) t[0x80 + i] = high[i]
    }

    /** StandardEncoding — Type1 글꼴의 기본값. ASCII 와 따옴표 두 자리가 다르고 높은 쪽은 드문드문하다. */
    val STANDARD: IntArray = IntArray(256).also { t ->
        for (c in 0x20..0x7E) t[c] = c
        t[0x27] = 0x2019
        t[0x60] = 0x2018
        val pairs = intArrayOf(
            0xA1, 0x00A1, 0xA2, 0x00A2, 0xA3, 0x00A3, 0xA4, 0x2044, 0xA5, 0x00A5, 0xA6, 0x0192, 0xA7, 0x00A7, 0xA8, 0x00A4,
            0xA9, 0x0027, 0xAA, 0x201C, 0xAB, 0x00AB, 0xAC, 0x2039, 0xAD, 0x203A, 0xAE, 0xFB01, 0xAF, 0xFB02, 0xB1, 0x2013,
            0xB2, 0x2020, 0xB3, 0x2021, 0xB4, 0x00B7, 0xB6, 0x00B6, 0xB7, 0x2022, 0xB8, 0x201A, 0xB9, 0x201E, 0xBA, 0x201D,
            0xBB, 0x00BB, 0xBC, 0x2026, 0xBD, 0x2030, 0xBF, 0x00BF, 0xC1, 0x0060, 0xC2, 0x00B4, 0xC3, 0x02C6, 0xC4, 0x02DC,
            0xC5, 0x00AF, 0xC6, 0x02D8, 0xC7, 0x02D9, 0xC8, 0x00A8, 0xCA, 0x02DA, 0xCB, 0x00B8, 0xCD, 0x02DD, 0xCE, 0x02DB,
            0xCF, 0x02C7, 0xD0, 0x2014, 0xE1, 0x00C6, 0xE3, 0x00AA, 0xE8, 0x0141, 0xE9, 0x00D8, 0xEA, 0x0152, 0xEB, 0x00BA,
            0xF1, 0x00E6, 0xF5, 0x0131, 0xF8, 0x0142, 0xF9, 0x00F8, 0xFA, 0x0153, 0xFB, 0x00DF,
        )
        for (i in pairs.indices step 2) t[pairs[i]] = pairs[i + 1]
    }

    /** 글리프 이름 → 글자. 라틴 문자 집합(위 세 인코딩이 쓰는 이름 전부)과 그리스 문자, 자주 쓰는 기호. */
    private val NAMES: Map<String, String> = HashMap<String, String>(700).also { m ->
        fun put(name: String, cp: Int) {
            m[name] = String(Character.toChars(cp))
        }
        for (c in 'A'..'Z') put(c.toString(), c.code)
        for (c in 'a'..'z') put(c.toString(), c.code)
        val digits = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        digits.forEachIndexed { i, n -> put(n, '0'.code + i) }
        val ascii = listOf(
            "space" to 0x20, "exclam" to 0x21, "quotedbl" to 0x22, "numbersign" to 0x23, "dollar" to 0x24,
            "percent" to 0x25, "ampersand" to 0x26, "quotesingle" to 0x27, "parenleft" to 0x28, "parenright" to 0x29,
            "asterisk" to 0x2A, "plus" to 0x2B, "comma" to 0x2C, "hyphen" to 0x2D, "period" to 0x2E, "slash" to 0x2F,
            "colon" to 0x3A, "semicolon" to 0x3B, "less" to 0x3C, "equal" to 0x3D, "greater" to 0x3E, "question" to 0x3F,
            "at" to 0x40, "bracketleft" to 0x5B, "backslash" to 0x5C, "bracketright" to 0x5D, "asciicircum" to 0x5E,
            "underscore" to 0x5F, "grave" to 0x60, "braceleft" to 0x7B, "bar" to 0x7C, "braceright" to 0x7D,
            "asciitilde" to 0x7E,
        )
        for ((n, cp) in ascii) put(n, cp)
        val latin1 = listOf(
            "nbspace" to 0xA0, "exclamdown" to 0xA1, "cent" to 0xA2, "sterling" to 0xA3, "currency" to 0xA4, "yen" to 0xA5,
            "brokenbar" to 0xA6, "section" to 0xA7, "dieresis" to 0xA8, "copyright" to 0xA9, "ordfeminine" to 0xAA,
            "guillemotleft" to 0xAB, "logicalnot" to 0xAC, "sfthyphen" to 0xAD, "registered" to 0xAE, "macron" to 0xAF,
            "degree" to 0xB0, "plusminus" to 0xB1, "twosuperior" to 0xB2, "threesuperior" to 0xB3, "acute" to 0xB4,
            "mu" to 0xB5, "paragraph" to 0xB6, "periodcentered" to 0xB7, "cedilla" to 0xB8, "onesuperior" to 0xB9,
            "ordmasculine" to 0xBA, "guillemotright" to 0xBB, "onequarter" to 0xBC, "onehalf" to 0xBD,
            "threequarters" to 0xBE, "questiondown" to 0xBF, "Agrave" to 0xC0, "Aacute" to 0xC1, "Acircumflex" to 0xC2,
            "Atilde" to 0xC3, "Adieresis" to 0xC4, "Aring" to 0xC5, "AE" to 0xC6, "Ccedilla" to 0xC7, "Egrave" to 0xC8,
            "Eacute" to 0xC9, "Ecircumflex" to 0xCA, "Edieresis" to 0xCB, "Igrave" to 0xCC, "Iacute" to 0xCD,
            "Icircumflex" to 0xCE, "Idieresis" to 0xCF, "Eth" to 0xD0, "Ntilde" to 0xD1, "Ograve" to 0xD2, "Oacute" to 0xD3,
            "Ocircumflex" to 0xD4, "Otilde" to 0xD5, "Odieresis" to 0xD6, "multiply" to 0xD7, "Oslash" to 0xD8,
            "Ugrave" to 0xD9, "Uacute" to 0xDA, "Ucircumflex" to 0xDB, "Udieresis" to 0xDC, "Yacute" to 0xDD,
            "Thorn" to 0xDE, "germandbls" to 0xDF, "agrave" to 0xE0, "aacute" to 0xE1, "acircumflex" to 0xE2,
            "atilde" to 0xE3, "adieresis" to 0xE4, "aring" to 0xE5, "ae" to 0xE6, "ccedilla" to 0xE7, "egrave" to 0xE8,
            "eacute" to 0xE9, "ecircumflex" to 0xEA, "edieresis" to 0xEB, "igrave" to 0xEC, "iacute" to 0xED,
            "icircumflex" to 0xEE, "idieresis" to 0xEF, "eth" to 0xF0, "ntilde" to 0xF1, "ograve" to 0xF2, "oacute" to 0xF3,
            "ocircumflex" to 0xF4, "otilde" to 0xF5, "odieresis" to 0xF6, "divide" to 0xF7, "oslash" to 0xF8,
            "ugrave" to 0xF9, "uacute" to 0xFA, "ucircumflex" to 0xFB, "udieresis" to 0xFC, "yacute" to 0xFD,
            "thorn" to 0xFE, "ydieresis" to 0xFF,
        )
        for ((n, cp) in latin1) put(n, cp)
        val more = listOf(
            "Euro" to 0x20AC, "quotesinglbase" to 0x201A, "florin" to 0x0192, "quotedblbase" to 0x201E, "ellipsis" to 0x2026,
            "dagger" to 0x2020, "daggerdbl" to 0x2021, "circumflex" to 0x02C6, "perthousand" to 0x2030, "Scaron" to 0x0160,
            "guilsinglleft" to 0x2039, "OE" to 0x0152, "Zcaron" to 0x017D, "quoteleft" to 0x2018, "quoteright" to 0x2019,
            "quotedblleft" to 0x201C, "quotedblright" to 0x201D, "bullet" to 0x2022, "endash" to 0x2013, "emdash" to 0x2014,
            "tilde" to 0x02DC, "trademark" to 0x2122, "scaron" to 0x0161, "guilsinglright" to 0x203A, "oe" to 0x0153,
            "zcaron" to 0x017E, "Ydieresis" to 0x0178, "fi" to 0xFB01, "fl" to 0xFB02, "ff" to 0xFB00, "ffi" to 0xFB03,
            "ffl" to 0xFB04, "dotlessi" to 0x0131, "Lslash" to 0x0141, "lslash" to 0x0142, "fraction" to 0x2044,
            "breve" to 0x02D8, "dotaccent" to 0x02D9, "ring" to 0x02DA, "hungarumlaut" to 0x02DD, "ogonek" to 0x02DB,
            "caron" to 0x02C7, "minus" to 0x2212, "notequal" to 0x2260, "infinity" to 0x221E, "lessequal" to 0x2264,
            "greaterequal" to 0x2265, "partialdiff" to 0x2202, "summation" to 0x2211, "product" to 0x220F,
            "integral" to 0x222B, "radical" to 0x221A, "approxequal" to 0x2248, "Delta" to 0x2206, "lozenge" to 0x25CA,
            "Omega" to 0x03A9, "pi" to 0x03C0, "middot" to 0x00B7, "nonbreakingspace" to 0xA0, "minute" to 0x2032,
            "second" to 0x2033, "arrowleft" to 0x2190, "arrowup" to 0x2191, "arrowright" to 0x2192, "arrowdown" to 0x2193,
            "arrowboth" to 0x2194, "checkmark" to 0x2713, "square" to 0x25A1, "circle" to 0x25CB, "dotlessj" to 0x0237,
            "Idotaccent" to 0x0130, "Gbreve" to 0x011E, "gbreve" to 0x011F, "Scedilla" to 0x015E, "scedilla" to 0x015F,
            "Ccaron" to 0x010C, "ccaron" to 0x010D, "Rcaron" to 0x0158, "rcaron" to 0x0159, "Ecaron" to 0x011A,
            "ecaron" to 0x011B, "Uring" to 0x016E, "uring" to 0x016F, "Ncaron" to 0x0147, "ncaron" to 0x0148,
            "Tcaron" to 0x0164, "tcaron" to 0x0165, "Dcaron" to 0x010E, "dcaron" to 0x010F, "Zacute" to 0x0179,
            "zacute" to 0x017A, "Sacute" to 0x015A, "sacute" to 0x015B, "Nacute" to 0x0143, "nacute" to 0x0144,
            "Cacute" to 0x0106, "cacute" to 0x0107, "Zdotaccent" to 0x017B, "zdotaccent" to 0x017C, "Aogonek" to 0x0104,
            "aogonek" to 0x0105, "Eogonek" to 0x0118, "eogonek" to 0x0119, "Ohungarumlaut" to 0x0150,
            "ohungarumlaut" to 0x0151, "Uhungarumlaut" to 0x0170, "uhungarumlaut" to 0x0171, "Amacron" to 0x0100,
            "amacron" to 0x0101, "Emacron" to 0x0112, "emacron" to 0x0113, "Imacron" to 0x012A, "imacron" to 0x012B,
            "Omacron" to 0x014C, "omacron" to 0x014D, "Umacron" to 0x016A, "umacron" to 0x016B, "dong" to 0x20AB,
            "won" to 0x20A9, "estimated" to 0x212E, "afii61289" to 0x2113, "numero" to 0x2116, "degreecentigrade" to 0x2103,
        )
        for ((n, cp) in more) put(n, cp)
        val greek = listOf(
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu",
            "Xi", "Omicron", "Pi", "Rho", "", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega",
        )
        greek.forEachIndexed { i, n -> if (n.isNotEmpty()) m.putIfAbsent(n, String(Character.toChars(0x0391 + i))) }
        greek.forEachIndexed { i, n ->
            if (n.isNotEmpty()) m.putIfAbsent(n.lowercase(), String(Character.toChars(0x03B1 + i)))
        }
        put("sigma1", 0x03C2)
        put("theta1", 0x03D1)
        put("phi1", 0x03D5)
        put("omega1", 0x03D6)
        // 'Delta'·'Omega' 의 수학 기호 자리(∆·Ω)는 위 'more' 가 먼저 넣었다 — 그리스 문자보다 그쪽이 PDF 의 관례다.
    }
}
