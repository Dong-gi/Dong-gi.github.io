package io.github.donggi.iroiroviewer.io

import android.util.Log

/**
 * 로그. **릴리스 빌드에서는 호출 지점 자체가 사라진다.**
 *
 * `-assumenosideeffects` 로 `android.util.Log` 를 지우는 방법만 믿지 않는 이유는,
 * 그 규칙이 호출은 지워도 **인자를 만드는 식은 지우지 못하기** 때문이다.
 * `Iro.d { "경로: $path" }` 처럼 람다로 받으면 릴리스에서 문자열이 아예 만들어지지 않고,
 * 따라서 파일 경로가 메모리에도 로그에도 남지 않는다.
 *
 * 파일 경로를 로그에 남기지 않는 것은 취향이 아니라 규칙이다 — logcat 은 다른 앱과
 * 사용자가 볼 수 있고, 경로에는 사람이 무엇을 가지고 있는지가 그대로 드러난다.
 */
object Iro {

    /**
     * 디버그 빌드인가. app 모듈이 시작할 때 `BuildConfig.DEBUG` 로 채운다.
     *
     * `@JvmField` 인 것은 아래 inline 함수들이 이 값을 읽기 위해서다 — 보통의 코틀린
     * 프로퍼티는 뒷받침 필드가 private 이라 공개 inline 함수가 접근하지 못한다.
     */
    @JvmField
    var debug: Boolean = false

    // inline 함수의 기본 인자로 쓰이므로 공개여야 한다.
    const val TAG = "iroiro"

    inline fun d(tag: String = TAG, message: () -> String) {
        if (debug) Log.d(tag, message())
    }

    inline fun w(tag: String = TAG, throwable: Throwable? = null, message: () -> String) {
        if (debug) Log.w(tag, message(), throwable)
    }

    /**
     * 오류는 릴리스에서도 남긴다. 다만 **메시지에 경로를 넣지 마라** — 무엇이
     * 실패했는지는 파일 이름과 포맷 이름으로 충분하다.
     */
    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
