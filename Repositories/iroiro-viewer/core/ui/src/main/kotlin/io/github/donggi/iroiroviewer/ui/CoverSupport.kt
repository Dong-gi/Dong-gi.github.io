package io.github.donggi.iroiroviewer.ui

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 만화책의 **표지 바이트**를 얻는 한 함수짜리 이음매.
 *
 * ## 왜 인터페이스가 한 겹 더 필요한가
 *
 * 목록의 썸네일은 [ThumbnailStore] 가 만드는데 그것은 `core:ui` 에 있고, 아카이브를
 * 읽는 코드는 `format:archive` 에 있다. `core:ui` 가 `format:*` 을 보게 하면
 * **계층이 통째로 뒤집힌다**(format 이 core 를 보는 구조다). 8단계가 아카이브 풀기를
 * 파일 작업 큐에 잇느라 `ExtractSupport.Runner` 를 둔 것과 **같은 형태의 문제**이고,
 * 같은 해법을 쓴다 — 포맷을 전혀 모르는 함수 하나를 두고 구현은 `feature:comic` 이,
 * 꽂는 것은 `app` 이 한다.
 *
 * ## 왜 [gate] 가 여기 있는가
 *
 * 썸네일 디스패처는 병렬도가 3이다. 7z 표지를 세 개가 동시에 만들면 **`SevenZFile` 셋이
 * 각자 사전 메모리를 잡아** 정점이 세 배가 된다. 그 셋은 서로 다른 책이라 병합할 수도
 * 없다. 그래서 표지 만들기는 **열기부터 디코딩까지 통째로** 한 줄로 세운다 —
 * 표지는 한 번 만들면 디스크에 남으므로 이 비용은 책마다 한 번뿐이다.
 *
 * 관문을 [CoverBytes] 구현이 아니라 여기에 두는 것은, 구현이 바뀌어도 이 약속이
 * 유지되게 하려는 것이다.
 */
fun interface CoverBytes {
    /**
     * 이 만화책의 첫 쪽 바이트. 열 수 없거나 그림이 없으면 null.
     *
     * **바이트를 돌려주고 끝낸다.** 디코딩까지 맡기면 `format:*` 이 비트맵을 알게 되고,
     * 그것은 '파서는 이미지 디코딩을 하지 않는다' 는 저장소 규칙을 깨는 일이다.
     */
    suspend fun cover(path: String): ByteArray?
}

object CoverSupport {

    /** `app` 이 시작할 때 꽂는다. 꽂히지 않으면 표지를 만들지 않고 종류 배지로 남는다. */
    @Volatile
    var provider: CoverBytes? = null

    private val gate = Semaphore(1)

    /**
     * 표지 바이트를 **한 줄로 세워** 얻는다.
     *
     * 읽는 동안 리더가 살아 있으므로, 바이트를 받은 뒤에야 다음 책이 시작한다.
     */
    suspend fun cover(path: String): ByteArray? {
        val p = provider ?: return null
        return gate.withPermit { p.cover(path) }
    }
}
