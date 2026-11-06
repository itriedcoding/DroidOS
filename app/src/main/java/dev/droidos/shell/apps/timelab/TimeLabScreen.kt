package dev.droidos.shell.apps.timelab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TimeLabScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var zoneId by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var pickerOpen by remember { mutableStateOf(false) }
    val zone = remember(zoneId) { runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenScaffold(
        title = "Time lab",
        subtitle = "Zones, epochs and date maths",
        onBack = onBack,
        actions = {
            IconButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Rounded.Public, contentDescription = "Choose a time zone")
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Now", "Convert", "Difference").forEachIndexed { index, label ->
                    FilterChip(
                        selected = mode == index,
                        onClick = { mode = index },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            when (mode) {
                0 -> NowPanel(now, zone)
                1 -> ConvertPanel(zone)
                else -> DifferencePanel(zone)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (pickerOpen) {
            ZonePicker(
                selected = zone.id,
                onPick = {
                    zoneId = it
                    pickerOpen = false
                },
                onDismiss = { pickerOpen = false }
            )
        }
    }
}

@Composable
private fun NowPanel(now: Long, zone: ZoneId) {
    val clipboard = LocalClipboardManager.current
    val local = ZoneId.systemDefault()

    GlassCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        TimeMath.isoLabel(now, local).substringAfter(' '),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        TimeMath.weekdayLabel(now, local) + " - " + local.id,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ClockRow("UTC", TimeMath.isoLabel(now, ZoneId.of("UTC")), TimeMath.offsetLabel(ZoneId.of("UTC"), now))
            ClockRow(zone.id, TimeMath.isoLabel(now, zone), TimeMath.offsetLabel(zone, now))
            ClockRow("Epoch seconds", (now / 1000L).toString(), "")
            ClockRow("Epoch millis", now.toString(), "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString((now / 1000L).toString())) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Copy s", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(now.toString())) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Copy ms", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ConvertPanel(zone: ZoneId) {
    var input by remember { mutableStateOf((System.currentTimeMillis() / 1000L).toString()) }
    val parsed = remember(input, zone) { TimeMath.parseInstant(input, zone) }
    val local = ZoneId.systemDefault()

    GlassCard {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Epoch seconds, millis, ISO date or date-time") },
                singleLine = true,
                isError = input.isNotBlank() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    input.isBlank() -> "Ten-digit values are read as seconds, longer ones as milliseconds"
                    parsed == null -> "That is not a timestamp this app can read"
                    else -> "Read as ${if (input.trim().toLongOrNull() != null) "epoch" else "ISO-8601"}"
                },
                fontSize = 10.sp,
                color = if (parsed == null && input.isNotBlank()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (parsed != null) {
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockRow("UTC", TimeMath.isoTLabel(parsed, ZoneId.of("UTC")), "UTC")
                ClockRow(local.id, TimeMath.isoTLabel(parsed, local), TimeMath.offsetLabel(local, parsed))
                ClockRow(zone.id, TimeMath.isoTLabel(parsed, zone), TimeMath.offsetLabel(zone, parsed))
                val delta = parsed - System.currentTimeMillis()
                Text(
                    "That moment is ${TimeMath.humanDuration(delta)} " +
                        if (delta >= 0) "from now" else "in the past",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DifferencePanel(zone: ZoneId) {
    val today = LocalDate.now(zone)
    var from by remember { mutableStateOf(today.toString()) }
    var to by remember { mutableStateOf(today.plusDays(30).toString()) }
    val fromDate = TimeMath.parseDate(from)
    val toDate = TimeMath.parseDate(to)
    val days = if (fromDate != null && toDate != null) TimeMath.daysBetween(fromDate, toDate) else null

    GlassCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("From (YYYY-MM-DD)") },
                singleLine = true,
                isError = from.isNotBlank() && fromDate == null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To (YYYY-MM-DD)") },
                singleLine = true,
                isError = to.isNotBlank() && toDate == null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (days != null) {
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$days ${if (days == 1L || days == -1L) "day" else "days"}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${days / 7} weeks and ${days % 7} days - ${TimeMath.humanDuration(days * 86_400_000L)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "From ${fromDate} to ${toDate} in ${zone.id}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (from.isNotBlank() || to.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        MessageBanner("Both dates must be real calendar dates in YYYY-MM-DD form", isError = true)
    }
}

@Composable
private fun ClockRow(label: String, value: String, offset: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (offset.isNotEmpty()) {
                Text(offset, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ZonePicker(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val zones = remember(query) { TimeMath.searchZones(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Time zone", fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    items(zones) { id ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(id) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                id,
                                fontSize = 13.sp,
                                color = if (id == selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                TimeMath.offsetLabel(
                                    runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC")),
                                    Instant.now().toEpochMilli()
                                ),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}
