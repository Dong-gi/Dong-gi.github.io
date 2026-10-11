package io.github.donggi.iroirobrowser.adblock

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.donggi.iroirobrowser.WebApp

/**
 * 하루 한 번 깨어 만료된 광고 목록만 다시 묻는다([AdBlockService.updateDue]). 플랫폼 JobScheduler(새 의존 없이) — 네트워크가 있을 때만,
 * 'Wi-Fi 에서만' 이면 요금이 들지 않는 네트워크에서만 돈다.
 */
class AdBlockUpdateJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        WebApp.of(this).adblock.updateDue { jobFinished(params, false) }
        return true
    }

    /** 시스템이 도중에 멈췄다 — 다음 기회에 다시. */
    override fun onStopJob(params: JobParameters): Boolean = true
}
