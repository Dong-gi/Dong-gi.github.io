package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ZIP 파일명 판정의 **회귀 표본**.
 *
 * 판정을 `core:charset` 과 합치기 전에(CLAUDE.md '판정 규칙을 손볼 때는 두 곳을 함께 봐라') 지금의
 * 답을 먼저 박는다. 표본마다 이름 칸의 **바이트**와 UTF-8 표시를 정하고, 나온 이름과 근거 표지를 본다.
 */
class EntryNameRegressionTest {

    private val dir: File = Files.createTempDirectory("iroiro-names").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private val cp949: Charset? = NameSamples.cp949
    private val cpLabel: String get() = cp949!!.name()

    private fun read(raw: ByteArray, flag: Boolean): Pair<String, String> {
        val f = NameSamples.zipWithRawName(File(dir, "n${System.nanoTime()}.zip"), raw, flag)
        return ZipArchiveReader(FileDocumentSource(f), EntryBudget()).use { r ->
            val e = r.entries.single()
            e.name to e.nameCharset
        }
    }

    private fun check(expectedName: String, expectedLabel: String, raw: ByteArray, flag: Boolean = false) {
        val (name, label) = read(raw, flag)
        assertEquals(expectedName, name, "이름")
        assertEquals(expectedLabel, label, "근거")
    }

    private fun cs(name: String): Charset = Charset.forName(name)

    @Test
    fun `CP949 한국어 — 표시 없음과 거짓 UTF-8 표시`() {
        if (cp949 == null) return
        check("한글이름.txt", cpLabel, NameSamples.KOREAN_CP949)
        // 표시가 섰는데 UTF-8 로 읽히지 않으면 표시가 거짓말을 한 것이다.
        check("한글이름.txt", cpLabel, NameSamples.KOREAN_CP949, flag = true)
    }

    @Test
    fun `EUC-KR 범위만 쓰는 이름과 확장 완성형 글자`() {
        if (cp949 == null) return
        check("가나다라 문서.hwp", cpLabel, "가나다라 문서.hwp".toByteArray(cp949))
        // 똠(8C 63)은 EUC-KR 에 없고 CP949 확장 완성형에만 있다.
        check("똠방각하.txt", cpLabel, "똠방각하.txt".toByteArray(cp949))
    }

    @Test
    fun `한자가 섞인 이름과 ASCII 가 섞인 이름과 폴더 경로`() {
        if (cp949 == null) return
        check("漢字한글.txt", cpLabel, "漢字한글.txt".toByteArray(cp949))
        check("IMG_2024_사진 (1).jpg", cpLabel, "IMG_2024_사진 (1).jpg".toByteArray(cp949))
        check("자료/보고서.txt", cpLabel, "자료/보고서.txt".toByteArray(cp949))
    }

    /** 받침 있는 글자가 대부분인 제목. 긴 글의 받침 검사로 판정하면 가짜 한글로 몰린다. */
    @Test
    fun `받침이 많은 한국어 제목`() {
        if (cp949 == null) return
        check("월간 경영 전략 분석 결과.txt", cpLabel, "월간 경영 전략 분석 결과.txt".toByteArray(cp949))
    }

    /** CP949 바이트가 우연히 유효한 UTF-8 인 이름(`C2 A1`…). 라틴 기호로 읽히면 안 된다. */
    @Test
    fun `UTF-8 로도 읽히는 CP949 이름`() {
        if (cp949 == null) return
        check("짱짱.txt", "$cpLabel(한글 우선)", "짱짱.txt".toByteArray(cp949))
        check("징짖짙짚짜짝짠.txt", "$cpLabel(한글 우선)", "징짖짙짚짜짝짠.txt".toByteArray(cp949))
        // UTF-8 로는 라틴 **글자**가 되는 것들(`ĸó`·`ġŲ`·`ȣȯ`). 합친 판정의 첫 판이 이것을 라틴 낱말로 읽었다(검토가
        // 잡은 회귀 — 윈도우의 '캡처' 폴더가 그 모양이다). 합치기 전의 답을 그대로 지킨다.
        check("캡처.png", "$cpLabel(한글 우선)", "캡처.png".toByteArray(cp949))
        check("치킨.jpg", "$cpLabel(한글 우선)", "치킨.jpg".toByteArray(cp949))
        check("호환.txt", "$cpLabel(한글 우선)", "호환.txt".toByteArray(cp949))
        check("체크 (2).txt", "$cpLabel(한글 우선)", "체크 (2).txt".toByteArray(cp949))
    }

    /** 한글 없이 기호만 든 CP949 이름. 옛 판정은 CP949 였고, 합친 판정의 첫 판은 Shift_JIS 반각 부호(`｡､`)로 읽었다. */
    @Test
    fun `기호만 든 CP949 이름`() {
        if (cp949 == null) return
        check("DCIM · Pictures.jpg", cpLabel, "DCIM · Pictures.jpg".toByteArray(cp949))
    }

