package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * `META-INF/encryption.xml` 을 읽는다.
 *
 * ## 이 파일이 있다고 다 DRM 이 아니다
 *
 * 같은 파일이 두 가지를 뜻한다.
 *
 * * **DRM** — 본문이 암호화되어 있다. 방식이 공개되지 않았으므로(Adobe ADEPT 등) 열 수 없다.
 * * **글꼴 난독화**(IDPF·Adobe) — 본문은 멀쩡하고 내장 글꼴만 XOR 로 뒤섞여 있다. 방법이
 *   공개돼 있으므로 **푼다**([FontObfuscation]). 이것을 DRM 으로 보고 거절하면 멀쩡히 읽히는
 *   상업 EPUB 을 '잠겼다' 고 말하게 된다.
 *
 * 그래서 **무엇이 어떤 방식으로 걸렸는지**를 본다([plan]).
 *
 * ## 모르면 거절한다
 *
 * * `EncryptedData` 하나라도 무엇을 가리키는지 적지 않았다(`CipherReference` 가 없다). **하나라도**다 —
 *   예전 판은 파일 전체가 비었을 때만 거절해, 글꼴 하나와 가리키는 곳 없는 블록이 함께 있으면
 *   통과했다(검토가 잡았다).
 * * `CipherReference/@URI` 가 책 안의 이름으로 풀리지 않는다(책 밖을 가리키거나 망가졌다).
 *
 * 반대로 `EncryptedData` 가 **하나도 없으면** 잠긴 것이 없다 — 명세상 틀린 파일이지만 거절할 이유가
 * 아니다(`mimetype` 을 압축한 책을 받아 주는 것과 같은 판단).
 *
 * 둘 다 '아마 글꼴이겠지' 로 넘기면 본문이 암호화된 책을 깨진 글자로 보여 주게 된다. 뒤의
 * 것은 오래 구멍이었다 — 풀리지 않는 주소를 **조용히 버렸기** 때문에, 글꼴 하나와 풀리지 않는
 * 본문 주소 하나가 적힌 책이 '글꼴만 걸렸다' 로 통과했다.
 */
internal object EpubEncryption {

    /**
     * @property entries ZIP 이름 → 알고리즘 URI(적혀 있지 않으면 null).
     * @property unresolved 책 안의 이름으로 풀리지 않은 `CipherReference` 의 수.
     */
    data class Result(val entries: Map<String, String?>, val unresolved: Int) {
        val paths: Set<String> get() = entries.keys
    }

    /** 어떻게 할 것인가. */
    sealed interface Plan {
        /**
         * 연다.
         *
         * @property obfuscated 우리가 풀 글꼴(ZIP 이름 → 알고리즘).
         * @property lockedFonts 풀 수 없는 방식으로 걸린 **글꼴**의 수. 본문은 멀쩡하므로 열고,
         *   그 글꼴은 기본 글꼴로 대신 보인다는 것을 알린다.
         */
        data class Open(val obfuscated: Map<String, String>, val lockedFonts: Int) : Plan

        /** 본문이 잠겼거나 무엇이 잠겼는지 모른다. */
        data object Refuse : Plan
    }

    private val FONT_EXTENSIONS = setOf("otf", "ttf", "woff", "woff2", "ttc", "eot")

    fun parse(input: InputStream, limits: ParseLimits = ParseLimits.DEFAULT): Result {
        val parser = SafeXml.newParser(input, null, limits)
        val entries = LinkedHashMap<String, String?>()
        var unresolved = 0
        var algorithm: String? = null
        // 지금 `EncryptedData` 안에서 `CipherReference` 를 봤는가. 끝날 때 못 봤으면 '무엇이 걸렸는지
        // 모른다' 로 센다.
        var sawReference = true
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    // 방식은 `EncryptedData` 마다 하나다. 다음 것으로 넘어가면 잊는다 —
                    // 남겨 두면 앞 항목의 방식이 방식을 적지 않은 뒤 항목에 붙는다.
                    "EncryptedData" -> {
                        algorithm = null
                        sawReference = false
                    }
                    "EncryptionMethod" -> algorithm = parser.getAttributeValue(null, "Algorithm")?.trim()
                    "CipherReference" -> {
                        sawReference = true
                        val uri = parser.getAttributeValue(null, "URI")
                        // URI 는 EPUB 뿌리 기준의 상대 주소다(명세).
                        val path = uri?.takeIf { it.isNotBlank() }?.let { EpubHref.resolve("", it) }
                        if (path == null) unresolved++ else entries[path] = algorithm
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "EncryptedData") {
                    if (!sawReference) unresolved++
                    sawReference = true
                    algorithm = null
                }
            }
            event = parser.next()
        }
        return Result(entries, unresolved)
    }

    fun plan(result: Result): Plan {
        if (result.unresolved > 0) return Plan.Refuse
        val obfuscated = LinkedHashMap<String, String>()
        var lockedFonts = 0
        for ((path, algorithm) in result.entries) {
            when {
                FontObfuscation.isObfuscation(algorithm) -> obfuscated[path] = algorithm!!
                // 풀 수 없는 방식이어도 글꼴이면 본문은 읽힌다.
                isFont(path) -> lockedFonts++
                else -> return Plan.Refuse
            }
        }
        return Plan.Open(obfuscated, lockedFonts)
    }

    fun isFont(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in FONT_EXTENSIONS
}
