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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ipanz.mod.x7pro.hardware.HardwareCheckResult
import com.ipanz.mod.x7pro.ui.components.KegunaanFiturBox
import com.ipanz.mod.x7pro.ui.theme.CarbonBorder
import com.ipanz.mod.x7pro.ui.theme.CarbonElevated
import com.ipanz.mod.x7pro.ui.theme.CarbonSurface
import com.ipanz.mod.x7pro.ui.theme.NeonCrimson
import com.ipanz.mod.x7pro.ui.theme.PitchBlack
import com.ipanz.mod.x7pro.ui.theme.PocoElectricYellow
import com.ipanz.mod.x7pro.ui.theme.TextPrimary
import com.ipanz.mod.x7pro.ui.theme.TextSecondary

@Composable
fun GatekeeperLockedScreen(
    hardwareCheck: HardwareCheckResult,
    onExitApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CarbonSurface)
                .border(1.5.dp, NeonCrimson, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.GppBad,
                contentDescription = "Peranti Tidak Disokong",
                tint = NeonCrimson,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Peranti Tidak Disokong",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCrimson,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "iPanz Mod hanya dikunci khas untuk POCO X7 Pro",
                style = MaterialTheme.typography.titleMedium,
                color = PocoElectricYellow,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))

            KegunaanFiturBox(
                text = "Kegunaan Fitur Ini: Kunci perkakasan iPanz Hardware Lock memastikan parameter penalaan Dimensity 8400-Ultra, UFS 4.0, dan enjin audio NNAPI hanya berjalan pada perkakasan asal POCO X7 Pro (rodin / 2412DPC0AG / 2412DPC0AI).",
                accentColor = NeonCrimson
            )

            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonElevated)
                    .border(1.dp, CarbonBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DeviceFieldRow("Device", hardwareCheck.deviceCodename.ifBlank { "Tidak diketahui" })
                DeviceFieldRow("Product", hardwareCheck.productName.ifBlank { "Tidak diketahui" })
                DeviceFieldRow("Model", hardwareCheck.modelNumber.ifBlank { "Tidak diketahui" })
                DeviceFieldRow("Platform", hardwareCheck.boardPlatform.ifBlank { "Tidak diketahui" })
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onExitApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("gatekeeper_exit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCrimson,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Tutup Aplikasi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeviceFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
