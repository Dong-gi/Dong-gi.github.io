package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FormatId

/**
 * 컨테이너의 **실제 모양**. 매직 바이트로 정한다.
 *
 * ## [FormatId] 와 따로 두는 이유
 *
 * [FormatId] 는 `format:api` 의 목록이고 등록소(`FormatRegistry`)가 '무엇으로 여는가' 를 가르는 신원이다.
 * tar 계열에는 거기 자리가 없다 — 아카이브는 등록소가 아니라 목록 화면의 종류(`FileKind`)로 열리므로
 * 신원이 필요한 적이 없었다. 여기는 **읽는 쪽이 아는 사실**(어떤 압축으로 싸였나, 목록을 싸게 읽을 수
 * 있나)을 담는다. [formatId] 는 [FormatId] 에 대응하는 값이 있을 때만 채운다.
 */
enum class ArchiveKind(
    /** 화면에 적는 형식 이름. 문장이 아니라 고유명이라 문자열 자원에 두지 않는다. */
    val label: String,
    val formatId: FormatId?,
) {
    ZIP("ZIP", FormatId.ZIP),
    SEVEN_Z("7z", FormatId.SEVEN_Z),
    RAR("RAR", FormatId.RAR),

    /** 압축하지 않은 tar. 머리만 읽고 자료는 건너뛸 수 있어 목록이 싸다. */
    TAR("TAR", null),

    /** gzip 으로 싼 tar(`.tar.gz`·`.tgz`). 스트림이라 한 항목을 꺼내려면 앞에서부터 풀어야 한다. */
    TAR_GZ("TAR.GZ", null),

    /** bzip2 로 싼 tar(`.tar.bz2`·`.tbz2`). */
    TAR_BZIP2("TAR.BZ2", null),

    /** xz 로 싼 tar(`.tar.xz`·`.txz`). */
    TAR_XZ("TAR.XZ", null),
    ;

    val isTar: Boolean get() = this == TAR || this == TAR_GZ || this == TAR_BZIP2 || this == TAR_XZ

    companion object {
        /** 기존 [FormatId] 에서 되짚는다. 세 리더가 스스로 적지 않았을 때의 기본값이다. */
        fun of(formatId: FormatId?): ArchiveKind = when (formatId) {
            FormatId.SEVEN_Z, FormatId.CB7 -> SEVEN_Z
            FormatId.RAR, FormatId.CBR -> RAR
            else -> ZIP
        }
    }
}
