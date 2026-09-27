package io.github.donggi.iroiroviewer.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Rational
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.playback.AbRepeat
import io.github.donggi.iroiroviewer.playback.PauseIcon
import io.github.donggi.iroiroviewer.playback.PipIcon
import io.github.donggi.iroiroviewer.playback.PipMath
import io.github.donggi.iroiroviewer.playback.PlaybackStrings
import io.github.donggi.iroiroviewer.playback.SpeedSteps
import io.github.donggi.iroiroviewer.playback.SubtitleIcon
import io.github.donggi.iroiroviewer.playback.R as PlaybackR
import io.github.donggi.iroiroviewer.playback.PlaylistIcon
import io.github.donggi.iroiroviewer.playback.playlistLabel
import io.github.donggi.iroiroviewer.playback.RepeatIcon
import io.github.donggi.iroiroviewer.playback.RepeatOneIcon
import io.github.donggi.iroiroviewer.playback.ShuffleIcon
import io.github.donggi.iroiroviewer.playback.SkipNextIcon
import io.github.donggi.iroiroviewer.playback.SkipPreviousIcon
import io.github.donggi.iroiroviewer.playback.nextLabel
import io.github.donggi.iroiroviewer.playback.previousLabel
import io.github.donggi.iroiroviewer.playback.queuePosition
import io.github.donggi.iroiroviewer.playback.repeatLabel
import io.github.donggi.iroiroviewer.playback.shuffleLabel
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import io.github.donggi.iroiroviewer.playback.formatTime
import io.github.donggi.iroiroviewer.playback.pauseLabel
import io.github.donggi.iroiroviewer.playback.playLabel
import io.github.donggi.iroiroviewer.playback.playbackFailureText
import io.github.donggi.iroiroviewer.playback.resumeConfirmLabel
import io.github.donggi.iroiroviewer.playback.resumeOfferText
import io.github.donggi.iroiroviewer.playback.abLabel
import io.github.donggi.iroiroviewer.playback.abResultText
import io.github.donggi.iroiroviewer.playback.abSymbol
import io.github.donggi.iroiroviewer.playback.orientationLockLabel
import io.github.donggi.iroiroviewer.playback.pipLabel
import io.github.donggi.iroiroviewer.playback.pipFailedText
import io.github.donggi.iroiroviewer.playback.speedLabel
import io.github.donggi.iroiroviewer.playback.speedTitle
import io.github.donggi.iroiroviewer.playback.tracksTitle
import io.github.donggi.iroiroviewer.ui.IroiroTheme

/**
 * 큰 화면 재생기.
 *
 * 쥐고 있는 것: 영상 표면과 자막, 제스처, 조작부 **두 줄**(위는 큐 조작, 아래는 이 재생에
 * 대한 조작 — 배속·A-B·트랙 시트·방향 잠금·PiP), 이어보기 제안, 재생목록 층.
 *
 * ## 이 화면이 지키는 불변식 넷
 *
 * 1. **화면이 사라져도 재생은 멈추지 않는다.** 플레이어는 서비스에 있고 이 화면은 표면만
 *    빌려 준다. 표면을 떼는 것은 컴포지션의 `DisposableEffect`(화면에서 빠질 때)이고,
 *    [onStop] 은 `pause` 를 부르지 않는다. **단 하나의 예외가 PiP 창을 닫는 길**이다 —
 *    그것은 '이 창을 치워라' 가 아니라 '그만 본다' 로 읽히는 유일한 동작이다.
 * 2. **액티비티의 설정은 컴포저블이 쥐지 않는다.** 방향 요청은 컴포지션이 아니라
 *    [followDeviceRotationWhileVideo] 가 수명에 묶어 건다 — 정지한 화면은 다시 그려지지
 *    않아, 컴포저블이 쥐면 보이지도 않는 화면이 옛 요청을 붙들고 있게 된다(함정 표).
 * 3. **겹치는 층의 자리를 상수로 맞히지 않는다.** 조작부 판의 높이는 `onSizeChanged` 로
 *    재고, 위에 뜨는 것들(이어보기 알약·실패 문구·안내)은 잰 값을 따라 올라간다.
 * 4. **화면과 PiP 판정이 같은 값을 읽는다.** 재생목록의 여닫음은 컴포지션이 아니라
 *    액티비티가 들고([playlistChoice]) [playlistShown] 한 함수가 답한다 — 갈라 놓으면
 *    '목록이 펼쳐져 있으면 PiP 에 들어가지 않는다' 가 코드로 성립하지 않는다.
 */
class PlayerActivity : ComponentActivity() {

    /**
     * 들어오면서 '재생목록을 펼쳐라' 를 받았는가.
     *
     * **`launchMode="singleTask"` 라 두 번째부터는 [onNewIntent] 로 온다.** `intent` 필드를
     * 그대로 읽으면 처음 들어올 때의 값이 영영 남아, 파일을 탭해 들어와도 목록이 펼쳐진다.
     */
    private val playlistRequest = MutableStateFlow(PlaylistRequest(0, false))

    /**
     * 들어오라는 요청 하나. **일련번호가 붙어 있는 것이 요점이다.**
     *
     * 값만 들고 있으면 `MutableStateFlow` 가 같은 값을 합쳐 버려(`equals` 기반) **같은
     * 뜻의 인텐트가 두 번째로 올 때 아무 일도 일어나지 않는다.** 알림이 싣고 오는 값은
     * 언제나 `true` 라 정확히 그 경우다 — 목록을 펼쳐 들어왔다가 사용자가 닫은 뒤
     * 알림을 다시 누르면 펼쳐지지 않았다. 번호가 바뀌므로 요청이 **도착했다는 사실**
     * 자체가 상태를 되돌리는 계기가 된다.
     */
    private data class PlaylistRequest(val seq: Long, val expand: Boolean)

    private var requestSeq = 0L

    /**
     * 사용자가 재생목록을 직접 여닫았는가. null 이면 아직 손대지 않았다.
     *
     * **컴포지션이 아니라 액티비티가 든다.** 예전에는 `remember` 였는데, 그러면 이 값이
     * 컴포지션 안에만 있어 **PiP 판정이 그것을 볼 수 없었다.** 목록이 펼쳐진 채 PiP 로
     * 들어가면 표면이 컴포지션에 없어 **영상 없는 창**이 뜨는데, 그것을 막을 조건을
     * 세울 자리가 없었던 것이다(설계 검토가 치명으로 잡았다). 지금은 화면과 PiP 판정이
     * **같은 흐름**을 읽는다.
     */
    private val playlistChoice = MutableStateFlow<Boolean?>(null)

    /**
     * **시스템이 확인해 준** 화면 속 화면 상태. 갱신하는 것은 콜백뿐이다.
     *
     * 창을 닫았는지 판정하는 것과 큐가 소리로 넘어갔을 때 창을 닫는 것은 **이 값만** 읽는다.
     * 낙관값을 섞으면 진입이 거절된 순간에도 '창 안에 있다' 로 읽혀, 하지 않은 일에
     * 대고 재생을 멈추거나 화면을 끝낸다.
     */
    private val pipActive = MutableStateFlow(false)

    /**
     * **첫 프레임을 깨끗하게 만들려고 미리 접었다.** 아직 창 안이라는 뜻이 아니다.
     *
     * `onPictureInPictureModeChanged` 는 애니메이션이 끝난 뒤에 오고 시작 시점 콜백은
     * API 35 다. minSdk 31 에서 조작부가 첫 프레임에 찍히는 것을 막을 방법은 우리가 먼저
     * 접는 것뿐인데, 그 낙관을 [pipActive] 에 섞으면 위의 판정들이 함께 거짓말을 한다.
     * 화면이 그리기를 줄일 때만 이 값을 본다.
     */
    private val pipCollapsing = MutableStateFlow(false)

