package io.github.donggi.iroiroviewer.format.archive

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * gzip 멤버 머리에서 우리가 쓰는 것(RFC 1952 2.3).
 *
 * @property nameBytes FNAME(NUL 앞까지)의 원본 바이트. 없거나 [GzipHeaders.MAX_NAME_BYTES] 를 넘었으면 null.
 * @property mtimeSeconds MTIME(유닉스 초). 0 은 '모름' 이다(명세가 그렇게 적는다).
 */
internal class GzipHeader(val nameBytes: ByteArray?, val mtimeSeconds: Long)

/**
 * gzip 멤버의 머리를 읽는 순수 함수. [GzipInputStream] 과 목록(`SingleStreamReader`)이 같은 것을 쓴다.
 *
 * ## 가변 칸의 상한
 *
 * FNAME·FCOMMENT 는 **NUL 이 나올 때까지**다 — 길이 칸이 없다. commons-compress 의 `GzipCompressorInputStream` 은 그것을
 * `ByteArrayOutputStream` 에 끝까지 모은다(1.28.0 소스의 `readToNull`). NUL 없는 수백 MB 를 적은 파일 하나가 힙을 다 쓴다.
 * 여기서는 이름을 [MAX_NAME_BYTES] 까지만 들고 나머지는 **읽어서 버린다**(메모리는 늘지 않고 시간은 파일 크기에 비례한다).
 * 주석은 들지 않는다. FEXTRA 는 길이 칸이 2바이트라 65,535 를 넘지 못한다 — 역시 버린다.
 *
 * **목록은 버리며 읽지도 않는다**([readAfterId] 의 `fieldLimit`). 해제기는 자료에 닿으려면 칸의 끝까지 가야 하지만 목록이 머리에서
 * 쓰는 것은 이름과 시각뿐이다. 판별이 입력 상한에서 멈춘 파일(끝나지 않는 이름·주석 — `Archives`)을 목록이 마감 없이 다시 끝까지
 * 읽으면 그 상한을 둔 뜻이 사라진다.
 */
internal object GzipHeaders {

    const val ID1 = 0x1F
    const val ID2 = 0x8B
    private const val CM_DEFLATE = 8

    private const val FHCRC = 0x02
    private const val FEXTRA = 0x04
    private const val FNAME = 0x08
    private const val FCOMMENT = 0x10
    private const val FRESERVED = 0xE0

    /** 들고 있을 FNAME 의 상한. 리눅스의 경로 상한(PATH_MAX)과 같다 — 파일 이름 하나가 이보다 길 일은 없다. */
    const val MAX_NAME_BYTES = 4096

    /**
     * ID 두 바이트 **뒤부터** 머리를 읽는다(ID 는 부르는 쪽이 이미 보았다).
     *
     * @param next 다음 바이트(0..255). 끝이면 -1.
     * @param fieldLimit FNAME·FCOMMENT 하나를 읽을 상한. 넘으면 **거기서 멈추고** 이름 없는 머리를 준다(그 뒤의 칸은 읽지 않는다 —
     *   목록이 쓴다. 해제기는 자료에 닿아야 하므로 끝까지 읽는다).
     * @throws IOException 머리가 아니거나 중간에 끝났다.
     */
    fun readAfterId(next: () -> Int, fieldLimit: Long = Long.MAX_VALUE): GzipHeader {
        fun need(): Int {
            val b = next()
            if (b < 0) throw EOFException("gzip 머리가 중간에 끝났다")
            return b
        }
        if (need() != CM_DEFLATE) throw IOException("gzip 의 압축 방식이 deflate 가 아니다")
        val flg = need()
        if (flg and FRESERVED != 0) throw IOException("gzip 머리의 예약 비트가 섰다")
        var mtime = 0L
        for (i in 0 until 4) mtime = mtime or (need().toLong() shl (8 * i))
        need() // XFL
        need() // OS
        if (flg and FEXTRA != 0) {
            val xlen = need() or (need() shl 8)
            for (i in 0 until xlen) need()
        }
        val name = try {
            val fname = if (flg and FNAME != 0) zeroTerminated(MAX_NAME_BYTES, fieldLimit, ::need) else null
            if (flg and FCOMMENT != 0) zeroTerminated(0, fieldLimit, ::need)
            fname
        } catch (e: FieldTooLong) {
            // 머리를 끝까지 읽지 못했다 — 읽은 이름도 쓰지 않는다('이름 없음' — 부르는 쪽이 압축 파일 이름에서 짓는다).
            return GzipHeader(null, mtime)
        }
        if (flg and FHCRC != 0) {
            // 머리의 CRC16. 그것을 적는 도구가 드물고(GNU gzip 은 적지 않는다) 머리의 손상은 뒤의 해제가 드러낸다.
            need()
            need()
        }
        return GzipHeader(name, mtime)
    }

