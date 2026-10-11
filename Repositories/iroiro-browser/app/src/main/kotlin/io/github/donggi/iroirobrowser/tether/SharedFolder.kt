package io.github.donggi.iroirobrowser.tether

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import io.github.donggi.iroirobrowser.tether.core.SharedEntry
import io.github.donggi.iroirobrowser.tether.core.SharedPaths
import io.github.donggi.iroirobrowser.tether.core.SharedTree
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 파일 서버가 내주는 폴더 — 사용자가 고른 **SAF 트리 하나**(`ACTION_OPEN_DOCUMENT_TREE` + 영속 권한). 저장소 권한을 선언하지 않는다
 * (`MANAGE_EXTERNAL_STORAGE` 는 범위가 지나치고 설정에서 따로 줘야 한다 — 고른 폴더 하나만 받는 것이 최소 권한이다).
 *
 * usb-tether 는 `androidx.documentfile` 로 지었다 — Web 은 그 의존을 들이지 않고(지켜야 할 것 — 의존은 webkit·media3 둘) `DocumentsContract`
 * 로 다시 지었다. 그 김에 목록이 빨라졌다: `DocumentFile.listFiles()` 는 자식마다 이름·종류·크기·시각을 따로 물어 항목 수 × 다섯 번의 IPC 였고,
 * 이제 자식 질의 하나에 모든 열을 받는다.
 *
 * **성분마다 자식만 찾아 내려간다**([SharedTree] 의 계약) — 문서 ID 는 그 트리의 자식 질의가 준 것만 쓰고, 트리 URI 로 지은 문서 URI 는
 * 제공자가 그 트리 아래인지 본다. 그래서 트리 밖으로 나갈 길이 구조로 없다. 경로를 파일 경로로 이어 붙이는 구현으로 바꾸면 [SharedPaths.split]
 * 의 검사 하나에 기대게 된다(usb-tether 의 경고 그대로) — 그러지 마라.
 *
 * 질의는 서버의 일꾼 스레드에서 돈다(ContentResolver 는 여러 스레드에서 써도 된다). 권한이 사라졌으면(사용자가 거둠·앱 다시 깔기·SD 카드 뺌)
 * [isAvailable] 이 false 가 되고 서버는 폴더를 다시 고르라고 답한다.
 *
 * 올리기는 숨은 임시 문서(`.이름.part`)로 받고 다 받은 뒤에 원래 것을 지우고 `renameDocument` 로 바꾼다([SharedTree] 의 머리말).
 * 그래서 **이름 바꾸기를 하는 제공자**여야 한다(`FLAG_SUPPORTS_RENAME` — 기기의 저장소·SD 카드의 제공자는 한다). 하지 못하는 제공자에서는 받기
 * 전에 실패한다 — 다 받은 뒤에 바꾸지 못해 원래 것만 지우는 일이 없게.
 */
internal class SharedFolder(context: Context, private val settings: TetherSettings) : SharedTree {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

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
        if (!supportsRename(created)) {
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
        // 원래 것을 지우기 전 — 여기서 실패하면 임시를 지우고 끝낸다(원래 파일은 그대로).
        val existing = try {
            val parent = find(path.dropLast(1)) ?: error("부모 폴더가 없다")
            children(parent).firstOrNull { it.name == name }
        } catch (e: Exception) {
            Log.w(TAG, "올린 자리를 보지 못했다: ${e.javaClass.simpleName}")
            deleteQuietly(tempUri)
            return null
        }
        if (existing != null) {
            val removed = !existing.isDirectory && try {
                documentUri(existing)?.let { DocumentsContract.deleteDocument(resolver, it) } == true
            } catch (e: Exception) {
                false
            }
            if (!removed) {
                deleteQuietly(tempUri)
                return null
            }
        }
        // 원래 것을 지웠다 — 여기서 실패하면 받은 내용은 임시에만 있으므로 지우지 않고 남긴다.
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

    override fun delete(file: SharedEntry) {
        documentUri(file)?.let { deleteQuietly(it) }
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            Log.w(TAG, "지우지 못했다: ${e.javaClass.simpleName}")
        }
    }

    /** 제공자가 이 문서의 이름 바꾸기를 하는가(`FLAG_SUPPORTS_RENAME`). 묻지 못하면 하지 않는다고 본다. */
    private fun supportsRename(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use {
            it.moveToFirst() && (it.getInt(0) and Document.FLAG_SUPPORTS_RENAME) != 0
        } == true
    } catch (e: Exception) {
        false
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

        /** 임시 문서의 MIME — 이름을 그대로 두게 하는 일반 바이트. */
        const val OCTET_STREAM = "application/octet-stream"

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
