package dev.droidos.shell.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.ui.BiometricRequest
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun LockScreen(viewModel: ShellViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val error by viewModel.unlockError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var pin by remember { mutableStateOf("") }
    var showKeypad by remember { mutableStateOf(!settings.biometricEnabled) }
    var biometricTrigger by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    // Offer biometrics as soon as the lock screen appears, falling back to the PIN pad.
    LaunchedEffect(settings.biometricEnabled) {
        if (settings.biometricEnabled) {
            biometricTrigger += 1
        } else {
            showKeypad = true
        }
    }

    BiometricRequest(
        trigger = biometricTrigger,
        title = "Unlock DroidOS",
        subtitle = "Confirm your identity"
    ) { success, message ->
        if (success) {
            viewModel.onBiometricSuccess()
        } else {
            showKeypad = true
            if (message != null && !message.contains("cancel", ignoreCase = true)) {
                // Hardware/enrolment problems are worth showing; a user cancel is not.
                if (message.contains("Enrol", true) || message.contains("hardware", true)) {
                    viewModel.clearUnlockError()
                }
            }
        }
    }

    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = Fmt.clockTime(now.hour, now.minute, use24Hour),
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-2).sp
            )
            Text(
                text = DATE_FORMAT.format(now),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { index ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            if (showKeypad) {
                Keypad(
                    onDigit = { digit ->
                        if (pin.length < 12) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pin += digit
                            viewModel.clearUnlockError()
                        }
                    },
                    onBackspace = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        pin = pin.dropLast(1)
                    },
                    onBiometric = if (settings.biometricEnabled) {
                        { biometricTrigger += 1 }
                    } else null
                )
                Spacer(Modifier.height(18.dp))
                UnlockButton(enabled = pin.length >= 4) {
                    val attempt = pin
                    pin = ""
                    viewModel.submitPin(attempt)
                }
            } else {
                Text(
                    text = "Tap the sensor or enter your PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { showKeypad = true }
                )
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit -> KeypadKey(digit) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBiometric != null) {
                KeypadIcon(Icons.Rounded.Fingerprint, "Use biometrics", onBiometric)
            } else {
                Spacer(Modifier.size(72.dp))
            }
            KeypadKey("0") { onDigit("0") }
            KeypadIcon(Icons.Rounded.Backspace, "Delete", onBackspace)
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun KeypadIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
private fun UnlockButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Unlock",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
