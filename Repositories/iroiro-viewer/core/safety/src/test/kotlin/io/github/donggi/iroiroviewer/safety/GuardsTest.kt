package io.github.donggi.iroiroviewer.safety

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitedInputStreamTest {

    @Test
    fun `상한 안에서는 그대로 읽힌다`() {
        val data = ByteArray(1000) { it.toByte() }
        val s = LimitedInputStream(ByteArrayInputStream(data), maxBytes = 1000)
        assertEquals(1000, s.readBytes().size)
        assertEquals(1000L, s.bytesRead)
    }

    @Test
    fun `상한을 넘기면 끊는다`() {
        val data = ByteArray(2000)
        val s = LimitedInputStream(ByteArrayInputStream(data), maxBytes = 1000)
        assertFailsWith<ParseLimitExceededException> { s.readBytes() }
    }

    @Test
    fun `한 바이트씩 읽어도 센다`() {
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(10)), maxBytes = 5)
        assertFailsWith<ParseLimitExceededException> {
            repeat(10) { s.read() }
        }
    }

    @Test
    fun `건너뛴 바이트도 출력으로 센다`() {
        // 건너뛰기는 압축을 실제로 푸는 비용이 드는 경로다. 세지 않으면 폭탄이
        // skip 만으로 통과한다.
        val s = LimitedInputStream(ByteArrayInputStream(ByteArray(10_000)), maxBytes = 100)
        assertFailsWith<ParseLimitExceededException> { s.skip(10_000) }
    }

    @Test
    fun `skip 은 위임하기 전에 자른다`() {
        // 먼저 위임하고 나중에 세면, 막겠다던 해제 비용을 이미 치른 뒤에 예외가 난다.
        // 실제로 몇 바이트가 소비됐는지로 확인한다.
        val source = ByteArrayInputStream(ByteArray(10_000))
        val s = LimitedInputStream(source, maxBytes = 100)
        assertFailsWith<ParseLimitExceededException> { s.skip(10_000) }
        // 상한 100 + 초과 감지용 1 바이트를 넘게 소비하지 않아야 한다.
        assertTrue(10_000 - source.available() <= 101, "위임 전에 잘라야 한다")
    }
}

class RatioGuardTest {

    @Test
    fun `바닥값 아래에서는 비율을 보지 않는다`() {
        // 20바이트가 1MB 로 부푸는 것은 정상 파일에도 흔하다. 절대량이 의미 있는
        // 수준에 이르기 전에는 막지 않는다.
        val g = RatioGuard(ParseLimits.DEFAULT) { 20 }
        g.observe(1000)
        g.observe(ParseLimits.DEFAULT.ratioFloorBytes - 1)
    }

    @Test
    fun `바닥값을 넘고 비율도 넘으면 끊는다`() {
        val g = RatioGuard(ParseLimits.DEFAULT) { 1000 }
        assertFailsWith<ParseLimitExceededException> {
            g.observe(1000L * 1024 * 1024) // 1000:1
        }
    }

    @Test
    fun `정상 압축비는 통과한다`() {
        // 10MB 가 1MB 로 줄어든 텍스트 파일 — 10:1
        val g = RatioGuard(ParseLimits.DEFAULT) { 1024L * 1024 }
        g.observe(10L * 1024 * 1024)
    }
}

class EntryBudgetTest {

    @Test
    fun `엔트리 수 상한`() {
        val b = EntryBudget(ParseLimits(maxEntries = 3))
        repeat(3) { b.beginEntry() }
        assertFailsWith<ParseLimitExceededException> { b.beginEntry() }
    }

    @Test
    fun `분모를 함수로 받아 실제 소비량을 반영한다`() {
        // 헤더의 선언값 대신 라이브러리가 알려주는 '지금까지 소비한 압축 바이트' 를
        // 쓰면, 선언을 위조해 감시를 끄는 수법이 통하지 않는다.
        var consumed = 4L * 1024 * 1024
        val g = RatioGuard(ParseLimits.DEFAULT) { consumed }
        // 4MB 를 소비해 4MB 를 냈으면 1:1 이다. 통과해야 한다.
        g.observe(4L * 1024 * 1024)
        // 같은 출력인데 소비가 1바이트로 줄면 비율이 폭증한다 — 매번 다시 읽으므로 잡힌다.
        consumed = 1L
        assertFailsWith<ParseLimitExceededException> { g.observe(4L * 1024 * 1024) }
    }

    @Test
    fun `새 작업을 시작하면 출력 총량이 되돌아간다`() {
        // 상한의 뜻은 '이 리더로 평생 읽을 양' 이 아니라 '한 번에 풀어낼 양' 이다.
        // 되돌리지 않으면 300쪽 만화책을 끝까지 넘기는 것만으로 총량에 걸린다.
        val b = EntryBudget(ParseLimits(maxSingleOutput = 1000, maxTotalOutput = 1500))
        b.beginEntry()
        b.guard(ByteArrayInputStream(ByteArray(1000))).readBytes()
        b.resetOutput()
        assertEquals(0L, b.totalOutput)
        b.guard(ByteArrayInputStream(ByteArray(1000))).readBytes()
        assertEquals(1000L, b.totalOutput)
    }

