package app.morrow.diary.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DayEntity::class, GoalEntity::class, PlanEntity::class, WeekReviewEntity::class, WeekFocusEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MorrowDatabase : RoomDatabase() {
    abstract fun days(): DayDao
    abstract fun goals(): GoalDao
    abstract fun plans(): PlanDao
    abstract fun weeks(): WeekDao

    companion object {
        const val FILE_NAME = "morrow.db"

        /**
         * Opens the diary database, encrypted with SQLCipher (AES-256) using [key].
         * The caller should wipe its own copy of the key afterwards.
         */
        fun open(context: Context, key: ByteArray, fileName: String = FILE_NAME): MorrowDatabase {
            System.loadLibrary("sqlcipher")
            // The factory keeps its own copy, so Room can reopen the file later.
            val factory = SupportOpenHelperFactory(key.copyOf())
            return Room.databaseBuilder(context.applicationContext, MorrowDatabase::class.java, fileName)
                .openHelperFactory(factory)
                .build()
        }
    }
}
