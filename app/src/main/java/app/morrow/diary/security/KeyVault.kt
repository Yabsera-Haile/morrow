package app.morrow.diary.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts small secrets (like the database key) with a key that can't leave the phone. */
interface KeyWrapper {
    fun wrap(secret: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

/**
 * Holds the random 256-bit key that encrypts the diary database.
 *
 * The key is only ever stored in wrapped (encrypted) form, in the app's no-backup folder.
 * For now it is wrapped by the Android Keystore alone; the lock milestone adds the Morrow PIN
 * and fingerprint layers on top, so the diary can't be opened without one of them.
 */
class KeyVault(
    private val dir: File,
    private val wrapper: KeyWrapper = AndroidKeystoreWrapper(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val keyFile get() = File(dir, "db.key")

    @Synchronized
    fun databaseKey(): ByteArray {
        keyFile.takeIf { it.exists() }?.let { return wrapper.unwrap(it.readBytes()) }

        dir.mkdirs()
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val temp = File(dir, "db.key.tmp")
        temp.writeBytes(wrapper.wrap(key))
        check(temp.renameTo(keyFile)) { "Couldn't save the database key" }
        return key
    }

    companion object {
        const val KEY_BYTES = 32
    }
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