    /**
     * 사용자가 잠근 **구체적인 방향**. null 이면 잠그지 않았다.
     *
     * **컴포저블이 아니라 액티비티가 든다**(함정 표). 그리고 `SCREEN_ORIENTATION_LOCKED`
     * 를 들고 있지 않는 것이 중요하다 — 그 값은 '거는 그 순간의 방향' 에 묶이므로,
     * [onStop] 에서 놓았다가 다시 설 때 그대로 걸면 **그 사이에 기기가 놓인 방향**으로
     * 잠긴다. 가로로 눕혀 잠그고 화면을 껐다 켜면 세로로 잠기는 식이다. 누른 순간의
     * 방향을 구체값(`LANDSCAPE`·`PORTRAIT`)으로 적어 두면 다시 설 때도 같은 방향이다.
     */
    private val orientationLocked = MutableStateFlow<Int?>(null)

    /** 영상 상자가 창 안에서 차지한 자리. PiP 진입 애니메이션의 출발 사각형이다. */
    private val videoBounds = MutableStateFlow<Rect?>(null)

    /** 이 기기가 PiP 를 할 수 있는가. 못 하면 단추를 아예 띄우지 않는다. */
    private val pipSupported: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * PiP 창에 놓을 수 있는 조작의 수. **한 번만 읽는다.**
     *
     * `Activity.getMaxNumPictureInPictureActions()` 는 바인더 호출이 아니라 리소스 읽기지만
     * (`config_pictureInPictureMaxNumberOfActions`), 값이 액티비티 수명 동안 바뀌지 않는데
     * 상태가 흐를 때마다 다시 읽을 이유가 없다 — 재생 중에는 그것이 초당 두 번이다.
     */
    private val maxPipActions: Int by lazy { maxNumPictureInPictureActions }

    /** 마지막으로 만든 PiP 계획. 단추로 들어갈 때 같은 값을 그대로 쓴다. */
    @Volatile
    private var lastPipPlan = PipPlan()

    /** 우리가 스스로 끝내는 중인가. 사용자가 PiP 창을 닫은 것과 갈라야 한다([onStop]). */
    private var closingForAudio = false

    /**
     * **PiP 인 채로 화면이 멈췄다.** 'PiP 창을 닫았다' 를 알아보는 첫 조각이다.
     *
     * 콜백 차례를 기기에서 재 보고 두 번 고쳤다. 둘 다 추측으로는 맞힐 수 없는 것이었다.
     *
     * * 창의 X 는 액티비티를 **끝내지 않는다.** 고정 태스크만 걷어 내고 액티비티는
     *   전체화면 크기로 뒤에 남는다 — `isFinishing=false` 이고 창이 `[0,0][1080,2400]`
     *   인 것을 실측했다. 그래서 처음 쓴 `isFinishing` 검사는 **한 번도 참이 되지 않았고**
     *   '닫으면 멈춘다' 가 조용히 빠져 있었다.
     * * 닫을 때의 차례는 **`onStop` → `onPictureInPictureModeChanged(false)`** 다.
     *   모드 콜백에서 표시를 세우는 방식은 이미 늦다(그 다음에 오는 `onStop` 이 없다).
     *
     * 그래서 [onStop] 이 '그때 PiP 였는가' 를 적어 두고, **그 뒤에 모드가 꺼지면** 창을
     * 닫은 것으로 본다. 전체화면으로 되돌리는 길은 멈추지 않고 곧바로 리줌되므로 이 표시가
     * 서 있지 않고, 화면이 꺼져 멈춘 길에서는 모드 콜백이 아예 오지 않는다 — 그 구분이
     * 중요한 이유는 5단계부터 지켜 온 **'화면을 꺼도 소리는 계속된다'** 때문이다.
     */
    private var stoppedWhileInPip = false

    /**
     * PiP 파라미터 한 벌. **`data class` 인 것이 요점이다** — 값이 실제로 바뀔 때만
     * 시스템을 부른다. 레이아웃마다 부르면 조작부가 나타났다 사라질 때마다 바인더 호출이 난다.
     */
    private data class PipPlan(
        val aspect: PipMath.Aspect? = null,
        val autoEnter: Boolean = false,
        val commands: List<PipMath.Command> = emptyList(),
        val sourceRect: Rect? = null,
    )

    private fun receive(intent: android.content.Intent?) {
        playlistRequest.value = PlaylistRequest(
            seq = ++requestSeq,
            expand = intent?.getBooleanExtra(PlayerEntry.EXTRA_SHOW_PLAYLIST, false) == true,
        )
        // 새 요청이 도착하면 **사용자가 앞서 고른 여닫음을 잊는다** — 그때는 방금 새로
        // 요구한 것이기 때문이다(알림을 다시 누르면 목록이 다시 펼쳐져야 한다).
        playlistChoice.value = null
    }

