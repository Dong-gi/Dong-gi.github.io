package io.github.donggi.iroirobrowser.tether

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * 핫스팟(Wi-Fi Direct 그룹 만들기)에 드는 런타임 권한. **테더 화면에서 '테더링 켜기' 를 누를 때만** 청한다 — 앱을 열자마자 묻지 않는다
 * (브라우저를 열 때 묻지 않는다).
 *
 * 꼭 있어야 하는 것은 `NEARBY_WIFI_DEVICES`(근처 기기) 하나다 — 매니페스트에 `neverForLocation`(위치를 얻는 데 쓰지 않는다)을 밝혀 위치 권한도
 * 위치 서비스도 필요 없다. 알림(`POST_NOTIFICATIONS` — 테더 서비스의 알림)도 함께 청하되 거절해도 켠다.
 *
 * 앱은 위치 권한을 갖지 않는다(minSdk 36 — 12 의 Wi-Fi Direct 가 요구하던 위치 권한을 걷었다). 웹 페이지의 위치는 그와 따로 막는다 —
 * WebView 가 위치 API 를 끄고(`setGeolocationEnabled(false)`) 위치 요청을 늘 거절한다(PageChrome.onGeolocationPermissionsShowPrompt).
 */
internal object TetherPermissions {

    /** 핫스팟을 켜는 데 꼭 있어야 하는 권한. */
    private const val REQUIRED = Manifest.permission.NEARBY_WIFI_DEVICES

    fun hasHotspotPermission(context: Context): Boolean = granted(context, REQUIRED)

    /** 청할 것 — 이미 받은 것은 뺀다. 알림도(서비스의 알림 — 거절해도 테더는 돈다). */
    fun toRequest(context: Context): Array<String> =
        listOf(REQUIRED, Manifest.permission.POST_NOTIFICATIONS).filterNot { granted(context, it) }.toTypedArray()

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
