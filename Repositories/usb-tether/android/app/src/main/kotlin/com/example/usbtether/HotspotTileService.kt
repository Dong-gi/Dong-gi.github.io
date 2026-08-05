package com.example.usbtether

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class HotspotTileService : TileService() {

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TetherService.ACTION_HOTSPOT_STATE_CHANGED) return
            syncTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(TetherService.ACTION_HOTSPOT_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        syncTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterReceiver(stateReceiver)
    }

    override fun onClick() {
        super.onClick()
        // 결과 브로드캐스트를 기다리는 동안 대기 상태를 표시한다.
        qsTile?.let { tile ->
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
        }
        if (TetherService.hotspotActive) {
            // OFF 는 백그라운드에서도 안정적으로 동작한다.
            ContextCompat.startForegroundService(
                this,
                Intent(this, TetherService::class.java).apply {
                    action = TetherService.ACTION_HOTSPOT_OFF
                },
            )
        } else {
            // ON 은 이 앱의 액티비티가 포그라운드에 없으면 계속 실패한다
            // (createGroup BUSY). 그래서 비공개 트램폴린 액티비티를 띄워
            // 그쪽이 resumed 상태에서 ACTION_HOTSPOT_ON 인텐트를 보내도록 한다.
            launchTrampolineForHotspotStart()
        }
    }

    private fun launchTrampolineForHotspotStart() {
        // 명시 인텐트이고 대상이 exported=false 라 같은 앱만 띄울 수 있다.
        val activityIntent = Intent(this, HotspotStartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(activityIntent)
        }
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        tile.state = if (TetherService.hotspotActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
