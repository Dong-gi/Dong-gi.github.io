package io.github.donggi.iroiroviewer.diag

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Environment
import android.os.ext.SdkExtensions
import io.github.donggi.iroiroviewer.format.archive.ArchiveSelfTest
import io.github.donggi.iroiroviewer.format.archive.EntryNameDecoder
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

/**
 * 2단계의 관통 스파이크.
 *
 * 조사에서 "미확인" 으로 남은 런타임 값을 **이 기기에서 실제로 재서** 보여준다.
 * 추정으로 계획을 세우면 뒤 단계에서 그 추정이 틀렸을 때 되돌릴 것이 많아지므로,
 * 코드를 쓰기 전에 값부터 확정한다.
 *
 * 특히 압축 절은 **릴리스(R8) 빌드에서 반드시 다시 돌려야 한다.** R8 은 이름으로만
 * 불리는 LZMA2 디코더를 조용히 지우는데, 빌드는 성공하고 사용자가 파일을 열 때에야
 * 터진다. 디버그 빌드만 보면 끝까지 못 잡는다.
 */
object Diagnostics {

    data class Row(val label: String, val value: String, val ok: Boolean? = null)
    data class Section(val title: String, val rows: List<Row>)

    suspend fun collect(context: Context): List<Section> = withContext(IroDispatchers.parsing) {
        listOf(
            environment(),
            volumes(context),
            charsets(),
            sdkExtensions(),
            codecs(),
            monospace(),
            archiveSpike(context),
        )
    }

    /**
     * 고정폭 글꼴에서 글자 하나가 몇 픽셀인가. **텍스트 뷰어의 전제다.**
     *
     * 7단계는 저장소에 글꼴을 넣지 않기로 하고 [Typeface.MONOSPACE] 를 쓴다. 그 글꼴에는
     * 한글·한자가 없고 시스템 폴백 사슬이 `NotoSansCJK` 로 이어 준다 — 글자는 빠지지
     * 않지만 **글자폭이 라틴 문자의 정확히 두 배라는 보장이 없다.** 두 배가 아니면 한글이
     * 섞인 줄에서 세로 정렬이 어긋난다.
     *
     * 그래서 재서 적는다. 추정하지 않는다.
     */
    private fun monospace(): Section {
        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 100f
        }
        fun advance(s: String): Float = paint.measureText(s) / s.length

        val latin = advance("MMMMMMMM")
        val digit = advance("00000000")
        val hangul = advance("가나다라마바사아")
        val han = advance("漢字文書書體字形")
        val kana = advance("あいうえおかきく")

        fun ratio(v: Float): String =
            if (latin <= 0f) "모름" else String.format(Locale.ROOT, "%.3f", v / latin)

