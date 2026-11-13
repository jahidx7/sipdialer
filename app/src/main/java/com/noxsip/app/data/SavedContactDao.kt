package com.noxsip.app.data

import androidx.room.*
import com.noxsip.app.data.models.SavedContact
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedContactDao {

    @Query("SELECT * FROM saved_contacts ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<SavedContact>>

    @Query("SELECT * FROM saved_contacts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<SavedContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: SavedContact): Long

    @Delete
    suspend fun delete(contact: SavedContact)

    @Query("SELECT COUNT(*) FROM saved_contacts WHERE category = :category AND number = :number")
    suspend fun countSame(category: String, number: String): Int
}
