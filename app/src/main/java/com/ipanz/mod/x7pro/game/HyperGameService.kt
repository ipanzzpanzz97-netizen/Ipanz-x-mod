package com.ipanz.mod.x7pro.game

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.ipanz.mod.x7pro.IPanzApplication
import com.ipanz.mod.x7pro.MainActivity
import com.ipanz.mod.x7pro.ServiceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class HyperGameService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pingJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    private var frameCount = 0
    private var lastFpsSampleNs = 0L
    private var choreographerActive = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!choreographerActive) return
            if (lastFpsSampleNs == 0L) {
                lastFpsSampleNs = frameTimeNanos
            }
            frameCount++
            val elapsedNs = frameTimeNanos - lastFpsSampleNs
            if (elapsedNs >= 500_000_000L) {
                val fps = ((frameCount * 1_000_000_000.0) / elapsedNs).roundToInt().coerceAtLeast(1)
                ServiceRegistry.updateHudTelemetry { it.copy(fps = fps) }
                frameCount = 0
                lastFpsSampleNs = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempTenths / 10f
            ServiceRegistry.updateHudTelemetry { it.copy(temperatureCelsius = tempC) }
        }
    }

    private var thermalListener: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView == null) {
            attachOverlay()
            startFpsCounter()
            startThermalMonitoring()
            startRealTcpPingLoop()
            ServiceRegistry.updateHudTelemetry { it.copy(isOverlayRunning = true) }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        choreographerActive = false
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Throwable) {
        }
        unregisterThermalListener()
        pingJob?.cancel()
        detachOverlay()
        ServiceRegistry.updateHudTelemetry { it.copy(isOverlayRunning = false, fps = 0) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }
        overlayParams = params

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycleOwner = lifecycleOwner

        val voiceEngine = ServiceRegistry.getVoiceEngine(applicationContext)
        serviceScope.launch {
            voiceEngine.initializeModel()
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val hudState by ServiceRegistry.hudState.collectAsState()
                val voiceState by voiceEngine.uiState.collectAsState()

                FloatingHudCompose(
                    hudState = hudState,
                    voiceState = voiceState,
                    onDragDelta = { dx, dy ->
                        overlayParams?.let { p ->
                            p.x += dx.roundToInt()
                            p.y += dy.roundToInt()
                            try {
                                windowManager?.updateViewLayout(this, p)
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    onSelectPreset = { preset -> voiceEngine.selectPreset(preset) },
                    onToggleQuickRecord = {
                        if (voiceState.isRecordingQuickDeck) {
                            voiceEngine.stopQuickRecordAndConvert()
                        } else {
                            voiceEngine.startQuickRecord()
                        }
                    },
                    onPlayVoiceClip = { voiceEngine.playLastProcessedClip() },
                    onShareVoiceClip = {
                        val shareIntent = voiceEngine.buildShareVoiceNoteIntent()
                        if (shareIntent != null) {
                            val chooser = Intent.createChooser(shareIntent, "Hantar Nota Suara AI").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                startActivity(chooser)
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    onCloseHud = { stopSelf() }
                )
            }
        }
        overlayView = view
        try {
            wm.addView(view, params)
        } catch (_: Throwable) {
            detachOverlay()
            stopSelf()
        }
    }

    private fun detachOverlay() {
        val view = overlayView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (_: Throwable) {
            }
        }
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
        overlayView = null
        overlayParams = null
    }

    private fun startFpsCounter() {
        choreographerActive = true
        frameCount = 0
        lastFpsSampleNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun startThermalMonitoring() {
        try {
            val initial = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = initial?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            ServiceRegistry.updateHudTelemetry { it.copy(temperatureCelsius = tempTenths / 10f) }
        } catch (_: Throwable) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    val label = when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> "Optimum"
                        PowerManager.THERMAL_STATUS_LIGHT -> "Sederhana"
                        PowerManager.THERMAL_STATUS_MODERATE -> "Suam"
                        PowerManager.THERMAL_STATUS_SEVERE -> "Panas"
                        PowerManager.THERMAL_STATUS_CRITICAL,
                        PowerManager.THERMAL_STATUS_EMERGENCY,
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Kritikal"
                        else -> "Normal"
                    }
                    ServiceRegistry.updateHudTelemetry { it.copy(thermalLabel = label) }
                }
                pm.addThermalStatusListener(listener)
                thermalListener = listener
            } catch (_: Throwable) {
            }
        }
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.removeThermalStatusListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    private fun startRealTcpPingLoop() {
        pingJob?.cancel()
        pingJob = serviceScope.launch(Dispatchers.IO) {
            val targets = listOf("1.1.1.1" to 443, "8.8.8.8" to 53)
            while (isActive) {
                var measuredMs: Int? = null
                var usedHost = "1.1.1.1"
                for ((host, port) in targets) {
                    val startNs = System.nanoTime()
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 1400)
                        }
                        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                        measuredMs = elapsedMs.coerceAtLeast(1)
                        usedHost = host
                        break
                    } catch (_: Throwable) {
                        // Try secondary host
                    }
                }
                ServiceRegistry.updateHudTelemetry {
                    it.copy(pingMs = measuredMs, pingServer = usedHost)
                }
                delay(2000L)
            }
        }
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
            Intent(this, HyperGameService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, IPanzApplication.CHANNEL_HYPERGAME_HUD)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("iPanz HyperGame HUD Aktif")
            .setContentText("Memantau FPS, Suhu Terma & Ping Masa Nyata.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Tutup HUD", stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.ipanz.mod.x7pro.game.START_HUD"
        const val ACTION_STOP = "com.ipanz.mod.x7pro.game.STOP_HUD"

        fun start(context: Context) {
            val intent = Intent(context, HyperGameService::class.java).apply {
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
                Intent(context, HyperGameService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
