package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.io.FileStamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 한글 서식(`.hwt`·`.hwtx`·`.owpml`)을 편집기에서 고쳐 저장하면 **서식은 그대로, 곁에 새 `.hwp`·`.hwpx`** 다. 파일 쪽은 그 셋을
 * 확장자의 별칭으로 HWP·HWPX 로 넘기고(`core:io` 의 `EditorApp`), rhwp 는 `.hwp`·`.hwpx` 로 저장한다 — 형식 글자가 같아도 이름이 원본과
 * 다르니 새 파일이다(이전 오피스 형식이 곁에 OOXML 이 되는 것과 같은 결과). 이름을 견주는 규칙을 고칠 때(대문자 확장자 따위를 같은
 * 이름으로 보기) **확장자가 다른 이름까지 같게 보면** 서식 파일을 HWP 로 덮는다 — 이 시험이 그것을 막는다.
 */
class HancomTemplateSaveTest {

    private val stamp = FileStamp(1000, 5000)

    private fun session(original: String, kind: String) = EditorSlots.Session(
        token = "0".repeat(32),
        original = original,
        kind = kind,
        keptPrevious = false,
        stamp = stamp,
        created = emptyMap(),
        saves = 0,
        last = null,
        openedAt = 0,
    )

    @Test
    fun `한글 서식을 고쳐 저장하면 곁에 새 HWP 다`() {
        // 어댑터가 스튜디오에 '신청서.hwp' 로 넘기므로 rhwp 가 저장하는 이름도 그것이다(iroiro-rhwp.js 의 studioName).
        val plan = EditorSlots.plan(session("/storage/emulated/0/서식/신청서.hwt", "hwp"), "hwp", "신청서.hwp") { stamp }!!
        assertEquals("/storage/emulated/0/서식/신청서.hwp", plan.path.replace('\\', '/'))
        assertTrue(plan.createNew)
        assertEquals("신청서.hwp", plan.nameKey)
    }

    @Test
    fun `HWPX 서식과 OWPML 은 곁에 새 HWPX 다`() {
        for (original in listOf("/storage/emulated/0/서식/양식.hwtx", "/storage/emulated/0/서식/양식.owpml")) {
            val plan = EditorSlots.plan(session(original, "hwpx"), "hwpx", "양식.hwpx") { stamp }!!
            assertEquals("/storage/emulated/0/서식/양식.hwpx", plan.path.replace('\\', '/'), original)
            assertTrue(plan.createNew, original)
        }
        // rhwp 가 내용을 보고 HWP 로 저장해도(서식 안이 HWP 5.0) 곁에 새 파일이다 — 형식이 다르다.
        val asHwp = EditorSlots.plan(session("/storage/emulated/0/서식/양식.hwtx", "hwpx"), "hwp", "양식.hwp") { stamp }!!
        assertEquals("/storage/emulated/0/서식/양식.hwp", asHwp.path.replace('\\', '/'))
        assertTrue(asHwp.createNew)
    }

    @Test
    fun `내용과 확장자가 어긋난 한글 문서도 형식이 다르면 곁에 새 파일이다`() {
        // `.hwpx` 이름의 HWP 5.0(표본 K36) — 파일 쪽은 이름으로 HWPX 라고 넘기고, rhwp 가 바이트로 HWP 를 읽었다면 HWP 로 저장한다.
        val plan = EditorSlots.plan(session("/storage/emulated/0/Docs/K36.hwpx", "hwpx"), "hwp", "K36.hwp") { stamp }!!
        assertEquals("/storage/emulated/0/Docs/K36.hwp", plan.path.replace('\\', '/'))
        assertTrue(plan.createNew)
    }
}
