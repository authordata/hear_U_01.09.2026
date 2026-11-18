package com.hearu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.data.local.entity.MessageEntity

@Database(entities = [MessageEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN isVoiceNote INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN voiceDurationSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_sessionId_timestamp ON cached_messages(sessionId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_sessionId ON cached_messages(sessionId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN audioUrl TEXT")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN audioDurationSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN waveformAmplitudes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_sessionId_timestamp ON cached_messages(sessionId, timestamp)")
                db.execSQL("DROP INDEX IF EXISTS index_cached_messages_sessionId")
            }
        }
    }
}
