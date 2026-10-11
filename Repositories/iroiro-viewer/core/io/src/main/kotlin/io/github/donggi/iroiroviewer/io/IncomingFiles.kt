package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * **다른 앱이 넘긴 파일**(`ACTION_VIEW`·`ACTION_SEND` 의 URI)을 이 앱의 뷰어가 여는 **경로**로 바꾼다(14단계 뒤 — 사용자
 * 요청 '다른 앱에서 이 앱으로 열 수 없음 => 추가해주세요').
 *
 * 이 앱의 뷰어는 전부 경로를 받는다 — pdfium 은 되감을 수 있는 파일 서술자를, 압축·만화는 임의 접근을, 이어보기는 크기·이름·
 * 수정 시각을 원한다. 그래서 길이 둘이다.
 *
 * - **제자리**([Received.InPlace]) — URI 가 저장 볼륨(`/storage/`)의 파일을 가리키고 그 자리를 **확실히** 알 수 있으면 그 파일을
 *   그대로 연다. 큰 영상도 곧바로 열리고, 압축은 그 옆에 풀린다. 모든 파일 접근 권한이 있어야 한다(없으면 사본이다).
 * - **사본**([Received.Copied]) — 그 밖(메일·메신저의 첨부, 브라우저가 받은 것)은 앱 캐시의 `share/inbox/<무작위>/` 에 복사한다.
 *   사본은 **읽기 전용으로 다룬다** — 화면이 지우기·회전·풀기를 내지 않는다(보낸 쪽의 원본이 아니고, 캐시는 사용자가 찾아갈 수
 *   없는 자리다). 공유와 '다른 앱으로 열기' 는 된다(`ShareHelper` 가 `share/` 를 허락한다). 다 보면 지운다([discardLater]) —
 *   메일의 명세서 같은 첨부가 캐시에 쌓이지 않게. 지우지 못하고 남은 것(재생기에 넘긴 사본 등)은 다음에 받을 때 하루가 지난 것부터
 *   치운다([purgeStale]).
 *
 * ## 제자리를 '확실히 안다' 는 것
 *
 * 시스템의 제공자 셋은 경로를 정해진 모양으로 적는다 — 저장소 문서(`primary:Download/a.pdf`), 내려받기(`raw:/storage/…`·
 * `msf:번호`), 미디어 저장소(`_data` 칸). 다른 앱의 제공자는 모양이 제각각이라 **URI 의 경로 안에 `/storage/…` 가 통째로 들어
 * 있고, 그 파일의 이름과 크기가 제공자가 말한 것과 같을 때만** 믿는다(Files by Google 의 꼴). 맞히지 못하면 사본이다 — 틀린 파일을
 * 여는 것보다 복사가 낫다.
 *
 * ## 막는 것
 *
 * - 스킴은 `content`·`file` 둘. `file:` 과 제자리는 **정규화한 경로가 `/storage/` 아래**일 때만 받는다 — 우리 앱의 내부 파일
 *   (DB·설정)을 가리키는 URI 로 우리를 속여 그것을 화면에 띄우게 할 수 없다. 휴지통 안도 받지 않는다.
 * - **우리 자신의 제공자**를 가리키는 URI 는 받지 않는다. 우리는 우리 제공자를 허락 없이 열 수 있으므로, 남이 그 URI 를 지어
 *   보내면 보낸 쪽이 못 읽는 파일을 우리가 대신 열게 된다. 권한자 앞의 사용자 번호(`0@…`)는 떼고 견준다 — `ContentResolver` 는
 *   그것을 떼고 같은 사용자의 제공자를 연다.
 * - 이름은 제공자가 적은 글이다. 마지막 조각만 쓰고 `PathRules.sanitize` 를 지나며, 사본 폴더 밖이 아닌지 정규화한 자리로 한 번
 *   더 본다.
 * - 제공자가 말한 크기는 믿지 않는다(진행률의 분모로만 쓴다). 복사하면서 볼륨의 남은 공간을 보고 [RESERVE_BYTES] 밑으로 내려가면
 *   멈춘다 — 끝없는 스트림도 여기서 끊긴다.
 *
 * **취소.** 복사는 조각마다 취소를 본다. 제공자가 읽기 한 번에서 멎으면 그 스레드는 돌아올 때까지 남는다 — 화면은 기다리지 않고
 * 닫히고, 돌아온 뒤 남은 조각은 지운다.
 */
