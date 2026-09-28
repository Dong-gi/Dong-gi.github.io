package io.github.donggi.iroiroviewer.io

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.SystemClock
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import java.io.File

/**
 * 파일을 **다른 앱으로** 연다. 이 앱이 다루지 않는 파일(APK·글꼴·모르는 형식)을 누를 때와, 뷰어가 '이 앱이 다루지
 * 않는다' 로 끝날 때, 그리고 사용자가 '다른 앱으로 열기' 를 고를 때의 길이다.
 *
 * ## 지키는 것
 *
 * * **URI 는 [ShareHelper.uriOf] 한 곳에서만 만든다**(그 파일의 KDoc). 저장 볼륨 밖(앱 내부·캐시 밖)의 파일은
 *   넘기지 않는다 — [Result.NOT_SHAREABLE]. 볼륨 안이어도 휴지통은 넘기지 않는다([refuses]). 압축 안의 항목은 경로가
 *   없어 애초에 이 길에 오지 않는다(압축 화면의 '엔트리를 디스크로 뽑지 않는다').
 * * 권한은 **이 인텐트에만, 읽기만** 준다(`FLAG_GRANT_READ_URI_PERMISSION`). 쓰기를 주지 않는다 — 받는 앱이
 *   사용자의 파일을 고쳐 쓰게 되는 길을 우리가 열지 않는다.
 * * **실패는 결과로 돌려준다.** 예외 문구는 화면에 나가지 않는다 — 화면은 [messageOf] 의 우리 문장을 쓴다.
 * * **메인 스레드에서, 컴포지션의 액티비티 컨텍스트(`LocalContext.current`)로 부른다.** 동기이고 가볍다(경로 정규화,
 *   고르는 창이면 `PackageManager` 질의 한두 번, `startActivity` 한 번). 아래 '두 번 누르기' 의 기록도 그래서 잠그지 않는다.
 *
 * ## 두 모양
 *
 * [Mode.VIEW] 는 파일을 눌러 여는 길이다. 받는 앱이 하나면(APK 의 설치 관리자) 곧바로 뜨고, 여럿이면 시스템의
 * '다음으로 열기'(한 번만/항상)가 뜬다. [Mode.CHOOSE] 는 '다른 앱으로 열기' 메뉴의 길이라 **언제나 고르게** 한다
 * (`createChooser` — 기본 앱이 정해져 있어도 묻는다). MIME 이 와일드카드면([ExternalMime.isWildcard]) VIEW 로 불러도
 * 고르는 창이다 — 그 까닭은 [ExternalMime] 의 '와일드카드는 언제나 고르는 창이다'. 곧바로 여는가 고르는 창인가는
 * [usesChooser], 어느 고르는 창인가는 [chooserPlan] 한 곳이 정한다.
 *
 * **고르는 창에서는 [Result.NO_APP] 이 나오지 않는다.** `ACTION_CHOOSER` 는 언제나 시스템의 고르는 창이 받으므로
 * 받을 앱이 없어도 창은 뜬다 — 우리 쪽에는 [Result.STARTED] 로 보인다. 그래서 [Result.NO_APP] 은 곧바로 여는 길
 * ([Mode.VIEW] + 구체적인 MIME)에서만 나오고, 목록은 그때 정보 시트를 띄워 거기서 [Mode.CHOOSE] 로 다시 고르게 한다 —
 * 그 길이 아래의 '모든 앱에서 고르기' 에 닿는다. 받는 앱이 하나뿐이면 고르는 창이 그 앱을 곧바로 띄울 수 있다 —
 * '언제나 묻는다' 는 '기본 앱을 쓰지도, 정하지도 않는다' 는 뜻이다.
 *
 * 고르는 창 쪽의 거동(빈 화면 `NoAppsAvailableEmptyStateProvider`, 하나면 곧바로 띄우는
 * `shouldAutoLaunchSingleChoice`, 고른 앱을 띄우는 `DisplayResolveInfo.startAsCaller`)은 SDK 소스(android-36.1)의
 * `com.android.internal.app` 으로 읽었는데, **그것은 옛 고르는 창이다** — 그 파일 머리말이 '시스템이 실제로 쓰는 것은
 * IntentResolver 모듈' 이라고 적는다. 에뮬레이터에서 본 것(2026-09-28): API 31 은 그 옛 창(`android/…ChooserActivity`)이고,
 * API 35 는 IntentResolver(`com.android.intentresolver`)다. 둘 다 받는 앱이 하나면 곧바로 띄우고(API 35 는 우리를
 * `realCallingUid` 로 달고 띄워 설치 관리자가 받아들인다), 받을 앱이 없으면 'No apps can perform this action.' 뿐이며,
 * **VIEW 의 고르는 창에 제목을 그리지 않는다.**
 *
 * ## 이 형식을 아는 앱이 없으면 — 모든 앱에서 고르기
 *
 * 우리 표나 플랫폼 표가 **구체적인 MIME** 을 주는데 그것을 선언한 앱이 기기에 하나도 없으면, 그 MIME 의 고르는 창은
 * 받을 앱이 없다는 빈 화면만 띄운다(에뮬레이터에서 봤다 — `.xyz` 는 플랫폼 표가 `chemical/x-xyz` 로,
 * `.iso`·`.mobi`·`.ttf` 는 우리 표가 그 형식의 이름으로 준다). 사용자는 그래도 어떤 앱으로든 열어 보고 싶어 한다(요청).
 * 그래서 고르는 창을 띄우기 전에 **그 MIME 을 받을 다른 앱이 보이는지 묻고**([hasHandler]), 없으면 같은 URI 를
 * 모든 것([ExternalMime.ANY])으로 넘기는 고르는 창을 띄운다([Plan.ALL_APPS]). 모든 것은 `content:` 를 받는 필터
 * (스킴을 적지 않았거나 `content` 를 적었고, 호스트가 우리 URI 에 맞는 것)에 MIME 을 하나라도 선언한 앱이 다 받는다
 * (`IntentFilter.findMimeType` 이 모든 것을 따로 다루는 갈래 — SDK 소스 android-36.1 에서 읽었다).
 * **이 길은 언제나 고르는 창이다** — 모든 것으로 기본 앱을 정하게 두면 MIME 이 붙은 모든 열기가 그 앱으로 간다
 * ([ExternalMime] 의 '와일드카드는 언제나 고르는 창이다'). 제목을 [R.string.io_open_with_any] 로 바꿔 **이 앱들이
 * 이 형식을 안다고 한 적은 없다**는 것을 알린다([titleOf]) — 다만 두 에뮬레이터의 고르는 창은 그 제목을 그리지 않았다
 * (위). 제목을 그리는 판에서만 보이는 구분이고, 목록에서 누른 길은 정보 시트의 문장이 먼저 말한다. 계열 전체(그림·소리·영상)와 글(`text/plain`)도 받는 앱이
 * 없으면 같다 — 규칙은 '빈 고르는 창을 띄우지 않는다' 하나다.
 *
 * **처음부터 모든 것이면**(확장자가 없거나 어느 표도 모르는 파일) 묻지 않고 곧장 이 창이다. 더 넓힐 곳이 없고, 목록도
 * 넓혀서 띄우는 창과 **같은 앱들**이다. 같은 목록에 제목만 둘이면(모르는 `.bin` 은 '다른 앱으로 열기', 선언한 앱이 없는
 * `.xyz` 는 '모든 앱에서 고르기') 사용자는 무엇이 다른지 알 길이 없다 — 모든 것의 고르는 창은 언제나 같은 이름이다.
 *
 * **묻는 것은 넓힐지만 정한다. 막는 데 쓰지 않는다.** 모든 것까지 받는 앱이 없다고 [Result.NO_APP] 을 돌려주는 길도
 * 있었지만 쓰지 않았다. 그 대답이 틀리는 길(아래 공개 범위가 기기에서 읽은 것과 다르게 도는 경우)에서는 모르는 파일을
 * 하나도 열 수 없게 되고, 맞는 길에서 얻는 것은 시스템의 빈 화면 대신 우리 문장 하나뿐이다. 잘못 넓히는 쪽의 대가는
 * 목록이 길어지고 제목이 '모든 앱' 이 되는 것뿐이다.
 *
 * ## 묻는 질의와 패키지 공개 범위
 *
 * API 30 부터 `queryIntentActivities` 는 **우리에게 보이는 앱만** 답한다. 그래서 이 모듈의 매니페스트가 `<queries>` 로
 * 'content URI 를 아무 형식으로 보는 VIEW' 를 선언한다(그 주석). 매니페스트 병합이 그것을 앱에 싣는다. 질의는 고르는
 * 창이 목록을 세우는 것과 같다 — `MATCH_DEFAULT_ONLY`(옛 고르는 창 `ResolverListController` 가 기본으로 켠다)에, 그
 * 창의 `filterIneligibleActivities` 처럼 내보내지 않았거나 우리가 못 가진 권한을 요구하는 것은 세지 않고([counts]),
 * **우리 자신도 세지 않는다.**
 *
 * **확인한 것과 못 한 것.** 선언이 무엇으로 읽히는지(`ParsingPackageUtils.parseQueries` — MIME 하나와 스킴 하나가
 * `content:` 데이터와 모든 것의 형식을 가진 인텐트가 된다)와, 그 인텐트가 어느 필터에 맞는지(`IntentFilter.matchData` —
 * 형식을 하나라도 적은 필터는 모든 것에 맞고, 스킴을 적지 않은 필터는 `content:` 를 받으며, 스킴을 적었으면 `content`
 * 가 들어 있어야 한다. 선언의 데이터에는 호스트가 없어 호스트를 적은 필터는 맞지 않는데, 그런 필터는 우리 FileProvider
 * 의 URI 도 받지 않는다. 호스트 `*` 만은 와일드카드를 받는 비교에서 맞는다)는 SDK 소스(android-36.1)로 읽었다. 그러면
 * **우리 인텐트를 받을 수 있는 필터는 다 이 선언에도 맞는다.** 어긋나더라도(호스트 `*` 를 AppsFilter 가 와일드카드로
 * 비교하지 않는 등) 결과는 안 보이는 앱 하나 때문에 넓히는 것뿐이다 — 모든 것의 고르는 창에는 그 앱도 선다. 그 인텐트로
 * 남의 앱을 보이게 하는 쪽(`AppsFilter`)은 system_server 의 코드라 소스가 여기 없다. **에뮬레이터 둘(API 31·35)에서
 * 공개가 들었다** — 이진 `.txt` 의 고르는 창이 넓혀지지 않고 Chrome·HTML Viewer 로 떴다(공개가 듣지 않았다면 받는 앱이
 * 안 보여 모든 것으로 넓혔을 것이다).
 *
 * ## 우리 자신
 *
 * 병합된 매니페스트에 VIEW 필터가 하나 있다 — media3 의 `BluetoothValidationActivity`(`content://media/…` 의 소리,
 * `DEFAULT` 범주 없음, 블루투스 특권 권한). 범주가 없어 `MATCH_DEFAULT_ONLY` 에 안 잡히고 호스트도 우리 URI 와 다르다.
 * 그래도 고르는 창에서 우리 화면을 뺀다(`EXTRA_EXCLUDE_COMPONENTS`, [ownHandlers]) — 언젠가 이 앱이 VIEW 를 받게 되면
 * '다른 앱으로' 에서 우리가 우리를 고르게 된다. 지금은 뺄 것이 없다.
 *
 * ## 권한이 받는 앱에 닿는 길
 *
 * 곧바로 여는 길은 인텐트의 데이터와 `FLAG_GRANT_READ_URI_PERMISSION` 이 그대로 받는 앱에 간다. 고르는 창은 두 겹이다.
 * `Intent.createChooser`(프레임워크 코드라 판마다 같다)는 안쪽 인텐트의 권한 깃발과 데이터 URI 를 **고르는 창 자신의**
 * `ClipData` 로 옮겨 단다(미리보기용 — 고르는 창이 파일을 읽을 수 있게). 사용자가 고른 앱은 고르는 창이
 * **우리 이름으로** 띄운다(프레임워크의 `Intent.migrateExtraStreamToClipData` 가 '고르는 창은 고른 것을 부른 쪽으로서
 * 띄운다' 고 적는다. 옛 고르는 창에서는 `startAsCaller`). 안쪽 인텐트는 데이터와 깃발을 그대로 들고 있으므로 권한은
 * 우리 FileProvider 에서 곧장 그 앱으로 간다.
 *
 * ## 두 번 누르기
 *
 * 받는 앱의 창이 뜨기 전(수백 ms)에는 우리 화면이 아직 탭을 받는다. 그 사이에 같은 파일을 한 번 더 누르면 고르는 창·
 * 설치 관리자가 두 벌 쌓인다. 그래서 **같은 파일을 같은 모양으로 [REPEAT_WINDOW_MS] 안에 다시 띄우지 않는다**
 * ([isRepeat]). 모든 화면이 이 함수를 지나므로 뷰어의 '다른 앱으로 열기' 단추마다 막지 않아도 된다. 띄우지 못한
 * 결과(`NO_APP` 등)는 기억하지 않는다 — 다시 누르면 다시 시도하고 다시 알린다.
 *
 * ## APK
 *
 * 설치 관리자는 **보낸 앱이 `REQUEST_INSTALL_PACKAGES` 를 선언했는지** 본다. 선언은 `app` 의 매니페스트에 있고,
 * 무엇을 확인했는지도 거기 적었다. 선언하지 않으면 설치 관리자가 **아무 말 없이 닫혀** 우리에게는 [Result.STARTED]
 * 로 보인다 — 선언이 빠진 것을 결과로 알아챌 길이 없으므로 매니페스트의 그 줄을 지우지 마라.
 *
 * ## 태스크
 *
 * 액티비티에서 부르면 받는 앱의 화면이 **우리 태스크 위에** 쌓인다 — 뒤로 가면 우리 목록이다. 액티비티가 아닌
 * 컨텍스트(서비스 등)에서 부를 때만 `FLAG_ACTIVITY_NEW_TASK` 를 단다(없으면 `startActivity` 가 던진다).
 * Compose 의 `LocalContext` 는 액티비티를 감싼 `ContextWrapper` 일 수 있어 벗겨 가며 찾는다.
 */
