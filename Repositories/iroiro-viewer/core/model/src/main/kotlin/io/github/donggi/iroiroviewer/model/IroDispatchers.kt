package io.github.donggi.iroiroviewer.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * 어떤 일을 어느 디스패처에서 하는가. **한곳에 모아 둔다.**
 *
 * `Dispatchers.Default` 를 아무 데서나 쓰면 파싱 여섯 개가 동시에 돌면서 서로의 캐시를
 * 밀어내고, 그 사이 썸네일은 한 장도 못 그린다. 용도별로 병렬도를 나눠 두면 그런 일이
 * 구조적으로 생기지 않는다.
 *
 * **`core:model` 에 두는 이유**는 이 정책을 쓰는 곳이 `core:io` 만이 아니기 때문이다.
 * 썸네일은 `core:ui` 가, 파싱은 `format:*` 이 돈다. 모듈마다 자기 `limitedParallelism`
 * 을 만들면 "한곳에 모아 둔다" 가 곧바로 깨진다.
 *
 * 값의 근거:
 * - [parsing] 2 — 파서는 메모리를 많이 쓴다. 동시에 여럿 돌면 OOM 위험이 곱해진다.
 * - [thumbnail] 3 — 화면 하나에 보이는 썸네일 수 대비. 더 늘려도 디코더가 병목이다.
 * - [image] 1 — **뷰어의 전체 화면 디코딩.** 스레드 안전성 때문이 아니라 **정점 메모리**
 *   때문이다. `setTargetSampleSize` 로 줄여 떠도 디코더는 한 단계 큰 임시본을 만들고,
 *   EXIF 회전이 붙은 사진은 회전 버퍼가 하나 더 생긴다. 동시에 둘이면 그 임시본이 둘이다.
 *   바닥층과 상세층을 한 줄에 세워 정점과 총량을 동시에 묶는다.
 * - [videoFrame] 1 — 동영상 첫 장면 추출. **[Dispatchers.IO] 의 뷰라야 한다** — 프레임
 *   추출은 FUSE 위의 블로킹 호출이고, 그것을 Default 에 얹으면 파싱·PDF 렌더가 함께 굶는다.
 * - [pdfRender] 1 — PdfRenderer 는 스레드 안전하지 않다. 하나로 직렬화하는 것이 유일한 방법이다.
 * - 디스크 입출력은 [Dispatchers.IO] 그대로. 블로킹 호출이라 스레드를 늘리는 것이 맞다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object IroDispatchers {

    val io: CoroutineDispatcher get() = Dispatchers.IO

    val parsing: CoroutineDispatcher by lazy { Dispatchers.Default.limitedParallelism(2) }

    val thumbnail: CoroutineDispatcher by lazy { Dispatchers.Default.limitedParallelism(3) }

    val pdfRender: CoroutineDispatcher by lazy { Dispatchers.Default.limitedParallelism(1) }

    val image: CoroutineDispatcher by lazy { Dispatchers.Default.limitedParallelism(1) }

    val videoFrame: CoroutineDispatcher by lazy { Dispatchers.IO.limitedParallelism(1) }
}
