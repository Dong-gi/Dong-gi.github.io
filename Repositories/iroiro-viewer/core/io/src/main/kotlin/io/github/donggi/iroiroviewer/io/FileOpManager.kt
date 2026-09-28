package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 파일 작업의 대기열. **앱 하나에 하나.**
 *
 * 작업을 화면(ViewModel)이 아니라 여기서 도는 것으로 만든 이유는 두 가지다. 화면을
 * 돌리거나 폴더를 옮겨도 복사가 끊기면 안 되고, 사용자가 앱을 나가도 이어져야 한다
 * (그때 [FileOpService] 가 프로세스를 붙잡는다).
 *
 * **동시에 하나만 돈다.** FUSE 위에서 복사를 병렬로 돌리면 오히려 느려지고, 진행률과
 * 충돌 처리도 사용자가 따라갈 수 없게 된다. 같은 이유로 **휴지통 정합성 검사도 이 큐에
 * 넣는다** — 바깥에서 돌리면 휴지통으로 옮기는 중인 파일을 '기록 없는 고아' 로 보게 된다.
 *
 * ## 결과는 상태가 아니라 사건이다
 *
 * 예전에는 끝난 결과를 [state] 에 `Finished` 로 실어 화면이 읽고 지우게 했는데, 그것이
 * **결과를 잃는 구조**였다. `StateFlow` 는 마지막 값만 들고 있으므로 (1) 다음 작업의
 * `Running` 이 앞 작업의 결과를 덮고, (2) 화면이 안 보이는 동안 끝난 작업의 '3개 실패'
 * 는 아무에게도 전해지지 않는다. 이제 결과는 [results] 채널로 하나씩 나가고 소비자가
 * 반드시 받는다. [state] 에는 지금 도는 것만 남는다.
 */
object FileOpManager {

    sealed interface Request {
        val label: String

        /**
         * 요청의 신원. **결과를 되찾는 유일한 방법이다.**
         *
         * [Finished] 에 이것이 실려 나오므로, 작업을 넣은 쪽은 "내가 넣은 그 작업이 끝났는가"
         * 를 물을 수 있다. 예전에는 종류(`Kind`)와 결과만 나와서, 되돌리기·썸네일 무효화처럼
         * **특정 요청에 매달리는 후속 처리**를 하려면 이름이나 시각으로 추측해야 했다.
         * 이 프로젝트는 "신원은 이름이 아니다" 를 아카이브 엔트리에서 이미 한 번 배웠다
         * (같은 이름 두 엔트리가 하나로 접혀 다른 파일의 바이트가 열렸다).
         */
        val id: String

        /** 화면에 진행과 결과를 보일 것인가. 정합성 검사처럼 사용자가 시키지 않은 일은 조용히 돈다. */
        val silent: Boolean get() = false

