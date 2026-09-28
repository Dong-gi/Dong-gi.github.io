package io.github.donggi.iroiroviewer.io

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.content.getSystemService
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 휴지통 자동 비우기를 **앱을 켜지 않아도** 하루에 한 번 돌린다.
 *
 * ## 왜 WorkManager 가 아니라 JobScheduler 인가
 *
 * 3·4단계 검토가 'WorkManager 를 넣으면 된다' 고 적어 두었지만, 그것은 새 의존이다. 하는 일은 주기 작업
 * 하나이고 minSdk 31 의 플랫폼 `JobScheduler` 가 그것을 그대로 한다(WorkManager 도 안에서 이것을 쓴다).
 *
 * ## 무엇을 돌리는가
 *
 * 앱을 켤 때 도는 것과 **같은 것** — `FileOpManager.Request.Reconcile` 을 **파일 작업 큐에** 넣고 끝을
 * 기다린다. 큐 밖에서 돌리면 휴지통으로 옮기는 중인 파일(`rename` 은 끝났고 기록은 아직)을 고아로 보게 되고,
 * 사용자의 복사와 같은 폴더를 동시에 만진다. 큐가 그 둘을 한 줄로 세운다.
 *
 * ## 언제 돌리지 않는가
 *
 * **모든 파일 접근 권한이 없으면 아무것도 하지 않는다.** 권한 없이 보면 휴지통 폴더가 '없는 것' 으로 보이고,
 * 영구 삭제는 '파일이 없으면 기록만 지운다'(`TrashStore.purge`)이므로 **파일은 남은 채 기록만 사라진다** —
 * 앱에서 되살릴 길이 끊긴다. 앱을 켤 때의 검사도 권한 화면을 지난 뒤에만 돈다(`MainActivity.Root`).
 */
object TrashPurgeJob {

    /** 이 앱의 예약 작업 번호. 다른 예약 작업이 생기면 겹치지 않게 여기서 나눈다. */
    const val JOB_ID = 0x1_7A5E

    /** 하루에 한 번. 30일 보존에서 하루 늦는 것은 문제가 아니다 — 앱을 켤 때의 검사도 그대로 돈다. */
    val PERIOD_MS: Long = TimeUnit.DAYS.toMillis(1)

    /** 주기의 끝 여섯 시간 안이면 언제든. 시스템이 다른 작업과 묶어 깨우도록 넉넉히 준다. */
    val FLEX_MS: Long = TimeUnit.HOURS.toMillis(6)

    /**
     * 예약의 모양. 이미 걸린 것과 이것이 같으면 **다시 걸지 않는다.**
     *
     * 같은 번호로 `schedule` 을 다시 부르면 주기의 시계가 **처음부터 다시 간다.** 앱을 하루에 한 번보다 자주
     * 켜는 사람에게는 작업이 영영 돌지 않는다 — 그래서 '앱을 켤 때마다 건다' 가 아니라 '없거나 다를 때만 건다' 다.
     */
    data class Spec(
        val service: String,
        val intervalMs: Long,
        val flexMs: Long,
        val persisted: Boolean,
        val requiresIdle: Boolean,
        val requiresBatteryNotLow: Boolean,
    )

    /** 새로 걸어야 하는가. 순수 함수 — JVM 시험이 지킨다. */
    fun needsSchedule(existing: Spec?, wanted: Spec): Boolean = existing != wanted

    /**
     * 시스템이 멈춰 달라고 했을 때 다시 걸어 달라고 할 것인가. **주기 작업은 다음 주기에 어차피 다시 돈다** —
     * 다시 걸어 달라고 하면 뒤로 물리기(backoff)가 붙어 오히려 늦게 돈다. 앱을 켤 때의 검사도 있다.
     */
    const val RESCHEDULE_ON_STOP = false

    /** 지금 걸고 싶은 모양. */
    fun wantedSpec(context: Context): Spec = Spec(
        service = ComponentName(context, TrashPurgeJobService::class.java).className,
        intervalMs = PERIOD_MS,
        flexMs = FLEX_MS,
        // 재부팅 뒤에도 남긴다. 그러려면 RECEIVE_BOOT_COMPLETED 권한이 있어야 한다(없으면 `schedule` 이
        // IllegalArgumentException 이다) — 매니페스트의 같은 주석 참고.
        persisted = true,
        // 기기를 쓰지 않을 때만. 사용자의 복사와 같은 큐를 쓰므로 쓰는 동안 끼어들면 그 복사가 뒤로 밀린다.
        requiresIdle = true,
        requiresBatteryNotLow = true,
    )

