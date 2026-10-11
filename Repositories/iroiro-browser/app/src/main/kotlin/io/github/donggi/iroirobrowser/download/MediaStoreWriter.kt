package io.github.donggi.iroirobrowser.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * 받은 파일을 공용 '내려받기' 폴더에 넣는다(`MediaStore.Downloads` — 저장소 권한이 필요 없다. 사용자 결정, 2026-10-05: '기본 다운로드
 * 폴더면 충분합니다'). 받는 동안은 `IS_PENDING` 으로 감춰 두고, 다 받으면 드러낸다. 멈춘(이어받을) 것은 감춘 채 남겨 두고 이어 쓴다 — 시스템은
 * 오래된(이레) 감춘 항목을 지우므로 그때는 처음부터 다시 받는다. 취소·실패하면 지운다. 이름이 겹치면 MediaStore 가 ` (1)` 을 붙인다. 받던 중에
 * 앱이 죽어 목록에 기록이 없는 감춘 항목은 다음에 뜰 때 지운다([pending] — [DownloadCenter.sweepOrphans]).
 */
internal object MediaStoreWriter {

    /**
     * 감춘 항목을 만든다. [folder] 가 있으면 '내려받기' 아래 그 폴더에(갤러리 받기 — `Download/Pixiv/<작가>/`, 성분은 [GalleryRules.relativePath] 가
     * 한 번 더 거른다). MediaStore 가 그 경로를 받지 않으면(정리한 이름이 그래도 거슬렸다) '내려받기' 바로 아래로 — 파일을 못 받는 것보다 낫다
     * ([TorrentMover] 의 `insert` 와 같은 꼴).
     */
    fun create(context: Context, displayName: String, mimeType: String, folder: List<String> = emptyList()): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, if (folder.isEmpty()) Environment.DIRECTORY_DOWNLOADS + "/" else GalleryRules.relativePath(folder))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: IllegalArgumentException) {
            if (folder.isEmpty()) throw e
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }
        return uri ?: throw IOException("cannot create a download entry")
    }

    /** [position] 에서 이어 쓰는 흐름 — 그 뒤는 잘라 낸다(앞 시도가 반쯤 쓴 것). */
    fun openAt(context: Context, uri: Uri, position: Long): FileOutputStream {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: throw IOException("cannot open the download file")
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        try {
            out.channel.truncate(position)
            out.channel.position(position)
        } catch (e: IOException) {
            out.close()
            throw e
        }
        return out
    }

    /** 감춘 항목이 아직 있는가(시스템이 지웠을 수 있다). */
    fun exists(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: FileNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    /** 앱 안의 파일을 새 항목으로 옮겨 담고 드러낸다 — 최종 이름을 돌려준다. */
    fun copyIn(context: Context, file: File, displayName: String, mimeType: String): Pair<Uri, String> {
        val uri = create(context, displayName, mimeType)
        try {
            (context.contentResolver.openOutputStream(uri) ?: throw IOException("cannot open the download file")).use { out ->
                FileInputStream(file).use { it.copyTo(out, 256 * 1024) }
            }
            return uri to (publish(context, uri) ?: displayName)
        } catch (e: Throwable) {
            delete(context, uri)
            throw e
        }
    }

    /** 드러내고, MediaStore 가 정한 최종 이름을 돌려준다. */
    fun publish(context: Context, uri: Uri): String? {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
        return context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** 지운다 — 지웠으면 true(이미 없거나 이 앱의 것이 아니면 false). */
    fun deleteCounted(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    /** 감춘(받는 중인) 항목 하나 — MediaStore 의 번호와 만든 때(초). */
    class PendingEntry(val id: Long, val addedSeconds: Long)

    /**
     * **이 앱이 만든** 감춘 항목들. 저장소 권한이 없는 앱에는 제 것만 보이지만, 지울 것을 고르는 데 쓰므로 만든 앱의 이름도 한 번 더 본다
     * (모르면 고르지 않는다). 읽지 못하면 빈 목록.
     */
    fun pending(context: Context): List<PendingEntry> {
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY) }
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DATE_ADDED, MediaStore.Downloads.OWNER_PACKAGE_NAME)
        return try {
            context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, args, null)?.use { c ->
                val out = ArrayList<PendingEntry>()
                while (c.moveToNext()) {
                    if (c.getString(2) != context.packageName) continue
                    out += PendingEntry(c.getLong(0), c.getLong(1))
                }
                out
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
}
