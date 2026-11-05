package app.morrow.diary.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One row per diary day. `date` is the logical day (a 1 AM check-in belongs to the day before). */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val date: LocalDate,
    val note: String = "",
    @ColumnInfo(name = "checkin_done_at") val checkinDoneAt: Instant? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

enum class GoalOrigin {
    /** Typed in that morning. */
    MANUAL,

    /** Transferred from the previous evening's plans. */
    PLAN,

    /** An unfinished goal from an earlier day, added again. */
    CARRIED,
}

@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(entity = DayEntity::class, parentColumns = ["date"], childColumns = ["date"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("date")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val text: String,
    val position: Int,
    val done: Boolean = false,
    val origin: GoalOrigin = GoalOrigin.MANUAL,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/** A plan written on the evening of `writtenOn`, meant for the next day. */
@Entity(
    tableName = "plans",
    foreignKeys = [
        ForeignKey(entity = DayEntity::class, parentColumns = ["date"], childColumns = ["written_on"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["transferred_to_goal_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["source_goal_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("written_on"), Index("transferred_to_goal_id"), Index("source_goal_id")],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "written_on") val writtenOn: LocalDate,
    val text: String,
    val position: Int,
    /** Set once the plan has been added to a day's goals. */
    @ColumnInfo(name = "transferred_to_goal_id") val transferredToGoalId: Long? = null,
    /** Set when the plan came from a not-done goal via "→ Tomorrow". */
    @ColumnInfo(name = "source_goal_id") val sourceGoalId: Long? = null,
)

@Entity(tableName = "week_reviews")
data class WeekReviewEntity(
    @PrimaryKey @ColumnInfo(name = "week_start") val weekStart: LocalDate,
    @ColumnInfo(name = "went_well") val wentWell: String = "",
    @ColumnInfo(name = "got_in_way") val gotInWay: String = "",
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** A focus item shown on Today during the week starting `forWeek`. */
@Entity(tableName = "week_focus", indices = [Index("for_week")])
data class WeekFocusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "for_week") val forWeek: LocalDate,
    val text: String,
    val position: Int,
)
