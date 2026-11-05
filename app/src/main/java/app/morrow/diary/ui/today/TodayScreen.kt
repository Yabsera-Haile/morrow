package app.morrow.diary.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.R
import app.morrow.diary.domain.DayPhase
import app.morrow.diary.ui.components.ComingSoon
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.QuoteBlock
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenJournal: () -> Unit, onOpenSettings: () -> Unit) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val current = header ?: return
    val c = Morrow.colors

    MorrowPage {
        TodayHeaderRow(current.date, onOpenJournal, onOpenSettings)

        current.quote?.let { QuoteBlock(it, Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) }

        Text(
            text = stringResource(
                when (current.phase) {
                    DayPhase.MORNING -> R.string.greeting_morning
                    DayPhase.AFTERNOON -> R.string.greeting_afternoon
                    DayPhase.EVENING -> R.string.greeting_evening
                },
            ),
            style = MorrowType.greeting,
            color = c.muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )

        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 26.dp)
                .fillMaxWidth()
                .background(c.card, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.today_goals_title), style = MorrowType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            ComingSoon(stringResource(R.string.today_goals_coming), Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TodayHeaderRow(date: LocalDate, onOpenJournal: () -> Unit, onOpenSettings: () -> Unit) {
    val c = Morrow.colors
    val locale = Locale.getDefault()
    val dateLabel = remember(date, locale) { date.format(DateTimeFormatter.ofPattern("MMMM d", locale)).uppercase(locale) }
    val dayName = remember(date, locale) { date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }

    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dateLabel, style = MorrowType.overline, color = c.muted)
            Text(
                dayName,
                style = MorrowType.dayTitle,
                color = c.ink,
                modifier = Modifier.padding(top = 2.dp).semantics { heading() },
            )
        }
        IconButton(onClick = onOpenJournal) {
            Icon(MorrowIcons.Calendar, contentDescription = stringResource(R.string.cd_journal), tint = c.ink, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(MorrowIcons.Sliders, contentDescription = stringResource(R.string.cd_settings), tint = c.ink, modifier = Modifier.size(22.dp))
        }
    }
}
