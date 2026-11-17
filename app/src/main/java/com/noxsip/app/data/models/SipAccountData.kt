package com.noxsip.app.data.models

/**
 * SIP অ্যাকাউন্ট ডেটা মডেল
 * id: অভ্যন্তরীণ ইউনিক আইডি (খালি হলে সেভ করার সময় নতুন তৈরি হয়)
 * name: ব্যবহারকারীর দেওয়া অ্যাকাউন্টের নাম (বাধ্যতামূলক)
 */
data class SipAccountData(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 5060,
    val transport: String = "UDP",
    val proxy: String = "",
    val displayName: String = "",
    val autoStart: Boolean = false,
    val enabled: Boolean = true, // false = স্থায়ীভাবে বন্ধ (কানেক্ট হয় না)
    val backupServer: String = "",
    val backupPort: Int = 5060,
    val backupTransport: String = "UDP",
    /** Preferred SIP signaling transport while Android is using mobile data. */
    val cellularTransport: String = "TCP"
)
