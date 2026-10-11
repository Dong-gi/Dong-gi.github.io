package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.mse.MseInitiator
import io.github.donggi.iroirobrowser.torrent.mse.Rc4
import java.math.BigInteger
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MseTest {

    @Test
    fun rc4Vectors() {
        for (c in PythonVectors.rc4) {
            val data = unhex(c.plaintextHex)
            Rc4(unhex(c.keyHex), c.drop).process(data)
            assertEquals(c.ciphertextHex, hex(data), "key ${c.keyHex} drop ${c.drop}")
        }
    }

    /** JDK 의 ARCFOUR(시험에서만 — 안드로이드엔 없을 수 있다)와 무작위로 견준다. 나누어 부른 흐름도 같아야 한다. */
    @Test
    fun rc4MatchesJdk() {
        val rnd = Random(42)
        repeat(50) {
            val key = ByteArray(5 + rnd.nextInt(60)).also(rnd::nextBytes) // JDK 의 ARCFOUR 는 5~128 바이트
            val data = ByteArray(rnd.nextInt(3000)).also(rnd::nextBytes)
            val jdk = Cipher.getInstance("ARCFOUR").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ARCFOUR")) }
            jdk.update(ByteArray(1024))
            val expected = jdk.update(data) ?: ByteArray(0)
            val ours = Rc4(key, 1024)
            val got = data.copyOf()
            var off = 0
            while (off < got.size) {
                val n = minOf(got.size - off, 1 + rnd.nextInt(200))
                ours.process(got, off, n)
                off += n
            }
            assertContentEquals(expected, got)
        }
    }

    /** 파이썬 벡터 — 같은 개인 키·패딩이면 메시지 1·3 이 바이트까지 같고, B 의 메시지 2·4 와 그 뒤 흐름을 풀어 같은 평문을 얻는다. */
    @Test
    fun mseVectors() {
        for (c in PythonVectors.mse) {
            val ih = unhex(c.infoHashHex)
            val a = MseInitiator(ih, BigInteger(c.xaHex, 16), unhex(c.padAHex), unhex(c.iaHex), c.cryptoProvide, unhex(c.padCHex))
            assertEquals(c.message1Hex, hex(a.message1()), "message1")
            // 메시지 2 를 둘로 나눠 넣어 본다(Yb 가 반쯤 온 때).
            val m2 = unhex(c.message2Hex)
            assertTrue(a.receive(m2, 0, 50) is MseInitiator.Result.NeedMore)
            assertNull(a.takeOutgoing())
            assertTrue(a.receive(m2, 50, m2.size - 50) is MseInitiator.Result.NeedMore)
            assertEquals(c.message3Hex, hex(a.takeOutgoing()!!), "message3")
            val tail = unhex(c.message4Hex) + unhex(c.afterBCipherHex)
            val r = a.receive(tail)
            assertTrue(r is MseInitiator.Result.Done, "done: $r")
            r as MseInitiator.Result.Done
            assertEquals(c.cryptoSelect, r.select)
            assertEquals(c.afterBPlainHex, hex(r.leftover))
            // A 가 그 뒤 보내는 것
            val out = unhex(c.afterAPlainHex)
            r.encrypt?.process(out)
            assertEquals(c.afterACipherHex, hex(out))
        }
    }

    @Test
    fun rejectsGarbage() {
        val ih = ByteArray(20) { it.toByte() }
        val rnd = Random(7)
        // VC 를 찾지 못한다(B 가 MSE 를 모르고 엉뚱한 바이트를 보낸다)
        val a = MseInitiator(ih, MseInitiator.newPrivateKey(rnd), ByteArray(0), ByteArray(0))
        val yb = MseInitiator.publicKey(MseInitiator.newPrivateKey(rnd))
        assertTrue(a.receive(yb) is MseInitiator.Result.NeedMore)
        val r = a.receive(ByteArray(600) { 0x55 })
        assertTrue(r is MseInitiator.Result.Failed)
        // Yb 가 1(약한 값)
        val b = MseInitiator(ih, MseInitiator.newPrivateKey(rnd), ByteArray(0), ByteArray(0))
        assertTrue(b.receive(ByteArray(95) + byteArrayOf(1)) is MseInitiator.Result.Failed)
    }

    @Test
    fun fixedWidthKeys() {
        val x = BigInteger("ff" + "00".repeat(95), 16)
        assertEquals(96, MseInitiator.fixed(x).size)
        assertEquals(96, MseInitiator.fixed(BigInteger.ONE).size)
        assertEquals(1, MseInitiator.fixed(BigInteger.ONE)[95].toInt())
    }
}
