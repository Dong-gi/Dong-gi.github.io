plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 순수 JVM 이다 — 주소창 해석·내려받기 이름·예외 규칙·출구 확인처럼 기기 없이 정할 수 있는 것을 여기 모아 초 단위로 시험한다.
// 툴체인(jvmToolchain)은 쓰지 않는다(net 과 같은 까닭). 바이트코드만 17 로 맞춘다.
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
    testImplementation(kotlin("test"))
}
