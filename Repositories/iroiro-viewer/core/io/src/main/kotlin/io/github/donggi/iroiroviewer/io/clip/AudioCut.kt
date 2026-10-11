package io.github.donggi.iroiroviewer.io.clip

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * 우리가 직접 자르는 소리 형식(WAV·MP3·FLAC)의 한 구간. **다시 인코딩하지 않는다** — 형식이 정한 경계(표본·프레임)에서 바이트를
 * 옮긴다. 플랫폼의 `MediaMuxer` 는 이 셋을 쓰지 못한다(MP4·WebM·Ogg Opus·3GPP 만 쓴다).
 *
 * 순수 JVM 이다 — 표본을 시험이 지어 초 단위로 돈다.
 */
internal interface AudioCut {
    /** 실제로 담는 구간(마이크로초). 경계에 맞춰져 고른 구간보다 조금 넓을 수 있다. */
    val startUs: Long
    val endUs: Long

    /** 쓸 바이트(진행률의 분모). */
    val outputBytes: Long

    /** 결과 확장자(점 없이). */
    val extension: String

    /** [out] 에 쓴다. 쓴 바이트를 [onBytes] 로 알린다. 취소를 본다. */
    suspend fun writeTo(out: OutputStream, onBytes: (Long) -> Unit)
}

/** 자르기 계획. */
internal sealed interface CutPlan {
    class Ok(val cut: AudioCut) : CutPlan

    /** 이 파일은 이 형식이 아니거나, 이 형식이지만 다루지 않는 갈래다(RF64·ADPCM WAV, 자유 비트율 MP3 등). */
    data object Unsupported : CutPlan

    /** 고른 구간에 담을 것이 없다(미디어의 끝 너머). */
    data object Empty : CutPlan

    /**
     * 고른 구간이 너무 길다 — 담을 프레임 수가 상한을 넘었다. 형식 탓이 아니므로 [Unsupported] 와 가른다(예전에는 '이 형식은 저장할 수
     * 없습니다' 였다). 더 짧게 고르면 된다.
     */
    data object TooLong : CutPlan
}

/**
 * 자리로 읽는 바이트 — 파일([WindowReader])이든 메모리([ArraySource])든. FLAC 프레임 머리 읽기가 둘 다에서 돈다(파일을 자를 때와,
 * 그릇에서 뽑은 프레임을 다시 쓸 때 — `AudioExtract`).
 */
internal interface ByteSource {
    val length: Long

    /** [pos] 의 바이트(0..255). 밖이면 -1. */
    fun u8(pos: Long): Int

    /** [pos] 부터 [len] 바이트를 [dst] 에. 끝에서 모자라면 읽은 만큼을 돌려준다. */
    fun read(pos: Long, dst: ByteArray, off: Int = 0, len: Int = dst.size): Int
}

/** 메모리의 바이트 [bytes] 의 앞 [size] 바이트. */
internal class ArraySource(private val bytes: ByteArray, private val size: Int = bytes.size) : ByteSource {
    override val length: Long get() = size.toLong()

    override fun u8(pos: Long): Int = if (pos < 0 || pos >= size) -1 else bytes[pos.toInt()].toInt() and 0xFF

    override fun read(pos: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (pos < 0 || pos >= size) return 0
        val n = minOf(len.toLong(), size - pos).toInt()
        System.arraycopy(bytes, pos.toInt(), dst, off, n)
        return n
    }
}

/**
 * 파일을 **창 하나로** 읽는다. 프레임을 훑는 일은 앞으로만 가는 작은 읽기의 연속이라, `RandomAccessFile.read` 를 바이트마다
 * 부르면 시스템 호출이 바이트 수만큼 난다.
 *
 * 길이는 만들 때 한 번 읽는다. **그 뒤에 파일이 줄면**(다른 앱이 잘라 다시 썼다) 줄어든 자리를 파일 밖으로 본다 — 창을 채우지 못하면
 * [u8] 은 -1, [read] 는 읽은 만큼이다. 예전에는 빈 창에서 [u8] 이 지난 창의 바이트를, [read] 가 진행 없이 끝없이 돌았다(FLAC 훑기가
 * 취소도 닿지 않은 채 멈춰 뒤의 파일 작업이 모두 막혔다).
 */
