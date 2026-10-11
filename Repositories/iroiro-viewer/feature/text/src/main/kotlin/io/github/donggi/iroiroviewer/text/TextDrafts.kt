package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.io.FileStamp
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * **저장하지 않은 고침**(초안) — 편집 중에 프로세스가 죽어도 고치던 글을 잃지 않게 앱 전용 폴더에 적어 둔다.
 *
 * - 자리는 `filesDir/text-drafts/` — 사용자 파일의 내용이라 공유 저장소·캐시가 아니다(캐시는 시스템이 말없이 지운다). 앱의 백업
 *   규칙이 `file` 영역을 내보내지 않는다(`allowBackup=false`, `data_extraction_rules.xml`).
 * - 이름은 **정규화한 경로의 SHA-256** 이다 — 이름에 경로를 드러내지 않는다(이어보기 키와 같은 판단). 안에도 경로를 적지 않는다.
 * - 쓰기는 임시본 → fsync → 원자적 이름 바꾸기 — 반쯤 쓴 초안이 앞의 온전한 초안을 덮지 않게. 같은 파일의 쓰기는 한 줄로
 *   선다([lock] — 늦춘 쓰기와 화면을 떠날 때의 쓰기가 겹친다).
 * - [STALE_MS] 넘게 손대지 않은 초안은 편집기를 열 때 치운다 — 이름을 바꾸거나 지운 파일의 초안이 영영 남지 않게.
 *
 * 글을 읽고 쓰는 일만 하고 '언제 쓰는가' 는 편집기([TextEditViewModel])가 정한다.
 */
internal object TextDrafts {

    /**
     * 초안 하나.
     *
     * @param session 이 초안을 쓴 편집 세션의 표(편집 화면이 저장 상태에 든다) — 같은 표로 다시 열리면 프로세스가 죽었다 살아난
     *   것이라 묻지 않고 이어 간다.
     * @param base 고치기 시작할 때(또는 지난 저장 뒤) 파일의 크기·수정 시각 — 다시 열 때 지금 파일과 다르면 '그 뒤에 바뀌었다' 를 말한다.
     * @param encoding 그 세션이 쓰던 인코딩(`TextEncoding.name`). 뷰어에서 고른 것이 없으면 이것으로 연다.
     */
    class Draft(
        val session: Long,
        val baseSize: Long,
        val baseModified: Long,
        val encoding: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val text: String,
    )

    /** 열 때 남은 초안을 어떻게 하는가([choiceFor]). */
    sealed interface Choice {
        /** 지금 파일과 같은 글이다(저장한 뒤에 남은 것) — 묻지 않고 지운다. */
        data object Discard : Choice

        /** 같은 편집 세션이 남긴 것이다 — 프로세스가 죽었다 살아났다. 묻지 않고 잇는다. */
        data object Continue : Choice

        /** 다른 때 남은 것이다 — '이어서 고치기 / 버리기' 를 묻는다. [changedSince] 면 그 뒤에 파일이 다른 곳에서 바뀌었다. */
        data class Ask(val changedSince: Boolean) : Choice
    }

    /**
     * 남은 [draft] 를 어떻게 하는가 — 파일의 지금 글([fileText])·지금 편집 세션의 표([session])·지금 파일의 표([current])와 견준다.
     * 같은 글이면 그 무엇보다 먼저 지운다(물을 것이 없다).
     */
    fun choiceFor(draft: Draft, fileText: String, session: Long, current: FileStamp): Choice = when {
        draft.text == fileText -> Choice.Discard
        draft.session == session -> Choice.Continue
        else -> Choice.Ask(changedSince = FileStamp(draft.baseSize, draft.baseModified) != current)
    }

    /** 초안을 두는 폴더. */
    fun dirOf(filesDir: File): File = File(filesDir, DIR_NAME)

    /** [canonicalPath] 의 초안 파일 — 이름은 경로의 해시다. */
    fun fileOf(dir: File, canonicalPath: String): File = File(dir, keyOf(canonicalPath) + SUFFIX)

