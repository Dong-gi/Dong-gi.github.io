package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * **'Files에 저장'** — 다른 앱의 공유(`ACTION_SEND`·`ACTION_SEND_MULTIPLE`)로 온 파일을 사용자가 고른 폴더에 **복사해 둔다**(`app` 의
 * `SaveActivity`). 보는 길(`IncomingFiles` — 사본은 캐시에, 읽기 전용)과 달리 여기서 만든 것은 사용자의 파일이다.
 *
 * 쓰기는 파일 작업 큐를 지난다(`FileOpManager.Request.SaveIncoming`) — 진행·취소·포그라운드 서비스, 같은 폴더를 두 작업이 함께 만지지 않기.
 * 항목마다 임시본([AtomicFileWriter.begin])에 다 쓰고 fsync 한 뒤 **끝에** 없는 이름을 `O_EXCL` 로 맡는다([NewFiles.place] — 있으면 ` (2)`).
 *
 * ## 막는 것
 *
 * - **스킴은 `content` 하나.** `file:` 은 받지 않는다 — targetSdk 24 이상인 앱은 보낼 수도 없고(`FileUriExposedException`), 저장 볼륨 안의
 *   파일이면 파일 목록의 복사가 한다. 받으면 보낸 앱이 못 읽는 자리를 이 앱의 모든 파일 권한으로 읽게 하는 길만 하나 는다.
 * - **우리 자신의 제공자는 받지 않는다**(`IncomingFiles.isOwnProvider` — 권한자의 사용자 번호를 떼고 견준다). 우리는 우리 제공자를 허락 없이
 *   열 수 있어, 남이 지어 보낸 URI 로 앱 캐시의 것을 사용자 폴더로 꺼내 줄 수 있다.
 * - **이름**은 보낸 쪽이 적은 글이다 — 마지막 조각만, `PathRules.sanitize`(제어 문자·FAT 금지 글자·글 방향 글자), 점으로 시작하면 `_` 로
 *   바꾼다([nameFor] — `.nomedia` 하나가 그 폴더의 사진을 갤러리에서 모두 감춘다). 다듬은 이름에는 빗금이 없어 고른 폴더 밖으로 나갈 수
 *   없다 — 놓인 자리의 부모가 고른 폴더인지는 놓은 뒤에 견줘 **기록만** 남긴다(정규화하지 않는다, 되돌리지도 않는다).
 * - **자리**는 저장 볼륨의 폴더만 — 휴지통·`Android/data`·`Android/obb` 는 안 된다([isAllowedDestination]). 큐 안에서 다시 본다.
 * - **공간** — 제공자가 크기를 말하면 시작 전에, 쓰는 동안 16 MiB 마다 남은 공간을 보고 [RESERVE_BYTES] 밑이면 멈춘다(끝없는 스트림도 여기서 끊긴다).
 *   공간으로 멈추면 그 항목과 뒤의 항목은 '남음'(`Outcome.Failed.skipped`)이지 '못 읽음' 이 아니다.
 * - **제공자는 남의 코드다** — 묻기·열기·읽기가 무엇을 던져도(취소 말고) 그 항목만 '못 읽음' 으로 센다. 응답하지 않으면 큐가 멎으므로 그 호출은
 *   따로 된 스레드에서 돌고([BlockingReads]) [STALL_MS] 동안 답이나 바이트가 하나도 오지 않으면 그 항목을 '못 읽음' 으로 넘긴다 — 그 제공자(권한자)의
 *   나머지 항목도 묻지 않고 '못 읽음', 버린 스레드가 프로세스 전체 상한([BlockingReads.MAX_ABANDONED])이면 새로 묻지 않는다. 적힌 크기보다
 *   짧게 끝난 스트림(잘렸다)과 크게 넘치는 스트림도 '못 읽음' 이다([sizeVerdict]).
 * - **설치 파일(APK)도 저장한다** — 사용자가 고른 폴더에 두는 것뿐이고 이 길은 아무것도 열지 않는다. 그 뒤 파일 목록에서 누르는 것은 사용자가
 *   고른 APK 다(`IncomingFiles` 의 '받은 것은 열지 않는다' 와 다르다 — 거기서는 보낸 앱이 이 앱의 설치 권한을 빌려 쓴다).
 */
object IncomingSaves {

    /** 한 번에 받는 항목 수의 상한. 넘으면 받는 화면이 저장하지 않는다(갤러리에서 수천 장을 고른 공유). */
    const val MAX_ITEMS = 1000

