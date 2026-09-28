package io.github.donggi.iroiroviewer

import androidx.annotation.RawRes
import androidx.annotation.StringRes

/**
 * 고지 화면의 차례. **화면(`NoticeScreen`)과 따로 둔 까닭은 JVM 시험이다** — 버전 카탈로그의 라이브러리가 모두 여기 어느
 * 절에 들어 있는지, 한컴 문장이 명세가 요구한 글자 그대로인지를 시험이 본다(`NoticeCatalogTest`).
 *
 * ## 무엇을 적는가 — 릴리스 APK 에 실리는 서드파티 전부
 *
 * 목록은 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 에서 얻었다(14단계, 2026-09-28 — 좌표
 * 147개). 라이선스는 **캐시에 이미 있는 jar·POM 에서만** 읽었다(내려받지 않았다): 각 POM 의 `<licenses>`(없으면 부모
 * POM), jar 안의 `META-INF/LICENSE*`·`NOTICE*`, 그것도 없으면 소스 jar 의 머리 주석. 결과는 다섯 갈래다.
 *
 * - **Apache License 2.0** — AndroidX(Compose·Media3·Room·DataStore 포함), Kotlin 표준 라이브러리, kotlinx(coroutines·
 *   serialization), JetBrains annotations, Guava(Media3 가 쓴다), Okio(DataStore 가 쓴다), JSpecify, Apache Commons
 *   넷(Compress 와 그것이 끌고 오는 Codec·IO·Lang). **전문은 한 번만 싣는다**(`notice_apache2`). Commons 넷은 jar 에
 *   `NOTICE` 가 있고 라이선스 4(d)가 그것을 함께 싣으라고 하므로 따로 적는다.
 * - **0BSD** — XZ for Java.
 * - **UnRAR License** — junrar. 압축기를 만드는 데 쓸 수 없다는 제한 문장이 따로 있다(`notice_unrar_restriction`).
 * - **MIT** — SLF4J API(junrar 가 끌고 온다).
 * - **BSD 3-Clause** — DataStore 가 설정 파일 형식으로 옮겨 담은 Protocol Buffers(`datastore-preferences-external-
 *   protobuf`). 그 jar 의 `LICENSE.txt` 에는 저작권 줄이 없어 적힌 그대로 싣는다 — 우리가 지어 넣지 않는다.
 *
 * 의존성을 더하면 **이 목록부터** 고친다. 버전 카탈로그에 더한 것은 시험이 잡지만, 전이 의존(예: Guava)은 잡지 못한다 —
 * 위 명령을 다시 돌려 견줘라.
 */
internal object NoticeCatalog {

    data class Section(
        @param:StringRes val title: Int,
        @param:StringRes val subtitle: Int,
        /** 이 절에 드는 구성 요소의 목록(글). 라이선스 전문이 따로 있는 절은 없다. */
        @param:StringRes val components: Int? = null,
        /** 라이선스·NOTICE 전문. 바이트 그대로 `res/raw` 에 있다. */
        @param:RawRes val body: Int? = null,
    )

    val sections: List<Section> = listOf(
        Section(R.string.notice_apache_title, R.string.notice_apache_subtitle, components = R.string.notice_apache_components),
        Section(R.string.notice_commons_compress_title, R.string.notice_commons_subtitle, body = R.raw.notice_commons_compress),
        Section(R.string.notice_commons_codec_title, R.string.notice_commons_subtitle, body = R.raw.notice_commons_codec),
        Section(R.string.notice_commons_io_title, R.string.notice_commons_subtitle, body = R.raw.notice_commons_io),
        Section(R.string.notice_commons_lang_title, R.string.notice_commons_subtitle, body = R.raw.notice_commons_lang),
        Section(R.string.notice_xz_title, R.string.notice_xz_subtitle, body = R.raw.notice_xz),
        Section(R.string.notice_junrar_title, R.string.notice_junrar_subtitle, body = R.raw.notice_unrar),
        Section(R.string.notice_slf4j_title, R.string.notice_slf4j_subtitle, body = R.raw.notice_slf4j),
        Section(R.string.notice_protobuf_title, R.string.notice_protobuf_subtitle, body = R.raw.notice_protobuf),
        Section(R.string.notice_apache_full_title, R.string.notice_apache_full_subtitle, body = R.raw.notice_apache2),
    )

    /**
     * 이 고지가 덮는 Maven 좌표의 머리(`그룹:` 또는 `그룹:이름`). 버전 카탈로그의 런타임 라이브러리가 전부 이 가운데 하나로
     * 시작해야 한다(시험). 전이 의존도 여기 적어 둔다 — 위 목록과 같은 것을 말해야 한다.
     */
    val coveredModules: List<String> = listOf(
        "androidx.",
        "org.jetbrains.kotlin:",
        "org.jetbrains.kotlinx:",
        "org.jetbrains:annotations",
        "com.google.guava:",
        "com.squareup.okio:",
        "org.jspecify:",
        "org.apache.commons:commons-compress",
        "commons-codec:",
        "commons-io:",
        "org.apache.commons:commons-lang3",
        "org.tukaani:xz",
        "com.github.junrar:junrar",
        "org.slf4j:slf4j-api",
    )
}
