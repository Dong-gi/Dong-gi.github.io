plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 툴체인(jvmToolchain)을 쓰지 않는다. 그러면 JDK 17 을 따로 내려받아야 하고,
// 그 다운로드는 foojay 같은 해석기 플러그인을 또 물어온다. 데몬 JDK 21 로 컴파일하되
// 산출 바이트코드만 17 로 맞춘다 — 안드로이드 쪽(D8)이 받아들이는 상한이 17 이다.
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
    // api 로 노출한다 — 파서가 OpenFailure 를 만들려면 상한 예외 타입을 알아야 한다.
    api(project(":core:safety"))
    implementation(libs.kotlinx.coroutines.core)
    // 안드로이드가 런타임에 주는 API 라 컴파일에만 쓴다. APK 에 들어가지 않는다.
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
}
