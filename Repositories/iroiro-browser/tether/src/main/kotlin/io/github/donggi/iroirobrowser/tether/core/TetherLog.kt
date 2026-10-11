package io.github.donggi.iroirobrowser.tether.core

/**
 * 테더 서버의 기록 줄. 이 모듈은 순수 JVM 이라 `android.util.Log` 를 모른다 — 앱이 그것으로 잇고, 시험은 모으거나 버린다.
 *
 * **목적지의 호스트·포트와 손님의 주소를 싣지 않는다.** 실패한 연결만 모아도 방문 기록이 되고, 폰에 붙은 PC 가 `adb logcat` 으로 읽어 갈 수
 * 있다(usb-tether 때부터의 규칙). 예외는 종류만 적는다.
 */
fun interface TetherLog {
    fun write(level: Level, tag: String, message: String, error: Throwable?)

    enum class Level { VERBOSE, INFO, WARN, ERROR }

    companion object {
        val NONE = TetherLog { _, _, _, _ -> }
    }
}

internal fun TetherLog.v(tag: String, message: String) = write(TetherLog.Level.VERBOSE, tag, message, null)

internal fun TetherLog.i(tag: String, message: String) = write(TetherLog.Level.INFO, tag, message, null)

internal fun TetherLog.w(tag: String, message: String) = write(TetherLog.Level.WARN, tag, message, null)

internal fun TetherLog.e(tag: String, message: String, error: Throwable? = null) = write(TetherLog.Level.ERROR, tag, message, error)
