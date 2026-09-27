package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.BitSet

/**
 * CFB(Compound File Binary, 흔히 OLE2) 파일 하나 — MS-CFB v3·v4 를 **엄격하게** 읽는다.
 *
 * 암호가 걸린 OOXML(MS-OFFCRYPTO 의 `EncryptionInfo`·`EncryptedPackage`), 이전 형식 오피스
 * (`WordDocument`·`Workbook`), HWP 5.0(`FileHeader`)이 전부 이 그릇에 들어 있다. 이 클래스는
 * 그릇만 안다 — 무엇이 들었는지는 부르는 쪽이 이름으로 가린다.
 *
 * ## 왜 전용 불변식이 필요한가
 *
 * CFB 는 파일 안에 **파일 시스템**(섹터·할당표·디렉터리)이 통째로 들어 있는 구조라, ZIP 의
 * 압축비·XML 의 중첩 같은 기존 상한이 하나도 걸리지 않는다. 공격자가 적는 값은 전부 '다음
 * 섹터 번호' 이고, 그 번호를 믿으면 무한 순환·범위 밖 읽기·선언 크기만큼의 할당이 된다.
 * 그래서 이 클래스가 지키는 것(CLAUDE.md '안전'):
 *
 * * **판과 섹터 크기가 짝이 맞아야 연다** — v3 는 512(`sectorShift` 9), v4 는 4096(12)만.
 *   작은 섹터는 64(6), 작은 스트림 경계는 4096 만. 그 밖은 명세가 허락하지 않는다.
 * * **섹터 번호는 범위를 검사한 뒤에만 읽는다.** 범위는 **실제 파일 크기**에서 셈한다.
 * * **모든 체인 순회(FAT·DIFAT·miniFAT·디렉터리·스트림)에 방문 집합과 길이 상한을 함께 건다.**
 *   방문 집합이 순환을 바로 잡고, 길이 상한(파일의 섹터 수)이 그것을 겹쳐 막는다.
 * * **스트림 길이는 `min(적힌 크기, 체인 길이 × 섹터 크기)`** 이고, 읽기 전에 부른 쪽의
 *   상한(`maxBytes`)과도 견준다. **적힌 크기로 먼저 할당하지 않는다** — 체인을 걸어 본 뒤에 잡는다.
 * * **디렉터리 트리(좌·우 형제와 자식)는 방문 집합과 항목 수 상한으로 건다.** 한 항목이 두 번
 *   닿으면(순환이든 두 부모든) 파일이 깨진 것으로 본다.
 * * 이름이 망가진 항목(길이 칸이 2..64 의 짝수가 아니거나, 끝에 NUL 이 없거나, 중간에 NUL 이
 *   있다)은 **목록에서 뺀다.** 트리 모양은 그대로 따라가므로 그 형제들은 보인다.
 * * v3 파일은 스트림 크기의 **위 32비트를 무시한다**(명세 — 옛 구현이 쓰레기를 남긴다).
 *
 * 너그러운 곳도 있다 — 읽지 않는 값(CLSID·부판·트랜잭션 서명·색)은 보지 않는다. 적힌 대로
 * 믿지 않으면 되는 값이고, 그것까지 막으면 멀쩡한 파일을 거절한다.
 *
 * 실패는 [CfbFormatException](깨졌다) 또는 `ParseLimitExceededException`(너무 크다) 하나로 온다.
 *
 * ## 읽는 방법
 *
 * 무작위 접근 채널(`SeekableByteChannel`)을 받아 **필요한 섹터만 그때그때** 읽는다. 들고 있는
 * 것은 머리·디렉터리·FAT 섹터 번호표(파일 64 KiB 당 4바이트)와 작은 섹터 캐시뿐이다 — FAT
 * 자체를 통째로 올리지 않는다(선언된 FAT 섹터 수는 공격자가 적는 값이다).
 *
 * ## 스레드
 *
 * **스레드 안전하다** — 채널 읽기와 캐시를 한 잠금으로 줄 세운다. 다만 [openStream] 이 돌려준
 * 스트림 하나를 여러 스레드가 함께 읽으면 안 된다(`InputStream` 의 보통 약속).
 *
 * ## 자원
 *
 * [open] 이 채널의 주인이 된다. 여는 데 실패하면 채널을 **스스로 닫고** 던진다(생성자가 던진
 * 객체는 호출자가 닫을 방법이 없다). [close] 뒤의 읽기는 `IOException` 이다.
 */
