plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 순수 JVM 이다 — 테더링의 서버(SOCKS5·HTTP 프록시·파일 서버의 규약)와 거르개(손님·목적지·Host)를 기기 없이 루프백에서 초 단위로 시험한다.
// usb-tether 를 Web 에 합치며(2026-10-10) 안드로이드에 기대지 않는 것을 옮겨 왔다 — 앱의 tether/ 가 잇는다(서비스·핫스팟·화면·SAF 폴더).
// 새 의존이 없다 — JDK 와 코틀린 표준 라이브러리만. 툴체인(jvmToolchain)은 쓰지 않는다(net 과 같은 까닭). 바이트코드만 17 로 맞춘다.
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
