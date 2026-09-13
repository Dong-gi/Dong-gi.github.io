plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.diag"
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
    implementation(project(":core:io"))
    implementation(project(":format:api"))
    // 진단 화면은 압축 경로를 '실제로 돌려 보는' 것이 목적이라 이 모듈을 직접 쓴다.
    implementation(project(":format:archive"))
    implementation(project(":core:safety"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
