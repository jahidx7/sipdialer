package com.noxsip.app.telecom

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.noxsip.app.sip.SipCallState
import com.noxsip.app.sip.SipEngine

/**
 * Android Telecom ফ্রেমওয়ার্ক ConnectionService
 * নেটিভ ডায়ালারের সাথে SIP কল ইন্টিগ্রেট করে
 */
class SipConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "SipConnectionService"
    }

    /**
     * আউটগোয়িং কল - ফোনের মূল ডায়ালার বা অ্যাপ থেকে কল করলে এটি কল হয়
     */
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val rawUri = request?.address
        val targetNumber = Uri.decode(rawUri?.schemeSpecificPart ?: "").trim()
        Log.d(TAG, "onCreateOutgoingConnection: uri=$rawUri, target=$targetNumber")

        val displayNumber = targetNumber.removePrefix("sip:").substringBefore("@").ifEmpty { targetNumber }

        val connection = SipConnection(applicationContext).apply {
            setAddress(
                rawUri ?: Uri.fromParts("tel", displayNumber, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setCallerDisplayName(
                displayNumber.ifEmpty { "Unknown" },
                TelecomManager.PRESENTATION_ALLOWED
            )
            connectionCapabilities = Connection.CAPABILITY_MUTE or
                    Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD
            audioModeIsVoip = true
            setDialing()
        }

        val sipEngine = SipEngine.getInstance(applicationContext)

        // যদি ইতোমধ্যে কল শুরু না হয়ে থাকে তবে SIP কল শুরু করুন
        val alreadyCalling = sipEngine.callState.value is SipCallState.Outgoing ||
                sipEngine.callState.value is SipCallState.Connected

        if (alreadyCalling || sipEngine.makeCall(targetNumber)) {
            Log.i(TAG, "Outgoing SIP call active/initiated via native dialer: $targetNumber")
        } else {
            connection.setDisconnected(
                DisconnectCause(
                    DisconnectCause.ERROR,
                    "SIP call failed. Please make sure your SIP account is registered."
                )
            )
            connection.destroy()
            Log.e(TAG, "Failed to initiate SIP call: $targetNumber")
        }

        return connection
    }

    /**
     * ইনকামিং কল - SIP ইনকামিং কল পেলে এটি কল হয়
     */
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection")

        val extras = request?.extras
        val callerAddress = extras?.getString("caller_address")
            ?: request?.address?.schemeSpecificPart
            ?: "Unknown"
        val cleanCaller = callerAddress
            .removePrefix("sip:")
            .substringBefore("@")
            .ifEmpty { callerAddress }

        val connection = SipConnection(applicationContext).apply {
            setAddress(
                Uri.fromParts("sip", cleanCaller, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setCallerDisplayName(cleanCaller, TelecomManager.PRESENTATION_ALLOWED)
            connectionCapabilities = Connection.CAPABILITY_MUTE or
                    Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD
            audioModeIsVoip = true
            setRinging()
        }

        Log.i(TAG, "Incoming SIP call presented in native dialer from: $callerAddress")
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateOutgoingConnectionFailed: ${request?.address}")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed")
    }
}
