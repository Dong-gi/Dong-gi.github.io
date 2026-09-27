package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.InputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * HWP 의 압축 — **zlib 머리 없는 raw DEFLATE** 다. 명세는 'zlib 을 쓴다' 고만 적지만 pyhwp(`wbits=-15`), hwplib
 * (`new Inflater(true)`), 한컴의 기술 블로그(`zlib.decompress(…, -15)`)가 모두 머리 없이 푼다.
 *
 * `Inflater(true)` 의 javadoc 은 'nowrap 이면 입력 끝에 가짜 바이트 하나를 더 주어야 한다' 고 적는다. 요즘 zlib 에서는
 * 필요 없는 일이 많지만, 입력을 다 먹고도 끝나지 않았을 때 **한 번만** 가짜 바이트를 준다. 그래도 끝나지 않으면
 * 스트림이 잘린 것이다.
 */
internal object HwpInflate {

    private val DUMMY = byteArrayOf(0)

    /**
     * [src] 의 `[off, off+len)` 을 푼다. 결과가 [max] 바이트를 넘으면 `ParseLimitExceededException`(압축 폭탄),
     * 형식이 깨졌거나 잘렸으면 [HwpFormatException].
     *
     * @param wipe 늘리느라 버리는 중간 버퍼를 0 으로 덮는다. **배포용 문서의 평문**을 풀 때 켠다 — 버린 배열이 GC 를
     *   기다리는 동안 평문을 들고 있지 않게.
     */
    fun inflate(
        src: ByteArray,
        off: Int,
        len: Int,
        max: Long,
        limitName: String,
        wipe: Boolean = false,
        checkCancel: () -> Unit = {},
    ): ByteArray {
        require(max in 0..(Int.MAX_VALUE - 16).toLong()) { "상한이 너무 크다" }
        val cap = max.toInt() + 1 // 한 바이트 더 받아 봐야 '넘었다' 를 안다.
        val inf = Inflater(true)
        try {
            inf.setInput(src, off, len)
            var buf = ByteArray(minOf(cap.toLong(), maxOf(4096L, len.toLong() * 4)).toInt())
            var n = 0
            var dummyFed = false
            var sinceCheck = 0
            while (!inf.finished()) {
                if (n == buf.size) {
                    if (buf.size >= cap) throw ParseLimitExceededException(limitName, "${max}바이트를 넘게 풀린다")
                    val grown = buf.copyOf(minOf(cap.toLong(), buf.size * 2L).toInt())
                    if (wipe) buf.fill(0)
                    buf = grown
                }
                val r = try {
                    inf.inflate(buf, n, buf.size - n)
                } catch (e: DataFormatException) {
                    throw HwpFormatException("압축이 깨졌다")
                }
                n += r
                if (n > max) throw ParseLimitExceededException(limitName, "${max}바이트를 넘게 풀린다")
                if (r == 0) {
                    if (inf.finished()) break
                    if (inf.needsDictionary()) throw HwpFormatException("압축이 깨졌다")
                    if (inf.needsInput()) {
                        if (dummyFed) throw HwpFormatException("압축 스트림이 잘렸다")
                        dummyFed = true
                        inf.setInput(DUMMY)
                    }
                }
                sinceCheck += r
                if (sinceCheck >= CHECK_EVERY) {
                    sinceCheck = 0
                    checkCancel()
                }
            }
            if (n == buf.size) return buf
            val out = buf.copyOf(n)
            if (wipe) buf.fill(0)
            return out
        } finally {
            inf.end()
        }
    }

    /**
     * [input] 을 풀면 [max] 바이트를 **넘는가**. 푼 것은 버리고 세기만 한다 — 그림이 자원 하나의 상한을 넘는지 `img` 를 쓰기
     * 전에 볼 때 쓴다([Hwp5Package.fitsResource]). [inflate] 로 한 벌을 풀어 두면 상한만큼(32 MiB)을 들었다 버린다.
     * [max] 를 넘는 첫 덩이에서 멈춘다.
     *
     * 끝을 가리는 규칙은 [inflate] 와 같다(입력이 끝나고도 덜 풀렸으면 가짜 바이트를 한 번) — 둘이 다르게 가리면 여기서
     * 들어간다던 그림을 [inflate] 가 넘는다고 던진다. 깨졌거나 잘렸으면 [HwpFormatException].
     */
    fun exceeds(input: InputStream, max: Long, checkCancel: () -> Unit = {}): Boolean {
        require(max >= 0) { "음수 상한" }
        val inf = Inflater(true)
        try {
            val inBuf = ByteArray(COUNT_CHUNK)
            val outBuf = ByteArray(COUNT_CHUNK)
            var total = 0L
            var inputEnded = false
            var dummyFed = false
            var sinceCheck = 0
            while (!inf.finished()) {
                if (inf.needsInput()) {
                    val read = if (inputEnded) -1 else input.read(inBuf)
                    if (read > 0) {
                        inf.setInput(inBuf, 0, read)
                    } else {
                        inputEnded = true
                        if (dummyFed) throw HwpFormatException("압축 스트림이 잘렸다")
                        dummyFed = true
                        inf.setInput(DUMMY)
                    }
                }
                val r = try {
                    inf.inflate(outBuf)
                } catch (e: DataFormatException) {
                    throw HwpFormatException("압축이 깨졌다")
                }
                total += r
                if (total > max) return true
                if (r == 0 && inf.needsDictionary()) throw HwpFormatException("압축이 깨졌다")
                sinceCheck += r
                if (sinceCheck >= CHECK_EVERY) {
                    sinceCheck = 0
                    checkCancel()
                }
            }
            return false
        } finally {
            inf.end()
        }
    }

