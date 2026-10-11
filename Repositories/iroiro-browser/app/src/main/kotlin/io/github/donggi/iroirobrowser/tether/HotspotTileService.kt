package io.github.donggi.iroirobrowser.tether

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.donggi.iroirobrowser.R

/**
 * 퀵 설정 타일 '테더링' — 테더링을 통째로 켜고 끈다(서버 셋과 Wi-Fi Direct 핫스팟을 함께 — 켜기·끄기는 하나, 사용자 결정 2026-10-10).
 * `:tether` 프로세스 — 상태([TetherState])가 그 프로세스의 정적 필드라서다. 길게 누르면 시스템 UI 가 테더 화면을 연다
 * ([TilePreferencesActivity] — `QS_TILE_PREFERENCES`).
 *
 * 끄기는 서비스를 곧바로 멈춘다(`stopService` — 뒤에서도 막히지 않는다). 켜기는 바로 보내지 못한다 — 이 앱의 화면이 앞에 없으면
 * `createGroup` 이 BUSY 로 실패하므로 내보내지 않은 트램폴린([HotspotStartActivity])을 띄운다. 누른 직후 '쓸 수 없음' 으로 바꾸고 상태가
 * 바뀌기를 기다린다. 이름(테더링)·부제(SSID·켜는 중·꺼짐)는 들을 때마다 다시 적는다 — 이름은 예전 판의 '테더 핫스팟' 을 시스템 UI 가 들고
 * 있을 수 있어서다.
 */
class HotspotTileService : TileService() {

    private val listener: () -> Unit = { syncTile() }

    override fun onStartListening() {
        super.onStartListening()
        TetherState.addListener(listener)
        syncTile()
    }

    override fun onStopListening() {
        TetherState.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (TetherState.tetheringOn) {
            // 끄기 — 핫스팟과 서버를 함께(서비스를 멈춘다). 다 내려가면 상태의 듣개가 타일을 고친다.
            TetherService.stop(this)
            return
        }
        qsTile?.let { tile ->
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
        }
        launchTrampoline()
    }

    /** 내보내지 않은 트램폴린을 띄우고 패널을 접는다. 14 이상은 PendingIntent 판만 된다(Intent 판은 targetSdk 34 부터 던진다). */
    private fun launchTrampoline() {
        val intent = Intent(this, HotspotStartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tether_tile)
        // 그룹이 서기를 기다리는 동안은 '쓸 수 없음'(켜짐은 그룹을 확인한 뒤 — WifiHotspot 의 머리말).
        when {
            TetherState.tetheringOn && TetherState.hotspotActive -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = TetherState.hotspotSsid
            }
            TetherState.tetheringOn -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.tether_tile_starting)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tether_tile_off)
            }
        }
        tile.updateTile()
    }
}
