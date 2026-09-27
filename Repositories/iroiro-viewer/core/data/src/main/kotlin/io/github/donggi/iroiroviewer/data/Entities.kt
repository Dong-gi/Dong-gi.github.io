package io.github.donggi.iroiroviewer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 이어보기 위치.
 *
 * **키에 절대경로를 쓰지 않는다.** 경로를 키로 삼으면 파일을 옮기는 순간 기록이
 * 끊기고, 무엇보다 이 표가 "이 사람이 무엇을 봤는가" 의 목록이 되어 경로가 평문으로
 * 쌓인다. 키는 `sha256(크기 + 파일명 + 수정시각)` 이다 — 폴더를 옮겨도 살아남고
 * 경로를 담지 않는다.
 *
 * [volumeRelativePath] 는 표시와 재탐색용 보조 정보이고 사용자가 지울 수 있다.
 */
@Entity(tableName = "playback_position")
data class PlaybackPositionEntity(
    @PrimaryKey @ColumnInfo(name = "file_key") val fileKey: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "volume_relative_path") val volumeRelativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 만화 읽던 쪽. 재생 위치와 성격이 같아 규칙도 같다. */
@Entity(tableName = "comic_progress")
data class ComicProgressEntity(
    @PrimaryKey @ColumnInfo(name = "file_key") val fileKey: String,
    @ColumnInfo(name = "page") val page: Int,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    /** 0 = 왼쪽에서 오른쪽, 1 = 오른쪽에서 왼쪽, 2 = 세로 스크롤. */
    @ColumnInfo(name = "read_direction") val readDirection: Int,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * 문서에서 읽던 자리. **11~13단계의 모든 문서 포맷이 이 표 하나를 쓴다.**
 *
 * ## 왜 `comic_progress` 를 다시 쓰지 않는가
 *
 * 그 표에는 `read_direction` 이 있다. 문서에는 읽는 방향이 없으므로(쪽 차례를 문서가
 * 정한다) 뜻이 없는 열을 공유하게 되고, 그러면 어느 화면이 그 열의 주인인지 모르게 된다.
 *
 * ## 왜 포맷마다 표를 나누지 않는가
 *
 * 나누면 마이그레이션이 포맷 수만큼 늘고, **두 사람이 각자 `version = 3` 을 만들면 합칠
 * 수 없다.** 표 하나·마이그레이션 하나·스키마 json 하나로 못 박는다.
 *
 * ## 왜 쪽 번호가 nullable 인가
 *
 * 12·13단계의 흐름 렌더 포맷(docx·HWP)에는 **쪽이 없다.** 지금 `NOT NULL` 로 세우면
 * 그때 표를 통째로 다시 만들어야 한다. 쪽이 있는 포맷(PDF)은 [page]·[pageCount] 를,
 * 흐름 포맷은 [locator]·[progress] 를 쓴다 — 서로의 칸을 비워 둔다.
 */
@Entity(tableName = "doc_progress")
data class DocProgressEntity(
    /** `FileKey.of(이름, 크기, 수정시각)`. **경로를 담지 않는다** — 무엇을 읽었는지가 새지 않게. */
    @PrimaryKey @ColumnInfo(name = "file_key") val fileKey: String,
    /** 쪽이 있는 포맷의 읽던 쪽(0부터). 흐름 포맷은 null. */
    @ColumnInfo(name = "page") val page: Int? = null,
    /**
     * 그때의 총 쪽 수. **되살린 값을 믿을지 판단하는 데 쓴다** — 파일이 바뀌어 쪽 수가
     * 달라졌으면 저장된 번호가 다른 곳을 가리킨다.
     */
    @ColumnInfo(name = "page_count") val pageCount: Int? = null,
    /**
     * 흐름 포맷의 읽던 자리. 포맷이 뜻을 정한다(EPUB 은 스파인 자리, 13단계는 문단 번호).
     * **여기에 경로나 원문을 담지 않는다.**
     */
    @ColumnInfo(name = "locator") val locator: String? = null,
    /** 문서 안에서 얼마나 왔는가(0~1). 화면이 막대를 그리는 데 쓴다. */
    @ColumnInfo(name = "progress") val progress: Double? = null,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * 휴지통 항목의 **정본**.
 *
 * 원래 경로를 여기(앱 전용 디렉터리)에 두고, 볼륨에 남기는 사이드카에는 넣지 않는다.
 * 볼륨의 `.iroiro-trash/` 는 모든 파일 접근 권한을 가진 어떤 앱이든 읽을 수 있어서,
 * 거기에 경로를 평문으로 쌓으면 '무엇을 언제 지웠는가' 가 통째로 새어 나간다.
 */
@Entity(
    tableName = "trash_entry",
    indices = [Index("deleted_at"), Index("volume_id")],
)
data class TrashEntryEntity(
    @PrimaryKey @ColumnInfo(name = "uuid") val uuid: String,
    /** 어느 볼륨의 휴지통인가. 복원은 같은 볼륨 안에서만 일어난다. */
    @ColumnInfo(name = "volume_id") val volumeId: String,
    @ColumnInfo(name = "original_parent") val originalParent: String,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)

/** 폴더마다 기억하는 보기 설정. 정렬을 폴더별로 기억하는 것이 파일 관리자의 관행이다. */
@Entity(tableName = "folder_pref")
data class FolderPrefEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    /** 0 = 이름, 1 = 수정시각, 2 = 크기, 3 = 종류. */
    @ColumnInfo(name = "sort_key") val sortKey: Int,
    @ColumnInfo(name = "ascending") val ascending: Boolean,
    /** 0 = 목록, 1 = 격자. */
    @ColumnInfo(name = "view_mode") val viewMode: Int,
)

/** 최근에 연 위치. 첫 화면의 바로가기로 쓴다. */
@Entity(tableName = "recent_location", indices = [Index("visited_at")])
data class RecentLocationEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
)

/**
 * 즐겨찾기한 폴더.
 *
 * 경로를 그대로 키로 쓴다. 이어보기 위치와 달리 이것은 **사용자가 직접 만들고 직접
 * 지우는 목록**이라, 경로가 보이는 것이 기능이다(어디를 즐겨찾기했는지 보여야 한다).
 * 폴더를 옮기면 끊기는데, 그때는 목록에서 '없음' 으로 보이고 지울 수 있으면 된다.
 */
@Entity(tableName = "bookmark", indices = [Index("added_at")])
data class BookmarkEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    /** 사용자가 바꾼 이름. 비어 있으면 폴더 이름을 쓴다. */
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
