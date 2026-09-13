package io.github.donggi.iroiroviewer

import android.app.Application
import io.github.donggi.iroiroviewer.archive.ArchiveExtractEngine
import io.github.donggi.iroiroviewer.comic.ComicCover
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
 */
class IroiroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ExtractSupport.runner = ArchiveExtractEngine(this)
        CoverSupport.provider = ComicCover
        PlayerSupport.launcher = PlayerEntry
    }
}
