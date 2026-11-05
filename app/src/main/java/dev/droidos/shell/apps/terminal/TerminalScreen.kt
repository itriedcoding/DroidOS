package dev.droidos.shell.apps.terminal

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { TerminalSession(context) }
    val scope = rememberCoroutineScope()

    val lines by session.lines.collectAsState()
    val cwd by session.cwd.collectAsState()
    val running by session.running.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun submit() {
        val command = input
        if (command.isBlank() || running) return
        input = ""
        scope.launch { session.run(command) }
    }

    ScreenScaffold(
        title = "Terminal",
        subtitle = if (running) "Running…" else "sh as this app's UID",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TerminalEngine.prompt(cwd, session.home),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row {
                    TextButton(onClick = { session.clear() }) { Text("Clear") }
                    if (running) {
                        TextButton(onClick = { session.cancel() }) { Text("Stop") }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = when (line.kind) {
                            TerminalSession.Kind.INPUT -> MaterialTheme.colorScheme.primary
                            TerminalSession.Kind.ERROR -> MaterialTheme.colorScheme.error
                            TerminalSession.Kind.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                            TerminalSession.Kind.OUTPUT -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !running,
                    placeholder = { Text("command", fontSize = 13.sp) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() })
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { submit() }, enabled = !running && input.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Run command",
                        tint = if (input.isNotBlank() && !running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
