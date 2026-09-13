package io.github.donggi.iroiroviewer.playback

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * 재생에 필요한데 **아이콘 코어 세트에 없는** 것들.
 *
 * 코어에 있는 것은 48개뿐이고(`Add`·`PlayArrow`·`Close` 등) 재생에 필요한
 * `Pause`·`SkipNext`·`SkipPrevious`·`Shuffle`·`Repeat` 는 하나도 없다. 그렇다고
 * `material-icons-extended`(수천 개, APK 를 크게 불린다)를 들이지는 않는다 —
 * 5단계가 `Pause` 를 손으로 그린 이유가 그것이고 여기서도 같다.
 *
 * 모양은 Material 의 24×24 격자를 따른다. 색은 칠하는 쪽이 `tint` 로 준다.
 */
private fun icon(name: String, path: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = PathData(path), fill = SolidColor(Color.Black))
    }.build()

/** ▶| 다음 곡. */
val SkipNextIcon: ImageVector by lazy {
    icon("SkipNext") {
        moveTo(6f, 6f); lineTo(15f, 12f); lineTo(6f, 18f); close()
        moveTo(16f, 6f); lineTo(19f, 6f); lineTo(19f, 18f); lineTo(16f, 18f); close()
    }
}

/** |◀ 이전 곡. */
val SkipPreviousIcon: ImageVector by lazy {
    icon("SkipPrevious") {
        moveTo(18f, 6f); lineTo(18f, 18f); lineTo(9f, 12f); close()
        moveTo(5f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(5f, 18f); close()
    }
}

/**
 * 섞기. 엇갈린 화살표 둘.
 *
 * 켜고 끄는 상태는 **색으로** 말한다(`tint`) — 아이콘을 둘로 나누면 꺼진 모양이 무엇을
 * 뜻하는지 아무도 모른다.
 */
val ShuffleIcon: ImageVector by lazy {
    icon("Shuffle") {
        // 위 화살표: 왼쪽에서 들어와 오른쪽 위로 나간다.
        moveTo(3f, 7f); lineTo(7.5f, 7f); lineTo(11f, 11f); lineTo(9f, 13f); lineTo(6.5f, 10f)
        lineTo(3f, 10f); close()
        moveTo(13f, 9f); lineTo(15.5f, 7f); lineTo(18f, 7f); lineTo(18f, 4.5f); lineTo(22f, 8.5f)
        lineTo(18f, 12.5f); lineTo(18f, 10f); lineTo(16.5f, 10f); close()
        // 아래 화살표: 왼쪽에서 들어와 오른쪽 아래로 나간다.
        moveTo(3f, 14f); lineTo(6.5f, 14f); lineTo(9f, 11f); lineTo(11f, 13f); lineTo(7.5f, 17f)
        lineTo(3f, 17f); close()
        moveTo(13f, 15f); lineTo(16.5f, 14f); lineTo(18f, 14f); lineTo(18f, 11.5f); lineTo(22f, 15.5f)
        lineTo(18f, 19.5f); lineTo(18f, 17f); lineTo(15.5f, 17f); close()
    }
}

/** 전체 반복. 네모난 순환 화살표. */
val RepeatIcon: ImageVector by lazy {
    icon("Repeat") {
        moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 4f); lineTo(22f, 8.5f); lineTo(17f, 13f)
        lineTo(17f, 10f); lineTo(9f, 10f); lineTo(9f, 13f); lineTo(6f, 13f); lineTo(6f, 8f); close()
        moveTo(17f, 17f); lineTo(7f, 17f); lineTo(7f, 20f); lineTo(2f, 15.5f); lineTo(7f, 11f)
        lineTo(7f, 14f); lineTo(15f, 14f); lineTo(15f, 11f); lineTo(18f, 11f); lineTo(18f, 16f); close()
    }
}

/**
 * 한 곡 반복.
 *
 * **전체 반복과 한눈에 갈려야 한다.** 처음에는 같은 화살표에 작은 '1' 을 얹었는데, 24dp
 * 아이콘 안의 5px 짜리 숫자는 나란히 놓고 봐도 구별되지 않았다(사용자가 지적했다).
 * 그래서 **화살표를 위아래 두 도막으로 줄이고 가운데를 비워, 그 자리에 큰 '1'** 을 넣는다.
 * 획이 굵고 높이가 아이콘의 절반이라 곁눈으로도 읽힌다.
 */
