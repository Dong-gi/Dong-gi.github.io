package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.cfb.TinyCfb
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 여는 흐름 — 형식 판정, 잠긴 문서, 소유권, 판별기. */
class Hwp5OpenerTest {

    private fun simple(flags: Long = 1): HwpFile = HwpFile().apply {
        this.flags = flags
        section(P().text("안녕하세요 한글 문서"))
    }

    private fun failure(bytes: ByteArray): OpenFailure {
        val o = Hwp5.outcome(bytes)
        assertIs<OpenOutcome.Failed>(o, "$o")
        return o.failure
    }

    @Test
    fun 한글_97_이전의_파일은_이전_형식이라고_말한다() {
        val v3 = ("HWP Document File V3.00 \u001a\u0001\u0002\u0003\u0004\u0005" + "x".repeat(200)).toByteArray()
        val f = failure(v3)
        assertIs<OpenFailure.LegacyFormat>(f)
        assertEquals(Hwp5Opener.HWP3, f.detail)
    }

    @Test
    fun CFB_도_HWP3_도_아니면_깨진_파일이다() {
        assertIs<OpenFailure.Corrupt>(failure("<html><body>not hwp</body></html>".toByteArray()))
        // CFB 인데 FileHeader 가 없다(이전 형식 오피스 따위).
        val cfb = TinyCfb().stream("WordDocument", ByteArray(100)).build().bytes
        assertIs<OpenFailure.Corrupt>(failure(cfb))
        // FileHeader 의 서명이 틀렸다.
        val file = simple().apply { headerOverride = Bytes().bytes("NOT A HWP".toByteArray()).zeros(247).toByteArray() }
        assertIs<OpenFailure.Corrupt>(failure(file.build()))
    }

    @Test
    fun 암호_문서는_묻지_않고_열_수_없다고_끝낸다() {
        // 비트 1. 암호를 넣어 와도 마찬가지 — 방식이 공개되지 않아 풀 방법이 없다(PasswordRequired 가 아니다).
        val bytes = simple(flags = 0x3).build()
        assertIs<OpenFailure.Encrypted>(failure(bytes))
        val withPassword = Hwp5.outcome(bytes, password = "12345".toCharArray())
        assertIs<OpenOutcome.Failed>(withPassword)
        assertIs<OpenFailure.Encrypted>(withPassword.failure)
    }

    @Test
    fun DRM_과_인증서_문서도_열_수_없다고_끝낸다() {
        for (bit in listOf(4, 8, 10)) {
            val f = failure(simple(flags = 1L or (1L shl bit)).build())
            assertIs<OpenFailure.Encrypted>(f, "bit $bit")
        }
        // 개인 정보 보안(13)·변경 추적(14) 따위는 잠금이 아니다 — 연다.
        Hwp5.open(simple(flags = 1L or (1L shl 13) or (1L shl 14))).close()
    }

    @Test
    fun 판이_5_가_아니면_다루지_않는다() {
        val f = failure(simple().apply { version = 0x03000000 }.build())
        assertIs<OpenFailure.Unsupported>(f)
    }

    @Test
    fun 문서는_만든_자리에서_넘기고_부분이_있다() {
        var handed: OpenedDocument? = null
        val o = runBlocking {
            Hwp5Opener(null).open(ByteArrayDocumentSource(simple().build(), "a.hwp")) { handed = it }
        }
        assertIs<OpenOutcome.Success>(o)
        assertSame(handed, o.document)
        val doc = o.document as FlowDocument
        assertEquals(FormatId.HWP5, doc.formatId)
        assertEquals(1, doc.parts.size)
        assertTrue("안녕하세요 한글 문서" in doc.body())
        doc.close()
        // 닫힌 뒤에는 null — 던지지 않는다.
        assertNull(doc.partHtml(0))
    }

    @Test
    fun 압축한_파일과_압축하지_않은_파일이_같은_본문을_낸다() {
        val a = Hwp5.open(simple(flags = 1))
        val b = Hwp5.open(simple(flags = 0))
        assertEquals(a.body(), b.body())
        a.close()
        b.close()
    }

