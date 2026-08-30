package com.flowerwhisp.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DictationEntity::class,
        DictionaryEntryEntity::class,
        SnippetEntity::class,
        TransformProfileEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FlowerWhispDatabase : RoomDatabase() {
    abstract fun dictationDao(): DictationDao

    abstract fun dictionaryEntryDao(): DictionaryEntryDao

    abstract fun snippetDao(): SnippetDao

    abstract fun transformProfileDao(): TransformProfileDao

    companion object {
        const val DATABASE_NAME = "flowerwhisp_v2.db"

        fun create(context: Context): FlowerWhispDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlowerWhispDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dictations ADD COLUMN safeText TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE dictations ADD COLUMN cleanupStatus TEXT NOT NULL DEFAULT 'DISABLED'")
                database.execSQL("ALTER TABLE dictations ADD COLUMN cleanupError TEXT")
                database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN scope TEXT NOT NULL DEFAULT 'ALL'")
                database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE snippets ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transform_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        builtIn INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