object IncomingFiles {

    sealed interface Received {
        val path: String

        /** 저장 볼륨의 그 파일 그대로. */
        data class InPlace(override val path: String) : Received

        /** 앱 캐시에 둔 사본. 읽기 전용으로 다룬다. */
        data class Copied(override val path: String) : Received
    }

    enum class Failure {
        /** 파일을 가리키지 않거나(스킴·우리 자신의 제공자), 읽을 수 없다(권한·사라짐·제공자의 오류). */
        UNREADABLE,

        /** 사본을 둘 자리가 모자라다. */
        NO_SPACE,
    }

    sealed interface Outcome {
        data class Ok(val received: Received) : Outcome
        data class Failed(val failure: Failure) : Outcome
    }

    /** 복사의 진행. [total] 은 모르면 -1 이다. 입출력 스레드에서 불린다. */
    fun interface Progress {
        fun onProgress(copied: Long, total: Long)
    }

    /** 사본을 두는 폴더의 이름(`share/` 아래). */
    const val INBOX_DIR = "inbox"

    /** 복사하면서 볼륨에 남겨 둘 공간. 이 밑으로 내려가면 멈춘다 — 기기 전체가 저장 공간 부족에 빠지지 않게. */
    const val RESERVE_BYTES = 256L * 1024 * 1024

    /** 이만큼 지난 사본은 다음에 받을 때 치운다. */
    const val STALE_MS = 24L * 60 * 60 * 1000

    @StringRes
    fun messageOf(failure: Failure): Int = when (failure) {
        Failure.UNREADABLE -> R.string.io_incoming_unreadable
        Failure.NO_SPACE -> R.string.io_incoming_no_space
    }

    /**
     * [uri] 를 연다. 제자리면 곧바로, 아니면 복사한다. 입출력 디스패처에서 돈다.
     *
     * @param mimeType 보낸 쪽이 적은 형식. 이름에 확장자가 없을 때만 쓴다(뷰어는 확장자로 종류를 정한다).
     * @param onCopied 사본을 다 만든 **바로 그때** 그 경로를 준다 — 돌려주기 전이다. 그 사이에 취소되면 결과는 버려지지만 사본은
     *   남으므로, 지울 사람이 경로를 먼저 쥐어야 한다(CLAUDE.md 함정 표의 '코루틴 경계 너머로 … 돌려주지 마라' 와 같은 까닭).
     */
    suspend fun receive(
        context: Context,
        uri: Uri,
        mimeType: String?,
        progress: Progress,
        onCopied: (String) -> Unit = {},
    ): Outcome = withContext(IroDispatchers.io) {
        val authority = authorityOf(uri)
        when (uri.scheme?.lowercase()) {
            // `file:` 은 제자리로 확인된 경로만 읽는다 — 그 밖의 경로(앱 내부 파일)는 사본으로도 읽지 않는다. 이름이 종류를 말하지
            // 않으면 그 자리를 사본으로 옮겨 형식의 확장자를 붙인다(content 와 같은 규칙).
            SCHEME_FILE -> {
                val path = inPlace(uri.path)
                when {
                    path == null -> Outcome.Failed(Failure.UNREADABLE)
                    namesItsKind(path, mimeType) -> Outcome.Ok(Received.InPlace(path))
                    else -> File(path).let { f ->
                        copy(context, Uri.fromFile(f), mimeType, Meta(f.name, f.length(), f.lastModified()), progress, onCopied)
                    }
                }
            }
            SCHEME_CONTENT -> {
                if (isOwnProvider(context, authority)) {
                    Outcome.Failed(Failure.UNREADABLE)
                } else {
                    val meta = metaOf(context, uri)
                    val real = realPathOf(context, uri, authority, meta)?.let(::inPlace)
                    when {
                        real == null -> copy(context, uri, mimeType, meta, progress, onCopied)
                        namesItsKind(real, mimeType) -> Outcome.Ok(Received.InPlace(real))
                        // 자리는 아는데 이름이 종류를 말하지 않는다 — **그 자리에서** 복사해 형식의 확장자를 붙인다. 제공자를 거치면
                        // 보낸 쪽이 허락을 싣지 않은 URI(제자리로 열 수 있어서 허락이 필요 없던 것)가 여기서 막힌다.
                        else -> File(real).let { f ->
                            copy(context, Uri.fromFile(f), mimeType, Meta(meta.name ?: f.name, f.length(), f.lastModified()), progress, onCopied)
                        }
                    }
                }
            }
            else -> Outcome.Failed(Failure.UNREADABLE)
        }
    }

