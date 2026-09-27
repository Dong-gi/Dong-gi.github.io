package io.github.donggi.iroiroviewer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 화면이 재생에 붙는 유일한 통로.
 *
 * 화면은 플레이어를 **직접 만들지 않는다.** 컨트롤러로 서비스 안의 세션에 붙을 뿐이다.
 * 그래서 화면이 사라졌다 돌아와도, 회전해도, 다른 액티비티로 갔다 와도 재생은 그대로다 —
 * 애초에 화면이 재생을 쥐고 있지 않기 때문이다.
 *
 * 컨트롤러는 **메인 스레드에서만** 만지게 되어 있다. 그래서 이 객체의 공개 함수는 전부
 * 메인에서 부른다고 전제한다.
 */
object PlaybackConnection {

    data class State(
        val connected: Boolean = false,
        val fileKey: String? = null,
        val title: String = "",
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val hasVideo: Boolean = false,
        /** 영상의 실제 화면 비율(가로/세로). 0 이면 아직 모른다. */
        val videoAspect: Float = 0f,
        /** 재생 실패. 화면이 한 번 읽고 [consumeFailure] 로 지운다. */
        val failure: PlaybackFailure? = null,
        /**
         * **이어서 볼 수 있는 위치가 있다는 제안.** 실제로 건너뛰지는 않았다.
         *
         * 예전에는 저장된 위치로 **바로 뛴 다음** '처음부터' 를 띄웠다. 그것은 되돌릴
         * 것을 먼저 해 놓고 무르라고 하는 꼴이고, 안내가 화면에 뜨지 않는 길(재생 화면을
         * 열지 않고 미니 바만 보는 길)에서는 무를 수단조차 없었다. 지금은 **언제나 0초부터
         * 틀고** 이 값으로 "이어서 볼까요?" 만 묻는다 — 묻는 쪽이 안 보이면 그냥 처음부터
         * 보게 될 뿐이라 잃는 것이 없다.
         *
         * 화면이 3초 뒤 [consumeResumeOffer] 로 지우고, 항목이 바뀌어도 지워진다.
         */
        val resumeOfferMs: Long? = null,
        /** 큐 길이. 1 이면 한 곡짜리다. */
        val queueSize: Int = 0,
        /** 큐 안에서 지금 몇 번째인가(0부터). */
        val queueIndex: Int = 0,
        val shuffle: Boolean = false,
        /** [Player.REPEAT_MODE_OFF]·`REPEAT_MODE_ONE`·`REPEAT_MODE_ALL`. */
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        /**
         * 재생 배속. 언제나 [SpeedSteps.STEPS] 의 한 칸이다.
         *
         * 플레이어가 주는 날것을 그대로 싣지 않는 이유는 그 값이 부동소수라 우리가 넣은
         * 1.25f 가 그대로 돌아온다는 보장이 없고, 다른 컨트롤러(잠금화면·블루투스)가
         * 임의의 값을 넣었을 수도 있기 때문이다. 화면의 눈금이 정확히 하나만 켜지려면
         * 들어오는 자리에서 한 번 떨어뜨려야 한다.
         */
        val speed: Float = SpeedSteps.NORMAL,
        /** 찍어 둔 A-B 구간. null 이면 꺼져 있다. */
        val abSpan: AbRepeat.Span? = null,
        /**
         * 고를 만한 트랙이 있는가. **화면이 컨트롤러를 만져 세지 않게** 여기서 실어 보낸다.
         *
         * 큐 항목의 종류(`QueueItem.kind`)를 커넥션이 정해 보내기로 한 것과 같은 판단이다 —
         * 화면이 매 프레임 `getCurrentTracks()` 를 부르면 판정이 두 곳으로 갈린다.
         */
        val canChooseTracks: Boolean = false,
        /**
         * 지금 항목이 소리인가 영상인가. **큐가 정한 값**([QueueItem.kind])을 그대로 싣는다.
         *
         * 화면이 `queue` 와 `State.queueIndex` 를 **각각 다른 흐름에서** 받아 짝지으면,
         * 둘이 한 프레임 어긋난 순간에 엉뚱한 줄의 종류를 읽는다 — 그 값으로 PiP 창을
         * 닫기로 했으므로 어긋난 짝 하나가 **재생 화면을 끝내 버린다.** 한 흐름에서
         * 함께 실어 보내면 그런 짝이 생기지 않는다.
         */
        val currentKind: FileKind? = null,
    ) {
        val hasItem: Boolean get() = fileKey != null
        val hasQueue: Boolean get() = queueSize > 1
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 지금 화면에 떠 있어야 할 자막 조각들.
     *
     * **[State] 에 넣지 않는다.** 자막은 초에 여러 번 바뀌는데 그때마다 `State` 를 통째로
     * 복사하면 제목·위치·큐를 보는 컴포저블이 전부 다시 그려진다. 자막만 보는 흐름을
     * 따로 둔다.
     */
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues.asStateFlow()

    /**
     * 재생목록 한 줄. 화면이 보여 주고 눌러서 건너뛰는 단위다.
     *
     * **종류를 여기서 정해 실어 보낸다.** 화면이 제목의 확장자를 다시 들여다보게 하면
     * 판정이 두 곳으로 갈리고(`EntryNameDecoder` 로 이미 한 번 겪었다), 목록을 그릴 때마다
     * 보이는 줄 수만큼 되풀이된다. 큐는 항목이 실제로 바뀔 때만 다시 만들어지므로
     * 여기서 한 번 계산하는 편이 값이 싸고 자리가 옳다.
     */
    data class QueueItem(
        val index: Int,
        val title: String,
        val mediaId: String,
        /** [FileKind.VIDEO] 인가 [FileKind.AUDIO] 인가. 목록의 아이콘이 이것으로 갈린다. */
        val kind: FileKind,
    )

    /**
     * 지금 큐에 든 것 전부. **보이는 목록이 곧 재생목록이다.**
     *
     * 순서는 **폴더에서 본 그대로**다 — 섞기를 켜도 이 목록은 바뀌지 않고 다음에 무엇이
     * 나올지만 달라진다. 섞을 때마다 목록이 재배열되면 방금 본 줄이 어디로 갔는지 알 수 없다.
     *
     * [State] 에 넣지 않는 이유는 [cues] 와 같다 — 500줄짜리 목록을 위치가 바뀔 때마다
     * 복사할 이유가 없다. 항목이 실제로 바뀔 때(`onTimelineChanged`)만 다시 만든다.
     */
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /**
     * 고를 수 있는 트랙 하나.
     *
     * [id] 는 **우리가 붙인 것**이고 화면은 그것만 되돌려 준다. media3 의 `Tracks.Group` 을
     * 화면까지 내보내지 않는 이유가 중요하다 — 세션은 컨트롤러로 나가는 모든 `TrackGroup`
     * 에 **매번 새로 매긴 유일 id** 를 붙여 보내고(`MediaSessionStub` 의
     * `generateAndCacheUniqueTrackGroupIds`), 돌아온 오버라이드를 그 표로 되돌린다.
     * 그래서 예전 `Group` 으로 만든 오버라이드는 짝을 못 찾아 **조용히 무시된다.**
     * 오버라이드는 언제나 **방금 받은** `getCurrentTracks()` 의 그룹으로 만들어야 한다.
     */
    data class TrackOption(
        val id: String,
        val descriptor: TrackLabels.Descriptor,
        val selected: Boolean,
        /** 이 기기가 풀 수 있는가. 못 푸는 것도 보여 주되 고를 수 없게 한다. */
        val supported: Boolean,
    )

    /** 같은 폴더의 자막 파일 하나. */
    data class SubtitleFile(val path: String, val name: String, val selected: Boolean)

    /**
     * 지금 고를 수 있는 것 전부.
     *
     * [State] 에 넣지 않는 이유는 [cues]·[queue] 와 같다 — 목록은 항목이 바뀔 때만
     * 달라지는데 `State` 는 0.5초마다 복사된다.
     */
    data class TrackChoices(
        val audio: List<TrackOption> = emptyList(),
        val text: List<TrackOption> = emptyList(),
        /** '자막 끄기' 가 켜져 있는가. 내장이든 사이드로드든 한 번에 꺼진다. */
        val textDisabled: Boolean = false,
        val files: List<SubtitleFile> = emptyList(),
    ) {
        /** 보여 줄 것이 있는가. 소리 트랙이 하나뿐이고 자막이 없으면 시트를 열 이유가 없다. */
        val isEmpty: Boolean get() = audio.size < 2 && text.isEmpty() && files.isEmpty()
    }

    private val _tracks = MutableStateFlow(TrackChoices())
    val tracks: StateFlow<TrackChoices> = _tracks.asStateFlow()

    /**
     * [TrackOption.id] → 방금 받은 `Tracks` 안의 자리. **메인 스레드에서만 만진다.**
     *
     * [refreshTracks] 가 통째로 갈아 끼운다. 오래된 그룹으로 오버라이드를 만들지 않기
     * 위한 장치다(위 [TrackOption] 주석).
     */
    private val trackRefs = HashMap<String, Pair<Tracks.Group, Int>>()

    /**
     * 같은 폴더의 자막 파일들. 이름 추측이 틀렸을 때 손으로 고르는 후보다.
     *
     * 큐는 **한 폴더**에서 만들어지므로([FolderQueue]) 이 목록은 큐 전체에 유효하다.
     * 항목이 바뀌어도 후보는 그대로고, '지금 붙어 있는 것'([attachedSubtitle])만 달라진다.
     */
    private var subtitleFiles: List<FileEntry> = emptyList()

    /** 지금 손으로 붙여 둔 자막 파일의 경로. 이름 추측으로 붙은 것은 여기 들지 않는다. */
    private var attachedSubtitle: String? = null

    /** 트랙 선택을 되돌릴 기준이 되는 항목. **전환 이유가 아니라 이 값으로 가른다**([resetTracksIfItemChanged]). */
    private var trackResetKey: String? = null

    private var controller: MediaController? = null

    /**
     * 섞어 듣는 동안의 이력. **우리가 뽑고 우리가 기억한다.**
     *
     * media3 의 `shuffleModeEnabled` 는 쓰지 않는다 — 그것은 '한 번 섞어 둔 차례' 라
     * 같은 자리에서 다시 눌러도 늘 같은 곡이 나온다. 자세한 이유는 [ShuffleHistory].
     */
    private val shuffleHistory = ShuffleHistory()
    private val random = java.util.Random()
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = push()

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(failure = PlaybackFailure.of(error, currentFormatHint()))
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = push()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueue()
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            _cues.value = cueGroup.cues
        }

