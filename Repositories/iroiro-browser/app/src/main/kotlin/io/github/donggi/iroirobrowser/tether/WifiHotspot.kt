package io.github.donggi.iroirobrowser.tether

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.donggi.iroirobrowser.tether.core.GroupWatch
import io.github.donggi.iroirobrowser.tether.core.Passphrases

/**
 * Wi-Fi Direct 그룹 오너(GO) — 손님은 이 SSID·패스프레이즈로 붙어 192.168.49.0/24 의 주소를 받고, 게이트웨이는 이 폰(192.168.49.1)이다. 서버
 * 셋이 `0.0.0.0` 에서 듣고 [io.github.donggi.iroirobrowser.tether.core.PeerFilter] 가 **이 그룹의 인터페이스**([groupInterface])로 들어온 손님만
 * 받는다.
 *
 * 사용자가 정한 SSID·패스프레이즈(`WifiP2pConfig.Builder` — API 29)로 만든다. 권한: Android 13 이상은 `NEARBY_WIFI_DEVICES`(neverForLocation),
 * 12 이하는 **정확한 위치**(`ACCESS_FINE_LOCATION`) — 그리고 `CHANGE_WIFI_STATE`. 권한을 청하는 것은 테더 화면에서 테더링을 켤 때뿐이다.
 *
 * ## 켜짐은 그룹이 선 것을 확인한 뒤에
 *
 * `createGroup` 의 성공은 '요청을 받았다' 일 뿐 그룹이 섰다는 뜻이 아니다. 예전에는 그것으로 '켜짐' 이라 했고 인터페이스 이름도 몰랐다. 이제
 * 성공 뒤에 그룹 조회(`requestGroupInfo`)를 [FORMATION_POLL_MS] 마다 되풀이해 **우리 그룹**([GroupWatch])이 인터페이스와 함께 보이면 켜짐이다
 * (서비스가 받는 연결 변경 방송이 먼저 알려 주면 [noteGroup] 으로 더 일찍). [FORMATION_TIMEOUT_MS] 안에 서지 않으면 그룹을 내리고 실패다.
 *
 * ## 밖에서 내려가는 것
 *
 * Wi-Fi 를 끄거나 다른 앱이 Wi-Fi Direct 를 가져가면 그룹이 밖에서 내려간다. 서비스가 연결 변경 방송으로 알고 [confirm] 으로 확인하며, 프레임
 * 워크와의 Channel 을 잃는 것은 여기서 [onChannelLost] 로 알린다(예전에는 `initialize(…, null)` 이라 몰랐다).
 *
 * **한 번 쓰고 버린다.** [stop] 이 `WifiP2pManager.Channel` 을 닫으므로 같은 인스턴스로 다시 [start] 하지 못한다 — 켤 때마다 새로 만든다.
 * 오류는 기계 말([problem]·[detail])로 남기고 글은 화면이 고른다([TetherTexts.hotspot]) — usb-tether 는 영어 글을 그대로 보였다.
 *
 * @param onChannelLost 프레임워크와의 Channel 이 끊겼다(메인 스레드). 우리가 닫은 것은 알리지 않는다.
 */
