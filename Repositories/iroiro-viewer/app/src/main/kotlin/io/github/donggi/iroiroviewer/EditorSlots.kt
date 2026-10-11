package io.github.donggi.iroiroviewer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import io.github.donggi.iroiroviewer.io.EditorApp
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.FileStamp
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.PathRules
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 편집기(`feature:editor`)의 **저장 자리**. 파일 쪽이 문서 하나를 편집기에 넘길 때 세션을 열고([open] — 무작위 표 하나와 그 표의 자리
 * `content://<앱>.editorslot/slot/<표>`), 편집기는 '저장' 할 때마다 그 자리에 문서 전체를 쓰고([EditorSlotProvider.openFile]) 반영을
 * 부른다([commit]). 반영은 파일 작업 큐([FileOpManager.Request.SaveFromEditor])로 원본을 바꿔 쓴다 — 그래서 편집기를 닫지 않아도 저장한
 * 것이 파일에 있다. 편집기가 끝나면 [close] 가 세션을 닫는다. 편집기는 따로 된 프로세스(`:editor`)에서 돌고 이것은 주 프로세스에 있다 —
 * 둘을 잇는 것이 제공자 호출이다.
 *
 * ## 어디에 쓰는가([plan])
 *
 * - 원본과 **같은 형식·같은 이름**이면 원본에. 세션의 첫 저장이면(또는 연 뒤에 밖에서 바뀌었으면) 고치기 전 판을 휴지통에 남긴다. 같은 이름인가는
 *   원본 이름에도 같은 다듬기를 하고 대소문자를 가리지 않고 본다([sameName]).
 * - 형식이나 이름이 다르면('HWPX 형식으로 저장'·'다른 이름으로 저장') 원본 곁의 **새 파일**에. 있는 이름이면 `(2)` — 남의 파일을 덮지
 *   않는다. 같은 세션이 그 이름으로 다시 저장하면 그 새 파일을 바꿔 쓴다.
 *
 * 편집기가 댄 이름은 믿지 않는다 — 마지막 조각만, 파일 시스템이 받지 않는 글자를 빼고, 확장자는 형식의 것으로([cleanName]).
 *
 * ## 반영의 답은 참이어야 한다
 *
 * - 반영마다 `incoming` 을 **그 반영만의 파일로 옮긴 뒤** 큐에 넣는다([stage]) — 다음 저장이 `incoming` 을 다시 쓰는 동안 앞 요청이 그것을
 *   읽으면 잘린 문서가 원본 자리에 놓였다.
 * - 큐에 줄 서서 기다리는 것은 [QUEUE_WAIT_MS] 까지다. 넘으면 **줄에서 거두고**([FileOpManager.withdraw]) '바쁘다' 로 답한다. 이미 돌기 시작한
 *   요청은 끝을 기다려 그 결과로 답한다([ResultWait]). 예전에는 300초를 기다려 '저장하지 못했다' 고 답하면서 요청을 그대로 두어, 나중에 말없이
 *   원본을 바꿨다 — 그동안 편집기의 뒤로 가기는 몇 분씩 아무 일도 하지 않았다.
 * - 세션이 닫히면(편집기가 끝났다) 아직 줄 서 있는 그 세션의 반영을 거둔다([close]) — 결과를 받을 이가 없어진 저장이 원본을 몰래 바꾸지 않게.
 *
 * ## 기록
 *
 * 세션은 no-backup 폴더의 작은 JSON 에 남긴다 — 편집기가 앞에 있는 동안 Files 의 프로세스가 거둬지면 다음 저장이 새 프로세스로 오는데,
 * 그때도 같은 원본에 같은 규칙으로 써야 한다. 일주일 동안 쓰이지 않은 세션은 지운다(파일 쪽이 결과를 받지 못하고 끝난 길). 수가 상한을 넘으면
 * 가장 오래 쓰이지 않은 것부터 지우되 **편집기가 쥔 세션은 지우지 않는다**([hold] — 띄운 화면이 저장 상태로 표를 들고 있다).
 */
internal object EditorSlots {

