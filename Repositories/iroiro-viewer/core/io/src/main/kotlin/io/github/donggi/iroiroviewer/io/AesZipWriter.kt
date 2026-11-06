package io.github.donggi.iroiroviewer.io

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.util.zip.Deflater

/**
 * WinZip AES-256(AE-2)으로 잠근 ZIP 을 쓴다 — 'ZIP으로 압축' 에서 암호를 넣었을 때의 길([ZipMaker]). PC 의 7-Zip·WinZip 이 연다.
 *
 * ## 플랫폼의 ZIP 쓰개를 쓰지 않는 까닭
 *
 * `java.util.zip.ZipOutputStream` 은 압축 방식이 저장(0)·deflate(8)가 아니면 받지 않는다 — 방식 99 를 적을 수 없다. commons-compress 는
 * 암호 항목을 쓰지 못하고(함정 표) `core:io` 가 볼 수도 없다(의존 표). zip4j 는 새 의존이라 들이지 않았다. 그래서 이 형식에 드는 만큼만 우리가
 * 쓴다 — 로컬 머리, 데이터, 가운데 목록, 끝 레코드, ZIP64. 암호 없는 ZIP 은 예전 그대로 플랫폼의 쓰개가 쓴다.
 *
 * ## 무엇을 적는가
 *
 * | 칸 | 값 |
 * |---|---|
 * | 압축 방식 | 99. 실제 방식(deflate 8·저장 0 — [ZipMaker] 가 파일마다 고른 것)은 추가 필드 `0x9901` 에 |
 * | 추가 필드 `0x9901` | 판 2(AE-2)·`AE`·세기 3(AES-256)·실제 방식. 로컬 머리와 가운데 목록 양쪽에 |
 * | 일반 목적 비트 | 0(암호) + 11(이름이 UTF-8). 데이터 기술자(비트 3)는 쓰지 않는다(아래) |
 * | 푸는 데 필요한 판 | 51(APPNOTE 의 5.1 — AES 암호. 7-Zip 과 같다) |
 * | CRC | **0**(AE-2). 평문의 CRC 를 적지 않는다 — 작은 파일은 CRC 만으로 내용을 맞혀 볼 수 있다(네 자리 비밀번호를 적은 메모면 1만 번). 무결성은 HMAC 이 지킨다 |
 * | 압축 크기 | 솔트 16 + 확인값 2 + 암호문 + 인증값 10 |
 * | 시각 | DOS 시각(기기의 시간대) + 확장 시각 `0x5455`(UTC 초) — 암호 없는 ZIP 만들기와 같다 |
 * | 폴더 | 잠그지 않는다(내용이 없고 이름은 어차피 보인다) |
 *
 * **파일 이름은 잠기지 않는다** — 이 형식의 성질이다(가운데 목록이 평문이다). 화면이 그렇게 말한다.
 *
 * ## 데이터 기술자 대신 머리를 고쳐 쓴다
 *
 * 압축한 크기는 다 쓰기 전에 모른다. 플랫폼의 쓰개는 그래서 deflate 항목 뒤에 데이터 기술자를 붙이는데, 암호 항목에 그것을 쓰면 흐름으로
 * 읽는 쪽(가운데 목록 없이 머리만 보고 데이터의 끝을 찾는 쪽)이 '저장' 안쪽의 끝을 알 수 없다. 우리 출력은 되감을 수 있는 임시본이므로 **머리를
 * 먼저 쓰고, 항목이 끝나면 그 머리의 크기 칸을 고쳐 쓴다**(`FileChannel` 의 위치 지정 쓰기). 사용자의 파일이 아니라 우리 임시본 안의 일이다.
 *
 * ## ZIP64
 *
 * 크기·자리가 [zip64Limit](32비트의 끝)에 닿으면 ZIP64 칸을 쓴다 — 가운데 목록의 추가 필드(넘친 칸만, 명세의 차례대로), ZIP64 끝 레코드와
 * 그 위치표. 로컬 머리의 ZIP64 칸은 **머리를 쓸 때** 정해야 한다(자리를 나중에 늘릴 수 없다) — 훑을 때 잰 크기에 deflate 가 가장 나쁘게 부푼
 * 몫을 넉넉히 얹어 미리 잡는다([needsZip64]). 잡지 않았는데 넘치면(읽는 사이 파일이 4 GiB 를 넘게 자랐다) 깨진 ZIP 을 쓰지 않고
 * [SizeOverflow] 를 던진다 — 부르는 쪽이 전부 버린다. ZIP64 추가 필드는 추가 필드의 맨 앞에 둔다(파이썬 `zipfile` 이 윈도 7 때문에 그렇게
 * 한다고 적었다 — 확인하지는 않았다. 플랫폼의 쓰개도 맨 앞이다).
 *
 * ## 차례
 *
 * [addDirectory] 나 [beginFile] → [write]… → [finishFile] 을 되풀이하고 [finish] 로 끝낸다. 채널은 비어 있고 자리가 0 이어야 하며, 이 쓰개는
 * 채널을 닫지 않는다(부르는 쪽의 임시본이다 — fsync 하고 놓는 것은 그쪽 일). [close] 는 압축기의 네이티브 메모리를 놓는다 — 어느 길로 끝나든
 * 부른다. 던진 뒤의 쓰개는 다시 쓰지 않는다(그 ZIP 은 버린다).
 *
 * @param zip64Limit 이 값부터 ZIP64 칸을 쓴다. 앱은 기본값(32비트의 끝)만 쓴다 — 작게 주는 것은 4 GiB 를 만들지 않고 ZIP64 길을 타 보는
 *   시험의 손잡이다.
 * @param random 솔트를 뽑는다.
 */
