package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.model.NameSortKey
import java.text.Collator
import java.util.Locale

/**
 * 아카이브 엔트리(또는 폴더 안의 파일) 가운데 **무엇이 만화의 쪽이고, 어떤 차례인가.**
 *
 * ## 왜 순수 JVM 에 두는가
 *
 * 여기 든 규칙은 전부 문자열 규칙이고 경계가 많다 — `__MACOSX` 같은 쓰레기, 확장자가
 * 대문자인 파일, `1·2·10` 을 `1·10·2` 로 세우는 사전식 정렬, 장별 폴더. 화면에 두면
 * 그것을 전부 에뮬레이터로만 확인하게 된다. 또 **만화 뷰어와 폴더 만화가 같은 규칙을
 * 써야 하는데** 둘은 서로 다른 모듈에서 목록을 얻는다(아카이브 / `core:io` 의 나열).
 *
 * ## 쪽의 차례는 이름이 정한다
 *
 * 아카이브 저장 순서를 쓰는 길도 있지만, 실제 만화 파일은 압축 도구가 이름순으로 넣어
 * 주지 않는 경우가 흔하고(폴더를 통째로 끌어 넣으면 파일시스템 순서가 그대로 들어간다),
 * 사람이 기대하는 것은 언제나 **이름의 자연 정렬**이다. 그래서 이름으로 세운다.
 * 저장 순서로 보고 싶은 사람을 위해 화면이 따로 고를 수 있게 둔다.
 */
object ComicPages {

    /**
     * 플랫폼이 실제로 디코딩하는 그림 확장자.
     *
     * **SVG·TIFF 를 넣지 않는다.** 확장자는 그림처럼 보이지만 안드로이드 디코더가 읽지
     * 못한다(지침의 '지원하지 않는 것' 참고). 쪽으로 세어 두면 만화 한가운데에 영영
     * 열리지 않는 쪽이 생긴다.
     */
    private val PAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
    )

    /**
     * 압축 도구와 운영체제가 남기는 찌꺼기.
     *
     * macOS 의 `__MACOSX/` 는 **모든 쪽마다 하나씩** 들어 있어서, 거르지 않으면 쪽 수가
     * 정확히 두 배로 보이고 한 장 건너 한 장이 깨진 그림이 된다.
     */
    private val JUNK_NAMES = setOf("thumbs.db", "desktop.ini", ".ds_store", "comicinfo.xml")

    private const val MACOSX_DIR = "__macosx"

    /** 이 이름이 읽을 쪽인가. 경로가 들어 있어도 된다. */
    fun isPage(name: String): Boolean {
        if (isJunk(name)) return false
        val leaf = name.substringAfterLast('/')
        val dot = leaf.lastIndexOf('.')
        if (dot <= 0) return false
        return leaf.substring(dot + 1).lowercase(Locale.ROOT) in PAGE_EXTENSIONS
    }

    /**
     * 쪽으로 세지 말아야 할 이름인가.
     *
     * 숨김 파일(`.` 로 시작)도 거른다 — macOS 가 남기는 `._그림.png` 가 그것이고,
     * 내용이 그림이 아니라 리소스 포크라 열면 깨진다.
     */
    fun isJunk(name: String): Boolean {
        val segments = name.split('/', '\\')
        if (segments.any { it.lowercase(Locale.ROOT) == MACOSX_DIR }) return true
        val leaf = segments.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        if (leaf.isEmpty()) return true
        if (leaf.startsWith(".")) return true
        return leaf in JUNK_NAMES
    }

    /**
     * 읽을 쪽들을, 읽는 차례대로.
     *
     * @param names 후보 이름. 목록의 자리(index)가 그대로 돌아오는 값의 뜻이다 —
     *   **이름이 아니라 자리를 돌려주는 것**은 같은 이름의 엔트리가 실재하기 때문이다.
     * @return 원래 목록에서의 자리들. 쪽이 아닌 것은 빠진다.
     */
    fun order(
        names: List<String>,
        collator: Collator = NameSortKey.koreanCollator(),
    ): List<Int> {
        val keyed = ArrayList<Triple<Int, NameSortKey, NameSortKey>>(names.size)
        for ((i, n) in names.withIndex()) {
            if (!isPage(n)) continue
            // **폴더와 파일 이름을 따로 센다.** 한 문자열로 세우면 `ch2/p1` 이 `ch10/p1`
            // 보다 뒤에 오는 것까지는 맞지만, 구분자 자리에서 숫자와 글자가 섞여
            // 비교가 흔들린다. 장별 폴더가 흔하므로 두 단계로 나눈다.
            val dir = n.substringBeforeLast('/', "")
            val leaf = n.substringAfterLast('/')
            keyed += Triple(i, NameSortKey.of(dir, collator), NameSortKey.of(leaf, collator))
        }
        keyed.sortWith(
            compareBy<Triple<Int, NameSortKey, NameSortKey>> { it.second }
                .thenBy { it.third }
                // 이름까지 같으면 **원래 자리**로 가른다. 안정적인 차례가 나와야
                // 이어보기의 '몇 쪽' 이 다음에 열 때도 같은 그림을 가리킨다.
                .thenBy { it.first },
        )
        return keyed.map { it.first }
    }

    /**
     * 표지로 쓸 쪽. 없으면 -1.
     *
     * 첫 쪽을 쓴다. `cover.jpg` 같은 이름을 특별 취급하지 않는 것은, 그 관행이 표준이
     * 아니고 자연 정렬에서 어차피 앞쪽에 오는 경우가 많기 때문이다. 규칙을 늘리면
     * **틀리는 경우도 함께 는다.**
     */
    fun coverIndex(names: List<String>, collator: Collator = NameSortKey.koreanCollator()): Int =
        order(names, collator).firstOrNull() ?: -1
}
