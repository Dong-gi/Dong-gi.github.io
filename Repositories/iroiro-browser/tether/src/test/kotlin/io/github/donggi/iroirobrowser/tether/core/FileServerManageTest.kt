package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 파일 서버의 **관리 기능** — 스위치(요청마다 본다)·CSRF 꼴(단순 POST·다른 출처)·폴더 만들기·이름 바꾸기·옮기기·복사(자기 안으로는
 * 거절)·지우기는 숨은 휴지통으로·되살리기·비우기·휴지통은 보통의 길로 닿지 않음·폴더째 올리기·같은 이름 묻기.
 */
class FileServerManageTest {

    private val kits = ArrayList<FileServerKit>()

    private fun kit(
        manage: Boolean = true,
        nativeMove: Boolean = true,
        maxTreeEntries: Int = SharedOps.MAX_TREE_ENTRIES,
        trashDays: Int = SharedOps.DEFAULT_TRASH_DAYS,
        trashSweepFirstMs: Long = FileServer.TRASH_SWEEP_FIRST_MS,
        wallClockMs: () -> Long = { System.currentTimeMillis() },
    ): FileServerKit {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        return FileServerKit(
            root, manage, DirectoryTree(root, nativeMove = nativeMove), maxTreeEntries = maxTreeEntries,
            trashDays = { trashDays }, trashSweepFirstMs = trashSweepFirstMs, wallClockMs = wallClockMs,
        ).also { kits += it }
    }

    @AfterTest
    fun cleanUp() {
        kits.forEach { it.close() }
    }

    private val trash = SharedPaths.TRASH_NAME

    @Test
    fun 관리_스위치가_꺼져_있으면_403_과_까닭이고_요청마다_본다() {
        val k = kit(manage = false)
        k.file("a.txt")
        val attempts = listOf(
            { k.put("/api/folder?path=new") },
            { k.post("/api/rename", """{"path":"a.txt","name":"b.txt"}""") },
            { k.post("/api/move", """{"base":"","names":["a.txt"],"to":"new"}""") },
            { k.post("/api/copy", """{"base":"","names":["a.txt"],"to":""}""") },
            { k.post("/api/delete", """{"base":"","names":["a.txt"]}""") },
            { k.get("/api/trash") },
            { k.post("/api/trash/restore", """{"ids":["t1-00000000"]}""") },
            { k.post("/api/trash/purge", """{"all":true}""") },
            { k.put("/api/file?path=new/x.txt&parents=1", "x".toByteArray()) },
        )
        for (attempt in attempts) {
            val reply = attempt()
            assertEquals(403, reply.status, reply.text)
            assertEquals(true, reply.json()["manageOff"])
            assertTrue(reply.text.contains("관리 허용"))
        }
        assertEquals(setOf("a.txt"), k.names())
        // 켜면 다음 요청부터 — 서버를 다시 세우지 않는다.
        k.manage.set(true)
        assertEquals(200, k.put("/api/folder?path=new").status)
        k.manage.set(false)
        assertEquals(403, k.put("/api/folder?path=new2").status)
        assertEquals(setOf("a.txt", "new"), k.names())
        // 정보에도 스위치가 보인다.
        assertEquals(false, k.get("/api/info").json()["manage"])
    }

    @Test
    fun 단순_POST_는_거절한다_CSRF() {
        val k = kit()
        k.file("a.txt")
        // 다른 사이트의 폼·fetch 가 사전 확인 없이 보낼 수 있는 꼴들 — 아무것도 바뀌지 않는다.
        for (type in listOf("text/plain", "application/x-www-form-urlencoded", "multipart/form-data; boundary=x", null, "application/jsonp", "text/json")) {
            val reply = k.post("/api/delete", """{"base":"","names":["a.txt"]}""", type = type)
            assertEquals(415, reply.status, "$type")
        }
        assertEquals(setOf("a.txt"), k.names())
        // 매개변수가 붙은 JSON 은 받는다.
        assertEquals(200, k.post("/api/delete", """{"base":"","names":["a.txt"]}""", type = "application/json; charset=utf-8").status)
        assertFalse("a.txt" in k.names())
    }

    @Test
    fun 다른_출처의_바꾸는_요청은_403() {
        val k = kit()
        k.file("a.txt")
        val evil = listOf("Origin: http://evil.example\r\n", "Origin: http://192.168.49.10\r\n", "Origin: https://192.168.49.1\r\n", "Origin: null\r\n", "Sec-Fetch-Site: cross-site\r\n", "Sec-Fetch-Site: same-site\r\n")
        for (header in evil) {
            assertEquals(403, k.post("/api/rename", """{"path":"a.txt","name":"b.txt"}""", extra = header).status, header)
            assertEquals(403, k.put("/api/file?path=c.txt", "c".toByteArray(), extra = header).status, header)
        }
        assertEquals(setOf("a.txt"), k.names())
        // 같은 출처(포트가 옮겨 서도)는 받는다.
        val same = "Origin: http://192.168.49.1:8081\r\nSec-Fetch-Site: same-origin\r\n"
        assertEquals(200, k.post("/api/rename", """{"path":"a.txt","name":"b.txt"}""", extra = same).status)
        assertEquals(200, k.put("/api/file?path=c.txt", "c".toByteArray(), extra = same).status)
        assertEquals(setOf("b.txt", "c.txt"), k.names())
    }

    @Test
    fun GET_과_OPTIONS_로는_관리_길이_없다() {
        val k = kit()
        k.file("a.txt")
        for (request in listOf("GET /api/delete?base=&names=a.txt", "GET /api/rename?path=a.txt&name=b", "OPTIONS /api/delete", "GET /api/folder?path=x", "DELETE /api/file?path=a.txt", "PATCH /api/rename")) {
            val reply = rawHttp(k.port, "$request HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 0\r\n\r\n".toByteArray())
            assertEquals(404, reply.status, request)
        }
        assertEquals(setOf("a.txt"), k.names())
    }

    @Test
    fun 폴더_만들기와_이름_바꾸기() {
        val k = kit()
        k.file("d/a.txt")
        assertEquals(200, k.put("/api/folder?path=d/${enc("새 폴더")}").status)
        assertTrue(File(k.root, "d/새 폴더").isDirectory)
        assertEquals(409, k.put("/api/folder?path=d/${enc("새 폴더")}").status)
        assertEquals(409, k.put("/api/folder?path=d/A.TXT").status, "대소문자만 다른 것도 겹친다")
        assertEquals(404, k.put("/api/folder?path=nope/x").status)
        // 제어 글자만이라 빼면 비는 이름도 거절이다(제어 글자가 섞인 이름은 빼고 만든다 — 아래 '새 이름의 보이지 않는 글자').
        for (bad in listOf("d/%01", "d/%E2%80%8B%E2%80%8E", "", "d/..", "d/.%E2%80%8B.", "d/a%5Cb", "%20%20")) {
            assertTrue(k.put("/api/folder?path=$bad").status in setOf(400, 404), bad)
        }
        // 맨 위의 휴지통 이름은 보통의 길로는 없는 자리다(대소문자를 가리지 않는다).
        assertEquals(404, k.put("/api/folder?path=${enc(trash.uppercase())}").status)
        assertFalse(File(k.root, trash.uppercase()).exists())

        val renamed = k.post("/api/rename", """{"path":"d/a.txt","name":"b.txt"}""")
        assertEquals(200, renamed.status)
        assertEquals("b.txt", renamed.json()["name"])
        assertEquals(setOf("b.txt", "새 폴더"), k.names("d"))
        assertEquals(409, k.post("/api/rename", """{"path":"d/b.txt","name":"새 폴더"}""").status)
        assertEquals(400, k.post("/api/rename", """{"path":"d/b.txt","name":"x/y"}""").status)
        assertEquals(400, k.post("/api/rename", """{"path":"","name":"x"}""").status, "공유 폴더 자체")
        assertEquals(400, k.post("/api/rename", """{"path":"d","name":"$trash"}""").status, "맨 위의 휴지통 이름")
        assertEquals(404, k.post("/api/rename", """{"path":"d/none","name":"x"}""").status)
        // 대소문자만 바꾸기는 겹침(409)이 아니다(결과의 이름은 이 PC 의 파일 시스템에 달려 보지 않는다).
        assertEquals(200, k.post("/api/rename", """{"path":"d/b.txt","name":"B.txt"}""").status)
    }

