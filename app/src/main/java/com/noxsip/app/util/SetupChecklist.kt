package com.noxsip.app.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noxsip.app.CHANNEL_CALL_ID

/**
 * ইনকামিং কল নির্ভরযোগ্যভাবে (লক-স্ক্রিন/স্ক্রিন-অফ অবস্থায়) পেতে যেসব ফোন-সেটিং লাগে তার চেকলিস্ট।
 * ok = true (ঠিক আছে), false (ঠিক করতে হবে), null (অ্যাপ থেকে যাচাই করা যায় না, ম্যানুয়ালি দেখতে হবে)
 */
data class SetupItem(
    val title: String,
    val hint: String,
    val ok: Boolean?,
    val fix: () -> Unit
)

object SetupChecklist {

    private fun open(context: Context, vararg intents: Intent) {
        val all = intents.toList() + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        for (intent in all) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")

    /** ফোন নির্মাতার অটো-স্টার্ট পেজ; যেটা এই ফোনে খোলে সেটাই, না হলে অ্যাপ-ইনফো */
    private fun oemAutoStartIntents(): Array<Intent> {
        fun c(pkg: String, cls: String) = Intent().setClassName(pkg, cls)
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> arrayOf(
                c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            "vivo", "iqoo" -> arrayOf(
                c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            "oppo", "realme", "oneplus" -> arrayOf(
                c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            "huawei", "honor" -> arrayOf(
                c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            "samsung" -> arrayOf(
                c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            else -> emptyArray()
        }
    }

    fun evaluate(context: Context): List<SetupItem> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val items = mutableListOf<SetupItem>()

        // 1. মাইক্রোফোন
        items += SetupItem(
            title = "Microphone permission",
            hint = "Needed so the other side can hear you",
            ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            fix = { open(context) }
        )

        // 2. নোটিফিকেশন (সব + ইনকামিং কল চ্যানেল)
        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelOn = try {
            val ch = nm.getNotificationChannel(CHANNEL_CALL_ID)
            ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
        } catch (_: Exception) {
            true
        }
        items += SetupItem(
            title = "Notifications",
            hint = "Allow all, including 'Incoming Calls' and lock screen",
            ok = notificationsOn && channelOn,
            fix = {
                open(
                    context,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
        )

        // 3. ব্যাটারি অপটিমাইজেশন
        items += SetupItem(
            title = "Battery: allow background running",
            hint = "Without it calls do not arrive when the screen is off",
            ok = BatteryOptimization.isIgnoring(context),
            fix = { BatteryOptimization.request(context) }
        )

        // 3b. নির্মাতার অটো-স্টার্ট / ব্যাকগ্রাউন্ড চালানো (vivo, Xiaomi, Oppo, Huawei, Samsung); অ্যাপ থেকে যাচাই করা যায় না
        items += SetupItem(
            title = "Auto-start / background launch",
            hint = "Turn ON for this app, otherwise the phone may stop it and calls will not arrive",
            ok = null,
            fix = { open(context, *oemAutoStartIntents()) }
        )

        // 4. অন্য অ্যাপের ওপরে দেখানো (ইনকামিং কল পপআপ)
        items += SetupItem(
            title = "Display over other apps",
            hint = "Shows the incoming-call popup",
            ok = Settings.canDrawOverlays(context),
            fix = {
                open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = pkgUri(context) })
            }
        )

        // 5. ফুল-স্ক্রিন ইনটেন্ট (Android 14+): লক-স্ক্রিনে কল স্ক্রিন জ্বালাতে
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            items += SetupItem(
                title = "Full-screen notifications",
                hint = "Lets incoming calls light up the lock screen",
                ok = nm.canUseFullScreenIntent(),
                fix = {
                    open(
                        context,
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data = pkgUri(context) }
                    )
                }
            )
        }

        return items
    }
}
