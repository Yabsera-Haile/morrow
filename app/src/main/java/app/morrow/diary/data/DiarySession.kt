package app.morrow.diary.data

import app.morrow.diary.data.db.MorrowDatabase

/** Everything that only exists while the diary is unlocked. Closing it closes the database. */
class DiarySession(val database: MorrowDatabase) : AutoCloseable {
    val diary = DiaryRepository(database)

    override fun close() = database.close()
}
