package io.github.donggi.iroiroviewer.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import io.github.donggi.iroiroviewer.charset.Bom
import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.io.Iro
import java.io.EOFException
import java.io.IOException
import kotlin.math.min

/**
 * 자막 파일을 **UTF-8 로 바꿔** media3 에 흘린다.
 *
 * ## 왜 필요한가 — 바이트코드로 확인한 구멍
 *
 * media3 에는 사이드로드 자막의 인코딩을 말해 줄 자리가 **없다.**
 * `MediaItem.SubtitleConfiguration.Builder` 가 받는 것은 `setUri`·`setMimeType`·
 * `setLanguage`·`setSelectionFlags`·`setRoleFlags`·`setLabel`·`setId` 뿐이고(javap 로 확인),
 * `SubripParser.parse(byte[], …)` 는 `ParsableByteArray` 로 읽는데 그쪽이 아는 것은
 * `readUtfCharsetFromBom()` — **BOM 이 없으면 UTF-8 로 단정한다.**
 *
 * 한국어 자막은 CP949 가, 일본어 자막은 Shift_JIS 가 흔하고 둘 다 BOM 이 없다.
 * 그대로 넘기면 **화면에 깨진 글자가 뜬다.** 7단계가 텍스트 뷰어를 위해 만들어 둔
 * `CharsetDetector` 가 답을 알고 있는데 그것을 먹일 구멍이 없는 셈이다.
 *
 * 그래서 **바이트가 들어가는 길**에서 바꾼다. media3 가 자막을 읽을 때 쓰는
 * [DataSource] 를 한 겹 감싸, 자막 확장자면 통째로 읽어 판정하고 UTF-8 로 변환한 바이트를
 * 대신 내놓는다. 파서는 BOM 없는 UTF-8 을 보게 되므로 아무것도 바꿀 필요가 없다.
 *
 * ## 왜 통째로 메모리에 올리는가
 *
 * 변환하면 **길이가 달라진다**(CP949 한 글자 2바이트 → UTF-8 3바이트). 스트리밍으로
 * 바꾸면 `DataSpec.position` 이 원본 오프셋인지 변환본 오프셋인지가 어긋나고, media3 는
 * 변환본 기준으로 물어본다. 통째로 만들어 두면 어느 구간을 물어도 정확히 답할 수 있다.
 *
 * 자막은 글이다 — 두 시간짜리 영화도 100 KB 안쪽이다. 그래도 사용자 파일이므로
 * [MAX_BYTES] 로 자른다. 넘으면 **변환하지 않고 원본을 그대로 흘린다**(자막이 아니라
 * 다른 것이 확장자만 자막인 경우다. 깨져 보이는 것이 앱이 멈추는 것보다 낫다).
 */
@OptIn(UnstableApi::class)
class SubtitleDataSourceFactory(
    private val delegate: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = SubtitleDataSource(delegate.createDataSource())

    companion object {
        /**
         * 자막 하나의 상한. **4 MiB.**
         *
         * 7단계가 문법 강조를 끄는 경계로 쓴 값과 같다 — '이것은 사람이 읽는 글이다' 의
         * 실질적 상한으로 이미 한 번 고른 수이고, 자막에는 훨씬 넉넉하다.
         */
        const val MAX_BYTES = 4 * 1024 * 1024

        /** 판정에 쓰는 앞머리. `CharsetDetector` 의 상한과 같다. */
        const val DETECT_BYTES = CharsetDetector.DETECT_SCAN_MAX
    }
}

@OptIn(UnstableApi::class)
private class SubtitleDataSource(private val inner: DataSource) : DataSource {

    /** 변환본. 자막이 아니거나 너무 크면 null 이고, 그때는 [inner] 가 그대로 답한다. */
    private var converted: ByteArray? = null
    private var position = 0
    private var remaining = 0

    override fun addTransferListener(transferListener: TransferListener) {
        inner.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val name = dataSpec.uri.lastPathSegment.orEmpty()
        if (!SubtitleNames.isSubtitle(name)) return inner.open(dataSpec)

        // **원본 전체를 읽는다.** 구간 요청(position/length)은 변환본에 대해 다시 계산한다.
        val whole = DataSpec.Builder().setUri(dataSpec.uri).build()
        val raw = readAll(whole) ?: return inner.open(dataSpec)

        val utf8 = toUtf8(raw, name)
        converted = utf8
        position = dataSpec.position.toInt().coerceIn(0, utf8.size)
        remaining = if (dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong()) {
            utf8.size - position
        } else {
            min(dataSpec.length.toInt(), utf8.size - position)
        }
        return remaining.toLong()
    }

    /** 원본을 상한까지 읽는다. 넘거나 실패하면 null — 부르는 쪽이 원본 경로로 돌아간다. */
    private fun readAll(spec: DataSpec): ByteArray? = try {
        inner.open(spec)
        val buffer = ByteArray(SubtitleDataSourceFactory.MAX_BYTES)
        var total = 0
        var full = false
        while (total < buffer.size) {
            // **`readNBytes` 를 쓰지 않는다** — API 33 이고 minSdk 는 31 이다(함정 표).
            val n = inner.read(buffer, total, buffer.size - total)
            if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) { full = true; break }
            if (n <= 0) break
            total += n
        }
        inner.close()
        if (!full && total >= buffer.size) null else buffer.copyOf(total)
    } catch (e: IOException) {
        runCatching { inner.close() }
        null
    } catch (e: EOFException) {
        runCatching { inner.close() }
        null
    }

    /**
     * 판정해서 UTF-8 로. 이미 UTF-8 이면 **BOM 만 떼고** 그대로 쓴다 — 다시 인코딩하면
     * 잘못된 바이트가 조용히 `U+FFFD` 로 바뀐다.
     */
    private fun toUtf8(raw: ByteArray, name: String): ByteArray {
        val bom = Bom.detect(raw)
        val head = if (raw.size > SubtitleDataSourceFactory.DETECT_BYTES) {
            raw.copyOf(SubtitleDataSourceFactory.DETECT_BYTES)
        } else {
            raw
        }
        val detection = CharsetDetector.detect(head)
        val charset = detection.encoding.charset
        Iro.d(TAG) { "자막 $name → ${detection.encoding.label} (${detection.confidence})" }
        if (charset == null) return raw
        val body = if (bom != null) raw.copyOfRange(bom.length, raw.size) else raw
        if (charset == Charsets.UTF_8) return body
        return try {
            String(body, charset).toByteArray(Charsets.UTF_8)
        } catch (e: RuntimeException) {
            raw
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val data = converted ?: return inner.read(buffer, offset, length)
        if (remaining == 0) return androidx.media3.common.C.RESULT_END_OF_INPUT
        val n = min(length, remaining)
        System.arraycopy(data, position, buffer, offset, n)
        position += n
        remaining -= n
        return n
    }

    override fun getUri(): android.net.Uri? = inner.uri

    override fun close() {
        if (converted == null) inner.close()
        converted = null
        position = 0
        remaining = 0
    }

    private companion object {
        const val TAG = "Subtitle"
    }
}

/**
 * 기기 파일을 읽는 기본 소스에 위 변환을 얹은 공장.
 *
 * `INTERNET` 을 선언하지 않으므로 [DefaultDataSource] 의 네트워크 갈래는 어차피 죽어 있다.
 */
@OptIn(UnstableApi::class)
fun subtitleAwareDataSourceFactory(context: android.content.Context): DataSource.Factory =
    SubtitleDataSourceFactory(DefaultDataSource.Factory(context))
