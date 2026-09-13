plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 툴체인을 쓰지 않는 이유는 CLAUDE.md 의 '함정' 절에 있다.
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
    implementation(libs.kotlinx.coroutines.core)
    // 안드로이드가 런타임에 주는 API 라 컴파일에만 건다. APK 에 들어가지 않는다.
    compileOnly(libs.xmlpull)
    // JVM 테스트에는 구현체가 없으므로 kxml2 를 끼운다. 안드로이드의 XmlPullParser
    // 구현(ExpatPullParser)과 같은 API 를 만족하므로 이 조합으로 파서를 JVM 에서 돌린다.
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
}
