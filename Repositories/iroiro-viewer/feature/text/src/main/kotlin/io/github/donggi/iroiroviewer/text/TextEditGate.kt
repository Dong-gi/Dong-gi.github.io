package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.io.TextSaver
import io.github.donggi.iroiroviewer.safety.TextLimits

/**
 * 텍스트 뷰어의 **'편집' 단추**를 낼 것인가. 판단을 여기 모아 JVM 에서 시험한다(`TextEditGateTest`).
 *
 * * **숨긴다**([Hidden]) — 고칠 수 없는 자리(저장 볼륨 밖 — 다른 앱이 넘긴 읽기 전용 사본은 앱 캐시다, 휴지통
 *   안, 보통 파일이 아니다, 쓸 수 없다)이거나, 다시 쓸 수 없는 인코딩이다. 글이 아닌 파일·UTF-32 는 뷰어가 열지 못해 여기 오지 않는다.
 *   압축 파일 안의 항목은 경로가 없어 텍스트 뷰어에 오지 않는다.
 * * **크기 때문이면 단추는 두고 누를 때 까닭을 말한다**([TooLarge]) — 숨기면 사용자는 이 파일만 왜 고칠 수 없는지 모른다.
 *
 * 마지막 관문은 저장하는 쪽이다(`TextSaver.save` 가 같은 [TextSaver.placeOf] 로 다시 본다) — 이 단추를 빠뜨려도 앱 캐시에 쓰지 않는다.
 */
internal sealed interface TextEditGate {
    data object Hidden : TextEditGate

    data object Open : TextEditGate

    data class TooLarge(val limitBytes: Long) : TextEditGate

    companion object {
        fun of(
            place: TextSaver.Place,
            sizeBytes: Long,
            encoding: TextEncoding,
            limitBytes: Long = TextLimits.EDIT_MAX_BYTES,
        ): TextEditGate = when {
            place != TextSaver.Place.OK -> Hidden
            !writable(encoding) -> Hidden
            sizeBytes > limitBytes -> TooLarge(limitBytes)
            else -> Open
        }

        /** 이 인코딩으로 다시 쓸 수 있는가 — 기기에 그 변환기가 있고 쓰기를 한다. 쓰지 못하는 변환기(읽기 전용)는 고치지 않는다. */
        fun writable(encoding: TextEncoding): Boolean = encoding.charset?.canEncode() == true
    }
}
