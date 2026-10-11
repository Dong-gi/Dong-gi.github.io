package io.github.donggi.iroirobrowser.tether

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 퀵 설정 타일 '테더링' 을 **길게 누를 때** 시스템 UI 가 여는 화면(`TileService.ACTION_QS_TILE_PREFERENCES` — API 24) — 테더 화면
 * ([TetherActivity])을 열고 곧바로 끝난다(사용자 요청 2026-10-10 — 앱 바로가기 '테더링' 대신). `:tether` 프로세스(테더의 구성 요소는 모두
 * 그 프로세스다 — TetherState).
 *
 * ## 왜 내보낸 화면이 따로 있는가
 *
 * 시스템 UI 는 그 액션을 이 꾸러미로 좁혀 찾은 화면을 **자기 uid 로** 띄운다 — 내보내지 않은 화면은 띄우지 못한다(찾지 못하면 앱 정보 화면을
 * 연다). 테더 화면은 내보내지 않는다(다른 앱이 그 화면을 띄울 까닭이 없다 — 그 화면의 머리말). 그래서 내보내는 것은 이 작은 화면 하나이고,
 * 이것은 **받은 인텐트를 읽지 않는다**(액션·데이터·덧붙임 — 시스템 UI 가 싣는 타일 이름과 상태도). 누가 띄우든 테더 화면을 여는 것 밖에 하지
 * 않는다 — 핫스팟을 켜거나 서버를 세우지 않는다(그 일은 내보내지 않은 [HotspotStartActivity] 와 테더 화면의 단추만 한다 — 아무 앱이나 무선
 * 공격면을 열지 못하게).
 *
 * 테더 화면은 제 작업(taskAffinity)에 연다(NEW_TASK — 이미 떠 있으면 그 작업을 앞으로 가져온다). 이 화면은 보이지 않고(`Theme.NoDisplay` —
 * onCreate 에서 끝내므로 그 테마의 조건을 지킨다) 최근 앱에 남지 않는다.
 */
class TilePreferencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TetherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
