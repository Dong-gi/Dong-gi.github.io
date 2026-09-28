package io.github.donggi.iroiroviewer.comic

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.donggi.iroiroviewer.comic.ComicOpen.Kind
import io.github.donggi.iroiroviewer.io.ExternalOpen
import kotlinx.coroutines.launch
import java.io.File

/**
 * 만화 뷰어에서 '다른 앱으로 열기' 를 **어디에 다는가.** 판단을 순수 함수로 모아 JVM 시험이 박는다(`ComicOpenWithTest`).
 * 여는 일 자체는 `core:io` 의 `ExternalOpen` 이 하고, 이 화면의 두 자리(실패 화면의 단추·⋮ 메뉴)는 언제나 고르는 창이다.
 *
 * ## 넘기는 것은 **디스크 위의 파일**이다 — 책 파일, 또는 폴더 만화의 쪽 파일
 *
 * * **폴더 자체는 넘기지 않는다.** 폴더는 다른 앱에 넘길 파일이 아니다([ComicViewModel.Book.file] 이 null) — 실패 화면의
 *   단추도 ⋮ 메뉴의 줄도 없다.
 * * **아카이브 안의 쪽은 넘기지 않는다.** 경로가 없고, 경로를 만들려면 쪽을 디스크에 뽑아야 한다 — 만화 뷰어가 하지 않기로
 *   한 일이다(`ImageIo.decodeFitted` 의 '왜 파일로 뽑지 않는가'). 그 '이 쪽을 열 수 없습니다' 에는 단추가 없다.
 * * **폴더로 연 만화의 쪽은 넘긴다**([pageFailureTarget]). 그 쪽은 이미 디스크 위의 그림 파일이다 — 이 기기가 못 푸는 HEIC·AVIF,
 *   깨진 JPEG 를 다른 앱은 열 수 있다. 넘기는 것은 **그 쪽의 파일**이다(책은 폴더라 넘길 것이 아니다). 파일이 그새 사라졌거나
 *   열리지 않으면 달지 않는다.
 * * 압축 목록에서 그림 항목을 눌러 들어온 길도 **그 아카이브 파일**을 넘긴다 — 뷰어가 연 책이 그 파일이다.
 *
 * ## 실패 화면의 단추 — '이 앱이 보여 줄 수 없다' 로 끝났을 때만
 *
 * | 실패 | 단추 | 까닭 |
 * |---|---|---|
 * | UNSUPPORTED | 단다 | 우리가 열지 않기로 한 갈래(iso·분할·압축 스트림 하나). 압축 관리자는 연다 |
 * | CORRUPT | 단다 | 우리 리더가 못 읽은 구조. 복구 기능이 있는 앱이 있다 |
 * | ENCRYPTED | 단다 | 우리가 풀지 않는 방식의 잠금(PKWARE 강한 암호화 등) |
 * | TOO_LARGE | 단다 | 우리 방어 상한·시간 제한에 걸렸다. 모든 화면이 같은 규칙을 따른다 — 너무 큰 것·시간 초과는 넘길 수 있다 |
 * | NEEDS_PASSWORD | 없다 | 암호는 **이 앱이 묻고 푼다**(공개된 방식). 단추가 '암호 넣기' 와 다투면 안 된다 |
 * | UNREADABLE | 없다 | 파일이 없거나 읽을 수 없다. 넘겨도 열리지 않는다 |
 * | NO_PAGES | 없다 | 열렸고 그림이 없다. 보여 줄 것이 없는 것이지 못 보여 주는 것이 아니다(메뉴에는 남는다) |
 *
 * ⋮ 메뉴는 **사용자가 고른 것**이라 더 넓다 — 책이 보통 파일이면 열린 책에도, 암호를 묻는 책에도 선다. 파일이 없어진
 * 책(UNREADABLE)만 뺀다.
 */
internal object ComicOpenWith {

    private val FAILURES_TO_HAND_OFF = setOf(Kind.UNSUPPORTED, Kind.CORRUPT, Kind.ENCRYPTED, Kind.TOO_LARGE)

