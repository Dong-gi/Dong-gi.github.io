package io.github.donggi.iroiroviewer.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import android.app.PendingIntent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.io.Iro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 재생의 실체. **플레이어가 여기 산다.**
 *
 * ## 백그라운드 재생이 기능이 아니라 구조인 이유
 *
 * 플레이어를 화면(액티비티)이 들고 있으면 화면이 사라질 때 소리도 사라진다. 그래서
 * 흔히 '백그라운드 재생' 을 켜고 끄는 설정을 두는데, 그것은 잘못된 곳에 스위치를 단
 * 것이다. **플레이어를 서비스에 두면** 화면을 끄든, 홈으로 나가든, 다른 앱으로 바꾸든
 * 재생은 그냥 계속된다 — 끊는 코드가 없기 때문이다. 다시 앱으로 돌아오면 화면이
 * [androidx.media3.session.MediaController] 로 **지금 돌고 있는 그 세션에 다시 붙을 뿐**이라,
 * 이어서 트는 것이 아니라 애초에 끊긴 적이 없다.
 *
 * 그래서 이 앱에는 백그라운드 재생 스위치가 없다. 끄는 길은 하나다 — 사용자가 멈추거나,
 * 최근앱에서 앱을 밀어 없애는 것([onTaskRemoved]).
 *
 * ## 직접 구현하지 않는 것
 *
 * 오디오 포커스(다른 앱이 소리를 내면 줄이고 돌려주기)와 이어폰 뽑힘은 **라이브러리에
 * 맡긴다.** 둘 다 우리가 또 구현하면 pause 가 두 번 걸려 사용자가 한 번 눌러 두 번
 * 멈춘 것처럼 보인다.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var saver: PositionSaver? = null
    /**
     * **메인 디스패처다.** 이 스코프에서 도는 것은 전부 플레이어를 만지므로, IO 로 두면
     * `Player is accessed on the wrong thread` 로 죽는다. 디스크 쓰기는 그 안에서
     * 다시 IO 로 넘긴다([PositionSaver]).
     */
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    /**
     * 블루투스가 끊겨 멈춘 재생을 다시 연결되면 이어 튼다(설정 '블루투스가 연결되면 이어서 재생', 기본 켜짐). 판단은 [BluetoothResume] 이
     * 하고, 여기는 소리 장치 알림을 받아 넘기고 플레이어를 부른다. 알림·플레이어 모두 주 스레드다.
     */
    private val bluetoothResume = BluetoothResume()
    private var resumeOnBluetooth = true
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 플레이어의 '틀 것인가' 가 바뀐 횟수. 이어 틀기를 미뤄 둔 사이에 사용자가 무엇이든 했으면 틀지 않는다. */
    private var playIntentChanges = 0

    private fun bluetoothSinkConnected(): Boolean =
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type in BluetoothResume.BLUETOOTH_OUTPUTS } == true

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val sinks = addedDevices.filter { it.isSink }.map { it.type }
            if (!bluetoothResume.onDevicesAdded(sinks, resumeOnBluetooth)) return
            // **조금 기다렸다 튼다.** 장치가 붙었다는 알림이 소리 경로가 그리로 넘어가기 전에 올 수 있다 — 곧바로 틀면 첫 소리가
            // 기기 스피커로 샐 수 있다. 그 사이에 사용자가 틀거나 멈췄으면(횟수가 바뀌었으면) 그 뜻을 따른다.
            val at = playIntentChanges
            mainHandler.postDelayed({
                val p = player ?: return@postDelayed
                // 기다리는 사이에 장치가 다시 떨어졌으면 틀지 않는다 — 소리가 기기 스피커로 난다. 떨어질 때 멈춤
                // 알림은 오지 않는다(그때 재생 중이 아니었다).
                if (playIntentChanges == at && !p.playWhenReady && bluetoothSinkConnected()) {
                    Iro.d { "블루투스가 다시 연결되어 이어서 튼다" }
                    p.play()
                }
            }, BLUETOOTH_RESUME_DELAY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            // **자막 바이트가 지나는 길에 인코딩 변환을 끼운다.** media3 에는 사이드로드
            // 자막의 인코딩을 말해 줄 자리가 없어서, CP949·Shift_JIS 자막이 그대로
            // 들어가면 깨진 글자가 뜬다. 자세한 것은 `SubtitleSource.kt` 의 주석.
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    subtitleAwareDataSourceFactory(this),
                    // 추출기는 기본값 그대로이고 MPEG-TS 의 길이를 찾는 범위만 넓힌다 — 아래 [TS_TIMESTAMP_SEARCH_BYTES].
                    DefaultExtractorsFactory().setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES),
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                // true = 오디오 포커스를 라이브러리가 다룬다(줄이기·멈추기·돌려주기).
                true,
            )
            // 이어폰을 뽑으면 멈춘다. ACTION_AUDIO_BECOMING_NOISY 를 직접 받지 않는다.
            .setHandleAudioBecomingNoisy(true)
            // 화면이 꺼져도 CPU 가 잠들지 않게 한다. 이것이 없으면 잠금화면에서 끊긴다.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playIntentChanges++
                bluetoothResume.onPlayWhenReadyChanged(playWhenReady, reason)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                bluetoothResume.disarm()
            }
        })
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.registerAudioDeviceCallback(deviceCallback, mainHandler)
        scope.launch { AppPreferences(this@PlaybackService).resumeOnBluetooth.collect { resumeOnBluetooth = it } }
        session = MediaSession.Builder(this, exo)
            .setCallback(TrustedOnlyCallback(packageName))
            .apply {
                // **알림을 누르면 재생 화면으로 간다 — 영상이면 영상, 소리면 재생목록.**
                //
                // 달지 않으면 알림 본체를 눌러도 아무 일도 일어나지 않는다(버튼만 듣는다).
                // '목록을 펼쳐라' 를 싣지 않는 것이 요점이다(그 전에는 언제나 실어, 영상을 뒤에서 틀어 두고 알림으로
                // 돌아온 사람이 매번 목록을 닫아야 했다). 싣지 않으면 화면의 기본값(`PlayerActivity.playlistShown`)이 정한다 — 영상이
                // 보이는 큐는 영상을, 소리만 나는 큐는 목록을 펼친다. 소리에서는 지금 나오는 것을 알림이 이미 적고 있어 돌아온 사람이
                // 대개 **다음에 무엇이 나오는지**를 보려 하므로, 소리 쪽은 예전처럼 목록이다. 판정이 화면 하나에 있으므로 알림을 만든
                // 뒤에 큐가 영상 ↔ 소리로 바뀌어도 누르는 순간의 큐를 따른다.
                //
                // 화면이 어느 모듈에 있는지 여기서는 모른다. 인텐트는 이음매가 준다
                // (`PlayerSupport`). `app` 이 `onCreate` 에서 꽂으므로 — 애플리케이션이
                // 서비스보다 먼저 만들어진다 — 여기서는 이미 꽂혀 있다. 그래도 null 을
                // 견딘다: 안 꽂혔으면 누를 곳을 **달지 않는** 것이 엉뚱한 데로 보내는
                // 것보다 낫다.
                PlayerSupport.intent(this@PlaybackService, showPlaylist = false)?.let { i ->
                    setSessionActivity(
                        PendingIntent.getActivity(
                            this@PlaybackService,
                            0,
                            i,
                            // **API 31+ 는 IMMUTABLE 과 MUTABLE 중 하나를 반드시 적어야
                            // 하고, 안 적으면 만드는 자리에서 죽는다.** 둘 중 IMMUTABLE 인
                            // 이유는 받는 쪽(시스템 UI)이 인텐트를 채워 넣을 이유가 없기
                            // 때문이다. 같은 요청 코드로 다시 만들 때 extras 가 갱신되도록
                            // UPDATE_CURRENT 를 함께 준다.
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                }
            }
            .build()
        saver = PositionSaver(this, exo, scope).also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * 최근앱에서 밀어 없앴을 때.
     *
     * 사용자가 앱을 치웠는데 소리가 계속 나면 그것은 앱이 말을 안 듣는 것이다.
     * 멈추고 서비스도 내린다.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saver?.saveNow()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.unregisterAudioDeviceCallback(deviceCallback)
        mainHandler.removeCallbacksAndMessages(null)
        saver?.saveNow()
        saver?.stop()
        scope.cancel()
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

/** 블루투스가 다시 붙은 뒤 이어 틀기까지 기다리는 시간. 소리 경로가 그 장치로 넘어갈 틈이다. */
private const val BLUETOOTH_RESUME_DELAY_MS = 1_000L

/**
 * MPEG-TS 의 시각(PCR)을 찾는 범위 — 188바이트 패킷 5,600개(1,052,800 바이트, 1 MiB 남짓).
 *
 * media3 는 TS 의 길이를 **처음과 끝의 이 범위**에서 찾은 첫·마지막 PCR 로 재고, 끝에서 하나도 못 찾으면 그 파일을 '되감을 수
 * 없는' 것으로 본다 — 재생 화면에 길이가 0:00 으로 뜨고 막대를 끌어도 그 자리로 가지 않는다. 기본값은 600패킷(112,800 바이트)인데
 * HLS 를 받아 이어 붙인 실물(3.38 GB·62분, 사용자가 짚었다)은 마지막 PCR 이 끝에서 142,128 바이트 앞이었다 — PCR 은 영상 줄기의
 * 패킷 일부에만 실리는데, 끝의 142 KB 는 소리 패킷이 682개·영상 패킷이 75개였고 그 영상 패킷에는 PCR 이 없었다.
 *
 * 같은 값이 되감기의 한 걸음이 읽는 양이기도 하다(`TsBinarySearchSeeker` — 걸음마다 이만큼을 훑어 PCR 을 찾는다). 걸음은 사이를
 * 짐작해 좁히므로 몇 번이고, 이 앱의 `.ts` 는 언제나 기기의 파일이다.
 */
private const val TS_TIMESTAMP_SEARCH_BYTES = 188 * 5_600

/**
 * 우리 앱과 시스템(잠금화면·블루투스·어시스턴트)만 이 세션에 붙게 한다.
 *
 * `MediaSessionService` 는 **exported 가 강제**다(시스템이 붙어야 하므로). 그래서 아무
 * 앱이나 컨트롤러로 연결해 지금 무엇을 보고 있는지 — 파일 이름이 메타데이터로 나간다 —
 * 읽을 수 있다. 막는 곳은 여기 하나뿐이다.
 */
@OptIn(UnstableApi::class)
private class TrustedOnlyCallback(private val selfPackage: String) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val allowed = controller.packageName == selfPackage || session.isMediaNotificationController(controller) ||
            session.isAutoCompanionController(controller) || controller.packageName == "android"
        if (!allowed) {
            Iro.d { "알 수 없는 컨트롤러를 거절했다: ${controller.packageName}" }
            return MediaSession.ConnectionResult.reject()
        }
        return super.onConnect(session, controller)
    }
}
