package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 7z AES 의 열쇠 유도 반복을 **유도하기 전에** 거절하는가([SevenZKeyGuard]).
 *
 * 받을 수 없는 반복(25·30·62)을 적은 7z 는 암호를 넣어도 **몇 초 안에** '풀지 않는 방식'([SevenZUnsafeKeyException])으로 끝나야 한다.
 * 라이브러리가 그 반복을 돌면 25 는 이 PC 에서도 몇 초, 62 는 끝나지 않는다 — 그래서 시험은 시간 제한이 있는 데몬 스레드에서 돈다
 * ([within]). 가드가 없으면 62 는 시간 초과로, 25·30 은 다른 결과로 깨진다.
 *
 * 표본은 전부 **합성**이다 — commons-compress 가 잠근 것(반복 19)의 속성 바이트를 고쳐 CRC 를 다시 맞춘 것, py7zr 표본(`archivecrypt/`)의
 * 평문 StreamsInfo 를 고친 것, 이 시험이 손으로 지은 머리([Mini7z]).
 */
class SevenZKeyGuardTest {

    private val password = "iroiro"
    private val unsafeCycles = listOf(25, 30, 62)

    private val dir = File(javaClass.classLoader!!.getResource("archivecrypt/hello.txt")!!.toURI()).parentFile

    // ---- 내용만 잠긴 7z(평문 머리) --------------------------------------------------------------------------------------

