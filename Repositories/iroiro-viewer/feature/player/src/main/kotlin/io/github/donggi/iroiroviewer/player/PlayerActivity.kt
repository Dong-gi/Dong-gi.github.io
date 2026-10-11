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
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.common.Player
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileOpText
import io.github.donggi.iroiroviewer.io.IncomingFiles
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MediaClipper
import io.github.donggi.iroiroviewer.io.ShareHelper
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
import io.github.donggi.iroiroviewer.playback.PlayerSupport
import io.github.donggi.iroiroviewer.ui.IroSlider
import io.github.donggi.iroiroviewer.playback.formatTime
import io.github.donggi.iroiroviewer.playback.pauseLabel
import io.github.donggi.iroiroviewer.playback.playLabel
import io.github.donggi.iroiroviewer.playback.playbackFailureText
import io.github.donggi.iroiroviewer.playback.resumeConfirmLabel
import io.github.donggi.iroiroviewer.playback.resumeOfferText
import io.github.donggi.iroiroviewer.playback.seekBarLabel
import io.github.donggi.iroiroviewer.playback.seekBarState
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
 * 대한 조작 — 배속·A-B·트랙 시트·방향 잠금·터치 잠금·PiP), 가운데 큰 재생 단추, 뒤로 단추,
 * 이어보기 제안, 재생목록 층. 조작부는 재생 중이면 저절로 거두고, 그동안 시스템 바도 거둔다
 * ([PlayerChrome]).
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
 *    화면 켜 둠([keepScreenOnWhileVideoPlays])과 시스템 바([holdSystemBars])도 같다 — 그래서
 *    바가 따르는 '조작부가 떠 있는가' 도 컴포지션이 아니라 이 액티비티가 든다([controlsShown]).
 * 3. **겹치는 층의 자리를 상수로 맞히지 않는다.** 조작부 판의 높이는 `onSizeChanged` 로
 *    재고, 위에 뜨는 것들(이어보기 알약·실패 문구·안내)은 잰 값을 따라 올라간다.
 * 4. **화면과 PiP 판정이 같은 값을 읽는다.** 재생목록의 여닫음은 컴포지션이 아니라
 *    액티비티가 들고([playlistChoice]) [playlistShown] 한 함수가 답한다 — 갈라 놓으면
 *    '목록이 펼쳐져 있으면 PiP 에 들어가지 않는다' 가 코드로 성립하지 않는다.
 *
 * ## 이 앱이 못 트는 파일 — 다른 앱으로 넘긴다
 *
 * 재생이 '이 기기·이 앱이 못 튼다' 로 끝나면(코덱·추출기가 없다, 잠겼다) 실패 문구 곁에 '다른 앱으로 열기' 를 단다.
 * 조작부 둘째 줄 끝의 ⋮ 에도 지금 항목을 넘기는 줄이 있다. 둘 다 고르는 창이고, 띄웠으면 우리 재생은 **멈춘다**(끝내지는
 * 않는다). 무엇에 다는가·언제 멈추는가는 [PlayerOpenWith], 경로를 찾는 것은 [ItemPaths], 넘기는 것은 [openWithOtherApp] 이다.
 * 곡이 바뀌면 앞 곡의 실패는 커넥션이 버린다(`PlaybackConnection.State.carriedFailure`) — 이 화면이 보는 실패는 언제나
 * 지금 곡의 것이다. **PiP 에서는 서지 않는다** — 불변식 1 의 예외(창을 닫으면 멈춘다)와 같은 이유로, 작은 창에는 영상 말고
 * 아무것도 없다.
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
     * 세울 자리가 없었던 것이다. 지금은 화면과 PiP 판정이
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

    /**
     * 조작부가 떠 있는가. **컴포저블이 아니라 액티비티가 든다**(불변식 2) — 시스템 바를 함께 거두는 판정([holdSystemBars])이 이 값을
     * 읽는데, 그것은 수명에 묶여야 한다. 화면은 이 값을 그리고, 두드림과 저절로 거두기([PlayerChrome.AUTO_HIDE_MS])가 바꾼다. 새로
     * 들어오면([receive]) 다시 띄운다 — 무엇을 틀었는지 한 번은 보여야 한다.
     */
    private val controlsShown = MutableStateFlow(true)

    /**
     * 터치 잠금(큰 재생기의 관행). 켜면 조작부와 몸짓이 모두 멎고, 화면을 두드리면 잠금 풀기 단추만 잠깐 뜬다 — 누워서
     * 보다가 손바닥이 닿아 건너뛰거나 소리가 바뀌는 일을 막는다. **뒤로 가기도 막는다**([backRouter]) — 가장자리를 스친 손이 화면을
     * 닫지 않게(잠금 풀기 단추를 띄운다). 액티비티가 드는 까닭은 [controlsShown] 과 같고, 뒤로 가기를 가르는 것도 여기다.
     */
    private val touchLocked = MutableStateFlow(false)

    /** 잠금 풀기 단추를 띄우라는 요청의 번호 — 값이 아니라 **도착**이다(함정 표의 '`MutableStateFlow` 는 같은 값을 합친다'). */
    private val unlockHint = MutableStateFlow(0L)

    /** 마지막으로 건 시스템 바([applyBars]). PiP 에서 전체 화면으로 돌아올 때 같은 것을 다시 건다. */
    private var lastBars: BarsPlan? = null

    /**
     * 프로세스가 죽은 뒤 되살아난 화면이 다시 세울 큐([PlayerRestore]). 다시 세우기 전에 또 저장 상태를 적게 되면(그사이 다시
     * 떠났다) 이것을 그대로 적는다 — 그때는 플레이어가 아직 비어 있어 큐에서 읽을 것이 없다.
     */
    private var pendingRestore: PlayerRestore.Snapshot? = null

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

    /** 큐 항목의 파일 경로를 찾는 읽기 전용 컨트롤러. [onCreate] 에서 붙고 [onDestroy] 에서 놓는다. */
    private lateinit var itemPaths: ItemPaths

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

    /**
     * 다른 앱이 넘긴 파일로 **막** 들어왔다(`PlayerEntry.EXTRA_FROM_OUTSIDE`). 그러면 뒤로 가기가 이 화면을 끝내지 않고 **태스크를
     * 뒤로 보낸다** — 이 화면은 우리 태스크에 얹히므로 끝내면 그 밑의 파일 목록이 드러나는데, 그 사람은 보낸 앱에서 왔다. 재생은
     * 여느 뒤로 가기처럼 서비스에서 이어진다. 우리 앱 안에서 다시 열면(미니 바·알림·목록) 그 인텐트가 이것을 거둔다.
     *
     * **한 번뿐이다**([forgetSender]) — 뒤로 가기로 보낸 앱에 돌아갔거나 홈·최근 앱으로 떠났으면 거둔다. 그 뒤 런처로 돌아온 사람은
     * 보낸 앱에서 오지 않았다. 처음에는 거두지 않아, 내려받은 영상을 뷰어로 연 사람이 그 뒤 몇 번을 돌아와도 뒤로 가기가 파일
     * 목록 대신 홈으로 튕겼다(사용자가 짚었다).
     */
    private var fromOutside = false

    /**
     * 다른 앱으로 넘기는 중이다([openWithOtherApp]). 우리가 띄운 앱도 [onUserLeaveHint] 를 부르는데, 그 앱은 우리 태스크에 얹혀
     * 뒤로 가면 이 화면으로 돌아온다 — 떠난 것이 아니므로 [fromOutside] 를 거두지 않는다. 다시 서면([onResume]) 내린다.
     */
    private var handingOff = false

    /**
     * 시스템의 뒤로 가기를 [goBack] 으로 보낸다. 거기서 할 일이 '끝내기' 뿐이면 꺼 둔다([routeBack]) — 여느 뒤로 가기가 그대로
     * 돈다(13 이상의 미리 보는 뒤로 가기도). 터치 잠금 중에는 닫지 않고 잠금 풀기 단추를 띄운다([touchLocked]).
     */
    private val backRouter = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (touchLocked.value) revealUnlock() else goBack()
        }
    }

    /**
     * 뒤로 가기가 가는 곳.
     *
     * * 다른 앱이 막 넘긴 파일이면([fromOutside]) 그 앱으로 — 태스크를 뒤로 보낸다. 한 번뿐이다.
     * * 밑에 파일 목록이 있으면 끝낸다 — 그것이 드러난다. PiP 를 거쳐도 같다: PiP 에 들어가면 이 화면이 제 태스크로 옮겨 가지만
     *   전체 화면으로 되돌리면 원래 태스크로 돌아온다(API 31 실측).
     * * **이 화면이 태스크의 뿌리면**(밑에 아무것도 없다 — 다른 앱이 넘긴 파일로 우리 앱이 처음 떴거나, 파일 목록을 닫은 뒤 알림으로
     *   다시 떴다) 지금 트는 파일의 폴더로 파일 목록을 열고 끝낸다([PlayerSupport.openFileList]). 그냥 끝내면 홈으로 떨어진다.
     */
    private fun goBack() {
        when {
            fromOutside -> {
                forgetSender()
                moveTaskToBack(true)
            }
            isTaskRoot -> {
                PlayerSupport.openFileList(this, playingFolder())
                finish()
            }
            else -> finish()
        }
    }

    /** 보낸 앱으로 돌아가는 길을 거둔다([fromOutside]). */
    private fun forgetSender() {
        if (fromOutside) Iro.d { "보낸 앱으로 돌아가는 길을 거둔다" }
        fromOutside = false
        routeBack()
    }

    /**
     * [backRouter] 를 켤지 — [goBack] 이 '끝내기' 말고 할 일이 있을 때, 그리고 터치 잠금 중일 때만. 뿌리인지는 PiP 를 오가며
     * 바뀌므로 다시 설 때마다 본다.
     */
    private fun routeBack() {
        backRouter.isEnabled = fromOutside || isTaskRoot || touchLocked.value
    }

    /**
     * 지금 트는 파일의 폴더 — 기기 저장소(`/storage/`)의 파일일 때만. 앱 캐시의 사본(다른 앱이 넘긴 것 — 사용자가 찾아갈 수 없는
     * 자리다)은 null 이고, 그때 파일 목록은 첫 화면이다.
     * 폴더가 그사이 사라졌어도 그대로 넘긴다 — 주 스레드라 디스크를 묻지 않고, 파일 목록이 그 자리에서 말한다.
     */
    private fun playingFolder(): String? {
        val key = PlaybackConnection.state.value.fileKey ?: return null
        val path = itemPaths.pathOf(key) ?: return null
        return java.io.File(path).parent?.takeIf { it.startsWith("/storage/") }
    }

    private fun receive(intent: android.content.Intent?) {
        fromOutside = intent?.getBooleanExtra(PlayerEntry.EXTRA_FROM_OUTSIDE, false) == true
        // 새로 들어왔다 — 잠가 둔 손을 풀고 조작부를 띄운다. 목록·미니 바·알림에서 들어온 사람은 방금 무엇을 하겠다고 말했다.
        touchLocked.value = false
        controlsShown.value = true
        routeBack()
        playlistRequest.value = PlaylistRequest(
            seq = ++requestSeq,
            expand = intent?.getBooleanExtra(PlayerEntry.EXTRA_SHOW_PLAYLIST, false) == true,
        )
        // 새 요청이 도착하면 **사용자가 앞서 고른 여닫음을 잊는다** — 그때는 방금 새로
        // 요구한 것이기 때문이다(알림을 다시 누르면 목록이 다시 펼쳐져야 한다).
        playlistChoice.value = null
    }

    /** 조작부를 여닫는다(한 번 두드림). 함수인 까닭은 몸짓 인식기가 처음 붙을 때 람다를 잡기 때문이다 — 값을 넘기면 낡는다. */
    private fun toggleControls() {
        controlsShown.value = !controlsShown.value
    }

    /** 터치 잠금을 켜고 끈다. 켜면 조작부를 거두고, 풀면 띄운다 — 풀자마자 무엇을 할 수 있는지 보여야 한다. */
    private fun setTouchLocked(on: Boolean) {
        touchLocked.value = on
        controlsShown.value = !on
        routeBack()
    }

    /** 잠금 풀기 단추를 잠깐 띄운다(터치 잠금 중의 두드림·뒤로 가기). */
    private fun revealUnlock() {
        unlockHint.value = unlockHint.value + 1
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
        onBackPressedDispatcher.addCallback(this, backRouter)
        PlaybackConnection.connect(this)
        // 누르는 순간 경로가 있어야 한다(`ExternalOpen.open` 은 동기다). 붙는 데 걸리는 시간을 여기서 미리 쓴다.
        itemPaths = ItemPaths(this)
        receive(intent)
        // 다시 선 화면(밤 모드를 바꿨다·프로세스가 죽었다 살아났다)은 처음 인텐트를 그대로 받는다 — 거둔 [fromOutside] 가 되살아나지
        // 않게 저장해 둔 값을 따른다.
        if (savedInstanceState != null) {
            fromOutside = savedInstanceState.getBoolean(KEY_FROM_OUTSIDE, false)
            Iro.d { "재생 화면이 다시 섰다 — 보낸 앱으로 돌아가는 길=$fromOutside" }
            routeBack()
            // 프로세스가 죽었다 살아났으면 플레이어가 비어 있다 — 적어 둔 큐로 다시 세운다([restoreIfEmpty]).
            pendingRestore = PlayerRestore.read(savedInstanceState)
            restoreIfEmpty()
        }
        followDeviceRotationWhileVideo()
        keepScreenOnWhileVideoPlays()
        keepPipParamsFresh()
        leavePipWhenQueueTurnsToAudio()
        enableEdgeToEdge()
        holdSystemBars()
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
            val shown by controlsShown.collectAsStateWithLifecycle()
            val touchLock by touchLocked.collectAsStateWithLifecycle()
            val hint by unlockHint.collectAsStateWithLifecycle()

            val playlistVisible = playlistShown(playback, request, choice)
            // 영상이 실제로 **보이고 있을 때만** 어둡게 못 박는다. 영상은 밝은 배경에서
            // 테두리가 번지고 어두운 방에서는 눈이 부시다. 목록과 소리 파일은 그냥 글과
            // 목록이라 다른 화면과 같은 규칙을 따르는 편이 낫다.
            //
            // **테마와 조작부가 같은 식을 써야 한다** — 어긋나면 목록은 밝은데 조작부만
            // 어두운 그림이 나온다(한 번 겪었다). 그래서 여기서 한 번 정해 내려보낸다.
            IroiroTheme(forceDark = playback.hasVideo && !playlistVisible) {
                PlayerScreen(
                    onBack = ::goBack,
                    playlistOpen = playlistVisible,
                    onPlaylistOpenChange = { playlistChoice.value = it },
                    inPip = inPip,
                    pipAvailable = PipMath.canEnter(playback.hasVideo, playlistVisible, pipSupported),
                    onEnterPip = ::enterPip,
                    orientationLocked = locked != null,
                    onToggleOrientationLock = ::toggleOrientationLock,
                    controlsVisible = shown,
                    onToggleControls = ::toggleControls,
                    onHideControls = { controlsShown.value = false },
                    touchLocked = touchLock,
                    onTouchLockChange = ::setTouchLocked,
                    unlockHint = hint,
                    onRevealUnlock = ::revealUnlock,
                    onVideoBounds = { videoBounds.value = it },
                    onOpenWith = ::openWithOtherApp,
                    onClip = ::startClip,
                    onClipPreview = ::previewClip,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FROM_OUTSIDE, fromOutside)
        // 프로세스가 죽은 뒤 다시 설 때를 위해 큐를 적는다([PlayerRestore]). 아직 다시 세우지 못한 것이 있으면 그것을 그대로 —
        // 그때는 플레이어가 비어 있어 큐에서 읽을 것이 없다.
        val key = PlaybackConnection.state.value.fileKey
        if (key != null) pendingRestore = null // 큐가 섰다 — 이제부터는 지금 큐를 적는다.
        val snapshot = if (key != null) {
            itemPaths.queueOf(key)?.let { q -> PlayerRestore.snapshotOf(q.paths, q.index, PlaybackConnection.positionNow()) }
        } else {
            pendingRestore
        }
        snapshot?.let { PlayerRestore.write(outState, it) }
    }

    override fun onDestroy() {
        itemPaths.close()
        super.onDestroy()
    }

    /**
     * 큐 항목 하나([mediaId])의 [startMs, endMs) 를 새 파일로 저장하라고 큐에 넣는다(구간 저장 — [ClipControls]). [audioOnly] 면
     * 소리 트랙만, [endMs] 가 null 이면 끝까지다(⋮ 의 '소리만 저장'). 결과는 [ClipJobs] 가 받아 이 화면이 말한다.
     *
     * @return 넣었으면 그 요청의 신원, 못 넣었으면 그 까닭.
     */
    private fun startClip(mediaId: String, startMs: Long, endMs: Long?, audioOnly: Boolean): ClipStart {
        val path = itemPaths.pathOf(mediaId) ?: return ClipStart.Refused(R.string.player_clip_no_path)
        // 다른 앱이 넘겨 앱 캐시에 둔 사본은 그 곁에 저장해도 사용자가 찾아갈 수 없다(읽기 전용 사본 — `IncomingFiles`).
        if (IncomingFiles.isCopy(this, path)) return ClipStart.Refused(R.string.player_clip_copy)
        // 앱 캐시의 공유 폴더 아래는 **모두** 같다 — 받은 사본이 아닌 것(예전의 네트워크 저장소 사본 `share/remote`)이 위의 검사를
        // 빠져 '저장했습니다' 가 뜨고 파일은 캐시에 남았다. 까닭을 말한다([ClipSource]).
        if (ClipSource.isAppCopy(path, ShareHelper.shareDir(this))) return ClipStart.Refused(R.string.player_clip_app_copy)
        val id = java.util.UUID.randomUUID().toString()
        // 넣기 **전에** 신원을 적는다 — 곧바로 끝난 결과가 먼저 오면 신원이 영영 '기다리는 중' 에 남는다.
        ClipJobs.started(id)
        FileOpManager.enqueue(this, FileOpManager.Request.SaveClip(id, path, startMs, endMs, audioOnly, ClipJobs.sink))
        return ClipStart.Started(id)
    }

    /**
     * 구간 칸이 누르기 전에 말할 것 — 담을 수 있는가, 영상이면 실제로 시작할 키프레임([MediaClipper.preview]). 경로를 찾는 일은 메인
     * 스레드의 컨트롤러가 하고([itemPaths]), 파일을 여는 일은 입출력 스레드에서 한다.
     */
    private suspend fun previewClip(mediaId: String, startMs: Long): MediaClipper.Preview? {
        val path = itemPaths.pathOf(mediaId) ?: return null
        return MediaClipper.preview(path, startMs)
    }

    /**
     * 큐 항목 하나([mediaId])를 **다른 앱으로** 넘긴다. 언제나 고르는 창이다 — 누른 사람이 '다른 앱으로' 라고 말했다.
     *
     * 메인 스레드에서 이 액티비티를 컨텍스트로 부른다(컴포지션의 `LocalContext` 가 곧 이 액티비티다) — 받는 앱의 화면이 우리
     * 태스크 위에 쌓여 뒤로 가면 이 재생 화면으로 돌아온다. 결과는 화면이 우리 문장으로 알린다.
     *
     * ## 넘기는 동안 PiP 자동 진입을 끈다
     *
     * 영상을 트는 중이면 PiP 계획의 자동 진입이 켜져 있고, 그 판정은 **우리가 멈추는 모든 길**에 걸린다([keepPipParamsFresh]
     * 의 주석 — 다른 앱 실행도 그 길이다). 그대로 두면 VLC 가 뜨는 순간 우리 영상이 작은 창으로 떠 **두 재생기가 한 화면에**
     * 선다. 고르는 창은 반투명이라 그것만으로는 걸리지 않을 것이지만(플랫폼 소스로 읽은 추측이다 — 기기에서 확인하지 않았다),
     * 고른 앱이 곧장 뜨는 길(받는 앱이 하나면 고르는 창이 스스로 넘긴다)과 기기마다의 차이를 다 읽을 수는 없다. 그래서 띄우기
     * **전에** 끄고, 띄우지 못했으면 되돌린다. 띄웠으면 곧 멈추므로 흐름이 새로 낸 계획도 자동 진입이 꺼져 있다. 같은 까닭으로
     * [onUserLeaveHint] 가 화면을 미리 접지도 않는다(그것은 [lastPipPlan] 을 읽는다).
     *
     * 파라미터 갱신과 액티비티 시작이 시스템에 **이 차례로 닿는다는 보장은 없다**(서로 다른 바인더다). 이것은 두 번째 방어다.
     */
    private fun openWithOtherApp(mediaId: String): ExternalOpen.Result {
        // PiP 창에서는 우리 UI 를 누를 수 없다. 그래도 넘기는 길 자체가 PiP 를 모르지 않게 한 번 더 막는다.
        if (pipActive.value || isInPictureInPictureMode) return ExternalOpen.Result.FAILED
        val path = itemPaths.pathOf(mediaId) ?: return ExternalOpen.Result.FAILED
        val plan = lastPipPlan
        val holdPip = pipSupported && plan.autoEnter
        if (holdPip) {
            lastPipPlan = plan.copy(autoEnter = false)
            setPictureInPictureParams(paramsOf(lastPipPlan))
        }
        // 우리가 띄우는 앱도 [onUserLeaveHint] 를 부른다 — 떠난 것이 아니다([handingOff]).
        handingOff = true
        val result = ExternalOpen.open(this, path, ExternalOpen.Mode.CHOOSE)
        if (result != ExternalOpen.Result.STARTED) handingOff = false
        if (PlayerOpenWith.pausesAfter(result)) {
            // 같은 파일을 저쪽에서 본다 — 둘이 함께 소리를 내지 않게. 끝내지 않는다(큐·위치·미니 바가 남는다).
            PlaybackConnection.pause()
        } else if (holdPip) {
            // 띄우지 못했다. 흐름은 계획이 바뀔 때만 다시 거므로(`distinctUntilChanged`) 여기서 되돌리지 않으면 자동
            // 진입이 꺼진 채로 남는다.
            lastPipPlan = plan
            setPictureInPictureParams(paramsOf(plan))
        }
        return result
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
        // 거둬 둔 시스템 바도 놓는다 — 방향 요청과 같다. 다시 서면 [holdSystemBars] 의 수집이 지금 값으로 다시 건다.
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        runCatching { unregisterReceiver(pipReceiver) }
    }

    /**
     * 전체화면으로 돌아왔으면 접어 둔 층을 편다.
     *
     * **진입이 실패한 길도 여기서 낫는다.** PiP 는 사용자가 설정에서 막아 두었을 때
     * 예외 없이 그냥 들어가지 않는데, 우리는 첫 프레임을 깨끗하게 만들려고 **부르기 전에**
     * 화면을 접는다. 되돌리는 코드가 없으면 조작부도 제스처도 없는 화면이 남는다.
     * 'PiP 가 아니면 펴 둔다' 는 이 한 줄이 그 모든 길을 덮는다.
     */
    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) pipActive.value = false
        // **진입이 거절된 길이 여기서 낫는다.** 미리 접어 둔 것을 되돌리는 코드가 없으면
        // 조작부도 제스처도 없는 화면이 남는다.
        pipCollapsing.value = false
        // 전체화면으로 되돌아왔다 — 창을 닫은 것이 아니다.
        stoppedWhileInPip = false
        handingOff = false
        // PiP 를 오가면 태스크가 바뀐다(들어가면 제 태스크, 되돌리면 원래 태스크) — 뒤로 가기가 할 일을 다시 본다.
        routeBack()
    }

    /**
     * 사용자가 이 화면을 떠난다 — 홈·최근 앱, 그리고 우리가 띄운 앱.
     *
     * **보낸 앱으로 돌아가는 길을 거둔다**([fromOutside]) — 다음에 런처로 돌아온 사람에게 뒤로 가기는 파일 목록이다. 우리가 다른
     * 앱을 띄운 길([handingOff])은 빼고, 화면이 꺼진 것은 여기 오지 않는다.
     *
     * **API 31 의 자동 PiP(영상을 트는 중에 홈)는 이 콜백을 부르지 않는다** — 모드 콜백만 온다(2026-10-05, 로그로 실측). 그래서
     * PiP 에 들어가는 길은 [onPictureInPictureModeChanged] 가 따로 거둔다. 같은 까닭으로 아래의 '미리 접기' 는 그 길에서 돌지
     * 않는다 — 그 길을 위해 둔 것이었다: 모드 콜백은 애니메이션이 **끝난 뒤** 오고 시작 시점을 알려 주는
     * `PictureInPictureUiState.isTransitioningToPip()` 는 API 35 라, 첫 프레임에 조작부가 찍히지 않게 하려면 우리가 먼저 접어야
     * 했다. 계획이 자동 진입인데 이 콜백이 오는 길이 API 31 에 남아 있는지는 확인하지 않았다(오면 접고, 다시 서면 [onResume] 이
     * 편다). 계획이 자동 진입이 아닐 때는 접지 않는다 — 소리 파일이나 목록을 펼친 상태에서 홈을 눌러도 화면이 공연히 접히면
     * 돌아왔을 때 한 프레임이 비어 보인다.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Iro.d { "사용자가 떠난다 — 자동PiP=${lastPipPlan.autoEnter} 넘기는중=$handingOff" }
        if (lastPipPlan.autoEnter) pipCollapsing.value = true
        if (!handingOff) forgetSender()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) pipCollapsing.value = false
        Iro.d { "PiP=$isInPictureInPictureMode 멈춘뒤=$stoppedWhileInPip" }
        // **PiP 에 들어갔다 = 전체 화면을 떠났다** — 보낸 앱으로 돌아가는 길을 거둔다([fromOutside]). 홈으로 들어가는 자동 PiP 는
        // [onUserLeaveHint] 를 부르지 않는다(API 31 실측). 단추로 들어간 길도 같게 본다 — 작은 창을 띄워 둔 채 다른 데를 다녀온 사람이다.
        if (isInPictureInPictureMode) forgetSender()
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
        // **전체 화면으로 돌아왔으면 시스템 바를 다시 건다.** 계획은 PiP 를 오가며 바뀌지 않아(`distinctUntilChanged`) 흐름이
        // 다시 걸어 주지 않는데, 창이 작은 창에서 전체 화면으로 갈리는 사이 시스템이 바를 다시 보일 수 있다(기기에서 볼 것).
        if (!isInPictureInPictureMode) lastBars?.let(::applyBars)
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
     * 해도 창이 사라진다. 큐가 실어 보내는 종류(`QueueItem.kind`)는
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
     * 누른 순간의 방향을 구체값(`LANDSCAPE`·`PORTRAIT`)으로 묶는다(`toggleOrientationLock`).
     * 잠금은 영상이 아니게 되어도 **풀리지 않는다**:
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
     * 잠금의 `LANDSCAPE`·`PORTRAIT` 는 **고정 방향이다** — 태블릿에서 잠금이 안 들으면
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

    /**
     * **영상이 도는 동안 화면을 끄지 않는다**(기기에서 화면 꺼짐 시간을 15초로 두고 영상을 틀자 32초 뒤
     * 화면이 꺼진 채 소리만 이어졌다. 에뮬레이터의 기본값은 '끄지 않음' 이라 10단계부터 아무도 보지 못했다). 재생은 서비스가 하고
     * 표면은 우리가 직접 붙이므로 `PlayerView` 가 해 주던 `keepScreenOn` 이 없다 — 창의 [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
     * 을 우리가 건다.
     *
     * **소리에는 걸지 않는다** — 화면을 끄고 듣는 것이 정상이다. **멈추면 곧바로 풀지 않는다**([KEEP_SCREEN_GRACE_MS]). `isPlaying`
     * 은 버퍼링하는 동안(큐가 다음 영상으로 넘어갈 때) 잠깐 거짓이 되는데, 그 순간 풀면 마지막으로 손댄 지 오래된 화면이 바로 어두워진다.
     * 방향과 같은 까닭으로 액티비티가 `repeatOnLifecycle(STARTED)` 로 쥔다(함정 표 — 컴포지션은 정지 중에 다시 그려지지 않는다).
     * 창이 보이지 않을 때는 깃발이 뜻이 없어 정지 중에 남아 있어도 해가 없다.
     */
    private fun keepScreenOnWhileVideoPlays() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackConnection.state
                    .map { it.hasVideo && it.isPlaying }
                    .distinctUntilChanged()
                    .collectLatest { playingVideo ->
                        if (playingVideo) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            delay(KEEP_SCREEN_GRACE_MS)
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }
    }

    /**
     * **영상을 보는 동안 시스템 바를 거둔다**(기기에서 잡았다: 1080×2400 에뮬레이터를 가로로 눕히고 조작부를 감춰도
     * 상태 표시줄이 영상 위에 겹쳤고 내비게이션 바가 밝은 회색 띠로 남았다). 언제 거두는지는 [PlayerChrome.barsHidden] 이고, 거는 일은
     * 이 액티비티가 수명에 묶어 한다 — 방향 잠금과 같은 까닭이다(불변식 2: 정지한 화면은 다시 그려지지 않아 컴포저블이 쥐면 보이지 않는
     * 화면이 바를 붙든다). 놓는 것은 [onStop], PiP 에서 돌아올 때 다시 거는 것은 [onPictureInPictureModeChanged] 다.
     *
     * 영상이 보이는 동안에는 **바의 색도 어둡게** 둔다 — 상태 표시줄 아이콘은 테마(`IroiroTheme(forceDark)`)가 밝게 두지만, 3버튼
     * 내비게이션의 바탕은 시스템이 밝은 모드를 따라 흰 띠를 깐다(`enableEdgeToEdge` 의 기본은 '시스템을 따른다'). 두 값 모두 테마와
     * **같은 식**(영상이 보이고 목록이 닫혔다)을 쓴다 — 어긋나면 목록은 밝은데 바만 검거나 그 반대가 된다(10단계가 조작부로 겪은 모양).
     *
     * 조작부의 판은 바가 드나들어도 출렁이지 않게 `…IgnoringVisibility` 인셋을 쓴다([PlayerScreen]).
     */
    private fun holdSystemBars() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    PlaybackConnection.state,
                    playlistRequest,
                    playlistChoice,
                    controlsShown,
                    touchLocked,
                ) { s, req, choice, shown, locked ->
                    val showingVideo = s.hasVideo && !playlistShown(s, req, choice)
                    BarsPlan(dark = showingVideo, hidden = PlayerChrome.barsHidden(showingVideo, shown, locked))
                }.distinctUntilChanged().collect(::applyBars)
            }
        }
    }

    private fun applyBars(plan: BarsPlan) {
        lastBars = plan
        if (plan.dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
        } else {
            enableEdgeToEdge()
        }
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (plan.hidden) {
            // 가장자리를 밀면 잠깐 보였다가 저절로 들어간다 — 큰 재생기들의 가로 전체 화면과 같다.
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 시스템 바의 계획 한 벌 — 값이 실제로 바뀔 때만 시스템을 부르게 `data class` 다([PipPlan] 과 같다). */
    private data class BarsPlan(val dark: Boolean, val hidden: Boolean)

    /**
     * **되살아난 화면이 빈 채로 남지 않게 한다**(기기에서 잡았다: 재생하던 앱이 죽은 뒤 안드로이드가 이 화면을 저장
     * 상태로 되살리면 '0:00 / 0:00' 에 제목도 없고 재생 단추가 듣지 않았다. 뒤로 가기만 파일 목록으로 갔다).
     *
     * 재생은 서비스에 있고 서비스는 프로세스와 함께 죽어 큐가 없다. 붙고 나서 플레이어가 비어 있으면 — 프로세스가 새로 섰다 — 적어 둔
     * 큐([PlayerRestore])를 **멈춘 채, 그 자리에서** 다시 세운다(`PlaybackConnection.restore`). 비어 있지 않으면 프로세스가 살아 있던
     * 것(밤 모드를 바꿔 다시 섰다)이라 아무것도 하지 않는다. 다시 세울 것이 없으면 — 적어 둔 것이 없다·지금 항목이 사라졌다·네트워크
     * 저장소에서 흘려 읽던 것이라 연결이 없다 — 알리고 파일 목록(그 폴더)으로 간다([leaveEmptyScreen]).
     */
    private fun restoreIfEmpty() {
        lifecycleScope.launch {
            val connected = withTimeoutOrNull(RESTORE_CONNECT_TIMEOUT_MS) {
                PlaybackConnection.state.first { it.connected }
            }
            if (connected?.hasItem == true) {
                pendingRestore = null
                return@launch
            }
            val saved = pendingRestore
            val restored = saved?.let { s -> withContext(Dispatchers.IO) { PlayerRestore.load(s) } }
            if (saved == null || restored == null) {
                leaveEmptyScreen(saved?.folder)
                return@launch
            }
            Iro.d { "되살아난 재생 화면 — 큐 ${restored.plan.items.size}개를 멈춘 채 다시 세운다" }
            PlaybackConnection.restore(this@PlayerActivity, restored.plan, saved.positionMs, restored.subtitles)
        }
    }

    /**
     * 다시 세울 큐가 없다 — 빈 재생 화면을 남기지 않는다. 알리고, 뒤로 가기와 같은 길로 파일 목록에 간다(밑에 파일 목록이 있으면 끝내기만,
     * 뿌리면 그 폴더로 연다 — [goBack]). 폴더는 기기 저장소의 것일 때만이다(앱 캐시의 사본이면 첫 화면 — [playingFolder] 와 같은 규칙).
     *
     * 알림은 **토스트**다 — 이 화면은 곧 사라지고 파일 목록은 다른 액티비티라, 이 화면 안의 안내 자리는 아무도 보지 못한다.
     */
    private fun leaveEmptyScreen(folder: String?) {
        Iro.d { "되살아난 재생 화면에 다시 세울 큐가 없다 — 파일 목록으로" }
        Toast.makeText(this, R.string.player_restore_failed, Toast.LENGTH_LONG).show()
        if (isTaskRoot) PlayerSupport.openFileList(this, folder?.takeIf { it.startsWith("/storage/") })
        finish()
    }

    private companion object {
        /** 되살아난 화면이 재생 서비스에 붙기를 기다리는 시간. 넘으면 다시 세우지 못한 것으로 본다([restoreIfEmpty]). */
        const val RESTORE_CONNECT_TIMEOUT_MS = 5_000L

        /** 영상이 멈춘 뒤 화면 켜 둠을 풀기까지(위 [keepScreenOnWhileVideoPlays]). 큐가 다음 영상을 준비하는 틈보다 넉넉하다. */
        const val KEEP_SCREEN_GRACE_MS = 3_000L

        /** 저장 상태에 든 [fromOutside] — 거둔 뒤에 다시 선 화면이 처음 인텐트로 그것을 되살리지 않게. */
        const val KEY_FROM_OUTSIDE = "player.fromOutside"

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
    /**
     * 조작부가 떠 있는가 — **액티비티가 든다**(시스템 바가 같은 값을 따른다 — 불변식 2). **조작부는 영상일 때만 숨긴다**(아래
     * `showControls`) — 소리만 나는 화면에서 막대까지 감추면 아무것도 없는 빈 화면이 되고, 그때 다시 꺼내는 방법을 알 길이 없다.
     */
    controlsVisible: Boolean,
    /** 두드림이 조작부를 여닫는다. 값이 아니라 함수인 것은 몸짓 인식기가 처음 붙을 때 람다를 잡기 때문이다 — 값을 잡으면 낡는다. */
    onToggleControls: () -> Unit,
    /** 조작부를 거둔다 — 저절로 거두기([PlayerChrome.AUTO_HIDE_MS]). */
    onHideControls: () -> Unit,
    /** 터치 잠금 중인가. 그동안은 조작부도 몸짓도 없고, 두드리면 잠금 풀기 단추만 뜬다([TouchLockLayer]). */
    touchLocked: Boolean,
    onTouchLockChange: (Boolean) -> Unit,
    /** 잠금 풀기 단추를 띄우라는 요청의 번호(두드림·뒤로 가기 — 액티비티가 센다). */
    unlockHint: Long,
    onRevealUnlock: () -> Unit,
    onVideoBounds: (Rect) -> Unit,
    /** 큐 항목(`mediaId`)을 다른 앱으로 넘긴다. 결과를 이 화면이 알린다. */
    onOpenWith: (String) -> ExternalOpen.Result,
    /**
     * 큐 항목(`mediaId`)의 구간을 새 파일로 저장하라고 넣는다 — (항목, 시작, 끝 — null 이면 끝까지, 소리만인가). 넣었으면 그 요청의
     * 신원, 못 넣었으면 그 까닭.
     */
    onClip: (String, Long, Long?, Boolean) -> ClipStart,
    /** 큐 항목의 구간을 미리 잰다(담을 수 있는가, 영상이면 실제 시작). 경로를 못 찾으면 null. */
    onClipPreview: suspend (String, Long) -> MediaClipper.Preview?,
) {
    val state by PlaybackConnection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    // 배속 눈금을 펼쳤는가. **조작부 안에서** 펼친다 — 판이 그만큼 높아지고, 위에 뜨는
    // 안내들은 잰 높이를 따라 저절로 올라간다([controlsHeight]).
    var speedOpen by remember { mutableStateOf(false) }
    var trackSheetOpen by remember { mutableStateOf(false) }
    // A-B 단추를 누른 결과를 잠깐 띄운다. 거절(`TooShort`)을 말할 자리가 여기뿐이다.
    var abNote by remember { mutableStateOf<AbRepeat.Result?>(null) }
    // 화면 속 화면에 들어가지 못했다(사용자가 껐거나 다른 PiP 가 떠 있다).
    var pipFailed by remember { mutableStateOf(false) }
    // 다른 앱으로 넘기지 못했다 — 알릴 **우리 문장**(`ExternalOpen.messageOf`). 이 화면에는 스낵바가 없고(앱의 것은 다른
    // 액티비티에 있다) 방금 누른 것의 답은 아래 '잠깐 뜨는 안내' 자리가 말한다 — PiP 실패와 같은 자리다.
    var handoffNote by remember { mutableStateOf<Int?>(null) }
    // 구간 저장의 결과 한 줄(`FileOpText` — 그 작업을 만든 `core:io` 의 문장). 같은 자리('잠깐 뜨는 안내')에 뜬다.
    var clipNote by remember { mutableStateOf<String?>(null) }
    // 펼친 구간 칸. null 이면 닫혀 있다. 항목이 바뀌면 닫는다 — 다른 파일의 시각으로 저장하지 않게.
    var clip by remember { mutableStateOf<ClipRange?>(null) }
    LaunchedEffect(state.fileKey) { if (clip?.key != state.fileKey) clip = null }
    // 이 화면이 넣은 저장 가운데 아직 끝나지 않은 것(줄 서 있는 것 포함) — 진행 줄을 보이고 저장 단추를 끈다. 큐의 진행은 진행 줄이
    // 스스로 읽는다([ClipProgress]) — 여기서 들면 다른 작업의 진행마다 화면 전체가 다시 그려진다.
    val clipPending by ClipJobs.pending.collectAsStateWithLifecycle()
    // 고른 시작이 바뀌면 다시 잰다 — 끌어 옮기는 동안 여러 번 열지 않게 잠깐 기다린다.
    var clipPreview by remember { mutableStateOf<MediaClipper.Preview?>(null) }
    LaunchedEffect(clip?.key, clip?.startMs) {
        val c = clip
        if (c == null) {
            clipPreview = null
            return@LaunchedEffect
        }
        delay(CLIP_PREVIEW_DELAY_MS)
        clipPreview = onClipPreview(c.key, c.startMs)
    }
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
    // 터치 잠금 중에는 무엇이든 거둔다 — 목록이 펼쳐져 있거나 소리 항목이어도(잠그는 단추는 영상이 보일 때만 있다).
    val showControls = !touchLocked && (controlsVisible || !state.hasVideo || playlistOpen)

    // **영상이 실제로 보이고 있는가.** 목록이 열려 있으면 영상은 가려져 있으므로 그때는
    // 영상 화면이 아니다 — 배경·글자색·조작부의 판까지 모두 이 하나로 갈린다.
    // 액티비티가 테마를 고르는 조건과 **같은 식**이어야 한다. 어긋나면 목록은 밝은데
    // 조작부만 어두운 그림이 나온다.
    val showingVideo = state.hasVideo && !playlistOpen

    // **두 번 두드림의 걸음** — 설정(`AppPreferences.tapSeekSeconds`, 기본 10초). 처음 값을 기본으로 두고 읽는 대로 바꾼다.
    val prefs = remember(context) { AppPreferences(context.applicationContext) }
    val tapSeekSeconds by prefs.tapSeekSeconds.collectAsStateWithLifecycle(AppPreferences.DEFAULT_TAP_SEEK_SECONDS)

    // **조작부를 저절로 거둔다**(기기에서 잡았다: 재생 12초 뒤에도 그대로였다). 거둘 수 있는 동안 [PlayerChrome.AUTO_HIDE_MS]
    // 를 재고, 무엇이든 하는 중이면(손가락이 닿아 있다·막대를 끈다·메뉴·시트·배속 눈금·구간 칸이 열려 있다·저장이 돈다) 멈췄다가 끝나면
    // 처음부터 다시 잰다 — 효과의 키가 바뀌면 앞의 기다림이 취소되는 것이 곧 '만지면 시계를 다시 잰다' 다. 판정은 [PlayerChrome].
    var touching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val touchExploration = rememberTouchExploration()
    val autoHide = controlsVisible && !touchLocked && PlayerChrome.autoHideAllowed(
        showingVideo = showingVideo,
        playing = state.isPlaying,
        inPip = inPip,
        scrubbing = scrubbing != null,
        panelOpen = speedOpen || trackSheetOpen || clip != null || menuOpen || clipPending.isNotEmpty(),
        touching = touching,
        touchExploration = touchExploration,
    )
    LaunchedEffect(autoHide) {
        if (autoHide) {
            delay(PlayerChrome.AUTO_HIDE_MS)
            onHideControls()
        }
    }

    // **조작부가 떠 있는 동안 자막을 판 위로 올린다**(가로 영상에서 판이 자막을 덮었다). 판이 영상의 아래쪽을 얼마나
    // 덮는지는 잰다 — 화면 높이, 영상 상자의 아래 끝, 판의 높이(불변식 3 — 상수로 맞히지 않는다). 세로 화면처럼 영상이 판보다 위에서
    // 끝나면 덮는 것이 없어 0 이다. 판이 사라지면 제자리로 내려온다(움직임은 짧게 — 글자가 순간이동하면 읽던 줄을 놓친다).
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var videoBottomPx by remember { mutableFloatStateOf(0f) }
    val coveredPx = if (showControls && controlsMeasured && showingVideo) {
        (videoBottomPx - (rootHeightPx - with(density) { controlsHeight.toPx() })).coerceAtLeast(0f)
    } else {
        0f
    }
    val subtitleLift by animateDpAsState(with(density) { coveredPx.toDp() }, label = "subtitleLift")
    // **낭독기용 문구는 미리 만들어 둔다.** `Modifier.semantics` 의 람다는 컴포저블이
    // 아니라 그 안에서 `stringResource` 를 부를 수 없다.
    val speedDescription = speedLabel(state.speed)
    val abDescription = abLabel(state.abSpan)
    val abText = abSymbol()
    val background = if (showingVideo) Color.Black else MaterialTheme.colorScheme.background
    val onBackground = if (showingVideo) Color.White else MaterialTheme.colorScheme.onSurface

    // 저장이 끝났다 — **이 화면이 앞에 있는 동안만** 결과를 받아 하나씩 말한다([ClipJobs.listening]). 뒤에 있는 동안 끝난 것은 앱의
    // 결과 소비자(파일 목록)가 말한다 — 가려진 화면이 받으면 아무도 못 보는 자리에 떴다 사라진다(함정 표의 '값을 지우는 소비자를 …').
    // 잇달아 끝난 둘은 차례로 — 앞의 것을 읽을 시간을 준 뒤에 다음 것을 띄운다. 구간을 저장했으면 칸을 닫는다(실패면 열어 두어 고쳐
    // 다시 누르게. 소리만 저장했으면 열어 둔다 — 같은 구간의 영상도 저장할 수 있다).
    //
    // **칸은 자기가 넣은 요청의 결과에만 닫는다**([panelRequest]) — 다른 파일에서 넣어 둔 긴 저장이 끝났다고 지금 파일의 칸이 닫히면, 그
    // 사이 고른 구간이 말없이 사라진다. 화면을 떠나면 아직 말하지 못한 결과를 앱의 결과 줄로 돌려보낸다
    // ([ClipJobs.stopListening]) — 다음에 아무 영상이나 열 때 맥락 없이 뜨지 않게.
    var panelRequest by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ClipJobs.startListening()
            try {
                while (true) {
                    val f = ClipJobs.next()
                    clipNote = FileOpText.savedMessage(context, f)
                    noteSeq++
                    if (f.outcome is FileOpEngine.Outcome.Done && f.kind == FileOpManager.Kind.CLIP && f.id == panelRequest) clip = null
                    delay(PlayerOpenWith.HANDOFF_NOTE_HOLD_MS)
                }
            } finally {
                ClipJobs.stopListening()
            }
        }
    }
    // 넣었으면 그 신원, 못 넣었으면 까닭을 잠깐 띄우고 null.
    val startSave: (String, Long, Long?, Boolean) -> String? = { key, start, end, audioOnly ->
        when (val r = onClip(key, start, end, audioOnly)) {
            is ClipStart.Started -> r.id
            is ClipStart.Refused -> {
                handoffNote = r.why
                noteSeq++
                null
            }
        }
    }
    // 칸에서 끝을 미디어의 끝으로 골랐으면 '끝까지'(null) 로 보낸다 — 재생 화면의 길이는 트랙의 끝보다 몇 ms 짧을 수 있어 그 값을
    // 넣으면 끝 프레임을 잃는다(⋮ 의 '소리만 저장' 과 같은 까닭).
    val endOf: (ClipRange) -> Long? = { c -> if (state.durationMs > 0 && c.endMs >= state.durationMs) null else c.endMs }
    val saveClip: () -> Unit = {
        clip?.takeIf { it.valid }?.let { c -> panelRequest = startSave(c.key, c.startMs, endOf(c), false) }
    }
    val saveClipAudio: () -> Unit = {
        clip?.takeIf { it.valid }?.let { c -> panelRequest = startSave(c.key, c.startMs, endOf(c), true) }
    }

    // 다른 앱으로 넘긴다. 띄웠으면 말할 것이 없고(STARTED), 못 띄웠으면 우리 문장을 잠깐 띄운다.
    val openWith: (String) -> Unit = { key ->
        ExternalOpen.messageOf(onOpenWith(key))?.let {
            handoffNote = it
            noteSeq++
        }
    }

    // **조작부를 Column 의 한 칸이 아니라 위에 겹친다.**
    //
    // 칸으로 두면 조작부가 나타날 때마다 위 칸이 좁아져 **영상이 줄어들고 자리가 움직인다.**
    // 눈에는 "화면이 흔들린다" 로 보이고 무엇이 바뀐 것인지 읽어 내기 어렵다. 겹쳐 두면
    // 바뀌는 것은 조작부의 유무 하나뿐이다.
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .onSizeChanged { rootHeightPx = it.height }
            // **손가락이 화면에 닿아 있는 동안은 조작부를 저절로 거두지 않는다**(위 `autoHide`) — 누르고 있는 단추·끄는 막대가 손
            // 밑에서 사라지지 않게. 손을 떼면 그때부터 다시 잰다. **Initial 단계에서 보기만 한다**(소비하지 않는다) — 슬라이더·
            // 두드림·끌기는 그대로 받는다(함정 표 — 모든 변화를 소비하면 슬라이더의 드래그가 죽는다). 바뀔 때만 적는다 — 움직일
            // 때마다 적으면 끄는 내내 화면 전체가 다시 그려진다.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.any { it.pressed }
                        if (down != touching) touching = down
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // **목록이 열려 있으면 제스처를 끈다.** 목록이 스크롤을 먹어야 한다.
                // PiP 창에서는 애초에 우리 UI 를 만질 수 없다(시스템이 막는다). 터치 잠금 중에도 끈다 — 그것이 잠금이다.
                .playerGestures(
                    enabled = !playlistOpen && !inPip && !touchLocked,
                    durationMs = state.durationMs,
                    tapSeekMs = tapSeekSeconds * 1_000L,
                    positionMs = { PlaybackConnection.state.value.positionMs },
                    onToggleControls = onToggleControls,
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
                // **`fillMaxWidth` 를 앞에 두지 않는다**. 폭을 화면에 못 박으면 `aspectRatio` 가 높이에
                // 맞추는 길을 잃어, 20:9 폰을 가로로 눕힌 화면(2400×1080)에서 16:9 영상이 2400×1350 이 되어 위아래가 잘렸다 — 그 상자 안의
                // 자막도 함께 잘린다. `aspectRatio` 만 두면 폭에 맞춰 보고 넘치면 높이에 맞춘다(부모 `Box` 는 최소 크기를 0 으로 넘긴다).
                //
                // **자막을 이 상자 안에 넣는다.** 밖에 두면 세로 영상 아래의 빈 여백에
                // 자막이 뜨고, 영상과 글이 멀어져 함께 보기 어렵다.
                Box(
                    modifier = (
                        if (state.videoAspect > 0f) {
                            Modifier.aspectRatio(state.videoAspect)
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
                            // 자막을 판 위로 올릴 높이를 재는 데 쓴다(위 `subtitleLift`). 판의 높이와 같은 좌표계(루트)로 잰다.
                            videoBottomPx = coords.boundsInRoot().bottom
                        },
                ) {
                    VideoSurface(Modifier.fillMaxSize())
                    // PiP 창에는 **영상 말고 아무것도 두지 않는다**(공식 문서). 작은 창에
                    // 자막을 얹으면 글자가 뭉개져 읽히지도 않는다.
                    if (!inPip) SubtitleLayer(bottomLift = subtitleLift)
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
        // 소비(`consumeFailure`)는 하지 않는다 — 나가면 목록이 한 번 더 말해 주고, 새로
        // 틀면 `PlaybackConnection` 이 스스로 지운다. 큐 안에서 다음 곡으로 넘어가면 커넥션이
        // 앞 곡의 실패를 버린다(`State.carriedFailure`) — 여기 뜨는 실패는 언제나 지금 곡의 것이다.
        if (inPip) return@Box
        state.failure?.let { failure ->
            // **이 앱이 못 트는 것이면 곁에 '다른 앱으로 열기'.** 넘기는 것은 지금 항목이다 — 실패와 항목을 같은 상태 한
            // 벌에서 읽으므로 둘이 어긋나지 않는다([PlayerOpenWith.failureTarget]).
            val handoff = PlayerOpenWith.failureTarget(failure, state.fileKey)
            // **글과 단추를 한 줄에 놓는다.** 세로로 쌓으면 판이 단추 높이만큼 위로 자라, 가로 폰에서 조작부 판 +
            // 이어보기 자리 위에 얹힌 이 판의 윗부분이 화면 밖으로 밀린다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 조작부 판 위로, 그리고 이어보기 제안보다 한 칸 더 위로.
                    // 둘이 동시에 뜨는 일은 드물지만 겹쳐 읽히면 둘 다 못 읽는다.
                    .padding(bottom = aboveControls + OFFER_HEIGHT)
                    .background(Color(0xC0000000))
                    .padding(
                        start = 16.dp,
                        end = if (handoff != null) 8.dp else 16.dp,
                        top = if (handoff != null) 4.dp else 12.dp,
                        bottom = if (handoff != null) 4.dp else 12.dp,
                    ),
            ) {
                Text(
                    text = playbackFailureText(failure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (handoff != null) {
                    // 판은 테마와 상관없이 늘 검다 — 단추 색도 테마에서 가져오지 않는다. 목록을 펼친 밝은 테마에서
                    // `primary` 는 어둡고, 영상의 어두운 테마에서 `inversePrimary` 는 어둡다. 흰 글자면 둘 다에서 읽힌다.
                    TextButton(
                        onClick = { openWith(handoff) },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.White),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
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
        // 다른 앱으로 넘기지 못한 것도 여기로 온다 — 방금 누른 것에 대한 답이다.
        // 저장의 결과는 몇 초 전에 누른 것의 답이라 맨 뒤다 — 방금 누른 것(A-B·넘기기)의 답을 가리지 않는다.
        val noteText = when {
            handoffNote != null -> stringResource(handoffNote!!)
            pipFailed -> pipFailedText()
            abNote != null -> abResultText(abNote!!)
            clipNote != null -> clipNote
            else -> null
        }
        if (controlsMeasured && noteText != null) {
            LaunchedEffect(noteSeq) {
                val mine = noteSeq
                // 넘기지 못한 까닭은 문장이라 읽을 시간을 스낵바만큼 준다([PlayerOpenWith.HANDOFF_NOTE_HOLD_MS]).
                delay(if (handoffNote != null || clipNote != null) PlayerOpenWith.HANDOFF_NOTE_HOLD_MS else NOTE_HOLD_MS)
                // **그사이 새 안내가 왔으면 지우지 않는다.** 기다림이 끝나는 프레임에 새 안내가 들어오면 이 효과가 취소되기 전에
                // 새것까지 지워, 잇달아 끝난 둘째 결과가 한 번도 그려지지 않았다.
                if (noteSeq == mine) {
                    abNote = null
                    pipFailed = false
                    handoffNote = null
                    clipNote = null
                }
            }
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                // **두 줄까지 접는다.** 넘기지 못한 까닭('파일이 없거나, 다른 앱에 넘길 수 없는 위치에 있습니다')은 360dp
                // 폰에서 보통 글꼴로도 한 줄을 넘는다. 한 줄로 못 박으면 문장의 끝이 말없이 잘린다.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 알약이 화면 끝에 붙지 않게. 접힌 두 줄이 가장자리까지 번지면 알약으로 읽히지 않는다.
                    .padding(horizontal = 16.dp)
                    .padding(bottom = aboveControls)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    // 화면 낭독기가 뜨는 안내를 읽는다(저장의 결과 같은 것은 누른 지 몇 초 뒤에 온다).
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
        // 통째로 빠져, 제스처까지 꺼 둔 상태에서는 **되돌릴 수 없는 화면**이 남는다.
        // 시트는 자기 창이라 여기 있어도 자리를 차지하지 않는다.
        if (trackSheetOpen) TrackSheet(onDismiss = { trackSheetOpen = false })

        // **터치 잠금은 맨 위에 깐다** — 실패 문구·안내 알약의 단추까지 손이 닿지 않게. 두드리면 잠금 풀기 단추만 잠깐 뜬다.
        if (touchLocked && !inPip) {
            TouchLockLayer(hint = unlockHint, onTap = onRevealUnlock, onUnlock = { onTouchLockChange(false) })
            return@Box
        }

        if (!showControls) return@Box

        // **가운데 큰 재생/일시정지 단추**(큰 재생기의 관행). 영상이 보일 때만, 판 위의 남은 자리 가운데에 — 판이
        // 덮는 높이를 빼고 가운데에 두어야 가로 화면에서 판과 겹치지 않는다(불변식 3 — 잰 높이를 쓴다). 아래 줄의 단추는 그대로
        // 둔다 — 소리 화면에는 이 단추가 없고, 이전·다음과 나란히 있는 자리를 찾는 손도 있다.
        if (showingVideo) {
            Box(Modifier.fillMaxSize().padding(bottom = controlsHeight), contentAlignment = Alignment.Center) {
                CenterPlayButton(playing = state.isPlaying, onClick = { PlaybackConnection.togglePlayPause() })
            }
        }

        // **조작부 아래에 반투명한 판을 깐다.** 영상 위에 흰 글자만 얹으면 밝은 장면에서
        // 글자와 단추가 사라진다. 위로 갈수록 옅어지는 그라데이션이라 영상을 통째로
        // 가리지도 않는다. 영상이 아니면 테마의 표면색으로 덮는다.
        Box(
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
                .onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }
        ) {
            // **여기서 탭을 삼킨다.** 이 판에는 포인터를 잡는 수정자가 하나도
            // 없어서, 단추가 아닌 빈 자리(제목 줄·시간 표시 옆)를 누르면 Compose 가
            // 형제를 더 내려가 **판 뒤에 깔린 재생목록 줄**을 눌러 버렸다. 사용자는
            // 아무것도 누르지 않았는데 곡이 바뀐다. `indication = null` 이라 눌린
            // 표시는 나지 않는다. **내용 아래의 형제**에 단다 — 판(부모)에 달면 `clickable` 이 자손의 의미를 합쳐 화면 낭독기가 판
            // 전체(구간 칸의 안내·진행 포함)를 눌러도 아무 일 없는 단추 하나로 읽었다(함정 표).
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clearAndSetSemantics {}
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    // **판의 위쪽에는 인셋을 두지 않는다**(화면 아래에 붙은 판이 상태 표시줄 높이만큼 위 여백을 가져
                    // 220dp 가운데 72dp 가 인셋이었다). 아래와 양옆만 피한다. 그리고 **바가 보이든 거둬졌든 같은 자리를 비운다**
                    // (`…IgnoringVisibility`) — 조작부와 바가 함께 드나드는데, 인셋이 따라 바뀌면 판이 그때마다 출렁인다.
                    .windowInsetsPadding(controlsInsets())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            ) {
                // **뒤로 단추를 판 왼쪽 위에, 제목 곁에 둔다**(VLC·MX·YouTube 의 관행). 시스템 바를 거두면 3버튼
                // 내비게이션에서는 바를 끌어내야만 뒤로 갈 수 있다. 하는 일은 시스템의 뒤로 가기와 같다([PlayerActivity.goBack]).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.player_back),
                            tint = onBackground,
                        )
                    }
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                val fraction = scrubbing ?: if (state.durationMs > 0) {
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else 0f
                // **막대를 끄는 동안에는 갈 자리를 보인다**(시간 글이 손을 뗄 때까지 지금 자리에 머물러, 구간 저장에서
                // 자리를 찾을 때 특히 불편했다). 몸짓의 끌기가 '1:15 (+0:30)' 을 보이는 것과 같은 일을 막대도 한다.
                val shownMs = scrubbing?.let { (it * state.durationMs).toLong() } ?: state.positionMs
                // **화면 낭독기에게 이름과 값을 준다**(백분율로만 읽혔다). `IroSlider` 는 그것을 부르는 쪽에 맡긴다.
                val seekName = seekBarLabel()
                val seekValue = seekBarState(shownMs, state.durationMs)
                // **YouTube·VLC 의 모양**(얇은 트랙 + 둥근 점, 끝점 표시 없음) — 이 막대에서 세운 모양을 앱의 모든 슬라이더가 쓰도록
                // `core:ui` 의 [IroSlider] 로 옮겼다. 앞서 Material 3 의 기본 손잡이(세로로 긴 알약)를 얇은 막대로 바꿨더니
                // "익숙한 형태가 아니라 어색하다" 는 지적이 왔다 — 재생기의 관행은 하나로 모여 있다.
                //
                // 값은 위에서 계산한 [fraction] 이다 — 끄는 동안에는 `scrubbing` 이 손가락을 따라간다.
                IroSlider(
                    modifier = Modifier.semantics {
                        contentDescription = seekName
                        stateDescription = seekValue
                    },
                    value = fraction,
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { PlaybackConnection.seekTo((it * state.durationMs).toLong()) }
                        scrubbing = null
                    },
                    // 남은 쪽. 배경이 검든 희든 보이도록 글자색을 옅혀 쓴다.
                    inactiveColor = onBackground.copy(alpha = 0.28f),
                    trackOverlay = {
                        // **찍어 둔 구간을 막대에 보인다.** 숫자로만 말하면 '어디쯤을 되풀이하고 있는가' 가 머릿속에 그려지지 않는다.
                        state.abSpan?.takeIf { state.durationMs > 0 }?.let { span ->
                            AbMarker(maxWidth, span.aMs, state.durationMs)
                            span.bMs?.let { AbMarker(maxWidth, it, state.durationMs) }
                        }
                        // 저장할 구간도 같은 막대에 띠로 — 끌어서 자리를 찾는 일과 고른 구간이 한 눈에 보여야 한다.
                        clip?.let { ClipBand(maxWidth, it.startMs, it.endMs, state.durationMs) }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${formatTime(shownMs)} / ${formatTime(state.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            // 끄는 동안에는 강조색 — 지금 자리가 아니라 갈 자리라는 것을 글자색으로도 가른다.
                            color = if (scrubbing != null) MaterialTheme.colorScheme.primary else onBackground,
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
                    // 그림은 **도는 폰**이다 — 자물쇠는 아래의 터치 잠금에 준다. VLC 를 쓰던 사람은 자물쇠를 터치
                    // 잠금으로 읽는다([RotationLockIcon]).
                    if (state.hasVideo || orientationLocked) {
                        IconButton(onClick = onToggleOrientationLock) {
                            Icon(
                                imageVector = RotationLockIcon,
                                contentDescription = orientationLockLabel(orientationLocked),
                                tint = if (orientationLocked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    onBackground
                                },
                            )
                        }
                    }

                    // **터치 잠금**(큰 재생기의 관행). 영상이 보일 때만 — 소리 화면에서는 손바닥이 닿아도 망칠 것이
                    // 막대 하나라 잠글 까닭이 작다. 켜면 조작부가 사라지고, 화면을 두드리면 잠금 풀기 단추가 잠깐 뜬다([TouchLockLayer]).
                    if (showingVideo) {
                        IconButton(onClick = { onTouchLockChange(true) }) {
                            Icon(Icons.Filled.Lock, stringResource(R.string.player_touch_lock), tint = onBackground)
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

                    // **⋮ — 이 재생에 대한 드문 조작.** 지금은 '다른 앱으로 열기' 하나다. 줄 끝에 둔다: 자주 누르는 것이
                    // 앞에 있어야 좁은 폰에서 가로 스크롤 없이 닿는다. 단추와 메뉴는 **한 상자에** 담는다 — `Popup` 은
                    // 감싼 부모를 앵커로 삼아, 형제로 두면 메뉴가 줄의 맨 앞에서 열린다(함정 표).
                    // 지금 항목이 파일에 닿지 못해 실패했으면 서지 않는다 — 받는 앱도 그 파일에 닿지 못한다.
                    PlayerOpenWith.menuTarget(state.fileKey, inPip, state.failure)?.let { key ->
                        PlayerMoreMenu(
                            tint = onBackground,
                            // 펼쳐 둔 동안은 조작부를 저절로 거두지 않는다(위 `autoHide`) — 메뉴는 따로 된 창이라 손가락을 볼 수 없다.
                            onExpandedChange = { menuOpen = it },
                            onOpenWith = { openWith(key) },
                            // 구간 저장 — 길이를 아는 항목만. 처음 구간은 찍어 둔 A-B, 없으면 지금 자리부터 끝까지.
                            onClip = if (state.durationMs > 0) {
                                {
                                    val span = state.abSpan
                                    val dur = state.durationMs
                                    clip = ClipRange(
                                        key = key,
                                        startMs = (span?.aMs ?: PlaybackConnection.positionNow()).coerceIn(0, dur),
                                        endMs = (span?.bMs ?: dur).coerceIn(0, dur),
                                    )
                                }
                            } else {
                                null
                            },
                            // 소리만 저장 — 영상의 소리 트랙 전체(구간을 고르려면 '구간 저장' 칸의 '소리만 저장'). 저장이 줄 서 있으면 끈다.
                            onSaveAudio = if (state.hasVideo && clipPending.isEmpty()) {
                                { startSave(key, 0, null, true) }
                            } else {
                                null
                            },
                        )
                    }
                }

                // **구간 칸은 조작부 안에서 펼친다**(배속 눈금과 같다 — 판이 자라고 위의 안내들이 따라 올라간다).
                clip?.let { c ->
                    ClipControls(
                        range = c,
                        // 컨트롤러에서 곧바로 — 상태의 위치는 500 ms 늦을 수 있다.
                        positionMs = { PlaybackConnection.positionNow() },
                        durationMs = state.durationMs,
                        hasVideo = state.hasVideo,
                        preview = clipPreview,
                        saving = clipPending.isNotEmpty(),
                        tint = onBackground,
                        onChange = { clip = it },
                        onPreview = {
                            PlaybackConnection.seekTo(c.startMs)
                            PlaybackConnection.resume()
                        },
                        onSave = saveClip,
                        onSaveAudio = if (state.hasVideo) saveClipAudio else null,
                        onClose = { clip = null },
                    )
                }
                if (clipPending.isNotEmpty()) ClipProgress(clipPending, onBackground)
            }
        }
    }
}

/**
 * 조작부 ⋮ 단추와 그 메뉴. **펼침 상태를 이 안에 든다** — 단추가 사라지면(지금 항목이 파일에 닿지 못해 실패했다·PiP·조작부를
 * 감췄다) 상태도 함께 사라진다. 화면 쪽에 두었더니 메뉴를 펼친 채 단추가 사라진 뒤 다시 설 때 메뉴가 **저 혼자 펼쳐졌다** —
 * 그 사이 항목이 바뀌었으면 누르지도 않은 메뉴가 다른 파일을 넘기는 줄을 들이민다. 만화 뷰어의 ⋮ 와 같은 모양이다.
 *
 * 단추와 메뉴는 **한 상자에** 담는다(함정 표의 `Popup` 앵커).
 *
 * 펼침 상태는 여기 든 채 **밖에 알리기만** 한다([onExpandedChange] — 펼쳐 둔 동안 조작부를 저절로 거두지 않게). 이 상자가
 * 컴포지션에서 빠지면 '닫혔다' 를 한 번 더 알린다 — 그러지 않으면 펼친 채 빠진 메뉴가 저절로 거두기를 영영 막는다.
 */
@Composable
private fun PlayerMoreMenu(
    tint: Color,
    onExpandedChange: (Boolean) -> Unit,
    onOpenWith: () -> Unit,
    onClip: (() -> Unit)?,
    onSaveAudio: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { onExpandedChange(open) }
    DisposableEffect(Unit) { onDispose { onExpandedChange(false) } }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.player_more), tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onClip != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_clip)) },
                    onClick = {
                        open = false
                        onClip()
                    },
                )
            }
            if (onSaveAudio != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_save_audio)) },
                    onClick = {
                        open = false
                        onSaveAudio()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(io.github.donggi.iroiroviewer.io.R.string.io_open_with)) },
                onClick = {
                    open = false
                    onOpenWith()
                },
            )
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
 * 다른 앱으로 넘기지 못한 까닭은 같은 자리에 뜨지만 문장이라 더 오래 둔다([PlayerOpenWith.HANDOFF_NOTE_HOLD_MS]).
 */
