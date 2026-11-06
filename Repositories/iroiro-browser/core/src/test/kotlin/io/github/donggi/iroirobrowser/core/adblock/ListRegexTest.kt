package io.github.donggi.iroirobrowser.core.adblock

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 목록의 정규식을 맞추는 엔진([ListRegex] — 2026-10-16 보안 점검, ReDoS). 뜻은 java.util.regex(이 JVM 의 것)와 같아야 하고, 시간은 주소 길이에
 * 비례해야 하며, 예산을 넘으면 멈춰야 한다.
 */
class ListRegexTest {

    private fun ours(pattern: String, input: String, ignoreCase: Boolean = false): Boolean =
        ListRegex.compileOrThrow(pattern, ignoreCase).find(input, Long.MAX_VALUE) == ListRegex.Result.MATCH

    private fun java(pattern: String, input: String, ignoreCase: Boolean = false): Boolean =
        Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(input)

    private fun same(pattern: String, vararg inputs: String, ignoreCase: Boolean = false) {
        for (input in inputs) {
            assertEquals(java(pattern, input, ignoreCase), ours(pattern, input, ignoreCase), "/$pattern/ ← ${show(input)} ignoreCase=$ignoreCase")
        }
    }

    private fun show(s: String) = s.map { if (it.code in 32..126) it.toString() else "\\x" + it.code.toString(16) }.joinToString("")

    private companion object {
        /** 동시에 살아 있는 실이 많은 넓은 무늬 — 한 글자에 수백 걸음이고, 되짚는 엔진에서는 입력 끝에서 아무것도 읽지 않고 2^40 갈래를 돈다. */
        val WIDE = "^.*" + "(?:a?|b?)".repeat(40) + "y"

        /** 무작위로 견줄 무늬의 수(무늬마다 입력 25개, 대소문자 무시는 넷에 하나). */
        const val PATTERNS = 8000

        /** 기준이 입력 하나에 읽을 글자의 상한 — 보통 무늬는 수백이다. */
        const val JAVA_READS = 200_000
    }

    private val nel = Char(0x85).toString()
    private val ls = Char(0x2028).toString()
    private val ps = Char(0x2029).toString()

    @Test
    fun 목록이_쓰는_꼴은_java_util_regex_와_같은_답() {
        same("""^https?:\/\/[a-z]{8,15}\.(com|net)\/$""", "https://abcdefgh.com/", "http://abcdefghijklmnopq.com/", "https://abcdefgh.com/x", "https://abc.com/")
        same("""(https?:\/\/)104\.154\..{10,}""", "http://104.154.1234567890", "http://104.154.123", "x https://104.154.aaaaaaaaaaaaa")
        same("""^https:\/\/[a-z]\.pussyspace\.(?:com|net)\/(?:yip?|xvs)\/videos\/20\d{4}\/\d{2}\/\d{9}\/(?:original|thumbs_\d{2})\/\d{0,2}(?:\(m=e[0-9A-Za-z]{5,7}(?:aaaa)?\)\(mh=[-_0-9A-Za-z]{16}\))?\d{0,2}\.jpg$""",
            "https://a.pussyspace.com/yip/videos/202601/02/123456789/original/1(m=eabcdefaaaa)(mh=abcdefghij_-1234)2.jpg",
            "https://a.pussyspace.com/yip/videos/202601/02/123456789/original/12.jpg",
            "https://a.pussyspace.com/yip/videos/202601/02/12345678/original/12.jpg")
        same("""\w{5,}\.me\/\w{5,}\.""", "https://abcdef.me/ghijkl.js", "https://abc.me/ghijkl.js")
        same("""/ads[0-9]+\.js""", "https://x.test/ads12.js", "https://x.test/adsx.js")
        same("""[^a-z0-9]ad[sv]?[-_.]""", "https://x.test/?ad_1", "https://x.test/head.js", "https://x.test/ADV.", ignoreCase = true)
        same("""^https?:\/\/(?:[a-z]+\.)+(?:club|xyz|top)\/[a-z0-9]{10}$""", "https://a.b.club/abcdefghij", "https://a.b.club/abcdefghi", "https://.club/abcdefghij")
    }

