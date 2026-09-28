package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.format.archive.ComicPages
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.NameSortKey
import java.io.File

/**
 * 같은 폴더의 **다음 권**을 고른다(14단계). 9단계가 '권 번호를 이름에서 읽는 규칙이 필요하다' 며 미뤄 둔 것이다.
 *
 * ## 권 번호를 읽지 않는다 — 파일 관리자의 이름 차례를 쓴다
 *
 * `1권`·`Vol.02`·`제3화`·`#4` 처럼 권을 적는 방식은 끝이 없고, 그것을 맞히는 규칙은 `ComicPages` 와 다른 종류의
 * 추측이다. 대신 **파일 관리자가 이름으로 늘어놓는 차례**(`NameSortKey` — 자연 정렬 + 한국어 대조)에서 지금 책
 * 바로 뒤의 책을 고른다. 목록에서 사용자가 보는 차례와 같으므로 '왜 이것이 다음인가' 를 따로 설명할 필요가 없고,
 * 틀려도 사용자가 목록에서 본 그대로다.
 *
 * **폴더를 앞에 모으지 않는다**(목록의 `foldersFirst` 를 따르지 않는다). 권은 이름으로 이어지고, 한 시리즈에 폴더
 * 권과 압축 권이 섞여 있을 때 폴더를 앞에 모으면 `2권/` 다음이 `1권.cbz` 가 된다. 정렬 기준(날짜·크기)도 따르지
 * 않는다 — 날짜로 늘어놓은 목록의 '다음' 은 권의 차례가 아니다.
 *
 * ## 무엇이 권인가
 *
 * - 만화 확장자(cbz·cbr·cb7·cbt) 파일.
 * - **지금 책과 확장자가 같은 일반 압축**(zip·7z·rar) — 권마다 `.zip` 으로 묶어 둔 시리즈가 흔하다. 지금 책이
 *   만화 확장자일 때 옆의 `.zip` 은 권으로 치지 않는다 — 그것은 아무 압축 파일일 수 있다.
 * - 쪽(그림)을 직접 담은 폴더 — '이 폴더를 만화로 보기' 가 여는 것과 같은 규칙(`comicFilesIn`)이다.
 *
 * 숨김 항목(`.` 으로 시작)은 목록이 숨김을 보일 때만 후보다. 우리 휴지통 폴더는 언제나 뺀다.
 */
object NextVolume {

    /** 폴더 안의 한 항목. 이름과 폴더 여부만 안다 — 목록을 만들며 파일을 열지 않는다. */
    data class Candidate(val name: String, val isDirectory: Boolean)

    /**
     * 폴더인 후보를 몇 개까지 들여다보는가.
     *
     * 폴더가 권인지 알려면 그 폴더를 나열해야 한다. 지금 책 뒤에 권이 아닌 폴더가 수천 개 늘어서 있어도 책을 여는
     * 일이 그것 때문에 길어지지 않게 묶는다 — 넘으면 '다음 권이 없다' 로 친다.
     */
    const val MAX_FOLDER_PROBES = 64

    private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar")

    /**
     * @param current 지금 책(파일이면 확장자를 포함한 이름).
     * @param siblings 같은 폴더의 항목 전부(지금 책을 포함해도 되고 빠져 있어도 된다).
     * @param showHidden 숨김 항목도 후보로 볼 것인가(파일 목록의 설정).
     * @param isComicFolder 폴더 후보가 쪽을 담았는가. **파일시스템을 읽는 자리라 필요할 때만 부른다** —
     *   지금 책 뒤에서 차례대로, 권을 찾으면 멈춘다.
     * @param isReadableFile 권 이름을 가진 파일 후보가 실제로 읽을 수 있는 파일인가. 아니면 그다음을 본다 —
     *   이름으로 짐작한 것이 폴더였거나, 나열한 뒤에 지워졌을 수 있다.
     */
    fun pick(
        current: Candidate,
        siblings: List<Candidate>,
        showHidden: Boolean,
        isComicFolder: (Candidate) -> Boolean,
        isReadableFile: (Candidate) -> Boolean = { true },
    ): Candidate? {
        val collator = NameSortKey.koreanCollator()
        val currentKey = NameSortKey.of(current.name, collator)
        val currentExt = if (current.isDirectory) "" else MimeResolver.extensionOf(current.name)

        // 지금 책보다 **뒤에** 오는 것만. 같은 키(대소문자만 다른 이름)는 이름 글자로 가른다 — 목록의 안정 정렬과 같다.
        val after = siblings.asSequence()
            .filter { it.name != current.name || it.isDirectory != current.isDirectory }
            .filter { it.name != TrashStore.DIR_NAME }
            .filter { showHidden || !it.name.startsWith('.') }
            .map { it to NameSortKey.of(it.name, collator) }
            .filter { (c, key) ->
                val cmp = key.compareTo(currentKey)
                cmp > 0 || (cmp == 0 && c.name > current.name)
            }
            .sortedWith(compareBy<Pair<Candidate, NameSortKey>> { it.second }.thenBy { it.first.name })
            .map { it.first }

        var probes = 0
        for (c in after) {
            if (c.isDirectory) {
                if (probes >= MAX_FOLDER_PROBES) return null
                probes++
                if (isComicFolder(c)) return c
            } else if (isVolumeFile(c.name, currentExt) && isReadableFile(c)) {
                return c
            }
        }
        return null
    }

