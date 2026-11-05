package app.morrow.diary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.security.KeyVault
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Runs on a phone or emulator: proves the diary file is really encrypted, not just locked. */
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

    @Test fun theVaultKeepsTheSameKeyAndNeverStoresItInTheClear() {
        val first = KeyVault(vaultDir).databaseKey()
        val again = KeyVault(vaultDir).databaseKey()
        assertEquals(32, first.size)
        assertArrayEquals(first, again)

        val stored = File(vaultDir, "db.key").readBytes()
        val leaked = stored.toList().windowed(first.size).any { it.toByteArray().contentEquals(first) }
        assertFalse("raw key found in the stored file", leaked)
    }
}
