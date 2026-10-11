plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.image"
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
    // feature 끼리는 참조하지 않는다. 잇는 곳은 app 하나다.
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    implementation(project(":core:io"))
    implementation(project(":core:ui"))
    // SVG 는 플랫폼 디코더가 없어 잠긴 WebView 로 그린다(`SvgPage`). 문서 뷰어·마크다운 미리보기와 같은 호스트다.
    implementation(project(":core:webhost"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 그리기·메모의 그린 것을 ViewModel 이 든다 — 저장 상태 번들에 싣지 않는다. 다른 feature 모듈이 이미 쓰는 것이다.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // '다른 앱으로 열기' 를 어디에 다는가(`ImageOpenWith`)를 JVM 에서 박는다.
    // **`kotlin("test-junit")` 이 필요하다.** 안드로이드 라이브러리 모듈에서는
    // `kotlin("test")` 만으로 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다.
    testImplementation(kotlin("test-junit"))

    // 그리기·메모의 뜨는 쪽(원본 해상도로 뜬 위에 그린 화소)은 플랫폼의 디코더·캔버스가 있어야 한다.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // EXIF 방향이 붙은 사진을 시험이 짓는다(core:io·core:ui 가 이미 싣는 것이다).
    androidTestImplementation(libs.androidx.exifinterface)
}
