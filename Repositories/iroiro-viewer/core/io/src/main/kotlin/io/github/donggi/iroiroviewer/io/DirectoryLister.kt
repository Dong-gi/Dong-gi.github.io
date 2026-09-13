package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.NameSortKey
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Paths

/**
 * 폴더 하나를 읽는다.
 *
 * ## 왜 `java.io.File` 을 쓰지 않는가
 *
 * `File.listFiles()` 는 이름을 읽은 뒤 항목마다 `isDirectory`·`length`·`lastModified`
 * 를 부를 때 **각각 stat 시스템 호출을 한 번씩** 한다 — 1만 개 폴더면 3만 번이다.
 * FUSE 위에 얹힌 `/storage/emulated/0` 에서 이 비용은 그대로 체감된다.
 * [Os.lstat] 을 항목당 한 번만 부르면 같은 정보를 3분의 1 비용으로 얻는다.
 *
 * `lstat` 이 `stat` 이 아닌 것도 의도다. 심볼릭 링크를 **따라가지 않는다.** 따라가면
 * 순환 고리에 빠지거나 볼륨 밖으로 나간다.
 */
object DirectoryLister {

    /** 나열의 상태. 큰 폴더에서 화면이 빈 채로 멈춰 있지 않도록 진행을 흘린다. */
    sealed interface Listing {
        /** 아직 읽는 중. [count] 는 지금까지 본 항목 수. */
        data class Scanning(val count: Int) : Listing

        /** 읽기가 끝났다. [entries] 는 **정렬되지 않은** 원본이다. */
        data class Ready(
            val entries: List<FileEntry>,
            /** 숨김 파일을 빼기 전의 총 개수. '숨김 N개' 를 보여주는 데 쓴다. */
            val hiddenCount: Int,
            val scanMillis: Long,
        ) : Listing

        data class Failed(val reason: Reason) : Listing
    }

    enum class Reason {
        /** 없어졌다. 다른 앱이 지웠거나 SD 가 빠졌다. */
        NOT_FOUND,

        /** 안드로이드가 막는 곳. `Android/data`·`Android/obb` 가 그렇다. */
        LOCKED,

        NOT_A_DIRECTORY,
        IO,
    }

    /** 몇 개마다 진행을 알릴 것인가. 너무 잦으면 그것만으로 느려진다. */
    private const val PROGRESS_EVERY = 500

    /**
     * 폴더를 읽어 흘린다. **정렬하지 않는다** — 정렬은 [sort] 가 따로 하고, 사용자가
     * 정렬 기준을 바꿀 때 디스크를 다시 읽지 않기 위해서다.
     *
     * @param showHidden `.` 으로 시작하는 항목을 포함할 것인가.
     */
    fun list(path: String, showHidden: Boolean): Flow<Listing> = flow {
        emit(Listing.Scanning(0))
        val scanStart = System.nanoTime()

        val raw = ArrayList<FileEntry>(256)
        var hidden = 0
        try {
            Files.newDirectoryStream(Paths.get(path)).use { stream ->
                for (child in stream) {
                    // 협조적 취소. 폴더를 나가면 즉시 멈춰야 한다.
                    currentCoroutineContext().ensureActive()
                    val name = child.fileName?.toString() ?: continue
                    // 우리 휴지통은 목록에 내보내지 않는다. 휴지통 화면으로만 들어가야
                    // UUID 이름의 파일을 사용자가 직접 보고 혼란스러워하지 않는다.
                    if (name == TrashStore.DIR_NAME) continue
                    val isHidden = name.startsWith('.')
                    if (isHidden) hidden++
                    if (isHidden && !showHidden) continue
                    raw += stat(path, name, isHidden) ?: continue
                    if (raw.size % PROGRESS_EVERY == 0) emit(Listing.Scanning(raw.size))
                }
            }
        } catch (e: AccessDeniedException) {
            emit(Listing.Failed(Reason.LOCKED)); return@flow
        } catch (e: NoSuchFileException) {
            emit(Listing.Failed(Reason.NOT_FOUND)); return@flow
        } catch (e: NotDirectoryException) {
            emit(Listing.Failed(Reason.NOT_A_DIRECTORY)); return@flow
        } catch (e: java.io.IOException) {
            // 막힌 폴더가 AccessDenied 가 아니라 일반 IOException 으로 오는 경우가 있다.
            emit(Listing.Failed(if (LockedPaths.isLocked(path)) Reason.LOCKED else Reason.IO))
            return@flow
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // **DirectoryStream 의 반복자는 검사 예외를 던질 수 없어 감싸서 던진다**
            // (`DirectoryIteratorException`, RuntimeException). 위 catch 넷이 그것을
            // 모두 놓치면 flow 밖으로 나가 stateIn 의 공유 코루틴이 죽고, 그 자리에서
            // 앱이 통째로 내려간다. 폴더 하나를 못 읽은 것이 앱을 죽일 이유는 없다.
            emit(Listing.Failed(if (LockedPaths.isLocked(path)) Reason.LOCKED else Reason.IO))
            return@flow
        }
        val scanMillis = (System.nanoTime() - scanStart) / 1_000_000
        emit(Listing.Ready(raw, hidden, scanMillis))
    }.flowOn(IroDispatchers.io)

