package io.github.donggi.iroiroviewer

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.playback.FileListOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 재생 화면 밑에 파일 목록이 없을 때 그것을 연다(`PlayerSupport.FileListOpener` — 재생 화면이 태스크의 뿌리인 채 뒤로 갈 때).
 *
 * * 파일 목록이 **다른 태스크에** 살아 있으면 그 태스크를 앞으로 가져온다 — 새로 열면 최근 앱에 이 앱이 둘이 된다. 재생 화면은
 *   PiP 에 들어가면 제 태스크로 옮겨 가는데, API 31 은 전체 화면으로 되돌릴 때 원래 태스크로 돌려놓는다(실측) — 그사이 원래
 *   태스크가 사라졌거나 PiP 동안 런처로 새 태스크가 섰을 때 같은 드문 길을 위한 것이다. 그 목록은 있던 자리 그대로 둔다
 *   (재생 화면이 그 위에서 끝난 것과 같다).
 * * 없으면 재생 화면의 태스크에 새로 연다. 폴더는 [FileListRequests] 로 건넨다.
 *
 * ## 인텐트에 싣지 않는 것
 *
 * 폴더를 extras 로 싣지 않는다 — `MainActivity` 는 런처 화면이라 내보내져 있어, 남의 앱이 같은 extras 로 파일 목록을 아무 폴더에서나
 * 열게 할 수 있다(`withoutIntentArgs` 와 같은 판단). 그리고 **런처 인텐트(`ACTION_MAIN` + `CATEGORY_LAUNCHER`)로 열지 않는다** —
 * 그 순간에는 재생 화면이 아직 뿌리라 `MainActivity.finishIfDuplicateLaunch` 가 군더더기로 보고 끝낸다. 이름만 가리킨 인텐트로
 * 연 태스크에 나중에 런처가 오면 그것은 군더더기로 끝나고 하던 화면이 그대로 보인다(그 함수의 주석).
 */
internal object FileListLauncher : FileListOpener {

    override fun open(activity: Activity, folder: String?) {
        val other = otherFileListTask(activity)
        if (other != null) {
            try {
                other.moveToFront()
                return
            } catch (e: IllegalArgumentException) {
                // 그사이 태스크가 사라졌다 — 새로 연다.
                Iro.d { "파일 목록 태스크가 사라졌다 — 새로 연다" }
            }
        }
        FileListRequests.post(folder)
        activity.startActivity(Intent(activity, MainActivity::class.java))
    }

    /** 이 앱의 다른 태스크 가운데 뿌리가 파일 목록(`MainActivity`)인 것. */
    private fun otherFileListTask(activity: Activity): ActivityManager.AppTask? {
        val tasks = activity.getSystemService(ActivityManager::class.java)?.appTasks ?: return null
        return tasks.firstOrNull { task ->
            val info = try {
                task.taskInfo
            } catch (e: IllegalArgumentException) {
                null // 훑는 사이 사라진 태스크
            } ?: return@firstOrNull false
            info.taskId != activity.taskId && info.baseActivity?.className == MainActivity::class.java.name
        }
    }
}

/**
 * 파일 목록을 어느 폴더에서 열지 — 액티비티 사이를 건너는 요청이라 프로세스에 둔다. [FileListLauncher] 가 넣고 파일 목록(`Root`)이
 * 앞에 섰을 때 꺼낸다.
 *
 * 요청은 **값이 아니라 도착**이다 — 같은 폴더를 두 번 청해도 두 번 와야 한다. [Request] 가 `data class` 가 아닌 것이 그것이다(같은 값을
 * 합치는 `MutableStateFlow` 가 인스턴스마다 새로 낸다 — CLAUDE.md 함정 표의 '`MutableStateFlow` 는 같은 값을 합친다').
 */
internal object FileListRequests {

    class Request(val folder: String)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /** [folder] 가 null 이면(첫 화면) 남은 요청을 비운다 — 꺼내지 못한 옛 폴더가 다음에 열 파일 목록으로 새지 않게. */
    fun post(folder: String?) {
        _pending.value = folder?.let(::Request)
    }

    /** 꺼낸 그 요청일 때만 비운다 — 그사이 새로 온 것은 남는다. */
    fun consume(request: Request) {
        _pending.compareAndSet(request, null)
    }
}
