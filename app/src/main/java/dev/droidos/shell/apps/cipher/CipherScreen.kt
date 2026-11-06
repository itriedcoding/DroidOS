package dev.droidos.shell.apps.cipher

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CipherScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun run(encrypt: Boolean) {
        error = null
        status = null
        if (input.isBlank()) {
            error = if (encrypt) "Type something to encrypt" else "Paste a payload to decrypt"
            return
        }
        if (passphrase.isEmpty()) {
            error = "Enter a passphrase - it is never stored"
            return
        }
        busy = true
        val plaintext = input
        val secret = passphrase.toCharArray()
        // PBKDF2 at 120k rounds is deliberately slow, so it stays off the UI thread.
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (encrypt) CipherEngine.encrypt(plaintext, secret)
                    else CipherEngine.decrypt(plaintext, secret)
                }
            }
            secret.fill('\u0000')
            outcome.onSuccess { result ->
                output = result
                status = if (encrypt) {
                    "Encrypted with AES-256-GCM. Copy the payload - the passphrase is the only key."
                } else {
                    "Decrypted and authenticated: the GCM tag matched, so the payload was not altered."
                }
            }.onFailure { failure ->
                output = ""
                error = when (failure) {
                    is IllegalArgumentException -> failure.message
                    else -> "Cipher failure: ${failure.message ?: failure::class.java.simpleName}"
                }
            }
            busy = false
        }
    }

    ScreenScaffold(
        title = "Cipher",
        subtitle = "AES-256-GCM, PBKDF2-HMAC-SHA256",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                input = ""
                output = ""
                error = null
                status = null
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Clear")
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
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(if (CipherEngine.looksLikePayload(input)) "Payload" else "Text or payload") },
                        minLines = 3,
                        maxLines = 8,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = if (reveal) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    if (reveal) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = if (reveal) "Hide passphrase" else "Show passphrase",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { run(true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Encrypt") }
                        OutlinedButton(
                            onClick = { run(false) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Decrypt") }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it, isError = true)
            }
            status?.let {
                Spacer(Modifier.height(10.dp))
                MessageBanner(it)
            }

            if (output.isNotBlank()) {
                SectionLabel("Result")
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            output,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Copy", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = {
                                input = output
                                output = ""
                                status = null
                            }) {
                                Text("Use as input", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            SectionLabel("How it works")
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "A 16-byte random salt and 12-byte IV are generated per message. The passphrase " +
                            "goes through PBKDF2-HMAC-SHA256 (120,000 rounds, 256-bit key) and the text is " +
                            "sealed with AES-256-GCM, whose 128-bit tag rejects any tampering.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Output is base64 of salt | IV | ciphertext and is prefixed v1:. Nothing is written to " +
                            "disk or to the network - lose the passphrase and the payload is unrecoverable.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
