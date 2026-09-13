package io.github.donggi.iroiroviewer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.execSQL

/**
 * 앱의 영속 상태. **앱 전용 디렉터리에만 있다.**
 *
 * 스키마를 `schemas/` 에 내보내 커밋한다. 마이그레이션을 쓰려면 이전 버전의 모양이
 * 저장소에 있어야 하고, 그것이 없으면 나중에 손으로 추측하게 된다.
 *
 * 파괴적 마이그레이션(fallbackToDestructiveMigration)을 켜지 않는다. 휴지통 표가
 * 날아가면 볼륨에는 UUID 이름의 파일만 남아 사용자가 무엇이 무엇인지 알 수 없게 된다.
 * 스키마를 바꿀 때는 마이그레이션을 쓴다.
 */
@Database(
    entities = [
        PlaybackPositionEntity::class,
        ComicProgressEntity::class,
        TrashEntryEntity::class,
        FolderPrefEntity::class,
        RecentLocationEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class IroiroDatabase : RoomDatabase() {

    abstract fun playbackPositions(): PlaybackPositionDao
    abstract fun comicProgress(): ComicProgressDao
    abstract fun trash(): TrashDao
    abstract fun folderPrefs(): FolderPrefDao
    abstract fun recentLocations(): RecentLocationDao
    abstract fun bookmarks(): BookmarkDao

    companion object {
        private const val NAME = "iroiro.db"

        @Volatile
        private var instance: IroiroDatabase? = null

        /**
         * 2 — 즐겨찾기 표를 더한다.
         *
         * 파괴적 마이그레이션을 켜지 않았으므로 표를 더할 때마다 여기에 한 줄이 는다.
         * 그 번거로움이 휴지통 표를 지키는 값이다.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmark` (" +
                        "`path` TEXT NOT NULL, `label` TEXT NOT NULL, `added_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`path`))"
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmark_added_at` ON `bookmark` (`added_at`)")
            }
        }

        fun get(context: Context): IroiroDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IroiroDatabase::class.java,
                    NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
