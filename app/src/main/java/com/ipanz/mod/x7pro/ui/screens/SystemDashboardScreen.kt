package com.ipanz.mod.x7pro.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.hardware.HardwareCheckResult
import com.ipanz.mod.x7pro.hardware.LiveSystemTelemetry
import com.ipanz.mod.x7pro.permissions.PermissionCenter
import com.ipanz.mod.x7pro.permissions.PermissionSnapshot
import com.ipanz.mod.x7pro.ui.components.CircularTelemetryGauge
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
fun SystemDashboardScreen(
    hardwareCheck: HardwareCheckResult,
    telemetry: LiveSystemTelemetry,
    permissions: PermissionSnapshot,
    useShizuku: Boolean,
    onToggleShizuku: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    val tempColor = when {
        telemetry.batteryTempCelsius < 39f -> NeonEmerald
        telemetry.batteryTempCelsius <= 42f -> ThermalAmber
        else -> NeonCrimson
    }

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
                // Module 1: Hardware Lock Banner
                CyberModuleCard(
                    title = "iPANZ HARDWARE LOCK",
                    kegunaanText = "Kegunaan Fitur Ini: Mengesahkan identiti perkakasan asal Xiaomi POCO X7 Pro (rodin / 2412DPC0AG / 2412DPC0AI / mt6899) sebelum membuka papan pemuka telemetri perkakasan sebenar.",
                    accentColor = if (hardwareCheck.isGenuinePocoX7Pro) PocoElectricYellow else ThermalAmber,
                    trailingBadge = {
                        if (hardwareCheck.isGenuinePocoX7Pro) {
                            StatusPillBadge("X7 PRO DISAHKAN", NeonEmerald)
                        } else {
                            StatusPillBadge("MOD PRATONTON DEBUG", ThermalAmber)
                        }
                    }
                ) {
                    if (hardwareCheck.isEmulatorDebugPreview) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ThermalAmber.copy(alpha = 0.14f))
                                .border(1.dp, ThermalAmber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Amaran Pratonton Emulator",
                                tint = ThermalAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Binaan Debug membenarkan pratonton pada emulator. Pada APK Release, kunci ketat POCO X7 Pro aktif secara automatik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonElevated)
                            .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpecRow("Kod Peranti (Device)", hardwareCheck.deviceCodename)
                        SpecRow("Produk & Model", "${hardwareCheck.productName} / ${hardwareCheck.modelNumber}")
                        SpecRow("Platform Cip (SoC)", "${hardwareCheck.boardPlatform} (${hardwareCheck.socModel.ifBlank { "MT6899" }})")
                        SpecRow("Sistem Operasi", hardwareCheck.androidRelease)
                    }
                }
            }

            item {
                // Real-time System Dashboard Gauges
                CyberModuleCard(
                    title = "iPANZ SYSTEM DASHBOARD",
                    kegunaanText = "Kegunaan Fitur Ini: Memantau bacaan sensor sebenar masa nyata termasuk suhu bateri/terma, penggunaan RAM LPDDR5X, ruang storan UFS 4.0, dan voltan bateri.",
                    accentColor = NeonCyan
                ) {
                    val ramProgress = if (telemetry.ramTotalMb > 0L) {
                        telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.toFloat()
                    } else 0f

                    val storageProgress = if (telemetry.storageTotalGb > 0.0) {
                        (telemetry.storageUsedGb / telemetry.storageTotalGb).toFloat()
                    } else 0f

                    val tempProgress = (telemetry.batteryTempCelsius / 55f).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularTelemetryGauge(
                            label = "SUHU",
                            valueText = "${String.format("%.1f", telemetry.batteryTempCelsius)}°C",
                            subText = telemetry.thermalStatusLabel,
                            progress = tempProgress,
                            accentColor = tempColor,
                            modifier = Modifier.weight(1f)
                        )
                        CircularTelemetryGauge(
                            label = "LPDDR5X",
                            valueText = "${(ramProgress * 100).toInt()}%",
                            subText = "${telemetry.ramAvailableMb}MB Bebas",
                            progress = ramProgress,
                            accentColor = PocoElectricYellow,
                            modifier = Modifier.weight(1f)
                        )
                        CircularTelemetryGauge(
                            label = "UFS 4.0",
                            valueText = "${(storageProgress * 100).toInt()}%",
                            subText = "${String.format("%.1f", telemetry.storageFreeGb)}GB Bebas",
                            progress = storageProgress,
                            accentColor = NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Battery & Memory Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Telemetri Bateri",
                                tint = PocoElectricYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bateri: ${telemetry.batteryLevelPercent}% • ${telemetry.batteryVoltageMv} mV • ${telemetry.batteryHealthLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Telemetri RAM",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RAM Digunakan: ${telemetry.ramUsedMb} MB / ${telemetry.ramTotalMb} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "Storan UFS 4.0 Digunakan: ${String.format("%.2f", telemetry.storageUsedGb)} GB / ${String.format("%.2f", telemetry.storageTotalGb)} GB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        if (telemetry.cpuFrequenciesMhz.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Teras CPU (MHz): " + telemetry.cpuFrequenciesMhz.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            item {
                // Optional Shizuku Integration Card
                CyberModuleCard(
                    title = "SOKONGAN SHIZUKU (NON-ROOT)",
                    kegunaanText = "Kegunaan Fitur Ini: Memberi kuasa tambahan tanpa root melalui Shizuku API untuk pembersihan cache aplikasi sistem (pm trim-caches) yang lebih mendalam.",
                    accentColor = NeonEmerald,
                    trailingBadge = {
                        StatusPillBadge(
                            text = when {
                                permissions.shizukuGranted -> "SHIZUKU AKTIF"
                                permissions.shizukuAvailable -> "SHIZUKU DIKESAN"
                                else -> "TIDAK AKTIF"
                            },
                            color = if (permissions.shizukuGranted) NeonEmerald else TextSecondary
                        )
                    }
                ) {
                    CyberSwitchRow(
                        title = "Aktifkan Peningkatan Shizuku",
                        kegunaanText = "Kegunaan Fitur Ini: Menghubungkan iPanz Mod ke perkhidmatan Shizuku apabila pengguna mengaktifkannya untuk pemangkasan cache pantas tanpa akses root.",
                        checked = useShizuku && permissions.shizukuGranted,
                        onCheckedChange = { onToggleShizuku(it) },
                        testTag = "switch_shizuku_elevation",
                        accentColor = NeonEmerald
                    )
                }
            }

            item {
                // Android 15/16 Permission Hub
                CyberModuleCard(
                    title = "PUSAT KEBENARAN ANDROID 15/16",
                    kegunaanText = "Kegunaan Fitur Ini: Memastikan semua kebenaran sah diberikan oleh pengguna sebelum menggunakan modul Cleaner, Overlay HUD, DND, dan Mikrofon AI agar tiada crash.",
                    accentColor = PocoElectricYellow,
                    trailingBadge = {
                        StatusPillBadge("${permissions.coreGrantedCount}/${permissions.coreTotalCount} SAH")
                    }
                ) {
                    LinearProgressIndicator(
                        progress = { permissions.coreGrantedCount.toFloat() / permissions.coreTotalCount.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PocoElectricYellow,
                        trackColor = CarbonBorder
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PermissionRow(
                            title = "Akses Semua Fail (UFS 4.0 Cleaner)",
                            kegunaan = "Diperlukan untuk mengimbas sisa APK, log, dan fail pendua SHA-256 dalam storan dalaman.",
                            granted = permissions.allFilesAccess,
                            testTag = "perm_all_files",
                            onRequest = { PermissionCenter.openAllFilesSettings(context) }
                        )
                        PermissionRow(
                            title = "Statistik Penggunaan & Cache Aplikasi",
                            kegunaan = "Membaca saiz cache sebenar aplikasi melalui StorageStatsManager dan proses latar UsageStatsManager.",
                            granted = permissions.usageStatsAccess,
                            testTag = "perm_usage_stats",
                            onRequest = { PermissionCenter.openUsageAccessSettings(context) }
                        )
                        PermissionRow(
                            title = "Paparan Terapung (HyperGame HUD)",
                            kegunaan = "Memaparkan panel FPS, suhu, ping, dan Quick Voice-Deck terapung di atas permainan.",
                            granted = permissions.overlayAccess,
                            testTag = "perm_overlay",
                            onRequest = { PermissionCenter.openOverlaySettings(context) }
                        )
                        PermissionRow(
                            title = "Ubah Tetapan Sistem (Kunci AMOLED)",
                            kegunaan = "Mengunci mod kecerahan skrin manual semasa bermain supaya skrin tidak malap sendiri.",
                            granted = permissions.writeSettingsAccess,
                            testTag = "perm_write_settings",
                            onRequest = { PermissionCenter.openWriteSettings(context) }
                        )
                        PermissionRow(
                            title = "Akses Jangan Ganggu (Auto-DND)",
                            kegunaan = "Menyekat notifikasi masuk secara automatik apabila mod Game Boost diaktifkan.",
                            granted = permissions.dndPolicyAccess,
                            testTag = "perm_dnd",
                            onRequest = { PermissionCenter.openDndPolicySettings(context) }
                        )
                        PermissionRow(
                            title = "Mikrofon (AI Voice Studio)",
                            kegunaan = "Menangkap input suara anda untuk diproses oleh penapis RNNoise dan model AI ONNX.",
                            granted = permissions.microphoneAccess,
                            testTag = "perm_mic",
                            onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PermissionRow(
                                title = "Notifikasi Perkhidmatan Latar",
                                kegunaan = "Wajib pada Android 13–16 untuk mengekalkan perkhidmatan HUD Game & Voice Loopback stabil.",
                                granted = permissions.notificationsAccess,
                                testTag = "perm_notif",
                                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRefreshPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("btn_refresh_permissions")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Semak Semula Kebenaran",
                            tint = PocoElectricYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Semak Semula Status Kebenaran", color = PocoElectricYellow)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    kegunaan: String,
    granted: Boolean,
    testTag: String,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonElevated)
            .border(
                1.dp,
                if (granted) NeonEmerald.copy(alpha = 0.4f) else CarbonBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.VerifiedUser,
                    contentDescription = title,
                    tint = if (granted) NeonEmerald else PocoElectricYellow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
            if (!granted) {
                Button(
                    onClick = onRequest,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocoElectricYellow,
                        contentColor = PitchBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Benarkan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                StatusPillBadge("DIBENARKAN", NeonEmerald)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        KegunaanFiturBox(text = "Kegunaan Fitur Ini: $kegunaan", accentColor = NeonCyan)
    }
}
