plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.donggi.iroiroviewer.io"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    // api 다. 이 모듈의 공개 API(FileEntry·SortSpec·IroDispatchers)가 거기서 온다 —
    // implementation 으로 두면 쓰는 쪽이 core:model 을 또 선언해야 한다.
    api(project(":core:model"))
    // 휴지통은 '파일을 옮기는 것' 과 '어디서 왔는지 적는 것' 이 한 몸이라 갈라 놓으면
    // 정합성이 깨진다. 그래서 core:io 가 core:data 를 쓰는 것을 예외로 허용한다.
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    // EXIF 방향 태그만 고쳐 무손실로 돌린다. 순수 자바 AndroidX 라 의존 정책에 맞고,
    // media3 가 이미 1.3.6 을 싣고 있어 새 라이브러리가 아니라 사본이 하나 올라갈 뿐이다.
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
