package app.morrow.diary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayEntity?>

    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun get(date: LocalDate): DayEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(day: DayEntity)

    @Query("UPDATE days SET note = :note, updated_at = :now WHERE date = :date")
    suspend fun updateNote(date: LocalDate, note: String, now: Instant)

    @Query("UPDATE days SET checkin_done_at = :doneAt, updated_at = :now WHERE date = :date")
    suspend fun setCheckinDone(date: LocalDate, doneAt: Instant?, now: Instant)

    /** The most recent day before [before] that has any goals or plans: the source for "From last night". */
    @Query(
        """
        SELECT * FROM days
        WHERE date < :before
          AND (EXISTS (SELECT 1 FROM goals WHERE goals.date = days.date)
               OR EXISTS (SELECT 1 FROM plans WHERE plans.written_on = days.date))
        ORDER BY date DESC LIMIT 1
        """,
    )
    suspend fun latestUsedBefore(before: LocalDate): DayEntity?

    @Query("SELECT * FROM days WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayEntity>>
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE date = :date ORDER BY position, id")
    fun observeFor(date: LocalDate): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE date = :date ORDER BY position, id")
    suspend fun listFor(date: LocalDate): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE date BETWEEN :from AND :to ORDER BY date, position, id")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<GoalEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM goals WHERE date = :date")
    suspend fun nextPosition(date: LocalDate): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert
    suspend fun insertAll(goals: List<GoalEntity>): List<Long>

    @Query("UPDATE goals SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE goals SET text = :text WHERE id = :id")
    suspend fun rename(id: Long, text: String)

    @Query("UPDATE goals SET position = :position WHERE id = :id")
    suspend fun move(id: Long, position: Int)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goals WHERE text LIKE '%' || :query || '%' ORDER BY date DESC")
    suspend fun search(query: String): List<GoalEntity>
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE written_on = :date ORDER BY position, id")
    fun observeWrittenOn(date: LocalDate): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE written_on = :date ORDER BY position, id")
    suspend fun listWrittenOn(date: LocalDate): List<PlanEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM plans WHERE written_on = :date")
    suspend fun nextPosition(date: LocalDate): Int

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Query("UPDATE plans SET transferred_to_goal_id = :goalId WHERE id = :planId")
    suspend fun markTransferred(planId: Long, goalId: Long?)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM plans WHERE text LIKE '%' || :query || '%' ORDER BY written_on DESC")
    suspend fun search(query: String): List<PlanEntity>
}

@Dao
interface WeekDao {
    @Query("SELECT * FROM week_reviews WHERE week_start = :weekStart")
    fun observeReview(weekStart: LocalDate): Flow<WeekReviewEntity?>

    @Upsert
    suspend fun upsertReview(review: WeekReviewEntity)

    @Query("SELECT * FROM week_focus WHERE for_week = :week ORDER BY position, id")
    fun observeFocus(week: LocalDate): Flow<List<WeekFocusEntity>>

    @Query("DELETE FROM week_focus WHERE for_week = :week")
    suspend fun clearFocus(week: LocalDate)

    @Insert
    suspend fun insertFocus(items: List<WeekFocusEntity>)

    @Transaction
    suspend fun replaceFocus(week: LocalDate, texts: List<String>) {
        clearFocus(week)
        insertFocus(texts.mapIndexed { i, text -> WeekFocusEntity(forWeek = week, text = text, position = i) })
    }
}
