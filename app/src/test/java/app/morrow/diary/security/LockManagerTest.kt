package app.morrow.diary.security

import app.morrow.diary.data.prefs.AutoLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeSession(val key: ByteArray) : AutoCloseable {
    var closed = false
    override fun close() {
        closed = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LockManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private var elapsed = 0L
    private var wall = 1_000_000L
    private val autoLock = MutableStateFlow(AutoLock.ONE_MINUTE)
    private val opened = mutableListOf<FakeSession>()

    private fun TestScope.newLock(): LockManager<FakeSession> {
        val vault = KeyVault(File(tmp.root, "vault"), FakeDeviceWrapper(), FakeBiometricKeys(), PinKdf(iterations = 1_000))
        return LockManager(
            vault = vault,
            openSession = { key -> FakeSession(key.copyOf()).also { opened += it } },
            autoLockSetting = autoLock,
            scope = backgroundScope,
            io = StandardTestDispatcher(testScheduler),
            elapsedMillis = { elapsed },
            wallMillis = { wall },
            closeDelayMillis = 750,
        )
    }

    private suspend fun LockManager<*>.awaitReady() = info.first { it.ready }

    @Test fun `starts locked with the starter PIN`() = runTest {
        val lock = newLock()
        val info = lock.awaitReady()
        assertEquals(LockState.Locked, lock.state.value)
        assertEquals(4, info.pinLength)
        assertTrue(info.starterPin)
    }

    @Test fun `the right PIN opens a session`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        assertEquals(PinResult.Success, lock.unlockWithPin("1234"))
        val state = lock.state.value
        assertTrue(state is LockState.Unlocked)
        assertEquals(32, (state as LockState.Unlocked).session.key.size)
    }

    @Test fun `wrong PINs count down, then lock out, even for the right PIN`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        assertEquals(PinResult.Wrong(4), lock.unlockWithPin("0000"))
        repeat(3) { lock.unlockWithPin("0000") }
        assertEquals(PinResult.LockedOut(wall + 30_000), lock.unlockWithPin("0000"))
        assertEquals(PinResult.LockedOut(wall + 30_000), lock.unlockWithPin("1234"))
        assertEquals(LockState.Locked, lock.state.value)

        wall += 30_001
        assertEquals(PinResult.Success, lock.unlockWithPin("1234"))
        assertEquals(0, lock.info.value.failures)
    }

    @Test fun `locking closes the session shortly after`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        lock.unlockWithPin("1234")
        val session = opened.single()

        lock.lock()
        assertEquals(LockState.Locked, lock.state.value)
        assertEquals(1, lock.lockCount.value)
        assertFalse(session.closed)
        advanceTimeBy(800)
        runCurrent()
        assertTrue(session.closed)
    }

    @Test fun `auto-lock waits for the chosen time away`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        lock.unlockWithPin("1234")
        advanceUntilIdle()

        lock.onAppBackgrounded()
        elapsed += 30_000
        lock.onAppForegrounded()
        assertTrue(lock.isUnlocked)

        lock.onAppBackgrounded()
        elapsed += 60_000
        lock.onAppForegrounded()
        assertFalse(lock.isUnlocked)
    }

    @Test fun `immediate auto-lock locks as soon as the app leaves the screen`() = runTest {
        autoLock.value = AutoLock.IMMEDIATELY
        val lock = newLock().also { it.awaitReady() }
        advanceUntilIdle()
        lock.unlockWithPin("1234")

        lock.onAppBackgrounded()
        assertFalse(lock.isUnlocked)
    }

    @Test fun `system dialogs like the fingerprint sheet don't trigger auto-lock`() = runTest {
        autoLock.value = AutoLock.IMMEDIATELY
        val lock = newLock().also { it.awaitReady() }
        advanceUntilIdle()
        lock.unlockWithPin("1234")

        lock.systemDialogOpened()
        lock.onAppBackgrounded()
        assertTrue(lock.isUnlocked)
        lock.systemDialogClosed()
    }

    @Test fun `a changed PIN works after locking and the old one doesn't`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        lock.unlockWithPin("1234")
        lock.changePin("482916")
        assertFalse(lock.info.value.starterPin)
        assertEquals(6, lock.info.value.pinLength)

        lock.lock()
        assertEquals(PinResult.Wrong(4), lock.unlockWithPin("1234"))
        assertEquals(PinResult.Success, lock.unlockWithPin("482916"))
        assertArrayEquals(opened.first().key, opened.last().key)
    }

    @Test fun `checking the current PIN counts toward the limit`() = runTest {
        val lock = newLock().also { it.awaitReady() }
        lock.unlockWithPin("1234")
        assertEquals(PinResult.Wrong(4), lock.verifyPin("1111"))
        assertEquals(PinResult.Success, lock.verifyPin("1234"))
        assertEquals(0, lock.info.value.failures)
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) = org.junit.Assert.assertArrayEquals(expected, actual)
}
