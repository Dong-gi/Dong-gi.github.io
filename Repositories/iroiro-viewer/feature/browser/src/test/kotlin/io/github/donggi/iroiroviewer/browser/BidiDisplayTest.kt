package io.github.donggi.iroiroviewer.browser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 파일 목록이 이름을 **화면에 보일 이름**(`PathNames.forDisplay` — 방향 제어 글자를 보이는 표시로)으로 적는가(2026-10-16 보안 점검).
 * `invoice` + U+202E + `fdp.apk` 가 목록에서 'invoicekpa.pdf' 로 보이면 사용자는 APK 를 문서로 알고 누른다. 판단은 `core:model` 의
 * `PathNamesTest` 가 보고, 여기서는 목록·격자·정보 시트가 그것을 지나는지(배선)를 소스로 본다 — 이 저장소의 관례.
 */
class BidiDisplayTest {

    private fun source(name: String) = File("src/main/kotlin/io/github/donggi/iroiroviewer/browser/$name").readText()

    @Test
    fun 목록과_격자는_보일_이름을_적는다() {
        val items = source("FileItems.kt")
        assertEquals(2, Regex("""text = PathNames\.forDisplay\(entry\.name\)""").findAll(items).count(), "목록 줄·격자 칸")
        assertFalse(Regex("""text = entry\.name\b""").containsMatchIn(items), "이름을 그대로 적는 자리가 남았다")
    }

    @Test
    fun 정보_시트도_보일_이름을_적는다() {
        val sheet = source("BrowserScreen.kt").substringAfter("private fun DetailSheet(").substringBefore("\n}\n")
        assertTrue("Text(PathNames.forDisplay(entry.name)" in sheet, sheet.take(300))
    }
}
