package io.github.donggi.iroirobrowser.tether

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import io.github.donggi.iroirobrowser.tether.core.SharedEntry
import io.github.donggi.iroirobrowser.tether.core.SharedPaths
import io.github.donggi.iroirobrowser.tether.core.SharedTree
import io.github.donggi.iroirobrowser.tether.core.Storage
import io.github.donggi.iroirobrowser.tether.core.Thumbnails
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 파일 서버가 내주는 폴더 — 사용자가 고른 **SAF 트리 하나**(`ACTION_OPEN_DOCUMENT_TREE` + 영속 권한). 저장소 권한을 선언하지 않는다
 * (`MANAGE_EXTERNAL_STORAGE` 는 범위가 지나치고 설정에서 따로 줘야 한다 — 고른 폴더 하나만 받는 것이 최소 권한이다).
 *
 * `androidx.documentfile` 을 들이지 않고(지켜야 할 것 — 의존은 webkit·media3 둘) `DocumentsContract` 로 짓는다. 자식 질의 하나에 모든 열을
 * 받는다(`DocumentFile.listFiles()` 는 자식마다 이름·종류·크기·시각을 따로 물어 항목 수 × 다섯 번의 IPC 다).
 *
 * **성분마다 자식만 찾아 내려간다**([SharedTree] 의 계약) — 문서 ID 는 그 트리의 자식 질의가 준 것만 쓰고, 트리 URI 로 지은 문서 URI 는
 * 제공자가 그 트리 아래인지 본다. 그래서 트리 밖으로 나갈 길이 구조로 없다. 경로를 파일 경로로 이어 붙이는 구현으로 바꾸면 [SharedPaths.split]
 * 의 검사 하나에 기대게 된다 — 그러지 마라(`java.io.File` 로 바꾸지 마라 — CLAUDE.md '구조' 의 UID 경계).
 *
 * 질의는 서버의 일꾼 스레드에서 돈다(ContentResolver 는 여러 스레드에서 써도 된다). 권한이 사라졌으면(사용자가 거둠·앱 다시 깔기·SD 카드 뺌)
 * [isAvailable] 이 false 가 되고 서버는 폴더를 다시 고르라고 답한다.
 *
 * 올리기는 숨은 임시 문서(`.이름.part`)로 받고 다 받은 뒤에 `renameDocument` 로 바꾼다([SharedTree] 의 머리말 — 덮어쓰기면 파일 서버가 그
 * 앞에 원래 것을 휴지통으로 옮긴다, 여기서는 같은 이름을 지우지 않는다). 그래서 **이름 바꾸기를 하는 제공자**여야 한다(`FLAG_SUPPORTS_RENAME`
 * — 기기의 저장소·SD 카드의 제공자는 한다). 하지 못하는 제공자에서는 받기 전에 실패한다 — 다 받은 뒤에 바꾸지 못해 원래 것만 치우는 일이 없게.
 *
 * 관리 기능의 바탕(폴더·파일 만들기, 이름 바꾸기, 옮기기 `moveDocument`, 복사 `copyDocument`, 지우기)은 그대로 `DocumentsContract` 다 — 옮기기·
 * 복사는 제공자가 그 깃발(`FLAG_SUPPORTS_MOVE`·`COPY`)을 세운 것만 하고, 아니면 null 로 돌려 [io.github.donggi.iroirobrowser.tether.core.SharedOps]
 * 가 한 항목씩 짓는다.
 *
 * **섬네일은 남이 올린 그림을 이 프로세스에서 풀지 않는다** — 이 프로세스(UID)는 프록시 계정에 닿으므로 그림 풀개의 결함이 곧 그 위험이 된다.
 * 그림은 제공자가 준 섬네일 바이트를 그대로(기기 저장소의 제공자는 EXIF 섬네일 — 없으면 원본 전체를 주므로 [Thumbnails.MAX_BYTES] 를 넘으면
 * 버린다 — 화면이 작은 원본을 대신 쓴다), 동영상은 미디어 서버가 푼 장면(`MediaMetadataRetriever` — 풀기는 시스템의 미디어 프로세스가 하고 이
 * 프로세스는 받은 화소를 JPEG 로 눌러 담기만 한다), 소리는 파일에 든 앨범 그림 바이트 그대로. 파일 서버가 바이트의 머리로 형식을 다시 본다.
 */
