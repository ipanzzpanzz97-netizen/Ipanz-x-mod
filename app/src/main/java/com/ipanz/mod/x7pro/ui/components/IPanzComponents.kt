package com.ipanz.mod.x7pro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.ui.theme.CarbonBorder
import com.ipanz.mod.x7pro.ui.theme.CarbonElevated
import com.ipanz.mod.x7pro.ui.theme.CarbonSurface
import com.ipanz.mod.x7pro.ui.theme.NeonCyan
import com.ipanz.mod.x7pro.ui.theme.PitchBlack
import com.ipanz.mod.x7pro.ui.theme.PocoElectricYellow
import com.ipanz.mod.x7pro.ui.theme.PocoYellowContainer
import com.ipanz.mod.x7pro.ui.theme.TextPrimary
import com.ipanz.mod.x7pro.ui.theme.TextSecondary

@Composable
fun CyberModuleCard(
    title: String,
    kegunaanText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = PocoElectricYellow,
    trailingBadge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.55f), CarbonBorder)
            )
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                if (trailingBadge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingBadge()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            KegunaanFiturBox(text = kegunaanText, accentColor = accentColor)
            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
fun KegunaanFiturBox(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    collapsible: Boolean = false
) {
    var expanded by remember { mutableStateOf(!collapsible) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonElevated.copy(alpha = 0.85f))
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .then(
                if (collapsible) {
                    Modifier.clickable { expanded = !expanded }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Kegunaan Fitur Ini",
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Kegunaan Fitur Ini",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun CyberSwitchRow(
    title: String,
    kegunaanText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = PocoElectricYellow
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonElevated)
            .border(
                1.dp,
                if (checked) accentColor.copy(alpha = 0.5f) else CarbonBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PitchBlack,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = PitchBlack
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        KegunaanFiturBox(text = kegunaanText, accentColor = accentColor)
    }
}

@Composable
fun CircularTelemetryGauge(
    label: String,
    valueText: String,
    subText: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonElevated)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center
        ) {
            val clamped = progress.coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                drawArc(
                    color = CarbonBorder,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = 270f * clamped,
                    useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accentColor
        )
        Text(
            text = subText,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    color: Color = PocoElectricYellow,
    backgroundColor: Color = PocoYellowContainer
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatBytesHuman(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
