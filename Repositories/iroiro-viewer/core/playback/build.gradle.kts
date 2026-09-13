plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.playback"
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
    // **api 다.** 이 모듈의 공개 API 가 `core:model` 의 타입을 그대로 내보낸다 —
    // `PlaybackConnection.play(entry: FileEntry)`, `QueueItem.kind: FileKind`.
    // implementation 으로 두면 쓰는 쪽이 `core:model` 을 또 선언해야 한다
    // (`core:io` 의 같은 줄이 같은 이유로 api 다).
    api(project(":core:model"))
    implementation(project(":core:safety"))
    // 자막 인코딩 판정. 7단계가 텍스트 뷰어를 위해 만든 것을 그대로 쓴다.
    implementation(project(":core:charset"))
    implementation(project(":core:io"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // **`kotlin("test-junit")` 이 필요하다.** 안드로이드 라이브러리 모듈에서는
    // `kotlin("test")` 만으로 러너가 붙지 않아 `Unresolved reference 'Test'` 가 난다.
    testImplementation(kotlin("test-junit"))
}
