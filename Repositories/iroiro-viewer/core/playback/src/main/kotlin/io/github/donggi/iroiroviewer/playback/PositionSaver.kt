package io.github.donggi.iroiroviewer.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.PlaybackPositionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 어디까지 봤는지 기억한다.
 *
 * ## 언제 저장하는가
 *
 * 10초마다 한 번, 그리고 **끊길 수 있는 모든 순간**에 한 번 더 — 멈출 때, 다음 항목으로
 * 넘어갈 때, 서비스가 내려갈 때, 앱을 최근앱에서 밀 때. 주기만 믿으면 마지막 10초가
 * 날아가고, 순간만 믿으면 앱이 갑자기 죽었을 때 통째로 날아간다.
 *
 * ## 왜 DataStore 가 아니라 Room 인가
 *
 * DataStore 는 쓸 때마다 파일 전체를 다시 쓴다. 10초마다 그러면 저장소를 계속 긁는다.
 * 그래서 이런 잦은 갱신은 Room 이 맞다(설정 몇 개는 DataStore 가 맞다).
 *
 * ## 짧은 것은 기억하지 않는다
 *
 * 3분짜리 노래의 5초 지점을 기억했다가 다음에 5초부터 틀면 그것은 도움이 아니라 방해다.
 * 충분히 긴 것만, 충분히 진행했을 때만, 그리고 거의 끝난 것은 지운다.
 *
 * @param scope **메인 디스패처의 스코프여야 한다.** ExoPlayer 는 자기를 만든 스레드에서만
 *   만질 수 있어서, 다른 스레드에서 `currentPosition` 하나만 읽어도
 *   `IllegalStateException: Player is accessed on the wrong thread` 로 앱이 죽는다.
 *   DB 쓰기는 이 안에서 IO 로 넘긴다.
 */
class PositionSaver(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
) {

    private val dao = IroiroDatabase.get(context).playbackPositions()
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 넘어가기 **전** 항목의 위치는 이 시점에 이미 사라졌다. 그래서 아래
            // onPositionDiscontinuity 가 아니라 여기서는 새 항목만 다룬다.
            saveNow()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveNow()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) clearCurrent()
        }
    }

    fun start() {
        player.addListener(listener)
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                saveNow()
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player.removeListener(listener)
    }

    /**
     * 지금 위치를 적는다. **메인 스레드에서 부른다.**
     *
     * 플레이어에서 값만 먼저 꺼내고, 디스크 쓰기는 IO 로 넘긴다.
     */
    fun saveNow() {
        val key = player.currentMediaItem?.mediaId ?: return
        val name = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: return
        val position = player.currentPosition
        val duration = player.duration

        if (duration <= 0 || duration < MIN_DURATION_MS) return
        if (position < MIN_POSITION_MS) return
        // 거의 끝났으면 기억하지 않는다 — 다음에 열었을 때 끝에서 시작하면 황당하다.
        if (duration - position < END_MARGIN_MS) {
            scope.launch { withContext(Dispatchers.IO) { dao.remove(key) } }
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                dao.upsert(
                    PlaybackPositionEntity(
                        fileKey = key,
                        positionMs = position,
                        durationMs = duration,
                        // 경로는 적지 않는다. 이 표가 '무엇을 봤는가' 의 목록이 되지 않도록
                        // 키는 해시이고 표시용 이름만 남긴다.
                        volumeRelativePath = null,
                        displayName = name,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private fun clearCurrent() {
        val key = player.currentMediaItem?.mediaId ?: return
        scope.launch { withContext(Dispatchers.IO) { dao.remove(key) } }
    }

    private companion object {
        const val TICK_MS = 10_000L

        /** 이보다 짧은 것은 이어보기를 하지 않는다(1분). */
        const val MIN_DURATION_MS = 60_000L

        /** 이보다 앞이면 기억할 것이 없다(10초). */
        const val MIN_POSITION_MS = 10_000L

        /** 끝에서 이만큼 안쪽이면 다 본 것으로 친다(15초). */
        const val END_MARGIN_MS = 15_000L
    }
}
