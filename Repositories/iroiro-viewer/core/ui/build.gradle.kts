plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.ui"
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
    api(project(":core:model"))
    // 이미지 상한(ImageLimits)이 여기 산다. 압축 파서의 상한과 같은 자리에 두는 것이
    // 규칙이다 — 방어 상한을 한곳에 못 박는다.
    api(project(":core:safety"))

    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    implementation(libs.compose.ui.graphics)
    // WindowCompat(상태표시줄 글자색)
    implementation(libs.androidx.core.ktx)
    // EXIF 방향·촬영 정보. 순수 자바 AndroidX.
    api(libs.androidx.exifinterface)

    // 안드로이드 라이브러리의 JVM 단위 시험. kotlin("test") 만으로는 러너가 붙지 않아
    // junit 변종을 명시한다(순수 JVM 모듈은 kotlin("test") 로 충분하다).
    testImplementation(kotlin("test-junit"))
}
