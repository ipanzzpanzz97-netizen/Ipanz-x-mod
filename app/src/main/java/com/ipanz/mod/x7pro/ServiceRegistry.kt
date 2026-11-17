package com.ipanz.mod.x7pro

import android.content.Context
import com.ipanz.mod.x7pro.cleaner.RamBoosterManager
import com.ipanz.mod.x7pro.voice.AiVoiceStudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HudTelemetryState(
    val isOverlayRunning: Boolean = false,
    val fps: Int = 0,
    val temperatureCelsius: Float = 0f,
    val thermalLabel: String = "Optimum",
    val pingMs: Int? = null,
    val pingServer: String = "1.1.1.1"
)

object ServiceRegistry {

    @Volatile
    var voiceEngineOrNull: AiVoiceStudioEngine? = null
        private set

    @Volatile
    private var ramBoosterOrNull: RamBoosterManager? = null

    private val _hudState = MutableStateFlow(HudTelemetryState())
    val hudState: StateFlow<HudTelemetryState> = _hudState.asStateFlow()

    fun getVoiceEngine(context: Context): AiVoiceStudioEngine {
        return voiceEngineOrNull ?: synchronized(this) {
            voiceEngineOrNull ?: AiVoiceStudioEngine(context.applicationContext).also {
                voiceEngineOrNull = it
            }
        }
    }

    fun getRamBooster(context: Context): RamBoosterManager {
        return ramBoosterOrNull ?: synchronized(this) {
            ramBoosterOrNull ?: RamBoosterManager(context.applicationContext).also {
                ramBoosterOrNull = it
            }
        }
    }

    fun updateHudTelemetry(transform: (HudTelemetryState) -> HudTelemetryState) {
        _hudState.value = transform(_hudState.value)
    }
}
