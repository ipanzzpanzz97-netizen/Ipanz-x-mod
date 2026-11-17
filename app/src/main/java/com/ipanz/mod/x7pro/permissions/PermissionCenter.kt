package com.ipanz.mod.x7pro.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

data class PermissionSnapshot(
    val allFilesAccess: Boolean = false,
    val usageStatsAccess: Boolean = false,
    val overlayAccess: Boolean = false,
    val writeSettingsAccess: Boolean = false,
    val dndPolicyAccess: Boolean = false,
    val microphoneAccess: Boolean = false,
    val notificationsAccess: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false
) {
    val coreGrantedCount: Int
        get() = listOf(
            allFilesAccess,
            usageStatsAccess,
            overlayAccess,
            writeSettingsAccess,
            dndPolicyAccess,
            microphoneAccess,
            notificationsAccess
        ).count { it }

    val coreTotalCount: Int = 7
}

object PermissionCenter {

    const val SHIZUKU_REQUEST_CODE = 7001

    private val _snapshot = MutableStateFlow(PermissionSnapshot())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    fun refresh(context: Context): PermissionSnapshot {
        val shizukuAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        val shizukuAuth = if (shizukuAlive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }

        val current = PermissionSnapshot(
            allFilesAccess = hasAllFilesAccess(),
            usageStatsAccess = hasUsageStatsAccess(context),
            overlayAccess = Settings.canDrawOverlays(context),
            writeSettingsAccess = Settings.System.canWrite(context),
            dndPolicyAccess = hasDndPolicyAccess(context),
            microphoneAccess = hasMicrophoneAccess(context),
            notificationsAccess = hasNotificationsAccess(context),
            shizukuAvailable = shizukuAlive,
            shizukuGranted = shizukuAuth
        )
        _snapshot.value = current
        return current
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    fun hasUsageStatsAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    fun hasDndPolicyAccess(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationPolicyAccessGranted
        } catch (_: Throwable) {
            false
        }
    }

    fun hasMicrophoneAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationsAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openWriteSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openDndPolicySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Ignored on restricted OEM builds
        }
    }

    fun requestShizukuPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
