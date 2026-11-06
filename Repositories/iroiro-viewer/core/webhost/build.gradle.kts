plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.webhost"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
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

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    // 포맷을 모른다. 자원은 `ResourceProvider` 로 들어온다 — 의존 표가 허락하는 것이
    // core:model·safety·charset 뿐이고, 같은 호스트를 EPUB·SVG·HTML·마크다운 미리보기가
    // 함께 쓴다.

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    // 스크립트를 켠 쪽의 문서 시작 스크립트(클립보드 막음 — `ScriptPage.CLIPBOARD_GUARD`). 편집기(`feature:editor`)가 이미 싣는 같은
    // 판이라 APK 에 새로 드는 것이 없다(2026-10-16 보안 점검).
    implementation(libs.androidx.webkit)

    testImplementation(kotlin("test-junit"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
