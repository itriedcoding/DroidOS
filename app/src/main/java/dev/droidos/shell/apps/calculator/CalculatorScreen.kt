package dev.droidos.shell.apps.calculator

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.ScreenScaffold

@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    val preview = remember(expression) {
        if (expression.isBlank()) "" else when (val result = CalculatorEngine.evaluate(expression)) {
            is CalculatorEngine.EvalResult.Value -> "= ${CalculatorEngine.format(result.value)}"
            is CalculatorEngine.EvalResult.Error -> result.message
        }
    }
    val haptics = LocalHapticFeedback.current

    fun append(token: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expression += token
    }

    ScreenScaffold(title = "Calculator", subtitle = "Evaluates as you type", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = expression.ifBlank { "0" },
                fontSize = 44.sp,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.titleMedium,
                color = if (preview.startsWith("=")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            val rows = listOf(
                listOf("AC", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "%", "=")
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        KeypadButton(
                            label = key,
                            modifier = Modifier.weight(1f),
                            accent = key in setOf("÷", "×", "−", "+", "="),
                            onClick = {
                                when (key) {
                                    "AC" -> {
                                        expression = ""
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    "=" -> {
                                        val result = CalculatorEngine.evaluate(expression)
                                        expression = when (result) {
                                            is CalculatorEngine.EvalResult.Value ->
                                                CalculatorEngine.format(result.value)
                                            is CalculatorEngine.EvalResult.Error -> expression
                                        }
                                    }
                                    "÷" -> append("/")
                                    "×" -> append("*")
                                    "−" -> append("-")
                                    else -> append(key)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeypadButton("⌫", Modifier.weight(1f), accent = false) {
                    if (expression.isNotEmpty()) expression = expression.dropLast(1)
                }
                KeypadButton("^", Modifier.weight(1f), accent = false) { append("^") }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean,
    onClick: () -> Unit
) {
    val background = if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
