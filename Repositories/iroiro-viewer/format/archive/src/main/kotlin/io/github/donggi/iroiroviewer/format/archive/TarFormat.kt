package io.github.donggi.iroiroviewer.format.archive

import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * tar 만의 구조 상한. 여러 포맷이 같이 쓰는 예산(`ParseLimits`)이 아니라 **tar 만 아는** 값이라
 * 포맷 곁에 둔다(CLAUDE.md 안전 절의 단서).
 */
internal object TarLimits {

    // 이름의 두 상한(하나 64 KiB, 합 16 MiB)은 `EntryNameLimits` 로 옮겼다 — cpio·ar 의 머리도 같은 바탕에서 같은 값을 쓴다.
    // xz 사전의 메모리 상한은 `StreamLimits.DICTIONARY_MEMORY_KIB` 로 옮겼다 — 단독 `.xz`·`.lzma` 도 같은 해제기를 쓴다.

    /**
     * PAX 확장 머리(`x`·`g`) 하나의 상한. 확장 속성(ACL·xattr)이 실리면 이름보다 길다 — 1 MiB.
     * 넘으면 목록 전체가 멈춘다(그 뒤 머리의 자리를 알 수 없다).
     */
    const val MAX_PAX_BYTES = 1024 * 1024

    /**
     * 빈 tar(0 으로만 된 파일)로 받아 줄 최대 길이. GNU tar 는 빈 목록도 막(기본 20칸 = 10,240바이트)으로 채워 쓰고
     * 막 크기를 키워도 1 MiB 를 넘기는 일은 없다시피 하다. 그보다 긴 0 은 tar 가 아니다(디스크 이미지 따위).
     */
    const val MAX_EMPTY_TAR_BYTES = 1024 * 1024

    /**
     * 항목 하나의 크기로 받아 줄 상한(2^62 = 4 EiB). 그보다 크다고 적힌 머리는 **깨진 것**이다 — 어떤 파일 시스템도 그런 파일을 담지
     * 못하고, 받아 주면 자리 셈(크기 + 채움 + 지금 자리)이 `Long` 을 넘어 상한 검사가 꺼진다(PAX `size` 와 256진 칸은
     * `Long` 의 끝까지 적을 수 있다). 2^62 아래면 그 셈들이 넘치지 않는다.
     */
    const val MAX_ENTRY_BYTES = 1L shl 62
}

/**
 * tar 머리 한 칸(512바이트)을 읽는 순수 함수들.
 *
 * ## 무엇을 따르는가
 *
 * POSIX ustar(IEEE 1003.1-1988)와 pax(2001), 그리고 GNU 확장(긴 이름 `L`·`K`, 256진 숫자, 옛 희소 파일 `S`).
 * 명세의 필드 자리는 머리 안의 **바이트 오프셋**으로 적는다 — 이름 0(100), 모드 100, 크기 124(12),
 * 수정시각 136(12), 검사합 148(8), 종류 156, 링크 이름 157(100), 매직 257(6), 판 263(2), 앞머리 345(155).
 */
internal object TarFormat {

    const val RECORD = 512

    private const val NAME = 0
    private const val NAME_LEN = 100
    private const val SIZE = 124
    private const val SIZE_LEN = 12
    private const val MTIME = 136
    private const val MTIME_LEN = 12
    private const val CHECKSUM = 148
    private const val CHECKSUM_LEN = 8
    const val TYPE = 156
    private const val MAGIC = 257
    private const val PREFIX = 345
    private const val PREFIX_LEN = 155

    /** 옛 GNU 희소 머리의 '뒤에 확장 머리가 더 있다' 표시. 본 머리는 482, 확장 머리는 504 에 있다. */
    const val GNU_SPARSE_EXTENDED = 482
    const val GNU_SPARSE_EXTENSION_EXTENDED = 504

    fun isZero(rec: ByteArray): Boolean {
        for (b in rec) if (b.toInt() != 0) return false
        return true
    }

