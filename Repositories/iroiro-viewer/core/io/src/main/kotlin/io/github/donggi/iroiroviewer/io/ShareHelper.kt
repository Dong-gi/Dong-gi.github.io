package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

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
     */
    fun uriOf(context: Context, path: String): Uri? {
        val file = File(path).canonicalFile
        if (!file.isFile) return null
        val canonical = file.path
        if (allowedRoots(context).none { canonical.startsWith(it) }) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }

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
