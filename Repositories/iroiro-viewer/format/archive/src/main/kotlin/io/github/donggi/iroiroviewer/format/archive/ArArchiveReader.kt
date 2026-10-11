package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.EOFException
import java.io.IOException

/**
 * ar(`!<arch>`)을 읽는다 — `.ar`, 그리고 데비안 꾸러미 `.deb`(첫 항목이 `debian-binary` 인 ar). 머리는 우리가
 * 읽고([ArScanner]) 목록·열기·순차 추출의 규칙은 tar·cpio 와 같은 [ScannedArchive] 다.
 *
 * commons-compress 의 `ArArchiveInputStream` 을 쓰지 않는 이유는 cpio 와 같다 — 이름을 ASCII 로만 풀어(그 밖의 바이트는
 * U+FFFD 가 된다) 원본 바이트가 사라지고, 건너뛸 때 자료를 전부 읽는다.
 *
 * `.deb` 안의 `control.tar.*`·`data.tar.*` 는 **열지 않는다** — 압축 파일 안의 압축 파일이다(풀어서 연다). 목록은
 * `debian-binary`·`control.tar.xz`·`data.tar.xz` 셋으로 보인다.
 *
 * @param kind [ArchiveKind.AR] 또는 [ArchiveKind.DEB] — 판별(`Archives.probeKind`)이 첫 항목의 이름으로 정한다.
 */
class ArArchiveReader private constructor(core: ScannedArchive) : ArchiveReader by core {

    constructor(
        source: DocumentSource,
        budget: EntryBudget,
        limits: ParseLimits = ParseLimits.DEFAULT,
        kind: ArchiveKind = ArchiveKind.AR,
    ) : this(
        ScannedArchive(source, budget, limits, codec = null, kind = kind.also { require(it.shape == ArchiveKind.Shape.AR) }) { src, cap, onDrain ->
            ArScanner(src, cap, onDrain)
        }.also { it.prepare() },
    )
}

/**
 * ar 의 모양.
 *
 * 전역 머리 `!<arch>\n`(8바이트) 뒤에 항목마다 60바이트 머리 — 이름 16 · 수정시각 12(10진 초) · uid 6 · gid 6 ·
 * 모드 8(8진) · 크기 10(10진) · `` `\n `` 2 — 와 자료가 오고, 자료가 홀수 바이트면 `\n` 한 바이트로 짝을 맞춘다. 칸은 공백으로
 * 채운다.
 *
 * | 이름 칸 | 뜻 |
 * |---|---|
 * | `이름/` | GNU — `/` 가 이름의 끝이다(공백이 든 이름을 허락한다) |
 * | `이름` | BSD·System V — 끝의 공백을 뗀다 |
 * | `//` | GNU 긴 이름 표. 항목이 아니다 |
 * | `/123` | GNU 긴 이름 — 표의 123번째 바이트부터 `/\n` 까지 |
 * | `/`·`/SYM64/` | GNU 기호 표(정적 라이브러리의 색인). 항목이 아니다 |
 * | `#1/17` | BSD 긴 이름 — 자료의 앞 17바이트가 이름이고 크기 칸은 그것까지 센다 |
 * | `__.SYMDEF`… | BSD 기호 표. 항목이 아니다 — 이름 칸에 바로 오거나(`__.SYMDEF`) `#1/` 긴 이름으로 온다(아래 [isSymbolTable]) |
 */
internal object ArFormat {

    val MAGIC = "!<arch>\n".toByteArray(Charsets.ISO_8859_1)
    const val HEADER = 60
    private const val DEBIAN_BINARY = "debian-binary"
    private val SYMDEF = "__.SYMDEF".toByteArray(Charsets.ISO_8859_1)

    /**
     * **항목이 아닌 멤버**(기호 표 `/`·`/SYM64/`·`__.SYMDEF…`, 긴 이름 표 `//`)의 상한. 정적 라이브러리는 기호 표 하나와 긴 이름 표
     * 하나를 둔다 — 셋을 넘을 일이 없다. 그것들은 항목이 아니라 항목 수 상한(`maxEntries`)에 세지 않았는데, 이름 표를 `__.SYMDEF…` 로
     * 시작하게 지은 파일이 **머리마다 16 MiB 를 훑으면서도** 어느 상한에도 걸리지 않았다. 넘으면 상한이다.
     */
    const val MAX_META_MEMBERS = 16

