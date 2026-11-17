package com.ipanz.mod.x7pro.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.permissions.PermissionSnapshot
import com.ipanz.mod.x7pro.ui.components.CyberModuleCard
import com.ipanz.mod.x7pro.ui.components.KegunaanFiturBox
import com.ipanz.mod.x7pro.ui.components.StatusPillBadge
import com.ipanz.mod.x7pro.ui.theme.CarbonBorder
import com.ipanz.mod.x7pro.ui.theme.CarbonElevated
import com.ipanz.mod.x7pro.ui.theme.NeonCrimson
import com.ipanz.mod.x7pro.ui.theme.NeonCyan
import com.ipanz.mod.x7pro.ui.theme.NeonEmerald
import com.ipanz.mod.x7pro.ui.theme.PitchBlack
import com.ipanz.mod.x7pro.ui.theme.PocoElectricYellow
import com.ipanz.mod.x7pro.ui.theme.TextPrimary
import com.ipanz.mod.x7pro.ui.theme.TextSecondary
import com.ipanz.mod.x7pro.ui.theme.ThermalAmber
import com.ipanz.mod.x7pro.voice.AiVoiceStudioEngine
import com.ipanz.mod.x7pro.voice.VoicePreset
import com.ipanz.mod.x7pro.voice.VoiceStudioService
import com.ipanz.mod.x7pro.voice.VoiceStudioUiState
import kotlin.math.sin

