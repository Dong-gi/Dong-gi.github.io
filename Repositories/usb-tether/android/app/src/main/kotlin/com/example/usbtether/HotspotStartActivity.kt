package com.example.usbtether

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 퀵 설정 타일이 핫스팟을 켤 때 거쳐 가는 비공개 트램폴린 액티비티.
 *
 * ## 왜 별도 액티비티인가
 *
 * `WifiP2pManager.createGroup` 은 이 앱의 액티비티가 포그라운드에 있지 않으면
 * 계속 실패한다(BUSY). 그래서 타일이 직접 서비스를 부르지 못하고 액티비티를 거쳐야
 * 한다. 이전에는 그 역할을 [MainActivity] 가 맡았는데, MainActivity 는 런처
 * 필터 때문에 `android:exported="true"` 여야 하고 인텐트 액션만 보고 호출자를
 * 검증하지 않았다.
 *
 * `startActivity` 에는 어떤 권한도 필요하지 않다. 따라서 기기에 설치된 **아무 앱이나**
 * `com.example.usbtether/.MainActivity` 에 액션
 * `com.example.usbtether.START_HOTSPOT_FROM_TILE` 을 명시 인텐트로 던져 저장된
 * 자격증명으로 핫스팟을 켤 수 있었다. 사용자 상호작용이 전혀 없이 무선 공격면이
 * 열리고 배터리가 소모된다.
 *
 * 이 액티비티는 `android:exported="false"` 이므로 같은 앱(= 타일 서비스)만 띄울 수
 * 있다. MainActivity 는 런처 역할만 남고 특권 액션을 처리하지 않는다.
 *
 * ## 창을 띄우지 않는 방법
 *
 * 투명 테마 + `noHistory` + `excludeFromRecents` 로 사용자에게 보이지 않게 한다.
 * `Theme.NoDisplay` 는 최신 Android 에서 `onResume` 반환 전에 finish 하지 않으면
 * 크래시하므로 쓰지 않는다.
 *
 * **`AppCompatActivity` 가 아니라 [Activity] 를 상속한다.** AppCompat 은
 * `Theme.AppCompat` 계열이 아니면 `createSubDecor()` 에서
 * `IllegalStateException("You need to use a Theme.AppCompat theme…")` 을 던진다.
 * 매니페스트에 지정한 `Theme.Translucent.NoTitleBar` 는 플랫폼 테마라 그 조건을
 * 만족하지 않는다. 이 액티비티는 UI 가 없어 AppCompat 기능을 전혀 쓰지 않으므로
 * 플랫폼 Activity 로 충분하다.
 *
 * 기동은 [onResume] 에서 한다. `createGroup` 이 실행되는 시점에 액티비티가 실제로
 * 포그라운드에 있어야 하기 때문이다.
 */
class HotspotStartActivity : Activity() {

    private val hotspotPrefs by lazy { HotspotPreferences(this) }

    override fun onResume() {
        super.onResume()
        startHotspot()
        finish()
    }

    private fun startHotspot() {
        // 조기 반환 시에도 상태를 브로드캐스트해야 한다. 타일은 클릭 직후
        // STATE_UNAVAILABLE 로 바꾸고 ACTION_HOTSPOT_STATE_CHANGED 를 기다리므로,
        // 아무것도 보내지 않으면 퀵 설정 패널을 다시 열 때까지 '사용 불가' 로 멈춘다.
        if (TetherService.hotspotActive) {
            notifyStateUnchanged()
            return
        }

        val ssid = hotspotPrefs.ssid()
        val passphrase = hotspotPrefs.passphraseOrCreate()
        if (ssid.isEmpty() || passphrase.isEmpty()) {
            Log.w(TAG, "저장된 SSID/패스프레이즈가 없어 타일 기동을 건너뛴다")
            notifyStateUnchanged()
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, TetherService::class.java).apply {
                action = TetherService.ACTION_HOTSPOT_ON
                putExtra(TetherService.EXTRA_SSID, ssid)
                putExtra(TetherService.EXTRA_PASSPHRASE, passphrase)
            },
        )
    }

    /**
     * 상태가 바뀌지 않았음을 알린다.
     *
     * 타일이 낙관적으로 걸어둔 STATE_UNAVAILABLE 를 실제 상태로 되돌리게 하는 것이
     * 목적이다. TetherService 가 보내는 것과 같은 브로드캐스트를 쓰고,
     * setPackage 로 이 앱 안에서만 전달되게 한다.
     */
    private fun notifyStateUnchanged() {
        sendBroadcast(
            Intent(TetherService.ACTION_HOTSPOT_STATE_CHANGED).setPackage(packageName)
        )
    }

    companion object {
        private const val TAG = "HotspotStartActivity"
    }
}