    /**
     * 없거나 모양이 다르면 건다. **앱을 켤 때 부른다**(`BrowserViewModel.reconcileTrash`) — 따로 바꿀 조립이 없다.
     *
     * 바인더 호출이 둘이라 주 스레드에서 부르지 않는다.
     */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val scheduler = app.getSystemService<JobScheduler>() ?: return
        val wanted = wantedSpec(app)
        // 걸지 못해도 앱은 돈다 — 앱을 켤 때의 검사가 그대로 남아 있다. **예외를 부르는 쪽으로 올리지 않는다**:
        // 부르는 곳이 `viewModelScope` 라 새면 앱이 내려간다. 메시지에는 경로가 없지만 규칙대로 종류만 적는다.
        val result = try {
            val existing = scheduler.getPendingJob(JOB_ID)?.let { info ->
                Spec(
                    service = info.service.className,
                    intervalMs = info.intervalMillis,
                    flexMs = info.flexMillis,
                    persisted = info.isPersisted,
                    requiresIdle = info.isRequireDeviceIdle,
                    requiresBatteryNotLow = info.isRequireBatteryNotLow,
                )
            }
            if (!needsSchedule(existing, wanted)) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(app, TrashPurgeJobService::class.java))
                .setPeriodic(wanted.intervalMs, wanted.flexMs)
                .setPersisted(wanted.persisted)
                .setRequiresDeviceIdle(wanted.requiresIdle)
                .setRequiresBatteryNotLow(wanted.requiresBatteryNotLow)
                .build()
            scheduler.schedule(info)
        } catch (e: RuntimeException) {
            Iro.e(message = "휴지통 비우기 예약 실패: ${e::class.java.simpleName}")
            return
        }
        if (result != JobScheduler.RESULT_SUCCESS) Iro.e(message = "휴지통 비우기 예약이 거절됐다")
    }
}

/**
 * 예약된 휴지통 비우기. 하는 일은 [TrashPurgeJob] 의 주석에 있다.
 *
 * ## 취소를 어떻게 다루는가
 *
 * 시스템이 [onStopJob] 을 부르면(기기를 다시 쓰기 시작했다 · 10분 상한) **멈춤 신호만 켠다.** 큐의
 * `cancelCurrent` 를 부르면 그때 도는 것이 사용자의 복사여도 끊긴다. 신호는 휴지통 비우기만 보고, 그것도 항목
 * 경계에서만 본다 — 한 항목의 '파일 지우기 → 사이드카 지우기 → 기록 지우기' 는 쪼개지지 않는다
 * (`TrashStore.purge` 의 `NonCancellable`). 아직 큐에서 차례를 기다리던 중이면 시작하자마자 멈춘다.
 */
class TrashPurgeJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + IroDispatchers.io)

    /** 지금 도는 작업의 멈춤 신호. 번호가 하나뿐이라 동시에 둘이 돌지 않는다. */
    @Volatile private var stop: AtomicBoolean? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!StorageAccess.isGranted()) {
            // 권한이 없으면 할 수 있는 일이 없다(위 '언제 돌리지 않는가'). 끝났다고 말한다.
            return false
        }
        val flag = AtomicBoolean(false)
        stop = flag
        val done = CompletableDeferred<Boolean>()
        FileOpManager.enqueue(this, FileOpManager.Request.Reconcile(done = done, stop = flag))
        // 서비스가 내려가며 이 스코프가 닫히면 기다리기만 그친다. 큐의 일은 큐가 마저 끝내거나 멈춘다.
        scope.launch {
            val ran = done.await()
            Iro.d { "예약된 휴지통 비우기 끝: ${if (ran) "완료" else "멈춤"}" }
            // 시스템이 이미 멈춘 작업에 끝을 알리지 않는다 — 그 알림은 무시되고 경고만 남는다.
            if (!flag.get()) jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stop?.set(true)
        return TrashPurgeJob.RESCHEDULE_ON_STOP
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
