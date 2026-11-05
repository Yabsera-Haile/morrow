package app.morrow.diary.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.domain.DayClock
import app.morrow.diary.domain.DayPhase
import app.morrow.diary.domain.QuoteOfTheDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TodayHeader(
    val date: LocalDate,
    val phase: DayPhase,
    val quote: String?,
)

class TodayViewModel(settings: SettingsStore, dayClock: DayClock) : ViewModel() {

    /** Re-evaluates once a minute, so the greeting and the day roll over while the app is open. */
    private val minuteTicks = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val header: StateFlow<TodayHeader?> = combine(settings.settings, minuteTicks) { s, _ ->
        val now = dayClock.now()
        val date = DayClock.logicalDate(now, s.dayStartsAt)
        TodayHeader(
            date = date,
            phase = DayClock.phase(now, s.dayStartsAt, s.eveningTime),
            quote = if (s.showQuote) QuoteOfTheDay.pick(date, s.quotes) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