        data class Copy(
            override val id: String,
            val sources: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        data class Move(
            override val id: String,
            val sources: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        /** 휴지통으로 보낸다. 되돌릴 수 있는 삭제다. */
        data class Trash(override val id: String, val paths: List<String>) : Request {
            override val label get() = paths.firstOrNull().orEmpty()
        }

        /** 되돌릴 수 없는 삭제. */
        data class DeleteForever(override val id: String, val paths: List<String>) : Request {
            override val label get() = paths.firstOrNull().orEmpty()
        }

        data class Restore(override val id: String, val uuid: String) : Request {
            override val label get() = uuid
        }

        /**
         * 휴지통 항목을 **사용자가 고른 폴더로** 되돌린다(`TrashStore.restoreTo`). 원래 자리를 모르는
         * 항목이나 다른 곳에 두고 싶은 항목을 위한 길이다. 옮기는 일은 복사·이동의 엔진이 한다 —
         * 볼륨을 넘을 수 있고, 이름 충돌을 사용자가 고른 규칙으로 풀어야 하기 때문이다.
         */
        data class RestoreTo(
            override val id: String,
            val uuids: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        data class Purge(override val id: String, val uuids: List<String>) : Request {
            override val label get() = uuids.firstOrNull().orEmpty()
        }

        /**
         * EXIF 방향 태그만 고쳐 사진을 돌린다. **픽셀을 다시 인코딩하지 않는다.**
         *
         * 이것이 원본을 고치는 작업이므로 이 큐를 지난다 — 복사·이동과 직렬화되지 않으면
         * 같은 파일을 두 작업이 동시에 만진다.
         */
        data class RotateExif(
            override val id: String,
            val path: String,
            val degrees: Int,
        ) : Request {
            override val label get() = path
        }

        /**
         * 아카이브를 푼다. **이 큐를 지나는 이유는 복사·이동과 같다** — 사용자 저장소에
         * 쓰는 작업이고, 직렬화하지 않으면 같은 폴더를 두 작업이 동시에 만진다.
         *
         * 실제로 푸는 것은 [ExtractSupport.runner] 다. `core:io` 가 `format:archive` 를
         * 볼 수 없어 생긴 이음매이고, 그 이유는 [ExtractSupport] 에 적어 두었다.
         *
         * @param entryIndices 풀 엔트리 번호. **null 이면 전부다.** 이름이 아니라 번호인
         *   것은 같은 이름의 엔트리가 실재하기 때문이다([ArchiveEntry] 의 같은 이유).
         * @param newFolderName null 이면 [destParent] 에 바로 푼다. 값이 있으면 그 이름의
         *   폴더를 **엔진이** 원자적으로 맡아 만든다 — 화면이 미리 정한 이름을 그대로
         *   쓰면 정하는 사이에 다른 앱이 같은 이름을 만들 수 있다.
         */
        data class Extract(
            override val id: String,
            val archivePath: String,
            val entryIndices: List<Int>?,
            val destParent: String,
            val newFolderName: String?,
            val conflict: FileOpEngine.Conflict,
            /**
             * 아카이브 암호(없으면 null). **요청을 만들 때 붙든 사본**이다 — 큐가 밀려 앱이
             * 화면에서 사라진 뒤에 돌아도 `SessionPasswords` 가 지워져 있어도 풀 수 있게.
             * 푸는 쪽(`ExtractSupport.Runner`)이 다 쓰고 0 으로 덮는다. **돌지 않고 버려지는
             * 길**(대기 중 '전부 취소', 작업 사이의 취소, 시작 전에 끊긴 작업)은 큐가 덮는다
             * — [wipeSecrets].
             */
            val password: CharArray? = null,
        ) : Request {
            override val label get() = destParent

            // 배열이 `toString`(로그·예외)에 실려 나가지 않게 한다. 데이터 클래스의 기본
            // `toString` 은 배열의 주소만 찍지만, 판이 바뀌어 내용을 찍게 되는 날을 막아 둔다.
            override fun toString(): String = "Extract(id=$id, entries=${entryIndices?.size ?: "all"})"
        }

        /**
         * 휴지통의 기록과 실제 파일을 맞춘다. **이 큐에서 도는 것이 핵심이다** — 밖에서
         * 돌리면 `rename` 과 기록 쓰기 사이의 파일을 고아로 보고 손댈 수 있다.
         *
         * 앱을 켤 때와, 앱이 꺼져 있어도 하루에 한 번([TrashPurgeJobService]) 돈다. 예약 작업은 끝을
         * 기다려야 하므로 [done] 을 싣고, 시스템이 '그만' 을 말하면 [stop] 을 켠다.
         *
         * @param done 끝나면 완료된다. 값은 '끝까지 돌았는가'(취소·건너뜀이면 false). **어느 길로
         *   끝나든 반드시 완료된다** — 대기 중 전부 취소, 작업 사이의 취소, 시작 전에 끊긴 작업까지
         *   [settle] 이 맡는다. 완료되지 않으면 예약 작업이 시스템의 시간 상한까지 매달린다.
         * @param stop 켜지면 항목 경계에서 멈춘다. 큐의 [cancelCurrent] 를 쓰지 않는 까닭은
         *   `TrashStore.reconcileAndPurgeExpired` 의 같은 인자 주석에 있다.
         */
        class Reconcile(
            val done: CompletableDeferred<Boolean>? = null,
            val stop: AtomicBoolean? = null,
        ) : Request {
            override val id get() = "reconcile"
            override val label get() = ""
            override val silent get() = true
            override fun toString(): String = "Reconcile"
        }
    }

    sealed interface State {
        data object Idle : State

        data class Running(
            val kind: Kind,
            val progress: FileOpEngine.Progress,
            val queued: Int,
        ) : State
    }

    /**
     * 끝난 작업 하나. [results] 로 정확히 한 번 나간다.
     *
     * [id] 는 [Request.id] 그대로다 — 넣은 쪽이 자기 작업을 알아보는 열쇠이고,
     * [extra] 는 그 작업이 만들어 낸 값(휴지통 uuid 등)이다.
     */
    data class Finished(
        val id: String,
        val kind: Kind,
        val outcome: FileOpEngine.Outcome,
        val extra: Extra? = null,
    ) {
        /**
         * 풀기가 끝난 뒤 사용자가 '열기' 로 갈 폴더. 풀기가 아니거나 갈 곳이 없으면 null.
         *
         * 목적지는 이미 결과에 실려 있다(`Extra.Extracted` 의 `ExtractReport.destDir`). 여기서 정하는
         * 것은 **단추를 보일 것인가** 하나다 — 판단이 화면(`app`)으로 새지 않게 이 타입 곁에 둔다.
         * 규칙은 [ExtractResults.folderToOpen].
         */
        val folderToOpen: String?
            get() = ExtractResults.folderToOpen(kind, outcome, extra)
    }

    /** 결과에 딸려 나오는 값. 되돌리기처럼 **그 작업이 아니면 알 수 없는** 것만 싣는다. */
    sealed interface Extra {
        /** 휴지통으로 보낸 항목들의 uuid. 되돌리기가 이것으로 되돌린다. */
        data class Trashed(val uuids: List<String>) : Extra

        /** 푼 곳과, 풀지 **못한** 것들. 화면이 숨기지 않고 말한다. */
        data class Extracted(val report: ExtractReport) : Extra
    }

    enum class Kind { COPY, MOVE, TRASH, DELETE, RESTORE, PURGE, RECONCILE, ROTATE, EXTRACT }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 끝난 작업. **버퍼가 무제한이고 소비자가 하나씩 받는다** — 화면이 없는 동안 끝난
     * 것도 다음에 화면이 붙을 때 그대로 전해진다.
     */
    private val _results = Channel<Finished>(Channel.UNLIMITED)
    val results: Flow<Finished> = _results.receiveAsFlow()

    /**
     * 워커가 던지면 프로세스가 죽는다. `SupervisorJob` 은 자식의 실패를 형제에게
     * 옮기지 않을 뿐, **처리되지 않은 예외를 삼키지는 않는다.**
     */
    private val scope = CoroutineScope(
        SupervisorJob() + IroDispatchers.io +
            CoroutineExceptionHandler { _, t -> Iro.e(message = "파일 작업 스코프에서 예외: ${t::class.java.simpleName}") }
    )
    private val queue = Channel<Pair<Request, Context>>(Channel.UNLIMITED)

    @Volatile private var worker: Job? = null
    @Volatile private var current: Job? = null

    /**
     * 대기 수. **원자적으로 센다** — `enqueue` 는 주로 메인 스레드에서, 감소는 워커
     * 스레드에서 일어난다. 평범한 `Int` 로 두면 갱신 하나가 사라져 서비스가 안 멈추거나
     * 대기 중인 작업이 있는데 먼저 멈춘다.
     */
    private val queued = AtomicInteger(0)

    /** 작업 사이에 눌린 취소. 다음 작업이 시작하기 전에 소비한다. */
    @Volatile private var cancelRequested = false

    /** 서비스를 띄워 달라고 했는가. */
    @Volatile private var serviceRequested = false

    /**
     * 서비스가 실제로 `startForeground` 까지 갔는가.
     *
     * **이 신호 없이 `stopService` 를 부르면 앱이 죽는다.** `startForegroundService` 를
     * 부른 쪽은 5초 안에 `startForeground` 까지 가야 하는데, 서비스가 `onCreate` 에
     * 닿기 전에 정지 요청이 AMS 에 먼저 도착하면 그 약속이 깨진 것으로 처리된다
     * (`ForegroundServiceDidNotStartInTimeException`). 400ms 지연은 그 죽음을 막으려고
     * 넣은 장치인데, 400~450ms 에 끝나는 작업이 바로 그 창을 연다.
     */
    @Volatile private var serviceForeground = false

    /** 이 시간보다 오래 걸리는 작업에만 서비스와 알림을 붙인다. */
    private const val SERVICE_DELAY_MS = 400L

    /**
     * 작업을 넣는다.
     *
     * **서비스를 여기서 띄우지 않는다.** 휴지통으로 보내기나 이름 바꾸기는 `rename`
     * 한 번이라 수십 밀리초에 끝나는데, 그때 포그라운드 서비스를 띄우고 곧바로 멈추면
     * 안드로이드가 `ForegroundServiceDidNotStartInTimeException` 으로 앱을 죽인다.
     * 그래서 [SERVICE_DELAY_MS] 만큼 지나고도 아직 도는 작업에만 서비스를 붙인다 —
     * 짧은 작업은 알림이 번쩍이지도 않는다.
     */
    fun enqueue(context: Context, request: Request) {
        val app = context.applicationContext
        queued.incrementAndGet()
        queue.trySend(request to app)
        ensureWorker()
    }

    /** 지금 도는 작업만 취소한다. 대기 중인 것은 그대로 둔다. */
    fun cancelCurrent() {
        val job = current
        if (job != null) job.cancel() else cancelRequested = true
    }

    /**
     * 전부 취소한다. 알림의 취소 단추가 이것을 부른다.
     *
     * 예전에는 알림이 [cancelCurrent] 만 부르고 서비스를 멈췄다. 그러면 대기 중이던
     * 작업들이 **포그라운드 보호 없이** 계속 돌아, 사용자가 취소를 눌렀는데도 파일이
     * 계속 옮겨지면서 프로세스는 언제 죽어도 이상하지 않은 상태가 된다.
     */
    fun cancelAll() {
        while (true) {
            val (request, _) = queue.tryReceive().getOrNull() ?: break
            queued.decrementAndGet()
            // 돌지 않을 요청이다. 붙든 암호를 여기서 지운다(돌았다면 푸는 쪽이 지웠다).
            request.settle(ran = false)
        }
        cancelRequested = true
        current?.cancel()
    }

    /**
     * 요청이 끝났거나 버려질 때 부른다. 붙든 비밀(아카이브 암호)을 0 으로 덮고, 끝을 기다리는 쪽
     * (예약된 휴지통 비우기)에 알린다. 두 번 불러도 된다 — 덮은 배열을 다시 덮고, 완료된 신호는
     * 다시 완료되지 않는다.
     *
     * @param ran 끝까지 돌았는가. 돌지 않고 버려진 길이면 false.
     */
    private fun Request.settle(ran: Boolean) {
        if (this is Request.Extract) password?.fill('\u0000')
        if (this is Request.Reconcile) done?.complete(ran)
    }

    /** [FileOpService] 가 `startForeground` 를 마치고 부른다. */
    internal fun onServiceForeground() {
        serviceForeground = true
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            for ((request, app) in queue) {
                queued.decrementAndGet()
                val media = MediaIndex(app)
                val engine = FileOpEngine(media)
                val trash = TrashStore(app, media)
                val kind = kindOf(request)

                if (cancelRequested) {
                    // 작업 사이에 눌린 취소. 시작하지 않고 결과만 남긴다.
                    cancelRequested = false
                    request.settle(ran = false)
                    if (!request.silent) {
                        _results.trySend(Finished(request.id, kind, FileOpEngine.Outcome.Cancelled))
                    }
                    continue
                }

                if (!request.silent) _state.value = State.Running(kind, empty(), queued.get())
                // 끝까지 돌았는가. 블록이 아예 시작하지 못하고 취소되면 false 그대로다.
                var ran = false
                val job = scope.launch {
                    var extra: Extra? = null
                    val outcome = execute(app, request, engine, trash, { extra = it }) { p ->
                        if (!request.silent) _state.value = State.Running(kind, p, queued.get())
                    }
                    ran = outcome !is FileOpEngine.Outcome.Cancelled
                    if (!request.silent) _results.trySend(Finished(request.id, kind, outcome, extra))
                }
                current = job

                // 오래 걸리는 작업에만 서비스를 붙인다. 위 enqueue 주석 참고.
                val watchdog = if (request.silent) null else scope.launch {
                    delay(SERVICE_DELAY_MS)
                    if (job.isActive) startService(app)
                }

                job.join()
                // 시작하기 전에 취소된 작업은 블록이 아예 돌지 않는다 — 푸는 쪽이 암호를 지울
                // 기회가 없었다. 어느 길로 끝났든 여기서 한 번 더 덮는다(기다리는 쪽에도 알린다).
                request.settle(ran)
                watchdog?.cancel()
                current = null
                if (queued.get() <= 0) {
                    // 큐가 비었을 때만 Idle 로 돌린다. 작업 사이마다 돌리면 연속
                    // 붙여넣기에서 진행 바가 한 번 사라졌다 다시 나타난다.
                    _state.value = State.Idle
                    // **취소 요청도 여기서 거둔다.** 다음 작업이 없는데 플래그가 남으면,
                    // 한참 뒤에 사용자가 새로 시킨 복사가 시작하자마자 취소된다.
                    cancelRequested = false
                    stopServiceSafely(app)
                }
            }
        }
    }

    private suspend fun execute(
        context: Context,
        request: Request,
        engine: FileOpEngine,
        trash: TrashStore,
        onExtra: (Extra) -> Unit,
        onProgress: (FileOpEngine.Progress) -> Unit,
    ): FileOpEngine.Outcome = try {
        val volumes = VolumeRegistry.volumes(context)
        when (request) {
            is Request.Copy -> engine.copy(request.sources, request.dest, request.conflict, onProgress)
            is Request.Move -> engine.move(request.sources, request.dest, request.conflict, onProgress)
            is Request.DeleteForever -> engine.deletePermanently(request.paths, onProgress)
            is Request.Trash -> trash.moveToTrash(request.paths, volumes)
                .also { if (it is TrashStore.Result.Done) onExtra(Extra.Trashed(it.uuids)) }
                .toOutcome()
            is Request.Restore -> trash.restore(request.uuid, volumes).toOutcome()
            is Request.RestoreTo ->
                trash.restoreTo(request.uuids, request.dest, request.conflict, volumes, engine, onProgress)
            is Request.Purge -> trash.purge(request.uuids, volumes, engine).toOutcome()
            is Request.RotateExif -> engine.rotateExif(request.path, request.degrees)
            is Request.Extract -> {
                // 꽂히지 않았으면 **실패로 끝낸다.** 조용히 성공을 보고하면 사용자가
                // '풀었다' 고 믿고 원본 아카이브를 지울 수 있다.
                val runner = ExtractSupport.runner
                if (runner == null) {
                    Iro.e(message = "풀기 구현이 꽂히지 않았다")
                    FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, "")
                } else {
                    val result = runner.run(request, onProgress)
                    onExtra(Extra.Extracted(result.report))
                    result.outcome
                }
            }
            is Request.Reconcile -> {
                val stop = request.stop
                trash.reconcileAndPurgeExpired(volumes, engine, isStopped = { stop?.get() == true })
                // 멈춤 신호로 끝난 것은 '끝까지 돌았다' 가 아니다. 기다리는 쪽이 그것을 가른다.
                if (stop?.get() == true) FileOpEngine.Outcome.Cancelled else FileOpEngine.Outcome.Done(0, 0, 0)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // **취소를 예외로 올려보내지 않는 유일한 자리다.** 여기가 작업의 경계이고,
        // 이 코루틴은 이미 죽었다. 취소를 사용자가 읽을 결과로 바꾸는 것이 이 함수의 일이다.
        FileOpEngine.Outcome.Cancelled
    } catch (t: Throwable) {
        Iro.e(message = "파일 작업이 실패했다: ${t::class.java.simpleName}")
        FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, "")
    }

    private fun TrashStore.Result.toOutcome(): FileOpEngine.Outcome = when (this) {
        is TrashStore.Result.Done -> FileOpEngine.Outcome.Done(done, 0, failed)
        is TrashStore.Result.Failed -> FileOpEngine.Outcome.Done(0, 0, failed)
        is TrashStore.Result.Unavailable -> FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, name)
    }

