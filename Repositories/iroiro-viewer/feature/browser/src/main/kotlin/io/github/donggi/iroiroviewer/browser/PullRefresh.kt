package io.github.donggi.iroiroviewer.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 당겨서 새로고침(사용자 요청 — "화면을 아래로 당겼다 놓으면 새로고침").
 *
 * ## 왜 `PullToRefreshBox` 를 그대로 쓰지 않는가
 *
 * material3 1.4 의 `PullToRefreshBox` 에는 **끄는 길이 없다**(`enabled` 가 없다). 폴더 목록은 선택 모드에서 제스처를
 * 꺼야 한다 — 고르는 중에 목록이 다시 읽혀 줄이 움직이면 사용자는 다른 줄을 누른다. 그래서 그 상자가 안에서 쓰는
 * 두 조각(`Modifier.pullToRefresh` 와 `PullToRefreshDefaults.Indicator`)을 같은 모양으로 직접 잇는다.
 *
 * ## 무엇과 부딪히지 않는가
 *
 * 제스처는 **안쪽 스크롤이 쓰고 남긴 것**(nested scroll 의 `onPostScroll`)만 받는다. 그래서 목록이 맨 위에 있을
 * 때만 당겨지고, 목록을 내리는 스크롤·격자·빵부스러기의 가로 스크롤과 다투지 않는다. 안쪽이 스크롤할 수 없을
 * 만큼 짧은 목록도 된다 — 스크롤 수정자는 움직일 곳이 없어도 끌기를 중첩 스크롤로 넘긴다. **스크롤 수정자가 아예
 * 없는 화면**(빈 폴더 안내)은 넘길 것이 없어 당겨지지 않으므로 [ScrollableNote] 로 감싼다.
 *
 * ## 몸짓을 쓸 수 없으면
 *
 * 당기기는 몸짓뿐이라 화면 낭독기로는 찾을 길이 없다. 폴더 목록은 ⋮ 메뉴의 '새로고침' 이 같은 일을 한다
 * (`BrowserViewModel.pullToRefresh` — 표시도 같다). 상자에 접근성 동작을 다는 길은 쓰지 않았다 — 초점은 목록의
 * 줄에 가 있고, 그때 낭독기는 줄을 감싼 상자의 동작을 보여 주지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshableBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    Box(
        modifier.pullToRefresh(
            isRefreshing = refreshing,
            state = state,
            enabled = enabled,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 스크롤할 것이 없는 안내(빈 폴더·못 읽는 폴더)를 **당길 수 있게** 한다.
 *
 * 항목 하나짜리 `LazyColumn` 에 화면 크기를 채워 가운데 둔다. `verticalScroll` 을 쓰지 않는 것은 그 안에서
 * `fillMaxSize` 가 높이를 채우지 못해(무한한 높이를 받는다) 안내가 위로 붙기 때문이다.
 */
@Composable
internal fun ScrollableNote(content: @Composable BoxScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "note") {
            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center, content = content)
        }
    }
}
