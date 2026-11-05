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
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.firewall.FirewallStatus
import dev.droidos.shell.shell.BuiltInApp
import dev.droidos.shell.shell.BuiltInApps
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.system.DeviceStatsRepository
import dev.droidos.shell.ui.components.DroidRadius
import dev.droidos.shell.ui.components.DroidSpacing
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.RingStat
import dev.droidos.shell.ui.components.RowDivider
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import dev.droidos.shell.ui.components.StatBar
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HOME_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")

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
    var clock by remember { mutableStateOf("") }
    var dateLine by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        ServiceLocator.privacy.start()
        onDispose { ServiceLocator.privacy.stop() }
    }

    LaunchedEffect(Unit) {
        val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
        while (true) {
            val now = LocalTime.now()
            clock = Fmt.clockTime(now.hour, now.minute, use24Hour)
            dateLine = LocalDate.now().format(HOME_DATE)
            network = stats.network()
            delay(5_000)
        }
    }

    val favourites = listOf(Screen.FILES, Screen.NOTES, Screen.VAULT, Screen.CLOCK)
        .mapNotNull { BuiltInApps.forScreen(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = DroidSpacing.xl)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    clock,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1.6).sp
                )
                Text(
                    dateLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            RoundIconButton(Icons.Rounded.Notifications, "Open notifications", onOpenShade)
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Icons.Rounded.Lock, "Lock device", onLock)
        }

        Spacer(Modifier.height(18.dp))

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
                        size = 84,
                        strokeWidth = 8
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (network.validated) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                network.transport,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (network.validated) "online" else "offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MeterRow("Storage", storage.usedFraction, MaterialTheme.colorScheme.primary)
                        MeterRow("Memory", memory.usedFraction, MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Spacer(Modifier.height(DroidSpacing.md))

            SearchPill { onOpenScreen(Screen.SEARCH) }

            SectionLabel("Favourites")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DroidSpacing.md)
            ) {
                favourites.forEach { app ->
                    FavouriteTile(app, Modifier.weight(1f)) { onOpenScreen(app.screen) }
                }
            }

            SectionLabel("Status")
            GlassCard {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Visibility,
                        title = when {
                            privacy.cameraInUse && privacy.microphoneInUse -> "Camera and microphone in use"
                            privacy.cameraInUse -> "Camera in use"
                            privacy.microphoneInUse -> "Microphone in use"
                            privacy.microphoneMuted -> "Microphone muted"
                            else -> "No sensor activity"
                        },
                        subtitle = "Reported live by the camera and audio services",
                        tint = if (privacy.cameraInUse || privacy.microphoneInUse) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        onClick = { onOpenScreen(Screen.PRIVACY) }
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Security,
                        title = if (firewallRunning) "Firewall active" else "Firewall off",
                        subtitle = "${settings.firewallBlocked.size} apps blocked from the network",
                        tint = if (firewallRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onOpenScreen(Screen.FIREWALL) }
                    )
                }
            }

            Spacer(Modifier.height(DroidSpacing.xl))
        }

        DockBar(
            onOpenDrawer = onOpenDrawer,
            onOpenRecents = onOpenRecents,
            onOpenShade = onOpenShade,
            onOpenSearch = { onOpenScreen(Screen.SEARCH) },
            onOpenSettings = { onOpenScreen(Screen.SETTINGS) }
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MeterRow(label: String, fraction: Float, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(Fmt.percent(fraction), style = MaterialTheme.typography.labelSmall)
        }
        StatBar(fraction = fraction, color = tint)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DroidRadius.md))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Search apps, notes and files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FavouriteTile(app: BuiltInApp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DroidRadius.md))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(DroidRadius.md))
                .background(
                    Brush.linearGradient(
                        listOf(app.tint.copy(alpha = 0.32f), app.tint.copy(alpha = 0.07f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                app.icon,
                contentDescription = app.label,
                tint = app.tint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DockBar(
    onOpenDrawer: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenShade: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DroidRadius.xl))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(Icons.Rounded.Apps, "All apps", onOpenDrawer)
        DockItem(Icons.Rounded.History, "Recents", onOpenRecents)
        DockItem(Icons.Rounded.Search, "Search", onOpenSearch)
        DockItem(Icons.Rounded.Notifications, "Notifications", onOpenShade)
        DockItem(Icons.Rounded.Security, "Settings", onOpenSettings)
    }
}

@Composable
private fun DockItem(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(23.dp)
        )
    }
}
