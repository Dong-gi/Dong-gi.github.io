package com.example.usbtether

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

/**
 * 파일 서버가 공개하는 폴더. SAF(Storage Access Framework) 위에 얹혀 있다.
 *
 * ## 왜 SAF 인가
 *
 * 스코프드 스토리지 이후 임의 폴더를 `java.io.File` 로 다루려면
 * `MANAGE_EXTERNAL_STORAGE`(전체 파일 접근)가 필요하다. 그 권한은 사용자가 설정
 * 화면에서 따로 허용해야 하고 범위가 과하다. `ACTION_OPEN_DOCUMENT_TREE` 로 사용자가
 * **고른 폴더 하나만** 받는 편이 최소 권한 원칙에 맞고, 매니페스트에 스토리지 권한을
 * 선언할 필요도 없다.
 *
 * 대가는 목록 조회가 IPC 라는 점이다. `DocumentFile.listFiles()` 는 항목 수만큼
 * ContentProvider 를 왕복하므로 항목이 많은 폴더에서 느리다. 파일 서버 용도에서는
 * 목록 요청이 드물어 감수한다.
 *
 * ## 경로 규약
 *
 * API 가 받는 `path` 는 공개 폴더를 루트로 하는 **상대 경로**이고 구분자는 `/` 다.
 * 빈 문자열이 루트다. 절대 경로, `..`, 빈 세그먼트는 [resolve] 가 거부한다.
 *
 * 구조적으로도 탈출이 어렵다 — 세그먼트마다 `findFile(name)` 으로 **자식만** 찾아
 * 내려가므로 트리 밖으로 나갈 방법이 없다. 그래도 명시적으로 검사하는 이유는, 이
 * 검사가 없으면 나중에 구현을 `File` 기반으로 바꿀 때 조용히 취약해지기 때문이다.
 *
 * ## 영속성
 *
 * 사용자가 고른 트리 URI 를 SharedPreferences 에 담고
 * `takePersistableUriPermission` 으로 재부팅 후에도 접근 권한을 유지한다. 권한이
 * 사라졌으면(사용자가 회수, 앱 재설치, SD 카드 제거) [isAvailable] 이 false 가 되고
 * 서버는 폴더를 다시 고르라고 응답한다.
 */
