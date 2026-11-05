package app.morrow.diary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.BuildConfig
import app.morrow.diary.R
import app.morrow.diary.data.prefs.ThemeMode
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.SubpageHeader
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType

/** First slice of Settings: the Display group. Reminders, security and backup come with their milestones. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val c = Morrow.colors

    MorrowPage {
        SubpageHeader(stringResource(R.string.settings_title), onBack)

        GroupLabel(stringResource(R.string.settings_display))
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_theme), style = MorrowType.bodyMedium, color = c.ink)
                ThemePicker(s.theme, viewModel::setTheme)
            }
            HorizontalDivider(color = c.hairline)
            SwitchRow(stringResource(R.string.settings_daily_quote), s.showQuote, viewModel::setShowQuote)
            HorizontalDivider(color = c.hairline)
            SwitchRow(stringResource(R.string.settings_show_stats), s.showStats, viewModel::setShowStats)
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_footer_name, BuildConfig.VERSION_NAME), style = MorrowType.sectionTitle, color = c.ink)
            Text(
                stringResource(R.string.settings_footer_privacy),
                style = MorrowType.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MorrowType.overline.copy(letterSpacing = MorrowType.overline.letterSpacing * 0.8f),
        color = Morrow.colors.muted,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val c = Morrow.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(c.group, shape)
            .border(1.dp, c.hairline, shape),
    ) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Morrow.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MorrowType.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.emptyRing,
                uncheckedThumbColor = c.onAccent,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = Morrow.colors
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .background(if (isSelected) c.inputSurface else Color.Transparent, RoundedCornerShape(9.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = MorrowType.small.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = c.ink,
                )
            }
        }
    }
}