private const val NOTE_HOLD_MS = 1_800L

/** 구간의 시작을 옮긴 뒤 미리 재기까지 기다리는 시간 — 끌어 옮기는 동안 파일을 여러 번 열지 않는다. */
private const val CLIP_PREVIEW_DELAY_MS = 150L

/** 진행 바에 찍는 A·B 표시의 너비. 트랙보다 좁으면 눈에 띄지 않는다. */
private val AB_MARKER_WIDTH = 3.dp

/** 같은 표시의 높이. 트랙(4dp)보다 높아 위아래로 조금 넘친다. */
private val AB_MARKER_HEIGHT = 12.dp

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

/**
 * 조작부 판이 피할 인셋 — **아래와 양옆만**, 그리고 **바가 보이든 거둬졌든 같은 값**.
 *
 * 위쪽을 빼는 것은 판이 화면 아래에 붙어 있기 때문이다 — 예전의 `safeDrawingPadding()` 은 판 위에도 상태 표시줄 높이만큼 여백을 두어
 * 가로 영상을 그만큼 더 덮었다. `…IgnoringVisibility` 인 것은 조작부와 시스템 바가 **함께** 드나들기 때문이다([PlayerChrome]) — 보이는
 * 바만 재는 인셋이면 바가 들어오고 나가는 애니메이션 내내 판의 높이가 바뀌어 판과 그 위의 안내들이 출렁인다. 잘린 화면(노치)은
 * 늘 그 자리라 그대로 더한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun controlsInsets(): WindowInsets =
    WindowInsets.systemBarsIgnoringVisibility
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

/**
 * 화면 낭독기(TalkBack)의 '터치하여 탐색' 이 켜져 있는가 — 켜져 있으면 조작부를 저절로 거두지 않는다([PlayerChrome.autoHideAllowed]).
 * 켜고 끄는 것을 듣는다 — 재생 중에 낭독기를 켠 사람도 곧바로 거두기가 멈춘다.
 */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> on = enabled }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * 가운데 큰 재생/일시정지 단추(큰 재생기의 관행). 영상 위에 뜨므로 바탕을 반투명 검정 원으로 깐다 — 밝은 장면에서도
 * 보이게(조작부 판의 그라데이션과 같은 까닭).
 */
