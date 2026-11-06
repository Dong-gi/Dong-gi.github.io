package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import org.tukaani.xz.LZMA2InputStream
import org.tukaani.xz.LZMAInputStream
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 7z AES 의 **열쇠 유도 반복을 유도하기 전에** 본다(2026-10-16 보안 점검 — '7z AES 열쇠 유도 반복 횟수에 상한이 없다').
 *
 * ## 왜 머리를 따로 읽는가
 *
 * commons-compress 1.28.0 은 AES 코더 속성 첫 바이트의 아래 6비트(`numCyclesPower`)만큼 `1L shl n` 번 SHA-256 을 돈다
 * (`AES256SHA256Decoder.sha256Password` — 소스와 바이트코드로 확인). 상한도 인터럽트 확인도 없다 — 62 를 적은 파일에 암호를 넣으면 그
 * 스레드가 끝나지 않는다. `withTimeout`·`runInterruptible` 은 기다리기를 그만둘 뿐 그 고리를 멈추지 못하고, 해석 디스패처는 둘뿐이라
 * (`IroDispatchers.parsing`) 그런 파일 둘이면 압축 해석 전체가 멎는다.
 *
 * 라이브러리는 그 값을 공개 API 로 내주지 않는다 — 항목의 `contentMethods` 는 해제기 사슬을 만든 **뒤에** 채워지고, LZMA 해제기는
 * 만드는 순간 AES 를 읽어 열쇠를 유도한다. 그래서 7z 의 머리를 **우리가** 읽는다: 서명 머리 → 다음 머리 → (암호화·압축된 머리면 그
 * StreamsInfo, 아니면 MainStreamsInfo) → 폴더의 코더 속성. AES 메서드 id 는 `06 F1 07 01`. 읽는 차례와 모양은 라이브러리의
 * `SevenZFile`(`readHeaders` → `readEncodedHeader` → `readHeader` → `readStreamsInfo` → `readFolder`)을 그대로 따른다 — 라이브러리가
 * 쓰는 폴더는 여기서도 다 지난다.
 *
 * ## 무엇을 받는가
 *
 * 7-Zip 이 받는 것만 — 지수 0~[MAX_CYCLES_POWER] 와 [NO_HASH](반복 없이 소금과 암호를 그대로 열쇠로 쓴다 — 값이 싸다). 7-Zip
 * (`7zAes.cpp` 의 `k_NumCyclesPower_Supported_MAX`)과 py7zr(`numcyclespower <= 24`)이 같은 상한을 둔다. 7-Zip·p7zip·py7zr·
 * commons-compress 가 쓰는 값은 19 다. 24 면 2^24 번이라 기기에서 오래 돌 수 있다 — 끝은 있다(재지 않았다).
 *
 * ## 머리까지 잠긴 7z
 *
 * 머리를 잠근 열쇠는 그 속성이 평문(암호화된 머리의 StreamsInfo)이라 곧바로 본다. 그런데 **내용 폴더의 속성은 잠긴 머리 안에
 * 있다** — 머리의 반복이 정상이어도 내용 폴더에 62 를 적으면 라이브러리는 머리를 연 뒤 첫 항목을 읽는 순간 멎는다. 그래서 암호를
 * 받았으면 머리를 **우리가 풀어**(같은 유도 — 상한 안이고, 덩이로 묶어 해시해 라이브러리보다 훨씬 빠르다) 안의 폴더까지 본다. 압축만
 * 된 머리(7-Zip 의 기본 — LZMA)는 암호 없이도 푼다. 머리를 푸는 사슬은 7-Zip·py7zr 이 쓰는 것만 안다 — 복사·LZMA·LZMA2·AES. 그 밖이고
 * 암호를 받았으면 열지 않는다([ArchiveEncryptionUnsupportedException]) — 우리가 보지 못한 열쇠를 라이브러리가 유도하게 두지 않는다.
 *
 * ## 보지 않는 것
 *
 * 라이브러리가 유도하기 **전에** 스스로 실패하는 갈래(판·'머리가 없다'·추가 스트림·외부 폴더·같은 코더를 두 번 지나는 사슬)는
 * 따라가지 않고 [Verdict.SAFE] 로 둔다 — 그 파일은 라이브러리가 '깨졌다' 로 끝낸다. 머리의 CRC 는 보지 않는다(라이브러리가 본다 — 다르면 유도
 * 전에 끝난다. 우리는 맞든 틀리든 읽어 본다). 머리의 모양을 읽지 못하면(우리가 라이브러리보다 엄할 수 있는 자리) `IOException` 으로 끝낸다 —
 * 모르는 채 라이브러리에 넘기지 않는다.
 */
