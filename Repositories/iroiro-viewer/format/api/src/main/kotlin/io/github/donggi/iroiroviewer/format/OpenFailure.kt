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

    /**
     * **이전 형식**이다 — `.doc`·`.xls`·`.ppt`(OLE2 이진 형식). 1단계에서 제외했다.
     *
     * [Unsupported] 와 가르는 것은 사용자가 할 수 있는 일이 있기 때문이다 — 오피스에서
     * 새 형식(docx·xlsx·pptx)으로 저장하면 열린다. '다루지 않는 문서' 로 끝내면 그 길을
     * 알 방법이 없다.
     */
    data class LegacyFormat(override val detail: String) : OpenFailure

    /**
     * 암호를 넣으면 열 수 있다. 잠근 방식이 공개 명세라 우리가 풀 줄 안다.
     *
     * [wrongPassword] 는 **이번에 넣은 암호가 틀렸다**는 뜻이다. 암호 없이 처음 열었을 때는
     * 거짓이다 — 화면이 '틀렸습니다' 를 띄울지 이것으로 가른다.
     *
     * [Encrypted] 와 가르는 이유는 사용자가 할 수 있는 일이 다르기 때문이다. 이쪽은
     * 암호를 물어야 하고, 저쪽은 물어도 소용이 없다.
     */
    data class PasswordRequired(
        override val detail: String,
        val wrongPassword: Boolean = false,
    ) : OpenFailure

    /**
     * 잠겨 있고 **우리가 풀 수 없다** — 잠근 방식이 공개되지 않았거나(상업 DRM),
     * 암호가 아니라 다른 열쇠(인증서)를 요구한다. 암호를 물어도 소용이 없는 경우다.
     */
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
