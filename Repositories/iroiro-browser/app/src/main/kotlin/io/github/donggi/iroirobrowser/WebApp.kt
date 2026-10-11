package io.github.donggi.iroirobrowser

import android.app.Application
import android.content.Context
import android.webkit.WebView
import io.github.donggi.iroirobrowser.adblock.AdBlockService
import io.github.donggi.iroirobrowser.adblock.HiddenElements
import io.github.donggi.iroirobrowser.download.DownloadCenter
import io.github.donggi.iroirobrowser.download.TorrentCenter
import io.github.donggi.iroirobrowser.library.Library
import io.github.donggi.iroirobrowser.net.RelayAuthenticator
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import io.github.donggi.iroirobrowser.proxy.SettingsStore
import io.github.donggi.iroirobrowser.sites.PopupSites
import io.github.donggi.iroirobrowser.sites.SiteList
import java.net.Authenticator

/**
 * 앱 하나에 하나씩인 것: 설정, 네트워크 길(중계), 내려받기, 토렌트, 연 사이트의 목록, 북마크·방문 기록, 광고 차단, 숨긴 요소. **처음 쓸 때 세운다** — 프로세스가 디버그의 실험 화면 때문에 떴을 때 중계를
 * 세우고 출구를 확인하러 나가지 않게.
 */
class WebApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }

    val network: NetworkPath by lazy {
        NetworkPath(this, settings).also {
            // 앱의 HttpURLConnection 이 중계의 407 에 답하게 — 중계에게만(RelayAuthenticator 의 머리말).
            Authenticator.setDefault(RelayAuthenticator { it.relay })
            it.start()
        }
    }

    val downloads: DownloadCenter by lazy { DownloadCenter(this, network) }

    /**
     * 토렌트 — 엔진 하나(프록시를 거치지 않고 기기에서 바로 — 사용자 결정). 웹 쪽 길([network])을 쓰지 않으므로 그것에 기대지 않는다. 엔진은 쓸 때
     * 열고 쓰지 않으면 닫는다([TorrentCenter]).
     */
    val torrents: TorrentCenter by lazy { TorrentCenter(this) }

    val sites: SiteList by lazy { SiteList(this) }

    /** 팝업을 허용한 사이트 — 브라우저 화면이 막을지 묻고, 설정이 푼다. */
    val popups: PopupSites by lazy { PopupSites(this) }

    val library: Library by lazy { Library(this) }

    val adblock: AdBlockService by lazy { AdBlockService(this, network) }

    /** 사용자가 숨긴 요소(메뉴의 '요소 숨기기') — 광고 목록과 따로 둔다. 브라우저 화면이 페이지마다 걸고, 광고 차단 설정이 지운다. */
    val hidden: HiddenElements by lazy { HiddenElements(this) }

    /**
     * 브라우저의 프로세스(주 프로세스 — 이름이 패키지 이름)인가. 테더(`:tether` — 서비스·화면·타일·트램폴린, CLAUDE.md '테더링')는 같은 앱의
     * 따로 된 프로세스라 이 Application 도 그 프로세스에서 한 번 더 뜬다.
     */
    val isMainProcess: Boolean by lazy { Application.getProcessName() == packageName }

    // 가로채기·녹화의 임시 폴더(PageBridge.captureRoot)는 앱이 떠도 지우지 않는다 — 앞 프로세스가 남긴 것은 시스템이 앱을 정리해 저장하지 못한
    // 것이다. 브라우저 화면이 뜰 때 저장할지 묻는다(CaptureRecovery — 앞 판은 여기서 통째로 지워 담은 것이 말없이 사라졌다).
    override fun onCreate() {
        super.onCreate()
        // **브라우저는 주 프로세스에서만 세운다.** 테더 프로세스에는 WebView·중계·프록시 계정·내려받기·토렌트가 올라오지 않는다 — 결함·메모리
        // 고갈이 서로 옮지 않게. 그리고 아래의 고아 정리는 '이 프로세스가 뜨기 전에 생긴' 감춘 항목을 지우므로,
        // 테더 프로세스가 브라우저보다 늦게 뜨며 돌리면 **브라우저가 받는 중인 파일**(아직 목록에 적지 않은 blob·합치기)을 지운다.
        // WebView 는 아예 끈다(`disableWebView` — 그 프로세스에서 WebView 를 쓰면 던진다). 그 밖의 것은 [of] 가 막는다.
        if (!isMainProcess) {
            WebView.disableWebView()
            return
        }
        // 앞 프로세스가 마무리·blob 받기 도중에 죽어 남긴 감춘 '내려받기' 항목 — 목록에 기록이 없는 것만 지운다(디스크·MediaStore 라 일꾼에서).
        Thread({ DownloadCenter.sweepOrphans(this) }, "web-download-sweep").apply { isDaemon = true }.start()
    }

    companion object {
        /**
         * 브라우저의 것(설정·길·내려받기·토렌트·라이브러리·광고 차단 — 위의 lazy 들)으로 가는 하나뿐인 길. **테더 프로세스에서 부르면 던진다** —
         * 그 프로세스의 코드가 브라우저를 세우면(프록시 계정을 열고 중계를 띄우면) 결함이다. 테더의 코드는 이것을 부르지 않는다(앱의 시험
         * TetherIsolationTest 가 소스를 본다).
         */
        fun of(context: Context): WebApp = (context.applicationContext as WebApp).also {
            check(it.isMainProcess) { "브라우저의 것을 테더 프로세스에서 세우려 했다" }
        }
    }
}
