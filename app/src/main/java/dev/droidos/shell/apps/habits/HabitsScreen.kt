package dev.droidos.shell.apps.habits

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.StatBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun HabitsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { HabitRepository(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var habits by remember { mutableStateOf<List<HabitRepository.Habit>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { repository.close() }
    }

    LaunchedEffect(revision) {
        habits = withContext(Dispatchers.IO) { repository.habits() }
    }

    val doneToday = habits.count { it.days.contains(today) }

    ScreenScaffold(
        title = "Habits",
        subtitle = HabitEngine.summary(habits.size, doneToday),
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("New habit") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val problem = HabitEngine.validateName(draft)
                        if (problem != null) {
                            error = problem
                            return@Button
                        }
                        val name = HabitEngine.normaliseName(draft)
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.add(name) }
                            draft = ""
                            error = null
                            revision++
                        }
                    },
                    enabled = draft.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            if (habits.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                StatBar(
                    fraction = if (habits.isEmpty()) 0f else doneToday.toFloat() / habits.size,
                    height = 5
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                MessageBanner(it, isError = true)
            }

            SectionLabel(if (habits.isEmpty()) "Today" else "Tap a habit to check it off")

            if (habits.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Whatshot,
                    title = "Build the first streak",
                    message = "Habits are stored in a private SQLite database on this device. " +
                        "Checking one off marks today; streaks count consecutive days.",
                    actionLabel = null,
                    onAction = null
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(habits, key = { it.id }) { habit ->
                        val checked = habit.days.contains(today)
                        GlassCard(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.toggle(habit.id, today) }
                                revision++
                            }
                        }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (checked) "Done today" else "Not done today",
                                    tint = if (checked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        habit.name,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        HabitEngine.streakLabel(HabitEngine.streak(habit.days, today)) +
                                            "  -  ${HabitEngine.completionPercent(habit.days, today)}% of 30 days",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repository.delete(habit.id) }
                                        revision++
                                    }
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete habit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
