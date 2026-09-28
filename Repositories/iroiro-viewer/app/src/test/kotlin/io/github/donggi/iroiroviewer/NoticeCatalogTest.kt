package io.github.donggi.iroiroviewer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 오픈소스 고지. 이 화면은 **법적 의무를 지는 유일한 화면**이라, 글이 틀리거나 빠져도 앱은 멀쩡히 돈다 — 그래서 시험이 본다.
 *
 * 파일은 모듈 폴더(Gradle 이 JVM 시험을 돌리는 자리)에서 상대 경로로 읽는다. 저장소 밖의 것은 읽지 않는다.
 */
class NoticeCatalogTest {

    private val strings = File("src/main/res/values/strings.xml").readText(Charsets.UTF_8)

    private fun string(name: String): String {
        val m = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(strings)
        return checkNotNull(m) { "strings.xml 에 $name 이 없다" }.groupValues[1]
    }

    /**
     * **한컴 명세가 요구한 문장이 글자 그대로다.** 문장은 명세의 사용 조건에서 왔고, 그것을 옮겨 적은 곳은
     * `Hwp5Opener` 의 KDoc 이다(13단계). 두 곳이 어긋나면 어느 쪽이 맞는지 아무도 모른다 — 한 곳을 고치면 시험이 깨진다.
     */
    @Test
    fun `한컴 고지 문장이 Hwp5Opener 의 KDoc 과 글자 그대로 같다`() {
        val kdoc = File("../format/hwp5/src/main/kotlin/io/github/donggi/iroiroviewer/format/hwp5/Hwp5Opener.kt")
            .readLines(Charsets.UTF_8)
            .map { it.trim().removePrefix("*").trim() }
            .single { it.startsWith("본 제품은") }
        assertEquals(kdoc, string("notice_hancom"))
        // 두 곳이 같아도 둘 다 틀릴 수 있다 — 처음 옮길 때 '한글' 의 옛한글 첫 음절(조합형 자모 셋)이 두 곳 모두에서 빠져
        // '한글과컴퓨터의 글 문서 파일' 이었다. 명세 원문(S01, 사용 조건 절)에서 읽은 글자를 박아 둔다.
        assertTrue(kdoc.contains("한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp)"), kdoc)
    }

    @Test
    fun `고지 화면이 한컴 문장과 명세 문장과 UnRAR 제한을 실제로 그린다`() {
        val screen = File("src/main/kotlin/io/github/donggi/iroiroviewer/NoticeScreen.kt").readText(Charsets.UTF_8)
        for (name in listOf("notice_hancom", "notice_specs", "notice_unrar_restriction")) {
            assertTrue(screen.contains("R.string.$name"), "NoticeScreen 이 $name 을 그리지 않는다")
        }
    }

    /**
     * `res/raw` 의 전문은 **전부 어느 절이 쓰고**, Apache-2.0 전문은 **한 벌뿐이다**(같은 글을 둘 두면 한쪽만 고쳐진다).
     * R 의 이름은 리플렉션으로 얻는다 — 정수만으로는 어느 파일인지 모른다.
     */
    @Test
    fun `raw 의 전문은 모두 쓰이고 Apache 전문은 한 벌이다`() {
        val nameOf = R.raw::class.java.fields.associate { it.getInt(null) to it.name }
        val used = NoticeCatalog.sections.mapNotNull { it.body }.map { nameOf.getValue(it) }
        assertEquals(used.size, used.toSet().size, "같은 전문을 두 절이 싣는다")
        val files = File("src/main/res/raw").listFiles()!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(files, used.toSet(), "쓰이지 않거나 없는 전문")

        val apache = File("src/main/res/raw").listFiles()!!.filter {
            it.readText(Charsets.UTF_8).trimStart().startsWith("Apache License")
        }
        assertEquals(listOf("notice_apache2.txt"), apache.map { it.name })
        for (f in File("src/main/res/raw").listFiles()!!) assertTrue(f.length() > 0, "${f.name} 이 비었다")
    }

    /**
     * 버전 카탈로그의 라이브러리가 모두 고지의 어느 절에 든다. **릴리스 APK 에 실리지 않는 것**만 뺀다 — 이유를 함께 적는다.
     * 전이 의존(Guava·Okio·SLF4J…)은 카탈로그에 없어 여기서 잡히지 않는다(`NoticeCatalog` 주석의 명령으로 다시 본다).
     */
    @Test
    fun `버전 카탈로그의 런타임 라이브러리가 모두 고지에 있다`() {
        val notShipped = mapOf(
            "xmlpull:xmlpull" to "compileOnly — 런타임에는 안드로이드가 준다",
            "net.sf.kxml:kxml2" to "JVM 시험 전용",
        )
        val toml = File("../gradle/libs.versions.toml").readText(Charsets.UTF_8)
        val libraries = toml.substringAfter("[libraries]").substringBefore("[plugins]")
        val modules = Regex("""module\s*=\s*"([^"]+)"""").findAll(libraries).map { it.groupValues[1] }.toList()
        assertTrue(modules.size > 20, "카탈로그를 읽지 못했다: $modules")
        val missing = modules.filter { m ->
            m !in notShipped && NoticeCatalog.coveredModules.none { m.startsWith(it) }
        }
        assertEquals(emptyList(), missing, "고지에 없는 라이브러리")
    }

    /** 덮는다고 적은 좌표마다 **화면의 글**에 그 이름이 있다 — 목록(코드)과 글(strings.xml)이 같은 것을 말한다. */
    @Test
    fun `덮는 좌표마다 고지의 글에 그 이름이 있다`() {
        val nameInText = mapOf(
            "androidx." to "AndroidX",
            "org.jetbrains.kotlin:" to "Kotlin 표준 라이브러리",
            "org.jetbrains.kotlinx:" to "kotlinx.coroutines",
            "org.jetbrains:annotations" to "JetBrains Java Annotations",
            "com.google.guava:" to "Guava",
            "com.squareup.okio:" to "Okio",
            "org.jspecify:" to "JSpecify",
            "org.apache.commons:commons-compress" to "Apache Commons Compress",
            "commons-codec:" to "Apache Commons Codec",
            "commons-io:" to "Apache Commons IO",
            "org.apache.commons:commons-lang3" to "Apache Commons Lang",
            "org.tukaani:xz" to "XZ for Java",
            "com.github.junrar:junrar" to "junrar",
            "org.slf4j:slf4j-api" to "SLF4J",
        )
        assertEquals(nameInText.keys, NoticeCatalog.coveredModules.toSet(), "좌표를 더했으면 이 표에도 이름을 더한다")
        for ((module, name) in nameInText) assertTrue(strings.contains(name), "$module 의 이름 '$name' 이 고지에 없다")
        // DataStore 가 옮겨 담은 Protocol Buffers 는 AndroidX 좌표지만 라이선스가 다르다(BSD 3-Clause) — 따로 적혀 있어야 한다.
        assertTrue(string("notice_protobuf_subtitle").contains("BSD 3-Clause"))
    }
}
