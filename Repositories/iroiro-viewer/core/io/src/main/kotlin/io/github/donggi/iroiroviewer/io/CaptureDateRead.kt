package io.github.donggi.iroiroviewer.io

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * 파일 하나의 찍힌 시각을 읽는 일의 **결과를 가른다**. 갤러리가 남의 파일을 여는 자리라 어떤 실패도 훑기를 죽이지 못하게 한다.
 *
 * ## 셋으로 가르는 까닭
 *
 * - [Result.Taken] — 읽었다.
 * - [Result.NoDate] — 열어서 읽었으나 날짜가 없거나 못 믿는다, **또는 파서가 무너졌다**. 캐시에 '날짜 없음' 으로 남긴다.
 * - [Result.Unknown] — 열지 못했거나 읽는 도중 입출력이 실패했다. 일시적일 수 있으므로(SD 카드·권한·잠깐의 EIO) 캐시에 남기지 않고
 *   다음 훑기가 다시 연다.
 *
 * ## `Throwable` 을 잡는다 — `OutOfMemoryError` 까지
 *
 * ExifInterface 1.4.2 는 PNG 의 `eXIf`·WebP 의 `EXIF` 덩이와 그 안(JPEG APP1 도)의 TIFF IFD 칸을 **적힌 길이대로** `new byte[길이]` 로
 * 잡는다. 길이를 `0x7FFFFFF0` 으로 적은 57바이트 PNG 하나가 `OutOfMemoryError` 를 던지고, `IOException`·`RuntimeException` 만 잡으면 그것이
 * 훑기 밖으로 나가 **갤러리를 열 때마다 앱이 죽는다**(독립 검토가 재현했다). 할당이 실패한 것이라 힙은 그대로다 — 잡고 넘어가도 된다. 그
 * 파일은 [Result.NoDate] 로 남아 다음 훑기가 다시 열지 않는다. 취소(`CancellationException`)만 그대로 던진다.
 *
 * **잡는 것만으로는 모자란다** — 길이를 힙 바로 아래로 적으면 할당이 성공하고 다른 스레드가 죽는다. 그래서 부르는 쪽이 먼저 덩이와 TIFF
 * 블록을 걸어 본다(`ExifContainerGuard`). 이 가르기는 그 뒤의 마지막 그물이다.
 *
 * `LinkageError`(클래스 초기화 실패 따위)는 파일 탓이 아니라 [Result.Unknown] 이다 — '날짜 없음' 으로 남기면 고쳐진 뒤에도 다시 읽지 않는다.
 */
internal object CaptureDateRead {

    sealed interface Result {
        data class Taken(val millis: Long) : Result
        data object NoDate : Result
        data object Unknown : Result
    }

    /**
     * [open] 으로 열고 [parse] 로 읽는다. [parse] 가 닫을 책임을 진다([open] 은 성공하면 그것을 넘긴다).
     */
    inline fun <T> read(open: () -> T, parse: (T) -> Long?): Result {
        val source = try {
            open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Result.Unknown
        }
        return try {
            parse(source)?.let { Result.Taken(it) } ?: Result.NoDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.Unknown
        } catch (e: LinkageError) {
            Result.Unknown
        } catch (e: Throwable) {
            Result.NoDate
        }
    }
}
