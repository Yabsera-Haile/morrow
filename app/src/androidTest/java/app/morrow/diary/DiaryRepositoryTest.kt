package app.morrow.diary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.morrow.diary.data.DiaryRepository
import app.morrow.diary.data.db.GoalOrigin
import app.morrow.diary.data.db.MorrowDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The morning logic against a real (in-memory) database: transfer, carry over, undo, reorder. */
@RunWith(AndroidJUnit4::class)
class DiaryRepositoryTest {
    private lateinit var db: MorrowDatabase
    private lateinit var diary: DiaryRepository

    private val tue = LocalDate.of(2026, 11, 3)
    private val wed = LocalDate.of(2026, 11, 4)
    private val thu = LocalDate.of(2026, 11, 5)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MorrowDatabase::class.java).build()
        diary = DiaryRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun goalTexts(date: LocalDate) = diary.observeGoals(date).first().map { it.text }

    /** Wednesday: three goals (one done), and two plans written for Thursday. */
    private suspend fun wednesday() {
        val ids = diary.addGoals(wed, listOf("Send the invoice", "Finish chapter 3", "Call the bank"))
        diary.setGoalDone(ids[0], true)
        diary.addPlan(wed, "Book the dentist")
        diary.addPlan(wed, "Run 5k after work")
    }

    @Test fun lastNightsPlansAndUnfinishedGoalsAreOffered() = runBlocking {
        wednesday()
        val carry = diary.observeCarryOver(thu).first()!!
        assertEquals(wed, carry.sourceDate)
        assertEquals(listOf("Book the dentist", "Run 5k after work"), carry.pendingPlans.map { it.text })
        assertEquals(listOf("Finish chapter 3", "Call the bank"), carry.pendingGoals.map { it.text })
    }

    @Test fun transferAllAddsEverythingInOrderAndEmptiesTheCard() = runBlocking {
        wednesday()
        diary.addGoals(thu, listOf("Pick up the package"))
        val carry = diary.observeCarryOver(thu).first()!!

        val created = diary.transferAll(thu, carry)
        assertEquals(4, created.size)
        assertEquals(
            listOf("Pick up the package", "Book the dentist", "Run 5k after work", "Finish chapter 3", "Call the bank"),
            goalTexts(thu),
        )
        val origins = diary.observeGoals(thu).first().map { it.origin }
        assertEquals(listOf(GoalOrigin.MANUAL, GoalOrigin.PLAN, GoalOrigin.PLAN, GoalOrigin.CARRIED, GoalOrigin.CARRIED), origins)
        assertFalse(diary.observeCarryOver(thu).first()!!.hasPending)
    }

    @Test fun undoPutsEverythingBackOnTheCard() = runBlocking {
        wednesday()
        val created = diary.transferAll(thu, diary.observeCarryOver(thu).first()!!)
        diary.undoTransfer(created)

        assertEquals(emptyList<String>(), goalTexts(thu))
        assertEquals(4, diary.observeCarryOver(thu).first()!!.pendingCount)
    }

    @Test fun transferringTwiceDoesNotDuplicate() = runBlocking {
        wednesday()
        val plan = diary.observeCarryOver(thu).first()!!.pendingPlans.first()
        diary.transferPlans(thu, listOf(plan.id))
        diary.transferPlans(thu, listOf(plan.id))
        val goal = diary.observeCarryOver(thu).first()!!.pendingGoals.first()
        diary.carryGoals(thu, listOf(goal.id))
        diary.carryGoals(thu, listOf(goal.id))
        assertEquals(listOf("Book the dentist", "Finish chapter 3"), goalTexts(thu))
    }

    @Test fun goalsSentOnWithTomorrowShowAsPlansNotAsUnfinished() = runBlocking {
        val ids = diary.addGoals(wed, listOf("Finish chapter 3", "Call the bank"))
        diary.addPlan(wed, "Finish chapter 3", sourceGoalId = ids[0]) // "→ Tomorrow"
        val carry = diary.observeCarryOver(thu).first()!!
        assertEquals(listOf("Finish chapter 3"), carry.pendingPlans.map { it.text })
        assertEquals(listOf("Call the bank"), carry.pendingGoals.map { it.text })
    }

    @Test fun skippedDaysFallBackToTheMostRecentDayUsed() = runBlocking {
        diary.addGoals(tue, listOf("Old unfinished goal"))
        // Wednesday never opened. Thursday's card looks at Tuesday.
        val carry = diary.observeCarryOver(thu).first()!!
        assertEquals(tue, carry.sourceDate)
        assertEquals(listOf("Old unfinished goal"), carry.pendingGoals.map { it.text })
    }

    @Test fun nothingBeforeTodayMeansNoCard() = runBlocking {
        diary.addGoals(thu, listOf("Only today"))
        assertNull(diary.observeCarryOver(thu).first())
    }

    @Test fun deletingATransferredGoalAndUndoingRestoresTheLink() = runBlocking {
        wednesday()
        val plan = diary.observeCarryOver(thu).first()!!.pendingPlans.first()
        val goalId = diary.transferPlans(thu, listOf(plan.id)).single()

        val deleted = diary.deleteGoal(goalId)!!
        assertTrue(diary.observeCarryOver(thu).first()!!.pendingPlans.any { it.id == plan.id }) // offered again

        diary.restoreGoal(deleted)
        assertEquals(listOf("Book the dentist"), goalTexts(thu))
        assertFalse(diary.observeCarryOver(thu).first()!!.pendingPlans.any { it.id == plan.id })
    }

    @Test fun tomorrowSendsAGoalOnOnceAndTomorrowsCardShowsItAsAPlan() = runBlocking {
        val ids = diary.addGoals(wed, listOf("Finish chapter 3", "Call the bank"))
        val chapter = diary.observeGoals(wed).first().first { it.id == ids[0] }

        val planId = diary.sendToTomorrow(chapter)
        val again = diary.sendToTomorrow(chapter)
        assertTrue(planId != null)
        assertNull(again)
        assertEquals(listOf("Finish chapter 3"), diary.observePlansWrittenOn(wed).first().map { it.text })

        // Thursday morning: it's a plan now, and only the other goal is "not done yesterday".
        val carry = diary.observeCarryOver(thu).first()!!
        assertEquals(listOf("Finish chapter 3"), carry.pendingPlans.map { it.text })
        assertEquals(listOf("Call the bank"), carry.pendingGoals.map { it.text })
    }

    @Test fun eveningPlansCanBeAddedRemovedAndRestored() = runBlocking {
        diary.addPlans(wed, listOf("Book the dentist", "  ", "Run 5k"))
        val plans = diary.observePlansWrittenOn(wed).first()
        assertEquals(listOf("Book the dentist", "Run 5k"), plans.map { it.text })

        val removed = diary.deletePlan(plans[0].id)!!
        assertEquals(listOf("Run 5k"), diary.observePlansWrittenOn(wed).first().map { it.text })
        diary.restorePlan(removed)
        assertEquals(listOf("Book the dentist", "Run 5k"), diary.observePlansWrittenOn(wed).first().map { it.text })
    }

    @Test fun removingASentPlanMakesTheGoalSendableAgain() = runBlocking {
        val id = diary.addGoals(wed, listOf("Finish chapter 3")).single()
        val goal = diary.observeGoals(wed).first().single()
        val planId = diary.sendToTomorrow(goal)!!
        diary.deletePlan(planId)
        assertTrue(diary.sendToTomorrow(goal) != null)
        assertEquals(id, diary.observePlansWrittenOn(wed).first().single().sourceGoalId)
    }

    @Test fun notesAndCheckinAreSaved() = runBlocking {
        diary.updateNote(wed, "Invoice is finally out.")
        diary.setCheckinDone(wed, true)
        val day = diary.observeDay(wed).first()!!
        assertEquals("Invoice is finally out.", day.note)
        assertTrue(day.checkinDoneAt != null)

        diary.setCheckinDone(wed, false)
        assertNull(diary.observeDay(wed).first()!!.checkinDoneAt)
    }

    @Test fun reorderAndRename() = runBlocking {
        val ids = diary.addGoals(thu, listOf("A", "B", "C"))
        diary.reorderGoals(listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("C", "A", "B"), goalTexts(thu))

        diary.renameGoal(ids[0], "  Alpha  ")
        assertEquals(listOf("C", "Alpha", "B"), goalTexts(thu))

        val deleted = diary.renameGoal(ids[1], "   ")
        assertEquals(listOf("C", "Alpha"), goalTexts(thu))
        diary.restoreGoal(deleted!!)
        assertEquals(listOf("C", "Alpha", "B"), goalTexts(thu))
    }
}
