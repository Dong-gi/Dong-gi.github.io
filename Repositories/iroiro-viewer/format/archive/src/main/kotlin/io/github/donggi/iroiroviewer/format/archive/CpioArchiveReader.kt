package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.EOFException
import java.io.IOException

/**
 * cpio 를 읽는다. 머리는 우리가 읽는다([CpioScanner]) — 목록·열기·순차 추출의 규칙은 tar 와 같은 [ScannedArchive] 다.
 *
 * ## commons-compress 의 `CpioArchiveInputStream` 을 쓰지 않는 이유
 *
 * tar 때와 같은 셋이다(`TarScanner` 의 주석). 이름을 적힌 길이만큼 메모리에 모으고(`readRange(namesize)` — 4 GiB 까지 적을 수
 * 있다), 이름을 인코딩 하나로 푼 문자열만 주며, **항목을 건너뛸 때 자료를 전부 읽는다**(자리를 옮기지 않는다) — 1 GiB 짜리
 * cpio 의 목록 한 번이 1 GiB 를 읽는다. 여기서는 압축하지 않은 파일이면 자료를 자리 옮기기로 건너뛴다(목록이 머리만 읽는다).
 *
 * cpio 를 압축으로 싼 것(`.cpio.gz`, RPM 의 짐, initramfs)은 열지 않는다 — 그것은 압축 스트림 하나로 보이고 안의 `.cpio` 는
 * '압축 파일 안의 압축 파일' 이다. tar 만 압축과 한 몸으로 여는 것은 tar 가 그렇게 쓰이기 때문이다.
 */
class CpioArchiveReader private constructor(core: ScannedArchive) : ArchiveReader by core {

    constructor(
        source: DocumentSource,
        budget: EntryBudget,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ) : this(
        ScannedArchive(source, budget, limits, codec = null, kind = ArchiveKind.CPIO) { src, cap, onDrain ->
            CpioScanner(src, cap, onDrain)
        }.also { it.prepare() },
    )
}

/**
 * cpio 의 네 머리 — commons-compress 가 읽는 것 전부다.
 *
 * | 형식 | 매직 | 머리 | 숫자 | 이름 뒤 채움 | 자료 뒤 채움 |
 * |---|---|---|---|---|---|
 * | 새 ASCII(SVR4, `newc`) | `070701` | 110 | 16진 8자리 13개 | 머리+이름을 4의 배수로 | 4의 배수로 |
 * | 새 ASCII + 검사값(`crc`) | `070702` | 110 | 위와 같다. 마지막 칸이 자료 바이트의 합 | 4 | 4 |
 * | 옛 ASCII(POSIX `odc`) | `070707` | 76 | 8진 6·11자리 | 없다 | 없다 |
 * | 옛 이진 | 16비트 `070707`(`0x71C7`) | 26 | 16비트 낱말 13개(32비트 값은 높은 낱말이 먼저) | 짝수로 | 짝수로 |
 *
 * 옛 이진은 **낱말의 바이트 차례가 만든 기계를 따른다** — 매직이 `C7 71` 이면 작은 끝(리눅스·x86), `71 C7` 이면 큰 끝이다.
 * 끝은 이름이 `TRAILER!!!` 인 항목이고, 그 뒤의 채움(블록 크기까지)은 읽지 않는다.
 */
internal object CpioFormat {

    const val NEW_HEADER = 110
    const val ODC_HEADER = 76
    const val BINARY_HEADER = 26
    const val TRAILER = "TRAILER!!!"

    /** 파일 종류(st_mode 의 위 4비트). */
    const val S_IFMT = 0xF000
    const val S_IFREG = 0x8000
    const val S_IFDIR = 0x4000
    const val S_IFLNK = 0xA000

    /** 장치·FIFO·소켓 — 내용이 없다. */
    val DEVICE_TYPES = setOf(0x2000, 0x6000, 0x1000, 0xC000)