    /** [readAfterId] 의 `fieldLimit` 에 닿았다. 바깥으로 나가지 않는다. */
    private class FieldTooLong : IOException("gzip 머리의 칸이 상한보다 길다")

    /**
     * NUL 까지 읽는다. [keep] 바이트까지만 든다 — 넘으면 null(쓸 수 없는 이름)이고 나머지는 버린다. [limit] 바이트를 넘으면 더 읽지
     * 않고 [FieldTooLong] 이다.
     */
    private fun zeroTerminated(keep: Int, limit: Long, need: () -> Int): ByteArray? {
        val out = if (keep > 0) ByteArrayOutputStream(64) else null
        var seen = 0L
        var overflow = false
        while (true) {
            val b = need()
            if (b == 0) break
            seen++
            if (seen > limit) throw FieldTooLong()
            if (seen and 0xFFFF == 0L && Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("gzip 머리를 읽는 중에 중단되었다")
            }
            if (out != null && !overflow) {
                if (out.size() < keep) out.write(b) else overflow = true
            }
        }
        return if (out == null || overflow) null else out.toByteArray()
    }
}

/**
 * gzip(RFC 1952)을 푼다 — **이어 붙인 멤버까지**(pigz·`cat a.gz b.gz`·bgzip 이 만든다). 첫 멤버에서 멈추면 뒤의 자료가
 * 통째로 사라진다.
 *
 * ## 머리는 우리가 읽고 해제기만 플랫폼의 것을 쓴다
 *
 * tar 가 commons-compress 의 tar 읽기를 버리고 머리를 스스로 읽기로 한 것과 같은 판단이다(`TarScanner` 의 주석).
 * 해제(deflate)는 `java.util.zip.Inflater`(zlib) 가 하고, 멤버의 머리·트레일러·멤버 사이의 경계는 여기서 읽는다.
 * 그래서 FNAME 의 상한([GzipHeaders])을 우리가 정하고, 이름의 **원본 바이트**를 판정에 넘길 수 있다.
 *
 * ## 지키는 것
 *
 * - 멤버마다 트레일러의 CRC32 와 ISIZE(길이를 2^32 로 나눈 나머지)를 실제로 푼 것과 견준다 — 다르면 깨진 파일이다.
 * - 멤버 뒤가 **0 으로만 채워져 있으면** 끝으로 본다(테이프·블록 장치가 남기는 채움 — 파이썬의 gzip 과 같은 판단).
 *   0 이 아닌 다른 것이 붙어 있으면 깨진 파일이다. GNU gzip 은 경고만 하고 넘어가지만, 우리는 그 자리를 '푼 것이 전부' 라고
 *   말할 근거가 없다.
 * - 인터럽트를 본다 — 읽을 때마다, 그리고 긴 이름을 버리는 동안에도.
 *
 * 만들 때 **첫 멤버의 머리를 읽는다**([firstHeader]). gzip 이 아니면 여기서 던진다. 해제는 첫 `read` 에서 시작한다.
 */
internal class GzipInputStream(private val input: InputStream) : InputStream() {

    private val buf = ByteArray(BUFFER)
    private var pos = 0
    private var lim = 0

    private var inflater: Inflater? = Inflater(true)
    private val crc = CRC32()

    /** 지금 멤버의 자료를 풀고 있다. 거짓이면 다음 머리를 읽을 차례다. */
    private var inMember = false
    private var done = false
    private val one = ByteArray(1)

    /**
     * 첫 멤버의 머리. 판별(`TarArchiveReader.startsLikeTar`)이 본다 — 첫 칸이 0 일 때 안의 이름(FNAME)이 tar 인지. 목록
     * (`SingleStreamReader`)은 이것을 쓰지 않고 `GzipHeaders.firstOf` 로 머리만 따로 읽는다 — 이름과 시각을 보려고 해제기
     * (`Inflater` 의 네이티브 자원)를 만들 까닭이 없다.
     */
    val firstHeader: GzipHeader

