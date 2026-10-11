package io.github.donggi.iroiroviewer.player

import java.io.File
import java.io.IOException

/**
 * 구간 저장·소리만 저장을 **이 자리의 파일에 해도 되는가**.
 *
 * 새 파일은 원본 곁에 생긴다(`MediaClipper`). 원본이 앱 캐시의 공유 폴더(`ShareHelper.shareDir` — 다른 앱이 넘긴 사본 `share/inbox`
 * 따위)에 있으면 '저장했습니다' 가 떠도 사용자는 그 파일을 찾아갈 수 없고, 나중에 캐시 정리가 지운다. 처음에는 받은 사본
 * (`IncomingFiles.isCopy`)만 거절해 그 밖의 사본(그때의 네트워크 저장소 사본 `share/remote`)이 그 길로 빠졌다 — 그래서 공유 폴더
 * 아래는 **모두** 거절한다. 까닭을 말하는 것은 화면이다(받은 사본과 그 밖을 갈라 말한다).
 */
internal object ClipSource {

    /**
     * [path] 가 [shareRoot] 아래인가 — 둘 다 정규화한 경로로 견준다(링크·`..` 로 돌아 들어오는 것도 같은 자리다). 정규화가 실패하면
     * 참이다: 무엇인지 모르는 자리의 곁에 새 파일을 만들지 않는다.
     */
    fun isAppCopy(path: String, shareRoot: File): Boolean = try {
        isUnder(File(path).canonicalPath, shareRoot.canonicalPath)
    } catch (e: IOException) {
        true
    }

    /**
     * 정규화한 [child] 가 [root] 의 **아래**인가. 이름이 [root] 로 시작하기만 하는 옆 폴더(`share2/…`)는 아니고, [root] 자신도 아니다.
     * 구분자는 `/` 와 이 기계의 것을 함께 본다 — 기기에서는 같은 글자이고, JVM 시험이 도는 기계에서는 다를 수 있다.
     */
    internal fun isUnder(child: String, root: String): Boolean {
        val base = root.trimEnd('/', File.separatorChar)
        if (child.length <= base.length + 1 || !child.startsWith(base)) return false
        val next = child[base.length]
        return next == '/' || next == File.separatorChar
    }
}
