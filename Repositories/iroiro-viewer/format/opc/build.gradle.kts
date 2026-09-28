plugins {
    alias(libs.plugins.kotlin.jvm)
    // 시험용 패키지 짜기(`TinyOoxml`)를 docx·xlsx·pptx 의 시험이 함께 쓴다. 세 벌로 두면
    // 한쪽만 고쳐지는 날이 온다(5단계의 '문구 두 벌').
    `java-test-fixtures`
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
    api(project(":core:safety"))
    api(project(":format:api"))
    // OPC 패키지는 ZIP 이다. 안전한 ZIP 리더를 한 벌 더 쓰지 않는다(8단계가 엔트리 수·압축비·
    // 경로 탈출을 거기서 막아 두었다).
    implementation(project(":format:archive"))
    // 변환기가 쓰는 `HtmlWriter`·`CssValues` 와 위생기. **api 로 낸다** — `OpcFlowDocument` 를
    // 잇는 변환기 모듈이 그것들을 그대로 쓴다.
    api(project(":format:html"))
    // 암호가 걸린 OOXML 은 CFB(OLE2) 안에 들어 있다(MS-OFFCRYPTO).
    implementation(project(":format:cfb"))
    api(libs.kotlinx.coroutines.core)

    // 안드로이드가 런타임에 주는 API 라 컴파일에만 건다(`format:epub` 과 같은 모양).
    compileOnly(libs.xmlpull)
    testFixturesImplementation(project(":format:api"))
    // 실세계 표본 시험 틀(`OoxmlCorpus`)이 앱처럼 판별하려면 8단계의 ZIP 리더로 이름을 읽어야 한다.
    testFixturesImplementation(project(":format:archive"))
    // 잠긴 패키지를 CFB 에 담아 보는 시험(`OfficeCfbAgileTest`)이 `format:cfb` 의 짜개(`TinyCfb`)를 쓴다.
    testImplementation(testFixtures(project(":format:cfb")))
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
