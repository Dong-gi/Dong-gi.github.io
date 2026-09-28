package io.github.donggi.iroiroviewer.browser

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 목록의 파일을 눌렀을 때 가는 곳.
 *
 * **`when` 이 `else` 로 끝나지 않는다.** 예전 분기는 `else -> 정보 시트` 로 끝나서, 종류가 하나 늘거나 옮겨 가면
 * 컴파일러가 아무 말도 하지 않은 채 새 종류가 엉뚱한 길로 떨어질 수 있었다(함정 표 '종류를 옮기면 그것을 검사하던
 * `when` 을 전부 찾아라'). 이제는 뷰어가 없는 종류가 정보 시트가 아니라 **다른 앱**으로 가므로 잘못 떨어진 값이 더
 * 비싸다 — 뷰어가 있는 파일을 다른 앱에 넘기게 된다. 그래서 종류마다 길을 적고, 종류가 늘면 여기서 컴파일이 멈추며,
 * 시험(`OpenWithTest`)이 모든 값을 표로 박는다.
 *
 * 순수 함수로 둔 것은 그 표를 JVM 에서 지키려는 것이다 — 화면의 람다 안에 있으면 눌러 봐야만 틀린 것이 보인다.
 */
internal enum class TapRoute {
    /** 폴더로 들어간다. */
    FOLDER,

    /** 소리·영상. 폴더를 큐로 만들어 재생 화면을 연다. */
    PLAYER,

    /** 이미지 뷰어. 좌우로 넘길 목록은 지금 보이는 폴더의 이미지들이다. */
    IMAGE,

    /** 텍스트·코드 뷰어. */
    TEXT,

    /** 만화 뷰어. 확장자로 만화라고 말한 것만. */
    COMIC,

    /** 압축 목록 화면. */
    ARCHIVE,

    /** 문서 뷰어(PDF·EPUB·오피스·한글). 그 안의 못 여는 형식은 뷰어가 말한다. */
    DOCUMENT,

    /**
     * 이 앱에 뷰어가 없다(APK·글꼴·모르는 형식). **다른 앱으로 연다** — `ExternalOpen.Mode.VIEW` 라 받는 앱이
     * 하나거나 기본 앱이 정해져 있으면 곧바로 뜨고(APK 는 설치 관리자), 여럿이면 시스템의 '다음으로 열기' 다. 형식을
     * 모르면(모든 것) 모든 앱에서 고르는 창이고, 그 형식을 받을 앱이 없으면 정보 시트다([OpenWithRules.afterTap]).
     */
    EXTERNAL,

    /**
     * 정보 시트만. 다른 앱에 넘기지 않는 것 — **휴지통 안의 항목**(지운 것을 다른 앱이 받아 가면 지운 것이 지워지지
     * 않은 셈이다)과, 폴더라고 적혔는데 폴더가 아닌 항목(나열이 만들지 않는 모양이라 예전처럼 정보만 보인다).
     */
    DETAIL,
    ;

    companion object {

        fun of(entry: FileEntry): TapRoute =
            of(entry.isDirectory, entry.kind, inTrash = TrashStore.isTrashPath(entry.path))

        /**
         * @param inTrash 휴지통 안의 경로인가. 목록은 휴지통 폴더를 내보내지 않지만(`DirectoryLister`), 다른 앱에
         *   넘기는 길은 **되돌릴 수 없는 유출**이라 여기서 한 번 더 막는다. 이 앱의 뷰어로 여는 것은 밖으로 나가지
         *   않으므로 그대로 둔다.
         */
        fun of(isDirectory: Boolean, kind: FileKind, inTrash: Boolean): TapRoute {
            if (isDirectory) return FOLDER
            return when (kind) {
                FileKind.FOLDER -> DETAIL
                FileKind.AUDIO, FileKind.VIDEO -> PLAYER
                FileKind.IMAGE -> IMAGE
                FileKind.TEXT, FileKind.CODE -> TEXT
                FileKind.COMIC -> COMIC
                FileKind.ARCHIVE -> ARCHIVE
                FileKind.PDF, FileKind.EBOOK,
                FileKind.DOCUMENT, FileKind.SHEET, FileKind.SLIDE,
                FileKind.HWP -> DOCUMENT
                FileKind.APK, FileKind.FONT, FileKind.OTHER -> if (inTrash) DETAIL else EXTERNAL
            }
        }
    }
}

/**
 * 정보 시트에 띄울 것.
 *
 * @param notice 다른 앱으로 넘기지 못한 까닭(`ExternalOpen.messageOf` 의 문장). **시트 안에도 적는다** — 정보 시트는
 *   화면 아래를 덮는 창이라 같은 순간에 뜬 스낵바가 그 밑에 깔려 보이지 않는다. 누른 파일이 왜 열리지 않고 정보만
 *   떴는지 말하지 않으면 사용자는 앱이 누른 것을 잘못 알아들었다고 여긴다.
 */
internal data class DetailRequest(
    val entry: FileEntry,
    @StringRes val notice: Int? = null,
)

/** 다른 앱으로 열기의 순수 규칙. 화면은 이것을 부르기만 한다. */
internal object OpenWithRules {

    /**
     * 다른 앱에 넘겨도 되는 항목인가. 폴더가 아니고 휴지통 안이 아닌 파일이다. 정보 시트의 단추와 선택 메뉴가
     * **같은 판정**을 쓴다 — 두 곳이 따로 정하면 한쪽에서만 휴지통이 새어 나간다.
     */
    fun canHandOff(entry: FileEntry): Boolean =
        !entry.isDirectory && entry.kind != FileKind.FOLDER && !TrashStore.isTrashPath(entry.path)

    /**
     * 선택 모드의 '다른 앱으로 열기'·'정보' 가 겨눌 항목. **정확히 하나를 골랐고 그것이 넘길 수 있는 파일일 때만**
     * 그 항목이고, 아니면 null(메뉴 항목이 꺼진다 — '이름 바꾸기' 가 하나일 때만 켜지는 것과 같다).
     *
     * [entries] 는 **지금 보이는 목록**이다. 이름 필터로 가려진 것은 여기 없어 null 이 된다 — 보이지 않는 파일을
     * 다른 앱에 넘기게 두지 않는다.
     */
    fun singleFile(selection: Set<String>, entries: List<FileEntry>): FileEntry? {
        if (selection.size != 1) return null
        val path = selection.first()
        return entries.firstOrNull { it.path == path }?.takeIf(::canHandOff)
    }

    /**
     * 눌러서 다른 앱으로 넘긴 뒤. 떴으면 할 일이 없고(null), 아니면 **정보 시트를 까닭과 함께** 띄운다 — 적어도
     * 무엇을 눌렀는지(종류·크기·경로)는 보여야 하고, 거기서 '다른 앱으로 열기' 로 다시 고를 수 있다.
     *
     * 까닭이 '이 형식을 아는 앱이 없습니다'([ExternalOpen.Result.NO_APP] — 곧바로 여는 길에서만 난다)여도 그 단추는
     * 헛걸음이 아니다. 고르는 창의 길은 받는 앱이 없는 형식을 **모든 앱에서 고르는 창**으로 넓힌다(`ExternalOpen` 의
     * '이 형식을 아는 앱이 없으면'). 글꼴·`.xyz` 처럼 선언한 앱이 없는 파일을 그래도 열어 보는 길이 이것이다.
     */
    fun afterTap(entry: FileEntry, result: ExternalOpen.Result): DetailRequest? =
        if (result == ExternalOpen.Result.STARTED) null else DetailRequest(entry, ExternalOpen.messageOf(result))

    /**
     * 시트의 '다른 앱으로 열기' 뒤. 떴으면 시트를 닫고(null — 할 일을 마쳤다), 아니면 시트를 남긴 채 까닭을 적는다.
     */
    fun afterChoose(shown: DetailRequest, result: ExternalOpen.Result): DetailRequest? =
        if (result == ExternalOpen.Result.STARTED) null else shown.copy(notice = ExternalOpen.messageOf(result))

    /**
     * 다른 앱을 띄운 직후 목록의 누름을 흘려보내는 시간(ms).
     *
     * `startActivity` 는 곧바로 돌아오고 받는 앱의 창은 그 뒤에 뜬다. 그 사이에 온 누름은 **아직 우리 창**에 떨어진다 —
     * 줄을 두 번 누르면 설치 관리자·고르는 창이 두 겹으로 쌓이고(뒤로를 두 번 눌러야 목록이다), 시트의 단추나 선택 메뉴의
     * 항목을 두 번 누르면 시트·메뉴가 사라진 자리 아래의 **다른 줄**이 눌려 엉뚱한 파일이 열린다(띄웠으면 선택이 끝나 있으므로
     * 두 번째 누름은 고르기가 아니라 열기다). 사람의 두 번 누름 간격보다 넉넉히 길게 잡았다. 받는 앱이 뜬 뒤에는 목록이
     * 가려져 있어 이 시간은 보이지 않는다. 받는 앱이 뜨는 데 걸리는 시간은 재지 않았다(기기 확인 대기).
     */
    const val SETTLE_MS = 700L

    /**
     * [startedAt] 에 다른 앱을 띄웠다면 [now] 에 온 누름을 흘려보내야 하는가. 둘 다 같은 단조 시계(`SystemClock.uptimeMillis`)
     * 의 값이고, 띄운 적이 없으면 [startedAt] 이 null 이다. 시계가 거꾸로 간 값([now] 가 더 이르다)은 흘려보내지 않는다 — 누름을
     * 삼키는 쪽으로 틀리면 사용자는 앱이 멈췄다고 여긴다.
     */
    fun settling(startedAt: Long?, now: Long): Boolean =
        startedAt != null && now >= startedAt && now - startedAt < SETTLE_MS
}

/**
 * 다른 앱으로 여는 것. 결과가 `STARTED` 가 아니면 **우리 문장을 이 화면의 스낵바로** 알린다 — 예외 문구는
 * 화면에 나가지 않는다(`ExternalOpen` 이 결과만 돌려준다). 띄웠으면 알릴 것이 없고, 그 시각을 적어 둔다([settling]).
 *
 * **컴포지션의 액티비티 컨텍스트로 부른다**(`LocalContext`). 액티비티에서 부르면 받는 앱의 화면이 우리 태스크 위에
 * 쌓여 뒤로 가면 이 목록이다. 호출은 동기이고 가벼워(인텐트 하나, 고르는 창이면 받을 앱을 묻는 `PackageManager` 질의
 * 한두 번 — `ExternalOpen` 의 '묻는 질의와 패키지 공개 범위') 누른 그 자리에서, 메인 스레드에서 부른다.
 */
internal class OpenWith(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val messages: Map<ExternalOpen.Result, String?>,
) {
    /** 마지막으로 다른 앱을 띄운 시각. 화면이 그리는 값이 아니라서 Compose 상태로 두지 않는다 — 적을 때마다 재구성할 까닭이 없다. */
    private var startedAt: Long? = null

    operator fun invoke(entry: FileEntry, mode: ExternalOpen.Mode): ExternalOpen.Result {
        val result = ExternalOpen.open(context, entry.path, mode)
        if (result == ExternalOpen.Result.STARTED) startedAt = SystemClock.uptimeMillis()
        messages[result]?.let { text -> scope.launch { snackbar.showSnackbar(text) } }
        return result
    }

    /** 방금 다른 앱을 띄워 그 창이 아직 뜨는 중일 수 있다 — 목록은 이 동안의 누름을 받지 않는다([OpenWithRules.SETTLE_MS]). */
    fun settling(): Boolean = OpenWithRules.settling(startedAt, SystemClock.uptimeMillis())
}

@Composable
internal fun rememberOpenWith(snackbar: SnackbarHostState): OpenWith {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 문장은 컴포지션에서 받아 둔다 — 콜백에서 `context.getString` 을 부르지 않는 것이 이 모듈의 규칙이다(설정 화면과
    // 같은 모양). 결과마다 어느 문장인지는 `messageOf` 한 곳이 정한다 — 여기서 대응표를 다시 적지 않는다.
    val messages = ExternalOpen.Result.entries.associateWith { r ->
        ExternalOpen.messageOf(r)?.let { stringResource(it) }
    }
    // 띄운 시각이 재구성마다 사라지지 않게 기억한다. 열쇠가 바뀌는 것(언어가 바뀌어 문장이 달라짐)은 드물고, 그때 잃는 것은
    // 흘려보내기 한 번뿐이다. 문장 표는 내용으로 견준다(`Map.equals`) — 매번 새로 만들어도 같은 것으로 본다.
    return remember(context, scope, snackbar, messages) { OpenWith(context, scope, snackbar, messages) }
}