    @Test
    fun 옮기기와_복사() {
        val k = kit()
        k.file("src/a.txt", "A")
        k.file("src/sub/b.txt", "B")
        k.dir("dst")
        val moved = k.post("/api/move", """{"base":"src","names":["a.txt"],"to":"dst"}""")
        assertEquals(200, moved.status, moved.text)
        assertEquals("A", File(k.root, "dst/a.txt").readText())
        assertFalse(File(k.root, "src/a.txt").exists())

        val copied = k.post("/api/copy", """{"base":"src","names":["sub"],"to":"dst"}""")
        assertEquals(200, copied.status, copied.text)
        assertEquals("B", File(k.root, "dst/sub/b.txt").readText())
        assertEquals("B", File(k.root, "src/sub/b.txt").readText())

        // 같은 폴더로 복사하면 사본.
        assertEquals(200, k.post("/api/copy", """{"base":"dst","names":["a.txt"],"to":"dst"}""").status)
        assertTrue("a (1).txt" in k.names("dst"))
        // 같은 폴더로 옮기기는 건너뛴다.
        val same = k.post("/api/move", """{"base":"dst","names":["a.txt"],"to":"dst"}""").json()
        assertEquals(listOf("a.txt"), same["skipped"])
    }

    @Test
    fun 폴더를_자기_안으로_옮기거나_복사하면_거절하고_아무것도_바꾸지_않는다() {
        val k = kit()
        k.file("a/b/c.txt")
        for (op in listOf("move", "copy")) {
            for (to in listOf("a", "a/b")) {
                val reply = k.post("/api/$op", """{"base":"","names":["a"],"to":"$to"}""")
                assertEquals(400, reply.status, "$op → $to")
                assertTrue(reply.text.contains("자기 안으로"))
            }
        }
        assertEquals(setOf("b"), k.names("a"))
        assertEquals(setOf("c.txt"), k.names("a/b"))
    }

    @Test
    fun 같은_이름은_묻고_건너뛰거나_새_이름으로() {
        val k = kit()
        k.file("src/x.txt", "new")
        k.file("src/y.txt", "y")
        k.file("dst/X.txt", "old")
        val ask = k.post("/api/copy", """{"base":"src","names":["x.txt","y.txt"],"to":"dst"}""")
        assertEquals(409, ask.status)
        assertEquals(listOf("x.txt"), ask.json()["conflicts"])
        assertEquals(setOf("X.txt"), k.names("dst"), "물을 때는 아무것도 하지 않는다")

        val skip = k.post("/api/copy", """{"base":"src","names":["x.txt","y.txt"],"to":"dst","conflict":"skip"}""").json()
        assertEquals(listOf("x.txt"), skip["skipped"])
        assertEquals("old", File(k.root, "dst/X.txt").readText())
        assertEquals("y", File(k.root, "dst/y.txt").readText())

        val rename = k.post("/api/move", """{"base":"src","names":["x.txt"],"to":"dst","conflict":"rename"}""").json()
        assertEquals(listOf(mapOf("from" to "x.txt", "to" to "x (1).txt")), rename["done"])
        assertEquals("new", File(k.root, "dst/x (1).txt").readText())
        assertEquals("old", File(k.root, "dst/X.txt").readText())
        assertFalse(File(k.root, "src/x.txt").exists())
        assertEquals(400, k.post("/api/move", """{"base":"src","names":["y.txt"],"to":"dst","conflict":"overwrite"}""").status)
    }

    @Test
    fun 제공자가_옮기기를_못_하면_복사한_뒤_지운다() {
        val k = kit(nativeMove = false)
        k.file("a/deep/f.txt", "F")
        k.dir("b")
        assertEquals(200, k.post("/api/move", """{"base":"","names":["a"],"to":"b"}""").status)
        assertEquals("F", File(k.root, "b/a/deep/f.txt").readText())
        assertFalse(File(k.root, "a").exists())
    }

    @Test
    fun 손으로_짓는_복사의_항목_수_상한() {
        val k = kit(maxTreeEntries = 5)
        for (i in 1..6) k.file("many/f$i.txt")
        val reply = k.post("/api/copy", """{"base":"","names":["many"],"to":"","conflict":"rename"}""")
        assertEquals(200, reply.status)
        val failed = reply.json()["failed"] as List<*>
        assertEquals(1, failed.size)
        assertTrue(failed.toString().contains("너무 많습니다"))
        // 반쯤 지은 사본을 남기지 않는다.
        assertEquals(setOf("many"), k.names())
    }

    @Test
    fun 지우기는_숨은_휴지통으로_옮기고_되살리기는_제자리로() {
        val k = kit()
        k.file("a/b.txt", "B")
        k.file("a/c/d.txt", "D")
        val deleted = k.post("/api/delete", """{"base":"a","names":["b.txt","c"]}""")
        assertEquals(200, deleted.status, deleted.text)
        assertEquals(emptySet(), k.names("a"))
        // 휴지통은 디스크에는 있지만 목록에서 감춘다.
        assertTrue(trash in k.names())
        assertFalse(k.get("/api/list?path=").text.contains(trash))

        val items = k.get("/api/trash").json()["items"] as List<*>
        assertEquals(2, items.size)
        val byName = items.associateBy { (it as Map<*, *>)["name"] }
        assertEquals("a", (byName["b.txt"] as Map<*, *>)["from"])
        assertEquals(true, (byName["c"] as Map<*, *>)["dir"])

        // 원래 폴더가 사라져도 되살리면 다시 만든다. 같은 이름이 생겼으면 새 이름으로.
        File(k.root, "a").deleteRecursively()
        k.file("a/b.txt", "other")
        val ids = items.map { (it as Map<*, *>)["id"] as String }
        val restored = k.post("/api/trash/restore", """{"ids":[${ids.joinToString(",") { q(it) }}]}""")
        assertEquals(200, restored.status, restored.text)
        assertEquals("B", File(k.root, "a/b (1).txt").readText())
        assertEquals("other", File(k.root, "a/b.txt").readText())
        assertEquals("D", File(k.root, "a/c/d.txt").readText())
        assertEquals(emptyList<Any>(), k.get("/api/trash").json()["items"])
    }

    @Test
    fun 휴지통_비우기와_하나씩_지우기() {
        val k = kit()
        k.file("x.txt")
        k.file("y.txt")
        k.post("/api/delete", """{"base":"","names":["x.txt","y.txt"]}""")
        val ids = (k.get("/api/trash").json()["items"] as List<*>).map { (it as Map<*, *>)["id"] as String }
        assertEquals(1L, k.post("/api/trash/purge", """{"ids":[${q(ids[0])}]}""").json()["count"])
        assertEquals(1, (k.get("/api/trash").json()["items"] as List<*>).size)
        assertEquals(1L, k.post("/api/trash/purge", """{"all":true}""").json()["count"])
        // 비우기는 칸과 기록을 모두 지우고 휴지통 폴더와 .nomedia 는 남긴다.
        assertEquals(setOf(trash), k.names())
        assertEquals(setOf(SharedOps.NO_MEDIA), k.names(trash))
        assertEquals(emptyList<Any>(), k.get("/api/trash").json()["items"])
        assertEquals(400, k.post("/api/trash/restore", """{"ids":["../x"]}""").status)
        assertEquals(400, k.post("/api/trash/purge", """{"ids":["t1-zz"]}""").status)
    }

    @Test
    fun 휴지통은_보통의_길로_닿지_않는다() {
        val k = kit()
        k.file("secret.txt", "S")
        k.post("/api/delete", """{"base":"","names":["secret.txt"]}""")
        val id = ((k.get("/api/trash").json()["items"] as List<*>)[0] as Map<*, *>)["id"] as String
        for (variant in listOf(trash, trash.uppercase(), "$trash/$id")) {
            assertEquals(404, k.get("/api/list?path=${enc(variant)}").status, variant)
            assertEquals(404, k.get("/api/file?path=${enc("$variant/secret.txt")}").status, variant)
            assertEquals(404, k.get("/api/thumb?path=${enc("$variant/secret.txt")}").status, variant)
            assertEquals(400, k.put("/api/file?path=${enc("$variant/x.txt")}", "x".toByteArray()).status, variant)
            assertEquals(404, k.post("/api/move", """{"base":"","names":["x"],"to":${q(variant)}}""").status, variant)
            assertEquals(404, k.post("/api/delete", """{"base":${q(variant)},"names":["x"]}""").status, variant)
            assertEquals(404, k.post("/api/zip", """{"base":${q(variant)},"names":["x"]}""").status, variant)
        }
        assertEquals(400, k.post("/api/zip", """{"base":"","names":[${q(trash)}]}""").status)
        assertEquals(400, k.post("/api/copy", """{"base":"","names":[${q(trash)}],"to":""}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":[${q(trash)}]}""").status)
        assertEquals(400, k.put("/api/file?path=${enc(trash)}", "x".toByteArray()).status)
    }

