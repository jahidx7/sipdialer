package com.noxsip.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * কল হিস্ট্রি রেকর্ড
 */
@Entity(tableName = "call_history")
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteAddress: String,
    val displayName: String = "",
    val isIncoming: Boolean,
    val isAnswered: Boolean = true,
    val duration: Int = 0, // সেকেন্ডে
    val timestamp: Long = System.currentTimeMillis(),
    val recordingPath: String = "",
    val accountUser: String = "", // কল যে SIP অ্যাকাউন্টের ইউজারনেম দিয়ে হয়েছে
    val codec: String = "",
    val qualityRating: Float = -1f,
    val jitterMs: Int = 0,
    val lossPercent: Float = 0f,
    val rttMs: Int = 0
)
