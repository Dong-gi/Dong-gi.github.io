package io.github.donggi.iroiroviewer.archive

import java.io.File
import java.nio.file.Files

/**
 * 풀기의 진행 바가 무엇을 세는가.
 *
 * ## 분모가 둘이다
 *
 * 8단계는 **선언 크기 합**을 분모로, 쓴 바이트를 분자로 썼다. 선언 크기는 공격자가 적는 값이고(진행 바에만 쓰니
 * 해는 없다), 더 큰 문제는 solid 아카이브에서 **고르지 않은 앞 항목을 풀어서 버리는 동안 바가 멎어 있는** 것이다
 * — 목록 끝의 폴더 하나만 풀면 바가 0% 에 몇 분 서 있다가 한 번에 찬다.
 *
 * 그래서 리더가 셀 수 있으면 **아카이브에서 실제로 소비한 입력 바이트**를 쓴다(`ArchiveReader.inputBytesFor` 의
 * 형식별 표). 못 세면(-1) 예전 방식으로 물러난다. 둘을 섞지 않는다 — 분자와 분모가 같은 단위여야 한다.
 *
 * 화면(`OperationBars`)은 이 값을 '크기 / 크기' 로 적는다. 입력으로 셀 때 그 수는 **아카이브 쪽의 크기**다.
 */
internal class ExtractMeter(
    /** 리더가 준 분모. 0 이하면 모른다는 뜻이다. */
    inputTotal: Long,
    /** 물러날 때의 분모 — 고른 항목의 선언 크기 합. */
    private val declaredTotal: Long,
) {
    val byInput: Boolean = inputTotal > 0

    val bytesTotal: Long = if (byInput) inputTotal else declaredTotal

    var bytesDone: Long = 0L
        private set

    /** 리더가 알린 누적 소비량. 줄지 않고 분모를 넘지 않는다(버퍼가 미리 읽은 몫이 끝에서 넘칠 수 있다). */
    fun consumed(inputBytes: Long) {
        if (!byInput) return
        val clamped = inputBytes.coerceIn(0L, bytesTotal)
        if (clamped > bytesDone) bytesDone = clamped
    }

    /** 항목 하나가 끝났다. 입력으로 세는 동안에는 쓴 바이트를 더하지 않는다 — 단위가 다르다. */
    fun finished(written: Long) {
        if (byInput) return
        bytesDone += written.coerceAtLeast(0L)
    }
}

/**
 * 풀기가 **만든 폴더**의 수정시각을 끝에서 되살린다.
 *
 * ## 왜 끝에서, 깊은 것부터인가
 *
 * 폴더의 수정시각은 그 안에 이름이 생기거나 바뀔 때마다 '지금' 이 된다. 파일 하나를 쓰는 것이 곧 임시 이름을
 * 만들고 바꾸는 일이라(원자적 쓰기), 폴더를 만들 때 시각을 걸어 두면 첫 파일에서 지워진다. 그래서 **모든 쓰기가
 * 끝난 뒤** 한 번에 건다. 깊은 것부터 거는 것은 자식 폴더의 시각을 바꾸는 일이 부모에 닿지 않는다는 사실에
 * 기대지 않으려는 것이다 — 부모를 마지막에 걸면 어느 파일시스템에서도 부모가 마지막 값을 갖는다.
 *
 * ## 무엇만 건드리는가
 *
 * - **이번 풀기가 만든 폴더만.** '여기에 풀기' 로 이미 있던 폴더 안에 풀었다면 그 폴더는 사용자의 것이다.
 * - **아카이브에 시각이 적힌 폴더만.** 디렉터리 엔트리가 없어 우리가 지어 넣은 중간 폴더는 '지금' 으로 둔다 —
 *   모르면 지어내지 않는다(`ArchiveEntry.lastModified`). 새 폴더에 풀 때의 그 새 폴더도 대개 여기 든다 — 맨 위 폴더
 *   자신을 적은 항목(tar 의 `./`)이 있을 때만 그 시각을 받는다.
 * - 7z 도 폴더 항목을 소비자에게 준다(예전에는 7z 만 건너뛰어 빈 폴더조차 생기지 않았다).
 * - 실패는 무시한다. 시각을 못 거는 파일시스템(일부 FAT·FUSE)이 있고, 그것 때문에 풀기를 실패로 보고할 이유가 없다.
 */
