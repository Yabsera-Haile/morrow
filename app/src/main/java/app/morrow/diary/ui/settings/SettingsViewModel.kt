package app.morrow.diary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morrow.diary.data.prefs.MorrowSettings
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.data.prefs.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val settings: StateFlow<MorrowSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }
    fun setShowQuote(show: Boolean) = update { it.copy(showQuote = show) }
    fun setShowStats(show: Boolean) = update { it.copy(showStats = show) }

    private fun update(change: (MorrowSettings) -> MorrowSettings) {
        viewModelScope.launch { store.update(change) }
    }
}
