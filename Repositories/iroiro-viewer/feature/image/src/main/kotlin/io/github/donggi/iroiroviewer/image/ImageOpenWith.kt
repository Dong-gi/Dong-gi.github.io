package io.github.donggi.iroiroviewer.image

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.FileProbe
import io.github.donggi.iroiroviewer.model.FileEntry
import kotlinx.coroutines.launch
import java.io.File

/**
 * 이미지 뷰어에서 '다른 앱으로 열기' 를 **어디에 다는가.** 화면의 판단을 순수 함수로 모아 JVM 시험이 박는다
 * (`ImageOpenWithTest`). 여는 일 자체는 `core:io` 의 `ExternalOpen` 이 한다 — 언제나 고르는 창(`Mode.CHOOSE`)이다.
 * 이 화면의 두 자리(못 연 장의 단추·⋮ 메뉴)는 둘 다 사용자가 '다른 앱으로' 라고 말한 것이라 기본 앱으로 곧장 보내지 않는다.
 *
 * ## 이 뷰어가 받는 것은 언제나 디스크 위의 파일이다
 *
 * 목록은 셋에서만 온다 — 폴더 화면(그 폴더의 그림들), 갤러리(`GalleryScanner` 가 훑은 파일), 프로세스가 되살아날 때의
 * 한 장짜리 폴백(`MainActivity.entryOf`, `isFile` 을 확인한다). 압축 안의 그림은 경로가 없어 **만화 뷰어**가 연다. 휴지통
 * 폴더는 나열에서 빠지고(`DirectoryLister`) 갤러리도 건너뛰므로(`GalleryScanner`) 휴지통 항목이 여기 올 길이 없다.
 * 그래서 메뉴는 폴더가 아닌 한 서고, **지금 장이 파일에 닿지 못해 실패했을 때만**(그새 지워졌다·열리지 않는다) 빠진다.
 * 그래도 판단을 여기 한 곳에 둔다 — 이 뷰어가 경로 없는 그림을 받게 되는 날 고칠 자리가 하나이게.
 */
internal object ImageOpenWith {

    /** 한 장을 못 연 까닭. 화면의 문구는 같고(`image_failed`) 단추를 다는가만 갈린다. */
    enum class PageFailure {
        /**
         * 파일은 있고 읽을 수 있는데 **이 기기의 디코더가 풀지 못했다** — SVG·TIFF, 기기가 못 푸는 HEIC/AVIF, 깨진 파일,
         * 디코더가 거절한 너무 큰 그림. 다른 앱은 열 수 있을 수 있다.
         */
        UNDECODABLE,

        /** 파일이 없거나(그새 지워졌다) 읽을 수 없다. 다른 앱에 넘겨도 열리지 않는다 — 단추를 달지 않는다. */
        UNREADABLE,
    }

    /**
     * 디코딩이 실패한 뒤 파일을 한 번 더 보고 가른다. `ImageIo.decodeFitted` 는 두 경우 모두 null 이라 그 값만으로는
     * '없는 파일' 에도 단추를 달게 된다 — 누르면 '넘길 수 없다' 는 말만 돌아오는 단추다. 모든 화면의 규칙이 같다:
     * 파일에 닿지 못한 실패(없다·못 연다)에는 달지 않는다. 받는 앱도 같은 파일에 닿지 못한다.
     *
     * @param opens 읽으려고 **실제로 열리는가**([opensForReading]).
     */
    fun failureOf(isFile: Boolean, opens: Boolean): PageFailure =
        if (isFile && opens) PageFailure.UNDECODABLE else PageFailure.UNREADABLE

    /**
     * [file] 을 읽으려고 열어 본다 — 열리면 참. **디스크 입출력이다**(부르는 쪽이 `IroDispatchers.io` 에서 부른다).
     *
     * `canRead()` 로 묻지 않는다 — 묻는 답과 여는 답이 FUSE 위에서 같다는 것을 확인한 적이 없고, 틀리면 멀쩡한 그림이
     * '읽을 수 없다' 가 되어 단추를 잃거나, 열리지 않는 파일에 단추가 선다. 탐침은 [FileProbe] 한 벌이다(문서·압축·만화 뷰어가
     * 같은 답을 낸다). [open] 은 시험이 열리지 않는 파일을 흉내 내는 자리다(JVM 에서는 있는데 열리지 않는 파일을 만들 수 없다).
     */
    fun opensForReading(file: File, open: (File) -> Unit = FileProbe.openAndClose): Boolean =
        FileProbe.openError(file, open) == null

    /** 못 연 장에 '다른 앱으로 열기' 단추를 다는가. */
    fun offersOnFailure(failure: PageFailure): Boolean = failure == PageFailure.UNDECODABLE

    /**
     * 위 막대 ⋮ 메뉴의 대상 경로. 지금 장이 없거나 폴더거나, **지금 장이 파일에 닿지 못해 실패했으면**(UNREADABLE) null 이고,
     * 그때는 메뉴 줄을 감춘다 — 없어진 파일을 넘기는 줄은 누르면 '넘길 수 없다' 는 말만 돌아온다. 못 푼 장(UNDECODABLE)과
     * 열어 보인 장에는 선다.
     *
     * @param failure 지금 장의 실패. 열었거나 아직 여는 중이면 null 이다.
     */
    fun menuTarget(current: FileEntry?, failure: PageFailure?): String? =
        current?.takeUnless { it.isDirectory || failure == PageFailure.UNREADABLE }?.path
}

/**
 * 경로 하나를 **다른 앱으로** 여는 함수. 언제나 고르는 창이다(`ExternalOpen.Mode.CHOOSE`) — 이 화면에서 그 길로 오는
 * 것은 전부 사용자가 '다른 앱으로' 를 누른 것이다. 띄우지 못했으면(파일이 그새 사라졌다·받는 앱이 거절했다) **우리
 * 문장**([ExternalOpen.messageOf])을 이 화면의 스낵바로 알린다 — 예외 문구는 내보내지 않는다. 띄웠으면 알릴 것이 없다
 * (이 형식을 받을 앱이 보이지 않으면 `ExternalOpen` 이 모든 앱에서 고르는 창으로 넓힌다 — 어느 쪽이든 우리 쪽에는 띄운 것으로 보인다).
 *
 * **액티비티의 컨텍스트로, 주 스레드에서 부른다**(`LocalContext`). 받는 앱의 화면이 우리 태스크 위에 쌓여 뒤로 가면
 * 이 화면으로 돌아온다(`ExternalOpen` 의 '태스크'). 호출은 동기이고 가볍다. 경로는 **누를 때** 받는다 — 한 뷰어가
 * 여러 장을 넘기고, 못 연 장의 단추는 그 장의 파일을, ⋮ 는 지금 장의 파일을 넘긴다.
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
