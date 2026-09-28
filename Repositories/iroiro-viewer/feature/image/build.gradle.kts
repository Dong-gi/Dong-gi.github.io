plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.image"
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
    implementation(project(":core:io"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // '다른 앱으로 열기' 를 어디에 다는가(`ImageOpenWith`)를 JVM 에서 박는다.
    // **`kotlin("test-junit")` 이 필요하다.** 안드로이드 라이브러리 모듈에서는
    // `kotlin("test")` 만으로 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다.
    testImplementation(kotlin("test-junit"))
}
