package io.github.donggi.iroiroviewer.io

import java.security.MessageDigest

/**
 * 이어보기·읽던 쪽의 키.
 *
 * **경로를 쓰지 않는다.** 두 가지 이유가 있다. 파일을 다른 폴더로 옮기면 경로 키는
 * 끊기지만 이 키는 살아남는다. 그리고 기록 표가 '무엇을 봤는가' 의 목록이 되는 것을
 * 막는다 — 해시만 남으므로 표를 통째로 가져가도 파일 이름을 알 수 없다.
 *
 * 크기와 수정시각을 함께 넣는 것은 이름만으로는 충돌이 잦기 때문이고(`001.mp4` 는
 * 어디에나 있다), 이름을 넣는 것은 크기·시각이 같은 파일이 실제로 존재하기 때문이다.
 *
 * 파일을 고치면 키가 바뀐다. 그래서 편집된 파일은 처음부터 다시 본다 — 뷰어로서
 * 오히려 맞는 동작이다.
 */
object FileKey {

    fun of(name: String, size: Long, lastModified: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(size.toString().toByteArray())
        md.update(0)
        md.update(lastModified.toString().toByteArray())
        md.update(0)
        md.update(name.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
