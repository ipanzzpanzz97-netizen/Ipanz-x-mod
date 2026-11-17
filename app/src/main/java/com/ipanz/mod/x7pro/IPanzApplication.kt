package com.ipanz.mod.x7pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.R
import com.ipanz.mod.x7pro.hardware.HardwareCheckResult
import com.ipanz.mod.x7pro.hardware.IPanzHardwareGatekeeper

class IPanzApplication : Application() {

    lateinit var hardwareCheckResult: HardwareCheckResult
        private set

    override fun onCreate() {
        super.onCreate()
        hardwareCheckResult = IPanzHardwareGatekeeper.verifyHardware()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val hudChannel = NotificationChannel(
                CHANNEL_HYPERGAME_HUD,
                getString(R.string.notification_channel_hud_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_hud_desc)
                setShowBadge(false)
            }

            val voiceChannel = NotificationChannel(
                CHANNEL_VOICE_STUDIO,
                getString(R.string.notification_channel_voice_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_voice_desc)
                setShowBadge(false)
            }

            manager.createNotificationChannel(hudChannel)
            manager.createNotificationChannel(voiceChannel)
        }
    }

    companion object {
        const val CHANNEL_HYPERGAME_HUD = "ipanz_hypergame_hud"
        const val CHANNEL_VOICE_STUDIO = "ipanz_voice_studio"
    }
}