    fun looksLike(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /**
     * BSD 기호 표인가 — **이름을 다 푼 뒤에** 본다. 맥의 cctools `ranlib` 은 기호 표를 `#1/20` 긴 이름(`__.SYMDEF SORTED` 와 NUL
     * 채움)으로 적는다고 알려져 있다(우리가 그 도구로 만든 표본은 없다 — 확인하지 못했다). 이름 칸만 보면 `#1/20` 이 보통 파일로
     * 지나가 목록에 기호 표가 파일로 뜬다. `__.SYMDEF_64`(64비트 표)도 같은 앞머리다.
     */
    fun isSymbolTable(name: ByteArray): Boolean {
        // 앞 아홉 바이트만 견준다 — 이름 전체를 글로 바꾸면 긴 이름(64 KiB 까지)마다 그만큼을 짓는다.
        if (name.size < SYMDEF.size) return false
        for (i in SYMDEF.indices) if (name[i] != SYMDEF[i]) return false
        return true
    }

    /** 첫 항목의 이름이 `debian-binary` 인가 — 데비안 꾸러미다. 이름 칸 뒤는 공백(BSD 모양)이나 `/`(GNU 모양)다. */
    fun isDebian(head: ByteArray): Boolean {
        if (!looksLike(head) || head.size < MAGIC.size + 16) return false
        val name = String(head, MAGIC.size, 16, Charsets.ISO_8859_1)
        if (!name.startsWith(DEBIAN_BINARY)) return false
        val rest = name.substring(DEBIAN_BINARY.length)
        return rest.trimEnd(' ').let { it.isEmpty() || it == "/" }
    }

    /** 공백으로 채운 10진 숫자 칸. 비었으면 [blank]. 숫자가 아닌 글자가 섞이면 깨진 머리다. */
    fun decimal(b: ByteArray, at: Int, len: Int, blank: Long = 0L): Long = number(b, at, len, 10, blank)

    fun number(b: ByteArray, at: Int, len: Int, radix: Int, blank: Long = 0L): Long {
        var i = at
        val end = at + len
        while (i < end && b[i] == ' '.code.toByte()) i++
        if (i == end) return blank
        var v = 0L
        var digits = 0
        while (i < end && b[i] != ' '.code.toByte()) {
            val d = Character.digit(b[i].toInt(), radix)
            if (d < 0) throw IOException("ar 머리의 숫자 칸이 깨졌다")
            if (v > (Long.MAX_VALUE - d) / radix) throw IOException("ar 머리의 숫자가 너무 크다")
            v = v * radix + d
            digits++
            i++
        }
        while (i < end) {
            if (b[i] != ' '.code.toByte()) throw IOException("ar 머리의 숫자 칸이 깨졌다")
            i++
        }
        return if (digits == 0) blank else v
    }
}

/**
 * ar 머리를 하나씩 읽는다(모양은 [ArFormat] 의 표). 항목은 언제나 보통 파일이다 — ar 에는 폴더·링크가 없다(모드 칸은 읽지
 * 않는다).
 *
 * 이름의 상한은 tar·cpio 와 같다([EntryNameLimits] — 이름 하나 64 KiB, 합 16 MiB). GNU 긴 이름 표도 그 합(16 MiB)을 넘으면
 * 읽기 전에 거절한다. 적힌 길이가 파일에 남은 것보다 길면 상한이 아니라 깨진 머리다(`EntryScannerBase.readBytes`).
 */
internal class ArScanner(
    src: ScanSource,
    scanCap: Long = Long.MAX_VALUE,
    onDrain: (Long) -> Unit = {},
) : EntryScannerBase(src, scanCap, onDrain, "arHeader") {

    private var started = false

    /** GNU 긴 이름 표(`//` 항목의 자료). */
    private var longNames: ByteArray? = null

    /** 지금까지 지나간 항목이 아닌 멤버의 수([ArFormat.MAX_META_MEMBERS]). */
    private var metaMembers = 0

    override fun next(): ScannedEntry? {
        if (ended) return null
        skipRest()
        if (!started) {
            val magic = ByteArray(ArFormat.MAGIC.size)
            readFully(magic, 0, magic.size)
            if (!magic.contentEquals(ArFormat.MAGIC)) throw IOException("ar 이 아니다")
            started = true
        }
        while (true) {
            val h = ByteArray(ArFormat.HEADER)
            val got = readUpTo(h, 0, ArFormat.HEADER)
            if (got == 0) return end()
            if (got < ArFormat.HEADER) throw EOFException("ar 가 중간에 끝났다")
            if (h[58] != '`'.code.toByte() || h[59] != '\n'.code.toByte()) throw IOException("ar 머리가 깨졌다")
            val size = ArFormat.decimal(h, 48, 10)
            val pad = size and 1
            val field = String(h, 0, 16, Charsets.ISO_8859_1).trimEnd(' ')
            when {
                field == "//" -> {
                    countMeta()
                    // 표는 하나다(GNU ar 가 그렇게 쓴다). 둘째는 앞의 것을 갈아 끼워 앞 머리들의 `/123` 이 다른 이름을 가리키게 한다.
                    if (longNames != null) throw IOException("ar 긴 이름 표가 둘이다")
                    longNames = readBytes(size, EntryNameLimits.MAX_NAME_BYTES_TOTAL)
                    skipBytes(pad)
                    continue
                }
                // GNU 기호 표. BSD 의 것(`__.SYMDEF`)은 이름을 푼 뒤에 본다 — `#1/` 긴 이름으로도 온다.
                field == "/" || field == "/SYM64/" -> {
                    countMeta()
                    skipBytes(size + pad)
                    continue
                }
            }
            var dataSize = size
            val name: ByteArray = when {
                field.length > 1 && field[0] == '/' && field.substring(1).all { it.isDigit() } ->
                    gnuLongName(field.substring(1).toLongOrNull() ?: throw IOException("ar 긴 이름의 자리가 깨졌다"))
                field.startsWith("#1/") && field.length > 3 && field.substring(3).all { it.isDigit() } -> {
                    val n = field.substring(3).toLongOrNull() ?: throw IOException("ar 긴 이름의 길이가 깨졌다")
                    if (n > size) throw IOException("ar 긴 이름이 항목보다 길다")
                    dataSize = size - n
                    readBytes(n, EntryNameLimits.MAX_LONG_NAME_BYTES).trimEndNul()
                }
                // GNU 는 이름 끝을 `/` 로 표시한다(그래서 이름에 공백을 허락한다). BSD 는 없다.
                field.endsWith("/") -> h.copyOf(field.length - 1)
                else -> h.copyOf(field.length)
            }
            if (ArFormat.isSymbolTable(name)) {
                // 긴 이름은 이미 읽었다 — 남은 자료와 채움만 지나간다. 항목이 아니라 항목 수에 세지 않으므로 따로 센다.
                countMeta()
                skipBytes(dataSize + pad)
                continue
            }
            countName(name)
            val mtime = ArFormat.decimal(h, 16, 12)
            beginData(dataSize, pad)
            return ScannedEntry(
                nameBytes = name,
                kind = ScannedEntry.Kind.FILE,
                dataSize = dataSize,
                declaredSize = dataSize,
                mtimeMillis = if (mtime <= 0 || mtime > Long.MAX_VALUE / 1000) 0L else mtime * 1000,
                dataOffset = position,
            )
        }
    }

    /**
     * GNU 긴 이름 — 표의 [offset] 에서 `/\n`(또는 `\n`·NUL) 까지. **이름 하나의 상한([EntryNameLimits.MAX_LONG_NAME_BYTES])까지만
     * 훑는다** — 표는 16 MiB 까지 오는데, 줄바꿈 없는 표를 가리키는 머리마다 끝까지 훑고 복사하면 600 KB 의 머리 1만 개가 목록을 몇 분
     * 멈췄다. 상한 안에서 끝나지 않는 이름은 상한이다(BSD 긴 이름 `#1/` 과 같은 값).
     */
    private fun gnuLongName(offset: Long): ByteArray {
        val table = longNames ?: throw IOException("ar 긴 이름 표가 없다")
        if (offset < 0 || offset >= table.size) throw IOException("ar 긴 이름의 자리가 표 밖이다")
        val start = offset.toInt()
        val limit = minOf(table.size.toLong(), start.toLong() + EntryNameLimits.MAX_LONG_NAME_BYTES + 1).toInt()
        var end = start
        while (end < limit && table[end] != '\n'.code.toByte() && table[end].toInt() != 0) end++
        if (end == limit && limit < table.size) {
            throw ParseLimitExceededException(
                "arHeader",
                "긴 이름이 ${EntryNameLimits.MAX_LONG_NAME_BYTES}바이트 안에서 끝나지 않는다",
            )
        }
        if (end > start && table[end - 1] == '/'.code.toByte()) end--
        return table.copyOfRange(start, end)
    }

    private fun countMeta() {
        if (++metaMembers > ArFormat.MAX_META_MEMBERS) {
            throw ParseLimitExceededException("arHeader", "항목이 아닌 멤버 ${metaMembers}개 (상한 ${ArFormat.MAX_META_MEMBERS})")
        }
    }

    private fun ByteArray.trimEndNul(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1].toInt() == 0) end--
        return if (end == size) this else copyOf(end)
    }
}
