package dev.droidos.shell.settings

import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.security.PackageFacts
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.IconBadge
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Location cannot legally be toggled by a launcher, so this screen reports the real state from
 * LocationManager, lists the packages that actually declare location permissions, and hands the
 * switch itself to the platform panel.
 */
@Composable
fun LocationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }

    var enabled by remember { mutableStateOf(runCatching { locationManager?.isLocationEnabled == true }.getOrDefault(false)) }
    var gpsEnabled by remember {
        mutableStateOf(runCatching { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false))
    }
    var networkEnabled by remember {
        mutableStateOf(runCatching { locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }.getOrDefault(false))
    }
    var holders by remember { mutableStateOf<List<PackageFacts>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        holders = withContext(Dispatchers.IO) {
            ServiceLocator.apps.allFacts()
                .filter { facts ->
                    facts.requestedPermissions.any {
                        it.endsWith("ACCESS_FINE_LOCATION") || it.endsWith("ACCESS_COARSE_LOCATION")
                    }
                }
                .sortedBy { it.label.lowercase() }
        }
        enabled = runCatching { locationManager?.isLocationEnabled == true }.getOrDefault(false)
        gpsEnabled = runCatching { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)
        networkEnabled = runCatching { locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }.getOrDefault(false)
        loading = false
    }

    ScreenScaffold(
        title = "Location",
        subtitle = "Access state and which apps can ask for it",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            Icons.Rounded.MyLocation,
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (enabled) "Location is on" else "Location is off",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                when {
                                    !enabled -> "No app can obtain a fix until it is enabled"
                                    gpsEnabled && networkEnabled -> "GPS and network providers are both available"
                                    gpsEnabled -> "GPS provider is available, network provider is off"
                                    networkEnabled -> "Network provider is available, GPS is off"
                                    else -> "Location is enabled but no provider is currently available"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingRow(
                        icon = Icons.Rounded.Settings,
                        title = "Open Android location settings",
                        subtitle = "Android reserves the location switch for the system, so this hands you the official panel",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        trailing = { Chevron() }
                    )
                }
            }

            item { SectionLabel("Apps that declare location access") }
            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (holders.isEmpty()) {
                item {
                    GlassCard {
                        Column {
                            Text("No installed app declares location", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Nothing on this device requests fine or coarse location access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(holders, key = { it.packageName }) { facts ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Icons.Rounded.LocationOn, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                facts.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    append(if (facts.isSystem) "System app" else "Installed app")
                                    if (facts.requestedPermissions.any { it.endsWith("ACCESS_FINE_LOCATION") }) {
                                        append("  \u00b7  precise and approximate")
                                    } else {
                                        append("  \u00b7  approximate only")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
