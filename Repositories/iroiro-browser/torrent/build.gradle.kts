plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 순수 JVM 이다 — 토렌트 받기 엔진(bencode·메타정보·마그넷·트래커·피어·MSE·DHT·웹 시드·저장)을 기기 없이 루프백의 가짜 군집으로 시험한다.
// 새 의존이 없다 — JDK 와 코틀린 표준 라이브러리만. 받은 파일 이름을 고르는 데 :core 의 SafeFileName 을 쓴다(:core 도 의존이 없다).
// 툴체인(jvmToolchain)은 쓰지 않는다(net 과 같은 까닭). 바이트코드만 17 로 맞춘다.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

// 실제 인터넷 시험(사용자 승인 — 합법 공개 토렌트로). 보통의 `test` 에는 들지 않는다 — 사람이 돌린다(시험 소스의 real/RealSwarm.kt 머리말).
// 이 PC 의 IP 가 그 군집에 드러난다. 받은 것은 -Pargs 로 준 작업 폴더(저장소 밖)에 둔다.
tasks.register<JavaExec>("realSwarm") {
    group = "verification"
    description = "합법 공개 토렌트로 실제 군집에 붙어 본다(PC 의 JVM, 프록시 없이). -Pargs=\"mode url work mb seconds files\""
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.donggi.iroirobrowser.torrent.real.RealSwarmKt")
    args = (project.findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}
