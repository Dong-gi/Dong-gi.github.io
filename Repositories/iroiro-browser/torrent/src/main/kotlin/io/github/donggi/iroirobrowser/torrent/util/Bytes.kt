package io.github.donggi.iroirobrowser.torrent.util

import java.nio.Buffer
import java.nio.ByteBuffer
import java.security.MessageDigest

/** 16진 글 — `java.util.HexFormat` 은 안드로이드 API 34 부터라 쓰지 않는다. */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(DIGITS[(b.toInt() shr 4) and 0xF]).append(DIGITS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    /** 짝수 길이의 16진 글(대소문자 무관). 아니면 null. */
    fun decode(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[2 * i], 16)
            val lo = Character.digit(s[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}

/** RFC 4648 base32(마그넷의 32자 info-hash). 덧채움(`=`) 없이, 대소문자 무관. */
object Base32 {
    fun decode(s: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0L
        var bits = 0
        for (ch in s) {
            val v = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a'
                in '2'..'7' -> ch - '2' + 26
                else -> return null
            }
            buffer = (buffer shl 5) or v.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write(((buffer shr bits) and 0xFF).toInt())
            }
        }
        return out.toByteArray()
    }
}

object Sha1 {
    fun digest(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        for (p in parts) md.update(p)
        return md.digest()
    }

    fun digest(data: ByteArray, offset: Int, length: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(data, offset, length)
        return md.digest()
    }
}

internal fun ByteArray.readIntBE(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

internal fun ByteArray.readLongBE(at: Int): Long =
    (readIntBE(at).toLong() shl 32) or (readIntBE(at + 4).toLong() and 0xFFFFFFFFL)

internal fun ByteArray.readUShortBE(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

internal fun ByteArray.writeIntBE(at: Int, v: Int) {
    this[at] = (v ushr 24).toByte()
    this[at + 1] = (v ushr 16).toByte()
    this[at + 2] = (v ushr 8).toByte()
    this[at + 3] = v.toByte()
}

internal fun ByteArray.writeLongBE(at: Int, v: Long) {
    writeIntBE(at, (v ushr 32).toInt())
    writeIntBE(at + 4, v.toInt())
}

internal fun ByteArray.writeShortBE(at: Int, v: Int) {
    this[at] = (v ushr 8).toByte()
    this[at + 1] = v.toByte()
}

/*
 * ByteBuffer 의 flip·clear·position(int) 은 JDK 9 부터 ByteBuffer 를 돌려주는 판(공변 반환)이 따로 있다. JDK 21 로 컴파일하면 그 판을
 * 부르게 되는데 안드로이드의 표(api-versions.xml)에는 없다 — 기기에서 NoSuchMethodError 로 죽는다(tools/check_api_level.py 가 잡았다).
 * Buffer 로 올려 불러 API 1 의 판을 쓴다.
 */
internal fun ByteBuffer.flipBuffer(): ByteBuffer {
    (this as Buffer).flip()
    return this
}

internal fun ByteBuffer.clearBuffer(): ByteBuffer {
    (this as Buffer).clear()
    return this
}

internal fun ByteBuffer.moveTo(position: Int): ByteBuffer {
    (this as Buffer).position(position)
    return this
}
