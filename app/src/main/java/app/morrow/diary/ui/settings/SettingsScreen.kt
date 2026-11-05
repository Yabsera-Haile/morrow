package app.morrow.diary.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.BuildConfig
import app.morrow.diary.R
import app.morrow.diary.data.prefs.AutoLock
import app.morrow.diary.data.prefs.ThemeMode
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.SubpageHeader
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.lock.canUseFingerprint
import app.morrow.diary.ui.lock.enrollFingerprint
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import kotlinx.coroutines.launch

/** Security and Display for now. Reminders, day & week, and backup arrive with their milestones. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onAddSampleDays: (() -> Unit)? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val info by viewModel.lockInfo.collectAsStateWithLifecycle()
    val s = settings ?: return
    val c = Morrow.colors
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var showAutoLock by remember { mutableStateOf(false) }

    val fingerprintAvailable = remember { canUseFingerprint(context) }
    val fingerprintOn = s.fingerprintUnlock && info.hasFingerprint
    val enrollTitle = stringResource(R.string.fingerprint_enroll_title)
    val enrollSubtitle = stringResource(R.string.fingerprint_enroll_subtitle)
    val cancel = stringResource(R.string.cancel)

    MorrowPage {
        SubpageHeader(stringResource(R.string.settings_title), onBack)

        if (info.starterPin) {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                    .fillMaxWidth()
                    .background(c.card, RoundedCornerShape(16.dp))
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MorrowIcons.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.settings_starter_pin),
                    style = MorrowType.small,
                    color = c.ink,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                TextButton(onClick = onChangePin) {
                    Text(stringResource(R.string.settings_change), style = MorrowType.button, color = c.accent)
                }
            }
        }

        GroupLabel(stringResource(R.string.settings_security))
        SettingsGroup {
            NavRow(stringResource(R.string.settings_change_pin), stringResource(R.string.settings_change_pin_sub), onClick = onChangePin)
            HorizontalDivider(color = c.hairline)
            SwitchRow(
                label = stringResource(R.string.settings_fingerprint),
                subtitle = stringResource(
                    when {
                        !fingerprintAvailable -> R.string.settings_fingerprint_unavailable
                        fingerprintOn -> R.string.settings_fingerprint_on
                        else -> R.string.settings_fingerprint_off
                    },
                ),
                checked = fingerprintOn,
                enabled = fingerprintAvailable || fingerprintOn,
                onChange = { wantOn ->
                    if (!wantOn) {
                        viewModel.disableFingerprint()
                    } else {
                        scope.launch {
                            if (activity.enrollFingerprint(viewModel.lock, enrollTitle, enrollSubtitle, cancel)) viewModel.fingerprintEnabled()
                        }
                    }
                },
            )
            HorizontalDivider(color = c.hairline)
            NavRow(stringResource(R.string.settings_auto_lock), value = stringResource(s.autoLock.label()), onClick = { showAutoLock = true })
            HorizontalDivider(color = c.hairline)
            SwitchRow(
                label = stringResource(R.string.settings_hide_recents),
                subtitle = stringResource(R.string.settings_hide_recents_sub),
                checked = s.hideInRecents,
                onChange = viewModel::setHideInRecents,
            )
        }
        Text(
            stringResource(R.string.settings_security_note),
            style = MorrowType.caption,
            color = c.muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        )

        GroupLabel(stringResource(R.string.settings_display))
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_theme), style = MorrowType.bodyMedium, color = c.ink)
                ThemePicker(s.theme, viewModel::setTheme)
            }
            HorizontalDivider(color = c.hairline)
            SwitchRow(stringResource(R.string.settings_daily_quote), checked = s.showQuote, onChange = viewModel::setShowQuote)
            HorizontalDivider(color = c.hairline)
            SwitchRow(stringResource(R.string.settings_show_stats), checked = s.showStats, onChange = viewModel::setShowStats)
        }

        if (onAddSampleDays != null) {
            GroupLabel("Developer (debug builds only)")
            SettingsGroup {
                NavRow("Add sample days", "Fills the last 3 days with goals, plans and notes", onClick = onAddSampleDays)
            }
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

    if (showAutoLock) {
        AutoLockDialog(
            selected = s.autoLock,
            onSelect = {
                viewModel.setAutoLock(it)
                showAutoLock = false
            },
            onDismiss = { showAutoLock = false },
        )
    }
}

private fun AutoLock.label(): Int = when (this) {
    AutoLock.IMMEDIATELY -> R.string.auto_lock_immediately
    AutoLock.THIRTY_SECONDS -> R.string.auto_lock_30s
    AutoLock.ONE_MINUTE -> R.string.auto_lock_1m
    AutoLock.FIVE_MINUTES -> R.string.auto_lock_5m
}

@Composable
private fun AutoLockDialog(selected: AutoLock, onSelect: (AutoLock) -> Unit, onDismiss: () -> Unit) {
    val c = Morrow.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.accent) } },
        title = { Text(stringResource(R.string.settings_auto_lock), style = MorrowType.sectionTitle) },
        text = {
            Column(Modifier.selectableGroup()) {
                AutoLock.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.emptyRing),
                        )
                        Text(stringResource(option.label()), style = MorrowType.body, color = c.ink, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        containerColor = c.paper,
    )
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
private fun NavRow(label: String, subtitle: String? = null, value: String? = null, onClick: () -> Unit) {
    val c = Morrow.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MorrowType.bodyMedium, color = c.ink)
            subtitle?.let { Text(it, style = MorrowType.caption, color = c.muted, modifier = Modifier.padding(top = 2.dp)) }
        }
        value?.let { Text(it, style = MorrowType.small, color = c.muted, modifier = Modifier.padding(start = 8.dp)) }
        Icon(MorrowIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = Morrow.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MorrowType.bodyMedium, color = c.ink)
            subtitle?.let { Text(it, style = MorrowType.caption, color = c.muted, modifier = Modifier.padding(top = 2.dp)) }
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.emptyRing,
                uncheckedThumbColor = c.onAccent,
                uncheckedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.padding(start = 8.dp),
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
