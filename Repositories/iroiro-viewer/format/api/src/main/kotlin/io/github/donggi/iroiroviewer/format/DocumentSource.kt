package io.github.donggi.iroiroviewer.format

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel

/**
 * 파서가 읽는 대상. 파일일 수도, 아카이브 안의 엔트리일 수도, 테스트의 바이트 배열일
 * 수도 있다.
 *
 * **경로가 아니라 [displayName] 만 노출한다.** 파서는 파일이 어디에 있는지 알 필요가
 * 없고, 알면 그 경로가 로그·예외 메시지·경고 문구를 타고 밖으로 샌다.
 *
 * 무작위 접근이 필요한 포맷(zip·7z·rar·CFB)이 있어서 [openChannel]·[asFile] 을 둔다.
 * 순차로만 읽을 수 있는 원본(아카이브 안의 아카이브 등)은 둘 다 null 을 돌려주고,
 * 그런 경우 파서는 임시 파일로 옮기든 포기하든 스스로 정한다.
 */
interface DocumentSource {

    /** 화면과 진단에 쓸 이름. 보통 확장자를 포함한 파일 이름이다. */
    val displayName: String

    /** 바이트 수. 모르면 -1. */
    val length: Long

    /** 처음부터 순차로 읽는 스트림. 부를 때마다 새로 연다. */
    fun openStream(): InputStream

    /** 무작위 접근 채널. 지원하지 않으면 null. 쓰는 쪽이 닫는다. */
    fun openChannel(): SeekableByteChannel? = null

    /** 실제 파일. 파일을 직접 요구하는 라이브러리(junrar)를 위한 탈출구. 없으면 null. */
    fun asFile(): File? = null

    /**
     * 앞부분 [n] 바이트. 포맷 판별에만 쓴다. 파일이 짧으면 짧은 배열이 온다.
     */
    fun head(n: Int = 64): ByteArray = openStream().use { stream ->
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = stream.read(buf, read, n - read)
            if (r <= 0) break
            read += r
        }
        if (read == n) buf else buf.copyOf(read)
    }
}

/** 파일 하나. 가장 흔한 경우다. */
class FileDocumentSource(private val file: File) : DocumentSource {

    override val displayName: String get() = file.name

    override val length: Long get() = file.length()

    override fun openStream(): InputStream = file.inputStream()

    override fun openChannel(): SeekableByteChannel = RandomAccessFile(file, "r").channel

    override fun asFile(): File = file
}

/** 메모리 위의 바이트. 테스트와 작은 내장 자원(아카이브 안의 한 엔트리)에 쓴다. */
class ByteArrayDocumentSource(
    private val bytes: ByteArray,
    override val displayName: String,
) : DocumentSource {

    override val length: Long get() = bytes.size.toLong()

    override fun openStream(): InputStream = bytes.inputStream()

    /**
     * 무작위 접근도 된다 — **암호를 푼 OOXML 패키지**가 이 모양으로 ZIP 리더에 들어간다.
     * 평문을 저장소에 쓰지 않는다는 규칙(CLAUDE.md '암호가 걸린 파일') 때문에 푼 바이트는
     * 메모리에만 있고, ZIP 은 중앙 디렉터리를 끝에서 찾으므로 채널이 필요하다.
     */
    override fun openChannel(): SeekableByteChannel = ByteArrayChannel(bytes)

    override fun head(n: Int): ByteArray = bytes.copyOf(minOf(n, bytes.size))
}

/**
 * 바이트 배열 위의 **읽기 전용** 채널. 배열을 복사하지 않는다 — 여러 채널이 같은 배열을
 * 나눠 읽어도 된다(자리는 채널마다 따로다).
 */
class ByteArrayChannel(private val bytes: ByteArray) : SeekableByteChannel {

    private var position = 0L
    private var open = true

    override fun read(dst: java.nio.ByteBuffer): Int {
        check()
        if (position >= bytes.size) return -1
        val n = minOf(dst.remaining().toLong(), bytes.size - position).toInt()
        dst.put(bytes, position.toInt(), n)
        position += n
        return n
    }

    override fun write(src: java.nio.ByteBuffer): Int = throw java.nio.channels.NonWritableChannelException()

    override fun position(): Long {
        check()
        return position
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        check()
        require(newPosition >= 0) { "음수 자리" }
        position = newPosition
        return this
    }

    override fun size(): Long {
        check()
        return bytes.size.toLong()
    }

    override fun truncate(size: Long): SeekableByteChannel = throw java.nio.channels.NonWritableChannelException()

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }

    private fun check() {
        if (!open) throw java.nio.channels.ClosedChannelException()
    }
}
