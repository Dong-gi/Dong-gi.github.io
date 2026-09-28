package io.github.donggi.iroiroviewer

import androidx.compose.runtime.saveable.Saver

/**
 * 지금 보고 있는 화면.
 *
 * **`navigation-compose` 를 들이지 않는다.** 화면이 셋이고 백스택이 한 겹인데, 그 라이브러리의
 * 지금 권장 형태인 타입 안전 라우트는 `kotlin.plugin.serialization` 과
 * `kotlinx-serialization-json` 을 요구한다 — 둘 다 이 저장소의 버전 카탈로그에 없다.
 * 경로를 라우트 문자열에 넣으려면 임의의 유니코드와 `/` 를 인코딩해야 하는 문제도 그대로다.
 *
 * **백스택이 한 겹을 넘는 자리가 둘 생겼다**(14단계) — 설정에서 연 고지·진단은 뒤로 가면 설정으로, 압축 목록에서 연 고지는
 * 그 압축 목록으로 돌아가야 한다. 스택을 들이는 대신 그 두 화면이 **돌아갈 곳**([Notice.back]·[Diag.back])을 든다.
 * 저장 상태에도 그대로 들어간다([AppScreenCodec]).
 */
internal sealed interface AppScreen {
    data object Browser : AppScreen

    /** 진단. [back] 은 뒤로 갔을 때의 화면 — 첫 화면에서 열면 [Browser], 설정에서 열면 [Settings]. */
    data class Diag(val back: AppScreen = Browser) : AppScreen

    /** 설정(14단계). 첫 화면의 '설정' 줄에서만 열린다. */
    data object Settings : AppScreen

    /**
     * 이미지 뷰어. **저장 상태에는 시작 경로 하나만** 남긴다.
     *
     * 목록 전체를 담으면 사진이 많은 폴더에서 번들이 MB 단위가 되고
     * `TransactionTooLargeException` 으로 죽는다.
     */
    data class Viewer(val startPath: String) : AppScreen

    /** 텍스트 뷰어. 목록이 필요 없어 경로 하나가 전부다. */
    data class Text(val path: String) : AppScreen

    /** 압축 파일 탐색. 아카이브 안 폴더 위치는 화면이 들고 있으므로 여기에는 경로만 남는다. */
    data class Archive(val path: String) : AppScreen

    /**
     * 만화 뷰어. 경로와 **엔트리 번호**뿐이다.
     *
     * 쪽 목록을 저장 상태에 담지 않는다 — 300쪽짜리 이름 목록이 번들에 들어가고,
     * 그것은 뷰어에 사진 목록을 담았을 때와 같은 형태의 `TransactionTooLargeException`
     * 이다. 프로세스가 죽었다 살아나면 목록은 다시 만들고, 읽던 쪽은 이어보기가 안다.
     */
    data class Comic(val path: String, val entryIndex: Int) : AppScreen

    /**
     * 문서 뷰어. 경로 하나가 전부다.
     *
     * 쪽 수도 읽던 쪽도 담지 않는다 — 쪽 수는 문서를 열면 곧 알고, 읽던 쪽은
     * `doc_progress` 가 안다. 만화 뷰어가 쪽 목록을 담지 않기로 한 것과 같은 판단이다.
     */
    data class Doc(val path: String) : AppScreen

    /** 오픈소스 고지. 라이선스가 요구하는 화면이라 어디서든 닿아야 한다. [back] 은 [Diag.back] 과 같다. */
    data class Notice(val back: AppScreen = Browser) : AppScreen

    companion object {
        val Saver: Saver<AppScreen, Any> = Saver(
            save = { AppScreenCodec.encode(it) },
            restore = { (it as? String)?.let(AppScreenCodec::decode) ?: Browser },
        )
    }
}

/**
 * [AppScreen] ↔ 저장 상태의 글자. **순수 함수**라 JVM 시험이 왕복을 본다 — 새 화면을 더하고 이 표를 잊으면 회전·프로세스
 * 재생성 뒤에 첫 화면으로 떨어지는데, 컴파일러는 `when` 이 `else` 로 끝나는 쪽(되살리기)에서 아무 말도 하지 않는다.
 *
 * 모양은 '머리 글자 + 값' 이다. 경로에는 `:` 가 들어갈 수 있으므로 **머리만 보고 가른 뒤 나머지는 통째로** 값이다.
 * 돌아갈 곳을 드는 화면(고지·진단)은 `>` 뒤에 그 화면을 같은 규칙으로 적는다 — 머리에서만 가르므로 겹쳐도 풀린다.
 * 옛 판이 남긴 `"d"`·`"n"` 은 돌아갈 곳이 첫 화면인 것으로 읽는다.
 */
internal object AppScreenCodec {

    fun encode(screen: AppScreen): String = when (screen) {
        is AppScreen.Browser -> "b"
        is AppScreen.Settings -> "s"
        is AppScreen.Diag -> if (screen.back == AppScreen.Browser) "d" else "d>" + encode(screen.back)
        is AppScreen.Notice -> if (screen.back == AppScreen.Browser) "n" else "n>" + encode(screen.back)
        is AppScreen.Viewer -> "v:" + screen.startPath
        is AppScreen.Text -> "t:" + screen.path
        is AppScreen.Archive -> "a:" + screen.path
        is AppScreen.Comic -> "c:" + screen.entryIndex + ":" + screen.path
        is AppScreen.Doc -> "p:" + screen.path
    }

    fun decode(v: String): AppScreen = when {
        v == "s" -> AppScreen.Settings
        v == "d" -> AppScreen.Diag()
        v.startsWith("d>") -> AppScreen.Diag(backOf(decode(v.removePrefix("d>"))))
        v == "n" -> AppScreen.Notice()
        v.startsWith("n>") -> AppScreen.Notice(backOf(decode(v.removePrefix("n>"))))
        v.startsWith("v:") -> AppScreen.Viewer(v.removePrefix("v:"))
        v.startsWith("t:") -> AppScreen.Text(v.removePrefix("t:"))
        v.startsWith("a:") -> AppScreen.Archive(v.removePrefix("a:"))
        v.startsWith("c:") -> {
            val rest = v.removePrefix("c:")
            val at = rest.indexOf(':')
            if (at < 0) AppScreen.Browser
            else AppScreen.Comic(rest.substring(at + 1), rest.substring(0, at).toIntOrNull() ?: -1)
        }
        v.startsWith("p:") -> AppScreen.Doc(v.removePrefix("p:"))
        else -> AppScreen.Browser
    }

    /**
     * 돌아갈 곳으로 받아 주는 것은 **곧바로 여는 화면**뿐이다. 고지에서 고지로, 진단에서 고지로 가는 길은 없으므로 그런
     * 값(손상된 저장 상태)은 첫 화면으로 돌린다 — 뒤로가기가 끝없이 제자리를 도는 모양을 만들지 않는다.
     */
    private fun backOf(screen: AppScreen): AppScreen = when (screen) {
        is AppScreen.Notice, is AppScreen.Diag -> AppScreen.Browser
        else -> screen
    }
}