internal object SevenZKeyGuard {

    /** 7-Zip 이 받는 반복 지수의 상한(2^24 번). */
    const val MAX_CYCLES_POWER = 24

    /** 반복 없음 — 소금 ‖ 암호를 그대로 열쇠로 쓴다. 7-Zip 도 받는다. */
    const val NO_HASH = 0x3F

    /**
     * 한 머리(평문이든 풀어 낸 것이든)에서 읽는 바이트의 상한. 넘으면 '너무 크다' 다. 항목 1만 개(목록의 상한)짜리 7z 의 머리는 몇
     * MB 다 — 라이브러리는 머리를 통째로 배열에 올리므로 이보다 큰 머리는 어차피 메모리를 넘본다.
     */
    const val MAX_HEADER_BYTES = 64L * 1024 * 1024

    /** 폴더 하나의 코더 수 상한 — 7-Zip 의 `k_Scan_NumCoders_MAX` 와 같다. 실제 7z 는 넷을 넘지 않는다. */
    private const val MAX_CODERS = 64

    /** 코더 하나의 입출력 스트림 수 상한 — 7-Zip 의 `k_Scan_NumCodersStreams_in_Folder_MAX` 와 같다. */
    private const val MAX_CODER_STREAMS = 64

    /** 코더 속성 하나의 상한. AES 는 34바이트, LZMA 는 5바이트다. */
    private const val MAX_PROPERTIES = 64 * 1024

    /** 덩이 해시의 크기 — 반복 한 번(소금 ‖ 암호 ‖ 계수기 8바이트)을 이만큼 이어 붙여 한 번에 넘긴다. */
    private const val HASH_CHUNK = 64 * 1024

    /** 이만큼 읽을 때마다 인터럽트를 본다. */
    private const val CHECK_EVERY = 64 * 1024

    private const val SIGNATURE_HEADER = 32L
    private val SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    private val AES_ID = byteArrayOf(0x06, 0xF1.toByte(), 0x07, 0x01)
    private val COPY_ID = byteArrayOf(0x00)
    private val LZMA_ID = byteArrayOf(0x03, 0x01, 0x01)
    private val LZMA2_ID = byteArrayOf(0x21)

    // 머리의 이름표(7-Zip 의 7zFormat.txt — 라이브러리의 `NID`).
    private const val K_END = 0x00
    private const val K_HEADER = 0x01
    private const val K_ARCHIVE_PROPERTIES = 0x02
    private const val K_ADDITIONAL_STREAMS = 0x03
    private const val K_MAIN_STREAMS = 0x04
    private const val K_PACK_INFO = 0x06
    private const val K_UNPACK_INFO = 0x07
    private const val K_SIZE = 0x09
    private const val K_CRC = 0x0A
    private const val K_ENCODED_HEADER = 0x17

    enum class Verdict {
        /** 보이는 AES 코더가 모두 받을 수 있는 반복이다(또는 라이브러리가 유도 전에 스스로 실패한다). */
        SAFE,

        /** 받을 수 없는 반복이 있다 — 유도하지 않는다. */
        UNSAFE,
    }

    /** 이 지수로 유도해도 되는가 — 7-Zip 이 받는 값인가. */
    fun allows(cyclesPower: Int): Boolean = cyclesPower in 0..MAX_CYCLES_POWER || cyclesPower == NO_HASH

    /**
     * **라이브러리에 암호를 넘기기 직전에** 부른다. 받을 수 없는 반복이 보이면 [SevenZUnsafeKeyException].
     *
     * @param memoryLimitKiB 머리를 푸는 LZMA·LZMA2 사전의 메모리 상한 — 리더가 라이브러리에 거는 값과 같아야 한다.
     * @throws SevenZUnsafeKeyException 받을 수 없는 반복이 있다.
     * @throws ArchiveEncryptionUnsupportedException 잠긴 머리를 푸는 사슬을 우리가 모른다.
     * @throws IOException 머리를 읽지 못했다 — 깨졌거나 틀린 암호다(잠긴 머리가 풀리지 않았다). 라이브러리도 그 자리에서 실패한다.
     */
    fun check(source: DocumentSource, password: CharArray, memoryLimitKiB: Int) {
        if (scan(source, password, memoryLimitKiB) == Verdict.UNSAFE) throw SevenZUnsafeKeyException()
    }

