package com.ipanz.mod.x7pro.shizuku

import androidx.annotation.Keep
import kotlin.system.exitProcess

@Keep
class IPanzShizukuUserService : IIPanzShizukuService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun trimCaches(targetBytes: Long): Boolean {
        return try {
            val safeBytes = targetBytes.coerceAtLeast(1024L * 1024L * 256L)
            val proc = ProcessBuilder("pm", "trim-caches", safeBytes.toString())
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }

    override fun forceStopApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            val proc = ProcessBuilder("am", "force-stop", packageName)
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }
}