internal class DirTimes {

    private val created = LinkedHashSet<String>()
    private val times = HashMap<String, Long>()

    /** 이번 풀기가 만든 폴더. */
    fun created(dir: File) {
        created += key(dir)
    }

    /** 아카이브의 디렉터리 엔트리가 적은 시각. 0 이하는 '모름' 이라 버린다. */
    fun entryTime(dir: File, millis: Long) {
        if (millis > 0) times[key(dir)] = millis
    }

    /**
     * 되살린다. 깊은 것부터. 건 수를 돌려준다(시험과 진단용).
     *
     * @param setter 시각을 거는 함수. 시험이 바꿔 끼운다.
     */
    fun restore(setter: (File, Long) -> Boolean = { f, t -> f.setLastModified(t) }): Int {
        var applied = 0
        val targets = created.filter { it in times }.sortedByDescending { depthOf(it) }
        for (path in targets) {
            val ok = try {
                setter(File(path), times.getValue(path))
            } catch (e: SecurityException) {
                false
            }
            if (ok) applied++
        }
        return applied
    }

    private fun key(dir: File): String = dir.absoluteFile.normalize().path

    private fun depthOf(path: String): Int = path.count { it == File.separatorChar }
}

/**
 * 풀어 쓸 자리가 **아카이브 자신**인가. '여기에 풀기' 가 아카이브와 같은 이름의 항목을 만나면 원본이 그 항목의 내용으로 바뀐다
 * — 엔진(`ArchiveExtractEngine`)은 정책(덮어쓰기·건너뛰기·둘 다 보관)과 상관없이 막는다.
 *
 * ## 문자열로는 모자란다
 *
 * canonical 경로가 다르다고 다른 파일이 아니다. **대소문자를 가리지 않는 볼륨**에서는 `a_b.gz` 와 `A_B.GZ` 가 한 파일이다 —
 * FAT·exFAT SD 카드가 그렇다(안드로이드의 공유 저장소도 대소문자를 가리지 않는다고 알려져 있다 — 기기에서 확인하지 못했다).
 * canonical 경로는 심볼릭 링크와 `..` 만 푼다(`realpath`). 대소문자는 적힌 그대로라 문자열이 달라진다. 예: gzip 의
 * FNAME `a?b.gz` 가 풀기의 관문(`PathRules.sanitize`)에서 `a_b.gz` 가 되어, 덮어쓰기로 '여기에 풀기' 하면 `A_B.GZ` 가 제
 * 내용으로 바뀌었다. 유니코드 정규화(NFC·NFD)가 다른 같은 이름도 같은 모양일 수 있다.
 *
 * 그래서 대상이 **이미 있으면** 파일의 정체로 견준다 — `Files.isSameFile`(유닉스에서는 장치 번호와 inode). 없는 대상은 아카이브일
 * 수 없다(아카이브는 지금 있다). 하드링크도 같은 파일로 본다 — 원자적 쓰기(rename)는 링크 하나만 바꾸므로 원본이 사라지지는
 * 않지만, 그런 자리를 덮어쓰라는 아카이브는 없다시피 하고 막는 쪽이 싸다.
 *
 * 정체를 읽지 못하면(권한·그 사이에 지워짐) [Verdict.UNKNOWN] 이다 — 엔진은 그 항목을 실패로 센다. **모르면 덮어쓰지 않는다.**
 */
internal class SelfOverwriteGuard(private val archive: File) {

    enum class Verdict { SELF, OTHER, UNKNOWN }

    /** 아카이브 자신의 canonical 경로. 대상이 아직 없을 때(가장 흔한 경우)는 이 문자열만 견준다. */
    private val archiveCanonical: String? = runCatching { archive.canonicalPath }.getOrNull()

    fun check(target: File): Verdict {
        if (archiveCanonical != null && runCatching { target.canonicalPath }.getOrNull() == archiveCanonical) return Verdict.SELF
        if (!target.exists()) return Verdict.OTHER
        val same = runCatching { Files.isSameFile(target.toPath(), archive.toPath()) }.getOrNull() ?: return Verdict.UNKNOWN
        return if (same) Verdict.SELF else Verdict.OTHER
    }
}
