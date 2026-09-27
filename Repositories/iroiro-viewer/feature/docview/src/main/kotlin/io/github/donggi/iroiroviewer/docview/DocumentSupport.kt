package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource

/**
 * **네 번째 이음매.** 이 화면이 '무엇을 여는가' 를 스스로 정하지 않게 한다.
 *
 * ## 왜 필요한가
 *
 * 12·13단계가 docx·xlsx·pptx·HWPX·HWP 를 붙이면 이 화면이 여는 포맷이 일곱이 된다.
 * 그런데 의존 표는 `feature:*` 이 **포맷 모듈 하나**만 보도록 정해 두었고, 그것은
 * 빌드 그래프를 나무로 유지하는 규칙이다. 화면이 일곱을 직접 보면 규칙이 무너진다.
 *
 * `ExtractSupport`(8단계)·`CoverSupport`(9단계)·`PlayerSupport`(10단계)와 같은 모양이다 —
 * **포맷을 전혀 모르는 함수 하나**를 소비자 쪽에 두고, 조립은 `app` 이 한다.
 * (`app/FormatRegistry.kt` 가 그 자리이고, 2단계가 계획에 적어 두고 11단계까지 비어
 * 있던 파일이다.)
 *
 * ## 이 화면이 `format:epub` 을 여전히 보는 이유
 *
 * 이음매가 주는 것은 **여는 법**이고, EPUB 화면이 필요한 것은 그 뒤의 **읽는 법**
 * (차례·목차·장 HTML)이다. 그쪽은 `EpubBook` 의 API 라 타입을 봐야 한다. 그것이
 * 의존 표가 허락한 '자기 포맷 모듈 하나' 다. PDF 는 포맷 모듈이 아예 없다
 * (`android.graphics.pdf` 라 순수 JVM 에 둘 수 없다).
 */
object DocumentSupport {

    /**
     * 이 원본을 열 수 있는 것을 고른다. 모르면 null.
     *
     * **확장자를 믿지 않는다.** 판별은 `format:api` 의 `FormatProbe` 가 하고, 그것은
     * 매직 바이트와 ZIP 엔트리 이름을 본다 — 2단계가 그 계약을 세워 두고 11단계까지
     * 부르는 코드가 없었다.
     *
     * [password] 는 사용자가 넣은 암호다(없으면 null). **여는이가 받아 쓰기만 하고 지우지
     * 않는다** — 배열의 주인은 부르는 쪽이고, 여는 일이 끝나면 거기서 지운다. 암호를 받지
     * 않는 포맷(EPUB)의 여는이는 그냥 버린다.
     */
    fun interface Registry {
        fun openerFor(source: DocumentSource, password: CharArray?): DocumentOpener?
    }

    /**
     * `app` 이 꽂기 전의 기본값.
     *
     * **비어 있는 것이 기본값이다.** 여기에 PDF 를 기본으로 넣어 두면 `app` 이 꽂는 것을
     * 잊어도 화면이 반만 도는 상태가 되고, 그 상태는 시험에서 통과한다.
     */
    @Volatile
    var registry: Registry = Registry { _, _ -> null }
}
