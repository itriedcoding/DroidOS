package dev.droidos.shell.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** The first-party applications that ship inside the shell. */
data class BuiltInApp(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val description: String
)

object BuiltInApps {
    val all: List<BuiltInApp> = listOf(
        BuiltInApp(
            Screen.FILES, "Files", Icons.Rounded.Folder, Color(0xFF5B8CFF),
            "Browse and manage documents"
        ),
        BuiltInApp(
            Screen.NOTES, "Notes", Icons.AutoMirrored.Rounded.Notes, Color(0xFF3BD6A6),
            "Write and search notes"
        ),
        BuiltInApp(
            Screen.VAULT, "Vault", Icons.Rounded.Lock, Color(0xFFA87BFF),
            "Encrypted private storage"
        ),
        BuiltInApp(
            Screen.CLOCK, "Clock", Icons.Rounded.Alarm, Color(0xFFFF9F6B),
            "Alarms, timer and stopwatch"
        ),
        BuiltInApp(
            Screen.SCANNER, "Scan", Icons.Rounded.BugReport, Color(0xFFFFB454),
            "Check installed apps for risk"
        ),
        BuiltInApp(
            Screen.CALCULATOR, "Calculator", Icons.Rounded.Calculate, Color(0xFF9BA8C2),
            "Offline expression calculator"
        ),
        BuiltInApp(
            Screen.FIREWALL, "Firewall", Icons.Rounded.Shield, Color(0xFFFF7C9C),
            "Per-app network control"
        ),
        BuiltInApp(
            Screen.PRIVACY, "Privacy", Icons.Rounded.Visibility, Color(0xFF6FE3FF),
            "Live sensor indicators"
        ),
        BuiltInApp(
            Screen.TERMINAL, "Terminal", Icons.Rounded.Terminal, Color(0xFFFF2D78),
            "Real shell in the app sandbox"
        ),
        BuiltInApp(
            Screen.SENSORS, "Sensors", Icons.Rounded.Sensors, Color(0xFFFF9EC4),
            "Live readings from device sensors"
        ),
        BuiltInApp(
            Screen.CONVERT, "Convert", Icons.Rounded.Straighten, Color(0xFF7CE38B),
            "Length, mass, temperature, data, speed, time"
        ),
        BuiltInApp(
            Screen.DIGEST, "Digest", Icons.Rounded.Fingerprint, Color(0xFF9FE8FF),
            "SHA digests, Base64 and UUIDs"
        ),
        BuiltInApp(
            Screen.TEXT_TOOLS, "Text tools", Icons.Rounded.TextFields, Color(0xFFFFC76B),
            "Counts, case changes, regex replace"
        ),
        BuiltInApp(
            Screen.COMPASS, "Compass", Icons.Rounded.Explore, Color(0xFFFFB0CF),
            "Heading from the rotation vector"
        ),
        BuiltInApp(
            Screen.NETCHECK, "Netcheck", Icons.Rounded.NetworkCheck, Color(0xFF8AFFD6),
            "Real DNS, TCP and HTTPS probes"
        ),
        BuiltInApp(
            Screen.LEDGER, "Ledger", Icons.Rounded.AccountBalanceWallet, Color(0xFFC3A6FF),
            "Local income and spending totals"
        ),
        BuiltInApp(
            Screen.CLEANER, "Cleaner", Icons.Rounded.CleaningServices, Color(0xFF6FE3FF),
            "Real cache purge and media audit"
        ),
        BuiltInApp(
            Screen.TORCH, "Torch", Icons.Rounded.FlashlightOn, Color(0xFFFFE082),
            "Switch the camera flash directly"
        ),
        BuiltInApp(
            Screen.LEVEL, "Level", Icons.Rounded.Straighten, Color(0xFF9BFFD9),
            "Bubble level from the gravity sensor"
        ),
        BuiltInApp(
            Screen.TIMELAB, "Time lab", Icons.Rounded.Public, Color(0xFF8FB4FF),
            "Zones, epochs and date maths"
        ),
        BuiltInApp(
            Screen.CIPHER, "Cipher", Icons.Rounded.Lock, Color(0xFFC3A6FF),
            "AES-256-GCM text encryption"
        ),
        BuiltInApp(
            Screen.SPEEDTEST, "Speed test", Icons.Rounded.Speed, Color(0xFFFFB0CF),
            "Measured download throughput"
        ),
        BuiltInApp(
            Screen.GENERATOR, "Generator", Icons.Rounded.Casino, Color(0xFF7CE38B),
            "Passwords, dice and coins"
        ),
        BuiltInApp(
            Screen.SETTINGS, "Settings", Icons.Rounded.Settings, Color(0xFF8FB4FF),
            "System preferences"
        )
    )

    fun forScreen(screen: Screen): BuiltInApp? = all.firstOrNull { it.screen == screen }
}
