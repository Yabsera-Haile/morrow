package app.morrow.diary.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morrow.diary.R
import app.morrow.diary.ui.components.ComingSoon
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.SubpageHeader
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType

@Composable
fun JournalScreen(onBack: () -> Unit, onOpenWeek: () -> Unit) {
    val c = Morrow.colors
    MorrowPage {
        SubpageHeader(stringResource(R.string.journal_title), onBack) {
            OutlinedButton(
                onClick = onOpenWeek,
                border = BorderStroke(1.dp, c.hairline),
                contentPadding = PaddingValues(start = 14.dp, end = 10.dp),
            ) {
                Text(stringResource(R.string.journal_last_review), style = MorrowType.caption, color = c.accent)
                Icon(MorrowIcons.ChevronRight, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp).padding(start = 2.dp))
            }
        }
        ComingSoon(stringResource(R.string.coming_soon), Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
    }
}
