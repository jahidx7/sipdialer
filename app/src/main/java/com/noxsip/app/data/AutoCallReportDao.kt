package com.noxsip.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.noxsip.app.data.models.AutoCallReportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoCallReportDao {
    @Query("SELECT * FROM auto_call_reports ORDER BY id DESC")
    fun observeAll(): Flow<List<AutoCallReportRecord>>

    @Query("SELECT * FROM auto_call_reports ORDER BY id DESC")
    suspend fun getAll(): List<AutoCallReportRecord>

    @Query("SELECT * FROM auto_call_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AutoCallReportRecord?

    @Query("SELECT * FROM auto_call_reports WHERE status = 'READY' ORDER BY id ASC")
    suspend fun getQueued(): List<AutoCallReportRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AutoCallReportRecord>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AutoCallReportRecord): Long

    @Update
    suspend fun update(record: AutoCallReportRecord)

    @Query("UPDATE auto_call_reports SET status = 'FAILED', detail = 'App closed before this call finished', endedBy = 'App' WHERE status IN ('CALLING', 'PLAYING')")
    suspend fun markInterrupted()

    @Query("DELETE FROM auto_call_reports")
    suspend fun clearAll()
}
