package com.ipanz.mod.x7pro.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.ipanz.mod.x7pro.IPanzApplication
import com.ipanz.mod.x7pro.MainActivity
import com.ipanz.mod.x7pro.ServiceRegistry

class VoiceStudioService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceRegistry.voiceEngineOrNull?.stopLiveLoopbackInternal()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val engine = ServiceRegistry.getVoiceEngine(applicationContext)
        val started = engine.startLiveLoopbackInternal()
        if (!started) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceRegistry.voiceEngineOrNull?.stopLiveLoopbackInternal()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceStudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, IPanzApplication.CHANNEL_VOICE_STUDIO)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("iPanz AI Voice Studio Aktif")
            .setContentText("Loopback mikrofon Oboe AAudio kependaman rendah sedang berjalan.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Hentikan", stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.ipanz.mod.x7pro.voice.START_LOOPBACK"
        const val ACTION_STOP = "com.ipanz.mod.x7pro.voice.STOP_LOOPBACK"

        fun start(context: Context) {
            val intent = Intent(context, VoiceStudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, VoiceStudioService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
