package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.model.NameSortKey
import java.text.Collator

/**
 * 엔트리 목록을 폴더 트리로 세운다.
 *
 * ## 왜 순수 JVM 에서 만드는가
 *
 * 아카이브의 엔트리 이름은 `docs/img/그림.png` 처럼 **경로가 문자열 안에 들어 있다.**
 * 그것을 트리로 세우는 규칙에는 까다로운 구석이 여럿 있고(아래), 전부 화면 없이 시험할
 * 수 있다. 화면에서 만들면 그 규칙을 에뮬레이터로만 확인하게 된다.
 *
 * ## 까다로운 구석 넷
 *
 * 1. **디렉터리 엔트리가 없는 아카이브가 흔하다.** `a/b/c.txt` 하나만 있는 zip 에서
 *    `a` 와 `a/b` 는 아무도 적어 주지 않는다. 없으면 우리가 **만들어 넣는다**(합성 폴더).
 * 2. **같은 이름이 두 번 나올 수 있다.** ZIP 명세가 금지하지 않는다. 경로를 열쇠로 쓰면
 *    둘이 하나로 접혀 사용자가 고른 것과 **다른 파일**이 열린다. 그래서 트리의 열쇠는
 *    경로가 아니라 [Node.entryIndex] 이고, 같은 이름의 형제가 여럿 있을 수 있다.
 * 3. **풀 수 없는 이름도 보여 준다.** `../../etc/passwd` 같은 이름은 [ArchiveEntry.safeName]
 *    이 null 이다. 숨기면 사용자는 아카이브에 무엇이 들었는지 영영 모른다 — 목록에는
 *    남기고 **풀지 못한다고 표시**한다([Node.unsafe]).
 * 4. **같은 이름이 파일이자 폴더일 수 있다.** `a` 와 `a/b` 가 함께 든 아카이브가 실재한다
 *    (악의적으로 만들기도 쉽다). 둘을 합치지 않고 **형제로 나란히** 둔다.
 */
class ArchiveTree private constructor(val root: Node) {

    /**
     * 트리의 한 칸.
     *
     * [entryIndex] 가 -1 이면 **우리가 만들어 넣은 폴더**다. 아카이브에 그 엔트리가
     * 없으므로 풀기 대상 목록에 넣어서는 안 되고(만들기만 한다), 크기도 없다.
     */
    class Node(
        /** 화면에 보이는 이름. 이 칸의 마지막 조각이다. */
        val name: String,
        /** 아카이브 안의 경로. 루트는 빈 문자열. 다듬은 이름(safeName) 기준이다. */
        val path: String,
        val isDirectory: Boolean,
        /** 아카이브 엔트리 번호. -1 은 합성 폴더. */
        val entryIndex: Int,
        /** 풀 수 없는 이름인가. 목록에는 보이되 풀기에서 빠진다. */
        val unsafe: Boolean,
        /** 이름이 다듬어졌는가(절대경로·드라이브 문자를 뗐다). 화면이 원래 이름을 함께 보인다. */
        val rawName: String,
        val declaredSize: Long,
        val compressedSize: Long,
        val isEncrypted: Boolean,
        val isLink: Boolean,
        children: List<Node>,
        /** 암호 항목이지만 리더가 받은 암호로 풀 수 있다([ArchiveEntry.decryptable]). */
        val decryptable: Boolean = false,
        /** 암호를 넣으면 읽히게 된다([ArchiveEntry.needsPassword]). 화면이 이것으로 암호를 묻는다. */
        val needsPassword: Boolean = false,
    ) {
        val children: List<Node> = children

        /** 이 아래의 파일 수(폴더 제외). 폴더 한 줄에 '몇 개' 를 적는 데 쓴다. */
        val fileCount: Int = if (isDirectory) children.sumOf { it.fileCount } else 1

        /** 이 아래의 선언 크기 합. **공격자가 적는 값이므로 화면 표시 말고는 쓰지 마라.** */
        val totalDeclaredSize: Long =
            if (isDirectory) children.sumOf { it.totalDeclaredSize } else declaredSize.coerceAtLeast(0L)

        /** 이 아래의 모든 엔트리 번호. 폴더째 풀기가 이것을 쓴다. 합성 폴더는 빠진다. */
        fun collectIndices(into: MutableList<Int>) {
            if (!isDirectory && entryIndex >= 0 && !unsafe) into += entryIndex
            for (c in children) c.collectIndices(into)
        }
    }

