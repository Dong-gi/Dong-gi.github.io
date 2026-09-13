package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import android.content.Context
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.TrashEntryEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 휴지통.
 *
 * ## 왜 볼륨마다 두는가
 *
 * 삭제는 **언제나 같은 볼륨 안의 `rename` 한 번**이어야 한다. 휴지통을 한 군데에만 두면
 * SD 카드의 4GB 영상을 지울 때 내부 저장소로 복사가 일어나고, 그 순간 저장공간이
 * 모자라 '삭제가 실패' 한다. 볼륨마다 두면 크기와 무관하게 즉시 끝난다.
 *
 * ## 어디에 무엇을 적는가
 *
 * 정본은 **Room**(앱 전용 디렉터리)이다. 볼륨의 사이드카에는 `uuid`·`deletedAt`·
 * `isDirectory`·`size`·`ext` 만 적고 **원래 경로와 이름을 적지 않는다** — `.iroiro-trash`
 * 는 모든 파일 접근 권한을 가진 어떤 앱이든 읽을 수 있어서, 거기에 경로를 평문으로
 * 쌓으면 '이 사람이 무엇을 언제 지웠는가' 가 통째로 새어 나간다. 확장자 하나만은
 * 예외로 적는다 — 앱 바깥에서 uuid 덩어리를 되살릴 때 그것이 무엇인지 알 유일한 단서다.
 *
 * ## 손실이 생기지 않게 하는 두 가지 규칙
 *
 * 1. **항목 하나의 세 쓰기(사이드카 → rename → 기록)는 쪼개지지 않는다.**
 *    `withContext(NonCancellable)` 로 묶고 취소는 **항목 경계에서만** 받는다. 중간에
 *    끊기면 이름이 uuid 로 바뀐 채 기록이 없는 파일이 남는데, 앱 안에서는 그것을 되살릴
 *    방법이 없다.
 * 2. **판단하지 못한 것을 지우지 않는다.** 기록이 없는 파일(고아)을 만나면 지우는 것이
 *    아니라 **기록을 새로 만들어 목록에 띄운다.** 원래 경로는 모르지만 사용자는 그것이
 *    있다는 사실과 크기·시각을 보고 스스로 정할 수 있다. 자동 영구 삭제는 우리가
 *    무엇인지 아는 것에만 건다.
 */
