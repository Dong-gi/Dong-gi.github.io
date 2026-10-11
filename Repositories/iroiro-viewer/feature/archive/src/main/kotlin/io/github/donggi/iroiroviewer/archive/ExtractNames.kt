package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.io.PathRules
import io.github.donggi.iroiroviewer.safety.ArchivePath
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/**
 * 아카이브 엔트리 이름 → 실제 파일 경로. **계산은 여기 한 번만 있다.**
 *
 * ## 왜 함수를 따로 빼는가
 *
 * 화면은 풀기 전에 "덮어쓸 것이 몇 개인가" 를 미리 세어 사용자에게 묻고, 엔진은 그
 * 뒤에 실제로 쓴다. **둘이 이름을 다르게 계산하면 사용자가 본 것과 덮어써지는 것이
 * 달라진다.** 4단계 검토가 치명으로 고친 결함이 정확히 그 모양이었다 — 화면은 원래
 * 이름을, 엔진은 다듬은 이름을 봐서 `a?.txt` 가 경고 없이 `a_.txt` 를 덮어썼다.
 *
 * 그래서 선검사와 엔진이 **같은 함수**를 부른다. 두 곳에 같은 규칙을 적어 두고
 * "같게 유지하자" 고 약속하는 방법은 이 프로젝트에서 이미 실패했다.
 *
 * ## 세 관문
 *
 * 1. [ArchiveEntry.safeName] — [ArchivePath.sanitize] 가 만든다. 탈출(`..`)·제어문자·
 *    끝점을 거르고 절대경로·드라이브 문자를 상대경로로 다듬는다.
 * 2. **조각마다** [PathRules.sanitize] — FAT 금지문자와 255바이트 상한. 조각 하나를
 *    받는 함수이므로 경로 전체를 통째로 넘기면 `/` 까지 치환된다.
 * 3. [ArchivePath.resolveInside] — canonical 봉쇄. 문자열 검사만으로는 목적지 안에
 *    이미 있던 심볼릭 링크를 타고 밖으로 나갈 수 있다.
 */
object ExtractNames {

    /**
     * 목적지 기준 상대 경로. 쓸 수 없는 이름이면 null.
     *
     * 1·2번 관문만 지난다. 3번은 목적지를 알아야 하므로 [targetOf] 가 한다.
     */
    fun relPathOf(entry: ArchiveEntry): String? {
        val safe = entry.safeName ?: return null
        val segments = safe.split('/')
        if (segments.isEmpty()) return null
        val cleaned = segments.map { PathRules.sanitize(it) }
        if (cleaned.any { it.isBlank() }) return null
        return cleaned.joinToString("/")
    }

    /** 실제로 쓸 파일. 목적지 밖으로 나가거나 봉쇄를 확인할 수 없으면([inside]) null. */
    fun targetOf(dest: File, entry: ArchiveEntry): File? {
        val rel = relPathOf(entry) ?: return null
        return inside(dest, rel)
    }

    /**
     * [ArchivePath.resolveInside] — 그런데 **봉쇄를 확인하지 못하면 쓸 수 없는 이름**(null)이다. canonical 경로는 실제 파일 시스템에
     * 묻는 것이라 실패할 수 있다 — 경로가 PATH_MAX(4,096바이트)를 넘거나(tar·cpio·ar 의 이름은 64 KiB 까지 온다) 링크가 고리를 이룬다.
     * 그 입출력 예외를 그대로 두면 계획(화면의 '전부 풀기')에서 앱이 죽고, 풀기는 그 항목에서 통째로 '입출력 실패' 로 끝났다.
     * 밖으로 나가지 않는다는 것을 확인할 수 없는 이름에는 쓰지 않는다 — 위험한 이름과 같은 갈래다. 계획과 엔진이 이것 하나를
     * 부른다(위 '왜 함수를 따로 빼는가').
     *
     * @param resolve 시험이 가짜를 꽂는다(윈도에서는 긴 경로가 실패하지 않는다).
     */
    fun inside(
        dest: File,
        relative: String,
        resolve: (File, String) -> File? = ArchivePath::resolveInside,
    ): File? = try {
        resolve(dest, relative)
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: IOException) {
        null
    }

    /**
     * 아카이브의 **최상위 조각**들. '여기에 풀기' 가 무엇을 흩뜨릴지 화면이 이것으로 센다.
     *
     * 폴더 하나로 잘 싸인 아카이브는 조각이 하나이고, 파일 스무 개가 맨 위에 있는
     * 아카이브는 스무 개다. 그 차이가 기본 목적지를 가른다.
     */
    fun topLevelNames(entries: List<ArchiveEntry>): List<String> =
        entries.mapNotNull { relPathOf(it)?.substringBefore('/') }.distinct()

    /**
     * '새 폴더에 풀기' 의 이름 후보 — 아카이브 이름에서 **확장자를 뗀** 것.
     *
     * 확장자가 두 겹인 것(`.tar.gz`·`.tar.lz4`·`.tar.Z` …)은 두 겹을 뗀다. 마지막 점만 떼면 `backup.tar.gz` 가
     * `backup.tar` 라는 **폴더**가 되어, 사용자는 그것을 tar 파일로 읽는다. 줄인 이름(`.tgz`·`.tlz`·`.taz`)은 한 겹이다.
     * 점이 없거나 이름 전체가 확장자면 이름을 그대로 둔다(`.hidden` 을 빈 이름으로 만들지 않는다).
     *
     * 파일 하나를 싼 것(`log.txt.gz`)은 한 겹만 뗀다 — 폴더가 `log.txt` 이고 그 안에 `log.txt` 가 풀린다(7-Zip 의 '…\에
     * 풀기' 와 같다). 항목이 하나뿐이라 기본값은 대개 '여기에 풀기' 다(`ExtractPlan.preferNewFolder`).
     */
    fun folderNameOf(archiveName: String): String {
        val lower = archiveName.lowercase()
        for (double in DOUBLE_EXTENSIONS) {
            if (!lower.endsWith(double)) continue
            return if (archiveName.length > double.length) archiveName.substring(0, archiveName.length - double.length)
            else archiveName
        }
        val dot = archiveName.lastIndexOf('.')
        return if (dot > 0) archiveName.substring(0, dot) else archiveName
    }

    /** 소문자로 견준다 — `.tar.Z` 는 `.tar.z` 로 걸린다. */
    private val DOUBLE_EXTENSIONS = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.lz4", ".tar.lzma", ".tar.z")
}