    /**
     * 암호 없이 보이는 것만으로 받을 수 없는 반복이 있는가 — 잠긴 머리를 연 열쇠, 그리고 압축만 된(잠기지 않은) 머리 안의 내용 폴더.
     * 머리를 읽지 못하면 거짓이다(모른다 — 암호를 넣으면 [check] 가 다시 본다). 취소는 그대로 던진다.
     */
    fun unsafeWithoutPassword(source: DocumentSource, memoryLimitKiB: Int): Boolean = try {
        scan(source, null, memoryLimitKiB) == Verdict.UNSAFE
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: IOException) {
        false
    }

    internal fun scan(source: DocumentSource, password: CharArray?, memoryLimitKiB: Int): Verdict {
        val channel = source.openChannel() ?: throw IOException("7z 는 무작위 접근이 필요하다")
        channel.use { return scan(it, password, memoryLimitKiB) }
    }

    internal fun scan(channel: SeekableByteChannel, password: CharArray?, memoryLimitKiB: Int): Verdict {
        val size = channel.size()
        if (size < SIGNATURE_HEADER) throw EOFException("7z 서명 머리보다 짧다")
        val signature = readFully(channel, 0L, SIGNATURE_HEADER.toInt())
        for (i in SIGNATURE.indices) if (signature[i] != SIGNATURE[i]) throw IOException("7z 가 아니다")
        // 판이 0 이 아니면 라이브러리가 '모르는 판' 으로 끝낸다.
        if (signature[6].toInt() != 0) return Verdict.SAFE
        // 시작 머리의 CRC 가 0 이고 뒤 20바이트가 다 0 이면 라이브러리는 복구 모드(끄고 쓴다) 없이 '깨졌다' 로 끝낸다.
        if (le(signature, 8, 4) == 0L && (12 until 32).all { signature[it].toInt() == 0 }) return Verdict.SAFE
        val nextOffset = le(signature, 12, 8)
        val nextSize = le(signature, 20, 8)
        // 라이브러리의 `readStartHeader` 와 같은 검사 — 밖이면 그쪽이 먼저 던진다.
        if (nextOffset < 0 || nextOffset > size - SIGNATURE_HEADER) return Verdict.SAFE
        if (nextSize <= 0 || nextSize > size - SIGNATURE_HEADER - nextOffset) return Verdict.SAFE
        if (nextSize > MAX_HEADER_BYTES) throw ParseLimitExceededException("sevenZHeaderBytes", "다음 머리 ${nextSize}바이트")
        val head = HeaderInput(ChannelInput(channel, SIGNATURE_HEADER + nextOffset, nextSize), nextSize)
        return when (head.byte()) {
            K_HEADER -> scanHeader(head)
            K_ENCODED_HEADER -> scanEncoded(channel, head, password, memoryLimitKiB)
            // 라이브러리가 '머리가 없다' 로 끝낸다.
            else -> Verdict.SAFE
        }
    }

    // ---- 머리 ---------------------------------------------------------------------------------------------------------

    /** `kHeader` 뒤 — 내용 폴더(MainStreamsInfo)의 코더만 본다. 파일 목록(FilesInfo)은 보지 않는다(열쇠가 없다). */
    private fun scanHeader(head: HeaderInput): Verdict {
        var nid = head.byte()
        if (nid == K_ARCHIVE_PROPERTIES) {
            skipArchiveProperties(head)
            nid = head.byte()
        }
        // 추가 스트림은 라이브러리가 받지 않는다(그 자리에서 던진다). 내용 폴더가 없으면 열쇠도 없다.
        if (nid != K_MAIN_STREAMS) return Verdict.SAFE
        return if (readStreamsInfo(head, keepFirst = false).unsafe) Verdict.UNSAFE else Verdict.SAFE
    }

