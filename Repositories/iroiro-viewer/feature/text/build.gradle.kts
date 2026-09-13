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
    implementation(project(":core:ui"))
    // 이 화면이 쓰는 포맷 모듈 하나.
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
}
