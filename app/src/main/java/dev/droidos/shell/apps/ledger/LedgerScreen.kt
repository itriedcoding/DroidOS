package dev.droidos.shell.apps.ledger

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CATEGORIES = listOf("Food", "Transport", "Bills", "Home", "Fun", "Health", "Other")

@Composable
fun LedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { LedgerRepository(context) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var income by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        entries = runCatching { repository.all() }
            .onFailure { error = "Could not read the ledger: ${it.message}" }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { reload() }

    val cents = Money.parseToCents(amount)
    val summary = remember(entries) { LedgerMath.summarise(entries) }

    ScreenScaffold(
        title = "Ledger",
        subtitle = "Integer cents, stored in SQLite",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !income,
                            onClick = { income = false },
                            label = { Text("Spend") }
                        )
                        FilterChip(
                            selected = income,
                            onClick = { income = true },
                            label = { Text("Income") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        isError = amount.isNotBlank() && (cents == null || cents <= 0L),
                        supportingText = {
                            Text(
                                when {
                                    amount.isBlank() -> "Up to two decimal places"
                                    cents == null -> "Not a valid amount"
                                    cents <= 0L -> "Amount must be greater than zero"
                                    else -> Money.format(cents)
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CATEGORIES.forEach { name ->
                            FilterChip(
                                selected = category == name,
                                onClick = { category = name },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val value = cents ?: return@Button
                            if (value <= 0L) return@Button
                            scope.launch {
                                error = null
                                runCatching {
                                    repository.add(
                                        Entry(
                                            cents = value,
                                            category = category,
                                            note = note.trim(),
                                            income = income,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }.onFailure { error = "Could not save: ${it.message}" }
                                amount = ""
                                note = ""
                                reload()
                            }
                        },
                        enabled = cents != null && cents > 0L,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (income) "Add income" else "Add spending")
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }

            SectionLabel("Totals")
            GlassCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Total("Balance", summary.balanceCents, true)
                    Total("Income", summary.incomeCents, false)
                    Total("Spending", summary.expenseCents, false)
                    Text(
                        "${entries.size} entr${if (entries.size == 1) "y" else "ies"} on this device",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("Entries")
            if (entries.isEmpty()) {
                GlassCard {
                    Text(
                        "Nothing recorded yet. Entries are stored in a local SQLite database and are " +
                            "never uploaded.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEach { entry ->
                        GlassCard {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.category, fontSize = 14.sp)
                                    Text(
                                        buildString {
                                            if (entry.note.isNotBlank()) append(entry.note).append(" · ")
                                            append(dateLabel(entry.createdAt))
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    (if (entry.income) "+" else "-") + Money.format(entry.cents),
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                    color = if (entry.income) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { repository.delete(entry.id) }
                                            .onFailure { error = "Could not delete: ${it.message}" }
                                        reload()
                                    }
                                }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete entry",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Total(label: String, cents: Long, emphasise: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (emphasise) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            Money.format(cents),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            color = if (emphasise) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun dateLabel(epochMillis: Long): String = try {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
} catch (e: Exception) {
    "date unknown"
}
