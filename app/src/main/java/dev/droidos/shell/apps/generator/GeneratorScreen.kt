package dev.droidos.shell.apps.generator

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.StatBar
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    var length by remember { mutableStateOf(20) }
    var sets by remember { mutableStateOf(SymbolSet.values().toSet()) }
    var password by remember { mutableStateOf("") }
    var diceCount by remember { mutableStateOf(2) }
    var diceSides by remember { mutableStateOf(6) }
    var rolls by remember { mutableStateOf<List<Int>>(emptyList()) }
    var flips by remember { mutableStateOf<List<Boolean>>(emptyList()) }

    val options = PasswordOptions(length = length, sets = sets)
    val bits = GeneratorEngine.entropyBits(options)

    ScreenScaffold(
        title = "Generator",
        subtitle = "SecureRandom, no network",
        onBack = onBack,
        actions = {
            IconButton(onClick = { password = GeneratorEngine.generate(options) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Generate another")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Length", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            length.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.roundToInt() },
                        valueRange = GeneratorEngine.MIN_LENGTH.toFloat()..GeneratorEngine.MAX_LENGTH.toFloat(),
                        steps = GeneratorEngine.MAX_LENGTH - GeneratorEngine.MIN_LENGTH - 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SymbolSet.values().forEach { set ->
                            FilterChip(
                                selected = set in sets,
                                onClick = {
                                    sets = if (set in sets) sets - set else sets + set
                                    password = ""
                                },
                                label = { Text(set.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { password = GeneratorEngine.generate(options) },
                        enabled = sets.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Generate password") }
                }
            }

            if (password.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            password,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        StatBar(
                            fraction = (bits / 128.0).toFloat().coerceIn(0f, 1f),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                GeneratorEngine.strengthLabel(bits),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${bits.roundToInt()} bits of entropy",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(password)) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                }
            }

            SectionLabel("Dice")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$diceCount d$diceSides", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (rolls.isEmpty()) "Not rolled" else "Total ${GeneratorEngine.diceTotal(rolls)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = diceCount.toFloat(),
                        onValueChange = { diceCount = it.roundToInt().coerceIn(1, 20) },
                        valueRange = 1f..20f,
                        steps = 18
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(4, 6, 8, 10, 12, 20).forEach { sides ->
                            FilterChip(
                                selected = diceSides == sides,
                                onClick = { diceSides = sides },
                                label = { Text("d$sides", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { rolls = GeneratorEngine.rollDice(diceCount, diceSides) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Roll")
                        }
                        OutlinedButton(
                            onClick = { flips = List(10) { GeneratorEngine.flipCoin() } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Flip 10") }
                    }
                    if (rolls.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            rolls.joinToString("  "),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (flips.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            flips.joinToString("  ") { if (it) "H" else "T" },
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            SectionLabel("Source")
            GlassCard {
                Text(
                    "Every character comes from java.security.SecureRandom, and each selected set is " +
                        "guaranteed at least one character before the result is shuffled. Look-alike glyphs " +
                        "(l, I, 1, O, 0) are left out of the pools so a copied password stays readable. " +
                        "Entropy is length x log2(pool size).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
