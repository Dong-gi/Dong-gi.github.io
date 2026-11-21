package io.github.donggi.iroiroviewer.io

import android.content.Context
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 묶어 보내기 — 공유에 폴더가 섞였을 때 고른 것을 ZIP 하나로 묶어 다른 앱에 넘긴다(파일 목록의 '공유'). 폴더는 공유 인텐트에 실을 수
 * 없어 예전에는 말없이 빠졌다.
 *
 * ## 어디에 만드는가
 *
 * 앱 캐시의 `share/zip/<무작위>/<이름>.zip`. `share/` 는 FileProvider 가 내보내는 캐시 자리다(`iro_file_paths.xml` 의 `cache-path`,
 * [ShareHelper] 의 허용 루트) — 새 자리를 열지 않는다. 무작위 폴더를 두는 것은 받는 쪽이 보는 이름을 고른 것의 이름(`사진.zip`)으로 두면서
 * 앞의 것과 겹치지 않게 하려는 것이다. 사용자 저장소에는 아무것도 남지 않는다.
 *
 * 묶는 일은 [ZipMaker] 그대로다 — 같은 훑기(링크·inode·깊이 64), 같은 임시본·끝에 놓기, 읽다 실패하면 전부 버리기, 쓰는 동안의 공간 지킴.
 * 파일 작업 큐를 지나므로 진행·취소·포그라운드 서비스도 같다. 암호는 걸지 않는다(받는 쪽이 열어야 한다).
 *
 * ## 무엇을 빼는가 — [excludes]
 *
 * **남에게 나가는 묶음**이라 사용자가 목록에서 보지 못한 것을 싣지 않는다. 숨김 파일 보기가 꺼져 있으면 점으로 시작하는 이름을 모두 뺀다
 * (폴더째 — 목록의 숨김 규칙과 같다). 그리고 MediaStore 의 `.trashed-`·`.pending-` 으로 시작하는 이름은 보기와 상관없이 늘 뺀다 — 다른 앱의
 * 휴지통에 든 것과 아직 다 쓰지 않은 파일이다. 뺀 것은 '건너뜀' 에 든다. 사용자 저장소에 남는 'ZIP으로 압축' 은 거르지 않는다.
 *
 * ## 한계
 *
 * 담을 파일의 합이 [MAX_INPUT_BYTES](2 GiB)를 넘으면 묶지 않는다 — 캐시는 내부 저장소라 사용자 저장소의 공간을 한 벌 더 쓰고, 그만한
 * 것을 받는 앱도 드물다(메일·메신저의 첨부 상한은 그보다 훨씬 작다). 2 GiB 안이면 ZIP64 를 모르는 받는 쪽도 연다. 그리고 그 합에
 * [RESERVE_BYTES] 를 더한 만큼 캐시 볼륨에 남아 있어야 시작한다 — 묶는 도중의 공간 지킴(64 MiB)에 걸려 다 묶고 버리는 일을 앞에서 막는다.
 *
 * ## 언제 지우는가
 *
 * 받는 앱이 언제 다 읽는지 알 길이 없다(공유 창은 결과를 돌려주지 않는다 — 메신저는 올리기를 나중에 하기도 한다). 그래서 **공유 창을 띄운
 * 지 [KEEP_MS](한 시간) 지난 것**을 지운다 — 다음 묶어 보내기를 시작할 때, 앱 프로세스가 설 때, 그리고 묶은 뒤 그 시간이 지나 한 번.
 * **놓았지만 아직 공유 창을 띄우지 않은 것은 이 프로세스가 사는 동안 지우지 않는다**([unshown] — 결과는 파일 목록이 앞에 설 때만 받으므로
 * 묶고 한참 뒤에 공유 창이 뜰 수 있다). 묶다 실패하거나 취소한 것은 그 자리에서 지운다. 시스템도 공간이 모자라면 캐시를 비운다.
 */
object ShareZips {

    /** `share/` 아래 묶어 보내기의 자리. */
    const val DIR = "zip"

    /** 담을 파일 바이트의 합의 상한. 문구(`io_error_too_large`)가 '2 GB' 라고 말한다 — 바꾸면 함께 고친다. */
    const val MAX_INPUT_BYTES = 2L shl 30

    /** 묶은 뒤에도 남길 캐시 볼륨의 공간 — 묶는 동안의 지킴([ZipMaker.SPACE_MARGIN_BYTES])에 머리 몫을 얹은 것. */
    const val RESERVE_BYTES = 2 * ZipMaker.SPACE_MARGIN_BYTES

    /** 공유 창을 띄운 뒤 이만큼 지나면 지운다. */
    const val KEEP_MS = 60L * 60 * 1000

    /** 지금 묶고 있는 자리. 지우기가 건드리지 않는다 — 큰 것을 한 시간 넘게 쓰는 동안 폴더의 시각은 그대로다. */
    @Volatile private var active: File? = null

    /**
     * 놓았지만 아직 공유 창을 띄우지 않은 묶음의 자리(경로). 지우기가 건너뛴다. 공유 창을 띄우면 빠진다([touch]). 프로세스와 함께
     * 사라진다 — 그 뒤에는 결과도 사라졌으므로 띄울 일이 없다.
     */
    private val unshown: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun rootOf(context: Context): File = File(ShareHelper.shareDir(context), DIR)