    /** 열린 세션 하나. 바꿀 때는 새 값으로 갈아 끼운다. */
    data class Session(
        val token: String,
        /** 넘긴 문서(정규화한 경로). */
        val original: String,
        /** 넘긴 문서의 편집기 형식 글자. */
        val kind: String,
        /** 원본의 고치기 전 판을 이미 휴지통에 남겼다('+ 새 문서' 는 남길 것이 없어 처음부터 참이다). */
        val keptPrevious: Boolean,
        /** 원본을 마지막으로 본 표 — 연 때, 그리고 우리가 쓴 뒤. 다르면 그사이 밖에서 바뀌었다. */
        val stamp: FileStamp?,
        /** 이 세션이 만든 새 파일 — 편집기가 댄 이름(다듬은 것) → 실제 경로와 쓴 뒤의 표. */
        val created: Map<String, Created>,
        /** 반영한 저장의 수. */
        val saves: Int,
        /** 마지막으로 쓴 경로. */
        val last: String?,
        val openedAt: Long,
        /** 이 세션의 저장이 고치기 전 판을 휴지통에 실제로 남긴 적이 있다 — 돌아왔을 때 한 번 말한다([noticeOf]). */
        val trashed: Boolean = false,
        /** 마지막으로 쓰인 때(연 때·반영할 때) — 오래된 세션을 고르는 기준이다([evictable]·[expired]). */
        val usedAt: Long = openedAt,
    )

    data class Created(val path: String, val stamp: FileStamp?)

    /**
     * 반영 하나를 어디에 어떻게 쓸 것인가. [nameKey] 는 새 파일이면 그 이름(다음 저장이 같은 파일을 찾는 열쇠). [expected] 는 [path] 를 마지막으로
     * 본 표 — 큐 안에서 지금 표와 견준다([FileOpManager.Request.SaveFromEditor.expected]).
     */
    data class Plan(
        val path: String,
        val createNew: Boolean,
        val keepPrevious: Boolean,
        val nameKey: String?,
        val expected: FileStamp? = null,
    )

    /**
     * 세션을 닫은 뒤의 요약 — 무엇을 썼는가. [created] 는 이 세션이 만든 새 파일들, [trashed] 는 고치기 전 판을 휴지통에 남긴 적이 있는가.
     */
    data class Summary(
        val original: String,
        val last: String?,
        val saves: Int,
        val created: List<String> = emptyList(),
        val trashed: Boolean = false,
    )

    /** 돌아왔을 때 한 번 말할 것([noticeOf]) — 새로 만든 파일의 이름, 고치기 전 판을 휴지통에 남겼는가. */
    data class Notice(val newFile: String?, val trashed: Boolean)

    const val AUTHORITY_SUFFIX = ".editorslot"
    private const val SLOT = "slot"
    private const val FILE_NAME = "editor-slots.json"
    private const val DIR = "editor-slot"
    private const val INCOMING = "incoming"

    /** 반영 하나만의 바이트 — `incoming` 을 큐에 넣기 전에 이 이름으로 옮긴다([stage]). */
    private const val STAGED_PREFIX = "commit-"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * 세션 수의 상한. 넘으면 가장 오래 쓰이지 않은 것부터 지운다([evictable]). 보기 모드도 세션을 열므로(문서를 누를 때마다) 넉넉히 둔다 — 정상으로
     * 닫힌 세션은 남지 않고, 남는 것은 결과를 받지 못한 길(태스크를 지웠다)뿐이다.
     */
    private const val MAX_SESSIONS = 32

    /**
     * 반영이 큐에 **줄 서서** 기다리는 시간. 넘으면 거두고 '바쁘다' 로 답한다 — 앞 작업이 짧으면(휴지통·이름 바꾸기) 그 안에 차례가 오고, 길면(큰
     * 복사) 편집기가 '저장하는 중' 에 몇 분씩 묶이지 않는다(그동안 편집기의 뒤로 가기가 듣지 않는다). 돌기 시작한 반영은 끝까지 기다린다.
     */
    internal const val QUEUE_WAIT_MS = 15_000L

    private val lock = Any()
    private val random = SecureRandom()

