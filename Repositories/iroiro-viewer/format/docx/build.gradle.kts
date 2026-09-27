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
    // 패키지·관계·흐름 문서 바탕·HTML 쓰기가 전부 여기 있다(api 로 따라온다).
    api(project(":format:opc"))
    implementation(libs.kotlinx.coroutines.core)

    // 안드로이드가 런타임에 주는 API 라 컴파일에만 건다(`format:epub` 과 같은 모양).
    compileOnly(libs.xmlpull)
    testImplementation(testFixtures(project(":format:opc")))
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
