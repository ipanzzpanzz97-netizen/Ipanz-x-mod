package com.ipanz.mod.x7pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.HudTelemetryState
import com.ipanz.mod.x7pro.HyperGameUiState
import com.ipanz.mod.x7pro.game.HyperGameService
import com.ipanz.mod.x7pro.permissions.PermissionCenter
import com.ipanz.mod.x7pro.permissions.PermissionSnapshot
import com.ipanz.mod.x7pro.ui.components.CyberModuleCard
import com.ipanz.mod.x7pro.ui.components.CyberSwitchRow
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

@Composable
fun HyperGameScreen(
    gameState: HyperGameUiState,
    hudState: HudTelemetryState,
    permissions: PermissionSnapshot,
    onToggleAutoDnd: (Boolean) -> Unit,
    onToggleLockBrightness: (Boolean) -> Unit,
    onActivateOneTapBoost: (String?) -> Unit,
    onRestoreEnvironment: () -> Unit,
    onRefreshGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                    title = "3A: iPANZ GAME BOOST MODE",
                    kegunaanText = "Kegunaan Fitur Ini: Mengoptimumkan POCO X7 Pro sebelum bermain dengan membersihkan proses latar, mengaktifkan mod Jangan Ganggu (Auto-DND), dan mengunci kecerahan skrin AMOLED supaya tidak malap sendiri.",
                    accentColor = PocoElectricYellow
                ) {
                    CyberSwitchRow(
                        title = "Auto-DND (Sekat Gangguan Notifikasi)",
                        kegunaanText = "Kegunaan Fitur Ini: Mengubah polisi gangguan sistem kepada Mod Keutamaan menggunakan NotificationManager supaya panggilan dan mesej tidak mengganggu permainan.",
                        checked = gameState.enableAutoDnd,
                        onCheckedChange = onToggleAutoDnd,
                        testTag = "switch_auto_dnd",
                        accentColor = PocoElectricYellow
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberSwitchRow(
                        title = "Kunci Kecerahan AMOLED Manual",
                        kegunaanText = "Kegunaan Fitur Ini: Menetapkan SCREEN_BRIGHTNESS_MODE ke manual melalui Settings.System supaya skrin tidak malap secara tiba-tiba apabila jari menutup sensor cahaya.",
                        checked = gameState.lockManualBrightness,
                        onCheckedChange = onToggleLockBrightness,
                        testTag = "switch_brightness_lock",
                        accentColor = NeonCyan
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onActivateOneTapBoost(null) },
                            enabled = !gameState.isBoosting,
                            modifier = Modifier
                                .weight(1.4f)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_one_tap_game_boost"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PocoElectricYellow,
                                contentColor = PitchBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "Aktifkan Game Boost",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (gameState.isBoosting) "Mengoptimum..." else "Boost Sekarang",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onRestoreEnvironment,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("btn_restore_env"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Pulih Tetapan",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pulih", color = TextSecondary)
                        }
                    }

                    gameState.lastBoostResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        StatusPillBadge(
                            text = "${res.gameManagerStatus} • ${res.ramBoostResult.killedProcessesCount} proses dibersih" +
                                (if (res.dndEnabled) " • DND Aktif" else "") +
                                (if (res.brightnessLockedManual) " • AMOLED Dikunci" else ""),
                            color = NeonEmerald
                        )
                    }
                }
            }

            item {
                // 3B: Floating HUD Overlay Card
                CyberModuleCard(
                    title = "3B: PANEL TERAPUNG iPANZ HUD",
                    kegunaanText = "Kegunaan Fitur Ini: Memaparkan meter FPS sebenar (Choreographer), Suhu peranti (BatteryManager/Thermal), Ping TCP (1.1.1.1), dan butang pantas Quick Voice-Deck secara terapung di atas game.",
                    accentColor = NeonCyan,
                    trailingBadge = {
                        StatusPillBadge(
                            text = if (hudState.isOverlayRunning) "HUD AKTIF" else "TIDAK AKTIF",
                            color = if (hudState.isOverlayRunning) NeonEmerald else TextSecondary
                        )
                    }
                ) {
                    if (!permissions.overlayAccess) {
                        KegunaanFiturBox(
                            text = "Kebenaran Paparan Terapung (SYSTEM_ALERT_WINDOW) diperlukan untuk memaparkan HUD di atas permainan.",
                            accentColor = ThermalAmber
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { PermissionCenter.openOverlaySettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_grant_overlay_hud"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ThermalAmber,
                                contentColor = PitchBlack
                            )
                        ) {
                            Text("Benarkan Paparan Terapung", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (hudState.isOverlayRunning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CarbonElevated)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Text(
                                    text = "FPS: ${hudState.fps}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PocoElectricYellow
                                )
                                Text(
                                    text = "Suhu: ${String.format("%.1f", hudState.temperatureCelsius)}°C",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonEmerald
                                )
                                Text(
                                    text = "Ping: ${hudState.pingMs?.let { "${it}ms" } ?: "N/A"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Button(
                            onClick = {
                                if (hudState.isOverlayRunning) {
                                    HyperGameService.stop(context)
                                } else {
                                    HyperGameService.start(context)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_toggle_floating_hud"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hudState.isOverlayRunning) NeonCrimson else NeonCyan,
                                contentColor = if (hudState.isOverlayRunning) TextPrimary else PitchBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Kawal HUD Terapung",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hudState.isOverlayRunning) "Tutup Panel Terapung HUD" else "Lancar Panel Terapung HUD",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pelancar Pantas Game (${gameState.installedGames.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    OutlinedButton(onClick = onRefreshGames) {
                        Text("Imbas Game", style = MaterialTheme.typography.labelSmall, color = PocoElectricYellow)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                KegunaanFiturBox(
                    text = "Kegunaan Fitur Ini: Mengesan game yang dipasang pada POCO X7 Pro menggunakan Android GameManager dan terus mengaktifkan Game Boost sebelum melancarkan permainan.",
                    accentColor = PocoElectricYellow
                )
            }

            if (gameState.installedGames.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonElevated)
                            .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tiada pakej CATEGORY_GAME ditemui pada peranti ini. Gunakan Boost Sekarang sebelum membuka game anda secara manual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(gameState.installedGames, key = { it.packageName }) { game ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonElevated)
                            .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${game.gameModeLabel} • ${game.packageName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onActivateOneTapBoost(game.packageName) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_launch_game_${game.packageName}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PocoElectricYellow,
                                contentColor = PitchBlack
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Boost & Main",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Main", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
