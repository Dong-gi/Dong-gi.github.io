package io.github.donggi.iroirobrowser.tether

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 핫스팟(Wi-Fi Direct 그룹 만들기)에 드는 런타임 권한. **테더 화면에서 '테더링 켜기' 를 누를 때만** 청한다 — 앱을 열자마자 묻지 않는다
 * (usb-tether 는 화면을 열자마자 물었다. Web 은 브라우저다 — 브라우저를 열 때 위치를 묻지 않는다).
 *
 * - Android 13 이상: `NEARBY_WIFI_DEVICES`(매니페스트에 `neverForLocation` — 위치를 얻는 데 쓰지 않는다고 밝혀 위치 권한이 필요 없다)
 * - Android 12 이하(사용자 폰 SM-A908N 은 12): **정확한 위치** `ACCESS_FINE_LOCATION` — `WifiP2pManager.createGroup` 이 요구한다. targetSdk 31
 *   이상이 FINE 만 청하면 안드로이드 12 는 그 요청을 무시한다고 문서에 있어 **COARSE 를 함께** 청한다(둘 다 매니페스트에 maxSdkVersion 32).
 *   사용자가 '대략적인 위치' 를 고르면 COARSE 만 받아 핫스팟을 켤 수 없다 — 화면이 앱 설정으로 안내한다.
 *
 * 앱이 위치 권한을 갖게 되어도 **웹 페이지에는 위치를 주지 않는다** — 브라우저의 WebView 가 위치 API 를 끄고(`setGeolocationEnabled(false)`)
 * 위치 요청을 늘 거절한다(PageChrome.onGeolocationPermissionsShowPrompt). 그 둘이 이제 OS 권한이라는 바깥 겹 없이 지키는 선이다.
 */
internal object TetherPermissions {

    /** 핫스팟을 켜는 데 꼭 있어야 하는 권한. */
    fun required(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION

    fun hasHotspotPermission(context: Context): Boolean = granted(context, required())

    /** 청할 것 — 이미 받은 것은 뺀다. 13 이상은 알림도(서비스의 알림 — 거절해도 테더는 돈다). */
    fun toRequest(context: Context): Array<String> {
        val wanted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return wanted.filterNot { granted(context, it) }.toTypedArray()
    }

    /** 12 이하에서 '대략적인 위치' 만 받았다 — 정확한 위치로 바꾸라고 안내할 때. */
    fun onlyCoarse(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