class TrashStore(
    private val context: Context,
    private val media: MediaIndex,
) {

    private val dao get() = IroiroDatabase.get(context).trash()

    fun observe(): Flow<List<TrashEntryEntity>> = dao.observeAll()

    /** 볼륨 루트의 휴지통 폴더. 없으면 만든다. 만들 수 없으면 null. */
    private fun trashDirOf(volumeRoot: String): File? {
        val dir = File(volumeRoot, DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        // 갤러리·음악 앱이 휴지통 안을 훑지 않게 한다. 이것이 없으면 지운 사진이
        // 갤러리에 그대로 남아 있는 것처럼 보인다.
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return dir
    }

    /**
     * 지운다. 같은 볼륨의 휴지통으로 옮기고 기록을 남긴다.
     *
     * **한 항목이 실패해도 배치를 끝내지 않는다.** 500개를 고른 뒤 마지막 하나의 볼륨
     * 판정이 실패했다고 앞의 499개를 '처리하지 않은 것' 으로 돌려주면, 화면은 한 줄짜리
     * 오류를 보이고 사용자는 499개가 이미 원래 자리에서 사라진 것을 모른다.
     */
    suspend fun moveToTrash(
        paths: List<String>,
        volumes: List<VolumeRegistry.Volume>,
    ): Result = withContext(IroDispatchers.io) {
        var moved = 0
        var failed = 0
        var unavailable = 0
        var firstUnavailable: String? = null
        val touched = ArrayList<String>(paths.size)
        val movedUuids = ArrayList<String>(paths.size)

        try {
            for (path in paths) {
                // 취소는 여기서만 받는다. 아래 블록은 쪼개지지 않는다.
                currentCoroutineContext().ensureActive()

                val src = File(path)
                val volume = VolumeRegistry.volumeOf(volumes, path)
                val dir = volume?.let { trashDirOf(it.path) }
                if (volume == null || dir == null) {
                    unavailable++
                    if (firstUnavailable == null) firstUnavailable = src.name
                    continue
                }

                val uuid = UUID.randomUUID().toString()
                val isDir = src.isDirectory
                val size = if (isDir) 0L else src.length()

                val ok = withContext(NonCancellable) {
                    // 사이드카를 **먼저** 쓴다. 이 순서라면 어느 지점에서 죽어도 '파일은
                    // 있는데 그것이 언제 지운 무엇인지 모르는' 상태가 되지 않는다.
                    // 반대로 짝 없는 사이드카가 남는 것은 아무것도 잃지 않는다.
                    val sidecar = File(dir, "$uuid$SIDECAR_EXT")
                    writeSidecar(sidecar, uuid, isDir, size, src.name.substringAfterLast('.', ""))

                    // 같은 볼륨 안이므로 rename 한 번이면 끝난다. 이것이 실패하면 볼륨
                    // 판정이 틀렸다는 뜻이라 복사로 내려가지 않고 실패로 둔다.
                    val renamed = src.renameTo(File(dir, uuid))
                    if (renamed) {
                        // 기록 쓰기가 실패해도(디스크 가득 등) 파일은 이미 옮겨졌다.
                        // 되돌리지 않는다 — 정합성 검사가 사이드카를 보고 되살린다.
                        runCatching {
                            dao.insert(
                                TrashEntryEntity(
                                    uuid = uuid,
                                    volumeId = volume.id,
                                    originalParent = src.parent.orEmpty(),
                                    originalName = src.name,
                                    isDirectory = isDir,
                                    size = size,
                                    deletedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    } else {
                        sidecar.delete()
                    }
                    renamed
                }

                if (ok) {
                    touched += path
                    movedUuids += uuid
                    moved++
                } else {
                    failed++
                }
            }
        } finally {
            // 취소로 빠져나가더라도 **여기까지 옮긴 것**은 갤러리에서 지워야 한다.
            // 건너뛰면 이미 사라진 사진이 갤러리에 유령으로 남는다.
            withContext(NonCancellable) { media.scanAll(touched) }
        }

        when {
            moved == 0 && unavailable > 0 && failed == 0 -> Result.Unavailable(firstUnavailable.orEmpty())
            moved == 0 && failed + unavailable > 0 -> Result.Failed(failed + unavailable)
            else -> Result.Done(moved, failed + unavailable, movedUuids)
        }
    }

    /**
     * 되돌린다. 원래 부모가 사라졌으면 다시 만들고, 같은 이름이 있으면 번호를 붙인다.
     *
     * **원래 이름을 먼저 그대로 시도한다.** `sanitize` 를 무조건 걸면 `...` 이나 끝에
     * 공백이 붙은 이름 — 리눅스에서는 합법이고 실제로 만들어진다 — 이 `이름없음` 으로
     * 돌아온다. 복원은 우리가 이름을 만들어 내는 자리가 아니라 사용자가 쓰던 이름을
     * 되돌리는 자리다.
     *
     * 출처를 모르는 항목([UNKNOWN_PARENT])은 볼륨 루트의 [RECOVERED_DIR] 로 꺼낸다.
     */
    suspend fun restore(
        uuid: String,
        volumes: List<VolumeRegistry.Volume>,
    ): Result = withContext(IroDispatchers.io) {
        val record = dao.find(uuid) ?: return@withContext Result.Failed(1)
        val volume = volumes.firstOrNull { it.id == record.volumeId }
            ?: return@withContext Result.Unavailable(record.originalName)
        val dir = trashDirOf(volume.path) ?: return@withContext Result.Unavailable(record.originalName)
        val stored = File(dir, uuid)
        if (!stored.exists()) {
            // 파일이 없는 기록은 남겨 둘 이유가 없다.
            dao.delete(record)
            return@withContext Result.Failed(1)
        }

        val parent = if (record.originalParent == UNKNOWN_PARENT) {
            File(volume.path, RECOVERED_DIR)
        } else {
            File(record.originalParent)
        }
        if (!parent.isDirectory && !parent.mkdirs()) return@withContext Result.Failed(1)

        val target = runCatching {
            // 원래 이름 → (그 이름을 쓸 수 없으면) 다듬은 이름 → (겹치면) 번호.
            val wanted = if (canCreate(parent, record.originalName)) {
                record.originalName
            } else {
                PathRules.sanitize(record.originalName)
            }
            File(parent, PathRules.nextAvailable(parent, wanted))
        }.getOrElse { return@withContext Result.Failed(1) }

        withContext(NonCancellable) {
            if (!stored.renameTo(target)) {
                Result.Failed(1)
            } else {
                File(dir, "$uuid$SIDECAR_EXT").delete()
                dao.delete(record)
                media.scanAll(listOf(target.absolutePath))
                Result.Done(1, 0)
            }
        }
    }

    /**
     * 그 폴더에 그 이름으로 만들 수 있는가. 원래 이름을 그대로 쓸 수 있는지 보는 데만 쓴다.
     *
     * 실제로 만들어 보고 지우는 것 말고 확실한 방법이 없다 — 파일시스템마다 받는 이름이
     * 다르고(FAT 은 `?` 를 거부한다), 그 목록을 우리가 다시 적으면 언젠가 어긋난다.
     */
    private fun canCreate(parent: File, name: String): Boolean = runCatching {
        val probe = File(parent, name)
        if (probe.exists()) return@runCatching true
        if (!probe.createNewFile()) return@runCatching false
        probe.delete()
        true
    }.getOrDefault(false)

    /**
     * 영구 삭제. 휴지통에서 빼고 실제로 지운다.
     *
     * **기록은 파일을 지웠을 때만 지운다.** 볼륨이 빠진 상태에서 기록만 지우면 카드
     * 안에는 uuid 이름의 덩어리가 그대로 남는데 목록에서는 사라져, 사용자 눈에는
     * 지워진 것으로 보이고 용량은 그대로다.
     */
    suspend fun purge(
        uuids: List<String>,
        volumes: List<VolumeRegistry.Volume>,
        engine: FileOpEngine,
    ): Result = withContext(IroDispatchers.io) {
        var done = 0
        var failed = 0
        for (uuid in uuids) {
            currentCoroutineContext().ensureActive()
            val record = dao.find(uuid) ?: continue
            val volume = volumes.firstOrNull { it.id == record.volumeId }
            if (volume == null) {
                failed++
                continue
            }
            val dir = File(volume.path, DIR_NAME)
            val stored = File(dir, uuid)

            withContext(NonCancellable) {
                val gone = if (!stored.exists()) {
                    true
                } else {
                    val outcome = engine.deletePermanently(listOf(stored.absolutePath)) { }
                    outcome is FileOpEngine.Outcome.Done && outcome.failed == 0
                }
                if (gone) {
                    File(dir, "$uuid$SIDECAR_EXT").delete()
                    dao.delete(record)
                    done++
                } else {
                    failed++
                }
            }
        }
        Result.Done(done, failed)
    }

    /**
     * 보존 기간이 지난 것을 지우고, 기록과 실제 파일을 맞춘다.
     *
     * ## 지우는 것과 지우지 않는 것
     *
     * 지운다 — **우리가 무엇인지 아는 것**뿐이다. 기록이 있고 기간이 지난 항목, 그리고
     * 짝이 없는 오래된 사이드카(파일이 아니라 메모다. 잃을 것이 없다).
     *
     * 지우지 않는다 — 기록이 없는 파일(고아). 예전에는 사이드카의 시각을 못 읽으면
     * 그 자리에서 영구 삭제했는데, 그것은 **판단 실패를 삭제로 해석하는 것**이었다.
     * 이제는 기록을 새로 만들어 목록에 띄운다. 원래 경로는 모르지만([UNKNOWN_PARENT])
     * 사용자는 그것이 있다는 사실을 보고 스스로 정할 수 있다.
     */
    suspend fun reconcileAndPurgeExpired(
        volumes: List<VolumeRegistry.Volume>,
        engine: FileOpEngine,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
    ): Int = withContext(IroDispatchers.io) {
        val now = System.currentTimeMillis()
        val cutoff = now - retentionDays * 24L * 3600 * 1000
        var purged = 0

        // 1) 기간이 지난 기록. 볼륨이 붙어 있는 것만 실제로 지워진다(purge 가 판단한다).
        val expired = dao.olderThan(cutoff)
        if (expired.isNotEmpty()) {
            val r = purge(expired.map { it.uuid }, volumes, engine)
            if (r is Result.Done) purged += r.done
        }

        for (v in volumes) {
            currentCoroutineContext().ensureActive()
            val dir = File(v.path, DIR_NAME)
            if (!dir.isDirectory) continue
            val present = dir.list()?.toHashSet() ?: continue

            // 2) 기록은 있는데 파일이 없는 것. 되살릴 것이 없으므로 기록을 지운다.
            for (record in dao.inVolume(v.id)) {
                if (record.uuid !in present) dao.delete(record)
            }

            for (name in present) {
                if (name == ".nomedia") continue

                // 3) 짝 없는 사이드카. 사이드카의 수정시각은 **우리가 쓴 시각**이라
                //    믿을 수 있다. 방금 쓴 것은 건드리지 않는다(쓰기와 rename 사이).
                if (name.endsWith(SIDECAR_EXT)) {
                    val owner = name.removeSuffix(SIDECAR_EXT)
                    if (owner !in present) {
                        val f = File(dir, name)
                        if (now - f.lastModified() > STRAY_GRACE_MS) f.delete()
                    }
                    continue
                }

                // 4) 파일은 있는데 기록이 없는 것(고아). **지우지 않고 되살린다.**
                if (!isUuidName(name)) continue
                if (dao.find(name) != null) continue
                val file = File(dir, name)
                val side = readSidecar(File(dir, "$name$SIDECAR_EXT"))
                // 사이드카가 없으면 '언제 지웠는지' 를 모른다. 그 경우의 나이는 **지금**
                // 부터 센다 — 모르는 것을 오래된 것으로 치면 보자마자 지우게 된다.
                dao.insert(
                    TrashEntryEntity(
                        uuid = name,
                        volumeId = v.id,
                        originalParent = UNKNOWN_PARENT,
                        originalName = side?.ext?.takeIf { it.isNotEmpty() }?.let { "$name.$it" } ?: name,
                        isDirectory = side?.isDirectory ?: file.isDirectory,
                        size = side?.size ?: if (file.isDirectory) 0L else file.length(),
                        deletedAt = side?.deletedAt ?: now,
                    )
                )
            }
        }
        purged
    }

    private fun isUuidName(name: String): Boolean =
        name.length == 36 && name.count { it == '-' } == 4 &&
            name.all { it == '-' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun writeSidecar(file: File, uuid: String, isDirectory: Boolean, size: Long, ext: String) {
        runCatching {
            val json = JSONObject()
                .put("uuid", uuid)
                .put("deletedAt", System.currentTimeMillis())
                .put("isDirectory", isDirectory)
                .put("size", size)
                // 확장자만. 이름 전체를 적으면 '무엇을 지웠는가' 가 볼륨에 평문으로 쌓인다.
                .put("ext", ext.take(MAX_EXT_LEN))
            file.writeText(json.toString())
        }
    }

    private data class Sidecar(val deletedAt: Long?, val isDirectory: Boolean?, val size: Long?, val ext: String)

    /**
     * 사이드카를 읽는다. **크기를 먼저 잰다** — 다른 앱이 이 폴더에 1GB 짜리 `.json` 을
     * 두면 `readText` 가 그것을 통째로 메모리에 올린다.
     */
    private fun readSidecar(file: File): Sidecar? = runCatching {
        if (!file.isFile || file.length() > MAX_SIDECAR_BYTES) return@runCatching null
        val json = JSONObject(file.readText())
        Sidecar(
            deletedAt = json.optLong("deletedAt").takeIf { it > 0 },
            isDirectory = if (json.has("isDirectory")) json.optBoolean("isDirectory") else null,
            size = json.optLong("size", -1).takeIf { it >= 0 },
            ext = json.optString("ext").take(MAX_EXT_LEN),
        )
    }.getOrNull()

    sealed interface Result {
        /**
         * [uuids] 는 **이번에 휴지통으로 들어간 항목들**이다.
         *
         * 되돌리기가 이것으로 되돌린다. (부모·이름·최근 삭제시각)으로 되찾는 방법은
         * 쓰지 않는다 — 그런 질의가 DAO 에 없고, 무엇보다 **신원은 이름이 아니다**
         * (아카이브 엔트리에서 같은 실수를 이미 한 번 했고 회귀 시험까지 붙여 막았다).
         */
        data class Done(val done: Int, val failed: Int, val uuids: List<String> = emptyList()) : Result
        data class Failed(val failed: Int) : Result
        /** 볼륨이 없거나 휴지통을 만들 수 없다. */
        data class Unavailable(val name: String) : Result
    }

    companion object {
        /** 볼륨 루트의 휴지통 폴더 이름. 목록에서는 우리가 직접 숨긴다. */
        const val DIR_NAME = ".iroiro-trash"

        /** 출처를 모르는 항목의 [TrashEntryEntity.originalParent]. 화면이 이것으로 구분한다. */
        const val UNKNOWN_PARENT = ""

        /** 출처를 모르는 항목을 되돌릴 곳. */
        const val RECOVERED_DIR = "복구된 항목"

        private const val SIDECAR_EXT = ".json"

        /** 사이드카 크기 상한. 우리가 쓰는 것은 200바이트를 넘지 않는다. */
        private const val MAX_SIDECAR_BYTES = 4096L

        private const val MAX_EXT_LEN = 16

        /** 짝 없는 사이드카를 지우기 전에 두는 유예. 쓰기와 rename 사이의 창보다 넉넉하다. */
        private const val STRAY_GRACE_MS = 60 * 60 * 1000L

        const val DEFAULT_RETENTION_DAYS = 30

        /** 이 경로가 휴지통 폴더(또는 그 안)인가. 목록에서 감추는 데 쓴다. */
        fun isTrashPath(path: String): Boolean =
            path.endsWith("/$DIR_NAME") || path.contains("/$DIR_NAME/")
    }
}
