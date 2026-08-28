package com.flowerwhisp.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DictationEntity::class, DictionaryEntryEntity::class, SnippetEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlowerWhispDatabase : RoomDatabase() {
    abstract fun dictationDao(): DictationDao

    abstract fun dictionaryEntryDao(): DictionaryEntryDao

    abstract fun snippetDao(): SnippetDao

    companion object {
        const val DATABASE_NAME = "flowerwhisp_v2.db"

        fun create(context: Context): FlowerWhispDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlowerWhispDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
