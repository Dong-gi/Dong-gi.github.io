plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.donggi.iroiroviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.donggi.iroiroviewer"
        minSdk = 31
        // compileSdk 를 그대로 물려받지 않도록 반드시 적는다. AGP 9 는 이 값이 없으면
        // 조용히 compileSdk(37)로 채우고, 그러면 검증할 수 없는 Android 17 동작변화를
        // 떠안는다. 검증 기기는 Android 12 와 15 다.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 14단계 전까지는 release 도 debug 키로 서명한다. 서명이 바뀌면 기존 앱을
            // 지워야 설치되고 그때 앱 데이터(이어보기·읽던 쪽·휴지통 인덱스)가 날아가므로,
            // 키 전환은 마지막에 한 번만 한다. 이 기계의 debug.keystore 는 2056년까지 유효하다.
            signingConfig = signingConfigs.getByName("debug")
            // R8 을 1단계부터 켠다. 12단계에 가서 처음 켜면 파서들의 리플렉션과
            // 디코더 제거가 한꺼번에 터진다. 매 단계 끝에 release 를 설치해 훑는다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // commons-compress 계열이 저마다 들고 오는 라이선스·멀티릴리스 잔여물.
            // 그대로 두면 병합 충돌로 패키징이 멈춘다.
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:io"))
    implementation(project(":format:api"))
    implementation(project(":core:data"))
    implementation(project(":feature:diag"))
    implementation(project(":core:ui"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:image"))
    implementation(project(":feature:text"))
    implementation(project(":feature:archive"))
    implementation(project(":feature:comic"))
    implementation(project(":feature:docview"))
    // 판별기가 ZIP 엔트리 이름을 봐야 한다(`FormatRegistry`). 12·13단계에 가면
    // docx·xlsx·pptx·hwpx 가 전부 ZIP 이라 이름 없이는 갈리지 않는다.
    implementation(project(":format:archive"))
    implementation(project(":format:epub"))
    // 12단계. `FormatRegistry` 가 OOXML 여는이에 세 변환기를 이어 준다(조립은 `app` 의 일이다).
    implementation(project(":format:opc"))
    implementation(project(":format:docx"))
    implementation(project(":format:xlsx"))
    implementation(project(":format:pptx"))
    // 13단계. 한글 둘 — HWPX(ZIP)와 HWP 5.0(CFB).
    implementation(project(":format:hwpx"))
    implementation(project(":format:hwp5"))
    implementation(project(":core:playback"))
    implementation(project(":feature:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // 두 한글 변환기를 함께 여는 시험(`HancomPairTest`) — 포맷 모듈끼리는 서로를 볼 수 없어 조립하는 여기에만 둘 수 있다.
    // 안드로이드 모듈의 JVM 시험이라 `kotlin("test-junit")` 이 있어야 러너가 붙는다(CLAUDE.md 함정 표).
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.core)
    // HWPX 는 XML 풀 파서를 쓴다. 안드로이드 모듈의 JVM 시험에서는 android.jar 의 빈 껍데기(`XmlPullParserFactory`)가 던지므로
    // 포맷 모듈의 시험과 같은 구현(kxml2)을 끼운다.
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
}