    @Test
    fun 반복_19_인_정상_7z_는_그대로_열린다() {
        val bytes = sevenZWithPassword(mapOf("a.txt" to "가나다 hello".toByteArray(), "b.bin" to ByteArray(4000) { it.toByte() }))
        val src = ByteArrayDocumentSource(bytes, "ok.7z")
        assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(src, password.toCharArray(), MEMORY_KIB))
        Archives.open(src, password = password.toCharArray()).use { r ->
            assertTrue(Archives.verifyPassword(r))
            assertContentEquals("가나다 hello".toByteArray(), r.open(r.entries.first { it.name == "a.txt" }).use { it.readBytes() })
        }
        // 암호 없이 — 자물쇠가 있고 암호를 물을 수 있다(물어도 소용없는 잠금이 아니다).
        Archives.open(src).use { r ->
            assertTrue(r.entries.all { it.isEncrypted && it.needsPassword && !it.lockedForGood }, "${r.entries}")
        }
    }

    @Test
    fun 받을_수_없는_반복은_유도하기_전에_거절한다() {
        val normal = sevenZWithPassword(mapOf("a.txt" to "secret".toByteArray()))
        for (cycles in unsafeCycles) {
            val src = ByteArrayDocumentSource(withCycles(normal, cycles), "c$cycles.7z")
            // 목록 → 암호 확인(화면이 암호를 받으면 하는 일)까지. 라이브러리는 확인에서 내용 폴더의 열쇠를 유도한다.
            val outcome = within(TIME_LIMIT_MS) {
                Archives.open(src, password = password.toCharArray()).use { Archives.verifyPassword(it) }
            }
            assertIs<SevenZUnsafeKeyException>(outcome.exceptionOrNull(), "반복 $cycles: $outcome")
            // 화면이 가르는 바탕 타입이다(이름이 아니라 — 릴리스에서는 이름이 줄어든다).
            assertIs<ArchiveEncryptionUnsupportedException>(outcome.exceptionOrNull())
        }
    }

    @Test
    fun 받을_수_없는_반복이면_암호_없는_목록에서도_묻지_않는다() {
        val normal = sevenZWithPassword(mapOf("a.txt" to "secret".toByteArray()))
        for (cycles in unsafeCycles) {
            val src = ByteArrayDocumentSource(withCycles(normal, cycles), "c$cycles.7z")
            val entries = within(TIME_LIMIT_MS) { Archives.open(src).use { it.entries } }.getOrThrow()
            assertTrue(entries.all { it.isEncrypted && it.lockedForGood && !it.needsPassword && !it.isReadable }, "반복 $cycles: $entries")
        }
    }

    @Test
    fun 경계는_7_Zip_과_같다() {
        assertTrue(SevenZKeyGuard.allows(0))
        assertTrue(SevenZKeyGuard.allows(19))
        assertTrue(SevenZKeyGuard.allows(24))
        assertFalse(SevenZKeyGuard.allows(25))
        assertFalse(SevenZKeyGuard.allows(62))
        // 63(0x3F)은 '반복 없음' — 7-Zip 도 받는다.
        assertTrue(SevenZKeyGuard.allows(63))
        val normal = sevenZWithPassword(mapOf("a.txt" to "x".toByteArray()))
        assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(ByteArrayDocumentSource(withCycles(normal, 24), "24.7z"), null, MEMORY_KIB))
        assertEquals(SevenZKeyGuard.Verdict.UNSAFE, SevenZKeyGuard.scan(ByteArrayDocumentSource(withCycles(normal, 25), "25.7z"), null, MEMORY_KIB))
    }

    // ---- 머리까지 잠긴 7z ---------------------------------------------------------------------------------------------

    @Test
    fun 머리를_잠근_열쇠의_반복도_유도하기_전에_본다() {
        val sample = File(dir, "7z-aes-header-py7zr.7z").readBytes()
        for (cycles in unsafeCycles) {
            val src = ByteArrayDocumentSource(withCycles(sample, cycles), "h$cycles.7z")
            // 암호를 넣으면 — 라이브러리는 여는 순간 머리의 열쇠를 유도한다.
            val withPassword = within(TIME_LIMIT_MS) { Archives.open(src, password = password.toCharArray()).use { it.entries } }
            assertIs<SevenZUnsafeKeyException>(withPassword.exceptionOrNull(), "반복 $cycles: $withPassword")
            // 암호 없이 — 물어도 소용없으니 묻지 않는다('암호가 필요하다' 가 아니라 '풀지 않는 방식').
            val without = within(TIME_LIMIT_MS) { Archives.open(src).use { it.entries } }
            assertIs<SevenZUnsafeKeyException>(without.exceptionOrNull(), "반복 $cycles(암호 없이): $without")
        }
    }

    /**
     * **머리의 반복은 정상이고 잠긴 머리 안의 내용 폴더가 62** — 라이브러리는 머리를 열고 첫 항목을 읽는 순간 멎는다. 머리를 우리가 풀어
     * 안을 봐야 잡힌다.
     */
    @Test
    fun 잠긴_머리_안의_내용_폴더까지_본다() {
        val content = ByteArray(32) { 7 }
        val main = Mini7z.mainHeader(content.size.toLong(), Mini7z.folder(Mini7z.coder(Mini7z.AES, Mini7z.aesProps(62))))
        val archive = Mini7z.encryptedHeaderArchive(content, main, password, headerCycles = 3)
        val src = ByteArrayDocumentSource(archive, "inner62.7z")
        val outcome = within(TIME_LIMIT_MS) {
            Archives.open(src, password = password.toCharArray()).use { Archives.verifyPassword(it) }
        }
        assertIs<SevenZUnsafeKeyException>(outcome.exceptionOrNull(), "$outcome")
        // 암호 없이는 안을 볼 수 없다 — 머리의 열쇠(3)는 정상이라 암호를 묻는다. 암호를 넣으면 위에서 거절된다.
        assertFailsWith<ArchivePasswordException> { Archives.open(src) }
    }

    /** 우리가 지은 잠긴 머리를 **라이브러리가** 연다 — 우리 머리 풀기(AES-CBC·열쇠·사슬)가 라이브러리와 같은 것을 읽는다는 오라클이다. */
    @Test
    fun 손으로_지은_잠긴_머리를_라이브러리와_우리가_같게_읽는다() {
        val content = "copy folder".toByteArray()
        val main = Mini7z.mainHeader(content.size.toLong(), Mini7z.folder(Mini7z.coder(Mini7z.COPY)))
        val src = ByteArrayDocumentSource(Mini7z.encryptedHeaderArchive(content, main, password, headerCycles = 3), "ok.7z")
        assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(src, password.toCharArray(), MEMORY_KIB))
        Archives.open(src, password = password.toCharArray()).use { r ->
            assertEquals(listOf("a.txt"), r.entries.map { it.name })
            assertContentEquals(content, r.open(r.entries.single()).use { it.readBytes() })
        }
        // 틀린 암호 — 우리 머리 풀기가 머리를 얻지 못한다. 라이브러리와 같이 '틀렸다' 다.
        val wrong = assertFailsWith<ArchivePasswordException> { Archives.open(src, password = "iroiro!".toCharArray()) }
        assertTrue(wrong.wrongPassword)
    }

    /** 압축만 된(잠기지 않은) 머리 — 7-Zip 의 기본이다. 암호 없이도 풀어 안의 내용 폴더를 본다. */
    @Test
    fun 압축만_된_머리는_암호_없이도_풀어_본다() {
        val content = ByteArray(32) { 1 }
        val main = Mini7z.mainHeader(content.size.toLong(), Mini7z.folder(Mini7z.coder(Mini7z.AES, Mini7z.aesProps(62))))
        val src = ByteArrayDocumentSource(Mini7z.lzma2HeaderArchive(content, main), "lzma2head.7z")
        assertEquals(SevenZKeyGuard.Verdict.UNSAFE, SevenZKeyGuard.scan(src, null, MEMORY_KIB))
        val entries = within(TIME_LIMIT_MS) { Archives.open(src).use { it.entries } }.getOrThrow()
        assertTrue(entries.single().lockedForGood, "$entries")
        val outcome = within(TIME_LIMIT_MS) { Archives.open(src, password = password.toCharArray()).use { it.entries } }
        assertIs<SevenZUnsafeKeyException>(outcome.exceptionOrNull(), "$outcome")
        // 같은 모양에 정상 반복이면 묻는다.
        val okMain = Mini7z.mainHeader(content.size.toLong(), Mini7z.folder(Mini7z.coder(Mini7z.AES, Mini7z.aesProps(19))))
        val ok = ByteArrayDocumentSource(Mini7z.lzma2HeaderArchive(content, okMain), "lzma2ok.7z")
        assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(ok, null, MEMORY_KIB))
        Archives.open(ok).use { r -> assertTrue(r.entries.single().needsPassword, "${r.entries}") }
    }

    /** 잠긴 머리를 푸는 사슬이 우리가 모르는 것이면 열지 않는다 — 안을 보지 못한 채 라이브러리가 열쇠를 유도하게 두지 않는다. */
    @Test
    fun 모르는_머리_사슬은_암호를_받아도_열지_않는다() {
        val content = ByteArray(16)
        val main = Mini7z.mainHeader(content.size.toLong(), Mini7z.folder(Mini7z.coder(Mini7z.COPY)))
        // 델타 필터(03) → AES 의 두 코더 사슬. 델타는 머리를 푸는 데 쓰지 않는다(7-Zip·py7zr).
        val headerFolder = Mini7z.chain(Mini7z.coder(Mini7z.AES, Mini7z.aesProps(3)), Mini7z.coder(byteArrayOf(0x03), byteArrayOf(0)))
        val src = ByteArrayDocumentSource(Mini7z.archiveWithEncodedHeader(content, ByteArray(32), headerFolder, listOf(32L, main.size.toLong())), "delta.7z")
        val outcome = within(TIME_LIMIT_MS) { Archives.open(src, password = password.toCharArray()).use { it.entries } }
        assertIs<ArchiveEncryptionUnsupportedException>(outcome.exceptionOrNull(), "$outcome")
    }

    /** 이 저장소의 7z 표본(py7zr — 반복 19)이 모두 받아들여진다. 내용만 잠근 것·머리까지 잠근 것·LZMA·한글 암호·암호 없는 64 MiB 사전. */
    @Test
    fun 표본_7z_는_모두_받아들여진다() {
        val samples = mapOf(
            "7z-aes-py7zr.7z" to "iroiro",
            "7z-aes-header-py7zr.7z" to "iroiro",
            "7z-lzma-aes-py7zr.7z" to "iroiro",
            "7z-aes-ko-py7zr.7z" to "비밀번호",
        )
        for ((name, pw) in samples) {
            val src = FileDocumentSource(File(dir, name))
            assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(src, pw.toCharArray(), MEMORY_KIB), name)
            assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(src, null, MEMORY_KIB), "$name(암호 없이)")
            Archives.open(src, password = pw.toCharArray()).use { assertTrue(Archives.verifyPassword(it), name) }
        }
        val plain = FileDocumentSource(File(dir, "7z-lzma2-64m-py7zr.7z"))
        assertEquals(SevenZKeyGuard.Verdict.SAFE, SevenZKeyGuard.scan(plain, null, MEMORY_KIB))
    }

    // ---- 열쇠 ----------------------------------------------------------------------------------------------------------

    /** 우리 열쇠 유도(덩이로 묶어 해시한다)가 라이브러리의 것(`AES256SHA256Decoder.sha256Password` — 반사로 부른다)과 같다. */
    @Test
    fun 열쇠는_라이브러리와_같다() {
        val library = Class.forName("org.apache.commons.compress.archivers.sevenz.AES256SHA256Decoder")
            .getDeclaredMethod("sha256Password", ByteArray::class.java, Int::class.javaPrimitiveType, ByteArray::class.java)
            .apply { isAccessible = true }
        val passwords = listOf("", "a", "iroiro", "비밀번호", "x".repeat(70))
        val salts = listOf(ByteArray(0), ByteArray(1) { 9 }, ByteArray(16) { (it * 3).toByte() })
        for (pw in passwords) for (salt in salts) for (cycles in listOf(0, 1, 5, 12)) {
            val bytes = pw.toByteArray(Charsets.UTF_16LE)
            val ours = SevenZKeyGuard.deriveKey(bytes, salt, cycles)
            val theirs = library.invoke(null, bytes, cycles, salt) as ByteArray
            assertContentEquals(theirs, ours, "'$pw' 소금 ${salt.size} 반복 $cycles")
            assertContentEquals(Mini7z.key(pw, cycles, salt), ours, "참조 구현과 다르다 — '$pw' ${salt.size} $cycles")
        }
        // 63 은 해시하지 않는다 — 소금 ‖ 암호를 32바이트로.
        val noHash = SevenZKeyGuard.deriveKey("ab".toByteArray(Charsets.UTF_16LE), byteArrayOf(1, 2), SevenZKeyGuard.NO_HASH)
        assertContentEquals(byteArrayOf(1, 2, 'a'.code.toByte(), 0, 'b'.code.toByte(), 0) + ByteArray(26), noHash)
        // 받을 수 없는 반복은 유도 자체를 거절한다.
        assertFailsWith<SevenZUnsafeKeyException> { SevenZKeyGuard.deriveKey(ByteArray(2), ByteArray(0), 25) }
    }

    @Test
    fun 열쇠_유도는_취소가_닿는다() {
        val outcome = within(TIME_LIMIT_MS) {
            Thread.currentThread().interrupt()
            SevenZKeyGuard.deriveKey(ByteArray(12), ByteArray(0), SevenZKeyGuard.MAX_CYCLES_POWER)
        }
        assertIs<InterruptedIOException>(outcome.exceptionOrNull(), "$outcome")
    }

    // ---- 도구 ----------------------------------------------------------------------------------------------------------

    /**
     * [block] 을 데몬 스레드에서 돌려 [ms] 안에 끝나면 그 결과. 끝나지 않으면 시험이 실패한다 — 라이브러리가 열쇠를 유도하고 있다는
     * 뜻이다(그 스레드는 멈출 수 없어 시험 JVM 이 끝날 때 함께 사라진다).
     */
    private fun <T> within(ms: Long, block: () -> T): Result<T> {
        val result = AtomicReference<Result<T>>()
        val thread = Thread { result.set(runCatching(block)) }.apply {
            isDaemon = true
            start()
        }
        thread.join(ms)
        if (thread.isAlive) {
            thread.interrupt()
            fail("${ms}ms 안에 끝나지 않았다 — 열쇠를 유도하고 있다")
        }
        return result.get()
    }

    /** commons-compress 로 잠근 7z(AES-256, 반복 19, 내용은 LZMA2 — 머리는 평문 `kHeader`). */
    private fun sevenZWithPassword(files: Map<String, ByteArray>): ByteArray {
        val channel = SeekableInMemoryByteChannel()
        SevenZOutputFile(channel, password.toCharArray()).use { out ->
            for ((name, data) in files) {
                val entry = SevenZArchiveEntry()
                entry.name = name
                entry.size = data.size.toLong()
                out.putArchiveEntry(entry)
                out.write(data)
                out.closeArchiveEntry()
            }
        }
        return channel.array().copyOf(channel.size().toInt())
    }

    /**
     * 다음 머리(평문 — `kHeader` 든 잠긴 머리의 StreamsInfo 든) 안의 **첫 AES 코더**의 반복 지수를 [cycles] 로 고치고 두 CRC(다음 머리·시작
     * 머리)를 다시 맞춘다. CRC 를 맞추지 않으면 라이브러리가 유도 전에 'CRC 가 다르다' 로 끝나 시험이 아무것도 보지 않는다.
     */
    private fun withCycles(archive: ByteArray, cycles: Int): ByteArray {
        val out = archive.copyOf()
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        val start = 32 + bb.getLong(12).toInt()
        val end = start + bb.getLong(20).toInt()
        // 코더 머리 바이트 0x24(id 4바이트 + 속성 있음) 뒤의 AES id.
        val marker = byteArrayOf(0x24) + Mini7z.AES
        val at = (start until end - marker.size).firstOrNull { i -> marker.indices.all { out[i + it] == marker[it] } }
            ?: error("AES 코더를 찾지 못했다")
        val props = at + marker.size + 1 // 속성 길이(1바이트 — 34)
        out[props] = ((out[props].toInt() and 0xC0) or cycles).toByte()
        bb.putInt(28, CRC32().apply { update(out, start, end - start) }.value.toInt())
        bb.putInt(8, CRC32().apply { update(out, 12, 20) }.value.toInt())
        return out
    }

    private companion object {
        /** 리더가 라이브러리에 거는 머리 사전 상한(64 MiB)과 같다. */
        const val MEMORY_KIB = 64 * 1024

        /** 우리 거절은 수십 ms 다. 라이브러리가 25 를 돌면 이 PC 에서도 몇 초다. */
        const val TIME_LIMIT_MS = 2_000L
    }
}