    /** 편집기가 쥔 세션의 표([hold]) — 개수 상한·나이로 지우지 않는다. [lock] 아래에서만. */
    private val held = HashSet<String>()

    /** 반영 중인 요청(세션의 표 → 요청의 신원) — 세션이 닫히면 거둔다([close]). */
    private val pending = PendingCommits()

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    fun uriOf(context: Context, token: String): Uri =
        Uri.Builder().scheme("content").authority(authority(context)).appendPath(SLOT).appendPath(token).build()

    /** 자리의 표 — 꼴이 맞지 않으면 null. */
    fun tokenOf(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != SLOT) return null
        return segments[1].takeIf { TOKEN.matches(it) }
    }

    /**
     * 세션을 연다 — [path] 를 [kind] 로 편집기에 넘기기 직전에. 입출력이 있다(작은 JSON 과 폴더) — 주 스레드에서 부르지 않는다.
     * 연 세션은 편집기가 쥔 것으로 친다([hold]).
     *
     * @param fresh 방금 만든 빈 문서(폴더의 '+ 새 문서') — 첫 저장에 고치기 전 판(빈 문서)을 휴지통에 남기지 않는다.
     */
    fun open(context: Context, path: String, kind: String, fresh: Boolean = false): Session? {
        val canonical = try {
            File(path).canonicalPath
        } catch (e: IOException) {
            return null
        }
        val token = newToken()
        val session = Session(
            token = token,
            original = canonical,
            kind = kind,
            keptPrevious = fresh,
            stamp = FileStamp.of(File(canonical)),
            created = emptyMap(),
            saves = 0,
            last = null,
            openedAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            val all = load(context).toMutableMap()
            // 상한을 넘으면 오래 쓰이지 않은 것부터 걷는다 — 결과를 받지 못하고 끝난 세션이 쌓이지 않게. 편집기가 쥔 세션은 걷지 않는다.
            for (old in evictable(all.values, held.toSet(), MAX_SESSIONS)) {
                drop(context, old)
                all.remove(old)
            }
            all[token] = session
            held += token
            store(context, all)
        }
        slotDir(context, token).mkdirs()
        return session
    }

    /**
     * [token] 의 세션을 편집기가 쥐고 있다 — 개수 상한·나이로 지우지 않는다. 연 때 붙들고([open]) 닫을 때 놓는다([close]). 이 프로세스가 거둬졌다
     * 다시 섰으면 붙든 기록이 사라지므로, 편집기를 띄운 화면이 저장 상태로 든 표를 다시 붙든다(`Root`).
     */
    fun hold(token: String) {
        synchronized(lock) { held += token }
    }

    /**
     * 세션을 닫는다 — 아직 줄 서 있는 반영을 거두고 자리를 지운다. 무엇을 썼는지 돌려준다(모르는 표면 null). 입출력이 있다 — 주 스레드에서
     * 부르지 않는다.
     */
    fun close(context: Context, token: String): Summary? {
        val (session, unfinished) = synchronized(lock) {
            held -= token
            val all = load(context).toMutableMap()
            val s = all.remove(token)
            if (s != null) store(context, all)
            s to pending.drain(token)
        }
        // 편집기가 끝났다 — 결과를 받을 이가 없다. 아직 줄 서 있는 반영은 거둔다(나중에 원본을 몰래 바꾸지 않게). 돌기 시작한 것은
        // 그대로 끝난다(사용자가 누른 저장이다). 그 반영이 읽는 바이트도 아래에서 함께 지운다 — 거두기와 넣기가 겹쳐도 원본에 닿지 않는다.
        for (id in unfinished) FileOpManager.withdraw(id)
        drop(context, token)
        return session?.let { s ->
            Summary(s.original, s.last, s.saves, created = s.created.values.map { it.path }, trashed = s.trashed)
        }
    }

    fun session(context: Context, token: String): Session? = synchronized(lock) { load(context)[token] }

    /** 편집기가 쓰는 자리(앱 캐시). 세션이 없으면 null. */
    fun incoming(context: Context, token: String): File? {
        if (session(context, token) == null) return null
        return File(slotDir(context, token).apply { mkdirs() }, INCOMING)
    }

    /**
     * 반영 — 편집기가 [incoming] 에 다 쓰고 부른다([EditorSlotProvider.call], 바인더 스레드). 어디에 쓸지 정하고([plan]) 그 바이트를 이 반영만의
     * 파일로 옮긴 뒤([stage]) 파일 작업 큐에 넣어 끝을 기다리고([ResultWait]) 세션을 고친다. 답은 [EditorApp.KEY_OK]·[EditorApp.KEY_NAME](실제로
     * 쓴 파일의 이름)·[EditorApp.KEY_REASON].
     */
    fun commit(context: Context, token: String, kind: String?, name: String?): Bundle {
        val session = touch(context, token) ?: return failed(REASON_SESSION)
        if (kind == null || EditorApp.extensionOf(kind) == null) return failed(REASON_INVALID)
        val incoming = File(slotDir(context, token), INCOMING)
        if (!incoming.isFile || incoming.length() <= 0) return failed(REASON_INVALID)
        val plan = plan(session, kind, name) { p -> FileStamp.of(File(p)) } ?: return failed(REASON_INVALID)

        val id = "editor-" + UUID.randomUUID()
        val source = stage(incoming, id) ?: return failed(REASON_IO)
        val wait = ResultWait()
        val request = FileOpManager.Request.SaveFromEditor(
            id = id,
            path = plan.path,
            source = source.path,
            kind = kind,
            keepPrevious = plan.keepPrevious,
            createNew = plan.createNew,
            expected = plan.expected,
            sink = wait,
        )
        pending.add(token, id)
        val finished = try {
            FileOpManager.enqueue(context, request)
            wait.await(QUEUE_WAIT_MS) { FileOpManager.withdraw(id) }
        } finally {
            pending.remove(token, id)
            source.delete()
        }
        val reply = replyOf(finished)
        val saved = finished.extra as? FileOpManager.Extra.DocSaved
        if (reply.ok && saved != null) {
            record(context, token, plan, saved)
        } else {
            Iro.d { "편집기의 저장을 반영하지 못했다: ${reply.reason}" }
        }
        return Bundle().apply {
            putBoolean(EditorApp.KEY_OK, reply.ok)
            reply.name?.let { putString(EditorApp.KEY_NAME, it) }
            reply.reason?.let { putString(EditorApp.KEY_REASON, it) }
        }
    }

    /** 반영이 끝난 뒤 세션을 고친다. 그사이 세션이 닫혔으면(편집기가 끝났다) 되살리지 않는다 — 저장 자체는 끝났다. */
    private fun record(context: Context, token: String, plan: Plan, saved: FileOpManager.Extra.DocSaved) {
        synchronized(lock) {
            val all = load(context).toMutableMap()
            val current = all[token] ?: return
            val now = System.currentTimeMillis()
            val updated = if (plan.createNew || plan.nameKey != null) {
                current.copy(
                    created = current.created + (plan.nameKey!! to Created(saved.path, saved.stamp)),
                    saves = current.saves + 1,
                    last = saved.path,
                    trashed = current.trashed || saved.keptPrevious,
                    usedAt = now,
                )
            } else {
                current.copy(
                    keptPrevious = current.keptPrevious || saved.keptPrevious,
                    stamp = saved.stamp,
                    saves = current.saves + 1,
                    last = saved.path,
                    trashed = current.trashed || saved.keptPrevious,
                    usedAt = now,
                )
            }
            all[token] = updated
            store(context, all)
        }
    }

    /** [token] 의 세션을 쓰였다고 적고 돌려준다 — 없으면 null. */
    private fun touch(context: Context, token: String): Session? = synchronized(lock) {
        val all = load(context).toMutableMap()
        val s = all[token] ?: return@synchronized null
        val touched = s.copy(usedAt = System.currentTimeMillis())
        all[token] = touched
        store(context, all)
        touched
    }

    /**
     * 반영 하나를 어디에 쓸 것인가 — 판단만(JVM 시험). [now] 는 지금 그 경로의 표(없으면 null). 형식이 편집기의 것이 아니면 null.
     */
    internal fun plan(session: Session, kind: String, rawName: String?, now: (String) -> FileStamp?): Plan? {
        val ext = EditorApp.extensionOf(kind) ?: return null
        val original = File(session.original)
        val name = cleanName(rawName, ext, original.name)
        if (kind == session.kind && sameName(name, original.name, ext)) {
            val changed = now(session.original) != session.stamp
            return Plan(session.original, createNew = false, keepPrevious = !session.keptPrevious || changed, nameKey = null, expected = session.stamp)
        }
        // 이 세션이 만든 파일 — 대소문자만 다른 이름도 같은 파일이다(바깥 저장소는 가리지 않는다). 열쇠는 처음 적은 그대로 쓴다.
        val key = session.created.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        key?.let { session.created[it] }?.let { made ->
            val current = now(made.path)
            // 우리가 만든 파일이 그대로 있으면 그것을 바꿔 쓴다(밖에서 바뀌었으면 그 판을 휴지통에). 지워졌으면 새로 만든다.
            if (current != null) return Plan(made.path, createNew = false, keepPrevious = current != made.stamp, nameKey = key, expected = made.stamp)
        }
        val dir = original.parentFile?.path ?: return null
        return Plan(File(dir, name).path, createNew = true, keepPrevious = false, nameKey = key ?: name)
    }

    /**
     * 편집기가 댄 이름(다듬은 것 — [name])이 원본([originalName])과 같은 이름인가 — 판단만(JVM 시험). 원본 이름에도 **같은 다듬기**([cleanName])를
     * 하고 견준다. 편집기 쪽 이름은 엔진(rhwp 는 확장자를 소문자로 갈아 끼운다)·편집기의 `OutputNames`·[cleanName] 을 지나며
     * 다듬어지는데 원본은 그대로 견주어, 대문자 확장자(`공문.HWP`)·점으로 시작하는 이름·확장자 앞의 빈칸·금지 글자·긴 이름이 영영 제자리에
     * 저장되지 않고 열 때마다 `(2)`·`(3)` 이 쌓였다. 대소문자는 가리지 않는다 — 바깥 저장소(FUSE·FAT·exFAT)가 가리지 않으므로 같은 파일이다.
     * 원본의 확장자가 그 형식의 것이 아니면 같은 이름이 아니다(곁에 새 파일 — 원본의 형식을 바꿔 쓰지 않는다).
     */
    internal fun sameName(name: String, originalName: String, ext: String): Boolean =
        originalName.substringAfterLast('.', "").trim().equals(ext, ignoreCase = true) &&
            name.equals(cleanName(originalName, ext, originalName), ignoreCase = true)

    /**
     * 편집기가 댄 이름을 다듬는다 — 판단만(JVM 시험). 마지막 조각만, 제어 문자와 흔한 파일 시스템(FAT·exFAT·NTFS)이 받지 않는 글자를
     * 빼고, 앞뒤의 점·빈칸을 걷고, 길이를 묶고, 확장자는 [ext] 로 맞춘다. 남는 것이 없으면 [originalName] 의 이름 부분으로.
     *
     * 길이는 **바이트로** 묶는다 — 255바이트(ext4·F2FS 의 이름 상한)에서 확장자 몫을 뺀 만큼. 글자 수(120)로만 묶었더니
     * 한글 90자 이름이 이름 맡기에서 실패해 '저장하지 못했습니다' 만 남았다. 글자로도 [MAX_BASE] 까지다 — 편집기 쪽(`OutputNames`)이 같은 자리에서
     * 자르므로, 긴 이름의 같은 이름 판정([sameName])이 맞는다.
     */
    internal fun cleanName(rawName: String?, ext: String, originalName: String): String {
        val room = PathRules.MAX_NAME_BYTES - ".$ext".toByteArray(Charsets.UTF_8).size
        fun tidy(s: String) = s.trim { it.isWhitespace() || it == '.' }
        fun base(n: String): String {
            var b = BAD.replace(n.substringAfterLast('/').substringAfterLast('\\'), "").trim()
            // 확장자를 먼저 뗀다 — 점·빈칸을 걷은 뒤에 보면 `.hwp` 가 `hwp` 라는 이름이 된다. 점 둘레의 빈칸(`보고서. hwp`)도 확장자로 본다.
            val dot = b.lastIndexOf('.')
            if (dot >= 0 && b.substring(dot + 1).trim().lowercase(Locale.ROOT) in DOC_EXTS) b = b.substring(0, dot)
            b = tidy(b)
            // 글자(UTF-16)로 자르는 자리는 편집기 쪽 `OutputNames` 와 같다 — 서로게이트 쌍의 앞 반쪽에서 자르지 않는다.
            if (b.length > MAX_BASE) b = tidy(b.take(if (Character.isHighSurrogate(b[MAX_BASE - 1])) MAX_BASE - 1 else MAX_BASE))
            return tidy(PathRules.cutToBytes(b, room))
        }
        val b = base(rawName.orEmpty()).ifEmpty { base(originalName) }.ifEmpty { "document" }
        return "$b.$ext"
    }

    /**
     * 편집기가 다 쓴 [incoming] 을 이 반영([id])만의 파일로 옮긴다 — 같은 폴더 안의 rename 이라 원자적이다. 다음 저장은 새 `incoming` 을
     * 만든다([EditorSlotProvider.openFile] — 자르고 연다). 옮기지 못하면 null. (JVM 시험 — 파일시스템만 쓴다.)
     */
    internal fun stage(incoming: File, id: String): File? {
        val staged = File(incoming.parentFile, STAGED_PREFIX + id)
        return if (incoming.renameTo(staged)) staged else null
    }

    /** 반영의 답 — [reason] 은 편집기에 보낼 까닭 글자(편집기가 제 문장으로 옮긴다). */
    internal data class Reply(val ok: Boolean, val name: String? = null, val reason: String? = null)

    /** 큐의 결과 → 반영의 답. 판단만(JVM 시험). */
    internal fun replyOf(finished: FileOpManager.Finished): Reply {
        val saved = finished.extra as? FileOpManager.Extra.DocSaved
        return when (val outcome = finished.outcome) {
            // 줄 서 있다 거두었다(앞 작업이 길다 — [ResultWait]) — 쓰지 않았다. 알림의 '전부 취소' 로 거둔 것도 여기다.
            is FileOpEngine.Outcome.Cancelled -> Reply(false, reason = REASON_BUSY)
            is FileOpEngine.Outcome.Failed -> Reply(false, reason = reasonOf(outcome.reason))
            is FileOpEngine.Outcome.Done -> if (saved != null) Reply(true, name = File(saved.path).name) else Reply(false, reason = REASON_IO)
        }
    }

    /**
     * 돌아왔을 때 한 번 말할 것 — 판단만(JVM 시험). 새 파일을 만들었으면 그 이름(마지막으로 쓴 것이 새 파일이면 그것, 아니면 마지막으로 만든 것),
     * 고치기 전 판을 휴지통에 남겼으면 그것(글 편집기는 저장할 때 말하는데 편집기의 저장은 어디서도 말하지 않았다). 둘 다 없으면 null.
     */
    internal fun noticeOf(summary: Summary): Notice? {
        val newFile = summary.last?.takeIf { it != summary.original } ?: summary.created.lastOrNull()
        if (newFile == null && !summary.trashed) return null
        return Notice(newFile?.let { File(it).name }, summary.trashed)
    }

    /**
     * 새 세션 하나를 더하기 전에 걷을 세션들 — 판단만(JVM 시험). 상한([max])을 넘는 만큼 **가장 오래 쓰이지 않은 것부터**, 편집기가 쥔 것
     * ([held])은 빼고(예전에는 연 때가 가장 이른 것을 지워, 다른 태스크에 떠 있는 편집기의 세션이 사라질 수 있었다. 그
     * 편집기의 저장은 모두 실패한다). 쥔 것만 남았으면 상한을 넘겨 둔다 — 사람이 띄운 편집기의 수만큼이다.
     */
    internal fun evictable(all: Collection<Session>, held: Set<String>, max: Int): List<String> {
        val over = all.size + 1 - max
        if (over <= 0) return emptyList()
        return all.filter { it.token !in held }.sortedBy { it.usedAt }.take(over).map { it.token }
    }

    /**
     * 오래 쓰이지 않았는가 — 판단만(JVM 시험). 쓰인 때가 지금보다 뒤면(시계를 뒤로 돌렸다) 오래된 것이 아니다(예전에는 시계가
     * 뒤로 가면 살아 있는 세션까지 지웠다).
     */
    internal fun expired(session: Session, now: Long): Boolean = now - session.usedAt > MAX_AGE_MS

    /**
     * 반영 하나의 결과를 기다리는 자리 — 큐의 받는 자리([FileOpManager.ResultSink])이자 기다림. 큐의 작업 스레드(또는 거두는 스레드)가 [offer] 를
     * 부르고, 반영을 부른 바인더 스레드가 [await] 에서 기다린다.
     */
    internal class ResultWait : FileOpManager.ResultSink {
        private val result = AtomicReference<FileOpManager.Finished?>(null)
        private val latch = CountDownLatch(1)

        override fun offer(finished: FileOpManager.Finished): Boolean {
            if (result.compareAndSet(null, finished)) latch.countDown()
            return true
        }

        /**
         * [queueWaitMs] 안에 끝나면 그 결과. 넘으면 [withdraw] 로 줄에서 거두어 본다 — 거두었으면 큐가 그 자리에서 '취소' 를 여기에 주고, 이미 돌기
         * 시작했으면(거두지 못했다) **끝날 때까지** 기다려 그 결과를 돌려준다. 거짓으로 '바쁘다' 하지 않는다 — 큐는 결과를 어느 길로든 정확히 한 번
         * 낸다(`FileOpManager.deliver`). 도는 반영은 512 MiB 를 옮겨 적는 시간으로 묶인다.
         */
        fun await(queueWaitMs: Long, withdraw: () -> Boolean): FileOpManager.Finished {
            if (!latch.await(queueWaitMs, TimeUnit.MILLISECONDS)) {
                withdraw()
                latch.await()
            }
            return checkNotNull(result.get())
        }
    }

    /** 반영 중인 요청 — 세션의 표 → 요청의 신원들. 세션이 닫히면 [drain] 으로 꺼내 거둔다. */
    internal class PendingCommits {
        private val byToken = HashMap<String, MutableSet<String>>()

        @Synchronized
        fun add(token: String, id: String) {
            byToken.getOrPut(token) { LinkedHashSet() } += id
        }

        @Synchronized
        fun remove(token: String, id: String) {
            val ids = byToken[token] ?: return
            ids -= id
            if (ids.isEmpty()) byToken -= token
        }

        @Synchronized
        fun drain(token: String): List<String> = byToken.remove(token)?.toList().orEmpty()
    }

    private fun failed(reason: String): Bundle = Bundle().apply {
        putBoolean(EditorApp.KEY_OK, false)
        putString(EditorApp.KEY_REASON, reason)
    }

    /** 큐의 까닭 → 편집기에 보낼 글자(편집기가 제 문장으로 옮긴다). */
    private fun reasonOf(reason: FileOpEngine.Reason?): String = when (reason) {
        FileOpEngine.Reason.NOT_FOUND -> REASON_GONE
        FileOpEngine.Reason.NO_SPACE -> REASON_SPACE
        FileOpEngine.Reason.PREVIOUS_NOT_KEPT -> REASON_PREVIOUS
        FileOpEngine.Reason.VERIFY_FAILED -> REASON_INVALID
        FileOpEngine.Reason.PERMISSION -> REASON_DENIED
        else -> REASON_IO
    }

    private fun slotDir(context: Context, token: String): File = File(File(context.cacheDir, DIR), token)

    private fun drop(context: Context, token: String) {
        slotDir(context, token).deleteRecursively()
    }

    private fun newToken(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    // ---- 기록(no-backup 의 JSON) — lock 을 쥐고 부른다 ------------------------------------------------------------------

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    private fun load(context: Context): Map<String, Session> {
        val text = try {
            String(file(context).readFully(), Charsets.UTF_8)
        } catch (e: IOException) {
            return emptyMap()
        }
        val now = System.currentTimeMillis()
        val out = LinkedHashMap<String, Session>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val s = sessionOf(arr.getJSONObject(i)) ?: continue
                // 편집기가 쥔 세션은 나이로 지우지 않는다.
                if (s.token !in held && expired(s, now)) {
                    drop(context, s.token)
                    continue
                }
                out[s.token] = s
            }
        } catch (e: JSONException) {
            Iro.d { "저장 자리의 기록을 읽지 못했다 — 비운다" }
        }
        return out
    }

    private fun store(context: Context, all: Map<String, Session>) {
        val arr = JSONArray()
        for (s in all.values) arr.put(jsonOf(s))
        val f = file(context)
        val out = try {
            f.startWrite()
        } catch (e: IOException) {
            Iro.d { "저장 자리의 기록을 쓰지 못했다" }
            return
        }
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: IOException) {
            f.failWrite(out)
        }
    }

    private fun jsonOf(s: Session): JSONObject = JSONObject()
        .put("token", s.token)
        .put("original", s.original)
        .put("kind", s.kind)
        .put("kept", s.keptPrevious)
        .put("stamp", s.stamp?.let { stampJson(it) } ?: JSONObject.NULL)
        .put("created", JSONArray().apply {
            for ((name, c) in s.created) {
                put(JSONObject().put("name", name).put("path", c.path).put("stamp", c.stamp?.let { stampJson(it) } ?: JSONObject.NULL))
            }
        })
        .put("saves", s.saves)
        .put("last", s.last ?: JSONObject.NULL)
        .put("openedAt", s.openedAt)
        .put("trashed", s.trashed)
        .put("usedAt", s.usedAt)

    private fun stampJson(t: FileStamp) = JSONObject().put("size", t.size).put("modified", t.modified)

    private fun stampOf(o: JSONObject?): FileStamp? = o?.let { FileStamp(it.getLong("size"), it.getLong("modified")) }

    private fun sessionOf(o: JSONObject): Session? = try {
        val token = o.getString("token")
        if (!TOKEN.matches(token)) {
            null
        } else {
            val created = LinkedHashMap<String, Created>()
            val arr = o.optJSONArray("created") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                created[c.getString("name")] = Created(c.getString("path"), stampOf(c.optJSONObject("stamp")))
            }
            val openedAt = o.getLong("openedAt")
            Session(
                token = token,
                original = o.getString("original"),
                kind = o.getString("kind"),
                keptPrevious = o.optBoolean("kept"),
                stamp = stampOf(o.optJSONObject("stamp")),
                created = created,
                saves = o.optInt("saves"),
                last = if (o.isNull("last")) null else o.optString("last"),
                openedAt = openedAt,
                // 2026-10-10 전의 기록에는 없다 — 연 때로 본다.
                trashed = o.optBoolean("trashed"),
                usedAt = o.optLong("usedAt", openedAt),
            )
        }
    } catch (e: JSONException) {
        null
    }

    private val TOKEN = Regex("[0-9a-f]{32}")
    private val BAD = Regex("""[\u0000-\u001f\u007f<>:"|?*]""")
    private val DOC_EXTS = EditorApp.extensions + "hml"

    /** 이름 부분의 글자 상한 — 편집기 쪽(`OutputNames`)과 같다. 바이트 상한은 [cleanName] 이 따로 본다. */
    private const val MAX_BASE = 120

    /** 원본을 찾지 못했다(지워졌다·옮겨졌다·폴더가 사라졌다). */
    const val REASON_GONE = "gone"

    /** 이 표의 세션이 없다(닫혔다·기록을 잃었다·오래되어 지웠다) — 다른 이름으로 저장해도 같다(예전에는 [REASON_GONE] 이었다). */
    const val REASON_SESSION = "session"
    const val REASON_SPACE = "space"
    const val REASON_PREVIOUS = "previous"
    const val REASON_INVALID = "invalid"
    const val REASON_DENIED = "denied"
    const val REASON_BUSY = "busy"
    const val REASON_IO = "io"
}
