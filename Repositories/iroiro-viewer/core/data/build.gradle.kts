plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.donggi.iroiroviewer.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    // 스키마를 파일로 남긴다. 마이그레이션을 쓰려면 '이전 버전이 어떤 모양이었는지'가
    // 저장소에 있어야 하고, 그것이 없으면 나중에 손으로 추측하게 된다.
    // JSON 이라 공개 저장소에 들어가도 문제없다.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    // IroiroDatabase 가 RoomDatabase 를 상속해 밖으로 드러나므로 api 여야 한다.
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
