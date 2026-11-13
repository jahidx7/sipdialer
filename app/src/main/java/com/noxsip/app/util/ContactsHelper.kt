package com.noxsip.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val id: String,
    val name: String,
    val number: String,
    val normalizedDigits: String
)

object ContactsHelper {

    /** WhatsApp/Telegram লিঙ্কের জন্য দেশের কোডসহ ডিজিট (বাংলাদেশি 01XXXXXXXXX → 8801XXXXXXXXX) */
    fun chatDigits(number: String): String {
        val trimmed = number.trim()
        var d = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("+")) return d
        if (d.startsWith("00")) return d.removePrefix("00")
        if (d.startsWith("0") && d.length == 11) d = "88$d"
        return d
    }

    /**
     * ফোনবুকে নম্বরটি সেভ থাকলে ওই কন্টাক্টে WhatsApp/Telegram এন্ট্রি আছে কি না দেখে।
     * (এই অ্যাপগুলো ফোনবুকের কন্টাক্টে নিজেদের ডেটা-এন্ট্রি জুড়ে দেয়।) ফেরত: (whatsapp, telegram)
     */
    suspend fun detectMessengers(context: Context, number: String): Pair<Boolean, Boolean> =
        withContext(Dispatchers.IO) {
            if (!hasContactsPermission(context) || number.filter { it.isDigit() }.length < 6) {
                return@withContext false to false
            }
            var wa = false
            var tg = false
            try {
                val lookup = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(number)
                )
                val ids = mutableListOf<String>()
                context.contentResolver.query(
                    lookup, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
                )?.use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
                for (id in ids) {
                    context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.MIMETYPE),
                        "${ContactsContract.Data.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val mt = c.getString(0).orEmpty().lowercase()
                            if (mt.contains("whatsapp")) wa = true
                            if (mt.contains("telegram")) tg = true
                        }
                    }
                }
            } catch (_: Exception) {
            }
            wa to tg
        }

    /** নম্বর (শেষ ১০ ডিজিট) মিলিয়ে সংরক্ষিত কন্টাক্টের নাম; না পেলে null */
    fun nameFor(address: String, contacts: List<DeviceContact>): String? {
        val digits = address.filter { it.isDigit() }
        if (digits.length < 6) return null
        val tail = digits.takeLast(10)
        return contacts.firstOrNull { it.normalizedDigits.filter { c -> c.isDigit() }.endsWith(tail) }?.name
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission(context)) return@withContext emptyList()

        val contacts = mutableListOf<DeviceContact>()
        val seenKeys = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    if (name.isEmpty() || rawNumber.isEmpty()) continue

                    val digits = rawNumber.filter { it.isDigit() || it == '+' }
                    if (digits.isEmpty()) continue

                    val dedupKey = "${name.lowercase()}|${digits.takeLast(10)}"
                    if (seenKeys.add(dedupKey)) {
                        contacts.add(
                            DeviceContact(
                                id = id,
                                name = name,
                                number = rawNumber,
                                normalizedDigits = digits
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        contacts
    }
}
