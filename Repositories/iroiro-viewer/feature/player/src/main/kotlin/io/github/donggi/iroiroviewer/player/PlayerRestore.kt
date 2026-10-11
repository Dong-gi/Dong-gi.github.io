package io.github.donggi.iroiroviewer.player

import android.os.Bundle
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.playback.FolderQueue
import io.github.donggi.iroiroviewer.playback.SubtitleNames
import java.io.File

/**
 * 프로세스가 죽은 뒤 되살아난 재생 화면이 **큐를 다시 세울 거리**(기기에서 잡았다).
 *
 * 재생은 서비스에 있고 서비스는 프로세스와 함께 죽는다. 재생하던 앱이 죽은 뒤(충돌·시스템의 정리) 안드로이드가 재생 화면을 저장
 * 상태로 되살리면 화면은 서는데 큐가 없어 '0:00 / 0:00' 에 제목도 없고 재생 단추가 듣지 않았다. 그래서 화면이 저장 상태에 **큐의 파일
 * 경로와 지금 자리·위치**를 적어 두고, 되살아나 보니 플레이어가 비어 있으면 그것으로 다시 세운다(`PlaybackConnection.restore` — 멈춘 채).
 *
 * ## 왜 이어보기 기록이나 media3 의 재개 장치가 아닌가
 *
 * 둘 다 **디스크에 남는 '무엇을 봤는가' 의 목록**이 필요하다 — 이어보기 기록은 일부러 경로를 적지 않고(키는 해시, `PositionSaver`),
 * media3 의 재개(`onPlaybackResumption`)는 마지막 큐를 앱이 파일로 들고 있어야 한다. 저장 상태는 시스템이 **그 태스크가 사는 동안
 * 메모리에만** 든다(최근 앱의 태스크를 디스크에 남기는 것은 `persistableMode` 를 켠 액티비티의 `PersistableBundle` 뿐이다) — 기록을 새로
 * 만들지 않고 되살리는 길이다.
 *
 * ## 크기
 *
 * 저장 상태는 바인더 한 번에 실려 간다 — 500항목의 경로(수십 KB)는 그 한도(1 MB, 프로세스가 나눠 쓴다)를 넉넉히 쓰는 일이다. 지금
 * 항목을 앞쪽 1/4 쯤에 두고 [MAX_ITEMS] 개만 적는다([FolderQueue.plan] 이 상한을 넘을 때 자르는 모양과 같다).
 */
internal object PlayerRestore {

    /** 저장 상태에 싣는 큐 — 기기 파일의 경로들, 그 안의 지금 자리, 위치(ms). */
    data class Snapshot(val paths: List<String>, val index: Int, val positionMs: Long) {
        /** 지금 항목의 폴더. 되살리지 못했을 때 파일 목록이 열 곳이다. */
        val folder: String? get() = paths.getOrNull(index)?.let { File(it).parent }
    }

    /** 다시 세울 큐와 자막 후보. */
    data class Restored(val plan: FolderQueue.Plan, val subtitles: List<FileEntry>)

    /** 저장 상태에 적는 항목 수의 상한(위 '크기'). */
    const val MAX_ITEMS = 100

    /**
     * 큐 → 저장할 거리. [MAX_ITEMS] 를 넘으면 지금 항목이 앞쪽 1/4 쯤에 오게 자른다 — 앞에서 자르면 폴더 뒤쪽을 듣던 사람의 지금 항목이
     * 빠진다(`FolderQueue.plan` 의 주석과 같은 까닭). [index] 가 범위 밖이면 null.
     */
    fun snapshotOf(paths: List<String>, index: Int, positionMs: Long, max: Int = MAX_ITEMS): Snapshot? {
        if (index !in paths.indices || max <= 0) return null
        val position = positionMs.coerceAtLeast(0)
        if (paths.size <= max) return Snapshot(paths.toList(), index, position)
        val from = (index - max / 4).coerceAtLeast(0).coerceAtMost(paths.size - max)
        // `subList` 를 그대로 내보내지 않는다 — 원본을 들여다보는 뷰다(`FolderQueue.plan` 과 같다).
        return Snapshot(ArrayList(paths.subList(from, from + max)), index - from, position)
    }

    fun write(out: Bundle, s: Snapshot) {
        out.putStringArrayList(KEY_PATHS, ArrayList(s.paths))
        out.putInt(KEY_INDEX, s.index)
        out.putLong(KEY_POSITION, s.positionMs)
    }

    /** 적어 둔 것이 없거나 망가졌으면 null. 저장 상태는 우리가 쓴 것이지만 자리가 범위 안인지는 한 번 더 본다. */
    fun read(b: Bundle): Snapshot? {
        val paths = b.getStringArrayList(KEY_PATHS) ?: return null
        val index = b.getInt(KEY_INDEX, -1)
        if (index !in paths.indices) return null
        return Snapshot(paths, index, b.getLong(KEY_POSITION, 0L).coerceAtLeast(0))
    }

    /**
     * 적어 둔 경로로 큐를 다시 만든다. **디스크를 본다** — 입출력 디스패처에서 부른다.
     *
     * 지금 항목이 사라졌으면 null 이다 — 다른 곡부터 틀어 주면 '하던 것을 돌려받는다' 가 아니게 된다(화면이 알리고 파일 목록으로 간다).
     * 그 밖에 사라진 항목은 빼고 세운다. 항목의 열쇠(`FileKey` — 이름·크기·수정 시각)는 다시 재므로, 파일이 그대로면 이어보기 기록과
     * 그대로 맞는다. 자막 후보는 지금 항목의 폴더에서 다시 고른다(처음 틀 때 목록에서 고른 것과 같은 규칙 — [SubtitleNames.isSubtitle]).
     */
    fun load(s: Snapshot): Restored? {
        val current = s.paths.getOrNull(s.index) ?: return null
        val entries = s.paths.mapNotNull { entryOf(File(it)) }
        val start = entries.indexOfFirst { it.path == current }
        if (start < 0) return null
        val subtitles = File(current).parentFile?.listFiles()
            ?.filter { SubtitleNames.isSubtitle(it.name) }
            ?.mapNotNull(::entryOf)
            .orEmpty()
        return Restored(FolderQueue.Plan(entries, start), subtitles)
    }

    /** 파일 하나의 목록 줄. 파일이 아니면(사라졌다·폴더다) null. */
    private fun entryOf(f: File): FileEntry? {
        if (!f.isFile) return null
        return FileEntry(
            name = f.name,
            path = f.path,
            isDirectory = false,
            size = f.length(),
            lastModified = f.lastModified(),
            isHidden = f.name.startsWith("."),
            isSymlink = false,
            kind = MimeResolver.kindOf(f.name, isDirectory = false),
        )
    }

    private const val KEY_PATHS = "player.restore.paths"
    private const val KEY_INDEX = "player.restore.index"
    private const val KEY_POSITION = "player.restore.position"
}
