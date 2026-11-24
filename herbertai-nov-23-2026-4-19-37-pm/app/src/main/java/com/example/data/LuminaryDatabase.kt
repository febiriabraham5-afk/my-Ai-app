package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, CreationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LuminaryDatabase : RoomDatabase() {
    abstract fun luminaryDao(): LuminaryDao

    companion object {
        @Volatile
        private var INSTANCE: LuminaryDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `creations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `content` TEXT NOT NULL DEFAULT '',
                        `filePath` TEXT,
                        `metadata` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creations_type` ON `creations` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creations_createdAt` ON `creations` (`createdAt`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `thoughts` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `thinkingSeconds` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): LuminaryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LuminaryDatabase::class.java,
                    "luminary_qna.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
