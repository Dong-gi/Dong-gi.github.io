plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 툴체인(jvmToolchain)을 쓰지 않는다 — CLAUDE.md 의 함정 표 참고.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    implementation(project(":format:api"))
    // EPUB 은 ZIP 이다. 안전한 ZIP 리더를 한 벌 더 쓰지 않는다 —
    // 8단계가 엔트리 수·압축비·경로 탈출을 거기서 이미 막아 두었다.
    implementation(project(":format:archive"))
    // 본문이 XHTML 이다. 위생 규칙은 12·13단계와 함께 쓴다.
    // **api 로 낸다.** `EpubBook.chapterHtml` 이 `HtmlShell` 이 씌운 문서를 돌려주므로
    // 그 계약의 일부가 공개 API 에 있다(`core:playback` 이 `core:model` 을 api 로 내는 것과
    // 같은 이유다). 화면은 여전히 `format:html` 을 직접 부르지 않는다.
    api(project(":format:html"))
    implementation(libs.kotlinx.coroutines.core)

    // 안드로이드가 런타임에 주는 API 라 컴파일에만 건다(`core:safety` 와 같은 모양).
    compileOnly(libs.xmlpull)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
