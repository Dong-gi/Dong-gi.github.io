package io.github.donggi.iroiroviewer.io

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * 편집기(`feature:editor` — rhwp·ONLYOFFICE 웹 편집기를 앱 안에 묶은 것)와의 약속. 편집기는 **이 앱의 따로 된 프로세스**([PROCESS])에서 돈다.
 * 편집기 쪽의 같은 이름(`EditorContract`)은 이 글자열을 그대로 쓴다.
 *
 * ## 길
 *
 * 1. 파일 쪽이 [intent] 로 편집기를 **이름으로** 띄운다(`startActivityForResult` — 편집기는 이 앱이 띄웠는지 본다). 데이터는 문서의 content
 *    URI(우리 FileProvider), 추가값은 형식·모드·보이는 이름·저장 자리.
 * 2. **저장 자리**([EXTRA_SAVE_SLOT] — `app` 의 `EditorSlotProvider`, 주 프로세스): 편집기는 '저장' 할 때마다 문서 전체를 그 자리에 쓰고
 *    [METHOD_COMMIT] 을 부른다 — 파일 쪽이 파일 작업 큐로 원본을 바꿔 쓴다(첫 저장이면 고치기 전 판을 휴지통에 — [EditorSaver]). 편집기는
 *    사용자 파일에 직접 쓰지 않는다 — 원본을 바꾸는 길은 이것 하나다. 두 프로세스를 잇는 것이 이 제공자다(편집하는 동안 주 프로세스가
 *    거둬져도 다음 저장이 그것을 다시 세운다).
 * 3. 편집기가 끝나면 결과가 온다(고친 것이 있었는가) — 무엇을 썼는지는 저장 자리가 안다.
 *
 * ## 무엇을 편집기로 여는가
 *
 * 편집기 화면의 매니페스트에 적힌 형식([META_KINDS] — 빌드가 엔진 묶음이 든 것으로 채운다)만. 묶음 없이 지은 빌드는 목록이 비어 아무것도
 * 넘기지 않는다 — 오피스·한글 문서는 문서 뷰어가 '다루지 않는 문서' 와 '다른 앱으로 열기' 로 끝낸다. **볼 때도** 편집기로 여는 형식은
 * [viewKindFor] 가 고른다(오피스·한글 문서의 뷰어가 편집기의 보기 모드다). 한글 서식(`.hwt`·`.hwtx`·`.owpml`)은 확장자의
 * 별칭으로 HWP·HWPX 가 되어 같은 길을 간다([kindOfName]).
 */
object EditorApp {

    /** 편집기 화면(`feature:editor`). 이름으로 띄운다 — 파일 쪽 모듈은 그 모듈을 보지 않는다. */
    const val ACTIVITY = "io.github.donggi.iroiroviewer.editor.EditorActivity"

    /** 편집기가 도는 프로세스의 꼬리(그 모듈 매니페스트의 `android:process`). 그 프로세스는 파일 쪽의 초기화를 건너뛴다(`app` 의 IroiroApp). */
    const val PROCESS = ":editor"

    const val ACTION_OPEN = "io.github.donggi.iroiro.action.OPEN_DOCUMENT"
    const val EXTRA_KIND = "io.github.donggi.iroiro.extra.KIND"
    const val EXTRA_MODE = "io.github.donggi.iroiro.extra.MODE"
    const val EXTRA_NAME = "io.github.donggi.iroiro.extra.NAME"

    /**
     * 파일 쪽의 저장 자리(content URI). 편집기는 저장할 때마다 여기에 쓰고 [METHOD_COMMIT] 을 부른다. 보기 모드에도 싣는다 — 편집기가
     * '편집' 단추로 같은 문서를 편집 모드로 다시 열 수 있다는 뜻이다(읽기 전용 사본에는 싣지 않는다).
     */
    const val EXTRA_SAVE_SLOT = "io.github.donggi.iroiro.extra.SAVE_SLOT"

    /**
     * 읽는 동안 화면 켜 두기(분) — 보기 모드에서 마지막 조작 뒤 그 시간까지 편집기가 화면을 켜 둔다(파일 쪽 설정 '읽는 동안 › 화면 켜 두기').
     * 편집기의 프로세스는 이 앱의 설정 저장소(DataStore — 한 프로세스의 것이다)를 열지 않으므로 값을 실어 보낸다. 없거나 0 이면 기기의 시간 그대로.
     */
    const val EXTRA_KEEP_SCREEN_MINUTES = "io.github.donggi.iroiro.extra.KEEP_SCREEN_MINUTES"

