package com.noxsip.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.core.app.NotificationCompat
import com.noxsip.app.CHANNEL_SERVICE_ID
import com.noxsip.app.MainActivity
import com.noxsip.app.R
import com.noxsip.app.data.AccountRepository
import com.noxsip.app.sip.SipEngine
import com.noxsip.app.ptt.PttLanEngine
import com.noxsip.app.sip.SipRegistrationState
import com.noxsip.app.telecom.TelecomHelper
import com.noxsip.app.util.CallNetworkLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SIP ফোরগ্রাউন্ড সার্ভিস - ব্যাকগ্রাউন্ডে SIP রেজিস্ট্রেশন ধরে রাখে
 */
class SipForegroundService : Service() {

    companion object {
        private const val TAG = "SipForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val NO_DEFAULT_NETWORK_GRACE_MS = 1_200L

        const val ACTION_START = "com.noxsip.app.action.START"
        const val ACTION_STOP = "com.noxsip.app.action.STOP"
        @Volatile
        private var isRunning = false

        const val ACTION_POWER_REFRESH = "com.noxsip.app.action.POWER_REFRESH"

        fun isActive(): Boolean = isRunning

        /** ব্যাটারি মোড বদলালে চলমান সার্ভিসে নতুন মোড প্রয়োগ */
        fun refreshPower(context: Context) {
            try {
                if (isRunning) {
                    context.startService(Intent(context, SipForegroundService::class.java).apply { action = ACTION_POWER_REFRESH })
                } else {
                    start(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshPower failed: ${e.message}")
            }
        }

        const val ACTION_CALL_ACTIVE ="com.noxsip.app.action.CALL_ACTIVE"
        const val ACTION_CALL_ENDED = "com.noxsip.app.action.CALL_ENDED"

        /**
         * কল শুরু/রিসিভের সময় সার্ভিসকে phoneCall|microphone টাইপে আপগ্রেড করে।
         * Android 11+ এ ব্যাকগ্রাউন্ড অ্যাপের মাইক্রোফোন ক্যাপচার সাইলেন্ট করে দেওয়া হয়
         * (অপরপ্রান্ত আপনার কথা শোনে না), যদি না microphone-টাইপ ফোরগ্রাউন্ড সার্ভিস চালু থাকে।
         */
        fun setCallActive(context: Context, active: Boolean) {
            // সার্ভিস বন্ধ থাকলে (অ্যাকাউন্ট আনরেজিস্টার্ড) কল শেষে নতুন করে চালু করে নোটিফিকেশন আটকে রাখব না
            if (!isRunning) return
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = if (active) ACTION_CALL_ACTIVE else ACTION_CALL_ENDED
                }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "setCallActive($active) failed: ${e.message}")
            }
        }