    /** 이 파일이 권인가 — 만화 확장자이거나, 지금 책과 같은 일반 압축. */
    internal fun isVolumeFile(name: String, currentExtension: String): Boolean {
        if (MimeResolver.kindOf(name, false) == FileKind.COMIC) return true
        val ext = MimeResolver.extensionOf(name)
        return ext.isNotEmpty() && ext == currentExtension && ext in ARCHIVE_EXTENSIONS
    }

    /**
     * 파일시스템에서 고른다. **IO 스레드에서 부른다.**
     *
     * `File.list()` 로 이름만 읽는다 — 항목마다 `stat` 을 부르는 `listFiles()` 는 1만 개 폴더에서 체감된다
     * (`DirectoryLister` 의 주석). 폴더 여부는 지금 책 **뒤의** 후보에만 묻는다.
     *
     * @param reachable 파일 후보가 실제로 열리는가([isReachable]). 시험이 '있는데 열리지 않는 파일' 을 흉내 내는 자리다.
     */
    fun find(book: File, showHidden: Boolean, reachable: (File) -> Boolean = ::isReachable): File? {
        val parent = book.absoluteFile.parentFile ?: return null
        val names = parent.list() ?: return null
        val current = Candidate(book.name, book.isDirectory)
        // 폴더 여부를 이름만으로 가른다(항목마다 stat 을 부르지 않으려고).
        // - 권 확장자(만화·일반 압축) → 파일로 본다. 아래에서 실제로 파일인지 한 번 더 확인한다.
        // - 이 앱이 아는 다른 종류(그림·글·영상…) → 권이 아니다. 후보에서 뺀다 — 그런 이름의 폴더는 드물다.
        // - 확장자가 없거나 모르는 것(`Vol.1` 도 여기 든다) → 폴더일 수 있다. 차례가 오면 그때 묻는다.
        val siblings = names.mapNotNull { name ->
            val kind = MimeResolver.kindOf(name, false)
            when {
                kind == FileKind.COMIC || kind == FileKind.ARCHIVE -> Candidate(name, isDirectory = false)
                kind == FileKind.OTHER -> Candidate(name, isDirectory = true)
                else -> null
            }
        }
        val picked = pick(
            current = current,
            siblings = siblings,
            showHidden = showHidden,
            isComicFolder = { c -> File(parent, c.name).let { it.isDirectory && isComicFolder(it) } },
            // 이름만 보고 '파일' 로 짐작한 것이 실제로 읽을 수 있는 파일인지 여기서 확인한다. 책을 여는 문(`ComicOpen.open`)과
            // 같은 탐침이다([isReachable]) — `canRead()` 로 물으면 그 답과 여는 답이 어긋나는 파일(FUSE)에서, 권해 준 다음
            // 권이 열자마자 '읽을 수 없다' 로 끝나거나 열리는 권을 건너뛴다. 후보를 차례로 하나씩 열어 보므로 대개 한 번이다.
            isReadableFile = { c -> reachable(File(parent, c.name)) },
        ) ?: return null
        return File(parent, picked.name)
    }

    /** 쪽을 한 장이라도 직접 담았는가. 첫 장을 찾으면 멈춘다. */
    private fun isComicFolder(dir: File): Boolean {
        val names = dir.list() ?: return false
        return names.any { ComicPages.isPage(it) && File(dir, it).isFile }
    }
}

/**
 * 다음 권을 **언제** 권하는가(14단계). 책 한 권 동안의 작은 상태 기계다.
 *
 * `ComicViewModel` 은 Room·DataStore 를 직접 불러 JVM 시험이 닿지 못한다. 그래서 판단만 여기로 떼었다 —
 * [ReadingStart] 와 같은 이유다.
 *
 * - 끝에 닿았고 **다음 권을 찾았을 때** 권한다. 둘 중 무엇이 먼저 오든 된다 — 폴더를 나열하는 일은 책을 연 뒤에
 *   따로 돌아서, 짧은 책은 찾기 전에 끝에 닿는다.
 * - 책 한 권에 **한 번만** 권한다. 끝에서 앞뒤로 넘길 때마다 알림이 뜨면 방해다.
 * - 다음 권이 없으면 권하지 않는다.
 * - 책이 바뀌면 [reset] 한다(VM 이 여는 첫머리와 닫을 때).
 */
internal class NextOfferGate {

    private var endReached = false
    private var offered = false
    private var found = false

    fun reset() {
        endReached = false
        offered = false
        found = false
    }

    /** 다음 권을 찾아 본 결과가 나왔다. @return 지금 권할 때인가. */
    fun onFound(exists: Boolean): Boolean {
        found = exists
        return poll()
    }

    /** 화면이 책의 끝에 닿았다. @return 지금 권할 때인가. */
    fun onReachedEnd(): Boolean {
        endReached = true
        return poll()
    }

    private fun poll(): Boolean {
        if (!endReached || offered || !found) return false
        offered = true
        return true
    }
}
