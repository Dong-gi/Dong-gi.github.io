package io.github.donggi.iroirobrowser.tether.core

/**
 * 핫스팟 패스프레이즈를 쥐는 곳 — 잠근 저장값(앱은 SharedPreferences 의 블롭 + AndroidKeyStore 의 기기 키)과 **이 프로세스의 메모리**.
 *
 * ## 왜 메모리에 드는가
 *
 * 테더 화면과 서비스는 같은 프로세스(`:tether`)에 있고, 화면이 값을 저장한 뒤 서비스가 다시 읽어 그룹을 만든다. 예전에는 둘 다 저장값만 보았다:
 *
 *  - 기기 키로 **잠그지 못하면**(키 저장소의 일시 오류 등) 저장값을 지웠고, 서비스는 값이 없다고 보아 **새 난수**로 그룹을 세웠다 — 화면에 보이는
 *    값과 실제 그룹의 패스프레이즈가 달라 PC 가 붙지 못한다.
 *  - 잠근 값을 **한 번 열지 못하면** 없는 것으로 보아 새 난수로 **덮어썼다** — 사용자가 정한 값이 사라진다(일시 오류여도).
 *
 * 이제 마지막으로 정하거나 연 값을 메모리에 들고 화면과 서비스가 그것을 함께 쓴다(앱이 이 객체를 프로세스에 하나만 둔다). 잠그지 못해도 이
 * 프로세스에서는 그 값을 쓰고([Current.Ready.saved] 가 false — 화면이 '저장하지 못했다' 를 알린다), 잠근 값이 있는데 열지 못하면 덮어쓰지 않고
 * [Current.Unreadable] 로 알린다(화면은 새 값을 넣게 하고, 타일의 길은 핫스팟을 켜지 않고 까닭을 보인다).
 *
 * 저장값이 약하거나 규칙에 맞지 않으면([Passphrases.passphraseProblem]) 없는 것으로 친다 — 새로 만든다(usb-tether 의 옛 기본값 `12345678` 을
 * 그대로 옮겨 오지 않으려고. 그대로 쓰면 켤 때마다 '뻔한 패스프레이즈' 로 막힌다).
 */
class PassphraseVault(
    private val store: Store,
    private val generate: () -> String = { Passphrases.generate() },
) {
    /** 잠근 값을 두는 곳과 자물쇠. */
    interface Store {
        /** 잠근 블롭. 없으면 null. */
        fun readSealed(): String?

        /** 잠근 블롭을 쓴다. 쓰지 못하면 false. */
        fun writeSealed(sealed: String): Boolean

        /** 잠근다. 못 하면 null(평문으로 두지 않는다). */
        fun seal(plain: String): String?

        /** 연다. 못 하면 null(키가 사라졌다·꼴이 틀렸다·키 저장소의 일시 오류). */
        fun open(sealed: String): String?
    }

    /** [current] 의 답. */
    sealed interface Current {
        /** 쓸 값. [saved] 가 false 면 기기에 잠가 두지 못했다 — 이 프로세스가 살아 있는 동안만 이 값이다. */
        data class Ready(val passphrase: String, val saved: Boolean) : Current

        /** 잠근 값이 있는데 열지 못했다 — 덮어쓰지 않았다. 사용자가 새 값을 넣어야 한다(다음에 다시 열어 볼 수도 있다). */
        data object Unreadable : Current
    }

    @Volatile
    private var remembered: Current.Ready? = null

    /** 지금 쓸 값 — 메모리에 있으면 그것, 없으면 저장값을 연다. 저장값이 없으면 새로 만들어 저장한다. */
    @Synchronized
    fun current(): Current {
        remembered?.let { return it }
        val sealed = store.readSealed()
        if (sealed != null) {
            val opened = store.open(sealed) ?: return Current.Unreadable
            if (Passphrases.passphraseProblem(opened) == null) return Current.Ready(opened, saved = true).also { remembered = it }
            // 약하거나 규칙에 맞지 않는 값 — 없는 것으로 친다(아래에서 새로 만든다).
        }
        return set(generate())
    }

    /**
     * 사용자가 정한 값(부르는 쪽이 [Passphrases.check] 를 이미 보았다)을 메모리에 두고 잠가 저장한다. 잠그거나 쓰지 못해도 **이 프로세스에서는
     * 이 값을 쓴다** — 앞의 저장값은 지우지 않는다(지우면 다음에 새 난수가 되어 더 멀어진다).
     */
    @Synchronized
    fun set(passphrase: String): Current.Ready {
        val sealed = store.seal(passphrase)
        val saved = sealed != null && store.writeSealed(sealed)
        return Current.Ready(passphrase, saved).also { remembered = it }
    }
}