    private const val CHECK_EVERY = 1 shl 20

    /** [exceeds] 가 한 번에 읽고 푸는 양. */
    private const val COUNT_CHUNK = 64 * 1024
}

/**
 * **배포용 문서**(배포용으로 저장한 문서)의 본문을 푼다. 본문은 `BodyText` 가 아니라 `ViewText/SectionN` 에 있고,
 * 스트림마다 256바이트짜리 `HWPTAG_DISTRIBUTE_DOC_DATA` 레코드로 시작한다.
 *
 * ## 푸는 법 — 공개된 방식이다
 *
 * 한컴의 5.0 명세(rev 1.3)는 '배포용 문서는 별도 문서에서 설명한다' 고만 적는다. 푸는 법은 류창우가 hwp-foss
 * 모임에 공개했고(2014-01-04, https://groups.google.com/g/hwp-foss/c/d2KL2ypR89Q), pyhwp(`distdoc.py`)·hwplib·rhwp·
 * Apache Tika 가 똑같이 구현한다. **열쇠는 파일 안에 있다** — 암호를 묻지 않는다.
 *
 * 1. 256바이트의 앞 4바이트가 씨앗이다. MSVC 의 `rand()`(`seed*214013+2531011`, 위 15비트)로 **4바이트 뒤를 XOR 로
 *    되돌린다** — 글쇠 한 바이트를 `(rand() & 0xF) + 1` 바이트 동안 쓰고 새로 뽑는다.
 * 2. `4 + (씨앗 & 0xF)` 자리부터 80바이트가 SHA-1 16진 문자열(UTF-16LE)이다. **그 앞 16바이트가 AES-128 열쇠**다.
 * 3. 레코드 뒤의 나머지를 AES-128-ECB 로 풀고, `FileHeader` 가 압축이라 하면 raw DEFLATE 로 푼다. 결과는 보통 구역과
 *    같은 레코드 흐름이다.
 *
 * 실물 셋(한컴 명세 둘, hwplib 표본 하나)이 이 방식으로 풀려 레코드 흐름이 스트림 끝에서 정확히 끝난다(13단계).
 *
 * ## 제한 표시 — 읽지만 따르지 않는다
 *
 * 한글은 배포용 문서에 '복사 막기'·'인쇄 막기' 를 걸 수 있다(HWPX 는 `ha:docdistribute nocopy noprint`). HWP 5.0 의
 * 256바이트 안에서 그것이 어디 있는지는 **공개된 자료에서 찾지 못했다.** 실물에서 SHA-1 문자열 바로 뒤의 16비트가
 * 한컴 명세 둘은 `0x8003`, hwplib 표본은 `0x8001` 이었다 — 비트 0·1 이 그 둘일 가능성이 높지만 어느 쪽이 무엇인지
 * 가릴 근거가 없다. 그래서 값은 [Decoded.flags] 로 **읽어 두기만** 하고 화면을 바꾸지 않는다(틀리게 짐작해 글자
 * 선택을 막는 것보다, 확인된 뒤에 켜는 편이 낫다). 확인하려면 한컴의 '배포용 문서 형식' 문서(rev 1.2)가 필요하다.
 *
 * ## 평문
 *
 * 풀어 낸 본문은 메모리에만 있다. 씨앗을 푼 256바이트·열쇠·풀기 전의 중간 평문은 쓰고 나서 0 으로 덮는다.
 * `SecretKeySpec` 이 안에 드는 열쇠 사본은 덮을 수 없다(CLAUDE.md '완전히 지우지는 못한다').
 */
internal object Hwp5Distribution {

    /** `HWPTAG_DISTRIBUTE_DOC_DATA` 의 알맹이 길이. */
    const val DATA_SIZE = 256

    /** 레코드 머리(4) + 알맹이(256). */
    const val PREFIX = 4 + DATA_SIZE

