package dev.droidos.shell.apps.convert

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun ConvertScreen(onBack: () -> Unit) {
    var category by remember { mutableStateOf(ConversionEngine.Category.LENGTH) }
    var from by remember { mutableStateOf(ConversionEngine.defaultFrom(category)) }
    var to by remember { mutableStateOf(ConversionEngine.defaultTo(category)) }
    var input by remember { mutableStateOf("1") }

    val value = ConversionEngine.parse(input)
    val result = value?.let { ConversionEngine.convert(it, from, to) }

    ScreenScaffold(title = "Convert", subtitle = "Exact factors, no network", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConversionEngine.Category.values().forEach { entry ->
                    FilterChip(
                        selected = category == entry,
                        onClick = {
                            category = entry
                            from = ConversionEngine.defaultFrom(entry)
                            to = ConversionEngine.defaultTo(entry)
                        },
                        label = { Text(entry.label) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Value") },
                        singleLine = true,
                        isError = input.isNotBlank() && value == null,
                        supportingText = {
                            Text(
                                if (input.isNotBlank() && value == null) "That is not a number"
                                else from.label
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UnitPicker(
                            units = ConversionEngine.units(category),
                            selected = from,
                            onSelect = { from = it },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { val swap = from; from = to; to = swap }) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = "Swap units",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        UnitPicker(
                            units = ConversionEngine.units(category),
                            selected = to,
                            onSelect = { to = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = result?.let { ConversionEngine.format(it) } ?: "-",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        to.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("Units")
            GlassCard {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "1 ${from.label} = " +
                            ConversionEngine.format(ConversionEngine.convert(1.0, from, to)) + " ${to.label}",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${ConversionEngine.units(category).size} units in ${category.label.lowercase()}, " +
                            "converted through one base unit so the arithmetic cannot drift.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UnitPicker(
    units: List<ConversionEngine.Unit>,
    selected: ConversionEngine.Unit,
    onSelect: (ConversionEngine.Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        units.forEach { unit ->
            FilterChip(
                selected = unit.id == selected.id,
                onClick = { onSelect(unit) },
                label = { Text(unit.label, fontSize = 11.sp) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