    /** [path] 가 가리키는 폴더. 없으면 null. 빈 문자열은 루트다. */
    fun folderAt(path: String): Node? {
        if (path.isEmpty()) return root
        var cur = root
        for (seg in path.split('/')) {
            cur = cur.children.firstOrNull { it.isDirectory && it.name == seg } ?: return null
        }
        return cur
    }

    companion object {

        fun build(entries: List<ArchiveEntry>, collator: Collator = NameSortKey.koreanCollator()): ArchiveTree {
            val root = Builder("", "")
            val loose = ArrayList<ArchiveEntry>()

            for (e in entries) {
                val safe = e.safeName
                if (safe == null) {
                    // 맨 위 폴더 자신(`./`)은 위험한 이름이 아니다 — 루트가 곧 그것이다. 목록에 경고로 세우지 않는다.
                    if (e.isRootDirectory) continue
                    // 풀 수 없는 이름. 경로로 쪼갤 수 없으므로 루트에 그대로 둔다 —
                    // 이름 안의 `..` 을 폴더로 해석하면 그 순간 우리가 탈출을 재현한다.
                    loose += e
                    continue
                }
                val segs = safe.split('/')
                var cur = root
                // 마지막 조각 앞까지는 폴더다. 없으면 만든다.
                for (i in 0 until segs.size - 1) {
                    cur = cur.dir(segs[i])
                }
                val last = segs.last()
                if (e.isDirectory) {
                    cur.dir(last).claim(e)
                } else {
                    cur.files += e to last
                }
            }

            return ArchiveTree(root.toNode(loose, collator))
        }

        /** 만드는 동안만 쓰는 가변 폴더. */
        private class Builder(val name: String, val path: String) {
            val dirs = LinkedHashMap<String, Builder>()
            val files = ArrayList<Pair<ArchiveEntry, String>>()

            /** 이 폴더가 실제 엔트리이기도 한가. -1 이면 합성한 것이다. */
            var entryIndex = -1
            var rawName = name

            fun dir(seg: String): Builder =
                dirs.getOrPut(seg) { Builder(seg, if (path.isEmpty()) seg else "$path/$seg") }

            fun claim(e: ArchiveEntry) {
                // 같은 폴더 엔트리가 두 번 나오면 **처음 것을 신원으로 삼는다.** 뒤엣것을
                // 덮어쓰면 화면의 번호와 리더의 번호가 어긋난다.
                if (entryIndex < 0) {
                    entryIndex = e.index
                    rawName = e.name
                }
            }

            fun toNode(loose: List<ArchiveEntry>, collator: Collator): Node {
                val childDirs = dirs.values.map { it.toNode(emptyList(), collator) }
                val childFiles = files.map { (e, leaf) ->
                    Node(
                        name = leaf,
                        path = if (path.isEmpty()) leaf else "$path/$leaf",
                        isDirectory = false,
                        entryIndex = e.index,
                        unsafe = false,
                        rawName = e.name,
                        declaredSize = e.declaredSize,
                        compressedSize = e.compressedSize,
                        isEncrypted = e.isEncrypted,
                        isLink = e.isLink,
                        children = emptyList(),
                        decryptable = e.decryptable,
                        needsPassword = e.needsPassword,
                    )
                }
                val looseNodes = loose.map { e ->
                    Node(
                        name = e.name,
                        path = "",
                        isDirectory = false,
                        entryIndex = e.index,
                        unsafe = true,
                        rawName = e.name,
                        declaredSize = e.declaredSize,
                        compressedSize = e.compressedSize,
                        isEncrypted = e.isEncrypted,
                        isLink = e.isLink,
                        children = emptyList(),
                        decryptable = e.decryptable,
                        needsPassword = e.needsPassword,
                    )
                }
                val all = (childDirs + childFiles + looseNodes).sortedWith(nodeOrder(collator))
                return Node(
                    name = name,
                    path = path,
                    isDirectory = true,
                    entryIndex = entryIndex,
                    unsafe = false,
                    rawName = rawName,
                    declaredSize = 0L,
                    compressedSize = 0L,
                    isEncrypted = false,
                    isLink = false,
                    children = all,
                )
            }
        }

        /**
         * 폴더 먼저, 그 다음 한국어 자연 정렬.
         *
         * 목록 화면과 **같은 규칙**이어야 한다. 아카이브 안에 들어갔다고 정렬이 달라지면
         * 사용자는 자기가 다른 앱에 들어온 줄 안다.
         */
        private fun nodeOrder(collator: Collator): Comparator<Node> =
            compareByDescending<Node> { it.isDirectory }
                .thenBy { NameSortKey.of(it.name, collator) }
                .thenBy { it.entryIndex }
    }
}
