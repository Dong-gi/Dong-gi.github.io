package io.github.donggi.iroiroviewer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.io.FileKey
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
     * media3 는 항목을 세운 뒤에 자막을 더할 수 없어서 항목을 다시 세운다. 그래서
     * **지금 위치를 그대로 들고** 다시 건다 — 그러지 않으면 자막을 고를 때마다 처음으로
     * 돌아간다. 큐의 자리도 유지한다.
     *
     * @param sub null 이면 사이드로드 자막을 뗀다(내장 자막은 트랙 선택으로 끈다).
     */
    fun attachSubtitle(sub: FileEntry?) {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        val at = c.currentMediaItemIndex
        val position = c.currentPosition
        val rebuilt = item.buildUpon()
            .setSubtitleConfigurations(
                if (sub == null) emptyList() else listOf(subtitleConfigOf(sub, selected = true))
            )
            .build()
        c.replaceMediaItem(at, rebuilt)
        c.seekTo(at, position)
        c.prepare()
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
        )
        _queue.value = emptyList()
        _cues.value = emptyList()
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

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(500)
                if (controller?.isPlaying == true) push()
            }
        }
    }

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
        _state.value = _state.value.copy(
            connected = true,
            resumeOfferMs = offer,
            fileKey = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            positionMs = position,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasVideo = c.currentTracks.groups.any { g ->
                g.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && g.isSelected
            },
            videoAspect = aspectOf(c.videoSize),
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
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
