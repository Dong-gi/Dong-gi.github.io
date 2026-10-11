package io.github.donggi.iroirobrowser.browser

import android.content.Context
import android.text.format.Formatter
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.download.DownloadCenter
import java.io.File
import java.util.Collections
import java.util.Locale

/**
 * 지금 녹화·가로채기 중인 것 — 브라우저 화면이 적고([publish]) 서비스([CaptureService])가 읽어 알림에 보인다. 알림의 단추는 [host](화면)에 넘긴다.
 *
 * 쓰는 중이거나 저장으로 넘긴 폴더도 적어 둔다([claim]) — 되살리기([CaptureRecovery])가 그것을 '앞 프로세스가 남긴 것' 으로 보지 않게(같은
 * 프로세스에서 화면을 다시 만들 때).
 */
object CaptureRegistry {

    /** 받는 탭 하나 — [title] 은 그 탭의 제목(알림), 세션에서 받은 양·시간을 읽는다. */
    class Active(val tabId: Long, val title: String, val recording: RecordingSession?, val capture: CaptureSession?)

    /** 알림의 단추를 받는 쪽(브라우저 화면). */
    fun interface Host {
        fun onNoticeAction(save: Boolean)
    }

    @Volatile
    var host: Host? = null

    @Volatile
    private var active: List<Active> = emptyList()

    private val claimed: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun publish(list: List<Active>) {
        active = list
    }

    fun snapshot(): List<Active> = active

    /** [dir] 은 쓰는 중이거나 저장으로 넘겼다 — 남은 것이 아니다. */
    fun claim(dir: File) {
        claimed += dir.absolutePath
    }

    /** [dir] 을 놓는다 — 버렸거나, 화면이 사라져 다음에 뜰 때 물을 것으로 남겼다. */
    fun release(dir: File) {
        claimed -= dir.absolutePath
    }

    fun isClaimed(dir: File): Boolean = dir.absolutePath in claimed
}

/** 받는 것의 한 줄 — 막대와 알림이 함께 쓴다. */
object CaptureText {

    /** '1:23'·'1:02:03'. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    /** 녹화는 실제로 담긴 시간과 크기, 가로채기는 저장하면 담길 크기(영상이 여럿이면 그 수와 가장 큰 것). 버린 것이 있으면 덧붙인다. */
    fun status(context: Context, recording: RecordingSession?, capture: CaptureSession?): String {
        val size = { n: Long -> Formatter.formatShortFileSize(context, n) }
        val (text, dropped) = when {
            recording != null -> when {
                recording.stopping -> context.getString(R.string.capture_status_record_stopping)
                recording.paused -> context.getString(R.string.capture_status_record_paused, clock(recording.recordedMs()), size(recording.bytes))
                else -> context.getString(R.string.capture_status_record, clock(recording.recordedMs()), size(recording.bytes))
            } to recording.dropped
            capture != null -> {
                val clips = capture.savables()
                val best = clips.firstOrNull()
                when {
                    capture.drm -> context.getString(R.string.capture_status_drm)
                    best == null -> context.getString(R.string.capture_status_waiting)
                    clips.size > 1 -> context.getString(R.string.capture_status_intercept_many, clips.size, size(best.total))
                    else -> context.getString(R.string.capture_status_intercept, size(best.video), size(best.audio))
                } to capture.dropped
            }
            else -> return ""
        }
        return if (dropped > 0) context.getString(R.string.capture_status_dropped, text, size(dropped)) else text
    }
}

/**
 * 앞 프로세스가 남긴 녹화·가로채기 — 시스템이 앱을 정리해 저장하지 못하고 끝난 것(앞 판은 앱이 뜰 때 그 폴더를 통째로 지워 담은
 * 것이 말없이 사라졌다). 폴더의 `meta.json` 으로 되살려 저장할 일로 바꾼다. 읽을 것이 없는 폴더(빈 녹화·`meta.json` 이 없는 앞 판의 것)는 지운다.
 * 쓰는 중이거나 저장으로 넘긴 폴더([CaptureRegistry.claim])는 건너뛴다.
 */
object CaptureRecovery {

    /** 남은 것 하나 — [title] 은 처음 잡힌 때의 페이지 제목(없으면 빈 글), [bytes] 는 저장하면 담길 크기. */
    class Leftover(val dir: File, val title: String, val recording: Boolean, val bytes: Long, val job: () -> DownloadCenter.RemuxJob)

    /** 일꾼 스레드에서 부른다(폴더를 읽는다). */
    fun find(root: File): List<Leftover> {
        val out = ArrayList<Leftover>()
        for (dir in root.listFiles()?.filter { it.isDirectory }.orEmpty().sortedBy { it.name }) {
            if (CaptureRegistry.isClaimed(dir)) continue
            val recording = RecordingSession.restore(dir)
            if (recording != null) {
                if (recording.bytes > 0) out += Leftover(dir, recording.title, true, recording.bytes) { recording.job() } else dir.deleteRecursively()
                continue
            }
            val capture = CaptureSession.restore(dir)
            val clips = capture?.savables().orEmpty()
            if (capture != null && clips.isNotEmpty()) {
                out += Leftover(dir, clips.first().clip.title.ifBlank { capture.site }, false, clips.first().total) { capture.job(null, audioOnly = false) }
            } else {
                dir.deleteRecursively()
            }
        }
        return out
    }
}
