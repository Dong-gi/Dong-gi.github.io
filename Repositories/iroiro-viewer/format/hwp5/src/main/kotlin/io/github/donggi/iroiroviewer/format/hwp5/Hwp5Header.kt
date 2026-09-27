package io.github.donggi.iroiroviewer.format.hwp5

/**
 * `FileHeader` 스트림(256바이트) [SPEC p.13/8 표 3] — 서명·판·속성.
 *
 * @param version `0xMMnnPPrr`(파일에는 `rr PP nn MM` 차례로 적혀 있다). MM·nn 이 다르면 '호환 불가능' 이라고 명세가 적는다.
 * @param flags 속성. 비트의 뜻은 아래 속성들.
 * @param encryptVersion 암호 판(0 없음 … 4 '한글 7.0 이후'). **쓰지 않는다** — 암호 문서는 열지 않으므로 기록만 한다.
 */
internal class Hwp5FileHeader(
    val version: Int,
    val flags: Long,
    val license: Long,
    val encryptVersion: Long,
) {
    val major: Int get() = (version ushr 24) and 0xFF

    /** 비트 0 — `DocInfo`·본문 스트림이 raw DEFLATE 로 압축되어 있다. */
    val compressed: Boolean get() = bit(0)

    /**
     * 비트 1 — 암호. **방식이 공개 명세에 없다**(명세는 이 비트와 판 번호만 적는다). 거꾸로 짜 맞춘 구현(rhwp)이 있지만
     * 쓰지 않는다 — '푸는 방법이 공개 명세에 있는가' 가 이 앱의 기준이다(CLAUDE.md '암호가 걸린 파일').
     */
    val password: Boolean get() = bit(1)

    /** 비트 2 — 배포용 문서. 본문이 `ViewText` 에 잠겨 있다([Hwp5Distribution]). */
    val distribution: Boolean get() = bit(2)

    /** 비트 3 — 스크립트(JScript)가 들어 있다. **돌리지 않는다.** */
    val scripts: Boolean get() = bit(3)

    /**
     * 비트 4(DRM)·8(공인 인증서 암호화)·10(공인 인증서 DRM) — 열쇠가 암호가 아니다. 암호를 물어도 소용이 없다.
     */
    val drm: Boolean get() = bit(4) || bit(8) || bit(10)

    /** 비트 14 — 변경 추적 문서. */
    val trackChanges: Boolean get() = bit(14)

    private fun bit(n: Int): Boolean = (flags ushr n) and 1L == 1L

    /** 판이 `a.b.c.d` 이상인가. 명세가 판에 따라 칸을 더한 레코드(글자 모양·문단 모양)를 가를 때 쓴다. */
    fun atLeast(a: Int, b: Int, c: Int, d: Int): Boolean {
        val want = (a shl 24) or (b shl 16) or (c shl 8) or d
        return Integer.compareUnsigned(version, want) >= 0
    }

    companion object {
        /** `"HWP Document File"` — 32바이트 칸의 앞 17바이트. 나머지는 NUL 이다. */
        private val SIGNATURE = "HWP Document File".toByteArray(Charsets.US_ASCII)

        /** 한글 97 이전(HWP 3.0)의 파일 앞머리. CFB 가 아니다. */
        private val HWP3_HEAD = "HWP Document File V3".toByteArray(Charsets.US_ASCII)

        /** 32바이트 서명 + 판 + 속성 + 사용권 + 암호 판. 뒤의 예약 칸은 보지 않는다. */
        private const val MIN_BYTES = 48

        fun parse(bytes: ByteArray): Hwp5FileHeader {
            if (bytes.size < MIN_BYTES) throw HwpFormatException("HWP 파일 머리가 짧다")
            for (i in SIGNATURE.indices) {
                if (bytes[i] != SIGNATURE[i]) throw HwpFormatException("HWP 서명이 아니다")
            }
            // 서명 뒤는 NUL 로 채운다(pyhwp 는 32바이트를 통째로 견준다). 첫 칸만 본다 — 뒤는 쓰레기가 남아도 해가 없다.
            if (bytes[SIGNATURE.size] != 0.toByte()) throw HwpFormatException("HWP 서명이 아니다")
            return Hwp5FileHeader(
                version = bytes.i32(32),
                flags = bytes.u32(36),
                license = bytes.u32(40),
                encryptVersion = bytes.u32(44),
            )
        }

        /** HWP 3.0 파일인가 — 앞머리 글자로 가른다. */
        fun isHwp3(head: ByteArray): Boolean =
            head.size >= HWP3_HEAD.size && HWP3_HEAD.indices.all { head[it] == HWP3_HEAD[it] }
    }
}
