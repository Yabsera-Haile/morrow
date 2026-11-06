package app.morrow.diary.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morrow.diary.data.CarryOver
import app.morrow.diary.data.DeletedGoal
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.db.GoalEntity
import app.morrow.diary.data.db.PlanEntity
import app.morrow.diary.data.prefs.SettingsStore
import app.morrow.diary.domain.DayClock
import app.morrow.diary.domain.DayPhase
import app.morrow.diary.domain.QuoteOfTheDay
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    data class PlanDeleted(val plan: PlanEntity) : TodayEvent
}

/**
 * Today, morning and evening. [persistScope] outlives this ViewModel, so a note being typed when
 * the app locks still gets saved (the database closes a moment after locking, not instantly).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    settings: SettingsStore,
    dayClock: DayClock,
    private val diary: DiaryRepository,
    private val persistScope: CoroutineScope,
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

    private fun <T> perDay(source: (LocalDate) -> Flow<T>): StateFlow<T?> =
        dates.flatMapLatest(source).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val goals: StateFlow<List<GoalEntity>?> = perDay { diary.observeGoals(it) }
    val carryOver: StateFlow<CarryOver?> = perDay { diary.observeCarryOver(it) }
    /** The saved note; null while it's still loading (an empty note is ""). */
    val savedNote: StateFlow<String?> = perDay { date -> diary.observeDay(date).map { it?.note ?: "" } }

    /** Whether "Done for today" was pressed; null while loading. */
    val checkedIn: StateFlow<Boolean?> = perDay { date -> diary.observeDay(date).map { it?.checkinDoneAt != null } }

    val plansForTomorrow: StateFlow<List<PlanEntity>?> = perDay { diary.observePlansWrittenOn(it) }

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

    // ---- Evening -------------------------------------------------------------------------------

    fun sendToTomorrow(goal: GoalEntity) {
        viewModelScope.launch { diary.sendToTomorrow(goal) }
    }

    fun addPlans(texts: List<String>) {
        if (texts.isEmpty()) return
        withToday { diary.addPlans(it, texts) }
    }

    fun removePlan(plan: PlanEntity) {
        viewModelScope.launch {
            diary.deletePlan(plan.id)?.let { _events.send(TodayEvent.PlanDeleted(it)) }
        }
    }

    fun restorePlan(plan: PlanEntity) {
        viewModelScope.launch { diary.restorePlan(plan) }
    }

    fun setCheckinDone(done: Boolean) = withToday { diary.setCheckinDone(it, done) }

    /** Saving notes as you type: each change queues a save, and only the newest text is written. */
    private val noteMutex = Mutex()
    private var pendingNote: Pair<LocalDate, String>? = null

    fun onNoteChange(text: String) {
        val today = header.value?.date ?: return
        pendingNote = today to text
        persistScope.launch {
            noteMutex.withLock {
                val (date, latest) = pendingNote ?: return@withLock
                pendingNote = null
                // If the diary locked and closed in the meantime there's nothing left to write to.
                runCatching { diary.updateNote(date, latest) }
            }
        }
    }
}
