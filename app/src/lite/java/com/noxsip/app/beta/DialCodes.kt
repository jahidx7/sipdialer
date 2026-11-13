package com.noxsip.app.beta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.noxsip.app.telecom.TelecomHelper

/**
 * Beta: ডায়ালপ্যাডের বিশেষ কোড। সাধারণ ফোন-ডায়ালার এগুলো নিজে সামলায়, ডিফল্ট ডায়ালার হলে আমাদেরই সামলাতে হয়।
 *  - *#06#            : IMEI দেখানো (ফোন অ্যাপকে IMEI পড়তে না দিলে ফোনের "About phone" খোলে)
 *  - *#*#1234#*#*     : ইঞ্জিনিয়ারিং/সিক্রেট কোড, Telecom-এর sendDialerSpecialCode দিয়ে (ডিফল্ট ডায়ালার লাগে)
 *  - *21#, **21*নম্বর#, *123# ইত্যাদি : MMI/USSD, Telecom দিয়ে SIM-এ পাঠানো (ফলাফল ফোনের সিস্টেম দেখায়)
 * ফোনভেদে কিছু কোড কাজ নাও করতে পারে (নির্মাতার নিজস্ব সিক্রেট কোড অন্য ডায়ালারে বন্ধ রাখে)।
 */
object DialCodes {

    private const val TAG = "DialCodes"
    private val secretRegex = Regex("""^\*#\*#(.+)#\*#\*$""")
    private val shortSecretRegex = Regex("""^\*#(\d{3,})#$""")

    /** * বা # দিয়ে শুরু ও # দিয়ে শেষ হলে কোড, কলের নম্বর নয় */
    fun isCode(n: String): Boolean {
        val s = n.trim()
        if (secretRegex.matches(s)) return true // *#*#1234#*#* শেষে * দিয়ে
        return s.length >= 3 && (s.startsWith("*") || s.startsWith("#")) && s.endsWith("#")
    }

    /** ফোনের নিজের ডায়ালারের মতো: শেষ # টাইপ করতেই চলে (কল বোতাম বা SIM বাছাই লাগে না) */
    fun isAutoCode(n: String): Boolean {
        val s = n.trim()
        return s == "*#06#" || secretRegex.matches(s) || shortSecretRegex.matches(s)
    }

    /** কোড সামলালে true; [show] দিয়ে ব্যবহারকারীকে বার্তা দেখানো হয় */
    fun handle(ctx: Context, input: String, simHandle: PhoneAccountHandle?, show: (String) -> Unit): Boolean {
        val s = input.trim()
        if (!isCode(s)) return false
        Log.i(TAG, "code $s")
        when {
            s == "*#06#" -> showImei(ctx, show)
            secretRegex.matches(s) -> sendSecret(ctx, secretRegex.find(s)!!.groupValues[1], show)
            shortSecretRegex.matches(s) -> sendSecret(ctx, shortSecretRegex.find(s)!!.groupValues[1], show)
            else -> TelecomHelper.placeSimCallWithHandle(ctx, s, simHandle)
        }
        return true
    }

    private fun showImei(ctx: Context, show: (String) -> Unit) {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val lines = mutableListOf<String>()
        try {
            val slots = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) maxOf(1, tm?.activeModemCount ?: 1) else 2
            for (slot in 0 until slots) {
                val imei = tm?.getImei(slot)
                if (!imei.isNullOrBlank()) lines += "IMEI ${slot + 1}: $imei"
            }
        } catch (e: Exception) {
            Log.w(TAG, "IMEI not readable", e)
        }
        if (lines.isNotEmpty()) {
            show(lines.joinToString("\n"))
        } else {
            // Android 10+ সাধারণ অ্যাপকে IMEI পড়তে দেয় না (পরীক্ষা: ফোনের নিজের ডায়ালার ডিফল্ট না হলে কোড চালায় না,
            // আর নেটওয়ার্কে *#06# পাঠালে "Service not available" আসে)। তাই ফোনের নিজের তথ্য পেজ খুলি
            // শেষ উপায়: ফোনের নিজের তথ্য পেজ
            show("Android does not let apps read the IMEI. Opening About phone: scroll down to IMEI (or open Status information).")
            try {
                ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    private fun sendSecret(ctx: Context, code: String, show: (String) -> Unit) {
        var sent = false
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.sendDialerSpecialCode(code) // ডিফল্ট ডায়ালার না হলে SecurityException
                sent = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendDialerSpecialCode failed", e)
        }
        show(
            if (sent) "Code $code sent. If this phone supports it, the tool opens now; if nothing happens, the maker has locked this code to its own dialer."
            else "Could not send code $code. Make Nox SIP the default phone app first (Settings > Default Dialer)."
        )
    }
}