@Composable
fun VoiceStudioScreen(
    voiceState: VoiceStudioUiState,
    voiceEngine: AiVoiceStudioEngine,
    permissions: PermissionSnapshot,
    onRefreshPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                CyberModuleCard(
                    title = "iPANZ AI VOICE STUDIO (ONNX + OBOE)",
                    kegunaanText = "Kegunaan Fitur Ini: Menukar suara kepada 6 karakter neural luar talian menggunakan Google Oboe AAudio, penapis bising RNNoise, dan ONNX Runtime Mobile (NNAPI/XNNPACK).",
                    accentColor = NeonCyan,
                    trailingBadge = {
                        StatusPillBadge(
                            text = when {
                                voiceState.isNnapiAccelerated -> "NPU NNAPI AKTIF"
                                voiceState.isNativeEngineActive -> "C++ OBOE + ORT"
                                else -> "ORT + DSP AKTIF"
                            },
                            color = NeonCyan
                        )
                    }
                ) {
                    // Live RMS Audio Visualizer Canvas
                    AudioEnergyVisualizer(
                        rms = voiceState.liveInputRms,
                        isActive = voiceState.isRecordingQuickDeck || voiceState.isLoopbackRunning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = voiceState.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = PocoElectricYellow
                    )

                    if (!permissions.microphoneAccess) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_grant_mic_voice"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PocoElectricYellow,
                                contentColor = PitchBlack
                            )
                        ) {
                            Text("Benarkan Akses Mikrofon", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                // 6 Voice Characters
                CyberModuleCard(
                    title = "PILIH KARAKTER SUARA AI (6 MODEL)",
                    kegunaanText = "Kegunaan Fitur Ini: Memilih model penukaran vokal ONNX FP16 luar talian dan menetapkan profil Pitch, Formant, dan penyahbisingan secara automatik.",
                    accentColor = PocoElectricYellow
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VoicePreset.entries.forEach { preset ->
                            val selected = preset == voiceState.selectedPreset
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CarbonElevated)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) PocoElectricYellow else CarbonBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { voiceEngine.selectPreset(preset) }
                                    .padding(12.dp)
                                    .testTag("voice_preset_${preset.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${preset.id + 1}. ${preset.title}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (selected) PocoElectricYellow else TextPrimary
                                        )
                                        Text(
                                            text = preset.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan
                                        )
                                    }
                                    if (selected) {
                                        StatusPillBadge("DIPILIH", PocoElectricYellow)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                KegunaanFiturBox(
                                    text = preset.kegunaanMs,
                                    accentColor = if (selected) PocoElectricYellow else NeonCyan,
                                    collapsible = !selected
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Fine-tuning DSP + AI Sliders
                CyberModuleCard(
                    title = "PELARAS DSP & TIMBRE AI",
                    kegunaanText = "Kegunaan Fitur Ini: Menala nada suara (Pitch), bentuk salur vokal (Formant), keamatan transformasi AI (Timbre), dan tahap penapis bunyi kipas/angin (RNNoise Denoise).",
                    accentColor = NeonCyan
                ) {
                    VoiceSliderRow(
                        label = "Pitch (Nada)",
                        value = voiceState.pitchRatio,
                        valueRange = 0.65f..1.75f,
                        valueLabel = String.format("%.2fx", voiceState.pitchRatio),
                        onValueChange = { voiceEngine.updateParameters(pitchRatio = it) }
                    )
                    VoiceSliderRow(
                        label = "Formant (Salur Vokal)",
                        value = voiceState.formantShift,
                        valueRange = 0.70f..1.50f,
                        valueLabel = String.format("%.2fx", voiceState.formantShift),
                        onValueChange = { voiceEngine.updateParameters(formantShift = it) }
                    )
                    VoiceSliderRow(
                        label = "Timbre AI (Keamatan Model)",
                        value = voiceState.timbreIntensity,
                        valueRange = 0f..1f,
                        valueLabel = "${(voiceState.timbreIntensity * 100).toInt()}%",
                        onValueChange = { voiceEngine.updateParameters(timbreIntensity = it) }
                    )
                    VoiceSliderRow(
                        label = "Tahap Denoise (Buang Bising Kipas)",
                        value = voiceState.denoiseStrength,
                        valueRange = 0f..1f,
                        valueLabel = "${(voiceState.denoiseStrength * 100).toInt()}%",
                        onValueChange = { voiceEngine.updateParameters(denoiseStrength = it) }
                    )
                }
            }

            item {
                // Mode 1: Quick Voice-Deck (1-12s -> AI -> Play / Share WhatsApp/Discord/Telegram)
                CyberModuleCard(
                    title = "MOD 1: iPANZ QUICK VOICE-DECK (TANPA ROOT)",
                    kegunaanText = "Kegunaan Fitur Ini: Merakam suara pantas (1–12 saat), menukarnya serta-merta dengan model AI, kemudian memainkannya atau menghantar nota suara ke WhatsApp, Discord, dan Telegram.",
                    accentColor = PocoElectricYellow
                ) {
                    Button(
                        onClick = {
                            if (voiceState.isRecordingQuickDeck) {
                                voiceEngine.stopQuickRecordAndConvert()
                            } else {
                                voiceEngine.startQuickRecord()
                            }
                        },
                        enabled = permissions.microphoneAccess && !voiceState.isProcessingClip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("btn_quick_voice_record"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (voiceState.isRecordingQuickDeck) NeonCrimson else PocoElectricYellow,
                            contentColor = if (voiceState.isRecordingQuickDeck) TextPrimary else PitchBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (voiceState.isRecordingQuickDeck) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Rakam Quick Voice-Deck",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                voiceState.isProcessingClip -> "Memproses Audio AI..."
                                voiceState.isRecordingQuickDeck -> "Henti & Tukar AI (${String.format("%.1f", voiceState.quickRecordSeconds)}s / 12s)"
                                else -> "Tekan Untuk Rakam (1–12s)"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (voiceState.lastProcessedWavPath != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { voiceEngine.playLastProcessedClip() },
                                enabled = !voiceState.isPlayingClip,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_play_ai_clip"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = PitchBlack
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Main Semula",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mainkan", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    voiceEngine.buildShareVoiceNoteIntent()?.let { intent ->
                                        context.startActivity(
                                            Intent.createChooser(intent, "Kongsi Nota Suara AI")
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_share_ai_clip")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Kongsi Audio",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kongsi", color = NeonEmerald)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickShareAppChip(
                                label = "WhatsApp",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    voiceEngine.buildShareVoiceNoteIntent("com.whatsapp")?.let {
                                        context.startActivity(it)
                                    }
                                }
                            )
                            QuickShareAppChip(
                                label = "Discord",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    voiceEngine.buildShareVoiceNoteIntent("com.discord")?.let {
                                        context.startActivity(it)
                                    }
                                }
                            )
                            QuickShareAppChip(
                                label = "Telegram",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    voiceEngine.buildShareVoiceNoteIntent("org.telegram.messenger")?.let {
                                        context.startActivity(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                // Mode 2: Live Earphone / Splitter Loopback with Anti-Feedback Protection
                CyberModuleCard(
                    title = "MOD 2: LIVE EARPHONE / SPLITTER LOOPBACK",
                    kegunaanText = "Kegunaan Fitur Ini: Pemantauan dan penukaran suara AI secara langsung kependaman ultra-rendah ke fon telinga atau kabel audio splitter dengan perlindungan Anti-Feedback.",
                    accentColor = NeonEmerald
                ) {
                    val safe = voiceState.earphoneSafety.isExternalAudioConnected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonElevated)
                            .border(
                                1.dp,
                                if (safe) NeonEmerald.copy(alpha = 0.5f) else ThermalAmber.copy(alpha = 0.6f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (safe) Icons.Default.Headphones else Icons.Default.Warning,
                            contentDescription = "Status Fon Telinga",
                            tint = if (safe) NeonEmerald else ThermalAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = voiceState.earphoneSafety.outputDeviceName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (safe) NeonEmerald else ThermalAmber
                            )
                            Text(
                                text = if (safe) {
                                    "Anti-Feedback Selamat: Output audio luaran dikesan."
                                } else {
                                    "Perlindungan Anti-Feedback Aktif: Sila sambung fon telinga/splitter dahulu untuk mengelakkan gema."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (voiceState.isLoopbackRunning) {
                                    VoiceStudioService.stop(context)
                                } else {
                                    VoiceStudioService.start(context)
                                }
                            },
                            enabled = permissions.microphoneAccess && (safe || voiceState.isLoopbackRunning),
                            modifier = Modifier
                                .weight(1.4f)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_toggle_live_loopback"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (voiceState.isLoopbackRunning) NeonCrimson else NeonEmerald,
                                contentColor = if (voiceState.isLoopbackRunning) TextPrimary else PitchBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Live Loopback",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (voiceState.isLoopbackRunning) "Henti Loopback" else "Mula Live Loopback",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { voiceEngine.checkEarphoneSafety() },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_recheck_earphone")
                        ) {
                            Text("Semak Port", color = NeonCyan)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AudioEnergyVisualizer(
    rms: Float,
    isActive: Boolean
) {
    val barColor = if (isActive) NeonCyan else CarbonBorder
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonElevated)
            .padding(horizontal = 10.dp)
    ) {
        val bars = 32
        val step = size.width / bars
        val centerY = size.height / 2f
        val energy = (rms * 5.5f).coerceIn(0.06f, 1.0f)

        for (i in 0 until bars) {
            val wave = 0.45f + 0.55f * kotlin.math.abs(sin((i * 0.48f).toDouble()).toFloat())
            val halfH = if (isActive) {
                (size.height * 0.44f) * energy * wave
            } else {
                3.dp.toPx()
            }
            val x = i * step + step / 2f
            drawLine(
                color = if (isActive && i % 3 == 0) PocoElectricYellow else barColor,
                start = Offset(x, centerY - halfH),
                end = Offset(x, centerY + halfH),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun VoiceSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = PocoElectricYellow,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = PocoElectricYellow,
                activeTrackColor = PocoElectricYellow,
                inactiveTrackColor = CarbonBorder
            )
        )
    }
}

@Composable
private fun QuickShareAppChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonElevated)
            .border(1.dp, NeonEmerald.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NeonEmerald,
            fontWeight = FontWeight.Bold
        )
    }
}
