package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.IOException

/** 디렉터리 항목의 종류(MS-CFB 2.6.1 의 Object Type 가운데 우리가 받는 셋). */
enum class CfbEntryType {
    /** 뿌리 저장소. 항목 0 하나뿐이고, 작은 스트림들의 그릇(mini stream)을 들고 있다. */
    ROOT,

    /** 저장소 — 폴더에 해당한다. 자식이 있다. */
    STORAGE,

    /** 스트림 — 파일에 해당한다. 바이트가 있다. */
    STREAM,
}

/**
 * CFB 디렉터리 항목 하나.
 *
 * ## 신원은 이름이 아니라 [id] 다
 *
 * 명세는 한 저장소 안에서 같은 이름을 금지하지만 공격자는 그것을 지키지 않는다. 이름으로 다시
 * 찾으면 **다른 항목의 바이트**가 열릴 수 있다(CLAUDE.md '아카이브 엔트리의 신원은 이름이 아니라
 * 인덱스다' 와 같은 판단). 그래서 읽는 함수는 이 객체를 받고, 이 객체는 디렉터리 배열의 번호를 든다.
 *
 * @param id 디렉터리 배열 안의 번호. 파일 하나 안에서 유일하다.
 * @param name 이름(UTF-16 을 푼 것). 제어 문자(`\u0005SummaryInformation`·`\u0006DataSpaces`)가 그대로 들어 있다.
 * @param size **적힌** 크기. v3 파일은 아래 32비트만 본다(위 32비트는 옛 구현이 쓰레기를 남긴다 — 명세).
 *   실제로 읽히는 길이는 이보다 짧을 수 있다 — 섹터 체인이 먼저 끝나면 거기까지다([CfbFile.streamLength]).
 */
class CfbEntry internal constructor(
    val id: Int,
    val name: String,
    val type: CfbEntryType,
    val size: Long,
    internal val startSector: Int,
) {
    val isStream: Boolean get() = type == CfbEntryType.STREAM

    /** 자식을 가질 수 있는가(뿌리와 저장소). */
    val isStorage: Boolean get() = type != CfbEntryType.STREAM

    override fun toString(): String = "CfbEntry(#$id, $type, $size)"

    companion object {
        /**
         * MS-CFB 2.6.4 의 이름 비교로 같은가 — **길이가 같고, 글자마다 대문자로 바꾼 값이 같다.**
         *
         * `String.equals(ignoreCase = true)` 를 쓰지 않는다. 그것은 대문자로 한 번, 소문자로 한 번 더
         * 견줘서 명세보다 넓게 같다고 본다(명세는 '단순 대문자 변환' 하나만 적는다).
         */
        fun sameName(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            for (i in a.indices) {
                if (a[i] != b[i] && a[i].uppercaseChar() != b[i].uppercaseChar()) return false
            }
            return true
        }
    }
}

/**
 * CFB 를 읽을 때의 상한. 항목 수는 `core:safety` 의 [ParseLimits] 에서 온다([from]). 파일 크기는 CFB 만의
 * 구조 상한이라 여기 둔다 — 섹터 번호를 `Int` 로 다루고 방문 집합을 섹터 수만큼 잡기 때문이다(포맷의 구조
 * 상한은 그 포맷 곁에 둔다는 CLAUDE.md '안전' 의 단서).
 *
 * @param maxEntries 디렉터리 항목(저장소 + 스트림) 수. 디렉터리 체인의 길이도 이것으로 자른다.
 * @param maxFileBytes 파일 크기. 섹터 번호를 `Int` 로 다루고 방문 집합(`BitSet`)을 섹터 수만큼 잡으므로
 *   크기를 먼저 자른다. 2 GiB 면 v3(512바이트 섹터)에서도 섹터가 400만 개다.
 */
data class CfbLimits(
    val maxEntries: Int = ParseLimits.DEFAULT.maxEntries,
    val maxFileBytes: Long = 2L * 1024 * 1024 * 1024,
) {
    companion object {
        val DEFAULT = CfbLimits()

        fun from(limits: ParseLimits): CfbLimits = CfbLimits(maxEntries = limits.maxEntries)
    }
}

/**
 * CFB 가 명세에 어긋난다 — 서명·판·섹터 크기·체인·디렉터리 어느 것이든.
 *
 * **메시지에는 우리가 쓴 고정 문장만 들어간다**(경로도, 파일에서 읽은 값도 없다). 그래도 화면에
 * 보내지 마라 — 부르는 쪽이 `OpenFailure.Corrupt` 로 옮긴다.
 *
 * `CorruptFormatException`(그래서 [IOException])을 잇는 것은 스트림을 읽다가도 나기 때문이다
 * (잘린 파일). 공용 `toOpenFailure()` 가 이것을 `Io` 가 아니라 `Corrupt` 로 옮기므로 부르는 쪽이
 * 따로 잡지 않아도 된다(13단계의 HWP 가 이 위에 선다).
 *
 * 상한을 넘은 것(항목 수·파일 크기·부른 쪽의 `maxBytes`)은 이것이 아니라
 * `ParseLimitExceededException` 이다 — 화면에서 '너무 크다' 와 '깨졌다' 는 다른 말이다.
 */
class CfbFormatException internal constructor(message: String) : CorruptFormatException(message)
