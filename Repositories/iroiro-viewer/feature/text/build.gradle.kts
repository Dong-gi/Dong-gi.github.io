plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.text"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
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
    implementation(project(":core:charset"))
    implementation(project(":core:io"))
    // 뷰어의 기본값(줄 접기·줄 번호·글자 크기·줄 끝 표시)을 저장한다(14단계).
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    // 마크다운 미리보기를 띄우는 잠긴 WebView(14단계). 문서 뷰어와 같은 호스트다.
    implementation(project(":core:webhost"))
    // 이 화면이 쓰는 포맷 모듈 하나. 마크다운 변환도 여기 있다 — `format:html` 은 그 안쪽 일이라 이 모듈이 보지 않는다.
    implementation(project(":format:text"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // **`kotlin("test-junit")` 이 필요하다.** 안드로이드 라이브러리 모듈에서는
    // `kotlin("test")` 만으로 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
