plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.archive"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        // 풀기는 파일시스템 경계를 건드린다 — 경로 봉쇄와 임시 파일은 진짜 기기에서만
        // 확인된다. 시험은 앱 전용 외부 디렉터리에서 돌아 권한이 필요 없다.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 암호 아카이브 표본(`archivecrypt/`)을 **JVM 시험과 한 벌로 나눠 쓴다** — 리더 시험은
// `format:archive` 에 있고, 여기 계측 시험은 같은 표본으로 실제 파일시스템에 푼다.
// 두 벌로 두면 한쪽만 다시 만들어지는 날이 온다(`feature:docview` 와 같은 장치).
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("../../format/archive/src/test/resources")
    }
}

dependencies {
    // feature 끼리는 참조하지 않는다. 잇는 곳은 app 하나다.
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    implementation(project(":core:io"))
    implementation(project(":core:ui"))
    // 이 화면이 쓰는 포맷 모듈 하나.
    implementation(project(":format:archive"))
    implementation(project(":format:api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // 이름 계산·진행 바·폴더 시각은 순수 계산이라 JVM 에서 돈다. **`kotlin("test-junit")` 이 필요하다** —
    // 안드로이드 라이브러리 모듈에서는 `kotlin("test")` 만으로 러너가 붙지 않는다(함정 표).
    testImplementation(kotlin("test-junit"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.commons.compress)
}
