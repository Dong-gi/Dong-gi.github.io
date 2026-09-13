package io.github.donggi.iroiroviewer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackPositionDao {

    @Query("SELECT * FROM playback_position WHERE file_key = :fileKey")
    suspend fun find(fileKey: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_position WHERE file_key = :fileKey")
    suspend fun remove(fileKey: String)

    /** '기록 지우기'. 사용자가 언제든 전부 지울 수 있어야 한다. */
    @Query("DELETE FROM playback_position")
    suspend fun clear()
}

@Dao
interface ComicProgressDao {

    @Query("SELECT * FROM comic_progress WHERE file_key = :fileKey")
    suspend fun find(fileKey: String): ComicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ComicProgressEntity)

    @Query("DELETE FROM comic_progress")
    suspend fun clear()
}

@Dao
interface TrashDao {

    /** 최근에 지운 것이 위로. 휴지통 화면이 그대로 쓴다. */
    @Query("SELECT * FROM trash_entry ORDER BY deleted_at DESC")
    fun observeAll(): Flow<List<TrashEntryEntity>>

    @Query("SELECT * FROM trash_entry WHERE uuid = :uuid")
    suspend fun find(uuid: String): TrashEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrashEntryEntity)

    @Delete
    suspend fun delete(entity: TrashEntryEntity)

    /** 보존 기간이 지난 것. 자동 비우기가 이것으로 고른다. */
    @Query("SELECT * FROM trash_entry WHERE deleted_at < :before")
    suspend fun olderThan(before: Long): List<TrashEntryEntity>

    @Query("SELECT COUNT(*) FROM trash_entry")
    suspend fun count(): Int

    /** 한 볼륨의 기록 전부. 정합성 검사가 파일 목록과 맞대 본다. */
    @Query("SELECT * FROM trash_entry WHERE volume_id = :volumeId")
    suspend fun inVolume(volumeId: String): List<TrashEntryEntity>
}

@Dao
interface FolderPrefDao {

    @Query("SELECT * FROM folder_pref WHERE path = :path")
    suspend fun find(path: String): FolderPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FolderPrefEntity)
}

@Dao
interface RecentLocationDao {

    @Query("SELECT * FROM recent_location ORDER BY visited_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun visit(entity: RecentLocationEntity)

    @Query("DELETE FROM recent_location")
    suspend fun clear()
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmark ORDER BY added_at ASC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE path = :path")
    suspend fun remove(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE path = :path)")
    suspend fun has(path: String): Boolean
}
