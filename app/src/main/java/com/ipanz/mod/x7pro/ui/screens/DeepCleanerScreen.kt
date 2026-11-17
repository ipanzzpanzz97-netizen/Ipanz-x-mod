package com.ipanz.mod.x7pro.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.CleanerUiState
import com.ipanz.mod.x7pro.hardware.LiveSystemTelemetry
import com.ipanz.mod.x7pro.permissions.PermissionCenter
import com.ipanz.mod.x7pro.permissions.PermissionSnapshot
import com.ipanz.mod.x7pro.ui.components.CyberModuleCard
import com.ipanz.mod.x7pro.ui.components.KegunaanFiturBox
import com.ipanz.mod.x7pro.ui.components.StatusPillBadge
import com.ipanz.mod.x7pro.ui.components.formatBytesHuman
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
fun DeepCleanerScreen(
    cleanerState: CleanerUiState,
    telemetry: LiveSystemTelemetry,
    permissions: PermissionSnapshot,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onToggleJunk: (String) -> Unit,
    onCleanSelectedJunk: () -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onCleanSelectedDuplicates: () -> Unit,
    onToggleHeavyFile: (String) -> Unit,
    onCleanSelectedHeavyFiles: () -> Unit,
    onBoostRam: () -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onRefreshApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PitchBlack),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            modifier = Modifier.widthIn(max = 680.dp),
            containerColor = CarbonElevated,
            contentColor = PocoElectricYellow,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = PocoElectricYellow
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                modifier = Modifier.testTag("tab_cleaner_junk"),
                text = { Text("Sampah UFS", style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                modifier = Modifier.testTag("tab_cleaner_sha256"),
                text = { Text("Pendua SHA-256", style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                modifier = Modifier.testTag("tab_cleaner_ram"),
                text = { Text("RAM & Cache", style = MaterialTheme.typography.labelLarge) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            if (selectedSubTab == 0 || selectedSubTab == 1) {
                item {
                    CyberModuleCard(
                        title = if (selectedSubTab == 0) {
                            "2A: PEMBERSIH SAMPAH MENDALAM"
                        } else {
                            "2B: PENGESAN PENDUA SHA-256 & FAIL BESAR"
                        },
                        kegunaanText = if (selectedSubTab == 0) {
                            "Kegunaan Fitur Ini: Mengimbas baki APK pemasang, fail .tmp/.log, folder kosong, dan cache aplikasi tanpa menyentuh gambar, video, atau dokumen peribadi anda."
                        } else {
                            "Kegunaan Fitur Ini: Mengenal pasti fail kembar tepat melalui cincangan SHA-256 penstriman dan menyenaraikan fail melebihi 100 MB supaya ruang UFS 4.0 tidak dibazirkan."
                        },
                        accentColor = PocoElectricYellow
                    ) {
                        if (!permissions.allFilesAccess) {
                            KegunaanFiturBox(
                                text = "Akses Storan Penuh diperlukan untuk mengimbas storan dalaman.",
                                accentColor = ThermalAmber
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { PermissionCenter.openAllFilesSettings(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_grant_storage_cleaner"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ThermalAmber,
                                    contentColor = PitchBlack
                                )
                            ) {
                                Text("Beri Kebenaran Storan Penuh", fontWeight = FontWeight.Bold)
                            }
                        } else if (cleanerState.isScanning) {
                            Text(
                                text = cleanerState.scanProgress.phaseLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = PocoElectricYellow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = PocoElectricYellow,
                                trackColor = CarbonBorder
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Diimbas: ${cleanerState.scanProgress.scannedFiles} fail • ${cleanerState.scanProgress.currentPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onCancelScan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_cancel_scan")
                            ) {
                                Text("Batal Imbasan", color = NeonCrimson)
                            }
                        } else {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_start_deep_scan"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PocoElectricYellow,
                                    contentColor = PitchBlack
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Mula Imbasan Storan",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Imbas Storan UFS 4.0 Sekarang", fontWeight = FontWeight.Bold)
                            }
                        }

                        cleanerState.lastCleanSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(10.dp))
                            StatusPillBadge(
                                text = "Dibersihkan: ${summary.deletedCount} item (${formatBytesHuman(summary.freedBytes)})",
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            if (selectedSubTab == 0) {
                val res = cleanerState.scanResult
                if (res != null) {
                    val selectedCount = res.junkFiles.count { it.selected }
                    val selectedBytes = res.junkFiles.filter { it.selected }.sumOf { it.sizeBytes }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonElevated)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${res.junkFiles.size} Fail Sampah Dikesan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Dipilih: $selectedCount item (${formatBytesHuman(selectedBytes)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PocoElectricYellow
                                )
                            }
                            Button(
                                onClick = onCleanSelectedJunk,
                                enabled = selectedCount > 0 && !cleanerState.isScanning,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_clean_junk"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCrimson,
                                    contentColor = TextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Padam Sampah",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Padam", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (res.junkFiles.isEmpty()) {
                        item {
                            EmptyScanCard("Tiada fail sampah dikesan. Storan UFS 4.0 anda bersih!")
                        }
                    } else {
                        items(res.junkFiles, key = { it.path }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CarbonElevated)
                                    .clickable { onToggleJunk(item.path) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { onToggleJunk(item.path) },
                                    colors = CheckboxDefaults.colors(checkedColor = PocoElectricYellow)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.category.labelMs} • ${formatBytesHuman(item.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedSubTab == 1) {
                val res = cleanerState.scanResult
                if (res != null) {
                    val dupSelected = res.duplicateGroups.sumOf { g ->
                        g.files.count { it.selectedForDeletion }
                    }
                    val dupBytes = res.duplicateGroups.sumOf { g ->
                        g.files.filter { it.selectedForDeletion }.sumOf { it.sizeBytes }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonElevated)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${res.duplicateGroups.size} Kumpulan Pendua SHA-256",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Jimat: ${formatBytesHuman(dupBytes)} ($dupSelected salinan)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan
                                )
                            }
                            Button(
                                onClick = onCleanSelectedDuplicates,
                                enabled = dupSelected > 0 && !cleanerState.isScanning,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_clean_duplicates"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = PitchBlack
                                )
                            ) {
                                Text("Padam Pendua", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(res.duplicateGroups, key = { it.sha256 }) { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonElevated)
                                .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Fail Pendua",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SHA-256: ${group.sha256.take(12)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan
                                    )
                                }
                                Text(
                                    text = "${formatBytesHuman(group.fileSizeBytes)} / salinan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PocoElectricYellow
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            group.files.forEach { fileEntry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleDuplicate(group.sha256, fileEntry.path) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = fileEntry.selectedForDeletion,
                                        onCheckedChange = { onToggleDuplicate(group.sha256, fileEntry.path) },
                                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                                    )
                                    Text(
                                        text = fileEntry.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val heavySelected = res.heavyFiles.count { it.selectedForDeletion }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonElevated)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fail Besar (> 100 MB): ${res.heavyFiles.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Semak secara manual sebelum padam",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (heavySelected > 0) {
                                Button(
                                    onClick = onCleanSelectedHeavyFiles,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCrimson,
                                        contentColor = TextPrimary
                                    )
                                ) {
                                    Text("Padam ($heavySelected)")
                                }
                            }
                        }
                    }

                    items(res.heavyFiles, key = { it.path }) { heavy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CarbonElevated)
                                .clickable { onToggleHeavyFile(heavy.path) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = heavy.selectedForDeletion,
                                onCheckedChange = { onToggleHeavyFile(heavy.path) },
                                colors = CheckboxDefaults.colors(checkedColor = NeonCrimson)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = heavy.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatBytesHuman(heavy.sizeBytes)} • ${heavy.path}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // 2C: Smart RAM & App Cache Booster
                item {
                    CyberModuleCard(
                        title = "2C: PEMBEBAS RAM LPDDR5X & CACHE APLIKASI",
                        kegunaanText = "Kegunaan Fitur Ini: Mematikan proses latar bukan kritikal menggunakan ActivityManager + UsageStatsManager sebelum bermain game, serta menyenaraikan saiz cache aplikasi sebenar daripada StorageStatsManager.",
                        accentColor = NeonEmerald
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "RAM Digunakan: ${telemetry.ramUsedMb} MB",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Bebas: ${telemetry.ramAvailableMb} MB / ${telemetry.ramTotalMb} MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonEmerald
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "RAM LPDDR5X",
                                tint = NeonEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onBoostRam,
                            enabled = !cleanerState.isBoostingRam,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("btn_boost_ram_now"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = PitchBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = "Bebas RAM",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cleanerState.isBoostingRam) "Membersihkan Proses Latar..." else "Bebaskan RAM LPDDR5X Sekarang",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        cleanerState.lastRamBoost?.let { res ->
                            Spacer(modifier = Modifier.height(10.dp))
                            val shizukuTag = if (res.shizukuCacheTrimmed) " + Shizuku Trim-Caches Aktif" else ""
                            StatusPillBadge(
                                text = "${res.killedProcessesCount} proses dibersihkan (${res.ramBeforeUsedMb}MB → ${res.ramAfterUsedMb}MB)$shizukuTag",
                                color = NeonEmerald
                            )
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
                            text = "Pembersih Cache Berpandu (StorageStatsManager)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        OutlinedButton(onClick = onRefreshApps) {
                            Text("Segarkan", style = MaterialTheme.typography.labelSmall, color = PocoElectricYellow)
                        }
                    }
                }

                items(cleanerState.heavyCacheApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonElevated)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (app.cacheBytes > 0L) {
                                    "Cache: ${formatBytesHuman(app.cacheBytes)}"
                                } else {
                                    app.packageName
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = PocoElectricYellow
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenAppSettings(app.packageName) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_app_cache_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Buka Storan Aplikasi",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Padam Cache", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyScanCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonElevated)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = NeonEmerald)
    }
}