    const val MODE_EDIT = "edit"
    const val MODE_VIEW = "view"

    /** 편집기 화면(`EditorActivity`)에 붙은 meta-data — 다루는 형식 글자를 쉼표로(`hwp,hwpx`). */
    const val META_KINDS = "io.github.donggi.iroiro.editor.KINDS"

    /** 저장 자리에 다 썼다 — 반영하라(`ContentResolver.call` 의 방법 이름, 인자는 자리의 표). */
    const val METHOD_COMMIT = "commit"
    const val KEY_KIND = "kind"
    const val KEY_NAME = "name"
    const val KEY_OK = "ok"
    const val KEY_REASON = "reason"

    /**
     * 형식 글자(= 확장자) → 넘길 때의 MIME(소문자 — 안드로이드의 MIME 대조는 대소문자를 가린다). 편집기의 `EditorContract.Kind` 와 같은 글자다.
     * 한글(rhwp)·OOXML 과 그 판·이전 오피스 형식·ODF·TXT·CSV·PDF(ONLYOFFICE — RTF 는 그 변환기가 한글을 버려 넘기지 않는다). 이 가운데 무엇을 실제로 넘기는지는 편집기가
     * 알린다([META_KINDS]).
     */
    private val MIME_OF_KIND = mapOf(
        "hwp" to "application/x-hwp",
        "hwpx" to "application/hwp+zip",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docm" to "application/vnd.ms-word.document.macroenabled.12",
        "dotx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "dotm" to "application/vnd.ms-word.template.macroenabled.12",
        "doc" to "application/msword",
        "dot" to "application/msword",
        "odt" to "application/vnd.oasis.opendocument.text",
        "txt" to "text/plain",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsm" to "application/vnd.ms-excel.sheet.macroenabled.12",
        "xltx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "xltm" to "application/vnd.ms-excel.template.macroenabled.12",
        "xls" to "application/vnd.ms-excel",
        "xlt" to "application/vnd.ms-excel",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "csv" to "text/csv",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pptm" to "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        "ppsx" to "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "ppsm" to "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
        "potx" to "application/vnd.openxmlformats-officedocument.presentationml.template",
        "potm" to "application/vnd.ms-powerpoint.template.macroenabled.12",
        "ppt" to "application/vnd.ms-powerpoint",
        "pot" to "application/vnd.ms-powerpoint",
        "pps" to "application/vnd.ms-powerpoint",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "pdf" to "application/pdf",
    )

    /** 형식 글자 → 확장자. 둘이 같다(편집기의 `Kind` 가 그렇게 정했다). */
    private val KIND_OF_EXT = MIME_OF_KIND.keys.associateBy { it }

    /**
     * 확장자의 **별칭** → 형식 글자. 바이트 모양이 편집기의 형식과 같은데 이름만 다른 것 — 한글 서식(`.hwt` — HWP 5.0 과 같은
     * CFB), HWPX 서식(`.hwtx`)과 OWPML(`.owpml` — HWPX 와 같은 꾸러미). 목록은 이 셋을 '한글 문서' 로 그린다(`MimeResolver`). 뷰어 교체
     * 전에는 이 앱의 판별기가 내용으로 열었는데, 그것을 걷은 뒤 이 표에 없어 누르면 '이 앱이 다루지 않는 문서입니다' 였다.
     *
     * **[extensions] 에는 넣지 않는다** — 편집기는 이 확장자로 쓰지 않는다(rhwp 는 `.hwp`·`.hwpx` 로 저장한다). 그래서 고쳐 저장하면 원본
     * 서식은 그대로 두고 곁에 새 `.hwp`·`.hwpx` 가 생긴다(이전 오피스 형식이 곁에 OOXML 이 되는 것과 같은 결과 — `app` 의 `EditorSlots.plan`
     * 이 이름이 다르면 새 파일로 둔다). 서식 파일을 덮어 고치면 다음에 그 서식으로 새 문서를 시작할 수 없다.
     */
    private val ALIAS_OF_EXT = mapOf("hwt" to "hwp", "hwtx" to "hwpx", "owpml" to "hwpx")

    /** 편집기의 형식이 쓰는 확장자 전부 — 저장 이름을 다듬을 때 사용자가 친 확장자를 떼는 데 쓴다(`EditorSlots.cleanName`). 별칭은 없다. */
    val extensions: Set<String> get() = KIND_OF_EXT.keys

