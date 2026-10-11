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
    implementation(project(":core:safety"))
    // 인코딩 판정과 창 단위 디코딩. 의존 표가 7단계에 이 줄을 허용하도록 고쳐졌다 —
    // 지도는 처음부터 "텍스트·자막·zip 파일명이 공유한다" 고 적고 있었다.
    implementation(project(":core:charset"))
    implementation(project(":format:api"))
    // 마크다운 미리보기(14단계)가 HTML 을 쓰는 길. 의존 표가 허락하는 기반 셋 가운데 하나다 — 위생·껍데기를
    // 한 벌로 두려는 것이고, `feature:text` 는 이 모듈을 보지 않는다(공개 API 에 이 모듈의 타입을 내지 않는다).
    implementation(project(":format:html"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// 작은 힙의 시험. 시험 작업자의 기본 힙은 512 MB 라 '상한 밖에서 커지는' 결함이 그냥 지나간다 —
// 192 MB(기기의 힙 등급)에서 OOM 을 쟀다. 그래서 `*MemoryTest` 만 따로 그 힙으로 돌리고, `test` 가 이것을 함께 돌린다.
// (`val x by tasks.registering(…)` 는 Gradle 9.6 에서 폐기 예고다 — `register<…>` 로 적는다.)
val memoryTest = tasks.register<Test>("memoryTest") {
    description = "HTML 미리보기가 기기의 힙 등급(192 MB)에서 원문 상한 크기의 쪽을 견디는가"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxHeapSize = "192m"
    filter.includeTestsMatching("*MemoryTest")
}

tasks.test {
    filter.excludeTestsMatching("*MemoryTest")
    dependsOn(memoryTest)
}