    @Test
    fun 폴더째_올리기는_관리일_때만_없는_폴더를_만든다() {
        val k = kit(manage = false)
        assertEquals(403, k.put("/api/file?path=p/q/r.txt&parents=1", "R".toByteArray()).status)
        assertEquals(400, k.put("/api/file?path=p/q/r.txt", "R".toByteArray()).status, "parents 없이는 부모를 만들지 않는다")
        assertFalse(File(k.root, "p").exists())
        k.manage.set(true)
        assertEquals(200, k.put("/api/file?path=p/q/r.txt&parents=1", "R".toByteArray()).status)
        assertEquals("R", File(k.root, "p/q/r.txt").readText())
        k.file("f")
        assertEquals(409, k.put("/api/file?path=f/x.txt&parents=1", "x".toByteArray()).status, "가는 길에 파일")
    }

    @Test
    fun 덮어쓰기는_overwrite_1_로_밝힌_요청만() {
        val k = kit(manage = false)
        k.file("same.txt", "old")
        // 밝히지 않은 요청(옛 화면·curl)은 덮지 않는다 — 사용자 결정: 같은 이름이면 화면이 한 번 묻는다.
        val reply = k.put("/api/file?path=same.txt", "new".toByteArray())
        assertEquals(409, reply.status)
        assertEquals(true, reply.json()["exists"])
        assertEquals(409, k.put("/api/file?path=same.txt&overwrite=0", "new".toByteArray()).status)
        assertEquals(409, k.put("/api/file?path=SAME.txt", "new".toByteArray()).status, "대소문자만 다른 것도 같은 이름")
        assertEquals(400, k.put("/api/file?path=same.txt&overwrite=yes", "new".toByteArray()).status)
        assertEquals("old", File(k.root, "same.txt").readText())
        assertEquals(200, k.put("/api/file?path=same.txt&overwrite=1", "new".toByteArray()).status, "덮어쓰기는 예전처럼 스위치와 상관없다")
        assertEquals("new", File(k.root, "same.txt").readText())
        assertEquals(200, k.put("/api/file?path=fresh.txt", "f".toByteArray()).status, "새 이름은 그대로 올린다")
        k.dir("d")
        assertEquals(409, k.put("/api/file?path=d", "x".toByteArray()).status, "폴더와 같은 이름도 겹침")
        assertEquals(400, k.put("/api/file?path=${enc("\u0001\u200B")}", "x".toByteArray()).status, "제어 글자를 빼면 비는 이름")
    }

    // ── 같은 이름의 폴더·덮어쓴 원래 파일은 휴지통으로(사용자 결정 11-21) ──────────────────────────────────────────────

    @Test
    fun 폴더와_같은_이름은_덮어쓰기로도_덮지_않고_까닭을_준다() {
        val k = kit(manage = false)
        k.file("Photos/a.jpg", "A")
        // 한 폴더에 같은 이름의 파일과 폴더는 둘 수 없다 — overwrite=1 이어도 409 와 folder, 폴더는 그대로.
        for (target in listOf("Photos", "photos", "PHOTOS")) {
            for (flag in listOf("", "&overwrite=1", "&overwrite=0")) {
                val reply = k.put("/api/file?path=$target$flag", "x".toByteArray())
                assertEquals(409, reply.status, "$target$flag")
                assertEquals(true, reply.json()["exists"])
                assertEquals(true, reply.json()["folder"], reply.text)
                assertTrue((reply.json()["error"] as String).startsWith(SharedOps.FOLDER_SAME_NAME), reply.text)
            }
        }
        assertTrue(File(k.root, "Photos").isDirectory)
        assertEquals(setOf("a.jpg"), k.names("Photos"))
        assertEquals(setOf("Photos"), k.names(), "휴지통도 임시도 생기지 않는다")
        // 파일과 겹친 409 는 folder 가 아니다(화면은 이것으로 '덮어쓰기' 를 고를 수 있게 둔다).
        k.file("f.txt", "F")
        assertEquals(false, k.put("/api/file?path=f.txt", "x".toByteArray()).json()["folder"])
    }

