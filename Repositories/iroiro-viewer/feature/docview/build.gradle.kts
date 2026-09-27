plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer.docview"
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

// **암호 PDF 표본을 한 벌만 둔다.** JVM 시험(`PdfDecryptorTest`)과 계측 시험
// (`EncryptedPdfTest`)이 같은 파일을 읽는다 — 두 벌이면 한쪽만 다시 만들어지는 날이 온다.
// 계측 시험 APK 에는 자산(`assets/pdfcrypt/…`)으로 실린다. 앱 APK 에는 실리지 않는다.
// (AGP 9 에서 `android.sourceSets.getByName(...)` 은 옛 타입으로 캐스팅하다 죽는다 —
// 변형 API 로 붙인다.)
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("src/test/resources")
    }
}

dependencies {
    // feature 끼리는 참조하지 않는다. 잇는 곳은 app 하나다.
    implementation(project(":core:model"))
    implementation(project(":core:safety"))
    implementation(project(":core:io"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    // 잠긴 WebView. EPUB 본문을 그리는 유일한 길이다.
    implementation(project(":core:webhost"))
    // **PDF 는 포맷 모듈이 없다.** `android.graphics.pdf` 를 쓰므로 순수 JVM 인
    // `format:*` 에 둘 수 없고, 계약(`DocumentOpener`)만 `format:api` 에서 가져온다.
    implementation(project(":format:api"))
    // **자기 포맷 모듈 하나**(의존 표). 화면이 차례·목차·장 HTML 을 읽어야 해서
    // 타입을 본다. PDF 는 포맷 모듈이 아예 없다.
    implementation(project(":format:epub"))

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

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
