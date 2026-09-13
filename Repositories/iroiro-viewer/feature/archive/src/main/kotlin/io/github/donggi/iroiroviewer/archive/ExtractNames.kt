package io.github.donggi.iroiroviewer.archive

import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.io.PathRules
import io.github.donggi.iroiroviewer.safety.ArchivePath
import java.io.File

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

    /** 실제로 쓸 파일. 목적지 밖으로 나가면 null. */
    fun targetOf(dest: File, entry: ArchiveEntry): File? {
        val rel = relPathOf(entry) ?: return null
        return ArchivePath.resolveInside(dest, rel)
    }

    /**
     * 아카이브의 **최상위 조각**들. '여기에 풀기' 가 무엇을 흩뜨릴지 화면이 이것으로 센다.
     *
     * 폴더 하나로 잘 싸인 아카이브는 조각이 하나이고, 파일 스무 개가 맨 위에 있는
     * 아카이브는 스무 개다. 그 차이가 기본 목적지를 가른다.
     */
    fun topLevelNames(entries: List<ArchiveEntry>): List<String> =
        entries.mapNotNull { relPathOf(it)?.substringBefore('/') }.distinct()
}