    /**
     * 제자리로 열어도 뷰어가 종류를 알아보는가. 이름이 종류를 말하지 않는데(확장자 없음·모르는 확장자) 보낸 형식은 아는 종류면
     * 사본으로 간다 — 사본은 형식의 확장자를 붙여 이름을 짓는다([nameFor]). 그러지 않으면 같은 파일이 모든 파일 접근 권한이 있을
     * 때는 '보여 줄 화면이 없다' 로, 없을 때는 제대로 열린다.
     */
    private fun namesItsKind(path: String, mimeType: String?): Boolean =
        MimeResolver.kindOf(File(path).name, isDirectory = false) != FileKind.OTHER ||
            extensionFor(mimeType) { mime -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) } == null

    /** 권한자에서 사용자 번호(`0@`)를 뗀 것. 남의 사용자에게서 넘어온 URI 도 같은 규칙으로 가른다. */
    internal fun authorityOf(uri: Uri): String? = uri.authority?.let(::bareAuthority)

    internal fun bareAuthority(authority: String): String = authority.substringAfterLast('@')

    /**
     * 되살린 경로(프로세스가 죽었다 살아난 받기 화면)를 그대로 써도 되는가 — 저장 상태는 우리가 쓴 것이지만 한 번 더 본다.
     * 사본이라고 적혔으면 정말 우리 사본 폴더 안이어야 하고, 제자리라고 적혔으면 지금도 제자리로 열려야 한다.
     * 디스크를 읽으므로 입출력 디스패처에서 부른다.
     */
    fun stillUsable(context: Context, path: String, copy: Boolean): Boolean =
        if (copy) isCopy(context, path) && File(path).isFile else inPlace(path) == path

    /** [path] 가 우리 사본인가. */
    fun isCopy(context: Context, path: String): Boolean = copyDirOf(context, path) != null

    /**
     * [path] 가 우리 사본이면 그 폴더째 지운다 — 다른 스레드에서, 기다리지 않고. 화면이 끝날 때 부른다(`ViewModel.onCleared`).
     * 사본이 아니면 아무것도 하지 않는다 — 제자리의 파일은 사용자의 것이다.
     */
    fun discardLater(context: Context, path: String) {
        val app = context.applicationContext
        cleanup.launch { copyDirOf(app, path)?.deleteRecursively() }
    }

    /**
     * 하루가 지난 사본을 치운다. 받을 때와 프로세스가 설 때 부른다(입출력 디스패처에서) — 재생기에 넘긴 사본은 화면이 끝나도
     * 남기므로 누군가는 치워야 한다.
     *
     * @param keep 아직 쓰는 파일인가(재생기가 들고 있는 것). 그 사본은 하루가 지났어도 남긴다 — 하루 넘게 멈춰 둔 재생의 파일을
     *   발밑에서 지우면 다시 틀 때 '없는 파일' 이 된다.
     */
    fun purgeStale(context: Context, now: Long = System.currentTimeMillis(), keep: (File) -> Boolean = { false }) {
        val dirs = inboxDir(context).listFiles() ?: return
        for (d in dirs) {
            if (!d.isDirectory || now - d.lastModified() <= STALE_MS) continue
            if (d.listFiles()?.any(keep) == true) continue
            d.deleteRecursively()
        }
    }

    /** [purgeStale] 을 다른 스레드에서, 기다리지 않고. 앱이 설 때 부른다. */
    fun purgeStaleLater(context: Context, keep: (File) -> Boolean = { false }) {
        val app = context.applicationContext
        cleanup.launch { purgeStale(app, keep = keep) }
    }

    // ---- 이름 ----------------------------------------------------------------------------------

    /**
     * 사본의 이름. 제공자가 적은 이름([raw])의 **마지막 조각**을 다듬는다. 확장자가 없거나 이 앱이 모르는 확장자면 보낸 형식
     * ([mimeType])의 확장자를 붙인다 — `download` + `application/pdf` 는 `download.pdf`. 이 앱이 아는 확장자가 있으면 그대로 둔다
     * (형식 문자열보다 이름이 대개 정확하다).
     *
     * @param extensionOfMime 형식 → 확장자(플랫폼 표). 시험이 바꿔 끼운다.
     */
    internal fun nameFor(raw: String?, mimeType: String?, extensionOfMime: (String) -> String?): String {
        val last = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        val base = if (last.isEmpty() || last == "." || last == "..") FALLBACK_NAME else last
        val known = MimeResolver.kindOf(base, isDirectory = false) != FileKind.OTHER
        val ext = if (known) null else extensionFor(mimeType, extensionOfMime)
        return PathRules.sanitize(if (ext != null) "$base.$ext" else base)
    }

    /** 형식 → 확장자. 이 앱이 아는 종류가 되는 확장자만 준다(모르는 것을 붙여 봐야 이름만 길어진다). */
    internal fun extensionFor(mimeType: String?, extensionOfMime: (String) -> String?): String? {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() && '*' !in it } ?: return null
        val ext = OUR_EXTENSIONS[mime] ?: extensionOfMime(mime)?.lowercase() ?: return null
        return ext.takeIf { it.isNotEmpty() && MimeResolver.kindOf("x.$it", isDirectory = false) != FileKind.OTHER }
    }

    /**
     * 플랫폼 표(`MimeTypeMap`)가 모를 수 있는 형식. 한글·만화·압축은 기기마다 표가 다르다(`MimeResolver` 의 KDoc). 받는 쪽에서
     * 흔히 보는 이름을 함께 적는다 — 형식 문자열은 보낸 앱이 짓는 것이라 모양이 여럿이다.
     */
    private val OUR_EXTENSIONS: Map<String, String> = mapOf(
        "application/x-hwp" to "hwp",
        "application/haansofthwp" to "hwp",
        "application/vnd.hancom.hwp" to "hwp",
        "application/hwp" to "hwp",
        "application/hwp+zip" to "hwpx",
        "application/haansofthwpx" to "hwpx",
        "application/vnd.hancom.hwpx" to "hwpx",
        "application/vnd.comicbook+zip" to "cbz",
        "application/x-cbz" to "cbz",
        "application/vnd.comicbook-rar" to "cbr",
        "application/x-cbr" to "cbr",
        "application/x-cb7" to "cb7",
        "application/x-cbt" to "cbt",
        "application/x-7z-compressed" to "7z",
        "application/vnd.rar" to "rar",
        "application/x-rar-compressed" to "rar",
        "application/epub+zip" to "epub",
        "text/markdown" to "md",
        "text/x-markdown" to "md",
    )

    // ---- 제자리 --------------------------------------------------------------------------------

    /** [path] 가 저장 볼륨의 열리는 파일이면 그 정규화한 경로. 권한이 없으면 null 이다(그때는 사본으로 간다). */
    private fun inPlace(path: String?): String? {
        if (path.isNullOrEmpty() || !StorageAccess.isGranted()) return null
        val file = try {
            File(path).canonicalFile
        } catch (e: IOException) {
            return null
        }
        if (!isUnderStorage(file.path) || TrashStore.isTrashPath(file.path)) return null
        return file.path.takeIf { FileProbe.opens(file) }
    }

    /** 정규화한 경로가 저장 볼륨 아래인가. 빗금까지 견준다 — `/storagex/…` 는 아니다. */
    internal fun isUnderStorage(canonicalPath: String): Boolean = canonicalPath.startsWith(STORAGE_ROOT)

    private fun realPathOf(context: Context, uri: Uri, authority: String?, meta: Meta): String? = when (authority) {
        EXTERNAL_STORAGE_AUTHORITY -> documentId(context, uri)?.let { id ->
            storagePathOf(id) { root -> volumeDir(context, root) }
        }
        DOWNLOADS_AUTHORITY -> documentId(context, uri)?.let { id -> downloadsPath(context, id) }
        MediaStore.AUTHORITY -> queryString(context, uri, MediaStore.MediaColumns.DATA)
        else -> embeddedPath(uri.path)?.takeIf { matches(File(it), meta) }
    }

    /**
     * 저장소 문서의 문서 id(`primary:Download/a.pdf`·`1A2B-3C4D:Music/b.mp3`·`home:c.txt`)를 경로로. [volumeDir] 가 앞 조각을
     * 볼륨의 폴더로 바꾼다. 모양이 아니면 null.
     */
    internal fun storagePathOf(docId: String, volumeDir: (String) -> String?): String? {
        val colon = docId.indexOf(':')
        if (colon <= 0) return null
        val base = volumeDir(docId.substring(0, colon)) ?: return null
        val rest = docId.substring(colon + 1).trimStart('/')
        return if (rest.isEmpty()) base else "$base/$rest"
    }

    private fun volumeDir(context: Context, root: String): String? {
        val sm = context.getSystemService<StorageManager>() ?: return null
        val primary = sm.primaryStorageVolume.directory?.path
        return when (root) {
            "primary" -> primary
            // 저장소 문서 제공자의 '문서' 뿌리 — 기본 볼륨의 Documents 폴더다.
            "home" -> primary?.let { "$it/Documents" }
            else -> sm.storageVolumes.firstOrNull { it.uuid.equals(root, ignoreCase = true) }?.directory?.path
        }
    }

    /** 내려받기 제공자의 문서 id. `raw:` 는 경로 그대로, `msf:` 는 미디어 저장소의 번호다. 옛 숫자 id 는 모른다(사본). */
    private fun downloadsPath(context: Context, id: String): String? = when {
        id.startsWith(RAW_PREFIX) -> id.substring(RAW_PREFIX.length)
        id.startsWith(MSF_PREFIX) -> id.substring(MSF_PREFIX.length).toLongOrNull()?.let { n ->
            queryString(context, MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, n), MediaStore.MediaColumns.DATA)
        }
        else -> null
    }

    /** URI 경로 안에 통째로 든 `/storage/…`. `uri.path` 는 이미 풀린 글이라 `file%3A%2F%2F…` 도 여기서는 `file:///…` 다. */
    internal fun embeddedPath(uriPath: String?): String? {
        val at = uriPath?.indexOf(STORAGE_ROOT) ?: return null
        return if (at < 0) null else uriPath.substring(at)
    }

    /** 남의 제공자가 가리키는 듯한 파일이 그것인가 — 이름과 크기를 **둘 다** 알아야 하고 둘 다 같아야 한다. */
    private fun matches(file: File, meta: Meta): Boolean =
        meta.name != null && meta.size >= 0 && file.name == meta.name && file.isFile && file.length() == meta.size

    private fun documentId(context: Context, uri: Uri): String? = try {
        if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else null
    } catch (e: IllegalArgumentException) {
        null
    }

    // ---- 우리 자신 -----------------------------------------------------------------------------

    private fun isOwnProvider(context: Context, authority: String?): Boolean {
        if (authority.isNullOrEmpty()) return true
        val own = context.packageName
        if (authority == own || authority.startsWith("$own.")) return true
        @Suppress("DEPRECATION") // ProviderInfoFlags 판은 API 33 부터다(minSdk 31). 정수 판은 뜻이 같다.
        val owner = context.packageManager.resolveContentProvider(authority, 0)?.packageName
        return owner == own
    }

    // ---- 제공자에게 묻기 -----------------------------------------------------------------------

    /** 제공자가 말한 것. 이름은 아직 다듬지 않았다. 모르면 null·-1·0. */
    private class Meta(val name: String?, val size: Long, val lastModified: Long)

    private fun metaOf(context: Context, uri: Uri): Meta {
        val document = documentId(context, uri) != null
        val columns = buildList {
            add(OpenableColumns.DISPLAY_NAME)
            add(OpenableColumns.SIZE)
            if (document) add(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        }.toTypedArray()
        return try {
            context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun at(column: String): Int? = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
                Meta(
                    name = at(OpenableColumns.DISPLAY_NAME)?.let(c::getString),
                    size = at(OpenableColumns.SIZE)?.let(c::getLong) ?: -1L,
                    lastModified = at(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.let(c::getLong) ?: 0L,
                )
            } ?: UNKNOWN
        } catch (e: RuntimeException) {
            // 제공자는 남의 코드다 — 모르는 칸·권한·내부 오류가 무엇으로든 올 수 있다. 모르면 모르는 채로 복사한다.
            if (e is CancellationException) throw e
            UNKNOWN
        }
    }

    private fun queryString(context: Context, uri: Uri, column: String): String? = try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: RuntimeException) {
        if (e is CancellationException) throw e
        null
    }

    // ---- 사본 ----------------------------------------------------------------------------------

    // 남길 공간([RESERVE_BYTES])을 재는 것이라 일부러 `usableSpace` 를 쓴다. lint 가 권하는 `getAllocatableBytes` 는 시스템이 지울 수
    // 있는 남의 캐시까지 세어 주므로, 그 값으로 재면 기기를 거의 다 채운 뒤에야 멈춘다.
    @SuppressLint("UsableSpace")
    private suspend fun copy(
        context: Context,
        uri: Uri,
        mimeType: String?,
        meta: Meta,
        progress: Progress,
        onCopied: (String) -> Unit,
    ): Outcome {
        val name = nameFor(meta.name ?: uri.lastPathSegment, mimeType) { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        }
        val dir = File(inboxDir(context), UUID.randomUUID().toString())
        if (!dir.mkdirs()) return Outcome.Failed(Failure.NO_SPACE)
        val target = File(dir, name)
        try {
            // 다듬은 이름이라 폴더 밖으로 나갈 수 없지만, 정규화한 자리로 한 번 더 본다.
            if (target.canonicalFile.parentFile != dir.canonicalFile) return failed(dir, Failure.UNREADABLE)
            if (meta.size > 0 && meta.size > dir.usableSpace - RESERVE_BYTES) return failed(dir, Failure.NO_SPACE)
            copyInto(context, uri, dir, target, meta.size, progress)?.let { return failed(dir, it) }
            onCopied(target.path)
            // 다 만드는 사이에 취소됐으면 여기서 지운다 — 받는 쪽이 경로를 쥐었다가 이미 끝났을 수 있다(그 쪽은 끝날 때 한 번 지우고
            // 다시 보지 않는다).
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            dir.deleteRecursively()
            throw e
        } catch (e: IOException) {
            return failed(dir, Failure.UNREADABLE)
        }
        // 시각을 알면 옮겨 둔다 — 이어보기의 열쇠(크기·이름·수정 시각)가 같은 첨부를 다시 열 때 이어진다.
        if (meta.lastModified > 0) target.setLastModified(meta.lastModified)
        return Outcome.Ok(Received.Copied(target.path))
    }

    private fun failed(dir: File, failure: Failure): Outcome {
        dir.deleteRecursively()
        return Outcome.Failed(failure)
    }

    /** 실패하면 그 까닭, 끝까지 옮겼으면 null. 임시 이름으로 쓰고 다 쓴 뒤에 [target] 으로 바꾼다 — 뷰어가 반쪽을 보지 않게. */
    @SuppressLint("UsableSpace") // [copy] 의 주석과 같은 까닭이다.
    private suspend fun copyInto(
        context: Context,
        uri: Uri,
        dir: File,
        target: File,
        total: Long,
        progress: Progress,
    ): Failure? {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            null
        } catch (e: RuntimeException) {
            if (e is CancellationException) throw e
            null
        } ?: return Failure.UNREADABLE
        val temp = File(dir, TEMP_NAME)
        try {
            input.use { inp ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var copied = 0L
                    var reported = 0L
                    var checked = 0L
                    progress.onProgress(0, total)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = inp.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        copied += n
                        if (copied - checked >= SPACE_CHECK_BYTES) {
                            checked = copied
                            if (dir.usableSpace < RESERVE_BYTES) return Failure.NO_SPACE
                        }
                        if (copied - reported >= REPORT_BYTES) {
                            reported = copied
                            progress.onProgress(copied, total)
                        }
                    }
                    progress.onProgress(copied, total)
                }
            }
        } catch (e: IOException) {
            return if (isNoSpace(e)) Failure.NO_SPACE else Failure.UNREADABLE
        } catch (e: SecurityException) {
            return Failure.UNREADABLE
        }
        return if (temp.renameTo(target)) null else Failure.UNREADABLE
    }

    /** `ENOSPC` 는 한두 겹 안쪽 원인에 있다(`FileOpEngine.reasonOf` 와 같은 까닭). */
    private fun isNoSpace(t: Throwable): Boolean {
        var e: Throwable? = t
        var hop = 0
        while (e != null && hop < 4) {
            if (e is ErrnoException) return e.errno == OsConstants.ENOSPC || e.errno == OsConstants.EDQUOT
            e = e.cause
            hop++
        }
        return false
    }

    private fun inboxDir(context: Context): File = File(ShareHelper.shareDir(context), INBOX_DIR)

    /** [path] 가 사본이면 그 무작위 폴더(`inbox/<uuid>`). */
    private fun copyDirOf(context: Context, path: String): File? = try {
        val inbox = inboxDir(context).canonicalFile
        File(path).canonicalFile.parentFile?.takeIf { it.parentFile == inbox }
    } catch (e: IOException) {
        null
    }

    private val UNKNOWN = Meta(null, -1L, 0L)

    /** 지우기는 화면을 기다리게 하지 않는다. 프로세스와 함께 산다. */
    private val cleanup = CoroutineScope(SupervisorJob() + IroDispatchers.io)

    private const val SCHEME_CONTENT = "content"
    private const val SCHEME_FILE = "file"
    private const val STORAGE_ROOT = "/storage/"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
    private const val RAW_PREFIX = "raw:"
    private const val MSF_PREFIX = "msf:"

    /** 이름을 알 수 없을 때. 형식을 알면 확장자가 붙는다([nameFor]). */
    private const val FALLBACK_NAME = "file"

    /** 쓰는 중의 이름. 받을 때마다 폴더가 새것이라 겹치지 않는다. */
    private const val TEMP_NAME = ".iroiro-receiving.part"

    private const val BUFFER_BYTES = 64 * 1024
    private const val REPORT_BYTES = 256L * 1024
    private const val SPACE_CHECK_BYTES = 16L * 1024 * 1024
}