    /** 쓰는 동안 볼륨에 남겨 둘 공간 — ZIP 만들기의 지킴과 같다. */
    const val RESERVE_BYTES = ZipMaker.SPACE_MARGIN_BYTES

    /** 받은 하나의 결과. */
    internal enum class ItemResult { SAVED, UNREADABLE, NO_SPACE }

    /**
     * 받는다. [onExtra] 는 하나 놓을 때마다 지금까지 놓은 것을 싣는다 — 중간에 취소돼도 결과가 '이미 저장한 것' 을 말한다.
     * [onPlaced] 는 **마지막 항목을 놓은 그 자리**에서 끝난 결과를 준다(그 뒤에 온 취소가 '취소했습니다' 를 말하지 않게 — `NewFiles.place`).
     */
    suspend fun save(
        context: Context,
        request: FileOpManager.Request.SaveIncoming,
        media: MediaIndex,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
    ): FileOpEngine.Outcome {
        val dir = destinationOf(context, request.destDir)
            ?: return FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PERMISSION, "")
        if (!dir.isDirectory) return FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, "")
        val saved = ArrayList<String>()
        var failed = 0
        val stalled = HashSet<String>()
        val total = request.uris.size
        for ((index, uri) in request.uris.withIndex()) {
            currentCoroutineContext().ensureActive()
            // 한 번 시간을 넘긴 제공자(권한자)의 나머지는 묻지 않고 '못 읽음' — 막힌 제공자 하나가 항목마다 몇십 초씩 큐를 묶지 않게.
            val authority = IncomingFiles.authorityOf(uri).orEmpty()
            if (!accepts(context, uri) || authority in stalled || BlockingReads.saturated()) {
                failed++
                continue
            }
            // 제공자에게 묻는 일도 막힐 수 있다 — 상한을 넘으면 모르는 채로 간다(이름은 URI 에서).
            val stall = { stalled += authority }
            val meta = BlockingReads.call(STALL_MS, stall) { metaOf(context, uri) } ?: Meta(null, -1L)
            val type = if (authority in stalled) null else BlockingReads.call(STALL_MS, stall) { typeOf(context, uri) }
            if (authority in stalled) {
                failed++
                continue
            }
            val name = nameFor(meta.name ?: uri.lastPathSegment, type) { mime ->
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            }
            // 크기를 모르면 0 — 화면이 진행을 정하지 않은 막대로 그린다.
            onProgress(FileOpEngine.Progress(0, meta.size.coerceAtLeast(0), index, total, name))
            if (meta.size > 0 && meta.size > usableSpaceOf(dir) - RESERVE_BYTES) {
                return FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, name, saved.size, total - index, failed)
            }
            val last = index == total - 1
            val result = copyOne(context, uri, dir, name, meta.size, index, total, media, onProgress, stall) { placed ->
                saved += placed.path
                onExtra(FileOpManager.Extra.Received(request.destDir, request.destLabel, saved.toList()))
                if (last) onPlaced(FileOpEngine.Outcome.Done(saved.size, 0, failed))
            }
            when (result) {
                ItemResult.SAVED -> Unit
                ItemResult.UNREADABLE -> failed++
                // 멈춘 항목과 그 뒤는 '남음'(skipped)이다 — 읽지 못한 것이 아니다.
                ItemResult.NO_SPACE ->
                    return FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, name, saved.size, total - index, failed)
            }
        }
        return FileOpEngine.Outcome.Done(saved.size, 0, failed)
    }

    /** 고른 폴더를 정규화해 다시 본다(넣은 뒤에 바뀌었을 수 있다). 안 되는 자리·권한 없음이면 null. */
    private fun destinationOf(context: Context, path: String): File? {
        if (!StorageAccess.isGranted()) return null
        val canonical = try {
            File(path).canonicalFile
        } catch (e: IOException) {
            return null
        }
        val roots = VolumeRegistry.volumes(context).map { it.path }
        return canonical.takeIf { isAllowedDestination(it.path, roots) }
    }

    /** 받는 URI 인가 — `content` 이고 우리 제공자가 아니다. 받는 화면이 셀 때와 큐가 읽기 전에 본다. */
    fun accepts(context: Context, uri: Uri): Boolean =
        acceptsScheme(uri.scheme) && !IncomingFiles.isOwnProvider(context, IncomingFiles.authorityOf(uri))

    internal fun acceptsScheme(scheme: String?): Boolean = scheme.equals("content", ignoreCase = true)

    // 남길 몫을 재는 자리라 보수적인 값이 맞다(ZIP 만들기·받기 사본과 같다). `getAllocatableBytes` 는 남의 캐시까지 센다.
    @SuppressLint("UsableSpace")
    private fun usableSpaceOf(dir: File): Long = dir.usableSpace

    /**
     * 하나를 [dir] 에 복사한다. 놓으면 [onPlaced] 를 **놓은 그 자리에서**(취소를 받지 않는 구간 — [NewFiles.place]) 부른다.
     * 이름을 맡지 못했거나(`ClaimFailed`) 읽다·쓰다 실패하면 임시본을 버리고 그 항목만 실패로 센다. 공간이 모자라면 [ItemResult.NO_SPACE].
     */
    private suspend fun copyOne(
        context: Context,
        uri: Uri,
        dir: File,
        name: String,
        size: Long,
        index: Int,
        total: Int,
        media: MediaIndex,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onStall: () -> Unit,
        onPlaced: (File) -> Unit,
    ): ItemResult {
        // 여는 일(바인더)도 따로 된 스레드에서 — 늦게 열린 스트림은 [BlockingReads.call] 이 닫는다.
        val input = BlockingReads.call(STALL_MS, onStall) { openInput(context, uri) } ?: return ItemResult.UNREADABLE
        val pending = try {
            AtomicFileWriter.begin(File(dir, name), lastModified = 0)
        } catch (e: IOException) {
            BlockingReads.Pump(input).close()
            return if (isNoSpace(e)) ItemResult.NO_SPACE else ItemResult.UNREADABLE
        } catch (e: ErrnoException) {
            BlockingReads.Pump(input).close()
            return if (isNoSpace(e)) ItemResult.NO_SPACE else ItemResult.UNREADABLE
        }
        try {
            // 읽기는 따로 된 스레드가 한다 — 닫기도 그쪽 스레드에서(막힌 `read` 를 풀려고, 닫기 자체가 막힐 수도 있어서).
            // 버린 스레드가 상한이면 읽는 스레드를 더 세우지 않는다.
            val copied = if (BlockingReads.saturated()) {
                BlockingReads.Pump(input).close()
                ItemResult.UNREADABLE
            } else {
                BlockingReads.Pump(input).start().use { p -> pump(p, pending, dir, name, size, index, total, onProgress, onStall) }
            }
            if (copied != null) {
                withContext(NonCancellable) { pending.abort() }
                return copied
            }
            NewFiles.place(pending, dir, name, media) { placed ->
                // 다듬은 이름이라 폴더 밖으로 나갈 수 없지만, 놓인 자리를 한 번 더 본다(되돌릴 수는 없다 — 기록만).
                if (placed.parentFile?.path != dir.path) Iro.e(message = "받은 파일이 고른 폴더 밖에 놓였다")
                onPlaced(placed)
            }
            return ItemResult.SAVED
        } catch (e: CancellationException) {
            withContext(NonCancellable) { pending.abort() }
            throw e
        } catch (e: NewFiles.ClaimFailed) {
            withContext(NonCancellable) { pending.abort() }
            return if (e.reason == FileOpEngine.Reason.NO_SPACE) ItemResult.NO_SPACE else ItemResult.UNREADABLE
        } catch (e: IOException) {
            withContext(NonCancellable) { pending.abort() }
            return if (isNoSpace(e)) ItemResult.NO_SPACE else ItemResult.UNREADABLE
        } catch (e: ErrnoException) {
            // 놓기의 `rename`·폴더 fsync 가 던진다(`IOException` 이 아니다).
            withContext(NonCancellable) { pending.abort() }
            return if (isNoSpace(e)) ItemResult.NO_SPACE else ItemResult.UNREADABLE
        } catch (e: RuntimeException) {
            // 제공자의 스트림이 IOException 이 아닌 것을 던지기도 한다(남의 코드).
            withContext(NonCancellable) { pending.abort() }
            return ItemResult.UNREADABLE
        }
    }

    /**
     * 끝까지 옮겼으면 null, 아니면 그 까닭. 조각마다 취소를 보고(기다림은 취소로 끊긴다), [STALL_MS] 동안 조각이 오지 않으면 '못 읽음',
     * 16 MiB 마다 남은 공간을 본다. 적힌 크기([size])가 있으면 그보다 짧게 끝나거나 크게 넘치면 '못 읽음'([sizeVerdict]).
     */
    private suspend fun pump(
        input: BlockingReads.Pump,
        pending: AtomicFileWriter.Pending,
        dir: File,
        name: String,
        size: Long,
        index: Int,
        total: Int,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onStall: () -> Unit,
    ): ItemResult? {
        var copied = 0L
        var reported = 0L
        var checked = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val item = input.next(STALL_MS) ?: run {
                onStall()
                return ItemResult.UNREADABLE
            }
            when (item) {
                is BlockingReads.Pump.Item.End -> break
                is BlockingReads.Pump.Item.Failed -> return ItemResult.UNREADABLE
                is BlockingReads.Pump.Item.Chunk -> {
                    pending.stream.write(item.bytes, 0, item.length)
                    copied += item.length
                }
            }
            if (sizeVerdict(copied, size, ended = false) != SizeVerdict.OK) return ItemResult.UNREADABLE
            if (copied - checked >= SPACE_CHECK_BYTES) {
                checked = copied
                if (usableSpaceOf(dir) < RESERVE_BYTES) return ItemResult.NO_SPACE
            }
            if (copied - reported >= REPORT_BYTES) {
                reported = copied
                // 크기를 모르면 0 — 화면은 정하지 않은 막대와 받은 바이트를 보인다.
                onProgress(FileOpEngine.Progress(copied, size.coerceAtLeast(0), index, total, name))
            }
        }
        return if (sizeVerdict(copied, size, ended = true) == SizeVerdict.OK) null else ItemResult.UNREADABLE
    }

    /** 받은 바이트([copied])를 제공자가 적은 크기([declared] — 모르면 0 이하)와 견준 판정. */
    enum class SizeVerdict { OK, TRUNCATED, OVERFLOW }

    /**
     * 끝났는데([ended]) 적힌 크기보다 짧으면 잘린 스트림, 적힌 크기에 그 크기만큼(적어도 [OVERFLOW_SLACK_BYTES])을 더 넘으면 넘치는 스트림이다.
     * 조금 넘는 것은 받는다 — 제공자의 크기 칸은 늦게 고쳐지기도 한다. 크기를 모르면 언제나 OK(공간 지킴이 끊는다).
     */
    fun sizeVerdict(copied: Long, declared: Long, ended: Boolean): SizeVerdict = when {
        declared <= 0 -> SizeVerdict.OK
        copied > declared + maxOf(declared, OVERFLOW_SLACK_BYTES) -> SizeVerdict.OVERFLOW
        ended && copied < declared -> SizeVerdict.TRUNCATED
        else -> SizeVerdict.OK
    }

    private fun openInput(context: Context, uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: RuntimeException) {
        // 권한 없음(`SecurityException`)·제공자 안의 오류 — 무엇이든 '못 읽음'.
        if (e is CancellationException) throw e
        null
    }

    private fun typeOf(context: Context, uri: Uri): String? = try {
        context.contentResolver.getType(uri)
    } catch (e: RuntimeException) {
        if (e is CancellationException) throw e
        null
    }

    /** 받는 화면에 보일 이름 — 저장할 때와 같은 다듬기([nameFor]). 제공자가 무엇을 던지거나 답하지 않아도 이름을 짓는다. */
    suspend fun displayNameOf(context: Context, uri: Uri): String {
        val meta = BlockingReads.call(STALL_MS) { metaOf(context, uri) }
        val type = BlockingReads.call(STALL_MS) { typeOf(context, uri) }
        return nameFor(meta?.name ?: uri.lastPathSegment, type) { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        }
    }

    /** 제공자가 말한 이름과 크기. 모르면 null·-1. */
    private class Meta(val name: String?, val size: Long)

    private fun metaOf(context: Context, uri: Uri): Meta = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun at(column: String): Int? = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }
            Meta(at(OpenableColumns.DISPLAY_NAME)?.let(c::getString), at(OpenableColumns.SIZE)?.let(c::getLong) ?: -1L)
        } ?: Meta(null, -1L)
    } catch (e: RuntimeException) {
        if (e is CancellationException) throw e
        Meta(null, -1L)
    }

    /** `ENOSPC`·`EDQUOT` 는 한두 겹 안쪽 원인에 있다(`IncomingFiles` 와 같은 까닭). */
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

    // ---- 판단(JVM 시험) ------------------------------------------------------------------------

    /**
     * 저장할 이름. 보낸 쪽이 적은 이름([raw])의 **마지막 조각**을 다듬는다 — 빗금·역빗금 앞은 버리고, 비었거나 점뿐이면 [FALLBACK_NAME],
     * **점으로 시작하면 그 점들을 `_` 하나로**(숨은 파일·`.nomedia` 를 만들지 않는다), 확장자가 없으면 형식([mimeType])의 확장자를 붙이고,
     * 끝에 `PathRules.sanitize`(제어 문자·FAT 금지 글자·글 방향 글자를 `_` 로, 255바이트 — 확장자는 남긴다).
     *
     * @param extensionOfMime 형식 → 확장자(플랫폼 표). 시험이 바꿔 끼운다.
     */
    fun nameFor(raw: String?, mimeType: String?, extensionOfMime: (String) -> String?): String {
        val last = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        var base = if (last.isEmpty() || last.all { it == '.' }) FALLBACK_NAME else last
        if (base.startsWith('.')) base = "_" + base.trimStart('.')
        val ext = if (base.lastIndexOf('.') > 0) null else extensionFor(mimeType, extensionOfMime)
        val name = PathRules.sanitize(if (ext != null) "$base.$ext" else base)
        // 다듬기가 앞의 공백을 걷어 다시 점으로 시작할 수 있다(`_` 앞의 공백은 없지만 — 한 번 더 본다).
        return if (name.startsWith('.')) "_" + name.trimStart('.') else name
    }

    /** 형식 → 확장자. 형식이 없거나 와일드카드·`octet-stream` 이면, 또는 확장자 모양(영문·숫자 1~10자)이 아니면 null. */
    internal fun extensionFor(mimeType: String?, extensionOfMime: (String) -> String?): String? {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() && '*' !in it && it != "application/octet-stream" } ?: return null
        val ext = extensionOfMime(mime)?.lowercase() ?: return null
        return ext.takeIf { e -> e.length in 1..10 && e.all { it in 'a'..'z' || it in '0'..'9' } }
    }

    /**
     * 저장해도 되는 폴더인가 — 정규화한 경로([canonicalPath])가 **저장 볼륨([volumeRoots]) 아래**이고(`/storage/` 아래만), 휴지통이 아니고,
     * 볼륨의 `Android/data`·`Android/obb`(다른 앱의 자리 — 대소문자를 가리지 않는다. 내부 저장소는 대소문자를 가리지 않는 FUSE 다)가 아니다.
     * 안드로이드의 구분자는 언제나 `/` 다.
     */
    fun isAllowedDestination(canonicalPath: String, volumeRoots: List<String>): Boolean {
        if (!canonicalPath.startsWith(STORAGE_ROOT)) return false
        if (TrashStore.isTrashPath(canonicalPath)) return false
        val root = volumeRoots
            .filter { it.startsWith(STORAGE_ROOT) && (canonicalPath == it || canonicalPath.startsWith("$it/")) }
            .maxByOrNull { it.length } ?: return false
        val parts = canonicalPath.removePrefix(root).trim('/').split('/')
        if (parts.any { it == ".." || it == "." }) return false
        return !(parts.size >= 2 && parts[0].equals("Android", ignoreCase = true) &&
            (parts[1].equals("data", ignoreCase = true) || parts[1].equals("obb", ignoreCase = true)))
    }

    /** 처음 보일 폴더 — 마지막으로 저장한 폴더([last])가 아직 쓸 수 있으면 그것, 아니면 내려받기 폴더([download]). */
    fun startDir(last: String?, download: String, usable: (String) -> Boolean): String =
        last?.takeIf { it.isNotEmpty() && usable(it) } ?: download

    /** 받은 URI 목록을 겹치지 않게(문자열로 견준다), 받은 차례대로. 상한을 넘으면 null(저장하지 않는다 — 일부만 받으면 무엇이 빠졌는지 모른다). */
    fun <T> distinctCapped(items: List<T>, key: (T) -> String, max: Int = MAX_ITEMS): List<T>? {
        val seen = HashSet<String>()
        val out = ArrayList<T>()
        for (item in items) {
            if (seen.add(key(item))) {
                out += item
                if (out.size > max) return null
            }
        }
        return out
    }

    private const val STORAGE_ROOT = "/storage/"
    private const val FALLBACK_NAME = "file"
    /** 제공자의 답·바이트를 이만큼 기다린다. 넘으면 그 항목은 '못 읽음' 이고 큐는 다음으로 간다. */
    const val STALL_MS = 30_000L

    /** 적힌 크기를 넘어도 받는 최소 몫. */
    const val OVERFLOW_SLACK_BYTES = 1L shl 20

    private const val REPORT_BYTES = 256L * 1024
    private const val SPACE_CHECK_BYTES = 16L * 1024 * 1024
}
