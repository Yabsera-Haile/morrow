package app.morrow.diary.data

import androidx.room.withTransaction
import app.morrow.diary.data.db.DayEntity
import app.morrow.diary.data.db.GoalEntity
import app.morrow.diary.data.db.GoalOrigin
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.data.db.PlanEntity
import app.morrow.diary.data.db.UnfinishedGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * What the "From last night" card offers: the plans written on the most recent day used before
 * today, and that day's unfinished goals (minus ones already sent on as plans via "→ Tomorrow").
 */
data class CarryOver(
    val sourceDate: LocalDate,
    val plans: List<PlanEntity>,
    val unfinished: List<UnfinishedGoal>,
) {
    val pendingPlans: List<PlanEntity> get() = plans.filter { it.transferredToGoalId == null }
    val pendingGoals: List<GoalEntity> get() = unfinished.filterNot { it.carried }.map { it.goal }
    val pendingCount: Int get() = pendingPlans.size + pendingGoals.size
    val hasPending: Boolean get() = pendingCount > 0
}

/** Everything needed to put a deleted goal back exactly as it was. */
data class DeletedGoal(
    val goal: GoalEntity,
    val plansTransferredToIt: List<Long>,
    val plansMadeFromIt: List<Long>,
    val goalsCarriedFromIt: List<Long>,
)

/** The one place the UI reads and writes diary content. */
class DiaryRepository(
    private val db: MorrowDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private fun now(): Instant = clock.instant()

    // ---- Reading -------------------------------------------------------------------------------

    fun observeGoals(date: LocalDate): Flow<List<GoalEntity>> = db.goals().observeFor(date)

    fun observePlansWrittenOn(date: LocalDate): Flow<List<PlanEntity>> = db.plans().observeWrittenOn(date)

    fun observeDay(date: LocalDate): Flow<DayEntity?> = db.days().observe(date)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCarryOver(today: LocalDate): Flow<CarryOver?> =
        db.days().observeLatestUsedBefore(today)
            .distinctUntilChanged()
            .flatMapLatest { source ->
                if (source == null) {
                    flowOf(null)
                } else {
                    combine(db.plans().observeWrittenOn(source), db.goals().observeUnfinishedNotForwarded(source)) { plans, unfinished ->
                        CarryOver(source, plans, unfinished)
                    }
                }
            }

    // ---- Goals ---------------------------------------------------------------------------------

    suspend fun ensureDay(date: LocalDate) {
        val now = now()
        db.days().insertIfMissing(DayEntity(date = date, createdAt = now, updatedAt = now))
    }

    /** Adds goals in order after the existing ones; blank lines are skipped. Returns the new ids. */
    suspend fun addGoals(date: LocalDate, texts: List<String>, origin: GoalOrigin = GoalOrigin.MANUAL): List<Long> =
        db.withTransaction {
            ensureDay(date)
            val start = db.goals().nextPosition(date)
            val goals = texts.map(String::trim).filter(String::isNotEmpty).mapIndexed { i, text ->
                GoalEntity(date = date, text = text, position = start + i, origin = origin, createdAt = now())
            }
            db.goals().insertAll(goals)
        }

    suspend fun setGoalDone(id: Long, done: Boolean) = db.goals().setDone(id, done)

    /** Renames a goal. A blank name deletes it instead; the deletion is returned so it can be undone. */
    suspend fun renameGoal(id: Long, text: String): DeletedGoal? {
        val clean = text.trim()
        if (clean.isEmpty()) return deleteGoal(id)
        db.goals().rename(id, clean)
        return null
    }

    suspend fun reorderGoals(idsInOrder: List<Long>) = db.goals().reorder(idsInOrder)

    suspend fun deleteGoal(id: Long): DeletedGoal? = db.withTransaction {
        val goal = db.goals().get(id) ?: return@withTransaction null
        val snapshot = DeletedGoal(
            goal = goal,
            plansTransferredToIt = db.plans().transferredTo(id),
            plansMadeFromIt = db.plans().fromGoal(id),
            goalsCarriedFromIt = db.goals().carriedFrom(id),
        )
        db.goals().delete(id)
        snapshot
    }

    suspend fun restoreGoal(deleted: DeletedGoal) = db.withTransaction {
        ensureDay(deleted.goal.date)
        db.goals().insert(deleted.goal) // same id, so the links below point at it again
        val id = deleted.goal.id
        if (deleted.plansTransferredToIt.isNotEmpty()) db.plans().relinkTransferred(deleted.plansTransferredToIt, id)
        if (deleted.plansMadeFromIt.isNotEmpty()) db.plans().relinkSource(deleted.plansMadeFromIt, id)
        if (deleted.goalsCarriedFromIt.isNotEmpty()) db.goals().relinkCarried(deleted.goalsCarriedFromIt, id)
    }

    // ---- From last night -----------------------------------------------------------------------

    /** Turns plans into goals for [today]. Plans already transferred are skipped. Returns the new goal ids. */
    suspend fun transferPlans(today: LocalDate, planIds: List<Long>): List<Long> = db.withTransaction {
        ensureDay(today)
        var position = db.goals().nextPosition(today)
        planIds.mapNotNull { planId ->
            val plan = db.plans().get(planId)?.takeIf { it.transferredToGoalId == null } ?: return@mapNotNull null
            val goalId = db.goals().insert(
                GoalEntity(date = today, text = plan.text, position = position++, origin = GoalOrigin.PLAN, createdAt = now()),
            )
            db.plans().markTransferred(planId, goalId)
            goalId
        }
    }

    /** Adds unfinished goals from an earlier day to [today]. Ones already carried are skipped. */
    suspend fun carryGoals(today: LocalDate, goalIds: List<Long>): List<Long> = db.withTransaction {
        ensureDay(today)
        var position = db.goals().nextPosition(today)
        goalIds.mapNotNull { goalId ->
            val goal = db.goals().get(goalId) ?: return@mapNotNull null
            if (goal.done || goal.date >= today || db.goals().carriedFrom(goalId).isNotEmpty()) return@mapNotNull null
            db.goals().insert(
                GoalEntity(
                    date = today,
                    text = goal.text,
                    position = position++,
                    origin = GoalOrigin.CARRIED,
                    createdAt = now(),
                    carriedFromGoalId = goalId,
                ),
            )
        }
    }

    /** "Transfer all": every pending plan, then every pending unfinished goal. */
    suspend fun transferAll(today: LocalDate, carry: CarryOver): List<Long> = db.withTransaction {
        transferPlans(today, carry.pendingPlans.map { it.id }) + carryGoals(today, carry.pendingGoals.map { it.id })
    }

    /** Removes goals created by a transfer; their plans and goals become available to transfer again. */
    suspend fun undoTransfer(goalIds: List<Long>) {
        if (goalIds.isNotEmpty()) db.goals().deleteAll(goalIds)
    }

    // ---- Evening (used from the next milestone on) ---------------------------------------------

    suspend fun updateNote(date: LocalDate, note: String) {
        ensureDay(date)
        db.days().updateNote(date, note, now())
    }

    suspend fun addPlan(writtenOn: LocalDate, text: String, sourceGoalId: Long? = null): Long =
        db.withTransaction {
            ensureDay(writtenOn)
            db.plans().insert(
                PlanEntity(writtenOn = writtenOn, text = text.trim(), position = db.plans().nextPosition(writtenOn), sourceGoalId = sourceGoalId),
            )
        }
}