        /**
         * 항목이 바뀌면 **앞 항목의 이어보기 제안을 지운다.**
         *
         * 제안은 "이 파일을 3:12 부터 볼래요?" 라는 뜻이라, 다음 곡으로 넘어간 뒤에도
         * 남아 있으면 **다른 파일의 위치로 뛰는 단추**가 된다.
         *
         * `PLAYLIST_CHANGED` 만 뺀다. 그것은 우리가 방금 [play]·[playQueue] 에서 큐를
         * 갈아 끼운 것이라, 그때 함께 세운 제안을 우리 손으로 지우게 된다.
         */
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                _state.value = _state.value.copy(resumeOfferMs = null)
            }
            push()
        }

        override fun onTracksChanged(tracks: Tracks) {
            // '영상은 나오는데 무음' 을 재생 실패로 오해하지 않도록, 지원되지 않는
            // 트랙이 있으면 재생이 되더라도 알려 준다.
            val unsupported = tracks.groups.filter { !it.isSupported }
            if (unsupported.isNotEmpty() && _state.value.failure == null) {
                val names = unsupported.mapNotNull { g ->
                    (0 until g.length).firstNotNullOfOrNull { g.getTrackFormat(it).sampleMimeType }
                }.distinct()
                if (names.isNotEmpty()) {
                    _state.value = _state.value.copy(failure = PlaybackFailure.UnsupportedTrack(names))
                }
            }
            refreshTracks()
            push()
        }
    }

    /** 서비스에 붙는다. 이미 붙어 있으면 아무것도 하지 않는다. */
    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also {
                    it.addListener(listener)
                    _state.value = _state.value.copy(connected = true)
                    push()
                    refreshQueue()
                    refreshTracks()
                    startTicker()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * 파일 하나를 튼다. **언제나 0초부터다.**
     *
     * 저장된 위치가 있으면 [State.resumeOfferMs] 에 실어 보내 화면이 물어보게 한다.
     * 저장된 위치로 바로 뛰지 않는 이유는 그 필드의 주석에 있다.
     */
    fun play(context: Context, entry: FileEntry, subtitles: List<FileEntry> = emptyList()) {
        connect(context)
        val key = FileKey.of(entry.name, entry.size, entry.lastModified)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                IroiroDatabase.get(context).playbackPositions().find(key)?.positionMs ?: 0L
            }
            val c = awaitController() ?: return@launch
            c.shuffleModeEnabled = false
            rememberSubtitleCandidates(subtitles)
            c.setMediaItem(itemOf(entry, subtitles), 0L)
            c.prepare()
            c.play()
            refreshQueue()
            _state.value = _state.value.copy(
                failure = null,
                resumeOfferMs = saved.takeIf { it > 0 },
            )
        }
    }

    /**
     * 폴더를 **임시 재생 목록**으로 튼다. VLC 차용의 본체다.
     *
     * ## 어디에서 시작하는가 — 언제나 0초다
     *
     * 시작 항목이든 저절로 넘어간 항목이든 **모두 0초부터** 튼다. 예전에는 시작 항목만
     * 저장된 위치로 뛰었는데, 그 예외가 있던 이유("넘어간 곡이 중간부터 나오면 무를 수
     * 없다")가 이제 시작 항목에도 똑같이 적용된다 — 무르는 수단인 안내가 재생 화면에만
     * 뜨기 때문이다. 예외를 없애니 규칙이 한 줄이 됐고, 저장된 위치는
     * [State.resumeOfferMs] 로 **묻기만** 한다.
     *
     * ## 항목 만들기를 메인 스레드에서 하지 않는다
     *
     * [io.github.donggi.iroiroviewer.safety.PlaybackLimits.MAX_QUEUE_ITEMS] 개까지 만들 수
     * 있고 항목마다 `FileKey.of` 가 SHA-256 을 돈다. 500번이면 메인 스레드에서 볼 수 있는
     * 시간이다. 컨트롤러를 만지는 일만 메인으로 돌아온다.
     */
    fun playQueue(
        context: Context,
        plan: FolderQueue.Plan,
        subtitles: List<FileEntry> = emptyList(),
    ) {
        if (plan.isEmpty) return
        connect(context)
        val start = plan.items.getOrNull(plan.startIndex) ?: return
        val startKey = FileKey.of(start.name, start.size, start.lastModified)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                IroiroDatabase.get(context).playbackPositions().find(startKey)?.positionMs ?: 0L
            }
            val items = withContext(Dispatchers.Default) { plan.items.map { itemOf(it, subtitles) } }
            val c = awaitController() ?: return@launch
            c.shuffleModeEnabled = false
            shuffleHistory.clear()
            rememberSubtitleCandidates(subtitles)
            c.setMediaItems(items, plan.startIndex, 0L)
            c.prepare()
            c.play()
            refreshQueue()
            _state.value = _state.value.copy(
                failure = null,
                resumeOfferMs = saved.takeIf { it > 0 },
            )
        }
    }

    /**
     * 재생목록의 **아무 줄이나** 눌러 그것으로 건너뛴다.
     *
     * `seekTo(index, C.TIME_UNSET)` 은 그 항목을 **처음부터** 튼다 — 이 앱에서 재생이
     * 시작되는 모든 길이 그렇다([playQueue] 참고).
     */
    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, androidx.media3.common.C.TIME_UNSET)
        c.play()
        _cues.value = emptyList()
        push()
    }

    private fun refreshQueue() {
        val c = controller
        if (c == null) {
            _queue.value = emptyList()
            return
        }
        val n = c.mediaItemCount
        _queue.value = if (n == 0) {
            emptyList()
        } else {
            (0 until n).map { i ->
                val item = c.getMediaItemAt(i)
                val title = item.mediaMetadata.title?.toString().orEmpty()
                QueueItem(
                    index = i,
                    title = title,
                    mediaId = item.mediaId,
                    // `mediaId` 는 `FileKey`(해시)라 확장자가 없다. 제목이 곧 파일 이름이다
                    // (`itemOf` 가 `entry.name` 을 그대로 넣는다).
                    kind = MimeResolver.kindOf(title, isDirectory = false),
                )
            }
        }
    }

    /** 큐 안에서 앞뒤로. 큐가 하나뿐이면 아무 일도 하지 않는다. */
    /**
     * 다음 곡.
     *
     * **섞기가 켜져 있으면 그때마다 새로 뽑는다.** media3 의 섞인 차례를 따라가면 같은
     * 자리에서 몇 번을 눌러도 늘 같은 곡이 나온다 — 사용자가 바란 것은 '무작위' 였다.
     * 방금 들은 것은 [ShuffleHistory] 가 빼 준다.
     */
    fun next() {
        val c = controller ?: return
        if (c.shuffleModeEnabled) {
            val ids = _queue.value.map { it.mediaId }
            val at = shuffleHistory.nextIndex(ids, c.currentMediaItemIndex, random)
            if (at >= 0) {
                ids.getOrNull(c.currentMediaItemIndex)?.let { shuffleHistory.remember(it) }
                playAt(at)
                return
            }
        }
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        _cues.value = emptyList()
    }

    fun previous() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
        _cues.value = emptyList()
    }

    /**
     * 섞기를 켜고 끈다.
     *
     * **목록을 우리가 섞지 않는다.** media3 의 `shuffleModeEnabled` 는 재생 순서만 바꾸고
     * 인덱스는 그대로 두므로, 끄면 원래 순서로 정확히 돌아온다. 우리가 섞으면 그 되돌림을
     * 직접 기억해야 하고, 그 사이에 '다음 곡' 이 가리키는 것이 달라진다.
     */
    fun toggleShuffle() {
        val c = controller ?: return
        val on = !c.shuffleModeEnabled
        c.shuffleModeEnabled = on
        // **끄면 이력을 비운다.** 순차로 돌아왔는데 이력이 남아 있으면, 다시 켰을 때
        // 지난번에 듣던 흐름이 이어져 '방금 켠 섞기' 가 새로 시작하지 않는다.
        if (!on) shuffleHistory.clear()
        push()
    }

    /** 재생목록을 닫았다. 섞기 이력을 비운다 — '이 묶음을 그만 본다' 는 뜻이다. */
    fun onPlaylistClosed() {
        shuffleHistory.clear()
    }

    /**
     * 이 큐의 자막 후보를 기억해 둔다.
     *
     * 큐는 한 폴더에서 만들어지므로([FolderQueue]) 후보 목록은 큐 전체에 유효하다.
     * 새로 틀 때마다 **손으로 붙여 둔 것을 잊는** 것이 요점이다 — 다른 폴더를 틀었는데
     * 앞 폴더에서 고른 자막이 붙어 있으면 다른 영화의 대사가 흐른다.
     */
    private fun rememberSubtitleCandidates(subtitles: List<FileEntry>) {
        subtitleFiles = subtitles.filter { !it.isDirectory && !it.isLocked && SubtitleNames.isSubtitle(it.name) }
        attachedSubtitle = null
        refreshTracks()
    }

    /**
     * 지금 고를 수 있는 것을 다시 센다. **메인 스레드에서만 부른다**(컨트롤러를 만진다).
     *
     * `Tracks.Group` 을 [trackRefs] 에 **통째로 갈아 끼우는** 것이 핵심이다. 세션은
     * 컨트롤러로 나가는 그룹마다 매번 새 id 를 매기므로, 예전 그룹으로 만든 오버라이드는
     * 짝을 잃고 조용히 무시된다([TrackOption] 주석).
     */
    private fun refreshTracks() {
        val c = controller
        if (c == null) {
            trackRefs.clear()
            _tracks.value = TrackChoices()
            return
        }
        trackRefs.clear()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        val groups = c.currentTracks.groups
        for ((gi, g) in groups.withIndex()) {
            val into = when (g.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> text
                else -> continue
            }
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                // **그림 자막은 목록에 올리지 않는다.** 우리 자막 층은 글만 그린다
                // (`SubtitleLayer` 의 주석). 고를 수 있게 두면 표시만 옮겨 가고 화면에는
                // 아무 글자도 뜨지 않는다.
                //
                // **`sampleMimeType` 을 그대로 보면 이 조건은 절대 참이 되지 않는다** —
                // media3 가 자막을 뽑는 길에서 그 값을 `application/x-media3-cues` 로
                // 갈아 끼우기 때문이다(PGS·VobSub 도 그 길을 지난다). 원래 형식은
                // `codecs` 에 있다([TrackLabels.originalMimeOf]).
                val mime = TrackLabels.originalMimeOf(f.sampleMimeType, f.codecs)
                if (g.type == C.TRACK_TYPE_TEXT && TrackLabels.isPictureSubtitle(mime)) continue
                val id = "${g.type}:$gi:$i"
                trackRefs[id] = g to i
                into += TrackOption(
                    id = id,
                    descriptor = TrackLabels.describe(
                        TrackLabels.Info(
                            label = f.label,
                            language = f.language,
                            channelCount = f.channelCount,
                            sampleMimeType = f.sampleMimeType,
                            codecs = f.codecs,
                            forced = (f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
                        ),
                        ordinal = into.size + 1,
                    ),
                    selected = g.isTrackSelected(i),
                    supported = g.isTrackSupported(i),
                )
            }
        }
        val disabled = c.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val choices = TrackChoices(
            audio = audio,
            text = text,
            textDisabled = disabled,
            // **짝이 맞는 것을 앞에 둔다.** 목록 자체는 큐 단위(한 폴더)지만 '어느 것이
            // 지금 항목의 짝인가' 는 항목마다 다르다. [SubtitleNames.candidatesFor] 가
            // 그 차례를 아는 유일한 곳이라 여기서 거친다 — 거치지 않으면 그 함수가
            // 부르는 곳 없이 시험만 통과하는 상태로 남는다.
            files = SubtitleNames.candidatesFor(c.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(), subtitleFiles)
                .map { SubtitleFile(path = it.path, name = it.name, selected = it.path == attachedSubtitle) },
        )
        _tracks.value = choices
        _state.value = _state.value.copy(canChooseTracks = !choices.isEmpty)
    }

    /**
     * 항목이 실제로 바뀌었으면 트랙 선택을 **기본으로 되돌린다.**
     *
     * 판정을 전환 이유(`onMediaItemTransition` 의 `reason`)로 하지 않는 것이 요점이다.
     * 한 곡 반복(`REASON_REPEAT`)은 항목이 바뀌지 않았는데도 전환으로 오고, 자막 파일을
     * 손으로 바꿔 큐를 다시 세우는 길(`PLAYLIST_CHANGED`)도 마찬가지다 — 이유로 가르면
     * 한 바퀴마다, 그리고 자막을 고를 때마다 **사용자가 고른 소리 트랙이 지워진다.**
     * 그래서 **`mediaId` 가 달라졌을 때만** 되돌린다.
     */
    private fun resetTracksIfItemChanged(key: String?) {
        if (key == trackResetKey) return
        trackResetKey = key
        // 손으로 붙인 자막은 **그 항목의 것**이다. 다음 항목에는 이름 추측이 붙인 자막이
        // 따로 걸려 있으므로, 표시만 남아 있으면 목록의 체크가 거짓말을 한다.
        attachedSubtitle = null
        val c = controller ?: return
        val p = c.trackSelectionParameters
        // 되돌릴 것이 없으면 건드리지 않는다 — 바인더 호출과 불필요한 이벤트를 아낀다.
        if (p.overrides.isEmpty() && p.disabledTrackTypes.isEmpty()) return
        c.trackSelectionParameters = p.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
    }

    /** 끄기 → 전체 반복 → 한 곡 반복 → 끄기. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        push()
    }

    /**
     * @param subtitles 같은 폴더의 자막 파일들. 이름이 맞는 것 하나가 **기본으로** 붙는다
     *   ([SubtitleNames.defaultFor]). 맞는 것이 없으면 아무것도 붙이지 않는다 —
     *   아무 자막이나 붙이면 다른 영화의 대사가 흐른다.
     */
    private fun itemOf(entry: FileEntry, subtitles: List<FileEntry>): MediaItem {
        val b = MediaItem.Builder()
            .setMediaId(FileKey.of(entry.name, entry.size, entry.lastModified))
            .setUri(File(entry.path).toUri())
            .setMimeType(MimeResolver.mimeOf(entry.name))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.name)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
        SubtitleNames.defaultFor(entry.name, subtitles)?.let { sub ->
            b.setSubtitleConfigurations(listOf(subtitleConfigOf(sub, selected = true)))
        }
        return b.build()
    }

    /** 자막 파일 하나를 media3 가 아는 모양으로. */
    private fun subtitleConfigOf(sub: FileEntry, selected: Boolean) =
        MediaItem.SubtitleConfiguration.Builder(File(sub.path).toUri())
            .setMimeType(SubtitleNames.mimeOf(sub.name))
            .setLabel(sub.name)
            // **기본 선택 표시를 붙인다.** 붙이지 않으면 트랙은 생기지만 아무도 고르지 않아
            // '자막이 있는데 안 나온다' 가 된다.
            .setSelectionFlags(if (selected) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0)
            .build()

    /**
     * 지금 재생 중인 항목에 **자막 파일을 손으로 붙인다.** 이름 추측이 틀렸을 때의 출구다.
     *
     * @param path [TrackChoices.files] 의 경로. null 이면 사이드로드 자막을 뗀다
     *   (내장 자막을 끄는 것은 [setTextEnabled] 다).
     */
    fun selectSubtitleFile(path: String?) {
        val c = controller ?: return
        if (path == null) {
            attachedSubtitle = null
            rebuildCurrentItem(null)
            return
        }
        val at = c.currentMediaItemIndex
        val key = c.currentMediaItem?.mediaId
        val sub = subtitleFiles.firstOrNull { it.path == path } ?: return
        scope.launch {
            val exists = withContext(Dispatchers.IO) { File(sub.path).isFile }
            if (!exists) {
                // **없어진 파일은 목록에서 뺀다. 그것이 곧 안내다.**
                //
                // 새 실패 종류를 만들어 `State.failure` 에 실으면 그 값을 지우는 사람이
                // 재생 화면에 없어 다음 곡 위에도 계속 남고, 뒤따르는 진짜 경고
                // (`UnsupportedTrack`)를 통째로 막는다. 사라진 줄이 사라지는 것으로 족하다.
                Iro.d { "자막 파일이 사라졌다: ${sub.name}" }
                subtitleFiles = subtitleFiles.filterNot { it.path == path }
                if (attachedSubtitle == path) attachedSubtitle = null
                refreshTracks()
                return@launch
            }
            // **되돌아오는 사이에 항목이 바뀌었으면 붙이지 않는다.** 붙이면 다른 영상에
            // 엉뚱한 자막이 걸리고, 그 항목은 큐에 그대로 남는다.
            val now = controller ?: return@launch
            if (now.currentMediaItemIndex != at || now.currentMediaItem?.mediaId != key) {
                Iro.d { "자막을 고르는 사이에 항목이 바뀌었다. 붙이지 않는다" }
                return@launch
            }
            attachedSubtitle = path
            // **고르면 자막이 켜진다.** 꺼 둔 채로 파일을 고르면 붙기는 하는데 아무것도
            // 뜨지 않아 단추가 죽은 것처럼 보인다. 고르는 것은 '이것으로 보겠다' 는 뜻이다.
            now.trackSelectionParameters = now.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            rebuildCurrentItem(sub)
        }
    }

    /**
     * 지금 항목만 자막 구성을 바꿔 **다시 세운다.**
     *
     * ## `replaceMediaItem` 으로는 되지 않는다
     *
     * 예전 코드가 그것이었다. media3 의 `replaceMediaItem` 은 새 항목을 받으면
     * `MediaSource.canUpdateMediaItem` 에 물어보고, 참이면 **소스를 다시 만들지 않고
     * 메타데이터만 갈아 끼운다.** 로컬 파일의 `ProgressiveMediaSource` 는
     * `uri`·`imageDurationMs`·`customCacheKey` 셋만 비교하므로 자막 구성이 통째로 바뀌어도
     * 참을 준다(바이트코드로 확인했다). 게다가 사이드로드 자막은 `MergingMediaSource` 의
     * **뒤쪽** 소스인데 그 클래스의 `canUpdateMediaItem` 은 **맨 앞 소스에만 위임한다.**
     * 결과는 조용한 실패다 — `prepare()` 를 불러도 같은 소스를 다시 준비할 뿐이다.
     *
     * 그래서 **큐를 통째로 다시 세운다.** 위치와 자리를 우리가 들고 다시 넣는다.
     *
     * `playWhenReady` 를 그대로 옮기는 것이 중요하다. `isPlaying` 으로 재면 **버퍼링
     * 중이거나 오디오 포커스를 잠깐 잃은 동안 거짓**이라, 그 순간 자막을 고른 사람은
     * 재생이 멈춘 채 다시 시작되지 않는 것을 본다.
     */
    private fun rebuildCurrentItem(sub: FileEntry?) {
        val c = controller ?: return
        val at = c.currentMediaItemIndex
        val n = c.mediaItemCount
        if (at !in 0 until n) return
        val position = c.currentPosition
        val wasReady = c.playWhenReady
        val items = (0 until n).map { i ->
            val item = c.getMediaItemAt(i)
            if (i != at) {
                item
            } else {
                item.buildUpon()
                    .setSubtitleConfigurations(
                        if (sub == null) emptyList() else listOf(subtitleConfigOf(sub, selected = true))
                    )
                    .build()
            }
        }
        // **컨트롤러가 돌려주는 항목에 `localConfiguration` 이 살아 있다**(uri·자막 구성).
        // 기기에서 찍어 확인했다 — 세션을 건너오면서 지워졌다면 여기서 uri 없는 항목을
        // 다시 걸어 재생이 통째로 죽었을 것이다.
        c.setMediaItems(items, at, position)
        c.prepare()
        c.playWhenReady = wasReady
        refreshQueue()
        push()
    }

    /** 재생을 멈춘다. 항목과 큐는 그대로다 — 끝내는 것은 [stop] 이다. */
    fun pause() {
        controller?.pause()
    }

    /** 멈춘 것을 다시 튼다. */
    fun resume() {
        controller?.play()
    }

    /**
     * 배속을 바꾼다. 들어온 값은 **반드시 눈금으로 떨어뜨린다**([SpeedSteps.nearest]) —
     * `PlaybackParameters` 의 생성자가 0 이하를 예외로 막고, 그 예외는 메인 스레드에서 난다.
     */
    fun setSpeed(speed: Float) {
        val c = controller ?: return
        c.setPlaybackSpeed(SpeedSteps.nearest(speed))
        push()
    }

    /**
     * A-B 구간 단추를 눌렀다. A 찍기 → B 찍기 → 해제가 한 바퀴다.
     *
     * 결과를 그대로 돌려주는 것은 **거절(`TooShort`)을 화면이 말해야** 하기 때문이다.
     * 여기서 A 를 슬쩍 옮겨 구간을 만들어 주면 그 옮김이 1초 미만이라 진행 바에서도
     * 시간 문구에서도 보이지 않는다 — 사용자는 자기가 찍은 자리가 왜 달라졌는지 모른다.
     */
    fun markAb(): AbRepeat.Result {
        val c = controller ?: return AbRepeat.Result.TooShort
        val result = AbRepeat.mark(
            current = _state.value.abSpan,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
        )
        when (result) {
            is AbRepeat.Result.Marked -> _state.value = _state.value.copy(abSpan = result.span)
            AbRepeat.Result.Cleared -> _state.value = _state.value.copy(abSpan = null)
            AbRepeat.Result.TooShort -> Unit
        }
        return result
    }

    /** 구간을 푼다. 항목이 바뀌거나 사용자가 구간 밖으로 건너뛰면 저절로 불린다. */
    fun clearAb() {
        if (_state.value.abSpan != null) _state.value = _state.value.copy(abSpan = null)
    }

    /**
     * 트랙 하나를 고른다. [TrackChoices] 가 방금 준 [TrackOption.id] 만 받는다.
     *
     * 자막을 고르면 **'자막 끄기' 도 함께 풀린다** — 끈 채로 고르면 아무 일도 일어나지
     * 않아 단추가 죽은 것처럼 보인다.
     */
    fun selectTrack(id: String) {
        val c = controller ?: return
        val (group, index) = trackRefs[id] ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, index)
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            // `setOverrideForType` 은 같은 종류의 기존 오버라이드를 먼저 지운다(바이트코드 확인).
            .setOverrideForType(override)
            .setTrackTypeDisabled(group.type, false)
            .build()
        refreshTracks()
        push()
    }

    /**
     * 자막을 켜고 끈다. **내장 자막과 사이드로드 자막이 함께** 걸린다 — 사이드로드 자막도
     * 합쳐진 뒤에는 그냥 텍스트 트랙이라 타입을 끄면 같이 꺼진다.
     *
     * 이 값은 항목이 바뀌면 되돌아간다([resetTracksIfItemChanged]). media3 에서는
     * `disabledTrackTypes` 가 플레이어에 그대로 남지만, 사용자는 '이 영상의 자막을 껐다'
     * 고 믿지 '앞으로 여는 모든 영상' 을 껐다고 믿지 않는다.
     */
    fun setTextEnabled(enabled: Boolean) {
        val c = controller ?: return
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .apply { if (!enabled) clearOverridesOfType(C.TRACK_TYPE_TEXT) }
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        refreshTracks()
        push()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** '이어서 재생' — 제안을 받아들여 저장된 위치로 간다. */
    fun resumeFromOffer() {
        val to = _state.value.resumeOfferMs ?: return
        controller?.seekTo(to)
        _state.value = _state.value.copy(resumeOfferMs = null)
    }

    /** 화면이 제안을 다 보여 주었다(또는 볼 이유가 없어졌다). */
    fun consumeResumeOffer() {
        _state.value = _state.value.copy(resumeOfferMs = null)
    }

    fun consumeFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    /** 재생을 끝내고 미니 플레이어를 치운다. */
    fun stop() {
        controller?.let {
            it.pause()
            it.clearMediaItems()
            // **배속을 보통으로 되돌린다.** 끝내는 것은 '이 재생을 그만둔다' 이고, 그
            // 뒤에 다른 파일을 틀었을 때 앞에서 올려 둔 배속이 남아 있으면 사용자는
            // 무엇이 이상한지 모른 채 소리가 틀어진 것만 듣는다. 화면에 늘 적혀 있기는
            // 하지만, 재생 화면을 열지 않고 미니 바로만 듣는 길에서는 그 표시가 없다.
            it.setPlaybackSpeed(SpeedSteps.NORMAL)
        }
        _state.value = _state.value.copy(
            fileKey = null,
            title = "",
            isPlaying = false,
            hasVideo = false,
            // 틀던 것을 치웠으니 그 항목의 제안도 함께 치운다. [onMediaItemTransition] 은
            // `clearMediaItems` 를 `PLAYLIST_CHANGED` 로 보므로 일부러 건너뛰고,
            // [push] 의 '위치가 지났는가' 도 빈 플레이어의 0 에서는 성립하지 않는다.
            resumeOfferMs = null,
            // 구간도 트랙도 **그 파일에 매인 것**이라 함께 치운다.
            abSpan = null,
            canChooseTracks = false,
            currentKind = null,
        )
        _queue.value = emptyList()
        _cues.value = emptyList()
        _tracks.value = TrackChoices()
        trackRefs.clear()
        subtitleFiles = emptyList()
        attachedSubtitle = null
        trackResetKey = null
        shuffleHistory.clear()
    }

    /** 비디오 화면이 표면을 붙이고 뗄 때 쓴다. 표면을 떼도 **재생은 계속된다**. */
    fun attachSurface(surface: android.view.SurfaceView?) {
        val c = controller ?: return
        if (surface == null) c.clearVideoSurface() else c.setVideoSurfaceView(surface)
    }

    private suspend fun awaitController(): MediaController? {
        repeat(50) {
            controller?.let { return it }
            delay(40)
        }
        return controller
    }

    /**
     * 위치를 밀어 주는 시계. **A-B 구간을 되감는 것도 여기서 한다.**
     *
     * 되감기를 예약할 수단이 없다 — `ExoPlayer.createMessage(...).setPosition(b)` 는
     * `ExoPlayer` 인터페이스에만 있고 화면이 쥔 `MediaController` 에는 없다. 그래서 B 를
     * 지났는지 **직접 본다.**
     *
     * 간격은 [AbRepeat.tickDelayMs] 가 정한다. 기본 [TICK_MS] 로 두면 구간 끝이 반 박자
     * 넘쳐 들리고, 그렇다고 전부 50ms 로 올리면 [push] 가 `State` 를 통째로 복사하며
     * 재구성을 열 배로 늘린다. **B 근처에서만** 조인다.
     *
     * 조인 구간에서도 [push] 는 평소 간격으로만 한다 — 화면이 초당 스무 번 다시 그려질
     * 이유가 없다. 되감은 직후에는 위치가 크게 튀므로 그때는 한 번 밀어 준다.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var sincePushMs = 0L
            while (true) {
                val s = _state.value
                // **멈춰 있으면 조이지 않는다.** 위치가 더 이상 늘지 않으므로 B 근처에서
                // 일시정지하면 조인 간격(50ms)이 영원히 유지된다 — 되감을 일도 없는데
                // 초당 스무 번 깨어나는 꼴이다.
                val wait = if (s.isPlaying) {
                    AbRepeat.tickDelayMs(s.abSpan, s.positionMs, s.speed, TICK_MS)
                } else {
                    TICK_MS
                }
                delay(wait)
                sincePushMs += wait
                val c = controller ?: continue
                val span = _state.value.abSpan
                if (span != null) {
                    val position = c.currentPosition.coerceAtLeast(0)
                    if (AbRepeat.escaped(span, position)) {
                        // 알림·잠금화면·블루투스의 탐색은 우리 함수를 지나지 않고 세션으로
                        // 바로 들어온다. 그래서 '누가 불렀는가' 가 아니라 **위치 자체**로
                        // 본다 — 그러지 않으면 구간 밖으로 나간 사용자를 곧바로 끌어와
                        // 앱이 말을 안 듣는 것처럼 보인다.
                        Iro.d { "A-B 구간 밖으로 건너뛰었다. 구간을 푼다" }
                        clearAb()
                    } else if (AbRepeat.shouldLoop(span, position)) {
                        c.seekTo(span.aMs)
                        push()
                        sincePushMs = 0
                        continue
                    }
                }
                if (c.isPlaying && sincePushMs >= TICK_MS) {
                    push()
                    sincePushMs = 0
                }
            }
        }
    }

    /** 위치를 밀어 주는 기본 간격. */
    private const val TICK_MS = 500L

    private fun push() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val position = c.currentPosition.coerceAtLeast(0)
        // **이미 지나온 제안은 버린다.**
        //
        // 소리 파일을 탭한 길에서는 재생 화면이 열리지 않아(`BrowserScreen` 이 영상일
        // 때만 연다) 제안이 아무에게도 보이지 않은 채 상태에 남는다. 그 상태로 한참 뒤
        // 미니 바를 눌러 들어가면 "1:23 부터 이어서 재생할까요?" 가 뜨는데, 그때 이미
        // 5:00 을 듣고 있다면 그 단추는 **뒤로 가는** 단추다.
        val offer = _state.value.resumeOfferMs?.takeIf { position < it }
        val previous = _state.value
        val key = item?.mediaId
        resetTracksIfItemChanged(key)
        // **항목이 바뀌면 구간도 판다.** A-B 는 '이 파일의 이 구간' 이라, 다음 곡까지
        // 따라가면 엉뚱한 자리를 되풀이한다.
        val span = previous.abSpan?.takeIf { key != null && key == previous.fileKey }

        // **트랙 목록이 잠깐 비는 것을 '영상이 없다' 로 읽지 않는다.**
        //
        // 자막을 손으로 바꾸면 그 항목을 다시 세우는데([rebuildCurrentItem]), 그 사이
        // `currentTracks` 가 한 번 빈다. 그때 `hasVideo` 를 거짓으로 밀면 **표면이
        // 컴포지션에서 빠졌다 붙고 액티비티의 방향 요청까지 흔들린다** — 10단계가
        // '하단이 검게 보인다' 로 한 번 겪은 그 형태다. **같은 항목인 동안에는** 마지막
        // 값을 들고 있는다. 항목이 바뀌면 앞 항목의 값은 쓸 수 없으므로 그대로 다시 센다.
        val groups = c.currentTracks.groups
        val sameItem = key != null && key == previous.fileKey
        val index = c.currentMediaItemIndex
        val kind = _queue.value.getOrNull(index)?.kind
        val hasVideo = when {
            groups.isNotEmpty() -> groups.any { g -> g.type == C.TRACK_TYPE_VIDEO && g.isSelected }
            // 같은 항목인데 트랙만 잠깐 비었다(자막을 손으로 바꾸는 길). 마지막 값을 든다.
            sameItem -> previous.hasVideo
            // **항목이 막 바뀌어 아직 트랙을 모른다. 큐가 말하는 종류를 쓴다.**
            //
            // 여기서 거짓으로 떨어뜨리면 그 짧은 창 동안 화면이 통째로 흔들린다 — 테마가
            // 밝은 쪽으로 뒤집히고, 표면이 컴포지션에서 빠졌다 붙고, 방향 요청이
            // `UNSPECIFIED` 로 내려갔다 돌아오고(6단계가 '하단이 검게 보인다' 로 겪은 그
            // 재도색 경로다), 손대지 않은 재생목록이 한 번 펼쳐진 것으로 판정된다.
            // 큐의 종류는 확장자로 정해져 **항목이 실제로 바뀔 때만** 달라진다.
            else -> kind == FileKind.VIDEO
        }
        val aspect = aspectOf(c.videoSize).takeIf { it > 0f }
            ?: (previous.videoAspect.takeIf { sameItem && hasVideo } ?: 0f)

        _state.value = previous.copy(
            connected = true,
            resumeOfferMs = offer,
            fileKey = key,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            positionMs = position,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasVideo = hasVideo,
            videoAspect = aspect,
            queueSize = c.mediaItemCount,
            queueIndex = index,
            currentKind = kind,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = SpeedSteps.nearest(c.playbackParameters.speed),
            abSpan = span,
        )
    }

    /**
     * 영상의 **보여야 할** 비율.
     *
     * `width/height` 를 그대로 쓰면 안 된다 — 화소가 정사각형이 아닌 영상
     * (아나모픽 DVD 따위)이 있고, 그때 실제 비율은 `pixelWidthHeightRatio` 를 곱한 값이다.
     * media3 가 그 값을 따로 주는 이유가 그것이다.
     */
    private fun aspectOf(size: androidx.media3.common.VideoSize): Float {
        if (size.width <= 0 || size.height <= 0) return 0f
        val par = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
        return size.width * par / size.height
    }

    private fun currentFormatHint(): String? {
        val c = controller ?: return null
        return c.currentTracks.groups
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) } }
            .mapNotNull { it.sampleMimeType }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
    }

    private fun File.toUri(): android.net.Uri = android.net.Uri.fromFile(this)
}