    /**
     * `kEncodedHeader` 뒤 — 머리를 담은 폴더의 StreamsInfo(평문). 그 폴더의 코더를 보고, 풀 수 있으면 머리를 풀어 그 안의 내용 폴더를
     * 본다. 라이브러리처럼 **첫 폴더와 첫 팩 스트림**으로 푼다.
     */
    private fun scanEncoded(channel: SeekableByteChannel, head: HeaderInput, password: CharArray?, memoryLimitKiB: Int): Verdict {
        val streams = readStreamsInfo(head, keepFirst = true)
        if (streams.unsafe) return Verdict.UNSAFE
        // 폴더·팩 스트림이 없으면 라이브러리가 '머리를 읽을 수 없다' 로 끝낸다.
        val folder = streams.first ?: return Verdict.SAFE
        val packSize = streams.firstPackSize.takeIf { it >= 0 } ?: return Verdict.SAFE
        // 같은 코더를 두 번 지나는 사슬은 라이브러리가 던진다.
        val chain = folder.ordered() ?: return Verdict.SAFE
        // 사슬의 코더가 여러 입출력을 가지면 라이브러리가 사슬을 세우다 던진다 — 그 앞에서 유도한 열쇠가 있어도 반복은 위에서 봤다.
        if (chain.any { !it.simple }) return Verdict.SAFE
        if (chain.any { it.isAes } && password == null) {
            // 머리가 잠겼고 암호가 없다. 라이브러리도 암호를 묻고 멈춘다(유도 전에). 보이는 것(머리의 열쇠)은 위에서 봤다.
            return Verdict.SAFE
        }
        val unknown = chain.firstOrNull { !it.isAes && !it.isCopy && !it.isLzma && !it.isLzma2 }
        if (unknown != null) {
            // 암호가 없으면 라이브러리는 어떤 열쇠도 유도하지 않는다(내용 폴더를 읽을 때 암호를 묻고 멈춘다).
            if (password == null) return Verdict.SAFE
            throw ArchiveEncryptionUnsupportedException("이 앱이 풀지 않는 7z 머리 사슬")
        }
        val unpackSize = folder.unpackSize()
        if (unpackSize <= 0) return Verdict.SAFE
        if (unpackSize > MAX_HEADER_BYTES) throw ParseLimitExceededException("sevenZHeaderBytes", "풀어 낸 머리 ${unpackSize}바이트")
        val packStart = SIGNATURE_HEADER + streams.packPos
        if (streams.packPos < 0 || packSize > channel.size() || packStart > channel.size() - packSize) return Verdict.SAFE
        var stream: InputStream = ChannelInput(channel, packStart, packSize)
        for (coder in chain) {
            val outSize = folder.unpackSizeOf(coder)
            stream = when {
                coder.isCopy -> stream
                coder.isAes -> decryptor(stream, coder.properties, password!!)
                coder.isLzma -> lzma(stream, coder.properties, outSize, memoryLimitKiB)
                else -> lzma2(stream, coder.properties, memoryLimitKiB)
            }
        }
        BufferedInputStream(stream, 8192).use { decoded ->
            val inner = HeaderInput(decoded, unpackSize)
            // 풀어 낸 것이 머리가 아니면 라이브러리도 '머리가 없다' 로 끝낸다 — 대개 틀린 암호다.
            if (inner.byte() != K_HEADER) throw IOException("풀어 낸 7z 머리가 머리로 시작하지 않는다")
            return scanHeader(inner)
        }
    }

    private fun skipArchiveProperties(head: HeaderInput) {
        var nid = head.number()
        while (nid != K_END.toLong()) {
            head.skip(head.number())
            nid = head.number()
        }
    }

    /** StreamsInfo 를 읽은 것. [first] 는 첫 폴더(머리를 풀 때만 든다). */
    private class Streams {
        var unsafe = false
        var packPos = 0L
        var firstPackSize = -1L
        var first: Folder? = null
    }

    private class Coder(val id: ByteArray, val numIn: Long, val numOut: Long, val properties: ByteArray?) {
        val simple: Boolean get() = numIn == 1L && numOut == 1L
        val isAes: Boolean get() = id.contentEquals(AES_ID)
        val isCopy: Boolean get() = id.contentEquals(COPY_ID)
        val isLzma: Boolean get() = id.contentEquals(LZMA_ID)
        val isLzma2: Boolean get() = id.contentEquals(LZMA2_ID)
    }

