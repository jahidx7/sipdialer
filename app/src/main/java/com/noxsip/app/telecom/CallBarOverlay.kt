package com.noxsip.app.telecom

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.noxsip.app.R
import com.noxsip.app.sip.SipCallState
import com.noxsip.app.sip.SipEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * কল চলাকালীন (বা রিং হওয়া অবস্থায়) ইউজার কল স্ক্রিন ছেড়ে গেলে (হোমে/অন্য অ্যাপে/অ্যাপের অন্য পেজে)
 * স্ক্রিনের উপরে ছোট পপ-আপ বার:
 *  - কল চলছে: "Connected • টাইমার" + কেটে দেওয়ার বাটন
 *  - ইনকামিং (রিসিভ হয়নি): রিসিভ + কেটে দেওয়ার বাটন
 *  - আউটগোয়িং (রিসিভ হয়নি): কেটে দেওয়ার বাটন
 * বারে ট্যাপ করলে কল স্ক্রিনে ফেরে। "অন্য অ্যাপের উপরে দেখানো" (overlay) অনুমতি লাগে।
 */
object CallBarOverlay {
    /** MainActivity দৃশ্যমান কি না (onResume/onPause) */
    val appForeground = MutableStateFlow(false)

    /** অ্যাপের ভেতরে এখন কল স্ক্রিন খোলা কি না */
    val onCallRoute = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var attached = false
    private var bar: View? = null
    private var barKey: String? = null
    private var timerJob: Job? = null
    private var statusView: TextView? = null

    fun attach(context: Context, engine: SipEngine) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        scope.launch {
            combine(engine.callState, appForeground, onCallRoute) { s, fg, route -> Triple(s, fg, route) }
                .collect { (s, fg, route) ->
                    try {
                        render(app, engine, s, fg, route)
                    } catch (e: Exception) {
                        android.util.Log.w("CallBarOverlay", "render failed", e)
                    }
                }
        }
    }

    private fun render(app: Context, engine: SipEngine, s: SipCallState, fg: Boolean, route: Boolean) {
        val kind = when (s) {
            is SipCallState.Incoming -> "in"
            is SipCallState.Outgoing -> "out"
            is SipCallState.Connected, is SipCallState.OnHold -> "active"
            else -> null
        }
        val address = when (s) {
            is SipCallState.Incoming -> s.remoteAddress
            is SipCallState.Outgoing -> s.remoteAddress
            is SipCallState.Connected -> s.remoteAddress
            is SipCallState.OnHold -> s.remoteAddress
            else -> ""
        }
        val callScreenVisible = fg && route
        val hide = kind == null ||
            callScreenVisible ||
            !NativeDialerOverlay.canShowOverlay(app) ||
            (kind == "in" && NativeDialerOverlay.isIncomingOverlayShowing())
        if (hide) {
            dismiss()
            return
        }
        val key = "$kind|$address"
        if (bar == null || barKey != key) {
            dismiss()
            show(app, engine, kind!!, address)
            barKey = key
        }
        updateStatus(engine)
    }

    private fun statusText(engine: SipEngine): Pair<String, Int> {
        return when (engine.callState.value) {
            is SipCallState.Incoming -> "Incoming call" to Color.parseColor("#93C5FD")
            is SipCallState.Outgoing -> "Calling…" to Color.parseColor("#93C5FD")
            is SipCallState.OnHold -> "On hold" to Color.parseColor("#FBBF24")
            is SipCallState.Connected -> {
                val sec = engine.getCallDuration()
                "Connected • %02d:%02d".format(sec / 60, sec % 60) to Color.parseColor("#22C55E")
            }
            else -> "" to Color.WHITE
        }
    }

    private fun updateStatus(engine: SipEngine) {
        val (text, color) = statusText(engine)
        statusView?.text = text
        statusView?.setTextColor(color)
    }

    private fun show(app: Context, engine: SipEngine, kind: String, address: String) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val clean = address.removePrefix("sip:").substringBefore("@").ifEmpty { address }

        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14253E"))
                cornerRadius = dp(app, 14).toFloat()
                setStroke(dp(app, 1), Color.parseColor("#2C4A73"))
            }
            setPadding(dp(app, 16), dp(app, 8), dp(app, 8), dp(app, 8))
            elevation = dp(app, 6).toFloat()
        }
        val textCol = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        val nameView = TextView(app).apply {
            text = clean
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val status = TextView(app).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
        }
        statusView = status
        textCol.addView(nameView)
        textCol.addView(status)
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (kind == "in") {
            row.addView(circleButton(app, R.drawable.ic_phone, "#16A34A") { engine.answerCall() })
        }
        row.addView(circleButton(app, R.drawable.ic_call_end, "#DC2626") {
            if (kind == "in") engine.declineCall() else engine.endCall()
        })

        // বারে ট্যাপ করলে কল স্ক্রিনে ফেরা
        row.setOnClickListener {
            try {
                IncomingUiRequest.request()
                app.startActivity(
                    Intent(app, com.noxsip.app.MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                )
            } catch (_: Exception) {
            }
        }

        val root = FrameLayout(app).apply {
            setPadding(dp(app, 10), 0, dp(app, 10), 0)
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        wm.addView(root, params(app))
        bar = root

        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                updateStatus(engine)
                delay(1000)
            }
        }
    }

    private fun circleButton(app: Context, icon: Int, colorHex: String, onClick: () -> Unit): View {
        val size = dp(app, 40)
        return FrameLayout(app).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(app, 10).toFloat()
                setColor(Color.parseColor(colorHex))
            }
            addView(
                ImageView(app).apply {
                    setImageResource(icon)
                    setColorFilter(Color.WHITE)
                },
                FrameLayout.LayoutParams(dp(app, 22), dp(app, 22), Gravity.CENTER)
            )
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(app, 8) }
        }
    }

    private fun params(app: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val id = app.resources.getIdentifier("status_bar_height", "dimen", "android")
            y = (if (id > 0) app.resources.getDimensionPixelSize(id) else dp(app, 24)) + dp(app, 6)
        }
    }

    fun dismiss() {
        timerJob?.cancel()
        timerJob = null
        statusView = null
        barKey = null
        val v = bar ?: return
        bar = null
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
