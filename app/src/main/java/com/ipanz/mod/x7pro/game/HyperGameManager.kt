package com.ipanz.mod.x7pro.game

import android.app.GameManager
import android.app.GameState
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.ipanz.mod.x7pro.cleaner.RamBoostResult
import com.ipanz.mod.x7pro.cleaner.RamBoosterManager
import com.ipanz.mod.x7pro.permissions.PermissionCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledGameApp(
    val packageName: String,
    val appName: String,
    val gameModeLabel: String
)

data class GameBoostExecutionResult(
    val ramBoostResult: RamBoostResult,
    val dndEnabled: Boolean,
    val brightnessLockedManual: Boolean,
    val gameManagerStatus: String
)

class HyperGameManager(
    private val context: Context,
    private val ramBooster: RamBoosterManager
) {

    suspend fun loadInstalledGames(): List<InstalledGameApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(launchIntent, 0)
        } catch (_: Throwable) {
            emptyList()
        }

        val gameManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.GAME_SERVICE) as? GameManager
        } else {
            null
        }

        val games = mutableListOf<InstalledGameApp>()
        val seen = HashSet<String>()

        for (info in resolved) {
            val appInfo = info.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue

            val isCategoryGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }

            if (!isCategoryGame) continue

            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: Throwable) {
                pkg
            }

            val gmLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && gameManager != null) {
                try {
                    when (gameManager.gameMode) {
                        GameManager.GAME_MODE_PERFORMANCE -> "Mod Prestasi"
                        GameManager.GAME_MODE_BATTERY -> "Mod Jimat Bateri"
                        GameManager.GAME_MODE_STANDARD -> "Mod Standard"
                        else -> "Auto HyperOS"
                    }
                } catch (_: Throwable) {
                    "Auto HyperOS"
                }
            } else {
                "Standard"
            }

            games.add(
                InstalledGameApp(
                    packageName = pkg,
                    appName = label,
                    gameModeLabel = gmLabel
                )
            )
        }

        games.sortedBy { it.appName.lowercase() }
    }

    suspend fun activatePreGameBoost(
        enableAutoDnd: Boolean,
        lockManualBrightness: Boolean,
        useShizuku: Boolean,
        targetGamePackage: String? = null
    ): GameBoostExecutionResult = withContext(Dispatchers.IO) {
        val excluded = if (targetGamePackage != null) setOf(targetGamePackage) else emptySet()
        val ramRes = ramBooster.boostRamAndTrimCaches(
            useShizuku = useShizuku,
            excludedPackages = excluded
        )

        var dndActive = false
        if (enableAutoDnd && PermissionCenter.hasDndPolicyAccess(context)) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                dndActive = true
            } catch (_: Throwable) {
                dndActive = false
            }
        }

        var brightnessLocked = false
        if (lockManualBrightness && Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                brightnessLocked = true
            } catch (_: Throwable) {
                brightnessLocked = false
            }
        }

        val gameMgrStatus = notifyGameManagerActive()

        GameBoostExecutionResult(
            ramBoostResult = ramRes,
            dndEnabled = dndActive,
            brightnessLockedManual = brightnessLocked,
            gameManagerStatus = gameMgrStatus
        )
    }

    fun restoreEnvironmentSettings(restoreDnd: Boolean, restoreAutoBrightness: Boolean) {
        if (restoreDnd && PermissionCenter.hasDndPolicyAccess(context)) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: Throwable) {
            }
        }
        if (restoreAutoBrightness && Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun launchGamePackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return false
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun notifyGameManagerActive(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val gm = context.getSystemService(Context.GAME_SERVICE) as? GameManager
                gm?.setGameState(
                    GameState(false, GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE)
                )
                return "GameManager API 33+ Aktif"
            } catch (_: Throwable) {
                return "GameManager Sedia (Standard)"
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "GameManager API 31+ Sedia"
        }
        return "Mod Prestasi Standard"
    }
}
