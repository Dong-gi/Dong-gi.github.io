package io.github.donggi.iroirobrowser.tether.core

import java.security.SecureRandom

/** 관리 기능·zip 이 실패한 까닭 — 파일 서버가 [status] 와 [message](화면에 그대로 보이는 한국어)로 답한다. */
open class OpFailure(val status: Int, message: String) : Exception(message)

/**
 * 공유 폴더의 관리 기능 — 폴더 만들기·이름 바꾸기·옮기기·복사·지우기(**숨은 휴지통으로 옮기기**)·휴지통 보기·되살리기·비우기 — 와 zip 받기의
 * 차례 짓기. [SharedTree] 의 바탕 동작(만들기·이름 바꾸기·옮기기·복사·지우기)만 쓰고, 이름 검사·겹침·자기 안으로 옮기기·상한·휴지통의 기록은
 * 여기서 한다(그래서 앱의 SAF 와 시험의 임시 폴더가 같은 규칙을 탄다).
 *
 * **경로는 늘 성분 목록**([SharedPaths.split] 이 거른 것)이고 성분마다 자식만 찾아 내려간다([SharedTree.find]). 보통의 길은 휴지통
 * ([SharedPaths.TRASH_NAME])에 닿지 못한다 — 부르는 쪽([FileServer])이 [SharedPaths.isHidden] 으로 막고, 여기서도 맨 위의 그 이름을 새 이름으로
 * 받지 않는다.
 *
 * ## 휴지통
 *
 * 맨 위의 숨은 폴더 `.web-trash` 안에, 지운 것 하나마다 칸 폴더 `<id>`(그 안에 지운 항목이 이름 그대로)와 기록 `<id>.json`(원래 이름·원래
 * 폴더의 경로·지운 때·폴더인가·크기)을 둔다. 기록을 칸 밖에 두는 것은 지운 항목의 이름이 무엇이든 겹치지 않게다. 되살리기는 기록의 자리로
 * 옮기고(그 폴더가 사라졌으면 다시 만든다, 같은 이름이 있으면 `이름 (1)`), 비우기는 칸과 기록을 정말 지운다. 올리기의 덮어쓰기가 덮는 원래
 * 파일도 같은 꼴로 여기 온다([trashReplaced] — 관리 허용과 상관없다). 다만 **0바이트 원래 파일은 휴지통에 넣지 않는다**(사용자 결정 11-21 —
 * [setAside] 로 비켜 두고 새 것을 둔 뒤 [discardAside] 가 다시 보고 지운다. 그새 커졌으면 휴지통으로).
 *
 * **새 이름**(폴더 만들기·이름 바꾸기·폴더째 올리기의 성분·겹쳐 새로 짓는 이름)은 보이지 않는 글자를 뺀 뒤에 쓰고 검사한다([newName]).
 *
 * 휴지통 안에는 빈 파일 [NO_MEDIA](`.nomedia`)를 둔다 — 폰의 갤러리(MediaStore)가 지운 사진·영상을 보이지 않게. 휴지통을 만들 때와 휴지통을
 * 손볼 때([trash]·[maintainTrash]) 없으면 더한다. 칸도 기록도 아니라 목록·되살리기에 나오지 않고, 비우기도 지우지 않는다.
 *
 * **보관 기간**([maintainTrash]): 기록의 '지운 때' 가 기간보다 오래된 칸을 정말 지운다. 기록이 없거나 읽지 못하면 지우지 않는다(데이터를
 * 잃지 않는 쪽).
 */
