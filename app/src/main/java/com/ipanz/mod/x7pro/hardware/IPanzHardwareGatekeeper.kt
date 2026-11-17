package com.ipanz.mod.x7pro.hardware

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File

data class HardwareCheckResult(
    val isGenuinePocoX7Pro: Boolean,
    val isAccessAllowed: Boolean,
    val isEmulatorDebugPreview: Boolean,
    val deviceCodename: String,
    val productName: String,
    val modelNumber: String,
    val boardPlatform: String,
    val socModel: String,
    val androidRelease: String,
    val hyperOsFingerprint: String
)

data class LiveSystemTelemetry(
    val batteryTempCelsius: Float = 0f,
    val batteryLevelPercent: Int = 0,
    val batteryVoltageMv: Int = 0,
    val batteryHealthLabel: String = "Sihat",
    val isCharging: Boolean = false,
    val thermalStatusLabel: String = "Normal",
    val thermalSeverityCode: Int = 0,
    val ramTotalMb: Long = 0L,
    val ramUsedMb: Long = 0L,
    val ramAvailableMb: Long = 0L,
    val storageTotalGb: Double = 0.0,
    val storageUsedGb: Double = 0.0,
    val storageFreeGb: Double = 0.0,
    val cpuFrequenciesMhz: List<Int> = emptyList()
)

object IPanzHardwareGatekeeper {

    private val ALLOWED_MODELS = setOf("2412DPC0AG", "2412DPC0AI")
    private const val EXPECTED_PLATFORM = "mt6899"

    fun verifyHardware(isDebugBuild: Boolean = BuildConfig.DEBUG): HardwareCheckResult {
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val boardPlatform = readSystemProperty("ro.board.platform")
            .ifBlank { Build.HARDWARE.orEmpty() }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.orEmpty()
        } else {
            boardPlatform
        }
        val hyperOsVersion = readSystemProperty("ro.mi.os.version.name")
            .ifBlank { readSystemProperty("ro.build.version.incremental") }

        val isGenuine = verifyParameters(
            device = device,
            product = product,
            model = model,
            boardPlatform = boardPlatform,
            socModel = socModel
        )

        val accessAllowed = isGenuine || isDebugBuild
        val isEmulatorPreview = !isGenuine && isDebugBuild

        return HardwareCheckResult(
            isGenuinePocoX7Pro = isGenuine,
            isAccessAllowed = accessAllowed,
            isEmulatorDebugPreview = isEmulatorPreview,
            deviceCodename = device,
            productName = product,
            modelNumber = model,
            boardPlatform = boardPlatform,
            socModel = socModel,
            androidRelease = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            hyperOsFingerprint = hyperOsVersion.ifBlank { Build.DISPLAY.orEmpty() }
        )
    }

    fun verifyParameters(
        device: String,
        product: String,
        model: String,
        boardPlatform: String,
        socModel: String = ""
    ): Boolean {
        val devLower = device.trim().lowercase()
        val prodLower = product.trim().lowercase()
        val modelUpper = model.trim().uppercase()
        val platLower = boardPlatform.trim().lowercase()
        val socLower = socModel.trim().lowercase()

        val codenameMatch = devLower == "rodin" ||
            devLower.contains("rodin") ||
            prodLower == "rodin_global" ||
            prodLower.contains("rodin")
        val modelMatch = modelUpper in ALLOWED_MODELS
        val platformMatch = platLower == EXPECTED_PLATFORM ||
            socLower.contains(EXPECTED_PLATFORM) ||
            socLower.contains("8400")

        // Strict POCO X7 Pro check: codename/model match AND Dimensity 8400-Ultra platform match,
        // or exact POCO X7 Pro global/India model & rodin codename match.
        return (codenameMatch && platformMatch) || (modelMatch && (codenameMatch || platformMatch))
    }

    @SuppressLint("PrivateApi")
    private fun readSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}

class HardwareTelemetryRepository(private val context: Context) {

    fun observeTelemetry(intervalMs: Long = 1500L): Flow<LiveSystemTelemetry> = flow {
        while (currentCoroutineContext().isActive) {
            emit(captureSnapshot())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun captureSnapshot(): LiveSystemTelemetry {
        val batteryIntent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val healthCode = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_GOOD
        ) ?: BatteryManager.BATTERY_HEALTH_GOOD
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Baik (Sihat)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Terlalu Panas"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Rosak"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Voltan Berlebihan"
            BatteryManager.BATTERY_HEALTH_COLD -> "Sejuk"
            else -> "Normal"
        }

        val (thermalLabel, thermalCode) = readThermalStatus()
        val (ramTotalMb, ramAvailMb) = readRamInfo()
        val ramUsedMb = (ramTotalMb - ramAvailMb).coerceAtLeast(0L)
        val (storageTotalGb, storageFreeGb) = readStorageInfo()
        val storageUsedGb = (storageTotalGb - storageFreeGb).coerceAtLeast(0.0)
        val freqs = readCpuFrequencies()

        return LiveSystemTelemetry(
            batteryTempCelsius = tempC,
            batteryLevelPercent = pct,
            batteryVoltageMv = voltageMv,
            batteryHealthLabel = healthLabel,
            isCharging = isCharging,
            thermalStatusLabel = thermalLabel,
            thermalSeverityCode = thermalCode,
            ramTotalMb = ramTotalMb,
            ramUsedMb = ramUsedMb,
            ramAvailableMb = ramAvailMb,
            storageTotalGb = storageTotalGb,
            storageUsedGb = storageUsedGb,
            storageFreeGb = storageFreeGb,
            cpuFrequenciesMhz = freqs
        )
    }

    private fun readThermalStatus(): Pair<String, Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Normal" to 0
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val code = pm.currentThermalStatus
            val label = when (code) {
                PowerManager.THERMAL_STATUS_NONE -> "Optimum"
                PowerManager.THERMAL_STATUS_LIGHT -> "Sederhana"
                PowerManager.THERMAL_STATUS_MODERATE -> "Suam"
                PowerManager.THERMAL_STATUS_SEVERE -> "Panas Tinggi"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Kritikal"
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Bahaya Haba"
                else -> "Normal"
            }
            label to code
        } catch (_: Throwable) {
            "Normal" to 0
        }
    }

    private fun readRamInfo(): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val mb = 1024L * 1024L
            (info.totalMem / mb) to (info.availMem / mb)
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    private fun readStorageInfo(): Pair<Double, Double> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val avail = stat.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            total to avail
        } catch (_: Throwable) {
            0.0 to 0.0
        }
    }

    private fun readCpuFrequencies(): List<Int> {
        val list = mutableListOf<Int>()
        for (core in 0 until 8) {
            try {
                val f = File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                if (f.exists() && f.canRead()) {
                    val khz = f.readText().trim().toIntOrNull()
                    if (khz != null && khz > 0) {
                        list.add(khz / 1000)
                    }
                }
            } catch (_: Throwable) {
                // Ignored if SELinux restricts direct sysfs read on non-root HyperOS
            }
        }
        return list
    }
}
