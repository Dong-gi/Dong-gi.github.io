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
    // `FlowDocumentBase` 의 공개 API 가 `ParseLimits`·`FlowDocument` 를 낸다.
    api(project(":core:safety"))
    api(project(":format:api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}
