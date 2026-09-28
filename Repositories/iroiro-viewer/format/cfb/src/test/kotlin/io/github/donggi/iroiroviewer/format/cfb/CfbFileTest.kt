package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CFB 리더 — 정상 구조(v3·v4·작은 스트림·DIFAT)와, CLAUDE.md '안전' 의 CFB 불변식을 하나씩 깨뜨린 파일.
 *
 * 표본은 [TinyCfb] 가 메모리에서 짠다. 그 짜개가 만든 파일이 명세에 맞는지는 12단계에 `olefile`(독립 구현)로
 * 한 번 확인했다 — v3·v4·DIFAT 표본 셋을 파일로 내보내 olefile 로 열어 스트림마다 SHA-256 을 견줬다(내보내던 시험
 * `TinyCfbOracleDump` 는 환경 변수가 있을 때만 돌고 받는 스크립트가 저장소 밖이라 지웠다 — 짜개를 고치면 다시 확인해야 한다).
 * 실제 구현이 만든 CFB 는 [CfbRealWorldTest](msoffcrypto-tool 이 잠근 docx, 한글 문서)와 `CfbCorpusTest`(olefile 목록)가 본다.
 */
class CfbFileTest {

    private fun data(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed) xor (it ushr 7)).toByte() }

    private val small = data(100, 1)
    private val inner = data(3000, 2)
    private val leaf = data(64, 3)
    private val big = data(10_000, 4)
    private val summary = data(200, 5)

    private fun sample(version: Int) = TinyCfb(version)
        .stream("small", small)
        .stream("Sub/inner", inner)
        .stream("Sub/Deeper/leaf", leaf)
        .stream("big", big)
        .stream("empty", ByteArray(0))
        .stream("\u0005SummaryInformation", summary)
        .storage("EmptyStorage")

    private fun open(source: DocumentSource, limits: CfbLimits = CfbLimits.DEFAULT) = CfbFile.open(source, limits)

    private fun readAll(input: InputStream): ByteArray = input.use { it.readBytes() }

    private fun raw(bytes: ByteArray): DocumentSource = ByteArrayDocumentSource(bytes, "x.cfb")

    private fun checkSample(version: Int) {
        open(sample(version).source()).use { cfb ->
            assertEquals(version, cfb.version)
            // 명세의 이름 순서: 짧은 것 먼저, 같은 길이면 대문자 순.
            assertEquals(
                listOf("big", "Sub", "empty", "small", "EmptyStorage", "\u0005SummaryInformation"),
                cfb.children(cfb.root).map { it.name },
            )
            // 이름은 대소문자를 가리지 않는다(명세 2.6.4).
            val innerEntry = assertNotNull(cfb.find("SUB", "Inner"))
            assertContentEquals(inner, cfb.readStream(innerEntry, 1 shl 20))
            assertContentEquals(inner, readAll(cfb.openStream(innerEntry)))
            assertContentEquals(small, cfb.readStream(cfb.find("small")!!, 1 shl 20))
            assertContentEquals(leaf, cfb.readStream(cfb.find("Sub", "Deeper", "leaf")!!, 1 shl 20))
            assertContentEquals(summary, cfb.readStream(cfb.find("\u0005SummaryInformation")!!, 1 shl 20))
            val bigEntry = cfb.find("big")!!
            assertEquals(10_000L, bigEntry.size)
            assertEquals(10_000L, cfb.streamLength(bigEntry))
            assertContentEquals(big, cfb.readStream(bigEntry, 10_000))
            assertContentEquals(big, readAll(cfb.openStream(bigEntry)))
            assertEquals(0, cfb.readStream(cfb.find("empty")!!, 0).size)
            assertTrue(cfb.children(cfb.find("EmptyStorage")!!).isEmpty())
            assertNull(cfb.find("nope"))
            assertNull(cfb.find("small", "child-of-stream"))
            // 부른 쪽의 상한을 넘으면 읽지 않는다.
            assertFailsWith<ParseLimitExceededException> { cfb.readStream(bigEntry, 9_999) }
        }
    }

    @Test
    fun v3_트리와_작은_스트림과_일반_스트림을_읽는다() = checkSample(3)

    @Test
    fun v4_트리와_작은_스트림과_일반_스트림을_읽는다() = checkSample(4)

    @Test
    fun 작은_스트림_경계는_4096_미만이다() {
        // 검토가 더한 것 — 경계를 `<=` 로 바꿔도 시험이 하나도 깨지지 않았다(표본에 4096바이트 스트림이 없었다).
        // 명세(2.6.3): 크기가 경계 **미만**이면 작은 스트림, 경계와 같으면 일반 섹터다.
        val exact = data(4096, 12)
        val below = data(4095, 13)
        for (version in listOf(3, 4)) {
            open(TinyCfb(version).stream("exact", exact).stream("below", below).source()).use { cfb ->
                assertContentEquals(exact, cfb.readStream(cfb.find("exact")!!, 1 shl 20), "v$version")
                assertContentEquals(below, cfb.readStream(cfb.find("below")!!, 1 shl 20), "v$version")
                assertContentEquals(exact, readAll(cfb.openStream(cfb.find("exact")!!)), "v$version")
            }
        }
    }

    @Test
    fun 작은_스트림_경계의_양쪽을_두_읽기_길로_같게_읽는다() {
        // 12단계 검토가 남긴 빈틈 — 경계 **바로 양쪽**(4095·4096·4097)을 통째 읽기와 흘려 읽기가 같게 읽고, 길이도 맞는가.
        // 작은 스트림 쪽은 앞에 100바이트를 두어 그릇(뿌리의 체인)의 섹터 경계를 건너게 한다 — 4095바이트는 작은 섹터
        // 64개(4096바이트)라 v3 에서 그릇의 512바이트 섹터 여덟을 지난다. 일반 쪽의 4097바이트는 섹터 하나를 1바이트 넘긴다.
        val sizes = listOf(1, 63, 64, 65, 4095, 4096, 4097)
        for (version in listOf(3, 4)) {
            val t = TinyCfb(version).stream("lead", data(100, 20))
            for (n in sizes) t.stream("s$n", data(n, n))
            open(t.source()).use { cfb ->
                for (n in sizes) {
                    val e = cfb.find("s$n")!!
                    assertEquals(n.toLong(), cfb.streamLength(e), "v$version $n")
                    assertContentEquals(data(n, n), cfb.readStream(e, 1 shl 20), "v$version $n")
                    assertContentEquals(data(n, n), readAll(cfb.openStream(e)), "v$version $n")
                }
                // 부른 쪽의 상한은 경계와 관계없이 바이트로 건다.
                assertFailsWith<ParseLimitExceededException> { cfb.readStream(cfb.find("s4095")!!, 4094) }
                assertFailsWith<ParseLimitExceededException> { cfb.readStream(cfb.find("s4096")!!, 4095) }
                assertEquals(4096, cfb.readStream(cfb.find("s4096")!!, 4096).size)
            }
        }
    }

    @Test
    fun 이어지지_않은_체인은_파일_순서가_아니라_FAT_순서로_읽는다() {
        // 세 섹터 [s, s+1, s+2] 의 체인을 s → s+2 → s+1 로 바꾸고 섹터 내용도 그에 맞게 맞바꾼다.
        // 한 번에 이어 읽기(연달아 놓인 섹터를 묶는 것)가 체인을 무시하면 여기서 틀린다.
        val payload = data(1536, 9)
        val built = TinyCfb(3).stream("s", payload + data(4096, 10)).build()
        val s = built.startSector("s")
        built.putInt(built.fatEntryOffset(s), s + 2)
        built.putInt(built.fatEntryOffset(s + 2), s + 1)
        built.putInt(built.fatEntryOffset(s + 1), s + 3)
        val a = built.sectorOffset(s + 1)
        val b = built.sectorOffset(s + 2)
        val tmp = built.bytes.copyOfRange(a, a + 512)
        built.bytes.copyInto(built.bytes, a, b, b + 512)
        tmp.copyInto(built.bytes, b)
        open(built.source()).use { cfb ->
            val got = cfb.readStream(cfb.find("s")!!, 1 shl 20)
            assertContentEquals(payload, got.copyOf(1536))
        }
    }

    @Test
    fun DIFAT_체인_너머의_FAT_섹터를_따라간다() {
        // FAT 섹터가 109개를 넘어야 DIFAT 섹터가 생기고, 그 FAT 섹터가 실제로 쓰이려면 섹터가
        // 109 × 128 개를 넘어야 한다 — 7 MB 짜리 스트림 하나.
        val huge = data(7_300_000, 11)
        val built = TinyCfb(3).stream("huge", huge).stream("tiny", small).build()
        assertTrue(built.fatSectors.size > 109, "FAT ${built.fatSectors.size}")
        assertTrue(built.difatSectors.isNotEmpty())
        open(built.source()).use { cfb ->
            assertContentEquals(huge, cfb.readStream(cfb.find("huge")!!, 8_000_000))
            assertContentEquals(small, cfb.readStream(cfb.find("tiny")!!, 1000))
        }
    }

    // ---- 머리 ------------------------------------------------------------------------

    private fun headerBroken(patch: (TinyCfb.Built) -> Unit, version: Int = 3): CfbFormatException {
        val built = sample(version).build()
        patch(built)
        return assertFailsWith<CfbFormatException> { open(built.source()).close() }
    }

    /**
     * 방어가 겹쳐 있는 자리(방문 집합 뒤의 길이 상한, 방문 검사 뒤의 종류 검사)는 **어느 겹이 잡았는지**까지
     * 본다 — 우리가 쓴 고정 문장으로. 그러지 않으면 앞 겹을 지워도 뒤 겹이 잡아 시험이 그대로 통과한다
     * (12단계의 변이 시험에서 실제로 그랬다).
     */
    private fun assertCycle(e: CfbFormatException) = assertTrue("순환" in e.message.orEmpty(), e.message)

    @Test
    fun 판과_섹터_크기가_짝이_맞지_않으면_열지_않는다() {
        headerBroken({ it.putShort(TinyCfb.H_SECTOR_SHIFT, 10) })
        headerBroken({ it.putShort(TinyCfb.H_SECTOR_SHIFT, 12) })
        headerBroken({ it.putShort(TinyCfb.H_SECTOR_SHIFT, 9) }, version = 4)
        headerBroken({ it.putShort(TinyCfb.H_MAJOR, 5) })
        headerBroken({ it.putShort(TinyCfb.H_MINI_SHIFT, 7) })
        headerBroken({ it.putInt(TinyCfb.H_CUTOFF, 8192) })
        headerBroken({ it.bytes[3] = 0 })
        headerBroken({ it.putShort(28, 0xFEFF) })
        // 그대로면 연다 — 위의 실패가 표본 탓이 아님을 확인한다.
        open(sample(3).source()).close()
    }

    @Test
    fun FAT_섹터_번호가_범위를_벗어나거나_겹치면_열지_않는다() {
        headerBroken({ it.putInt(76, 0x00FF_FFFF) })
        headerBroken({ it.putInt(TinyCfb.H_NUM_FAT, 0x0FFF_FFFF) })
        headerBroken({ it.putInt(TinyCfb.H_NUM_FAT, 0) })
        headerBroken({ b ->
            // FAT 섹터를 둘로 적고 두 칸에 같은 번호를 넣는다.
            b.putInt(TinyCfb.H_NUM_FAT, 2)
            b.putInt(80, b.fatSectors[0])
        })
    }

    @Test
    fun DIFAT_체인이_순환하거나_범위를_벗어나면_열지_않는다() {
        fun built(): TinyCfb.Built {
            val t = sample(3)
            t.extraFatSectors = 250
            return t.build().also { assertTrue(it.difatSectors.size >= 2) }
        }
        open(built().source()).close()
        val loop = built()
        val first = loop.difatSectors[0]
        loop.putInt(loop.sectorOffset(first) + 127 * 4, first)
        assertCycle(assertFailsWith<CfbFormatException> { open(loop.source()).close() })
        val wild = built()
        wild.putInt(wild.sectorOffset(wild.difatSectors[0]) + 5 * 4, 0x7FFF_FFFF)
        assertFailsWith<CfbFormatException> { open(wild.source()).close() }
        val short = built()
        short.putInt(TinyCfb.H_NUM_DIFAT, 1)
        assertFailsWith<CfbFormatException> { open(short.source()).close() }
    }

    // ---- 체인 ------------------------------------------------------------------------

    @Test
    fun FAT_체인이_순환하면_깨진_파일이다() {
        val built = sample(3).build()
        val s = built.startSector("big")
        val last = s + (10_000 + 511) / 512 - 1
        built.putInt(built.fatEntryOffset(last), s)
        // 적힌 크기가 체인보다 길어야 순환에 닿는다.
        built.putInt(built.entryOffset(built.entryId("big")) + TinyCfb.E_SIZE, 100_000)
        open(built.source()).use { cfb ->
            val e = cfb.find("big")!!
            assertCycle(assertFailsWith<CfbFormatException> { cfb.readStream(e, 1 shl 20) })
            assertCycle(assertFailsWith<CfbFormatException> { readAll(cfb.openStream(e)) })
            assertCycle(assertFailsWith<CfbFormatException> { cfb.streamLength(e) })
            // 다른 스트림은 그대로 읽힌다.
            assertContentEquals(small, cfb.readStream(cfb.find("small")!!, 1000))
        }
    }

    @Test
    fun 작은_스트림_체인이_순환하면_깨진_파일이다() {
        val built = sample(3).build()
        val s = built.startSector("Sub/inner")
        val last = s + (3000 + 63) / 64 - 1
        built.putInt(built.miniFatEntryOffset(last), s)
        built.putInt(built.entryOffset(built.entryId("Sub/inner")) + TinyCfb.E_SIZE, 4000)
        open(built.source()).use { cfb ->
            assertCycle(assertFailsWith<CfbFormatException> { cfb.readStream(cfb.find("Sub", "inner")!!, 1 shl 20) })
        }
    }

    @Test
    fun 섹터_번호가_범위를_벗어나면_읽기_전에_끊는다() {
        val built = sample(3).build()
        built.putInt(built.entryOffset(built.entryId("big")) + TinyCfb.E_START, 0x0010_0000)
        built.putInt(built.entryOffset(built.entryId("small")) + TinyCfb.E_START, 0x0010_0000)
        open(built.source()).use { cfb ->
            assertFailsWith<CfbFormatException> { cfb.readStream(cfb.find("big")!!, 1 shl 20) }
            assertFailsWith<CfbFormatException> { cfb.readStream(cfb.find("small")!!, 1 shl 20) }
        }
        // 체인 한가운데의 다음 번호가 FREESECT(범위 밖의 특수값)인 것도 같다.
        val mid = sample(3).build()
        mid.putInt(mid.fatEntryOffset(mid.startSector("big") + 3), TinyCfb.FREE_SECT)
        open(mid.source()).use { cfb ->
            assertFailsWith<CfbFormatException> { cfb.readStream(cfb.find("big")!!, 1 shl 20) }
        }
        headerBroken({ it.putInt(TinyCfb.H_FIRST_DIR, 0x0010_0000) })
        headerBroken({ it.putInt(TinyCfb.H_FIRST_DIR, TinyCfb.END_OF_CHAIN) })
    }

    @Test
    fun 적힌_크기가_터무니없어도_체인만큼만_읽고_그만큼만_잡는다() {
        val built = sample(4).build()
        val at = built.entryOffset(built.entryId("big")) + TinyCfb.E_SIZE
        built.putLong(at, 1L shl 40)
        open(built.source()).use { cfb ->
            val e = cfb.find("big")!!
            assertEquals(1L shl 40, e.size)
            // 체인은 4096바이트 섹터 셋 — min(적힌 크기, 체인 길이 × 섹터 크기).
            assertEquals(3 * 4096L, cfb.streamLength(e))
            val got = cfb.readStream(e, 1 shl 20)
            assertEquals(3 * 4096, got.size)
            assertContentEquals(big, got.copyOf(10_000))
            assertEquals(3 * 4096, readAll(cfb.openStream(e)).size)
            assertFailsWith<ParseLimitExceededException> { cfb.readStream(e, 5_000) }
        }
    }

    @Test
    fun v3_는_스트림_크기의_위_32비트를_무시한다() {
        val built = sample(3).build()
        built.putInt(built.entryOffset(built.entryId("big")) + TinyCfb.E_SIZE + 4, -1)
        open(built.source()).use { cfb ->
            val e = cfb.find("big")!!
            assertEquals(10_000L, e.size)
            assertContentEquals(big, cfb.readStream(e, 10_000))
        }
    }

    @Test
    fun 잘린_파일() {
        val full = sample(3).build().bytes
        assertFailsWith<CfbFormatException> { open(raw(full.copyOf(300))).close() }
        assertFailsWith<CfbFormatException> { open(raw(full.copyOf(512))).close() }
        // 뒤쪽(일반 스트림)만 잘렸으면 열리고, 그 스트림을 읽을 때 끊긴다.
        val cut = full.copyOf(full.size - 512 * 5)
        open(raw(cut)).use { cfb ->
            assertContentEquals(small, cfb.readStream(cfb.find("small")!!, 1000))
            assertFailsWith<CfbFormatException> { cfb.readStream(cfb.find("big")!!, 1 shl 20) }
        }
    }

    // ---- 디렉터리 ------------------------------------------------------------------------

    @Test
    fun 디렉터리_트리가_순환하면_열지_않는다() {
        assertCycle(
            headerBroken({ b ->
                val id = b.entryId("small")
                b.putInt(b.entryOffset(id) + TinyCfb.E_LEFT, id)
            }),
        )
        assertCycle(
            headerBroken({ b ->
                // 저장소의 자식 트리가 뿌리를 가리킨다.
                b.putInt(b.entryOffset(b.entryId("Sub/Deeper")) + TinyCfb.E_CHILD, 0)
            }),
        )
        assertCycle(
            headerBroken({ b ->
                // 두 부모 — Deeper 의 자식이 Sub 의 자식(inner)을 함께 가리킨다.
                b.putInt(b.entryOffset(b.entryId("Sub/Deeper")) + TinyCfb.E_CHILD, b.entryId("Sub/inner"))
            }),
        )
    }

    @Test
    fun 디렉터리_번호가_범위를_벗어나거나_빈_칸을_가리키면_열지_않는다() {
        headerBroken({ b -> b.putInt(b.entryOffset(b.entryId("Sub")) + TinyCfb.E_CHILD, 9_999) })
        headerBroken({ b ->
            // 디렉터리의 마지막 칸은 빈 칸이다(항목 10개, 칸 12개).
            b.putInt(b.entryOffset(b.entryId("Sub/Deeper")) + TinyCfb.E_CHILD, 11)
        })
        headerBroken({ b -> b.bytes[b.entryOffset(0) + TinyCfb.E_TYPE] = 1 })
    }

    @Test
    fun 이름이_망가진_항목은_목록에서_빼고_형제는_남긴다() {
        fun withName(patch: (TinyCfb.Built, Int) -> Unit) {
            val built = sample(3).build()
            val id = built.entryId("small")
            patch(built, built.entryOffset(id))
            open(built.source()).use { cfb ->
                assertNull(cfb.find("small"))
                assertEquals(5, cfb.children(cfb.root).size)
                assertContentEquals(big, cfb.readStream(cfb.find("big")!!, 1 shl 20))
                assertContentEquals(inner, cfb.readStream(cfb.find("Sub", "inner")!!, 1 shl 20))
            }
        }
        // 끝에 NUL 이 없다 — 64바이트를 전부 글자로 채우고 길이를 64로.
        withName { b, o ->
            for (k in 0 until 32) b.putShort(o + k * 2, 'A'.code)
            b.putShort(o + TinyCfb.E_NAME_LEN, 64)
        }
        withName { b, o -> b.putShort(o + TinyCfb.E_NAME_LEN, 66) }
        withName { b, o -> b.putShort(o + TinyCfb.E_NAME_LEN, 5) }
        withName { b, o -> b.putShort(o + TinyCfb.E_NAME_LEN, 0) }
        // 중간에 NUL.
        withName { b, o -> b.putShort(o + 2, 0) }
    }

    @Test
    fun 항목_수_상한을_넘으면_너무_크다() {
        val source = sample(3).source()
        assertFailsWith<ParseLimitExceededException> { open(source, CfbLimits(maxEntries = 4)).close() }
        open(source, CfbLimits(maxEntries = 12)).close()
    }

    @Test
    fun 파일_크기_상한을_넘으면_너무_크다() {
        assertFailsWith<ParseLimitExceededException> { open(sample(3).source(), CfbLimits(maxFileBytes = 1000)).close() }
    }

    // ---- 자원 ----------------------------------------------------------------------

    @Test
    fun 무작위_접근이_없는_원본은_깨진_파일로_끝나고_채널은_실패하면_닫힌다() {
        val streamOnly = object : DocumentSource {
            override val displayName = "x.cfb"
            override val length = -1L
            override fun openStream(): InputStream = sample(3).build().bytes.inputStream()
        }
        assertFailsWith<CfbFormatException> { open(streamOnly) }
        val channel = io.github.donggi.iroiroviewer.format.ByteArrayChannel(ByteArray(600))
        assertFailsWith<CfbFormatException> { CfbFile.open(channel) }
        assertTrue(!channel.isOpen)
    }

    @Test
    fun 닫은_뒤에는_읽지_않는다() {
        val cfb = open(sample(3).source())
        val e = cfb.find("big")!!
        cfb.close()
        cfb.close()
        assertFailsWith<IOException> { cfb.readStream(e, 1 shl 20) }
    }

    @Test
    fun 다른_파일의_항목은_받지_않는다() {
        open(sample(3).source()).use { a ->
            open(sample(3).source()).use { b ->
                assertFailsWith<IllegalArgumentException> { a.readStream(b.find("big")!!, 1 shl 20) }
            }
        }
    }
}
