package com.ipanz.mod.x7pro.cleaner

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import com.ipanz.mod.x7pro.shizuku.IIPanzShizukuService
import com.ipanz.mod.x7pro.shizuku.IPanzShizukuUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppCacheCandidate(
    val packageName: String,
    val appName: String,
    val cacheBytes: Long,
    val lastUsedTimeMs: Long
)

data class RamBoostResult(
    val ramBeforeUsedMb: Long,
    val ramAfterUsedMb: Long,
    val killedProcessesCount: Int,
    val shizukuCacheTrimmed: Boolean
)

class RamBoosterManager(private val context: Context) {

    @Volatile
    private var shizukuService: IIPanzShizukuService? = null

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.isBinderAlive) {
                shizukuService = IIPanzShizukuService.Stub.asInterface(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuService = null
        }
    }

    fun bindShizukuIfAuthorized(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, IPanzShizukuUserService::class.java)
            )
                .daemon(false)
                .processNameSuffix("ipanz_shizuku")
                .tag("IPanzShizukuUserService")
                .version(1)
            Shizuku.bindUserService(args, shizukuConnection)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun unbindShizuku() {
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, IPanzShizukuUserService::class.java)
            )
                .daemon(false)
                .processNameSuffix("ipanz_shizuku")
                .tag("IPanzShizukuUserService")
                .version(1)
            Shizuku.unbindUserService(args, shizukuConnection, true)
        } catch (_: Throwable) {
            // Safe cleanup
        }
        shizukuService = null
    }

    suspend fun inspectHeavyCacheApps(): List<AppCacheCandidate> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val results = mutableListOf<AppCacheCandidate>()

        val usageMap = mutableMapOf<String, Long>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 3600L * 1000L,
                now
            )
            stats?.forEach { stat ->
                usageMap[stat.packageName] = maxOf(usageMap[stat.packageName] ?: 0L, stat.lastTimeUsed)
            }
        } catch (_: Throwable) {
            // Permission may not yet be granted
        }

        val storageStatsMgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }

        val installedApps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList()
        }

        for (appInfo in installedApps) {
            if (appInfo.packageName == context.packageName) continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) continue

            var cacheBytes = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && storageStatsMgr != null) {
                try {
                    val stat = storageStatsMgr.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        appInfo.packageName,
                        Process.myUserHandle()
                    )
                    cacheBytes = stat.cacheBytes
                } catch (_: Throwable) {
                    // Requires PACKAGE_USAGE_STATS
                }
            }

            val lastUsed = usageMap[appInfo.packageName] ?: 0L
            if (cacheBytes >= 512L * 1024L || lastUsed > 0L) {
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    appInfo.packageName
                }
                results.add(
                    AppCacheCandidate(
                        packageName = appInfo.packageName,
                        appName = label,
                        cacheBytes = cacheBytes,
                        lastUsedTimeMs = lastUsed
                    )
                )
            }
        }

        results.sortedWith(
            compareByDescending<AppCacheCandidate> { it.cacheBytes }
                .thenByDescending { it.lastUsedTimeMs }
        ).take(35)
    }

    suspend fun boostRamAndTrimCaches(
        useShizuku: Boolean,
        excludedPackages: Set<String> = emptySet()
    ): RamBoostResult = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val beforeInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(beforeInfo)
        val beforeUsedMb = ((beforeInfo.totalMem - beforeInfo.availMem) / (1024L * 1024L)).coerceAtLeast(0L)

        val candidates = mutableSetOf<String>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 12L * 3600L * 1000L,
                now
            )
            stats?.forEach { stat ->
                if (stat.packageName != context.packageName && stat.packageName !in excludedPackages) {
                    candidates.add(stat.packageName)
                }
            }
        } catch (_: Throwable) {
            // Fallback to installed third-party packages if usage stats not granted
        }

        if (candidates.isEmpty()) {
            try {
                context.packageManager.getInstalledApplications(0)
                    .filter {
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                            it.packageName != context.packageName &&
                            it.packageName !in excludedPackages
                    }
                    .forEach { candidates.add(it.packageName) }
            } catch (_: Throwable) {
                // Ignore
            }
        }

        var killedCount = 0
        for (pkg in candidates) {
            try {
                am.killBackgroundProcesses(pkg)
                killedCount++
            } catch (_: Throwable) {
                // Protected package
            }
        }

        var shizukuTrimmed = false
        if (useShizuku) {
            try {
                val svc = shizukuService
                if (svc != null && svc.asBinder().isBinderAlive) {
                    shizukuTrimmed = svc.trimCaches(10L * 1024L * 1024L * 1024L)
                }
            } catch (_: Throwable) {
                shizukuTrimmed = false
            }
        }

        val afterInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(afterInfo)
        val afterUsedMb = ((afterInfo.totalMem - afterInfo.availMem) / (1024L * 1024L)).coerceAtLeast(0L)

        RamBoostResult(
            ramBeforeUsedMb = beforeUsedMb,
            ramAfterUsedMb = afterUsedMb,
            killedProcessesCount = killedCount,
            shizukuCacheTrimmed = shizukuTrimmed
        )
    }

    fun openAppStorageSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Ignore
        }
    }
}
