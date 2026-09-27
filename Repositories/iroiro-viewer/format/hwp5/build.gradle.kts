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

// HWP 5.0 — CFB(`format:cfb`) 안의 레코드 스트림. 흐름 문서 바탕과 위생은 `format:html` 이 준다.
dependencies {
    implementation(project(":core:model"))
    api(project(":format:html"))
    implementation(project(":format:cfb"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":format:cfb")))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