internal class WindowReader(private val raf: RandomAccessFile, windowSize: Int = 64 * 1024) : ByteSource {
    override val length: Long = raf.length()
    private val buf = ByteArray(windowSize)
    private var winStart = -1L
    private var winLen = 0

    /** [pos] 의 바이트(0..255). 파일 밖이면 -1. */
    override fun u8(pos: Long): Int {
        if (pos < 0 || pos >= length) return -1
        if (pos < winStart || pos >= winStart + winLen) {
            fill(pos)
            // 파일이 그사이 줄었다 — 그 자리는 이제 파일 밖이다.
            if (winLen == 0) return -1
        }
        return buf[(pos - winStart).toInt()].toInt() and 0xFF
    }

    /** [pos] 부터 [len] 바이트를 [dst] 에. 파일 끝에서 모자라면 읽은 만큼을 돌려준다. */
    override fun read(pos: Long, dst: ByteArray, off: Int, len: Int): Int {
        var done = 0
        while (done < len) {
            val p = pos + done
            if (p >= length) break
            if (p < winStart || p >= winStart + winLen) {
                fill(p)
                // 파일이 그사이 줄었다 — 읽은 만큼만 돌려준다.
                if (winLen == 0) break
            }
            val inWin = (winStart + winLen - p).toInt()
            val n = minOf(inWin, len - done)
            System.arraycopy(buf, (p - winStart).toInt(), dst, off + done, n)
            done += n
        }
        return done
    }


    private fun fill(pos: Long) {
        raf.seek(pos)
        var n = 0
        while (n < buf.size) {
            val r = raf.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        winStart = pos
        winLen = n
    }
}

/** 늘어나는 Long 배열(상자 없이) — 프레임이 수십만 개여도 가볍게 든다(프레임마다 객체를 들면 수백 MB 다). */
internal class LongArrayList(initial: Int = 1024) {
    private var a = LongArray(initial)
    var size = 0
        private set

    fun add(v: Long) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]
}

internal class IntArrayList(initial: Int = 1024) {
    private var a = IntArray(initial)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]
}

/**
 * 프레임 차례마다의 자리를 **성기게** 기억한다 — MP3 목차(Xing 100칸)는 1% 마다 한 자리만 쓰는데, 프레임마다 Long 을 들면 긴 구간에서
 * 수십 MB 였다(자르기는 그 위에 한 벌을 더 지었다). [capacity] 칸이 차면 하나 걸러 버리고 간격을 두 배로 늘린다 — 몇
 * 프레임이든 그 칸 수만큼만 든다. 칸 사이의 자리는 앞뒤 칸을 잇는 선으로 어림한다(고정 비트율이면 정확하고, 가변이어도 어긋남이 간격
 * 하나의 바이트 — 프레임 수의 [capacity] 분의 2 — 를 넘지 않아 목차 한 칸(1%)보다 훨씬 작다). 프레임이 [capacity] 개 이하면 그대로다.
 */
internal class SparseOffsets(private val capacity: Int = 4096) {
    init {
        require(capacity >= 2 && capacity % 2 == 0)
    }

    /** 칸 i 는 프레임 i·[stride] 의 자리다. */
    private val at = LongArray(capacity)
    private var kept = 0
    private var stride = 1L

    /** 마지막으로 받은 프레임(차례 [count] - 1)의 자리 — 마지막 칸 뒤를 잇는 선의 끝. */
    private var last = 0L

    /** 받은 프레임 수. */
    var count = 0L
        private set

    /** 다음 프레임(차례 [count])의 자리. */
    fun add(offset: Long) {
        if (count % stride == 0L) {
            if (kept == capacity) {
                for (i in 0 until capacity / 2) at[i] = at[2 * i]
                kept = capacity / 2
                stride *= 2
            }
            // 칸 수가 짝수라 간격을 늘린 뒤에도 이 차례(capacity·옛 간격)는 새 간격의 배수다.
            at[kept++] = offset
        }
        last = offset
        count++
    }

    /** 프레임 [frame](0 ≤ frame < [count])의 자리 — 기억한 칸이면 그대로, 아니면 앞뒤 칸을 잇는 선으로. */
    fun offsetOf(frame: Long): Long {
        require(frame in 0 until count)
        val i = (frame / stride).toInt()
        val from = i * stride
        if (frame == from) return at[i]
        // 다음 칸이 없으면 마지막으로 받은 프레임까지 잇는다.
        val to = if (i + 1 < kept) from + stride else count - 1
        val toOffset = if (i + 1 < kept) at[i + 1] else last
        return at[i] + (toOffset - at[i]) * (frame - from) / (to - from)
    }
}