    @Test
    fun java_util_regex_의_뜻을_따르는_자리() {
        // `$` — 끝, 또는 끝에 붙은 줄 바꿈 하나 앞(`\r\n` 의 가운데는 아니다).
        same("a$", "a", "a\n", "a\r\n", "a\r", "a$nel", "a$ls", "a$ps", "a\n\n", "a\r\r", "ab", "\na")
        same("a\r$", "a\r\n")
        // `.` 은 줄 바꿈이 아닌 모든 글자.
        same("^.$", "\n", "\r", nel, ls, ps, Char(11).toString(), Char(12).toString(), "x", " ", Char(0xe9).toString())
        // `\b` — ASCII 의 `\w` 로(이 JDK 의 java.util.regex 와 같다).
        same("""\bx""", "x", "ax", " x", Char(0xe9) + "x", "_x")
        same("""x\B""", "xa", "x ", "x")
        // 모둠 안의 `-` — 범위 뒤나 모둠 뒤의 것은 글자다.
        same("[a-z-0]", "-", "0", "q", ".", "/", "!", "+")
        same("[a-z--0]", "-", ".", "/", "0", "!", "+")
        same("""[\w-.]""", "-", ".", "a", "/", "!", ",", "?")
        same("""[\d-z]""", "-", "z", "5", "q", "!", "+")
        same("""[\s-\d]""", "-", " ", "5", "!", "+")
        same("[--0]", "-", ".", "0", "1")
        same("[%--]", "%", "&", "-", ".")
        same("[^-]", "-", "a")
        same("""[a\-z]""", "-", "b", "z")
        same("[---]", "-", "+")
        // 대소문자 무시 — 접은 뒤에 뒤집는다.
        for (p in listOf("[^a]", "[A-Z]", "[^A-Z]", """\W""", """[^\W]""", "[Z-a]", "K", "[^k]", """\bA""")) {
            same(p, "a", "A", "z", "Z", "_", "k", "K", "1", ignoreCase = true)
        }
        // 빈 것들.
        same("a|", "b")
        same("|", "b", "")
        same("(|a)b", "b", "ab", "c")
        same("()", "x", "")
        same("(?:)a", "a", "b")
        same("a{0}b", "b", "ab")
        same("a{0,0}", "")
        // 게으른 되풀이는 맞는지가 같다.
        same("a*?b", "aab", "aa")
        same("a{2,}?c", "aac", "ac")
        // 역슬래시 꼴.
        same("""\x41B""", "AB", "ab")
        same("""\t\n\r\f""", "\t\n\r" + Char(12))
        same("""\/\-\_\.\ \#""", "/-_. #")
        same("""[\x61-\x63]""", "b", "d")
        same("""[\d\s]""", "5", " ", "a", Char(11).toString())
        same("""\s""", Char(11).toString(), Char(0x1c).toString(), Char(0xa0).toString())
        same("a{01}", "a")
        same("]}", "]}")
    }

    @Test
    fun 무작위_무늬와_주소로_java_util_regex_와_견준다() {
        val r = Random(20261016)
        var compared = 0
        var rejected = 0
        var tooSlow = 0
        repeat(PATTERNS) {
            val pattern = pattern(r, 0)
            val ignoreCase = r.nextInt(4) == 0
            val ourRegex = ListRegex.compile(pattern, ignoreCase)
            if (ourRegex == null) {
                rejected++
                return@repeat
            }
            val javaRegex = try {
                Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
            } catch (e: IllegalArgumentException) {
                throw AssertionError("우리는 받는데 java.util.regex 는 받지 않는다: /$pattern/", e)
            }
            repeat(25) {
                val input = input(r)
                // 기준(java.util.regex)이 되짚다 터지는 무늬는 견주지 않는다 — 그 시험 자체가 멈췄다(2026-10-16, 이 PC 에서 10분).
                val expected = try {
                    javaRegex.containsMatchIn(Bounded(input, JAVA_READS))
                } catch (e: TooManyReads) {
                    tooSlow++
                    return@repeat
                }
                val got = ourRegex.find(input, Long.MAX_VALUE) == ListRegex.Result.MATCH
                assertEquals(expected, got, "/$pattern/ ← ${show(input)} ignoreCase=$ignoreCase")
                compared++
            }
        }
        // 생성기는 거의 받는 꼴만 짓는다(받지 않는 것 — 단언 하나뿐인 묶음의 되풀이, 빈 글에 맞는 묶음의 {2,}) — 거의 다 받아야 견준 것이 뜻이 있다.
        assertTrue(rejected * 10 < PATTERNS, "받지 않은 무늬가 너무 많다: $rejected")
        assertTrue(tooSlow * 100 < compared, "java.util.regex 가 터진 것이 너무 많다: $tooSlow")
        assertTrue(compared > PATTERNS * 25 * 8 / 10)
    }

