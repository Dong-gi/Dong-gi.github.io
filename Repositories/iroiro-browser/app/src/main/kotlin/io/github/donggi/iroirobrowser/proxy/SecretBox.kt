package io.github.donggi.iroirobrowser.proxy

import io.github.donggi.iroirobrowser.crypto.DeviceKeyCipher
import java.security.GeneralSecurityException

/**
 * 프록시 비밀번호를 **기기 키**(AndroidKeyStore 의 AES-256-GCM, 밖으로 꺼낼 수 없는 키 — 별칭 `web-proxy-password`, 잠그고 여는 일은
 * [DeviceKeyCipher])로 잠가 설정 파일에 둔다. 백업은 이미 꺼져 있다(`allowBackup=false`, 이전 규칙도 모두 뺀다) — 키가 기기를 떠나지 않으니
 * 잠긴 값도 다른 기기에서 열리지 않는다.
 *
 * 잠그지 못하면 던진다(설정 저장이 실패한다). 열지 못하면(키가 지워졌다 — 앱 데이터를 지우는 등, 꼴이 틀렸다) null — 사용자가 다시 넣는다.
 */
internal object SecretBox {

    private val cipher = DeviceKeyCipher("web-proxy-password")

    fun seal(plain: String): String = cipher.seal(plain)

    fun open(blob: String): String? = try {
        cipher.open(blob)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }
}
