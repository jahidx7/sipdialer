package com.noxsip.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ব্লক করা নম্বরের তালিকা (SharedPreferences)। মেলানো হয় নম্বরের শেষ ১০ ডিজিট দিয়ে,
 * তাই +8801711..., 01711... একই নম্বর ধরা হয়।
 * ব্লক করা নম্বরের কল রিজেক্ট হয় (মিসড কল হিসেবে তালিকায় থাকে)।
 */
object BlockList {
    private const val PREFS = "block_list"
    private const val KEY = "numbers"

    private val _numbers = MutableStateFlow<Set<String>>(emptySet())
    val numbers: StateFlow<Set<String>> = _numbers
    @Volatile private var loaded = false

    private fun key(raw: String): String {
        val digits = raw.removePrefix("sip:").substringBefore("@").filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val set = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet()).orEmpty().toSet()
            _numbers.value = set
            loaded = true
        }
    }

    /** UI-র জন্য: প্রবাহটি চালু করে সংরক্ষিত তালিকা লোড করে */
    fun init(context: Context) = load(context)

    fun isBlocked(context: Context, raw: String): Boolean {
        load(context)
        val k = key(raw)
        return k.isNotEmpty() && k in _numbers.value
    }

    fun isBlocked(raw: String, set: Set<String>): Boolean {
        val k = key(raw)
        return k.isNotEmpty() && k in set
    }

    fun add(context: Context, raw: String) {
        load(context)
        val k = key(raw)
        if (k.isEmpty()) return
        save(context, _numbers.value + k)
    }

    fun remove(context: Context, raw: String) {
        load(context)
        save(context, _numbers.value - key(raw))
    }

    private fun save(context: Context, set: Set<String>) {
        _numbers.value = set
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, set).apply()
    }
}
