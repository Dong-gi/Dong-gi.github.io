package io.github.donggi.iroiroviewer.safety

import java.io.IOException

/**
 * 파일을 읽는 모든 코드가 지키는 상한. **한곳에만 있다.**
 *
 * 이 앱은 모든 파일 접근 권한을 가지고 돌기 때문에, 파서가 악의적 파일에 넘어가면
 * 피해 범위가 앱 샌드박스가 아니라 기기 저장소 전체다. 그래서 상한은 파서마다 정하는
 * 것이 아니라 여기서 한 번 정하고 전부가 같은 값을 쓴다.
 *
 * **선언된 크기를 믿지 않는다.** ZIP 의 `getSize`, OOXML 의 선언 길이, HWP 레코드
 * 헤더의 size 는 전부 공격자가 적는 값이다. 상한은 언제나 *실제로 읽은 바이트*를 센다.
 */
data class ParseLimits(
    /** 컨테이너 하나가 담을 수 있는 엔트리 수. 중첩 폭탄의 1차 방어선이다. */
    val maxEntries: Int = 10_000,

    /** 엔트리 하나를 풀어 만들 수 있는 최대 바이트. */
    val maxSingleOutput: Long = 256L * 1024 * 1024,

    /** 아카이브 하나에서 나올 수 있는 총 바이트. */
    val maxTotalOutput: Long = 1L * 1024 * 1024 * 1024,

    /**
     * 압축비 상한. 이것만으로 막으면 정상 파일을 거부한다 — 0 으로 채운 로그 파일은
     * 1000:1 을 쉽게 넘는다. 그래서 [ratioFloorBytes] 를 넘긴 뒤에만 본다.
     */
    val maxCompressionRatio: Int = 100,

    /** 압축비를 보기 시작하는 출력 바이트. 이 아래에서는 비율이 무의미하다. */
    val ratioFloorBytes: Long = 1L * 1024 * 1024,

    /**
     * 컨테이너 안의 컨테이너를 몇 겹까지 여는가. 1 은 "아카이브 안의 아카이브는 목록만
     * 보이고 열지 않는다" 는 뜻이다. 재귀 폭탄은 이 값으로 끝난다.
     */
    val maxContainerDepth: Int = 1,

    /** XML 요소 중첩 깊이. 수천 겹으로 스택을 터뜨리는 문서가 있다. */
    val maxXmlDepth: Int = 256,

    /**
     * XML 문서 하나의 **입력 바이트** 상한.
     *
     * 텍스트 길이 검사는 문자열이 이미 힙에 올라온 뒤에야 돌기 때문에 OOM 을 막지
     * 못한다. 실제 방어는 여기다 — 32MB 면 정상 OOXML·HWPX 본문을 충분히 덮으면서
     * 폰 힙이 넘어가는 것은 막는다.
     */
    val maxXmlBytes: Long = 32L * 1024 * 1024,

    /** 텍스트 노드 하나의 최대 문자 수. 화면까지 보내지 않기 위한 2차 방어다. */
    val maxXmlTextChars: Int = 8 * 1024 * 1024,

    /**
     * 파일 하나를 여는 데 허용하는 시간(밀리초). 무한 루프형 DoS 는 크기 상한에
     * 걸리지 않는다 — 아무것도 만들어 내지 않으면서 돌기 때문이다.
     *
     * 이 값을 쓰는 것은 파서가 아니라 호출부다: `withTimeout(limits.openTimeoutMs) { ... }`.
     * 파서는 협조적 취소(`ensureActive()`)만 지키면 된다.
     */
    val openTimeoutMs: Long = 30_000,
) {
    companion object {
        /** 기본값. 뷰어의 모든 경로가 이것을 쓴다. */
        val DEFAULT = ParseLimits()

        /**
         * 목록·판별처럼 "내용을 다 읽지 않는" 경로. 헤더만 보고 끝내야 하는 자리에서
         * 실수로 본문을 다 읽는 것을 막는다.
         */
        val PROBE = ParseLimits(
            maxEntries = 1_000,
            maxSingleOutput = 1L * 1024 * 1024,
            maxTotalOutput = 4L * 1024 * 1024,
            openTimeoutMs = 5_000,
        )

        /**
         * **사용자가 직접 시킨 풀기**에 쓰는 상한.
         *
         * [DEFAULT] 의 총량 1 GiB 는 "파서가 한 번에 힙에 올리는 양" 을 막는 값이다.
         * 푸는 것은 성격이 다르다 — 64 KiB 버퍼로 디스크에 흘려보내므로 메모리가 늘지
         * 않고, 사용자가 자기 500 MB 백업 zip 을 푸는 것은 **정상적인 일**이다.
         * 그 상한을 그대로 쓰면 정상 아카이브가 거절된다.
         *
         * 그래서 총량은 **대상 볼륨의 여유 공간**으로 바꾼다 — 그것이 진짜 상한이고,
         * 넘으면 어차피 `ENOSPC` 로 멈춘다. 대신 **엔트리별 방어는 그대로 둔다**:
         * 단일 엔트리 크기와 압축비는 폭탄을 막는 값이라 푼다고 풀릴 이유가 없다.
         *
         * @param freeBytes 대상 볼륨의 남은 바이트. 모르면 0 을 주고, 그러면 [DEFAULT]
         *   의 총량을 그대로 쓴다 — **모를 때 무제한으로 여는 쪽으로 기울지 않는다.**
         */
        fun forExtract(freeBytes: Long): ParseLimits {
            val total = if (freeBytes > EXTRACT_HEADROOM) freeBytes - EXTRACT_HEADROOM else DEFAULT.maxTotalOutput
            return DEFAULT.copy(
                maxTotalOutput = maxOf(total, DEFAULT.maxTotalOutput),
                // 엔트리 하나가 4 GiB 를 넘는 정상 아카이브가 있다(디스크 이미지·영상).
                maxSingleOutput = 64L * 1024 * 1024 * 1024,
            )
        }

        /** 여유 공간을 다 쓰지 않고 남겨 두는 양. 꽉 채우면 기기가 불안정해진다. */
        const val EXTRACT_HEADROOM = 64L * 1024 * 1024
    }
}

/**
 * 상한을 넘겨 읽기를 그만두었다.
 *
 * [IOException] 을 상속하는 것은 이 예외가 스트림 경계에서 자연스럽게 전파되어야 하기
 * 때문이다. 화면에 닿기 전에 `OpenFailure.TooLarge` 로 바뀐다.
 *
 * @param limitName 어느 상한인가. 사용자에게 그대로 보이지 않지만 진단에 쓴다.
 * @param detail 무엇이 얼마를 넘었는가.
 */
class ParseLimitExceededException(
    val limitName: String,
    detail: String,
) : IOException("$limitName 상한을 넘었다: $detail")
