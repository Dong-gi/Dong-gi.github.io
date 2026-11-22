package io.github.donggi.iroiroviewer.model

/**
 * 갤러리가 **어디를** 훑는가 — 볼륨마다 잘 알려진 사진 폴더 셋과 `Android/media`.
 *
 * ## `Android/media` 를 넣는 까닭
 *
 * 메신저(카카오톡·WhatsApp·텔레그램 등)는 Android 11 부터 받은 사진을 `Android/media/<꾸러미>/` 아래에 둔다 — 범위 저장소에서 앱이
 * 권한 없이 쓸 수 있는 공유 자리라서다. 사용자에게는 '받은 사진' 인데 DCIM·Pictures·Download 만 보면 갤러리에 없다. 이 앱은 모든 파일
 * 접근 권한으로 그 자리를 읽는다(`Android/data`·`obb` 와 달리 막혀 있지 않다 — 에뮬레이터에서 확인했다, CLAUDE.md).
 *
 * ## 상한
 *
 * `Android/media` 는 사용자가 만든 폴더가 아니라 **앱들이 쓰는 곳**이라 무엇이 얼마나 쌓였는지 모른다(알림음·스티커·미리보기를 두는 앱도
 * 있다). 그래서 그 뿌리 하나에서 담는 파일 수를 [MAX_MEDIA_FILES] 로 묶는다 — 넘으면 거기서 그 뿌리 훑기를 멈춘다(나머지 뿌리는 그대로).
 * 깊이·숨김(`.` 으로 시작)·`.nomedia`·링크 규칙은 다른 뿌리와 같다(`GalleryScanner.walk`). 앱이 감추려고 둔 `.nomedia`(WhatsApp 의
 * 보낸 사진 폴더 등)가 그대로 듣는다.
 */
object GalleryRoots {

    /** 볼륨마다 보는 사진 폴더. 볼륨 전체를 훑지 않는다 — 앱 캐시의 그림 수천 장이 섞이면 갤러리가 쓸모없어진다. */
    val DEFAULT_FOLDERS: List<String> = listOf("DCIM", "Pictures", "Download")

    /** 메신저가 받은 사진을 두는 자리(볼륨 아래). */
    const val MEDIA_FOLDER = "Android/media"

    /** [MEDIA_FOLDER] 뿌리 하나에서 담는 파일 수의 상한. */
    const val MAX_MEDIA_FILES = 20_000

    /** 훑을 뿌리 하나. [maxFiles] 개를 담으면 그 뿌리의 훑기를 멈춘다. */
    data class Root(val path: String, val maxFiles: Int)

    /** 볼륨 경로들 → 훑을 뿌리 후보(있는지는 보지 않는다 — 부르는 쪽이 폴더인 것만 남긴다). 볼륨마다 사진 폴더 셋, 그다음 `Android/media`. */
    fun candidates(volumePaths: List<String>): List<Root> = volumePaths.flatMap { v ->
        val base = v.trimEnd('/')
        DEFAULT_FOLDERS.map { Root("$base/$it", Int.MAX_VALUE) } + Root("$base/$MEDIA_FOLDER", MAX_MEDIA_FILES)
    }

    /**
     * [path] 가 어느 볼륨의 `Android/media` 아래인가. 갤러리는 그 사진의 썸네일을 **디스크에 남기지 않는다**(`persist=false`) — 갤러리는
     * 썸네일 통을 청소하지 않고(폴더 목록만 한다), 메신저가 지운 사진의 축소본이 앱 저장소에 남으면 안 된다.
     */
    fun isMediaPath(path: String): Boolean = path.contains("/$MEDIA_FOLDER/")

    /** 빈 갤러리 안내에 적을 폴더 이름들. */
    val SHOWN_FOLDERS: List<String> get() = DEFAULT_FOLDERS + MEDIA_FOLDER
}
