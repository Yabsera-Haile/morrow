package app.morrow.diary.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morrow.diary.data.CarryOver
import app.morrow.diary.data.DeletedGoal
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.db.GoalEntity
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.domain.DayClock
import app.morrow.diary.domain.DayPhase
import app.morrow.diary.domain.QuoteOfTheDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TodayHeader(
    val date: LocalDate,
    val phase: DayPhase,
    val quote: String?,
    val eveningTime: LocalTime,
)

sealed interface TodayEvent {
    data class GoalDeleted(val deleted: DeletedGoal) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    settings: SettingsStore,
    dayClock: DayClock,
    private val diary: DiaryRepository,
) : ViewModel() {

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
            eveningTime = s.eveningTime,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val dates: Flow<LocalDate> = header.filterNotNull().map { it.date }.distinctUntilChanged()

    val goals: StateFlow<List<GoalEntity>?> =
        dates.flatMapLatest { diary.observeGoals(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val carryOver: StateFlow<CarryOver?> =
        dates.flatMapLatest { diary.observeCarryOver(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Goals created by transfers since this screen opened, so "Undo" can take them back. */
    private val _transferredNow = MutableStateFlow<List<Long>>(emptyList())
    val transferredNow: StateFlow<List<Long>> = _transferredNow.asStateFlow()

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = _events.receiveAsFlow()

    private fun withToday(block: suspend (LocalDate) -> Unit) {
        val today = header.value?.date ?: return
        viewModelScope.launch { block(today) }
    }

    // ---- Goals ---------------------------------------------------------------------------------

    fun addGoals(texts: List<String>) {
        if (texts.isEmpty()) return
        withToday { diary.addGoals(it, texts) }
    }

    fun setDone(goal: GoalEntity, done: Boolean) {
        viewModelScope.launch { diary.setGoalDone(goal.id, done) }
    }

    fun rename(goal: GoalEntity, text: String) {
        if (text.trim() == goal.text) return
        viewModelScope.launch {
            diary.renameGoal(goal.id, text)?.let { _events.send(TodayEvent.GoalDeleted(it)) }
        }
    }

    fun delete(goal: GoalEntity) {
        viewModelScope.launch {
            diary.deleteGoal(goal.id)?.let { _events.send(TodayEvent.GoalDeleted(it)) }
        }
    }

    fun restore(deleted: DeletedGoal) {
        viewModelScope.launch { diary.restoreGoal(deleted) }
    }

    fun reorder(idsInOrder: List<Long>) {
        viewModelScope.launch { diary.reorderGoals(idsInOrder) }
    }

    // ---- From last night -----------------------------------------------------------------------

    fun transferPlan(planId: Long) = withToday { today ->
        trackTransfer(diary.transferPlans(today, listOf(planId)))
    }

    fun carryGoal(goalId: Long) = withToday { today ->
        trackTransfer(diary.carryGoals(today, listOf(goalId)))
    }

    fun transferAll() {
        val carry = carryOver.value ?: return
        withToday { today -> trackTransfer(diary.transferAll(today, carry)) }
    }

    fun undoTransfer() {
        val ids = _transferredNow.value
        _transferredNow.value = emptyList()
        viewModelScope.launch { diary.undoTransfer(ids) }
    }

    private fun trackTransfer(newGoalIds: List<Long>) {
        _transferredNow.value = _transferredNow.value + newGoalIds
    }
}