    /**
     * 항목 하나의 메타데이터. [Os.lstat] 한 번이면 종류·크기·시각이 전부 나온다.
     *
     * 실패하면 null 이다 — 읽는 도중에 지워지는 파일이 실제로 있고, 그것 때문에 폴더
     * 전체가 안 보이면 안 된다.
     */
    private fun stat(parent: String, name: String, isHidden: Boolean): FileEntry? {
        val full = if (parent.endsWith('/')) parent + name else "$parent/$name"
        return try {
            val st = Os.lstat(full)
            val isLink = OsConstants.S_ISLNK(st.st_mode)
            val isDir = OsConstants.S_ISDIR(st.st_mode)
            FileEntry(
                name = name,
                path = full,
                isDirectory = isDir,
                size = if (isDir) 0L else st.st_size,
                // st_mtime 은 초 단위다. 밀리초로 맞춘다.
                lastModified = st.st_mtime * 1000L,
                isHidden = isHidden,
                isSymlink = isLink,
                kind = MimeResolver.kindOf(name, isDir),
                isLocked = isDir && LockedPaths.isLocked(full),
            )
        } catch (e: ErrnoException) {
            null
        }
    }

    /**
     * 정렬.
     *
     * 이름 정렬에서만 [NameSortKey] 를 항목마다 미리 만든다 — 비교 때마다 대조기를
     * 부르면 1만 개에서 수백 밀리초가 날아간다. 다른 기준은 비교가 싸므로 그냥 비교한다.
     */
    fun sort(entries: List<FileEntry>, spec: SortSpec): List<FileEntry> {
        if (entries.isEmpty()) return entries
        val dir = if (spec.foldersFirst) compareByDescending<FileEntry> { it.isDirectory } else null

        val comparator: Comparator<FileEntry> = when (spec.key) {
            SortKey.NAME -> {
                val collator = NameSortKey.koreanCollator()
                val keys = HashMap<String, NameSortKey>(entries.size * 2)
                for (e in entries) keys[e.path] = NameSortKey.of(e.name, collator)
                compareBy { keys.getValue(it.path) }
            }
            SortKey.DATE -> compareBy { it.lastModified }
            SortKey.SIZE -> compareBy { it.size }
            SortKey.KIND -> compareBy<FileEntry> { it.kind.ordinal }
                .thenBy { MimeResolver.extensionOf(it.name) }
        }

        // 같은 값일 때 순서가 흔들리지 않도록 이름을 마지막 기준으로 둔다.
        val stable = comparator.thenBy { it.name }
        val directed = if (spec.ascending) stable else stable.reversed()
        return if (dir == null) entries.sortedWith(directed) else entries.sortedWith(dir.then(directed))
    }

    /** 현재 폴더 안에서 이름으로 거르는 것. 인덱스 없이 즉시 되는 값싼 기능이다. */
    fun filter(entries: List<FileEntry>, query: String): List<FileEntry> {
        if (query.isBlank()) return entries
        val q = query.trim().lowercase()
        return entries.filter { it.name.lowercase().contains(q) }
    }
}

/**
 * 안드로이드가 막아 둔 곳.
 *
 * `MANAGE_EXTERNAL_STORAGE` 로도 못 읽고 SAF 우회도 막혔다. 목록에서 **숨기지 않는다** —
 * 숨기면 사용자에게는 '내 파일이 사라졌다' 가 되고, 그것이 못 들어간다는 사실보다 나쁘다.
 */
object LockedPaths {

    private val lockedNames = setOf("data", "obb")

    fun isLocked(path: String): Boolean {
        val f = File(path)
        if (f.name !in lockedNames) return false
        return f.parentFile?.name == "Android"
    }
}