    @Test
    fun `엔트리 수는 목록을 만들기 전에 상계로 먼저 본다`() {
        val b = EntryBudget(ParseLimits(maxEntries = 10))
        assertFailsWith<ParseLimitExceededException> { b.checkEntryCount(1_000_000) }
    }

    @Test
    fun `총 출력 상한은 엔트리를 가로질러 누적된다`() {
        // 엔트리 하나하나는 작은데 합이 큰 것이 중첩 폭탄의 모양이다.
        val b = EntryBudget(ParseLimits(maxSingleOutput = 1000, maxTotalOutput = 2500))
        repeat(2) {
            b.beginEntry()
            b.guard(ByteArrayInputStream(ByteArray(1000))).readBytes()
        }
        assertEquals(2000L, b.totalOutput)
        b.beginEntry()
        assertFailsWith<ParseLimitExceededException> {
            b.guard(ByteArrayInputStream(ByteArray(1000))).readBytes()
        }
    }
}

class DepthGuardTest {

    @Test
    fun `깊이를 넘으면 끊고 예외가 나도 깊이가 새지 않는다`() {
        val g = DepthGuard(max = 2)
        g.around {
            g.around {
                assertFailsWith<ParseLimitExceededException> { g.around { Unit } }
            }
        }
        assertEquals(0, g.depth)
    }
}

class ArchivePathTest {

    @Test
    fun `정상 이름은 그대로 통과한다`() {
        assertEquals("a/b/c.txt", ArchivePath.sanitize("a/b/c.txt"))
        assertEquals("a/b.txt", ArchivePath.sanitize("./a/b.txt"))
    }

    @Test
    fun `백슬래시와 드라이브 문자와 선행 슬래시를 벗긴다`() {
        assertEquals("Windows/system32", ArchivePath.sanitize("C:\\Windows\\system32"))
        assertEquals("etc/passwd", ArchivePath.sanitize("/etc/passwd"))
    }

    @Test
    fun `위로 올라가는 이름은 거부한다`() {
        assertNull(ArchivePath.sanitize("../../etc/passwd"))
        assertNull(ArchivePath.sanitize("a/../../b"))
        assertNull(ArchivePath.sanitize("..\\..\\b"))
    }

    @Test
    fun `제어문자와 빈 이름을 거부한다`() {
        assertNull(ArchivePath.sanitize(""))
        assertNull(ArchivePath.sanitize("a\u0000b"))
        assertNull(ArchivePath.sanitize("/"))
    }

    @Test
    fun `형제 디렉터리 접두어 우회를 막는다`() {
        // junrar 의 2026년 CVE 가 정확히 이 모양이었다. 목적지가 out 일 때
        // outx 로 빠져나간다. 구분자를 붙여 비교해야 막힌다.
        val tmp = File(System.getProperty("java.io.tmpdir"), "iroiro-test-${System.nanoTime()}")
        val dest = File(tmp, "out").apply { mkdirs() }
        File(tmp, "outx").mkdirs()
        try {
            assertNull(ArchivePath.resolveInside(dest, "../outx/evil.txt"))
            val ok = ArchivePath.resolveInside(dest, "inner/ok.txt")
            assertNotNull(ok)
            assertTrue(ok.path.startsWith(dest.canonicalPath + File.separator))
        } finally {
            tmp.deleteRecursively()
        }
    }
}

class SafeXmlTest {

    @Test
    fun `순수 JVM 에서 파서가 만들어지고 네임스페이스를 URI 로 준다`() {
        // 이 테스트의 진짜 목적은 XML 을 파싱하는 것이 아니라, xmlpull(API) +
        // kxml2(구현) 조합으로 format 모듈의 파서를 에뮬레이터 없이 JVM 에서
        // 돌릴 수 있는지를 확인하는 것이다. 여기가 깨지면 파서 개발 방식 전체가 바뀐다.
        val xml = """<?xml version="1.0"?><w:p xmlns:w="urn:test"><w:t>안녕</w:t></w:p>"""
        val p = SafeXml.newParser(xml.byteInputStream(), "UTF-8")
        val seen = mutableListOf<Pair<String?, String>>()
        var text = ""
        while (p.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> seen += p.namespace to p.name
                org.xmlpull.v1.XmlPullParser.TEXT -> text += SafeXml.text(p)
                else -> Unit
            }
            p.next()
        }
        assertEquals(listOf<Pair<String?, String>>("urn:test" to "p", "urn:test" to "t"), seen)
        assertEquals("안녕", text)
    }

    @Test
    fun `거대한 텍스트 노드를 거부한다`() {
        val big = "가".repeat(100)
        val xml = "<a>$big</a>"
        val p = SafeXml.newParser(xml.byteInputStream(), "UTF-8")
        val limits = ParseLimits(maxXmlTextChars = 10)
        while (p.eventType != org.xmlpull.v1.XmlPullParser.TEXT) p.next()
        assertFailsWith<ParseLimitExceededException> { SafeXml.text(p, limits) }
    }
}
