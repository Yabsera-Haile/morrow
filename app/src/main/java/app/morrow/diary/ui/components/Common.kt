package app.morrow.diary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.morrow.diary.R
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType

/** A full-height, scrollable paper page that stays clear of the status and navigation bars. */
@Composable
fun MorrowPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Morrow.colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/** "‹ Today" back link plus a large serif title, as on Journal, Week review and Settings. */
@Composable
fun SubpageHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String = stringResource(R.string.back_to_today),
    trailing: @Composable () -> Unit = {},
) {
    val c = Morrow.colors
    Column(Modifier.padding(start = 16.dp, end = 24.dp, top = 12.dp)) {
        TextButton(onClick = onBack) {
            Icon(MorrowIcons.ChevronLeft, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            Text(backLabel, style = MorrowType.bodyMedium, color = c.ink, modifier = Modifier.padding(start = 2.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MorrowType.screenTitle, color = c.ink)
            trailing()
        }
    }
}

/** The day's quote: a terracotta quote mark over large serif text, between two hairlines. */
@Composable
fun QuoteBlock(quote: String, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val description = stringResource(R.string.cd_quote, quote)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(c.hairline, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                drawLine(c.hairline, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            }
            .padding(top = 16.dp, bottom = 20.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // The glyph sits in the top part of its 56sp line, so a 30dp box shows all of it.
        Box(Modifier.height(30.dp)) {
            Text(
                "“",
                style = MorrowType.quoteMark,
                color = c.accent,
                modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true),
            )
        }
        Text(quote, style = MorrowType.quote, color = c.ink)
    }
}

/** Muted placeholder copy for parts of a screen that later milestones fill in. */
@Composable
fun ComingSoon(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MorrowType.small, color = Morrow.colors.muted, modifier = modifier)
}
