package com.noxsip.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable result for one Auto Call attempt. */
@Entity(tableName = "auto_call_reports")
data class AutoCallReportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val number: String,
    val status: String = "READY",
    val detail: String = "Queued",
    val answered: Boolean = false,
    val callDurationSeconds: Int = 0,
    val voicePlayedMillis: Long = 0,
    val sipCode: Int = 0,
    val attempt: Int = 1,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val startedAtMillis: Long = 0,
    val finishedAtMillis: Long = 0,
    val endedBy: String = "",
    val terminationReason: String = ""
)
