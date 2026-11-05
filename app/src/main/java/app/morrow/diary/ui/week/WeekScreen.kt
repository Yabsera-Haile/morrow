package app.morrow.diary.ui.week

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morrow.diary.R
import app.morrow.diary.ui.components.ComingSoon
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.SubpageHeader

@Composable
fun WeekScreen(onBack: () -> Unit) {
    MorrowPage {
        SubpageHeader(stringResource(R.string.week_title), onBack)
        ComingSoon(stringResource(R.string.coming_soon), Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
    }
}
