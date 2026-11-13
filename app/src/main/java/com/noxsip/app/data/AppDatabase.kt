package com.noxsip.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.noxsip.app.data.models.CallRecord

/**
 * অ্যাপ ডেটাবেস
 */
@Database(
    entities = [CallRecord::class, com.noxsip.app.data.models.SavedContact::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun savedContactDao(): SavedContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_history ADD COLUMN recordingPath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`address` TEXT NOT NULL, `body` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`type` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, `subId` INTEGER NOT NULL, " +
                        "`sysId` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN simSlot INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN simNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_history ADD COLUMN accountUser TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_contacts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`category` TEXT NOT NULL, `name` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                        "`company` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, " +
                        "`birthday` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                        "`hasWhatsapp` INTEGER NOT NULL, `hasTelegram` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN dateSent INTEGER NOT NULL DEFAULT 0")
            }
        }

        // SMS ফিচার সরানো হয়েছে: পুরনো sms_messages টেবিল থাকলে মুছে ফেলা
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sms_messages")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sip_dialer_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
