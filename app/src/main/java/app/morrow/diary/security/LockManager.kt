package app.morrow.diary.security

import android.os.SystemClock
import app.morrow.diary.data.prefs.AutoLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

sealed interface LockState<out S> {
    data object Locked : LockState<Nothing>
    data class Unlocked<S>(val session: S) : LockState<S>
}

/** What the lock screen and Settings need to know. Contains no secrets. */
data class LockInfo(
    val ready: Boolean = false,
    val pinLength: Int = PinRules.MIN_LENGTH,
    val failures: Int = 0,
    val lockedOutUntilMillis: Long = 0,
    val starterPin: Boolean = false,
    val hasFingerprint: Boolean = false,
    val fingerprintInvalidated: Boolean = false,
)

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val triesLeft: Int) : PinResult
    data class LockedOut(val untilMillis: Long) : PinResult
}

/**
 * The gate in front of the diary. While locked, the database is closed and its key isn't in memory.
 * Unlocking (PIN or fingerprint) recovers the key from the [KeyVault] and opens a session.
 */
class LockManager<S : AutoCloseable>(
    private val vault: KeyVault,
    private val openSession: (databaseKey: ByteArray) -> S,
    autoLockSetting: Flow<AutoLock>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallMillis: () -> Long = { System.currentTimeMillis() },
    private val closeDelayMillis: Long = 750,
) {
    private val _state = MutableStateFlow<LockState<S>>(LockState.Locked)
    val state: StateFlow<LockState<S>> = _state.asStateFlow()

    private val _info = MutableStateFlow(LockInfo())
    val info: StateFlow<LockInfo> = _info.asStateFlow()

    /** Goes up every time the app locks, so the lock screen knows to offer the fingerprint again. */
    private val _lockCount = MutableStateFlow(0)
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    /** Set when "Forgot PIN?" was answered with a fingerprint: the app should go straight to a new PIN. */
    private val _pinResetRequested = MutableStateFlow(false)
    val pinResetRequested: StateFlow<Boolean> = _pinResetRequested.asStateFlow()

    private val mutex = Mutex()
    private var databaseKey: ByteArray? = null
    private var autoLock = AutoLock.ONE_MINUTE
    private var backgroundedAt: Long? = null
    private var openSystemDialogs = 0
    private var pendingClose: Pair<S, Job>? = null

    init {
        scope.launch { autoLockSetting.collect { autoLock = it } }
        scope.launch {
            withContext(io) { vault.initialize(PinRules.STARTER_PIN, isStarterPin = true) }
            refreshInfo()
        }
    }

    val isUnlocked: Boolean get() = _state.value is LockState.Unlocked

    // ---- Unlocking -----------------------------------------------------------------------------

    suspend fun unlockWithPin(pin: String): PinResult = mutex.withLock {
        if (isUnlocked) return@withLock PinResult.Success
        when (val check = checkPin(pin)) {
            is PinCheck.Right -> {
                val session = withContext(io) { openSession(check.key) }
                open(check.key, session)
                PinResult.Success
            }
            is PinCheck.Failed -> check.result
        }
    }

    /** For "enter your current PIN" while already unlocked. Counts toward the same attempt limit. */
    suspend fun verifyPin(pin: String): PinResult = mutex.withLock {
        when (val check = checkPin(pin)) {
            is PinCheck.Right -> {
                check.key.fill(0)
                PinResult.Success
            }
            is PinCheck.Failed -> check.result
        }
    }

    /** The cipher for the lock screen's fingerprint prompt, or null if fingerprint unlock isn't set up. */
    suspend fun fingerprintUnlockCipher(): Cipher? {
        val cipher = withContext(io) {
            if (!vault.hasBiometricSlot()) {
                null
            } else {
                try {
                    vault.biometricUnlockCipher()
                } catch (e: BiometricKeyInvalidatedException) {
                    null
                }
            }
        }
        refreshInfo()
        return cipher
    }

    suspend fun unlockWithFingerprint(authenticatedCipher: Cipher, thenResetPin: Boolean = false) = mutex.withLock {
        if (isUnlocked) return@withLock
        val key = withContext(io) {
            vault.unlockWithBiometric(authenticatedCipher).also { vault.recordSuccess() }
        }
        val session = withContext(io) { openSession(key) }
        if (thenResetPin) _pinResetRequested.value = true
        open(key, session)
    }

    fun pinResetHandled() {
        _pinResetRequested.value = false
    }

    // ---- While unlocked ------------------------------------------------------------------------

    suspend fun changePin(newPin: String) = mutex.withLock {
        val key = checkNotNull(databaseKey) { "Unlock first" }
        withContext(io) { vault.changePin(key, newPin) }
        refreshInfo()
    }

    suspend fun fingerprintEnrollCipher(): Cipher {
        check(isUnlocked) { "Unlock first" }
        return withContext(io) { vault.biometricEnrollCipher() }
    }

    suspend fun enableFingerprint(authenticatedCipher: Cipher) = mutex.withLock {
        val key = checkNotNull(databaseKey) { "Unlock first" }
        withContext(io) { vault.saveBiometricSlot(authenticatedCipher, key) }
        refreshInfo()
    }

    suspend fun disableFingerprint() = mutex.withLock {
        withContext(io) { vault.removeBiometric() }
        refreshInfo()
    }

    // ---- Locking -------------------------------------------------------------------------------

    fun lock() {
        val current = _state.value as? LockState.Unlocked ?: return
        databaseKey?.fill(0)
        databaseKey = null
        backgroundedAt = null
        _state.value = LockState.Locked
        _lockCount.value += 1
        // Give the UI a moment to leave the screen before the database underneath it closes.
        val job = scope.launch {
            delay(closeDelayMillis)
            closeQuietly(current.session)
            pendingClose = null
        }
        pendingClose = current.session to job
    }

    fun onAppBackgrounded() {
        if (!isUnlocked || openSystemDialogs > 0) return
        if (autoLock == AutoLock.IMMEDIATELY) lock() else backgroundedAt = elapsedMillis()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (isUnlocked && elapsedMillis() - since >= autoLock.seconds * 1000) lock()
    }

    /** Fingerprint prompts and file pickers can briefly push the app into the background; don't lock for those. */
    fun systemDialogOpened() {
        openSystemDialogs += 1
    }

    fun systemDialogClosed() {
        openSystemDialogs = (openSystemDialogs - 1).coerceAtLeast(0)
    }

    // ---- Internals -----------------------------------------------------------------------------

    private sealed interface PinCheck {
        class Right(val key: ByteArray) : PinCheck
        class Failed(val result: PinResult) : PinCheck
    }

    private suspend fun checkPin(pin: String): PinCheck {
        val check = withContext(io) {
            val now = wallMillis()
            val before = vault.state()
            if (before.lockedUntilMillis > now) return@withContext PinCheck.Failed(PinResult.LockedOut(before.lockedUntilMillis))

            val key = vault.unlockWithPin(pin)
            if (key != null) {
                vault.recordSuccess()
                PinCheck.Right(key)
            } else {
                val after = vault.recordFailure(now)
                PinCheck.Failed(
                    if (after.lockedUntilMillis > now) PinResult.LockedOut(after.lockedUntilMillis)
                    else PinResult.Wrong(PinAttemptPolicy.triesLeft(after.failures)),
                )
            }
        }
        refreshInfo()
        return check
    }

    private suspend fun open(key: ByteArray, session: S) {
        pendingClose?.let { (old, job) ->
            job.cancel()
            closeQuietly(old)
        }
        pendingClose = null
        databaseKey = key
        backgroundedAt = null
        _state.value = LockState.Unlocked(session)
        refreshInfo()
    }

    private suspend fun refreshInfo() {
        val (s, hasFingerprint) = withContext(io) { vault.state() to vault.hasBiometricSlot() }
        _info.value = LockInfo(
            ready = true,
            pinLength = s.pinLength,
            failures = s.failures,
            lockedOutUntilMillis = s.lockedUntilMillis,
            starterPin = s.starterPin,
            hasFingerprint = hasFingerprint,
            fingerprintInvalidated = s.biometricInvalidated,
        )
    }

    private fun closeQuietly(session: S) {
        runCatching { session.close() }
    }
}
