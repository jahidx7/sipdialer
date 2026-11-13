package com.noxsip.app.sip

/**
 * চলমান কলের লাইভ কোয়ালিটি তথ্য (Linphone CallStats থেকে)
 */
data class CallQualityStats(
    val codec: String = "",
    val rttMs: Int = 0,
    val jitterMs: Int = 0,
    val lossPercent: Float = 0f,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    /** 0.0 - 5.0 (Linphone call quality rating), নেগেটিভ মানে এখনো অজানা */
    val rating: Float = -1f,
    /** মাইক্রোফোন থেকে ধরা ভলিউম (dBm0); কথা বললেও প্রায় -90 বা কম থাকলে মাইক সাইলেন্ট */
    val micDb: Float = -120f,
    /** ইনপুট অডিও ডিভাইসের নাম (খালি হলে কোনো ক্যাপচার ডিভাইস নির্বাচিত নেই) */
    val inputDevice: String = "",
    val micEnabled: Boolean = true
)
