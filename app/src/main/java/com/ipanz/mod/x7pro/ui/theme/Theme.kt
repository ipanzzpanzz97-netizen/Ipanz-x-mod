package com.ipanz.mod.x7pro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IPanzDarkColorScheme = darkColorScheme(
    primary = PocoElectricYellow,
    onPrimary = PitchBlack,
    primaryContainer = PocoYellowContainer,
    onPrimaryContainer = PocoElectricYellow,
    secondary = NeonCyan,
    onSecondary = PitchBlack,
    secondaryContainer = NeonCyanDim,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonEmerald,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder,
    error = NeonCrimson,
    onError = TextPrimary
)

val IPanzShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

@Composable
fun IPanzModTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IPanzDarkColorScheme,
        typography = IPanzTypography,
        shapes = IPanzShapes,
        content = content
    )
}
