package com.noxsip.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * পারমিশন হেল্পার
 */
object PermissionHelper {

    /**
     * প্রয়োজনীয় সব পারমিশন
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.MANAGE_OWN_CALLS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // ব্লুটুথ হেডফোন/TWS-এ কল অডিও পাঠাতে
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.toTypedArray()
    }

    /**
     * সব পারমিশন আছে কিনা চেক করুন
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * মিসিং পারমিশন লিস্ট
     */
    fun getMissingPermissions(context: Context): Array<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
