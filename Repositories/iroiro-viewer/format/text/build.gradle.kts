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
    // 인코딩 판정과 창 단위 디코딩 — 텍스트·자막·zip 파일명이 함께 쓴다(의존 표가 허용한다).
    implementation(project(":core:charset"))
    implementation(project(":format:api"))
    // 마크다운 미리보기가 HTML 을 쓰는 길. 의존 표가 허락하는 기반 셋 가운데 하나다 — 위생·껍데기를
    // 한 벌로 두려는 것이고, `feature:text` 는 이 모듈을 보지 않는다(공개 API 에 이 모듈의 타입을 내지 않는다).
    implementation(project(":format:html"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
