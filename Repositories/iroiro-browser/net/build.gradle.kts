plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 순수 JVM 이다 — 소켓과 javax.net.ssl 만 쓴다. 그래서 중계의 규약(인증·터널·전달·막다른 길)을 에뮬레이터 없이 초 단위로 시험한다.
// 툴체인(jvmToolchain)은 쓰지 않는다(JDK 17 을 따로 내려받게 된다 — iroiro-viewer 의 함정 표). 바이트코드만 17 로 맞춘다.
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