    @Test
    fun 스크립트가_있으면_알리되_돌리지_않는다() {
        val doc = Hwp5.open(simple(flags = 1L or 8L))
        assertTrue(doc.warnings.any { it.code == FlowWarnings.MACROS })
        assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.MACRO])
        doc.close()
        val clean = Hwp5.open(simple())
        assertTrue(clean.warnings.none { it.code == FlowWarnings.MACROS })
        clean.close()
    }

    @Test
    fun 판별기는_한글_확장자의_CFB_와_HWP3_만_맡는다() {
        val cfb = simple().build()
        fun probe(ext: String, head: ByteArray) = Hwp5Probe.probe(ProbeContext(ByteArrayDocumentSource(head, "x.$ext"), ext, head.copyOf(minOf(64, head.size))))
        assertEquals(FormatId.HWP5, probe("hwp", cfb))
        assertEquals(FormatId.HWP5, probe("hwpx", cfb)) // 이름만 HWPX 인 HWP 5.0(표본 K36)
        assertNull(probe("doc", cfb))
        assertEquals(FormatId.HWP5, probe("hwp", "HWP Document File V3.00 \u001a".toByteArray()))
        assertNull(probe("hwp", "PK\u0003\u0004 zip".toByteArray()))
    }

    @Test
    fun 배포용_문서는_ViewText_를_풀어_연다() {
        val file = HwpFile().apply {
            flags = 1L or 4L
            section(P().text("배포용으로 잠긴 본문입니다"))
        }
        val doc = Hwp5.open(file) as Hwp5FlowDocument
        val body = doc.body()
        assertTrue("배포용으로 잠긴 본문입니다" in body, body)
        // BodyText 의 안내 문단은 읽지 않는다.
        assertTrue("배포용 문서 안내" !in body)
        assertEquals(0x8001, doc.distributionFlags)
        // 제한 표시는 읽기만 한다 — 글자 선택을 막는 CSS 를 넣지 않았다.
        assertTrue("user-select" !in doc.partHtml(0)!!)
        doc.close()
        // 압축하지 않은 배포용도.
        val raw = HwpFile().apply {
            flags = 4L
            section(P().text("압축 없는 배포용"))
        }
        Hwp5.open(raw).use { assertTrue("압축 없는 배포용" in it.body()) }
    }

    @Test
    fun 배포용_자료가_깨졌으면_본문_대신_미리보기_글을_보인다() {
        val file = HwpFile().apply {
            flags = 1L or 4L
            section(P().text("잠긴 본문"))
            prvText = "미리 보기 첫 줄\r\n둘째 줄"
        }
        val bytes = file.build()
        // ViewText/Section0 의 레코드 머리를 망가뜨린다(태그 0x1C 가 아니게).
        val needle = byteArrayOf(0x1C, 0x00, 0x00, 0x10)
        val at = (0 until bytes.size - 4).first { i -> (0..3).all { bytes[i + it] == needle[it] } }
        bytes[at] = 0x1D
        val doc = Hwp5.open(bytes)
        val body = doc.body()
        assertTrue("미리 보기 첫 줄" in body && "둘째 줄" in body, body)
        // HWPX 의 미리보기와 같은 모양 — 바탕 스타일의 `.preview p` 가 줄 사이를 띄운다(13단계 짝 대조).
        assertTrue(body.startsWith("<div class=\"preview\"><p>미리 보기 첫 줄</p><p>둘째 줄</p>"), body)
        assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED }, "${doc.warnings}")
        doc.close()
    }

    @Test
    fun 미리보기_글이_상한에서_잘리면_알린다() {
        // HWPX 와 같다 — 상한에 닿은 미리보기를 조용히 자르지 않는다.
        val file = HwpFile().apply {
            flags = 1L or 4L
            section(P().text("잠긴 본문"))
            prvText = (1..400).joinToString("\r\n") { "미리 보기 $it 번째 줄" }
        }
        val bytes = file.build()
        val needle = byteArrayOf(0x1C, 0x00, 0x00, 0x10)
        val at = (0 until bytes.size - 4).first { i -> (0..3).all { bytes[i + it] == needle[it] } }
        bytes[at] = 0x1D
        val doc = Hwp5.open(bytes, Hwp5Options(maxChars = 2_000))
        val body = doc.body()
        assertTrue("미리 보기 1 번째 줄" in body && "미리 보기 400 번째 줄" !in body, body)
        assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, "${doc.warnings}")
        doc.close()
    }

    @Test
    fun 본문도_미리보기도_없으면_깨진_파일이다() {
        val file = HwpFile().apply { section(P().text("x")) }
        file.sections[0] = byteArrayOf(1, 2, 3)
        val f = failure(file.build())
        assertIs<OpenFailure.Corrupt>(f)
        assertNotNull(f.detail)
    }
}
