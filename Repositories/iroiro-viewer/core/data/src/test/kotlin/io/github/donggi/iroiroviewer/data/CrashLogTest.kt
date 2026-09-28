package io.github.donggi.iroiroviewer.data

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 크래시 기록기. 기록은 공유를 누르면 앱 밖으로 나가므로 **경로가 남지 않는가**가 첫째이고, 저장소를 채우지 않는가
 * (건수·크기)와 앞의 처리기를 잇는가(끊으면 앱이 죽지 않고 얼어붙는다)가 그다음이다.
 */
class CrashLogTest {

    private val tmp = File(System.getProperty("java.io.tmpdir"), "iroiro-crash-${System.nanoTime()}").apply { mkdirs() }
    private val meta = CrashReport.Meta("1.0.0", 2, 31)

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    // ---- 경로 지우기 ----------------------------------------------------------------------------

    @Test
    fun `예외 메시지의 저장소 경로를 지운다 — 공백이 든 이름까지`() {
        val line = "java.io.FileNotFoundException: /storage/emulated/0/Download/비밀 문서 (2).pdf: open failed: ENOENT"
        assertEquals("java.io.FileNotFoundException: <경로>: open failed: ENOENT", CrashReport.redact(line))
    }

    @Test
    fun `sdcard·data·SD 카드 볼륨·file 주소·content 주소를 모두 지운다`() {
        assertEquals("x <경로>", CrashReport.redact("x /sdcard/Comics/a.cbz"))
        assertEquals("<경로>", CrashReport.redact("/data/user/0/io.github.donggi.iroiroviewer/files/crash"))
        assertEquals("열기 실패 <경로>", CrashReport.redact("열기 실패 /storage/0000-0000/사진/여름.jpg"))
        assertEquals("uri=<경로>", CrashReport.redact("uri=file:///storage/emulated/0/a b.jpg"))
        assertEquals(
            "<경로>: 거부",
            CrashReport.redact("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fx.pdf: 거부"),
        )
        // 따옴표 안의 경로는 따옴표에서 끝난다 — 뒤의 글이 남는다.
        assertEquals("'<경로>' is locked", CrashReport.redact("'/storage/emulated/0/a.7z' is locked"))
    }

    /**
     * 이름 안의 따옴표(아포스트로피)에서 멈추면 **그 뒤의 이름 조각이 기록에 남는다**(14단계 검토). 영어 음악 파일 이름에
     * 흔하다. `Uri.encode` 는 `'` 를 바꾸지 않아 문서 제공자 주소에도 날것으로 온다.
     */
    @Test
    fun `이름 안의 따옴표에서 멈추지 않는다`() {
        assertEquals(
            "java.io.FileNotFoundException: <경로>: open failed: ENOENT",
            CrashReport.redact("java.io.FileNotFoundException: /storage/emulated/0/Music/Guns N' Roses - Don't Cry.mp3: open failed: ENOENT"),
        )
        assertEquals("열기 실패 <경로>", CrashReport.redact("열기 실패 content://x.documents/document/primary%3AMusic%2FDon't.mp3"))
        // 따옴표로 싼 경로 안의 아포스트로피 — 여는 따옴표와 같은 마지막 따옴표까지 지우고 뒤의 까닭은 남긴다.
        assertEquals("Cannot open '<경로>' for writing", CrashReport.redact("Cannot open '/data/user/0/p/Tom's.db' for writing"))
        assertEquals("\"<경로>\" missing", CrashReport.redact("\"/storage/emulated/0/Tom's \"best\".jpg\" missing"))
        // 닫는 따옴표가 없으면 줄 끝까지.
        assertEquals("'<경로>", CrashReport.redact("'/storage/emulated/0/Tom's.jpg"))
        for (s in listOf(
            "x /storage/emulated/0/Don't.mp3",
            "'/storage/emulated/0/it's.jpg' is locked",
            "uri=file:///storage/emulated/0/I'm Yours.flac",
        )) {
            val out = CrashReport.redact(s)
            assertFalse(out.contains(".mp3") || out.contains(".jpg") || out.contains(".flac") || out.contains("storage"), out)
        }
    }

