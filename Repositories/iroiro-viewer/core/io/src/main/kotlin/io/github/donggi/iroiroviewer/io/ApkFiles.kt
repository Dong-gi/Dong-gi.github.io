package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/**
 * **설치 파일(APK)인가** — 이름(`.apk`)으로, 그리고 **내용**으로. 권한 없는 다른 앱이 Files 를 거쳐 APK 설치
 * 창을 띄우지 못하게 한다.
 *
 * ## 왜 내용까지 보는가
 *
 * 다른 앱이 이 앱에 넘긴 APK 를 '다른 앱으로 열기' 로 설치 관리자에 넘기면 설치 관리자는 그것을 **이 앱이 보낸 것**으로 받는다 —
 * 보낸 앱이 이 앱의 설치 권한(`REQUEST_INSTALL_PACKAGES`)과 '출처를 알 수 없는 앱' 허용을 빌려 쓴다. 이름만 보면 `invoice.pdf`·`a.zip`·
 * 확장자 없는 파일로 이름을 바꾼 APK 가 그 길을 그대로 지난다 — 모르는 형식은 '모든 앱에서 고르기'(`*` 형식)의 창으로 가고, 그 창에는
 * 설치 관리자도 선다(형식을 하나라도 적은 필터는 모든 형식에 맞는다 — `IntentFilter.findMimeType`). 설치 관리자는 이름을 보지 않고 내용을
 * 읽는다.
 *
 * ## 내용 — 설치 관리자가 찾는 것과 같게
 *
 * APK 는 ZIP 이고, 설치 관리자(libziparchive)는 **가운데 목록**에서 이름이 정확히 `AndroidManifest.xml` 인 항목을 찾는다. 그래서 같은
 * 차례로 읽는다 — 끝에서부터 끝 레코드(EOCD)를 찾고(주석 64 KiB 까지, 끝에 가장 가까운 것), ZIP64 면 그 위치표를 따라가, 가운데 목록의
 * 이름을 하나씩 바이트로 견준다. 폴더 안의 `app/src/main/AndroidManifest.xml`(소스 묶음)은 APK 가 아니다. 가운데 목록이 상한
 * ([MAX_DIRECTORY_BYTES]·[MAX_ENTRIES])보다 크면 **APK 로 친다** — 증명하지 못한 것을 설치 관리자에 넘기지 않는다(그런 ZIP 은 압축 화면도
 * 항목 상한으로 열지 않는다). 읽지 못하면 이름만 본다.
 *
 * 안드로이드 라이브러리 묶음(`.aar`)도 뿌리에 `AndroidManifest.xml` 이 있어 APK 로 보인다 — 다른 앱이 넘긴 것을 열지 않을 뿐이다.
 */
object ApkFiles {

    /** 설치 관리자가 찾는 이름. */
    private val MANIFEST = "AndroidManifest.xml".toByteArray(Charsets.US_ASCII)

    /** 끝 레코드를 찾는 범위 — 고정 22바이트 + 주석 최대 65,535바이트. */
    private const val EOCD_SIZE = 22
    private const val MAX_COMMENT = 0xFFFF

    /** 가운데 목록을 읽는 상한. 1만 항목 ZIP 의 가운데 목록은 1 MB 남짓이다. */
    const val MAX_DIRECTORY_BYTES = 64L * 1024 * 1024

    /** 가운데 목록의 항목 수 상한. */
    const val MAX_ENTRIES = 1_000_000L

    /** 이름이 APK 라고 말하는가 — 목록의 종류 표([MimeResolver.kindOf])로. */
    fun nameSaysApk(name: String): Boolean = MimeResolver.kindOf(name, isDirectory = false) == FileKind.APK

    /** 이름이나 내용으로 APK 인가. 디스크를 읽으므로 입출력 스레드에서 부른다. */
    fun isApk(file: File): Boolean = nameSaysApk(file.name) || hasManifest(file)

    /**
     * **다른 앱에 넘기지 않는가** — 다른 앱이 이 앱에 넘긴 파일([received])이 설치 파일([isApk])이면. 파일 목록에서 사용자가 고른 APK 는
     * 그대로 설치 관리자에 넘긴다(사용자 결정). [isApk] 는 받은 것일 때만 부른다(디스크를 읽는다).
     */
    fun refusesHandOff(received: Boolean, isApk: () -> Boolean): Boolean = received && isApk()

