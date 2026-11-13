package com.noxsip.app.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ব্যাকআপ পাসওয়ার্ড ফিঙ্গারপ্রিন্টের সাথে বেঁধে রাখে।
 * পাসওয়ার্ড Android Keystore-এর এমন একটি কী দিয়ে এনক্রিপ্ট হয় যা ব্যবহারের জন্য প্রতিবার
 * আঙুলের ছাপ লাগে। নতুন আঙুল যোগ হলে কী বাতিল হয়ে যায় (তখন পাসওয়ার্ড আবার দিতে হবে)।
 * এই পাসওয়ার্ড অটো-ব্যাকআপের পাসওয়ার্ড থেকে আলাদা।
 */
object BackupPassVault {
    private const val KEY_ALIAS = "nox_backup_pass_biometric"
    private const val PREFS = "backup_pass_vault"
    private const val K_IV = "iv"
    private const val K_CT = "ct"

    fun findActivity(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /** ফোনে শক্তিশালী বায়োমেট্রিক (ফিঙ্গারপ্রিন্ট) সেট করা আছে কি */
    fun available(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun hasSaved(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(K_CT)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun createKey(): SecretKey {
        val b = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(b.build())
        return kg.generateKey()
    }

    private fun getKey(): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFail: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val bp = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c == null) onFail() else onSuccess(c)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Backup password")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        bp.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** পাসওয়ার্ড সেভ করে (আঙুলের ছাপ নিয়ে)। সফল হলে onDone(true) */
    fun save(activity: FragmentActivity, passphrase: String, onDone: (Boolean) -> Unit) {
        try {
            clear(activity)
            createKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            prompt(activity, "Save password with fingerprint", cipher, { c ->
                try {
                    val ct = c.doFinal(passphrase.toByteArray(Charsets.UTF_8))
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(K_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                        .putString(K_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
                        .apply()
                    onDone(true)
                } catch (_: Exception) {
                    clear(activity); onDone(false)
                }
            }, {
                clear(activity); onDone(false)
            })
        } catch (_: Exception) {
            clear(activity); onDone(false)
        }
    }

    /** আঙুলের ছাপ নিয়ে সেভ করা পাসওয়ার্ড ফেরত দেয়; ব্যর্থ বা বাতিল হলে null */
    fun unlock(activity: FragmentActivity, onResult: (String?) -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(K_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val ct = prefs.getString(K_CT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (iv == null || ct == null) {
            onResult(null); return
        }
        try {
            val key = getKey() ?: run { clear(activity); onResult(null); return }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            prompt(activity, "Unlock backup password", cipher, { c ->
                try {
                    onResult(String(c.doFinal(ct), Charsets.UTF_8))
                } catch (_: Exception) {
                    onResult(null)
                }
            }, { onResult(null) })
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear(activity); onResult(null) // নতুন আঙুল যোগ হয়েছে: পাসওয়ার্ড আবার দিতে হবে
        } catch (_: Exception) {
            onResult(null)
        }
    }
}
