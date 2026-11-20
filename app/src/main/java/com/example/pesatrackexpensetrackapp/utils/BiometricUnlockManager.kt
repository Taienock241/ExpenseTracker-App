package com.example.pesatrackexpensetrackapp.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.pesatrackexpensetrackapp.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Biometric app-unlock gate. It never stores or reuses a Firebase password. */
object BiometricUnlockManager {
    private const val PREFERENCES = "pesatrack_biometric_unlock"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_PREFIX = "pesatrack_biometric_"

    fun isAvailable(context: Context): Boolean = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG,
    ) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context, userId: String): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(preferenceKey(userId), false)

    fun setEnabled(context: Context, userId: String, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(preferenceKey(userId), enabled).apply()
        if (!enabled) deleteKey(userId)
    }

    fun authenticate(
        activity: FragmentActivity,
        userId: String,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isAvailable(activity)) {
            onError("Set up a fingerprint or other strong biometric on this device first.")
            return
        }

        val cipher = try {
            createCipher(userId)
        } catch (_: KeyPermanentlyInvalidatedException) {
            setEnabled(activity, userId, false)
            onError("Fingerprint setup changed. Sign in with your password and enable fingerprint unlock again.")
            return
        } catch (exception: Exception) {
            android.util.Log.e("BiometricUnlock", "Could not prepare biometric unlock", exception)
            onError("Fingerprint unlock is unavailable. Sign in with your password.")
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (result.cryptoObject?.cipher == null) {
                        onError("Could not verify the biometric. Please sign in with your password.")
                    } else {
                        onSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError("$errString. You can sign in with your password instead.")
                    }
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.action_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun createCipher(userId: String): Cipher {
        val alias = keyAlias(userId)
        val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
        val key = store.getKey(alias, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    private fun deleteKey(userId: String) {
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
                deleteEntry(keyAlias(userId))
            }
        }
    }

    private fun preferenceKey(userId: String) = "enabled_${userId.filter(Char::isLetterOrDigit)}"
    private fun keyAlias(userId: String) = KEY_PREFIX + userId.filter(Char::isLetterOrDigit)
}