    /**
     * 묶어 보내기에서 뺄 이름(마지막 조각). MediaStore 의 임시 이름(`.trashed-`·`.pending-`)은 늘, 숨김 파일 보기가 꺼져 있으면 점으로
     * 시작하는 이름을 모두.
     */
    fun excludes(name: String, showHidden: Boolean): Boolean =
        name.startsWith(".trashed-") || name.startsWith(".pending-") || (!showHidden && name.startsWith('.'))

    /**
     * 묶는다. 결과의 경로가 받는 앱에 넘길 ZIP 이다. 놓기 전에 실패하거나 취소되면 만든 자리를 지운다.
     *
     * @param folderName 고른 것들이 있는 폴더의 화면 이름 — 여럿을 골랐으면 ZIP 의 이름이다([ZipMaker.baseName]).
     * @param fallbackName 이름을 지을 수 없을 때의 이름(화면의 문구).
     * @param showHidden 목록의 '숨김 파일 보기'([excludes]).
     */
    suspend fun make(
        context: Context,
        sources: List<String>,
        folderName: String,
        fallbackName: String,
        showHidden: Boolean,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onPlaced: (ZipMaker.Result) -> Unit,
    ): ZipMaker.Result {
        val app = context.applicationContext
        val dir = File(rootOf(app), UUID.randomUUID().toString())
        var kept = false
        try {
            withContext(IroDispatchers.io) { purgeStale(app) }
            active = dir
            val made = withContext(IroDispatchers.io) { dir.mkdirs() }
            if (!made) return ZipMaker.Result(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, ""), null)
            val name = withContext(IroDispatchers.io) { ZipMaker.baseName(sources.map(::File), folderName, fallbackName) }
            val result = ZipMaker.make(
                sources, dir.path, name, media = null, onProgress,
                limits = ZipMaker.Limits(MAX_INPUT_BYTES, RESERVE_BYTES),
                exclude = { excludes(it, showHidden) },
                onPlaced = { r ->
                    kept = true
                    // 놓는 그 순간에 적는다 — 결과가 화면에 닿기 전(앱을 떠나 있었다)에 지우기가 돌아도 남게.
                    unshown += dir.path
                    onPlaced(r)
                },
            )
            if (result.path != null) {
                kept = true
                unshown += dir.path
            }
            return result
        } finally {
            active = null
            withContext(NonCancellable) {
                if (!kept) withContext(IroDispatchers.io) { dir.deleteRecursively() }
            }
            sweepLater(app)
        }
    }

    /**
     * 공유 창을 띄운다 — 지울 때를 지금부터 다시 센다. [unshown] 에서 빼고 폴더의 시각을 지금으로 둔다. [zip] 이 묶어 보내기의 자리
     * 밖이면 아무것도 하지 않는다.
     */
    fun touch(zip: String) {
        val dir = File(zip).parentFile ?: return
        if (dir.parentFile?.name != DIR) return
        // 시각을 먼저 고치고 목록에서 뺀다 — 거꾸로면 그 사이에 돈 정리가 한 시간 넘은 묶음을 띄우는 순간에 지울 수 있다.
        dir.setLastModified(System.currentTimeMillis())
        unshown -= dir.path
    }

    /** 공유 창을 띄운 지 [KEEP_MS] 지난 묶음을 지운다. 지금 묶는 자리와 아직 띄우지 않은 것은 둔다. */
    fun purgeStale(context: Context, now: Long = System.currentTimeMillis()) {
        purgeIn(rootOf(context), now)
    }

    internal fun purgeIn(root: File, now: Long) {
        val dirs = root.listFiles() ?: return
        val busy = active
        for (d in dirs) {
            if (d == busy || d.path in unshown || !isStale(d.lastModified(), now)) continue
            d.deleteRecursively()
        }
    }

    /** 시험이 '놓았지만 띄우지 않은' 자리를 세운다. */
    internal fun markUnshown(dir: File) {
        unshown += dir.path
    }

    /** [purgeStale] 을 다른 스레드에서, 기다리지 않고. 앱이 설 때 부른다. */
    fun purgeStaleLater(context: Context) {
        val app = context.applicationContext
        cleanup.launch { purgeStale(app) }
    }

    /** 방금 놓은 것이 [KEEP_MS] 를 넘긴 뒤에 한 번 더 치운다 — 앱이 그동안 살아 있으면 다음 묶어 보내기를 기다리지 않는다. */
    private fun sweepLater(app: Context) {
        cleanup.launch {
            delay(KEEP_MS + SWEEP_SLACK_MS)
            purgeStale(app)
        }
    }

    /**
     * 지울 때가 되었는가. [KEEP_MS] 를 넘었으면 참. 시각이 지금보다 [KEEP_MS] 넘게 앞서 있어도 참이다 — 시계가 뒤로 크게 간
     * 기기에서 영영 남지 않게. 조금 앞선 것(시계를 맞췄다)은 둔다.
     */
    internal fun isStale(modified: Long, now: Long): Boolean =
        now - modified > KEEP_MS || modified - now > KEEP_MS

    private const val SWEEP_SLACK_MS = 60_000L

    /** 지우기는 화면을 기다리게 하지 않는다. 프로세스와 함께 산다. */
    private val cleanup = CoroutineScope(SupervisorJob() + IroDispatchers.io)
}
