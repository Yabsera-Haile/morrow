package app.morrow.diary.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    // ISO dates ("2026-11-05") sort the same way as text and as dates, so range queries just work.
    @TypeConverter fun dateToText(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun textToDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @TypeConverter fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}
