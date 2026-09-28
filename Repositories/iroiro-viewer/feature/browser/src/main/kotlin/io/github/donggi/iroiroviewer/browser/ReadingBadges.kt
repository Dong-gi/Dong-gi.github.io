package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.data.ComicProgressEntity
import io.github.donggi.iroiroviewer.data.DocProgressEntity
import io.github.donggi.iroiroviewer.data.ProgressLookup
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor

/**
 * 목록의 읽던 쪽 배지(9·11단계가 14단계로 미룬 '읽던 쪽 배지·읽음 표시').
 *
 * ## 스키마를 바꾸지 않는다
 *
 * '다 읽음' 은 저장된 값에서 **끌어낸다** — 마지막 쪽(마지막 자리)에 닿았으면 다 읽은 것이다. `finished_at` 칸을
 * 더하면 마이그레이션이 하나 늘고, 그 값은 이미 있는 두 칸(`page`·`page_count`)이 말하는 것과 같다.
 */
data class ReadingBadge(
    /** 지금 자리(1부터). [unit] 이 [BadgeUnit.PERCENT] 면 백분율(0~100)이다. */
    val current: Int,
    /** 전체. 백분율이면 100. */
    val total: Int,
    val unit: BadgeUnit,
    val finished: Boolean,
) {
    /** 막대가 채울 몫(0~1). */
    val fraction: Float get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}

/** 배지가 세는 단위. 문서 뷰어가 이어보기 안내에서 쓰는 단위와 같은 갈래다(`DocViewModel.ResumeUnit`). */
enum class BadgeUnit { PAGE, CHAPTER, SHEET, SLIDE, PART, PERCENT }

object ReadingBadges {

    /**
     * 흐름 문서의 `progress` 가 이만큼이면 끝에 닿은 것으로 본다. 스크롤 위치는 마지막 한 줄을 화면 위쪽에
     * 올려놓을 수 없어 1.0 에 정확히 닿지 않는 일이 흔하다.
     */
    const val FINISHED_PROGRESS = 0.99

    /** 이 종류의 파일에 배지를 달 수 있는가. 이어보기를 적는 뷰어(만화·문서)가 여는 것만이다. */
    fun wantsBadge(entry: FileEntry): Boolean = !entry.isDirectory && unitOf(entry.kind) != null

    /**
     * 종류마다 세는 단위. **폴더 만화는 뺀다** — 만화 뷰어는 폴더의 키도 `File` 의 크기·시각으로 만드는데
     * 목록은 폴더의 크기를 0 으로 적는다(`DirectoryLister` — 폴더의 `st_size` 는 거짓말이다). 키를 맞추려면
     * 폴더마다 stat 을 또 해야 하고, 폴더 만화는 드물다.
     */
    fun unitOf(kind: FileKind): BadgeUnit? = when (kind) {
        FileKind.COMIC, FileKind.PDF -> BadgeUnit.PAGE
        FileKind.EBOOK -> BadgeUnit.CHAPTER
        FileKind.SHEET -> BadgeUnit.SHEET
        FileKind.SLIDE -> BadgeUnit.SLIDE
        FileKind.DOCUMENT, FileKind.HWP -> BadgeUnit.PART
        else -> null
    }

    /**
     * 쪽이 있는 기록(만화·PDF·EPUB 의 장·시트·슬라이드·부분)의 배지.
     *
     * **자리가 하나뿐이면 배지를 달지 않는다.** 열기만 해도 마지막 자리에 닿으므로 '다 읽음' 이 거짓이 되고
     * (한 부분짜리 docx 를 열고 바로 닫아도 '다 읽음'), `1/1` 은 아무것도 말하지 않는다.
     *
     * @param page 저장된 자리(0부터 — 두 뷰어가 모두 그렇게 적는다).
     */
    fun ofPages(page: Int, pageCount: Int, unit: BadgeUnit): ReadingBadge? {
        if (pageCount < 2 || page < 0) return null
        val at = page.coerceAtMost(pageCount - 1)
        return ReadingBadge(current = at + 1, total = pageCount, unit = unit, finished = at >= pageCount - 1)
    }

    /** 흐름 문서의 몫(0~1)으로 만든 배지. 값이 아니면(NaN·무한) 없다. */
    fun ofProgress(progress: Double): ReadingBadge? {
        if (progress.isNaN() || progress.isInfinite()) return null
        val p = progress.coerceIn(0.0, 1.0)
        // 반올림하면 0.995 가 '100%' 인데 '다 읽음' 이 아닌 어색한 자리가 생긴다. 내림으로 적는다.
        return ReadingBadge(
            current = floor(p * 100).toInt(),
            total = 100,
            unit = BadgeUnit.PERCENT,
            finished = p >= FINISHED_PROGRESS,
        )
    }

