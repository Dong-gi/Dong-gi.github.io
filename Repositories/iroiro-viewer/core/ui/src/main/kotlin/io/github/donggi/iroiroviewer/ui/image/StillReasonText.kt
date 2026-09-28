package io.github.donggi.iroiroviewer.ui.image

import io.github.donggi.iroiroviewer.ui.R

/**
 * '첫 장면만 보여 준다' 의 까닭을 **사람이 읽는 문장**으로 옮긴다.
 *
 * 문구는 까닭을 만드는 타입([AnimationPlan.StillReason]) 곁, 이 모듈의 `strings.xml` 에 둔다(CLAUDE.md
 * 함정 표의 '문구는 그것을 만들어 내는 타입 곁에 둬라'). 처음에는 이미지 뷰어가 자기 모듈에 한 벌을 두었고
 * 만화 뷰어도 APNG 문구를 따로 들고 있었다 — 같은 사실을 두 화면이 들고 있으면 한쪽만 고쳐지는 날이 온다.
 *
 * [AnimationPlan] 을 안드로이드 없이 두려고(JVM 시험) 따로 뗀 파일이다. `when` 에 `else` 가 없으므로 까닭이
 * 늘면 컴파일러가 여기를 가리킨다.
 */
fun AnimationPlan.StillReason.messageRes(): Int = when (this) {
    AnimationPlan.StillReason.APNG -> R.string.ui_still_apng
    AnimationPlan.StillReason.TOO_LARGE -> R.string.ui_still_too_large
    AnimationPlan.StillReason.DECODE_FAILED -> R.string.ui_still_decode_failed
}
