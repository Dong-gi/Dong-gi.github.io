package io.github.donggi.iroiroviewer.editor

import android.content.Context
import io.github.donggi.iroiroviewer.io.EditorApp

/**
 * 파일 쪽(이 앱의 주 프로세스 — `app`·`core:io`)과 편집기(이 모듈 — `:editor` 프로세스)가 주고받는 약속. 글자열은 `core:io` 의 [EditorApp] 이
 * 한 벌로 든다.
 *
 * 파일 쪽이 [EditorActivity] 를 이름으로 `startActivityForResult` 한다: 동작 [ACTION_OPEN], 데이터는 문서의 content URI(파일 쪽의 FileProvider),
 * 추가값 [EXTRA_KIND]·[EXTRA_MODE]·[EXTRA_NAME]·[EXTRA_SAVE_SLOT]·[EXTRA_KEEP_SCREEN_MINUTES]. 편집기는 이 앱이 띄웠는지 본다(화면을 내보내지
 * 않으므로 남은 띄우지 못한다).
 *
 * ## 저장 — 저장 자리([EXTRA_SAVE_SLOT])가 있으면 그 자리에서 곧바로
 *
 * 파일 쪽이 준 **저장 자리**(`app` 의 `EditorSlotProvider` — 주 프로세스)에 '저장' 할 때마다 문서 전체를 쓰고 [METHOD_COMMIT] 을 부른다(인자는
 * 자리의 표, 추가값 [KEY_KIND]·[KEY_NAME]). 파일 쪽이 파일 작업 큐로 원본을 바꿔 쓰고(첫 저장이면 고치기 전 판을 휴지통에) 답한다 — [KEY_OK],
 * 실제로 쓴 파일의 이름 [KEY_NAME], 못 썼으면 까닭 [KEY_REASON]. 형식이나 이름을 바꿔 저장했으면 원본 곁에 새 파일을 만든다. 그래서 편집기를
 * 닫지 않아도 저장한 것이 파일에 있고, 반영되지 않은 저장은 편집기가 '저장됨' 으로 두지 않는다. 편집기는 사용자 파일에 직접 쓰지 않는다.
 *
 * 끝날 때는 `RESULT_OK`(저장한 것이 있었다)와 마지막 저장의 [EXTRA_KIND]·[EXTRA_NAME] 만 돌려준다 — 파일은 이미 있다.
 *
 * 저장 자리가 없으면(디버그 시험대) 끝날 때 결과로 돌려준다 — `RESULT_OK` + 고친 문서의 content URI([OutputProvider], 읽기 권한) +
 * [EXTRA_KIND](저장한 형식 — 받은 형식과 다를 수 있다. 예: 'HWPX 형식으로 저장') + [EXTRA_NAME](돌려준 파일의 이름). 고친 것이 없으면
 * `RESULT_CANCELED`.
 */
object EditorContract {

    const val ACTION_OPEN = EditorApp.ACTION_OPEN

    /** 문서의 형식 — 파일 쪽이 확장자로 정해 준다. 값은 [Kind] 의 [Kind.id]. */
    const val EXTRA_KIND = EditorApp.EXTRA_KIND

    /**
     * [MODE_VIEW] 또는 [MODE_EDIT]. 없으면 편집. 보기 모드는 오피스·한글 문서의 뷰어다 — 저장 자리([EXTRA_SAVE_SLOT])가 함께
     * 오면 '편집' 단추로 같은 문서를 편집 모드로 다시 연다.
     */
    const val EXTRA_MODE = EditorApp.EXTRA_MODE

    /** 보이는 이름(마지막 조각). 화면 제목과 저장할 때의 이름에만 쓴다. 결과에서는 마지막으로 저장한 파일의 이름. */
    const val EXTRA_NAME = EditorApp.EXTRA_NAME

    /** 파일 쪽의 저장 자리(content URI) — 위 '저장'. */
    const val EXTRA_SAVE_SLOT = EditorApp.EXTRA_SAVE_SLOT

    /** 읽는 동안 화면 켜 두기(분, 보기 모드만) — 파일 쪽 설정의 값을 싣는다(이 프로세스는 그 설정 저장소를 열지 않는다). */
    const val EXTRA_KEEP_SCREEN_MINUTES = EditorApp.EXTRA_KEEP_SCREEN_MINUTES

