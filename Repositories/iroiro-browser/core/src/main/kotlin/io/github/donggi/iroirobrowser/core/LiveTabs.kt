package io.github.donggi.iroirobrowser.core

/**
 * 살아 있는 탭의 수(앱의 설정 `SettingsStore.liveTabs` — 넘으면 가장 오래 안 본 탭을 접는다)로 고를 수 있는 값. 늘리면 메모리를 더 쓴다 — 탭들은
 * 렌더러 하나를 함께 쓰므로 메모리가 모자라 그것이 죽으면 모든 탭이 덮개('열지 못함')로 가고 고를 때 다시 연다. 지금 탭·뒤에서 재생을 잇는
 * 탭·녹화·가로채기 중인 탭은 수와 상관없이 접지 않는다. 설정 백업([io.github.donggi.iroirobrowser.core.backup.BackupRules.liveTabs])도 이
 * 범위로 거른다.
 */
object LiveTabs {
    const val DEFAULT = 5
    val CHOICES = listOf(1, 2, 3, 5, 8, 10, 15, 20)

    /** 고를 수 있는 범위 안으로 — 범위 밖의 저장값(옛 판·손으로 고친 설정)도 그 끝으로. */
    fun clamp(n: Int): Int = n.coerceIn(CHOICES.first(), CHOICES.last())
}
