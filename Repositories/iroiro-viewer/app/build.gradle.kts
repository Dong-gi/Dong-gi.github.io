import com.android.build.api.artifact.SingleArtifact

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
        // 14단계 마감 — 첫 1.0. 코드는 설치 순서를 가르는 값이라 1 에서 하나만 올린다(0.1.0 을 깔아 둔 기기에 덮어 깔린다).
        versionCode = 2
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // release 도 debug 키로 서명한다 — **14단계 마감(1.0.0)에서도 그대로 두었다.** 서명이 바뀌면 기존 앱을
            // 지워야 설치되고 그때 앱 데이터(이어보기·읽던 쪽·휴지통 인덱스)가 날아가므로, 키 전환은 사용자가 정할 때
            // 한 번만 한다(키스토어는 `.gitignore` 가 막는 자료라 이 저장소에 둘 수도 없다). 이 기계의 debug.keystore 는
            // 2056년까지 유효하다.
            signingConfig = signingConfigs.getByName("debug")
            // R8 을 1단계부터 켠다. 늦게 가서 처음 켜면 파서들의 리플렉션과
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

// 고지 시험(`NoticeCatalogTest`)은 컴파일되지 않는 파일을 읽는다 — 문구(strings.xml)·전문(res/raw)·버전 카탈로그.
// **입력으로 적지 않으면 그 파일만 바꾼 빌드에서 Gradle 이 시험을 '최신' 으로 건너뛰고 옛 결과를 보여 준다** — 되돌림 확인에서
// 실제로 그렇게 통과했다(14단계).
tasks.withType<Test>().configureEach {
    inputs.files(
        "src/main/res/values/strings.xml",
        "src/main/res/raw",
        "src/main/kotlin/io/github/donggi/iroiroviewer/NoticeScreen.kt",
        "../gradle/libs.versions.toml",
    ).withPropertyName("noticeSources").withPathSensitivity(PathSensitivity.RELATIVE)
}

// 받는 형식의 시험(`ReceiveFiltersTest`)은 매니페스트를 글로 읽는다 — 위와 같은 까닭으로 입력에 적는다.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPropertyName("manifestSource").withPathSensitivity(PathSensitivity.RELATIVE)
}

/**
 * **병합된 매니페스트에 INTERNET 이 없는가**. 이 앱은 INTERNET 을 선언하지 않는다(CLAUDE.md '안전') — 매니페스트의
 * `tools:node="remove"` 두 줄이 라이브러리가 들여오는 것을 막는다. 그 막음이 듣는지를 11·14단계 실측에서 손으로만 봤다. 변형마다 병합된
 * 매니페스트를 읽어 INTERNET 을 청하는 요소(`uses-permission`·`uses-permission-sdk-23`·옛 이름 `uses-permission-sdk-m`)가 있으면 빌드를
 * 멈춘다. `assemble<변형>` 이 이것을 기다린다 — 작은 XML 하나를 읽는 일이라 빌드가 느려지지 않는다.
 *
 * 주석은 지우고 본다 — 병합기가 원본의 주석을 옮겨 싣고, 이 앱의 매니페스트 주석이 INTERNET 을 말한다. 새 의존을 쓰지 않는다(정규식).
 */
abstract class VerifyNoInternet : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** 통과했다는 표 — 매니페스트가 그대로면 다시 돌지 않는다. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun verify() {
        val xml = COMMENT.replace(mergedManifest.get().asFile.readText(Charsets.UTF_8), "")
        val asking = INTERNET.findAll(xml).map { it.groupValues[1] }.toList()
        if (asking.isNotEmpty()) {
            throw GradleException(
                "병합된 매니페스트가 INTERNET 을 청한다(${asking.joinToString()}) — 그 요소를 들여온 라이브러리를 찾거나, " +
                    "app/src/main/AndroidManifest.xml 에 같은 모양의 tools:node=\"remove\" 를 둔다",
            )
        }
        marker.get().asFile.writeText("병합된 매니페스트에 INTERNET 이 없다\n", Charsets.UTF_8)
    }

    private companion object {
        val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

        /** INTERNET 을 청하는 권한 요소 — 속성이 여러 줄에 걸쳐도 그 요소의 `>` 앞까지 본다. */
        val INTERNET = Regex("""<(uses-permission(?:-sdk-23|-sdk-m)?)\b[^>]*?\bandroid:name\s*=\s*["']android\.permission\.INTERNET["']""")
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoInternet>("verify${suffix}NoInternet") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            marker.set(layout.buildDirectory.file("intermediates/verify-no-internet/${variant.name}.txt"))
        }
        tasks.configureEach { if (name == "assemble$suffix") dependsOn(verify) }
    }
}


dependencies {
    // 편집기. 화면은 따로 된 프로세스(:editor)에서 돈다 — 이 모듈은 EditorApp 으로만 부른다.
    implementation(project(":feature:editor"))
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
    // 판별기가 ZIP 엔트리 이름을 봐야 한다(`FormatRegistry` — EPUB 은 ZIP 이라 이름으로 갈린다).
    implementation(project(":format:archive"))
    implementation(project(":format:epub"))
    implementation(project(":core:playback"))
    implementation(project(":feature:player"))
    // 14단계. 설정 화면 — 고지(`NoticeScreen`)와 진단으로 가는 길은 여기서 잇는다(feature 끼리는 서로를 보지 않는다).
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    // 고지·진단에 씌우는 제목 막대의 뒤로 화살표. 다른 모듈이 이미 들인 코어 세트라 APK 에 새로 드는 것이 없다.
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // 안드로이드 모듈의 JVM 시험이라 `kotlin("test-junit")` 이 있어야 러너가 붙는다(CLAUDE.md 함정 표).
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.core)
    // 목록의 종류 표(`core:io`)와 텍스트 뷰어의 언어 표(`format:text`)를 견주는 시험(`TextNamesConsistencyTest`) — 둘은 서로를 볼 수
    // 없다. 앱은 `format:text` 를 쓰지 않으므로(텍스트 뷰어가 쓴다) 시험에만 든다.
    testImplementation(project(":format:text"))
}
