package com.routinealarm.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_contents")
data class RecentContentEntity(
    @PrimaryKey val key: String,
    val type: String,
    val reference: String,
    val displayName: String?,
    val visualKind: String?,
    val usedAtMillis: Long,
)