    private fun kindOf(r: Request): Kind = when (r) {
        is Request.Copy -> Kind.COPY
        is Request.Move -> Kind.MOVE
        is Request.Trash -> Kind.TRASH
        is Request.DeleteForever -> Kind.DELETE
        is Request.Restore, is Request.RestoreTo -> Kind.RESTORE
        is Request.Purge -> Kind.PURGE
        is Request.RotateExif -> Kind.ROTATE
        is Request.Extract -> Kind.EXTRACT
        is Request.Reconcile -> Kind.RECONCILE
    }

    private fun empty() = FileOpEngine.Progress(0, 0, 0, 0, "")

    /**
     * 서비스를 띄운다. **실패해도 작업은 계속 간다.**
     *
     * 앱이 백그라운드로 내려간 뒤에는 포그라운드 서비스 시작이 거부될 수 있다
     * (`ForegroundServiceStartNotAllowedException`). 그것을 잡지 않으면 프로세스가
     * 죽고 복사 중이던 임시 파일이 남는다. 알림 없이 도는 편이 죽는 것보다 낫다.
     */
    private fun startService(context: Context) {
        val intent = Intent(context, FileOpService::class.java)
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess
        if (ok) {
            serviceRequested = true
        } else {
            Iro.e(message = "포그라운드 서비스를 띄우지 못했다. 알림 없이 계속한다")
        }
    }

    /**
     * 서비스를 멈춘다. **`startForeground` 가 끝난 것을 확인한 뒤에** 멈춘다.
     * 자세한 이유는 [serviceForeground] 주석에 있다.
     */
    private suspend fun stopServiceSafely(context: Context) {
        if (!serviceRequested) return
        withTimeoutOrNull(SERVICE_START_TIMEOUT_MS) {
            while (!serviceForeground) delay(25)
        }
        serviceRequested = false
        serviceForeground = false
        runCatching { context.stopService(Intent(context, FileOpService::class.java)) }
    }

    private const val SERVICE_START_TIMEOUT_MS = 5_000L

    /** 앱이 완전히 끝날 때. 테스트에서도 쓴다. */
    suspend fun shutdown() {
        // 실제 작업 job 은 워커의 자식이 아니라 형제다. 워커만 끊으면 2GB 복사가 계속 돈다.
        withContext(NonCancellable) {
            current?.cancelAndJoin()
            current = null
            worker?.cancelAndJoin()
            worker = null
        }
    }
}
