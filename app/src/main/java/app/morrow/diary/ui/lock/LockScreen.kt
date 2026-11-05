package app.morrow.diary.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.R
import app.morrow.diary.data.prefs.MorrowSettings
import app.morrow.diary.domain.DayClock
import app.morrow.diary.domain.QuoteOfTheDay
import app.morrow.diary.security.LockManager
import app.morrow.diary.security.PinResult
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

private sealed interface LockMessage {
    data object Idle : LockMessage
    data class Wrong(val triesLeft: Int) : LockMessage
    data object FingerprintError : LockMessage
}

@Composable
fun LockScreen(lock: LockManager<*>, settings: MorrowSettings, dayClock: DayClock) {
    val c = Morrow.colors
    val info by lock.info.collectAsStateWithLifecycle()
    val lockCount by lock.lockCount.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as FragmentActivity
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<LockMessage>(LockMessage.Idle) }
    var prompting by remember { mutableStateOf(false) }
    var showForgotHelp by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    // Ticks while a wrong-PIN wait is running, so the countdown updates.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info.lockedOutUntilMillis) {
        while (System.currentTimeMillis() < info.lockedOutUntilMillis) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val lockedOut = info.lockedOutUntilMillis > now
    val fingerprintReady = settings.fingerprintUnlock && info.hasFingerprint && remember(lockCount) { canUseFingerprint(context) }

    val promptTitle = stringResource(R.string.fingerprint_unlock_title)
    val promptNegative = stringResource(R.string.fingerprint_use_pin)
    val resetTitle = stringResource(R.string.fingerprint_reset_title)
    val resetSubtitle = stringResource(R.string.fingerprint_reset_subtitle)
    val cancel = stringResource(R.string.cancel)

    fun askForFingerprint(thenResetPin: Boolean) {
        if (prompting) return
        prompting = true
        scope.launch {
            try {
                val cipher = lock.fingerprintUnlockCipher() ?: return@launch // key gone: info now says why
                val outcome = activity.askForFingerprint(
                    lock = lock,
                    cipher = cipher,
                    title = if (thenResetPin) resetTitle else promptTitle,
                    subtitle = if (thenResetPin) resetSubtitle else null,
                    negativeButton = if (thenResetPin) cancel else promptNegative,
                )
                when (outcome) {
                    is FingerprintOutcome.Success -> lock.unlockWithFingerprint(outcome.cipher, thenResetPin)
                    is FingerprintOutcome.Error -> message = LockMessage.FingerprintError
                    FingerprintOutcome.Cancelled -> Unit
                }
            } finally {
                prompting = false
            }
        }
    }

    // Offer the fingerprint as soon as the lock screen is in front of the user: on open and on return.
    val latestReady by rememberUpdatedState(fingerprintReady)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lockCount) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && latestReady) askForFingerprint(thenResetPin = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun onDigit(digit: Char) {
        if (busy || lockedOut || !info.ready || pin.length >= info.pinLength) return
        pin += digit
        message = LockMessage.Idle
        if (pin.length < info.pinLength) return
        busy = true
        scope.launch {
            when (val result = lock.unlockWithPin(pin)) {
                PinResult.Success -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                is PinResult.Wrong -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    message = LockMessage.Wrong(result.triesLeft)
                    shake.wrongPinShake()
                    pin = ""
                }
                is PinResult.LockedOut -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    message = LockMessage.Idle
                    shake.wrongPinShake()
                    pin = ""
                }
            }
            busy = false
        }
    }

    val quote = remember(settings) {
        if (!settings.showQuote) null
        else QuoteOfTheDay.pick(DayClock.logicalDate(dayClock.now(), settings.dayStartsAt), settings.quotes)
    }
    val dayName = remember(settings) {
        DayClock.logicalDate(dayClock.now(), settings.dayStartsAt).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.app_name), style = MorrowType.sectionTitle, color = c.muted)

        if (quote != null) {
            Column(
                Modifier.padding(top = 20.dp).widthIn(max = 340.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(30.dp)) {
                    Text(
                        "“",
                        style = MorrowType.quoteMark,
                        color = c.accent,
                        modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).clearAndSetSemantics { },
                    )
                }
                Text(quote, style = MorrowType.quote, color = c.ink, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.lock_quote_caption, dayName).uppercase(),
                    style = MorrowType.overline,
                    color = c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (fingerprintReady) {
            Box(
                Modifier
                    .padding(top = 32.dp)
                    .size(84.dp)
                    .background(c.inputSurface, CircleShape)
                    .border(1.5.dp, c.hairline, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = promptTitle) { askForFingerprint(thenResetPin = false) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MorrowIcons.Fingerprint, contentDescription = promptTitle, tint = c.accent, modifier = Modifier.size(40.dp))
            }
            Text(stringResource(R.string.lock_touch_sensor), style = MorrowType.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp))
        } else if (settings.fingerprintUnlock && info.fingerprintInvalidated) {
            Text(
                stringResource(R.string.lock_fingerprint_invalidated),
                style = MorrowType.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp).widthIn(max = 300.dp),
            )
        }

        PinDots(total = info.pinLength, filled = pin.length, shake = shake, modifier = Modifier.padding(top = 30.dp))

        Box(
            Modifier.heightIn(min = 44.dp).padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            val (text, color) = when {
                lockedOut -> stringResource(R.string.lock_locked_out, formatWait(info.lockedOutUntilMillis - now)) to c.accent
                message is LockMessage.Wrong -> pluralWrong((message as LockMessage.Wrong).triesLeft) to c.accent
                message is LockMessage.FingerprintError -> stringResource(R.string.lock_fingerprint_error) to c.accent
                fingerprintReady -> stringResource(R.string.lock_or_enter_pin) to c.muted
                else -> stringResource(R.string.lock_enter_pin) to c.muted
            }
            Text(text, style = MorrowType.small, color = color, textAlign = TextAlign.Center)
        }

        PinKeypad(
            onDigit = ::onDigit,
            onDelete = { if (!busy) pin = pin.dropLast(1) },
            enabled = info.ready && !lockedOut && !busy,
            modifier = Modifier.padding(top = 6.dp),
        )

        TextButton(
            onClick = { if (fingerprintReady) askForFingerprint(thenResetPin = true) else showForgotHelp = true },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.lock_forgot_pin), style = MorrowType.small, color = c.accent)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MorrowIcons.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.lock_encrypted), style = MorrowType.caption, color = c.muted, modifier = Modifier.padding(start = 6.dp))
        }
    }

    if (showForgotHelp) {
        AlertDialog(
            onDismissRequest = { showForgotHelp = false },
            confirmButton = { TextButton(onClick = { showForgotHelp = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.forgot_title), style = MorrowType.sectionTitle) },
            text = { Text(stringResource(R.string.forgot_body), style = MorrowType.body) },
            containerColor = c.paper,
        )
    }
}

@Composable
private fun pluralWrong(triesLeft: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(R.plurals.lock_wrong_pin, triesLeft, triesLeft)
}

private fun formatWait(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
