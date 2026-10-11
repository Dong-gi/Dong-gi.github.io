package io.github.donggi.iroirobrowser.tether

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 테더의 프로세스 경계를 **매니페스트와 소스로** 박는다 — 기기 없이 볼 수 있는 것만.
 *
 * - 테더 구성 요소 다섯(화면·트램폴린·서비스·타일·타일을 길게 누를 때의 작은 화면)은 모두 `:tether` 프로세스 — 상태([TetherState])가 그
 *   프로세스의 정적 필드라 하나라도 다른 프로세스면 늘 처음 값을 본다. 다른 구성 요소에는 프로세스를 따로 두지 않는다(브라우저는 주 프로세스 하나).
 * - 내보냄: 타일(시스템만 묶는 권한과 함께)과, 타일을 길게 누를 때 시스템 UI 가 띄우는 작은 화면(`QS_TILE_PREFERENCES` — 테더 화면을 열기만
 *   한다, 받은 인텐트를 읽지 않는다). 테더 화면과 트램폴린은 내보내지 않는다 — 다른 앱이 핫스팟을 켜지 못하게.
 * - 앱 바로가기는 없다(사용자가 타일 길게 누르기를 고르며 걷었다 — 2026-10-10).
 * - 테더의 소스는 브라우저의 것(WebApp·프록시·내려받기·WebView …)을 부르지 않는다 — 테더 프로세스에 프록시 계정·중계·WebView 가 올라오지 않게
 *   (부르면 WebApp.of 가 던진다 — 기기에서 그 길을 처음 밟을 때가 아니라 여기서 잡는다). 테더 화면이 부르는 설정 파일 zip(setup/)도 같다.
 * - 위치 권한은 COARSE 와 FINE 을 함께, 13 부터는 빼고(maxSdkVersion 32), 근처 기기는 neverForLocation.
 */
class TetherIsolationTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private val manifest: Element by lazy {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
    }

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    private fun components(): List<Element> {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val out = ArrayList<Element>()
        for (tag in listOf("activity", "service", "receiver", "provider", "activity-alias")) {
            val nodes = application.getElementsByTagName(tag)
            for (i in 0 until nodes.length) out += nodes.item(i) as Element
        }
        return out
    }

    @Test
    fun 테더_구성_요소_다섯은_모두_tether_프로세스이고_나머지는_주_프로세스() {
        val tether = components().filter { it.attr("name").startsWith(".tether.") }
        assertEquals(
            setOf(
                ".tether.TetherActivity", ".tether.HotspotStartActivity", ".tether.TetherService", ".tether.HotspotTileService",
                ".tether.TilePreferencesActivity",
            ),
            tether.map { it.attr("name") }.toSet(),
        )
        for (c in tether) assertEquals(":tether", c.attr("process"), c.attr("name"))
        for (c in components() - tether.toSet()) assertEquals("", c.attr("process"), "${c.attr("name")} 는 주 프로세스여야 한다")
        // 브로드캐스트 수신기·제공자는 없다(테더 프로세스가 뜰 때 브라우저의 것이 덩달아 돌지 않게).
        assertTrue(components().none { it.tagName == "receiver" || it.tagName == "provider" })
    }

    @Test
    fun 내보내는_것은_타일과_길게_누르기의_작은_화면뿐이다() {
        val byName = components().associateBy { it.attr("name") }
        assertEquals("false", byName.getValue(".tether.TetherActivity").attr("exported"))
        assertEquals("false", byName.getValue(".tether.HotspotStartActivity").attr("exported"))
        assertEquals("false", byName.getValue(".tether.TetherService").attr("exported"))
        val tile = byName.getValue(".tether.HotspotTileService")
        assertEquals("true", tile.attr("exported"))
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.attr("permission"))
        assertEquals("true", byName.getValue(".tether.TilePreferencesActivity").attr("exported"))
        val exported = components().filter { it.attr("name").startsWith(".tether.") && it.attr("exported") == "true" }.map { it.attr("name") }
        assertEquals(setOf(".tether.HotspotTileService", ".tether.TilePreferencesActivity"), exported.toSet())
    }

    @Test
    fun 타일을_길게_누를_때의_화면은_테더_화면을_열기만_한다() {
        // 시스템 UI 가 QS_TILE_PREFERENCES 를 이 꾸러미에서 찾아 자기 uid 로 띄운다 — 내보내야 하고, 그래서 받은 인텐트를 읽지 않는다.
        val prefs = components().first { it.attr("name") == ".tether.TilePreferencesActivity" }
        assertEquals("activity", prefs.tagName)
        assertEquals(":tether", prefs.attr("process"))
        assertEquals("true", prefs.attr("excludeFromRecents"))
        val filters = prefs.getElementsByTagName("intent-filter")
        assertEquals(1, filters.length)
        val actions = (filters.item(0) as Element).getElementsByTagName("action")
        assertEquals(
            listOf("android.service.quicksettings.action.QS_TILE_PREFERENCES"),
            (0 until actions.length).map { (actions.item(it) as Element).attr("name") },
        )
        // 소스는 테더 화면을 열고 끝낼 뿐 — 받은 인텐트(액션·덧붙임·데이터)를 읽지 않고 서비스·트램폴린·설정을 부르지 않는다.
        val source = File("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TilePreferencesActivity.kt").readText()
        val code = source.lines()
            .filterNot { it.trim().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/**") } }
            .joinToString(separator = " ")
        assertTrue(code.contains("startActivity(Intent(this, TetherActivity::class.java)"), code)
        assertTrue(code.contains("finish()"), code)
        val forbidden = listOf(
            Regex("""\bintent\b"""), Regex("getIntent"), Regex("extras"), Regex("Extra"), Regex("""\.data\b"""),
            Regex("TetherService"), Regex("HotspotStartActivity"), Regex("TetherSettings"),
        )
        for (pattern in forbidden) assertFalse(pattern.containsMatchIn(code), "${pattern.pattern} 를 쓴다")
    }

    @Test
    fun 서비스는_specialUse_와_서브타입이고_밀어_없애도_멈추지_않는다() {
        val service = components().first { it.attr("name") == ".tether.TetherService" }
        assertEquals("specialUse", service.attr("foregroundServiceType"))
        assertEquals("false", service.attr("stopWithTask"))
        val property = service.getElementsByTagName("property").item(0) as Element
        assertEquals("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", property.attr("name"))
        assertTrue(property.attr("value").isNotBlank())
        val permissions = permissions()
        assertTrue("android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in permissions.keys)
    }

    @Test
    fun 위치는_COARSE_와_FINE_을_함께_12_까지만이고_근처_기기는_위치에_쓰지_않는다() {
        val permissions = permissions()
        assertEquals("32", permissions.getValue("android.permission.ACCESS_COARSE_LOCATION").attr("maxSdkVersion"))
        assertEquals("32", permissions.getValue("android.permission.ACCESS_FINE_LOCATION").attr("maxSdkVersion"))
        assertEquals("neverForLocation", permissions.getValue("android.permission.NEARBY_WIFI_DEVICES").attr("usesPermissionFlags"))
        assertTrue("android.permission.ACCESS_WIFI_STATE" in permissions.keys && "android.permission.CHANGE_WIFI_STATE" in permissions.keys)
        // 백그라운드 위치는 청하지 않는다.
        assertTrue("android.permission.ACCESS_BACKGROUND_LOCATION" !in permissions.keys)
    }

    @Test
    fun 테더의_소스는_브라우저의_것을_부르지_않는다() {
        val dir = File("src/main/kotlin/io/github/donggi/iroirobrowser/tether")
        val sources = dir.listFiles { f -> f.name.endsWith(".kt") }.orEmpty()
        assertTrue(sources.size >= 11, "테더 소스를 찾지 못했다: ${dir.absolutePath}")
        // 테더 화면의 'PC 연결 파일 받기' 가 setup/ 을 부른다 — 그 패키지도 같은 규칙을 지키는지는 아래 시험이 본다.
        for (file in sources) assertBrowserFree(file, "io.github.donggi.iroirobrowser.tether.", "io.github.donggi.iroirobrowser.setup.")
    }

    @Test
    fun 테더가_부르는_설정_파일_zip_도_브라우저의_것을_부르지_않는다() {
        val dir = File("src/main/kotlin/io/github/donggi/iroirobrowser/setup")
        val sources = dir.listFiles { f -> f.name.endsWith(".kt") }.orEmpty()
        assertTrue(sources.isNotEmpty(), "설정 파일 zip 의 소스를 찾지 못했다: ${dir.absolutePath}")
        for (file in sources) assertBrowserFree(file, "io.github.donggi.iroirobrowser.setup.")
    }

    /** 브라우저의 것을 부르지 않는가 — 금지어(주석에만 있으면 괜찮다)와, 앱의 import 는 R·BuildConfig·ui.Ui 와 [ownPackages] 뿐인가. */
    private fun assertBrowserFree(file: File, vararg ownPackages: String) {
        val allowedApp = setOf("io.github.donggi.iroirobrowser.R", "io.github.donggi.iroirobrowser.BuildConfig", "io.github.donggi.iroirobrowser.ui.Ui")
        val forbidden = listOf("WebApp", "android.webkit", "NetworkPath", "SettingsStore", "SecretBox", "LocalRelay", "DownloadCenter", "TorrentCenter")
        val text = file.readText()
        for (word in forbidden) assertTrue(!text.contains(word) || isOnlyInComments(text, word), "${file.name} 가 $word 를 부른다")
        val appImports = Regex("^import (io\\.github\\.donggi\\.iroirobrowser\\.[\\w.]+)", RegexOption.MULTILINE).findAll(text)
            .map { it.groupValues[1] }
            .filterNot { import -> ownPackages.any { import.startsWith(it) } }
            .toList()
        for (import in appImports) assertTrue(import in allowedApp, "${file.name} 가 브라우저의 $import 를 가져온다")
    }

    @Test
    fun 앱_바로가기는_없다() {
        // 사용자가 앱 바로가기 대신 타일 길게 누르기를 골랐다(2026-10-10) — 바로가기 자원과 매니페스트의 meta-data 를 걷었다.
        val metas = manifest.getElementsByTagName("meta-data")
        assertTrue((0 until metas.length).none { (metas.item(it) as Element).attr("name") == "android.app.shortcuts" })
        assertFalse(File("src/main/res/xml/shortcuts.xml").exists())
        assertFalse(File("src/main/res/drawable/ic_shortcut_tether.xml").exists())
    }

    private fun permissions(): Map<String, Element> {
        val nodes = manifest.getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.attr("name") }
    }

    /** 그 낱말이 주석(`//`·KDoc 의 줄)에만 있는가 — 머리말이 '왜 부르지 않는지' 를 적는다. */
    private fun isOnlyInComments(text: String, word: String): Boolean =
        text.lines().filter { it.contains(word) }.all { line ->
            val trimmed = line.trim()
            trimmed.startsWith("*") || trimmed.startsWith("/**") || trimmed.startsWith("//") || line.substringBefore(word).contains("//")
        }
}
