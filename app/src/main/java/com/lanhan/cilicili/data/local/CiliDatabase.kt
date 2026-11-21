package com.lanhan.cilicili.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SiteRuleEntity::class, SearchHistoryEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CiliDatabase : RoomDatabase() {
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE site_rules ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'HTML'",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `dedupKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `magnetUri` TEXT,
                        `infoHash` TEXT,
                        `sizeText` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `dateText` TEXT NOT NULL,
                        `seeders` INTEGER,
                        `peers` INTEGER,
                        `primarySiteId` INTEGER NOT NULL,
                        `primarySiteName` TEXT NOT NULL,
                        `detailUrl` TEXT,
                        `sourceNames` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dedupKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): CiliDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CiliDatabase::class.java,
                "cilicili.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