/**
 * `RandomAccessFile` 위의 쓰개 — 64 KiB 씩 모아 쓰고, 다 쓴 뒤 앞의 머리를 고친다([patch]). 길이를 끝에서야 아는 형식(WAV 의 크기,
 * FLAC 의 STREAMINFO, MP3 의 Xing)을 한 번에 흘려 쓴다. 파일은 비어 있고 처음부터 쓴다고 본다.
 */
internal class PatchableWriter(private val raf: RandomAccessFile) {
    private val buf = ByteArray(64 * 1024)
    private var used = 0

    /** 쓴 바이트(아직 버퍼에 있는 것 포함). */
    var position = 0L
        private set

    fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
        if (len >= buf.size) {
            flush()
            raf.write(b, off, len)
        } else {
            if (used + len > buf.size) flush()
            System.arraycopy(b, off, buf, used, len)
            used += len
        }
        position += len
    }

    fun write(byte: Int) {
        if (used == buf.size) flush()
        buf[used++] = byte.toByte()
        position++
    }

    fun flush() {
        if (used > 0) raf.write(buf, 0, used)
        used = 0
    }

    /** 앞에서 쓴 자리 [at] 를 [b] 로 고친다(길이는 그대로). */
    fun patch(at: Long, b: ByteArray) {
        flush()
        val end = raf.filePointer
        raf.seek(at)
        raf.write(b)
        raf.seek(end)
    }
}

/** 파일의 [offset] 부터 [length] 바이트를 [out] 으로 옮긴다. 64 KiB 마다 취소를 본다. */
internal suspend fun copyRange(
    raf: RandomAccessFile,
    offset: Long,
    length: Long,
    out: OutputStream,
    buf: ByteArray,
    onBytes: (Long) -> Unit,
) {
    raf.seek(offset)
    var left = length
    while (left > 0) {
        currentCoroutineContext().ensureActive()
        val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
        if (n < 0) throw java.io.EOFException("파일이 계획보다 짧다")
        out.write(buf, 0, n)
        onBytes(n.toLong())
        left -= n
    }
}

/** [pos] 의 글자 [len] 개(ISO-8859-1). 모자라면 빈 문자열. */
internal fun ByteSource.ascii(pos: Long, len: Int): String {
    val b = ByteArray(len)
    return if (read(pos, b) == len) String(b, Charsets.ISO_8859_1) else ""
}

/** 작은 끝 정수. */
internal fun ByteArray.u16le(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.u32le(at: Int): Long =
    (this[at].toLong() and 0xFF) or ((this[at + 1].toLong() and 0xFF) shl 8) or
        ((this[at + 2].toLong() and 0xFF) shl 16) or ((this[at + 3].toLong() and 0xFF) shl 24)

internal fun ByteArray.putU32le(at: Int, v: Long) {
    this[at] = v.toByte()
    this[at + 1] = (v shr 8).toByte()
    this[at + 2] = (v shr 16).toByte()
    this[at + 3] = (v shr 24).toByte()
}

internal fun ByteArray.putU32be(at: Int, v: Long) {
    this[at] = (v shr 24).toByte()
    this[at + 1] = (v shr 16).toByte()
    this[at + 2] = (v shr 8).toByte()
    this[at + 3] = v.toByte()
}

/**
 * ID3v2 태그의 길이(머리 10바이트·꼬리 포함). 없으면 0. 크기는 '동기 안전' 정수(바이트마다 7비트)다. 파일보다 길다고 적혀
 * 있으면 0 — 태그로 보지 않는다.
 */
internal fun id3v2Length(r: ByteSource, at: Long = 0): Long {
    if (r.ascii(at, 3) != "ID3") return 0
    val flags = r.u8(at + 5)
    val s = LongArray(4) { r.u8(at + 6 + it).toLong() }
    if (flags < 0 || s.any { it < 0 || it >= 0x80 }) return 0
    val size = (s[0] shl 21) or (s[1] shl 14) or (s[2] shl 7) or s[3]
    val total = 10 + size + if (flags and 0x10 != 0) 10 else 0
    return if (at + total <= r.length) total else 0
}
