package io.github.donggi.iroiroviewer.editor

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * 넘겨받은 문서를 **앱 캐시에 한 벌 떠 두고** 일회 표(token)로 엔진에 내준다(`/__io/in/<표>`).
 *
 * 떠 두는 까닭 — 원본이 편집하는 사이 바뀌지 않게, 엔진이 몇 번이고 다시 읽을 수 있게, 그리고 가로채기가 내주는 흐름의
 * `available()` 이 옳게(Chromium 은 한 번에 `available()` 만큼만 읽는다 — 파이프 흐름이면 2 KB 씩이 된다). 보기 → 편집·다시 열기는 원본을 다시
 * 읽어 사본과 견주고([sameAs]), 바뀌었으면 새로 떠 온다.
 *
 * 표는 128비트 무작위다. 같은 출처의 다른 쪽(엔진이 연 틀 등)이 표를 짐작해 읽을 수 없다.
 */
class DocumentStore(private val root: File) {

    private val tokens = ConcurrentHashMap<String, File>()
    private val pages = ConcurrentHashMap<String, File>()
    private val random = SecureRandom()

    class TooLarge(val limit: Long) : IOException("문서가 상한을 넘는다")

    /** [uri] 를 읽어 떠 두고 표를 돌려준다. [limit] 바이트를 넘으면 [TooLarge]. 부르는 쪽이 IO 스레드에서 부른다. */
    fun copyIn(resolver: ContentResolver, uri: Uri, limit: Long = LIMIT): String {
        val input = resolver.openInputStream(uri) ?: throw IOException("열 수 없다")
        return input.use { copyIn(it, limit) }
    }

    /** 흐름에서 떠 둔다(디버그 시험대·시험). */
    fun copyIn(input: InputStream, limit: Long = LIMIT): String {
        val dir = File(root, newToken()).apply { mkdirs() }
        val file = File(dir, "doc")
        try {
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > limit) throw TooLarge(limit)
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        }
        val token = newToken()
        tokens[token] = file
        return token
    }

    /** 표의 파일. 모르는 표는 null. */
    fun fileOf(token: String): File? = tokens[token]?.takeIf { it.isFile }

    /**
     * [token] 의 사본이 [input] 과 바이트까지 같은가 — 끝까지(또는 처음 어긋날 때까지) 읽어 본다. 보기 → 편집·다시 열기가 원본이 연 뒤 바뀌었는지
     * 본다(오래 읽다 '편집' 하면 연 때의 사본으로 고쳐 저장했다). 받은 URI 로는 크기만 물을 수 있고 수정 시각은 알 수 없어
     * 내용으로 견준다 — 같으면 새 사본을 쓰지 않는다. 모르는 표·사라진 사본은 다르다고 본다. 부르는 쪽이 IO 스레드에서 부른다.
     */
    fun sameAs(token: String, input: InputStream): Boolean {
        val file = fileOf(token) ?: return false
        var same = true
        file.inputStream().use { mine ->
            val a = ByteArray(COMPARE_BUFFER)
            val b = ByteArray(COMPARE_BUFFER)
            while (true) {
                val n = readFully(mine, a)
                val m = readFully(input, b)
                if (n != m || !samePrefix(a, b, n)) {
                    same = false
                    break
                }
                if (n == 0) break
            }
        }
        return same
    }

    // 범위를 받는 Arrays.equals 는 자바 9(안드로이드 API 33)부터다 — minSdk 31 에서 손으로 견준다.
    private fun samePrefix(a: ByteArray, b: ByteArray, n: Int): Boolean {
        for (i in 0 until n) if (a[i] != b[i]) return false
        return true
    }

    /** 다 쓴 표 하나를 지운다(다시 떠 온 사본으로 갈아탔다). 부르는 쪽이 IO 스레드에서 부른다. */
    fun forget(token: String) {
        val file = tokens.remove(token) ?: return
        file.parentFile?.deleteRecursively()
    }

    /** [into] 를 채울 때까지(또는 흐름 끝까지) 읽는다 — 읽은 수. 흐름마다 한 번에 주는 양이 달라 그대로 견주면 같은 내용도 어긋난다. */
    private fun readFully(input: InputStream, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = input.read(into, total, into.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    /**
     * 인쇄할 쪽(엔진이 지은 HTML — [PrintJob])을 내주는 표. 문서의 표와 **따로 센다** — 문서의 바이트가 HTML 로 내줘지는 길이 없게
     * (`/__io/page/` 는 이 표만 받는다).
     */
    fun registerPage(file: File): String {
        val token = newToken()
        pages[token] = file
        return token
    }

    fun pageOf(token: String): File? = pages[token]?.takeIf { it.isFile }

    fun forgetPage(token: String) {
        pages.remove(token)
    }

    /** 다 쓴 것을 지운다(화면이 끝날 때). */
    fun clear() {
        tokens.clear()
        pages.clear()
        root.deleteRecursively()
    }

    private fun newToken(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        /** 문서 하나의 상한 — 512 MiB. 편집기는 문서를 메모리에 통째로 든다(그보다 훨씬 작은 데서 먼저 힘들어진다). */
        const val LIMIT = 512L * 1024 * 1024

        private const val COMPARE_BUFFER = 64 * 1024
    }
}
