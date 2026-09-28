package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind

/**
 * 다른 앱에 **열어 달라고** 넘길 때의 MIME.
 *
 * 공유(`ShareHelper`)는 [MimeResolver.mimeOf] 를 그대로 쓴다 — 모르면 `application/octet-stream` 이고, 받는 쪽이
 * 스스로 가려 받는다. 열기(`ACTION_VIEW`)는 사정이 다르다. 안드로이드는 인텐트의 MIME 과 **앱이 선언한 필터**를
 * 맞춰 보고 맞는 앱만 고르는 창에 올리므로, `octet-stream` 을 주면 그 MIME 을 선언한 드문 앱 말고는 아무도 나오지
 * 않는다 — `.flv` 를 VLC 로 열고 싶은 사람에게 받을 앱이 없다는 답이 돌아온다(`ExternalOpen` 이 그때 모든 앱으로
 * 넓히기는 하지만, 그 목록은 형식을 아는 앱과 모르는 앱이 뒤섞인 것이다). 그래서 차례대로 좁혀 간다:
 *
 * 1. **우리 표**([MimeResolver.mimeOf]). 이 앱이 이름을 아는 형식.
 * 2. **플랫폼 표**(`MimeTypeMap`, [resolve] 의 `platform`). 기기마다 조금씩 다르고 우리가 모르는 확장자를 안다.
 *    `octet-stream` 과 모양이 이상한 값(매개변수·공백·와일드카드)은 모르는 것으로 친다.
 * 3. **종류의 대표**. 그림·소리·영상은 그 계열 전체(뒤를 별표로 둔 값), 글·코드는 `text/plain`.
 * 4. **모든 것**([ANY]). 필터에 MIME 을 하나라도 선언한 앱이 모두 나온다.
 *
 * ## 계열 안에 들어야 한다
 *
 * 그림·소리·영상을 받는 앱은 거의 다 계열 전체(`video` 뒤에 별표)로 필터를 선언한다. 그 필터는 `video/x-flv` 는
 * 받지만 **`application/…` 은 받지 않는다**(`IntentFilter.findMimeType` 이 앞부분을 글자로 견준다). 그래서 1·2의
 * 답이 종류의 계열 밖이면 쓰지 않고 3으로 간다 — `.rmvb` 의 등록 MIME(`application/vnd.rn-realmedia-vbr`)을
 * 주면 영상 앱이 하나도 안 나오고, 계열 전체를 주면 영상 앱이 모두 나와 사용자가 고른다. 공유는 등록 MIME 을
 * 그대로 쓴다(받는 쪽이 대개 모든 것을 받는다).
 *
 * **글·코드는 `text/plain` 이다.** 편집기가 선언하는 것이 그것(과 `text` 계열 전체)이라서다. 플랫폼 표는
 * `.py` 를 `text/x-python`, `.js` 를 `application/javascript` 로 주는데(API 31 에뮬레이터 이미지의 표를 직접
 * 읽었다), 앞의 것은 `text/plain` 만 선언한 편집기에 닿지 않고 뒤의 것은 `text` 계열 필터에도 닿지 않는다.
 * `text/html`·`text/xml`·`text/csv` 처럼 우리 표가 `text` 계열로 적은 것은 그대로 둔다(브라우저·표 앱이 받는다).
 * `.json`(`application/json`)은 계열 밖이라 `text/plain` 으로 연다.
 *
 * ## 와일드카드는 언제나 고르는 창이다
 *
 * [isWildcard] 인 MIME 으로 시스템의 '다음으로 열기' 를 띄우면 '항상' 을 누를 수 있고, 그 선택은 **인텐트의 MIME
 * 그대로** 선호 필터가 된다 — 계열 전체면 그 계열의 모든 파일이, [ANY] 면 MIME 이 붙은 모든 열기가 그 앱으로
 * 간다. 받는 앱이 이 파일을 못 열 수도 있는 자리에서 그런 기본값이 생기면 안 되므로 `ExternalOpen` 은 이때 언제나
 * `createChooser`(기본값을 정할 수 없는 창)로 띄운다.
 *
 * `android.*` 를 쓰지 않는다(플랫폼 표는 함수로 받는다) — JVM 시험이 차례를 박는다.
 */
object ExternalMime {

    const val ANY = "*/*"
    private const val OCTET = "application/octet-stream"
    private const val TEXT_PLAIN = "text/plain"

    /** 가장 좁게 말할 수 있는 MIME. [platform] 은 소문자 확장자(점 없음)를 받아 플랫폼이 아는 MIME 을 준다. */
    fun resolve(name: String, platform: (String) -> String?): String {
        val kind = MimeResolver.kindOf(name, isDirectory = false)
        val family = familyOf(kind)
        val ours = MimeResolver.mimeOf(name)
        if (ours != OCTET && fits(ours, family)) return ours
        val ext = MimeResolver.extensionOf(name)
        if (ext.isNotEmpty()) {
            val theirs = platform(ext)?.trim()?.lowercase()
            if (theirs != null && theirs != OCTET && isConcrete(theirs) && fits(theirs, family)) return theirs
        }
        return when (kind) {
            FileKind.IMAGE -> "image/*"
            FileKind.AUDIO -> "audio/*"
            FileKind.VIDEO -> "video/*"
            FileKind.TEXT, FileKind.CODE -> TEXT_PLAIN
            else -> ANY
        }
    }

    /**
     * 계열 전체(뒤가 별표)나 모든 것([ANY])인가. 그런 MIME 으로는 기본 앱을 정하게 두지 않는다.
     * (이 주석에 MIME 와일드카드를 글자 그대로 적지 않는다 — 코틀린의 블록 주석은 겹쳐서, 빗금 뒤 별표가 주석을 하나 더 연다.)
     */
    fun isWildcard(mime: String): Boolean = mime.endsWith("/*")

    /** 받는 앱의 필터가 계열로 걸러 보는 종류면 그 앞부분(`image/` 등). 가리지 않는 종류는 null. */
    private fun familyOf(kind: FileKind): String? = when (kind) {
        FileKind.IMAGE -> "image/"
        FileKind.AUDIO -> "audio/"
        FileKind.VIDEO -> "video/"
        FileKind.TEXT, FileKind.CODE -> "text/"
        else -> null
    }

    private fun fits(mime: String, family: String?): Boolean = family == null || mime.startsWith(family)

    /** `종류/형식` 모양이고 와일드카드가 아닌가. 플랫폼 표가 이상한 값을 주면 쓰지 않는다. */
    private fun isConcrete(mime: String): Boolean {
        val slash = mime.indexOf('/')
        if (slash <= 0 || slash == mime.length - 1 || mime.indexOf('/', slash + 1) >= 0) return false
        if ('*' in mime) return false
        return mime.none { it.isWhitespace() || it == ';' }
    }
}