@Composable
private fun CenterPlayButton(playing: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(CENTER_BUTTON_SIZE)
            .clip(CircleShape)
            .background(Color(0x66000000)),
    ) {
        Icon(
            imageVector = if (playing) PauseIcon else Icons.Filled.PlayArrow,
            contentDescription = if (playing) pauseLabel() else playLabel(),
            tint = Color.White,
            modifier = Modifier.size(CENTER_ICON_SIZE),
        )
    }
}

/**
 * 터치 잠금 중의 화면 — 무엇을 눌러도 아래에 닿지 않고, 두드리면 잠금 풀기 단추가 [PlayerChrome.UNLOCK_HINT_MS] 동안 뜬다.
 * 잠근 직후에도 한 번 뜬다 — 어디를 누르면 풀리는지 보여야 한다.
 *
 * 두드림을 `clickable` 로 받는 것은 화면 낭독기 때문이다 — 그 동작이 '잠금 풀기 단추 보이기' 로 읽히고, 뜬 단추에 초점을 옮겨 풀 수
 * 있다. 끌기는 받지 않는다(아래의 몸짓은 꺼져 있다 — `playerGestures(enabled = …)`).
 *
 * @param hint 단추를 띄우라는 요청의 번호(두드림·뒤로 가기). 바뀔 때마다 다시 띄운다.
 */
@Composable
private fun BoxScope.TouchLockLayer(hint: Long, onTap: () -> Unit, onUnlock: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(hint) {
        visible = true
        delay(PlayerChrome.UNLOCK_HINT_MS)
        visible = false
    }
    val lockedLabel = stringResource(R.string.player_touch_locked)
    Box(
        Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = lockedLabel },
    )
    if (visible) {
        TextButton(
            onClick = onUnlock,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = Color(0xCC000000),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(controlsInsets())
                .padding(bottom = 32.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.player_touch_unlock),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 가운데 재생 단추의 지름. 엄지가 영상을 가리지 않고 짚을 만한 크기다(YouTube 의 그것과 비슷하다). */
private val CENTER_BUTTON_SIZE = 64.dp

/** 가운데 재생 단추 안의 그림. */
private val CENTER_ICON_SIZE = 40.dp
