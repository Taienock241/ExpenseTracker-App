package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Whether this device can use fingerprint sign-in. Uses only platform APIs (Android 9+). */
object BiometricSupport {
    enum class Availability { AVAILABLE, NOT_ENROLLED, UNSUPPORTED }

    @Suppress("DEPRECATION")
    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Availability.UNSUPPORTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return Availability.UNSUPPORTED
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            } else {
                manager.canAuthenticate()
            }
            return when (result) {
                BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
                else -> Availability.UNSUPPORTED
            }
        }
        // Android 9 cannot report enrolment; a missing fingerprint is caught when the key is created.
        return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            Availability.AVAILABLE
        } else {
            Availability.UNSUPPORTED
        }
    }

    fun isAvailable(context: Context): Boolean = availability(context) == Availability.AVAILABLE
}

/** Shows the system fingerprint prompt, unlocking [cipher] on success. */
object BiometricAuth {
    private const val ERROR_CANCELED = 5
    private const val ERROR_USER_CANCELED = 10

    /**
     * [onFailure] receives null when the user simply cancelled, or a message worth showing
     * (too many attempts, hardware unavailable, ...).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(negativeText, executor) { _, _ -> onFailure(null) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }
        builder.build().authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked != null) onSuccess(unlocked) else onFailure("Fingerprint could not be verified.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode == ERROR_CANCELED || errorCode == ERROR_USER_CANCELED) {
                        onFailure(null)
                    } else {
                        onFailure(errString?.toString())
                    }
                }
            },
        )
    }
}

/**
 * Keeps the email and password for fingerprint sign-in encrypted with an AES key that lives in the
 * Android Keystore and can only be used after a fingerprint scan. The key is invalidated when the
 * device's fingerprints change, which makes the stored data unreadable (the user then sets it up again).
 */
class BiometricCredentialStore(context: Context) {
    data class Credentials(val email: String, val password: String)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.contains(KEY_DATA) && prefs.contains(KEY_IV)

    /** Cipher that will encrypt the credentials once the user scans a fingerprint. Replaces any old key. */
    fun createEnrollmentCipher(): Cipher {
        deleteKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
    }

    /** Call with the cipher returned from a successful fingerprint scan. */
    fun save(unlockedCipher: Cipher, email: String, password: String): Boolean = try {
        val encrypted = unlockedCipher.doFinal("$email\n$password".toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(unlockedCipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
        true
    } catch (e: Exception) {
        clear()
        false
    }

    /** Cipher that will decrypt the credentials after a fingerprint scan, or null if it must be set up again. */
    fun createDecryptCipher(): Cipher? {
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            val key = loadKey() ?: run { clear(); return null }
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (e: Exception) {
            // Includes KeyPermanentlyInvalidatedException: fingerprints changed, so the key is unusable.
            clear()
            null
        }
    }

    fun decrypt(unlockedCipher: Cipher): Credentials? = try {
        val data = prefs.getString(KEY_DATA, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val plain = data?.let { String(unlockedCipher.doFinal(it), Charsets.UTF_8) }
        plain?.let { Credentials(it.substringBefore('\n'), it.substringAfter('\n')) }
    } catch (e: Exception) {
        null
    }

    fun clear() {
        prefs.edit().clear().apply()
        deleteKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun loadKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
        }
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "biometric_login"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pesatrack_fingerprint_login_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
