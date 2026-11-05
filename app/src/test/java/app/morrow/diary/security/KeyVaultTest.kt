package app.morrow.diary.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KeyVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val device = FakeDeviceWrapper()
    private val fingerprint = FakeBiometricKeys()
    private val dir: File get() = File(tmp.root, "vault")

    private fun vault() = KeyVault(dir, device, fingerprint, PinKdf(iterations = 1_000))

    @Test fun `the starter PIN opens a fresh vault and always gives the same key`() {
        vault().initialize("1234", isStarterPin = true)
        val first = vault().unlockWithPin("1234")
        val again = vault().unlockWithPin("1234")
        assertNotNull(first)
        assertEquals(32, first!!.size)
        assertArrayEquals(first, again)
        assertTrue(vault().state().starterPin)
        assertEquals(4, vault().state().pinLength)
    }

    @Test fun `a wrong PIN gives nothing`() {
        vault().initialize("1234", isStarterPin = true)
        assertNull(vault().unlockWithPin("0000"))
        assertNull(vault().unlockWithPin("12345"))
    }

    @Test fun `initializing again keeps the existing key`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")
        vault().initialize("9999", isStarterPin = false)
        assertArrayEquals(key, vault().unlockWithPin("1234"))
        assertNull(vault().unlockWithPin("9999"))
    }

    @Test fun `changing the PIN keeps the diary key and retires the old PIN`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        vault().changePin(key, "482916")

        assertNull(vault().unlockWithPin("1234"))
        assertArrayEquals(key, vault().unlockWithPin("482916"))
        assertEquals(6, vault().state().pinLength)
        assertFalse(vault().state().starterPin)
    }

    @Test fun `the key never appears in the stored files`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        dir.listFiles()!!.forEach { file -> assertFalse("key found in ${file.name}", file.readBytes().containsSequence(key)) }
    }

    @Test fun `failures lock out after five tries and success resets them`() {
        vault().initialize("1234", isStarterPin = true)
        val now = 1_000_000L
        repeat(4) { assertEquals(0L, vault().recordFailure(now).lockedUntilMillis) }
        assertEquals(now + 30_000, vault().recordFailure(now).lockedUntilMillis)
        assertEquals(now + 60_000, vault().recordFailure(now).lockedUntilMillis)
        assertEquals(6, vault().state().failures) // survives a restart (new instance, same files)

        vault().recordSuccess()
        assertEquals(0, vault().state().failures)
        assertEquals(0L, vault().state().lockedUntilMillis)
    }

    @Test fun `fingerprint slot opens the same key`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        vault().saveBiometricSlot(vault().biometricEnrollCipher(), key)

        assertTrue(vault().hasBiometricSlot())
        val unlocked = vault().unlockWithBiometric(vault().biometricUnlockCipher())
        assertArrayEquals(key, unlocked)
    }

    @Test fun `a new fingerprint on the phone removes the slot and remembers why`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        vault().saveBiometricSlot(vault().biometricEnrollCipher(), key)

        fingerprint.invalidated = true
        assertThrows(BiometricKeyInvalidatedException::class.java) { vault().biometricUnlockCipher() }
        assertFalse(vault().hasBiometricSlot())
        assertTrue(vault().state().biometricInvalidated)

        // Turning it back on clears the flag, and the PIN still works throughout.
        vault().saveBiometricSlot(vault().biometricEnrollCipher(), key)
        assertFalse(vault().state().biometricInvalidated)
        assertArrayEquals(key, vault().unlockWithPin("1234"))
    }

    @Test fun `turning fingerprint off deletes the slot and the key`() {
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        vault().saveBiometricSlot(vault().biometricEnrollCipher(), key)

        vault().removeBiometric()
        assertFalse(vault().hasBiometricSlot())
        assertNull(fingerprint.key)
        assertFalse(vault().state().biometricInvalidated)
    }

    @Test fun `PINs outside the rules are refused`() {
        assertThrows(IllegalArgumentException::class.java) { vault().initialize("12", isStarterPin = false) }
        vault().initialize("1234", isStarterPin = true)
        val key = vault().unlockWithPin("1234")!!
        assertThrows(IllegalArgumentException::class.java) { vault().changePin(key, "abcd") }
    }
}
