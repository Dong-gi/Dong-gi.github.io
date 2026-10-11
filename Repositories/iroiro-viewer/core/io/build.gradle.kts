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
    // `.ts`·`.mts` 를 글로 보낼지 텍스트 뷰어와 **같은 판정기**로 정한다(`ContentSniff`). 의존 표가 허락하는 잎 모듈이다.
    implementation(project(":core:charset"))
    // ZIP 만들기의 항목 상한을 압축 목록·풀기의 상한(`ParseLimits`)과 하나로 둔다 — 이 앱이 다시 열 수 있는 것만 만든다.
    // 의존 표가 허락하는 잎 모듈이다.
    implementation(project(":core:safety"))
    implementation(libs.androidx.core.ktx)
    // EXIF 방향 태그만 고쳐 무손실로 돌린다. 순수 자바 AndroidX 라 의존 정책에 맞고,
    // media3 가 이미 1.3.6 을 싣고 있어 새 라이브러리가 아니라 사본이 하나 올라갈 뿐이다.
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.core)

    // 순수 규칙(폴더 알림 거르기·묶기·수정 시각 대조·예약 판단·풀기 결과)을 JVM 에서 지킨다. 파일시스템 경계는
    // 여전히 계측 시험(androidTest)의 일이다. 안드로이드 라이브러리 모듈이라 `test-junit` 이어야 한다(함정 표).
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// 빈 문서의 서식(`BlankDocumentsTest` 가 자산 폴더에서 그대로 읽는다). 컴파일되지 않는 파일이라 **입력으로 적지 않으면 서식만 바꾼 빌드에서
// Gradle 이 시험을 '최신' 으로 건너뛴다**(함정 표 — app 의 고지 시험과 같다).
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/blank").withPropertyName("blankTemplates").withPathSensitivity(PathSensitivity.RELATIVE)
}
