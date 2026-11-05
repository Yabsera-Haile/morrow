package app.morrow.diary.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Holds the random 256-bit key that encrypts the diary database. The key itself is never stored;
 * only two locked copies of it are:
 *
 * - **PIN slot**: encrypted with a key derived from the Morrow PIN (PBKDF2), and that whole blob
 *   encrypted again by a key inside the Android Keystore. So a copied file can't be brute-forced
 *   off the phone, and on the phone [PinAttemptPolicy] limits guessing.
 * - **Fingerprint slot** (optional): encrypted by a Keystore key that only works right after a
 *   strong biometric match, and that Android destroys if a new fingerprint is enrolled.
 *
 * Changing the PIN re-encrypts the key, not the diary, so it's instant.
 */
class KeyVault(
    private val dir: File,
    private val deviceWrapper: KeyWrapper = AndroidKeystoreWrapper(),
    private val biometricKeys: BiometricKeys = AndroidBiometricKeys(),
    private val kdf: PinKdf = PinKdf(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val pinSlot get() = File(dir, "pin.slot")
    private val bioSlot get() = File(dir, "bio.slot")
    private val stateFile get() = File(dir, "state.properties")

    @Synchronized
    fun isInitialized(): Boolean = pinSlot.exists()

    /** Creates the database key, protected by [pin]. Does nothing if the vault already exists. */
    @Synchronized
    fun initialize(pin: String, isStarterPin: Boolean) {
        if (isInitialized()) return
        require(PinRules.isValid(pin)) { "Invalid PIN" }
        dir.mkdirs()
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        try {
            writePinSlot(key, pin)
            writeState(VaultState(pinLength = pin.length, starterPin = isStarterPin))
        } finally {
            key.fill(0)
        }
    }

    /** The database key if [pin] is right, otherwise null. Doesn't count attempts; see [recordFailure]. */
    @Synchronized
    fun unlockWithPin(pin: String): ByteArray? {
        val inner = ByteBuffer.wrap(deviceWrapper.unwrap(pinSlot.readBytes()))
        require(inner.get() == SLOT_VERSION) { "Unknown PIN slot format" }
        val iterations = inner.int
        val salt = ByteArray(inner.get().toInt()).also { inner.get(it) }
        val iv = ByteArray(inner.get().toInt()).also { inner.get(it) }
        val sealed = ByteArray(inner.remaining()).also { inner.get(it) }
        return try {
            Cipher.getInstance(GCM)
                .apply { init(Cipher.DECRYPT_MODE, kdf.derive(pin, salt, iterations), GCMParameterSpec(TAG_BITS, iv)) }
                .doFinal(sealed)
        } catch (wrongPin: AEADBadTagException) {
            null
        }
    }

    @Synchronized
    fun changePin(databaseKey: ByteArray, newPin: String) {
        require(PinRules.isValid(newPin)) { "Invalid PIN" }
        writePinSlot(databaseKey, newPin)
        writeState(state().copy(pinLength = newPin.length, starterPin = false))
    }

    @Synchronized
    fun state(): VaultState = VaultState.read(stateFile)

    @Synchronized
    fun recordFailure(nowMillis: Long): VaultState {
        val failures = state().failures + 1
        val lockout = PinAttemptPolicy.lockoutAfter(failures)
        val next = state().copy(
            failures = failures,
            lockedUntilMillis = if (lockout.isZero) 0 else nowMillis + lockout.toMillis(),
        )
        writeState(next)
        return next
    }

    @Synchronized
    fun recordSuccess() {
        val s = state()
        if (s.failures != 0 || s.lockedUntilMillis != 0L) writeState(s.copy(failures = 0, lockedUntilMillis = 0))
    }

    // ---- Fingerprint slot ----------------------------------------------------------------------

    @Synchronized
    fun hasBiometricSlot(): Boolean = bioSlot.exists()

    /** A fresh fingerprint-bound cipher. Pass it through BiometricPrompt, then to [saveBiometricSlot]. */
    @Synchronized
    fun biometricEnrollCipher(): Cipher {
        bioSlot.delete() // its key is about to be replaced, so the old slot could never be opened again
        return biometricKeys.newEncryptCipher()
    }

    @Synchronized
    fun saveBiometricSlot(authenticatedCipher: Cipher, databaseKey: ByteArray) {
        val sealed = authenticatedCipher.doFinal(databaseKey)
        val iv = authenticatedCipher.iv
        dir.mkdirs()
        bioSlot.writeAtomically(
            ByteBuffer.allocate(2 + iv.size + sealed.size).put(SLOT_VERSION).put(iv.size.toByte()).put(iv).put(sealed).array(),
        )
        writeState(state().copy(biometricInvalidated = false))
    }

    /**
     * The cipher for the lock screen's fingerprint prompt. If Android destroyed the key because a
     * new fingerprint was enrolled, the slot is removed and [BiometricKeyInvalidatedException] thrown.
     */
    @Synchronized
    fun biometricUnlockCipher(): Cipher {
        val (iv, _) = readBioSlot()
        return try {
            biometricKeys.decryptCipher(iv)
        } catch (e: BiometricKeyInvalidatedException) {
            removeBiometric(invalidated = true)
            throw e
        }
    }

    @Synchronized
    fun unlockWithBiometric(authenticatedCipher: Cipher): ByteArray = authenticatedCipher.doFinal(readBioSlot().second)

    @Synchronized
    fun removeBiometric(invalidated: Boolean = false) {
        bioSlot.delete()
        biometricKeys.delete()
        if (isInitialized()) writeState(state().copy(biometricInvalidated = invalidated))
    }

    // ---- Internals -----------------------------------------------------------------------------

    private fun writePinSlot(databaseKey: ByteArray, pin: String) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val sealed = Cipher.getInstance(GCM)
            .apply { init(Cipher.ENCRYPT_MODE, kdf.derive(pin, salt, kdf.iterations), GCMParameterSpec(TAG_BITS, iv)) }
            .doFinal(databaseKey)
        val inner = ByteBuffer.allocate(1 + 4 + 1 + salt.size + 1 + iv.size + sealed.size)
            .put(SLOT_VERSION)
            .putInt(kdf.iterations)
            .put(salt.size.toByte()).put(salt)
            .put(iv.size.toByte()).put(iv)
            .put(sealed)
            .array()
        pinSlot.writeAtomically(deviceWrapper.wrap(inner))
    }

    private fun readBioSlot(): Pair<ByteArray, ByteArray> {
        val buffer = ByteBuffer.wrap(bioSlot.readBytes())
        require(buffer.get() == SLOT_VERSION) { "Unknown fingerprint slot format" }
        val iv = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return iv to sealed
    }

    private fun writeState(state: VaultState) = state.write(stateFile)

    companion object {
        const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val SLOT_VERSION: Byte = 1
        private const val GCM = "AES/GCM/NoPadding"
    }
}

