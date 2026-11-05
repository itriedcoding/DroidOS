package dev.droidos.shell.shell.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.firewall.FirewallStatus
import dev.droidos.shell.shell.BuiltInApps
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.system.DeviceStatsRepository
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.IconBadge
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.StatBar
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HOME_TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    settings: AppSettings,
    onOpenDrawer: () -> Unit,
    onOpenShade: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenRecents: () -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val stats = remember { ServiceLocator.deviceStats }
    val battery by stats.batteryFlow().collectAsStateWithLifecycle(initialValue = DeviceStatsRepository.Battery())
    val privacy by ServiceLocator.privacy.state.collectAsStateWithLifecycle()
    val firewallRunning by FirewallStatus.running.collectAsStateWithLifecycle()

    var network by remember { mutableStateOf(stats.network()) }
    val storage = remember { stats.storage() }
    val memory = remember { stats.memory() }
    var clock by remember { mutableStateOf(LocalTime.now().format(HOME_TIME)) }
    var dateLine by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        ServiceLocator.privacy.start()
        onDispose { ServiceLocator.privacy.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now().format(HOME_TIME)
            dateLine = java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
            network = stats.network()
            delay(5_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    clock,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp
                )
                Text(
                    dateLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            RoundIconButton(Icons.Rounded.Notifications, "Open notifications", onOpenShade)
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Icons.Rounded.Lock, "Lock device", onLock)
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingStat(
                        fraction = battery.percent / 100f,
                        value = "${battery.percent}%",
                        caption = if (battery.charging) "charging" else "battery",
                        size = 96,
                        strokeWidth = 9
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusLine(
                            "${network.transport}",
                            if (network.validated) "Connected" else "No internet",
                            if (network.validated) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        StatBar(fraction = storage.usedFraction)
                        Text(
                            "Storage ${Fmt.percent(storage.usedFraction)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatBar(
                            fraction = memory.usedFraction,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Memory ${Fmt.percent(memory.usedFraction)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel("Live privacy")
            GlassCard(onClick = { onOpenScreen(Screen.PRIVACY) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        Icons.Rounded.Visibility,
                        if (privacy.cameraInUse || privacy.microphoneInUse) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                privacy.cameraInUse && privacy.microphoneInUse -> "Camera and microphone in use"
                                privacy.cameraInUse -> "Camera in use"
                                privacy.microphoneInUse -> "Microphone in use"
                                privacy.microphoneMuted -> "Microphone muted"
                                else -> "No sensor activity"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Signals reported by the Android camera and audio services",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel("Security")
            GlassCard(onClick = { onOpenScreen(Screen.FIREWALL) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Security, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (firewallRunning) "Firewall active" else "Firewall off",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "${settings.firewallBlocked.size} apps blocked from the network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Manage",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Favourites")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BuiltInApps.all.take(4).forEach { app ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenScreen(app.screen) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(app.tint.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                app.icon,
                                contentDescription = app.label,
                                tint = app.tint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        DockBar(
            onOpenDrawer = onOpenDrawer,
            onOpenRecents = onOpenRecents,
            onOpenShade = onOpenShade,
            onOpenSettings = { onOpenScreen(Screen.SETTINGS) }
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StatusLine(title: String, subtitle: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DockBar(
    onOpenDrawer: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenShade: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(Icons.Rounded.Apps, "All apps", onOpenDrawer)
        DockItem(Icons.Rounded.History, "Recents", onOpenRecents)
        DockItem(Icons.Rounded.Notifications, "Notifications", onOpenShade)
        DockItem(Icons.Rounded.Security, "Settings", onOpenSettings)
    }
}

@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp)
        )
    }
}