    /** 실패 화면에 단추를 단다면 넘길 경로. [file] 은 책이 보통 파일일 때의 경로(폴더·없는 파일이면 null). */
    fun failureTarget(kind: Kind, file: String?): String? = file?.takeIf { kind in FAILURES_TO_HAND_OFF }

    /**
     * ⋮ 메뉴가 넘길 경로. [failure] 가 null 이면 열린 책이다. 폴더거나 파일이 없어졌으면 null — 메뉴 줄을 감춘다.
     */
    fun menuTarget(file: String?, failure: Kind?): String? = file?.takeUnless { failure == Kind.UNREADABLE }

    /**
     * 못 연 쪽('이 쪽을 열 수 없습니다') 곁의 단추가 넘길 경로 — 쪽이 **디스크 위의 파일**([ComicSource.pageFile])이고 그 파일이
     * **실제로 열릴 때만**([reachable]). 아카이브 안의 쪽([pageFile] 이 null)과, 그새 지워졌거나 열리지 않는 파일에는 null 이다 —
     * 받는 앱도 그 파일에 닿지 못한다. 쪽이 왜 못 열렸는지(디코더가 못 풀었다·너무 크다)는 가르지 않는다 — 파일에 닿는다면
     * 전부 '이 앱이 보여 주지 못했다' 이고 모든 화면이 그것을 넘긴다.
     *
     * [reachable] 은 여는 탐침이라 디스크 입출력이다 — 부르는 쪽이 입출력 디스패처에서 부른다(`ComicPageStore.failedPageTarget`).
     */
    fun pageFailureTarget(pageFile: File?, reachable: (File) -> Boolean = ::isReachable): String? =
        pageFile?.takeIf(reachable)?.path

    /** 지금 상태에서 ⋮ 메뉴가 넘길 경로. 여는 중에는 아직 책이 없다. */
    fun menuTarget(state: ComicViewModel.State): String? = when (state) {
        is ComicViewModel.State.Loading -> null
        is ComicViewModel.State.Ready -> menuTarget(state.book.file, failure = null)
        is ComicViewModel.State.Failed -> menuTarget(state.file, state.kind)
    }
}

/**
 * 경로 하나를 **다른 앱으로** 여는 함수. 언제나 고르는 창이다(`ExternalOpen.Mode.CHOOSE`) — 이 화면에서 그 길로 오는
 * 것은 전부 사용자가 '다른 앱으로' 를 누른 것이다. 띄우지 못했으면(파일이 그새 사라졌다·받는 앱이 거절했다) **우리
 * 문장**([ExternalOpen.messageOf])을 이 화면의 스낵바로 알린다 — 예외 문구는 내보내지 않는다. 띄웠으면 알릴 것이 없다
 * (이 형식을 받을 앱이 보이지 않으면 `ExternalOpen` 이 모든 앱에서 고르는 창으로 넓힌다 — 어느 쪽이든 우리 쪽에는 띄운 것으로 보인다).
 *
 * **액티비티의 컨텍스트로, 주 스레드에서 부른다**(`LocalContext`). 받는 앱의 화면이 우리 태스크 위에 쌓여 뒤로 가면
 * 이 화면으로 돌아온다(`ExternalOpen` 의 '태스크'). 호출은 동기이고 가볍다. 경로는 **누를 때** 받는다 — 다음 권으로
 * 넘어가면 책이 화면을 떠나지 않고 바뀐다.
 *
 * 문장은 `LocalResources` 에서 읽는다 — 컨텍스트에서 읽으면 구성이 바뀌어도 다시 읽히지 않는다(lint 의
 * `LocalContextGetResourceValueCall`). 형제 화면(`TextOpenWith`·`DocOpenWith`)과 같은 모양이다.
 */
@Composable
internal fun rememberOpenWith(snackbar: SnackbarHostState): (path: String) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(context, resources, snackbar, scope) {
        { path ->
            ExternalOpen.messageOf(ExternalOpen.open(context, path, ExternalOpen.Mode.CHOOSE))?.let { id ->
                val message = resources.getString(id)
                scope.launch { snackbar.showSnackbar(message) }
            }
        }
    }
}
