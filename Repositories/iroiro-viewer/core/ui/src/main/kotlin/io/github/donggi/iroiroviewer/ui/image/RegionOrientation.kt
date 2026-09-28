package io.github.donggi.iroiroviewer.ui.image

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 사진 한 장의 선명화 조각을 **어느 방향으로 돌려 얹을 것인가.** 한 장에 한 번만 잰다.
 *
 * 치수로 답이 난 파일(정방향, 정사각이 아닌 90°·270°)은 곧바로 답하고, 나머지(180°·거울상·정사각)는
 * 확대해서 조각이 **처음 필요해질 때** 화소로 잰다(`ImageIo.appliedOrientationByPixels`). 확대하지
 * 않을 사진에까지 작은 디코딩 두 번을 치르지 않으려는 것이다. 6단계부터 11단계 뒤까지 그 파일들은
 * 흐린 채로 확대됐다.
 *
 * **'모른다' 도 기억한다.** 대칭인 그림은 다시 재도 같은 답이라, 확대할 때마다 재면 값만 치른다.
 * 재는 도중에 취소되면(손가락이 다시 움직였다) 기억하지 않고 다음에 다시 잰다.
 *
 * 화면의 한 장(경로·내용 판)마다 하나를 든다 — 파일이 바뀌면 답도 바뀔 수 있다.
 */
class RegionOrientation(private val path: String, private val probe: ImageProbe) {

    private val mutex = Mutex()
    private var known = false
    private var answer: Int? = null

    /** 조각에 입힐 EXIF 방향. null 이면 조각을 뜨지 않는다([RegionMath.tileOrientation]). */
    suspend fun tileOrientation(): Int? = mutex.withLock {
        if (!known) {
            answer = RegionMath.tileOrientation(
                ImageIo.appliedOrientationByPixels(path, probe),
                probe.orientation,
            )
            known = true
        }
        answer
    }
}
