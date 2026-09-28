plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.settings"
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
    // feature 끼리는 참조하지 않는다. 잇는 곳은 app 하나다 — 고지(app)·진단(feature:diag)으로 가는 길은 콜백으로 받는다.
    // `SortSpec` 이 설정의 공개 모양(`SettingsSource`)에 있어 직접 선언한다.
    implementation(project(":core:model"))
    // 설정 값(`AppPreferences`)·기록 표(DAO 의 `clear`)·크래시 기록(`CrashLog`).
    implementation(project(":core:data"))
    // 공유(`ShareHelper` — `getUriForFile` 을 부르는 유일한 곳)와 시각 표기(`Format`).
    implementation(project(":core:io"))

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
