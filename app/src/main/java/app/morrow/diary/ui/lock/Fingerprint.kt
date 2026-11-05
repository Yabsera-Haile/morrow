package app.morrow.diary.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.morrow.diary.security.LockManager
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

sealed interface FingerprintOutcome {
    data class Success(val cipher: Cipher) : FingerprintOutcome
    data object Cancelled : FingerprintOutcome
    data class Error(val message: String) : FingerprintOutcome
}

/** True when the phone has a strong fingerprint sensor with at least one fingerprint saved. */
fun canUseFingerprint(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** Asks for a fingerprint and, on a match, stores a fingerprint-locked copy of the diary key. */
suspend fun FragmentActivity.enrollFingerprint(lock: LockManager<*>, title: String, subtitle: String, negativeButton: String): Boolean {
    val cipher = lock.fingerprintEnrollCipher()
    return when (val outcome = askForFingerprint(lock, cipher, title, subtitle, negativeButton)) {
        is FingerprintOutcome.Success -> {
            lock.enableFingerprint(outcome.cipher)
            true
        }
        else -> false
    }
}

/**
 * Shows the system fingerprint sheet bound to [cipher]. Only strong biometrics are allowed: there
 * is deliberately no "use phone PIN" option, so the phone's own PIN can never open Morrow.
 */
suspend fun FragmentActivity.askForFingerprint(
    lock: LockManager<*>,
    cipher: Cipher,
    title: String,
    subtitle: String?,
    negativeButton: String,
): FingerprintOutcome {
    lock.systemDialogOpened()
    try {
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authenticated = result.cryptoObject?.cipher
                        if (cont.isActive) {
                            cont.resume(authenticated?.let { FingerprintOutcome.Success(it) } ?: FingerprintOutcome.Error("No cipher returned"))
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!cont.isActive) return
                        val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        cont.resume(if (cancelled) FingerprintOutcome.Cancelled else FingerprintOutcome.Error(errString.toString()))
                    }
                    // onAuthenticationFailed (finger not recognised) is shown by the system sheet itself.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { subtitle?.let { setSubtitle(it) } }
                .setNegativeButtonText(negativeButton)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    } finally {
        lock.systemDialogClosed()
    }
}
