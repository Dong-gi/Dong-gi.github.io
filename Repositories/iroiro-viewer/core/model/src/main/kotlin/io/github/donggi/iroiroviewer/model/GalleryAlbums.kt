package io.github.donggi.iroiroviewer.model

/**
 * 갤러리의 **앨범** — 사진·영상이 든 폴더마다 한 칸.
 *
 * ## 다시 훑지 않는다
 *
 * 앨범은 갤러리 훑기(`GalleryScanner`)가 이미 내놓은 목록을 **폴더로 묶은 것**이다. 목록은 찍힌 시각(없으면 수정일)이 늦은 것부터
 * 서 있으므로 묶기는 한 번 훑는 것으로 끝나고, 앨범의 차례·표지·안의 차례가 그 차례를 그대로 물려받는다 — 앨범을 열었을 때 보이는
 * 날짜 묶음도 갤러리의 구간([DayBucket.Section])을 잘라 쓴다([subset]). 시각을 다시 셈하지 않으니 두 화면이 같은 사진을 다른 날에
 * 두는 일이 없다.
 *
 * 폴더는 **바로 위 폴더**다(`DCIM/Camera/a.jpg` 는 `DCIM/Camera`). 아래 폴더를 위 폴더에 합치지 않는다 — 다른 갤러리 앱의 관행이고,
 * 합치면 `DCIM` 하나에 카메라·스크린숏이 섞인다.
 */
object GalleryAlbums {

    /**
     * 앨범 하나.
     *
     * @param folder 폴더의 경로(열쇠).
     * @param label 화면에 보일 이름 — 폴더 이름, 같은 이름의 앨범이 또 있으면 겹치지 않을 때까지 위 폴더를 붙인다([labels]).
     * @param cover 표지 — 그 폴더에서 가장 새 것(목록의 차례로 맨 앞).
     * @param count 든 사진·영상의 수.
     */
    data class Album(val folder: String, val label: String, val cover: FileEntry, val count: Int)

    /**
     * 항목들을 앨범으로. 차례는 **가장 새 것이 새로운 앨범부터**(목록에서 먼저 나온 폴더부터).
     *
     * @param volumes 볼륨 경로 → 화면 이름(`내부 저장소`·SD 카드의 이름). 이름이 겹쳐 위 폴더까지 붙여야 할 때 볼륨의 경로 조각(`0`·
     *   `1234-ABCD`) 대신 이 이름을 쓴다([labels]).
     */
    fun of(items: List<FileEntry>, volumes: Map<String, String> = emptyMap()): List<Album> {
        val order = LinkedHashMap<String, Int>()
        val counts = HashMap<String, Int>()
        val covers = HashMap<String, FileEntry>()
        for (e in items) {
            val folder = folderOf(e.path)
            if (folder !in order) {
                order[folder] = order.size
                covers[folder] = e
            }
            counts[folder] = (counts[folder] ?: 0) + 1
        }
        val folders = order.keys.toList()
        val names = labels(folders, volumes)
        return folders.map { f -> Album(f, names.getValue(f), covers.getValue(f), counts.getValue(f)) }
    }

    /** 앨범을 열면 보일 것 — 그 폴더의 항목과 그것들의 날짜 구간. */
    data class Subset(val items: List<FileEntry>, val sections: List<DayBucket.Section>)

    /**
     * [folder] 에 든 항목과 날짜 구간을 [items]·[sections] 에서 잘라 낸다. 항목의 차례는 그대로이고, 구간은 그 날에 든 이 폴더의
     * 항목만 센다(하나도 없는 날은 빠진다).
     *
     * [sections] 는 [items] 의 자리를 빈틈 없이 차례로 덮는다고 본다(`DayBucket.sectionsOf` 의 결과). 그렇지 않은 구간은 건너뛴다.
     */
    fun subset(items: List<FileEntry>, sections: List<DayBucket.Section>, folder: String): Subset {
        val picked = ArrayList<FileEntry>()
        val out = ArrayList<DayBucket.Section>()
        for (s in sections) {
            val end = minOf(s.firstIndex + s.count, items.size)
            val first = picked.size
            for (i in s.firstIndex.coerceAtLeast(0) until end) {
                if (folderOf(items[i].path) == folder) picked += items[i]
            }
            if (picked.size > first) out += DayBucket.Section(s.day, first, picked.size - first)
        }
        return Subset(picked, out)
    }

    /** 바로 위 폴더. 빗금이 없으면 빈 글자(뿌리). */
    fun folderOf(path: String): String {
        val cut = path.lastIndexOf('/')
        return if (cut <= 0) "" else path.substring(0, cut)
    }

    /**
     * 폴더마다 화면 이름 — **겹치지 않는 가장 짧은 꼬리**. 폴더 이름(마지막 조각)이 다른 앨범과 겹치면 한 조각씩 위를 붙인다.
     * 볼륨 아래의 폴더는 볼륨의 경로를 **볼륨의 화면 이름 한 조각**으로 바꿔 센다([volumes]) — 내부 저장소와 SD 카드의 `DCIM/Camera` 는
     * `내부 저장소/DCIM/Camera`·`SD 카드/DCIM/Camera` 다(경로 조각 `0`·`1234-ABCD` 를 보이지 않는다). 조각을 다 붙여도 같으면(같은 경로 —
     * 있을 수 없다) 경로 그대로.
     */
    fun labels(folders: List<String>, volumes: Map<String, String> = emptyMap()): Map<String, String> {
        // 긴 볼륨 경로부터 견준다(한 볼륨이 다른 볼륨의 앞부분일 때 더 구체적인 것이 이긴다).
        val roots = volumes.entries.map { (k, v) -> k.trimEnd('/') to v }.sortedByDescending { it.first.length }
        val parts = folders.associateWith { f -> segmentsOf(f, roots) }
        val depth = HashMap<String, Int>()
        for (f in folders) depth[f] = 1
        // 겹치는 무리가 있는 동안 그 무리만 한 조각씩 늘린다. 늘 때마다 어느 깊이가 하나는 오르고 깊이는 조각 수를 넘지 않으므로 끝난다.
        var grew = true
        while (grew) {
            grew = false
            val byLabel = folders.groupBy { f -> tail(parts.getValue(f), depth.getValue(f)) }
            for ((_, group) in byLabel) {
                if (group.size < 2) continue
                for (f in group) {
                    if (depth.getValue(f) < parts.getValue(f).size) {
                        depth[f] = depth.getValue(f) + 1
                        grew = true
                    }
                }
            }
        }
        return folders.associateWith { f ->
            tail(parts.getValue(f), depth.getValue(f)).ifEmpty { f }
        }
    }

    private fun tail(parts: List<String>, n: Int): String = parts.takeLast(n).joinToString("/")

    /** 폴더의 조각 — 볼륨 아래면 앞을 볼륨의 이름 한 조각으로. */
    private fun segmentsOf(folder: String, roots: List<Pair<String, String>>): List<String> {
        for ((root, name) in roots) {
            if (root.isEmpty() || name.isEmpty()) continue
            if (folder == root || folder.startsWith("$root/")) {
                return listOf(name) + folder.substring(root.length).split('/').filter { it.isNotEmpty() }
            }
        }
        return folder.split('/').filter { it.isNotEmpty() }
    }
}
