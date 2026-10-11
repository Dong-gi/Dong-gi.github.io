package io.github.donggi.iroiroviewer.editor

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 고친 문서를 결과로 돌려주는 자리(저장 자리가 없을 때 — 디버그 시험대) — **앱 캐시의 `editor/out/` 아래 파일만, 읽기로만** 내준다. 주소는
 * `content://<앱>.editorout/out/<무작위>/<이름>`. 저장 자리가 있으면(파일 화면에서 연 편집) 이 길을 쓰지 않는다 — 저장마다 파일 쪽에 반영한다.
 *
 * 내보내지 않는다(`exported=false`). 받는 쪽은 결과 인텐트에 실린 URI 읽기 권한으로만 닿는다. FileProvider(androidx.core)를 쓰지 않은 까닭 —
 * 내줄 수 있는 자리를 설정 파일이 아니라 이 코드가 못 박는다(자리가 하나뿐이다).
 *
 * **선언은 디버그 매니페스트에만 있다** — 이 제공자를 쓰는 길은 디버그 시험대뿐이다. 릴리스에서는 일터의 자리([WORK]·[rootOf] —
 * 저장 덩이를 맞추는 곳)만 쓴다.
 */
class OutputProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("읽기만 내준다")
        val file = fileOf(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val file = fileOf(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        return MatrixCursor(columns, 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String? {
        val file = fileOf(uri) ?: return null
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return EditorContract.Kind.entries.firstOrNull { it.ext == ext && it != EditorContract.Kind.PROBE }?.mime ?: "application/octet-stream"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun fileOf(uri: Uri): File? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != ROOT) return null
        val root = rootOf(ctx)
        return try {
            val file = File(File(root, segments[1]), segments[2]).canonicalFile
            val base = root.canonicalPath + File.separator
            file.takeIf { it.path.startsWith(base) && it.isFile }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private const val ROOT = "out"

        /** 편집기가 앱 캐시에 쓰는 것의 자리 — `cacheDir/editor/`(넘겨받은 문서의 사본 `in/`, 인쇄·내보내기의 임시본 `spool/`, 돌려줄 파일 `out/`). */
        const val WORK = "editor"

        /** 돌려줄 파일(저장한 문서)을 두는 자리 — 앱 캐시의 `editor/out/`. 화면이 끝날 때가 아니라 다음에 열 때 지난 것을 치운다(받는 쪽이 아직 읽고 있을 수 있다). */
        fun rootOf(context: Context): File = File(File(context.cacheDir, WORK), ROOT)

        /** [file]([rootOf] 아래 `<무작위>/<이름>`)의 주소. */
        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority("${context.packageName}.editorout")
                .appendPath(ROOT).appendPath(file.parentFile!!.name).appendPath(file.name).build()
    }
}
