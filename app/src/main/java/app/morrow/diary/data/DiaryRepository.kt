package app.morrow.diary.data

import androidx.room.withTransaction
import app.morrow.diary.data.db.DayEntity
import app.morrow.diary.data.db.GoalEntity
import app.morrow.diary.data.db.GoalOrigin
import app.morrow.diary.data.db.MorrowDatabase
import app.morrow.diary.data.db.PlanEntity
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The one place the UI reads and writes diary content. Later milestones add the transfer,
 * check-in and week-review operations here.
 */
class DiaryRepository(
    private val db: MorrowDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private fun now(): Instant = clock.instant()

    fun observeGoals(date: LocalDate): Flow<List<GoalEntity>> = db.goals().observeFor(date)

    fun observePlansWrittenOn(date: LocalDate): Flow<List<PlanEntity>> = db.plans().observeWrittenOn(date)

    fun observeDay(date: LocalDate): Flow<DayEntity?> = db.days().observe(date)

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