    /**
     * 검사합이 맞는가. **tar 인지 가르는 가장 센 근거다** — 다른 파일의 앞 512바이트가 우연히 맞을 확률은
     * 없다시피 하다. 검사합 칸을 공백 여덟으로 보고 전 바이트를 더한다. 옛 도구는 바이트를 **부호 있는**
     * 값으로 더했으므로 두 합을 모두 받는다.
     */
    fun checksumOk(rec: ByteArray): Boolean {
        if (rec.size < RECORD) return false
        val stored = try {
            parseOctal(rec, CHECKSUM, CHECKSUM_LEN)
        } catch (e: IOException) {
            return false
        } ?: return false
        var unsigned = 0L
        var signed = 0L
        for (i in 0 until RECORD) {
            val b = if (i in CHECKSUM until CHECKSUM + CHECKSUM_LEN) 0x20.toByte() else rec[i]
            unsigned += b.toInt() and 0xFF
            signed += b.toInt()
        }
        return stored == unsigned || stored == signed
    }

    /** 이 512바이트가 tar 머리로 보이는가(판별용). 끝 표시(0 으로 찬 칸)는 여기서 거짓이다. */
    fun looksLikeHeader(rec: ByteArray): Boolean = rec.size >= RECORD && !isZero(rec) && checksumOk(rec)

    fun type(rec: ByteArray): Char = (rec[TYPE].toInt() and 0xFF).toChar()

    /**
     * 머리에 적힌 크기. 음수·깨진 숫자·[TarLimits.MAX_ENTRY_BYTES] 를 넘는 값이면 던진다 — 그 뒤의 머리 자리를 알 수 없게 된다.
     */
    fun size(rec: ByteArray): Long {
        val v = parseNumber(rec, SIZE, SIZE_LEN) ?: throw IOException("tar 크기 칸이 깨졌다")
        if (v < 0) throw IOException("tar 크기가 음수다")
        if (v > TarLimits.MAX_ENTRY_BYTES) throw IOException("tar 크기가 너무 크다")
        return v
    }

    /** 수정시각(epoch 밀리초). 모르거나 음수면 0 — **지어내지 않는다**(`ArchiveEntry.lastModified`). */
    fun mtimeMillis(rec: ByteArray): Long {
        val v = try {
            parseNumber(rec, MTIME, MTIME_LEN)
        } catch (e: IOException) {
            null
        } ?: return 0L
        return if (v <= 0 || v > Long.MAX_VALUE / 1000) 0L else v * 1000
    }

    /**
     * 이름 바이트. POSIX ustar 이면 앞머리(prefix)를 `/` 로 잇는다.
     *
     * **GNU 형식(`ustar  `)에서는 앞머리를 잇지 않는다** — 옛 GNU tar 는 그 자리에 접근·변경 시각과 희소 지도를
     * 적는다. star 는 앞머리 131바이트 뒤에 시각을 적고 508 에 `tar\0` 을 둔다.
     */
    fun nameBytes(rec: ByteArray): ByteArray {
        val name = cString(rec, NAME, NAME_LEN)
        if (!isPosixUstar(rec)) return name
        val star = rec[508] == 't'.code.toByte() && rec[509] == 'a'.code.toByte() &&
            rec[510] == 'r'.code.toByte() && rec[511].toInt() == 0
        val prefix = cString(rec, PREFIX, if (star) 131 else PREFIX_LEN)
        if (prefix.isEmpty()) return name
        return prefix + byteArrayOf('/'.code.toByte()) + name
    }

    /** `ustar\0` — POSIX. GNU 는 `ustar ` + `" \0"` 이다. */
    private fun isPosixUstar(rec: ByteArray): Boolean {
        val magic = "ustar".toByteArray(StandardCharsets.US_ASCII)
        for (i in magic.indices) if (rec[MAGIC + i] != magic[i]) return false
        return rec[MAGIC + 5].toInt() == 0
    }

