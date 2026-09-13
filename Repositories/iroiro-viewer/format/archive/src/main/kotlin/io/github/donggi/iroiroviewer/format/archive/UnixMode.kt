package io.github.donggi.iroiroviewer.format.archive

/**
 * 아카이브가 실어 나르는 유닉스 파일 모드 비트.
 *
 * ZIP·7z·RAR 은 모두 원본 파일의 속성을 32비트 정수에 담는데, 유닉스에서 만든 항목은
 * **상위 16비트가 `st_mode`** 다. 심볼릭 링크(`S_IFLNK`)를 가려내는 데 쓴다.
 *
 * 링크를 굳이 가려내는 이유는 뷰어가 링크를 만들지 않기 때문이 아니라 — 만들지 않으므로
 * 링크를 통한 임의 파일 덮어쓰기는 구조적으로 불가능하다 — **링크 엔트리의 내용이
 * 파일 내용이 아니라 경로 문자열**이어서 그대로 보여주면 사용자를 속이기 때문이다.
 */
object UnixMode {

    private const val S_IFMT = 0xF000
    private const val S_IFLNK = 0xA000

    /** 7z 가 유닉스 확장 속성을 담고 있다는 표시. */
    private const val SEVEN_Z_UNIX_EXTENSION = 0x8000

    /** 상위 16비트에서 파일 종류를 꺼낸다. */
    fun fileType(attributes: Int): Int = (attributes ushr 16) and S_IFMT

    fun isSymlinkFromUnixAttributes(attributes: Int): Boolean = fileType(attributes) == S_IFLNK

    /** 7z 전용. 유닉스 확장 표시가 서 있을 때만 상위 비트가 `st_mode` 다. */
    fun isSevenZSymlink(attributes: Int): Boolean =
        (attributes and SEVEN_Z_UNIX_EXTENSION) != 0 && isSymlinkFromUnixAttributes(attributes)
}