    /**
     * 앞머리가 cpio 인가(판별용). ASCII 형식은 매직 뒤의 칸이 전부 그 진법의 숫자여야 하고, 옛 이진은 매직이 두 바이트뿐이라
     * 이름 길이 칸·이름 끝의 NUL·파일 종류까지 본다 — 우연히 `C7 71` 로 시작하는 이진 파일을 cpio 라고 하지 않으려는 것이다.
     */
    fun looksLike(head: ByteArray): Boolean {
        if (head.size >= 6 && head[0] == '0'.code.toByte() && head[1] == '7'.code.toByte() &&
            head[2] == '0'.code.toByte() && head[3] == '7'.code.toByte() && head[4] == '0'.code.toByte()
        ) {
            return when (head[5].toInt().toChar()) {
                '1', '2' -> head.size >= NEW_HEADER && (6 until NEW_HEADER).all { isHex(head[it]) }
                '7' -> head.size >= ODC_HEADER && (6 until ODC_HEADER).all { isOctal(head[it]) }
                else -> false
            }
        }
        if (head.size < BINARY_HEADER) return false
        val little = when {
            head[0] == 0xC7.toByte() && head[1] == 0x71.toByte() -> true
            head[0] == 0x71.toByte() && head[1] == 0xC7.toByte() -> false
            else -> return false
        }
        val mode = word(head, 6, little)
        val nameSize = word(head, 20, little)
        if (nameSize < 2 || BINARY_HEADER + nameSize > head.size) return false
        if (head[BINARY_HEADER + nameSize - 1].toInt() != 0) return false
        val name = head.copyOfRange(BINARY_HEADER, BINARY_HEADER + nameSize - 1)
        return kindOf(mode) != null || String(name, Charsets.ISO_8859_1) == TRAILER
    }

    /** 파일 종류. 모르는 종류는 null. */
    fun kindOf(mode: Int): ScannedEntry.Kind? = when (mode and S_IFMT) {
        S_IFREG -> ScannedEntry.Kind.FILE
        S_IFDIR -> ScannedEntry.Kind.DIRECTORY
        S_IFLNK -> ScannedEntry.Kind.LINK
        in DEVICE_TYPES -> ScannedEntry.Kind.DEVICE
        else -> null
    }

    fun word(b: ByteArray, at: Int, little: Boolean): Int {
        val lo = b[at].toInt() and 0xFF
        val hi = b[at + 1].toInt() and 0xFF
        return if (little) lo or (hi shl 8) else (lo shl 8) or hi
    }

    private fun isHex(b: Byte): Boolean = b in '0'.code.toByte()..'9'.code.toByte() ||
        b in 'a'.code.toByte()..'f'.code.toByte() || b in 'A'.code.toByte()..'F'.code.toByte()

    private fun isOctal(b: Byte): Boolean = b in '0'.code.toByte()..'7'.code.toByte()

    /** ASCII 숫자 칸. 그 진법이 아닌 글자가 섞이면 깨진 머리다. */
    fun number(b: ByteArray, at: Int, len: Int, radix: Int): Long {
        var v = 0L
        for (i in at until at + len) {
            val d = Character.digit(b[i].toInt(), radix)
            if (d < 0) throw IOException("cpio 머리의 숫자 칸이 깨졌다")
            v = v * radix + d
        }
        return v
    }
}

/**
 * cpio 머리를 하나씩 읽는다(형식은 [CpioFormat] 의 표).
 *
 * ## 종류마다
 *
 * - 자료는 **종류와 상관없이 적힌 크기만큼 놓여 있다** — 심볼릭 링크의 자료는 대상 경로다. 건너뛰기는 언제나 그 크기다
 *   (tar 와 다르다 — tar 는 폴더·링크의 크기 칸을 무시한다).
 * - 보통 파일 · 폴더 · 심볼릭 링크 · 장치(문자·블록·FIFO·소켓)를 가르고, 모르는 종류는 풀지 않는다(희소 파일과 같은 취급).
 * - **새 형식의 하드링크** — 링크가 여럿인(nlink > 1) 보통 파일은 자료를 **마지막 하나에만** 싣고 앞의 것은 크기 0 으로
 *   적는다(GNU cpio 의 `newc`). 그 크기 0 짜리를 파일로 풀면 내용 없는 사본이 생긴다 — 링크로 센다. 옛 형식은 링크마다
 *   자료를 되풀이해 실으므로 그대로 파일이다. **대가**: 서로 하드링크된 빈 파일들은 하나도 풀리지 않는다.
 * - 끝 표시(`TRAILER!!!`)가 없이 파일이 머리 경계에서 끝나면 그것을 끝으로 본다. 머리 한가운데에서 끝나면 잘린 파일이다.
 */