    @Test
    fun 덮어쓴_원래_파일은_관리가_꺼져_있어도_휴지통으로_가고_되살릴_수_있다() {
        val k = kit(manage = false)
        k.file("sub/doc.txt", "old")
        val reply = k.put("/api/file?path=sub/doc.txt&overwrite=1", "new!".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new!", File(k.root, "sub/doc.txt").readText())
        assertTrue(File(k.root, "$trash/${SharedOps.NO_MEDIA}").isFile, "이 길로 생긴 휴지통에도 .nomedia")
        // 관리가 꺼져 있으면 휴지통을 볼 수 없다 — 켜면 보이고 되살린다(같은 이름이 있으니 `doc (1).txt`).
        assertEquals(403, k.get("/api/trash").status)
        k.manage.set(true)
        val items = k.get("/api/trash").json()["items"] as List<*>
        assertEquals(1, items.size)
        val item = items[0] as Map<*, *>
        assertEquals("doc.txt", item["name"])
        assertEquals("sub", item["from"])
        val restored = k.post("/api/trash/restore", """{"ids":[${q(item["id"] as String)}]}""")
        assertEquals(200, restored.status, restored.text)
        assertEquals("old", File(k.root, "sub/doc (1).txt").readText())
        // 대소문자만 다른 원래 것도 휴지통으로 — 올린 이름으로 둔다. 새 파일(겹침 없음)은 휴지통에 아무것도 두지 않는다.
        k.manage.set(false)
        k.file("CASE.txt", "old")
        assertEquals(true, k.put("/api/file?path=case.txt&overwrite=1", "new".toByteArray()).json()["trashed"])
        assertEquals(false, k.put("/api/file?path=brand-new.txt&overwrite=1", "n".toByteArray()).json()["trashed"])
        k.manage.set(true)
        assertEquals(setOf("CASE.txt"), (k.get("/api/trash").json()["items"] as List<*>).map { (it as Map<*, *>)["name"] }.toSet())
        assertEquals("new", File(k.root, k.names().single { it.equals("case.txt", ignoreCase = true) }).readText())
    }

    @Test
    fun 원래_파일을_휴지통으로_옮기지_못하면_덮어쓰지_않는다() {
        val k = kit(manage = false)
        k.file("keep.txt", "original")
        // 휴지통 자리에 파일이 있어 휴지통을 만들 수 없다(맨 위의 `.web-trash` 파일 — 손님은 만들 수 없지만 폰에서는 생길 수 있다).
        File(k.root, trash).writeText("not a folder")
        val reply = k.put("/api/file?path=keep.txt&overwrite=1", "replacement".toByteArray())
        assertEquals(500, reply.status, reply.text)
        assertTrue(reply.text.contains("덮어쓰지 않았습니다") && reply.text.contains("원래 파일은 그대로"), reply.text)
        assertEquals("original", File(k.root, "keep.txt").readText())
        assertEquals(setOf("keep.txt", trash), k.names(), "받은 임시는 지운다")
    }

    @Test
    fun 덮어쓰기는_휴지통과_같은_차례를_쥔다() {
        // 원래 것을 옮기는 동안 휴지통을 비우거나 손보는 것과 엇갈리지 않게 관리 동작과 같은 잠금을 쓴다 — 붙잡힌 동안 오면 기다리다 503 이고
        // 원래 파일은 그대로다(여기서는 시간이 걸리는 tryLock 대신 잠금이 풀린 뒤 곧바로 되는 것만 본다).
        val k = kit(manage = false)
        k.file("x.txt", "old")
        val gate = java.util.concurrent.CountDownLatch(1)
        val held = java.util.concurrent.CountDownLatch(1)
        val lock = FileServer::class.java.getDeclaredField("mutation").apply { isAccessible = true }.get(k.server) as java.util.concurrent.locks.ReentrantLock
        val holder = Thread {
            lock.lock()
            held.countDown()
            gate.await()
            lock.unlock()
        }.apply { start() }
        held.await()
        val result = java.util.concurrent.atomic.AtomicReference<HttpReply>()
        val uploader = Thread { result.set(k.put("/api/file?path=x.txt&overwrite=1", "new".toByteArray())) }.apply { start() }
        Thread.sleep(300)
        assertEquals("old", File(k.root, "x.txt").readText(), "잠금을 쥔 동안에는 덮지 않는다")
        gate.countDown()
        uploader.join(10_000)
        holder.join(10_000)
        assertEquals(200, result.get().status)
        assertEquals("new", File(k.root, "x.txt").readText())
    }

    // ── 새 이름의 보이지 않는 글자(사용자 결정 11-21 — 거절하지 않고 뺀다) ─────────────────────────────────────────────

    @Test
    fun 새_이름의_제어_방향_폭_없는_글자는_빼고_쓰고_답에_실제_이름을_준다() {
        val k = kit()
        // 올리기 — RLO 로 `사진exe.jpg` 처럼 보이게 한 이름, C0·C1·폭 없는 글자.
        val upload = k.put("/api/file?path=${enc("사진\u202Egpj.exe")}", "x".toByteArray())
        assertEquals(200, upload.status, upload.text)
        assertEquals("사진gpj.exe", upload.json()["name"])
        assertTrue(File(k.root, "사진gpj.exe").isFile)
        assertEquals("a b.txt", k.put("/api/file?path=${enc("a\u0001\u0085 b\u200B.txt\uFEFF")}", "x".toByteArray()).json()["name"])
        // 같은 이름 검사는 뺀 뒤의 이름으로 — `a b.txt` 가 있으니 묻지 않은 올리기는 409.
        assertEquals(409, k.put("/api/file?path=${enc("a b\u2066.txt")}", "y".toByteArray()).status)
        // 폴더 만들기·이름 바꾸기.
        val made = k.put("/api/folder?path=${enc("새\u200D폴더\u061C")}")
        assertEquals(200, made.status, made.text)
        assertEquals("새폴더", made.json()["name"])
        assertEquals(409, k.put("/api/folder?path=${enc("\u200E새폴더")}").status, "뺀 뒤의 이름으로 겹침을 본다")
        val renamed = k.post("/api/rename", """{"path":"사진gpj.exe","name":${q("문서\u202D\u0000.txt")}}""")
        assertEquals(200, renamed.status, renamed.text)
        assertEquals("문서.txt", renamed.json()["name"])
        // 빼고 나서 비거나 . / .. 이거나 맨 위의 휴지통 이름이면 지금처럼 거절.
        assertEquals(400, k.post("/api/rename", """{"path":"문서.txt","name":${q("\u200B\u2028")}}""").status)
        assertEquals(400, k.post("/api/rename", """{"path":"문서.txt","name":${q(".\u200B.")}}""").status)
        for (sneaky in listOf(".web\u200B-trash", "\u202E.web-trash", ".WEB-TRASH\u2060.")) {
            assertEquals(400, k.put("/api/file?path=${enc(sneaky)}", "x".toByteArray()).status, sneaky)
            // 보이지 않는 글자를 뗀 이름이 휴지통이라 경로부터 숨은 자리(404 — 끝에 점을 붙인 별칭과 같다, 2026-11 점검).
            assertEquals(404, k.put("/api/folder?path=${enc(sneaky)}").status, sneaky)
            assertEquals(400, k.post("/api/rename", """{"path":"문서.txt","name":${q(sneaky)}}""").status, sneaky)
            assertEquals(400, k.put("/api/file?path=${enc("$sneaky/x.txt")}&parents=1", "x".toByteArray()).status, sneaky)
        }
        assertFalse(k.names().any { it.contains("trash", ignoreCase = true) }, "휴지통 자리에 아무것도 앉지 않았다: ${k.names()}")
        // 맨 위가 아니면 휴지통 이름도 그냥 이름이다(지금 규칙 그대로).
        assertEquals(200, k.put("/api/folder?path=${enc("새폴더/.web\u200B-trash")}").status)
    }

    @Test
    fun 폴더째_올리기의_성분은_없는_것만_빼고_만들고_있는_폴더는_이름_그대로_찾는다() {
        val k = kit()
        // 다른 앱이 지은, 보이지 않는 글자가 든 폴더 — 그 이름 그대로면 그 안으로 간다(다듬어 새 폴더를 만들지 않는다).
        val odd = "앨범\u200B"
        k.dir(odd)
        val inOdd = k.put("/api/file?path=${enc("$odd/a.txt")}&parents=1", "A".toByteArray())
        assertEquals(200, inOdd.status, inOdd.text)
        assertTrue(File(k.root, "$odd/a.txt").isFile)
        assertFalse(File(k.root, "앨범").exists())
        // 없는 성분은 빼고 만든다 — 같은 꼴의 둘째 파일도 같은 폴더로.
        for (file in listOf("b.txt", "c\u200F.txt")) {
            val reply = k.put("/api/file?path=${enc("새\u2067/깊\u0007은/$file")}&parents=1", "B".toByteArray())
            assertEquals(200, reply.status, reply.text)
        }
        assertEquals(setOf("b.txt", "c.txt"), k.names("새/깊은"))
        // 옮기기·복사에서 겹쳐 새로 짓는 이름도 뺀 이름에서(겹치지 않으면 이름 그대로 옮긴다).
        k.dir("dst")
        k.file("dst/r\u200B.txt", "D")
        k.file("src/r\u200B.txt", "S")
        k.file("src/plain\u200B.txt", "P")
        val copied = k.post("/api/copy", """{"base":"src","names":[${q("r\u200B.txt")},${q("plain\u200B.txt")}],"to":"dst","conflict":"rename"}""")
        assertEquals(200, copied.status, copied.text)
        assertEquals(setOf("r\u200B.txt", "r.txt", "plain\u200B.txt"), k.names("dst"))
    }

    @Test
    fun 받는_동안_같은_이름이_생기면_덮지_않고_409() {
        val k = kit(manage = false)
        java.net.Socket(LOOPBACK, k.port).use { sock ->
            sock.soTimeout = 5000
            sock.getOutputStream().write("PUT /api/file?path=race.txt HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 6\r\n\r\nmi".toByteArray())
            sock.getOutputStream().flush()
            // 서버가 받기 시작한 뒤(임시가 생긴 뒤) 다른 손님이 같은 이름을 만든다.
            waitUntil { k.names().any { it.endsWith(".part") } }
            k.file("race.txt", "theirs")
            sock.getOutputStream().write("ne!!".toByteArray())
            sock.shutdownOutput()
            val reply = parseReply(readAll(sock.getInputStream()))
            assertEquals(409, reply.status, reply.text)
            assertEquals(true, reply.json()["exists"])
        }
        assertEquals("theirs", File(k.root, "race.txt").readText())
        assertEquals(setOf("race.txt"), k.names(), "받은 임시는 버린다")
    }

    // ── 휴지통의 .nomedia·보관 기간 ─────────────────────────────────────────────────────────────────────────

    @Test
    fun 휴지통을_만들면_nomedia_를_두고_목록과_되살리기와_비우기에_나오지_않는다() {
        val k = kit()
        k.file("p.jpg", "P")
        k.file("q.jpg", "Q")
        assertEquals(200, k.post("/api/delete", """{"base":"","names":["p.jpg","q.jpg"]}""").status)
        assertTrue(File(k.root, "$trash/${SharedOps.NO_MEDIA}").isFile)
        val items = k.get("/api/trash").json()["items"] as List<*>
        assertEquals(setOf("p.jpg", "q.jpg"), items.map { (it as Map<*, *>)["name"] }.toSet(), ".nomedia 는 항목이 아니다")
        val ids = items.map { (it as Map<*, *>)["id"] as String }
        assertEquals(400, k.post("/api/trash/restore", """{"ids":[${q(SharedOps.NO_MEDIA)}]}""").status)
        assertEquals(400, k.post("/api/trash/purge", """{"ids":[${q(SharedOps.NO_MEDIA)}]}""").status)
        assertEquals(200, k.post("/api/trash/restore", """{"ids":[${q(ids[0])}]}""").status)
        assertEquals(1L, k.post("/api/trash/purge", """{"ids":[${q(ids[1])}]}""").json()["count"])
        assertEquals(setOf(SharedOps.NO_MEDIA), k.names(trash), "되살리기·하나씩 지우기도 .nomedia 를 건드리지 않는다")
        // 휴지통을 zip 으로 받을 길이 없다(맨 위의 휴지통은 담지 않는다).
        assertEquals(400, k.post("/api/zip", """{"base":"","names":[${q(trash)}]}""").status)
    }

    @Test
    fun 이미_있는_휴지통에도_nomedia_를_더한다() {
        // 옛 판이 만든 휴지통 — .nomedia 가 없다. 보관 기간 '비우지 않음' 이어도 손볼 때 더한다(오래된 칸은 그대로).
        val k = kit(trashDays = 0)
        k.file("$trash/t1-0000abcd/old.txt", "O")
        k.file("$trash/t1-0000abcd.json", """{"v":1,"name":"old.txt","from":"","deleted":1,"dir":false,"size":1}""")
        assertEquals(0, k.server.sweepTrash())
        assertTrue(File(k.root, "$trash/${SharedOps.NO_MEDIA}").isFile)
        assertEquals("O", File(k.root, "$trash/t1-0000abcd/old.txt").readText())
        // 지우기(휴지통으로)도 없으면 더한다.
        File(k.root, "$trash/${SharedOps.NO_MEDIA}").delete()
        k.file("x.txt")
        assertEquals(200, k.post("/api/delete", """{"base":"","names":["x.txt"]}""").status)
        assertTrue(File(k.root, "$trash/${SharedOps.NO_MEDIA}").isFile)
    }

    private val day = 24L * 60 * 60 * 1000

    /** 휴지통 칸 하나를 손으로 — 칸 이름의 시각 [idMs], 기록의 몸 [record](null 이면 기록 없음). */
    private fun FileServerKit.slot(idMs: Long, hex: String, record: String?) {
        file("$trash/t$idMs-$hex/item-$hex.txt", hex)
        if (record != null) file("$trash/t$idMs-$hex.json", record)
    }

    private fun FileServerKit.trashIds(): Set<String> =
        (get("/api/trash").json()["items"] as List<*>).map { (it as Map<*, *>)["id"] as String }.toSet()

    @Test
    fun 보관_기간이_지난_것만_저절로_비운다() {
        val clock = java.util.concurrent.atomic.AtomicLong(1_000_000_000_000)
        val k = kit(manage = false, trashDays = 30, wallClockMs = { clock.get() })
        k.manage.set(true)
        k.file("old.txt", "O")
        k.post("/api/delete", """{"base":"","names":["old.txt"]}""")
        clock.addAndGet(10 * day)
        k.file("new.txt", "N")
        k.post("/api/delete", """{"base":"","names":["new.txt"]}""")
        val trashed = k.get("/api/trash").json()
        assertEquals(30L, trashed["retentionDays"], "화면이 보관 기간을 한 줄로 보인다")
        assertEquals(2, (trashed["items"] as List<*>).size)

        clock.addAndGet(20 * day) // old 는 30일, new 는 20일
        assertEquals(0, k.server.sweepTrash(), "딱 30일은 아직 지나지 않았다")
        clock.addAndGet(1)
        // 관리 허용과 상관없이 돈다 — 보관 기간은 폰의 설정이다.
        k.manage.set(false)
        assertEquals(1, k.server.sweepTrash())
        k.manage.set(true)
        val left = k.get("/api/trash").json()["items"] as List<*>
        assertEquals(listOf("new.txt"), left.map { (it as Map<*, *>)["name"] })
        assertEquals(3, k.names(trash).size, "남은 칸·그 기록·.nomedia")
    }

    @Test
    fun 기록이_없거나_깨졌거나_지운_때가_없으면_비우지_않는다() {
        val now = 2_000_000_000_000
        val k = kit(trashDays = 7, wallClockMs = { now })
        val old = now - 100 * day
        k.slot(old, "0000000a", null) // 기록 없음
        k.slot(old, "0000000b", "{ 깨짐") // 읽지 못함
        k.slot(old, "0000000c", """{"v":1,"name":"x"}""") // 지운 때 없음
        k.slot(old, "0000000d", """{"v":1,"deleted":"어제"}""") // 수가 아님
        k.slot(old, "0000000e", """{"v":1,"deleted":${now - day}}""") // 칸 이름은 오래됐지만 기록은 하루 전
        k.slot(now - day, "0000000f", """{"v":1,"deleted":$old}""") // 기록은 오래됐지만 칸 이름은 하루 전
        k.slot(old, "00000010", """{"v":1,"deleted":$old}""") // 둘 다 지났다 — 이것만 지운다
        assertEquals(1, k.server.sweepTrash())
        val ids = k.trashIds()
        assertEquals(6, ids.size)
        for (hex in listOf("0000000a", "0000000b", "0000000c", "0000000d", "0000000e", "0000000f")) assertTrue(ids.any { it.endsWith(hex) }, hex)
        assertFalse(File(k.root, "$trash/t$old-00000010").exists())
        assertFalse(File(k.root, "$trash/t$old-00000010.json").exists())
        assertTrue(File(k.root, "$trash/t$old-0000000a/item-0000000a.txt").isFile)
    }

    @Test
    fun 보관_기간_비우지_않음이면_아무것도_지우지_않는다() {
        val now = 2_000_000_000_000
        val k = kit(trashDays = 0, wallClockMs = { now })
        k.slot(now - 1000 * day, "000000aa", """{"v":1,"deleted":${now - 1000 * day}}""")
        assertEquals(0, k.server.sweepTrash())
        assertEquals(1, k.trashIds().size)
        assertEquals(0L, k.get("/api/trash").json()["retentionDays"])
    }

    @Test
    fun 칸을_지우지_못하면_기록을_남겨_다음에_다시_한다() {
        val now = 2_000_000_000_000
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val stuck = java.util.concurrent.atomic.AtomicBoolean(true)
        val flaky = object : SharedTree by base {
            override fun delete(entry: SharedEntry): Boolean = if (stuck.get() && File(entry.ref).isDirectory) false else base.delete(entry)
        }
        val logs = LogLines()
        val k = FileServerKit(root, true, base, sharedTree = flaky, env = testEnv(log = logs), trashDays = { 7 }, wallClockMs = { now })
            .also { kits += it }
        val old = now - 30 * day
        k.slot(old, "000000bb", """{"v":1,"deleted":$old}""")
        assertEquals(0, k.server.sweepTrash())
        assertTrue(File(root, "$trash/t$old-000000bb.json").isFile, "기록을 남긴다")
        assertTrue(logs.lines.any { it.contains("못 지운 것 1개") }, "실패는 기록만")
        stuck.set(false)
        assertEquals(1, k.server.sweepTrash())
        assertEquals(setOf(SharedOps.NO_MEDIA), k.names(trash))
    }

    @Test
    fun 서버가_서면_휴지통을_손보고_설정을_바꾸면_곧바로_한_번_더() {
        val now = 2_000_000_000_000L
        val days = java.util.concurrent.atomic.AtomicInteger(30)
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val old = now - 40 * day
        File(root, "$trash/t$old-000000cc").mkdirs()
        File(root, "$trash/t$old-000000cc/a.txt").writeText("a")
        File(root, "$trash/t$old-000000cc.json").writeText("""{"v":1,"deleted":$old}""")
        val k = FileServerKit(root, false, trashDays = { days.get() }, trashSweepFirstMs = 50, wallClockMs = { now }).also { kits += it }
        waitUntil { !File(root, "$trash/t$old-000000cc").exists() }
        assertTrue(File(root, "$trash/${SharedOps.NO_MEDIA}").isFile)

        // 다음 차례는 하루 뒤다 — 설정을 바꾸면 앱이 requestTrashSweep 으로 곧바로 부른다.
        val recent = now - 10 * day
        File(root, "$trash/t$recent-000000dd").mkdirs()
        File(root, "$trash/t$recent-000000dd/b.txt").writeText("b")
        File(root, "$trash/t$recent-000000dd.json").writeText("""{"v":1,"deleted":$recent}""")
        days.set(7)
        k.server.requestTrashSweep()
        waitUntil { !File(root, "$trash/t$recent-000000dd").exists() }
    }

    @Test
    fun 몸을_흘리는_손님이_다른_관리_동작을_막지_않는다() {
        val k = kit()
        k.file("a.txt")
        // 머리만 보내고 몸을 흘린다 — 서버는 몸을 읽는 동안 관리 잠금을 쥐지 않아야 한다(쥐면 아래 요청이 30초 기다린다).
        java.net.Socket(LOOPBACK, k.port).use { slow ->
            val head = "POST /api/rename HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{\"pa"
            slow.getOutputStream().write(head.toByteArray())
            slow.getOutputStream().flush()
            Thread.sleep(300) // 서버가 머리를 다 읽고 몸 읽기에 들어가게
            val started = System.nanoTime()
            assertEquals(200, k.put("/api/folder?path=new").status)
            assertEquals(200, k.put("/api/file?path=p/q.txt&parents=1", "q".toByteArray()).status)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "잠금을 기다리지 않는다")
        }
        assertEquals(setOf("a.txt", "new", "p"), k.names())
    }

