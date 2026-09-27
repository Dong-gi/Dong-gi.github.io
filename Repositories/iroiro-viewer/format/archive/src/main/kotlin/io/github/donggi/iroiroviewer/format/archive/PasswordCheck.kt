package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * 암호가 맞는지 **읽어서** 확인한다([ArchiveReader.verifyPassword] 의 기본 구현).
 *
 * ## '틀렸다' 는 암호의 신호에만
 *
 * 예전 판은 어떤 `IOException` 이든 '틀렸다' 로 읽었다. 그러면 상한에 걸린 것(맞는 암호인데 항목이
 * 크다)도, 우리가 풀지 않는 압축 방식도 '암호가 맞지 않습니다' 가 되어 **맞는 암호를 영원히
 * 거절한다**(검토가 잡았다). 이제 가른다.
 *
 * | 무엇이 났나 | 뜻 |
 * |---|---|
 * | [ArchivePasswordException] | 틀렸다 |
 * | 상한([ParseLimitExceededException]) — 이미 [MIN_EVIDENCE] 넘게 풀었으면 | **맞다**(틀린 열쇠로는 그만큼 풀리지 않는다) |
 * | 상한 — 그 전이면 | 위로 던진다(화면이 '너무 크다' 를 말한다) |
 * | 풀지 않는 방식·메모리 상한 | 위로 던진다 |
 * | 그 밖의 `IOException`(깨진 LZMA·CRC·인증값) | 틀렸다 — 7z 는 틀린 암호를 '데이터가 깨졌다' 로만 알린다 |
 *
 * ## 어느 항목을 읽나
 *
 * 무작위 접근이 싼 ZIP 은 **가장 작은** 암호 항목을 끝까지(CRC·인증값까지) 읽는다. solid 아카이브는
 * 가장 작은 것을 고르면 앞의 덩어리를 전부 풀어야 닿으므로 **맨 앞의** 암호 항목을 [SOLID_CAP] 까지
 * 읽는다 — 틀린 열쇠는 LZMA 의 첫 몇 바이트에서 드러난다. 끝까지 읽지 않으므로 '저장(Copy)' 방식으로
 * 담은 큰 항목에서는 틀린 암호를 못 알아챌 수 있다(7-Zip 이 그렇게 만드는 일은 드물다).
 */
internal object PasswordCheck {

    /** solid 아카이브에서 확인을 위해 풀 최대량. */
    private const val SOLID_CAP = 16L shl 20

    /** 이만큼 풀렸으면 열쇠가 맞다고 본다. */
    private const val MIN_EVIDENCE = 1L shl 20

    fun byReading(reader: ArchiveReader): Boolean {
        val locked = reader.entries.filter { it.isEncrypted && it.isReadable }
        val target = (
            if (reader.randomAccess) locked.minByOrNull { it.declaredSize.coerceAtLeast(0L) }
            else locked.minByOrNull { it.index }
            ) ?: return true
        val cap = if (reader.randomAccess) Long.MAX_VALUE else SOLID_CAP
        var read = 0L
        return try {
            reader.open(target).use { input ->
                val buf = ByteArray(64 * 1024)
                while (read < cap) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("중단")
                    val n = input.read(buf)
                    if (n < 0) break
                    read += n
                }
            }
            true
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ArchivePasswordException) {
            false
        } catch (e: ParseLimitExceededException) {
            if (read >= MIN_EVIDENCE) true else throw e
        } catch (e: ZipDecryption.UnsupportedEncryptionException) {
            throw e
        } catch (e: org.apache.commons.compress.MemoryLimitException) {
            throw e
        } catch (e: IOException) {
            false
        }
    }
}
