package app.morrow.diary.security

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stands in for the Android Keystore off-device: same AES-GCM, but the key lives in memory. */
class FakeDeviceWrapper : KeyWrapper {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.doFinal(secret)
        return ByteBuffer.allocate(1 + cipher.iv.size + sealed.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(sealed).array()
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val b = ByteBuffer.wrap(blob)
        val iv = ByteArray(b.get().toInt()).also { b.get(it) }
        val sealed = ByteArray(b.remaining()).also { b.get(it) }
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }.doFinal(sealed)
    }
}

/** A fingerprint key that "authenticates" instantly, and can be invalidated like a new enrolment would. */
class FakeBiometricKeys : BiometricKeys {
    var key: SecretKey? = null
    var invalidated = false

    override fun newEncryptCipher(): Cipher {
        invalidated = false
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val k = key
        if (invalidated || k == null) throw BiometricKeyInvalidatedException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv)) }
    }

    override fun delete() {
        key = null
    }
}

fun ByteArray.containsSequence(needle: ByteArray): Boolean =
    (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
