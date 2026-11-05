package app.morrow.diary

import android.content.Context
import app.morrow.diary.data.DiarySession
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.data.prefs.settingsDataStore
import app.morrow.diary.domain.DayClock
import app.morrow.diary.security.KeyVault
import app.morrow.diary.security.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import java.io.File

/** Wires the app together by hand. Small enough that a dependency-injection library isn't worth it. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dayClock = DayClock()

    val settings = SettingsStore(appContext.settingsDataStore)

    /** The diary database only exists inside an unlocked session; see [LockManager]. */
    val lock: LockManager<DiarySession> = LockManager(
        vault = KeyVault(File(appContext.noBackupFilesDir, "vault")),
        openSession = { key -> DiarySession(MorrowDatabase.open(appContext, key)) },
        autoLockSetting = settings.settings.map { it.autoLock },
        scope = appScope,
    )
}
