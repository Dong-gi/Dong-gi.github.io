package io.github.donggi.iroirobrowser.tether

import io.github.donggi.iroirobrowser.tether.core.FileServer
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 파일 서버의 **화면 자산**(`assets/tether/fileman.*`)과 앱 쪽 이음새(관리 허용 스위치·섬네일)를 소스로 박는다. 서버의 규약 자체는 `:tether` 의
 * 시험(FileServerTest·FileServerManageTest·FileServerReadTest)이 루프백으로 돌려 본다.
 *
 * - 화면은 서버의 CSP(이 서버의 것만·인라인 없음)에서 돈다 — HTML 에 인라인 스크립트·스타일·이벤트 속성이 없고, 바깥 주소를 부르지 않는다.
 * - 파일 이름은 글로만 그린다 — 스크립트에 `innerHTML`·`outerHTML`·`insertAdjacentHTML`·`document.write`·`eval` 이 없다.
 * - 바꾸는 요청은 PUT 과 JSON 의 POST 뿐 — 스크립트가 다른 메서드를 쓰지 않는다.
 * - 관리 허용은 `tether` 설정에 있고 기본 끔, 서버는 요청마다 그 값을 읽는다.
 * - 섬네일은 남이 올린 그림을 이 프로세스에서 풀지 않는다 — SharedFolder 가 그림 풀개(BitmapFactory·ImageDecoder·loadThumbnail·
 *   getDocumentThumbnail)를 부르지 않는다.
 */
class FileManTest {

    private val assets = File("src/main/assets/tether")

    private fun text(name: String): String = File(assets, name).readText()