    /** 경로의 SHA-256(소문자 16진). */
    fun keyOf(canonicalPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPath.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    /** 읽는다. 없거나 망가졌으면 null — 망가진 것은 지운다(다시 물을 것이 없다). */
    fun read(file: File): Draft? {
        if (!file.isFile) return null
        if (file.length() > MAX_DRAFT_BYTES) {
            file.delete()
            return null
        }
        return try {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("모르는 초안")
                val session = input.readLong()
                val baseSize = input.readLong()
                val baseModified = input.readLong()
                val encoding = input.readUTF()
                val selectionStart = input.readInt()
                val selectionEnd = input.readInt()
                val length = input.readInt()
                if (length < 0 || length > MAX_DRAFT_BYTES) throw IOException("초안 길이")
                val bytes = ByteArray(length)
                input.readFully(bytes)
                Draft(session, baseSize, baseModified, encoding, selectionStart, selectionEnd, String(bytes, Charsets.UTF_8))
            }
        } catch (e: IOException) {
            // 끝이 모자란 것(`EOFException`)도 여기다 — 원자적으로 쓰므로 생기지 않아야 하지만, 생겼으면 물을 것이 없다.
            file.delete()
            null
        }
    }

    /** 쓴다 — 임시본에 다 쓰고 내린 뒤 이름을 바꾼다. 같은 파일의 쓰기는 [lock] 아래에서, 입출력 디스패처에서. */
    suspend fun write(file: File, draft: Draft) {
        lock.withLock { withContext(IroDispatchers.io) { writeNow(file, draft) } }
    }

    /** 지운다(저장했거나 버렸다). */
    suspend fun delete(file: File) {
        lock.withLock { withContext(IroDispatchers.io) { file.delete() } }
    }

    /** [maxAgeMs] 넘게 손대지 않은 초안과, 죽은 쓰기가 남긴 임시본을 치운다. */
    fun purgeStale(dir: File, now: Long = System.currentTimeMillis(), maxAgeMs: Long = STALE_MS) {
        dir.listFiles()?.forEach { f ->
            val stale = now - f.lastModified() > maxAgeMs
            val leftover = f.name.endsWith(TEMP_SUFFIX) && now - f.lastModified() > TEMP_GRACE_MS
            if (f.isFile && (stale || leftover)) f.delete()
        }
    }

    internal fun writeNow(file: File, draft: Draft) {
        val dir = file.parentFile ?: return
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("초안 폴더를 만들 수 없다")
        val tmp = File(dir, file.name + TEMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { raw ->
                val out = DataOutputStream(raw.buffered())
                val bytes = draft.text.toByteArray(Charsets.UTF_8)
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(draft.session)
                out.writeLong(draft.baseSize)
                out.writeLong(draft.baseModified)
                out.writeUTF(draft.encoding)
                out.writeInt(draft.selectionStart)
                out.writeInt(draft.selectionEnd)
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
                raw.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tmp.delete()
        }
    }

    /** 초안의 쓰기·지우기가 서는 한 줄. 파일이 달라도 한 줄이다 — 편집기는 하나씩 열리고 초안은 작다. */
    private val lock = Mutex()

    /** 화면·뷰모델이 사라진 뒤에도 끝나야 하는 쓰기(화면을 떠나며 적는 초안)가 도는 곳. 프로세스와 함께 산다. */
    val scope = CoroutineScope(SupervisorJob() + IroDispatchers.io)

    private const val HEX = "0123456789abcdef"
    private const val DIR_NAME = "text-drafts"
    private const val SUFFIX = ".draft"
    private const val TEMP_SUFFIX = ".part"
    private const val MAGIC = 0x49524F44 // "IROD"
    private const val VERSION = 1

    /** 초안 하나의 상한. 편집 상한(256 KiB)의 글은 UTF-8 로 커도 이것을 넘지 않는다 — 넘는 것은 우리가 쓴 초안이 아니다. */
    private const val MAX_DRAFT_BYTES = 64 * 1024 * 1024

    /** 이만큼 손대지 않은 초안은 치운다. */
    const val STALE_MS = 30L * 24 * 60 * 60 * 1000

    /** 임시본은 이보다 오래되었을 때만 치운다(지금 쓰는 중일 수 있다). */
    private const val TEMP_GRACE_MS = 60L * 60 * 1000
}
