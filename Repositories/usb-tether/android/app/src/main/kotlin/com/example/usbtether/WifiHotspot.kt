package com.example.usbtether

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
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
 *
 * **이 클래스는 1회용이다.** [stop] 이 `WifiP2pManager.Channel` 을 닫으므로 같은
 * 인스턴스로 다시 [start] 할 수 없다. 켤 때마다 새로 만들어 쓴다.
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
        if (passphrase.length !in MIN_PASSPHRASE..MAX_PASSPHRASE) {
            lastError = "Passphrase must be 8–63 chars"
            onResult(false); return
        }
        // 접두사까지 합쳐 32바이트를 넘으면 프레임워크가 던지고, 그 예외는 아래에서
        // 두루뭉술한 "Invalid SSID/passphrase" 가 된다. 여기서 미리 짚어 준다.
        if (displaySsid.toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES) {
            lastError = "SSID too long — name plus the $OWN_SSID_PREFIX prefix must fit in " +
                "$MAX_SSID_BYTES bytes"
            onResult(false); return
        }
        // 길이만 보면 12345678 같은 값이 통과한다. 그룹에 들어온 기기는 프록시와
        // 폰의 로컬 서비스에 바로 접근하므로, 명백히 약한 값은 여기서 막는다.
        if (isWeakPassphrase(passphrase)) {
            lastError = "Passphrase is too easy to guess — use the generated one"
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

    /**
     * `createGroup` 이 BUSY 로 실패했을 때, 이미 떠 있는 그룹이 **우리가 만들려던
     * 바로 그 그룹인지** 확인한 뒤에만 재사용한다.
     *
     * 이전에는 `group.isGroupOwner` 만 보고 무조건 채택해 성공을 보고했다.
     * 그래서 두 가지가 가능했다.
     *
     *  - **UI 와 실제 무선망의 자격증명 불일치.** 입력란에는 이 앱이 생성한
     *    116비트 패스프레이즈가 보이는데 실제로 떠 있는 그룹은 다른(사용자가 알 수
     *    없는) 패스프레이즈를 쓴다. 접속되지 않는 이유를 알 수 없고, 더 나쁘게는
     *    약한 패스프레이즈로 떠 있는 그룹을 강한 것으로 착각한다 —
     *    [isWeakPassphrase] 검사가 그대로 우회된다.
     *  - **남의 그룹 흡수.** 다른 앱이 만든 P2P 그룹도 채택되고, 이후 [stop] 이
     *    그 그룹을 내려버린다.
     *
     * 이제 SSID 와 패스프레이즈가 **모두** 일치할 때만 재사용한다. 확인할 수 없는
     * 경우(패스프레이즈 조회가 null 이거나 던지는 경우)도 불일치로 취급한다 —
     * 판단할 수 없을 때 안전한 쪽은 재사용하지 않는 쪽이다.
     */
    @SuppressLint("MissingPermission")
    private fun reuseExistingGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        onResult: (Boolean) -> Unit,
    ) {
        mgr.requestGroupInfo(ch) { group ->
            when {
                group == null || !group.isGroupOwner -> {
                    lastError = "createGroup failed (BUSY) — no existing group to reuse"
                    Log.w(TAG, lastError!!)
                    onResult(false)
                }
                !isOwnGroup(group) -> {
                    lastError = "Another Wi-Fi Direct group is already up with different " +
                        "credentials — stop that app (or toggle Wi-Fi off and on) and retry"
                    Log.w(TAG, lastError!!)
                    onResult(false)
                }
                else -> {
                    lastError = null
                    Log.i(TAG, "Reusing our own Wi-Fi P2P group: $displaySsid")
                    onResult(true)
                }
            }
        }
    }

    /**
     * 이미 떠 있는 그룹이 이 인스턴스가 만들려던 그룹과 같은지.
     *
     * 패스프레이즈 조회는 OEM 프레임워크나 권한 상태에 따라 null 을 주거나 던질 수
     * 있다. 그 경우 null 이 되어 불일치로 판정된다(fail-safe).
     */
    @SuppressLint("MissingPermission")
    private fun isOwnGroup(group: WifiP2pGroup): Boolean {
        val livePassphrase = runCatching { group.passphrase }.getOrNull()
        return group.networkName == displaySsid && livePassphrase == passphrase
    }

    /**
     * 그룹을 내리고 이 인스턴스가 쥔 [WifiP2pManager.Channel] 을 닫는다.
     *
     * Channel 은 인스턴스마다 `initialize()` 로 새로 만들어지고 재사용되지 않는다
     * ([TetherService] 는 `ACTION_HOTSPOT_ON` 마다 새 `WifiHotspot` 을 만든다).
     * 닫지 않으면 **핫스팟 on/off 1회당 하나씩**, 그리고 실패한 기동 시도마다
     * 하나씩 프레임워크와의 바인더 연결이 프로세스 수명 동안 쌓인다.
     *
     * `removeGroup` 의 결과도 버리지 않는다. 실패하면 P2P 그룹이 살아 있는데
     * UI·타일만 "off" 가 되므로, 원인을 [lastError] 에 남기고 [onResult] 로
     * 알린다. 성공이든 실패든 이 인스턴스는 버려지므로 Channel 은 닫는다.
     */
    @SuppressLint("MissingPermission")
    fun stop(onResult: ((Boolean) -> Unit)? = null) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            onResult?.invoke(true)
            return
        }
        try {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    closeChannel(ch)
                    onResult?.invoke(true)
                }

                override fun onFailure(reason: Int) {
                    lastError =
                        "removeGroup failed (code=$reason) — the Wi-Fi Direct group may still be up"
                    Log.w(TAG, lastError!!)
                    closeChannel(ch)
                    onResult?.invoke(false)
                }
            })
        } catch (e: Exception) {
            lastError = "removeGroup threw: ${e.message}"
            Log.w(TAG, lastError!!)
            closeChannel(ch)
            onResult?.invoke(false)
        }
    }

    /**
     * 그룹을 만들지 못한 인스턴스를 버릴 때 호출한다. **Channel 만 닫고
     * `removeGroup` 은 하지 않는다.**
     *
     * Channel 은 생성자에서 만들어지므로 [start] 가 사전 점검 단계에서 실패해도
     * 이미 하나를 쥐고 있다("Wi-Fi is off", "Location off", BUSY 불일치 등 재시도를
     * 반복하게 되는 오류들이 전부 이 경로다). 그 인스턴스를 그냥 버리면 시도마다
     * 하나씩 누수된다.
     *
     * 이 경우 [stop] 을 쓰면 안 된다. 이 인스턴스는 그룹을 만든 적이 없으므로
     * `removeGroup` 이 내리는 것은 **다른 앱이 만든 그룹**일 수 있다. 그것을 건드리지
     * 않는 것이 [reuseExistingGroup] 의 자격증명 대조와 같은 취지다.
     */
    fun release() {
        channel?.let { closeChannel(it) }
    }

    /**
     * Channel 을 닫는다.
     *
     * `Channel` 이 `AutoCloseable` 이 된 것은 API 27 이고 minSdk 는 26 이므로
     * 버전 가드가 필요하다. API 26 에서는 닫을 수단이 없어 그대로 둔다.
     */
    private fun closeChannel(ch: WifiP2pManager.Channel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        try {
            ch.close()
        } catch (e: Exception) {
            Log.w(TAG, "channel close failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WifiHotspot"

        /** WPA2 규격상 패스프레이즈 길이 범위. */
        private const val MIN_PASSPHRASE = 8
        private const val MAX_PASSPHRASE = 63

        /** SSID(네트워크 이름) 최대 길이. 802.11 의 SSID 요소가 32옥텟이다. */
        private const val MAX_SSID_BYTES = 32

        /**
         * 자주 쓰이는 취약 패스프레이즈. 완전한 사전이 아니라 명백한 것만 막는다.
         * 사전 공격을 전부 방어하려는 것이 아니라, 기본값을 그대로 쓰는 사고를
         * 방지하는 것이 목적이다.
         */
        private val WEAK_PASSPHRASES = setOf(
            "12345678", "123456789", "1234567890", "87654321",
            "password", "passw0rd", "p@ssword", "abcdefgh",
            "qwertyui", "asdfghjk", "iloveyou", "usbtether",
            "00000000", "11111111", "aaaaaaaa", "hotspot1",
        )

        /**
         * 명백히 추측하기 쉬운 패스프레이즈인지 판정한다.
         *
         * 세 가지를 본다: 알려진 취약 목록, 서로 다른 문자가 2종 이하, 전체가
         * 오름차순/내림차순 연속(12345678, abcdefgh 등).
         */
        internal fun isWeakPassphrase(value: String): Boolean {
            if (value.lowercase() in WEAK_PASSPHRASES) return true
            if (value.toSet().size <= 2) return true
            return isMonotonicSequence(value)
        }

        /** 인접 문자의 코드 차이가 전부 +1 이거나 전부 -1 인지. */
        private fun isMonotonicSequence(value: String): Boolean {
            if (value.length < 2) return false
            val step = value[1].code - value[0].code
            if (step != 1 && step != -1) return false
            return value.zipWithNext().all { (a, b) -> b.code - a.code == step }
        }

        /** 입력이 비어 있을 때 쓸 이름. 접두사는 [normalizeSsid] 가 붙인다. */
        private const val DEFAULT_SSID_BODY = "USBTether"

        /** 이 앱이 붙이는 접두사. `DIRECT-` + 영숫자 두 글자 규격을 만족한다. */
        private const val OWN_SSID_PREFIX = "DIRECT-UT-"

        /** 규격을 만족하는 접두사: `DIRECT-` 다음에 영숫자 두 글자. */
        private val DIRECT_PREFIX = Regex("^DIRECT-[A-Za-z0-9]{2}")

        /**
         * Wi-Fi Direct 규격에 맞는 네트워크 이름으로 정규화한다.
         *
         * 규격은 `DIRECT-` 다음에 **영숫자 두 글자**를 요구한다. 이전 코드는
         * `startsWith("DIRECT-")` 만 봤으므로 `DIRECT-`, `DIRECT-a`, `DIRECT-!!`
         * 처럼 규격을 어기는 값이 그대로 통과해 프레임워크의 두루뭉술한
         * "Invalid SSID/passphrase" 로만 실패했다. 두 글자를 요구한다고 적어 둔
         * KDoc 을 코드가 따라가지 못한 쪽이었다.
         *
         * 이제 [DIRECT_PREFIX] 를 만족할 때만 입력을 그대로 쓰고, 아니면
         * [OWN_SSID_PREFIX] 를 붙인다. 규격 미달인 값을 통과시키는 것보다 접두사를
         * 붙이는 쪽이 안전하다 — `DIRECT-a` 는 `DIRECT-UT-DIRECT-a` 가 되어 보기에
         * 이상하지만 동작한다.
         */
        fun normalizeSsid(raw: String): String {
            val cleaned = raw.trim().ifEmpty { DEFAULT_SSID_BODY }
            return if (DIRECT_PREFIX.containsMatchIn(cleaned)) cleaned else "$OWN_SSID_PREFIX$cleaned"
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
