package io.github.donggi.iroiroviewer.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.Player

/**
 * **블루투스가 다시 연결되면 이어서 튼다**(요구사항의 'VLC 에서 차용' — 연결/해제 시 자동 재개/멈춤). 판단만 한다 — 기기 소리 장치의
 * 알림을 받고 플레이어를 부르는 일은 [PlaybackService] 가 한다. 그래서 JVM 시험이 차례를 그대로 짚는다.
 *
 * 끊길 때 멈추는 것은 media3 가 한다(`setHandleAudioBecomingNoisy` — 소리가 스피커로 새지 않게). 그 멈춤을 이 객체가 **알아 두었다가**
 * ([armed]) 블루투스 소리 장치가 새로 붙으면 한 번 이어 튼다. 알아 둔 것은 다른 일이 끼어들면 버린다 — 그 사이에 사용자가 직접
 * 틀거나 멈췄거나, 다른 항목으로 넘어갔으면 '끊겨서 멈춘 그 재생' 이 아니다. 끊긴 지 오래되었어도 버리지 않는다(차에서 내렸다 다시
 * 타는 것이 흔한 쓰임이다).
 *
 * 처음 등록할 때 이미 붙어 있는 장치가 한꺼번에 알려 온다 — 그때는 알아 둔 것이 없으므로 아무 일도 없다.
 */
internal class BluetoothResume {

    /** 블루투스(또는 이어폰)가 끊겨 멈춘 재생이 있다. */
    var armed: Boolean = false
        private set

    /** 플레이어의 '틀 것인가' 가 바뀌었다. 끊김으로 멈춘 것만 알아 두고, 나머지는 모두 버린다. */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        armed = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
    }

    /** 다른 항목으로 넘어갔거나 재생을 멈췄다(정지). 끊겨서 멈춘 그 재생이 아니다. */
    fun disarm() {
        armed = false
    }

    /**
     * 소리 장치가 새로 붙었다. 이어 틀어야 하면 true — 한 번만 답한다(알아 둔 것을 쓴다).
     *
     * @param types 붙은 장치들의 `AudioDeviceInfo.TYPE_*`.
     * @param enabled 설정의 '블루투스가 연결되면 이어서 재생'.
     */
    fun onDevicesAdded(types: Collection<Int>, enabled: Boolean): Boolean {
        if (!armed || !enabled) return false
        if (types.none { it in BLUETOOTH_OUTPUTS }) return false
        armed = false
        return true
    }

    companion object {
        /**
         * 소리를 내는 블루투스 장치. A2DP(이어폰·스피커·차)와 LE 오디오의 헤드셋·스피커, 보청기다. **SCO(통화용)는 넣지 않는다** —
         * 헤드셋이 붙으면 A2DP 와 함께 오고, SCO 만 있는 장치(통화 전용)로 음악을 이어 틀 까닭이 없다.
         */
        val BLUETOOTH_OUTPUTS: Set<Int> = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