    private fun code(path: String): String =
        File(path).readText().lines().filterNot { it.trim().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/**") } }.joinToString("\n")

    @Test
    fun 서버가_묻는_화면_자산이_모두_있다() {
        val names = FileServer.UI_FILES.values.map { it.first }.toSet()
        assertEquals(setOf("fileman.html", "fileman.js", "fileman.css"), names)
        for (name in names) assertTrue(File(assets, name).isFile, name)
        assertEquals(names, assets.list()!!.toSet(), "자산 폴더에 서버가 내주지 않는 파일이 없다")
    }

    @Test
    fun HTML_에는_인라인_스크립트_스타일_이벤트_속성이_없다() {
        val html = text("fileman.html")
        assertEquals(1, Regex("<script\\b").findAll(html).count(), "스크립트는 하나")
        assertTrue(html.contains("<script src=\"/fileman.js\" defer></script>"))
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/fileman.css\">"))
        assertFalse(Regex("<style\\b").containsMatchIn(html))
        assertFalse(Regex("\\sstyle=").containsMatchIn(html))
        assertFalse(Regex("\\son[a-z]+=", RegexOption.IGNORE_CASE).containsMatchIn(html), "이벤트 속성")
        assertFalse(Regex("javascript:", RegexOption.IGNORE_CASE).containsMatchIn(html))
    }

    @Test
    fun 바깥_주소를_부르지_않는다() {
        for (name in listOf("fileman.html", "fileman.js", "fileman.css")) {
            val t = text(name)
            assertFalse(Regex("https?://(?!192\\.168\\.49\\.1)").containsMatchIn(t.replace("http://www.w3.org/2000/svg", "")), name)
            assertFalse(t.contains("@import"), name)
        }
        assertFalse(Regex("url\\(").containsMatchIn(text("fileman.css")), "CSS 의 바깥 그림·글꼴")
    }

    @Test
    fun 이름을_HTML_로_그리지_않는다() {
        val js = text("fileman.js")
        for (word in listOf("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function", "setAttribute('style'", "srcdoc")) {
            assertFalse(js.contains(word), word)
        }
        // setTimeout·setInterval 에 글을 넘기지 않는다(함수만).
        assertFalse(Regex("set(Timeout|Interval)\\(\\s*['\"]").containsMatchIn(js))
    }

    @Test
    fun 바꾸는_요청은_PUT_과_JSON_의_POST_뿐() {
        val js = text("fileman.js")
        val methods = Regex("api\\('([A-Z]+)'").findAll(js).map { it.groupValues[1] }.toSet() + Regex("open\\('([A-Z]+)'").findAll(js).map { it.groupValues[1] }
        assertEquals(setOf("GET", "POST", "PUT"), methods)
        // POST 는 늘 JSON 몸이다 — api() 가 몸이 있으면 Content-Type 을 application/json 으로 단다.
        assertTrue(js.contains("opts.headers['Content-Type'] = 'application/json';"))
        assertFalse(js.contains("FormData"), "폼 몸(multipart)을 보내지 않는다")
    }

    @Test
    fun 관리_허용은_tether_설정에_기본_끔이고_서버는_요청마다_읽는다() {
        val settings = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherSettings.kt")
        assertTrue(settings.contains("prefs.getBoolean(K_MANAGE, false)"), "기본 끔")
        assertTrue(settings.contains("const val PREFS = \"tether\""))
        val service = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherService.kt")
        // 값을 한 번 읽어 넘기지 않고 람다로 — 요청마다 본다.
        assertTrue(service.contains("manageAllowed = { settings.manageAllowed }"), "요청마다 읽는 람다")
        val activity = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherActivity.kt")
        assertTrue(activity.contains("setOnCheckedChangeListener { _, on -> settings.manageAllowed = on }"))
        val strings = strings()
        assertEquals("관리 허용", strings["tether_manage"])
        assertEquals("핫스팟에 붙은 기기가 공유 폴더를 바꾸고 지울 수 있습니다.", strings["tether_manage_note"])
    }

    @Test
    fun 올리기는_같은_이름이면_묻고_덮어쓰기를_밝힌_요청만_보낸다() {
        val js = text("fileman.js")
        // 올리기는 늘 overwrite 를 밝힌다 — '1' 은 사용자가 그 파일에 '덮어쓰기' 를 고른 뒤에만.
        assertTrue(js.contains("overwrite: item.mode === 'overwrite' ? '1' : '0'"))
        assertEquals(1, Regex("url\\('/api/file', params\\)").findAll(js).count(), "올리기의 길은 하나")
        assertEquals(1, Regex("item\\.mode = 'overwrite'").findAll(js).count(), "덮어쓰기는 묻는 창의 답에서만 정한다")
        // '나머지에도 적용' 은 그 올리기 묶음 안에서만 — 전역에 기억하지 않는다.
        assertFalse(js.contains("Q.policy"), "전역 정책 없음")
        assertTrue(js.contains("var batch = { policy: null };"))
        assertTrue(js.contains("if (r && r.checked) item.batch.policy = mode;"))
        // '다시' 는 새 묶음으로, 고른 것을 지운다.
        val retry = js.substringAfter("function retryItem(item) {").substringBefore("\n  }")
        assertTrue(retry.contains("item.mode = null;") && retry.contains("item.batch = { policy: null };"), retry)
        // 묻는 창의 기본 단추(Enter)는 덮어쓰기가 아니다 — 앞에 primary 가 있어야 한다(dialog 는 처음 만난 primary·danger 를 기본으로).
        val ask = js.substringAfter("function ask(item, decide, existing) {").substringAfter("dialog({").substringBefore("}).then")
        assertTrue(ask.contains("덮어쓸까요?"), ask)
        // 묻기는 한 번에 하나 — 동시에 살피는 둘이 함께 물으면 뒤의 창이 앞의 창을 덮어 앞의 것이 영영 남았다.
        assertTrue(js.contains("if (Q.asking) { Q.askWaiting.push(function () { ask(item, decide, existing); }); return; }"))
        assertTrue(ask.indexOf("primary: true") in 0 until ask.indexOf("value: 'overwrite'"), ask)
        // 끝내고 받은 409(받는 동안 누가 같은 이름을 만들었다)는 다시 묻는다.
        assertTrue(js.contains("} else if (xhr.status === 409 && d && d.exists) {"))
    }

    @Test
    fun 모든_파일에_적용은_겹치는_수와_함께이고_폴더와_겹치면_덮어쓰기를_고를_수_없다() {
        // 사용자 결정 11-21 — 윈도의 덮어쓰기 창처럼 '모든 파일에 적용', 겹치는 수를 알면 함께.
        val js = text("fileman.js")
        val ask = js.substringAfter("function ask(item, decide, existing) {").substringBefore("\n  }\n")
        assertTrue(ask.contains("check: more ? '모든 파일에 적용' + (count > 1 ? ' (겹치는 파일 ' + count + '개)' : '') : null"), ask)
        assertFalse(js.contains("나머지에도 적용"), "옛 글")
        // 같은 이름이 폴더면 '덮어쓰기' 는 흐리게 + 까닭(서버와 같은 글), 기본 단추가 되지 않는다.
        assertTrue(ask.contains("{ label: '덮어쓰기', value: 'overwrite', danger: true, disabled: folder, title: folder ? FOLDER_CLASH : '' }"), ask)
        assertTrue(ask.contains("dim: folder ? FOLDER_CLASH : ''"), ask)
        assertTrue(js.contains("var FOLDER_CLASH = '${io.github.donggi.iroirobrowser.tether.core.SharedOps.FOLDER_SAME_NAME}';"))
        assertTrue(js.contains("if ((b.primary || b.danger) && !b.disabled) primary = primary || { el: bt, value: b.value };"))
        // '모든 파일에 적용' 으로 덮어쓰기를 골랐어도 폴더와 겹친 것은 새 이름으로(실패로 끝나지 않게).
        assertTrue(js.contains("if (mode === 'overwrite' && existing.dir) { mode = 'rename'; item.folderClash = true; }"))
        // 서버가 실제로 둔 이름을 쓴다.
        assertTrue(js.contains("item.name = d.name;"))
    }

    /** fileman.js 의 `var <name> = /[…]/gu;` 글자 묶음을 펴서 코드 포인트 집합으로. */
    private fun jsClass(js: String, name: String): Set<Int> {
        assertTrue(js.contains("var $name = /[") && js.substringAfter("var $name = /[").contains("]/gu;"), "$name 은 u 깃발로 코드 포인트째")
        val cls = js.substringAfter("var $name = /[").substringBefore("]/gu;")
        val token = Regex("""\\u\{([0-9a-fA-F]{1,6})\}|\\u([0-9a-fA-F]{4})|-""")
        val tokens = token.findAll(cls).toList()
        assertEquals(cls, tokens.joinToString("") { it.value }, "묶음은 \\uXXXX·\\u{X…} 와 범위의 - 로만: $cls")
        fun cp(m: MatchResult) = m.groupValues[1].ifEmpty { m.groupValues[2] }.toInt(16)
        val out = HashSet<Int>()
        var i = 0
        while (i < tokens.size) {
            val lo = cp(tokens[i])
            if (i + 1 < tokens.size && tokens[i + 1].value == "-") {
                (lo..cp(tokens[i + 2])).forEach { out += it }
                i += 3
            } else {
                out += lo
                i += 1
            }
        }
        return out
    }

    @Test
    fun 화면이_빼는_글자는_서버와_같다() {
        // 새 이름에서 빼는 것(STRIP_RE ↔ SharedPaths.isStrippedCodePoint)과 휴지통 판정에서 떼는 것(IGNORABLE_RE ↔ isIgnorableCodePoint)을
        // 따로 모든 코드 포인트(보충 평면 포함 — u 깃발)에서 견준다(11-27 재검토 — 일반 이름의 소프트 하이픈·태그 글자·이체 선택자는 둔다).
        val js = text("fileman.js")
        val paths = io.github.donggi.iroirobrowser.tether.core.SharedPaths
        assertEquals((0..0x10FFFF).filter { paths.isStrippedCodePoint(it) }.toSet(), jsClass(js, "STRIP_RE"))
        assertEquals((0..0x10FFFF).filter { paths.isIgnorableCodePoint(it) }.toSet(), jsClass(js, "IGNORABLE_RE"))
        assertTrue(js.contains("function cleanName(name) { return name.replace(STRIP_RE, ''); }"))
        // 휴지통 이름은 무시되는 글자 전부를 떼고 본다(서버와 같다).
        assertTrue(js.contains("function isTrashName(name) { return name.replace(IGNORABLE_RE, '').replace(/[. ]+$/, '').toLowerCase() === '.web-trash'; }"))
        // 새 이름의 같은 이름 검사·보내기는 뺀 뒤의 이름으로.
        assertTrue(js.contains("var parts = rel.split('/').map(cleanName);"))
        assertTrue(js.contains("var name = cleanName(r.text);"))
    }

    @Test
    fun 이름을_열쇠로_쓰는_맵은_원형이_없다() {
        // `{}` 를 이름·경로 열쇠의 맵으로 쓰면 constructor·toString·__proto__ 같은 이름이 늘 '있는 것'(고른 것·같은 이름·이미 읽은 폴더·
        // 아는 확장자)으로 읽혔다(2026-11 점검).
        val js = text("fileman.js")
        assertTrue(js.contains("function dict() { return Object.create(null); }"))
        val maps = listOf(
            "selected: dict(),", "S.selected = dict();", "var keep = dict();", "var lower = dict();", "var o = dict();", "failed: dict(),",
            "names: dict(),", "var set = dict();", "seen = dict();", "var byDir = dict();", "var keepNodes = dict();", "var dirs = dict();",
            "V.nodes = dict();", "nodes: dict(),", "var o = dict(); o[''] = { path: '', loaded: false, open: true, children: [] };",
        )
        for (line in maps) assertTrue(js.contains(line), line)
        // 남은 `{}` 는 정해진 열쇠만 쓰는 셋뿐이다 — 요청 머리, 요소 id, 포인터 번호.
        val left = Regex("""[=:]\s*\{\}""").findAll(js).map { m ->
            js.substring(js.lastIndexOf('\n', m.range.first) + 1, js.indexOf('\n', m.range.first)).trim()
        }.toList()
        assertEquals(3, left.size, left.joinToString("\n"))
        assertTrue(left.any { it.startsWith("var opts = ") } && left.any { it == "var el = {};" } && left.any { it.contains("pointers: {}") }, left.joinToString("\n"))
    }

    @Test
    fun 창은_한_번에_하나_열린_창을_덮지_않는다() {
        // 올리기의 묻기가 떠 있는 동안 옮기기의 '같은 이름' 창이 뜨자 묻기의 약속이 영영 풀리지 않아 대기열이 '살피는 중' 에 멈췄다
        // (Edge 헤드리스에서 재현) — 창은 앞 창이 닫힌 뒤에 연다.
        val js = text("fileman.js")
        val wrapper = js.substringAfter("function dialog(opts) {").substringBefore("\n  }")
        assertTrue(wrapper.contains("dialogTail.then(function () { return openDialog(opts); })"), wrapper)
        assertTrue(wrapper.contains("dialogTail = shown.then("), wrapper)
        assertEquals(2, Regex("\\bopenDialog\\(").findAll(js).count(), "창을 여는 길은 dialog() 하나(정의와 부름)")
    }

    @Test
    fun 휴지통_보관_기간은_기본_30일이고_서버가_손볼_때마다_읽는다() {
        val settings = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherSettings.kt")
        assertTrue(settings.contains("SharedOps.trashDays(prefs.getInt(K_TRASH_DAYS, SharedOps.DEFAULT_TRASH_DAYS))"))
        assertEquals(30, io.github.donggi.iroirobrowser.tether.core.SharedOps.DEFAULT_TRASH_DAYS)
        assertEquals(listOf(7, 30, 90, 0), io.github.donggi.iroirobrowser.tether.core.SharedOps.TRASH_RETENTION_CHOICES)
        val service = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherService.kt")
        assertTrue(service.contains("trashRetentionDays = { settings.trashDays }"), "손볼 때마다 읽는 람다")
        assertTrue(service.contains("settings.listenTrashDays { files?.requestTrashSweep() }"), "바꾸면 곧바로 한 번 더")
        assertTrue(service.contains("trashListener?.let { settings.stopListening(it) }"))
        val activity = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/TetherActivity.kt")
        assertTrue(activity.contains("settings.trashDays = choices[which]"))
        // 공유 폴더 절의 '관리 허용' 곁.
        assertTrue(activity.indexOf("trashChoice(column)") > activity.indexOf("note(column, R.string.tether_manage_note)"))
        assertTrue(activity.indexOf("trashChoice(column)") < activity.indexOf("section(column, R.string.tether_section_pc)"))
        val strings = strings()
        assertEquals("휴지통 보관", strings["tether_trash_keep_title"])
        assertEquals(1, strings.getValue("tether_trash_note").count { it == '.' }, "설명은 한 문장")
        // 화면의 휴지통 보기에 한 줄.
        val js = text("fileman.js")
        assertTrue(js.contains("'보관 기간 ' + days + '일 — 지난 것은 저절로 비워집니다'"))
    }

    @Test
    fun 섬네일은_남이_올린_그림을_이_프로세스에서_풀지_않는다() {
        val folder = code("src/main/kotlin/io/github/donggi/iroirobrowser/tether/SharedFolder.kt")
        for (decoder in listOf("BitmapFactory", "ImageDecoder", "loadThumbnail", "getDocumentThumbnail", "createImageThumbnail", "decodeStream")) {
            assertFalse(folder.contains(decoder), decoder)
        }
        // java.io.File 로 공유 폴더를 짓지 않는다(UID 경계 — CLAUDE.md '구조').
        assertFalse(Regex("\\bFile\\(").containsMatchIn(folder))
        assertFalse(folder.contains("import java.io.File\n"))
    }

    private fun strings(): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_tether.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }
}