    @Test
    fun 끝에_점이나_공백을_붙인_휴지통_이름도_휴지통이다() {
        val k = kit(manage = false)
        k.dir("d")
        // 스위치가 꺼져 있어도 되는 올리기로 휴지통 자리에 파일을 앉히지 못한다(FAT·exFAT·윈도는 끝의 점을 떼고 만든다).
        for (variant in listOf("$trash.", "$trash ", "${trash.uppercase()}. .")) {
            assertEquals(400, k.put("/api/file?path=${enc(variant)}", "x".toByteArray()).status, variant)
            assertEquals(404, k.get("/api/list?path=${enc(variant)}").status, variant)
        }
        k.manage.set(true)
        for (variant in listOf("$trash.", "$trash ", "${trash.uppercase()}. .")) {
            assertEquals(404, k.put("/api/folder?path=${enc(variant)}").status, variant)
            assertEquals(400, k.post("/api/rename", """{"path":"d","name":${q(variant)}}""").status, variant)
            assertEquals(400, k.post("/api/delete", """{"base":"","names":[${q(variant)}]}""").status, variant)
        }
        assertEquals(setOf("d"), k.names())
        // 휴지통은 그대로 쓸 수 있다.
        k.file("x.txt")
        assertEquals(200, k.post("/api/delete", """{"base":"","names":["x.txt"]}""").status)
        assertEquals(1, (k.get("/api/trash").json()["items"] as List<*>).size)
    }

