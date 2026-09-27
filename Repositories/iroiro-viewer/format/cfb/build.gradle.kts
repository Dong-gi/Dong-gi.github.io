plugins {
    alias(libs.plugins.kotlin.jvm)
    // 시험용 CFB 짜개(`TinyCfb`)를 HWP 5.0(13단계)의 시험도 함께 쓴다. 두 벌로 두면
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

// CFB(OLE2, MS-CFB). 암호가 걸린 OOXML(12단계)과 HWP 5.0(13단계)이 이 위에 선다.
// 압축·XML 상한이 걸리지 않는 구조라 전용 불변식이 따로 필요하다(CLAUDE.md '안전').
dependencies {
    // **api 로 낸다** — 공개 API 가 `ParseLimits`(상한)를 받고 `ParseLimitExceededException` 을 던진다.
    api(project(":core:safety"))
    api(project(":format:api"))

    testImplementation(kotlin("test"))
}
