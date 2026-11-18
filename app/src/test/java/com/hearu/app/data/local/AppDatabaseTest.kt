package com.hearu.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hearu.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppDatabaseTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: AppDatabase
    private val openedDbs = mutableListOf<AppDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
        openedDbs.forEach { it.close() }
        openedDbs.clear()
    }

    @Test
    fun `converters round trip int lists and handle null blank and malformed input`() {
        val converters = Converters()
        assertEquals("", converters.fromIntList(emptyList()))
        assertEquals("", converters.fromIntList(null))
        assertEquals("15,42,88", converters.fromIntList(listOf(15, 42, 88)))

        assertEquals(emptyList<Int>(), converters.toIntList(null))
        assertEquals(emptyList<Int>(), converters.toIntList(""))
        assertEquals(emptyList<Int>(), converters.toIntList("   "))
        assertEquals(listOf(15, 42, 88), converters.toIntList("15,42,88"))
        assertEquals(listOf(10, 30), converters.toIntList("10, bad, 30"))
    }

    @Test
    fun `dao persists voice metadata syncStatus and purges older messages accurately`() = runTest {
        val dao = inMemoryDb.messageDao()
        val msgOld = MessageEntity(
            id = "m_old",
            sessionId = "s1",
            senderId = "u1",
            text = "Old voice note",
            timestamp = 1000L,
            isVoiceNote = true,
            voiceDurationSeconds = 15,
            messageType = "voice",
            audioUrl = "/cache/old.m4a",
            audioDurationSec = 15,
            waveformAmplitudes = listOf(20, 45, 70, 35),
            syncStatus = MessageEntity.SYNC_PENDING
        )
        val msgBoundary = MessageEntity(
            id = "m_boundary",
            sessionId = "s1",
            senderId = "u2",
            text = "Boundary msg",
            timestamp = 2000L,
            syncStatus = MessageEntity.SYNC_SENT
        )
        val msgOtherSession = MessageEntity(
            id = "m_other",
            sessionId = "s2",
            senderId = "u3",
            text = "Other session",
            timestamp = 3000L
        )

        dao.insertMessages(listOf(msgOld, msgBoundary, msgOtherSession))
        dao.updateSyncStatus("m_old", MessageEntity.SYNC_FAILED)

        val session1Before = dao.getMessagesForSession("s1").first()
        assertEquals(2, session1Before.size)
        assertEquals(MessageEntity.SYNC_FAILED, session1Before[0].syncStatus)
        assertEquals("voice", session1Before[0].messageType)
        assertEquals("/cache/old.m4a", session1Before[0].audioUrl)
        assertEquals(15, session1Before[0].audioDurationSec)
        assertEquals(listOf(20, 45, 70, 35), session1Before[0].waveformAmplitudes)

        // Cutoff == 2000L deletes timestamp < 2000L, keeping == 2000L and > 2000L
        dao.deleteMessagesOlderThan(2000L)
        val session1After = dao.getMessagesForSession("s1").first()
        assertEquals(listOf("m_boundary"), session1After.map { it.id })
        assertEquals(1, dao.getMessagesForSession("s2").first().size)

        dao.clearAll()
        assertTrue(dao.getMessagesForSession("s1").first().isEmpty())
        assertTrue(dao.getMessagesForSession("s2").first().isEmpty())
    }

    @Test
    fun `migration from v1 without indices to v3 preserves data and validates schema`() = runTest {
        val dbName = "migration_v1_noindex_${System.nanoTime()}.db"
        createLegacyDatabase(dbName, version = 1, includeV1Indices = false) { db ->
            db.execSQL(
                "INSERT INTO cached_messages (id, sessionId, senderId, text, timestamp, isSystemMessage) " +
                    "VALUES ('v1_msg', 'sess_1', 'user_1', 'Legacy v1 hello', 12345, 0)"
            )
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
            .also { openedDbs.add(it) }

        val rows = migrated.messageDao().getMessagesForSession("sess_1").first()
        assertEquals(1, rows.size)
        assertEquals("Legacy v1 hello", rows[0].text)
        assertFalse(rows[0].isVoiceNote)
        assertEquals("text", rows[0].messageType)
        assertNull(rows[0].audioUrl)
        assertEquals(emptyList<Int>(), rows[0].waveformAmplitudes)
        assertEquals(MessageEntity.SYNC_SENT, rows[0].syncStatus)

        assertCompositeIndexOnly(migrated.openHelper.readableDatabase)
    }

    @Test
    fun `migration from v2 with redundant sessionId index to v3 preserves voice columns and drops redundant index`() = runTest {
        val dbName = "migration_v2_${System.nanoTime()}.db"
        createLegacyDatabase(dbName, version = 2, includeV1Indices = true) { db ->
            db.execSQL(
                "INSERT INTO cached_messages (id, sessionId, senderId, text, timestamp, isSystemMessage, isVoiceNote, voiceDurationSeconds) " +
                    "VALUES ('v2_msg', 'sess_2', 'user_2', 'Voice v2', 67890, 0, 1, 14)"
            )
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
            .also { openedDbs.add(it) }

        val rows = migrated.messageDao().getMessagesForSession("sess_2").first()
        assertEquals(1, rows.size)
        assertTrue(rows[0].isVoiceNote)
        assertEquals(14, rows[0].voiceDurationSeconds)
        assertEquals("text", rows[0].messageType)
        assertEquals(MessageEntity.SYNC_SENT, rows[0].syncStatus)

        assertCompositeIndexOnly(migrated.openHelper.readableDatabase)
    }

    private fun createLegacyDatabase(
        dbName: String,
        version: Int,
        includeV1Indices: Boolean,
        seed: (SupportSQLiteDatabase) -> Unit
    ) {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (version == 1) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cached_messages` (" +
                            "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `senderId` TEXT NOT NULL, " +
                            "`text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isSystemMessage` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                } else {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cached_messages` (" +
                            "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `senderId` TEXT NOT NULL, " +
                            "`text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isSystemMessage` INTEGER NOT NULL, " +
                            "`isVoiceNote` INTEGER NOT NULL, `voiceDurationSeconds` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                }
                if (includeV1Indices) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_messages_sessionId_timestamp` ON `cached_messages` (`sessionId`, `timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_messages_sessionId` ON `cached_messages` (`sessionId`)")
                }
                seed(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close()
        helper.close()
    }

    private fun assertCompositeIndexOnly(db: SupportSQLiteDatabase) {
        val indexNames = mutableListOf<String>()
        db.query("PRAGMA index_list(cached_messages)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) {
                    indexNames.add(cursor.getString(nameIdx))
                }
            }
        }
        assertTrue(indexNames.contains("index_cached_messages_sessionId_timestamp"))
        assertFalse(indexNames.contains("index_cached_messages_sessionId"))
    }
}
