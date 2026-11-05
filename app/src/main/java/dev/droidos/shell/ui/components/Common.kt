package dev.droidos.shell.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shape and spacing scale. Every surface in the shell picks from these instead of inventing
 * its own radius, which is what keeps screens from drifting apart visually.
 */
object DroidRadius {
    val sm: Dp = 12.dp
    val md: Dp = 18.dp
    val lg: Dp = 24.dp
    val xl: Dp = 30.dp
}

object DroidSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
}

/** Theme-aware translucent fill used by every card, so light and dark both read correctly. */
/** Near-black card with a hot-pink wash, so surfaces read as gradients rather than flat fills. */
@Composable
private fun cardFill(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    )
)

@Composable
private fun hairline(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(DroidRadius.lg)
    val base = Modifier
        .clip(shape)
        .background(cardFill())
        .border(1.dp, hairline(), shape)
    Surface(
        modifier = if (onClick != null) modifier.then(base).clickable { onClick() } else modifier.then(base),
        color = Color.Transparent,
        shape = shape
    ) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(DroidRadius.sm))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(DroidRadius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
    )
}

/** Hairline separator for rows inside one card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(hairline())
    )
}

@Composable
fun StatBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, height: Int = 6) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                .height(height.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun RingStat(
    fraction: Float,
    value: String,
    caption: String,
    size: Int = 132,
    strokeWidth: Int = 12,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp)) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(54.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onAction() }
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBanner(text: String, isError: Boolean = false) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
