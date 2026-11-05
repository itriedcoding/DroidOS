package dev.droidos.shell.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
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
            Screen.SETTINGS, "Settings", Icons.Rounded.Settings, Color(0xFF8FB4FF),
            "System preferences"
        )
    )

    fun forScreen(screen: Screen): BuiltInApp? = all.firstOrNull { it.screen == screen }
}
