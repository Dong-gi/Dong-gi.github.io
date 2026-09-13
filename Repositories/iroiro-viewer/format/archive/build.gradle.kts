plugins {
    alias(libs.plugins.kotlin.jvm)
}

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
    api(project(":format:api"))
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.commons.compress)
    // commons-compress 가 끌고 오는 xz 1.10 을 1.12 로 올린다. LZMA2 해제 경로다.
    implementation(libs.tukaani.xz)
    implementation(libs.junrar)

    testImplementation(kotlin("test"))
}

configurations.configureEach {
    resolutionStrategy {
        // commons-compress 가 의존하는 commons-lang3 는 3.18.0 미만이면 CVE-2025-48924
        // (재귀 깊이 제한 없는 ClassUtils)에 걸린다. 하한을 강제한다.
        force("org.apache.commons:commons-lang3:3.18.0")
    }
}
