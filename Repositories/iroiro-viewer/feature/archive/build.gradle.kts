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

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.commons.compress)
}
