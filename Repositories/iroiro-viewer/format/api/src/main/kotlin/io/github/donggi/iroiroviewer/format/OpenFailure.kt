package io.github.donggi.iroiroviewer.format

/**
 * 포맷을 열지 못한 이유. 예외가 아니라 값으로 돌려주는 것은 부분 성공을 1급으로
 * 다루기 위해서다 — 파서는 "못 읽었다" 와 "일부만 읽었다" 를 구분해 보고해야 하고,
 * 화면은 그 둘에 다른 것을 보여준다.
 *
 * **[detail] 에 파일 경로나 원문 예외 메시지를 넣지 마라.** 이 값은 화면에 그대로
 * 나가고, 예외 메시지에는 절대경로나 공격자가 심은 문자열이 들어 있다.
 * 무엇이 잘못됐는지는 종류와 짧은 설명으로 충분하다.
 */
sealed interface OpenFailure {
    val detail: String

    /** 파일이 깨졌거나 명세에 어긋난다. */
    data class Corrupt(override val detail: String) : OpenFailure

    /** 포맷은 알아봤지만 이 뷰어가 다루지 않는 갈래다. */
    data class Unsupported(override val detail: String) : OpenFailure

    /** 암호가 걸려 있다. 이 뷰어는 복호화하지 않는다. */
    data class Encrypted(override val detail: String) : OpenFailure

    /** 읽을 권한이 없다. */
    data class NoPermission(override val detail: String) : OpenFailure

    /** 안전 한도를 넘었다. 압축폭탄과 비정상 크기를 여기서 끊는다. */
    data class TooLarge(override val detail: String) : OpenFailure

    /**
     * 시간 상한을 넘었다.
     *
     * 크기 상한에 걸리지 않는 DoS 가 있다 — 아무것도 만들어 내지 않으면서 도는 것.
     * 그 경우를 [Corrupt] 로 뭉뚱그리면 사용자에게 "파일이 깨졌다" 고 거짓말을 하게 된다.
     */
    data class Timeout(override val detail: String) : OpenFailure

    /** 읽는 도중 입출력이 실패했다. */
    data class Io(override val detail: String) : OpenFailure
}
