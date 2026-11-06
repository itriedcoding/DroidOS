package dev.droidos.shell.apps.flashcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun FlashcardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { FlashcardRepository(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var queue by remember { mutableStateOf<List<FlashcardRepository.Card>>(emptyList()) }
    var stats by remember { mutableStateOf(FlashcardRepository.Stats(0, 0, 0)) }
    var revealed by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { repository.close() }
    }

    LaunchedEffect(revision) {
        val loaded = withContext(Dispatchers.IO) {
            repository.due(today) to repository.stats(today)
        }
        queue = loaded.first
        stats = loaded.second
        revealed = false
    }

    fun grade(quality: Int) {
        val card = queue.firstOrNull() ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                val state = SrsEngine.CardState(card.easeFactor, card.intervalDays, card.repetitions)
                repository.review(card.id, state, quality, today)
            }
            revision++
        }
    }

    ScreenScaffold(
        title = "Flashcards",
        subtitle = "${stats.due} due of ${stats.total} cards",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val card = queue.firstOrNull()
            if (card == null) {
                EmptyState(
                    icon = Icons.Rounded.Style,
                    title = if (stats.total == 0) "No cards yet" else "All caught up",
                    message = if (stats.total == 0) {
                        "Add a question and answer below. Reviews use SM-2 scheduling, so the next " +
                            "due date comes from how well you graded yourself."
                    } else {
                        "Nothing is due today. New cards and lapsed cards will appear here."
                    },
                    actionLabel = null,
                    onAction = null
                )
            } else {
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            card.question,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (revealed) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                card.answer,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${SrsEngine.dueLabel(card.dueDate, today)}  -  " +
                                "ease ${card.easeFactor}  -  interval ${card.intervalDays}d",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (!revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Show answer", fontSize = 13.sp)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "Again", 3 to "Hard", 4 to "Good", 5 to "Easy").forEach { (quality, label) ->
                            OutlinedButton(
                                onClick = { grade(quality) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Grades update this card's ease factor and due date.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }

            SectionLabel("Add a card")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Question") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Answer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (question.isBlank() || answer.isBlank()) {
                                error = "Both sides of the card are needed"
                                return@Button
                            }
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.add(question, answer, today) }
                                question = ""
                                answer = ""
                                error = null
                                revision++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = question.isNotBlank() && answer.isNotBlank()
                    ) { Text("Add card", fontSize = 13.sp) }
                }
            }

            if (stats.total > 0) {
                SectionLabel("Progress")
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "${stats.total} cards  -  ${stats.reviews} successful reviews  -  " +
                                "${stats.due} due today",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