    /**
     * 읽은 글자를 세어 [limit] 을 넘으면 던지는 입력 — 이 JVM 의 java.util.regex(시험의 기준)는 `charAt` 으로 읽으므로 그 되짚기를 묶는다. 안드로이드
     * 의 java.util.regex 는 ICU 에 글로 넘겨 이것이 듣지 않는다(그래서 엔진을 따로 지었다 — [ListRegex]).
     */
    private class Bounded(private val text: String, private val limit: Int) : CharSequence {
        private var reads = 0

        override val length: Int get() = text.length

        override fun get(index: Int): Char {
            if (++reads > limit) throw TooManyReads()
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

        override fun toString(): String = text
    }

    private class TooManyReads : RuntimeException()

    /** 받는 문법 안의 무작위 무늬 — 깊이 2 까지의 묶음, 되풀이(게으른 것·수 붙은 것), 모둠, 단언, 빈 갈래. */
    private fun pattern(r: Random, depth: Int): String {
        val n = r.nextInt(5) // 0 이면 빈 갈래
        return buildString { repeat(n) { append(item(r, depth)) } }
    }

    private fun item(r: Random, depth: Int): String {
        val choice = r.nextInt(if (depth < 2) 12 else 9)
        val atom = when (choice) {
            0, 1, 2 -> literal(r)
            3 -> "."
            4, 5 -> charClass(r)
            6 -> listOf("""\d""", """\w""", """\s""", """\D""", """\W""", """\S""").random(r)
            7, 8 -> return listOf("^", "$", """\b""", """\B""").random(r) // 단언은 되풀이하지 않는다
            else -> "(" + (if (r.nextBoolean()) "?:" else "") + (1..(1 + r.nextInt(3))).joinToString("|") { pattern(r, depth + 1) } + ")"
        }
        return atom + quantifier(r)
    }

    private fun quantifier(r: Random): String = when (r.nextInt(14)) {
        0 -> "*"
        1 -> "+"
        2 -> "?"
        3 -> "{${r.nextInt(3)}}"
        4 -> "{${r.nextInt(3)},}"
        5 -> r.nextInt(3).let { a -> "{$a,${a + r.nextInt(3)}}" }
        6 -> "*?"
        7 -> "+?"
        8 -> "??"
        else -> ""
    }

    private fun literal(r: Random): String = when (r.nextInt(12)) {
        0 -> """\."""
        1 -> """\/"""
        2 -> """\-"""
        3 -> """\x61"""
        4 -> """b"""
        5 -> "/"
        6 -> "-"
        else -> "abcAB_1%".random(r).toString()
    }

    private fun charClass(r: Random): String = buildString {
        append('[')
        if (r.nextInt(3) == 0) append('^')
        val n = 1 + r.nextInt(3)
        repeat(n) {
            append(
                when (r.nextInt(12)) {
                    0 -> "a-c"
                    1 -> "A-C"
                    2 -> "0-9"
                    3 -> """\d"""
                    4 -> """\w"""
                    5 -> """\s"""
                    6 -> """\-"""
                    7 -> """\."""
                    8 -> """\W"""
                    9 -> "-" // 맨 `-` — 범위가 되거나(앞이 글자 하나면) 글자가 된다(모둠·범위 뒤면)
                    else -> "abcAB_1/.%!+ ".random(r).toString()
                },
            )
        }
        if (r.nextInt(5) == 0) append('-')
        append(']')
    }

