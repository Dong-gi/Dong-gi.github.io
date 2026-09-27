package io.github.donggi.iroiroviewer.docview.pdf.crypt

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor

/**
 * 복호화 결과를 **이름 없는 메모리 파일**(memfd)에 쓴다.
 *
 * ## 왜 memfd 인가
 *
 * pdfium 은 **seekable 한 파일 서술자**를 요구한다(`PdfRenderer` 가 `lseek`·`fstat` 을
 * 부른다). 평문을 일반 파일로 쓰면 그것이 곧 **사용자 문서의 평문 사본이 저장소에 남는**
 * 일이고, 프로세스가 죽으면 청소할 사람도 없다 — 만화 쪽을 디스크에 쓰지 않기로 한 9단계
 * 판단의 더 나쁜 판이다.
 *
 * memfd 는 경로가 없는 tmpfs 파일이다. 서술자를 닫으면 커널이 걷어 가고, 다른 앱이 이름으로
 * 찾아 들어올 길도 없다. `MFD_CLOEXEC` 로 자식 프로세스에도 새지 않는다.
 *
 * ## 한계를 적어 둔다
 *
 * 메모리다 — 문서 크기만큼 RAM 을 쓴다. 그래서 크기 상한이 있다(`PdfLimits.MAX_DECRYPTED_BYTES`).
 * 그리고 기기가 저장소로 스왑하면(일부 제조사의 'RAM 확장') 이 페이지들도 거기로 갈 수 있다.
 * 그것은 앱이 쥔 모든 메모리 — 풀어서 그린 쪽 비트맵까지 — 에 똑같이 해당하므로 memfd 만의
 * 문제가 아니다.
 *
 * `Os.memfd_create` 는 API 30 부터다(minSdk 31).
 */
internal class MemfdSink private constructor(private val fd: FileDescriptor) : PdfSink, AutoCloseable {

    override var position: Long = 0
        private set

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var n = len
        // `write(2)` 는 덜 쓰고 돌아올 수 있다. EINTR 은 libcore 가 되풀이해 준다.
        while (n > 0) {
            val w = Os.write(fd, b, o, n)
            o += w
            n -= w
        }
        position += len
    }

    override fun patch(at: Long, b: ByteArray) {
        var o = 0
        while (o < b.size) {
            o += Os.pwrite(fd, b, o, b.size - o, at + o)
        }
    }

    /**
     * pdfium 에 넘길 서술자. **복제본**을 준다 — 받은 쪽(`PdfRenderer`)이 그것을 닫고,
     * 이 싱크는 자기 것을 [close] 로 닫는다. 둘 다 닫혀야 메모리가 풀린다.
     */
    fun descriptor(): ParcelFileDescriptor {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        return ParcelFileDescriptor.dup(fd)
    }

    override fun close() {
        try {
            Os.close(fd)
        } catch (_: Exception) {
            // 이미 닫혔다. 닫기의 실패로 할 일은 없다.
        }
    }

    companion object {
        fun create(): MemfdSink = MemfdSink(Os.memfd_create("iroiro-pdf", OsConstants.MFD_CLOEXEC))
    }
}
