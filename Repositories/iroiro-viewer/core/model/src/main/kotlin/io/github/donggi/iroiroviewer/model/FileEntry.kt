package io.github.donggi.iroiroviewer.model

/**
 * 목록 한 줄.
 *
 * [kind] 를 나열할 때 함께 정해 두는 것은, 목록을 그릴 때마다 확장자를 다시 따지지
 * 않기 위해서다. 1만 줄짜리 폴더에서 스크롤할 때마다 문자열을 자르면 그것만으로 끊긴다.
 *
 * [lastModified] 는 epoch 밀리초. 0 은 '모름'.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    /** 심볼릭 링크. 따라가지 않는 것이 기본값이다(순환과 탈출을 막는다). */
    val isSymlink: Boolean,
    val kind: FileKind,
    /**
     * 들어갈 수 없는 폴더. `Android/data`·`Android/obb` 가 그렇다.
     *
     * 숨기지 않고 남겨 둔다 — 숨기면 사용자에게는 '내 파일이 사라졌다' 가 되고,
     * 그것이 못 들어간다는 사실보다 나쁘다.
     */
    val isLocked: Boolean = false,
)

/**
 * 무엇으로 보이는가. **확장자로만 정한다.**
 *
 * 목록을 그리려고 파일을 여는 일은 없어야 한다 — 폴더 하나에 1만 개가 있으면
 * 매직 바이트를 읽는 것만으로 수 초가 날아간다. 내용으로 하는 판별은 실제로 열 때 한다.
 */
enum class FileKind {
    FOLDER,
    IMAGE,
    AUDIO,
    VIDEO,
    TEXT,
    CODE,
    PDF,
    EBOOK,
    ARCHIVE,
    COMIC,
    DOCUMENT,
    SHEET,
    SLIDE,
    HWP,
    APK,
    FONT,
    OTHER,
}
