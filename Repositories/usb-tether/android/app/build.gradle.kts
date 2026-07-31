import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
}

configure<ApplicationExtension> {
    namespace = "com.example.usbtether"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.usbtether"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // viewBinding 은 켜져 있었지만 쓰는 곳이 없었다(MainActivity 는 findViewById).
    // 쓰지 않는 바인딩 클래스를 매 빌드마다 생성할 이유가 없어 껐다. 다시 쓰려면
    // buildFeatures { viewBinding = true } 를 되살리고 findViewById 를 걷어내면 된다.
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    // SAF 폴더를 트리로 다루기 위한 얇은 래퍼. DocumentsContract 를 직접 쓰면
    // 같은 코드를 손으로 재구현해야 한다. SharedFolder 참고.
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
}
