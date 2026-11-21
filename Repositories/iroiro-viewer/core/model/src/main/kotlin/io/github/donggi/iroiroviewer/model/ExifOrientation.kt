package io.github.donggi.iroiroviewer.model

/**
 * EXIF 방향(1~8)의 **합성 셈**. 회전·뒤집기를 저장할 때(`FileOpEngine.reorientExif`)와 저장 전 화면이 미리 보일 때(이미지 뷰어)가
 * 같은 셈을 쓴다 — 둘이 따로 셈하면 화면에서 본 것과 저장된 것이 갈린다.
 *
 * ## 값 하나가 변환 하나다
 *
 * 방향 값 `o` 는 '저장 화소를 화면으로 옮기는 변환' 이다. 여덟 값은 직사각형의 대칭 여덟(시계 방향 90°×t 회전 넷 × 좌우 뒤집힘
 * 있음·없음)과 하나씩 맞는다. 여기서는 그것을 **o = R(t)∘M^m** — 저장 그림을 먼저 좌우로 뒤집고(m) 그다음 시계 방향으로 90°×t
 * 돌린다 — 로 푼다. 좌표로 옮긴 정의는 `core:ui` 의 `RegionMath.displayOf` 이고, 이 표가 그것과 맞는지는 `core:ui` 의 시험이 본다.
 *
 * 화면에서 하는 일(오른쪽·왼쪽 회전, 좌우·상하 뒤집기)도 같은 꼴의 변환이라 같은 값으로 적는다 — 오른쪽 회전은 6, 왼쪽 회전은 8,
 * 좌우 뒤집기는 2, 상하 뒤집기는 4. 지금 방향 `o` 에 화면의 일 `d` 를 하면 새 방향은 `d∘o`([compose]) 다.
 *
 * **거울상은 회전 방향이 거꾸로 붙는다.** M∘R(t) = R(−t)∘M 이라, 뒤집힌 사진(2·4·5·7)에 '시계 방향 90°' 를 더하면 풀어 쓴
 * 각에서는 90° 를 **빼야** 한다 — 더하면 좌우가 뒤집힌 사진이 저장된 뒤 화면에서 본 것과 180° 어긋난다.
 */
object ExifOrientation {

    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /** 화면에서 하는 일. 각각 하나의 방향 값([of])이다. */
    enum class Op { ROTATE_RIGHT, ROTATE_LEFT, FLIP_HORIZONTAL, FLIP_VERTICAL }

    // 방향 값(1~8)마다 (t, m). 0 번 칸은 쓰지 않는다.
    private val TURNS = intArrayOf(0, 0, 0, 2, 2, 3, 1, 1, 3)
    private val MIRRORED = booleanArrayOf(false, false, true, false, true, true, false, true, false)

    /** (t, m) → 방향 값. [TURNS]·[MIRRORED] 의 역이다. */
    private fun codeOf(turns: Int, mirrored: Boolean): Int {
        val t = ((turns % 4) + 4) % 4
        for (o in NORMAL..ROTATE_270) if (TURNS[o] == t && MIRRORED[o] == mirrored) return o
        error("닿지 않는다")
    }

    /**
     * 1~8 밖의 값(0 = 정의 안 됨, 태그 없음, 깨진 값)은 정방향으로 친다 — 플랫폼 디코더도 그렇게 그린다. 정방향이 아닌 것으로 치면
     * 화면에서 본 것과 저장된 것이 갈린다.
     */
    fun normalize(orientation: Int): Int = if (orientation in NORMAL..ROTATE_270) orientation else NORMAL

    /** 화면의 일 하나를 방향 값으로. */
    fun of(op: Op): Int = when (op) {
        Op.ROTATE_RIGHT -> ROTATE_90
        Op.ROTATE_LEFT -> ROTATE_270
        Op.FLIP_HORIZONTAL -> FLIP_HORIZONTAL
        Op.FLIP_VERTICAL -> FLIP_VERTICAL
    }

    /** [first] 를 한 다음 [then] 을 한 변환(then∘first). 1~8 밖의 값은 [normalize] 를 거친다. */
    fun compose(then: Int, first: Int): Int {
        val a = normalize(then)
        val b = normalize(first)
        // R(ta)∘M^ma∘R(tb)∘M^mb — ma 면 M∘R(tb) = R(−tb)∘M 로 넘긴다.
        val turns = if (MIRRORED[a]) TURNS[a] - TURNS[b] else TURNS[a] + TURNS[b]
        return codeOf(turns, MIRRORED[a] != MIRRORED[b])
    }

    /** 지금 방향 [orientation] 에 화면의 일 [op] 를 한 새 방향. */
    fun apply(orientation: Int, op: Op): Int = compose(of(op), orientation)

    /** [orientation] 을 되돌리는 변환 — `compose(inverse(o), o) == NORMAL`. 뒤집힘이 든 것은 제 자신이 역이다. */
    fun inverse(orientation: Int): Int {
        val o = normalize(orientation)
        return if (MIRRORED[o]) o else codeOf(-TURNS[o], false)
    }

    /**
     * 화면에 겹으로 입히는 꼴 — **안쪽에서 좌우로 뒤집고**([Layer.mirrored]) **바깥에서 시계 방향으로 돌린다**([Layer.degrees]).
     * 한 겹에 둘을 함께 주면 플랫폼이 어느 쪽을 먼저 하는지에 답이 달리므로, 화면은 두 겹으로 나눠 입힌다.
     */
    data class Layer(val degrees: Int, val mirrored: Boolean)

    fun layerOf(orientation: Int): Layer {
        val o = normalize(orientation)
        return Layer(TURNS[o] * 90, MIRRORED[o])
    }
}
