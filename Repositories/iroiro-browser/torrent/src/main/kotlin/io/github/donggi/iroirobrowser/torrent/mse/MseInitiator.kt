package io.github.donggi.iroirobrowser.torrent.mse

import io.github.donggi.iroirobrowser.torrent.util.Sha1
import java.math.BigInteger
import java.util.Random

/**
 * MSE/PE(Message Stream Encryption — Vuze·libtorrent·Transmission 이 쓰는 꼴)의 **시작하는 쪽(A)** 만. 이 엔진은 나가는 연결만 하므로
 * 받는 쪽(B)이 없다. 블록하지 않는 상태 기계라 셀렉터 위에서 돈다 — 받은 바이트를 [receive] 에 넣고, 보낼 것이 생기면 [takeOutgoing].
 *
 * ```
 * 1 A→B: Ya, PadA
 * 2 B→A: Yb, PadB
 * 3 A→B: SHA1('req1', S), SHA1('req2', SKEY) xor SHA1('req3', S), ENC(VC, crypto_provide, len(PadC), PadC, len(IA)), ENC(IA)
 * 4 B→A: ENC(VC, crypto_select, len(PadD), PadD), ENC2(흐름)
 * ```
 * P 는 768비트 소수, G = 2, 공개값과 S 는 96바이트 big-endian, SKEY = info-hash. A 가 보낼 때 RC4(SHA1('keyA', S, SKEY)), 받을 때
 * RC4(SHA1('keyB', S, SKEY)), 둘 다 키 흐름의 앞 1024 바이트를 버린다. VC 는 0 여덟 바이트 — B 의 PadB(0~512) 뒤에서 ENC(VC) 를 찾아
 * 동기를 맞춘다. IA 에 BitTorrent 핸드셰이크를 실어 왕복 하나를 줄인다.
 *
 * 우리는 **RC4 만 내민다**(`crypto_provide = 0x02`) — 둘 다 내밀면 libtorrent 의 기본 설정(`prefer_rc4` 꺼짐)은 평문을 고르고, 그러면 머리만
 * 가리고 나머지는 평문이 된다. 상대가 MSE 를 모르거나 RC4 를 받지 않으면 연결이 끊기고, 엔진은 같은 피어에 평문으로 다시 붙는다.
 */
