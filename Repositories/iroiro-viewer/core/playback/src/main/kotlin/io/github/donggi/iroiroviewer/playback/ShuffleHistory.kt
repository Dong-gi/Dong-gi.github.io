package io.github.donggi.iroiroviewer.playback

/**
 * 섞어 듣는 동안 **무엇을 이미 들었는가.**
 *
 * ## 왜 media3 의 섞기를 그대로 쓰지 않는가
 *
 * `shuffleModeEnabled` 는 큐를 **한 번 섞어 둔 차례**다. 그래서 '다음' 을 누르면 언제나
 * 그 차례의 다음 것이 나온다 — 같은 자리에서 다시 눌러도 늘 같은 곡이다. 사용자가 바란
 * 것은 그것이 아니라 **누를 때마다 새로 뽑는 것**이고, 다만 방금 들은 것이 또 나오면
 * 안 된다. 그 둘을 함께 지키려면 '들은 것' 을 우리가 들고 있어야 한다.
 *
 * ## 언제 비우는가
 *
 * 이력은 메모리에만 있고 **셋 중 하나에서 비운다.**
 *
 * 1. 앱이 끝날 때 — 메모리에만 있으므로 저절로 사라진다.
 * 2. 재생목록을 닫을 때 — 목록을 닫는 것은 '이 묶음을 그만 본다' 는 뜻이다.
 * 3. 순차 재생으로 바꿀 때 — 섞기를 끄면 이력이 가리킬 대상이 없다.
 *
 * 디스크에 남기지 않는 것이 중요하다. 무엇을 들었는지는 **그 사람이 무엇을 갖고 있는지**
 * 만큼이나 사적인 값이고, 이 앱은 그런 것을 파일로 만들지 않는다(만화 쪽을 디스크에
 * 쓰지 않기로 한 것과 같은 기준이다).
 *
 * 이 클래스는 `android.*` 를 쓰지 않아 JVM 시험으로 전부 박힌다.
 */
class ShuffleHistory {

    private val played = LinkedHashSet<String>()

    fun clear() {
        played.clear()
    }

    fun remember(mediaId: String) {
        if (mediaId.isNotEmpty()) played.add(mediaId)
    }

    fun size(): Int = played.size

    /**
     * 다음에 들을 자리를 뽑는다.
     *
     * 규칙은 셋이다.
     *
     * 1. **지금 듣고 있는 것은 뽑지 않는다** — 바로 그것이 사용자가 '다음' 을 누른 이유다.
     * 2. 아직 안 들은 것 중에서 고른다.
     * 3. 다 들었으면 **이력을 비우고 다시 시작한다.** 비운 직후에도 1번은 지킨다 —
     *    한 바퀴가 끝나는 순간 같은 곡이 두 번 이어 나오면 그것이 가장 눈에 띄는 흠이다.
     *
     * @param ids 큐의 mediaId 들. 자리 번호가 곧 색인이다.
     * @param current 지금 자리. 큐 밖이면 -1.
     * @param random 뽑는 장치. 시험이 고정된 씨앗을 넣는다.
     * @return 다음 자리. 뽑을 것이 없으면 -1(항목이 하나뿐인 큐).
     */
    fun nextIndex(ids: List<String>, current: Int, random: java.util.Random): Int {
        if (ids.isEmpty()) return -1
        if (ids.size == 1) return -1

        val currentId = ids.getOrNull(current)
        var candidates = ids.indices.filter { i -> i != current && ids[i] !in played }
        if (candidates.isEmpty()) {
            // 한 바퀴 돌았다. 비우고 다시 — 다만 지금 것은 여전히 빼 둔다.
            played.clear()
            currentId?.let { played.add(it) }
            candidates = ids.indices.filter { it != current }
        }
        if (candidates.isEmpty()) return -1
        return candidates[random.nextInt(candidates.size)]
    }
}
