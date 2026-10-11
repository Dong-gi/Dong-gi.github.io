package io.github.donggi.iroiroviewer.editor

/**
 * 편집기 화면([EditorActivity])의 판단 — **순수**(JVM 시험). 화면은 상태를 들고, 무엇을 보일지는 여기서 정한다.
 *
 * - 떠 있는 단추 묶음([floating]) — 주 단추('편집'·'다시 열기'·'다른 앱으로 열기')와 ⋮(파일 이름·'다른 앱으로 열기').
 * - 이 화면이 아는 오류([Error])마다 달 단추([actions]).
 * - 보기 → 편집이 넘기는 읽던 자리([position]) — 쪽이 지은 글을 믿지 않는다.
 * - '새 파일로 저장했습니다' 를 알릴 이름([NewFileNotices]).
 */
object EditorRules {

    /** 쪽이 문서를 열지 못한 까닭 — `open-failed` 의 `reason`(어댑터가 싣는다). 모르는 글은 [OTHER]. */
    enum class OpenFailure {
        /** 암호 물음을 닫았다 — 다시 열면 엔진이 다시 묻는다. 그래서 주 단추가 '다시 열기' 다. */
        CANCELLED,

        /** 이 편집기가 풀지 못하는 방식으로 잠겼다(공개되지 않은 암호·DRM). */
        LOCKED,

        /** 깨졌거나 이 편집기가 읽지 못하는 문서. */
        CORRUPT,

        /** 그 밖·모름. */
        OTHER,
        ;

        companion object {
            fun of(reason: String?): OpenFailure = when (reason) {
                "cancelled" -> CANCELLED
                "locked" -> LOCKED
                "corrupt" -> CORRUPT
                else -> OTHER
            }
        }
    }

    /** 오른쪽 아래에 뜨는 주 단추. */
    enum class Primary { EDIT, REOPEN, OPEN_WITH }

    /** 떠 있는 단추 묶음 — 주 단추(없을 수 있다)와 ⋮ 의 유무. 둘 다 없으면 아무것도 띄우지 않는다. */
    data class Floating(val primary: Primary?, val more: Boolean)

    /**
     * 지금 띄울 단추.
     *
     * - 열지 못했으면 — 암호를 넣지 않았을 뿐이면 '다시 열기'(같은 문서를 다시 떠 와 엔진이 다시 묻는다), 그 밖은 '다른 앱으로 열기'.
     * - 보기 모드에서 문서를 열었고([opened] — 쪽의 인사) 고쳐 둘 곳([hasSlot] — 파일 쪽의 저장 자리)이 있으면 '편집'. 받은 사본처럼 자리가
     *   없는 문서에는 없다.
     * - ⋮ 는 **고치는 중이 아닐 때** — 보기 모드에서 문서를 열었거나 열지 못했을 때 — 둔다(보기 모드에 '다른 앱으로
     *   열기' 도 파일 이름도 없었다). 고치는 동안에는 두지 않는다 — 다른 앱은 고치기 전의 파일을 받으므로 고친 것이 거기 없다. **여는 동안에도
     *   두지 않는다** — 엔진이 여는 동안 띄우는 암호 칸의 오른쪽 끝을 ⋮ 가 가렸다(2026-10-10 기기, rhwp). 그때 넘기려면 암호 물음을 닫는다 — 열지
     *   못한 화면에 ⋮ 와 '다시 열기' 가 선다. 넘길 문서(content URI — [hasDocumentUri])가 있어야 한다.
     */
    fun floating(viewMode: Boolean, opened: Boolean, failure: OpenFailure?, hasSlot: Boolean, hasDocumentUri: Boolean): Floating {
        val primary = when {
            failure == OpenFailure.CANCELLED -> Primary.REOPEN
            failure != null -> if (hasDocumentUri) Primary.OPEN_WITH else null
            viewMode && opened && hasSlot -> Primary.EDIT
            else -> null
        }
        return Floating(primary, more = hasDocumentUri && ((viewMode && opened) || failure != null))
    }