object ExternalOpen {

    enum class Mode {
        /** 기본 앱이 있거나 받는 앱이 하나면 곧바로, 여럿이면 시스템의 '다음으로 열기'. 파일을 눌러 여는 길. */
        VIEW,

        /** 언제나 고르는 창. '다른 앱으로 열기' 메뉴의 길 — 사용자가 고르겠다고 말했다. */
        CHOOSE,
    }

    enum class Result {
        /** 다른 앱(또는 고르는 창)을 띄웠다. 그 뒤의 일(설치 거절·앱의 오류·고르는 창의 '앱 없음')은 우리가 알 수 없다. */
        STARTED,

        /** 이 파일을 받겠다는 앱이 기기에 없다. 곧바로 여는 길([Mode.VIEW] + 구체적인 MIME)에서만 나온다. */
        NO_APP,

        /** 넘길 수 없는 파일이다 — 없거나, 폴더거나, 저장 볼륨 밖이거나, 휴지통 안이다([refuses]). */
        NOT_SHAREABLE,

        /** 띄우다 막혔다(받는 앱이 우리 호출을 거절했다 등). */
        FAILED,
    }

    fun open(context: Context, path: String, mode: Mode = Mode.VIEW): Result {
        val now = SystemClock.uptimeMillis()
        // 방금 띄운 것을 또 누른 것이다. 앞의 것이 뜨는 중이므로 알릴 것도 없다.
        if (isRepeat(lastStart, path, mode, now)) return Result.STARTED
        if (refuses(path)) return Result.NOT_SHAREABLE
        val uri = ShareHelper.uriOf(context, path) ?: return Result.NOT_SHAREABLE
        val mime = ExternalMime.resolve(File(path).name, ::platformMime)
        val view = viewOf(uri, mime)
        val plan = chooserPlan(mode, mime) { hasHandler(context, view) }
        val intent = when (plan) {
            Plan.DIRECT -> view
            // 권한이 고른 앱에 닿는 길은 이 객체의 KDoc('권한이 받는 앱에 닿는 길'). 모든 것으로 넘길 때도 **같은 URI 와
            // 같은 읽기 권한**이다 — 바뀌는 것은 형식 하나다.
            Plan.CHOOSER -> chooserOf(context, view, plan)
            Plan.ALL_APPS -> chooserOf(context, viewOf(uri, ExternalMime.ANY), plan)
        }
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            lastStart = Started(path, mode, now)
            Result.STARTED
        } catch (e: ActivityNotFoundException) {
            Result.NO_APP
        } catch (e: SecurityException) {
            // 받는 액티비티가 내보내지 않았거나 권한을 요구한다. 문구는 화면에 내지 않는다.
            Iro.d { "다른 앱으로 열지 못했다: ${e.javaClass.simpleName}" }
            Result.FAILED
        }
    }

    /**
     * 고르는 창으로 띄우는가. 사용자가 고르겠다고 했거나([Mode.CHOOSE]) MIME 이 와일드카드라 기본 앱을 정하게 두면
     * 안 될 때다. 곧바로 여는 길은 구체적인 MIME 의 [Mode.VIEW] 하나뿐이다.
     */
    internal fun usesChooser(mode: Mode, mime: String): Boolean =
        mode == Mode.CHOOSE || ExternalMime.isWildcard(mime)

    /** 무엇을 띄우는가. */
    internal enum class Plan {
        /** 인텐트를 그대로. 받는 앱이 없으면 `startActivity` 가 던져 [Result.NO_APP] 이 된다. */
        DIRECT,

        /** 이 MIME 의 고르는 창. 이 형식(이나 그 계열)을 받겠다는 다른 앱이 보인다. */
        CHOOSER,

        /**
         * 모든 것([ExternalMime.ANY])의 고르는 창. 이 형식을 받겠다는 다른 앱이 보이지 않거나, 처음부터 형식을 모른다 —
         * 어느 쪽이든 거기 선 앱은 이 형식을 안다고 한 적이 없다.
         */
        ALL_APPS,
    }

    /**
     * 어느 창을 띄우는가. 곧바로 여는 길([usesChooser] 가 아니다)은 묻지 않는다 — [Mode.VIEW] 는 예전 그대로이고, 받는
     * 앱이 없으면 [Result.NO_APP] 으로 끝나 화면이 [Mode.CHOOSE] 로 다시 고르게 한다. 고르는 창이면 [hasHandler] 에 한 번
     * 묻고, 받을 앱이 없으면 모든 것으로 넓힌다. 이미 모든 것이면 묻지 않고 모든 것의 창이다 — 넓힐 곳이 없고, 대답으로
     * 막지 않으며(이 객체의 KDoc '묻는 것은 넓힐지만 정한다'), 넓혀서 띄우는 창과 목록이 같으니 이름도 같아야 한다
     * ('처음부터 모든 것이면').
     *
     * [hasHandler] 는 필요할 때만 부른다(질의 한 번이 바인더 호출이다).
     */
    internal fun chooserPlan(mode: Mode, mime: String, hasHandler: () -> Boolean): Plan = when {
        !usesChooser(mode, mime) -> Plan.DIRECT
        mime == ExternalMime.ANY -> Plan.ALL_APPS
        hasHandler() -> Plan.CHOOSER
        else -> Plan.ALL_APPS
    }

    /** 고르는 창의 제목. 모든 앱을 보여 줄 때는 그렇다고 말한다 — 거기 선 앱은 이 형식을 안다고 한 적이 없다. */
    @StringRes
    internal fun titleOf(plan: Plan): Int? = when (plan) {
        Plan.DIRECT -> null
        Plan.CHOOSER -> R.string.io_open_with
        Plan.ALL_APPS -> R.string.io_open_with_any
    }

    /**
     * 질의의 답 하나를 받는 앱으로 세는가 — 고르는 창이 목록에서 거르는 것(`ResolverListController.filterIneligibleActivities`
     * 의 `checkComponentPermission`)과 같게 거른다: 남의 앱이고, 내보냈고, 요구하는 권한이 있으면 우리가 가졌다. 세었는데
     * 고르는 창이 거르면 빈 창이 뜨고, 거를 것을 안 세면 넓히지 않아도 될 때 넓힌다 — 앞의 것만 피하면 된다.
     *
     * @param holds 우리가 이 권한을 가졌는가. 권한을 요구할 때만 부른다.
     */
    internal fun counts(
        ownPackage: String,
        packageName: String,
        exported: Boolean,
        permission: String?,
        holds: (String) -> Boolean,
    ): Boolean = packageName != ownPackage && exported && (permission == null || holds(permission))

    // `setData` 뒤에 `setType` 을 부르면 데이터가 지워진다 — 둘은 언제나 함께 준다.
    private fun viewOf(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun chooserOf(context: Context, view: Intent, plan: Plan): Intent {
        val chooser = Intent.createChooser(view, context.getString(checkNotNull(titleOf(plan))))
        val own = ownHandlers(context, view)
        if (own.isNotEmpty()) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own)
        return chooser
    }

    /**
     * [view] 를 받을 **다른 앱**이 보이는가. 고르는 창과 같은 질의(`MATCH_DEFAULT_ONLY`)에 [counts] 로 거른다. 보이는
     * 범위는 이 모듈 매니페스트의 `<queries>` 가 정한다(이 객체의 KDoc '묻는 질의와 패키지 공개 범위').
     */
    private fun hasHandler(context: Context, view: Intent): Boolean {
        val own = context.packageName
        return query(context, view).any { info ->
            val a = info.activityInfo ?: return@any false
            counts(own, a.packageName, a.exported, a.permission) {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * 우리 앱 안에서 [view] 를 받겠다는 화면. 고르는 창에서 뺀다(이 객체의 KDoc '우리 자신'). 우리 꾸러미로 좁혀
     * 물으므로 답이 짧다.
     */
    private fun ownHandlers(context: Context, view: Intent): Array<ComponentName> =
        query(context, Intent(view).setPackage(context.packageName))
            .mapNotNull { info -> info.activityInfo?.let { ComponentName(it.packageName, it.name) } }
            .toTypedArray()

    // `ResolveInfoFlags` 를 받는 판은 API 33 부터다(minSdk 31). 정수 판은 뜻이 같고 33 이후에도 돈다.
    @Suppress("DEPRECATION")
    private fun query(context: Context, intent: Intent): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

    /**
     * 저장 볼륨 안이어도 넘기지 않는 경로 — 휴지통(`TrashStore.DIR_NAME`)과 그 안. 휴지통의 파일은 uuid 이름에 확장자가
     * 없어 모든 것([ExternalMime.ANY])의 고르는 창에 알아볼 수 없는 이름으로 간다. 화면이 휴지통 항목에 이 단추를 내지
     * 않는 것이 규칙이지만, 모든 화면이 지나는 이곳에서 한 번 더 막는다.
     */
    internal fun refuses(path: String): Boolean = TrashStore.isTrashPath(path)

    /** 띄운 기록. 무엇을 어떤 모양으로 언제(`uptimeMillis`) 띄웠나. */
    internal class Started(val path: String, val mode: Mode, val at: Long)

    /**
     * 두 번 누르기를 한 번으로 치는 폭. 실수로 두 번 누르는 간격은 안드로이드가 두 번 누르기로 치는 폭
     * (`ViewConfiguration` 의 300ms) 안이다. 그보다 넉넉하되 **고르는 창을 보고 닫은 뒤 다시 누르는 것**은 막지 않을
     * 만큼 짧게 둔다 — 그 창이 뜨는 데만 수백 ms 가 들고 사람이 보고 닫는 데 또 그만큼 든다. 1초로 두면 빠른 사람의
     * 다시 누르기가 한 번 먹힌다. 목록 화면은 따로 제 누름 전체를 잠깐 흘려보낸다(`feature:browser` 의 `SETTLE_MS`) —
     * 그쪽은 시트가 닫힌 자리 아래의 **다른 줄**이 눌리는 것까지 막는 것이라 이것과 겹치지 않는다.
     */
    internal const val REPEAT_WINDOW_MS = 500L

    // 메인 스레드에서만 불리므로(이 객체의 사용 규칙) 잠그지 않는다. 프로세스가 죽으면 사라져도 되는 값이다.
    private var lastStart: Started? = null

    /** 방금 띄운 것과 같은 파일·같은 모양을 폭 안에서 또 부른 것인가. 시계가 뒤로 가면(음수) 새로 친다. */
    internal fun isRepeat(last: Started?, path: String, mode: Mode, now: Long): Boolean =
        last != null && last.path == path && last.mode == mode && now - last.at in 0 until REPEAT_WINDOW_MS

    /** 결과를 알릴 문장. 띄웠으면 알릴 것이 없다(null). */
    @StringRes
    fun messageOf(result: Result): Int? = when (result) {
        Result.STARTED -> null
        Result.NO_APP -> R.string.io_open_with_no_app
        Result.NOT_SHAREABLE -> R.string.io_open_with_not_shareable
        Result.FAILED -> R.string.io_open_with_failed
    }

    /** 플랫폼의 확장자 표. 기기마다 다르고, 모르면 null 이다. */
    private fun platformMime(ext: String): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)

    // 받는 쪽이 널 가능한 것은 `ContextWrapper.baseContext` 가 붙기 전에는 null 이기 때문이다(플랫폼 타입이라
    // 널 아님으로 받으면 그 자리에서 NPE 다).
    private tailrec fun Context?.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