internal class WifiHotspot(
    context: Context,
    private val requestedSsid: String,
    private val passphrase: String,
    private val onChannelLost: (WifiHotspot) -> Unit,
) {

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 우리가 Channel 을 닫았다 — 그 끊김은 '잃음' 이 아니다(닫기도 듣개를 부른다). */
    @Volatile
    private var closed = false

    private val manager: WifiP2pManager? = appContext.getSystemService(WifiP2pManager::class.java)
    private val channel: WifiP2pManager.Channel? = manager?.initialize(appContext, Looper.getMainLooper()) {
        if (!closed) onChannelLost(this)
    }

    @Volatile
    var problem: HotspotProblem? = null
        private set

    @Volatile
    var detail: String? = null
        private set

    /** 실제 네트워크 이름(앞머리 포함 — [Passphrases.normalizeSsid]). */
    val displaySsid: String = Passphrases.normalizeSsid(requestedSsid)

    /** 우리 그룹의 인터페이스(`WifiP2pGroup.getInterface()`) — 그룹이 선 것을 확인했을 때만. */
    @Volatile
    var groupInterface: String? = null
        private set

    /** [start] 의 결과를 기다리는 쪽 — 그룹이 서기를 기다리는 동안만 있다(한 번만 부른다). */
    private var pendingStart: ((Boolean) -> Unit)? = null

    /** 그룹을 만든다. 결과는 메인 스레드에서(사전 점검의 실패는 그 자리에서) [onResult] — true 는 우리 그룹이 선 것을 확인했다는 뜻이다. */
    @SuppressLint("MissingPermission") // 권한은 화면이 켤 때 청한다 — 없으면 SecurityException 을 PERMISSION 으로 알린다(tryCreateGroup)
    fun start(onResult: (Boolean) -> Unit) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            fail(HotspotProblem.P2P_UNAVAILABLE)
            onResult(false)
            return
        }
        // 길이만 보면 12345678 같은 값이 지나간다. 그룹에 들어온 기기는 프록시와 공유 폴더에 인증 없이 닿으므로 뻔한 값은 여기서 막는다.
        Passphrases.check(requestedSsid, passphrase)?.let {
            fail(TetherTexts.credential(it))
            onResult(false)
            return
        }
        // 프레임워크가 두루뭉술한 ERROR 를 돌려주기 전에, 흔한 까닭을 할 수 있는 말로 먼저 드러낸다.
        checkEnvironment()?.let {
            fail(it)
            onResult(false)
            return
        }
        val config = try {
            WifiP2pConfig.Builder()
                .setNetworkName(displaySsid)
                .setPassphrase(passphrase)
                .enablePersistentMode(false)
                .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
                .build()
        } catch (e: Exception) {
            fail(HotspotProblem.INVALID_CONFIG, e.message ?: e.javaClass.simpleName)
            onResult(false)
            return
        }
        verifyP2pStateThenCreate(mgr, ch, config, onResult)
    }

    /**
     * 그 자리의 환경 점검. Wi-Fi 가 꺼져 있으면 Wi-Fi Direct 그룹을 만들 수 없다(인터넷 연결은 필요 없다). 위치 서비스가 꺼져 있으면 안드로이드가
     * Wi-Fi Direct 를 막는다 — usb-tether 의 안내를 그대로 둔다(13 이상의 neverForLocation 에서도 필요한지는 확인하지 않았다).
     */
    private fun checkEnvironment(): HotspotProblem? {
        val wifi = appContext.getSystemService(WifiManager::class.java)
        if (wifi != null && !wifi.isWifiEnabled) return HotspotProblem.WIFI_OFF
        val location = appContext.getSystemService(LocationManager::class.java)
        if (location != null && !location.isLocationEnabled) return HotspotProblem.LOCATION_OFF
        return null
    }

    @SuppressLint("MissingPermission")
    private fun verifyP2pStateThenCreate(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, config: WifiP2pConfig, onResult: (Boolean) -> Unit) {
        try {
            mgr.requestP2pState(ch) { state ->
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    fail(HotspotProblem.P2P_DISABLED)
                    onResult(false)
                } else {
                    tryCreateGroup(mgr, ch, config, onResult)
                }
            }
        } catch (e: Exception) {
            // 상태 조회가 이상하면 그냥 그룹 만들기로 간다.
            Log.w(TAG, "requestP2pState 가 던졌다: ${e.javaClass.simpleName}")
            tryCreateGroup(mgr, ch, config, onResult)
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, config: WifiP2pConfig, onResult: (Boolean) -> Unit) {
        try {
            mgr.createGroup(
                ch, config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Wi-Fi Direct 그룹 만들기를 청했다 — 서기를 기다린다")
                        pendingStart = onResult
                        awaitFormation(mgr, ch, System.nanoTime())
                    }

                    override fun onFailure(reason: Int) {
                        if (reason == WifiP2pManager.BUSY) {
                            reuseExistingGroup(mgr, ch, onResult)
                        } else {
                            when (reason) {
                                WifiP2pManager.ERROR -> fail(HotspotProblem.CREATE_ERROR)
                                WifiP2pManager.P2P_UNSUPPORTED -> fail(HotspotProblem.UNSUPPORTED)
                                else -> fail(HotspotProblem.CREATE_FAILED, reason.toString())
                            }
                            Log.w(TAG, "createGroup 실패: $reason")
                            onResult(false)
                        }
                    }
                },
            )
        } catch (e: SecurityException) {
            fail(HotspotProblem.PERMISSION)
            Log.w(TAG, "권한이 없다")
            onResult(false)
        } catch (e: Exception) {
            fail(HotspotProblem.CREATE_THREW, e.javaClass.simpleName)
            Log.e(TAG, "createGroup 이 던졌다", e)
            onResult(false)
        }
    }

    /**
     * 그룹이 서기를 기다린다 — 조회해서 우리 그룹이 인터페이스와 함께 보이면 [finishStart]. 아직이면 [FORMATION_POLL_MS] 뒤에 다시, 시한을 넘기면
     * 만들기를 청한 그룹을 내리고 실패다(만들기가 받아들여졌으니 그 뒤에 선 그룹은 우리 것이다).
     */
    private fun awaitFormation(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, startedNs: Long) {
        if (pendingStart == null) return
        confirmOn(mgr, ch) { iface ->
            when {
                pendingStart == null -> Unit
                iface != null -> finishStart(iface)
                (System.nanoTime() - startedNs) / 1_000_000 >= FORMATION_TIMEOUT_MS -> {
                    Log.w(TAG, "그룹이 정한 시간 안에 서지 않았다")
                    val waiting = pendingStart
                    pendingStart = null
                    fail(HotspotProblem.NOT_FORMED)
                    removeQuietly(mgr, ch) { waiting?.invoke(false) }
                }
                else -> main.postDelayed({ awaitFormation(mgr, ch, startedNs) }, FORMATION_POLL_MS)
            }
        }
    }

    /** 서비스가 받은 방송이 우리 그룹이 섰다고 말한다 — 기다리던 중이면 끝낸다. 켜진 뒤면 이름만 고친다. */
    fun noteGroup(iface: String) {
        if (pendingStart != null) finishStart(iface) else groupInterface = iface
    }

    private fun finishStart(iface: String) {
        val waiting = pendingStart ?: return
        pendingStart = null
        groupInterface = iface
        problem = null
        detail = null
        Log.i(TAG, "Wi-Fi Direct 그룹이 섰다")
        waiting(true)
    }

    /**
     * `createGroup` 이 BUSY 로 실패했을 때, 떠 있는 그룹이 **우리가 만들려던 바로 그 그룹일 때만** 다시 쓴다(SSID 와 패스프레이즈가 모두 같을
     * 때). usb-tether 의 예전 판은 그룹 오너이기만 하면 받아들여, 화면에는 만든 패스프레이즈가 보이는데 실제 그룹은 다른(약할 수도 있는) 것을
     * 쓰거나 남의 그룹을 받아들였다가 [stop] 이 내려 버렸다. 패스프레이즈를 확인할 수 없으면(null·던짐) 다르다고 본다 — 판단할 수 없을 때 안전한
     * 쪽은 다시 쓰지 않는 쪽이다. 옛 앱(usb-tether)이 같은 자격 증명으로 그룹을 띄워 두었다면 그 그룹을 다시 쓰게 된다 — 옛 앱을 먼저 멈춘다
     * (CLAUDE.md '테더링' 의 '사용자가 폰에서 할 일').
     */
    @SuppressLint("MissingPermission")
    private fun reuseExistingGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, onResult: (Boolean) -> Unit) {
        mgr.requestGroupInfo(ch) { group ->
            val iface = interfaceIfOurs(group)
            when {
                group == null || !group.isGroupOwner -> {
                    fail(HotspotProblem.BUSY_NO_GROUP)
                    onResult(false)
                }
                !isOwnGroup(group) -> {
                    fail(HotspotProblem.BUSY_OTHER_GROUP)
                    onResult(false)
                }
                // 우리 그룹인데 인터페이스 이름을 주지 않았다 — 손님 거르기가 견줄 이름이 없어 켜짐으로 두지 않는다.
                iface == null -> {
                    fail(HotspotProblem.NOT_FORMED)
                    onResult(false)
                }
                else -> {
                    groupInterface = iface
                    problem = null
                    detail = null
                    Log.i(TAG, "떠 있는 우리 그룹을 다시 쓴다")
                    onResult(true)
                }
            }
        }
    }

    private fun isOwnGroup(group: WifiP2pGroup): Boolean {
        val livePassphrase = try {
            group.passphrase
        } catch (e: Exception) {
            null
        }
        return group.networkName == displaySsid && livePassphrase == passphrase
    }

    /** 조회한 그룹이 우리 그룹이면 그 인터페이스, 아니면 null. */
    private fun interfaceIfOurs(group: WifiP2pGroup?): String? =
        GroupWatch.ourInterface(group != null, group?.isGroupOwner == true, group?.networkName, group?.`interface`, displaySsid)

    /** 지금 우리 그룹이 떠 있는가를 프레임워크에 묻는다 — 떠 있으면 그 인터페이스, 아니면(조회 실패 포함) null. 메인 스레드에서. */
    fun confirm(onResult: (String?) -> Unit) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null || closed) {
            onResult(null)
            return
        }
        confirmOn(mgr, ch, onResult)
    }

    @SuppressLint("MissingPermission")
    private fun confirmOn(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, onResult: (String?) -> Unit) {
        try {
            mgr.requestGroupInfo(ch) { group -> onResult(interfaceIfOurs(group)) }
        } catch (e: Exception) {
            Log.w(TAG, "requestGroupInfo 가 던졌다: ${e.javaClass.simpleName}")
            onResult(null)
        }
    }

    /**
     * 그룹을 내리고 이 인스턴스가 쥔 Channel 을 닫는다. Channel 은 인스턴스마다 새로 만들어지므로 닫지 않으면 켜고 끌 때마다(그리고 실패한
     * 시도마다) 프레임워크와의 바인더 연결이 하나씩 쌓인다. `removeGroup` 의 결과도 버리지 않는다 — 실패하면 그룹은 살아 있는데 화면·타일만
     * '꺼짐' 이 되므로 까닭을 남기고 [onResult] 로 알린다. 다만 실패해도 **그룹이 이미 없을 수 있다**(밖에서 먼저 내려갔다 — 그때 removeGroup 은
     * 내릴 것이 없어 실패한다). 그때 '아직 떠 있을 수 있다' 고 알리면 거꾸로이므로 조회해서 우리 그룹이 정말 남았을 때만 실패로 본다.
     */
    @SuppressLint("MissingPermission")
    fun stop(onResult: ((Boolean) -> Unit)? = null) {
        pendingStart = null
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            onResult?.invoke(true)
            return
        }
        try {
            mgr.removeGroup(
                ch,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        closeChannel(ch)
                        onResult?.invoke(true)
                    }

                    override fun onFailure(reason: Int) {
                        confirmOn(mgr, ch) { stillUp ->
                            if (stillUp != null) {
                                fail(HotspotProblem.REMOVE_FAILED, reason.toString())
                                Log.w(TAG, "removeGroup 실패: $reason — 그룹이 남아 있다")
                            } else {
                                Log.i(TAG, "removeGroup 실패: $reason — 그룹은 이미 없다")
                            }
                            closeChannel(ch)
                            onResult?.invoke(stillUp == null)
                        }
                    }
                },
            )
        } catch (e: Exception) {
            fail(HotspotProblem.REMOVE_FAILED, e.javaClass.simpleName)
            Log.w(TAG, "removeGroup 이 던졌다: ${e.javaClass.simpleName}")
            closeChannel(ch)
            onResult?.invoke(false)
        }
    }

    /** 만들기를 청한 그룹이 서지 않았을 때 — 내려 보고(결과는 묻지 않는다) Channel 은 그대로 둔다(부르는 쪽이 [release] 한다). */
    @SuppressLint("MissingPermission")
    private fun removeQuietly(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, then: () -> Unit) {
        try {
            mgr.removeGroup(
                ch,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = then()

                    override fun onFailure(reason: Int) = then()
                },
            )
        } catch (e: Exception) {
            then()
        }
    }

    /**
     * 그룹을 만들지 못한(또는 이미 내려간) 인스턴스를 버릴 때 — **Channel 만 닫고 `removeGroup` 은 하지 않는다.** 이 인스턴스가 만든 그룹이 없으므로
     * `removeGroup` 이 내리는 것은 다른 앱의 그룹일 수 있다([reuseExistingGroup] 의 대조와 같은 뜻).
     */
    fun release() {
        pendingStart = null
        channel?.let { closeChannel(it) }
    }

    private fun closeChannel(ch: WifiP2pManager.Channel) {
        closed = true
        try {
            ch.close()
        } catch (e: Exception) {
            Log.w(TAG, "Channel 을 닫지 못했다: ${e.javaClass.simpleName}")
        }
    }

    private fun fail(what: HotspotProblem, extra: String? = null) {
        problem = what
        detail = extra
    }

    private companion object {
        const val TAG = "WebTether"

        /** 그룹 만들기를 청한 뒤 그룹이 섰는지 묻는 간격. */
        const val FORMATION_POLL_MS = 500L

        /** 그룹이 서기를 기다리는 시한 — 보통 1–2초에 선다(기기에서 재지 않았다). */
        const val FORMATION_TIMEOUT_MS = 15_000L
    }
}