val RepeatOneIcon: ImageVector by lazy {
    icon("RepeatOne") {
        // 위 팔 — 가로 막대에 오른쪽 화살촉. **가운데를 잇는 세로 토막을 그리지 않는다.**
        moveTo(4f, 6.2f); lineTo(16f, 6.2f); lineTo(16f, 3.4f); lineTo(21.5f, 7.6f)
        lineTo(16f, 11.8f); lineTo(16f, 9f); lineTo(4f, 9f); close()
        // 아래 팔 — 가로 막대에 왼쪽 화살촉.
        moveTo(20f, 15.2f); lineTo(8f, 15.2f); lineTo(8f, 12.4f); lineTo(2.5f, 16.6f)
        lineTo(8f, 20.8f); lineTo(8f, 18f); lineTo(20f, 18f); close()
        // 비워 둔 가운데의 '1'. 두 팔 사이(y 9~15)를 꽉 채워 곁눈으로도 읽힌다.
        moveTo(10.2f, 11.2f); lineTo(12.1f, 9.4f); lineTo(13.7f, 9.4f); lineTo(13.7f, 14.8f)
        lineTo(11.9f, 14.8f); lineTo(11.9f, 11.6f); lineTo(10.9f, 12.3f); close()
    }
}

/** 재생목록. 줄 셋에 ▶ 를 얹은 Material 의 `playlist_play` 모양. */
val PlaylistIcon: ImageVector by lazy {
    icon("Playlist") {
        moveTo(3f, 6f); lineTo(17f, 6f); lineTo(17f, 8f); lineTo(3f, 8f); close()
        moveTo(3f, 10f); lineTo(17f, 10f); lineTo(17f, 12f); lineTo(3f, 12f); close()
        moveTo(3f, 14f); lineTo(13f, 14f); lineTo(13f, 16f); lineTo(3f, 16f); close()
        moveTo(15f, 13f); lineTo(21f, 16.5f); lineTo(15f, 20f); close()
    }
}

/**
 * 지금 이 줄이 재생 중이라는 표시. 높낮이가 다른 막대 셋.
 *
 * 글씨를 굵게 하는 것만으로는 **줄이 길어 잘렸을 때** 구분이 안 된다 — 목록의 제목은
 * 대개 끝이 잘린다. 왼쪽 아이콘 자리에서 갈라 놓는 것이 VLC 를 비롯한 관행이다.
 */
val NowPlayingIcon: ImageVector by lazy {
    icon("NowPlaying") {
        moveTo(4f, 13f); lineTo(8f, 13f); lineTo(8f, 20f); lineTo(4f, 20f); close()
        moveTo(10f, 6f); lineTo(14f, 6f); lineTo(14f, 20f); lineTo(10f, 20f); close()
        moveTo(16f, 16f); lineTo(20f, 16f); lineTo(20f, 20f); lineTo(16f, 20f); close()
    }
}

/**
 * 캠코더. 재생목록에서 아직 재생하지 않은 **영상** 줄.
 *
 * 음표([TrackIcon])와 한눈에 갈려야 해서 굴곡이 아니라 **각진 덩어리**로 그렸다 —
 * 20dp 로 줄여 그리면 곡선의 차이는 사라지고 실루엣만 남는다. 몸통과 렌즈가 x=16 에서
 * 변을 맞대고 있어 둘이 한 물건으로 읽힌다(떼어 놓으면 네모와 세모 둘로 보인다).
 *
 * 필름 릴(구멍 뚫린 띠)이 더 흔한 모양이지만 **구멍은 이 헬퍼로 그릴 수 없다** —
 * [icon] 은 채우기 하나뿐이고 구멍을 뚫으려면 감기 방향을 뒤집거나 even-odd 를 써야 한다.
 */
val VideoIcon: ImageVector by lazy {
    icon("Video") {
        // 몸통
        moveTo(3f, 6f); lineTo(16f, 6f); lineTo(16f, 18f); lineTo(3f, 18f); close()
        // 렌즈
        moveTo(16f, 10.5f); lineTo(21f, 6.5f); lineTo(21f, 17.5f); lineTo(16f, 13.5f); close()
    }
}

/** 음표. 재생목록에서 아직 재생하지 않은 **소리** 줄. */
val TrackIcon: ImageVector by lazy {
    icon("Track") {
        moveTo(10f, 4f); lineTo(18f, 4f); lineTo(18f, 7f); lineTo(12.5f, 7f); lineTo(12.5f, 16f)
        lineTo(10f, 16f); close()
        moveTo(6.5f, 14f)
        curveTo(8.5f, 14f, 10f, 15.3f, 10f, 17f)
        curveTo(10f, 18.7f, 8.5f, 20f, 6.5f, 20f)
        curveTo(4.5f, 20f, 3f, 18.7f, 3f, 17f)
        curveTo(3f, 15.3f, 4.5f, 14f, 6.5f, 14f)
        close()
    }
}
