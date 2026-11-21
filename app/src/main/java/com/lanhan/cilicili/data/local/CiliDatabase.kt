package com.lanhan.cilicili.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SiteRuleEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CiliDatabase : RoomDatabase() {
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE site_rules ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'HTML'",
                )
            }
        }

        fun build(context: Context): CiliDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CiliDatabase::class.java,
                "cilicili.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
