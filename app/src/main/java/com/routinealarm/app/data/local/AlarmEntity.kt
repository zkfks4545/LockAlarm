package com.routinealarm.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val triggerAtMillis: Long,
    val enabled: Boolean,
    val repeatType: String,
    val localTimeMinutes: Int,
    val oneTimeDateEpochDay: Long?,
    val weekdaysCsv: String,
    val includeDatesCsv: String,
    val excludeDatesCsv: String,
    val brightnessPercent: Int,
    val mediaVolumePercent: Int,
    val dismissDelaySeconds: Int,
    @ColumnInfo(defaultValue = "1") val dismissTimerEnabled: Boolean,
    val restorePolicy: String,
    val contentMode: String,
    val visualUri: String?,
    val visualKind: String,
    @ColumnInfo(defaultValue = "'DEFAULT_ALARM'") val soundSource: String,
    val audioUri: String?,
    val youtubeUrl: String?,
    @ColumnInfo(defaultValue = "1") val scheduleRevision: Long,
    @ColumnInfo(defaultValue = "1") val homePreviewEnabled: Boolean,
    val updatedAtMillis: Long,
)
