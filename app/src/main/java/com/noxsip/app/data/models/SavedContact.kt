package com.noxsip.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * অ্যাপের নিজস্ব কন্টাক্ট (Client / Office); ফোনবুকে যায় না।
 */
@Entity(tableName = "saved_contacts")
data class SavedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = CATEGORY_CLIENT, // "client" | "office"
    val name: String,
    val number: String,
    val company: String = "",
    val email: String = "",
    val address: String = "",
    val birthday: String = "", // DD/MM/YYYY
    val notes: String = "",
    val hasWhatsapp: Boolean = false,
    val hasTelegram: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_CLIENT = "client"
        const val CATEGORY_OFFICE = "office"
    }
}
