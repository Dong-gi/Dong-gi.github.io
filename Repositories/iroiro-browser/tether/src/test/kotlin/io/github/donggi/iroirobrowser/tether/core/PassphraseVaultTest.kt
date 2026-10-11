package io.github.donggi.iroirobrowser.tether.core

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 패스프레이즈 보관 — 기기 키가 실패해도 화면과 서비스가 **같은 값**을 쓰고, 열지 못한 저장값을 **덮어쓰지 않는다**. 기기 키
 * (AndroidKeyStore)와 SharedPreferences 는 가짜로 바꾼다.
 */
class PassphraseVaultTest {

    /** 블롭 한 칸과 자물쇠. 잠그기·열기·쓰기의 실패를 손으로 켠다. 잠근 꼴은 `sealed:` + 평문(시험이라 감추지 않는다). */
    private class FakeStore(var blob: String? = null) : PassphraseVault.Store {
        var sealFails = false
        var openFails = false
        var writeFails = false
        val writes = CopyOnWriteArrayList<String>()

        override fun readSealed(): String? = blob

        override fun writeSealed(sealed: String): Boolean {
            if (writeFails) return false
            writes += sealed
            blob = sealed
            return true
        }

        override fun seal(plain: String): String? = if (sealFails) null else "sealed:$plain"

        override fun open(sealed: String): String? = if (openFails) null else sealed.removePrefix("sealed:")
    }

    private val strong = "k7Qm2xPa9Ld4Rt"

    @Test
    fun 잠그지_못해도_이_프로세스는_사용자가_정한_값을_쓴다() {
        // 예전에는 잠그기가 실패하면 저장값을 지웠고, 서비스가 '없다' 고 보아 새 난수로 그룹을 세웠다 — 화면의 값과 그룹이 어긋났다.
        val store = FakeStore().apply { sealFails = true }
        val generated = CopyOnWriteArrayList<String>()
        val vault = PassphraseVault(store) { "generated-${generated.size}".also { generated += it } }
        assertEquals(PassphraseVault.Current.Ready(strong, saved = false), vault.set(strong))
        // 서비스가 읽는 길(같은 프로세스의 같은 보관소).
        assertEquals(PassphraseVault.Current.Ready(strong, saved = false), vault.current())
        assertTrue(generated.isEmpty(), "새 난수를 만들지 않았다")
    }

    @Test
    fun 쓰기가_실패해도_같다() {
        val store = FakeStore(blob = "sealed:OldValue9xYz").apply { writeFails = true }
        val vault = PassphraseVault(store) { error("만들지 않는다") }
        assertEquals(false, vault.set(strong).saved)
        assertEquals(strong, (vault.current() as PassphraseVault.Current.Ready).passphrase)
        assertEquals("sealed:OldValue9xYz", store.blob, "앞의 저장값은 지우지 않는다")
    }

    @Test
    fun 잠근_값을_열지_못하면_덮어쓰지_않고_알린다() {
        // 예전에는 한 번 열지 못하면 새 난수로 덮어써 사용자가 정한 값이 사라졌다(일시 오류여도).
        val store = FakeStore(blob = "sealed:$strong").apply { openFails = true }
        val vault = PassphraseVault(store) { error("만들지 않는다") }
        assertEquals(PassphraseVault.Current.Unreadable, vault.current())
        assertEquals(PassphraseVault.Current.Unreadable, vault.current(), "기억하지 않는다 — 다음에 다시 열어 본다")
        assertTrue(store.writes.isEmpty())
        assertEquals("sealed:$strong", store.blob)
        // 키 저장소가 돌아왔다 — 저장한 값을 그대로 연다.
        store.openFails = false
        assertEquals(PassphraseVault.Current.Ready(strong, saved = true), vault.current())
    }

    @Test
    fun 열지_못한_뒤_사용자가_새_값을_넣으면_그것을_쓴다() {
        val store = FakeStore(blob = "sealed:$strong").apply { openFails = true }
        val vault = PassphraseVault(store) { error("만들지 않는다") }
        assertEquals(PassphraseVault.Current.Unreadable, vault.current())
        vault.set("Zz9yXw8vUt7s")
        assertEquals(PassphraseVault.Current.Ready("Zz9yXw8vUt7s", saved = true), vault.current())
        assertEquals("sealed:Zz9yXw8vUt7s", store.blob)
    }

    @Test
    fun 저장값이_없으면_만들어_저장하고_그_값을_계속_쓴다() {
        val store = FakeStore()
        var made = 0
        val vault = PassphraseVault(store) { made++; Passphrases.generate() }
        val first = vault.current() as PassphraseVault.Current.Ready
        assertEquals(true, first.saved)
        assertEquals(first, vault.current())
        assertEquals(1, made)
        assertEquals("sealed:${first.passphrase}", store.blob)
        // 다른 프로세스가 다시 떠도(새 보관소) 저장값을 연다.
        assertEquals(first.passphrase, (PassphraseVault(store) { error("만들지 않는다") }.current() as PassphraseVault.Current.Ready).passphrase)
    }

    @Test
    fun 약하거나_규칙에_맞지_않는_저장값은_없는_것으로_친다() {
        for (bad in listOf("12345678", "한글패스프레이즈임", "short")) {
            val store = FakeStore(blob = "sealed:$bad")
            val vault = PassphraseVault(store) { strong }
            assertEquals(PassphraseVault.Current.Ready(strong, saved = true), vault.current(), bad)
            assertEquals("sealed:$strong", store.blob)
        }
    }
}
