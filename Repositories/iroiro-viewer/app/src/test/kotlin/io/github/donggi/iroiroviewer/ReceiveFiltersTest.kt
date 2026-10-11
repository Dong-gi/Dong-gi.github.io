package io.github.donggi.iroiroviewer

import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 다른 앱에서 이 앱으로 넘기는 길(`OpenActivity` 의 VIEW·SEND 필터)과 매니페스트가 지키는 것. 매니페스트를 글로 읽는다(모듈 폴더에서 —
 * Gradle 이 JVM 시험을 돌리는 자리. `app/build.gradle.kts` 가 시험의 입력으로 적는다).
 *
 * **편집기가 보이는 형식은 받는다**. 뷰어 교체(10-09)로 이전 오피스 형식(doc·xls·ppt)과 ODF(odt·ods·odp)를 이 앱이 보이게
 * 됐는데 필터는 그대로라, 메일의 `.doc` 첨부를 '다음으로 열기' 하면 고르는 창에 이 앱이 없었다. 받은 뒤의 길은 이미 됐다(`IncomingRoute` →
 * 편집기의 보기 모드).
 *
 * '다른 앱으로 열기' 의 **우리 자신 빼기**는 기기에서 묻는다(`ExternalOpen` 의 `ownHandlers` — 이 필터에 그 형식이 있으면 고르는 창에서 우리를
 * 빼고, 곧바로 여는 길도 우리를 뺀 고르는 창으로 바꾼다. 그 판단은 `core:io` 의 `ExternalOpenTest` 가 본다). 여기서는 그 질의가 우리를
 * 찾을 수 있게 **그 파일의 형식이 필터에 있는가**를 본다.
 */
class ReceiveFiltersTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
    private val withoutComments = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(manifest, "")

    /** `OpenActivity` 의 필터에서 [action] 을 받는 것의 형식들. */
    private fun mimesOf(action: String): Set<String> {
        val activity = Regex("""<activity\b[^>]*android:name="\.OpenActivity".*?</activity>""", RegexOption.DOT_MATCHES_ALL)
            .find(withoutComments)?.value ?: error("OpenActivity 를 찾지 못했다")
        val filter = Regex("""<intent-filter>.*?</intent-filter>""", RegexOption.DOT_MATCHES_ALL).findAll(activity)
            .map { it.value }.singleOrNull { "android.intent.action.$action\"" in it } ?: error("$action 필터가 하나가 아니다")
        return Regex("""android:mimeType="([^"]+)"""").findAll(filter).map { it.groupValues[1] }.toSet()
    }

    private val view by lazy { mimesOf("VIEW") }
    private val send by lazy { mimesOf("SEND") }

    /** 안드로이드의 필터 대조처럼 — 같은 글자이거나 계열 전체(`text/` 뒤에 별표 — 주석에 그 글자를 적으면 블록 주석이 하나 더 열린다). */
    private fun Set<String>.takes(mime: String): Boolean =
        mime in this || any { it.endsWith("/*") && mime.startsWith(it.dropLast(1)) }

    private val oldOfficeAndOdf = listOf(
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
    )

    @Test
    fun `이전 오피스 형식과 ODF 를 보기와 공유 둘 다 받는다`() {
        assertTrue(view.size > 40 && send.size > 30, "필터를 읽지 못했다: ${view.size}·${send.size}")
        for (mime in oldOfficeAndOdf) {
            assertTrue(mime in view, "VIEW: $mime")
            assertTrue(mime in send, "SEND: $mime")
        }
    }

    /**
     * 편집기의 형식(`EditorApp.extensions` — 한글·OOXML 과 그 판·이전 형식·ODF·글·CSV·PDF) 가운데 **이 앱이 보이는 것은 모두** 다른 앱이
     * 그 형식(목록의 표 `MimeResolver.mimeOf` 가 주는 것 — 다른 앱으로 넘길 때 우리가 붙이는 것과 같다)으로 넘겨도 받는다. 공유(SEND)는
     * 글을 받지 않는다(브라우저의 주소 공유마다 이 앱이 서지 않게 — CLAUDE.md '다른 앱에서 이 앱으로 열기').
     */
    @Test
    fun `편집기가 보이는 형식은 다른 앱이 넘겨도 받는다`() {
        val exts = EditorApp.extensions
        assertTrue(exts.size > 20, "편집기의 형식을 읽지 못했다: $exts")
        for (ext in exts) {
            val mime = MimeResolver.mimeOf("x.$ext")
            assertTrue(view.takes(mime), "VIEW 가 .$ext($mime) 를 받지 않는다")
            if (!mime.startsWith("text/")) assertTrue(send.takes(mime), "SEND 가 .$ext($mime) 를 받지 않는다")
        }
        // 한글 서식(별칭)은 형식 이름이 없어 octet-stream 으로 온다 — 그것도 받는다.
        for (ext in listOf("hwt", "hwtx", "owpml")) assertTrue(view.takes(MimeResolver.mimeOf("x.$ext")), ext)
    }

    /** 받은 뒤의 길 — 이전 형식·ODF·한글 서식은 문서 갈래이고, 편집기가 그 형식을 보이면 편집기의 보기 모드로 간다(그 형식 글자로). */
    @Test
    fun `받은 이전 형식과 ODF 는 편집기의 보기 모드로 간다`() {
        for (ext in listOf("doc", "dot", "xls", "xlt", "ppt", "pot", "pps", "odt", "ods", "odp", "hwt", "hwtx", "owpml")) {
            val kind = MimeResolver.kindOf("보고서.$ext", isDirectory = false)
            assertTrue(kind in setOf(FileKind.DOCUMENT, FileKind.SHEET, FileKind.SLIDE, FileKind.HWP), "$ext → $kind")
            assertEquals(IncomingRoute.DOCUMENT, IncomingRoute.of(kind), ext)
        }
        assertEquals(AppScreen.Editor("/p/보고서.doc", "doc"), IncomingRoute.DOCUMENT.screenFor("/p/보고서.doc", "doc"))
        // 편집기 묶음 없이 지은 빌드 — 문서 뷰어가 '다루지 않는 문서' 와 '다른 앱으로 열기' 로 끝낸다.
        assertEquals(AppScreen.Doc("/p/보고서.doc"), IncomingRoute.DOCUMENT.screenFor("/p/보고서.doc", null))
    }

    /**
     * 형식은 소문자로만 적는다(안드로이드의 대조는 대소문자를 가리고 lint 가 대문자를 오류로 본다). APK·글꼴은 받지 않는다 — 받으면 이 앱의
     * '다른 앱으로 열기' 가 이 앱을 다시 고르는 고리가 된다(뷰어가 없다).
     */
    @Test
    fun `형식은 소문자이고 APK·글꼴은 받지 않는다`() {
        for (mime in view + send) {
            assertEquals(mime.lowercase(), mime)
            assertFalse(mime == "application/vnd.android.package-archive" || mime.startsWith("font/"), mime)
        }
    }

    /**
     * INTERNET 을 들여오는 두 모양을 모두 걷는다 — `uses-permission` 의 remove 는 `uses-permission-sdk-23` 에 닿지 않는다.
     * 병합된 매니페스트는 빌드가 본다(`app/build.gradle.kts` 의 `verify<변형>NoInternet`).
     */
    @Test
    fun `INTERNET 을 들여오는 두 모양을 모두 걷는다`() {
        val internet = Regex("""<(uses-permission(?:-sdk-23|-sdk-m)?)\s[^>]*>""").findAll(withoutComments)
            .filter { "\"android.permission.INTERNET\"" in it.value }.toList()
        assertEquals(setOf("uses-permission", "uses-permission-sdk-23"), internet.map { it.groupValues[1] }.toSet())
        // 선언하는 줄(remove 가 없는 것)은 없다 — INTERNET 을 적은 요소는 모두 걷는 줄이다.
        for (e in internet) assertTrue("tools:node=\"remove\"" in e.value, e.value)
    }
}
