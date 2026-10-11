plugins {
    alias(libs.plugins.android.library)
}

/**
 * 편집기 — rhwp(HWP·HWPX)·ONLYOFFICE(오피스·ODF·글·PDF) 웹 편집기를 앱 안에 묶어 WebView 로 띄운다. 화면은 **따로 된
 * 프로세스**(`:editor` — 매니페스트)에서 돈다. 파일 쪽과는 `core:io` 의 `EditorApp`(인텐트·저장 자리) 하나로 잇는다 — feature 끼리 보지 않는다.
 * 화면은 플랫폼 뷰다(화면의 대부분이 웹 편집기다).
 */
android {
    namespace = "io.github.donggi.iroiroviewer.editor"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        // 이 빌드가 다루는 형식 — 묶음이 든 엔진의 것만. 편집기 화면의 meta-data 로 실리고 파일 쪽(`EditorApp.kinds`)이 그 형식만 넘긴다.
        manifestPlaceholders["editorKinds"] = editorKinds()
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/** 묶음이 든 엔진의 형식 — `Engines.kt` 의 엔진·형식·installed 와 같다(EngineKindsTest 가 이 함수를 글로 읽어 견준다). */
fun editorKinds(): String {
    val engines = rootProject.file("vendor/editor/assets/engines")
    val kinds = mutableListOf<String>()
    if (File(engines, "rhwp/index.html").isFile) kinds += listOf("hwp", "hwpx")
    // ONLYOFFICE 는 한글 글꼴을 rhwp 묶음에서 빌린다(Engines.kt 의 installed 와 같은 조건).
    if (File(engines, "onlyoffice/web-apps/apps/api/documents/api.js").isFile && File(engines, "rhwp/fonts/NotoSansKR-Regular.woff2").isFile) {
        // Engines.kt 의 Engine.forKind 가 ONLYOFFICE 로 보내는 형식 전부(어댑터의 SPECS 와 같다).
        kinds += listOf(
            "docx", "docm", "dotx", "dotm", "doc", "dot", "odt", "txt",
            "xlsx", "xlsm", "xltx", "xltm", "xls", "xlt", "ods", "csv",
            "pptx", "pptm", "ppsx", "ppsm", "potx", "potm", "ppt", "pot", "pps", "odp",
            "pdf",
        )
    }
    return kinds.joinToString(",")
}

// tools/editor 가 지은 엔진 묶음(vendor/editor/assets — git 무시)을 APK 의 assets 로 붙인다(그 안의 engines/<엔진>/ 이 그대로
// assets/engines/<엔진>/ 이 된다). 묶음이 없으면 붙이지 않는다 — 시험 엔진(probe)만 든 앱이 되고, 형식 목록이 비어 파일 쪽이 넘기지 않는다.
// AGP 9 에서 sourceSets 의 assets.srcDir 는 설정 단계에서 죽으므로(함정 표) 변형 API 로 붙인다.
val vendorAssets = rootProject.file("vendor/editor/assets")
androidComponents {
    onVariants { variant ->
        if (vendorAssets.isDirectory) variant.sources.assets?.addStaticSourceDirectory(vendorAssets.absolutePath)
    }
}

dependencies {
    // 파일 쪽과의 약속(EditorApp — 동작 이름·추가값·저장 자리의 글자열). feature 끼리는 보지 않는다.
    implementation(project(":core:io"))
    // 사용자 승인(2026-10-09). 편집기와 바이트를 ArrayBuffer 로 주고받는 WebMessageListener 가 여기 있다. 순수 자바 AndroidX, 네이티브 없음.
    implementation(libs.androidx.webkit)

    // 순수한 판단(자산 자리 풀이·형식 표·저장 덩이 맞추기·이름 다듬기·용지)을 기기 없이 시험한다.
    // **`kotlin("test-junit")` 이 필요하다** — 안드로이드 라이브러리 모듈에서는 `kotlin("test")` 만으로 러너가 붙지 않는다(함정 표).
    testImplementation(kotlin("test-junit"))
}