    @Test
    fun `경로가 아닌 빗금은 그대로 둔다`() {
        val untouched = listOf(
            "Unsupported MIME: image/jpeg",
            "ratio 16/9 and 1/2",
            "\tat io.github.donggi.iroiroviewer.MainActivity.onCreate(MainActivity.kt:62)",
            "Caused by: java.lang.IllegalStateException: Player is accessed on the wrong thread",
            "/sdcard",
        )
        for (s in untouched) assertEquals(s, CrashReport.redact(s))
    }

    @Test
    fun `기록 한 건에는 판·API·시각·스레드·트레이스가 있고 경로는 없다`() {
        val error = IllegalStateException("열 수 없다", FileNotFoundException("/storage/emulated/0/Movies/개인.mkv: open failed"))
        val text = CrashReport.compose(meta, 1_790_000_000_000, "main", error)
        assertTrue(text.contains("version: 1.0.0 (2)"), text)
        assertTrue(text.contains("api: 31"), text)
        assertTrue(text.contains("time: 2026-"), text)
        assertTrue(text.contains("thread: main"), text)
        assertTrue(text.contains("java.lang.IllegalStateException: 열 수 없다"), text)
        assertTrue(text.contains("Caused by: java.io.FileNotFoundException: <경로>: open failed"), text)
        assertFalse(text.contains("/storage"), text)
        assertFalse(text.contains("개인.mkv"), text)
    }

    @Test
    fun `트레이스를 만들다 던지는 예외도 기록이 된다`() {
        val nasty = object : RuntimeException() {
            override fun toString(): String = throw IllegalStateException("toString 이 던진다")
        }
        val text = CrashReport.compose(meta, 0, "t", nasty)
        assertTrue(text.contains("version: 1.0.0"), text)
    }

    // ---- 크기 상한 ------------------------------------------------------------------------------

    @Test
    fun `64KiB 를 넘는 트레이스는 앞과 뒤를 남기고 자른다 — 원인은 끝에 있다`() {
        var e: Throwable = IOException("가장 안쪽 원인")
        repeat(40) { i -> e = RuntimeException("겹 $i 😀".repeat(400), e) }
        val full = e.stackTraceToString()
        assertTrue(CrashReport.utf8Length(full) > 2 * CrashReport.MAX_BYTES, "표본이 상한을 확실히 넘어야 시험이 뜻을 가진다")
        val text = CrashReport.compose(meta, 0, "main", e)
        val bytes = CrashReport.encode(text)
        assertTrue(bytes.size <= CrashReport.MAX_BYTES, "크기 ${bytes.size}")
        assertTrue(text.startsWith("iroiro-viewer crash report"), "머리가 남아야 한다")
        assertTrue(text.contains("겹 39 😀"), "앞(죽은 자리의 예외)이 남아야 한다")
        assertTrue(text.contains("가장 안쪽 원인"), "끝(가장 안쪽 원인)이 남아야 한다")
        assertTrue(text.contains(CrashReport.TRUNCATED), "잘랐다는 표시")
        // 대리 쌍을 가르지 않았다 — 짝 잃은 대리 문자가 하나도 없다.
        text.forEachIndexed { i, c ->
            if (Character.isHighSurrogate(c)) assertTrue(i + 1 < text.length && Character.isLowSurrogate(text[i + 1]), "앞 반쪽 @$i")
            if (Character.isLowSurrogate(c)) assertTrue(i > 0 && Character.isHighSurrogate(text[i - 1]), "뒤 반쪽 @$i")
        }
    }

    @Test
    fun `상한 안의 글은 자르지 않는다`() {
        assertEquals("짧다", CrashReport.limit("짧다", CrashReport.MAX_BYTES))
        assertEquals(6, CrashReport.utf8Length("짧다"))
        assertEquals(4, CrashReport.utf8Length("😀"))
    }

    // ---- 보존·요약·공유·지우기 ----------------------------------------------------------------------

