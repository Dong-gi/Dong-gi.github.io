package io.github.donggi.iroirobrowser.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.torrent.TorrentEngine
import io.github.donggi.iroirobrowser.torrent.TorrentException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 다 받은 토렌트 파일을 공용 '내려받기' 로 옮긴다(사용자 결정 ⑮ — 앱 안에 받고, 다 받으면 옮긴다). 엔진이 파일 하나를 다 받으면(해시를 확인하고
 * 디스크까지 내렸다 — `TorrentListener.onFileCompleted`) 줄에 서고, 일꾼 하나가 차례로:
 *
 * 1. 자리를 정한다([TorrentRules.placement] — 엔진이 정리한 경로를 쓴다). 남은 공간을 본다 — 옮기는 동안은 두 벌이 있다.
 * 2. MediaStore 에 감춘 항목(`IS_PENDING`)을 만들고 기록에 적는다 — 앱이 죽으면 다음에 엔진을 열 때 지우고 다시 옮긴다.
 * 3. 복사하고 디스크까지 내린 뒤(fsync) 크기를 견준다.
 * 4. 드러내고(이름이 겹치면 MediaStore 가 ` (1)` 을 붙인다) 기록에 '옮김' 을 적는다.
 * 5. 엔진에 알린다(`TorrentEngine.releaseFile` — 엔진은 그 파일을 다시 읽거나 쓰지 않는다).
 * 6. 앱 안의 것을 지운다(비게 된 폴더는 남긴다 — [deleteInApp]).
 *
 * 차례가 이런 까닭: 3 과 5 사이에 앱이 죽어도 기록의 '옮김' 이 남아 다시 옮기지 않고(엔진에 알리고 앱 안의 것만 지운다), 6 전에 죽어도 엔진이
 * '옮겨 감' 을 알므로 남은 것을 지운다([TorrentRules.reconcile]). 엔진에 알리기 전에 지우면 그 사이 죽었을 때 엔진이 파일이 사라진 것을 보고 그
 * 조각들을 다시 받는다.
 *
 * 실패하면 감춘 항목을 지우고 다시 해 본다(5초·30초·2분 — 세 번). 공간이 모자라면 다시 해 보지 않는다 — 사용자가 공간을 비우고 '옮기기 다시
 * 시도' 를 누르거나 다음에 엔진을 열 때 다시 한다. 토렌트를 지우면 그 토렌트의 일은 버린다. 일꾼은 받기(엔진)와 따로 돈다 — 큰 파일을 옮기는
 * 동안에도 다음 파일을 받는다.
 */
