package io.github.donggi.iroiroviewer.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.playback.PlaybackService
import java.util.concurrent.ExecutionException

/**
 * 큐 항목의 **파일 경로**를 `mediaId` 로 찾는다 — '다른 앱으로 열기' 가 넘길 것.
 *
 * ## 왜 컨트롤러를 하나 더 드는가
 *
 * `PlaybackConnection.State` 는 항목의 경로를 싣지 않는다(열쇠 `fileKey` 와 제목뿐이다). 커넥션의 컨트롤러는 그
 * 객체 안에 갇혀 있어, `core:playback` 을 고치지 않고 경로를 얻는 길은 **세션에 컨트롤러를 하나 더 붙여** 항목의
 * `localConfiguration.uri` 를 읽는 것뿐이다. 같은 프로세스의 컨트롤러에는 그 값이 건너온다(`PlaybackConnection` 이
 * 자막을 바꿀 때 기대는 사실이고 기기에서 확인했다고 적혀 있다 — **이 컨트롤러로는 기기에서 아직 확인하지 않았다**).
 * 까닭은 소스로 읽었다: media3 1.11 의 `MediaSessionStub` 은 같은 프로세스의 컨트롤러(`MediaControllerStub`)에게
 * `PlayerInfo` 를 묶지 않고 객체째 넘긴다(`toBundleInProcess` — 연결할 때와 상태가 바뀔 때 둘 다). 묶는 길
 * (`toBundleForRemoteProcess`)에서만 `localConfiguration` 이 빠진다. 컨트롤러가 몇 개든 이 판단은 같다.
 * 세션은 우리 패키지의 컨트롤러를 받는다(`PlaybackService` 의 `TrustedOnlyCallback`).
 *
 * **읽기만 한다.** 이 컨트롤러로 재생을 만지면 조작이 두 통로로 갈려, 커넥션이 드는 상태(A-B·섞기 이력·자막 선택)와
 * 어긋난다. 경로를 `State` 가 직접 싣게 되면 이 클래스는 지운다.
 *
 * ## 짝을 `mediaId` 로 맞춘다
 *
 * 실패와 지금 항목은 커넥션의 `State` 에서, 경로는 이 컨트롤러에서 온다 — **다른 통로**다(함정 표의 '서로 다른 흐름에서
 * 받은 값을 짝지어 판단하지 마라'). 자리(index)로 맞추면 둘이 한 박자 어긋난 순간 옆 줄의 파일을 넘긴다. 그래서
 * `State.fileKey` 와 **같은 `mediaId`**(`FileKey` — 크기·이름·수정 시각의 해시)를 가진 항목만 받는다. 못 찾으면 null 이고,
 * 화면은 '다른 앱을 열지 못했습니다' 로 말한다.
 *
 * 메인 스레드에서만 만든다·부른다·닫는다 — 컨트롤러는 자기를 만든 스레드의 루퍼에 묶인다(함정 표의 ExoPlayer 항목과 같은 제약).
 */
internal class ItemPaths(context: Context) : AutoCloseable {

    private val future = context.applicationContext.let { app ->
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    }

    /** 그 항목의 파일 경로. 아직 붙지 않았거나, 큐에 없거나, 파일이 아닌 URI 면 null. */
    fun pathOf(mediaId: String): String? {
        if (!future.isDone || future.isCancelled) return null
        val controller = try {
            future.get()
        } catch (e: ExecutionException) {
            // 세션이 거절했거나 서비스가 죽었다. 문구는 화면에 내지 않는다 — 화면은 우리 문장으로 말한다.
            Iro.d { "경로를 읽을 컨트롤러가 붙지 못했다: ${e.cause?.javaClass?.simpleName}" }
            return null
        }
        val item = controller.currentMediaItem?.takeIf { it.mediaId == mediaId } ?: find(controller, mediaId)
        val uri = item?.localConfiguration?.uri ?: return null
        return PlayerOpenWith.fileOf(uri.scheme, uri.path)
    }

    /** 지금 항목이 아니면 큐를 훑는다. 누를 때 한 번이고 큐는 500항목까지라 싸다. */
    private fun find(controller: MediaController, mediaId: String): MediaItem? {
        for (i in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(i)
            if (item.mediaId == mediaId) return item
        }
        return null
    }

    override fun close() {
        MediaController.releaseFuture(future)
    }
}
