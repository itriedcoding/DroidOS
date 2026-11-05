package dev.droidos.shell.shell.shade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.core.Fmt
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.ThemeMode
import dev.droidos.shell.shell.Screen
import dev.droidos.shell.shell.controls.DisplayControls
import dev.droidos.shell.shell.controls.TorchController
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Notification shade with genuine quick settings and the live notification list. */
@Composable
fun Shade(
    onClose: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val controls = remember { ServiceLocator.controls }
    val display = remember { DisplayControls(context) }
    val torch = remember { TorchController(context) }
    val notifications = ServiceLocator.notificationCenter
    val items by notifications.items.collectAsStateWithLifecycle()
    val connected by notifications.connected.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var wifi by remember { mutableStateOf(controls.isWifiEnabled()) }
    var bluetooth by remember { mutableStateOf(controls.isBluetoothEnabled()) }
    var dnd by remember { mutableStateOf(controls.isDndEnabled()) }
    var rotationLocked by remember { mutableStateOf(!display.isAutoRotateEnabled()) }
    val torchOn by torch.isOn.collectAsStateWithLifecycle()
    var brightness by remember { mutableFloatStateOf(display.brightness() / 255f) }
    var message by remember { mutableStateOf<String?>(null) }
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(
        initialValue = dev.droidos.shell.data.model.AppSettings()
    )

    DisposableEffect(Unit) {
        torch.startObserving()
        onDispose { torch.stopObserving() }
    }

    // Poll real system state so the tiles never drift from the OS.
    LaunchedEffect(Unit) {
        while (true) {
            wifi = controls.isWifiEnabled()
            bluetooth = controls.isBluetoothEnabled()
            dnd = controls.isDndEnabled()
            rotationLocked = !display.isAutoRotateEnabled()
            brightness = display.brightness() / 255f
            delay(2000)
        }
    }

    fun report(result: dev.droidos.shell.shell.controls.ControlResult) {
        val action = result.needsUserAction
        if (action != null) {
            runCatching { context.startActivity(action) }
        }
        if (!result.applied && result.message != null) message = result.message
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                RoundButton(Icons.Rounded.Close, "Close shade", onClose)
            }

            if (message != null) {
                MessageBanner(message.orEmpty())
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Rounded.Wifi, "Wi-Fi", wifi, Modifier.weight(1f)) {
                    report(controls.setWifi(!wifi))
                    wifi = controls.isWifiEnabled()
                }
                Tile(Icons.Rounded.Bluetooth, "Bluetooth", bluetooth, Modifier.weight(1f)) {
                    report(controls.setBluetooth(!bluetooth))
                    bluetooth = controls.isBluetoothEnabled()
                }
                Tile(Icons.Rounded.FlashlightOn, "Torch", torchOn, Modifier.weight(1f)) {
                    report(torch.toggle())
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Tile(Icons.Rounded.DoNotDisturbOn, "DND", dnd, Modifier.weight(1f)) {
                    report(controls.setDnd(!dnd))
                    dnd = controls.isDndEnabled()
                }
                Tile(Icons.Rounded.ScreenRotation, "Rotation lock", rotationLocked, Modifier.weight(1f)) {
                    report(display.setRotationLocked(!rotationLocked))
                    rotationLocked = !display.isAutoRotateEnabled()
                }
                Tile(
                    Icons.Rounded.DarkMode,
                    "Dark theme",
                    settings.themeMode != ThemeMode.LIGHT,
                    Modifier.weight(1f)
                ) {
                    scope.launch {
                        ServiceLocator.settings.setThemeMode(
                            if (settings.themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Brightness", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp))
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = { report(display.setBrightness((brightness * 255).toInt())) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("Notifications")

            Box(Modifier.weight(1f)) {
                when {
                    !connected -> EmptyState(
                        icon = Icons.Rounded.NotificationsOff,
                        title = "Notification access is off",
                        message = "DroidOS needs notification listener access to show and dismiss your notifications.",
                        actionLabel = if (notifications.hasAccess()) "Reconnect" else "Open access settings",
                        onAction = {
                            if (notifications.hasAccess()) {
                                message = "Access is granted - the listener reconnects automatically"
                            } else {
                                runCatching { context.startActivity(notifications.accessSettingsIntent()) }
                            }
                        }
                    )

                    items.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.NotificationsOff,
                        title = "No notifications",
                        message = "New notifications from your apps will appear here."
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.key }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.appLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            Fmt.relativeTime(item.postedAt, System.currentTimeMillis()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (item.title.isNotBlank()) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (item.text.isNotBlank()) {
                                        Text(
                                            item.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (item.clearable) {
                                    RoundButton(Icons.Rounded.Close, "Dismiss") {
                                        notifications.dismiss(item.key)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FooterButton("Clear all", Icons.Rounded.NotificationsOff, Modifier.weight(1f)) {
                    notifications.dismissAll()
                }
                FooterButton("Settings", Icons.Rounded.Settings, Modifier.weight(1f)) {
                    onOpenScreen(Screen.SETTINGS)
                }
                FooterButton("Lock", Icons.Rounded.Lock, Modifier.weight(1f)) { onLock() }
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) tint.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FooterButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