    /** 쪽이 아니라 이 화면이 아는 오류(엔진을 띄우기 전·렌더러가 죽은 뒤). */
    enum class Error {
        /** 이 앱이 띄우지 않았다. */
        CALLER,

        /** 인텐트에 문서·형식이 없다. */
        NO_DOCUMENT,

        /** 고치러 왔는데 저장 자리를 믿을 수 없다. */
        SLOT,

        /** 이 빌드에 그 형식의 엔진 묶음이 없다. */
        NO_ENGINE,

        /** 기기의 WebView 가 통로(WebMessageListener·ArrayBuffer)를 모른다. */
        WEBVIEW,

        /** 문서가 상한(512 MiB)을 넘는다. */
        TOO_LARGE,

        /** 넘겨받은 문서를 읽지 못했다(지워졌다·옮겨졌다·캐시에 쓸 자리가 없다). */
        READ,

        /** 편집기의 렌더러가 죽었다(대개 메모리 — 큰 문서). */
        RENDERER,
    }

    /** 오류 화면의 단추. */
    enum class Action { REOPEN, OPEN_WITH, CLOSE }

    /**
     * [error] 의 단추(위에서 아래로). **문서 탓**(이 기기·이 빌드의 편집기로는 보이지 못한다)이면 '다른 앱으로 열기', **다시 해 볼 만하면**
     * '다시 열기' — 같은 문서를 다시 떠 와 새 편집기로. 이 앱이 띄우지 않았거나 인텐트가 깨졌으면 '닫기' 뿐이다
     * — 넘길 문서를 믿을 수 없다. '닫기' 는 언제나 끝에.
     */
    fun actions(error: Error, hasDocumentUri: Boolean): List<Action> {
        val main = when (error) {
            Error.CALLER, Error.NO_DOCUMENT, Error.SLOT -> emptyList()
            Error.NO_ENGINE, Error.WEBVIEW, Error.TOO_LARGE -> listOf(Action.OPEN_WITH)
            Error.READ, Error.RENDERER -> listOf(Action.REOPEN, Action.OPEN_WITH)
        }
        return main.filter { it != Action.OPEN_WITH || hasDocumentUri } + Action.CLOSE
    }

    /** 렌더러가 죽었을 때 '저장하지 않은 고침은 잃었을 수 있다' 를 말하는가 — 고치던 것이 있었을 때만(보기 모드에서는 잃을 것이 없다). */
    fun rendererLostEdits(editing: Boolean, dirty: Boolean): Boolean = editing && dirty

    /** 읽던 자리의 꼴 — 영문 소문자 하나, 쌍점, 숫자·점·쉼표(어댑터가 짓는다 — rhwp `s:<위>,<왼쪽>`, ONLYOFFICE `p:<쪽>`·`c:<시트>,<행>,<열>`). */
    private val POSITION = Regex("[a-z]:[0-9.,]{1,60}")

    /**
     * 보기 쪽 어댑터가 알린 읽던 자리 — 그 꼴이면 그대로(편집 쪽 어댑터가 푼다), 아니면 null. 첫 쪽의 주소(질의)에 실리므로 꼴 밖의 글은
     * 넘기지 않는다 — 쪽은 남이 만든 문서를 다룬다.
     */
    fun position(raw: String?): String? = raw?.takeIf { POSITION.matches(it) }

    /**
     * '새 파일로 저장했습니다' 를 알릴 이름 — 형식·이름을 바꿔 저장하면 파일 쪽이 원본 곁에 새 파일을 만든다. **이 화면에서 그 이름을 처음 알릴 때만**
     * 말한다 — 같은 세션의 다음 저장은 그 새 파일을 고쳐 쓸 뿐이다(저장할 때마다 같은 토스트가 떴다).
     */
    class NewFileNotices(private val original: String) {
        private val told = HashSet<String>()

        /** 알릴 이름, 알릴 것이 없으면 null — 원본 이름 그대로 저장했거나 이미 알렸다. */
        fun toAnnounce(saved: String?): String? {
            if (saved == null || saved == original) return null
            return saved.takeIf { told.add(it) }
        }
    }
}
