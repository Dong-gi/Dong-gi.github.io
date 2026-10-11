plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.donggi.iroirobrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.donggi.iroirobrowser"
        minSdk = 31
        // 반드시 적는다 — 없으면 AGP 9 가 조용히 compileSdk(37)로 채운다(iroiro-viewer 의 함정 표).
        targetSdk = 36
        versionCode = 9
        versionName = "0.9.0"
    }

    buildTypes {
        debug {
            // 실험용 인증 기관(tools/lab/make_certs.py 가 만든다)이 있으면 그것을 lab.test 아래 이름에만 더 믿는 설정을, 없으면 시스템
            // 인증서만 믿는 설정을 건다. 둘 다 평문을 허락한다(실험실의 목적지·평문 프록시).
            manifestPlaceholders["networkSecurityConfig"] =
                if (file("src/debug/res/xml/nsc_lab.xml").isFile) "@xml/nsc_lab" else "@xml/nsc_system"
        }
        release {
            // 시스템 인증서만 믿고 평문 http 쪽을 허락한다(사용자 결정 — res/xml/nsc_release.xml 의 머리말).
            manifestPlaceholders["networkSecurityConfig"] = "@xml/nsc_release"
            // iroiro-viewer 와 같이 debug 키로 서명한다(사이드로드, 키 전환은 사용자가 정할 때 한 번).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // :core·:net·:torrent·:tether 는 순수 JVM 모듈이라 JDK 의 API 로 컴파일된다 — 안드로이드 12(API 31)에 없는 것을 써도
        // 컴파일러는 모른다(URLEncoder.encode(String, Charset) 로 앱이 죽었다). lint 는 이것을 켜도 그 모듈들을 보지 않았다(되돌려
        // 확인했다) — 그 모듈들의 API 수준은 tools/check_api_level.py 가 본다(CLAUDE.md '툴체인과 확인 명령').
        checkDependencies = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":net"))
    // 토렌트 받기 엔진(순수 코틀린, 새 의존 없음 — CLAUDE.md '토렌트 엔진'). 프록시를 거치지 않고 기기에서 바로 나간다(사용자 결정).
    implementation(project(":torrent"))
    // 테더링의 서버·거르개(순수 코틀린, 새 의존 없음 — CLAUDE.md '테더링'). 앱의 tether/ 가 따로 된 프로세스(:tether)에서 잇는다.
    implementation(project(":tether"))
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media3.extractor)

    // 앱 모듈의 순수 함수(실패 까닭의 글, 막대의 상태 글·오류 까닭·닫은 탭·당겨서 새로 고침의 판정 등)를 기기 없이 시험한다 —
    // :core·:net 이 이미 쓰는 것(APK 에는 들지 않는다).
    // 안드로이드 모듈의 JVM 시험이라 `kotlin("test")` 만으로는 러너가 붙지 않는다(iroiro-viewer 의 함정 표).
    testImplementation(kotlin("test-junit"))
}

// JVM 시험 몇이 컴파일되지 않는 파일(매니페스트·소스의 글·글 자원·zip 에 담는 자산 — TetherIsolationTest·TetherWiringTest·SetupAssetsTest)을
// 읽는다. 입력으로 적지 않으면 그 파일만 바꾼 빌드에서 시험이 UP-TO-DATE 나 빌드 캐시로 건너뛴다(iroiro-viewer 의 함정 표와 같다).
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/kotlin").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res/values").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/assets").withPathSensitivity(PathSensitivity.RELATIVE)
}
