package io.github.donggi.iroiroviewer.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 블루투스가 다시 연결되면 이어서 트는 판단([BluetoothResume]). 에뮬레이터에는 블루투스 장치를 붙일 길이 없어 기기에서는 등록이 죽지
 * 않는 것만 볼 수 있다 — 차례는 여기서 박는다.
 */
class BluetoothResumeTest {

    private val noisy = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
    private val user = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private val a2dp = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

    @Test
    fun `끊겨서 멈춘 재생은 블루투스가 붙으면 한 번 이어 튼다`() {
        val r = BluetoothResume()
        r.onPlayWhenReadyChanged(playWhenReady = false, reason = noisy)
        assertTrue(r.onDevicesAdded(a2dp, enabled = true))
        // 한 번만 — 같은 장치가 두 번 알려 와도 다시 틀지 않는다.
        assertFalse(r.onDevicesAdded(a2dp, enabled = true))
    }

    @Test
    fun `설정이 꺼져 있으면 틀지 않는다`() {
        val r = BluetoothResume()
        r.onPlayWhenReadyChanged(false, noisy)
        assertFalse(r.onDevicesAdded(a2dp, enabled = false))
    }

    @Test
    fun `사용자가 멈춘 것이나 그 사이의 다른 일은 이어 틀지 않는다`() {
        val r = BluetoothResume()
        r.onPlayWhenReadyChanged(false, user)
        assertFalse(r.onDevicesAdded(a2dp, enabled = true))
        // 끊겨 멈춘 뒤 사용자가 직접 틀었다가 멈췄다 — 끊겨서 멈춘 그 재생이 아니다.
        r.onPlayWhenReadyChanged(false, noisy)
        r.onPlayWhenReadyChanged(true, user)
        r.onPlayWhenReadyChanged(false, user)
        assertFalse(r.onDevicesAdded(a2dp, enabled = true))
        // 끊겨 멈춘 뒤 다른 항목으로 넘어갔다.
        r.onPlayWhenReadyChanged(false, noisy)
        r.disarm()
        assertFalse(r.onDevicesAdded(a2dp, enabled = true))
    }

    @Test
    fun `소리를 내는 블루투스 장치만 센다`() {
        val r = BluetoothResume()
        r.onPlayWhenReadyChanged(false, noisy)
        // 통화용(SCO)·유선 이어폰·기기 스피커는 아니다.
        assertFalse(r.onDevicesAdded(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO), enabled = true))
        assertFalse(r.onDevicesAdded(listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES), enabled = true))
        assertFalse(r.onDevicesAdded(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), enabled = true))
        // 섞여 오면 블루투스 하나로 충분하다(헤드셋은 A2DP 와 SCO 를 함께 알린다).
        assertTrue(r.onDevicesAdded(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET), enabled = true))
    }

    @Test
    fun `처음 등록할 때 알려 오는 장치로는 틀지 않는다`() {
        // 등록하면 이미 붙어 있는 장치가 한꺼번에 온다 — 끊겨 멈춘 적이 없으니 아무 일도 없다.
        assertFalse(BluetoothResume().onDevicesAdded(a2dp, enabled = true))
    }
}
