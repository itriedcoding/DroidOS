package dev.droidos.shell.apps.texttools

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun TextToolsScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }

    val stats = remember(text) { TextEngine.stats(text) }
    val replaced = remember(text, pattern, replacement) {
        if (pattern.isEmpty()) null else TextEngine.replace(text, pattern, replacement)
    }

    ScreenScaffold(
        title = "Text tools",
        subtitle = "Counts, case, regex - all local",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SectionLabel("Input")
            GlassCard {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        output = ""
                    },
                    placeholder = { Text("Paste or type text") },
                    minLines = 4,
                    textStyle = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionLabel("Statistics")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    StatRow("Characters", stats.characters.toString())
                    StatRow("Without spaces", stats.withoutSpaces.toString())
                    StatRow("Words", stats.words.toString())
                    StatRow("Lines", stats.lines.toString())
                    StatRow("Sentences", stats.sentences.toString())
                }
            }

            SectionLabel("Transform")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextEngine.Transform.values().forEach { transform ->
                    FilterChip(
                        selected = false,
                        onClick = { output = TextEngine.apply(text, transform) },
                        label = { Text(transform.label, fontSize = 12.sp) }
                    )
                }
            }
            if (output.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            output,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(output)) }) {
                            Text("Copy result")
                        }
                    }
                }
            }

            SectionLabel("Regex replace")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Pattern") },
                        singleLine = true,
                        isError = replaced is TextEngine.ReplaceResult.Failed,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text("Replacement") },
                        singleLine = true,
                        supportingText = { Text("Group references such as \$1 work") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    when (val result = replaced) {
                        is TextEngine.ReplaceResult.Ok -> {
                            Text(
                                result.value.ifEmpty { "(empty result)" },
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            )
                            Text(
                                "${result.replacements} match${if (result.replacements == 1) "" else "es"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is TextEngine.ReplaceResult.Failed -> Text(
                            result.reason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        null -> Text(
                            "Enter a pattern to run it against the input.",
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

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 13.sp,
            style = TextStyle(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
