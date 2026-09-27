package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentSource

/**
 * 시험용 CFB 를 **메모리에서** 짠다(MS-CFB v3·v4). 이 모듈과 HWP 5.0(13단계)의 시험이 함께 쓴다.
 *
 * 실세계 문서를 커밋하지 않으므로(CLAUDE.md '표본') 시험이 보는 구조는 대부분 여기서 만든다.
 * **나쁜 모양**(순환하는 체인, 범위 밖 번호, 망가진 이름)은 [Built] 가 알려 주는 자리를 시험이
 * 직접 고쳐 만든다 — 그래서 이 짜개는 배치를 단순하게 한다: 모든 체인이 **섹터를 연달아** 쓴다.
 *
 * 배치: `[FAT][DIFAT][miniFAT][디렉터리][작은 스트림 그릇][일반 스트림…]`. 4096 바이트 미만의
 * 스트림은 작은 스트림 그릇에, 나머지는 일반 섹터에 들어간다(명세의 경계).
 * 형제 트리는 명세의 이름 순서로 균형 잡힌 이진 트리를 만들고 **전부 검은색**으로 칠한다 —
 * 읽는 쪽이 색을 보지 않으므로 충분하다.
 *
 * 이 짜개가 만든 파일은 `olefile`(독립 구현)로 열어 확인했다(12단계, 표본 생성 스크립트).
 */
class TinyCfb(private val version: Int = 3) {

    init {
        require(version == 3 || version == 4) { "판은 3 또는 4" }
    }

    private class Node(val name: String, val data: ByteArray?) {
        val kids = ArrayList<Node>()
        val isStorage: Boolean get() = data == null
        var id = -1
        var start = END_OF_CHAIN
    }

    private val root = Node("Root Entry", null)

    /** FAT 섹터를 필요한 것보다 이만큼 더 둔다 — 작은 파일에서도 DIFAT 체인을 만들 수 있다. */
    var extraFatSectors: Int = 0

    /** 저장소 하나(경로는 `/` 로 가른다). 중간 저장소는 저절로 생긴다. */
    fun storage(path: String): TinyCfb {
        storageAt(split(path))
        return this
    }

    /** 스트림 하나. */
    fun stream(path: String, data: ByteArray): TinyCfb {
        val parts = split(path)
        storageAt(parts.dropLast(1)).kids.add(Node(parts.last(), data))
        return this
    }

