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
    // IroDispatchers 한 파일 때문이다. 디스패처 정책은 모듈마다 따로 둘 것이 아니라
    // 한곳에 있어야 하고, 그 한곳은 모두가 볼 수 있는 이 모듈이다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}
