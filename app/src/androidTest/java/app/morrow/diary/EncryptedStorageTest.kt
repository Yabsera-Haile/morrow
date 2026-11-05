package app.morrow.diary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.DiarySession
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.data.prefs.AutoLock
import app.morrow.diary.security.KeyVault
import app.morrow.diary.security.LockManager
import app.morrow.diary.security.LockState
import app.morrow.diary.security.PinResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Runs on a phone or emulator, with the real Android Keystore: proves the diary is really encrypted. */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-${System.nanoTime()}.db"
    private val vaultDir = File(context.cacheDir, "vault-test-${System.nanoTime()}")
    private val nov5 = LocalDate.of(2026, 11, 5)

    @After fun cleanUp() {
        context.deleteDatabase(dbName)
        vaultDir.deleteRecursively()
    }

    @Test fun diaryRoundTripsAndTheFileHasNoPlainText() = runBlocking {
        val db = MorrowDatabase.open(context, ByteArray(32) { it.toByte() }, dbName)
        DiaryRepository(db).addGoals(nov5, listOf("Send the invoice to Marta", "   ", "Run 5k after work"))

        val goals = db.goals().listFor(nov5)
        assertEquals(listOf("Send the invoice to Marta", "Run 5k after work"), goals.map { it.text })
        assertEquals(listOf(0, 1), goals.map { it.position })
        db.close()

        val bytes = context.getDatabasePath(dbName).readBytes()
        assertFalse("file starts with the plain SQLite header", String(bytes.copyOf(15), Charsets.US_ASCII) == "SQLite format 3")
        assertFalse("goal text is readable in the file", String(bytes, Charsets.ISO_8859_1).contains("invoice"))
    }

    @Test fun aWrongKeyCannotReadTheDiary() {
        runBlocking {
            MorrowDatabase.open(context, ByteArray(32) { 1 }, dbName).apply {
                DiaryRepository(this).ensureDay(nov5)
                close()
            }
        }
        val wrong = MorrowDatabase.open(context, ByteArray(32) { 2 }, dbName)
        assertThrows(Exception::class.java) { runBlocking { wrong.days().get(nov5) } }
        wrong.close()
    }

    @Test fun theVaultOpensOnlyWithTheRightPinAndNeverStoresTheKey() {
        val vault = KeyVault(vaultDir)
        vault.initialize("1234", isStarterPin = true)
        val key = vault.unlockWithPin("1234")
        assertNotNull(key)
        assertEquals(32, key!!.size)
        assertNull(vault.unlockWithPin("0000"))

        vault.changePin(key, "482916")
        assertNull(vault.unlockWithPin("1234"))
        assertArrayEquals(key, vault.unlockWithPin("482916"))

        vaultDir.listFiles()!!.forEach { file ->
            val stored = file.readBytes()
            val leaked = (0..stored.size - key.size).any { start -> key.indices.all { stored[start + it] == key[it] } }
            assertFalse("raw key found in ${file.name}", leaked)
        }
    }

    @Test fun thePinUnlocksTheRealDiaryAndLockingClosesIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lock = LockManager(
            vault = KeyVault(vaultDir),
            openSession = { key -> DiarySession(MorrowDatabase.open(context, key, dbName)) },
            autoLockSetting = flowOf(AutoLock.ONE_MINUTE),
            scope = scope,
            closeDelayMillis = 0,
        )
        lock.info.first { it.ready }

        assertEquals(PinResult.Wrong(4), lock.unlockWithPin("0000"))
        assertEquals(PinResult.Success, lock.unlockWithPin("1234"))
        val session = (lock.state.value as LockState.Unlocked).session
        session.diary.addGoals(nov5, listOf("Call the bank about the card"))
        assertEquals(1, session.database.goals().listFor(nov5).size)

        lock.lock()
        assertTrue(lock.state.value is LockState.Locked)
        lock.state.first { it is LockState.Locked }
        scope.cancel()
    }
}
