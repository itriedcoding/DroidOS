package dev.droidos.shell.apps.digest

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
import androidx.compose.foundation.selection.selectableGroup
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
fun DigestScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(DigestEngine.Algorithm.SHA256) }
    var decodeInput by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf<String?>(null) }

    val digest = remember(text, algorithm) { DigestEngine.digest(text, algorithm) }
    val encoded = remember(text) { DigestEngine.base64(text) }
    val decoded = remember(decodeInput) { DigestEngine.decodeBase64(decodeInput) }

    fun copy(value: String) = clipboard.setText(AnnotatedString(value))

    ScreenScaffold(
        title = "Digest",
        subtitle = "MessageDigest and Base64, computed locally",
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
                    onValueChange = { text = it },
                    placeholder = { Text("Text to hash") },
                    minLines = 3,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${DigestEngine.byteCount(text)} bytes of UTF-8",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("Hash")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DigestEngine.Algorithm.values().forEach { entry ->
                    FilterChip(
                        selected = algorithm == entry,
                        onClick = { algorithm = entry },
                        label = { Text(entry.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        digest,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { copy(digest) }) { Text("Copy hash") }
                }
            }

            SectionLabel("Base64")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        encoded,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                    TextButton(onClick = { copy(encoded) }) { Text("Copy encoded") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = decodeInput,
                        onValueChange = { decodeInput = it },
                        label = { Text("Decode Base64") },
                        isError = decoded is DigestEngine.Decoded.Failed,
                        minLines = 2,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    when (val result = decoded) {
                        is DigestEngine.Decoded.Text -> Text(
                            result.value,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        )
                        is DigestEngine.Decoded.Failed -> Text(
                            result.reason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SectionLabel("UUID v4")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { uuid = DigestEngine.uuid() }) { Text("Generate") }
                    val current = uuid
                    if (current != null) {
                        Text(
                            current,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { copy(current) }) { Text("Copy") }
                    } else {
                        Text(
                            "Generated with java.util.UUID from the platform's SecureRandom.",
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