        return Section(
            "고정폭 글꼴(텍스트 뷰어)",
            listOf(
                Row("라틴 글자폭(textSize=100)", String.format(Locale.ROOT, "%.1f", latin)),
                // 라틴끼리 폭이 같아야 '고정폭' 이다. 다르면 그 글꼴은 고정폭이 아니다.
                Row("숫자 / 라틴", ratio(digit), kotlin.math.abs(digit - latin) < 0.01f),
                // 여기가 진짜 질문이다 — 2.000 이면 칸이 맞고, 아니면 안 맞는다.
                Row("한글 / 라틴", ratio(hangul), kotlin.math.abs(hangul - latin * 2f) < 0.5f),
                Row("한자 / 라틴", ratio(han), kotlin.math.abs(han - latin * 2f) < 0.5f),
                Row("가나 / 라틴", ratio(kana), kotlin.math.abs(kana - latin * 2f) < 0.5f),
            ),
        )
    }

    /**
     * 보이는 볼륨과 그 안의 항목 수.
     *
     * 여기서 볼륨이 둘 이상으로 잡히는지가 4단계의 전제다 — 볼륨 간 이동은 `rename`
     * 이 EXDEV 로 실패하고 복사+삭제로 내려가는데, 볼륨이 하나뿐인 환경에서 시험하면
     * 그 경로가 한 번도 실행되지 않은 채 '통과' 로 표시된다.
     */
    private fun volumes(context: Context): Section {
        val list = io.github.donggi.iroiroviewer.io.VolumeRegistry.volumes(context)
        val rows = list.map { v ->
            Row(
                v.label,
                "${v.path} · ${io.github.donggi.iroiroviewer.io.Format.size(v.freeBytes)} 남음" +
                    (if (v.isRemovable) " · 분리 가능" else ""),
                true,
            )
        }
        return Section("저장소 볼륨", rows + Row("볼륨 수", list.size.toString(), list.size >= 2))
    }

    // ---- ① 환경 -----------------------------------------------------------

    private fun environment() = Section(
        "환경",
        listOf(
            Row("안드로이드", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            Row("기기", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Row("ABI", Build.SUPPORTED_ABIS.joinToString()),
            Row(
                "모든 파일 접근",
                if (Environment.isExternalStorageManager()) "허용됨" else "없음",
                Environment.isExternalStorageManager(),
            ),
        ),
    )

    // ---- ② 문자셋 ---------------------------------------------------------

    /**
     * 안드로이드가 어떤 별칭을 아는지는 문서로 확인되지 않았다. CP949 를 못 찾으면
     * 한국어 zip 파일명이 통째로 깨지므로(7단계의 텍스트 인코딩 감지도 같은 표를 쓴다)
     * 여기서 실제 이름을 확정한다.
     */
    private fun charsets(): Section {
        val candidates = listOf(
            "x-windows-949", "MS949", "windows-949", "EUC-KR",
            "Shift_JIS", "windows-31j", "GB18030", "Big5", "UTF-16LE",
        )
        val rows = candidates.map { name ->
            val supported = runCatching { Charset.isSupported(name) }.getOrDefault(false)
            val real = if (supported) runCatching { Charset.forName(name).name() }.getOrNull() else null
            Row(name, real ?: "없음", supported)
        }
        val chosen = EntryNameDecoder.cp949Name
        return Section(
            "문자셋",
            listOf(Row("CP949 로 고른 것", chosen ?: "없음 — UTF-8 로 물러남", chosen != null)) + rows,
        )
    }

    // ---- ③ SDK 확장 -------------------------------------------------------

    /**
     * 암호 PDF 를 **누가 푸는가**가 API 수준으로 갈린다. 35 이상은 플랫폼이 암호를 받고
     * (`PdfRenderer(pfd, LoadParams)`), 그 아래는 우리가 풀어 메모리 파일로 넘긴다.
     * 어느 쪽이든 암호 PDF 는 열린다.
     *
     * 확장 수준은 **이 앱의 경로를 가르지 않는다** — 확장 13 의 `PdfRendererPreV` 를 쓰지
     * 않기로 했다(`PdfEngine` 의 주석). 그래도 값을 보여 주는 것은 기기가 무엇을 가졌는지
     * 알아 두기 위해서다.
     */
    private fun sdkExtensions(): Section {
        val s = runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) }.getOrNull()
        val r = runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) }.getOrNull()
        val pdf = when {
            Build.VERSION.SDK_INT >= 35 -> "플랫폼이 암호를 받는다(LoadParams)"
            else -> "우리가 풀어 메모리 파일(memfd)로 넘긴다"
        }
        return Section(
            "SDK 확장",
            listOf(
                Row("S 확장", s?.toString() ?: "모름"),
                Row("R 확장", r?.toString() ?: "모름"),
                Row("암호 PDF", pdf),
            ),
        )
    }

    // ---- ④ 코덱 -----------------------------------------------------------

    /**
     * "마이너 코덱까지" 가 이 기기에서 실제로 어디까지인가.
     *
     * Media3 는 컨테이너를 넓게 열지만 코덱은 기기가 주는 만큼이다. 목록에 없는 것은
     * 재생이 안 되고, 그 사실을 사용자에게 **재생 전에** 말해 주어야 한다(10단계).
     */
    private fun codecs(): Section {
        val decoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        }.getOrDefault(emptyList())

        fun has(mime: String): Boolean =
            decoders.any { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }

        fun hasProfile(mime: String, profile: Int): Boolean = decoders.any { info ->
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                runCatching {
                    info.getCapabilitiesForType(mime).profileLevels.any { it.profile == profile }
                }.getOrDefault(false)
        }

        val rows = mutableListOf<Row>()
        fun row(label: String, present: Boolean) = rows.add(Row(label, if (present) "있음" else "없음", present))

        row("H.264", has("video/avc"))
        row("HEVC", has("video/hevc"))
        row("HEVC Main10", hasProfile("video/hevc", MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10))
        row("VP9", has("video/x-vnd.on2.vp9"))
        row("VP9 Profile2(10bit)", hasProfile("video/x-vnd.on2.vp9", MediaCodecInfo.CodecProfileLevel.VP9Profile2))
        row("AV1", has("video/av01"))
        row("FLAC", has("audio/flac"))
        row("Opus", has("audio/opus"))
        row("Vorbis", has("audio/vorbis"))
        row("ALAC", has("audio/alac"))
        row("AC-3", has("audio/ac3"))
        row("E-AC-3", has("audio/eac3"))
        row("DTS", has("audio/vnd.dts"))
        rows.add(Row("디코더 총수", decoders.size.toString()))
        return Section("코덱(기기 제공)", rows)
    }

    // ---- ⑤ 압축 관통 ------------------------------------------------------

    /**
     * 표본을 **이 기기에서 만들어** 다시 읽는다. 만드는 쪽과 읽는 쪽이 같은 라이브러리라
     * 순환처럼 보이지만, 확인하려는 것은 포맷의 정확성이 아니라 **디코더가 이 APK 안에
     * 살아 있는가** 이므로 이 방식이 맞다. 릴리스(R8) 빌드에서 반드시 다시 본다.
     *
     * 작업 디렉터리에 매번 다른 이름을 준다. 화면을 회전하면 앞선 실행이 아직 도는 채로
     * 새 실행이 시작되는데, 같은 폴더를 쓰면 먼저 끝난 쪽이 상대의 표본을 지워서
     * 멀쩡한 디코더가 실패로 보고된다.
     *
     * 압축 라이브러리를 직접 건드리는 코드는 `format:archive` 에만 둔다(모듈 규칙).
     */
    private fun archiveSpike(context: Context): Section {
        val dir = File(context.cacheDir, "diag-${System.nanoTime()}")
        val rows = ArchiveSelfTest.run(dir).map { r ->
            val v = r.values
            val label = when (r.check) {
                ArchiveSelfTest.Check.CP949_ZIP -> "CP949 zip"
                ArchiveSelfTest.Check.UTF8_NO_FLAG_ZIP -> "UTF-8 무플래그 zip"
                ArchiveSelfTest.Check.LZMA2_SEVEN_Z -> "LZMA2 7z (R8 관문)"
                ArchiveSelfTest.Check.PATH_TRAVERSAL -> "경로 탈출 차단"
            }
            val value = when {
                r.failureClass != null -> "실패: ${r.failureClass}"
                r.check == ArchiveSelfTest.Check.LZMA2_SEVEN_Z && v.size >= 3 ->
                    "${v[0]}바이트 복원 · 압축비 ${v[1]}:1 · CRC ${v[2]}"
                r.check == ArchiveSelfTest.Check.PATH_TRAVERSAL ->
                    if (r.ok == true) "차단됨 (${v.firstOrNull() ?: ""})" else "뚫림"
                v.size >= 2 -> "${v[0]} · ${v[1]}"
                r.ok == false -> "이 기기에 CP949 문자셋이 없다"
                else -> ""
            }
            Row(label, value, r.ok)
        }
        return Section(
            "압축 관통",
            rows + Row("RAR", "표본을 만들 수 없어 미검증(쓰기 구현이 없다)", null),
        )
    }
}