/**
 * 시험이 손으로 짓는 7z — 7-Zip 의 7zFormat.txt 와 commons-compress 의 `SevenZFile` 이 읽는 차례 그대로. 파일 하나(`a.txt`), 폴더 하나.
 */
internal object Mini7z {

    val AES = byteArrayOf(0x06, 0xF1.toByte(), 0x07, 0x01)
    val COPY = byteArrayOf(0x00)
    private val LZMA2 = byteArrayOf(0x21)
    private val SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val IV = ByteArray(16) { (it * 11 + 5).toByte() }

    /** 7z 의 가변 길이 수 — 7-Zip 의 `WriteNumber`. */
    fun num(v: Long): ByteArray {
        var first = 0
        var mask = 0x80
        var i = 0
        while (i < 8) {
            if (v < (1L shl (7 * (i + 1)))) {
                first = first or (v ushr (8 * i)).toInt()
                break
            }
            first = first or mask
            mask = mask ushr 1
            i++
        }
        val out = ByteArrayOutputStream()
        out.write(first)
        var rest = v
        repeat(i) {
            out.write((rest and 0xFF).toInt())
            rest = rest ushr 8
        }
        return out.toByteArray()
    }

    /** AES 코더 속성 — 반복 지수, 소금 없음, IV 16바이트(7-Zip 의 모양). */
    fun aesProps(cycles: Int): ByteArray = byteArrayOf((cycles or 0x40).toByte(), 0x0F) + IV

