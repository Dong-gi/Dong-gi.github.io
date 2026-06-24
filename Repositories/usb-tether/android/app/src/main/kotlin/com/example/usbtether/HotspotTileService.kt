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
        // Show pending state while waiting for the result broadcast.
        qsTile?.let { tile ->
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
        }
        if (TetherService.hotspotActive) {
            // OFF works reliably from the background.
            ContextCompat.startForegroundService(
                this,
                Intent(this, TetherService::class.java).apply {
                    action = TetherService.ACTION_HOTSPOT_OFF
                },
            )
        } else {
            // ON consistently fails (createGroup BUSY) when MainActivity isn't in
            // the foreground, so hand off to MainActivity and let it issue the
            // ACTION_HOTSPOT_ON intent while it's resumed.
            launchMainActivityForHotspotStart()
        }
    }

    private fun launchMainActivityForHotspotStart() {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_HOTSPOT_FROM_TILE
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