    init {
        try {
            if (readByte() != GzipHeaders.ID1 || readByte() != GzipHeaders.ID2) throw IOException("gzip 이 아니다")
            firstHeader = GzipHeaders.readAfterId(::readByte)
            inMember = true
        } catch (t: Throwable) {
            // 생성자가 던진 객체는 부른 쪽이 닫을 수 없다. 해제기의 네이티브 자원을 여기서 놓는다(입력은 부른 쪽이 닫는다).
            release()
            throw t
        }
    }

    override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("gzip 을 푸는 중에 중단되었다")
            if (done) return -1
            if (!inMember) {
                if (!nextMember()) {
                    done = true
                    release()
                    return -1
                }
                inMember = true
            }
            val inf = inflater ?: throw IOException("닫힌 gzip 스트림이다")
            if (inf.finished()) {
                endMember(inf)
                inMember = false
                continue
            }
            if (inf.needsInput()) {
                if (pos >= lim && !fill()) throw EOFException("gzip 이 중간에 끝났다")
                inf.setInput(buf, pos, lim - pos)
                pos = lim
            }
            val n = try {
                inf.inflate(b, off, len)
            } catch (e: DataFormatException) {
                throw IOException("gzip 자료가 깨졌다", e)
            }
            if (n > 0) {
                crc.update(b, off, n)
                return n
            }
            if (inf.finished()) continue
            // 날것의 deflate 는 사전을 요구하지 않는다. 그 상태로 돌면 끝나지 않는 고리가 된다.
            if (inf.needsDictionary() || !inf.needsInput()) throw IOException("gzip 자료가 깨졌다")
        }
    }

    /**
     * 멤버의 끝 — 트레일러를 읽고 견준다. 해제기가 받은 입력 가운데 쓰지 않은 것(트레일러와 다음 멤버)은 버퍼의 끝에
     * 있다: 마지막에 넘긴 조각이 `[처음, lim)` 이었으므로 쓰지 않은 [Inflater.getRemaining] 바이트는 `lim` 바로 앞이다.
     */
    private fun endMember(inf: Inflater) {
        pos = lim - inf.remaining
        val stored = u32()
        val isize = u32()
        if (stored != crc.value) throw IOException("gzip 검사값(CRC32)이 맞지 않는다")
        if (isize != (inf.bytesWritten and 0xFFFF_FFFFL)) throw IOException("gzip 길이(ISIZE)가 맞지 않는다")
        inf.reset()
        crc.reset()
    }

    /** 다음 멤버의 머리. 끝(또는 0 으로 된 채움)이면 거짓. */
    private fun nextMember(): Boolean {
        val id1 = readByte()
        if (id1 < 0) return false
        if (id1 == 0) {
            skipZeroPadding()
            return false
        }
        if (id1 != GzipHeaders.ID1 || readByte() != GzipHeaders.ID2) throw IOException("gzip 뒤에 알 수 없는 자료가 있다")
        GzipHeaders.readAfterId(::readByte)
        return true
    }

    /** 멤버 뒤의 0 을 끝까지 지나간다. 0 이 아닌 것이 섞여 있으면 깨진 파일이다. */
    private fun skipZeroPadding() {
        while (true) {
            if (pos >= lim) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("gzip 을 푸는 중에 중단되었다")
                if (!fill()) return
            }
            while (pos < lim) {
                if (buf[pos].toInt() != 0) throw IOException("gzip 뒤에 알 수 없는 자료가 있다")
                pos++
            }
        }
    }

    private fun u32(): Long {
        var v = 0L
        for (i in 0 until 4) {
            val b = readByte()
            if (b < 0) throw EOFException("gzip 트레일러가 중간에 끝났다")
            v = v or (b.toLong() shl (8 * i))
        }
        return v
    }

    private fun readByte(): Int {
        if (pos >= lim && !fill()) return -1
        return buf[pos++].toInt() and 0xFF
    }

    private fun fill(): Boolean {
        val n = input.read(buf, 0, buf.size)
        pos = 0
        lim = if (n > 0) n else 0
        return n > 0
    }

    private fun release() {
        inflater?.end()
        inflater = null
    }

    override fun close() {
        done = true
        release()
        input.close()
    }

    private companion object {
        /**
         * 입력을 한 번에 가져오는 양. 8 KiB 로 둔다 — 압축 tar 의 압축비 계산이 해제기가 미리 가져간 입력을 최대 64 KiB 로 본다
         * (`ScannedArchive.RATIO_SLACK`). commons-compress 의 gzip 이 쓰던 크기와 같다.
         */
        const val BUFFER = 8 * 1024
    }
}
