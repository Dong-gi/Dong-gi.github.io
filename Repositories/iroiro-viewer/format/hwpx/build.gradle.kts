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

// HWPX(OWPML, KS X 6101) — ZIP 안의 XML. 흐름 문서 바탕(`FlowDocumentBase`)과 위생은 `format:html`,
// ZIP 읽기는 `format:archive` 가 준다(의존 표의 기반 다섯 가운데 둘).
dependencies {
    implementation(project(":core:model"))
    api(project(":format:html"))
    implementation(project(":format:archive"))
    implementation(libs.kotlinx.coroutines.core)

    // 안드로이드가 런타임에 주는 API 라 컴파일에만 건다(`format:epub` 과 같은 모양).
    compileOnly(libs.xmlpull)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
