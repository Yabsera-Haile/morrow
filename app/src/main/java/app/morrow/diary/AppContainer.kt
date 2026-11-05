package app.morrow.diary

import android.content.Context
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.data.prefs.settingsDataStore
import app.morrow.diary.domain.DayClock
import app.morrow.diary.security.KeyVault
import java.io.File

/** Wires the app together by hand. Small enough that a dependency-injection library isn't worth it. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val dayClock = DayClock()

    val settings: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    private val keyVault: KeyVault by lazy { KeyVault(File(appContext.noBackupFilesDir, "vault")) }

    /** Opened on first use. From the lock milestone on, this only happens after unlocking. */
    val database: MorrowDatabase by lazy {
        val key = keyVault.databaseKey()
        try {
            MorrowDatabase.open(appContext, key)
        } finally {
            key.fill(0)
        }
    }

    val diary: DiaryRepository by lazy { DiaryRepository(database) }
}