class MseInitiator(
    private val infoHash: ByteArray,
    private val privateKey: BigInteger,
    private val padA: ByteArray,
    /** IA — 실어 보낼 첫 바이트(BitTorrent 핸드셰이크). */
    private val initialPayload: ByteArray,
    private val cryptoProvide: Int = CRYPTO_RC4,
    /** PadC — 명세는 0 으로 채운 0~512 바이트. 엔진은 빈 것을 쓴다(시험이 벡터와 맞추려고 준다). */
    private val padC: ByteArray = ByteArray(0),
) {
    sealed class Result {
        object NeedMore : Result()

        /** 핸드셰이크가 끝났다. [leftover] 는 그 뒤에 이미 온 흐름(풀어 둔 것). 평문을 골랐으면 두 RC4 는 null. */
        class Done(val select: Int, val decrypt: Rc4?, val encrypt: Rc4?, val leftover: ByteArray) : Result()

        class Failed(val reason: String) : Result()
    }

    private enum class Stage { AWAIT_YB, AWAIT_VC, AWAIT_SELECT, AWAIT_PADD, DONE, FAILED }

    private var stage = Stage.AWAIT_YB
    private var buf = ByteArray(1024)
    private var size = 0
    private var pos = 0
    private var outgoing: ByteArray? = null
    private lateinit var rc4In: Rc4
    private lateinit var rc4Out: Rc4
    private lateinit var vcPattern: ByteArray
    private var select = 0
    private var padDLength = 0

    init {
        require(infoHash.size == 20)
        require(padA.size <= MAX_PAD)
        require(initialPayload.size <= 0xFFFF)
        require(padC.size <= MAX_PAD)
        require(cryptoProvide and (CRYPTO_PLAIN or CRYPTO_RC4) != 0)
    }

    /** 첫 메시지(Ya ‖ PadA) — 연결되자마자 보낸다. */
    fun message1(): ByteArray = publicKey(privateKey) + padA

    /** 보낼 것(메시지 3)이 생겼으면 한 번 돌려준다. */
    fun takeOutgoing(): ByteArray? = outgoing.also { outgoing = null }

    fun receive(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Result {
        if (stage == Stage.FAILED) return Result.Failed("already failed")
        check(stage != Stage.DONE)
        append(data, offset, length)
        return step()
    }

    private fun append(data: ByteArray, offset: Int, length: Int) {
        if (size + length > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + length))
        System.arraycopy(data, offset, buf, size, length)
        size += length
    }

    private fun step(): Result {
        while (true) {
            when (stage) {
                Stage.AWAIT_YB -> {
                    if (size < KEY_BYTES) return Result.NeedMore
                    val yb = BigInteger(1, buf.copyOfRange(0, KEY_BYTES))
                    if (yb <= BigInteger.ONE || yb >= P.subtract(BigInteger.ONE)) return fail("bad Yb")
                    val s = fixed(yb.modPow(privateKey, P))
                    rc4Out = Rc4(Sha1.digest("keyA".toByteArray(), s, infoHash), 1024)
                    rc4In = Rc4(Sha1.digest("keyB".toByteArray(), s, infoHash), 1024)
                    vcPattern = ByteArray(8).also { rc4In.process(it) }
                    val req1 = Sha1.digest("req1".toByteArray(), s)
                    val req2 = Sha1.digest("req2".toByteArray(), infoHash)
                    val req3 = Sha1.digest("req3".toByteArray(), s)
                    val xored = ByteArray(20) { (req2[it].toInt() xor req3[it].toInt()).toByte() }
                    // VC(8) · crypto_provide(4) · len(PadC)(2) · PadC · len(IA)(2) · IA — 한 RC4 흐름으로
                    val enc = ByteArray(8 + 4 + 2 + padC.size + 2 + initialPayload.size)
                    enc[8] = (cryptoProvide ushr 24).toByte()
                    enc[9] = (cryptoProvide ushr 16).toByte()
                    enc[10] = (cryptoProvide ushr 8).toByte()
                    enc[11] = cryptoProvide.toByte()
                    enc[12] = (padC.size ushr 8).toByte()
                    enc[13] = padC.size.toByte()
                    System.arraycopy(padC, 0, enc, 14, padC.size)
                    val ia = 14 + padC.size
                    enc[ia] = (initialPayload.size ushr 8).toByte()
                    enc[ia + 1] = initialPayload.size.toByte()
                    System.arraycopy(initialPayload, 0, enc, ia + 2, initialPayload.size)
                    rc4Out.process(enc)
                    outgoing = req1 + xored + enc
                    pos = KEY_BYTES
                    stage = Stage.AWAIT_VC
                }
                Stage.AWAIT_VC -> {
                    val found = indexOf(vcPattern, KEY_BYTES)
                    if (found < 0) {
                        return if (size - KEY_BYTES >= MAX_PAD + 8) fail("no VC") else Result.NeedMore
                    }
                    pos = found + 8
                    stage = Stage.AWAIT_SELECT
                }
                Stage.AWAIT_SELECT -> {
                    if (size - pos < 6) return Result.NeedMore
                    rc4In.process(buf, pos, 6)
                    select = ((buf[pos].toInt() and 0xFF) shl 24) or ((buf[pos + 1].toInt() and 0xFF) shl 16) or
                        ((buf[pos + 2].toInt() and 0xFF) shl 8) or (buf[pos + 3].toInt() and 0xFF)
                    padDLength = ((buf[pos + 4].toInt() and 0xFF) shl 8) or (buf[pos + 5].toInt() and 0xFF)
                    pos += 6
                    if (select != CRYPTO_RC4 && select != CRYPTO_PLAIN) return fail("bad crypto_select $select")
                    if (select and cryptoProvide == 0) return fail("crypto_select not provided")
                    if (padDLength > MAX_PAD) return fail("PadD too long")
                    stage = Stage.AWAIT_PADD
                }
                Stage.AWAIT_PADD -> {
                    if (size - pos < padDLength) return Result.NeedMore
                    rc4In.process(buf, pos, padDLength)
                    pos += padDLength
                    stage = Stage.DONE
                    val leftover = buf.copyOfRange(pos, size)
                    buf = ByteArray(0)
                    return if (select == CRYPTO_RC4) {
                        rc4In.process(leftover)
                        Result.Done(select, rc4In, rc4Out, leftover)
                    } else {
                        Result.Done(select, null, null, leftover)
                    }
                }
                Stage.DONE, Stage.FAILED -> error("unreachable")
            }
        }
    }

    /** PadB 는 512 바이트를 넘지 않는다 — 그 밖에서 찾은 것은 받지 않는다. */
    private fun indexOf(pattern: ByteArray, from: Int): Int {
        var i = from
        val end = minOf(size, from + MAX_PAD + pattern.size)
        while (i + pattern.size <= end) {
            var k = 0
            while (k < pattern.size && buf[i + k] == pattern[k]) k++
            if (k == pattern.size) return i
            i++
        }
        return -1
    }

    private fun fail(reason: String): Result {
        stage = Stage.FAILED
        return Result.Failed(reason)
    }

    companion object {
        const val CRYPTO_PLAIN = 0x01
        const val CRYPTO_RC4 = 0x02
        const val KEY_BYTES = 96
        const val MAX_PAD = 512

        val P = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF2" +
                "5F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563",
            16,
        )
        private val G = BigInteger.valueOf(2)

        /** 160비트 개인 키(명세가 권하는 길이) — 맨 윗 비트를 켜 128비트 아래로 떨어지지 않게. */
        fun newPrivateKey(random: Random): BigInteger = BigInteger(160, random).setBit(159)

        /** PadA — 0~512 바이트의 무작위. */
        fun newPad(random: Random): ByteArray = ByteArray(random.nextInt(MAX_PAD + 1)).also(random::nextBytes)

        fun publicKey(privateKey: BigInteger): ByteArray = fixed(G.modPow(privateKey, P))

        /** 96바이트 big-endian(앞을 0 으로 채운다). */
        fun fixed(x: BigInteger): ByteArray {
            val raw = x.toByteArray()
            return when {
                raw.size == KEY_BYTES -> raw
                raw.size > KEY_BYTES -> raw.copyOfRange(raw.size - KEY_BYTES, raw.size)
                else -> ByteArray(KEY_BYTES - raw.size) + raw
            }
        }
    }
}
