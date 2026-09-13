package io.github.donggi.iroiroviewer.player

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import io.github.donggi.iroiroviewer.playback.PauseIcon
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
import io.github.donggi.iroiroviewer.ui.IroiroTheme

/**
 * 큰 화면 재생기. 지금은 최소한이다 — 표면, 재생/일시정지, 탐색.
 *
 * 제스처·PiP·자막·속도·트랙 선택은 10단계다. 여기서 중요한 것은 하나다:
 * **이 화면이 사라져도 재생이 멈추지 않는다.** 플레이어는 서비스에 있고 이 화면은
 * 표면만 빌려 준다. 그래서 `onStop` 에서 표면을 떼기만 하고 `pause` 를 부르지 않는다 —
 * 홈으로 나가면 영상은 멎지만 소리는 계속되고, 돌아오면 그 자리에서 화면이 다시 붙는다.
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
     * 자체가 아래 `remember` 의 키가 된다.
     */
    private data class PlaylistRequest(val seq: Long, val expand: Boolean)

    private var requestSeq = 0L

    private fun receive(intent: android.content.Intent?) {
        playlistRequest.value = PlaylistRequest(
            seq = ++requestSeq,
            expand = intent?.getBooleanExtra(PlayerEntry.EXTRA_SHOW_PLAYLIST, false) == true,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackConnection.connect(this)
        receive(intent)
        followDeviceRotationWhileVideo()
        enableEdgeToEdge()
        setContent {
            // **영상일 때만 어둡게 못 박는다.** 영상은 밝은 배경에서 테두리가 번져 보이고,
            // 어두운 방에서 흰 화면이 둘레를 감싸면 눈이 부시다. 그러나 **소리 파일과
            // 재생목록은 그냥 글과 목록**이라 다른 화면과 같은 규칙을 따르는 편이 낫다 —
            // 시스템이 밝은 모드인데 이 화면만 검으면 그것이 오히려 튄다.
            val playback by PlaybackConnection.state.collectAsStateWithLifecycle()
            val request = playlistRequest.collectAsStateWithLifecycle().value
            // **재생목록 상태를 테마보다 위에 둔다.** 목록이 열려 있으면 영상이 보이지
            // 않으므로 그때는 OS 테마를 따라야 하고, 그 판단을 테마가 알아야 한다.
            //
            // **사용자가 직접 여닫았는지를 따로 기억한다.** 예전에는 `hasVideo` 를
            // `remember` 의 키로 써서 영상 여부가 바뀔 때마다 기본값으로 되돌렸는데,
            // 폴더 큐는 소리와 영상을 **한 큐에 섞으므로**(`FolderQueue.isPlayable`)
            // 곡이 넘어갈 때마다 그 키가 실제로 바뀐다 — 닫아 둔 목록이 영상 위로 다시
            // 열렸다. 알림이 '목록을 펼쳐라' 를 싣고 오는 길이 생기면서 그 값이 액티비티
            // 수명 내내 남아 더 자주 드러나게 됐다.
            //
            // 그래서 **손대기 전에는 기본값을 따르고, 한 번 손대면 그 뜻이 이긴다.**
            // **인텐트가 새로 도착하면** 다시 기본값으로 돌아간다 — 그때는 사용자가 방금
            // 새로 요구한 것이기 때문이다. 키가 값이 아니라 [PlaylistRequest.seq] 인
            // 이유가 그것이다(그 주석 참고).
            var userChoice by remember(request.seq) { mutableStateOf<Boolean?>(null) }
            val playlistOpen = userChoice ?: (request.expand || !playback.hasVideo)
            // **큐가 없으면 목록을 '열린' 것으로 치지 않는다.** 한 곡짜리에서는 그릴
            // 목록이 없어서 `PlaylistPanel` 이 아예 안 그려지는데, 그래도 '열림' 으로
            // 치면 테마만 밝아져 **영상이 흰 바탕 위에 뜬다.** 알림이 목록을 펼쳐 달라고
            // 보내는 길이 생기면서 실제로 닿을 수 있는 자리가 됐다.
            val playlistVisible = playlistOpen && playback.hasQueue
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
                    onPlaylistOpenChange = { userChoice = it },
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
     * **보이지 않는 동안에는 방향 요청을 붙들지 않는다.**
     *
     * 이것이 컴포저블이 아니라 액티비티 콜백인 이유가 여기 있다. 컴포지션은 화면이
     * 정지하면 **다시 그려지지 않는다** — `collectAsStateWithLifecycle` 의
     * `minActiveState` 를 낮춰도 마찬가지다. 그래서 큐가 영상 → 소리로 넘어가도 효과가
     * 다시 돌지 않고, 보이지도 않는 화면이 [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 를
     * 그대로 쥐고 있었다.
     *
     * 그러면 알림에서 돌아오는 순간 창이 **가로로 잡혔다가 세로로 되돌아온다.** 소리
     * 파일인데 화면이 가로로 도는 것부터가 이 앱이 스스로 금지한 것이고(아래 KDoc),
     * 그 사이에 새로 넓어진 창의 아래쪽이 아무도 칠하지 않은 채 남는 것이 사용자가 본
     * '하단이 검게 보인다' 다.
     *
     * 에뮬레이터에서 재현되지 않은 이유도 이것이다 — `adb emu rotate` 를 주지 않는 한
     * 센서 방향이 세로로 고정이라 이 길을 한 번도 타지 않는다. 실기기는 손에 든 각도
     * 만으로 탄다. **'가끔' 이라는 말이 그 뜻이었다.**
     */
    override fun onStop() {
        super.onStop()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
     * **아직 못 한 것.** 누워서 볼 때를 위한 '방향 잠금' 단추가 없다. VLC·MX 플레이어가
     * 모두 가진 것이고 10단계(재생 심화)에서 넣는다.
     *
     * **태블릿에서 안 들을 수 있다. 다만 이유가 `targetSdk 36` 이 아니다.** Android 16 이
     * 무시하는 것은 *고정* 방향(`portrait`·`landscape` 등)이고
     * [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 는 `ActivityInfo.isFixedOrientation()` 에
     * 들어 있지 않아 그 정책의 대상이 아니다. 진짜 조건은 **그 디스플레이의
     * `ignoreOrientationRequest`** 다 — 폰 AVD 는 `false` 라 요청이 먹는 것을 실측했고
     * (`adb shell dumpsys window | grep ignoreOrientationRequest`), 태블릿 AVD 는 아직 재지 않았다.
     *
     * 이유를 틀리게 적어 두면 10단계에서 필요 없는 opt-out 속성
     * (`PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION`)을 넣게 되므로 여기 정확히 남긴다.
     */
    private fun followDeviceRotationWhileVideo() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackConnection.state.collect { s ->
                    requestedOrientation = if (s.hasVideo) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }
}

/**
 * **기기를 가로로 돌리면 가로로 튼다 — 화면이 세로로 고정되어 있어도.**
 *
 * 안드로이드에서 자동회전을 끄면 앱은 보통 따라 돌 수 없다. 그런데
 * [ActivityInfo.SCREEN_ORIENTATION_SENSOR] 는 문서가 명시적으로 "사용자의 센서 기반 회전
 * 끄기 설정을 무시한다"(*Ignores user's setting to turn off sensor-based rotation*)고 적은
 * 몇 안 되는 값이다. 그래서 `OrientationEventListener` 로 각도를 직접 재고 방향을 계산하는
 * 흔한 방법이 필요 없다 — 그쪽은 우리가 센서를 다시 구현하는 것이고, 기기마다 어긋난다.
 *
 * **영상일 때만** 건다. 소리만 나는 파일에서까지 기기를 돌릴 때마다 화면이 따라 돌면,
 * 그것은 사용자가 세로 고정을 켜 둔 뜻을 정면으로 거스르는 것이다. 화면을 떠나면
 * [ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED] 로 돌려 놓아 시스템 설정을 다시 따르게 한다.
 *
 * `FULL_SENSOR` 가 아니라 `SENSOR` 인 이유는 뒤집힌 세로(180°)까지 허용할 이유가 없기
 * 때문이다. 영상에 필요한 것은 양쪽 가로이고, 그 둘은 `SENSOR` 가 모든 기기에서 준다.
 *
 * 이 액티비티는 매니페스트에 `configChanges` 로 `orientation|screenSize` 를 적어 두었으므로
 * 회전해도 액티비티가 다시 만들어지지 않는다 — 표면이 끊기지 않고 재생이 이어진다.
 *
 * **아직 못 한 것.** 누워서 볼 때를 위한 '방향 잠금' 단추가 없다. VLC·MX 플레이어가 모두
 * 가진 것이고 10단계(재생 심화)에서 넣는다.
 *
 * **태블릿에서 안 들을 수 있다. 다만 이유가 `targetSdk 36` 이 아니다.** Android 16 이
 * 무시하는 것은 *고정* 방향(`portrait`·`landscape` 등)이고 [ActivityInfo.SCREEN_ORIENTATION_SENSOR]
 * 는 `ActivityInfo.isFixedOrientation()` 에 들어 있지 않아 그 정책의 대상이 아니다.
 * 진짜 조건은 **그 디스플레이의 `ignoreOrientationRequest`** 다 — 폰 AVD 는 `false` 라
 * 요청이 먹는 것을 실측했고(`adb shell dumpsys window | grep ignoreOrientationRequest`),
 * 태블릿 AVD 는 아직 재지 않았다.
 *
 * 이유를 틀리게 적어 두면 10단계에서 필요 없는 opt-out 속성
 * (`PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION`)을 넣게 되므로 여기 정확히 남긴다.
 */


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
    onBack: () -> Unit,
    playlistOpen: Boolean,
    onPlaylistOpenChange: (Boolean) -> Unit,
) {
    val state by PlaybackConnection.state.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    // **조작부는 영상일 때만 숨긴다.** 소리만 나는 화면에서 막대까지 감추면 아무것도 없는
    // 빈 화면이 되고, 그때 다시 꺼내는 방법을 알 길이 없다.
    var controlsVisible by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
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
                .playerGestures(
                    enabled = !playlistOpen,
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
                    modifier = if (state.videoAspect > 0f) {
                        Modifier.fillMaxWidth().aspectRatio(state.videoAspect)
                    } else {
                        Modifier.fillMaxSize()
                    },
                ) {
                    VideoSurface(Modifier.fillMaxSize())
                    SubtitleLayer()
                }
            } else if (!state.hasVideo) {
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
            feedback?.let { GestureNote(it, Modifier.align(Alignment.Center)) }
            if (playlistOpen && state.hasQueue) {
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
        if (controlsMeasured) state.resumeOfferMs?.let { from ->
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(TRACK_HEIGHT)
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
        }
    }
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