    /**
     * @param bytes 구역의 레코드 흐름(평문).
     * @param flags SHA-1 문자열 뒤의 16비트 — 위 '제한 표시'. 뜻을 확인하지 못했다.
     */
    class Decoded(val bytes: ByteArray, val flags: Int)

    fun decode(stream: ByteArray, compressed: Boolean, max: Long, checkCancel: () -> Unit = {}): Decoded {
        if (stream.size < PREFIX) throw HwpFormatException("배포용 자료가 짧다")
        val h = stream.i32(0)
        // 늘인 크기 모양(0xFFF)이 아닌 256 이어야 한다 — pyhwp 가 같은 것을 요구한다.
        if (h and 0x3FF != HwpTag.DISTRIBUTE_DOC_DATA || (h ushr 20) != DATA_SIZE) {
            throw HwpFormatException("배포용 자료가 아니다")
        }
        val data = stream.copyOfRange(4, PREFIX)
        val key = ByteArray(16)
        var plain: ByteArray? = null
        var result: ByteArray? = null
        try {
            descramble(data)
            val off = keyOffset(data)
            System.arraycopy(data, off, key, 0, key.size)
            val flags = data.u16(off + 80)
            // 명세도 구현들도 나머지 길이가 16의 배수라 보고(실물 셋이 그랬다), 모자란 꼬리는 풀 수 없다.
            val tail = (stream.size - PREFIX) / 16 * 16
            val decrypted = aesEcb(key, stream, PREFIX, tail)
            plain = decrypted
            val out = if (compressed) {
                HwpInflate.inflate(decrypted, 0, decrypted.size, max, "maxSectionBytes", wipe = true, checkCancel = checkCancel)
            } else {
                if (decrypted.size > max) throw ParseLimitExceededException("maxSectionBytes", "${decrypted.size}바이트")
                trimToRecords(decrypted)
            }
            result = out
            return Decoded(out, flags)
        } finally {
            data.fill(0)
            key.fill(0)
            // 풀기 전의 중간 평문은 덮는다 — 돌려주는 바로 그 배열(압축하지 않은 문서)만 빼고. 실패하면 그것도 덮는다.
            if (plain !== result) plain?.fill(0)
        }
    }

    /** 씨앗을 뺀 252바이트를 XOR 로 되돌린다. 같은 연산을 한 번 더 하면 원래대로다(시험의 암호기가 그것을 쓴다). */
    internal fun descramble(data: ByteArray) {
        require(data.size == DATA_SIZE)
        var seed = data.i32(0)
        fun rand(): Int {
            seed = seed * 214013 + 2531011
            return (seed ushr 16) and 0x7FFF
        }
        var n = 0
        var k = 0
        for (i in 0 until DATA_SIZE) {
            if (n == 0) {
                k = rand() and 0xFF
                n = (rand() and 0xF) + 1
            }
            if (i >= 4) data[i] = (data[i].toInt() xor k).toByte()
            n--
        }
    }

    /** 열쇠 문자열의 자리. 씨앗의 첫 바이트 아래 4비트로 정해진다. */
    internal fun keyOffset(data: ByteArray): Int = 4 + (data[0].toInt() and 0xF)

    private fun aesEcb(key: ByteArray, src: ByteArray, off: Int, len: Int): ByteArray {
        if (len == 0) return ByteArray(0)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(src, off, len)
    }

    /**
     * 압축하지 않은 배포용 문서는 풀린 평문 끝에 AES 블록을 채운 부스러기가 남을 수 있다(hwplib 은 PKCS5, rhwp 는 0 으로
     * 채운다 — **실물로 확인하지 못했다**). 온전한 레코드가 끝나는 자리까지만 쓴다. 남는 것이 한 블록(16바이트)을
     * 넘으면 채움이 아니라 깨진 것이므로 그대로 둔다 — 레코드 커서가 깨진 파일로 알린다.
     */
    private fun trimToRecords(bytes: ByteArray): ByteArray {
        var pos = 0
        while (pos < bytes.size) {
            if (bytes.size - pos < 4) break
            val h = bytes.i32(pos)
            // 태그 0x000–0x00F 는 레코드에 쓰지 않는다(명세 §4.1) — 0 으로 채운 부스러기가 크기 0 의 레코드처럼 읽히지 않게.
            if (h and 0x3FF < HwpTag.BEGIN) break
            var p = pos + 4
            var s = (h ushr 20).toLong()
            if (s == 0xFFFL) {
                if (bytes.size - p < 4) break
                s = bytes.u32(p)
                p += 4
            }
            if (s > (bytes.size - p).toLong()) break
            pos = p + s.toInt()
        }
        if (pos == bytes.size || bytes.size - pos > 16) return bytes
        val out = bytes.copyOf(pos)
        bytes.fill(0)
        return out
    }
}