internal class TorrentMover(private val app: Context, private val center: TorrentCenter) {

    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "web-torrent-move").apply { isDaemon = true } }

    /** 줄 선(또는 다시 해 보려 기다리는) 일 — `<id>/<번호>` → 표. 표가 바뀌었으면(지웠다 다시 넣었다) 옛 일은 그만둔다. */
    private val queued = ConcurrentHashMap<String, Long>()
    private val tokens = AtomicLong()

    /** 옮길 것이 남았다 — 서비스를 세워 두고 CPU 를 붙잡는다(큰 파일을 화면이 꺼진 채 옮긴다). 엔진도 닫지 않는다. */
    val busy: Boolean get() = queued.isNotEmpty()

    fun countFor(id: String): Int = queued.keys.count { it.startsWith("$id/") }

    fun enqueue(id: String, index: Int) {
        val key = key(id, index)
        val token = tokens.incrementAndGet()
        if (queued.putIfAbsent(key, token) != null) return
        center.changedSoon()
        try {
            executor.execute { run(id, index, token, attempt = 1) }
        } catch (e: RejectedExecutionException) {
            queued.remove(key, token)
        }
    }

    /** 그 토렌트의 일을 버린다(지웠다). 옮기는 중이면 다음 덩이에서 멈추고 감춘 항목을 지운다. */
    fun cancel(id: String) {
        queued.keys.removeAll { it.startsWith("$id/") }
    }

    /**
     * 지금 하는 일이 끝나기를 [timeoutMs] 까지 기다린다 — 지우기가 기록을 읽기 전에, 막 마무리(드러내기·기록)에 들어선 파일이 끝나게. 일꾼은 하나라
     * 빈 일 하나를 넣어 그것이 돌면 앞의 일은 끝났다. 다시 해 보려 기다리는(예약된) 일은 기다리지 않는다.
     */
    fun drain(timeoutMs: Long) {
        try {
            executor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // 시간을 넘겼거나 닫히는 중 — 그대로 간다(남은 파일은 다음에 엔진을 열 때 '기록에 없는 토렌트' 로 정리된다).
        }
    }

    private fun key(id: String, index: Int) = "$id/$index"

    private fun live(key: String, token: Long): Boolean = queued[key] == token

    private fun run(id: String, index: Int, token: Long, attempt: Int) {
        val key = key(id, index)
        if (!live(key, token)) return
        var done = true
        try {
            when (val r = moveOnce(id, index) { live(key, token) }) {
                Outcome.Moved, Outcome.Skipped -> Unit
                is Outcome.Retry -> if (attempt < BACKOFF_MS.size + 1) {
                    done = false
                    center.trace("torrent move retry $id/$index #$attempt ${app.getString(r.reason)}")
                    try {
                        executor.schedule({ run(id, index, token, attempt + 1) }, BACKOFF_MS[attempt - 1], TimeUnit.MILLISECONDS)
                    } catch (e: RejectedExecutionException) {
                        done = true
                    }
                } else {
                    failed(id, index, r.reason)
                }
                is Outcome.Failed -> failed(id, index, r.reason)
            }
        } catch (e: Exception) {
            // 모르는 실패 — 앱을 죽이지 않는다. 다음에 엔진을 열 때 다시 한다.
            failed(id, index, R.string.torrent_move_failed_other)
        } finally {
            if (done) queued.remove(key, token)
            center.changedSoon()
        }
    }

    private fun failed(id: String, index: Int, reason: Int) {
        val text = app.getString(reason)
        center.records.markFailed(id, index, text)
        center.onMoveFailed(id, text)
    }

    private sealed class Outcome {
        data object Moved : Outcome()

        /** 할 일이 없다(지운 토렌트·이미 옮겨 감·엔진이 닫혔다 — 다음에 열 때 마저 한다). */
        data object Skipped : Outcome()
        class Retry(val reason: Int) : Outcome()
        class Failed(val reason: Int) : Outcome()
    }

    private fun moveOnce(id: String, index: Int, live: () -> Boolean): Outcome {
        val engine = center.engineOrNull() ?: return Outcome.Skipped
        val info = engine.info(id) ?: return Outcome.Skipped
        val file = info.files.getOrNull(index) ?: return Outcome.Skipped
        val status = engine.status(id)?.files?.getOrNull(index)
        if (status?.released == true) {
            deleteInApp(engine, id, index)
            return Outcome.Skipped
        }
        val records = center.records
        if (records.movedOf(id).containsKey(index)) {
            // 앞서 옮겼는데 엔진에 알리기 전에 끊겼다 — 다시 옮기지 않는다(사용자가 그 사이 지웠어도 되살리지 않는다).
            if (release(engine, id, index)) deleteInApp(engine, id, index)
            return Outcome.Moved
        }
        val src = engine.fileOf(id, index) ?: return Outcome.Skipped
        // 앱 안의 파일이 없거나 크기가 다르다 — 엔진이 그 파일을 다시 확인하거나 받는 중이다(되살린 뒤 크기가 어긋났다). 다 받으면 엔진이 다시
        // 알린다(그때 다시 줄 선다) — 실패로 적지 않는다.
        if (!src.isFile || src.length() != file.length) return Outcome.Skipped
        val length = file.length
        // 옮기는 동안은 두 벌이 있다 — 공용 저장소는 앱 저장소와 같은 몫(/data)에 있어 그 몫에 더 쓸 수 있는 양으로 본다.
        if (freeBytes() < length + SPACE_MARGIN) return Outcome.Failed(R.string.torrent_move_no_space)
        val place = TorrentRules.placement(file.path, info.meta.isMultiFile)
        val mime = TorrentRules.mimeOf(place.displayName) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val uri = try {
            insert(place, mime)
        } catch (e: Exception) {
            return Outcome.Retry(R.string.torrent_move_create_failed)
        }
        val media = ContentUris.parseId(uri)
        records.markPending(id, index, media)
        try {
            if (!copy(src, uri, length, live)) {
                MediaStoreWriter.delete(app, uri)
                records.clearPending(id, index)
                return Outcome.Skipped
            }
            MediaStoreWriter.publish(app, uri)
            records.markMoved(id, index, media)
        } catch (e: Exception) {
            MediaStoreWriter.delete(app, uri)
            records.clearPending(id, index)
            return if (noSpace(e) || freeBytes() < length) Outcome.Failed(R.string.torrent_move_no_space) else Outcome.Retry(R.string.torrent_move_copy_failed)
        }
        center.trace("torrent moved $id/$index -> ${place.relativePath}")
        if (release(engine, id, index)) deleteInApp(engine, id, index)
        return Outcome.Moved
    }

    /**
     * 감춘 항목을 만든다. MediaStore 가 경로를 받지 않으면(정리한 이름이 그래도 거슬렸다) '내려받기' 바로 아래로 — 파일을 못 옮기는 것보다 낫다.
     */
    private fun insert(place: TorrentRules.Placement, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, place.displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, place.relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: IllegalArgumentException) {
            values.put(MediaStore.Downloads.RELATIVE_PATH, TorrentRules.DOWNLOAD_DIR + "/")
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }
        return uri ?: throw IOException("cannot create a download entry")
    }

    /** 복사하고 디스크까지 내린다. [live] 가 거짓이 되면(토렌트를 지웠다) 그만두고 false. */
    private fun copy(src: File, uri: Uri, length: Long, live: () -> Boolean): Boolean {
        val pfd = app.contentResolver.openFileDescriptor(uri, "w") ?: throw IOException("cannot open the download entry")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
            FileInputStream(src).use { input ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    if (!live()) return false
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                }
                out.flush()
                // 지우기 전에 디스크까지 — 앱 안의 것을 지운 뒤 전원이 끊겨도 옮긴 파일이 남게.
                out.fd.sync()
                if (total != length) throw IOException("copied $total of $length")
            }
        }
        return true
    }

    /** 엔진에 '옮겨 감' 을 알린다. 엔진이 닫혔거나 그 토렌트가 없으면 false(앱 안의 것은 다음에 엔진을 열 때 정리한다). */
    private fun release(engine: TorrentEngine, id: String, index: Int): Boolean = try {
        engine.releaseFile(id, index)
        true
    } catch (e: TorrentException) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    /**
     * 앱 안의 그 파일을 지운다. 비게 된 폴더는 둔다 — 엔진이 같은 폴더에 다음 파일을 만드는 순간과 겹치면(폴더를 만든 직후 지우면) 엔진의 쓰기가
     * 실패해 토렌트가 오류로 멈춘다. 빈 폴더는 자리를 거의 차지하지 않고, 토렌트를 지울 때 엔진이 함께 지운다.
     */
    private fun deleteInApp(engine: TorrentEngine, id: String, index: Int) {
        engine.fileOf(id, index)?.takeIf { it.isFile }?.delete()
    }

    /**
     * 앱 저장소의 몫에 더 쓸 수 있는 바이트 — 시스템이 비울 수 있는 캐시까지 센다(`getAllocatableBytes` — 남은 공간만 보면 캐시가 찬 폰에서 옮기지
     * 못한다고 잘못 말한다). 모르면 막지 않는다 — 복사가 공간 부족(ENOSPC)을 스스로 알린다.
     */
    private fun freeBytes(): Long = try {
        val storage = app.getSystemService(StorageManager::class.java)
        storage.getAllocatableBytes(storage.getUuidForPath(app.noBackupFilesDir))
    } catch (e: IOException) {
        Long.MAX_VALUE
    } catch (e: RuntimeException) {
        Long.MAX_VALUE
    }

    private fun noSpace(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is ErrnoException && t.errno == OsConstants.ENOSPC) return true
            if (t.message?.contains("ENOSPC") == true || t.message?.contains("No space left", ignoreCase = true) == true) return true
            t = t.cause
        }
        return false
    }

    private companion object {
        const val BUFFER = 256 * 1024

        /** 옮기기 전에 남겨 둘 여유 — 옮기는 사이에도 엔진이 다음 파일을 쓴다. */
        const val SPACE_MARGIN = 32L * 1024 * 1024

        /** 다시 해 보기 전에 기다리는 시간(첫째·둘째·셋째) — 그 뒤에도 안 되면 '옮기지 못함'. */
        val BACKOFF_MS = longArrayOf(5_000L, 30_000L, 120_000L)
    }
}
