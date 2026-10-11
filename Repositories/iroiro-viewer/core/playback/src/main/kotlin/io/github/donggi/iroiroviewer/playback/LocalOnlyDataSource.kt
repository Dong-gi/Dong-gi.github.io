package io.github.donggi.iroiroviewer.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.Locale

/**
 * 재생기가 **기기 안의 것만** 열게 한다(2026-10-05 — 이 앱이 그때 SFTP 로 `INTERNET` 을 선언했다. 10-09 에 다시 걷었다).
 *
 * media3 의 `DefaultDataSource` 는 스킴을 보고 길을 고른다 — `file`·`content` 말고도 `http`·`https`(기본 HTTP 소스)·`udp`(소켓을 연다)·
 * `rtmp`·`data` 를 연다. 재생 큐에 들어가는 것은 언제나 파일의 경로라 그 갈래에 닿을 일이 없고, `INTERNET` 이 없으니 OS 도 그 갈래를
 * 통째로 막는다. 그래도 여기서 한 번 더 막는다(겹쳐 둔다) — 열기 전에 스킴을 보고, 허락한 것이 아니면 입출력 실패로 끝낸다(재생
 * 화면은 그것을 '읽을 수 없다' 로 말한다). 스킴이 없는 것은 파일 경로다.
 */
@OptIn(UnstableApi::class)
class LocalOnlyDataSource(private val inner: DataSource) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) = inner.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        if (!allows(dataSpec.uri.scheme)) throw IOException("허락하지 않은 스킴")
        return inner.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = inner.read(buffer, offset, length)

    override fun getUri(): Uri? = inner.uri

    override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

    override fun close() = inner.close()

    class Factory(private val inner: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = LocalOnlyDataSource(inner.createDataSource())
    }

    companion object {
        /** 여는 스킴. 대소문자를 가리지 않는다. 스킴이 없으면(null) 파일 경로로 본다. */
        private val ALLOWED = setOf("file", "content")

        fun allows(scheme: String?): Boolean = scheme == null || scheme.lowercase(Locale.ROOT) in ALLOWED
    }
}
