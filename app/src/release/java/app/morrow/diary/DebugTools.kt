package app.morrow.diary

import app.morrow.diary.data.DiarySession
import java.time.LocalDate

/** Release builds: no development helpers. See src/debug for the real thing. */
object DebugTools {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun addSampleDays(session: DiarySession, today: LocalDate) = Unit
}
