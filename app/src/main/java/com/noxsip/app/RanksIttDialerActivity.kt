package com.noxsip.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout

class RanksIttDialerActivity : Activity() {
    private lateinit var dialer: WebView
    private var pendingMicrophone: PermissionRequest? = null

    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var previousMode: Int? = null
    private var previousSpeaker = false
    private lateinit var speaker: Button

    @Suppress("DEPRECATION")
    private fun toggleSpeaker() {
        if (previousMode != null) {
            restoreAudio()
            return
        }
        previousMode = audio.mode
        previousSpeaker = audio.isSpeakerphoneOn
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val device = audio.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (device == null || !audio.setCommunicationDevice(device)) {
                restoreAudio()
                return
            }
        } else audio.isSpeakerphoneOn = true
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        speaker.text = "Speaker on"
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        val mode = previousMode ?: return
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
        else audio.isSpeakerphoneOn = previousSpeaker
        audio.mode = mode
        previousMode = null
        volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        speaker.text = "Speaker"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        dialer = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.textZoom = (settings.textZoom * 0.9f).toInt().coerceAtLeast(1)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !isPortal(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (!isPortal(request.origin) ||
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources ||
                            pendingMicrophone != null || isFinishing || isDestroyed
                        ) {
                            request.deny()
                        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingMicrophone = request
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
                        }
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    runOnUiThread {
                        if (pendingMicrophone === request) pendingMicrophone = null
                    }
                }
            }
        }

        val close = Button(this).apply {
            text = "Back to NoxSIP"
            setOnClickListener { returnToNoxSip() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            val toolbar = LinearLayout(this@RanksIttDialerActivity)
            toolbar.addView(close, LinearLayout.LayoutParams(0, -2, 1f))
            speaker = Button(this@RanksIttDialerActivity).apply {
                text = "Speaker"
                setOnClickListener { toggleSpeaker() }
            }
            toolbar.addView(speaker)
            addView(toolbar)
            addView(dialer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        })
        if (savedInstanceState == null || dialer.restoreState(savedInstanceState) == null) {
            dialer.loadUrl(PORTAL_URL)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_REQUEST) return
        val request = pendingMicrophone ?: return
        pendingMicrophone = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && isPortal(request.origin)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
            request.deny()
        }
    }

    // Keep the portal's live page and session when returning to the main task.
    @Deprecated("Handled here because Back parks the web dialer task.")
    override fun onBackPressed() {
        returnToNoxSip()
    }

    private fun returnToNoxSip() {
        CookieManager.getInstance().flush()
        restoreAudio()
        moveTaskToBack(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        dialer.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        pendingMicrophone?.deny()
        pendingMicrophone = null
        (dialer.parent as? LinearLayout)?.removeView(dialer)
        restoreAudio()
        dialer.destroy()
        super.onDestroy()
    }

    private fun isPortal(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host.equals("sip.ranksitt.net", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443)

    private companion object {
        const val PORTAL_URL = "https://sip.ranksitt.net/vup/login"
        const val MICROPHONE_REQUEST = 1
    }
}