        fun start(context: Context) {
            KeepAliveState.setStoppedByUser(context, false)
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = ACTION_START
                }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SipForegroundService", e)
            }
        }

        fun stop(context: Context) {
            KeepAliveState.setStoppedByUser(context, true)
            try {
                val intent = Intent(context, SipForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SipForegroundService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sipEngine: SipEngine
    private val pttLanEngine by lazy { PttLanEngine.getInstance(applicationContext) }
    private var cpuWakeLock: PowerManager.WakeLock? = null

    /**
     * স্ক্রিন বন্ধ হলে CPU ঘুমিয়ে গেলে SIP টাইমার (REGISTER রিফ্রেশ, keep-alive) চলে না, সার্ভারে রেজিস্ট্রেশন
     * মেয়াদোত্তীর্ণ হয় আর ইনকামিং কল আসে না। তাই সার্ভিস চলাকালীন CPU ও Wi-Fi জাগিয়ে রাখা হয়।
     */
    private fun acquireStandbyLocks() {
        if (com.noxsip.app.util.PowerPrefs.mode(this) == com.noxsip.app.util.PowerPrefs.RELIABLE) {
            acquireCpuLock()
            // রিলায়েবল মোডেও প্রহরী অ্যালার্ম: সার্ভিস মরে গেলে সে-ই আবার চালু করে
            KeepAliveScheduler.schedule(this)
        } else {
            KeepAliveScheduler.schedule(this)
        }
        CallNetworkLock.acquireStandby(applicationContext)
    }

    private fun acquireCpuLock() {
        try {
            if (cpuWakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipDialer:Registration").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.i(TAG, "CPU wake lock acquired for SIP registration")
            }
        } catch (e: Exception) {
            Log.w(TAG, "CPU wake lock failed: ${e.message}")
        }
    }

    private fun releaseCpuLock() {
        try {
            cpuWakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        cpuWakeLock = null
    }

    private fun releaseStandbyLocks() {
        releaseCpuLock()
        KeepAliveScheduler.cancel(this)
        CallNetworkLock.releaseStandby()
    }

    override fun onCreate() {
        super.onCreate()
        com.noxsip.app.util.Diagnosis.record("Service", "Started")
        isRunning = true
        sipEngine = SipEngine.getInstance(applicationContext)

        // ইনকামিং কল হলে নেটিভ ডায়ালারে দেখান
        sipEngine.onIncomingCall = { callerAddress ->
            TelecomHelper.reportIncomingCall(applicationContext, callerAddress)
        }

        registerNetworkWatch()
        Log.i(TAG, "SipForegroundService created")
    }

    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private var lastIsCellular: Boolean? = null
    private var networkLost = false
    private var routeChangeAwaitingCapabilities = false
    private val networkHandler = Handler(Looper.getMainLooper())
    private var noNetworkCheck: Runnable? = null

    /** Wi-Fi ↔ মোবাইল ডেটা বদলালে বা নেটওয়ার্ক ফিরলে দ্রুত রি-রেজিস্টার */
    private fun registerNetworkWatch() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Android reports the already-active default network when a callback is first
                    // registered. That initial notification is not a handover and must not reset SIP.
                    val changed = lastNetwork != null && lastNetwork != network
                    val restored = networkLost
                    noNetworkCheck?.let(networkHandler::removeCallbacks)
                    noNetworkCheck = null
                    if (changed || restored) {
                        routeChangeAwaitingCapabilities = true
                        Log.i(TAG, "Default network changed/restored; waiting for its transport before SIP recovery")
                    }
                    lastNetwork = network
                    networkLost = false
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: android.net.NetworkCapabilities
                ) {
                    // Android calls this after onAvailable(). Learn the initial network type without
                    // treating startup as a handover; recover only after a real route/type change.
                    if (network == lastNetwork &&
                        networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ) {
                        val isCellular = networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        val previousType = lastIsCellular
                        val routeChanged = routeChangeAwaitingCapabilities
                        lastIsCellular = isCellular
                        routeChangeAwaitingCapabilities = false
                        if (routeChanged || (previousType != null && previousType != isCellular)) {
                            Log.i(TAG, "Default network transport changed: ${if (isCellular) "mobile data" else "Wi-Fi/other"}")
                            sipEngine.onNetworkChanged(isCellular)
                            pttLanEngine.onNetworkChanged()
                        } else if (previousType == null) {
                            sipEngine.onInitialNetwork(isCellular)
                            pttLanEngine.onNetworkChanged()
                        } else {
                            Log.d(TAG, "Current default network has INTERNET capability: $network")
                        }
                    }
                }

                override fun onLost(network: Network) {
                    if (lastNetwork == network) {
                        networkLost = true
                        lastNetwork = null
                        Log.i(TAG, "Default route lost; allowing a short handover window before pausing SIP")
                        val check = Runnable {
                            noNetworkCheck = null
                            if (lastNetwork != null || !networkLost) return@Runnable

                            // Some Android builds deliver onLost during a handover even though
                            // the replacement route is already active. Recheck outside the
                            // callback to avoid blocking Android's connectivity callback thread.
                            val current = cm.activeNetwork
                            val capabilities = current?.let(cm::getNetworkCapabilities)
                            if (current != null && capabilities?.hasCapability(
                                    android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
                                ) == true
                            ) {
                                lastNetwork = current
                                networkLost = false
                                lastIsCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                                routeChangeAwaitingCapabilities = false
                                Log.i(TAG, "Found active default route after handover; restarting SIP")
                                sipEngine.onNetworkChanged(lastIsCellular)
                            } else {
                                lastIsCellular = null
                                routeChangeAwaitingCapabilities = false
                                Log.i(TAG, "No replacement internet route; pausing SIP until connectivity returns")
                                sipEngine.onNetworkUnavailable()
                            }
                        }
                        noNetworkCheck = check
                        networkHandler.postDelayed(check, NO_DEFAULT_NETWORK_GRACE_MS)
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb, networkHandler)
            netCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "network watch failed: ${e.message}")
        }
    }

    private fun unregisterNetworkWatch() {
        noNetworkCheck?.let(networkHandler::removeCallbacks)
        noNetworkCheck = null
        try {
            netCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        netCallback = null
    }

    /** রিসেন্টস থেকে সোয়াইপ: কিছু ফোন সার্ভিস মেরে দেয়, তাই অল্প পরে ফেরানোর অ্যালার্ম */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!KeepAliveState.stoppedByUser(this)) {
            Log.i(TAG, "Task removed, scheduling revive")
            KeepAliveScheduler.schedule(this, 2_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                KeepAliveState.setStoppedByUser(this, false)
                startForegroundSafely()
                acquireStandbyLocks()
                pttLanEngine.setBackgroundListening(true)
                registerSipAccount()
            }
            ACTION_CALL_ACTIVE -> {
                startForegroundSafely(callActive = true)
                // কল চলাকালীন CPU জাগিয়ে রাখি (Balanced মোডেও)
                acquireCpuLock()
            }
            ACTION_CALL_ENDED -> {
                startForegroundSafely(callActive = false)
                if (com.noxsip.app.util.PowerPrefs.mode(this) != com.noxsip.app.util.PowerPrefs.RELIABLE) releaseCpuLock()
            }
            ACTION_POWER_REFRESH -> {
                releaseCpuLock()
                KeepAliveScheduler.cancel(this)
                acquireStandbyLocks()
            }
            ACTION_STOP -> {
                KeepAliveState.setStoppedByUser(this, true)
                pttLanEngine.setBackgroundListening(false)
                releaseStandbyLocks()
                sipEngine.unregister()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely(callActive: Boolean = false) {
        val notification = createNotification()
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (callActive && !micGranted) {
            Log.e(TAG, "RECORD_AUDIO not granted: the remote party will NOT hear you. Grant Microphone permission.")
        }
        try {
            if (callActive && micGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, types)
                    Log.i(TAG, "Foreground service promoted for call (microphone|phoneCall)")
                    return
                } catch (e: Exception) {
                    // ব্যাকগ্রাউন্ড থেকে microphone টাইপ শুরু করা নিষিদ্ধ হলে সাধারণ মোডে ফিরে যাই
                    Log.w(TAG, "Call-mode foreground promotion refused: ${e.message}")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback startForeground due to exception", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not startForeground", e2)
            }
        }
    }

    private fun registerSipAccount() {
        serviceScope.launch {
            try {
                TelecomHelper.registerPhoneAccount(applicationContext)
                // শুধুমাত্র যদি এখনো রেজিস্ট্রেশন শুরু না হয়ে থাকে (যেমন বুট বা ব্যাকগ্রাউন্ড রিস্টার্ট) তখনই রেজিস্টার করুন
                if (sipEngine.registrationState.value == SipRegistrationState.UNREGISTERED) {
                    val repository = AccountRepository(applicationContext)
                    val account = repository.getAccount().first()
                    if (account != null) {
                        sipEngine.registerAllSaved()
                        Log.i(TAG, "SIP accounts registered from service")
                    } else {
                        Log.w(TAG, "No SIP account configured")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SIP account", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_phone)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        com.noxsip.app.util.Diagnosis.record("Service", "Stopped")
        pttLanEngine.setBackgroundListening(false)
        unregisterNetworkWatch()
        releaseStandbyLocks()
        isRunning = false
        serviceScope.cancel()
        Log.i(TAG, "SipForegroundService destroyed")
        super.onDestroy()
    }
}
