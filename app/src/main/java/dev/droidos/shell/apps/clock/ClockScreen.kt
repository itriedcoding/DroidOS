package dev.droidos.shell.apps.clock

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.ui.components.DroidRadius
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

private val WEEK = listOf(
    Calendar.MONDAY to "M",
    Calendar.TUESDAY to "T",
    Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T",
    Calendar.FRIDAY to "F",
    Calendar.SATURDAY to "S",
    Calendar.SUNDAY to "S"
)

@Composable
fun ClockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ClockRepository(context) }
    var tab by remember { mutableIntStateOf(0) }
    var alarms by remember { mutableStateOf(repository.alarms()) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(repository.canScheduleExact()) }

    fun refresh() {
        alarms = repository.alarms()
        exactAllowed = repository.canScheduleExact()
    }

    ScreenScaffold(
        title = "Clock",
        subtitle = if (alarms.isEmpty()) "No alarms set" else "${alarms.count { it.enabled }} of ${alarms.size} alarms on",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Segmented(listOf("Alarms", "Timer", "Stopwatch"), tab) { tab = it }
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                MessageBanner(message.orEmpty())
            }
            Spacer(Modifier.height(14.dp))

            when (tab) {
                0 -> AlarmsTab(
                    alarms = alarms,
                    exactAllowed = exactAllowed,
                    onGrantExact = {
                        runCatching { context.startActivity(repository.exactAlarmSettingsIntent()) }
                        message = "Allow exact alarms, then enable the alarm again"
                    },
                    onCreate = { creating = true },
                    onEdit = { editing = it },
                    onToggle = { alarm, enabled ->
                        repository.upsert(alarm.copy(enabled = enabled))
                        refresh()
                        val scheduled = repository.alarms().firstOrNull { it.id == alarm.id }
                        message = when {
                            !enabled -> null
                            scheduled == null -> null
                            !exactAllowed -> "Alarm saved, but Android blocks exact alarms until you allow them"
                            else -> "Alarm set for ${alarm.timeLabel()}"
                        }
                    }
                )

                1 -> TimerTab(repository) { message = it }
                else -> StopwatchTab()
            }
        }
    }

    if (creating || editing != null) {
        AlarmEditor(
            initial = editing,
            nextId = (alarms.maxOfOrNull { it.id } ?: 0) + 1,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { alarm ->
                repository.upsert(alarm)
                refresh()
                message = if (repository.canScheduleExact()) {
                    "Alarm set for ${alarm.timeLabel()}"
                } else {
                    "Saved, but Android blocks exact alarms until you allow them"
                }
                creating = false
                editing = null
            },
            onDelete = editing?.let { existing ->
                {
                    repository.delete(existing.id)
                    refresh()
                    message = "Alarm deleted"
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DroidRadius.md))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlarmsTab(
    alarms: List<Alarm>,
    exactAllowed: Boolean,
    onGrantExact: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (!exactAllowed) {
            GlassCard {
                Column {
                    Text("Exact alarms are blocked", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Android will not deliver an alarm at an exact minute until this app is allowed to schedule them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onGrantExact) { Text("Allow exact alarms") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (alarms.isEmpty()) {
                item {
                    GlassCard {
                        Column {
                            Text("No alarms", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Alarms are scheduled with the system alarm service, so they ring even when the shell is closed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                GlassCard(onClick = { onEdit(alarm) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                alarm.timeLabel(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp,
                                color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                listOf(alarm.repeatLabel(), alarm.label.ifBlank { "Alarm" })
                                    .filter { it.isNotBlank() }
                                    .joinToString("  \u00b7  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { onToggle(alarm, it) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(DroidRadius.md))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .clickable { onCreate() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Add alarm",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TimerTab(repository: ClockRepository, onMessage: (String) -> Unit) {
    var minutes by remember { mutableIntStateOf(5) }
    var endAt by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableLongStateOf(0L) }

    LaunchedEffect(endAt) {
        while (true) {
            val target = endAt ?: break
            val left = (target - System.currentTimeMillis()).coerceAtLeast(0L)
            remaining = left
            if (left == 0L) {
                endAt = null
                break
            }
            delay(200)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    StopwatchFormat.elapsed(remaining),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    if (endAt == null) "Ready" else "Ringing when it reaches zero",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel("Duration")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(1, 5, 10, 25, 45).forEach { preset ->
                val active = minutes == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                        .clickable(enabled = endAt == null) { minutes = preset }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${preset}m",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DroidRadius.md))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .clickable {
                    if (endAt != null) {
                        repository.cancelTimer()
                        endAt = null
                        remaining = 0
                        onMessage("Timer cancelled")
                    } else {
                        val target = System.currentTimeMillis() + minutes * 60_000L
                        when (val result = repository.scheduleTimer(target, "Timer \u00b7 $minutes minutes")) {
                            is Outcome.Ok -> {
                                endAt = target
                                onMessage("Timer started for $minutes minutes")
                            }
                            is Outcome.Err -> onMessage(result.message)
                        }
                    }
                }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (endAt == null) "Start timer" else "Cancel timer",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "The countdown is also handed to the system alarm service, so it completes even if the shell is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun StopwatchTab() {
    var state by remember { mutableStateOf(StopwatchState()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.running) {
        while (state.running) {
            now = System.currentTimeMillis()
            delay(33)
        }
    }
    val elapsed = state.elapsed(now)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    StopwatchFormat.elapsed(elapsed),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    if (state.running) "Running" else if (elapsed > 0) "Paused" else "Ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                label = if (state.running) "Pause" else "Start",
                modifier = Modifier.weight(1f)
            ) {
                now = System.currentTimeMillis()
                state = if (state.running) state.stop(now) else state.start(now)
            }
            ActionButton(
                label = "Lap",
                enabled = state.running,
                modifier = Modifier.weight(1f)
            ) {
                now = System.currentTimeMillis()
                state = state.lap(now)
            }
            ActionButton(
                label = "Reset",
                enabled = elapsed > 0 && !state.running,
                modifier = Modifier.weight(1f)
            ) {
                state = state.reset()
                now = System.currentTimeMillis()
            }
        }

        if (state.laps.isNotEmpty()) {
            SectionLabel("Laps")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.laps.reversed().forEachIndexed { index, lap ->
                        val number = state.laps.size - index
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Lap $number",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                StopwatchFormat.elapsed(lap),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DroidRadius.md))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    initial: Alarm?,
    nextId: Int,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = java.time.LocalTime.now()
    val picker = rememberTimePickerState(
        initialHour = initial?.hour ?: now.hour,
        initialMinute = initial?.minute ?: now.minute,
        is24Hour = true
    )
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var days by remember { mutableStateOf(initial?.days.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New alarm" else "Edit alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimePicker(state = picker)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK.forEach { (day, letter) ->
                        val active = day in days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                                )
                                .clickable {
                                    days = if (active) days - day else days + day
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    if (days.isEmpty()) "Rings once" else "Repeats ${days.size} day(s) a week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Alarm(
                            id = initial?.id ?: nextId,
                            hour = picker.hour,
                            minute = picker.minute,
                            label = label.trim(),
                            days = days,
                            enabled = true
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun String.ifBlank(fallback: String): String =
    if (isBlank()) fallback else this
