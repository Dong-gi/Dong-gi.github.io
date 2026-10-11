package io.github.donggi.iroirobrowser.tether

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import io.github.donggi.iroirobrowser.R

/**
 * 퀵 설정 타일이 테더링을 켤 때 거쳐 가는 **보이지 않는 트램폴린** — `:tether` 프로세스, `exported="false"`. 켜기는 서버 셋과 핫스팟을 함께
 * 세운다(켜기·끄기는 하나 — 사용자 결정 2026-10-10). 끄기는 타일이 서비스를 곧바로 멈춘다(멈추기는 뒤에서도 막히지 않는다 — 이 화면을 거치지
 * 않는다).
 *
 * ## 왜 따로 된 화면인가
 *
 * `WifiP2pManager.createGroup` 은 이 앱의 화면이 앞에 있지 않으면 계속 BUSY 로 실패한다(usb-tether 때 실기기에서) — 그래서 타일이 서비스를 바로
 * 부르지 못하고 화면을 거친다. 그 일을 **내보낸 화면이 맡으면 안 된다**: usb-tether 의 예전 판은 런처 화면이 인텐트의 액션만 보고 핫스팟을
 * 켰고, `startActivity` 에는 권한이 필요 없어 기기의 **아무 앱이나** 사용자 모르게 무선 공격면을 열 수 있었다. 이 화면은 내보내지 않아 같은
 * 앱(타일)만 띄운다. 테더 화면([TetherActivity])도 내보내지 않는다(Web 의 런처는 브라우저 화면이다). 타일을 길게 누를 때 시스템 UI 가 여는
 * 화면([TilePreferencesActivity])은 내보내야 해서 따로 두었고, 그것은 테더 화면을 열기만 한다 — 아무것도 켜지 않는다.
 *
 * 투명 테마 + `noHistory` + `excludeFromRecents` 로 보이지 않게 한다(`Theme.NoDisplay` 는 onResume 이 끝나기 전에 finish 하지 않으면 죽는다).
 * 시작은 [onResume] 에서 — `createGroup` 이 돌 때 화면이 실제로 앞에 있어야 한다.
 *
 * 권한(위치·근처 기기)이 없으면 켜지 않고 테더 화면을 연다 — 권한은 그 화면에서 사용자가 '테더링 켜기' 를 누를 때만 청한다.
 */
class HotspotStartActivity : Activity() {

    override fun onResume() {
        super.onResume()
        when {
            // 이미 켜져 있으면(켜는 중 포함) 타일이 '쓸 수 없음' 에 걸려 있지 않게 알리기만 한다.
            TetherState.tetheringOn -> TetherState.changed()
            !TetherPermissions.hasHotspotPermission(this) -> {
                Toast.makeText(this, R.string.tether_tile_needs_permission, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, TetherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                TetherState.changed()
            }
            !TetherService.start(this) -> TetherState.changed()
        }
        finish()
    }
}