class CfbFile private constructor(
    private val channel: SeekableByteChannel,
    private val limits: CfbLimits,
) : Closeable {

    private val lock = Any()

    @Volatile
    private var closed = false

    private val fileSize: Long = channel.size()

    /** 판(3 또는 4). */
    val version: Int

    private val sectorShift: Int
    private val sectorSize: Int

    /** FAT·miniFAT 섹터 하나에 든 칸 수. */
    private val perFatSector: Int

    /** 파일에 실제로 있는 섹터 수(마지막 섹터가 잘려 있으면 그것도 센다). 섹터 번호 범위의 기준이다. */
    private val numSectors: Int

    /** k 번째 FAT 섹터의 번호. **파일의 섹터를 덮는 데 필요한 만큼만** 든다. */
    private val fatSectors: IntArray

    private val firstMiniFat: Int
    private val entries: Array<CfbEntry?>
    private val childIds: Array<IntArray?>

    /** 뿌리 저장소. */
    val root: CfbEntry

    /** 최근에 읽은 FAT·miniFAT 섹터. 체인은 대개 이어져 있어 같은 FAT 섹터를 수백 번 연달아 본다. */
    private val fatCache = object : LinkedHashMap<Int, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean = size > FAT_CACHE
    }

    /** 작은 스트림 상태. 처음 쓸 때 세운다([miniState]). */
    private var mini: MiniState? = null

    init {
        if (fileSize > limits.maxFileBytes) {
            throw ParseLimitExceededException("maxCfbFileBytes", "${fileSize}바이트 (상한 ${limits.maxFileBytes})")
        }
        if (fileSize < HEADER_SIZE) throw CfbFormatException("CFB 머리보다 짧다")
        val h = ByteArray(HEADER_SIZE)
        readAt(0, h, 0, HEADER_SIZE)
        for (i in SIGNATURE.indices) {
            if (h[i] != SIGNATURE[i]) throw CfbFormatException("CFB 서명이 아니다")
        }
        if (h.u16(28) != 0xFFFE) throw CfbFormatException("바이트 순서 표시가 틀렸다")
        val major = h.u16(26)
        val shift = h.u16(30)
        when (major) {
            3 -> if (shift != 9) throw CfbFormatException("v3 인데 섹터가 512바이트가 아니다")
            4 -> if (shift != 12) throw CfbFormatException("v4 인데 섹터가 4096바이트가 아니다")
            else -> throw CfbFormatException("모르는 CFB 판")
        }
        if (h.u16(32) != MINI_SHIFT) throw CfbFormatException("작은 섹터가 64바이트가 아니다")
        if (h.u32(56) != MINI_CUTOFF.toLong()) throw CfbFormatException("작은 스트림 경계가 4096이 아니다")
        version = major
        sectorShift = shift
        sectorSize = 1 shl shift
        perFatSector = sectorSize / 4
        // 머리가 섹터 하나를 차지한다(v4 는 4096바이트 가운데 앞 512만 쓴다). 그 뒤가 섹터 0 이다.
        if (fileSize < 2L * sectorSize) throw CfbFormatException("섹터가 하나도 없다")
        numSectors = ((fileSize - sectorSize + sectorSize - 1) / sectorSize).toInt()

        fatSectors = readFatSectorList(h)
        firstMiniFat = h.i32(60)

        val dir = readDirectory(h.i32(48))
        entries = dir.first
        childIds = dir.second
        root = entries[0]!!
    }

    // ---- 머리와 FAT ---------------------------------------------------------------

    /**
     * DIFAT(머리의 109칸 + DIFAT 섹터 체인)에서 FAT 섹터 번호표를 만든다.
     *
     * 적힌 FAT 섹터 수만큼 **전부 걸어서 검사**하되(범위·중복·체인 순환), 들고 있는 것은 파일의
     * 섹터를 덮는 데 필요한 앞쪽만이다. 적힌 수는 파일의 섹터 수를 넘을 수 없다 — FAT 섹터도
     * 파일 안의 섹터다.
     */
    private fun readFatSectorList(h: ByteArray): IntArray {
        val numFat = h.u32(44)
        val firstDifat = h.i32(68)
        val numDifat = h.u32(72)
        if (numFat < 1 || numFat > numSectors) throw CfbFormatException("FAT 섹터 수가 파일과 맞지 않다")
        if (numDifat > numSectors) throw CfbFormatException("DIFAT 섹터 수가 파일과 맞지 않다")
        val count = numFat.toInt()
        val needed = (numSectors + perFatSector - 1) / perFatSector
        val keep = IntArray(minOf(count, needed))
        val seen = BitSet()
        var collected = 0

        fun take(id: Int) {
            if (!isRegular(id)) throw CfbFormatException("FAT 섹터 번호가 범위를 벗어났다")
            if (seen.get(id)) throw CfbFormatException("FAT 섹터가 두 번 적혔다")
            seen.set(id)
            if (collected < keep.size) keep[collected] = id
            collected++
        }

        for (i in 0 until minOf(count, HEADER_DIFAT)) take(h.i32(HEADER_DIFAT_OFFSET + i * 4))
        if (count > HEADER_DIFAT) {
            val visited = BitSet()
            var sector = firstDifat
            var steps = 0L
            val buf = ByteArray(sectorSize)
            while (collected < count) {
                if (!isRegular(sector)) throw CfbFormatException("DIFAT 체인이 끊겼다")
                if (visited.get(sector)) throw CfbFormatException("DIFAT 체인이 순환한다")
                visited.set(sector)
                if (++steps > numDifat) throw CfbFormatException("DIFAT 체인이 적힌 것보다 길다")
                readRegular(sector, 0, buf, 0, sectorSize)
                for (j in 0 until perFatSector - 1) {
                    if (collected == count) break
                    take(buf.i32(j * 4))
                }
                sector = buf.i32((perFatSector - 1) * 4)
            }
        }
        return keep
    }

    private fun isRegular(id: Int): Boolean = id in 0 until numSectors

    private fun fatEntry(sector: Int): Int {
        val k = sector / perFatSector
        if (k >= fatSectors.size) throw CfbFormatException("FAT 가 이 섹터를 덮지 않는다")
        return fatSector(fatSectors[k]).i32((sector % perFatSector) * 4)
    }

    private fun fatSector(id: Int): ByteArray = synchronized(lock) {
        fatCache[id] ?: ByteArray(sectorSize).also {
            readRegular(id, 0, it, 0, sectorSize)
            fatCache[id] = it
        }
    }

    // ---- 디렉터리 -------------------------------------------------------------------

    /**
     * 디렉터리 체인을 읽고 뿌리에서부터 트리를 세운다.
     *
     * 트리는 **한 번에 끝까지** 세운다(반복문, 명시적 스택). 방문 집합은 파일 전체에 하나다 —
     * 한 항목이 두 번 닿으면 순환이거나 두 부모를 가진 것이고, 어느 쪽이든 명세 위반이다.
     * 그래서 세는 일이 항목 수에 비례해 끝나고, 되부름이 없어 스택도 터지지 않는다.
     */
    private fun readDirectory(firstDir: Int): Pair<Array<CfbEntry?>, Array<IntArray?>> {
        val perDirSector = sectorSize / DIR_ENTRY
        val maxDirSectors = (limits.maxEntries + perDirSector - 1) / perDirSector
        val sectors = ArrayList<Int>()
        val visited = BitSet()
        var sector = firstDir
        if (sector == END_OF_CHAIN) throw CfbFormatException("디렉터리가 없다")
        while (sector != END_OF_CHAIN) {
            if (!isRegular(sector)) throw CfbFormatException("디렉터리 섹터 번호가 범위를 벗어났다")
            if (visited.get(sector)) throw CfbFormatException("디렉터리 체인이 순환한다")
            visited.set(sector)
            if (sectors.size >= maxDirSectors) {
                throw ParseLimitExceededException("maxEntries", "디렉터리 섹터 ${sectors.size + 1}개 (상한 $maxDirSectors)")
            }
            sectors.add(sector)
            sector = fatEntry(sector)
        }
        val slots = sectors.size * perDirSector
        val raw = ByteArray(sectors.size * sectorSize)
        for ((i, s) in sectors.withIndex()) readRegular(s, 0, raw, i * sectorSize, sectorSize)

        val types = IntArray(slots) { raw[it * DIR_ENTRY + 66].toInt() and 0xFF }
        fun link(slot: Int, at: Int): Int {
            val id = raw.i32(slot * DIR_ENTRY + at)
            if (id != NO_STREAM && id !in 0 until slots) throw CfbFormatException("디렉터리 번호가 범위를 벗어났다")
            return id
        }
        if (types[0] != TYPE_ROOT) throw CfbFormatException("첫 디렉터리 항목이 뿌리가 아니다")

        val built = arrayOfNulls<CfbEntry>(slots)
        val children = arrayOfNulls<IntArray>(slots)
        built[0] = CfbEntry(0, decodeName(raw, 0) ?: "", CfbEntryType.ROOT, sizeOf(raw, 0), raw.i32(116))

        val reached = BitSet()
        reached.set(0)
        val storages = ArrayDeque<Int>()
        storages.addLast(0)
        val stack = ArrayDeque<Int>()
        while (storages.isNotEmpty()) {
            val parent = storages.removeFirst()
            val list = ArrayList<Int>()
            // 형제 트리를 중위 순회한다 — 명세의 이름 순서가 그대로 나온다.
            var node = link(parent, 76)
            while (node != NO_STREAM || stack.isNotEmpty()) {
                while (node != NO_STREAM) {
                    if (reached.get(node)) throw CfbFormatException("디렉터리 트리가 순환한다")
                    reached.set(node)
                    stack.addLast(node)
                    node = link(node, 68)
                }
                val cur = stack.removeLast()
                val type = when (types[cur]) {
                    TYPE_STORAGE -> CfbEntryType.STORAGE
                    TYPE_STREAM -> CfbEntryType.STREAM
                    else -> throw CfbFormatException("디렉터리 트리가 빈 항목이나 모르는 항목을 가리킨다")
                }
                val name = decodeName(raw, cur)
                // 이름이 망가진 항목은 목록에서 빼고 그 아래로도 내려가지 않는다. 형제는 계속 따라간다.
                if (name != null) {
                    val size = if (type == CfbEntryType.STREAM) sizeOf(raw, cur) else 0L
                    built[cur] = CfbEntry(cur, name, type, size, raw.i32(cur * DIR_ENTRY + 116))
                    list.add(cur)
                    if (type == CfbEntryType.STORAGE) storages.addLast(cur)
                }
                node = link(cur, 72)
            }
            children[parent] = list.toIntArray()
        }
        return built to children
    }

    /** 적힌 크기. v3 는 아래 32비트만, v4 는 64비트(음수가 되는 값은 '아주 크다' 로 본다). */
    private fun sizeOf(raw: ByteArray, slot: Int): Long {
        val o = slot * DIR_ENTRY + 120
        if (version == 3) return raw.u32(o)
        val v = raw.u32(o) or (raw.u32(o + 4) shl 32)
        return if (v < 0) Long.MAX_VALUE else v
    }

    /** 이름. 길이 칸이 2..64 의 짝수이고, 끝에 NUL 이 있고, 중간에 NUL 이 없어야 한다. 아니면 null. */
    private fun decodeName(raw: ByteArray, slot: Int): String? {
        val o = slot * DIR_ENTRY
        val len = raw.u16(o + 64)
        if (len < 2 || len > 64 || len % 2 != 0) return null
        val chars = len / 2 - 1
        if (raw.u16(o + chars * 2) != 0) return null
        val out = CharArray(chars)
        for (i in 0 until chars) {
            val c = raw.u16(o + i * 2)
            if (c == 0) return null
            out[i] = c.toChar()
        }
        return String(out)
    }

    // ---- 찾기 ----------------------------------------------------------------------

    /** 자식들 — 명세의 이름 순서(짧은 것 먼저, 같으면 대문자 순). 스트림이면 비어 있다. */
    fun children(entry: CfbEntry): List<CfbEntry> {
        own(entry)
        val ids = childIds[entry.id] ?: return emptyList()
        return ids.map { entries[it]!! }
    }

    /**
     * 저장소 안에서 이름으로 하나. 명세의 비교(대소문자 무시, [CfbEntry.sameName])를 따른다.
     * 같은 이름이 둘 이상이면(명세 위반) 이름 순서로 먼저 오는 것이다.
     */
    fun child(storage: CfbEntry, name: String): CfbEntry? =
        children(storage).firstOrNull { CfbEntry.sameName(it.name, name) }

    /** 뿌리에서 경로를 따라 하나. 경로가 비었으면 뿌리. 없으면 null. */
    fun find(vararg path: String): CfbEntry? {
        var cur = root
        for (name in path) {
            if (!cur.isStorage) return null
            cur = child(cur, name) ?: return null
        }
        return cur
    }

    private fun own(entry: CfbEntry) {
        require(entry.id in entries.indices && entries[entry.id] === entry) { "이 CFB 의 항목이 아니다" }
    }

    // ---- 스트림 --------------------------------------------------------------------

    /**
     * 실제로 읽히는 길이 — `min(적힌 크기, 체인 길이 × 섹터 크기)`. 체인을 끝까지(또는 적힌 크기까지)
     * 걷는다. 걷는 길이는 파일의 섹터 수로 잘린다.
     */
    fun streamLength(entry: CfbEntry): Long = effectiveLength(entry, Long.MAX_VALUE)

    /**
     * 스트림 하나를 통째로. 실제 길이가 [maxBytes] 를 넘으면 `ParseLimitExceededException`.
     *
     * **적힌 크기로 먼저 할당하지 않는다.** 체인을 `maxBytes + 1` 바이트어치까지만 걸어 실제 길이를
     * 구한 뒤에 잡는다 — v4 에는 2^40 바이트를 적은 스트림도 들어갈 수 있다.
     */
    fun readStream(entry: CfbEntry, maxBytes: Long): ByteArray {
        own(entry)
        require(entry.isStream) { "스트림이 아니다" }
        require(maxBytes >= 0) { "음수 상한" }
        val len = effectiveLength(entry, if (maxBytes == Long.MAX_VALUE) maxBytes else maxBytes + 1)
        if (len > maxBytes) throw ParseLimitExceededException("maxBytes", "스트림 ${len}바이트 (상한 $maxBytes)")
        if (len > MAX_ARRAY) throw ParseLimitExceededException("maxBytes", "스트림 ${len}바이트")
        val out = ByteArray(len.toInt())
        StreamInput(entry, len).use { input ->
            var read = 0
            while (read < out.size) {
                val n = input.read(out, read, out.size - read)
                if (n < 0) throw CfbFormatException("스트림이 체인보다 먼저 끝났다")
                read += n
            }
        }
        return out
    }

    /**
     * 스트림 하나를 차례로 읽는다. 체인은 **읽는 만큼만** 걷는다(방문 집합과 길이 상한은 그대로).
     * 체인이 적힌 크기보다 먼저 끝나면 거기서 EOF 다. 부르는 쪽이 닫는다.
     */
    fun openStream(entry: CfbEntry): InputStream {
        own(entry)
        require(entry.isStream) { "스트림이 아니다" }
        return StreamInput(entry, entry.size)
    }

    private fun isMini(entry: CfbEntry): Boolean = entry.type == CfbEntryType.STREAM && entry.size < MINI_CUTOFF

    private fun effectiveLength(entry: CfbEntry, cap: Long): Long {
        own(entry)
        if (entry.size <= 0) return 0
        val mini = isMini(entry)
        val unit = if (mini) MINI_SECTOR else sectorSize
        val target = minOf(entry.size, cap)
        val cursor = ChainCursor(entry.startSector, mini)
        var bytes = 0L
        while (bytes < target) {
            if (cursor.next() < 0) break
            bytes += unit
        }
        return minOf(entry.size, bytes)
    }

    /**
     * 체인 하나를 걷는 것. 스트림·작은 스트림 그릇·miniFAT 체인이 이것을 지난다(디렉터리와 DIFAT 은
     * 여는 동안 따로 걷되 같은 두 겹 — 방문 집합과 길이 상한 — 을 건다). 방문 집합(`BitSet`)이 순환을
     * 바로 잡고, 길이 상한(섹터 수)이 그 뒤를 받친다. 다음 번호는 쓸 때 검사한다: 스트림이 마지막 섹터에서
     * 끝나면 그 섹터의 다음 칸(대개 ENDOFCHAIN, 엉성한 구현은 FREESECT)은 보지 않는다.
     */
    private inner class ChainCursor(start: Int, private val mini: Boolean) {
        private val visited = BitSet()
        private var nextId = start
        private var steps = 0L
        private val limit: Long = if (mini) miniState().sectors.toLong() else numSectors.toLong()

        /** 다음 섹터 번호. 체인이 끝났으면 -1. */
        fun next(): Int {
            val s = nextId
            if (s == END_OF_CHAIN) return -1
            val valid = if (mini) s in 0 until miniState().sectors else isRegular(s)
            if (!valid) throw CfbFormatException("섹터 번호가 범위를 벗어났다")
            if (visited.get(s)) throw CfbFormatException("섹터 체인이 순환한다")
            visited.set(s)
            if (++steps > limit) throw CfbFormatException("섹터 체인이 파일보다 길다")
            nextId = if (mini) miniFatEntry(s) else fatEntry(s)
            return s
        }

        /** [next] 가 다음에 줄 번호(검사 전). 이어진 섹터를 한 번에 읽을지 보는 데만 쓴다. */
        fun peek(): Int = nextId
    }

    private inner class StreamInput(entry: CfbEntry, private val length: Long) : InputStream() {
        private val mini = isMini(entry)
        private val unit = if (mini) MINI_SECTOR else sectorSize
        private val cursor = ChainCursor(entry.startSector, mini)
        private var pos = 0L
        private var sector = -1
        private var inSector = unit
        private var eof = entry.size <= 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (eof || pos >= length) return -1
            if (inSector >= unit) {
                val s = cursor.next()
                if (s < 0) {
                    eof = true
                    return -1
                }
                sector = s
                inSector = 0
            }
            val want = minOf(len.toLong(), length - pos)
            var run = 0 // 이 섹터 뒤로 이어 붙여 한 번에 읽을 섹터 수
            if (!mini) {
                // 체인이 파일에서 연달아 놓여 있으면 한 번에 읽는다. 256 MiB 스트림을 512바이트씩
                // 읽으면 시스템 호출이 50만 번이다.
                while ((unit - inSector).toLong() + run.toLong() * unit < want &&
                    cursor.peek() == sector + run + 1
                ) {
                    cursor.next()
                    run++
                }
            }
            val n = minOf(want, (unit - inSector).toLong() + run.toLong() * unit).toInt()
            if (mini) readMini(sector, inSector, b, off, n) else readRegular(sector, inSector, b, off, n)
            val end = inSector + n
            sector += run
            inSector = end - run * unit
            pos += n
            return n
        }
    }

    // ---- 작은 스트림(mini stream) -------------------------------------------------------

    private class MiniState(val container: IntArray, val length: Long, val fat: IntArray) {
        val sectors: Int = ((length + MINI_SECTOR - 1) / MINI_SECTOR).toInt()
    }

    /**
     * 작은 스트림의 그릇(뿌리의 체인)과 miniFAT 섹터 번호표. **처음 쓸 때** 세운다 — 작은 스트림을
     * 읽지 않는 파일에서 망가진 miniFAT 때문에 열기를 실패하지 않게.
     *
     * 그릇의 길이는 `min(뿌리에 적힌 크기, 체인 길이, 항목 수 상한 × 4096)` 이다. 작은 스트림은
     * 4096 바이트 미만이고 항목은 [CfbLimits.maxEntries] 를 넘지 않으므로, 그보다 긴 그릇은 쓸 데가
     * 없다 — 번호표가 파일 크기에 비례해 커지지 않게 여기서 자른다.
     */
    private fun miniState(): MiniState = synchronized(lock) {
        mini?.let { return it }
        val cap = minOf(root.size, limits.maxEntries.toLong() * MINI_CUTOFF)
        val container = walkRegular(root.startSector, (cap + sectorSize - 1) / sectorSize)
        val length = minOf(cap, container.size.toLong() * sectorSize)
        val miniSectors = (length + MINI_SECTOR - 1) / MINI_SECTOR
        val fat = walkRegular(firstMiniFat, (miniSectors + perFatSector - 1) / perFatSector)
        MiniState(container, length, fat).also { mini = it }
    }

    /** 일반 체인을 최대 [max] 섹터까지 걸어 번호를 모은다. */
    private fun walkRegular(start: Int, max: Long): IntArray {
        if (max <= 0) return IntArray(0)
        val out = ArrayList<Int>()
        val cursor = ChainCursor(start, mini = false)
        while (out.size < max) {
            val s = cursor.next()
            if (s < 0) break
            out.add(s)
        }
        return out.toIntArray()
    }

    private fun miniFatEntry(miniSector: Int): Int {
        val state = miniState()
        val k = miniSector / perFatSector
        if (k >= state.fat.size) throw CfbFormatException("miniFAT 가 이 작은 섹터를 덮지 않는다")
        return fatSector(state.fat[k]).i32((miniSector % perFatSector) * 4)
    }

    private fun readMini(miniSector: Int, offset: Int, b: ByteArray, off: Int, len: Int) {
        val state = miniState()
        val at = miniSector.toLong() * MINI_SECTOR + offset
        if (at + len > state.length) throw CfbFormatException("작은 스트림이 그릇 밖을 가리킨다")
        // 작은 섹터 64바이트는 섹터(512·4096) 안에 통째로 든다 — 한 섹터만 읽으면 된다.
        val k = (at / sectorSize).toInt()
        readRegular(state.container[k], (at % sectorSize).toInt(), b, off, len)
    }

    // ---- 바이트 -------------------------------------------------------------------

    /** 섹터 [sector] 의 [offset] 부터 [len] 바이트. 범위를 검사한 뒤에만 읽는다. */
    private fun readRegular(sector: Int, offset: Int, b: ByteArray, off: Int, len: Int) {
        if (!isRegular(sector)) throw CfbFormatException("섹터 번호가 범위를 벗어났다")
        val pos = ((sector.toLong() + 1) shl sectorShift) + offset
        if (pos + len > fileSize) throw CfbFormatException("파일이 중간에서 끝났다")
        readAt(pos, b, off, len)
    }

    private fun readAt(pos: Long, b: ByteArray, off: Int, len: Int) = synchronized(lock) {
        if (closed) throw IOException("닫힌 CFB")
        channel.position(pos)
        val buf = ByteBuffer.wrap(b, off, len)
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) throw CfbFormatException("파일이 중간에서 끝났다")
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            fatCache.clear()
            channel.close()
        }
    }

    companion object {
        private const val HEADER_SIZE = 512
        private const val HEADER_DIFAT = 109
        private const val HEADER_DIFAT_OFFSET = 76
        private const val DIR_ENTRY = 128
        private const val MINI_SHIFT = 6
        private const val MINI_SECTOR = 64
        private const val MINI_CUTOFF = 4096
        private const val FAT_CACHE = 8
        private const val MAX_ARRAY = Int.MAX_VALUE - 16L

        private const val END_OF_CHAIN = 0xFFFFFFFE.toInt()
        private const val NO_STREAM = 0xFFFFFFFF.toInt()

        private const val TYPE_STORAGE = 1
        private const val TYPE_STREAM = 2
        private const val TYPE_ROOT = 5

        private val SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        /** CFB 서명(`D0 CF 11 E0 A1 B1 1A E1`)으로 시작하는가. 판별에만 쓴다. */
        fun isCfb(head: ByteArray): Boolean = head.size >= SIGNATURE.size && SIGNATURE.indices.all { head[it] == SIGNATURE[it] }

        /**
         * 원본을 연다. 원본이 무작위 접근을 주지 않으면(`openChannel() == null`) [CfbFormatException].
         *
         * @throws CfbFormatException 명세에 어긋난다.
         * @throws ParseLimitExceededException 파일·디렉터리가 상한을 넘는다.
         */
        fun open(source: DocumentSource, limits: CfbLimits = CfbLimits.DEFAULT): CfbFile {
            val channel = source.openChannel() ?: throw CfbFormatException("무작위 접근을 주지 않는 원본이다")
            return open(channel, limits)
        }

        /** 채널을 연다. **채널의 주인이 된다** — 실패하면 닫고 던진다. */
        fun open(channel: SeekableByteChannel, limits: CfbLimits = CfbLimits.DEFAULT): CfbFile {
            try {
                return CfbFile(channel, limits)
            } catch (t: Throwable) {
                try {
                    channel.close()
                } catch (e: IOException) {
                    t.addSuppressed(e)
                }
                throw t
            }
        }
    }
}

private fun ByteArray.u16(o: Int): Int = (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.i32(o: Int): Int =
    (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8) or
        ((this[o + 2].toInt() and 0xFF) shl 16) or ((this[o + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.u32(o: Int): Long = i32(o).toLong() and 0xFFFFFFFFL
