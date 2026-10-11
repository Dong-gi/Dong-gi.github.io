package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FormatId

/**
 * 컨테이너의 **실제 모양**. 매직 바이트로 정한다(내용이 이긴다).
 *
 * ## [FormatId] 와 따로 두는 이유
 *
 * [FormatId] 는 `format:api` 의 목록이고 등록소(`FormatRegistry`)가 '무엇으로 여는가' 를 가르는 신원이다.
 * tar 계열에는 거기 자리가 없다 — 아카이브는 등록소가 아니라 목록 화면의 종류(`FileKind`)로 열리므로
 * 신원이 필요한 적이 없었다. 여기는 **읽는 쪽이 아는 사실**(어떤 압축으로 싸였나, 목록을 싸게 읽을 수
 * 있나)을 담는다. [formatId] 는 [FormatId] 에 대응하는 값이 있을 때만 채운다.
 *
 * ## 갈래([shape])를 상수마다 적는다
 *
 * 리더를 고르는 `when` 이 [shape] 로 갈리고 `else` 가 없다 — 종류를 더하면 갈래를 정하기 전까지 컴파일되지 않는다
 * (함정 표의 '종류를 옮기면 그것을 검사하던 when 을 전부 찾아라').
 */
enum class ArchiveKind(
    /** 화면에 적는 형식 이름. 문장이 아니라 고유명이라 문자열 자원에 두지 않는다. */
    val label: String,
    val formatId: FormatId?,
    internal val shape: Shape,
    /** 감싼 압축 스트림. 압축하지 않았거나 스스로 압축하는 형식(ZIP·7z·RAR)이면 null. */
    val codec: StreamCodec? = null,
) {
    ZIP("ZIP", FormatId.ZIP, Shape.ZIP),
    SEVEN_Z("7z", FormatId.SEVEN_Z, Shape.SEVEN_Z),
    RAR("RAR", FormatId.RAR, Shape.RAR),

    /** 압축하지 않은 tar. 머리만 읽고 자료는 건너뛸 수 있어 목록이 싸다. */
    TAR("TAR", null, Shape.TAR),

    /** gzip 으로 싼 tar(`.tar.gz`·`.tgz`). 스트림이라 한 항목을 꺼내려면 앞에서부터 풀어야 한다. */
    TAR_GZ("TAR.GZ", null, Shape.TAR, StreamCodec.GZIP),

    /** bzip2 로 싼 tar(`.tar.bz2`·`.tbz2`). */
    TAR_BZIP2("TAR.BZ2", null, Shape.TAR, StreamCodec.BZIP2),

    /** xz 로 싼 tar(`.tar.xz`·`.txz`). */
    TAR_XZ("TAR.XZ", null, Shape.TAR, StreamCodec.XZ),

    /** LZ4 프레임으로 싼 tar(`.tar.lz4`). */
    TAR_LZ4("TAR.LZ4", null, Shape.TAR, StreamCodec.LZ4),

    /** LZMA-alone 으로 싼 tar(`.tar.lzma`·`.tlz`). 매직이 없어 이름의 도움을 받는다([LzmaHeader]). */
    TAR_LZMA("TAR.LZMA", null, Shape.TAR, StreamCodec.LZMA),

    /** 유닉스 compress(`.Z`)로 싼 tar(`.tar.Z`·`.taz`). */
    TAR_Z("TAR.Z", null, Shape.TAR, StreamCodec.Z),

    /**
     * gzip 으로 싼 **파일 하나**(안이 tar 가 아니다). 항목이 하나뿐인 아카이브로 연다([SingleStreamReader]).
     * 아래 다섯도 같다.
     */
    GZ("GZ", null, Shape.STREAM, StreamCodec.GZIP),
    BZIP2("BZ2", null, Shape.STREAM, StreamCodec.BZIP2),
    XZ("XZ", null, Shape.STREAM, StreamCodec.XZ),
    LZ4("LZ4", null, Shape.STREAM, StreamCodec.LZ4),
    LZMA("LZMA", null, Shape.STREAM, StreamCodec.LZMA),
    Z("Z", null, Shape.STREAM, StreamCodec.Z),

    /** cpio — commons-compress 가 읽는 네 형식(새 ASCII `070701`·CRC `070702`·옛 ASCII `070707`·옛 이진 `0x71C7`). */
    CPIO("CPIO", null, Shape.CPIO),

    /** ar(`!<arch>`) — GNU·BSD 의 긴 이름까지. */
    AR("AR", null, Shape.AR),

    /**
     * 데비안 꾸러미 — **ar 이고 첫 항목이 `debian-binary`** 다(deb(5) 가 그 차례를 못 박는다). 읽는 법은 [AR] 과 같고
     * 이름표만 다르다. 확장자가 아니라 내용으로 정한다 — `.deb` 로 적힌 다른 ar 은 `AR` 이다.
     */
    DEB("DEB", null, Shape.AR),
    ;

    /** tar 계열인가(압축한 것 포함). */
    val isTar: Boolean get() = shape == Shape.TAR

    /** 파일 하나를 싼 압축 스트림인가 — 항목이 하나뿐이다. */
    val isSingleStream: Boolean get() = shape == Shape.STREAM

    /** 리더의 갈래. */
    internal enum class Shape { ZIP, SEVEN_Z, RAR, TAR, STREAM, CPIO, AR }

    companion object {
        /** 기존 [FormatId] 에서 되짚는다. 세 리더가 스스로 적지 않았을 때의 기본값이다. */
        fun of(formatId: FormatId?): ArchiveKind = when (formatId) {
            FormatId.SEVEN_Z, FormatId.CB7 -> SEVEN_Z
            FormatId.RAR, FormatId.CBR -> RAR
            else -> ZIP
        }

        /** [codec] 으로 싼 tar. */
        internal fun tarOf(codec: StreamCodec): ArchiveKind = entries.first { it.shape == Shape.TAR && it.codec == codec }

        /** [codec] 으로 싼 파일 하나. */
        internal fun streamOf(codec: StreamCodec): ArchiveKind = entries.first { it.shape == Shape.STREAM && it.codec == codec }
    }
}
