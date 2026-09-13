plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.comic"
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
    implementation(project(":core:data"))
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

    // **`kotlin("test-junit")` 이 필요하다.** 안드로이드 라이브러리 모듈에서는
    // `kotlin("test")` 만으로 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.commons.compress)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.commons.compress)
}