    @Test
    fun `UTF-8 — 표시 있음과 없음과 일본어`() {
        check("한글이름.txt", "UTF-8(플래그 없음)", "한글이름.txt".toByteArray(Charsets.UTF_8))
        check("한글이름.txt", "UTF-8(플래그)", "한글이름.txt".toByteArray(Charsets.UTF_8), flag = true)
        check("テスト資料.txt", "UTF-8(플래그 없음)", "テスト資料.txt".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `ASCII — 표시가 서 있으면 표시를 따른다`() {
        check("readme.txt", "ASCII", "readme.txt".toByteArray(Charsets.US_ASCII))
        check("readme.txt", "UTF-8(플래그)", "readme.txt".toByteArray(Charsets.US_ASCII), flag = true)
    }

    @Test
    fun `Info-ZIP 유니코드 경로 추가필드를 따른다`() {
        if (cp949 == null) return
        val f = NameSamples.zipWithUnicodeExtra(File(dir, "uextra.zip"), "한글이름.txt", cp949)
        val e = ZipArchiveReader(FileDocumentSource(f), EntryBudget()).use { it.entries.single() }
        assertEquals("한글이름.txt", e.name)
        assertEquals("유니코드 경로 추가필드", e.nameCharset)
    }

    // ---- 합치며 바뀐 답 — 전부 예전이 틀렸던 것이다. 예전 답을 주석에 남긴다 --------------------------

    /**
     * 예전 판정은 UTF-8 이 아니면 **언제나 CP949** 였다(엄격하지도 않게). 그래서 일본어 이름은 한글 찌꺼기가
     * 되고 서유럽 이름은 대체 문자가 섞였다. `CharsetDetector` 로 합치며 고쳐졌다.
     */
    @Test
    fun `합치며 고쳐진 이름 — 일본어와 라틴 문자`() {
        if (cp949 == null) return
        val sjis = cs("Shift_JIS")
        // 예전: `긡긚긣럱뿿.txt`(x-windows-949). 가나의 앞 바이트가 CP949 확장 완성형 자리라 가짜 한글로 가려진다.
        check("テスト資料.txt", "Shift_JIS", "テスト資料.txt".toByteArray(sjis))
        // 예전: `볷�{뚭궻긲�@귽깑뼹궳궥.txt`.
        check("日本語のファイル名です.txt", "Shift_JIS", "日本語のファイル名です.txt".toByteArray(sjis))
        // 예전: `첵�.txt`. CP949 로는 엄격하게 읽히지 않는다.
        check("ﾃｽﾄ.txt", "Shift_JIS", "ﾃｽﾄ.txt".toByteArray(sjis))
        // 예전: `caf챕.txt`(한글 우선). `é`(C3 A9)가 CP949 로는 `챕` 이다 — 라틴 낱말의 글자면 UTF-8 을 지킨다.
        check("café.txt", "UTF-8(플래그 없음)", "café.txt".toByteArray(Charsets.UTF_8))
        check("Größe_naïve.txt", "UTF-8(플래그 없음)", "Größe_naïve.txt".toByteArray(Charsets.UTF_8))
        // 예전: `caf�.txt`. 아무 후보도 엄격하게 못 읽으면 한글이 또렷할 때만 CP949, 아니면 Latin-1.
        check("café.txt", "ISO-8859-1", "café.txt".toByteArray(Charsets.ISO_8859_1))
    }

    /** 깨진 바이트가 하나 섞인 CP949 이름은 예전처럼 한글로 읽고 깨진 자리만 대체 문자다(근거 표지만 새로 붙는다). */
    @Test
    fun `깨진 바이트가 섞인 CP949 이름`() {
        if (cp949 == null) return
        val raw = "보고서".toByteArray(cp949) + byteArrayOf(0xFF.toByte()) + ".txt".toByteArray()
        val (name, label) = read(raw, false)
        assertEquals("보고서�.txt", name)
        assertEquals("$cpLabel(대체)", label)
    }

    // ---- 여전히 틀리는 것 — 고치지 못했다고 박아 둔다 ----------------------------------------------------

    /**
     * **짧은 간체 중국어 이름은 한글 찌꺼기로 읽힌다**(예전과 같다). GB2312 한자 바이트의 60% 가 CP949 로는 완성형 한글
     * 음절이고, 몇 글자로는 받침 검사가 뜻이 없다 — 판정이 한국어 쪽으로 기운다(`CharsetDetector` 의 '못 하는 것').
     */
    @Test
    fun `짧은 간체 중국어 이름은 여전히 틀린다`() {
        if (cp949 == null) return
        check("櫓匡匡숭.txt", cpLabel, "中文文件.txt".toByteArray(cs("GB18030")))
    }

    /**
     * 번체 Big5 이름. 예전에는 한글 찌꺼기였고, 합친 판정의 첫 판은 **Shift_JIS 찌꺼기**였다 — Big5 의 앞 바이트 `A1`~`A5`
     * 가 Shift_JIS 로는 반각 문장 부호(`｡｢｣､･`)라 그것을 일본어의 본토 글자로 셌기 때문이다. 문장 부호를 글자로 세지 않게
     * 고치자(검토) 한자 비율로 Big5 가 이긴다. 글 판정도 같은 답이다.
     */
    @Test
    fun `번체 중국어 이름`() {
        if (cp949 == null) return
        check("繁體中文檔案.txt", "Big5", "繁體中文檔案.txt".toByteArray(cs("Big5")))
    }

    /**
     * **ASCII 낱말 끝에 붙은 한 글자**가 UTF-8 로도 읽히는 CP949 면 라틴 낱말과 바이트가 같다 — `Excel처` 의 `처`(C3 B3)는
     * UTF-8 로 `ó` 라 `Exceló` 가 된다. `café` 를 살리는 규칙의 대가다(옛 판정은 `Excel처` 였고 `café` 를 `caf챕` 로
     * 읽었다). 이름 전체가 `창`·`처`·`체` 같은 UTF-8 로도 읽히는 글자와 ASCII 로만 되어 있어야 걸린다.
     */
    @Test
    fun `ASCII 낱말에 붙은 한 글자는 라틴으로 읽힌다`() {
        if (cp949 == null) return
        check("Exceló.xlsx", "UTF-8(플래그 없음)", "Excel처.xlsx".toByteArray(cp949))
    }
}
