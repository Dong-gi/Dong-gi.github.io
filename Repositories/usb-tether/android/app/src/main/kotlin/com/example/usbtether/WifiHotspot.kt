package com.example.usbtether

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log

/**
 * Wi-Fi P2P 그룹 오너(GO). 클라이언트는 GO 의 SSID/패스프레이즈로 접속해
 * 192.168.49.0/24 대역의 IP 를 받고, 게이트웨이는 이 폰(192.168.49.1)이 된다.
 * 프록시 서버(SOCKS5 1080, HTTP 8282)가 `0.0.0.0` 에서 듣고 있으므로 접속한
 * 클라이언트는 모두 도달할 수 있다.
 *
 * 커스텀 SSID/패스프레이즈(setNetworkName / setPassphrase)는 API 29 이상에서만
 * 동작한다.
 * 필요 권한: NEARBY_WIFI_DEVICES (API 33+) 또는 ACCESS_FINE_LOCATION (29–32),
 * 그리고 CHANGE_WIFI_STATE.
 */
class WifiHotspot(
    context: Context,
    requestedSsid: String,
    private val passphrase: String,
) {
    private val appContext: Context = context.applicationContext
    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(appContext, Looper.getMainLooper(), null)

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
        // 프레임워크가 두루뭉술한 ERROR 코드를 돌려주기 전에, createGroup 실패의
        // 흔한 원인을 조치 가능한 메시지로 먼저 드러낸다.
        val envError = checkEnvironment()
        if (envError != null) {
            lastError = envError
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
        verifyP2pStateThenCreate(mgr, ch, config, onResult)
    }

    /** 동기적 환경 사전 점검. 문제가 있으면 오류 메시지를, 없으면 null 을 반환한다. */
    private fun checkEnvironment(): String? {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm != null && !wm.isWifiEnabled) {
            return "Wi-Fi is off — turn Wi-Fi on to use the hotspot"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null && !lm.isLocationEnabled) {
                return "Location services are off — required by Android for Wi-Fi Direct"
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun verifyP2pStateThenCreate(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        config: WifiP2pConfig,
        onResult: (Boolean) -> Unit,
    ) {
        try {
            mgr.requestP2pState(ch) { state ->
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    lastError = "Wi-Fi Direct is disabled — turn off Mobile Hotspot or any app using Wi-Fi Direct, then try again"
                    onResult(false)
                } else {
                    tryCreateGroup(mgr, ch, config, onResult)
                }
            }
        } catch (e: Exception) {
            // 상태 조회가 오작동하면 그냥 createGroup 으로 진행한다.
            Log.w(TAG, "requestP2pState threw: ${e.message}")
            tryCreateGroup(mgr, ch, config, onResult)
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        config: WifiP2pConfig,
        onResult: (Boolean) -> Unit,
    ) {
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
                        lastError = friendlyFailureMessage(reason)
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

        /** Wi-Fi Direct GO 규격상 SSID 는 "DIRECT-xx-"(xx 는 두 글자)로 시작해야 한다. */
        fun normalizeSsid(raw: String): String {
            val cleaned = raw.trim().ifEmpty { "USBTether" }
            return if (cleaned.startsWith("DIRECT-")) cleaned else "DIRECT-UT-$cleaned"
        }

        private fun friendlyFailureMessage(reason: Int): String = when (reason) {
            WifiP2pManager.ERROR ->
                "createGroup failed (ERROR) — Mobile Hotspot or another Wi-Fi Direct app may be active. Turn them off and retry."
            WifiP2pManager.P2P_UNSUPPORTED ->
                "Wi-Fi Direct is not supported on this device"
            WifiP2pManager.NO_SERVICE_REQUESTS ->
                "createGroup failed (NO_SERVICE_REQUESTS)"
            else ->
                "createGroup failed (code=$reason)"
        }
    }
}
