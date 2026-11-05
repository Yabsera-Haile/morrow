package app.morrow.diary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morrow.diary.data.prefs.AutoLock
import app.morrow.diary.data.prefs.MorrowSettings
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.data.prefs.ThemeMode
import app.morrow.diary.security.LockInfo
import app.morrow.diary.security.LockManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore, val lock: LockManager<*>) : ViewModel() {

    val settings: StateFlow<MorrowSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lockInfo: StateFlow<LockInfo> = lock.info

    fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }
    fun setShowQuote(show: Boolean) = update { it.copy(showQuote = show) }
    fun setShowStats(show: Boolean) = update { it.copy(showStats = show) }
    fun setAutoLock(value: AutoLock) = update { it.copy(autoLock = value) }
    fun setHideInRecents(hide: Boolean) = update { it.copy(hideInRecents = hide) }

    /** Called after a successful fingerprint enrolment. */
    fun fingerprintEnabled() = update { it.copy(fingerprintUnlock = true) }

    fun disableFingerprint() {
        viewModelScope.launch {
            lock.disableFingerprint()
            store.update { it.copy(fingerprintUnlock = false) }
        }
    }

    private fun update(change: (MorrowSettings) -> MorrowSettings) {
        viewModelScope.launch { store.update(change) }
    }
}
