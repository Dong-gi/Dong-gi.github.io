package io.github.donggi.iroiroviewer.io

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 사용자가 넣은 **아카이브 암호를 이번 세션 동안만** 기억한다.
 *
 * ## 왜 기억해야 하는가
 *
 * 아카이브 하나를 여러 화면이 연다 — 압축 목록(`feature:archive`)이 목록을 읽고, 그림 항목을
 * 누르면 만화 뷰어(`feature:comic`)가 같은 파일을 **다시** 열고, 풀기는 파일 작업 큐가 또 연다.
 * 화면끼리는 서로를 볼 수 없으므로(의존 표 — feature 끼리 참조 금지) 암호를 건네는 자리가
 * `core` 에 있어야 한다. 탐색 인자(`SavedStateHandle`)로 넘기면 번들에 실려 디스크까지 간다.
 *
 * ## 무엇을 지키는가
 *
 * * **메모리에만 둔다.** 디스크·번들·로그 어디에도 쓰지 않는다. 프로세스가 죽으면 사라진다.
 * * **앱이 화면에서 사라지면 전부 지운다**([install]) — 시작된 액티비티가 하나도 없을 때. 다시
 *   돌아오면 다시 묻는다. 기억해 두는 편의보다 암호가 오래 머물지 않는 쪽을 골랐다(PDF 는 아예
 *   기억하지 않는다 — 문서가 열려 있는 동안 다시 물을 일이 없어서다).
 * * 넣을 때도 꺼낼 때도 **사본**이다. 받은 쪽이 쓰고 지워도 여기 있는 것은 그대로고, 여기서
 *   지워도 받은 쪽은 그대로다. 지울 때는 0 으로 덮는다.
 *
 * 열쇠는 파일의 신원([FileKey])이다 — 경로가 아니다. 같은 경로에 다른 파일이 오면 다른 열쇠가 된다.
 *
 * **지웠다는 사실을 알린다**([clears]). 암호로 풀어 놓은 목록을 들고 있는 화면은 그것을 보고
 * 다시 읽어야 한다 — 안 그러면 목록은 '풀렸다' 고 말하는데 풀기·미리보기는 암호가 없어 거절되는,
 * 서로 다른 말을 하는 화면이 된다(기기에서 봤다: 홈에 나갔다 오면 자물쇠 없는 목록이 그대로였다).
 */
object SessionPasswords {

    private val map = HashMap<String, CharArray>()

    private val _clears = MutableStateFlow(0)

    /**
     * [clear] 가 돈 횟수. **값이 아니라 '또 지웠다' 를 알리려고 센다** — 같은 값을 넣으면
     * `StateFlow` 가 합쳐 버려 두 번째 지움이 전해지지 않는다(함정 표).
     */
    val clears: StateFlow<Int> = _clears.asStateFlow()

    /** 앱이 화면에 있는가([install] 이 센다). 설치 전에는 참으로 둔다 — 시험과 초기화 순서. */
    @Volatile
    private var foreground = true

    /**
     * 기억한다. **앱이 화면에 없으면 받지 않고 거짓을 준다.**
     *
     * 암호 확인은 몇 초 걸릴 수 있고(7z 의 열쇠 유도), 그동안 사용자가 홈으로 나가면 지우는 일은
     * 이미 지나갔다. 그 뒤에 도착한 `put` 을 받으면 **앱이 뒤에 있는 내내** 암호가 메모리에 남는다
     * — '화면에서 사라지면 전부 지운다' 는 약속이 깨진다(검토가 잡았다).
     */
    @Synchronized
    fun put(key: String, password: CharArray): Boolean {
        if (!foreground) return false
        map.put(key, password.copyOf())?.fill('\u0000')
        return true
    }

    /** 사본을 준다. 받은 쪽이 다 쓰면 지운다. 없으면 null. */
    @Synchronized
    fun get(key: String): CharArray? = map[key]?.copyOf()

    @Synchronized
    fun forget(key: String) {
        map.remove(key)?.fill('\u0000')
    }

    @Synchronized
    fun clear() {
        if (map.isEmpty()) return
        for (v in map.values) v.fill('\u0000')
        map.clear()
        _clears.value += 1
    }

    /** 파일의 신원. 압축 목록·만화·풀기가 같은 식으로 구해야 서로의 암호를 찾는다. */
    fun keyOf(file: java.io.File): String = FileKey.of(file.name, file.length(), file.lastModified())

    /**
     * 앱이 화면에서 사라지면 지운다. `app` 이 시작할 때 한 번 부른다.
     *
     * 시작된 액티비티 수를 센다. 우리 화면 사이를 옮겨 갈 때(목록 → 재생 화면)는 새 액티비티의
     * `onStart` 가 옛 것의 `onStop` 보다 먼저 와서 0 을 지나지 않는다. **회전은 다르다** — 같은
     * 액티비티를 다시 만들므로 옛 것의 `onStop` 이 먼저 와 0 을 지난다. 그때는
     * `isChangingConfigurations` 가 참이라 지우지 않는다.
     */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                foreground = true
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0 && !activity.isChangingConfigurations) {
                    started = 0
                    foreground = false
                    clear()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
