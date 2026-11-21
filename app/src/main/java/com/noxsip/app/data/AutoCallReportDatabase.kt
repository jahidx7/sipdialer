package com.noxsip.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.noxsip.app.data.models.AutoCallReportRecord

@Database(entities = [AutoCallReportRecord::class], version = 1, exportSchema = false)
abstract class AutoCallReportDatabase : RoomDatabase() {
    abstract fun reports(): AutoCallReportDao

    companion object {
        @Volatile private var instance: AutoCallReportDatabase? = null

        fun getInstance(context: Context): AutoCallReportDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AutoCallReportDatabase::class.java,
                "auto_call_reports.db"
            ).build().also { instance = it }
        }
    }
}
