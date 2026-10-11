package io.github.donggi.iroirobrowser.core.sites

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class JsonTest {

    private val site = Json.SITE

    private fun parse(text: String): Any? = Json.parse(text, site)

    @Test
    fun 기본_값() {
        val v = parse(""" { "a" : 1 , "b" : [true, false, null, "글", -2.5e3, 0], "c": {} , "d": [] } """)
        val map = assertIs<Map<*, *>>(v)
        assertEquals(listOf("a", "b", "c", "d"), map.keys.toList())
        assertEquals(1L, map["a"])
        assertEquals(listOf(true, false, null, "글", -2500.0, 0L), map["b"])
        assertEquals(emptyMap<String, Any?>(), map["c"])
        assertEquals(emptyList<Any?>(), map["d"])
        assertEquals("x", parse("\"x\""))
        assertNull(parse("null"))
        assertEquals(42L, parse("\uFEFF 42 "))
    }

    @Test
    fun 숫자() {
        assertEquals(Long.MAX_VALUE, parse("9223372036854775807"))
        assertEquals(Long.MIN_VALUE, parse("-9223372036854775808"))
        // Long 을 넘는 정수는 Double 로.
        assertEquals(9.223372036854775808E18, parse("9223372036854775808"))
        assertEquals(0L, parse("-0"))
        assertEquals(1.5, parse("1.5"))
        assertEquals(100.0, parse("1E2"))
        assertEquals(0.01, parse("1e-2"))
        for (bad in listOf("01", "1.", ".5", "+1", "1e", "1e+", "--1", "0x10", "NaN", "Infinity", "-", "1.e3", "1e999")) {
            assertFailsWith<JsonException>(bad) { parse(bad) }
        }
        val long = "1" + "0".repeat(70)
        assertTrue(assertFailsWith<JsonException> { parse(long) }.tooLarge)
    }

    @Test
    fun 글과_이스케이프() {
        assertEquals("a\"b\\c/d\b\u000C\n\r\t", parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\""))
        assertEquals("한글 \uD83D\uDE00", parse("\"\\ud55c\\uae00 \\uD83D\\uDE00\""))
        // 홀로 선 서로게이트도 받는다(이름으로 쓸 때 SiteNames 가 고친다).
        assertEquals("\uD800x", parse("\"\\ud800x\""))
        for (bad in listOf("\"abc", "\"a\\q\"", "\"\\u12\"", "\"\\u12g4\"", "\"a\nb\"", "\"tab\there\"", "\"\\")) {
            assertFailsWith<JsonException>(bad) { parse(bad) }
        }
    }

    @Test
    fun 엄격한_문법() {
        val bad = listOf(
            "", " ", "[1,]", "{\"a\":1,}", "[1 2]", "{\"a\" 1}", "{a:1}", "{'a':1}", "['a']", "// c\n1", "/* c */1", "[1]x",
            "{\"a\":1}}", "[", "]", "{", "tru", "nul", "True", "[,1]", "{,}", "{\"a\":}", "1 2",
        )
        for (text in bad) {
            val e = assertFailsWith<JsonException>(text) { parse(text) }
            assertFalse(e.tooLarge, text)
        }
    }

    @Test
    fun 같은_키는_뒤의_것() {
        assertEquals(mapOf("a" to 2L), parse("""{"a":1,"a":2}"""))
    }

    @Test
    fun 깊이_상한() {
        val ok = "[".repeat(64) + "]".repeat(64)
        Json.parse(ok, site)
        val deep = "[".repeat(65) + "]".repeat(65)
        val e = assertFailsWith<JsonException> { Json.parse(deep, site) }
        assertFalse(e.tooLarge)
        // 아주 깊어도 스택이 넘치기 전에 멈춘다.
        assertFailsWith<JsonException> { Json.parse("[".repeat(200_000), site) }
        assertFailsWith<JsonException> { Json.parse("{\"a\":".repeat(100_000), site) }
    }

    @Test
    fun 크기_상한() {
        val small = Json.Limits(maxChars = 100, maxDepth = 8, maxStringChars = 10, maxValues = 5)
        assertTrue(assertFailsWith<JsonException> { Json.parse("[" + "1,".repeat(60) + "1]", small) }.tooLarge)
        assertTrue(assertFailsWith<JsonException> { Json.parse("[1,2,3,4,5,6]", small) }.tooLarge)
        // 키도 값으로 센다.
        assertTrue(assertFailsWith<JsonException> { Json.parse("""{"a":1,"b":2,"c":3}""", small) }.tooLarge)
        assertTrue(assertFailsWith<JsonException> { Json.parse("\"12345678901\"", small) }.tooLarge)
        assertTrue(assertFailsWith<JsonException> { Json.parse("\"1234567890\\n\"", small) }.tooLarge)
        assertEquals("1234567890", Json.parse("\"1234567890\"", small))
        assertEquals(listOf(1L, 2L, 3L, 4L), Json.parse("[1,2,3,4]", small))
    }

    @Test
    fun 앞자리부터_값_하나() {
        val text = "var x = {\"a\":[1,2]} ;  "
        val (value, end) = Json.parsePrefix(text, 8, site)
        assertEquals(mapOf("a" to listOf(1L, 2L)), value)
        assertEquals(" ;  ", text.substring(end))
    }

    @Test
    fun 쓰기의_이스케이프() {
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\u0001\\u001f\"", Json.write("a\"b\\c\n\r\t\u0001\u001F"))
        assertEquals("\"\uD83D\uDE00\"", Json.write("\uD83D\uDE00"))
        // 홀로 선 서로게이트는 이스케이프로 — UTF-8 로 써도 왕복이 깨지지 않게.
        assertEquals("\"\\ud800x\\udc00\"", Json.write("\uD800x\uDC00"))
        assertEquals("\uD800x\uDC00", parse(Json.write("\uD800x\uDC00")))
        assertEquals("""{"a":[1,true,null,"x"],"b":{}}""", Json.write(linkedMapOf("a" to listOf(1L, true, null, "x"), "b" to emptyMap<String, Any>())))
        assertFailsWith<IllegalArgumentException> { Json.write(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Json.write(mapOf(1 to 2)) }
        assertFailsWith<IllegalArgumentException> { Json.write(Any()) }
    }

    /** 무작위 나무 → 글 → 나무 가 같은가(고정 씨앗 — 실패하면 같은 판이 다시 나온다). */
    @Test
    fun 왕복_속성() {
        val random = Random(20261011)
        repeat(2_000) { round ->
            val tree = randomTree(random, 0)
            val text = Json.write(tree)
            assertEquals(tree, Json.parse(text, site), "판 $round: $text")
        }
    }

    private fun randomTree(random: Random, depth: Int): Any? {
        val pick = if (depth > 5) random.nextInt(5) else random.nextInt(7)
        return when (pick) {
            0 -> null
            1 -> random.nextBoolean()
            2 -> random.nextLong()
            3 -> randomDouble(random)
            4 -> randomString(random)
            5 -> List(random.nextInt(5)) { randomTree(random, depth + 1) }
            else -> LinkedHashMap<String, Any?>().also { map ->
                repeat(random.nextInt(5)) { map[randomString(random)] = randomTree(random, depth + 1) }
            }
        }
    }

    private fun randomDouble(random: Random): Double {
        while (true) {
            val d = Double.fromBits(random.nextLong())
            // 정수인 Double 은 읽을 때 Long 이 될 수 있다(`1.0E10` 은 아니지만 우리 쓰기가 `toString` 이라) — 소수만.
            if (!d.isNaN() && !d.isInfinite() && d != Math.rint(d)) return d
        }
    }

    private fun randomString(random: Random): String {
        val pool = "ab가\"\\/\n\t\u0000\u001f\u007f\uFEFF\uD83D\uDE00\uD800é"
        return buildString { repeat(random.nextInt(8)) { append(pool[random.nextInt(pool.length)]) } }
    }

    /**
     * 퍼저 — 올바른 글을 바이트 단위로 뒤틀어(지우기·끼우기·바꾸기·잘라 내기·되풀이) 읽힌다. 값을 내거나 [JsonException] 이어야 하고, 그 밖의
     * 예외(인덱스·숫자 변환·스택 넘침)는 하나도 없어야 한다. 고정 씨앗 3만 판.
     */
    @Test
    fun 퍼저() {
        val random = Random(77_001)
        val seeds = listOf(
            """{"error":false,"message":"","body":{"illustId":"123","urls":{"original":"https://i.pximg.net/a_p0.png"},"pageCount":2}}""",
            """[{"hash":"0a1b","hasavif":1,"name":"001.jpg"},{"x":[1,2.5e-3,-0,true,null]}]""",
            "\"\\u00e9\\uD83D\\uDE00\\n\"",
            """{"a":{"b":{"c":[[[]]]}},"d":"\u0000"}""",
        )
        val small = Json.Limits(maxChars = 10_000, maxDepth = 16, maxStringChars = 500, maxValues = 2_000)
        var accepted = 0
        repeat(30_000) { round ->
            val text = mutate(seeds[round % seeds.size], random)
            try {
                Json.parse(text, small)
                accepted++
            } catch (e: JsonException) {
                // 받지 않은 것 — 우리 예외면 된다.
            } catch (e: Throwable) {
                fail("판 $round 에서 ${e.javaClass.name}: ${e.message} — 글: $text")
            }
        }
        assertTrue(accepted > 0, "뒤튼 글이 하나도 읽히지 않았다 — 퍼저가 값을 내는 길을 지나지 않는다")
    }

    private fun mutate(seed: String, random: Random): String {
        val s = StringBuilder(seed)
        val alphabet = "{}[]\":,\\/ 0123456789-+.eEtruefalsn\u0000\n\uD800\uDC00가u"
        repeat(1 + random.nextInt(4)) {
            when (random.nextInt(5)) {
                0 -> if (s.isNotEmpty()) s.deleteCharAt(random.nextInt(s.length))
                1 -> s.insert(random.nextInt(s.length + 1), alphabet[random.nextInt(alphabet.length)])
                2 -> if (s.isNotEmpty()) s.setCharAt(random.nextInt(s.length), alphabet[random.nextInt(alphabet.length)])
                3 -> if (s.isNotEmpty()) s.delete(random.nextInt(s.length), s.length)
                else -> if (s.isNotEmpty()) {
                    val from = random.nextInt(s.length)
                    val to = minOf(s.length, from + random.nextInt(20))
                    s.insert(from, s.substring(from, to).repeat(1 + random.nextInt(5)))
                }
            }
        }
        return s.toString()
    }
}
