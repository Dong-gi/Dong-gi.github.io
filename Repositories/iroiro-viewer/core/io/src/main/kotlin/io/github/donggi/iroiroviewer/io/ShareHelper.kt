package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 다른 앱으로 파일을 보낸다.
 *
 * ## `getUriForFile` 을 부르는 곳은 여기 하나뿐이다
 *
 * 이 앱은 사용자가 목록에서 고른 **임의의 경로**로 URI 를 만든다. `file_paths.xml` 을
 * `root-path "/"` 로 열어 두면 앱 내부 디렉터리(`/data/data/…` 의 DB·크래시 로그·설정)까지
 * content URI 로 만들 수 있게 되고, 정규화되지 않은 경로 하나가 통과하면 그것이 통째로
 * 다른 앱에 넘어간다. 그래서 노출 범위를 `/storage/` 와 캐시 하위로 좁히고,
 * **canonical 경로가 허용 루트 아래인지 여기서 한 번 더 확인한다.**
 */
object ShareHelper {

    /**
     * `file_paths.xml` 이 노출하는 루트. **코드와 XML 이 같은 범위를 말해야 한다.**
     *
     * `cache-path share/` 는 XML 에 처음부터 있었는데 코드 쪽 관문이 `/storage/` 뿐이라
     * 캐시 사본이 언제나 null 이 되고 있었다 — 두 곳이 어긋나면 좁은 쪽이 조용히 이긴다.
     * 캐시 경로는 기기마다 다르므로 문자열 상수로 적을 수 없고 **런타임에 계산한다.**
     */
    private fun allowedRoots(context: Context): List<String> = listOf(
        "/storage/",
        File(context.cacheDir, SHARE_DIR).canonicalPath + File.separator,
    )

    /** 정제 사본을 두는 곳. `file_paths.xml` 의 `cache-path share/` 와 같아야 한다. */
    const val SHARE_DIR = "share"

    fun shareDir(context: Context): File =
        File(context.cacheDir, SHARE_DIR).also { if (!it.isDirectory) it.mkdirs() }

    /**
     * 공유할 수 있는 URI. 범위 밖이면 null 이고, 그때는 화면이 '공유할 수 없음' 을 말한다.
     *
     * 없는 파일·폴더·저장 볼륨 밖으로 이어지는 심볼릭 링크는 모두 null 이다 — 링크는 `canonicalFile` 이 따라가
     * **도착한 자리**로 판정한다(FileProvider 도 URI 를 열 때 도착한 자리를 루트와 한 번 더 견준다).
     *
     * 파일을 누르는 손에서 불리므로 **던지지 않는다.** `canonicalFile` 은 경로가 망가졌거나 너무 길면
     * `IOException`, `getUriForFile` 은 루트 밖이면 `IllegalArgumentException` 이다. 그 둘만 잡는다 — 예전의
     * `runCatching` 은 오류(`Error`)까지 삼켰고, 정작 앞의 `IOException` 은 그 바깥에서 났다.
     */
    fun uriOf(context: Context, path: String): Uri? = try {
        val file = File(path).canonicalFile
        if (file.isFile && isUnderRoots(file.path, allowedRoots(context))) {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else {
            null
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * 정규화한 경로가 허용 루트 아래인가. 루트는 **빗금으로 끝나야 한다**(`/storage/`) — 빗금 없이 앞부분만 견주면
     * `/storagex/…` 같은 이웃 경로가 통과하므로, 빗금으로 끝나지 않는 루트는 아무것도 허락하지 않는다.
     * (안드로이드의 구분자는 언제나 `/` 다. `File.separator` 로 적지 않는 것은 윈도에서 도는 JVM 시험 때문이다.)
     */
    internal fun isUnderRoots(canonicalPath: String, roots: List<String>): Boolean =
        roots.any { it.endsWith('/') && canonicalPath.startsWith(it) }

    /**
     * 공유 창을 띄운다.
     *
     * 권한은 **이 인텐트에만, 읽기만** 준다. `grantUriPermission` 으로 특정 앱에 직접
     * 주지 않는다 — 그렇게 주면 우리가 취소하기 전까지 남는다.
     */
    fun share(context: Context, paths: List<String>, chooserTitle: String): Boolean {
        val uris = paths.mapNotNull { uriOf(context, it) }
        if (uris.isEmpty()) return false
        val mime = paths.map { MimeResolver.mimeOf(File(it).name) }.distinct()
            .let { if (it.size == 1) it.first() else "*/*" }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }
}