internal class SharedFolder(context: Context, private val settings: TetherSettings) : SharedTree {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    /** 고른 트리를 기억하고 영속 권한(읽기·쓰기)을 받는다. 앞서 고른 트리의 권한은 놓아 준다. 되면 true. */
    fun remember(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(uri, flags)
            val old = settings.treeUri
            settings.treeUri = uri
            if (old != null && old != uri) release(old)
            true
        } catch (e: Exception) {
            Log.w(TAG, "트리 권한을 받지 못했다: ${e.javaClass.simpleName}")
            false
        }
    }

    /** 공유를 그만둔다 — 기억을 지우고 영속 권한도 놓아 준다. */
    fun forget() {
        val uri = settings.treeUri ?: return
        settings.treeUri = null
        release(uri)
    }

    override fun isAvailable(): Boolean = root()?.isDirectory == true

    override fun rootName(): String? = root()?.name

    override fun find(path: List<String>): SharedEntry? {
        var current = root() ?: return null
        for (segment in path) {
            if (!current.isDirectory) return null
            current = try {
                children(current).firstOrNull { it.name == segment }
            } catch (e: Exception) {
                null
            } ?: return null
        }
        return current
    }

    override fun list(directory: SharedEntry): List<SharedEntry> = children(directory)

    override fun openRead(file: SharedEntry): InputStream? = try {
        documentUri(file)?.let { resolver.openInputStream(it) }
    } catch (e: Exception) {
        Log.w(TAG, "읽기를 열지 못했다: ${e.javaClass.simpleName}")
        null
    }

    override fun createUploadTemp(path: List<String>): SharedEntry? {
        if (path.isEmpty()) return null
        val parent = find(path.dropLast(1)) ?: return null
        if (!parent.isDirectory) return null
        val name = path.last()
        // 그 이름이 폴더면 파일로 덮지 않는다(경로가 틀렸다). 같은 이름의 파일은 건드리지 않는다 — 다 받은 뒤 commitUpload 에서 바꾼다.
        if (children(parent).any { it.name == name && it.isDirectory }) return null
        val parentUri = documentUri(parent) ?: return null
        // MIME 은 일반 바이트로 — 특정 형식을 주면 제공자가 이름 끝에 확장자를 덧붙일 수 있다. 이름이 겹치면 제공자가 번호를 붙인다(동시 올리기).
        val created = DocumentsContract.createDocument(resolver, parentUri, OCTET_STREAM, SharedPaths.uploadTempName(name))
            ?: throw IOException("임시 문서를 만들지 못했다")
        if (flags(created) and Document.FLAG_SUPPORTS_RENAME == 0) {
            deleteQuietly(created)
            throw IOException("이 폴더의 제공자는 이름 바꾸기를 하지 않는다")
        }
        return query(created) ?: run {
            deleteQuietly(created)
            throw IOException("임시 문서를 읽지 못했다")
        }
    }

    override fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry? {
        val tempUri = documentUri(temp) ?: return null
        val name = path.last()
        // 같은 이름이 있으면 두지 않는다 — 지우지 않는다(덮어쓰기는 파일 서버가 원래 것을 휴지통으로 옮긴 뒤에 부른다. 여기서 지우면 휴지통을
        // 비켜 간다). 그 사이에 누가 그 이름을 만들었다 — 임시를 지우고 끝낸다(원래 것은 그대로).
        val taken = try {
            val parent = find(path.dropLast(1)) ?: error("부모 폴더가 없다")
            children(parent).any { it.name.equals(name, ignoreCase = true) }
        } catch (e: Exception) {
            Log.w(TAG, "올린 자리를 보지 못했다: ${e.javaClass.simpleName}")
            true
        }
        if (taken) {
            deleteQuietly(tempUri)
            return null
        }
        // 이름 바꾸기가 실패하면 받은 내용은 임시에만 있으므로 지우지 않고 남긴다. 그 사이에 같은 이름이 생겼으면 제공자가 겹치지 않는 이름을
        // 고른다(기기 저장소의 FileSystemProvider — 덮지 않는다).
        val renamed = try {
            DocumentsContract.renameDocument(resolver, tempUri, name)
        } catch (e: Exception) {
            Log.w(TAG, "이름을 바꾸지 못했다 — 받은 내용은 임시 문서에 남았다: ${e.javaClass.simpleName}")
            null
        } ?: return null
        return query(renamed) ?: SharedEntry(DocumentsContract.getDocumentId(renamed), name, false, 0, 0)
    }

    /** `"wt"` — 잘라 쓴다(`"w"` 만 주면 짧은 파일로 덮을 때 뒤의 바이트가 남는 제공자가 있다). */
    override fun openWrite(file: SharedEntry): OutputStream? = try {
        documentUri(file)?.let { resolver.openOutputStream(it, "wt") }
    } catch (e: Exception) {
        Log.w(TAG, "쓰기를 열지 못했다: ${e.javaClass.simpleName}")
        null
    }

    /** 지운다 — 기기 저장소의 제공자는 폴더를 안의 것까지 지운다. */
    override fun delete(entry: SharedEntry): Boolean = documentUri(entry)?.let { deleteQuietly(it) } == true

    // ── 관리 기능의 바탕 ─────────────────────────────────────────────────────────────────────────────────

    override fun createDirectory(parent: SharedEntry, name: String): SharedEntry = create(parent, Document.MIME_TYPE_DIR, name)

    override fun createFile(parent: SharedEntry, name: String): SharedEntry = create(parent, OCTET_STREAM, name)

    private fun create(parent: SharedEntry, mime: String, name: String): SharedEntry {
        val parentUri = documentUri(parent) ?: throw IOException("공유 폴더가 없다")
        val created = DocumentsContract.createDocument(resolver, parentUri, mime, name) ?: throw IOException("만들지 못했다")
        return query(created) ?: throw IOException("만든 것을 읽지 못했다")
    }

    override fun rename(entry: SharedEntry, newName: String): SharedEntry {
        val uri = documentUri(entry) ?: throw IOException("공유 폴더가 없다")
        // 제공자가 이름을 바꾸면 문서 ID 가 바뀔 수 있다(기기 저장소는 경로가 ID 다) — 돌려준 URI 로 다시 읽는다. null 이면 그대로다.
        val renamed = DocumentsContract.renameDocument(resolver, uri, newName) ?: uri
        return query(renamed) ?: throw IOException("바꾼 것을 읽지 못했다")
    }

    override fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry? {
        val uri = documentUri(entry) ?: throw IOException("공유 폴더가 없다")
        if (flags(uri) and Document.FLAG_SUPPORTS_MOVE == 0) return null
        val from = documentUri(fromParent) ?: throw IOException("공유 폴더가 없다")
        val to = documentUri(toParent) ?: throw IOException("공유 폴더가 없다")
        val moved = DocumentsContract.moveDocument(resolver, uri, from, to) ?: throw IOException("옮기지 못했다")
        return query(moved) ?: throw IOException("옮긴 것을 읽지 못했다")
    }

    override fun copy(entry: SharedEntry, toParent: SharedEntry): SharedEntry? {
        val uri = documentUri(entry) ?: throw IOException("공유 폴더가 없다")
        if (flags(uri) and Document.FLAG_SUPPORTS_COPY == 0) return null
        val to = documentUri(toParent) ?: throw IOException("공유 폴더가 없다")
        val copied = DocumentsContract.copyDocument(resolver, uri, to) ?: throw IOException("복사하지 못했다")
        return query(copied) ?: throw IOException("복사한 것을 읽지 못했다")
    }

    /**
     * 공유 폴더가 있는 저장소의 공간. 제공자의 뿌리 질의는 `MANAGE_DOCUMENTS` 가 있어야 해서(트리 권한으로는 안 된다) 저장소 볼륨으로 어림한다 —
     * 기기 저장소(`primary:…`)는 앱 자료와 같은 분할이라 그 자리를 재고, SD 카드(`XXXX-XXXX:…`)는 그 볼륨의 자리를 잰다(읽을 수 없으면 null —
     * 화면이 공간을 보이지 않는다). 파일을 열지 않는다(statvfs 만).
     */
    override fun storage(): Storage? {
        val tree = settings.treeUri ?: return null
        return try {
            val volume = DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
            val path = if (volume == "primary" || volume.isEmpty()) {
                Environment.getDataDirectory()
            } else {
                appContext.getSystemService(StorageManager::class.java)?.storageVolumes
                    ?.firstOrNull { it.uuid.equals(volume, ignoreCase = true) }?.directory ?: return null
            }
            val stat = StatFs(path.path)
            Storage(free = stat.availableBytes, total = stat.totalBytes)
        } catch (e: Exception) {
            null
        }
    }

    override fun thumbnail(entry: SharedEntry, sizePx: Int): ByteArray? {
        val uri = documentUri(entry) ?: return null
        val mime = SharedPaths.guessMimeType(entry.name)
        return when {
            mime.startsWith("image/") -> providerThumbnail(uri, entry, sizePx)
            mime.startsWith("video/") -> providerThumbnail(uri, entry, sizePx) ?: videoFrame(uri, sizePx)
            mime.startsWith("audio/") -> albumArt(uri)
            else -> null
        }
    }

    /**
     * 제공자가 주는 섬네일 바이트(풀지 않는다). 기기 저장소의 제공자는 EXIF 섬네일이 없으면 원본 전체를 주므로 크기 상한을 넘으면 버린다 — 화면이
     * 그림이 작으면 원본으로 대신 그린다.
     */
    private fun providerThumbnail(uri: Uri, entry: SharedEntry, sizePx: Int): ByteArray? {
        if (flags(uri) and Document.FLAG_SUPPORTS_THUMBNAIL == 0) return null
        val options = Bundle().apply { putParcelable(ContentResolver.EXTRA_SIZE, Point(sizePx, sizePx)) }
        return try {
            resolver.openTypedAssetFileDescriptor(uri, "image/*", options, null)?.use { afd ->
                if (afd.length > Thumbnails.MAX_BYTES) return null
                afd.createInputStream().use { readCapped(it) }
            }
        } catch (e: Exception) {
            Log.v(TAG, "제공자의 섬네일이 없다(${entry.size}바이트): ${e.javaClass.simpleName}")
            null
        }
    }

    /** 동영상의 장면 — 풀기는 시스템의 미디어 프로세스가 한다. 받은 화소를 JPEG 로 담는다. */
    private fun videoFrame(uri: Uri, sizePx: Int): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val frame = retriever.getScaledFrameAtTime(FRAME_AT_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
                    ?: return null
                try {
                    ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
                } finally {
                    frame.recycle()
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "동영상 장면을 얻지 못했다: ${e.javaClass.simpleName}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 소리 파일에 든 앨범 그림 — 바이트 그대로(풀지 않는다). */
    private fun albumArt(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                retriever.embeddedPicture?.takeIf { it.size <= Thumbnails.MAX_BYTES }
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readCapped(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            out.write(buffer, 0, n)
            if (out.size() > Thumbnails.MAX_BYTES) return null
        }
        return out.toByteArray()
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────────────────────────────────

    private fun deleteQuietly(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (e: Exception) {
        Log.w(TAG, "지우지 못했다: ${e.javaClass.simpleName}")
        false
    }

    /** 제공자가 이 문서에 세운 깃발(`COLUMN_FLAGS`). 묻지 못하면 0(아무것도 하지 않는다고 본다). */
    private fun flags(uri: Uri): Int = try {
        resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /** 고른 트리의 맨 위. 고르지 않았거나 닿지 못하면 null. */
    private fun root(): SharedEntry? {
        val tree = settings.treeUri ?: return null
        return try {
            query(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
        } catch (e: Exception) {
            null
        }
    }

    private fun documentUri(entry: SharedEntry): Uri? =
        settings.treeUri?.let { DocumentsContract.buildDocumentUriUsingTree(it, entry.ref) }

    /** 폴더의 자식들 — 질의 하나에 모든 열을. 읽지 못하면 던진다. */
    private fun children(directory: SharedEntry): List<SharedEntry> {
        val tree = settings.treeUri ?: error("공유 폴더가 없다")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.ref)
        val cursor = resolver.query(uri, PROJECTION, null, null, null) ?: error("목록을 읽지 못했다")
        return cursor.use {
            val out = ArrayList<SharedEntry>(it.count.coerceAtLeast(0))
            while (it.moveToNext()) out += entryOf(it)
            out
        }
    }

    private fun query(uri: Uri): SharedEntry? = try {
        resolver.query(uri, PROJECTION, null, null, null)?.use { if (it.moveToFirst()) entryOf(it) else null }
    } catch (e: Exception) {
        null
    }

    private fun entryOf(c: Cursor): SharedEntry = SharedEntry(
        ref = c.getString(0),
        name = c.getString(1).orEmpty(),
        isDirectory = c.getString(2) == Document.MIME_TYPE_DIR,
        size = if (c.isNull(3)) 0 else c.getLong(3),
        modified = if (c.isNull(4)) 0 else c.getLong(4),
    )

    private fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w(TAG, "트리 권한을 놓지 못했다: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "WebTether"

        /** 임시 문서·새 파일의 MIME — 이름을 그대로 두게 하는 일반 바이트. */
        const val OCTET_STREAM = "application/octet-stream"

        /** 동영상 섬네일의 자리(1초 — 첫 장면은 검은 화면이 많다)와 JPEG 품질. */
        const val FRAME_AT_US = 1_000_000L
        const val JPEG_QUALITY = 80

        /** 열의 차례가 [entryOf] 의 번호다. */
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