    @Test
    fun 하위_폴더의_휴지통_이름을_맨_위로_옮기거나_복사해_휴지통_자리에_앉히지_못한다() {
        // 하위 폴더의 .web-trash 는 보통 폴더다(손님이 올리기로 채울 수 있다) — 휴지통이 아직 없을 때 맨 위로 옮기면 겹침이 없어 그대로 휴지통이 됐다.
        val k = kit()
        k.file("sub/$trash/t1-00000000/fake.txt", "F")
        k.file("sub/$trash/t1-00000000.json", """{"v":1,"name":"fake.txt","from":"","deleted":1}""")
        for (path in listOf("/api/move", "/api/copy")) {
            for (conflict in listOf("ask", "rename")) {
                val reply = k.post(path, """{"base":"sub","names":[${q(trash)}],"to":"","conflict":"$conflict"}""")
                assertEquals(200, reply.status, reply.text)
                assertEquals(1, (reply.json()["failed"] as List<*>).size, "$path $conflict")
            }
        }
        assertEquals(setOf("sub"), k.names(), "맨 위에 휴지통이 생기지 않는다")
        assertEquals("F", File(k.root, "sub/$trash/t1-00000000/fake.txt").readText())
        // 하위 폴더 사이로는 그대로 옮긴다(맨 위의 것만 휴지통이다).
        k.dir("other")
        assertEquals(200, k.post("/api/move", """{"base":"sub","names":[${q(trash)}],"to":"other"}""").status)
        assertTrue(File(k.root, "other/$trash/t1-00000000/fake.txt").isFile)
    }