    private class Folder(val coders: List<Coder>, val bindPairs: List<LongArray>, val packed: LongArray) {
        var unpackSizes: LongArray = LongArray(0)

        /**
         * 팩 스트림에서 바깥으로 — 라이브러리의 `Folder.getOrderedCoders` 그대로다(코더가 모두 단순할 때 입출력 번호가 곧 코더 번호다).
         * 팩 스트림이 없거나 첫 팩 스트림의 번호가 코더 밖이면 **빈 사슬**이다 — 라이브러리는 그때 팩 스트림을 **그대로** 머리로 읽는다
         * (그래서 그 안의 내용 폴더도 봐야 한다). 같은 코더를 두 번 지나면 null(라이브러리가 던진다).
         */
        fun ordered(): List<Coder>? {
            val out = ArrayList<Coder>(coders.size)
            if (packed.isEmpty() || coders.isEmpty()) return out
            var current = packed[0]
            while (current >= 0 && current < coders.size) {
                val c = coders[current.toInt()]
                if (out.any { it === c }) return null
                out += c
                val pair = bindPairs.indexOfFirst { it[1] == current }
                current = if (pair >= 0) bindPairs[pair][0] else -1
            }
            return out
        }

        fun unpackSizeOf(coder: Coder): Long = coders.indexOfFirst { it === coder }.let { unpackSizes.getOrElse(it) { 0L } }

        /** 폴더의 마지막 출력 — 묶이지 않은 출력 스트림의 크기(라이브러리의 `getUnpackSize`). */
        fun unpackSize(): Long {
            for (i in unpackSizes.indices.reversed()) {
                if (bindPairs.none { it[1] == i.toLong() }) return unpackSizes[i]
            }
            return 0L
        }
    }

    /** 라이브러리의 `readStreamsInfo` 차례 — 팩 정보, 그다음 폴더. 폴더까지 보면 끝이다(뒤의 하위 스트림 정보에는 열쇠가 없다). */
    private fun readStreamsInfo(head: HeaderInput, keepFirst: Boolean): Streams {
        val s = Streams()
        var nid = head.byte()
        if (nid == K_PACK_INFO) {
            readPackInfo(head, s)
            nid = head.byte()
        }
        if (nid == K_UNPACK_INFO) readUnpackInfo(head, s, keepFirst)
        return s
    }

    private fun readPackInfo(head: HeaderInput, s: Streams) {
        s.packPos = head.number()
        val count = head.number()
        var nid = head.byte()
        if (nid == K_SIZE) {
            var i = 0L
            while (i < count) {
                val size = head.number()
                if (i == 0L) s.firstPackSize = size
                i++
            }
            nid = head.byte()
        }
        if (nid == K_CRC) {
            head.skip(saturatingTimes4(definedCount(head, count)))
            head.byte()
        }
    }

    private fun readUnpackInfo(head: HeaderInput, s: Streams, keepFirst: Boolean) {
        head.byte() // kFolder
        val count = head.number()
        // 바깥 폴더(`External`)는 라이브러리가 받지 않는다.
        if (head.byte() != 0) return
        var i = 0L
        while (i < count) {
            val folder = readFolder(head, s, keep = keepFirst && i == 0L)
            if (s.unsafe) return
            if (folder != null) s.first = folder
            i++
        }
        val first = s.first ?: return
        // 풀 크기는 폴더마다 출력 수만큼 이어진다 — 첫 폴더의 것이 맨 앞이다.
        head.byte() // kCodersUnpackSize
        val outs = first.coders.sumOf { it.numOut }
        first.unpackSizes = LongArray(outs.toInt()) { head.number() }
    }

