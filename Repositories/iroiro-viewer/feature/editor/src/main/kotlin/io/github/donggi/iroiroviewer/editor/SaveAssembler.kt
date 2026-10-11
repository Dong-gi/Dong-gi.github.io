package io.github.donggi.iroiroviewer.editor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * 편집기가 보낸 저장 덩이를 받아 파일로 맞춘다 — **순수**(안드로이드를 모른다, JVM 시험).
 *
 * 약속(쪽 → 앱):
 * 1. 글 `{"op":"save-begin","id":N,"size":바이트,"sha256":"16진 64자"}` — [begin]
 * 2. 이진 덩이 여럿 — 앞 8바이트가 머리(번호 N, 차례 0·1·2… — 둘 다 부호 없는 32비트 빅 엔디언), 나머지가 내용 — [chunk]. 앱이 덩이마다
 *    `{"op":"ack","id":N,"seq":k}` 로 답하고 쪽은 답을 받은 뒤 다음 덩이를 보낸다(메모리를 묶는다).
 * 3. 글 `{"op":"save-end","id":N}` — [end]: 받은 길이와 SHA-256 이 머리에 적은 것과 같아야 끝난다.
 *
 * 한 번에 저장 하나만 받는다(새 [begin] 이 오면 앞의 것은 버린다). 상한([limit])을 넘거나 차례가 어긋나면 그 저장은 실패다.
 */
class SaveAssembler(private val limit: Long) {

    sealed class Step {
        data class Ack(val id: Long, val seq: Long) : Step()
        data class Done(val id: Long, val file: File, val size: Long) : Step()
        data class Failed(val id: Long, val why: String) : Step()
    }

    private class Pending(val id: Long, val size: Long, val sha256: String, val file: File, val out: FileOutputStream) {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var nextSeq = 0L
    }

    private var pending: Pending? = null

    /** 저장 하나를 연다. [file] 에 쓴다(부르는 쪽이 빈 자리를 준다). */
    fun begin(id: Long, size: Long, sha256: String, file: File): Step {
        abort()
        if (size < 0 || size > limit) return Step.Failed(id, "크기 $size 가 상한을 넘는다")
        if (!SHA256_HEX.matches(sha256)) return Step.Failed(id, "해시 꼴이 아니다")
        val out = try {
            FileOutputStream(file)
        } catch (e: IOException) {
            return Step.Failed(id, "쓸 자리를 열지 못했다")
        }
        pending = Pending(id, size, sha256, file, out)
        return Step.Ack(id, -1)
    }

    /** 이진 덩이 하나(머리 8바이트 + 내용). */
    fun chunk(data: ByteArray): Step {
        if (data.size < HEADER) return fail(-1, "덩이가 머리보다 짧다")
        val id = u32(data, 0)
        val seq = u32(data, 4)
        val p = pending ?: return Step.Failed(id, "열린 저장이 없다")
        if (id != p.id) return fail(id, "다른 저장의 덩이다")
        if (seq != p.nextSeq) return fail(id, "차례가 어긋났다($seq, 기대 ${p.nextSeq})")
        val n = data.size - HEADER
        if (p.received + n > p.size) return fail(id, "적은 크기보다 많이 왔다")
        try {
            p.out.write(data, HEADER, n)
        } catch (e: IOException) {
            return fail(id, "쓰지 못했다")
        }
        p.digest.update(data, HEADER, n)
        p.received += n
        p.nextSeq++
        return Step.Ack(id, seq)
    }

    /** 끝 — 길이와 해시를 견준다. 맞으면 파일을 내린다(fsync). */
    fun end(id: Long): Step {
        val p = pending ?: return Step.Failed(id, "열린 저장이 없다")
        if (id != p.id) return fail(id, "다른 저장의 끝이다")
        if (p.received != p.size) return fail(id, "길이가 다르다(${p.received}/${p.size})")
        val hex = p.digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (hex != p.sha256) return fail(id, "해시가 다르다")
        try {
            p.out.fd.sync()
            p.out.close()
        } catch (e: IOException) {
            return fail(id, "내리지 못했다")
        }
        pending = null
        return Step.Done(id, p.file, p.size)
    }

    /** 열린 저장을 버린다(쓰던 파일도). */
    fun abort() {
        val p = pending ?: return
        pending = null
        try {
            p.out.close()
        } catch (_: IOException) {
        }
        p.file.delete()
    }

    private fun fail(id: Long, why: String): Step {
        abort()
        return Step.Failed(id, why)
    }

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xff) shl 24) or ((b[at + 1].toLong() and 0xff) shl 16) or
            ((b[at + 2].toLong() and 0xff) shl 8) or (b[at + 3].toLong() and 0xff)

    companion object {
        const val HEADER = 8
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}
