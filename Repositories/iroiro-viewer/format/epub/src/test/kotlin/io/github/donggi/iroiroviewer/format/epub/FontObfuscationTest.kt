package io.github.donggi.iroiroviewer.format.epub

import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 글꼴 난독화 풀기.
 *
 * ## 오라클이 우리가 아니다
 *
 * * **열쇠**: 기대값을 파이썬 `hashlib` 으로 따로 구해 적었다(IDPF 는 SHA-1, Adobe 는 16진 해석).
 * * **푼 결과**: Readium(오픈소스 EPUB 엔진)이 만든 난독화 글꼴과 원본을 견준다 — 우리가
 *   잠그고 우리가 푸는 왕복은 명세를 같이 잘못 읽으면 통과한다. 그 표본은 제3자의 글꼴이라
 *   커밋하지 않는다(`samples-local/epub-deobfuscation/`, 없으면 건너뛴다). 표본은
 *   `readium/kotlin-toolkit` 의 `streamer/src/test/resources/.../deobfuscation/` 에 있고 식별자는
 *   `urn:uuid:36d5078e-ff7d-468e-a5f3-f47c14b91f2f` 다.
 */
class FontObfuscationTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val uuid = "urn:uuid:36d5078e-ff7d-468e-a5f3-f47c14b91f2f"

    @Test
    fun IDPF_열쇠는_공백을_뺀_식별자의_SHA1_이다() {
        // 기대값은 파이썬 hashlib.sha1 로 구했다.
        assertContentEquals(hex("d2855e2244f7ae733ab0d7bbadf7f1c2a44a939b"), FontObfuscation.idpfKey(uuid))
        // 명세의 공백 넷(U+0020·0009·000D·000A)을 뺀다.
        assertContentEquals(hex("d2855e2244f7ae733ab0d7bbadf7f1c2a44a939b"), FontObfuscation.idpfKey(" $uuid\n"))
        // 한글은 UTF-8 로.
        assertContentEquals(
            hex("b5db41a16b24ab3809e9a5fd9dbc59b6514d9aa1"),
            FontObfuscation.idpfKey("isbn:978-89-01-00000-0\t책"),
        )
        // 비면 열쇠가 없다 — 빈 문자열의 SHA-1 로 푸는 것은 추측이다.
        assertNull(FontObfuscation.idpfKey(" \t\r\n"))
    }

    @Test
    fun Adobe_열쇠는_UUID_의_16바이트다() {
        assertContentEquals(hex("36d5078eff7d468ea5f3f47c14b91f2f"), FontObfuscation.adobeKey(uuid))
        assertContentEquals(hex("36d5078eff7d468ea5f3f47c14b91f2f"), FontObfuscation.adobeKey("URN:UUID:36D5078E-FF7D-468E-A5F3-F47C14B91F2F"))
        assertNull(FontObfuscation.adobeKey("isbn:9788901000000"))
        assertNull(FontObfuscation.adobeKey("urn:uuid:36d5078e"))
    }

    @Test
    fun Adobe_는_고유_식별자가_UUID_가_아니면_다른_식별자에서_찾는다() {
        val mask = FontObfuscation.maskFor(FontObfuscation.ADOBE, "isbn:9788901000000", listOf("isbn:9788901000000", uuid))
        assertNotNull(mask)
        assertNull(FontObfuscation.maskFor(FontObfuscation.IDPF, null, listOf(uuid)), "IDPF 는 고유 식별자만 쓴다")
    }

    /** 원본 3000 바이트를 [key] 로 [n] 바이트까지 XOR 한 것. 이 시험 안의 독립 구현이다. */
    private fun obfuscate(plain: ByteArray, key: ByteArray, n: Int) =
        plain.copyOf().also { for (i in 0 until minOf(n, it.size)) it[i] = (it[i].toInt() xor key[i % key.size].toInt()).toByte() }

    private val plain = ByteArray(3000) { (it * 31 + 7).toByte() }

    @Test
    fun 범위_뒤는_그대로다() {
        val key = hex("d2855e2244f7ae733ab0d7bbadf7f1c2a44a939b")
        val obf = obfuscate(plain, key, FontObfuscation.IDPF_LENGTH)
        val mask = FontObfuscation.maskFor(FontObfuscation.IDPF, uuid, emptyList())!!
        val out = FontObfuscation.wrap(ByteArrayInputStream(obf), mask).use { it.readBytes() }
        assertContentEquals(plain, out)
    }

    @Test
    fun 조금씩_읽어도_건너뛰어도_같다() {
        // 화면(WebView 에 자원을 내주는 쪽)은 버퍼 크기를 우리가 정하지 않는다. 자리를 잘못 세면
        // 조각 경계에서 엉뚱한 열쇠 바이트가 걸린다.
        val key = hex("36d5078eff7d468ea5f3f47c14b91f2f")
        val obf = obfuscate(plain, key, FontObfuscation.ADOBE_LENGTH)
        val mask = FontObfuscation.maskFor(FontObfuscation.ADOBE, uuid, emptyList())!!
        for (chunk in listOf(1, 7, 19, 1023, 1025, 4096)) {
            val out = java.io.ByteArrayOutputStream()
            FontObfuscation.wrap(ByteArrayInputStream(obf), mask).use { s ->
                val buf = ByteArray(chunk)
                while (true) {
                    val n = s.read(buf, 0, chunk)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            assertContentEquals(plain, out.toByteArray(), "조각 $chunk")
        }
        // 1000 바이트를 건너뛰고 읽으면 1000 번째부터 풀린 값이다.
        val tail = FontObfuscation.wrap(ByteArrayInputStream(obf), mask).use { s ->
            var skipped = 0L
            while (skipped < 1000) skipped += s.skip(1000 - skipped)
            s.readBytes()
        }
        assertContentEquals(plain.copyOfRange(1000, plain.size), tail)
    }

    // ---- Readium 표본(제3자의 오라클) ------------------------------------------------

    private val local = File(System.getProperty("user.dir")).let { dir ->
        // 모듈 폴더에서 돌므로 저장소 뿌리는 두 단계 위다.
        generateSequence(dir) { it.parentFile }.map { File(it, "samples-local/epub-deobfuscation") }
            .firstOrNull { it.isDirectory }
    }

    private fun sample(name: String): ByteArray? = local?.let { File(it, name) }?.takeIf { it.isFile }?.readBytes()

    @Test
    fun Readium_이_만든_IDPF_글꼴이_풀린다() {
        val font = sample("cut-cut.woff")
        val obf = sample("cut-cut.obf.woff")
        assumeTrue("samples-local/epub-deobfuscation 가 없다", font != null && obf != null)
        val mask = FontObfuscation.maskFor(FontObfuscation.IDPF, uuid, emptyList())!!
        assertContentEquals(font, FontObfuscation.wrap(ByteArrayInputStream(obf!!), mask).use { it.readBytes() })
    }

    @Test
    fun Readium_이_만든_Adobe_글꼴이_풀린다() {
        val font = sample("cut-cut.woff")
        val adb = sample("cut-cut.adb.woff")
        assumeTrue("samples-local/epub-deobfuscation 가 없다", font != null && adb != null)
        val mask = FontObfuscation.maskFor(FontObfuscation.ADOBE, uuid, emptyList())!!
        assertContentEquals(font, FontObfuscation.wrap(ByteArrayInputStream(adb!!), mask).use { it.readBytes() })
    }
}