    private fun cString(rec: ByteArray, off: Int, len: Int): ByteArray {
        var end = off
        while (end < off + len && rec[end].toInt() != 0) end++
        return rec.copyOfRange(off, end)
    }

    /**
     * 숫자 칸. 8진 ASCII 이거나(앞의 공백·NUL 은 건너뛰고 공백·NUL 에서 끝난다), 첫 바이트의 높은 비트가 선
     * **256진**(GNU·star — 8 GiB 넘는 크기와 1970년 이전 시각)이다. 음수 256진은 -1 을 준다.
     *
     * @return 빈 칸이면 null.
     * @throws IOException 8진 숫자가 아닌 글자가 섞였다.
     */
    fun parseNumber(rec: ByteArray, off: Int, len: Int): Long? {
        val first = rec[off].toInt() and 0xFF
        if (first and 0x80 != 0) {
            if (first and 0x40 != 0) return -1L
            var v = (first and 0x3F).toLong()
            for (i in 1 until len) {
                if (v > (Long.MAX_VALUE shr 8)) throw IOException("tar 256진 숫자가 너무 크다")
                v = (v shl 8) or (rec[off + i].toLong() and 0xFF)
            }
            return v
        }
        return parseOctal(rec, off, len)
    }

    private fun parseOctal(rec: ByteArray, off: Int, len: Int): Long? {
        var i = off
        val end = off + len
        while (i < end && (rec[i].toInt() == 0x20 || rec[i].toInt() == 0)) i++
        if (i == end) return null
        var v = 0L
        var digits = 0
        while (i < end) {
            val c = rec[i].toInt()
            if (c == 0x20 || c == 0) break
            if (c < '0'.code || c > '7'.code) throw IOException("tar 숫자 칸에 8진 숫자가 아닌 글자가 있다")
            v = (v shl 3) or (c - '0'.code).toLong()
            digits++
            i++
        }
        return if (digits == 0) null else v
    }

    /** [size] 를 512의 배수로 올린 것 — 자료 뒤의 채움까지. */
    fun padded(size: Long): Long = (size + RECORD - 1) / RECORD * RECORD
}

/**
 * PAX 확장 머리의 값들. 우리가 쓰는 열쇠만 남긴다.
 *
 * 형식은 `길이 열쇠=값\n` 의 되풀이이고 길이는 **그 줄 전체**(길이 숫자·공백·줄바꿈 포함)의 10진 바이트 수다.
 *
 * **남기는 것은 [KEEP] 의 넷과 '희소 파일인가' 한 비트뿐이다.** GNU 희소 파일의 열쇠(`GNU.sparse.*`)를 값째 모아
 * 두면, PAX 머리를 이어 붙인 파일 하나가 머리마다 1 MiB 씩 서로 다른 열쇠를 채워 맵이 한없이 자란다(머리 하나가
 * 열쇠 4만 개, 합치면 머리 수만큼 곱해진다). 희소 여부는 열쇠의 **이름**만으로 정해지므로 값을 들고
 * 있을 이유가 없다.
 */
