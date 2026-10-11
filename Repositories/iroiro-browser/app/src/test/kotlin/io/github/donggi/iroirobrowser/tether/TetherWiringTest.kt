package io.github.donggi.iroirobrowser.tether

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 독립 검토(2026-10-10)의 고침 가운데 **안드로이드에 기대어 JVM 에서 돌릴 수 없는 이음새**를 소스·자원으로 박는다(`TetherIsolationTest` 와 같은
 * 꼴). 판정 자체는 `:tether` 의 시험이 돌려 본다(PeerFilter·GroupWatch·ChangeCoalescer·PassphraseVault) — 여기서는 앱이 그것을 실제로 잇는지만
 * 본다. 같은 날 저녁의 사용자 결정(켜기·끄기를 하나로 — 핫스팟이 서지 못하거나 내려가면 테더링을 통째로 끈다, HTTP·파일 포트는 +1 씩, 타일의
 * 이름 '테더링')도 같은 꼴로 박는다. 기기에서 볼 것은 CLAUDE.md '테더링' 의 '확인한 것·확인하지 못한 것'.
 */
class TetherWiringTest {

    private fun source(name: String): String = File("$DIR/$name").readText()

    /** 주석 줄을 뺀 코드 — 머리말이 '왜 하지 않는지' 를 적어도 걸리지 않게. */
    private fun code(name: String): String =
        source(name).lines().filterNot { it.trim().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/**") } }.joinToString("\n")

    /** [signature] 로 시작하는 함수의 몸 — 그 뒤 첫 `{` 부터 짝이 맞는 `}` 까지(주석을 뺀 코드에서). */
    private fun body(code: String, signature: String): String {
        val start = code.indexOf(signature)
        assertTrue(start >= 0, "찾지 못했다: $signature")
        val open = code.indexOf('{', start)
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return code.substring(open, i + 1)
            }
        }
        error("짝이 맞지 않는다: $signature")
    }

    @Test
    fun 손님_거르개는_떠_있는_우리_그룹의_인터페이스와_견준다() {
        // 그룹 인터페이스는 같은 프로세스의 상태에서. 들어온 망(SO_MARK)은 앱이 읽을 공개 API 가 없어 넣지 않았다 — 숨은 API 를 부르지 않는다
        // (Os.getsockoptInt 는 MAX_TARGET_O — targetSdk 36 에서 막힌다).
        val service = code("TetherService.kt")
        assertTrue(service.contains("peers = PeerFilter(groupInterface = { TetherState.groupInterface }),"))
        val all = File("src/main/kotlin/io/github/donggi/iroirobrowser/tether").listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertFalse(all.contains("getsockoptInt"), "숨은 API")
        // 그룹이 섰다고 확인한 인스턴스의 인터페이스만 상태에 두고, 끄거나 내려가면 걷는다.
        assertTrue(service.contains("TetherState.groupInterface = if (ok) hs.groupInterface else null"))
        assertTrue(Regex("""private fun clearHotspotState\(\) \{[^}]*TetherState\.groupInterface = null""").containsMatchIn(service))
    }

    @Test
    fun 그룹의_소식은_내보내지_않는_수신기로_받고_Channel_을_잃는_것도_본다() {
        val service = code("TetherService.kt")
        assertTrue(service.contains("IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)"))
        assertTrue(service.contains("registerReceiver(p2pReceiver, filter, RECEIVER_NOT_EXPORTED)"))
        assertTrue(service.contains("unregisterReceiver(p2pReceiver)"))
        // 예전에는 initialize(…, null) 이라 Channel 을 잃어도 몰랐다.
        val hotspot = code("WifiHotspot.kt")
        assertTrue(Regex("""initialize\(appContext, Looper\.getMainLooper\(\)\) \{\s*if \(!closed\) onChannelLost\(this\)""").containsMatchIn(hotspot), hotspot)
        assertFalse(hotspot.contains("Looper.getMainLooper(), null)"))
        // 매니페스트에 수신기를 더하지 않았다(내보낸 수신기의 틈 — TetherIsolationTest 도 본다).
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        assertEquals(0, manifest.getElementsByTagName("receiver").length)
    }

    @Test
    fun 바뀜_알림은_합치고_연결마다의_수는_알리지_않는다() {
        val state = code("TetherState.kt")
        assertTrue(Regex("""fun changed\(\) \{\s*coalescer\.changed\(\)\s*\}""").containsMatchIn(state), "changed() 는 합치는 것을 거친다")
        assertFalse(state.contains("main.post {"), "알림마다 메인 스레드에 일을 올리지 않는다")
        val service = code("TetherService.kt")
        val onRejected = service.substringAfter("onRejected = {").substringBefore("},")
        assertFalse(onRejected.contains("changed()"), "거절마다 알리지 않는다 — 화면이 1초마다 읽는다: $onRejected")
    }

    @Test
    fun 패스프레이즈는_보관소_하나로_화면과_서비스가_함께_쓴다() {
        val settings = code("TetherSettings.kt")
        assertFalse(settings.contains("passphraseOrCreate"))
        assertTrue(settings.contains("private val vault = vault(prefs)"), "화면과 서비스가 따로 지은 설정도 프로세스의 보관소 하나를 쓴다")
        assertTrue(settings.contains("shared ?: synchronized(this) { shared ?: PassphraseVault(PrefsStore(prefs)).also { shared = it } }"))
        val service = code("TetherService.kt")
        assertTrue(service.contains("PassphraseVault.Current.Unreadable ->"), "열지 못하면 새 난수로 켜지 않는다")
        val activity = code("TetherActivity.kt")
        assertTrue(activity.contains("val stored = settings.passphrase()"))
    }

    @Test
    fun 패스프레이즈_칸은_자동_완성에서_빠진다() {
        val activity = code("TetherActivity.kt")
        val field = activity.substringAfter("passphrase = field(").substringBefore("passphraseUnreadable =")
        assertTrue(field.contains("importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO"), field)
        assertTrue(field.contains("isSaveEnabled = false"))
    }

    @Test
    fun 상태_줄만_라이브_영역이고_같은_글이면_다시_쓰지_않는다() {
        val activity = code("TetherActivity.kt")
        assertEquals(1, Regex("accessibilityLiveRegion").findAll(activity).count())
        assertTrue(activity.contains("if (status.text.toString() != statusText) status.text = statusText"))
        // 1초마다 그리는 글도 같으면 손대지 않는다 — 같은 글을 넣어도 접근성 이벤트가 나가 화면이 늘 바쁘다.
        assertTrue(activity.contains("if (traffic.text.toString() != trafficText) traffic.text = trafficText"))
        assertTrue(activity.contains("if (toggleButton.text.toString() != toggleText) toggleButton.text = toggleText"))
        assertTrue(activity.contains("if (effectiveSsid.text.toString() != effective) effectiveSsid.text = effective"))
    }

    @Test
    fun 켜기_끄기는_단추_하나다() {
        // 사용자 결정(2026-10-10) — '서버 3종과 핫스팟의 시작/종료를 통합'. 서버 단추와 핫스팟 단추가 '테더링 켜기·끄기' 하나가 되었다.
        val strings = strings()
        for (gone in listOf("tether_server_start", "tether_server_stop", "tether_hotspot_start", "tether_hotspot_stop")) {
            assertFalse(gone in strings, "$gone 이 남았다")
        }
        assertEquals("테더링 켜기", strings["tether_toggle_start"])
        assertEquals("테더링 끄기", strings["tether_toggle_stop"])
        // 두 단추를 가정한 글(‘서버 끄기’ 는 핫스팟도 끕니다 등)이 남지 않았다.
        for ((name, text) in strings) {
            for (old in listOf("서버 켜기", "서버 끄기", "핫스팟 켜기", "핫스팟 끄기")) assertFalse(text.contains(old), "$name: $text")
        }
        val activity = code("TetherActivity.kt")
        assertEquals(1, Regex("""button\(column, R\.string\.tether_toggle_start\)""").findAll(activity).count())
        assertTrue(activity.contains("val on = TetherState.tetheringOn"), "단추·칸 잠금은 하나의 판정으로")
    }

    @Test
    fun 핫스팟이_서지_못하거나_내려가면_테더링을_통째로_끈다() {
        // 핫스팟 없는 서버는 손님을 받지 못한다(NO_GROUP) — 서버까지 내린다(서비스를 멈춘다). 까닭은 상태에 남아 화면이 보인다.
        val service = code("TetherService.kt")
        val end = body(service, "private fun endTethering(")
        assertTrue(end.contains("stopSelfResult(lastStartId)"), end)
        assertTrue(end.contains("TetherState.hotspotProblem = problem"), "까닭을 남긴다: $end")
        val start = body(service, "private fun startHotspot(")
        assertTrue(start.contains("endTethering(HotspotProblem.SECRET_UNREADABLE, null)"), start)
        val failed = start.substringAfter("if (!ok) {").substringBefore("return@start")
        assertTrue(failed.contains("endTethering(hs.problem, hs.detail)"), "서지 못하면(BUSY·시간 넘김·권한 …): $failed")
        val lost = body(service, "private fun hotspotLost(")
        assertTrue(lost.contains("endTethering(HotspotProblem.GROUP_LOST, null)"), "밖에서 내려가면(Wi-Fi 끄기·Channel 잃음): $lost")
        // 꺼진 뒤에도 까닭이 남는다 — onDestroy 는 핫스팟의 까닭을 지우지 않는다(사용자가 끄면 TetherService.stop 이 지운다).
        assertFalse(body(service, "override fun onDestroy(").contains("hotspotProblem = null"))
        // 시작 요청은 모두 켜기 — 서버와 핫스팟을 함께 세운다.
        val command = body(service, "override fun onStartCommand(")
        assertTrue(command.contains("startServersIfNeeded()") && command.contains("startHotspot()"), command)
    }

    @Test
    fun 끄기는_서비스를_멈춘다_화면_타일_알림() {
        assertTrue(body(code("TetherActivity.kt"), "private fun toggle(").contains("TetherService.stop(this)"))
        assertTrue(body(code("HotspotTileService.kt"), "override fun onClick(").contains("TetherService.stop(this)"))
        val service = code("TetherService.kt")
        val stop = body(service, "fun stop(context: Context)")
        assertTrue(stop.contains("context.stopService(Intent(context, TetherService::class.java))"), stop)
        assertTrue(stop.contains("TetherState.clearProblem()"), "사용자가 끈 것은 까닭을 남기지 않는다: $stop")
        // 알림의 단추는 하나 — '테더링 끄기'(서비스에 ACTION_STOP).
        val notice = body(service, "private fun notice(")
        assertEquals(1, Regex("""\.addAction\(""").findAll(notice).count(), notice)
        assertTrue(notice.contains("R.string.tether_toggle_stop") && notice.contains("setAction(ACTION_STOP)"), notice)
        assertTrue(body(service, "override fun onStartCommand(").contains("intent?.action == ACTION_STOP"))
        // 서버·핫스팟을 따로 켜고 끄던 액션은 없다.
        val all = File(DIR).listFiles { f -> f.name.endsWith(".kt") }.orEmpty().joinToString("\n") { code(it.name) }
        for (old in listOf("ACTION_HOTSPOT_ON", "ACTION_HOTSPOT_OFF", "ACTION_TURN_OFF")) assertFalse(all.contains(old), old)
    }

    @Test
    fun 타일의_이름은_테더링이고_들을_때마다_다시_적는다() {
        assertEquals("테더링", strings()["tether_tile"])
        assertTrue(body(code("HotspotTileService.kt"), "private fun syncTile(").contains("tile.label = getString(R.string.tether_tile)"))
    }

    @Test
    fun 포트_설명은_SOCKS5_고정과_HTTP_파일의_다음_포트를_적는다() {
        // 사용자 결정(2026-10-10) — HTTP·파일은 차 있으면 다음 포트로(+1 씩 열 개), SOCKS5 는 1080 하나(PC 의 런처가 1080 만 쓴다).
        val strings = strings()
        val note = strings.getValue("tether_server_note")
        assertTrue(note.contains("SOCKS5 는 1080 고정"), note)
        assertTrue(note.contains("다음 포트"), note)
        assertTrue(note.contains("실제 포트"), note)
        assertTrue(strings.getValue("tether_err_socks_port").contains("옮기지 않습니다"))
        for (name in listOf("tether_err_http_port", "tether_err_file_port")) {
            val text = strings.getValue(name)
            assertTrue(text.contains("%1\$d–%2\$d번을 모두"), "$name: $text")
            assertFalse(text.contains("옮기지"), "$name: $text")
        }
        // 공유 폴더의 설명은 위 상태 줄의 실제 포트를, 다른 기기의 설명은 'PC 연결 파일 받기' 로 받는 zip 의 README.txt 를 가리킨다
        // (포트를 적지 않는다 — HTTP 는 옮겨 설 수 있다. README 가 '실제 포트는 테더링 화면' 이라고 적는다).
        assertTrue(strings.getValue("tether_folder_note").contains("위 상태 줄의 ‘파일’ 값"))
        val pcNote = strings.getValue("tether_pc_note")
        assertTrue(pcNote.contains("README.txt"), pcNote)
        assertFalse(Regex("""\b(8282|1080)\b""").containsMatchIn(pcNote), pcNote)
    }

    @Test
    fun 도착한_인터페이스라는_틀린_말이_남지_않았다() {
        // 셋째 검사는 그룹 오너 주소가 '붙은' 인터페이스를 본다. 연결이 '들어온' 인터페이스가 아니다.
        for ((name, text) in strings()) assertFalse(text.contains("도착한 인터페이스"), "$name: $text")
    }

    @Test
    fun macOS_스크립트는_실행_권한_없이_받아도_sudo_로_다시_뜬다() {
        // 'PC 연결 파일 받기' 의 zip 에 담는 사본(예전에는 저장소의 pc/ 에 있었다). zip 은 실행 비트를 싣지 못한다.
        val bytes = File("src/main/assets/setup/tether-pc/macos-wifi.sh").readBytes()
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.contains("exec sudo /bin/bash \"\$0\" \"\$@\""), "실행 비트가 없으면 sudo \"\$0\" 은 '명령을 찾을 수 없다' 로 실패한다")
        assertFalse(text.contains("exec sudo \"\$0\""))
        assertFalse(bytes.contains('\r'.code.toByte()), "줄 끝은 LF — CR 이 있으면 bash 가 줄 끝의 \\r 을 명령의 일부로 읽는다")
    }

    private fun strings(): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_tether.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        const val DIR = "src/main/kotlin/io/github/donggi/iroirobrowser/tether"
    }
}
