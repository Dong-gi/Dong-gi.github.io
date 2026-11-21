package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/**
 * 폴더 정보 — 폴더 안의 파일·폴더 수와 크기(정보 시트).
 *
 * 훑기는 ZIP 만들기와 같은 [TreeWalk] 다 — 링크를 따라가지 않고(링크 너머의 것은 세지 않는다), 같은 (장치, inode) 를 두 번 세지 않고
 * (하드링크로 두 이름을 가진 파일은 한 번), 깊이 64 까지. **크기는 파일 길이의 합**이다 — 디스크에서 차지하는 블록이 아니다(탐색기·
 * 다른 파일 관리자의 '크기' 와 같다). 폴더 자체는 세지 않는다(그 안의 것만).
 *
 * **숨김은 목록과 같은 규칙이다** — 숨김 파일 보기가 꺼져 있으면 점으로 시작하는 이름을 세지 않는다(폴더째 — 크기도 그 기준). 목록에 보이는
 * 수와 맞게 한다(`DirectoryLister` 의 숨김이 이름의 점 하나다). 고른 폴더 자체에는 걸지 않는다.
 *
 * 읽지 못한 것(`Android/data` 처럼 들어갈 수 없는 폴더, 그 사이 지워진 항목)과 깊이 상한을 넘은 것이 있으면 [Count.partial] 이다 —
 * 화면이 '일부 세지 못함' 을 함께 보인다. 수를 지어내지 않는다.
 *
 * 내부 저장소 통째처럼 큰 폴더는 수 초가 걸린다. 그래서 흐름으로 낸다 — 세는 동안 [PROGRESS_NANOS] 마다 중간값을 내고 끝에 [Count.done]
 * 을 낸다. 모으는 쪽이 사라지면(시트를 닫았다) 취소가 닿아 곧바로 멈춘다(항목마다 본다). 화면을 멈추지 않는다 — 입출력 디스패처에서 돈다.
 */
object FolderStats {

    /** 센 값. [bytes] 는 파일 길이의 합. */
    data class Count(
        val files: Long,
        val folders: Long,
        val bytes: Long,
        /** 읽지 못했거나 깊이 상한을 넘은 것이 있었다. */
        val partial: Boolean,
        /** 다 셌다. 거짓이면 세는 중의 중간값이다. */
        val done: Boolean,
    )

    /** 중간값을 내는 간격. */
    private const val PROGRESS_NANOS = 150_000_000L

    /**
     * [path] 아래를 센다. 끝의 값은 [Count.done] 이 참이다. 앞의 값은 모으는 쪽이 늦으면 건너뛴다(가장 새 것만).
     *
     * @param showHidden 목록의 '숨김 파일 보기'. 꺼져 있으면 점으로 시작하는 이름을 세지 않는다.
     */
    fun count(path: String, showHidden: Boolean): Flow<Count> = channelFlow {
        val last = tally(path, PosixTree, System::nanoTime, hiddenRule(showHidden)) { trySend(it) }
        send(last)
    }.conflate().flowOn(IroDispatchers.io)

    /**
     * 센다 — 파일시스템과 시계를 받아 JVM 시험이 가짜 나무로 지킨다(`TreeWalkTest`). [onProgress] 는 세는 동안 시계가 [PROGRESS_NANOS]
     * 지날 때마다 불린다(끝의 값은 돌려주는 것이다).
     */
    internal suspend fun tally(
        path: String,
        fs: TreeWalk.Fs,
        nanoTime: () -> Long,
        exclude: ((String) -> Boolean)? = null,
        onProgress: (Count) -> Unit,
    ): Count {
        var files = 0L
        var folders = 0L
        var bytes = 0L
        var partial = false
        var lastReport = nanoTime()
        fun report() {
            val now = nanoTime()
            if (now - lastReport < PROGRESS_NANOS) return
            lastReport = now
            onProgress(Count(files, folders, bytes, partial, done = false))
        }
        val visitor = object : TreeWalk.Visitor {
            override fun entry(path: String, relative: String, stat: TreeWalk.Stat, depth: Int): Boolean {
                // 고른 폴더 자체(깊이 0)는 세지 않는다. 고른 것이 파일이면(그사이 폴더가 파일로 바뀌었다) 그 하나를 센다.
                if (stat.type == TreeWalk.Type.DIRECTORY) {
                    if (depth > 0) folders++
                } else {
                    files++
                    bytes += stat.size
                }
                report()
                return true
            }

            override fun skipped(why: TreeWalk.Skip) {
                // 링크·장치·두 번째 이름·숨김은 '세지 않을 것' 이다 — 빠뜨린 것이 아니다. 깊이 상한은 그 아래를 못 본 것이다.
                if (why == TreeWalk.Skip.DEPTH) partial = true
            }

            override fun unreadable() {
                partial = true
            }
        }
        TreeWalk.walk(listOf(path), fs, visitor, exclude = exclude)
        return Count(files, folders, bytes, partial, done = true)
    }

    /** 목록의 숨김 규칙 — 보기가 꺼져 있으면 점으로 시작하는 이름을 거른다(`DirectoryLister` 와 같다). */
    internal fun hiddenRule(showHidden: Boolean): ((String) -> Boolean)? =
        if (showHidden) null else { name -> name.startsWith('.') }
}
