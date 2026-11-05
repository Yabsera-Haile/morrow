package app.morrow.diary

import app.morrow.diary.data.DiarySession
import java.time.Instant
import java.time.LocalDate

/**
 * Development helpers. This file only exists in debug builds; release builds get a no-op twin
 * (src/release), so none of this can reach the installed app.
 */
object DebugTools {
    const val ENABLED = true

    /** Fills the few days before [today] with realistic goals, plans and notes, if they're empty. */
    suspend fun addSampleDays(session: DiarySession, today: LocalDate) {
        val diary = session.diary
        val days = session.database.days()
        val yesterday = today.minusDays(1)
        if (days.get(yesterday) != null) return

        suspend fun day(date: LocalDate, goals: List<Pair<String, Boolean>>, plans: List<String>, note: String) {
            val ids = diary.addGoals(date, goals.map { it.first })
            ids.zip(goals).forEach { (id, goal) -> if (goal.second) diary.setGoalDone(id, true) }
            plans.forEach { diary.addPlan(date, it) }
            diary.updateNote(date, note)
            days.setCheckinDone(date, Instant.now(), Instant.now())
        }

        day(
            today.minusDays(3),
            listOf("Submit the tax form" to true, "Reply to the landlord" to true, "Finish chapter 2 of the course" to false),
            listOf("Run 5k after work"),
            "Too many meetings, but I did the ones that mattered.",
        )
        day(
            today.minusDays(2),
            listOf("Run 5k after work" to true, "Dinner with Sam" to true, "Clean out the inbox" to false),
            listOf("Send the invoice to Marta"),
            "Good dinner. Inbox can wait.",
        )
        val ids = diary.addGoals(
            yesterday,
            listOf("Pick up the package at the post office", "Send the invoice to Marta", "Finish chapter 3 of the course", "Call the bank about the card"),
        )
        diary.setGoalDone(ids[0], true)
        diary.setGoalDone(ids[1], true)
        diary.addPlan(yesterday, "Book the dentist")
        diary.addPlan(yesterday, "Run 5k after work")
        diary.updateNote(yesterday, "Invoice is finally out. Chapter 3 is harder than I thought.")
        days.setCheckinDone(yesterday, Instant.now(), Instant.now())
    }
}