    const val MODE_VIEW = EditorApp.MODE_VIEW
    const val MODE_EDIT = EditorApp.MODE_EDIT

    /** 편집기 화면의 meta-data — 이 빌드가 다루는 형식(쉼표로). 파일 쪽이 읽는다. */
    const val META_KINDS = EditorApp.META_KINDS

    /** 저장 자리에 다 썼다 — 반영하라(`ContentResolver.call` 의 방법 이름). */
    const val METHOD_COMMIT = EditorApp.METHOD_COMMIT
    const val KEY_KIND = EditorApp.KEY_KIND
    const val KEY_NAME = EditorApp.KEY_NAME
    const val KEY_OK = EditorApp.KEY_OK
    const val KEY_REASON = EditorApp.KEY_REASON

    /** 저장 자리의 권한자(`app` 의 EditorSlotProvider) — 저장 자리가 정말 이 앱의 것인지 본다. */
    fun slotAuthority(context: Context): String = "${context.packageName}.editorslot"

    /**
     * 이 앱이 다루는 형식. [id] 는 약속의 글자열(확장자와 같다)이고 [ext] 는 저장할 때 붙이는 확장자, [mime] 은 돌려줄 때의 형식이다(소문자 —
     * 안드로이드의 MIME 대조는 대소문자를 가린다). ONLYOFFICE 가 여는 형식 가운데 이전 형식(doc·xls·ppt …)과 PDF 는 같은 형식으로 저장하지
     * 않는다 — 어댑터가 OOXML 로 저장해 Files 가 원본 곁에 새 파일로 둔다([Engine.saves] 에 없다).
     */
    enum class Kind(val id: String, val ext: String, val mime: String) {
        HWP("hwp", "hwp", "application/x-hwp"),
        HWPX("hwpx", "hwpx", "application/hwp+zip"),
        DOCX("docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        DOCM("docm", "docm", "application/vnd.ms-word.document.macroenabled.12"),
        DOTX("dotx", "dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template"),
        DOTM("dotm", "dotm", "application/vnd.ms-word.template.macroenabled.12"),
        DOC("doc", "doc", "application/msword"),
        DOT("dot", "dot", "application/msword"),
        ODT("odt", "odt", "application/vnd.oasis.opendocument.text"),
        TXT("txt", "txt", "text/plain"),
        XLSX("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        XLSM("xlsm", "xlsm", "application/vnd.ms-excel.sheet.macroenabled.12"),
        XLTX("xltx", "xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template"),
        XLTM("xltm", "xltm", "application/vnd.ms-excel.template.macroenabled.12"),
        XLS("xls", "xls", "application/vnd.ms-excel"),
        XLT("xlt", "xlt", "application/vnd.ms-excel"),
        ODS("ods", "ods", "application/vnd.oasis.opendocument.spreadsheet"),
        CSV("csv", "csv", "text/csv"),
        PPTX("pptx", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        PPTM("pptm", "pptm", "application/vnd.ms-powerpoint.presentation.macroenabled.12"),
        PPSX("ppsx", "ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow"),
        PPSM("ppsm", "ppsm", "application/vnd.ms-powerpoint.slideshow.macroenabled.12"),
        POTX("potx", "potx", "application/vnd.openxmlformats-officedocument.presentationml.template"),
        POTM("potm", "potm", "application/vnd.ms-powerpoint.template.macroenabled.12"),
        PPT("ppt", "ppt", "application/vnd.ms-powerpoint"),
        POT("pot", "pot", "application/vnd.ms-powerpoint"),
        PPS("pps", "pps", "application/vnd.ms-powerpoint"),
        ODP("odp", "odp", "application/vnd.oasis.opendocument.presentation"),
        PDF("pdf", "pdf", "application/pdf"),

        /** 통로를 시험하는 작은 엔진의 형식 — 디버그 시험대만 쓴다(어떤 바이트든 받는다). */
        PROBE("probe", "bin", "application/octet-stream"),
        ;

        companion object {
            fun of(id: String?): Kind? = entries.firstOrNull { it.id == id }
        }
    }
}
