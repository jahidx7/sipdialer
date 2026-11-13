package com.noxsip.app.data

import androidx.room.*
import com.noxsip.app.data.models.CallRecord
import kotlinx.coroutines.flow.Flow

/**
 * কল হিস্ট্রি DAO
 */
@Dao
interface CallHistoryDao {

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAllCallRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallRecords(limit: Int = 50): Flow<List<CallRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Delete
    suspend fun deleteCallRecord(record: CallRecord)

    @Query("SELECT * FROM call_history ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<CallRecord>

    @Query("SELECT COUNT(*) FROM call_history WHERE timestamp = :timestamp AND remoteAddress = :address")
    suspend fun countSame(timestamp: Long, address: String): Int

    @Query("DELETE FROM call_history")
    suspend fun deleteAllCallRecords()
}