    /** 라이브러리의 `readFolder` 그대로. AES 코더의 반복을 보며 지나간다. [keep] 이면 폴더를 돌려준다. */
    private fun readFolder(head: HeaderInput, s: Streams, keep: Boolean): Folder? {
        val numCoders = head.number()
        if (numCoders <= 0 || numCoders > MAX_CODERS) throw IOException("7z 폴더의 코더 수가 이상하다: $numCoders")
        val coders = if (keep) ArrayList<Coder>(numCoders.toInt()) else null
        var totalIn = 0L
        var totalOut = 0L
        repeat(numCoders.toInt()) {
            val flags = head.byte()
            val id = head.bytes((flags and 0x0F).toLong())
            val simple = flags and 0x10 == 0
            val numIn = if (simple) 1L else head.number()
            val numOut = if (simple) 1L else head.number()
            if (numIn !in 0L..MAX_CODER_STREAMS.toLong() || numOut !in 0L..MAX_CODER_STREAMS.toLong()) throw IOException("7z 코더의 스트림 수가 이상하다")
            totalIn += numIn
            totalOut += numOut
            val properties = if (flags and 0x20 != 0) {
                val n = head.number()
                if (n !in 0L..MAX_PROPERTIES.toLong()) throw IOException("7z 코더 속성이 너무 길다")
                head.bytes(n)
            } else {
                null
            }
            // 대체 메서드는 라이브러리가 받지 않는다(그 자리에서 던진다).
            if (flags and 0x80 != 0) throw IOException("7z 의 대체 메서드")
            if (id.contentEquals(AES_ID)) {
                val cycles = properties?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0x3F }
                if (cycles != null && !allows(cycles)) {
                    s.unsafe = true
                    return null
                }
            }
            coders?.add(Coder(id, numIn, numOut, properties))
        }
        if (totalOut == 0L || totalIn < totalOut - 1) throw IOException("7z 폴더의 스트림 수가 맞지 않는다")
        val bindPairs = ArrayList<LongArray>()
        repeat((totalOut - 1).toInt()) {
            val inIndex = head.number()
            val outIndex = head.number()
            if (keep) bindPairs += longArrayOf(inIndex, outIndex)
        }
        val numPacked = totalIn - (totalOut - 1)
        val packed = if (numPacked == 1L) {
            // 묶이지 않은 첫 입력 — 라이브러리의 `findBindPairForInStream` 차례다.
            var i = 0L
            while (i < totalIn && bindPairs.any { it[0] == i }) i++
            longArrayOf(i)
        } else {
            LongArray(numPacked.toInt()) { head.number() }
        }
        return if (keep) Folder(checkNotNull(coders), bindPairs, packed) else null
    }

    /** `allAreDefined` 바이트와 비트 — 정의된 것의 수. 라이브러리의 `readAllOrBits`. */
    private fun definedCount(head: HeaderInput, count: Long): Long {
        if (head.byte() != 0) return count
        var defined = 0L
        var i = 0L
        var cache = 0
        while (i < count) {
            if (i % 8 == 0L) cache = head.byte()
            if (cache and (0x80 ushr (i % 8).toInt()) != 0) defined++
            i++
        }
        return defined
    }

    private fun saturatingTimes4(n: Long): Long = if (n > Long.MAX_VALUE / 4) Long.MAX_VALUE else n * 4

    // ---- 머리를 푼다 ----------------------------------------------------------------------------------------------------

    /**
     * 라이브러리의 `AES256SHA256Decoder` 와 같은 열쇠·IV 로 AES-256-CBC 를 푼다. 속성의 반복은 이미 [allows] 를 지났다.
     * 암호의 바이트는 라이브러리와 같은 변환이다(`utf16Decode` — UTF-16LE 로 `encode`). 다 쓴 바이트는 덮는다.
     */
    private fun decryptor(input: InputStream, properties: ByteArray?, password: CharArray): InputStream {
        if (properties == null || properties.size < 2) throw IOException("7z AES 속성이 없거나 짧다")
        val b0 = properties[0].toInt() and 0xFF
        val b1 = properties[1].toInt() and 0xFF
        val cycles = b0 and 0x3F
        val ivSize = (b0 shr 6 and 1) + (b1 and 0x0F)
        val saltSize = (b0 shr 7 and 1) + (b1 shr 4)
        if (2 + saltSize + ivSize > properties.size) throw IOException("7z AES 속성의 소금·IV 가 길다")
        if (!allows(cycles)) throw SevenZUnsafeKeyException()
        val salt = properties.copyOfRange(2, 2 + saltSize)
        val iv = ByteArray(16)
        System.arraycopy(properties, 2 + saltSize, iv, 0, ivSize)
        val encoded = StandardCharsets.UTF_16LE.encode(CharBuffer.wrap(password))
        val passwordBytes = if (encoded.hasArray()) encoded.array() else ByteArray(encoded.remaining()).also { encoded.get(it) }
        val key = try {
            deriveKey(passwordBytes, salt, cycles)
        } finally {
            passwordBytes.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
        }
        try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return CipherInputStream(input, cipher)
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException("7z 머리를 풀 AES 를 세우지 못했다")
        } finally {
            key.fill(0)
        }
    }

    /**
     * 7z 의 열쇠 — `SHA-256(소금 ‖ 암호 ‖ 계수기)` 를 2^[cycles] 번 이은 것(계수기는 0 부터 8바이트 리틀 엔디언). [NO_HASH] 면 소금 ‖
     * 암호를 그대로(32바이트로 자르거나 0 으로 채운다). 라이브러리와 같은 값이다(시험이 견준다).
     *
     * **덩이로 묶어 넘긴다** — 반복마다 `update` 를 세 번 부르는 라이브러리와 달리 반복 수천 번을 한 배열에 이어 한 번에 넘긴다. 해시하는
     * 바이트는 같고 부르는 횟수만 준다. 덩이마다 인터럽트를 본다 — 취소가 닿는다.
     */
    internal fun deriveKey(password: ByteArray, salt: ByteArray, cycles: Int): ByteArray {
        if (!allows(cycles)) throw SevenZUnsafeKeyException()
        if (cycles == NO_HASH) {
            val key = ByteArray(32)
            System.arraycopy(salt, 0, key, 0, minOf(salt.size, 32))
            if (salt.size < 32) System.arraycopy(password, 0, key, salt.size, minOf(password.size, 32 - salt.size))
            return key
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val unit = salt.size + password.size + 8
        val perChunk = maxOf(1, HASH_CHUNK / unit)
        val chunk = ByteArray(perChunk * unit)
        for (k in 0 until perChunk) {
            System.arraycopy(salt, 0, chunk, k * unit, salt.size)
            System.arraycopy(password, 0, chunk, k * unit + salt.size, password.size)
        }
        try {
            val rounds = 1L shl cycles
            var counter = 0L
            while (counter < rounds) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("7z 열쇠 유도 중에 중단되었다")
                val n = minOf(perChunk.toLong(), rounds - counter).toInt()
                for (k in 0 until n) {
                    var c = counter + k
                    var at = k * unit + salt.size + password.size
                    repeat(8) {
                        chunk[at++] = c.toByte()
                        c = c ushr 8
                    }
                }
                digest.update(chunk, 0, n * unit)
                counter += n
            }
            return digest.digest()
        } finally {
            chunk.fill(0)
        }
    }

    /** 라이브러리의 `LZMADecoder` — 속성 1바이트 + 사전 4바이트(리틀 엔디언), 메모리 상한, 끝 조건을 느슨하게. */
    private fun lzma(input: InputStream, properties: ByteArray?, outSize: Long, memoryLimitKiB: Int): InputStream {
        if (properties == null || properties.size < 5) throw IOException("7z LZMA 속성이 없거나 짧다")
        val propsByte = properties[0]
        val dictSize = le(properties, 1, 4).toInt()
        // 사전·속성 바이트가 이상하면 xz 가 `IOException` 으로 알린다(라이브러리도 같은 함수로 잰다).
        val memory = LZMAInputStream.getMemoryUsage(dictSize, propsByte)
        if (memory > memoryLimitKiB) throw org.apache.commons.compress.MemoryLimitException(memory.toLong(), memoryLimitKiB)
        return LZMAInputStream(input, outSize, propsByte, dictSize).also { it.enableRelaxedEndCondition() }
    }

    /** 라이브러리의 `LZMA2Decoder` — 사전 크기의 비트, 메모리 상한. */
    private fun lzma2(input: InputStream, properties: ByteArray?, memoryLimitKiB: Int): InputStream {
        if (properties == null || properties.isEmpty()) throw IOException("7z LZMA2 속성이 없다")
        val bits = properties[0].toInt() and 0xFF
        if (bits and 0x3F.inv() != 0 || bits > 40) throw IOException("7z LZMA2 사전이 이상하다")
        val dictSize = if (bits == 40) -1 else (2 or (bits and 1)) shl (bits / 2 + 11)
        if (dictSize < 0) throw IOException("7z LZMA2 사전이 너무 크다")
        val memory = LZMA2InputStream.getMemoryUsage(dictSize)
        if (memory > memoryLimitKiB) throw org.apache.commons.compress.MemoryLimitException(memory.toLong(), memoryLimitKiB)
        return LZMA2InputStream(input, dictSize)
    }

    // ---- 읽기 -----------------------------------------------------------------------------------------------------------

    /**
     * 7z 머리를 한 바이트씩 읽는다 — 남은 양([remaining])을 넘지 않는다. 수(`UINT64`)는 7z 의 가변 길이다. 넘는 수는 언제나 바이트를
     * 먹으므로 고리는 머리의 길이로 묶인다.
     */
    private class HeaderInput(private val input: InputStream, private var remaining: Long) {
        private var sinceCheck = 0

        fun byte(): Int {
            if (remaining <= 0) throw EOFException("7z 머리가 모자라다")
            val b = input.read()
            if (b < 0) throw EOFException("7z 머리가 모자라다")
            remaining--
            if (++sinceCheck >= CHECK_EVERY) {
                sinceCheck = 0
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("7z 머리를 읽는 중에 중단되었다")
            }
            return b
        }

        /** 라이브러리의 `readUint64` 그대로. */
        fun number(): Long {
            val first = byte().toLong()
            var mask = 0x80
            var value = 0L
            for (i in 0 until 8) {
                if (first and mask.toLong() == 0L) return value or ((first and (mask - 1).toLong()) shl (8 * i))
                value = value or (byte().toLong() shl (8 * i))
                mask = mask ushr 1
            }
            return value
        }

        fun bytes(n: Long): ByteArray {
            if (n < 0 || n > remaining || n > Int.MAX_VALUE) throw EOFException("7z 머리가 모자라다")
            return ByteArray(n.toInt()) { byte().toByte() }
        }

        fun skip(n: Long) {
            if (n < 0 || n > remaining) throw EOFException("7z 머리가 모자라다")
            var left = n
            while (left > 0) {
                byte()
                left--
            }
        }
    }

    /** 채널의 [start] 부터 [length] 바이트. 자기 자리를 들고 있다 — 같은 채널을 다른 흐름과 번갈아 읽어도 섞이지 않는다. */
    private class ChannelInput(private val channel: SeekableByteChannel, private var position: Long, private var left: Long) : InputStream() {
        // `limit` 은 `Buffer` 의 것으로 부른다 — `ByteBuffer` 의 공변 반환 판(자바 9)은 api-versions.xml 이 API 31 에 있다고 말하지 않는다
        // (`tools/check_api_level.py` 가 잡았다).
        private val buffer = ByteBuffer.allocate(8192).also { (it as Buffer).limit(0) }

        override fun read(): Int = if (fill()) buffer.get().toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, buffer.remaining())
            buffer.get(b, off, n)
            return n
        }

        private fun fill(): Boolean {
            if (buffer.hasRemaining()) return true
            if (left <= 0) return false
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("7z 머리를 읽는 중에 중단되었다")
            buffer.clear()
            if (left < buffer.capacity()) (buffer as Buffer).limit(left.toInt())
            channel.position(position)
            var n = 0
            while (buffer.hasRemaining()) {
                val r = channel.read(buffer)
                if (r <= 0) break
                n += r
            }
            buffer.flip()
            if (n <= 0) return false
            position += n
            left -= n
            return true
        }
    }

    private fun readFully(channel: SeekableByteChannel, at: Long, n: Int): ByteArray {
        val buffer = ByteBuffer.allocate(n)
        channel.position(at)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) throw EOFException("7z 가 짧다")
        }
        return buffer.array()
    }

    /** 리틀 엔디언 [n] 바이트(8 이하). */
    private fun le(b: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (i in n - 1 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }
}

/** 7z AES 의 열쇠 유도 반복이 7-Zip 이 받는 범위 밖이다([SevenZKeyGuard]). 유도하지 않고 거절한다 — 화면은 '풀지 않는 방식' 을 말한다. */
internal class SevenZUnsafeKeyException : ArchiveEncryptionUnsupportedException("7z 열쇠 유도 반복이 상한을 넘는다")