class AesZipWriter(
    private val channel: FileChannel,
    private val key: AesZipKey,
    private val zip64Limit: Long = ZIP64_LIMIT,
    private val random: SecureRandom = SecureRandom(),
) : Closeable {

    /** 머리에 ZIP64 칸을 잡지 않은 항목이 32비트를 넘었다. 이 ZIP 은 버려야 한다. */
    class SizeOverflow : IOException("항목이 머리에 잡은 크기를 넘었다")

    init {
        require(zip64Limit in 1..ZIP64_LIMIT) { "zip64Limit 은 1..2^32-1" }
        require(channel.position() == 0L) { "빈 채널의 처음부터 쓴다" }
    }

    private val buf = ByteArray(BUFFER)
    private var bufLen = 0

    /** 채널에 내린 바이트. 지금 자리는 이것 + [bufLen] 이다. */
    private var flushed = 0L

    private val entries = ArrayList<Central>()
    private val crypt = AesCtrHmac()
    private var deflater: Deflater? = null
    private var open: Open? = null
    private var finished = false

    /** 가운데 목록에 적을 한 줄. */
    private class Central(
        val name: ByteArray,
        val version: Int,
        val flags: Int,
        val method: Int,
        val stamp: Stamp,
        /** 잠긴 항목의 실제 압축 방식. 잠기지 않은 항목(폴더)은 -1. */
        val actualMethod: Int,
        val compressedSize: Long,
        val size: Long,
        val offset: Long,
    )

    /** 쓰는 중인 파일 항목. */
    private class Open(
        val name: ByteArray,
        val stamp: Stamp,
        val actualMethod: Int,
        val offset: Long,
        val zip64: Boolean,
    ) {
        /** 받은 평문 바이트. */
        var size = 0L

        /** 쓴 암호문 바이트(솔트·확인값·인증값 빼고). */
        var data = 0L
    }

    /** DOS 시각(날짜 << 16 | 시각)과 확장 시각(UTC 초, 없으면 -1). */
    private class Stamp(val dos: Int, val unix: Long)

    // ---- 항목 -------------------------------------------------------------------

    /** 폴더 항목. 이름은 `/` 로 끝난다. 잠그지 않는다. */
    fun addDirectory(name: String, modified: Long) {
        check(open == null && !finished) { "열린 항목이 있다" }
        require(name.endsWith("/")) { "폴더 이름은 / 로 끝난다" }
        val nameBytes = nameBytes(name)
        val offset = position()
        // 자리가 넘친 폴더는 가운데 목록이 ZIP64 칸을 쓴다 — 두 머리의 판을 같게 둔다.
        val version = if (offset >= zip64Limit) VERSION_ZIP64 else VERSION_FOLDER
        val stamp = stamp(modified)
        writeLocal(nameBytes, version, FLAG_UTF8, METHOD_STORED, stamp, zip64 = false, actualMethod = -1)
        entries += Central(nameBytes, version, FLAG_UTF8, METHOD_STORED, stamp, -1, 0, 0, offset)
    }

    /**
     * 파일 항목을 연다. 내용은 [write] 로 넣고 [finishFile] 로 닫는다.
     *
     * @param compress deflate 로 줄인다(거짓이면 '저장' — 이미 압축된 형식).
     * @param sizeHint 훑을 때 잰 크기. 로컬 머리에 ZIP64 칸을 잡을지 이것으로 정한다(위 머리말).
     */
    fun beginFile(name: String, modified: Long, compress: Boolean, sizeHint: Long) {
        check(open == null && !finished) { "열린 항목이 있다" }
        val nameBytes = nameBytes(name)
        val offset = position()
        val zip64 = needsZip64(sizeHint)
        val stamp = stamp(modified)
        val actual = if (compress) METHOD_DEFLATED else METHOD_STORED
        // 항목마다 새 솔트, 그래서 새 열쇠다 — 같은 열쇠로 계수기를 1 부터 다시 쓰면 두 항목의 평문이 XOR 로 드러난다.
        val salt = ByteArray(AesZipCrypto.SALT_LEN).also { random.nextBytes(it) }
        val derived = key.derive(salt)
        val k = AesZipCrypto.KEY_LEN
        val verifier = derived.copyOfRange(2 * k, 2 * k + AesZipCrypto.VERIFIER_LEN)
        try {
            crypt.start(derived.copyOfRange(0, k), derived.copyOfRange(k, 2 * k))
        } finally {
            derived.fill(0)
        }
        writeLocal(nameBytes, VERSION_AES, FLAG_ENCRYPTED or FLAG_UTF8, AesZipCrypto.METHOD_AES, stamp, zip64, actual)
        putBytes(salt)
        putBytes(verifier)
        if (compress && deflater == null) deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        open = Open(nameBytes, stamp, actual, offset, zip64)
    }

    /** 열린 파일 항목에 평문을 넣는다. [b] 는 건드리지 않는다(deflate 는 돌아오기 전에 입력을 다 먹는다). */
    fun write(b: ByteArray, off: Int, len: Int) {
        val e = open ?: error("열린 항목이 없다")
        if (len <= 0) return
        e.size += len
        if (e.actualMethod == METHOD_DEFLATED) {
            val d = deflater!!
            d.setInput(b, off, len)
            while (!d.needsInput()) deflateStep(d, e)
        } else {
            var i = off
            val end = off + len
            while (i < end) {
                if (bufLen == BUFFER) flush()
                val n = minOf(BUFFER - bufLen, end - i)
                System.arraycopy(b, i, buf, bufLen, n)
                crypt.encrypt(buf, bufLen, n)
                bufLen += n
                e.data += n
                i += n
            }
        }
    }

    /**
     * 열린 파일 항목을 닫는다 — 압축의 끝을 쓰고, 인증값을 붙이고, 로컬 머리의 크기 칸을 고친다.
     *
     * @throws SizeOverflow 머리에 ZIP64 칸을 잡지 않았는데 32비트를 넘었다.
     */
    fun finishFile() {
        val e = open ?: error("열린 항목이 없다")
        if (e.actualMethod == METHOD_DEFLATED) {
            val d = deflater!!
            d.finish()
            while (!d.finished()) deflateStep(d, e)
            d.reset()
        }
        putBytes(crypt.authCode())
        crypt.forget()
        val compressed = e.data + AesZipCrypto.OVERHEAD
        if (!e.zip64 && (compressed >= zip64Limit || e.size >= zip64Limit)) throw SizeOverflow()
        // 로컬 머리의 크기 칸. ZIP64 칸을 잡았으면 32비트 칸은 이미 0xFFFFFFFF 이고 진짜 값은 추가 필드(평문 크기, 압축 크기 차례)에 간다.
        if (e.zip64) {
            patch(e.offset + LOCAL_FIXED + e.name.size + 4, le64(e.size) + le64(compressed))
        } else {
            patch(e.offset + LOCAL_SIZES_AT, le32(compressed) + le32(e.size))
        }
        entries += Central(
            e.name, VERSION_AES, FLAG_ENCRYPTED or FLAG_UTF8, AesZipCrypto.METHOD_AES, e.stamp,
            e.actualMethod, compressed, e.size, e.offset,
        )
        open = null
    }

    /** 가운데 목록과 끝 레코드를 쓰고 버퍼를 채널에 내린다. 채널은 닫지 않는다. */
    fun finish() {
        check(open == null && !finished) { "열린 항목이 있다" }
        val cdStart = position()
        for (c in entries) writeCentral(c)
        val cdSize = position() - cdStart
        val count = entries.size.toLong()
        if (count >= 0xFFFF || cdSize >= zip64Limit || cdStart >= zip64Limit) {
            val at = position()
            // ZIP64 끝 레코드(56바이트 — '확장 데이터' 없이)와 바로 뒤의 위치표. 파이썬 `zipfile` 은 둘이 붙어 있다고 보고 읽는다.
            u32(SIG_EOCD64)
            u64(44)
            u16(VERSION_ZIP64)
            u16(VERSION_ZIP64)
            u32(0)
            u32(0)
            u64(count)
            u64(count)
            u64(cdSize)
            u64(cdStart)
            u32(SIG_LOCATOR)
            u32(0)
            u64(at)
            u32(1)
        }
        val shortCount = if (count >= 0xFFFF) 0xFFFF else count.toInt()
        u32(SIG_EOCD)
        u16(0)
        u16(0)
        u16(shortCount)
        u16(shortCount)
        u32(clamp32(cdSize))
        u32(clamp32(cdStart))
        u16(0)
        flush()
        finished = true
    }

    /** 압축기의 네이티브 메모리를 놓고 남은 열쇠 흐름을 덮는다. 몇 번 불러도 된다. */
    override fun close() {
        deflater?.end()
        deflater = null
        crypt.forget()
    }

    // ---- 머리 -------------------------------------------------------------------

    private fun writeLocal(name: ByteArray, version: Int, flags: Int, method: Int, stamp: Stamp, zip64: Boolean, actualMethod: Int) {
        val extra = (if (zip64) ZIP64_LOCAL_LEN else 0) + (if (stamp.unix >= 0) UT_LEN else 0) + (if (actualMethod >= 0) AES_LEN else 0)
        u32(SIG_LOCAL)
        u16(version)
        u16(flags)
        u16(method)
        u32(stamp.dos.toLong() and 0xFFFFFFFFL)
        // CRC — AE-2 와 빈 폴더 모두 0.
        u32(0)
        // 크기 — 파일은 끝에서 고친다([finishFile]).
        val unknown = if (zip64) 0xFFFFFFFFL else 0L
        u32(unknown)
        u32(unknown)
        u16(name.size)
        u16(extra)
        putBytes(name)
        if (zip64) {
            u16(EXTRA_ZIP64)
            u16(16)
            u64(0)
            u64(0)
        }
        if (stamp.unix >= 0) extendedTime(stamp.unix)
        if (actualMethod >= 0) aesExtra(actualMethod)
    }

    private fun writeCentral(c: Central) {
        val bigSize = c.size >= zip64Limit
        val bigCompressed = c.compressedSize >= zip64Limit
        val bigOffset = c.offset >= zip64Limit
        val z64 = 8 * ((if (bigSize) 1 else 0) + (if (bigCompressed) 1 else 0) + (if (bigOffset) 1 else 0))
        val extra = (if (z64 > 0) 4 + z64 else 0) + (if (c.stamp.unix >= 0) UT_LEN else 0) + (if (c.actualMethod >= 0) AES_LEN else 0)
        val version = if (z64 > 0) maxOf(c.version, VERSION_ZIP64) else c.version
        u32(SIG_CENTRAL)
        // 만든 판 — 위쪽 바이트(호스트)는 0(MS-DOS). 플랫폼의 ZIP 쓰개와 같다(바깥 속성도 0 — 폴더는 이름의 `/` 로 안다).
        u16(version)
        u16(version)
        u16(c.flags)
        u16(c.method)
        u32(c.stamp.dos.toLong() and 0xFFFFFFFFL)
        u32(0)
        u32(if (bigCompressed) 0xFFFFFFFFL else c.compressedSize)
        u32(if (bigSize) 0xFFFFFFFFL else c.size)
        u16(c.name.size)
        u16(extra)
        // 주석 길이·시작 디스크·안쪽 속성·바깥 속성
        u16(0)
        u16(0)
        u16(0)
        u32(0)
        u32(if (bigOffset) 0xFFFFFFFFL else c.offset)
        putBytes(c.name)
        if (z64 > 0) {
            // 넘친 칸만, 명세의 차례대로(평문 크기, 압축 크기, 자리).
            u16(EXTRA_ZIP64)
            u16(z64)
            if (bigSize) u64(c.size)
            if (bigCompressed) u64(c.compressedSize)
            if (bigOffset) u64(c.offset)
        }
        if (c.stamp.unix >= 0) extendedTime(c.stamp.unix)
        if (c.actualMethod >= 0) aesExtra(c.actualMethod)
    }

    /** Info-ZIP 확장 시각(`0x5455`) — 수정 시각 하나(깃발 1). 로컬 머리와 가운데 목록이 같은 모양이다. */
    private fun extendedTime(unix: Long) {
        u16(EXTRA_UT)
        u16(5)
        u8(1)
        u32(unix)
    }

    /** WinZip AES 추가 필드 — 판·`AE`·세기·실제 방식. */
    private fun aesExtra(actualMethod: Int) {
        u16(AesZipCrypto.EXTRA_ID)
        u16(7)
        u16(AesZipCrypto.VENDOR_AE2)
        u8('A'.code)
        u8('E'.code)
        u8(AesZipCrypto.STRENGTH_256)
        u16(actualMethod)
    }

    private fun stamp(modified: Long): Stamp {
        // 시각을 모르면(0) 플랫폼의 쓰개처럼 지금 시각을 DOS 칸에만 적는다 — 확장 시각은 지어내지 않는다.
        if (modified <= 0) return Stamp(dosTime(System.currentTimeMillis()), -1)
        val seconds = modified / 1000
        return Stamp(dosTime(modified), if (seconds <= Int.MAX_VALUE) seconds else -1)
    }

    /**
     * 로컬 머리에 ZIP64 칸을 잡을 것인가. deflate 는 줄지 않는 데이터에서 조금 부푼다 — zlib 의 `deflateBound` 보다 넉넉한 몫(1/8 + 1/64 +
     * 1 KiB)을 얹어 본다. 저장 항목에는 지나친 몫이지만 잡아서 손해 보는 것은 20바이트다.
     */
    private fun needsZip64(sizeHint: Long): Boolean {
        val size = sizeHint.coerceAtLeast(0)
        val worst = size + size / 8 + size / 64 + 1024 + AesZipCrypto.OVERHEAD
        return worst < 0 || size >= zip64Limit || worst >= zip64Limit
    }

    private fun clamp32(v: Long): Long = if (v >= zip64Limit) 0xFFFFFFFFL else v

    private fun nameBytes(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 0xFFFF) { "이름이 너무 길다" }
        return bytes
    }

    // ---- 바이트 -------------------------------------------------------------------

    private fun deflateStep(d: Deflater, e: Open) {
        if (bufLen == BUFFER) flush()
        val n = d.deflate(buf, bufLen, BUFFER - bufLen)
        if (n > 0) {
            crypt.encrypt(buf, bufLen, n)
            bufLen += n
            e.data += n
        }
    }

    private fun position(): Long = flushed + bufLen

    private fun room(n: Int) {
        if (BUFFER - bufLen < n) flush()
    }

    private fun u8(v: Int) {
        room(1)
        buf[bufLen++] = v.toByte()
    }

    private fun u16(v: Int) {
        room(2)
        buf[bufLen++] = v.toByte()
        buf[bufLen++] = (v ushr 8).toByte()
    }

    private fun u32(v: Long) {
        room(4)
        for (i in 0 until 4) buf[bufLen++] = (v ushr (8 * i)).toByte()
    }

    private fun u64(v: Long) {
        room(8)
        for (i in 0 until 8) buf[bufLen++] = (v ushr (8 * i)).toByte()
    }

    private fun putBytes(b: ByteArray) {
        var off = 0
        while (off < b.size) {
            if (bufLen == BUFFER) flush()
            val n = minOf(BUFFER - bufLen, b.size - off)
            System.arraycopy(b, off, buf, bufLen, n)
            bufLen += n
            off += n
        }
    }

    private fun flush() {
        if (bufLen == 0) return
        val bb = ByteBuffer.wrap(buf, 0, bufLen)
        while (bb.hasRemaining()) channel.write(bb)
        flushed += bufLen
        bufLen = 0
    }

    /** 이미 쓴 자리를 고친다 — 버퍼를 먼저 내린다. 채널의 자리는 그대로다(위치 지정 쓰기). */
    private fun patch(at: Long, bytes: ByteArray) {
        flush()
        val bb = ByteBuffer.wrap(bytes)
        var pos = at
        while (bb.hasRemaining()) pos += channel.write(bb, pos)
    }

    companion object {
        /** 32비트 칸의 끝. 이 값부터는 ZIP64 칸에 적는다(이 값 자체가 'ZIP64 를 보라' 는 표시다). */
        const val ZIP64_LIMIT = 0xFFFFFFFFL

        private const val BUFFER = 64 * 1024

        private const val SIG_LOCAL = 0x04034B50L
        private const val SIG_CENTRAL = 0x02014B50L
        private const val SIG_EOCD = 0x06054B50L
        private const val SIG_EOCD64 = 0x06064B50L
        private const val SIG_LOCATOR = 0x07064B50L

        private const val FLAG_ENCRYPTED = 0x0001
        private const val FLAG_UTF8 = 0x0800

        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8

        private const val VERSION_FOLDER = 20
        private const val VERSION_ZIP64 = 45
        private const val VERSION_AES = 51

        private const val EXTRA_ZIP64 = 0x0001
        private const val EXTRA_UT = 0x5455

        /** 로컬 머리의 고정 부분(이름 앞까지). */
        private const val LOCAL_FIXED = 30L

        /** 로컬 머리에서 압축 크기·평문 크기 칸의 자리. */
        private const val LOCAL_SIZES_AT = 18L

        private const val ZIP64_LOCAL_LEN = 4 + 16
        private const val UT_LEN = 4 + 5
        private const val AES_LEN = 4 + 7

        /**
         * DOS 시각(날짜 << 16 | 시각) — 기기의 시간대로. 1980년 앞은 1980-01-01 00:00, 2107년 뒤는 2107-12-31 23:59:58 로 묶는다(칸에 들지
         * 않는다). 초는 2초 단위로 버린다.
         */
        internal fun dosTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
            val t = Instant.ofEpochMilli(millis).atZone(zone)
            if (t.year < 1980) return (1 shl 21) or (1 shl 16)
            if (t.year > 2107) return ((2107 - 1980) shl 25) or (12 shl 21) or (31 shl 16) or (23 shl 11) or (59 shl 5) or 29
            return ((t.year - 1980) shl 25) or (t.monthValue shl 21) or (t.dayOfMonth shl 16) or
                (t.hour shl 11) or (t.minute shl 5) or (t.second shr 1)
        }

        private fun le32(v: Long) = ByteArray(4) { (v ushr (8 * it)).toByte() }

        private fun le64(v: Long) = ByteArray(8) { (v ushr (8 * it)).toByte() }
    }
}