internal class CpioScanner(
    src: ScanSource,
    scanCap: Long = Long.MAX_VALUE,
    onDrain: (Long) -> Unit = {},
) : EntryScannerBase(src, scanCap, onDrain, "cpioHeader") {

    override fun next(): ScannedEntry? {
        if (ended) return null
        skipRest()
        val head = ByteArray(CpioFormat.NEW_HEADER)
        val got = readUpTo(head, 0, 2)
        if (got == 0) return end()
        if (got < 2) throw EOFException("cpio 가 중간에 끝났다")
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return when {
            b0 == 0xC7 && b1 == 0x71 -> binary(head, little = true)
            b0 == 0x71 && b1 == 0xC7 -> binary(head, little = false)
            else -> ascii(head)
        }
    }

    private fun ascii(head: ByteArray): ScannedEntry? {
        readFully(head, 2, 4)
        val magic = String(head, 0, 6, Charsets.ISO_8859_1)
        val newFormat = magic == "070701" || magic == "070702"
        if (!newFormat && magic != "070707") throw IOException("cpio 머리가 아니다")
        if (newFormat) {
            readFully(head, 6, CpioFormat.NEW_HEADER - 6)
            fun hex(i: Int) = CpioFormat.number(head, 6 + i * 8, 8, 16)
            val mode = hex(1).toInt()
            val nlink = hex(4)
            val mtime = hex(5)
            val size = hex(6)
            val nameSize = hex(11)
            val check = hex(12)
            val name = name(nameSize)
            // 머리(110)와 이름을 함께 4의 배수로 채운다.
            skipBytes(pad4(CpioFormat.NEW_HEADER + nameSize))
            return entry(name, mode, size, mtime, pad4(size), hardlinkCopy = nlink > 1, byteSum = if (magic == "070702") check else -1L)
        }
        readFully(head, 6, CpioFormat.ODC_HEADER - 6)
        fun oct(at: Int, len: Int) = CpioFormat.number(head, at, len, 8)
        val mode = oct(18, 6).toInt()
        val mtime = oct(48, 11)
        val nameSize = oct(59, 6)
        val size = oct(65, 11)
        val name = name(nameSize)
        return entry(name, mode, size, mtime, pad = 0L, hardlinkCopy = false, byteSum = -1L)
    }

    private fun binary(head: ByteArray, little: Boolean): ScannedEntry? {
        readFully(head, 2, CpioFormat.BINARY_HEADER - 2)
        fun word(i: Int) = CpioFormat.word(head, i * 2, little).toLong()
        // 32비트 값은 높은 낱말이 먼저다(PDP-11 의 차례) — 바이트 차례와 상관없다.
        fun long(i: Int) = (word(i) shl 16) or word(i + 1)
        val mode = word(3).toInt()
        val mtime = long(8)
        val nameSize = word(10)
        val size = long(11)
        val name = name(nameSize)
        // 이름 뒤를 짝수 자리로.
        skipBytes((CpioFormat.BINARY_HEADER + nameSize) and 1)
        return entry(name, mode, size, mtime, pad = size and 1, hardlinkCopy = false, byteSum = -1L)
    }

    /** 이름 칸 — 끝의 NUL 까지 [nameSize] 바이트. 길이가 상한을 넘으면 **읽기 전에** 거절한다. */
    private fun name(nameSize: Long): ByteArray {
        if (nameSize < 1) throw IOException("cpio 이름 길이가 0 이다")
        val raw = readBytes(nameSize, EntryNameLimits.MAX_LONG_NAME_BYTES + 1)
        var end = raw.size
        while (end > 0 && raw[end - 1].toInt() == 0) end--
        return raw.copyOf(end)
    }

    private fun entry(
        name: ByteArray,
        mode: Int,
        size: Long,
        mtime: Long,
        pad: Long,
        hardlinkCopy: Boolean,
        byteSum: Long,
    ): ScannedEntry? {
        if (String(name, Charsets.ISO_8859_1) == CpioFormat.TRAILER) return end()
        countName(name)
        var kind = CpioFormat.kindOf(mode) ?: ScannedEntry.Kind.UNREADABLE
        // 새 형식의 하드링크 — 자료가 없는 앞의 것은 링크다(위 주석).
        if (kind == ScannedEntry.Kind.FILE && hardlinkCopy && size == 0L) kind = ScannedEntry.Kind.LINK
        beginData(size, pad)
        return ScannedEntry(
            nameBytes = name,
            kind = kind,
            dataSize = size,
            declaredSize = if (kind == ScannedEntry.Kind.FILE) size else 0L,
            mtimeMillis = if (mtime <= 0 || mtime > Long.MAX_VALUE / 1000) 0L else mtime * 1000,
            dataOffset = position,
            byteSum = if (kind == ScannedEntry.Kind.FILE) byteSum else -1L,
        )
    }

    private fun pad4(n: Long): Long = (4 - n % 4) % 4
}
