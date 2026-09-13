plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.player"
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
    // 재생의 실체(서비스·세션·커넥션)는 `core:playback` 에 있고 이 모듈은 화면만 그린다.
    implementation(project(":core:playback"))
    // 로그(`Iro`). 다른 feature 모듈이 모두 같은 이유로 갖고 있다.
    implementation(project(":core:io"))
    // 재생목록이 `FileKind` 로 아이콘을 가른다. 전파에 기대지 않고 직접 선언한다 —
    // 이 모듈이 그 타입을 import 하기 때문이다.
    implementation(project(":core:model"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    // `core:playback` 이 coroutines 를 implementation 으로 가지므로 전파되지 않는다.
    // `PlaybackConnection.state` 의 타입이 `StateFlow` 라 여기서도 직접 선언해야 한다
    // (`feature:browser` 가 같은 이유로 같은 줄을 갖고 있다).
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}
