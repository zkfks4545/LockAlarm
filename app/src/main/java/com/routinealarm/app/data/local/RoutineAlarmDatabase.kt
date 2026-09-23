package com.routinealarm.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, AlarmOccurrenceEntity::class, RecentContentEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class RoutineAlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: RoutineAlarmDatabase? = null

        fun get(context: Context): RoutineAlarmDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RoutineAlarmDatabase::class.java,
                "routine-alarm.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN scheduleRevision INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alarm_occurrences (
                        occurrenceId TEXT NOT NULL PRIMARY KEY,
                        alarmId INTEGER NOT NULL,
                        scheduleRevision INTEGER NOT NULL,
                        sessionId TEXT NOT NULL,
                        scheduledAtMillis INTEGER NOT NULL,
                        claimedAtMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        FOREIGN KEY(alarmId) REFERENCES alarms(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alarm_occurrences_alarmId ON alarm_occurrences(alarmId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alarm_occurrences_status ON alarm_occurrences(status)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN soundSource TEXT NOT NULL DEFAULT 'DEFAULT_ALARM'",
                )
                database.execSQL("ALTER TABLE alarms ADD COLUMN audioUri TEXT")
                database.execSQL(
                    "UPDATE alarms SET soundSource = 'VISUAL_MEDIA' " +
                        "WHERE visualKind = 'VIDEO' AND visualUri IS NOT NULL",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS alarm_events")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recent_contents (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `type` TEXT NOT NULL,
                        `reference` TEXT NOT NULL,
                        `displayName` TEXT,
                        `visualKind` TEXT,
                        `usedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO recent_contents
                        (`key`, `type`, `reference`, `displayName`, `visualKind`, `usedAtMillis`)
                    SELECT 'VISUAL|' || visualUri, 'VISUAL_FILE', visualUri, NULL, visualKind, updatedAtMillis
                    FROM alarms
                    WHERE contentMode = 'LOCAL' AND visualUri IS NOT NULL AND visualUri != ''
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO recent_contents
                        (`key`, `type`, `reference`, `displayName`, `visualKind`, `usedAtMillis`)
                    SELECT 'AUDIO|' || audioUri, 'AUDIO_FILE', audioUri, NULL, NULL, updatedAtMillis
                    FROM alarms
                    WHERE contentMode = 'LOCAL' AND audioUri IS NOT NULL AND audioUri != ''
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO recent_contents
                        (`key`, `type`, `reference`, `displayName`, `visualKind`, `usedAtMillis`)
                    SELECT 'YOUTUBE|' || youtubeUrl, 'YOUTUBE', youtubeUrl, NULL, NULL, updatedAtMillis
                    FROM alarms
                    WHERE contentMode = 'YOUTUBE' AND youtubeUrl IS NOT NULL AND youtubeUrl != ''
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN homePreviewEnabled INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN dismissTimerEnabled INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "UPDATE alarms SET dismissTimerEnabled = " +
                        "CASE WHEN dismissDelaySeconds > 0 THEN 1 ELSE 0 END",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN oneTimeDateUserSelected INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