    private val alphabet = "abcAB/.-_1%!+ \n\r" + Char(0x85) + Char(0x2028)

    private fun input(r: Random): String {
        val n = r.nextInt(13)
        return buildString { repeat(n) { append(alphabet[r.nextInt(alphabet.length)]) } }
    }

    @Test
    fun 받지_않는_꼴은_건너뛴다() {
        val unsupported = listOf(
            "a(?=b)", "a(?!b)", "(?<=a)b", "(?<!a)b", // 앞뒤 보기
            """(a)\1""", """(?<n>a)\k<n>""", // 되돌아 가리키기·이름
            "(?i)a", "(?i:a)", "(?>a)", // 깃발·원자 묶음
            "a*+", "a++", "a?+", "a{2}+", // 소유 되풀이
            "a**", "a*??", "a{2}{3}", "a+*", // 겹친 되풀이
            "^*a", "$+", """\b*""", // 단언의 되풀이
            "[a[b]]", "[a-z&&[^x]]", "[a&&b]", "[[]", // 겹친 모둠·교집합
            "[]a]", "[^]a]", "[]", "[^]", // 처음의 `]`(자바스크립트와 다르다)
            """\p{L}""", """\Qa.b\E""", """\v""", """\a""", """\e""", """\cA""", """\0101""", """\h""", """\R""", """\A""", """\z""", """\Z""",
            """\G""", """\x{41}""", """\x4""", """\u004""", """[\b]""", """[a-\d]""", """[.-\w]""", "[z-a]",
            "*a", "+a", "?a", "{2}", "a{", "a{,3}", "a{3,1}", "a{1001}", "x{2147483648}", "a{1,2", // 되풀이의 틀
            "a)", "(a", "[a", "a\\", "\\", // 짝
            """(\b|.){2,}""", "(a?){2}", "(?:|a){3,}", "^.*(?:a?|b?){40}y", "(?:a*b?){2,5}", // 빈 글에 맞는 것을 두 번 넘게(엔진마다 뜻이 다르다)
            "(".repeat(40) + "a" + ")".repeat(40), // 너무 깊다
            "(?:a{1000}){5}", // 명령이 너무 많다
            "a".repeat(ListRegex.MAX_SOURCE + 1), // 너무 길다
        )
        for (p in unsupported) {
            assertNull(ListRegex.compile(p, ignoreCase = false), "/$p/ 를 받았다")
            assertFailsWith<ListRegex.Unsupported>("/$p/") { ListRegex.compileOrThrow(p, ignoreCase = true) }
        }
        // 상한 바로 안쪽은 받는다.
        assertNotNull(ListRegex.compile("a".repeat(ListRegex.MAX_SOURCE), ignoreCase = false))
        assertNotNull(ListRegex.compile("(".repeat(32) + "a" + ")".repeat(32), ignoreCase = false))
        assertNotNull(ListRegex.compile("a{1000}", ignoreCase = false))
        // 빈 글에 맞을 수 있어도 한 번까지(`?`·`*`·`+`·`{0,n}`·`{1,n}`)는 뜻이 같아 받는다.
        for (p in listOf("(a?)?", "(a|)*b", """(\b|.)+$""", "(a?){1,3}b", "(?:)*", "(a*){0,4}")) {
            assertNotNull(ListRegex.compile(p, ignoreCase = false), "/$p/")
            same(p, "", "a", "b", "aab", "c!", "ab")
        }
    }