internal class SharedFolder(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 공개 폴더의 트리 URI. 아직 고르지 않았으면 null. */
    val treeUri: Uri?
        get() = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    /** 사용자에게 보여줄 폴더 이름. 고르지 않았으면 null. */
    val displayName: String?
        get() = root()?.name

    /**
     * 폴더가 지금 실제로 쓸 수 있는지.
     *
     * URI 가 저장돼 있어도 권한이 회수됐거나 저장 매체가 사라졌으면 false 다.
     */
    fun isAvailable(): Boolean = root()?.isDirectory == true

    /**
     * 폴더 선택 결과를 저장한다.
     *
     * @param uri `ACTION_OPEN_DOCUMENT_TREE` 결과 URI
     * @return 권한을 영속화하고 저장했으면 true
     */
    fun remember(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
            prefs.edit { putString(KEY_TREE_URI, uri.toString()) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "트리 URI 권한 영속화 실패: ${e.javaClass.simpleName}")
            false
        }
    }

    /** 공개 폴더 지정을 해제한다. 영속 권한도 놓아준다. */
    fun forget() {
        val uri = treeUri
        prefs.edit { remove(KEY_TREE_URI) }
        if (uri == null) return
        try {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "트리 URI 권한 해제 실패: ${e.javaClass.simpleName}")
        }
    }

    /**
     * 상대 경로를 [DocumentFile] 로 해석한다.
     *
     * @return 해당 항목. 경로가 부적절하거나 존재하지 않으면 null
     */
    fun resolve(path: String): DocumentFile? {
        val segments = splitPath(path) ?: return null
        var current = root() ?: return null
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    /**
     * 업로드 대상을 만든다. 부모 폴더까지는 존재해야 하고, 같은 이름이 있으면 덮어쓴다.
     *
     * 폴더는 만들지 않는다 — 최소 기능 범위에 폴더 생성이 없고, 클라이언트가 임의
     * 깊이의 트리를 만들 수 있게 하면 검토 범위가 넓어진다.
     *
     * @return 쓸 수 있는 항목. 경로가 부적절하거나 부모가 없으면 null
     */
    fun createForWrite(path: String): DocumentFile? {
        val segments = splitPath(path) ?: return null
        if (segments.isEmpty()) return null

        var parent = root() ?: return null
        for (segment in segments.dropLast(1)) {
            val child = parent.findFile(segment) ?: return null
            if (!child.isDirectory) return null
            parent = child
        }
        val name = segments.last()

        // 같은 이름이 있으면 지우고 새로 만든다. createFile 은 이름 충돌 시 "(1)" 을
        // 붙인 새 파일을 만들어, 덮어쓰기를 기대한 클라이언트를 조용히 배신한다.
        parent.findFile(name)?.let { existing ->
            if (existing.isDirectory) return null
            if (!existing.delete()) return null
        }
        return parent.createFile(guessMimeType(name), name)
    }

    /** 읽기 스트림. 실패하면 null. */
    fun openInput(file: DocumentFile): InputStream? = try {
        appContext.contentResolver.openInputStream(file.uri)
    } catch (e: Exception) {
        Log.w(TAG, "읽기 스트림 열기 실패: ${e.javaClass.simpleName}")
        null
    }

    /**
     * 쓰기 스트림. 실패하면 null.
     *
     * 모드 `"wt"` 는 truncate 다. `"w"` 만 주면 일부 프로바이더가 기존 내용을 남겨
     * 더 짧은 파일로 덮어쓸 때 뒤쪽 바이트가 남는다.
     */
    fun openOutput(file: DocumentFile): OutputStream? = try {
        appContext.contentResolver.openOutputStream(file.uri, "wt")
    } catch (e: Exception) {
        Log.w(TAG, "쓰기 스트림 열기 실패: ${e.javaClass.simpleName}")
        null
    }

    /** 공개 폴더의 루트. 지정되지 않았거나 접근할 수 없으면 null. */
    private fun root(): DocumentFile? {
        val uri = treeUri ?: return null
        return try {
            DocumentFile.fromTreeUri(appContext, uri)
        } catch (e: Exception) {
            Log.w(TAG, "트리 URI 해석 실패: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val TAG = "SharedFolder"
        private const val PREFS_NAME = "usb_tether_files"
        private const val KEY_TREE_URI = "tree_uri"

        /** 경로 세그먼트 최대 개수. 무한 깊이 순회로 IPC 를 쏟아붓지 못하게 한다. */
        private const val MAX_PATH_SEGMENTS = 32

        /**
         * 상대 경로를 세그먼트로 쪼갠다.
         *
         * 거부 조건: 절대 경로(`/` 로 시작), `.` 또는 `..` 세그먼트, 빈 세그먼트,
         * 백슬래시, NUL, 세그먼트 수 초과. 빈 경로는 빈 목록(루트)이다.
         *
         * @return 세그먼트 목록. 경로가 부적절하면 null
         */
        fun splitPath(path: String): List<String>? {
            if (path.isEmpty()) return emptyList()
            if (path.startsWith("/")) return null
            if (path.contains('\\')) return null
            // NUL 은 네이티브 계층에서 문자열을 자르는 데 쓰일 수 있어 거부한다.
            // 공백은 정상적인 파일명 문자이므로 거부하지 않는다.
            if (path.contains('\u0000')) return null

            val segments = path.split('/')
            if (segments.size > MAX_PATH_SEGMENTS) return null
            for (segment in segments) {
                if (segment.isEmpty()) return null
                if (segment == "." || segment == "..") return null
            }
            return segments
        }

        /**
         * 확장자로 MIME 타입을 추정한다.
         *
         * `MimeTypeMap` 을 쓰지 않는다 — 알 수 없는 확장자에 null 을 돌려주는데
         * `createFile` 은 null 을 받지 못한다. 여기서는 값이 중요하지 않고
         * (다운로드 시에는 `Content-Type` 을 따로 정한다) 파일이 만들어지는 것이
         * 중요하므로 모르면 옥텟 스트림으로 둔다.
         */
        fun guessMimeType(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
        }

        /**
         * 최소한의 확장자 → MIME 표.
         *
         * 브라우저가 인라인으로 열 수 있는 흔한 형식만 담는다. 없으면 옥텟 스트림이
         * 되어 다운로드로 처리된다 — 파일 전송 용도에서는 그게 기본 동작으로 맞다.
         */
        private val MIME_BY_EXTENSION = mapOf(
            "txt" to "text/plain", "md" to "text/plain", "log" to "text/plain",
            "csv" to "text/csv", "json" to "application/json", "xml" to "text/xml",
            "html" to "text/html", "htm" to "text/html", "css" to "text/css",
            "js" to "text/javascript",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
            "svg" to "image/svg+xml", "heic" to "image/heic",
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
            "flac" to "audio/flac", "wav" to "audio/wav",
            "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
            "pdf" to "application/pdf", "zip" to "application/zip",
            "apk" to "application/vnd.android.package-archive",
        )
    }
}