internal class PaxHeaders private constructor(
    private val values: Map<String, ByteArray>,
    /** GNU 희소 파일의 PAX 표기(0.x·1.0). 내용이 구멍을 뺀 조각이라 그대로 풀면 다른 파일이 된다. */
    val sparse: Boolean,
) {

    /** 들고 있는 열쇠의 수. 위 주석의 약속(머리를 몇 개 이어도 [KEEP] 을 넘지 않는다)을 시험이 본다. */
    internal val keptKeys: Int get() = values.size

    val path: ByteArray? get() = values["path"]

    /** `hdrcharset=BINARY` 면 경로가 UTF-8 이라는 약속이 없다. */
    val binaryNames: Boolean get() = values["hdrcharset"]?.let { String(it, StandardCharsets.UTF_8) == "BINARY" } == true

    /** 크기. 음수·숫자가 아님·[TarLimits.MAX_ENTRY_BYTES] 를 넘는 값은 깨진 머리다(`TarFormat.size` 와 같은 경계). */
    val size: Long?
        get() = values["size"]?.let {
            String(it, StandardCharsets.US_ASCII).toLongOrNull()?.takeIf { v -> v >= 0 && v <= TarLimits.MAX_ENTRY_BYTES }
                ?: throw IOException("PAX 크기가 깨졌다")
        }

    /**
     * 수정시각(epoch 밀리초). 소수점 아래를 받는다. 모르거나 `Long` 밀리초로 담기지 않으면 null — 그러면 머리의
     * 시각을 쓴다. `BigDecimal.toLong()` 은 넘치면 **조용히 아래 64비트만 남긴다**(`1e20` 초가 엉뚱한 날짜가 된다).
     * 머리 쪽([TarFormat.mtimeMillis])과 같은 경계로 자른다.
     *
     * **글자 수를 먼저 본다.** PAX 값은 1 MiB 까지 오고, 10진 숫자 문자열을 `BigDecimal` 로 읽는 비용은 자릿수의
     * 제곱이라 백만 자리 하나에 몇 초가 든다 — 항목마다 그렇게 적은 파일이 목록을 멈춘다. 실제 값은
     * `1789207200.123456789` 꼴(스무 자 남짓)이다.
     */
    val mtimeMillis: Long?
        get() {
            val raw = values["mtime"] ?: return null
            if (raw.size > MAX_MTIME_CHARS) return null
            val v = String(raw, StandardCharsets.US_ASCII).toBigDecimalOrNull() ?: return null
            if (v.signum() <= 0) return null
            if (v > MAX_MTIME_SECONDS) return null
            return v.movePointRight(3).toLong()
        }

    fun merge(later: PaxHeaders): PaxHeaders = PaxHeaders(values + later.values, sparse || later.sparse)

    companion object {
        private val KEEP = setOf("path", "size", "mtime", "hdrcharset")

        /** 밀리초로 바꿔도 `Long` 에 담기는 초. */
        private val MAX_MTIME_SECONDS = java.math.BigDecimal.valueOf(Long.MAX_VALUE / 1000)

        /** PAX `mtime` 값으로 읽어 볼 최대 글자 수(초 19자리 + 점 + 나노초 9자리에 넉넉히). */
        private const val MAX_MTIME_CHARS = 64

        fun parse(data: ByteArray): PaxHeaders {
            val out = HashMap<String, ByteArray>()
            var sparse = false
            var at = 0
            while (at < data.size) {
                // 끝의 NUL 채움은 명세 밖이지만 흔하다.
                if (data[at].toInt() == 0) break
                var sp = at
                var len = 0
                while (sp < data.size && data[sp] in '0'.code.toByte()..'9'.code.toByte()) {
                    len = len * 10 + (data[sp] - '0'.code.toByte())
                    if (len > data.size) throw IOException("PAX 줄 길이가 머리보다 길다")
                    sp++
                }
                if (sp == at || sp >= data.size || data[sp] != ' '.code.toByte()) throw IOException("PAX 줄이 깨졌다")
                val end = at + len
                if (len <= 0 || end > data.size || data[end - 1] != '\n'.code.toByte()) throw IOException("PAX 줄이 깨졌다")
                var eq = sp + 1
                while (eq < end && data[eq] != '='.code.toByte()) eq++
                if (eq >= end) throw IOException("PAX 줄에 '=' 이 없다")
                val key = String(data, sp + 1, eq - sp - 1, StandardCharsets.UTF_8)
                if (key in KEEP) out[key] = data.copyOfRange(eq + 1, end - 1)
                if (key.startsWith("GNU.sparse.")) sparse = true
                at = end
            }
            return PaxHeaders(out, sparse)
        }

        val EMPTY = PaxHeaders(emptyMap(), sparse = false)
    }
}