    /**
     * 편집기가 다루는 형식 — 편집기 화면의 meta-data. 엔진 묶음 없이 지은 빌드면 빈 것. 처음 한 번만 꾸러미 관리자에 묻는다(이 앱의 매니페스트는
     * 도는 동안 바뀌지 않는다).
     */
    fun kinds(context: Context): Set<String> = cachedKinds ?: readKinds(context).also { cachedKinds = it }

    @Volatile
    private var cachedKinds: Set<String>? = null

    private fun readKinds(context: Context): Set<String> = try {
        @Suppress("DEPRECATION") // 플래그 객체를 받는 판은 API 33 부터다(minSdk 31).
        val info = context.packageManager.getActivityInfo(ComponentName(context.packageName, ACTIVITY), PackageManager.GET_META_DATA)
        parseKinds(info.metaData?.getString(META_KINDS))
    } catch (e: PackageManager.NameNotFoundException) {
        emptySet()
    }

    /** meta-data 의 글을 형식 글자로 — 판단만(JVM 시험). 모르는 글자는 버린다. */
    internal fun parseKinds(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim().lowercase() }.filter { it in MIME_OF_KIND }.toSet()

    /**
     * [fileName] 의 확장자가 고르는 편집기의 형식 글자 — 편집기가 다루는가와 상관없이(판단만, JVM 시험). 별칭([ALIAS_OF_EXT])은 그 형식으로
     * 읽는다 — `신청서.hwt` 는 `hwp` 다.
     */
    internal fun kindOfName(fileName: String): String? {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return null
        val ext = name.substring(dot + 1).lowercase()
        return KIND_OF_EXT[ext] ?: ALIAS_OF_EXT[ext]
    }

    /** [fileName] 을 편집기로 고칠 수 있으면 그 형식 글자, 아니면 null. */
    fun kindFor(context: Context, fileName: String): String? {
        val kind = kindOfName(fileName) ?: return null
        return kind.takeIf { it in kinds(context) }
    }

    /**
     * [fileName] 을 **볼 때도** 편집기(보기 모드)로 여는가 — 그러면 그 형식 글자, 아니면 null(이 앱의 뷰어가 연다). 2026-10-09 사용자 요청
     * ('Files 의 뷰어는 PC 에서 보는 것에 가까운 뷰어로 — 편집기보다 확연히 나쁜 것은 과감히 제거'): 한글(rhwp)·오피스·ODF 는 편집기가 쪽을
     * PC 처럼 그리므로 그쪽이 뷰어다.
     *
     * 빼는 것은 [OWN_VIEWERS] — PDF 는 이 앱의 pdfium 뷰어가 이미 쪽 그대로 그리고(편집기는 PDF 를 고칠 수 있는 문서로 **바꿔** 연다 —
     * 쪽 모양이 달라진다), 글·CSV 는 텍스트 뷰어의 것이다(목록이 글로 보낸다).
     */
    fun viewKindFor(context: Context, fileName: String): String? = kindFor(context, fileName)?.takeIf { viewsInEditor(it) }

    /** [kind] 를 볼 때도 편집기로 여는가 — 판단만(JVM 시험). */
    internal fun viewsInEditor(kind: String): Boolean = kind in MIME_OF_KIND && kind !in OWN_VIEWERS

    /** 편집기가 다뤄도 이 앱의 뷰어가 보이는 형식 — [viewKindFor]. */
    private val OWN_VIEWERS = setOf("pdf", "txt", "csv")

    /**
     * 편집기를 여는 인텐트 — **이름으로**(인텐트 필터가 없고 내보내지 않는다). [document] 에는 읽기 권한을 싣는다(같은 앱이라 없어도 열리지만,
     * 편집기가 받는 것을 그 문서 하나로 적어 둔다). 저장 자리는 같은 앱(UID)만 닿는 제공자라 따로 권한을 주지 않는다.
     */
    fun intent(context: Context, document: Uri, kind: String, mode: String, name: String, slot: Uri?, keepScreenMinutes: Int = 0): Intent =
        Intent(ACTION_OPEN)
            .setComponent(ComponentName(context.packageName, ACTIVITY))
            .setDataAndType(document, MIME_OF_KIND[kind] ?: "application/octet-stream")
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_MODE, mode)
            .putExtra(EXTRA_NAME, name)
            .apply { if (slot != null) putExtra(EXTRA_SAVE_SLOT, slot) }
            .apply { if (keepScreenMinutes > 0) putExtra(EXTRA_KEEP_SCREEN_MINUTES, keepScreenMinutes) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** 형식 글자의 확장자(형식이 바뀐 저장의 새 이름에 붙인다). */
    fun extensionOf(kind: String): String? = KIND_OF_EXT.entries.firstOrNull { it.value == kind }?.key
}
