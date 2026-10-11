package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.ContentSniff
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.playback.FolderQueue
import java.io.File

/**
 * 이름으로는 무엇인지 모르는 파일(`.ts`·`.mts` — MPEG-TS·AVCHD 와 TypeScript 가 확장자를 나눠 쓴다)을 목록이 어떻게 다루는가.
 *
 * 목록은 파일을 열지 않는다(`MimeResolver` 의 머리말) — 그래서 이름대로 적고(`.ts` 는 영상, `.mts` 는 기타), **목록이 선 뒤
 * 뒤에서** 앞머리를 읽어 가른다([sniff], VM 의 `streams`). 그 답을 세 곳이 쓴다:
 *
 * * **누름** — 글로 판정됐으면 텍스트 뷰어로 간다. 아직 모르면 누른 그 자리에서 본다(화면).
 * * **큐**([playable]) — 폴더를 큐로 만들 때 글로 판정된 것을 뺀다. 빼지 않으면 TypeScript 폴더의 큐가 차례로 실패한다.
 * * **▶ 단추**([hasPlayable]) — 갈리는 이름은 **영상으로 판정된 것만** 센다. 아직 모르는 것까지 세면 TypeScript 폴더에서 단추가
 *   떴다 사라진다.
 */
internal object AmbiguousStreams {

    /**
     * 한 폴더에서 새로 읽는 상한. 하나에 8 KiB(`ContentSniff.HEAD_BYTES`)라 1,000개면 8 MB 다 — 7단계가 잰 FUSE 처리량(약 73 MB/s)
     * 으로 0.1초 남짓에 파일 여는 값이 더해진다(재지 않았다). 넘는 것은 이름대로 둔다 — 누르면 그때 본다.
     */
    const val MAX_SNIFF = 1000

    /** 앞서 가른 답. 크기와 수정 시각이 그대로면 다시 읽지 않는다([sniff]). */
    data class Sniffed(val size: Long, val lastModified: Long, val stream: Boolean)

    /**
     * 목록의 갈리는 이름을 앞머리로 가른다 — 경로 → 영상 쪽인가(글이 아닌가). **읽지 못한 것은 넣지 않는다**(이름대로 둔다 — 그쪽이
     * '열 수 없다' 를 정확히 말한다). 디스크 입출력이다 — 부르는 쪽이 입출력 디스패처에서 부른다. [look] 은 시험이 끼우는 자리다.
     *
     * [cache] 가 있으면 크기·수정 시각이 같은 것은 그 답을 쓴다. **폴더 감시가 목록을 다시 세울 때마다 다시 읽지 않으려는 것이다** —
     * 조각(`.ts`)을 받는 HLS 폴더는 조각마다 목록이 다시 선다. 이번 목록에 없는 경로는 지운다 — 크기가 폴더 하나에 묶인다.
     */
    fun sniff(
        entries: List<FileEntry>,
        look: (File) -> Boolean? = ContentSniff::looksLikeStream,
        checkpoint: () -> Unit = {},
        cache: MutableMap<String, Sniffed>? = null,
    ): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        var reads = 0
        for (e in entries) {
            if (e.isDirectory || !ContentSniff.isAmbiguousStream(e.name)) continue
            val known = cache?.get(e.path)
            if (known != null && known.size == e.size && known.lastModified == e.lastModified) {
                out[e.path] = known.stream
                continue
            }
            if (reads >= MAX_SNIFF) continue
            reads++
            checkpoint()
            look(File(e.path))?.let {
                out[e.path] = it
                cache?.put(e.path, Sniffed(e.size, e.lastModified, it))
            }
        }
        cache?.keys?.retainAll(out.keys)
        return out
    }

    /** 이 줄을 글로 열어야 하는가 — 갈리는 이름이고 영상 쪽이 아니라고 판정됐다. */
    fun isText(entry: FileEntry, streams: Map<String, Boolean>): Boolean =
        ContentSniff.isAmbiguousStream(entry.name) && streams[entry.path] == false

    /** 큐에 넣을 것. 글로 판정된 갈리는 이름을 뺀다 — 아직 모르는 것은 이름대로(영상) 둔다. */
    fun playable(entries: List<FileEntry>, streams: Map<String, Boolean>): List<FileEntry> =
        entries.filterNot { isText(it, streams) }

    /**
     * ▶ 단추를 띄울까. 갈리지 않는 미디어가 있거나, 갈리는 이름 가운데 **영상으로 판정된 것**이 있다. 아직 모르는 갈리는 이름은
     * 세지 않는다 — 셌다가 판정이 오면 단추가 사라진다(TypeScript 폴더).
     */
    fun hasPlayable(entries: List<FileEntry>, streams: Map<String, Boolean>): Boolean = entries.any {
        FolderQueue.isPlayable(it) && (!ContentSniff.isAmbiguousStream(it.name) || streams[it.path] == true)
    }
}