    @Test
    fun `최근 다섯 건만 남긴다`() {
        var now = 1_000L
        val log = CrashLog(tmp) { now }
        repeat(7) {
            now += 1_000
            log.record(meta, Thread.currentThread(), RuntimeException("#$it"))
        }
        val files = log.reports()
        assertEquals(CrashLog.KEEP, files.size)
        assertEquals(CrashLog.Summary(5, 8_000L), log.summary())
        // 남은 것이 새것 다섯이다(#2..#6).
        val newest = files.first().readText()
        assertTrue(newest.contains("#6"), newest)
        assertFalse(files.any { it.readText().contains("RuntimeException: #1") })
        assertTrue(files.all { it.length() <= CrashReport.MAX_BYTES })
    }

    @Test
    fun `같은 밀리초의 두 죽음을 덮어쓰지 않는다`() {
        val log = CrashLog(tmp) { 5_000L }
        log.record(meta, Thread.currentThread(), RuntimeException("a"))
        log.record(meta, Thread.currentThread(), RuntimeException("b"))
        assertEquals(2, log.summary().count)
    }

    @Test
    fun `기록이 없으면 요약은 비어 있고 공유 사본도 없다`() {
        val log = CrashLog(File(tmp, "없는 폴더"))
        assertEquals(CrashLog.Summary.EMPTY, log.summary())
        assertNull(log.exportTo(File(tmp, "share/crash.txt")))
    }

    @Test
    fun `공유 사본은 새것부터 이어 쓰고 원본은 그대로 둔다`() {
        var now = 10_000L
        val log = CrashLog(File(tmp, "crash")) { now }
        log.record(meta, Thread.currentThread(), RuntimeException("옛것"))
        now += 1
        log.record(meta, Thread.currentThread(), RuntimeException("새것"))
        val out = assertNotNull(log.exportTo(File(tmp, "share/crash/iroiro-crash.txt")))
        val text = out.readText()
        assertTrue(text.indexOf("새것") in 0 until text.indexOf("옛것"), text)
        assertTrue(text.contains(CrashReport.separator(1, 2)) && text.contains(CrashReport.separator(2, 2)))
        assertEquals(2, log.summary().count)
        assertFalse(File(out.path + ".tmp").exists())
    }

    @Test
    fun `지우면 기록이 하나도 남지 않는다`() {
        val log = CrashLog(tmp) { 1L }
        log.record(meta, Thread.currentThread(), RuntimeException("x"))
        assertTrue(log.clear())
        assertEquals(CrashLog.Summary.EMPTY, log.summary())
    }

    @Test
    fun `폴더를 만들 수 없어도 던지지 않는다`() {
        val blocker = File(tmp, "blocker").apply { writeText("파일이 폴더 자리를 막고 있다") }
        val log = CrashLog(File(blocker, "crash"))
        log.record(meta, Thread.currentThread(), RuntimeException("x"))
        assertEquals(CrashLog.Summary.EMPTY, log.summary())
        assertNull(log.exportTo(File(tmp, "out.txt")))
    }

    // ---- 처리기 --------------------------------------------------------------------------------

    @Test
    fun `처리기는 기록한 뒤 앞의 처리기에 넘긴다`() {
        val seen = mutableListOf<Throwable>()
        val previous = Thread.UncaughtExceptionHandler { _, e -> seen += e }
        val log = CrashLog(tmp) { 42L }
        val boom = RuntimeException("boom")
        CrashHandler(log, meta, previous) { error("앞의 처리기가 있으면 부르지 않는다") }
            .uncaughtException(Thread.currentThread(), boom)
        assertEquals(1, seen.size)
        assertSame(boom, seen.single())
        assertEquals(1, log.summary().count)
    }

    @Test
    fun `기록이 실패해도 앞의 처리기는 불린다`() {
        val blocker = File(tmp, "blocker").apply { writeText("x") }
        var called = false
        val previous = Thread.UncaughtExceptionHandler { _, _ -> called = true }
        CrashHandler(CrashLog({ throw IllegalStateException("폴더를 못 정한다") }), meta, previous) {}
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(called)
        var called2 = false
        CrashHandler(CrashLog(File(blocker, "c")), meta, { _, _ -> called2 = true }) {}
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(called2)
    }

    @Test
    fun `앞의 처리기가 없으면 프로세스를 내리는 길로 간다`() {
        var exited = false
        CrashHandler(CrashLog(tmp), meta, null) { exited = true }
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(exited)
    }
}