    fun ofComic(entity: ComicProgressEntity): ReadingBadge? = ofPages(entity.page, entity.pageCount, BadgeUnit.PAGE)

    /**
     * 문서의 배지. **몫(`progress`)이 있으면 그것을 먼저 쓴다** — 쪽 번호보다 정밀하고(EPUB 은 `page` 가 장
     * 번호다), 흐름 포맷은 쪽이 없다(`DocProgressEntity` 의 주석). 지금은 어느 뷰어도 몫을 적지 않으므로 쪽으로 간다.
     */
    fun ofDoc(entity: DocProgressEntity, kind: FileKind): ReadingBadge? {
        entity.progress?.let { p -> ofProgress(p)?.let { return it } }
        val page = entity.page ?: return null
        val count = entity.pageCount ?: return null
        val unit = unitOf(kind) ?: return null
        return ofPages(page, count, unit)
    }

    /**
     * 이어보기 키. **뷰어가 부르는 것과 같은 호출이어야 한다** — `FileKey.of(file.name, file.length(),
     * file.lastModified())`(`ComicViewModel.restore`·`DocViewModel.restore`).
     *
     * 목록의 [FileEntry.lastModified] 를 쓰지 않는 까닭: 그것은 `lstat` 의 `st_mtime`(초 단위)에 1000 을 곱한
     * 값이고, `File.lastModified()` 가 밀리초까지 주는 플랫폼이면 둘이 달라 **모든 배지가 조용히 빠진다.**
     * 기기의 `File.lastModified()` 정밀도는 확인하지 않았다 — 같은 호출을 쓰면 어느 쪽이든 맞는다.
     * 링크도 같다: 뷰어는 링크를 따라간 파일의 크기를 본다(`lstat` 이 아니다).
     */
    fun keyFor(file: File): String = FileKey.of(file.name, file.length(), file.lastModified())

    /**
     * 한 번에 묻는 키의 묶음. SQLite 의 매개변수 상한을 넘지 않게 [ProgressLookup.MAX_KEYS] 씩 자른다.
     * 겹치는 키는 한 번만 묻는다.
     */
    fun chunks(keys: Collection<String>): List<List<String>> =
        keys.distinct().chunked(ProgressLookup.MAX_KEYS)

    /**
     * 폴더 하나의 배지를 찾는다. 경로 → 배지.
     *
     * **휴지통에서는 찾지 않는다.** 지운 것에 '읽던 쪽' 을 다는 것은 지운 것이 지워지지 않은 셈이고, 휴지통 안의
     * 이름은 uuid 라 키도 맞지 않는다. 목록이 휴지통 폴더를 보여 주지 않지만 이 함수는 경로를 받으므로 한 번 더 막는다.
     */
    suspend fun lookup(
        folder: String?,
        entries: List<FileEntry>,
        /** 만화 기록을 키 묶음으로 묻는다. 보통은 `ComicProgressDao.findAll` — 시험은 가짜를 넘긴다. */
        findComics: suspend (List<String>) -> List<ComicProgressEntity>,
        /** 문서 기록을 키 묶음으로 묻는다. 보통은 `DocProgressDao.findAll`. */
        findDocs: suspend (List<String>) -> List<DocProgressEntity>,
    ): Map<String, ReadingBadge> = withContext(IroDispatchers.io) {
        if (folder == null || TrashStore.isTrashPath(folder)) return@withContext emptyMap()
        val wanted = entries.filter { wantsBadge(it) }
        if (wanted.isEmpty()) return@withContext emptyMap()

        // 키 → 항목. 한 폴더 안에서 이름은 겹치지 않으므로 키도 겹치지 않는다.
        val comics = HashMap<String, FileEntry>()
        val docs = HashMap<String, FileEntry>()
        for (e in wanted) {
            currentCoroutineContext().ensureActive()
            val key = keyFor(File(e.path))
            if (e.kind == FileKind.COMIC) comics[key] = e else docs[key] = e
        }

        val out = HashMap<String, ReadingBadge>()
        for (chunk in chunks(comics.keys)) {
            currentCoroutineContext().ensureActive()
            for (row in findComics(chunk)) {
                val e = comics[row.fileKey] ?: continue
                ofComic(row)?.let { out[e.path] = it }
            }
        }
        for (chunk in chunks(docs.keys)) {
            currentCoroutineContext().ensureActive()
            for (row in findDocs(chunk)) {
                val e = docs[row.fileKey] ?: continue
                ofDoc(row, e.kind)?.let { out[e.path] = it }
            }
        }
        out
    }
}