class SharedOps(
    private val tree: SharedTree,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val random: SecureRandom = SecureRandom(),
    /** 손으로 짓는 복사와 zip 의 항목 수 상한 — 시험이 작게 준다. */
    private val maxTreeEntries: Int = MAX_TREE_ENTRIES,
) {

    /** 옮기기·복사에서 같은 이름이 있을 때. */
    enum class Conflict {
        /** 하나라도 겹치면 아무것도 하지 않고 겹친 이름을 돌려준다(409) — 화면이 사용자에게 묻는다. */
        ASK,

        /** 겹친 것은 건너뛴다. */
        SKIP,

        /** 겹친 것은 `이름 (1)` 로. */
        RENAME,
    }

    /** 여러 항목을 다룬 결과. [done] 은 (원래 이름, 새 이름), [failed] 는 (이름, 까닭). */
    class Outcome(
        val done: List<Pair<String, String>>,
        val skipped: List<String>,
        val failed: List<Pair<String, String>>,
    )

    /** 휴지통의 항목 하나. [from] 은 원래 폴더의 경로(맨 위면 빈 글). */
    class TrashItem(val id: String, val name: String, val from: String, val deleted: Long, val isDirectory: Boolean, val size: Long)

    /** zip 에 담을 것 하나 — [path] 는 zip 안의 경로(폴더는 `/` 로 끝난다). */
    class ZipItem(val path: String, val entry: SharedEntry)

    // ── 찾기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** [segments] 의 폴더. 없거나 폴더가 아니면 404. */
    fun directory(segments: List<String>): SharedEntry {
        val found = tree.find(segments)
        if (found == null || !found.isDirectory) throw OpFailure(404, "폴더를 찾을 수 없습니다")
        return found
    }

    /** [parent] 의 자식들 — 맨 위([atRoot])면 휴지통을 뺀다. */
    fun visibleChildren(parent: SharedEntry, atRoot: Boolean): List<SharedEntry> {
        val children = tree.list(parent)
        return if (atRoot) children.filterNot { isTrashName(it.name) } else children
    }

    /** 여러 항목을 고를 때의 이름 하나 — 한 성분이고, 맨 위에서는 휴지통 이름이 아니다. 틀리면 400. */
    private fun checkMemberName(name: String, atRoot: Boolean) {
        if (!SharedPaths.isValidName(name) || (atRoot && isTrashName(name))) throw OpFailure(400, "이름이 틀렸습니다")
    }

    /**
     * 손님이 보낸 새 이름 — 보이지 않는 글자를 뺀([SharedPaths.cleanName]) 뒤의 이름을 돌려준다(쓸 이름 — 같은 이름 검사도 이것으로). 빼고 나서
     * 비거나 `.`·`..`·틀린 이름이거나 맨 위의 휴지통 이름이면 400.
     */
    fun newName(raw: String, atRoot: Boolean): String {
        val name = SharedPaths.cleanName(raw)
        if (!SharedPaths.isValidName(name)) {
            throw OpFailure(400, "쓸 수 없는 이름입니다 — 비었거나, / 나 \\ 가 있거나, 너무 깁니다")
        }
        if (atRoot && isTrashName(name)) throw OpFailure(400, TRASH_NAME_TAKEN)
        return name
    }

    private fun isTrashName(name: String) = SharedPaths.isTrashName(name)

    // ── 폴더 만들기·이름 바꾸기 ──────────────────────────────────────────────────────────────────────────────

    /** [parentSegments] 안에 폴더 [rawName]([newName] 으로 다듬는다). 같은 이름(다듬은 뒤의 이름, 대소문자 무시)이 있으면 409. */
    fun makeDirectory(parentSegments: List<String>, rawName: String): SharedEntry {
        val name = newName(rawName, parentSegments.isEmpty())
        val parent = directory(parentSegments)
        if (tree.list(parent).any { it.name.equals(name, ignoreCase = true) }) throw OpFailure(409, "같은 이름이 이미 있습니다")
        return tree.createDirectory(parent, name)
    }

    /** [makeDirectories] 의 결과 — 그 폴더와, 실제로 밟은 성분 이름들(다듬어 만든 것은 다듬은 이름). */
    class Directories(val entry: SharedEntry, val names: List<String>)

    /**
     * [segments] 의 폴더 — 없는 성분은 만들며 내려간다(폴더째 올리기·되살리기). 성분마다 **그 이름 그대로의 폴더가 있으면 그것**(이미 있는 폴더의
     * 이름은 다듬지 않는다 — 다른 앱이 지은 이름에 보이지 않는 글자가 있을 수 있다), 없으면 [newName] 으로 다듬은 이름을 찾고(대소문자 무시)
     * 그래도 없으면 그 이름으로 만든다. 맨 위 성분은 다듬기 전이든 뒤든 휴지통 이름이면 400. 가는 길에 같은 이름의 파일이 있으면 409.
     */
    fun makeDirectories(segments: List<String>): Directories {
        var current = directory(emptyList())
        val names = ArrayList<String>()
        for ((index, segment) in segments.withIndex()) {
            if (index == 0 && isTrashName(segment)) throw OpFailure(400, TRASH_NAME_TAKEN)
            val children = tree.list(current)
            val exact = children.firstOrNull { it.name == segment }
            val name = if (exact != null) segment else newName(segment, index == 0)
            val existing = exact
                ?: children.firstOrNull { it.name == name }
                ?: children.firstOrNull { it.name.equals(name, ignoreCase = true) }
            current = when {
                existing == null -> tree.createDirectory(current, name)
                existing.isDirectory -> existing
                else -> throw OpFailure(409, "폴더를 만들 자리에 같은 이름의 파일이 있습니다")
            }
            names += current.name
        }
        return Directories(current, names)
    }

    /** [segments] 의 이름을 [rawName]([newName] 으로 다듬는다)으로. 맨 위(빈 경로)는 바꾸지 않는다. 같은 폴더에 그 이름이 있으면 409. */
    fun rename(segments: List<String>, rawName: String): SharedEntry {
        if (segments.isEmpty()) throw OpFailure(400, "공유 폴더 자체의 이름은 바꿀 수 없습니다")
        val newName = newName(rawName, segments.size == 1)
        val parent = directory(segments.dropLast(1))
        val target = tree.find(segments) ?: throw OpFailure(404, "항목을 찾을 수 없습니다")
        if (target.name == newName) return target
        // 대소문자만 바꾸는 것(자기 자신)은 겹침이 아니다.
        if (tree.list(parent).any { it.ref != target.ref && it.name.equals(newName, ignoreCase = true) }) {
            throw OpFailure(409, "같은 이름이 이미 있습니다")
        }
        return tree.rename(target, newName)
    }

    // ── 옮기기·복사 ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * [baseSegments] 폴더의 [names] 를 [toSegments] 폴더로 옮기거나([copy] = false) 복사한다. **폴더를 자기 안으로**(대상이 그 폴더이거나 그 아래)
     * 옮기거나 복사하려 하면 아무것도 하지 않고 400. 같은 폴더로 옮기기는 건너뛴다(복사는 새 이름으로 — 사본). 겹치는 이름은 [conflict].
     */
    fun transfer(baseSegments: List<String>, names: List<String>, toSegments: List<String>, copy: Boolean, conflict: Conflict): Outcome {
        checkMembers(names, baseSegments.isEmpty())
        val base = directory(baseSegments)
        val target = directory(toSegments)
        for (name in names) {
            val source = baseSegments + name
            if (toSegments.size >= source.size && toSegments.subList(0, source.size) == source) {
                throw OpFailure(400, "폴더를 자기 안으로 ${if (copy) "복사할" else "옮길"} 수 없습니다")
            }
        }
        val sameFolder = toSegments == baseSegments
        val baseChildren = tree.list(base)
        val taken = tree.list(target).mapTo(ArrayList()) { it.name }

        // 먼저 겹침을 본다 — ASK 면 하나라도 겹칠 때 아무것도 하지 않는다.
        if (conflict == Conflict.ASK && !sameFolder) {
            val clashes = names.filter { name -> taken.any { it.equals(name, ignoreCase = true) } }
            if (clashes.isNotEmpty()) throw ConflictFailure(clashes)
        }

        val done = ArrayList<Pair<String, String>>()
        val skipped = ArrayList<String>()
        val failed = ArrayList<Pair<String, String>>()
        for (name in names) {
            val entry = baseChildren.firstOrNull { it.name == name }
            if (entry == null) {
                failed += name to "찾을 수 없습니다"
                continue
            }
            if (sameFolder && !copy) {
                skipped += name
                continue
            }
            val clash = taken.any { it.equals(name, ignoreCase = true) }
            if (clash && conflict == Conflict.SKIP && !sameFolder) {
                skipped += name
                continue
            }
            // 겹치지 않으면 이름 그대로 옮긴다(이미 있는 이름은 다듬지 않는다). 겹쳐 새 이름(`이름 (1)`)을 지을 때만 보이지 않는 글자를 뺀
            // 이름에서 짓는다 — 새로 짓는 이름의 규칙(사용자 결정 11-21). 빼고 나서 틀린 이름이면 원래 이름에서.
            val finalName = if (!clash) name else {
                val base = SharedPaths.cleanName(name).takeIf { SharedPaths.isValidName(it) } ?: name
                SharedPaths.freeName(base, entry.isDirectory, taken)
            }
            // 하위 폴더의 `.web-trash` 를 맨 위로 옮기거나 복사하면 그것이 휴지통 자리에 앉는다 — 맨 위의 그 이름은 새 이름으로 받지 않는다
            // (휴지통이 아직 없으면 겹침도 없어 그대로 갔다 — 2026-11-21 검토에서 고침).
            if (toSegments.isEmpty() && isTrashName(finalName)) {
                failed += name to TRASH_NAME_TAKEN
                continue
            }
            try {
                if (copy) copyEntry(entry, target, finalName) else moveEntry(entry, base, target, finalName)
                taken += finalName
                done += name to finalName
            } catch (e: OpFailure) {
                failed += name to (e.message ?: "실패")
            } catch (e: Exception) {
                failed += name to "${if (copy) "복사" else "옮기기"}에 실패했습니다"
            }
        }
        return Outcome(done, skipped, failed)
    }

    /** 겹친 이름들 — 409 와 함께 화면에 돌려준다(묻기). */
    class ConflictFailure(val names: List<String>) : Exception("같은 이름이 있습니다")

    private fun checkMembers(names: List<String>, atRoot: Boolean) {
        if (names.isEmpty()) throw OpFailure(400, "고른 항목이 없습니다")
        if (names.size > MAX_BATCH) throw OpFailure(413, "한 번에 ${MAX_BATCH}개까지 다룹니다")
        if (names.toSet().size != names.size) throw OpFailure(400, "같은 항목이 두 번 들었습니다")
        for (name in names) checkMemberName(name, atRoot)
    }

    /**
     * 옮긴다. 이름이 그대로면 제공자의 옮기기(없으면 복사 뒤 지우기). 이름을 바꿔야 하면(대상에 같은 이름) 원래 폴더에서 겹치지 않는 임시
     * 이름으로 바꾸고 → 옮기고 → 새 이름으로 바꾼다(옮기기는 이름을 고르지 못한다). 중간에 실패하면 원래 이름으로 되돌려 본다.
     */
    private fun moveEntry(entry: SharedEntry, from: SharedEntry, to: SharedEntry, finalName: String): SharedEntry {
        if (finalName == entry.name) return moveSameName(entry, from, to)
        val temp = tree.rename(entry, ".${entry.name}.moving-${hex(4)}")
        val moved = try {
            moveSameName(temp, from, to)
        } catch (e: Exception) {
            runCatching { tree.rename(temp, entry.name) }
            throw e
        }
        return tree.rename(moved, finalName)
    }

    private fun moveSameName(entry: SharedEntry, from: SharedEntry, to: SharedEntry): SharedEntry {
        tree.move(entry, from, to)?.let { return it }
        // 제공자가 옮기기를 하지 않는다 — 복사한 뒤 원래 것을 지운다. 복사가 다 된 뒤에만 지운다.
        val copied = copyByHand(entry, to, entry.name, Budget())
        if (!tree.delete(entry)) throw CopiedNotRemoved()
        return copied
    }

    /**
     * 손으로 옮기기에서 사본은 다 지었는데 원래 것을 지우지 못했다 — 원래 것이 **반쯤 지워졌을 수 있다**(폴더 지우기는 중간에 멈출 수 있다).
     * 그래서 부르는 쪽은 사본을 지우면 안 된다(휴지통으로 옮기기가 칸을 치우면 온전한 것은 하나도 남지 않는다 — 2026-11-21 검토에서 고침).
     */
    private class CopiedNotRemoved : OpFailure(500, "복사는 했지만 원래 것을 지우지 못했습니다")

    /** 복사한다 — 이름이 그대로면 제공자의 복사부터, 아니면(또는 제공자가 못 하면) 한 항목씩 짓는다. */
    private fun copyEntry(entry: SharedEntry, to: SharedEntry, finalName: String): SharedEntry {
        if (finalName == entry.name) {
            val native = tree.copy(entry, to)
            if (native != null) return if (native.name == finalName) native else tree.rename(native, finalName)
        }
        return copyByHand(entry, to, finalName, Budget())
    }

    /** 손으로 짓는 복사의 상한 — 항목 수와 깊이. */
    private class Budget(var entries: Int = 0)

    private fun copyByHand(entry: SharedEntry, to: SharedEntry, name: String, budget: Budget, depth: Int = 0): SharedEntry {
        if (++budget.entries > maxTreeEntries) throw OpFailure(413, "항목이 너무 많습니다(${maxTreeEntries}개까지)")
        if (depth > SharedPaths.MAX_SEGMENTS) throw OpFailure(413, "폴더가 너무 깊습니다")
        if (entry.isDirectory) {
            val created = tree.createDirectory(to, name)
            try {
                for (child in tree.list(entry)) copyByHand(child, created, child.name, budget, depth + 1)
            } catch (e: Exception) {
                // 반쯤 지은 사본을 남기지 않는다 — 맨 위에서 한 번 통째로 지운다.
                if (depth == 0) runCatching { tree.delete(created) }
                throw e
            }
            return created
        }
        val created = tree.createFile(to, name)
        try {
            val source = tree.openRead(entry) ?: throw OpFailure(500, "원본을 읽을 수 없습니다")
            source.use { input ->
                val sink = tree.openWrite(created) ?: throw OpFailure(500, "사본을 쓸 수 없습니다")
                sink.use { input.copyTo(it, COPY_BUFFER) }
            }
        } catch (e: Exception) {
            // 반쯤 쓴 사본을 남기지 않는다.
            tree.delete(created)
            throw e
        }
        return created
    }

    // ── 휴지통 ───────────────────────────────────────────────────────────────────────────────────────────

    /** [baseSegments] 폴더의 [names] 를 휴지통으로 옮긴다. */
    fun trash(baseSegments: List<String>, names: List<String>): Outcome {
        checkMembers(names, baseSegments.isEmpty())
        val base = directory(baseSegments)
        val children = tree.list(base)
        val bin = trashDirectory(create = true)!!
        ensureNoMedia(bin, tree.list(bin))
        val done = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        for (name in names) {
            val entry = children.firstOrNull { it.name == name }
            if (entry == null) {
                failed += name to "찾을 수 없습니다"
                continue
            }
            try {
                trashOne(bin, base, entry, baseSegments.joinToString("/"))
                done += name to name
            } catch (e: OpFailure) {
                failed += name to (e.message ?: "실패")
            } catch (e: Exception) {
                failed += name to "휴지통으로 옮기지 못했습니다"
            }
        }
        return Outcome(done, emptyList(), failed)
    }

    /**
     * 올리기의 덮어쓰기 — [parentSegments] 폴더의 파일 [entry](덮일 원래 것)를 휴지통으로 옮긴다. 지우기와 같은 칸·기록이라 되살리기·보관 기간·
     * [NO_MEDIA] 의 규칙이 같다. 휴지통의 id. 옮기지 못하면 던진다 — 부르는 쪽([FileServer])은 덮어쓰지 않는다(데이터를 잃지 않게). **관리
     * 허용과 상관없다**(사용자 결정 11-21 — 덮어쓴 원래 것은 늘 휴지통에). 폴더는 덮지 않는다(409 — 한 폴더에 같은 이름의 파일과 폴더는 둘 수 없다).
     */
    fun trashReplaced(parentSegments: List<String>, entry: SharedEntry): String {
        if (entry.isDirectory) throw OpFailure(409, FOLDER_SAME_NAME)
        val parent = directory(parentSegments)
        val bin = trashDirectory(create = true)!!
        ensureNoMedia(bin, tree.list(bin))
        return trashOne(bin, parent, entry, parentSegments.joinToString("/"))
    }

    // ── 덮어쓰기의 빈 원래 파일(사용자 결정 11-21 — 0바이트면 휴지통에 넣지 않는다) ───────────────────────────────────────────

    /**
     * 덮어쓸 원래 파일 [entry] 가 **비었는가**(0바이트). 크기는 서버가 지금 읽은 실제 항목의 것이다(요청이 준 값이 아니다). 크기를 모르면 0 으로
     * 적히는 제공자가 있어(앱의 SAF — `COLUMN_SIZE` 가 null 이면 0) **열어서 한 바이트도 없는지까지** 본다. 열지 못하거나 읽다 실패하면 비지 않은
     * 것으로 친다 — 휴지통으로 가는 쪽이 안전하다. 폴더는 false.
     */
    fun isEmptyFile(entry: SharedEntry): Boolean {
        if (entry.isDirectory || entry.size != 0L) return false
        return try {
            tree.openRead(entry)?.use { it.read() == -1 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 덮어쓰기 동안 비켜 둔 빈 원래 파일 — [entry] 는 숨은 이름의 그것, [name] 은 원래 이름. */
    class SetAside(val entry: SharedEntry, val name: String)

    /**
     * 덮어쓰기의 빈 원래 파일 [entry] 를 같은 폴더의 숨은 이름(`.이름.replacing-<8 hex>`)으로 비켜 둔다 — 두기([SharedTree.commitUpload])는 같은
     * 이름을 지우지 않으므로 그 자리를 비운다. 지우지 않는다(두기가 실패하면 [putBack] 으로 돌린다). 못 하면 던진다 — 원래 것은 그대로이고 부르는
     * 쪽은 휴지통 길로 간다.
     */
    fun setAside(entry: SharedEntry): SetAside = SetAside(tree.rename(entry, ".${entry.name}$ASIDE_INFIX${hex(4)}"), entry.name)

    /**
     * 두기가 실패했다 — 비켜 둔 것을 [parentSegments] 폴더에서 원래 이름으로 돌린다. 그새 같은 이름이 생겼으면 `이름 (1)` 로. 돌린 경로(공유 폴더
     * 기준), 못 했으면 null(비켜 둔 숨은 이름으로 남는다 — 0바이트라 잃은 내용은 없다).
     */
    fun putBack(parentSegments: List<String>, aside: SetAside): String? {
        val prefix = if (parentSegments.isEmpty()) "" else parentSegments.joinToString("/") + "/"
        try {
            return prefix + tree.rename(aside.entry, aside.name).name
        } catch (_: Exception) {
            // 그새 같은 이름이 생겼다(관리 잠금을 쥐지 않는 새 올리기) — 겹치지 않는 이름으로.
        }
        return try {
            val taken = tree.list(directory(parentSegments)).filter { it.ref != aside.entry.ref }.map { it.name }
            prefix + tree.rename(aside.entry, SharedPaths.freeName(aside.name, false, taken)).name
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 새 것을 둔 뒤 비켜 둔 것을 치운다 — **지우기 직전에 다시 본다**([isEmptyFile]): 그새 커졌으면(이 폰의 다른 앱이 썼다) 정말 지우지 않고
     * 휴지통으로(기록과 칸 안의 이름은 원래 이름 — 안전한 쪽, 사용자 결정 11-21). 여전히 비었으면 지운다. 지우지 못해도 휴지통으로. 휴지통으로
     * 보냈으면 그 id, 지웠거나 이미 없으면 null. 휴지통으로도 못 보내면 던진다(비켜 둔 것은 숨은 이름으로 남는다 — 내용은 잃지 않는다).
     */
    fun discardAside(parentSegments: List<String>, aside: SetAside): String? {
        val parent = directory(parentSegments)
        val current = tree.list(parent).firstOrNull { it.name == aside.entry.name } ?: return null
        if (isEmptyFile(current) && tree.delete(current)) return null
        val bin = trashDirectory(create = true)!!
        ensureNoMedia(bin, tree.list(bin))
        return trashOne(bin, parent, current, parentSegments.joinToString("/"), recordName = aside.name)
    }

    /**
     * [entry] 를 휴지통의 새 칸으로 옮기고 기록을 쓴다. 칸의 id. [recordName] 은 기록과 칸 안에 남길 이름 — 비켜 둔 숨은 이름이 아니라 원래 이름을
     * 남길 때([discardAside]). 칸 안에서 이름을 바꾸지 못하면 숨은 이름 그대로 둔다(되살리기는 칸 안의 이름을 쓴다 — 내용은 같다).
     */
    private fun trashOne(bin: SharedEntry, parent: SharedEntry, entry: SharedEntry, from: String, recordName: String = entry.name): String {
        val id = "t${nowMs()}-${hex(4)}"
        val slot = tree.createDirectory(bin, id)
        val record = try {
            tree.createFile(bin, "$id$RECORD_SUFFIX")
        } catch (e: Exception) {
            tree.delete(slot)
            throw e
        }
        try {
            val info = Json.obj(
                "v" to 1, "name" to recordName, "from" to from, "deleted" to nowMs(), "dir" to entry.isDirectory, "size" to entry.size,
            )
            val sink = tree.openWrite(record) ?: throw OpFailure(500, "휴지통에 기록을 쓸 수 없습니다")
            sink.use { it.write(info.toByteArray(Charsets.UTF_8)) }
            val moved = moveSameName(entry, parent, slot)
            // 칸에는 겹칠 것이 없다 — 실패는 삼킨다(이 안에서 던지면 아래의 되돌리기가 옮겨진 것을 지울 수 있다).
            if (recordName != entry.name) runCatching { tree.rename(moved, recordName) }
            return id
        } catch (_: CopiedNotRemoved) {
            // 온전한 사본은 칸에만 있다 — 칸과 기록을 남긴다(휴지통에서 되살릴 수 있다). 원래 자리에 남은 것은 손님이 다시 지우면 된다.
            throw OpFailure(500, "휴지통에 사본을 두었지만 원래 것을 다 지우지 못했습니다")
        } catch (e: Exception) {
            // 옮기기가 된 뒤에 실패했을 수 있다(SAF — moveDocument 는 됐는데 옮긴 것을 다시 읽지 못함). 그때 칸을 지우면 하나뿐인 원래 것을
            // 지운다(덮어쓰기는 '원래 파일은 그대로' 라 답하고 원래 것을 잃었다 — 2026-11-21 3차 검토에서 고침). 칸에 있고 원래 자리에 없으면 옮겨진
            // 것이고, 칸이 비었거나 원래 것이 제자리에 있으면 칸을 치워도 잃는 것이 없고, 알 수 없으면 칸과 기록을 남긴다.
            val inSlot = try {
                tree.list(slot).any { it.name == entry.name }
            } catch (_: Exception) {
                null
            }
            val inPlace = try {
                tree.list(parent).any { it.name == entry.name }
            } catch (_: Exception) {
                null
            }
            when {
                inSlot == true && inPlace == false -> {
                    if (recordName != entry.name) {
                        runCatching { tree.list(slot).firstOrNull { it.name == entry.name }?.let { tree.rename(it, recordName) } }
                    }
                    return id
                }
                inSlot == false || inPlace == true -> {
                    tree.delete(record)
                    tree.delete(slot)
                    throw e
                }
                else -> throw OpFailure(500, "휴지통으로 옮겼는지 확인하지 못했습니다 — 휴지통을 확인하세요")
            }
        }
    }

    /** 휴지통 폴더. 없으면 [create] 일 때 만든다(아니면 null). */
    private fun trashDirectory(create: Boolean): SharedEntry? {
        val root = directory(emptyList())
        val children = tree.list(root)
        (children.firstOrNull { it.name == SharedPaths.TRASH_NAME } ?: children.firstOrNull { isTrashName(it.name) })?.let {
            if (!it.isDirectory) throw OpFailure(409, "휴지통 자리(${SharedPaths.TRASH_NAME})에 파일이 있습니다")
            return it
        }
        return if (create) tree.createDirectory(root, SharedPaths.TRASH_NAME) else null
    }

    /**
     * 휴지통 [bin]([children] 은 그 자식들)에 [NO_MEDIA] 가 없으면 더한다. 실패는 삼킨다 — 갤러리에서 감추는 덤이라 지우기·비우기를 막지 않는다.
     * 그 이름의 폴더가 있으면(사람이 만들었다) 그대로 둔다(MediaStore 는 이름만 본다).
     */
    private fun ensureNoMedia(bin: SharedEntry, children: List<SharedEntry>) {
        if (children.any { it.name == NO_MEDIA }) return
        runCatching { tree.createFile(bin, NO_MEDIA) }
    }

    /** 보관 기간을 지나 지운 결과 — 지운 칸 수와 지우지 못한 칸 수. */
    class Expiry(val purged: Int, val failed: Int)

    /**
     * 휴지통을 손본다 — 휴지통이 있으면 [NO_MEDIA] 를 더하고, [retentionDays] 가 0 보다 크면 기록의 '지운 때' 가 그만큼 지난 칸을 정말 지운다
     * (칸 → 기록 차례 — 칸을 지우지 못하면 기록을 남겨 다음에 다시 해 본다). 휴지통을 새로 만들지는 않는다.
     *
     * **기록이 없거나 읽지 못하거나 '지운 때' 가 없으면 지우지 않는다.** 칸 이름의 시각(`t<ms>-…` — 지울 때 기록과 같은 시계로 지었다)이 아직
     * 기간 안이면 기록을 읽지도 않는다(휴지통이 커도 SAF 질의가 지난 것만큼만 든다) — 둘 다 지났을 때만 지운다.
     */
    fun maintainTrash(retentionDays: Int): Expiry {
        val bin = trashDirectory(create = false) ?: return Expiry(0, 0)
        val children = tree.list(bin)
        ensureNoMedia(bin, children)
        if (retentionDays <= 0) return Expiry(0, 0)
        val cutoff = nowMs() - retentionDays * DAY_MS
        val records = children.filter { !it.isDirectory && it.name.endsWith(RECORD_SUFFIX) }.associateBy { it.name.removeSuffix(RECORD_SUFFIX) }
        var purged = 0
        var failed = 0
        for (slot in children) {
            if (!slot.isDirectory || !isTrashId(slot.name)) continue
            val slotMs = slot.name.substring(1, slot.name.indexOf('-')).toLongOrNull() ?: continue
            if (slotMs >= cutoff) continue
            val record = records[slot.name] ?: continue
            val deleted = readRecord(record)?.get("deleted") as? Long ?: continue
            if (deleted >= cutoff) continue
            val gone = try {
                tree.delete(slot)
            } catch (_: Exception) {
                false
            }
            if (!gone) {
                failed++
                continue
            }
            runCatching { tree.delete(record) }
            purged++
        }
        return Expiry(purged, failed)
    }

    /** 휴지통의 항목들 — 지운 때가 늦은 것부터. 기록이 없는 칸은 칸 안의 이름과 칸의 시각으로 보인다. */
    fun trashList(): List<TrashItem> {
        val bin = trashDirectory(create = false) ?: return emptyList()
        val children = tree.list(bin)
        val records = children.filter { !it.isDirectory && it.name.endsWith(RECORD_SUFFIX) }.associateBy { it.name.removeSuffix(RECORD_SUFFIX) }
        val out = ArrayList<TrashItem>()
        for (slot in children) {
            if (!slot.isDirectory || !isTrashId(slot.name)) continue
            if (out.size >= MAX_TRASH_LIST) break
            val item = tree.list(slot).firstOrNull() ?: continue
            val info = records[slot.name]?.let { readRecord(it) }
            out += TrashItem(
                id = slot.name,
                name = item.name,
                from = (info?.get("from") as? String)?.takeIf { SharedPaths.split(it) != null } ?: "",
                deleted = (info?.get("deleted") as? Long) ?: slot.modified,
                isDirectory = item.isDirectory,
                size = if (item.isDirectory) ((info?.get("size") as? Long) ?: 0) else item.size,
            )
        }
        return out.sortedByDescending { it.deleted }
    }

    /** 되살린다 — 기록의 폴더로(없으면 만들고), 같은 이름이 있으면 `이름 (1)`. [Outcome.done] 은 (id, 되살린 경로). */
    fun restore(ids: List<String>): Outcome {
        checkIds(ids)
        val bin = trashDirectory(create = false) ?: throw OpFailure(404, "휴지통이 비었습니다")
        val children = tree.list(bin)
        val done = ArrayList<Pair<String, String>>()
        val failed = ArrayList<Pair<String, String>>()
        for (id in ids) {
            val slot = children.firstOrNull { it.isDirectory && it.name == id }
            val item = slot?.let { tree.list(it).firstOrNull() }
            if (slot == null || item == null) {
                failed += id to "휴지통에 없습니다"
                continue
            }
            val record = children.firstOrNull { it.name == "$id$RECORD_SUFFIX" }
            try {
                val from = record?.let { readRecord(it) }?.get("from") as? String
                val segments = from?.let { SharedPaths.split(it) }?.takeUnless { SharedPaths.isHidden(it) } ?: emptyList()
                val place = makeDirectories(segments)
                val parent = place.entry
                val finalName = SharedPaths.freeName(item.name, item.isDirectory, tree.list(parent).map { it.name })
                // 칸 안에서 먼저 이름을 바꾼다(칸에는 겹칠 것이 없다) — 옮기기는 이름을 고르지 못한다.
                val ready = if (finalName == item.name) item else tree.rename(item, finalName)
                moveSameName(ready, slot, parent)
                tree.delete(slot)
                record?.let { tree.delete(it) }
                done += id to (place.names + finalName).joinToString("/")
            } catch (e: OpFailure) {
                failed += id to (e.message ?: "실패")
            } catch (e: Exception) {
                failed += id to "되살리지 못했습니다"
            }
        }
        return Outcome(done, emptyList(), failed)
    }

    /**
     * 휴지통에서 정말 지운다 — [ids] 만, null 이면 통째로(비우기 — 휴지통 폴더와 [NO_MEDIA] 는 남기고 그 밖의 것을 모두). 지운 칸 수.
     */
    fun purge(ids: List<String>?): Int {
        ids?.let { checkIds(it) }
        val bin = trashDirectory(create = false) ?: return 0
        if (ids == null) {
            val children = tree.list(bin)
            var count = 0
            var failed = false
            for (child in children) {
                if (child.name == NO_MEDIA) continue
                if (tree.delete(child)) {
                    if (child.isDirectory && isTrashId(child.name)) count++
                } else {
                    failed = true
                }
            }
            ensureNoMedia(bin, children)
            if (failed) throw OpFailure(500, "휴지통을 다 비우지 못했습니다")
            return count
        }
        val children = tree.list(bin)
        var count = 0
        for (id in ids) {
            val slot = children.firstOrNull { it.isDirectory && it.name == id } ?: continue
            if (tree.delete(slot)) count++
            children.firstOrNull { it.name == "$id$RECORD_SUFFIX" }?.let { tree.delete(it) }
        }
        return count
    }

    private fun checkIds(ids: List<String>) {
        if (ids.isEmpty()) throw OpFailure(400, "고른 항목이 없습니다")
        if (ids.size > MAX_BATCH) throw OpFailure(413, "한 번에 ${MAX_BATCH}개까지 다룹니다")
        if (ids.any { !isTrashId(it) }) throw OpFailure(400, "휴지통 항목의 번호가 틀렸습니다")
    }

    private fun isTrashId(name: String): Boolean = TRASH_ID.matches(name)

    /** 기록 하나 — 크기 상한까지만 읽는다. 읽지 못하면 null(그 칸은 기록 없이 보인다). */
    @Suppress("UNCHECKED_CAST")
    private fun readRecord(record: SharedEntry): Map<String, Any?>? = try {
        tree.openRead(record)?.use { input ->
            val bytes = readUpTo(input, MAX_RECORD_BYTES + 1)
            if (bytes.size > MAX_RECORD_BYTES) null else JsonReader.parse(String(bytes, Charsets.UTF_8)) as? Map<String, Any?>
        }
    } catch (e: Exception) {
        null
    }

    // ── zip 의 차례 ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * [baseSegments] 폴더의 [names] 를(폴더는 안의 것까지) zip 에 담을 차례로 편다. 항목 수가 [MAX_TREE_ENTRIES] 를, 깊이가 [SharedPaths.MAX_SEGMENTS]
     * 를 넘으면 413 — 받기를 시작하기 **전에** 알린다. 맨 위의 휴지통은 담지 않는다.
     */
    fun zipPlan(baseSegments: List<String>, names: List<String>): List<ZipItem> {
        checkMembers(names, baseSegments.isEmpty())
        val base = directory(baseSegments)
        val children = visibleChildren(base, baseSegments.isEmpty())
        val out = ArrayList<ZipItem>()
        for (name in names) {
            val entry = children.firstOrNull { it.name == name } ?: throw OpFailure(404, "‘$name’ 을(를) 찾을 수 없습니다")
            walk(entry, name, 1, out)
        }
        return out
    }

    private fun walk(entry: SharedEntry, path: String, depth: Int, out: MutableList<ZipItem>) {
        if (out.size >= maxTreeEntries) throw OpFailure(413, "항목이 너무 많습니다(${maxTreeEntries}개까지) — 폴더를 나눠 받으세요")
        if (depth > SharedPaths.MAX_SEGMENTS) throw OpFailure(413, "폴더가 너무 깊습니다(${SharedPaths.MAX_SEGMENTS}단까지)")
        if (!entry.isDirectory) {
            out += ZipItem(path, entry)
            return
        }
        out += ZipItem("$path/", entry)
        for (child in tree.list(entry).sortedBy { it.name }) walk(child, "$path/${child.name}", depth + 1, out)
    }

    private fun hex(bytes: Int): String {
        val raw = ByteArray(bytes).also { random.nextBytes(it) }
        return buildString { for (b in raw) append(HEX[(b.toInt() shr 4) and 0x0F]).append(HEX[b.toInt() and 0x0F]) }
    }

    companion object {
        /** [limit] 바이트까지 읽는다(그보다 길면 거기서 멈춘다 — 부르는 쪽이 길이로 넘침을 안다). */
        internal fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray = input.readNBytes(limit)

        /** 한 번에 다루는 항목 수 상한(고른 것). */
        const val MAX_BATCH = 1000

        /** 손으로 짓는 복사와 zip 의 항목 수 상한(폴더 안의 것까지). */
        const val MAX_TREE_ENTRIES = 10_000

        /** 휴지통 목록의 상한. */
        const val MAX_TRASH_LIST = 5000

        /** 휴지통 안에 두는 빈 파일 — 폰의 갤러리(MediaStore)가 그 폴더를 훑지 않는다. */
        const val NO_MEDIA = ".nomedia"

        /** 휴지통 보관 기간의 고를 값(일) — 0 은 '비우지 않음'. 폰의 테더 화면이 이 차례로 보인다. */
        val TRASH_RETENTION_CHOICES = listOf(7, 30, 90, 0)

        /** 휴지통 보관 기간의 기본값(일 — 사용자 결정). */
        const val DEFAULT_TRASH_DAYS = 30

        /** 저장된 값이 고를 값 밖이면 기본값으로. */
        fun trashDays(stored: Int): Int = if (stored in TRASH_RETENTION_CHOICES) stored else DEFAULT_TRASH_DAYS

        private const val DAY_MS = 24L * 60 * 60 * 1000

        private const val TRASH_NAME_TAKEN = "그 이름은 휴지통이 씁니다"

        /** 올리는 파일의 이름이 폴더와 겹칠 때 — 한 폴더에 같은 이름의 파일과 폴더는 둘 수 없다(파일 시스템의 제약). 화면도 같은 글을 보인다. */
        const val FOLDER_SAME_NAME = "같은 이름의 폴더가 있습니다"

        /** 덮어쓰기 동안 비켜 둔 빈 원래 파일의 숨은 이름 가운데(`.이름.replacing-<8 hex>` — [setAside]). */
        const val ASIDE_INFIX = ".replacing-"

        private const val RECORD_SUFFIX = ".json"
        private const val MAX_RECORD_BYTES = 16 * 1024
        private const val COPY_BUFFER = 64 * 1024
        private const val HEX = "0123456789abcdef"
        private val TRASH_ID = Regex("t[0-9]{1,15}-[0-9a-f]{8}")
    }
}