    /** ZIP 의 가운데 목록에 `AndroidManifest.xml` 이 있는가. 읽지 못하면 거짓이다. */
    fun hasManifest(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { hasManifest(it.channel) }
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    internal fun hasManifest(channel: SeekableByteChannel): Boolean {
        val size = channel.size()
        if (size < EOCD_SIZE) return false
        val tailLength = minOf(size, (EOCD_SIZE + MAX_COMMENT).toLong()).toInt()
        val tail = read(channel, size - tailLength, tailLength) ?: return false
        // 끝에 가장 가까운 끝 레코드 — libziparchive 의 차례(뒤에서 앞으로).
        var at = tail.size - EOCD_SIZE
        while (at >= 0 && !(tail[at] == 0x50.toByte() && tail.u32(at) == EOCD_SIG)) at--
        if (at < 0) return false
        val eocdOffset = size - tailLength + at
        var entries = tail.u16(at + 10).toLong()
        var directorySize = tail.u32(at + 12)
        var directoryOffset = tail.u32(at + 16)
        if (entries == 0xFFFFL || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
            // ZIP64 — 끝 레코드 바로 앞의 위치표가 ZIP64 끝 레코드를 가리킨다.
            if (eocdOffset < LOCATOR_SIZE) return false
            val locator = read(channel, eocdOffset - LOCATOR_SIZE, LOCATOR_SIZE) ?: return false
            if (locator.u32(0) != LOCATOR_SIG) return false
            val record = locator.u64(8)
            if (record < 0 || record > size - ZIP64_EOCD_SIZE) return false
            val zip64 = read(channel, record, ZIP64_EOCD_SIZE) ?: return false
            if (zip64.u32(0) != ZIP64_EOCD_SIG) return false
            entries = zip64.u64(32)
            directorySize = zip64.u64(40)
            directoryOffset = zip64.u64(48)
        }
        if (entries <= 0 || directorySize <= 0 || directoryOffset < 0) return false
        if (directoryOffset > size || directorySize > size - directoryOffset) return false
        // 너무 크면 APK 로 친다 — 증명하지 못한 것을 넘기지 않는다.
        if (directorySize > MAX_DIRECTORY_BYTES || entries > MAX_ENTRIES) return true
        val directory = read(channel, directoryOffset, directorySize.toInt()) ?: return false
        var p = 0
        var seen = 0L
        while (seen < entries && p + CENTRAL_SIZE <= directory.size) {
            if (directory.u32(p) != CENTRAL_SIG) return false
            val nameLength = directory.u16(p + 28)
            val extraLength = directory.u16(p + 30)
            val commentLength = directory.u16(p + 32)
            val name = p + CENTRAL_SIZE
            if (name + nameLength > directory.size) return false
            if (nameLength == MANIFEST.size && (0 until nameLength).all { directory[name + it] == MANIFEST[it] }) return true
            p = name + nameLength + extraLength + commentLength
            seen++
        }
        return false
    }

    private fun read(channel: SeekableByteChannel, at: Long, length: Int): ByteArray? {
        val buffer = ByteBuffer.allocate(length)
        channel.position(at)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) return null
        }
        return buffer.array()
    }

    private fun ByteArray.u16(at: Int): Int = ByteBuffer.wrap(this, at, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun ByteArray.u32(at: Int): Long = ByteBuffer.wrap(this, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    private fun ByteArray.u64(at: Int): Long = ByteBuffer.wrap(this, at, 8).order(ByteOrder.LITTLE_ENDIAN).long

    private const val EOCD_SIG = 0x06054b50L
    private const val LOCATOR_SIG = 0x07064b50L
    private const val ZIP64_EOCD_SIG = 0x06064b50L
    private const val CENTRAL_SIG = 0x02014b50L
    private const val LOCATOR_SIZE = 20
    private const val ZIP64_EOCD_SIZE = 56
    private const val CENTRAL_SIZE = 46
}
