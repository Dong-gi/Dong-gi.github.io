package io.github.donggi.iroiroviewer

import android.content.Context
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.ShareHelper
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * 걷어 낸 기능이 기기에 남긴 것을 지운다 — 앱이 설 때 한 번(뒤에서). 이미 없으면 아무 일도 하지 않는다.
 *
 * **네트워크 저장소(SFTP, 2026-10-05 ~ 10-09)** — 사용자가 이 앱에서 네트워크 기능을 걷기로 했다. 남는 것은 셋이다:
 * Keystore 의 열쇠로 다시 잠가 둔 SSH 열쇠 파일(`files/sftp/keys/`), 그 Keystore 의 열쇠(`iroiro.sftp.keys.v1`), 열려고 받아 둔
 * 사본(`cache/share/remote/`). 서버 목록 표는 DB 의 마이그레이션(4→5)이 지운다. 비밀이 든 것이라 남겨 둘 까닭이 없다 — 지우지 않으면
 * 앱의 자료를 지우기 전까지 잠근 열쇠가 기기에 남는다.
 *
 * **정리가 실패해도 앱을 죽이지 않는다**. 날것의 스레드라 여기서 새는 예외는 그 스레드의 처리되지 않은 예외로 **프로세스를
 * 끝내고**, 이 정리는 주 프로세스가 설 때마다(휴지통 비우기 예약 작업으로 설 때도) 돌므로 같은 조건이면 켤 때마다 죽는다. AndroidKeyStore 는
 * 선언하지 않은 예외도 던진다(`ProviderException`, keystore2 와의 연결이 끊긴 드문 경우의 NPE 따위 — 일어날 확률은 낮다). 지우지 못한 것은
 * 다음에 설 때 다시 지운다(없으면 아무 일도 없다 — 여러 번 돌아도 안전하다).
 */
internal object LegacyCleanup {

    /** `core:remote` 의 `KeyVault.ALIAS` 였던 이름. */
    private const val SFTP_KEY_ALIAS = "iroiro.sftp.keys.v1"

    fun removeSftpLeftoversLater(context: Context) {
        val app = context.applicationContext
        Thread({ removeSftpLeftovers(app) }, "iroiro-legacy-cleanup").apply { isDaemon = true }.start()
    }

    private fun removeSftpLeftovers(context: Context) {
        quietly("SFTP 의 잠근 열쇠 파일") { File(context.filesDir, "sftp").takeIf { it.exists() }?.deleteRecursively() }
        quietly("받아 둔 원격 사본") { File(ShareHelper.shareDir(context), "remote").takeIf { it.exists() }?.deleteRecursively() }
        // 지우지 못하면 다음에 다시 — 열쇠만으로는 아무것도 풀 수 없다(잠근 파일을 위에서 지웠다).
        quietly("Keystore 의 SFTP 열쇠") {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(SFTP_KEY_ALIAS)) store.deleteEntry(SFTP_KEY_ALIAS)
        }
    }

    /**
     * [step] 을 돌리되 실패를 삼킨다 — 해냈으면 참. 정리는 다음에 다시 하면 되는 일이라 어느 실패도 앱을 끝낼 까닭이 되지 않는다(위 KDoc).
     * 오류(`Error` — 메모리 부족 따위)는 삼키지 않는다 — 그것은 이 정리의 실패가 아니다. 판단만(JVM 시험).
     */
    internal fun quietly(what: String, step: () -> Unit): Boolean = try {
        step()
        true
    } catch (e: GeneralSecurityException) {
        Iro.d { "옛 기능의 남은 것을 지우지 못했다($what): ${e.javaClass.simpleName}" }
        false
    } catch (e: IOException) {
        Iro.d { "옛 기능의 남은 것을 지우지 못했다($what): ${e.javaClass.simpleName}" }
        false
    } catch (e: RuntimeException) {
        Iro.d { "옛 기능의 남은 것을 지우지 못했다($what): ${e.javaClass.simpleName}" }
        false
    }
}