    private fun split(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun storageAt(parts: List<String>): Node {
        var cur = root
        for (p in parts) {
            cur = cur.kids.firstOrNull { it.isStorage && it.name == p } ?: Node(p, null).also { cur.kids.add(it) }
        }
        return cur
    }

    fun source(name: String = "tiny.cfb"): DocumentSource = ByteArrayDocumentSource(build().bytes, name)

    /**
     * 짠 결과와, 시험이 바이트를 고칠 자리. 고친 뒤에는 [bytes] 를 그대로 쓴다.
     */
    class Built internal constructor(
        val bytes: ByteArray,
        val sectorSize: Int,
        private val ids: Map<String, Int>,
        private val starts: Map<String, Int>,
        val fatSectors: List<Int>,
        val difatSectors: List<Int>,
        val dirSectors: List<Int>,
        val miniFatSectors: List<Int>,
    ) {
        /** 섹터 [id] 의 파일 안 자리. 머리가 섹터 하나를 차지한다. */
        fun sectorOffset(id: Int): Int = (id + 1) * sectorSize

        /** 경로의 디렉터리 번호. 뿌리는 `""`. */
        fun entryId(path: String): Int = ids.getValue(path)

        /** 디렉터리 항목 [id] 의 파일 안 자리(128바이트). */
        fun entryOffset(id: Int): Int {
            val per = sectorSize / 128
            return sectorOffset(dirSectors[id / per]) + (id % per) * 128
        }

        /** 스트림의 첫 섹터(작은 스트림이면 작은 섹터 번호). */
        fun startSector(path: String): Int = starts.getValue(path)

        /** 일반 섹터 [sector] 의 FAT 칸 자리. */
        fun fatEntryOffset(sector: Int): Int {
            val per = sectorSize / 4
            return sectorOffset(fatSectors[sector / per]) + (sector % per) * 4
        }

        /** 작은 섹터 [miniSector] 의 miniFAT 칸 자리. */
        fun miniFatEntryOffset(miniSector: Int): Int {
            val per = sectorSize / 4
            return sectorOffset(miniFatSectors[miniSector / per]) + (miniSector % per) * 4
        }

        fun putShort(offset: Int, v: Int) {
            bytes[offset] = v.toByte()
            bytes[offset + 1] = (v shr 8).toByte()
        }

        fun putInt(offset: Int, v: Int) {
            for (i in 0 until 4) bytes[offset + i] = (v shr (8 * i)).toByte()
        }

        fun putLong(offset: Int, v: Long) {
            for (i in 0 until 8) bytes[offset + i] = (v shr (8 * i)).toByte()
        }

        fun source(name: String = "tiny.cfb"): DocumentSource = ByteArrayDocumentSource(bytes, name)
    }

    fun build(): Built {
        val sectorSize = if (version == 3) 512 else 4096
        val perFat = sectorSize / 4
        val perDir = sectorSize / 128

        // 1. 디렉터리 번호: 뿌리 0, 그 뒤로 너비 우선.
        val order = ArrayList<Node>()
        val paths = HashMap<Node, String>()
        root.id = 0
        order.add(root)
        paths[root] = ""
        var i = 0
        while (i < order.size) {
            val n = order[i++]
            for (k in n.kids.sortedWith(CFB_ORDER)) {
                k.id = order.size
                order.add(k)
                paths[k] = if (paths[n]!!.isEmpty()) k.name else paths[n] + "/" + k.name
            }
        }

        // 2. 작은 스트림과 일반 스트림.
        val miniStreams = order.filter { !it.isStorage && it.data!!.isNotEmpty() && it.data.size < 4096 }
        val bigStreams = order.filter { !it.isStorage && it.data!!.size >= 4096 }
        var miniCount = 0
        for (s in miniStreams) {
            s.start = miniCount
            miniCount += (s.data!!.size + 63) / 64
        }
        val miniLen = miniCount * 64
        val nMiniFat = (miniCount + perFat - 1) / perFat
        val nDir = (order.size + perDir - 1) / perDir
        val nContainer = (miniLen + sectorSize - 1) / sectorSize
        val nBig = bigStreams.sumOf { (it.data!!.size + sectorSize - 1) / sectorSize }
        val content = nMiniFat + nDir + nContainer + nBig
        var nFat = 1
        var nDifat = 0
        while (true) {
            val fat = (content + nFat + nDifat + perFat - 1) / perFat + extraFatSectors
            val difat = if (fat > 109) (fat - 109 + perFat - 2) / (perFat - 1) else 0
            if (fat == nFat && difat == nDifat) break
            nFat = fat
            nDifat = difat
        }

        // 3. 섹터 번호를 차례로 준다.
        var next = 0
        fun run(n: Int): List<Int> = List(n) { next + it }.also { next += n }
        val fatSectors = run(nFat)
        val difatSectors = run(nDifat)
        val miniFatSectors = run(nMiniFat)
        val dirSectors = run(nDir)
        val containerSectors = run(nContainer)
        val bigRuns = bigStreams.associateWith { run((it.data!!.size + sectorSize - 1) / sectorSize) }
        for ((s, r) in bigRuns) s.start = r.first()
        val total = next

        val out = ByteArray((1 + total) * sectorSize)
        fun putInt(o: Int, v: Int) {
            for (b in 0 until 4) out[o + b] = (v shr (8 * b)).toByte()
        }
        fun putShort(o: Int, v: Int) {
            out[o] = v.toByte()
            out[o + 1] = (v shr 8).toByte()
        }
        fun off(sector: Int) = (sector + 1) * sectorSize

        // 4. FAT.
        val fat = IntArray(nFat * perFat) { FREE_SECT }
        for (s in fatSectors) fat[s] = FAT_SECT
        for (s in difatSectors) fat[s] = DIF_SECT
        fun chain(r: List<Int>) {
            for ((k, s) in r.withIndex()) fat[s] = if (k == r.size - 1) END_OF_CHAIN else r[k + 1]
        }
        chain(miniFatSectors)
        chain(dirSectors)
        chain(containerSectors)
        for (r in bigRuns.values) chain(r)
        for ((k, v) in fat.withIndex()) putInt(off(fatSectors[k / perFat]) + (k % perFat) * 4, v)

        // 5. miniFAT.
        if (nMiniFat > 0) {
            val mf = IntArray(nMiniFat * perFat) { FREE_SECT }
            for (s in miniStreams) {
                val n = (s.data!!.size + 63) / 64
                for (k in 0 until n) mf[s.start + k] = if (k == n - 1) END_OF_CHAIN else s.start + k + 1
            }
            for ((k, v) in mf.withIndex()) putInt(off(miniFatSectors[k / perFat]) + (k % perFat) * 4, v)
        }

        // 6. 머리와 DIFAT.
        byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
            .copyInto(out, 0)
        putShort(24, 0x3E)
        putShort(26, version)
        putShort(28, 0xFFFE)
        putShort(30, if (version == 3) 9 else 12)
        putShort(32, 6)
        putInt(40, if (version == 3) 0 else nDir)
        putInt(44, nFat)
        putInt(48, dirSectors.first())
        putInt(56, 4096)
        putInt(60, if (nMiniFat > 0) miniFatSectors.first() else END_OF_CHAIN)
        putInt(64, nMiniFat)
        putInt(68, if (nDifat > 0) difatSectors.first() else END_OF_CHAIN)
        putInt(72, nDifat)
        for (k in 0 until 109) putInt(76 + k * 4, if (k < nFat) fatSectors[k] else FREE_SECT)
        var fatIndex = 109
        for ((d, s) in difatSectors.withIndex()) {
            for (k in 0 until perFat - 1) {
                putInt(off(s) + k * 4, if (fatIndex < nFat) fatSectors[fatIndex] else FREE_SECT)
                fatIndex++
            }
            putInt(off(s) + (perFat - 1) * 4, if (d == difatSectors.size - 1) END_OF_CHAIN else difatSectors[d + 1])
        }

        // 7. 디렉터리.
        fun treeOf(sorted: List<Node>, left: IntArray, right: IntArray): Int {
            if (sorted.isEmpty()) return NO_STREAM
            val mid = sorted.size / 2
            val n = sorted[mid]
            left[n.id] = treeOf(sorted.subList(0, mid), left, right)
            right[n.id] = treeOf(sorted.subList(mid + 1, sorted.size), left, right)
            return n.id
        }
        val left = IntArray(order.size) { NO_STREAM }
        val right = IntArray(order.size) { NO_STREAM }
        val child = IntArray(order.size) { NO_STREAM }
        for (n in order) if (n.isStorage) child[n.id] = treeOf(n.kids.sortedWith(CFB_ORDER), left, right)
        for (slot in 0 until nDir * perDir) {
            val o = off(dirSectors[slot / perDir]) + (slot % perDir) * 128
            if (slot >= order.size) {
                putInt(o + 68, NO_STREAM)
                putInt(o + 72, NO_STREAM)
                putInt(o + 76, NO_STREAM)
                continue
            }
            val n = order[slot]
            require(n.name.length <= 31) { "이름은 31자까지" }
            for ((k, c) in n.name.withIndex()) putShort(o + k * 2, c.code)
            putShort(o + 64, (n.name.length + 1) * 2)
            out[o + 66] = when {
                slot == 0 -> 5
                n.isStorage -> 1
                else -> 2
            }.toByte()
            out[o + 67] = 1
            putInt(o + 68, left[slot])
            putInt(o + 72, right[slot])
            putInt(o + 76, child[slot])
            val (start, size) = when {
                slot == 0 -> (if (nContainer > 0) containerSectors.first() else END_OF_CHAIN) to miniLen
                n.isStorage -> 0 to 0
                n.data!!.isEmpty() -> END_OF_CHAIN to 0
                else -> n.start to n.data.size
            }
            putInt(o + 116, start)
            putInt(o + 120, size)
        }

        // 8. 내용.
        for (s in miniStreams) {
            val at = s.start * 64
            val data = s.data!!
            // 작은 섹터를 하나씩 그릇 섹터에 옮긴다(작은 섹터 64바이트는 그릇 섹터 경계를 넘지 않는다).
            var copied = 0
            while (copied < data.size) {
                val pos = at + copied
                val n = minOf(64, data.size - copied)
                data.copyInto(out, off(containerSectors[pos / sectorSize]) + pos % sectorSize, copied, copied + n)
                copied += n
            }
        }
        for ((s, r) in bigRuns) s.data!!.copyInto(out, off(r.first()))

        val ids = order.associate { paths[it]!! to it.id }
        val starts = order.filter { !it.isStorage }.associate { paths[it]!! to it.start }
        return Built(out, sectorSize, ids, starts, fatSectors, difatSectors, dirSectors, miniFatSectors)
    }

    companion object {
        const val END_OF_CHAIN = 0xFFFFFFFE.toInt()
        const val FREE_SECT = 0xFFFFFFFF.toInt()
        const val FAT_SECT = 0xFFFFFFFD.toInt()
        const val DIF_SECT = 0xFFFFFFFC.toInt()
        const val NO_STREAM = 0xFFFFFFFF.toInt()

        /** 머리의 칸 자리. */
        const val H_MAJOR = 26
        const val H_SECTOR_SHIFT = 30
        const val H_MINI_SHIFT = 32
        const val H_NUM_FAT = 44
        const val H_FIRST_DIR = 48
        const val H_CUTOFF = 56
        const val H_FIRST_DIFAT = 68
        const val H_NUM_DIFAT = 72

        /** 디렉터리 항목 안의 칸 자리. */
        const val E_NAME_LEN = 64
        const val E_TYPE = 66
        const val E_LEFT = 68
        const val E_RIGHT = 72
        const val E_CHILD = 76
        const val E_START = 116
        const val E_SIZE = 120

        /** 명세 2.6.4 의 이름 순서 — 짧은 것 먼저, 같으면 대문자로 바꿔 코드 단위 순. */
        private val CFB_ORDER = Comparator<Node> { a, b ->
            if (a.name.length != b.name.length) return@Comparator a.name.length - b.name.length
            for (k in a.name.indices) {
                val d = a.name[k].uppercaseChar() - b.name[k].uppercaseChar()
                if (d != 0) return@Comparator d
            }
            0
        }
    }
}
