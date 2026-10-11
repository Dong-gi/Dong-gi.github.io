package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadNamesTest {

    private val extByMime = mapOf(
        "application/pdf" to "pdf", "image/jpeg" to "jpg", "image/png" to "png", "text/plain" to "txt", "application/zip" to "zip",
        "video/mp4" to "mp4", "text/html" to "html",
    )
    private val mimeByExt = mapOf(
        "pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "txt" to "text/plain",
        "zip" to "application/zip", "mp4" to "video/mp4", "html" to "text/html", "csv" to "text/csv",
    )
    private val names = DownloadNames(extByMime::get, mimeByExt::get)

    @Test
    fun filename_별표가_먼저다() {
        val cd = "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''%ED%95%9C%EA%B8%80%20%EB%AC%B8%EC%84%9C.pdf"
        assertEquals("한글 문서.pdf", ContentDisposition.fileName(cd))
        assertEquals("한글 문서.pdf", names.choose(cd, "https://x.test/dl?id=1", "application/pdf"))
    }

    @Test
    fun 따옴표와_역빗금() {
        assertEquals("a \"b\".txt", ContentDisposition.fileName("attachment; filename=\"a \\\"b\\\".txt\""))
        assertEquals("plain.zip", ContentDisposition.fileName("attachment;filename=plain.zip;size=3"))
        assertEquals("only.bin", ContentDisposition.fileName("filename=only.bin"))
    }

    @Test
    fun ISO_8859_1_로_읽힌_UTF_8_을_되돌린다() {
        val mojibake = String("보고서.hwp".toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertEquals("보고서.hwp", ContentDisposition.fileName("attachment; filename=\"$mojibake\""))
        // 진짜 라틴 글자(UTF-8 이 아닌 바이트)는 그대로.
        assertEquals("café.txt", ContentDisposition.fileName("attachment; filename=\"café.txt\""))
    }

    @Test
    fun 퍼센트로_부호화된_filename_도_푼다() {
        assertEquals("월간 보고.xlsx", ContentDisposition.fileName("attachment; filename=\"%EC%9B%94%EA%B0%84%20%EB%B3%B4%EA%B3%A0.xlsx\""))
        assertEquals("100%.txt", ContentDisposition.fileName("attachment; filename=\"100%.txt\""))
    }

    @Test
    fun ISO_8859_1_의_filename_별표() {
        assertEquals("café.txt", ContentDisposition.fileName("attachment; filename*=iso-8859-1'en'caf%E9.txt"))
    }

    @Test
    fun 깨진_값은_null() {
        assertNull(ContentDisposition.fileName(null))
        assertNull(ContentDisposition.fileName("inline"))
        assertNull(ContentDisposition.fileName("attachment; filename*=UTF-8''%ZZ.txt"))
        assertNull(ContentDisposition.fileName("attachment; filename*=nope'%41"))
    }

    @Test
    fun 주소의_마지막_토막() {
        assertEquals("report.pdf", names.choose(null, "https://x.test/files/report.pdf?token=1#p", "application/pdf"))
        assertEquals("사진.jpg", names.choose(null, "https://x.test/%EC%82%AC%EC%A7%84.jpg", "image/jpeg"))
        assertEquals("x.test.html", names.choose(null, "https://x.test/", "text/html"))
        assertEquals("download", names.choose(null, "data:text/plain,hi", null))
    }

    @Test
    fun 확장자를_형식에_맞춘다() {
        assertEquals("dl.pdf", names.choose(null, "https://x.test/dl", "application/pdf"))
        assertEquals("get.php.pdf", names.choose(null, "https://x.test/get.php", "application/pdf; charset=binary"))
        // 같은 갈래면 이름을 믿는다.
        assertEquals("photo.jpeg", names.choose(null, "https://x.test/photo.jpeg", "image/png"))
        // 대강의 형식이면 손대지 않는다.
        assertEquals("archive.7z", names.choose(null, "https://x.test/archive.7z", "application/octet-stream"))
        // text/plain 은 서버가 아무 데나 붙인다.
        assertEquals("data.csv", names.choose(null, "https://x.test/data.csv", "text/plain"))
    }

    @Test
    fun 정해진_이름은_확장자만_맞춘다() {
        // 확장자 없는 blob 의 download 속성 — 앞 판은 이름을 잃고 'download.mp4' 였다.
        assertEquals("내영상.mp4", names.named("내영상", "video/mp4"))
        assertEquals("보고서.pdf", names.named("보고서.pdf", "application/pdf"))
        assertEquals("a_b.mp4", names.named("a/b", "video/mp4; codecs=avc1"))
        assertEquals("그대로", names.named("그대로", null))
        assertEquals("download.zip", names.named(" .. ", "application/zip"))
    }

    @Test
    fun 고친_이름() {
        assertEquals("제목.mp4", DownloadNames.edited("", "제목.mp4"))
        assertEquals("제목.mp4", DownloadNames.edited("///", "제목.mp4"))
        // 확장자를 지웠으면 처음 이름의 것을 붙인다.
        assertEquals("내 영상.mp4", DownloadNames.edited("내 영상", "제목.mp4"))
        assertEquals("다른.webm", DownloadNames.edited("다른.webm", "제목.mp4"))
        assertEquals("a_b", DownloadNames.edited("a/b", "제목"))
        // 이름 가운데의 점은 확장자가 아니다.
        assertEquals("My v1.2 clip.mp4", DownloadNames.edited("My v1.2 clip", "제목.mp4"))
        // 확장자 없는 바탕 이름(HLS·DASH — 확장자는 받으면서 붙는다)의 점도.
        assertEquals("새 이름", DownloadNames.edited("새 이름", "Video 1.5 - 사이트"))
    }

    @Test
    fun 위험한_글자를_바꾼다() {
        assertEquals("a_b_c.txt", SafeFileName.clean("a/b\\c.txt"))
        assertEquals("evil_txt.exe", SafeFileName.clean("evil\u202Etxt.exe"))
        assertEquals("hidden", SafeFileName.clean("..hidden.."))
        assertEquals("download", SafeFileName.clean(" ... "))
        assertEquals("download", SafeFileName.clean("///"))
        assertEquals("tab_name.txt", SafeFileName.clean("tab\tname.txt"))
    }

    @Test
    fun 너무_길면_확장자를_남기고_줄인다() {
        val long = "가".repeat(200) + ".pdf"
        val cleaned = SafeFileName.clean(long)
        assertEquals(true, cleaned.toByteArray(Charsets.UTF_8).size <= SafeFileName.MAX_BYTES, cleaned)
        assertEquals(true, cleaned.endsWith(".pdf"))
        assertEquals(true, cleaned.startsWith("가가"))
    }
}
