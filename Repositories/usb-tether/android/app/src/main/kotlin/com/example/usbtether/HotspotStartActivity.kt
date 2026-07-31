package com.example.usbtether

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Intent
import android.util.Log

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
 * 기동은 [onResume] 에서 한다. `createGroup` 이 실행되는 시점에 액티비티가 실제로
 * 포그라운드에 있어야 하기 때문이다.
 */
class HotspotStartActivity : AppCompatActivity() {

    private val hotspotPrefs by lazy { HotspotPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI 없음. onResume 에서 기동하고 즉시 끝낸다.
    }

    override fun onResume() {
        super.onResume()
        startHotspot()
        finish()
    }

    private fun startHotspot() {
        if (TetherService.hotspotActive) return

        val ssid = hotspotPrefs.ssid()
        val passphrase = hotspotPrefs.passphraseOrCreate()
        if (ssid.isEmpty() || passphrase.isEmpty()) {
            Log.w(TAG, "저장된 SSID/패스프레이즈가 없어 타일 기동을 건너뛴다")
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

    companion object {
        private const val TAG = "HotspotStartActivity"
    }
}
