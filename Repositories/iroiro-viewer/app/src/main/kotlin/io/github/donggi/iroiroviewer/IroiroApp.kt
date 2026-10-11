package io.github.donggi.iroiroviewer

import android.app.Application
import android.content.Context
import android.webkit.WebView
import io.github.donggi.iroiroviewer.archive.ArchiveExtractEngine
import io.github.donggi.iroiroviewer.comic.ComicCover
import io.github.donggi.iroiroviewer.data.CrashLog
import io.github.donggi.iroiroviewer.image.ImageCropRenderer
import io.github.donggi.iroiroviewer.io.CropSupport
import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.ExtractSupport
import io.github.donggi.iroiroviewer.playback.PlayerSupport
import io.github.donggi.iroiroviewer.player.PlayerEntry
import io.github.donggi.iroiroviewer.ui.CoverSupport

/**
 * 프로세스 단위의 조립.
 *
 * **여기가 `core:io` 와 `format:archive` 를 잇는 유일한 자리다.** 의존 표상
 * `core:io` 는 `format:*` 을 볼 수 없으므로, 아카이브를 실제로 푸는 구현은
 * `feature:archive` 에 있고 그것을 꽂는 일은 조립 담당인 `app` 이 한다.
 * 자세한 이유는 `ExtractSupport` 의 주석에 있다.
 *
 * 9단계가 같은 자리를 하나 더 쓴다 — 목록의 만화 표지는 `core:ui` 가 그리는데 아카이브를
 * 읽는 코드는 `format:archive` 에 있다. 계층 방향이 같은 문제라 해법도 같다
 * (`CoverSupport`).
 *
 * **10단계가 세 번째를 더했다**(`PlayerSupport`). 미니 바는 `core:playback` 에 있고 큰
 * 재생 화면은 `feature:player` 에 있어 방향이 같은 문제다.
 *
 * **11단계가 네 번째를 더했다**(`DocumentSupport`). 문서 화면은 하나인데 여는 포맷은
 * 여럿일 수 있다 — 화면이 직접 고르면 `feature:*` 이 포맷 모듈을 여럿
 * 보게 되어 의존 표를 어긴다. 무엇을 무엇으로 여는지는 `FormatRegistry` 가 안다.
 *
 * **다섯 번째는 잘라 저장이다**(`CropSupport`). 사용자 파일을 쓰는 일은 `core:io` 의 큐가 하는데, 화면이 본 그대로
 * (EXIF 방향까지) 뜨는 셈은 `core:ui` 에 있어 `core:io` 가 볼 수 없다 — 뜨는 쪽을 `feature:image` 가 만들고 여기서 꽂는다.
 */
class IroiroApp : Application() {

    /**
     * 크래시 기록기(14단계)를 **여기서** 건다 — `onCreate` 보다 앞이라 콘텐츠 제공자(androidx.startup·
     * profileinstaller)의 초기화에서 나는 죽음도 잡힌다. 쓸 수 있는 것은 넘겨받은 [base] 뿐이다. 판 이름은
     * `BuildConfig` 가 `app` 에만 있어 여기서 넘긴다. 기록기는 앞의 처리기(안드로이드의 '앱이 멈췄습니다')를 반드시 잇는다.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(base, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
    }

    override fun onCreate() {
        super.onCreate()
        // 편집기(`feature:editor`)는 따로 된 프로세스(:editor)에서 돈다. 그 프로세스는 WebView 의 자료 폴더를 따로 쓰고 —
        // 한 앱의 두 프로세스가 같은 폴더로 WebView 를 쓰면 WebView 가 던진다(문서 뷰어의 WebView 는 주 프로세스에 있다) — 아래의 조립·정리를
        // 하지 않는다. 받은 사본 치우기는 주 프로세스의 재생기가 든 파일을 모르고 지울 수 있고, 나머지는 그 프로세스에 쓸 데가 없다.
        if (Application.getProcessName() == packageName + EditorApp.PROCESS) {
            WebView.setDataDirectorySuffix(EDITOR_WEBVIEW_DIR)
            return
        }
        ExtractSupport.runner = ArchiveExtractEngine(this)
        CoverSupport.provider = ComicCover
        PlayerSupport.launcher = PlayerEntry
        // 재생 화면이 태스크의 뿌리인 채 뒤로 가면 파일 목록을 연다 — 그냥 끝내면 홈으로 떨어진다(사용자가 짚었다).
        PlayerSupport.fileListOpener = FileListLauncher
        // 잘라 저장의 뜨는 쪽(화면이 본 그대로 — `core:ui` 를 아는 `feature:image`). 쓰는 쪽은 `core:io` 다(`CropSupport`).
        CropSupport.renderer = ImageCropRenderer
        FormatRegistry.install()
        // 아카이브 암호는 이번 세션 동안만 — 앱이 화면에서 사라지면 지운다.
        io.github.donggi.iroiroviewer.io.SessionPasswords.install(this)
        // 다른 앱이 넘겨 재생기에 맡긴 사본은 화면이 끝나도 남는다. 프로세스가 설 때 하루 지난 것을 치운다 — 받을 때만 치우면
        // 다음에 무엇을 받기 전까지 수 GB 의 영상 사본이 남는다. 이때는 아무것도 재생하고 있지 않다.
        io.github.donggi.iroiroviewer.io.IncomingFiles.purgeStaleLater(this)
        // 걷어 낸 네트워크 저장소(SFTP, 2026-10-05 ~ 10-09)가 남긴 것 — 잠근 열쇠 파일·기기의 열쇠·받아 둔 사본. 한 번 지우면 다시 할 일이 없다.
        LegacyCleanup.removeSftpLeftoversLater(this)
    }

    private companion object {
        /** 편집기 프로세스의 WebView 자료 폴더 꼬리(`app_webview_editor`). */
        const val EDITOR_WEBVIEW_DIR = "editor"
    }
}