    @Test
    fun 되짚는_엔진에서_터지는_무늬도_걸음이_길이에_비례한다() {
        val n = 20_000
        val cases = listOf(
            Triple("(a+)+$", "a".repeat(n) + "!", false),
            Triple("(a+)+$", "a".repeat(n), true),
            Triple("(a|aa)+$", "a".repeat(n) + "!", false),
            Triple("(a|a)*b", "a".repeat(n), false),
            Triple("""^(\w+\s?)*$""", "a".repeat(n) + "!", false),
            Triple("(.*a){12}", "b".repeat(n), false),
            Triple("(.*a){12}", "b".repeat(n) + "a".repeat(12), true),
            Triple("(x+x+)+y", "x".repeat(n), false),
            Triple("^(([a-z])+.)+[A-Z]([a-z])+$", "a".repeat(n) + "!", false),
            // 아무것도 읽지 않고 갈래마다 0 글자로 맞는 되짚기(입력 끝에서 2^40) — 읽은 글자를 세는 예산으로는 잡지 못한다.
            Triple(WIDE, "x".repeat(n), false),
        )
        for ((pattern, input, expected) in cases) {
            val re = ListRegex.compileOrThrow(pattern, ignoreCase = false)
            assertEquals(expected, re.find(input, Long.MAX_VALUE) == ListRegex.Result.MATCH, "/$pattern/")
            val steps = re.stepsFor(input)
            assertTrue(steps <= 2L * (input.length + 1) * re.size, "/$pattern/ 걸음 $steps — 길이에 비례하지 않는다(명령 ${re.size})")
            // 기본 예산으로는 답이 같거나, 넓은 무늬면 예산을 넘는다 — 틀린 답은 없다.
            val budgeted = re.find(input)
            if (budgeted != ListRegex.Result.OVER_BUDGET) assertEquals(expected, budgeted == ListRegex.Result.MATCH, "/$pattern/")
        }
        // 짧은 입력에서는 java.util.regex 와 같다(그쪽이 터지지 않는 길이).
        for (pattern in listOf("(a+)+$", "(a|aa)+$", "(a|a)*b", """^(\w+\s?)*$""", "(x+x+)+y")) {
            same(pattern, "aaaaaaaaaa!", "aaaaaaaa", "xxxxxxxy", "ab c", "")
        }
    }

    @Test
    fun 걸음은_입력이_같으면_같고_예산을_넘으면_멈춘다() {
        val re = ListRegex.compileOrThrow(WIDE, ignoreCase = false)
        val input = "x".repeat(5_000)
        val steps = re.stepsFor(input)
        assertEquals(steps, re.stepsFor(input), "같은 입력이면 같은 걸음")
        assertTrue(steps > ListRegex.budgetFor(input.length), "넓은 무늬는 기본 예산을 넘는다($steps)")
        assertEquals(ListRegex.Result.OVER_BUDGET, re.find(input))
        assertEquals(ListRegex.Result.NO_MATCH, re.find(input, steps))
        assertEquals(ListRegex.Result.OVER_BUDGET, re.find(input, steps - 1 - 2L * re.size))
        // 맞는 곳이 예산 안에 있으면 맞는다(끝까지 가지 않는다).
        assertEquals(ListRegex.Result.MATCH, re.find("y" + input))
        assertEquals(ListRegex.BASE_STEPS + ListRegex.STEPS_PER_CHAR * 100, ListRegex.budgetFor(100))
        // 좁은 무늬는 긴 주소에서도 예산 안이다.
        val narrow = ListRegex.compileOrThrow("""^https?:\/\/[a-z]+\.example\/ads\/""", ignoreCase = true)
        val long = "https://" + "a".repeat(200_000) + ".example/x"
        assertNotEquals(ListRegex.Result.OVER_BUDGET, narrow.find(long))
        assertEquals(ListRegex.Result.NO_MATCH, narrow.find(long))
    }

    @Test
    fun 여러_스레드가_함께_써도_답이_같다() {
        val patterns = listOf("(a+)+$", """[^a-z0-9]ad[sv]?[-_.]""", "^https?://[a-z.]+/x", "(.*a){3}b").map { ListRegex.compileOrThrow(it, ignoreCase = true) }
        val r = Random(7)
        val inputs = List(200) { buildString { repeat(r.nextInt(80)) { append("ab/.-xAB_?=sv"[r.nextInt(13)]) } } }
        val expected = patterns.map { p -> inputs.map { p.find(it) } }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = List(32) { k ->
                pool.submit(Callable { patterns.indices.all { p -> inputs.indices.all { i -> patterns[p].find(inputs[(i + k) % inputs.size]) == expected[p][(i + k) % inputs.size] } } })
            }
            assertTrue(jobs.all { it.get(30, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }
}
