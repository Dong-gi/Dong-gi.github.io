package com.example.usbtether

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log

/**
 * Wi-Fi P2P group owner — clients join via the GO's SSID/passphrase and get
 * an IP in 192.168.49.0/24 with gateway 192.168.49.1 (this phone). The proxy
 * servers (SOCKS5 on 1080, HTTP on 8282) listen on 0.0.0.0 so any joined
 * client can reach them.
 *
 * Requires API 29+ for custom SSID/passphrase (setNetworkName / setPassphrase).
 * Permissions: NEARBY_WIFI_DEVICES (API 33+) or ACCESS_FINE_LOCATION (29–32),
 * plus CHANGE_WIFI_STATE.
 */
class WifiHotspot(
    context: Context,
    requestedSsid: String,
    private val passphrase: String,
) {
    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)

    @Volatile var lastError: String? = null
        private set
    @Volatile var active: Boolean = false
        private set

    @Volatile var displaySsid: String = normalizeSsid(requestedSsid)
        private set

    @SuppressLint("MissingPermission")
    fun start(onResult: (Boolean) -> Unit) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            lastError = "Wi-Fi P2P not available on this device"
            onResult(false); return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            lastError = "Custom SSID requires Android 10+"
            onResult(false); return
        }
        if (passphrase.length !in 8..63) {
            lastError = "Passphrase must be 8–63 chars"
            onResult(false); return
        }
        val config = try {
            WifiP2pConfig.Builder()
                .setNetworkName(displaySsid)
                .setPassphrase(passphrase)
                .enablePersistentMode(false)
                .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
                .build()
        } catch (e: Exception) {
            lastError = "Invalid SSID/passphrase: ${e.message}"
            onResult(false); return
        }
        try {
            mgr.createGroup(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    active = true
                    lastError = null
                    Log.i(TAG, "Wi-Fi P2P group up: $displaySsid")
                    onResult(true)
                }
                override fun onFailure(reason: Int) {
                    if (reason == WifiP2pManager.BUSY) {
                        reuseExistingGroup(mgr, ch, onResult)
                    } else {
                        lastError = "createGroup failed (${reasonName(reason)})"
                        Log.w(TAG, lastError!!)
                        onResult(false)
                    }
                }
            })
        } catch (e: SecurityException) {
            lastError = "Missing NEARBY_WIFI_DEVICES / location permission"
            Log.w(TAG, lastError!!, e)
            onResult(false)
        } catch (e: Exception) {
            lastError = "createGroup threw: ${e.message}"
            Log.e(TAG, lastError!!, e)
            onResult(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun reuseExistingGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        onResult: (Boolean) -> Unit,
    ) {
        mgr.requestGroupInfo(ch) { group ->
            if (group != null && group.isGroupOwner) {
                displaySsid = group.networkName
                active = true
                lastError = null
                Log.i(TAG, "Reusing existing Wi-Fi P2P group: $displaySsid")
                onResult(true)
            } else {
                lastError = "createGroup failed (BUSY) — no existing group to reuse"
                Log.w(TAG, lastError!!)
                onResult(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.removeGroup(ch, null)
        } catch (e: Exception) {
            Log.w(TAG, "removeGroup failed: ${e.message}")
        }
        active = false
    }

    companion object {
        private const val TAG = "WifiHotspot"

        /** Wi-Fi Direct GO requires the SSID to begin with "DIRECT-xx-" where xx is two chars. */
        fun normalizeSsid(raw: String): String {
            val cleaned = raw.trim().ifEmpty { "USBTether" }
            return if (cleaned.startsWith("DIRECT-")) cleaned else "DIRECT-UT-$cleaned"
        }

        private fun reasonName(reason: Int): String = when (reason) {
            WifiP2pManager.ERROR -> "ERROR"
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
            else -> "code=$reason"
        }
    }
}