    /**
     * 재생목록이 **실제로 화면을 덮고 있는가.**
     *
     * 화면과 PiP 판정이 **이 한 함수**를 함께 쓴다. 갈라 놓으면 '목록이 펼쳐져 있으면
     * PiP 에 들어가지 않는다' 는 약속이 코드로는 성립하지 않는다.
     *
     * 큐가 없으면 '열린' 것으로 치지 않는다 — 한 곡짜리에서는 그릴 목록이 없어
     * `PlaylistPanel` 이 아예 안 그려지는데, 그래도 열림으로 치면 테마만 밝아져
     * **영상이 흰 바탕 위에 뜬다.**
     */
    private fun playlistShown(
        state: PlaybackConnection.State,
        request: PlaylistRequest,
        choice: Boolean?,
    ): Boolean {
        val open = choice ?: (request.expand || !state.hasVideo)
        return open && state.hasQueue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackConnection.connect(this)
        receive(intent)
        followDeviceRotationWhileVideo()
        keepPipParamsFresh()
        leavePipWhenQueueTurnsToAudio()
        enableEdgeToEdge()
        setContent {
            val playback by PlaybackConnection.state.collectAsStateWithLifecycle()
            val request = playlistRequest.collectAsStateWithLifecycle().value
            val choice = playlistChoice.collectAsStateWithLifecycle().value
            // **그리기는 둘 중 하나만 참이어도 접는다.** 판정(창을 닫았는가, 소리로
            // 넘어갔는가)은 확인된 쪽만 읽는다 — 두 값을 가른 이유가 그것이다.
            val confirmed by pipActive.collectAsStateWithLifecycle()
            val collapsing by pipCollapsing.collectAsStateWithLifecycle()
            val inPip = confirmed || collapsing
            val locked by orientationLocked.collectAsStateWithLifecycle()

            val playlistVisible = playlistShown(playback, request, choice)
            // 영상이 실제로 **보이고 있을 때만** 어둡게 못 박는다. 영상은 밝은 배경에서
            // 테두리가 번지고 어두운 방에서는 눈이 부시다. 목록과 소리 파일은 그냥 글과
            // 목록이라 다른 화면과 같은 규칙을 따르는 편이 낫다.
            //
            // **테마와 조작부가 같은 식을 써야 한다** — 어긋나면 목록은 밝은데 조작부만
            // 어두운 그림이 나온다(한 번 겪었다). 그래서 여기서 한 번 정해 내려보낸다.
            IroiroTheme(forceDark = playback.hasVideo && !playlistVisible) {
                PlayerScreen(
                    onBack = { finish() },
                    playlistOpen = playlistVisible,
                    onPlaylistOpenChange = { playlistChoice.value = it },
                    inPip = inPip,
                    pipAvailable = PipMath.canEnter(playback.hasVideo, playlistVisible, pipSupported),
                    onEnterPip = ::enterPip,
                    orientationLocked = locked != null,
                    onToggleOrientationLock = ::toggleOrientationLock,
                    onVideoBounds = { videoBounds.value = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    /**
     * PiP 창의 조작을 받는다. **동적 리시버**이고 화면이 서 있는 동안만 등록된다.
     *
     * PiP 중에도 액티비티는 STARTED 로 남으므로(정지가 아니라 일시정지다) 창이 사는 내내
     * 등록돼 있다. `RECEIVER_NOT_EXPORTED` 상수는 API 33 부터라 직접 쓸 수 없고,
     * 그냥 `registerReceiver(r, f)` 로 두면 targetSdk 36 인 앱이 Android 14+ 기기에서
     * `SecurityException` 으로 죽는다 — `ContextCompat` 의 것을 쓴다(이미 있는 의존이다).
     */
    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_PIP_PLAY)
            addAction(ACTION_PIP_PAUSE)
            addAction(ACTION_PIP_NEXT)
            addAction(ACTION_PIP_PREVIOUS)
        }
        ContextCompat.registerReceiver(this, pipReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // **PiP 인 채로 다시 섰다 = 창을 닫은 것이 아니다.** 화면을 껐다 켜면 PiP 창이
        // 사라졌다 돌아오는데, 그 사이 [onStop] 이 세워 둔 표시를 지우지 않으면 **한참
        // 뒤에 전체화면으로 되돌릴 때** 그것을 '창을 닫았다' 로 읽어 재생을 멈춘다.
        // PiP 중에는 리줌이 오지 않으므로([onResume] 이 지우지 못한다) 여기서 지운다.
        if (isInPictureInPictureMode) {
            pipActive.value = true
            stoppedWhileInPip = false
        }
    }

    /**
     * **떠 있지 않은 동안에는 방향 요청을 붙들지 않는다.**
     *
     * 이것이 컴포저블이 아니라 액티비티 콜백인 이유가 여기 있다. 컴포지션은 화면이
     * 정지하면 **다시 그려지지 않는다** — `collectAsStateWithLifecycle` 의
     * `minActiveState` 를 낮춰도 마찬가지다. 그래서 큐가 영상 → 소리로 넘어가도 효과가
     * 다시 돌지 않고, 보이지도 않는 화면이 [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 를
     * 그대로 쥐고 있었다. 알림에서 돌아오는 순간 창이 가로로 잡혔다가 세로로 되돌아오고,
     * 그 사이 새로 넓어진 창의 아래쪽이 아무도 칠하지 않은 채 남는 것이 사용자가 본
     * '하단이 검게 보인다' 다.
     *
     * **잠금 값은 지우지 않는다.** 다시 서면 [followDeviceRotationWhileVideo] 의 수집이
     * 처음부터 다시 돌면서 잠금을 그대로 다시 건다 — 전화를 받고 돌아온 사람이 잠금을
     * 다시 누를 이유가 없다.
     */
    override fun onStop() {
        // **여기서는 적어만 둔다.** 창을 닫았는지 여기서는 알 수 없다 — 화면이 꺼져 멈춘
        // 것일 수도 있고, 그때 재생을 멈추면 이 앱이 5단계부터 지켜 온 '화면을 꺼도 소리는
        // 계속된다' 를 정면으로 깨뜨린다. 판정은 모드 콜백이 이어서 한다.
        stoppedWhileInPip = pipActive.value
        super.onStop()
        // 창이 사라졌으니 접어 둔 것도 푼다. 다시 서면 [onStart] 가 실제 상태로 채운다.
        pipCollapsing.value = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        runCatching { unregisterReceiver(pipReceiver) }
    }

    /**
     * 전체화면으로 돌아왔으면 접어 둔 층을 편다.
     *
     * **진입이 실패한 길도 여기서 낫는다.** PiP 는 사용자가 설정에서 막아 두었을 때
     * 예외 없이 그냥 들어가지 않는데, 우리는 첫 프레임을 깨끗하게 만들려고 **부르기 전에**
     * 화면을 접는다. 되돌리는 코드가 없으면 조작부도 제스처도 없는 화면이 남는다
     * (설계 검토가 잡은 결함이다). 'PiP 가 아니면 펴 둔다' 는 이 한 줄이 그 모든 길을 덮는다.
     */
    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) pipActive.value = false
        // **진입이 거절된 길이 여기서 낫는다.** 미리 접어 둔 것을 되돌리는 코드가 없으면
        // 조작부도 제스처도 없는 화면이 남는다.
        pipCollapsing.value = false
        // 전체화면으로 되돌아왔다 — 창을 닫은 것이 아니다.
        stoppedWhileInPip = false
    }

    /**
     * **저절로 들어가는 길에서는 여기서 미리 접는다.**
     *
     * `onPictureInPictureModeChanged` 는 애니메이션이 **끝난 뒤** 오고, 시작 시점을 알려
     * 주는 `PictureInPictureUiState.isTransitioningToPip()` 는 API 35 다. minSdk 31 에서
     * 첫 프레임에 조작부가 찍히는 것을 막을 방법은 우리가 먼저 접는 것뿐이다.
     *
     * 계획이 자동 진입이 아닐 때는 접지 않는다 — 소리 파일이나 목록을 펼친 상태에서
     * 홈을 눌러도 화면이 공연히 접히면 돌아왔을 때 한 프레임이 비어 보인다.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (lastPipPlan.autoEnter) pipCollapsing.value = true
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) pipCollapsing.value = false
        Iro.d { "PiP=$isInPictureInPictureMode 멈춘뒤=$stoppedWhileInPip" }
        // **멈춘 뒤에 모드가 꺼졌다 = 창을 닫았다.** 전체화면으로 되돌리는 길은 멈추지
        // 않고 곧바로 리줌되므로 이 표시가 서 있지 않다.
        //
        // 멈추기만 하고 [PlaybackConnection.stop] 을 부르지 않는 것이 요점이다. X 는
        // '이 창을 치워라' 이지 '이 파일을 그만 본다' 가 아니고, `stop` 은 큐와 위치까지
        // 버리는 일이라 한 번 누르면 되돌릴 수 없다. 멈추기만 하면 미니 바와 알림이 남아
        // **보던 자리로 돌아가는 길**이 유지된다.
        //
        // 우리가 스스로 끝내는 길([leavePipWhenQueueTurnsToAudio])은 제외한다 — 그쪽은
        // 소리로 계속 듣겠다는 뜻이라 멈추면 정반대가 된다.
        if (!isInPictureInPictureMode && stoppedWhileInPip && !closingForAudio) {
            Iro.d { "PiP 창을 닫았다. 재생을 멈춘다" }
            PlaybackConnection.pause()
        }
        if (!isInPictureInPictureMode) stoppedWhileInPip = false
    }

    /**
     * 단추로 화면 속 화면에 들어간다. 들어갔으면 true.
     *
     * **리줌 상태에서만 부른다.** `Activity.enterPictureInPictureMode` 는 `onStop` 이
     * 지난 뒤에 부르면 `IllegalStateException` 으로 앱을 죽인다(API 31 의 프레임워크
     * 바이트코드에서 확인했다). 단추의 `onClick` 은 리줌 상태지만, 그 사이에 코루틴이나
     * 디바운스를 끼우면 창이 열린다 — **끼우지 않는다.**
     */
    private fun enterPip(): Boolean {
        if (!pipSupported) return false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        // 첫 프레임을 깨끗하게 만들려고 **부르기 전에** 접는다. 실패하면 그 자리에서
        // 되돌리고, 혹시 놓치더라도 [onResume] 이 다시 편다.
        pipCollapsing.value = true
        val entered = enterPictureInPictureMode(paramsOf(lastPipPlan))
        if (!entered) {
            Iro.d { "PiP 진입이 거절됐다(사용자가 껐거나 다른 PiP 가 떠 있다)" }
            pipCollapsing.value = false
        }
        return entered
    }

    /**
     * PiP 파라미터를 **상태가 바뀔 때마다** 새로 건다.
     *
     * 자동 진입(`setAutoEnterEnabled`)을 한 번 켜 두고 잊으면 안 된다 — 진입 판정이
     * `Task.startPausingLocked` 안에 있어서 **태스크가 바뀌며 우리가 멈추는 모든 길**
     * (홈 버튼·제스처 홈·최근앱·다른 앱 실행)이 그 한 곳을 지난다. 소리 파일을 듣다가
     * 홈으로 나가도 검은 창이 뜨는 것이 그래서다.
     *
     * **`repeatOnLifecycle(STARTED)` 다.** PiP 중 액티비티는 PAUSED 이면서 STARTED 로
     * 남으므로, `RESUMED` 로 내리면 PiP 창의 재생/일시정지 아이콘이 얼어붙는다.
     */
    private fun keepPipParamsFresh() {
        if (!pipSupported) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    PlaybackConnection.state,
                    playlistRequest,
                    playlistChoice,
                    videoBounds,
                ) { s, req, choice, bounds ->
                    PipPlan(
                        aspect = PipMath.aspectOf(s.videoAspect),
                        autoEnter = PipMath.autoEnter(
                            hasVideo = s.hasVideo,
                            isPlaying = s.isPlaying,
                            playlistOpen = playlistShown(s, req, choice),
                            supported = true,
                        ),
                        commands = PipMath.commands(s.hasQueue, s.isPlaying, maxPipActions),
                        sourceRect = bounds,
                    )
                }.distinctUntilChanged().collect { plan ->
                    lastPipPlan = plan
                    setPictureInPictureParams(paramsOf(plan))
                }
            }
        }
    }

    /**
     * 계획을 플랫폼 파라미터로. **여기서 예외를 잡지 않는다.**
     *
     * 비율이 극단적이면 `IllegalArgumentException: Aspect ratio is too extreme` 이 나는데,
     * 그것을 막는 것은 try/catch 가 아니라 [PipMath.aspectOf] 의 자르기다(JVM 시험이
     * 지킨다). 잡아 두면 시네마스코프 영상에서 PiP 가 조용히 안 되는 상태로 남는다.
     */
    private fun paramsOf(plan: PipPlan): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
            .setAutoEnterEnabled(plan.autoEnter)
            .setSeamlessResizeEnabled(true)
            .setActions(plan.commands.map(::remoteActionOf))
        // **모르면 넘기지 않는다.** 0 이나 NaN 으로 `Rational` 을 만들면 무한·NaN 이 되고
        // 그것은 한계 밖으로 판정되어 위의 그 예외가 난다. 넘기지 않으면 시스템이 16:9 를 쓴다.
        plan.aspect?.let { b.setAspectRatio(Rational(it.numerator, it.denominator)) }
        plan.sourceRect?.let { b.setSourceRectHint(it) }
        return b.build()
    }

    /**
     * PiP 창의 조작 하나.
     *
     * **액션 문자열과 요청 코드를 넷 다 다르게 준다.** `PendingIntent` 의 동일성 판정은
     * `Intent.filterEquals`(action·data·type·component·categories)뿐이고 **extras 를 보지
     * 않는다.** 같은 액션에 extras 만 달리 주면 `FLAG_UPDATE_CURRENT` 가 앞의 것을 덮어써
     * **세 단추가 모두 같은 일을 한다.**
     *
     * 재생/일시정지를 토글 하나로 두지 않는 이유는 [PipMath.commands] 의 주석에 있다.
     */
    private fun remoteActionOf(command: PipMath.Command): RemoteAction {
        val (icon, title, action, code) = when (command) {
            PipMath.Command.PLAY -> PipActionSpec(
                PlaybackR.drawable.ic_pip_play, PlaybackStrings.play(this), ACTION_PIP_PLAY, 1
            )
            PipMath.Command.PAUSE -> PipActionSpec(
                PlaybackR.drawable.ic_pip_pause, PlaybackStrings.pause(this), ACTION_PIP_PAUSE, 2
            )
            PipMath.Command.NEXT -> PipActionSpec(
                PlaybackR.drawable.ic_pip_next, PlaybackStrings.next(this), ACTION_PIP_NEXT, 3
            )
            PipMath.Command.PREVIOUS -> PipActionSpec(
                PlaybackR.drawable.ic_pip_previous, PlaybackStrings.previous(this), ACTION_PIP_PREVIOUS, 4
            )
        }
        val pending = PendingIntent.getBroadcast(
            this,
            code,
            android.content.Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    private data class PipActionSpec(val icon: Int, val title: String, val action: String, val code: Int)

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY -> PlaybackConnection.resume()
                ACTION_PIP_PAUSE -> PlaybackConnection.pause()
                ACTION_PIP_NEXT -> PlaybackConnection.next()
                ACTION_PIP_PREVIOUS -> PlaybackConnection.previous()
            }
        }
    }

    /**
     * PiP 로 보는 중에 큐가 **소리 항목으로 넘어가면** 창을 닫는다. 재생은 계속된다.
     *
     * 영상이 없으면 PiP 창은 검은 사각형이고, 소리의 백그라운드 자리는 5단계가 알림과
     * 미니 바로 이미 정해 두었다. 재생을 멈추지 않는 것은 **플레이어가 서비스에 살아
     * 화면이 사라져도 끊길 이유가 없기** 때문이다.
     *
     * **판정을 `state.hasVideo` 로 하지 않는다.** 그 값은 항목을 건너뛰는 동안 잠깐
     * 거짓이 되므로, 그것으로 닫으면 PiP 창의 '다음' 을 눌러 영상 → 영상으로 넘기기만
     * 해도 창이 사라진다(설계 검토가 잡았다). 큐가 실어 보내는 종류(`QueueItem.kind`)는
     * 확장자로 정해져 **항목이 실제로 바뀔 때만** 달라진다.
     */
    private fun leavePipWhenQueueTurnsToAudio() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // **종류와 자리를 한 흐름에서 받는다.** 예전에는 `queue` 와
                // `state.queueIndex` 를 각각 다른 흐름에서 받아 짝지었는데, 둘이 한 프레임
                // 어긋난 순간에 엉뚱한 줄의 종류를 읽는다 — 그 값으로 창을 닫기로 했으므로
                // **어긋난 짝 하나가 재생 화면을 끝내 버린다.** 지금은 커넥션이
                // `State.currentKind` 로 함께 실어 보낸다.
                combine(PlaybackConnection.state, pipActive) { s, pip ->
                    pip && s.currentKind == FileKind.AUDIO
                }.distinctUntilChanged().collect { leave ->
                    if (leave) {
                        Iro.d { "PiP 중에 큐가 소리로 넘어갔다. 창을 닫는다(재생은 계속)" }
                        closingForAudio = true
                        finish()
                    }
                }
            }
        }
    }

    /**
     * **기기를 가로로 돌리면 가로로 튼다 — 화면이 세로로 고정되어 있어도.**
     *
     * 안드로이드에서 자동회전을 끄면 앱은 보통 따라 돌 수 없다. 그런데
     * [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 는 문서가 명시적으로 "사용자의 센서 기반
     * 회전 끄기 설정을 무시한다"(*Ignores user's setting to turn off sensor-based
     * rotation*)고 적은 몇 안 되는 값이다. 그래서 `OrientationEventListener` 로 각도를
     * 직접 재고 방향을 계산하는 흔한 방법이 필요 없다 — 그쪽은 우리가 센서를 다시
     * 구현하는 것이고, 기기마다 어긋난다.
     *
     * **영상일 때만** 건다. 소리만 나는 파일에서까지 기기를 돌릴 때마다 화면이 따라
     * 돌면, 그것은 사용자가 세로 고정을 켜 둔 뜻을 정면으로 거스르는 것이다.
     *
     * **잠금이 그 위에 선다.** 누워서 볼 때 화면이 따라 도는 것을 멈추는 단추이고,
     * [ActivityInfo.SCREEN_ORIENTATION_LOCKED] 는 **지금 방향 그대로** 묶는다 — 어느
     * 방향인지 우리가 계산하지 않아도 된다. 잠금은 영상이 아니게 되어도 **풀리지 않는다**:
     * `hasVideo` 는 항목을 건너뛰는 동안 잠깐 거짓이 되므로 그것으로 풀면 영상 → 영상으로
     * 넘길 때마다 사용자가 건 잠금이 조용히 사라진다.
     *
     * `repeatOnLifecycle(STARTED)` 인 것이 요점이다 — 화면이 설 때마다 수집이 다시
     * 시작하고 `StateFlow` 가 **지금 값**을 즉시 준다. 그래서 정지 중에 큐가 바뀌어도
     * 돌아오는 순간 옳은 방향이 걸린다. 놓는 것은 [onStop] 이 한다.
     *
     * `FULL_SENSOR` 가 아니라 `SENSOR` 인 이유는 뒤집힌 세로(180°)까지 허용할 이유가
     * 없기 때문이다. 영상에 필요한 것은 양쪽 가로이고, 그 둘은 `SENSOR` 가 모든 기기에서 준다.
     *
     * 이 액티비티는 매니페스트에 `configChanges` 로 `orientation|screenSize` 를 적어
     * 두었으므로 회전해도 액티비티가 다시 만들어지지 않는다 — 표면이 끊기지 않고
     * 재생이 이어진다.
     *
     * **PiP 중에는 이 요청이 화면을 돌리지 못한다**(그래도 무해하다). PiP 태스크는 부모를
     * 채우지 않아 `WindowContainer.getOrientation` 이 첫 줄에서 `SCREEN_ORIENTATION_UNSET`
     * 을 돌려주기 때문이다. 전체화면으로 되돌아오면 그때 다시 산다.
     *
     * **태블릿에서 안 들을 수 있다. 다만 이유가 `targetSdk 36` 이 아니다.** Android 16 이
     * 무시하는 것은 *고정* 방향(`portrait`·`landscape` 등)이고
     * [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 는 `ActivityInfo.isFixedOrientation()` 에
     * 들어 있지 않아 그 정책의 대상이 아니다. 진짜 조건은 **그 디스플레이의
     * `ignoreOrientationRequest`** 다 — 폰 AVD 는 `false` 라 요청이 먹는 것을 실측했고
     * (`adb shell dumpsys window | grep ignoreOrientationRequest`), 태블릿 AVD 는 아직 재지 않았다.
     * `SCREEN_ORIENTATION_LOCKED` 는 **고정 방향에 든다** — 태블릿에서 잠금이 안 들으면
     * 그 값이 원인이고, 그때도 opt-out 속성으로 억지로 되돌리지 않는다.
     */
    /**
     * 방향 잠금을 켜고 끈다. **켤 때의 방향을 구체값으로 적어 둔다.**
     *
     * `SCREEN_ORIENTATION_LOCKED` 를 그대로 쓰지 않는 이유는 [orientationLocked] 의 주석에
     * 있다 — 그 값은 '거는 순간의 방향' 에 묶이는데, 이 앱은 [onStop] 에서 방향 요청을
     * 놓았다가 다시 설 때 새로 걸므로 **그 사이에 기기가 놓인 방향**으로 잠기게 된다.
     */
    private fun toggleOrientationLock() {
        orientationLocked.value = if (orientationLocked.value != null) {
            null
        } else if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun followDeviceRotationWhileVideo() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(PlaybackConnection.state, orientationLocked) { s, locked ->
                    locked to s.hasVideo
                }.distinctUntilChanged().collect { (locked, hasVideo) ->
                    requestedOrientation = when {
                        locked != null -> locked
                        hasVideo -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }

    private companion object {
        const val ACTION_PIP_PLAY = "io.github.donggi.iroiroviewer.player.PIP_PLAY"
        const val ACTION_PIP_PAUSE = "io.github.donggi.iroiroviewer.player.PIP_PAUSE"
        const val ACTION_PIP_NEXT = "io.github.donggi.iroiroviewer.player.PIP_NEXT"
        const val ACTION_PIP_PREVIOUS = "io.github.donggi.iroiroviewer.player.PIP_PREVIOUS"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
    onBack: () -> Unit,
    playlistOpen: Boolean,
    onPlaylistOpenChange: (Boolean) -> Unit,
    inPip: Boolean,
    pipAvailable: Boolean,
    onEnterPip: () -> Boolean,
    orientationLocked: Boolean,
    onToggleOrientationLock: () -> Unit,
    onVideoBounds: (Rect) -> Unit,
) {
    val state by PlaybackConnection.state.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    // **조작부는 영상일 때만 숨긴다.** 소리만 나는 화면에서 막대까지 감추면 아무것도 없는
    // 빈 화면이 되고, 그때 다시 꺼내는 방법을 알 길이 없다.
    var controlsVisible by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    // 배속 눈금을 펼쳤는가. **조작부 안에서** 펼친다 — 판이 그만큼 높아지고, 위에 뜨는
    // 안내들은 잰 높이를 따라 저절로 올라간다([controlsHeight]).
    var speedOpen by remember { mutableStateOf(false) }
    var trackSheetOpen by remember { mutableStateOf(false) }
    // A-B 단추를 누른 결과를 잠깐 띄운다. 거절(`TooShort`)을 말할 자리가 여기뿐이다.
    var abNote by remember { mutableStateOf<AbRepeat.Result?>(null) }
    // 화면 속 화면에 들어가지 못했다(사용자가 껐거나 다른 PiP 가 떠 있다).
    var pipFailed by remember { mutableStateOf(false) }
    // **안내에 번호를 붙인다.** 같은 결과를 두 번 내면(`구간이 너무 짧습니다` 를 두 번)
    // 값이 같아 `LaunchedEffect` 의 키가 바뀌지 않고, 그러면 **첫 번째 타이머가 그대로
    // 흘러 두 번째 안내가 곧바로 사라진다.** 10단계가 `MutableStateFlow` 에서 같은 형태를
    // 한 번 겪었다(함정 표).
    var noteSeq by remember { mutableStateOf(0L) }
    // 조작부 판이 실제로 차지하는 높이. 재생목록이 그만큼 끝을 비우고, 위에 뜨는
    // 안내들이 그만큼 비켜선다.
    var controlsHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // 판 바로 위. **아직 재기 전이면 그리지 않는다** — 추측한 자리에 그리느니 한
    // 프레임 늦게 뜨는 편이 낫다. 상수로 떨어뜨렸더니 그 상수가 실제 판(220dp)보다
    // 작아 같은 결함을 첫 프레임짜리로 남겼다.
    val controlsMeasured = controlsHeight > 0.dp
    val aboveControls = controlsHeight + OFFER_GAP
    val showControls = controlsVisible || !state.hasVideo || playlistOpen

    // **영상이 실제로 보이고 있는가.** 목록이 열려 있으면 영상은 가려져 있으므로 그때는
    // 영상 화면이 아니다 — 배경·글자색·조작부의 판까지 모두 이 하나로 갈린다.
    // 액티비티가 테마를 고르는 조건과 **같은 식**이어야 한다. 어긋나면 목록은 밝은데
    // 조작부만 어두운 그림이 나온다.
    val showingVideo = state.hasVideo && !playlistOpen
    // **낭독기용 문구는 미리 만들어 둔다.** `Modifier.semantics` 의 람다는 컴포저블이
    // 아니라 그 안에서 `stringResource` 를 부를 수 없다.
    val speedDescription = speedLabel(state.speed)
    val abDescription = abLabel(state.abSpan)
    val abText = abSymbol()
    val background = if (showingVideo) Color.Black else MaterialTheme.colorScheme.background
    val onBackground = if (showingVideo) Color.White else MaterialTheme.colorScheme.onSurface

    // **조작부를 Column 의 한 칸이 아니라 위에 겹친다.**
    //
    // 칸으로 두면 조작부가 나타날 때마다 위 칸이 좁아져 **영상이 줄어들고 자리가 움직인다.**
    // 눈에는 "화면이 흔들린다" 로 보이고 무엇이 바뀐 것인지 읽어 내기 어렵다. 겹쳐 두면
    // 바뀌는 것은 조작부의 유무 하나뿐이다.
    Box(Modifier.fillMaxSize().background(background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // **목록이 열려 있으면 제스처를 끈다.** 목록이 스크롤을 먹어야 한다.
                // PiP 창에서는 애초에 우리 UI 를 만질 수 없다(시스템이 막는다).
                .playerGestures(
                    enabled = !playlistOpen && !inPip,
                    durationMs = state.durationMs,
                    positionMs = { PlaybackConnection.state.value.positionMs },
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onFeedback = { feedback = it },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // **목록이 덮고 있으면 영상 표면을 아예 두지 않는다.**
            //
            // `SurfaceView` 는 `setZOrderOnTop` 을 부르지 않으면 **창에 구멍을 뚫고**
            // 그 자리를 컴포지터가 따로 합성한다. 목록이 그 위를 덮는 동안에도 구멍은
            // 살아 있어서, 창을 다시 칠할 일이 생기면(방향이 바뀌어 창 크기가 달라지는
            // 순간 따위) **아무도 칠하지 않은 자리가 검게 남는다** — 사용자가 알림에서
            // 돌아왔을 때 본 것이 그 그림이다(아래 절반이 검고 조작부가 통째로 없다).
            //
            // 그 그림은 **이 컴포지션이 만들 수 없다**: 목록이 그려졌다면
            // `playlistOpen` 이 참이고, 그러면 `showControls` 도 반드시 참이며
            // `showingVideo` 는 반드시 거짓이다(셋이 같은 값을 읽는다). 그러므로
            // 검정은 우리가 칠한 것이 아니라 **우리가 지운 자리**다.
            //
            // 어차피 보이지 않는 표면이므로 빼도 잃는 것이 없다. 목록을 닫으면
            // `AndroidView` 가 표면을 새로 만들고 `attachSurface` 가 다시 붙인다.
            if (state.hasVideo && !playlistOpen) {
                // **비율을 지킨다.** `fillMaxSize` 로 두면 표면이 화면을 꽉 채우느라
                // 영상을 늘여, 세로 화면에서 가로 영상이 위아래로 잡아당겨진다.
                // 비율을 아직 모르면(첫 프레임 전) 채워 두었다가 알게 되면 맞춘다.
                //
                // **자막을 이 상자 안에 넣는다.** 밖에 두면 세로 영상 아래의 빈 여백에
                // 자막이 뜨고, 영상과 글이 멀어져 함께 보기 어렵다.
                Box(
                    modifier = (
                        if (state.videoAspect > 0f) {
                            Modifier.fillMaxWidth().aspectRatio(state.videoAspect)
                        } else {
                            Modifier.fillMaxSize()
                        }
                        )
                        // **PiP 진입 애니메이션의 출발 사각형.** 주지 않으면 시스템이
                        // 콘텐츠 오버레이를 덧씌운다. 좌표는 **창 기준**이어야 한다 —
                        // `boundsInRoot`·`positionInParent` 로 잡으면 상태표시줄 인셋만큼
                        // 어긋나 애니메이션이 튄다. 값이 실제로 바뀔 때만 시스템을 부르는
                        // 것은 받는 쪽(`PipPlan`)이 `data class` 라 저절로 된다.
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInWindow()
                            onVideoBounds(
                                Rect(
                                    r.left.toInt(),
                                    r.top.toInt(),
                                    r.right.toInt(),
                                    r.bottom.toInt(),
                                )
                            )
                        },
                ) {
                    VideoSurface(Modifier.fillMaxSize())
                    // PiP 창에는 **영상 말고 아무것도 두지 않는다**(공식 문서). 작은 창에
                    // 자막을 얹으면 글자가 뭉개져 읽히지도 않는다.
                    if (!inPip) SubtitleLayer()
                }
            } else if (!state.hasVideo && !inPip) {
                // **PiP 창에는 영상 말고 아무것도 두지 않는다**(공식 문서). 소리 가지에도
                // 같은 가드가 필요하다 — 영상 가지에만 달아 두었더니 큐가 소리로 넘어간
                // 그 짧은 창 동안 제목과 자막이 작은 창에 찍혔다.
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(32.dp),
                )
                // 소리 파일에도 자막이 붙을 수 있다(내장 자막이 든 음성).
                if (!playlistOpen || !state.hasQueue) SubtitleLayer()
            }
            if (!inPip) feedback?.let { GestureNote(it, Modifier.align(Alignment.Center)) }
            if (playlistOpen && state.hasQueue && !inPip) {
                PlaylistPanel(
                    currentIndex = state.queueIndex,
                    // **조작부가 가리는 만큼 목록 끝에 자리를 비워 둔다.** 목록이 열려
                    // 있으면 조작부는 불투명한 판이고 끌 수도 없다(`showControls`).
                    // 그 높이를 빼 주지 않으면 **마지막 몇 줄이 판 뒤에서 영영 나오지
                    // 못한다** — 더 내릴 것이 없으므로 위로 올라오지도 않는다.
                    // 값을 상수로 적지 않고 재는 것은 글꼴 배율과 시스템 바 인셋에
                    // 따라 판 높이가 달라지기 때문이다.
                    bottomInset = controlsHeight,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        // **여기서 죽으면 여기서 말한다.** 실패 문구는 5단계부터 있었지만 읽는 곳이
        // 파일 목록뿐이라, 재생 화면에 들어와 있는 동안 디코더가 죽으면 빈 화면만 남았다.
        // 소비(`consumeFailure`)는 하지 않는다 — 나가면 목록이 한 번 더 말해 주고, 다음
        // 곡을 걸면 `PlaybackConnection` 이 스스로 지운다.
        if (inPip) return@Box
        state.failure?.let { failure ->
            Text(
                text = playbackFailureText(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 조작부 판 위로, 그리고 이어보기 제안보다 한 칸 더 위로.
                    // 둘이 동시에 뜨는 일은 드물지만 겹쳐 읽히면 둘 다 못 읽는다.
                    .padding(bottom = aboveControls + OFFER_HEIGHT)
                    .background(Color(0xC0000000))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // **이어보기 제안 — 여기서만, 3초만.**
        //
        // 자리: 재생목록 층보다 **뒤에** 그려야 목록을 펼친 채로도 보인다(Box 는 나중에
        // 그린 것이 위다). 조작부의 `return@Box` 보다 **앞에** 있어야 조작부를 숨긴
        // 상태에서도 보인다 — 영상을 탭해 조작부를 감추는 것과 이 제안은 무관한 일이다.
        //
        // 파일 관리자 목록에는 그리지 않는다. 거기서는 재생이 곁가지이고, 예전에 그 자리에
        // 띄웠던 스낵바가 사라지지도 않았다(`BrowserScreen` 의 주석).
        // **잠깐 뜨는 안내.** A-B 를 누른 결과와 PiP 진입 실패가 여기로 온다.
        //
        // 이어보기 알약과 **같은 자리**를 쓰므로 둘이 겹치지 않게 갈라 둔다. 방금 누른
        // 것에 대한 답이 먼저다 — 이어보기 제안은 3초 뒤 저절로 사라지고, 사라진 뒤에도
        // 같은 자리에서 다시 볼 일이 없다.
        val noteText = when {
            pipFailed -> pipFailedText()
            abNote != null -> abResultText(abNote!!)
            else -> null
        }
        if (controlsMeasured && noteText != null) {
            LaunchedEffect(noteSeq) {
                delay(NOTE_HOLD_MS)
                abNote = null
                pipFailed = false
            }
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = aboveControls)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        if (controlsMeasured && noteText == null) state.resumeOfferMs?.let { from ->
            // 사라지는 것은 **화면의 일이다.** 커넥션에 타이머를 두면 화면이 없는 동안에도
            // 3초가 흘러, 미니 바로 듣다가 재생 화면을 열면 이미 지워져 있다.
            LaunchedEffect(from, state.fileKey) {
                delay(RESUME_OFFER_HOLD_MS)
                PlaybackConnection.consumeResumeOffer()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // **조작부 판의 실제 높이를 재서 그 위로 비켜선다.**
                    //
                    // 상수(200dp)로 두었더니 판이 그보다 높아 **알약의 아래쪽이 잘렸다**
                    // — 위 모서리는 둥근데 아래는 직선으로 끊기고 글자가 바닥에 붙는다
                    // (사용자가 지적했다). 판 높이는 글꼴 배율과 시스템 바 인셋에 따라
                    // 달라지므로 상수로는 맞힐 수 없다.
                    //
                    // 조작부를 숨겨도 자리가 그대로인 것은 [controlsHeight] 가 **마지막으로
                    // 잰 값을 들고 있기** 때문이다 — 판이 사라져도 값은 남는다. 조작부의
                    // 유무로 다른 것이 움직이지 않아야 한다는 규칙이 그래서 지켜진다.
                    .padding(bottom = aboveControls)
                    // **색을 테마에서 가져온다.** 검정 알약은 영상 위에서 아예 보이지
                    // 않았고(검정 위 검정), 목록을 펼친 밝은 화면에서는 혼자 튄다.
                    // `inverseSurface` 는 스낵바가 쓰는 짝이라 어느 테마에서도 배경과
                    // 반대편에 선다.
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(24.dp))
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = resumeOfferText(from),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    // 한 줄로 못 박는다. 두 줄이 되면 알약이 56dp 를 넘어
                    // [OFFER_HEIGHT] 가 어긋나고 실패 문구와 부딪친다.
                    maxLines = 1,
                )
                TextButton(onClick = { PlaybackConnection.resumeFromOffer() }) {
                    Text(
                        text = resumeConfirmLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
            }
        }

        // **시트는 조작부보다 앞에 둔다.** 뒤에 두면 조작부를 감춘 순간 시트가 컴포지션에서
        // 통째로 빠져, 제스처까지 꺼 둔 상태에서는 **되돌릴 수 없는 화면**이 남는다
        // (설계 검토가 치명으로 잡았다). 시트는 자기 창이라 여기 있어도 자리를 차지하지 않는다.
        if (trackSheetOpen) TrackSheet(onDismiss = { trackSheetOpen = false })

        if (!showControls) return@Box
        // **조작부 아래에 반투명한 판을 깐다.** 영상 위에 흰 글자만 얹으면 밝은 장면에서
        // 글자와 단추가 사라진다. 위로 갈수록 옅어지는 그라데이션이라 영상을 통째로
        // 가리지도 않는다. 영상이 아니면 테마의 표면색으로 덮는다.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (showingVideo) {
                        Modifier.background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB3000000), Color(0xD9000000))
                            )
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    }
                )
                // **여기서 탭을 삼킨다.** 이 판에는 포인터를 잡는 수정자가 하나도
                // 없어서, 단추가 아닌 빈 자리(제목 줄·시간 표시 옆)를 누르면 Compose 가
                // 형제를 더 내려가 **판 뒤에 깔린 재생목록 줄**을 눌러 버렸다. 사용자는
                // 아무것도 누르지 않았는데 곡이 바뀐다. `indication = null` 이라 눌린
                // 표시는 나지 않는다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyMedium,
                color = onBackground,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            val fraction = scrubbing ?: if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f
            // 끄는 중인지 알아야 손잡이를 키울 수 있다. `Slider` 가 이 소스를 쓰게
            // 넘겨 주어야 하고, 그러지 않으면 우리가 만든 소스에는 아무것도 안 들어온다.
            val barInteraction = remember { MutableInteractionSource() }
            val dragging by barInteraction.collectIsDraggedAsState()
            // 평소에는 작은 점, 끄는 동안에는 커진다. 즉시 바뀌면 튀므로 애니메이션을 건다.
            val thumbDot by animateDpAsState(if (dragging) 16.dp else 11.dp, label = "thumb")
            Slider(
                value = fraction,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { PlaybackConnection.seekTo((it * state.durationMs).toLong()) }
                    scrubbing = null
                },
                interactionSource = barInteraction,
                // **YouTube·VLC 의 모양을 따른다.** 앞서 Material 3 의 기본 손잡이(세로로
                // 길고 좌우 여백이 있는 알약)를 얇은 막대로 바꿨더니 시인성은 나아졌지만
                // "익숙한 형태가 아니라 어색하다"는 지적이 왔다. 재생기의 관행은 하나로
                // 모여 있다 — **얇은 트랙 + 둥근 점**, 지나온 쪽은 강조색, 남은 쪽은 옅게.
                //
                // 기본 트랙을 쓰지 않는 이유가 둘이다. 하나는 두께(기본 16dp 짜리 알약)이고,
                // 다른 하나는 Material 3 이 트랙 오른쪽 끝에 찍는 **정지 표시 점**이다 —
                // 재생기에는 없는 기호라 '뭔가 더 있다' 로 읽힌다.
                //
                // 슬롯이 주는 `SliderState` 를 쓰지 않고 위에서 계산한 [fraction] 을 쓴다.
                // 같은 값이고, 끄는 동안에는 `scrubbing` 이 손가락을 따라가므로 이쪽이
                // 오히려 한 겹 덜 거친다.
                track = {
                    // **폭을 알아야 마커를 놓을 수 있다.** 비율로만 두면 `fillMaxWidth`
                    // 상자가 가운데 정렬을 물려받아 엉뚱한 자리에 그려진다(설계 검토가
                    // 잡았다). `offset(x)` 은 레이아웃 방향을 따르므로 RTL 도 함께 맞는다.
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(TRACK_HEIGHT),
                    ) {
                        val full = maxWidth
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(TRACK_HEIGHT / 2))
                                // 남은 쪽. 배경이 검든 희든 보이도록 글자색을 옅혀 쓴다.
                                .background(onBackground.copy(alpha = 0.28f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        // **찍어 둔 구간을 막대에 보인다.** 숫자로만 말하면 '어디쯤을
                        // 되풀이하고 있는가' 가 머릿속에 그려지지 않는다.
                        state.abSpan?.takeIf { state.durationMs > 0 }?.let { span ->
                            AbMarker(full, span.aMs, state.durationMs)
                            span.bMs?.let { AbMarker(full, it, state.durationMs) }
                        }
                    }
                },
                thumb = {
                    // **재는 상자는 고정하고 안의 점만 키운다.** Material 3 은 트랙을
                    // `constraints.offset(-손잡이 너비)` 로 재고 손잡이 너비의 절반만큼
                    // 민다(바이트코드로 확인했다). 그래서 손잡이의 **측정 크기**를
                    // 애니메이션하면 막대의 좌우 여백이 잡을 때마다 함께 흔들린다 —
                    // 커지는 것은 점 하나여야 하고 막대는 제자리에 있어야 한다.
                    Box(Modifier.size(THUMB_SLOT), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(thumbDot)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = onBackground,
                    )
                    // 큐가 하나뿐이면 "1 / 1" 은 아무것도 말하지 않는다.
                    if (state.hasQueue) {
                        Text(
                            text = queuePosition(state.queueIndex, state.queueSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
                // **큐 조작은 큐가 있을 때만 보인다.** 한 곡을 틀어 놓고 섞기·반복·다음이
                // 회색으로 늘어서 있으면 무엇이 되는 상태인지 읽히지 않는다.
                if (state.hasQueue) {
                    IconButton(onClick = {
                        val open = !playlistOpen
                        onPlaylistOpenChange(open)
                        // 목록을 닫는 것은 "이 묶음을 그만 본다" 이므로 섞기 이력도 비운다.
                        if (!open) PlaybackConnection.onPlaylistClosed()
                    }) {
                        Icon(
                            imageVector = PlaylistIcon,
                            contentDescription = playlistLabel(playlistOpen),
                            tint = if (playlistOpen) MaterialTheme.colorScheme.primary else onBackground,
                        )
                    }
                    IconButton(onClick = { PlaybackConnection.toggleShuffle() }) {
                        Icon(
                            imageVector = ShuffleIcon,
                            contentDescription = shuffleLabel(state.shuffle),
                            tint = if (state.shuffle) MaterialTheme.colorScheme.primary else onBackground,
                        )
                    }
                    IconButton(onClick = { PlaybackConnection.cycleRepeat() }) {
                        Icon(
                            imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                                RepeatOneIcon
                            } else {
                                RepeatIcon
                            },
                            contentDescription = repeatLabel(state.repeatMode),
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                                onBackground
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    IconButton(onClick = { PlaybackConnection.previous() }) {
                        Icon(SkipPreviousIcon, previousLabel(), tint = onBackground)
                    }
                }
                IconButton(onClick = { PlaybackConnection.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) pauseLabel() else playLabel(),
                        tint = onBackground,
                    )
                }
                if (state.hasQueue) {
                    IconButton(onClick = { PlaybackConnection.next() }) {
                        Icon(SkipNextIcon, nextLabel(), tint = onBackground)
                    }
                }
            }

            // **배속 눈금을 조작부 안에서 펼친다.**
            //
            // 떠 있는 층으로 두면 가로 영상에서 남는 높이가 절반뿐이라 아래 눈금이 잘린다.
            // 안에 두면 판이 그만큼 높아지고, 판 높이는 `onSizeChanged` 로 재고 있으므로
            // 위에 뜨는 안내들이 **저절로** 따라 올라간다 — 그 자리를 상수로 다시 적으면
            // 10단계가 이미 고친 결함이 되돌아온다.
            if (speedOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = speedTitle(),
                        style = MaterialTheme.typography.labelMedium,
                        color = onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    for (step in SpeedSteps.STEPS) {
                        val picked = SpeedSteps.nearest(state.speed) == step
                        TextButton(onClick = {
                            PlaybackConnection.setSpeed(step)
                            speedOpen = false
                        }) {
                            Text(
                                text = SpeedSteps.text(step),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                color = if (picked) MaterialTheme.colorScheme.primary else onBackground,
                            )
                        }
                    }
                }
            }

            // **이 화면에 대한 조작은 줄을 따로 쓴다.**
            //
            // 위 줄은 '큐에 대한 조작'(목록·섞기·반복·이전/재생/다음)이고 여기는 '지금 이
            // 재생에 대한 조작' 이다. 한 줄에 몰면 작은 폰에서 아홉 개가 넘어 겹치거나
            // 잘린다. 넘칠 때를 대비해 가로 스크롤을 둔다 — 글꼴을 크게 쓰는 사람에게는
            // 두 줄로도 모자랄 수 있다.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 배속은 **아이콘이 아니라 지금 값**을 보여 준다. 1.0 이 아닌 채로 잊으면
                // '왜 목소리가 이상하지' 가 되는데, 값이 늘 적혀 있으면 그 일이 없다.
                TextButton(
                    onClick = { speedOpen = !speedOpen },
                    modifier = Modifier.semantics { contentDescription = speedDescription },
                ) {
                    Text(
                        text = SpeedSteps.text(state.speed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (SpeedSteps.isNormal(state.speed)) {
                            FontWeight.Normal
                        } else {
                            FontWeight.Bold
                        },
                        color = if (SpeedSteps.isNormal(state.speed)) {
                            onBackground
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                // A-B 도 글자가 아이콘보다 낫다 — 그림으로 그리면 무슨 구간인지 읽히지 않고,
                // 'A-B' 는 재생기에서 널리 쓰이는 표기다.
                TextButton(
                    onClick = {
                        abNote = PlaybackConnection.markAb()
                        noteSeq++
                    },
                    modifier = Modifier.semantics { contentDescription = abDescription },
                ) {
                    Text(
                        text = abText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (state.abSpan == null) FontWeight.Normal else FontWeight.Bold,
                        color = when {
                            state.abSpan?.isLooping == true -> MaterialTheme.colorScheme.primary
                            state.abSpan != null -> MaterialTheme.colorScheme.tertiary
                            else -> onBackground
                        },
                    )
                }

                // **고를 것이 있을 때만** 뜬다. 소리 트랙 하나짜리 mp3 에서 '트랙' 단추를
                // 눌렀더니 줄이 하나뿐인 것은 아무것도 가르치지 않는다.
                if (state.canChooseTracks) {
                    IconButton(onClick = { trackSheetOpen = true }) {
                        Icon(SubtitleIcon, tracksTitle(), tint = onBackground)
                    }
                }

                // **잠겨 있는 동안에는 영상이 아니어도 단추를 남긴다.** 큐가 소리로 넘어가면
                // 조건이 꺼지는데, 그때 단추가 사라지면 **잠금을 풀 방법이 없다.**
                if (state.hasVideo || orientationLocked) {
                    IconButton(onClick = onToggleOrientationLock) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = orientationLockLabel(orientationLocked),
                            tint = if (orientationLocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                onBackground
                            },
                        )
                    }
                }

                if (pipAvailable) {
                    IconButton(onClick = {
                        if (!onEnterPip()) {
                            pipFailed = true
                            noteSeq++
                        }
                    }) {
                        Icon(PipIcon, pipLabel(), tint = onBackground)
                    }
                }
            }
        }
    }
}

/**
 * 진행 바에 찍는 A·B 표시.
 *
 * 트랙(4dp)보다 높게 그려 눈에 띄게 한다 — 부모가 자르지 않으므로 위아래로 넘쳐 그려지고,
 * 손잡이가 차지하는 16dp 안에 들어와 다른 것을 밀어내지 않는다.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AbMarker(
    full: androidx.compose.ui.unit.Dp,
    positionMs: Long,
    durationMs: Long,
) {
    val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Box(
        Modifier
            .align(Alignment.CenterStart)
            // 마커의 가운데가 그 자리에 오도록 절반만큼 당긴다.
            .offset(x = full * fraction - AB_MARKER_WIDTH / 2)
            .width(AB_MARKER_WIDTH)
            .height(AB_MARKER_HEIGHT)
            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(1.dp))
    )
}

/**
 * 이어보기 제안을 띄워 두는 시간. 사용자가 3초로 정했다.
 *
 * 예전 안내는 `actionLabel` 이 붙은 스낵바라 기간이 `Indefinite` 였고, 누르거나 밀기
 * 전에는 사라지지 않았다. **끝이 없는 안내를 만들지 마라** — 10단계가 두드림 안내에서
 * 같은 것을 한 번 겪었다(`GestureMath.TAP_FEEDBACK_HOLD_MS`).
 */
private const val RESUME_OFFER_HOLD_MS = 3_000L

/**
 * 이어보기 제안 알약이 차지하는 높이. 실패 문구가 그만큼 더 올라간다.
 *
 * 알약의 실제 높이는 **56dp** 다 — `TextButton` 이 `Surface` 를 거치며
 * `minimumInteractiveComponentSize`(48dp)를 받고 위아래 4dp 가 붙는다. 글꼴 배율을
 * 올려도 48dp 하한이 이기므로 그 값은 변하지 않는다(글이 한 줄인 한).
 */
private val OFFER_HEIGHT = 64.dp

/** 조작부 판과 그 위에 뜨는 안내 사이의 틈. */
private val OFFER_GAP = 16.dp

/**
 * 잠깐 뜨는 안내(A-B 결과·PiP 실패)를 띄워 두는 시간.
 *
 * 이어보기 제안(3초)보다 짧다 — 그쪽은 **답을 기다리는** 물음이고 이쪽은 방금 누른 것에
 * 대한 **답**이라, 읽을 만큼만 있으면 된다. 끝이 없는 안내를 만들지 않는다는 규칙은 같다.
 */
private const val NOTE_HOLD_MS = 1_800L

/** 진행 바에 찍는 A·B 표시의 너비. 트랙보다 좁으면 눈에 띄지 않는다. */
private val AB_MARKER_WIDTH = 3.dp

/** 같은 표시의 높이. 트랙(4dp)보다 높아 위아래로 조금 넘친다. */
private val AB_MARKER_HEIGHT = 12.dp

/**
 * 진행 바의 트랙 두께.
 *
 * YouTube 가 3dp 안팎, VLC 가 그보다 조금 두껍다. 4dp 면 검은 영상 위에서도 보이고
 * 손잡이(11dp 점)와 굵기가 확실히 갈린다 — 트랙이 두꺼우면 점이 트랙에 묻힌다.
 */
private val TRACK_HEIGHT = 4.dp

/**
 * 손잡이가 **차지하는** 자리. 점이 커져도 이 값은 변하지 않는다.
 *
 * 이 값의 절반이 막대의 좌우 여백이 된다(Material 3 의 측정 규칙). 커진 점(16dp)과
 * 같게 두어 끌 때 점이 상자 밖으로 삐져나오지 않게 했다.
 */
private val THUMB_SLOT = 16.dp

/**
 * 영상 표면.
 *
 * 화면에서 사라질 때 표면만 뗀다. **`pause` 를 부르지 않는다** — 그것이 백그라운드에서
 * 소리가 이어지는 이유다.
 */
@Composable
private fun VideoSurface(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { PlaybackConnection.attachSurface(it) }
        },
    )
    DisposableEffect(Unit) {
        onDispose { PlaybackConnection.attachSurface(null) }
    }
}