    @Test
    fun 손으로_옮겨_휴지통에_넣다가_원래_것을_다_지우지_못하면_사본을_남긴다() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root, nativeMove = false)
        // 원래 폴더를 지우다 중간에 멈추는 제공자 — 파일 하나만 지우고 실패를 알린다.
        val flaky = object : SharedTree by base {
            override fun delete(entry: SharedEntry): Boolean {
                val file = File(entry.ref)
                if (file.name == "keep" && file.parentFile == root) {
                    File(file, "one.txt").delete()
                    return false
                }
                return base.delete(entry)
            }
        }
        val k = FileServerKit(root, true, base, sharedTree = flaky).also { kits += it }
        k.file("keep/one.txt", "1")
        k.file("keep/two.txt", "2")
        val reply = k.post("/api/delete", """{"base":"","names":["keep"]}""")
        assertEquals(200, reply.status, reply.text)
        assertEquals(1, (reply.json()["failed"] as List<*>).size)
        // 온전한 것은 휴지통의 사본뿐이다 — 칸이 남아 되살릴 수 있다.
        val items = k.get("/api/trash").json()["items"] as List<*>
        assertEquals(1, items.size)
        val id = (items[0] as Map<*, *>)["id"] as String
        assertEquals(200, k.post("/api/trash/restore", """{"ids":[${q(id)}]}""").status)
        assertEquals("1", File(root, "keep (1)/one.txt").readText())
        assertEquals("2", File(root, "keep (1)/two.txt").readText())
    }

    @Test
    fun 옮기기가_된_뒤에_실패를_알려도_휴지통_칸을_지우지_않는다() {
        // SAF 의 moveDocument 는 됐는데 옮긴 것을 다시 읽지 못한 꼴 — 옮긴 뒤에 던지는 제공자. 칸을 치우면 하나뿐인 원래 것이 사라졌다
        // (덮어쓰기는 '원래 파일은 그대로' 라 답했다 — 11-21 3차 검토).
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val flaky = object : SharedTree by base {
            override fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry? {
                base.move(entry, fromParent, toParent)
                throw java.io.IOException("옮긴 것을 읽지 못했다")
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = flaky).also { kits += it }
        // 덮어쓰기 — 원래 것은 휴지통에, 새 것은 제자리에.
        k.file("doc.txt", "original")
        val reply = k.put("/api/file?path=doc.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new", File(root, "doc.txt").readText())
        // 관리의 지우기도 같은 길 — 옮겨졌으면 된 것으로.
        k.file("gone.txt", "G")
        k.manage.set(true)
        val deleted = k.post("/api/delete", """{"base":"","names":["gone.txt"]}""")
        assertEquals(200, deleted.status, deleted.text)
        assertEquals(true, deleted.json()["ok"], deleted.text)
        val items = (k.get("/api/trash").json()["items"] as List<*>).map { it as Map<*, *> }
        assertEquals(setOf("doc.txt", "gone.txt"), items.map { it["name"] }.toSet())
        for (item in items) {
            val slot = File(root, "$trash/${item["id"]}/${item["name"]}")
            assertEquals(if (item["name"] == "doc.txt") "original" else "G", slot.readText())
        }
    }

    @Test
    fun 덮어쓰기의_두기가_실패하면_원래_파일을_되살리고_어디_있는지_알린다() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        var mode = "fail"
        val flaky = object : SharedTree by base {
            override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? = when (mode) {
                // 이름 바꾸기가 실패한 꼴 — 받은 것은 임시에 남고 원래 것은 제자리로.
                "fail" -> null
                // 원래 것을 휴지통으로 옮긴 틈에 다른 손님이 같은 이름을 둔 꼴.
                else -> {
                    File(root, path.joinToString("/")).writeText("theirs")
                    base.commitUpload(temp, path)
                }
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = flaky).also { kits += it }
        k.file("a.txt", "original")
        val failed = k.put("/api/file?path=a.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, failed.status, failed.text)
        assertTrue(failed.text.contains("원래 파일은 그대로입니다"), failed.text)
        assertEquals("original", File(root, "a.txt").readText())
        mode = "race"
        k.file("b.txt", "original")
        val raced = k.put("/api/file?path=b.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, raced.status, raced.text)
        assertTrue(raced.text.contains("‘b (1).txt’ 로 되살렸습니다"), raced.text)
        assertEquals("theirs", File(root, "b.txt").readText())
        assertEquals("original", File(root, "b (1).txt").readText())
    }

    // ── 덮어쓰기의 빈 원래 파일(사용자 결정 11-21 — 0바이트면 휴지통에 넣지 않는다) ───────────────────────────────────────────

    /** 비켜 둔 빈 원래 파일이 남았는가(숨은 `.이름.replacing-…`). */
    private fun asides(dir: File): List<String> = dir.list().orEmpty().filter { it.contains(SharedOps.ASIDE_INFIX) }

    @Test
    fun 빈_원래_파일은_휴지통에_넣지_않고_바꾼다() {
        val k = kit(manage = false)
        k.file("sub/empty.txt", "")
        val reply = k.put("/api/file?path=sub/empty.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(false, reply.json()["trashed"])
        assertEquals("new", File(k.root, "sub/empty.txt").readText())
        assertFalse(File(k.root, trash).exists(), "휴지통을 만들지도 않는다")
        assertEquals(setOf("empty.txt"), k.names("sub"), "비켜 둔 것도 임시도 남지 않는다")
        // 대소문자만 다른 빈 원래 것도 — 올린 이름으로 하나만 남는다.
        k.file("CASE.txt", "")
        assertEquals(false, k.put("/api/file?path=case.txt&overwrite=1", "n".toByteArray()).json()["trashed"])
        assertEquals("n", File(k.root, k.names().single { it.equals("case.txt", ignoreCase = true) }).readText())
        assertFalse(File(k.root, trash).exists())
        // 경계 — 1바이트는 휴지통으로. 빈 것을 빈 것으로 덮어도 휴지통 없이.
        k.file("one.txt", "1")
        assertEquals(true, k.put("/api/file?path=one.txt&overwrite=1", "2".toByteArray()).json()["trashed"])
        k.file("zero.txt", "")
        assertEquals(false, k.put("/api/file?path=zero.txt&overwrite=1", ByteArray(0)).json()["trashed"])
        assertEquals(0L, File(k.root, "zero.txt").length())
        assertEquals(emptyList(), asides(k.root) + asides(File(k.root, "sub")))
        // 관리의 지우기는 그대로 — 빈 파일도 휴지통으로.
        k.manage.set(true)
        assertEquals(200, k.post("/api/delete", """{"base":"","names":["zero.txt"]}""").status)
        assertEquals(setOf("one.txt", "zero.txt"), (k.get("/api/trash").json()["items"] as List<*>).map { (it as Map<*, *>)["name"] }.toSet())
    }

    @Test
    fun 크기를_0_으로_적는_제공자의_파일은_열어_보고_휴지통으로() {
        // 앱의 SAF 는 COLUMN_SIZE 가 null 이면 0 으로 적는다 — 크기만 보고 지우면 내용이 있는 파일을 잃는다.
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        fun unknown(e: SharedEntry) = SharedEntry(e.ref, e.name, e.isDirectory, 0, e.modified)
        val sizeless = object : SharedTree by base {
            override fun find(path: List<String>): SharedEntry? = base.find(path)?.let(::unknown)
            override fun list(directory: SharedEntry): List<SharedEntry> = base.list(directory).map(::unknown)
        }
        val k = FileServerKit(root, false, base, sharedTree = sizeless).also { kits += it }
        k.file("data.txt", "important")
        val reply = k.put("/api/file?path=data.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new", File(root, "data.txt").readText())
        val slot = File(root, trash).listFiles().orEmpty().single { it.isDirectory }
        assertEquals("important", File(slot, "data.txt").readText())
        // 정말 빈 파일은 같은 제공자에서도 휴지통 없이.
        k.file("blank.txt", "")
        assertEquals(false, k.put("/api/file?path=blank.txt&overwrite=1", "b".toByteArray()).json()["trashed"])
    }

    @Test
    fun 판정_뒤에_원래_파일이_커지면_원래_이름으로_휴지통에_넣는다() {
        // 비켜 둔 뒤 두는 사이에 이 폰의 다른 앱이 원래 파일에 쓴 꼴 — 지우기 직전에 다시 보고 휴지통으로(안전한 쪽).
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val grows = object : SharedTree by base {
            override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? {
                File(root, asides(root).single()).writeText("late")
                return base.commitUpload(temp, path)
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = grows).also { kits += it }
        k.file("grown.txt", "")
        val reply = k.put("/api/file?path=grown.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new", File(root, "grown.txt").readText())
        assertEquals(emptyList(), asides(root))
        // 휴지통에는 원래 이름으로 — 되살리면 `grown (1).txt` 로 그 내용이.
        k.manage.set(true)
        val item = (k.get("/api/trash").json()["items"] as List<*>).single() as Map<*, *>
        assertEquals("grown.txt", item["name"])
        assertEquals(200, k.post("/api/trash/restore", """{"ids":[${q(item["id"] as String)}]}""").status)
        assertEquals("late", File(root, "grown (1).txt").readText())
    }

    @Test
    fun 빈_원래_파일을_바꾸다_두기가_실패하면_원래_것을_제자리로_돌린다() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        var mode = "fail"
        val flaky = object : SharedTree by base {
            override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? = when (mode) {
                "fail" -> null
                // 비켜 둔 틈에 다른 손님(관리 잠금을 쥐지 않는 새 올리기)이 같은 이름을 둔 꼴.
                else -> {
                    File(root, path.joinToString("/")).writeText("theirs")
                    base.commitUpload(temp, path)
                }
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = flaky).also { kits += it }
        k.file("a.txt", "")
        val failed = k.put("/api/file?path=a.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, failed.status, failed.text)
        assertTrue(failed.text.contains("원래 파일은 그대로입니다"), failed.text)
        assertTrue(File(root, "a.txt").isFile)
        assertEquals(0L, File(root, "a.txt").length())
        mode = "race"
        k.file("b.txt", "")
        val raced = k.put("/api/file?path=b.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, raced.status, raced.text)
        assertTrue(raced.text.contains("‘b (1).txt’ 로 되살렸습니다"), raced.text)
        assertEquals("theirs", File(root, "b.txt").readText())
        assertTrue(File(root, "b (1).txt").isFile)
        assertEquals(emptyList(), asides(root))
        assertFalse(File(root, trash).exists(), "빈 원래 것은 어느 길에서도 휴지통에 가지 않는다")
    }

    @Test
    fun 빈_원래_파일을_비켜_두지_못하면_휴지통_길로_간다() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val noRename = object : SharedTree by base {
            override fun rename(entry: SharedEntry, newName: String): SharedEntry = throw java.io.IOException("이름을 바꾸지 못했다")
        }
        val k = FileServerKit(root, false, base, sharedTree = noRename).also { kits += it }
        k.file("e.txt", "")
        val reply = k.put("/api/file?path=e.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new", File(root, "e.txt").readText())
        val slot = File(root, trash).listFiles().orEmpty().single { it.isDirectory }
        assertEquals(0L, File(slot, "e.txt").length())
    }

    @Test
    fun 빈지_열어_보지_못하면_휴지통으로() {
        // 크기 0 이어도 열지 못하거나 읽다 실패하면 비었다고 치지 않는다 — 휴지통 길(2026-11-21 4차 검토에서 시험을 더함).
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val unreadable = object : SharedTree by base {
            override fun openRead(file: SharedEntry): java.io.InputStream? = when (file.name) {
                "closed.txt" -> null
                "broken.txt" -> object : java.io.InputStream() {
                    override fun read(): Int = throw java.io.IOException("읽다 실패")
                }
                else -> base.openRead(file)
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = unreadable).also { kits += it }
        for (name in listOf("closed.txt", "broken.txt")) {
            k.file(name, "")
            val reply = k.put("/api/file?path=$name&overwrite=1", "new".toByteArray())
            assertEquals(200, reply.status, reply.text)
            assertEquals(true, reply.json()["trashed"], name)
            assertEquals("new", File(root, name).readText())
        }
        val kept = File(root, trash).listFiles().orEmpty().filter { it.isDirectory }.flatMap { it.list().orEmpty().toList() }
        assertEquals(setOf("closed.txt", "broken.txt"), kept.toSet())
        assertEquals(emptyList(), asides(root))
    }

    @Test
    fun 비켜_둔_빈_파일을_지우지_못하면_원래_이름으로_휴지통에() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val undeletable = object : SharedTree by base {
            override fun delete(entry: SharedEntry): Boolean = if (entry.name.contains(SharedOps.ASIDE_INFIX)) false else base.delete(entry)
        }
        val k = FileServerKit(root, false, base, sharedTree = undeletable).also { kits += it }
        k.file("stuck.txt", "")
        val reply = k.put("/api/file?path=stuck.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(true, reply.json()["trashed"])
        assertEquals("new", File(root, "stuck.txt").readText())
        assertEquals(emptyList(), asides(root), "비켜 둔 이름으로 남지 않는다")
        val slot = File(root, trash).listFiles().orEmpty().single { it.isDirectory }
        assertEquals(listOf("stuck.txt"), slot.list().orEmpty().toList())
    }

    @Test
    fun 커진_원래_파일을_휴지통에_넣지_못하면_숨은_이름으로_남긴다() {
        // 지우기 직전에 다시 보니 커졌는데 휴지통 자리에 파일이 있다 — 지우지 않는다(내용을 잃지 않는 쪽). 새 것은 이미 제자리에 있다.
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val grows = object : SharedTree by base {
            override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? {
                File(root, asides(root).single()).writeText("late")
                return base.commitUpload(temp, path)
            }
        }
        val k = FileServerKit(root, false, base, sharedTree = grows).also { kits += it }
        File(root, trash).writeText("not a folder")
        k.file("grown.txt", "")
        val reply = k.put("/api/file?path=grown.txt&overwrite=1", "new".toByteArray())
        assertEquals(200, reply.status, reply.text)
        assertEquals(false, reply.json()["trashed"])
        assertEquals("new", File(root, "grown.txt").readText())
        assertEquals(listOf("late"), asides(root).map { File(root, it).readText() })
    }

    @Test
    fun JSON_몸의_꼴과_상한() {
        val k = kit()
        k.file("a.txt")
        assertEquals(400, k.post("/api/delete", """{"base":"","names":["a.txt"]""").status)
        assertEquals(400, k.post("/api/delete", """["a.txt"]""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":"a.txt"}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":[1]}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":["a.txt","a.txt"]}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":[]}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"../x","names":["a.txt"]}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":["../a.txt"]}""").status)
        assertEquals(400, k.post("/api/delete", """{"base":"","names":[".."]}""").status)
        // 상한을 넘는 길이는 몸을 읽기 전에 거절한다(몸을 보내지 않고 길이만 선언한다 — 보내면 서버가 닫은 뒤의 쓰기가 끊긴다).
        val big = "POST /api/delete HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Type: application/json\r\nContent-Length: ${FileServer.MAX_JSON_BYTES + 1}\r\n\r\n"
        assertEquals(413, rawHttp(k.port, big.toByteArray()).status)
        val many = "{\"base\":\"\",\"names\":[" + (1..SharedOps.MAX_BATCH + 1).joinToString(",") { "\"n$it\"" } + "]}"
        assertEquals(413, k.post("/api/delete", many).status)
        val te = "POST /api/delete HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"
        assertEquals(501, rawHttp(k.port, te.toByteArray()).status)
        val noLength = "POST /api/delete HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Type: application/json\r\n\r\n"
        assertEquals(411, rawHttp(k.port, noLength.toByteArray()).status)
        val twoTypes = "POST /api/delete HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Type: text/plain\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}"
        assertEquals(400, rawHttp(k.port, twoTypes.toByteArray()).status)
        assertEquals(setOf("a.txt"), k.names())
    }

    @Test
    fun 휴지통으로_옮겼는지_모르면_원래_파일이_그대로라고_하지_않는다() {
        // 옮기기는 됐는데 실패를 알리고 칸도 읽지 못하는 제공자 — 원래 것은 제자리에 없다. '그대로' 라 답하면 손님은 원래 것이 거기 있는 줄 안다.
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val slotName = Regex("t[0-9]+-[0-9a-f]{8}")
        val flaky = object : SharedTree by base {
            override fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry? {
                base.move(entry, fromParent, toParent)
                throw java.io.IOException("옮긴 것을 읽지 못했다")
            }

            override fun list(directory: SharedEntry): List<SharedEntry> =
                if (slotName.matches(directory.name)) throw java.io.IOException("칸을 읽지 못했다") else base.list(directory)
        }
        val k = FileServerKit(root, false, base, sharedTree = flaky).also { kits += it }
        k.file("doc.txt", "original")
        val reply = k.put("/api/file?path=doc.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, reply.status, reply.text)
        assertFalse(reply.text.contains("그대로"), reply.text)
        assertTrue(reply.text.contains("제자리에 없습니다") && reply.text.contains("휴지통을 확인하세요"), reply.text)
        assertFalse(File(root, "doc.txt").exists())
        val slot = File(root, trash).listFiles().orEmpty().single { it.isDirectory }
        assertEquals("original", File(slot, "doc.txt").readText(), "원래 것은 휴지통 칸에 남는다")
    }

    @Test
    fun 휴지통은_정확히_그_이름으로_만들어져야_쓴다() {
        // 제공자가 겹침·못 쓰는 글자로 이름을 바꿔 만들면 숨지 않은 다른 폴더에 지운 것이 쌓인다 — 쓰지 않고 치운다.
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        val renaming = object : SharedTree by base {
            override fun createDirectory(parent: SharedEntry, name: String): SharedEntry =
                base.createDirectory(parent, if (name == SharedPaths.TRASH_NAME) "$name (1)" else name)
        }
        val k = FileServerKit(root, true, base, sharedTree = renaming).also { kits += it }
        k.file("gone.txt", "G")
        val deleted = k.post("/api/delete", """{"base":"","names":["gone.txt"]}""")
        assertTrue(deleted.text.contains(SharedOps.TRASH_NOT_CREATED), deleted.text)
        k.file("keep.txt", "original")
        val replaced = k.put("/api/file?path=keep.txt&overwrite=1", "new".toByteArray())
        assertEquals(500, replaced.status, replaced.text)
        assertTrue(replaced.text.contains("원래 파일은 그대로입니다"), replaced.text)
        assertEquals("original", File(root, "keep.txt").readText())
        assertEquals(setOf("gone.txt", "keep.txt"), k.names(), "잘못 지은 폴더와 받은 임시를 남기지 않는다")
    }

    @Test
    fun 옮긴_뒤_새_이름으로_바꾸지_못하면_숨은_임시_이름으로_남지_않는다() {
        val root = java.nio.file.Files.createTempDirectory("tether-manage").toFile()
        val base = DirectoryTree(root)
        var blockReturn = false
        val flaky = object : SharedTree by base {
            override fun rename(entry: SharedEntry, newName: String): SharedEntry =
                if (newName == "x (1).txt") throw java.io.IOException("이름을 바꾸지 못했다") else base.rename(entry, newName)

            override fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry? =
                if (blockReturn && toParent.name == "src") throw java.io.IOException("되돌리지 못했다") else base.move(entry, fromParent, toParent)
        }
        val k = FileServerKit(root, true, base, sharedTree = flaky).also { kits += it }
        k.file("src/x.txt", "new")
        k.file("dst/x.txt", "old")
        val back = k.post("/api/move", """{"base":"src","names":["x.txt"],"to":"dst","conflict":"rename"}""")
        assertEquals(200, back.status, back.text)
        assertTrue(back.text.contains("되돌렸습니다"), back.text)
        assertEquals("new", File(root, "src/x.txt").readText(), "제자리에 원래 이름으로")
        assertEquals(setOf("x.txt"), k.names("src"))
        assertEquals(setOf("x.txt"), k.names("dst"))
        // 되돌리지도 못하면(옮긴 곳의 원래 이름은 겹친다) 남은 실제 이름을 알린다.
        blockReturn = true
        val stuck = k.post("/api/move", """{"base":"src","names":["x.txt"],"to":"dst","conflict":"rename"}""")
        val left = k.names("dst").single { it != "x.txt" }
        assertTrue(stuck.text.contains("옮긴 곳에 ‘$left’ 로 남았습니다"), stuck.text)
    }
}
