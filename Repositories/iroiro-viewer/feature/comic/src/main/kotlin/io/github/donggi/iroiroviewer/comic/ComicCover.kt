package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.ui.CoverBytes

/**
 * 만화책 목록의 **표지**를 만든다. 8단계가 '목록 썸네일' 로 미뤄 둔 것이다.
 *
 * ## 왜 미뤘고 왜 지금 되는가
 *
 * 8단계의 이유는 "solid 아카이브에서 격자 12장은 해제를 12번 되풀이하는 일" 이었다.
 * 지금은 둘이 달라졌다.
 *
 * 1. 표지 만들기가 [io.github.donggi.iroiroviewer.ui.CoverSupport] 의 관문으로
 *    **한 줄로 선다.** 7z 리더 셋이 동시에 열려 사전 메모리가 세 배가 되는 일이 없다.
 * 2. 창이 차면 패스를 **그 자리에서 끝낸다**(`SolidComicSource.StopPass`). 표지 한 장을
 *    얻으려고 300쪽을 전부 푸는 일이 없다.
 *
 * 그리고 결과는 `ThumbnailStore` 가 디스크에 남기므로 **책마다 한 번**이다.
 *
 * ## 상한을 따로 좁힌다
 *
 * 표지는 '정상적인 만화 한 쪽' 이다. 기본 상한(엔트리 하나 256 MiB)은 사용자가 직접
 * 시킨 풀기를 위한 값이라 여기서는 지나치게 넉넉하다 — 목록을 스크롤하는 동안
 * 조용히 도는 작업에는 **거절이 이른 편이 낫다.**
 */
object ComicCover : CoverBytes {

    /**
     * 표지를 만들 때 쓰는 상한.
     *
     * - 엔트리 하나 **8 MiB**: 정상 만화 쪽은 몇 MB다. 넘으면 표지를 포기하고 종류
     *   배지로 남긴다 — 목록 한 칸을 위해 더 풀 이유가 없다.
     * - 총량 **64 MiB**: solid 아카이브에서 표지에 닿기까지 푸는 양의 상한이다.
     *   표지가 아카이브 뒤쪽에 있는 이상한 파일에서 값이 무한정 늘지 않게 한다.
     * - 시간 **10초**: 목록을 스크롤하는 동안 도는 작업이다. 기본 30초는 너무 길다.
     */
    private val LIMITS = ParseLimits.DEFAULT.copy(
        maxSingleOutput = 8L * 1024 * 1024,
        maxTotalOutput = 64L * 1024 * 1024,
        openTimeoutMs = 10_000,
    )

    /**
     * 창을 **1바이트**로 준다.
     *
     * 요청한 쪽은 상한과 무관하게 담기므로(`WindowSink.required`) 표지 한 장만 들어오고,
     * 담는 순간 패스가 끝난다. 표지 말고는 힙에 아무것도 남지 않는다.
     */
    private const val COVER_WINDOW = 1L

    override suspend fun cover(path: String): ByteArray? {
        // 목록을 스크롤하면 이 작업은 쉴 새 없이 취소된다. 취소가 열린 리더를 잃어버리지
        // 않도록 **만든 자리에서** 받아 두고 `finally` 에서 닫는다 — 7z 리더 하나가
        // 사전 메모리를 쥔 채 GC 를 기다리는 것을 관문(`CoverSupport.gate`)이 막아 주지
        // 못한다. 자세한 것은 `ComicOpen.openArchive` 의 주석.
        var opened: ComicSource? = null
        try {
            val result = ComicOpen.open(path, COVER_WINDOW, LIMITS) { opened = it }
            return when (result) {
                is ComicOpen.Result.Failed -> null
                is ComicOpen.Result.Ready -> result.source.bytes(0)
            }
        } finally {
            opened?.let { runCatching { it.close() } }
        }
    }
}
