package com.ipanz.mod.x7pro.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipanz.mod.x7pro.HudTelemetryState
import com.ipanz.mod.x7pro.ui.theme.CarbonBorder
import com.ipanz.mod.x7pro.ui.theme.CarbonElevated
import com.ipanz.mod.x7pro.ui.theme.IPanzModTheme
import com.ipanz.mod.x7pro.ui.theme.NeonCrimson
import com.ipanz.mod.x7pro.ui.theme.NeonCyan
import com.ipanz.mod.x7pro.ui.theme.NeonEmerald
import com.ipanz.mod.x7pro.ui.theme.PitchBlack
import com.ipanz.mod.x7pro.ui.theme.PocoElectricYellow
import com.ipanz.mod.x7pro.ui.theme.TextPrimary
import com.ipanz.mod.x7pro.ui.theme.TextSecondary
import com.ipanz.mod.x7pro.ui.theme.ThermalAmber
import com.ipanz.mod.x7pro.voice.VoicePreset
import com.ipanz.mod.x7pro.voice.VoiceStudioUiState

@Composable
fun FloatingHudCompose(
    hudState: HudTelemetryState,
    voiceState: VoiceStudioUiState,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onSelectPreset: (VoicePreset) -> Unit,
    onToggleQuickRecord: () -> Unit,
    onPlayVoiceClip: () -> Unit,
    onShareVoiceClip: () -> Unit,
    onCloseHud: () -> Unit
) {
    IPanzModTheme {
        var expanded by remember { mutableStateOf(false) }
        var showVoiceDeck by remember { mutableStateOf(false) }

        val tempColor = when {
            hudState.temperatureCelsius < 39f -> NeonEmerald
            hudState.temperatureCelsius <= 42f -> ThermalAmber
            else -> NeonCrimson
        }

        val pingColor = when {
            hudState.pingMs == null -> NeonCrimson
            hudState.pingMs < 45 -> NeonEmerald
            hudState.pingMs <= 95 -> ThermalAmber
            else -> NeonCrimson
        }

        val dragModifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDragDelta(dragAmount.x, dragAmount.y)
            }
        }

        if (!expanded) {
            // Minimized draggable floating capsule "iPanz"
            Surface(
                modifier = dragModifier
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, PocoElectricYellow, RoundedCornerShape(24.dp))
                    .clickable { expanded = true },
                color = PitchBlack.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tempColor)
                    )
                    Text(
                        text = "iPanz",
                        style = MaterialTheme.typography.labelLarge,
                        color = PocoElectricYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${hudState.fps} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        } else {
            // Expanded Real-Time HUD + Quick Voice-Deck
            Surface(
                modifier = dragModifier
                    .width(286.dp)
                    .border(1.5.dp, PocoElectricYellow, RoundedCornerShape(16.dp)),
                color = PitchBlack.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "iPanz HUD",
                                tint = PocoElectricYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "iPANZ HYPER HUD",
                                style = MaterialTheme.typography.labelLarge,
                                color = PocoElectricYellow
                            )
                        }
                        Row {
                            Text(
                                text = "Kecilkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { expanded = false }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            IconButton(
                                onClick = onCloseHud,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup HUD",
                                    tint = NeonCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real FPS / Temperature / Ping Telemetry Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudMetricPill(
                            label = "FPS",
                            value = "${hudState.fps}",
                            accent = PocoElectricYellow,
                            modifier = Modifier.weight(1f)
                        )
                        HudMetricPill(
                            label = "SUHU",
                            value = "${String.format("%.1f", hudState.temperatureCelsius)}°C",
                            accent = tempColor,
                            modifier = Modifier.weight(1f)
                        )
                        HudMetricPill(
                            label = "PING",
                            value = hudState.pingMs?.let { "${it}ms" } ?: "N/A",
                            accent = pingColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Voice-Deck Toggle Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonElevated)
                            .border(1.dp, if (showVoiceDeck) NeonCyan else CarbonBorder, RoundedCornerShape(10.dp))
                            .clickable { showVoiceDeck = !showVoiceDeck }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Voice-Deck",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quick Voice-Deck",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = if (showVoiceDeck) "Tutup" else "Buka",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }

                    AnimatedVisibility(visible = showVoiceDeck) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Suara aktif: ${voiceState.selectedPreset.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PocoElectricYellow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Compact 2-row preset selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    VoicePreset.ANIME_GIRL,
                                    VoicePreset.COMMANDER,
                                    VoicePreset.CYBORG
                                ).forEach { preset ->
                                    val selected = preset == voiceState.selectedPreset
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selected) PocoElectricYellow else CarbonElevated)
                                            .clickable { onSelectPreset(preset) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset.title.substringBefore(" "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) PitchBlack else TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (voiceState.isRecordingQuickDeck) NeonCrimson else PocoElectricYellow
                                        )
                                        .clickable { onToggleQuickRecord() }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (voiceState.isRecordingQuickDeck) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "Rakam Suara",
                                            tint = if (voiceState.isRecordingQuickDeck) TextPrimary else PitchBlack,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (voiceState.isRecordingQuickDeck) {
                                                "Henti (${String.format("%.1f", voiceState.quickRecordSeconds)}s)"
                                            } else {
                                                "Rakam 1-12s"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (voiceState.isRecordingQuickDeck) TextPrimary else PitchBlack,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (voiceState.lastProcessedWavPath != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeonCyan)
                                            .clickable { onPlayVoiceClip() }
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Main Klip AI",
                                            tint = PitchBlack,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeonEmerald)
                                            .clickable { onShareVoiceClip() }
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Kongsi Nota Suara",
                                            tint = PitchBlack,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonElevated)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