/** Small bookkeeping next to the slots. Holds no secrets. */
data class VaultState(
    val pinLength: Int = PinRules.MIN_LENGTH,
    val starterPin: Boolean = false,
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0,
    val biometricInvalidated: Boolean = false,
) {
    fun write(file: File) {
        val props = Properties().apply {
            setProperty("pinLength", pinLength.toString())
            setProperty("starterPin", starterPin.toString())
            setProperty("failures", failures.toString())
            setProperty("lockedUntilMillis", lockedUntilMillis.toString())
            setProperty("biometricInvalidated", biometricInvalidated.toString())
        }
        val text = java.io.StringWriter().also { props.store(it, null) }.toString()
        file.parentFile?.mkdirs()
        file.writeAtomically(text.toByteArray())
    }

    companion object {
        fun read(file: File): VaultState {
            if (!file.exists()) return VaultState()
            val p = Properties().apply { file.reader().use { load(it) } }
            return VaultState(
                pinLength = p.getProperty("pinLength")?.toIntOrNull() ?: PinRules.MIN_LENGTH,
                starterPin = p.getProperty("starterPin")?.toBoolean() ?: false,
                failures = p.getProperty("failures")?.toIntOrNull() ?: 0,
                lockedUntilMillis = p.getProperty("lockedUntilMillis")?.toLongOrNull() ?: 0,
                biometricInvalidated = p.getProperty("biometricInvalidated")?.toBoolean() ?: false,
            )
        }
    }
}

/** Write to a temp file, then swap it in, so a crash mid-write can't leave a half-written slot. */
private fun File.writeAtomically(bytes: ByteArray) {
    val temp = File(parentFile, "$name.tmp")
    temp.writeBytes(bytes)
    Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/**
 * PBKDF2-HMAC-SHA256, built into Android. The real barrier against guessing is the Keystore layer
 * around the PIN slot plus [PinAttemptPolicy]; this just adds cost without slowing down unlocking.
 * Each slot stores its own iteration count, so this can change without breaking existing slots.
 */
class PinKdf(val iterations: Int = 30_000) {
    fun derive(pin: String, salt: ByteArray, iterations: Int): SecretKey {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}

// ---- Android Keystore ----------------------------------------------------------------------------

/** Encrypts and decrypts small secrets with a key that can't leave the phone. */
interface KeyWrapper {
    fun wrap(secret: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

/** AES-256-GCM with a key generated inside the Android Keystore (hardware-backed on modern phones). */
class AndroidKeystoreWrapper(private val alias: String = "morrow.device.v1") : KeyWrapper {

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(secret)
        return ByteBuffer.allocate(2 + iv.size + sealed.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(sealed)
            .array()
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(blob)
        val version = buffer.get()
        require(version == FORMAT_VERSION) { "Unknown key format $version" }
        val iv = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(sealed)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}

class BiometricKeyInvalidatedException : Exception("The fingerprint key is gone (a new fingerprint was added to the phone)")

/** The fingerprint-bound key. An interface so the vault logic can be tested off-device. */
interface BiometricKeys {
    /** Replaces any old key and returns a cipher ready to encrypt; it needs a fingerprint match to finish. */
    fun newEncryptCipher(): Cipher

    /** A cipher ready to decrypt; it needs a fingerprint match to finish. */
    @Throws(BiometricKeyInvalidatedException::class)
    fun decryptCipher(iv: ByteArray): Cipher

    fun delete()
}

class AndroidBiometricKeys(private val alias: String = "morrow.fingerprint.v1") : BiometricKeys {

    override fun newEncryptCipher(): Cipher {
        delete()
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Adding a fingerprint to the phone destroys this key, so a new finger can't open Morrow.
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Every use needs a fresh strong-biometric match. The phone's own PIN never counts.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val key = keyStore().getKey(alias, null) as? SecretKey ?: throw BiometricKeyInvalidatedException()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException()
        }
    }

    override fun delete() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