    /** 단순 코더 하나(입출력 하나). 속성이 있으면 붙인다. */
    fun coder(id: ByteArray, props: ByteArray? = null): ByteArray =
        if (props == null) byteArrayOf(id.size.toByte()) + id else byteArrayOf((id.size or 0x20).toByte()) + id + num(props.size.toLong()) + props

    /** 코더 하나짜리 폴더. */
    fun folder(coder: ByteArray): ByteArray = num(1) + coder

    /** 코더 둘의 사슬 — 팩 스트림 → [first] → [second] → 출력. */
    fun chain(first: ByteArray, second: ByteArray): ByteArray = num(2) + first + second + num(1) + num(0)

    /**
     * StreamsInfo — 팩 하나([packPos]·[packSize]), 폴더 하나([folder] — 출력 크기 [unpackSizes]), 하위 스트림 정보(폴더마다 하나)를
     * [withSubStreams] 면 붙인다(파일이 있는 머리에만 — 라이브러리가 파일을 폴더에 잇는 데 쓴다).
     */
    fun streamsInfo(packPos: Long, packSize: Long, folder: ByteArray, unpackSizes: List<Long>, withSubStreams: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x06)
        out.write(num(packPos))
        out.write(num(1))
        out.write(0x09)
        out.write(num(packSize))
        out.write(0x00)
        out.write(0x07)
        out.write(0x0B)
        out.write(num(1))
        out.write(0x00)
        out.write(folder)
        out.write(0x0C)
        for (s in unpackSizes) out.write(num(s))
        out.write(0x00)
        if (withSubStreams) {
            out.write(0x08)
            out.write(0x00)
        }
        out.write(0x00)
        return out.toByteArray()
    }

    /** 내용 머리(`kHeader`) — 32바이트 뒤(팩 위치 0)의 [size] 바이트를 [folder] 하나로 푸는 파일 `a.txt`. */
    fun mainHeader(size: Long, folder: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x01)
        out.write(0x04)
        out.write(streamsInfo(0, size, folder, listOf(size), withSubStreams = true))
        val name = "a.txt".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        out.write(0x05)
        out.write(num(1))
        out.write(0x11)
        out.write(num(1L + name.size))
        out.write(0x00)
        out.write(name)
        out.write(0x00)
        out.write(0x00)
        return out.toByteArray()
    }

    /** 서명 머리 + [body] + [nextHeader]. 두 CRC 를 맞춘다. */
    fun archive(body: ByteArray, nextHeader: ByteArray): ByteArray {
        val start = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(body.size.toLong()).putLong(nextHeader.size.toLong())
            .putInt(CRC32().apply { update(nextHeader) }.value.toInt()).array()
        val signature = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(SIGNATURE).put(0).put(4).putInt(CRC32().apply { update(start) }.value.toInt()).put(start).array()
        return signature + body + nextHeader
    }

    /**
     * 머리를 담은 폴더([headerFolder] — 출력 크기 [headerUnpackSizes])로 [packedHeader] 를 푸는 7z. 몸은 [content] ‖ [packedHeader] 다
     * (잠긴 머리의 팩 위치는 내용 뒤).
     */
    fun archiveWithEncodedHeader(content: ByteArray, packedHeader: ByteArray, headerFolder: ByteArray, headerUnpackSizes: List<Long>): ByteArray {
        val encoded = byteArrayOf(0x17) +
            streamsInfo(content.size.toLong(), packedHeader.size.toLong(), headerFolder, headerUnpackSizes, withSubStreams = false)
        return archive(content + packedHeader, encoded)
    }

    /** py7zr 의 모양 — 머리를 AES 하나로 잠근다(압축 없이). 열쇠는 반복 [headerCycles] 로 유도한다. */
    fun encryptedHeaderArchive(content: ByteArray, main: ByteArray, password: String, headerCycles: Int): ByteArray {
        val padded = main.copyOf((main.size + 15) / 16 * 16)
        val ivBytes = IV
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(password, headerCycles), "AES"), IvParameterSpec(ivBytes))
        val encrypted = cipher.doFinal(padded)
        return archiveWithEncodedHeader(content, encrypted, folder(coder(AES, aesProps(headerCycles))), listOf(main.size.toLong()))
    }

    /** py7zr 의 잠기지 않은 머리 — LZMA2 로 압축한다(사전 64 KiB, 속성 바이트 8). */
    fun lzma2HeaderArchive(content: ByteArray, main: ByteArray): ByteArray {
        val packed = ByteArrayOutputStream()
        val options = LZMA2Options().apply { setDictSize(1 shl 16) }
        options.getOutputStream(FinishableWrapperOutputStream(packed)).use { it.write(main) }
        return archiveWithEncodedHeader(content, packed.toByteArray(), folder(coder(LZMA2, byteArrayOf(8))), listOf(main.size.toLong()))
    }

    /** 7z 의 열쇠 — 라이브러리와 같은 꼴(반복마다 소금·암호·계수기를 따로 넘긴다). 시험의 참조 구현이다. */
    fun key(password: String, cycles: Int, salt: ByteArray = ByteArray(0)): ByteArray {
        val pw = password.toByteArray(Charsets.UTF_16LE)
        val digest = MessageDigest.getInstance("SHA-256")
        val counter = ByteArray(8)
        repeat(1 shl cycles) {
            digest.update(salt)
            digest.update(pw)
            digest.update(counter)
            for (k in 0 until 8) {
                counter[k]++
                if (counter[k].toInt() != 0) break
            }
        }
        return digest.digest()
    }
}
