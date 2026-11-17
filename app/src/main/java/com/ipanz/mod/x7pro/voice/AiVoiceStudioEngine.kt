package com.ipanz.mod.x7pro.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class VoicePreset(
    val id: Int,
    val title: String,
    val subtitle: String,
    val kegunaanMs: String,
    val assetFileName: String,
    val defaultPitch: Float,
    val defaultFormant: Float,
    val defaultTimbre: Float,
    val defaultDenoise: Float
) {
    ANIME_GIRL(
        id = 0,
        title = "Gadis Anime HD",
        subtitle = "Vokal Halus & Forman Tinggi",
        kegunaanMs = "Kegunaan Fitur Ini: Menaikkan pic dan resonans forman vokal menjadi nada anime cerah menggunakan model neural FP16 luar talian.",
        assetFileName = "models/ipanz_voice_anime_fp16.onnx",
        defaultPitch = 1.42f,
        defaultFormant = 1.28f,
        defaultTimbre = 0.72f,
        defaultDenoise = 0.78f
    ),
    ADULT_WOMAN(
        id = 1,
        title = "Wanita Dewasa Natural",
        subtitle = "Nada Vokal Feminin Semula Jadi",
        kegunaanMs = "Kegunaan Fitur Ini: Melaras frekuensi vokal wanita yang lembut dan semula jadi tanpa kesan robotik melampau.",
        assetFileName = "models/ipanz_voice_woman_fp16.onnx",
        defaultPitch = 1.26f,
        defaultFormant = 1.16f,
        defaultTimbre = 0.62f,
        defaultDenoise = 0.75f
    ),
    COMMANDER(
        id = 2,
        title = "Komander Garau Dalam",
        subtitle = "Sub-Bass Dada & Berwibawa",
        kegunaanMs = "Kegunaan Fitur Ini: Menurunkan nada suara dan menguatkan resonans rendah untuk suara taktikal komander yang garau.",
        assetFileName = "models/ipanz_voice_commander_fp16.onnx",
        defaultPitch = 0.76f,
        defaultFormant = 0.84f,
        defaultTimbre = 0.78f,
        defaultDenoise = 0.72f
    ),
    CYBORG(
        id = 3,
        title = "Cyborg Taktikal",
        subtitle = "Modulasi Gelang Metalik AI",
        kegunaanMs = "Kegunaan Fitur Ini: Menambah modulasi harmonik siber dan resonans logam berfasa untuk suara skuad futuristik.",
        assetFileName = "models/ipanz_voice_cyborg_fp16.onnx",
        defaultPitch = 0.92f,
        defaultFormant = 0.96f,
        defaultTimbre = 0.85f,
        defaultDenoise = 0.80f
    ),
    ESPORTS_RADIO(
        id = 4,
        title = "Radio Skuad E-Sukan",
        subtitle = "Komunikasi Taktikal Jelas 3.4kHz",
        kegunaanMs = "Kegunaan Fitur Ini: Menapis julat frekuensi suara seperti walkie-talkie kejohanan e-sukan agar arahan vokal mudah didengar.",
        assetFileName = "models/ipanz_voice_radio_fp16.onnx",
        defaultPitch = 1.00f,
        defaultFormant = 1.05f,
        defaultTimbre = 0.74f,
        defaultDenoise = 0.85f
    ),
    PRO_MIC_ENHANCER(
        id = 5,
        title = "Pro Mic Enhancer",
        subtitle = "RNNoise Buang Bising Kipas + Jernih",
        kegunaanMs = "Kegunaan Fitur Ini: Mengekalkan suara asal anda sambil membuang bunyi bising kipas, angin, dan dengung latar secara AI.",
        assetFileName = "models/ipanz_denoiser_rnnoise.onnx",
        defaultPitch = 1.00f,
        defaultFormant = 1.00f,
        defaultTimbre = 0.45f,
        defaultDenoise = 0.92f
    )
}

data class EarphoneSafetyStatus(
    val isExternalAudioConnected: Boolean = false,
    val outputDeviceName: String = "Pembesar Suara Telefon (Risiko Gema)"
)

data class VoiceStudioUiState(
    val selectedPreset: VoicePreset = VoicePreset.ANIME_GIRL,
    val pitchRatio: Float = VoicePreset.ANIME_GIRL.defaultPitch,
    val formantShift: Float = VoicePreset.ANIME_GIRL.defaultFormant,
    val timbreIntensity: Float = VoicePreset.ANIME_GIRL.defaultTimbre,
    val denoiseStrength: Float = VoicePreset.ANIME_GIRL.defaultDenoise,
    val isModelReady: Boolean = false,
    val isNativeEngineActive: Boolean = NativeAudioBridge.isNativeLoaded,
    val isNnapiAccelerated: Boolean = false,
    val isRecordingQuickDeck: Boolean = false,
    val quickRecordSeconds: Float = 0f,
    val isProcessingClip: Boolean = false,
    val isPlayingClip: Boolean = false,
    val isLoopbackRunning: Boolean = false,
    val lastProcessedWavPath: String? = null,
    val liveInputRms: Float = 0f,
    val earphoneSafety: EarphoneSafetyStatus = EarphoneSafetyStatus(),
    val statusMessage: String = "Enjin AI Voice Studio sedia."
)

class AiVoiceStudioEngine(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 48000
        const val MAX_QUICK_RECORD_SECONDS = 12
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(VoiceStudioUiState())
    val uiState: StateFlow<VoiceStudioUiState> = _uiState.asStateFlow()

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var fallbackLoopbackJob: Job? = null

    private val recordedPcmSamples = mutableListOf<Float>()

    suspend fun initializeModel(preset: VoicePreset = _uiState.value.selectedPreset) = withContext(Dispatchers.IO) {
        var loaded = false
        var nnapi = false

        if (NativeAudioBridge.isNativeLoaded) {
            try {
                loaded = NativeAudioBridge.nativeInit(context.assets, preset.assetFileName)
                nnapi = NativeAudioBridge.nativeIsNnapiActive()
                NativeAudioBridge.nativeUpdateParams(
                    preset.id,
                    _uiState.value.pitchRatio,
                    _uiState.value.formantShift,
                    _uiState.value.timbreIntensity,
                    _uiState.value.denoiseStrength
                )
            } catch (_: Throwable) {
                loaded = false
            }
        }

        // Fallback / parallel ONNX Runtime Java session for guaranteed real inference even without C++ .so
        if (!loaded) {
            try {
                val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
                val bytes = context.assets.open(preset.assetFileName).use { it.readBytes() }
                ortSession?.close()
                val opts = OrtSession.SessionOptions()
                opts.setIntraOpNumThreads(2)
                ortSession = env.createSession(bytes, opts)
                loaded = true
            } catch (_: Throwable) {
                loaded = false
            }
        }

        val safety = checkEarphoneSafety()
        _uiState.update {
            it.copy(
                isModelReady = loaded,
                isNativeEngineActive = NativeAudioBridge.isNativeLoaded,
                isNnapiAccelerated = nnapi,
                earphoneSafety = safety,
                statusMessage = if (loaded) {
                    "Model ${preset.title} dimuatkan (${if (nnapi) "NNAPI NPU" else "XNNPACK/CPU"})."
                } else {
                    "Mod DSP aktif (model aset tidak ditemui)."
                }
            )
        }
    }

    fun selectPreset(preset: VoicePreset) {
        _uiState.update {
            it.copy(
                selectedPreset = preset,
                pitchRatio = preset.defaultPitch,
                formantShift = preset.defaultFormant,
                timbreIntensity = preset.defaultTimbre,
                denoiseStrength = preset.defaultDenoise
            )
        }
        syncNativeParams()
        scope.launch {
            initializeModel(preset)
        }
    }

    fun updateParameters(
        pitchRatio: Float = _uiState.value.pitchRatio,
        formantShift: Float = _uiState.value.formantShift,
        timbreIntensity: Float = _uiState.value.timbreIntensity,
        denoiseStrength: Float = _uiState.value.denoiseStrength
    ) {
        _uiState.update {
            it.copy(
                pitchRatio = pitchRatio.coerceIn(0.6f, 1.8f),
                formantShift = formantShift.coerceIn(0.7f, 1.5f),
                timbreIntensity = timbreIntensity.coerceIn(0f, 1f),
                denoiseStrength = denoiseStrength.coerceIn(0f, 1f)
            )
        }
        syncNativeParams()
    }

    private fun syncNativeParams() {
        val state = _uiState.value
        if (NativeAudioBridge.isNativeLoaded) {
            try {
                NativeAudioBridge.nativeUpdateParams(
                    state.selectedPreset.id,
                    state.pitchRatio,
                    state.formantShift,
                    state.timbreIntensity,
                    state.denoiseStrength
                )
            } catch (_: Throwable) {
                // Ignored
            }
        }
    }

    fun checkEarphoneSafety(): EarphoneSafetyStatus {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val external = outputs.firstOrNull { dev ->
                when (dev.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    } else {
                        false
                    }
                }
            }
            val status = if (external != null) {
                EarphoneSafetyStatus(
                    isExternalAudioConnected = true,
                    outputDeviceName = external.productName?.toString()?.ifBlank { "Fon Telinga / Splitter" }
                        ?: "Fon Telinga / Splitter"
                )
            } else {
                EarphoneSafetyStatus(
                    isExternalAudioConnected = false,
                    outputDeviceName = "Pembesar Suara Telefon (Anti-Gema Aktif)"
                )
            }
            _uiState.update { it.copy(earphoneSafety = status) }
            status
        } catch (_: Throwable) {
            EarphoneSafetyStatus()
        }
    }

    @SuppressLint("MissingPermission")
    fun startQuickRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.update { it.copy(statusMessage = "Sila beri kebenaran Mikrofon terlebih dahulu.") }
            return
        }
        if (_uiState.value.isRecordingQuickDeck) return

        synchronized(recordedPcmSamples) {
            recordedPcmSamples.clear()
        }

        recordJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (_: Throwable) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                _uiState.update {
                    it.copy(
                        isRecordingQuickDeck = false,
                        statusMessage = "Gagal memulakan mikrofon perkakasan."
                    )
                }
                recorder?.release()
                return@launch
            }

            _uiState.update {
                it.copy(
                    isRecordingQuickDeck = true,
                    quickRecordSeconds = 0f,
                    statusMessage = "Merakam Quick Voice-Deck (Maks 12s)..."
                )
            }

            val shortBuf = ShortArray(minBuf / 2)
            val maxSamples = SAMPLE_RATE * MAX_QUICK_RECORD_SECONDS
            var totalCaptured = 0

            try {
                recorder.startRecording()
                while (currentCoroutineContext().isActive &&
                    _uiState.value.isRecordingQuickDeck &&
                    totalCaptured < maxSamples
                ) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        val chunk = FloatArray(read)
                        for (i in 0 until read) {
                            val v = shortBuf[i] / 32768f
                            chunk[i] = v
                            sumSq += v * v
                        }
                        synchronized(recordedPcmSamples) {
                            for (sample in chunk) {
                                recordedPcmSamples.add(sample)
                            }
                        }
                        totalCaptured += read
                        val rms = sqrt(sumSq / read).toFloat()
                        _uiState.update {
                            it.copy(
                                liveInputRms = rms,
                                quickRecordSeconds = totalCaptured.toFloat() / SAMPLE_RATE.toFloat()
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Safe stop
            } finally {
                try {
                    recorder.stop()
                } catch (_: Throwable) {
                }
                recorder.release()
                _uiState.update { it.copy(isRecordingQuickDeck = false, liveInputRms = 0f) }
                if (totalCaptured > SAMPLE_RATE / 4) {
                    processRecordedClipToWav()
                } else {
                    _uiState.update { it.copy(statusMessage = "Rakaman terlalu pendek (< 0.25s).") }
                }
            }
        }
    }

    fun stopQuickRecordAndConvert() {
        _uiState.update { it.copy(isRecordingQuickDeck = false) }
    }

    private suspend fun processRecordedClipToWav() = withContext(Dispatchers.IO) {
        val rawSamples: FloatArray = synchronized(recordedPcmSamples) {
            recordedPcmSamples.toFloatArray()
        }
        if (rawSamples.isEmpty()) return@withContext

        _uiState.update {
            it.copy(
                isProcessingClip = true,
                statusMessage = "Memproses suara dengan AI + RNNoise Denoiser..."
            )
        }

        val state = _uiState.value
        val processed: FloatArray = if (NativeAudioBridge.isNativeLoaded) {
            NativeAudioBridge.nativeProcessClip(rawSamples) ?: processWithKotlinAndOrt(rawSamples, state)
        } else {
            processWithKotlinAndOrt(rawSamples, state)
        }

        val outDir = File(context.cacheDir, "voice_clips").apply { mkdirs() }
        val fileName = "ipanz_${state.selectedPreset.name.lowercase()}_${System.currentTimeMillis()}.wav"
        val outFile = File(outDir, fileName)
        writeWavFile(outFile, processed, SAMPLE_RATE)

        _uiState.update {
            it.copy(
                isProcessingClip = false,
                lastProcessedWavPath = outFile.absolutePath,
                statusMessage = "Klip AI ${state.selectedPreset.title} siap (${String.format("%.1f", rawSamples.size.toFloat() / SAMPLE_RATE)}s)."
            )
        }
    }

    private fun processWithKotlinAndOrt(input: FloatArray, state: VoiceStudioUiState): FloatArray {
        val output = FloatArray(input.size)
        val denoised = FloatArray(input.size)

        // Step 1: Adaptive RNNoise-style spectral gate & high-pass filter
        var hpPrevIn = 0f
        var hpPrevOut = 0f
        var noiseFloor = 0.0025f
        var gateEnv = 0f
        val blockSize = 256

        for (offset in input.indices step blockSize) {
            val count = minOf(blockSize, input.size - offset)
            var sumSq = 0.0
            for (i in 0 until count) {
                val x = input[offset + i]
                val hp = 0.9889f * (hpPrevOut + x - hpPrevIn)
                hpPrevIn = x
                hpPrevOut = hp
                denoised[offset + i] = hp
                sumSq += hp * hp
            }
            val rms = sqrt(sumSq / count).toFloat()
            noiseFloor = if (rms < noiseFloor * 1.35f) {
                0.95f * noiseFloor + 0.05f * rms
            } else {
                0.998f * noiseFloor + 0.002f * rms
            }.coerceIn(0.0008f, 0.04f)
            val snr = rms / (noiseFloor + 1e-6f)
            val speechProb = ((snr - 1.35f) / 2.4f).coerceIn(0f, 1f)
            val minGain = 1f - state.denoiseStrength * 0.90f
            val targetGain = minGain + (1f - minGain) * speechProb
            gateEnv += 0.35f * (targetGain - gateEnv)
            for (i in 0 until count) {
                denoised[offset + i] = denoised[offset + i] * gateEnv
            }
        }

        // Step 2: Dual-tap pitch & formant processor
        val windowLen = 1024f
        val ring = FloatArray(4096)
        var writeIdx = 0
        var phase = 0f
        var band1 = 0f
        var band2 = 0f
        var cyborgPhase = 0f
        val phaseStep = (1f - state.pitchRatio) / windowLen

        for (i in denoised.indices) {
            ring[writeIdx] = denoised[i]
            var shifted = denoised[i]
            if (abs(state.pitchRatio - 1f) > 0.015f) {
                val p1 = phase
                var p2 = p1 + 0.5f
                if (p2 >= 1f) p2 -= 1f
                val s1 = readRing(ring, writeIdx, p1 * windowLen)
                val s2 = readRing(ring, writeIdx, p2 * windowLen)
                val w1 = (0.5f - 0.5f * cos(2f * PI.toFloat() * p1))
                shifted = s1 * w1 + s2 * (1f - w1)
                phase += phaseStep
                while (phase >= 1f) phase -= 1f
                while (phase < 0f) phase += 1f
            }

            val lp = band1 + 0.16f * (shifted - band1)
            band1 = lp
            val hp = shifted - lp
            val bp = band2 + 0.34f * (hp - band2)
            band2 = bp

            val shaped = when (state.selectedPreset) {
                VoicePreset.ANIME_GIRL, VoicePreset.ADULT_WOMAN ->
                    shifted + hp * (0.35f * state.formantShift * state.timbreIntensity) - lp * 0.18f
                VoicePreset.COMMANDER ->
                    shifted + lp * (0.65f * state.timbreIntensity) - hp * 0.15f
                VoicePreset.CYBORG -> {
                    cyborgPhase += (2f * PI.toFloat() * 115f) / SAMPLE_RATE.toFloat()
                    if (cyborgPhase > 2f * PI.toFloat()) cyborgPhase -= 2f * PI.toFloat()
                    (shifted * (0.65f + 0.35f * sin(cyborgPhase))) + bp * (0.45f * state.timbreIntensity)
                }
                VoicePreset.ESPORTS_RADIO ->
                    tanh((bp * 1.75f) * (1f + 0.4f * state.timbreIntensity))
                VoicePreset.PRO_MIC_ENHANCER ->
                    shifted + bp * (0.22f * state.timbreIntensity)
            }
            output[i] = tanh(shaped * 0.95f).coerceIn(-0.98f, 0.98f)
            writeIdx = (writeIdx + 1) % ring.size
        }

        // Step 3: Batched ONNX Runtime spectral refinement if session available
        val env = ortEnv
        val session = ortSession
        val frameCount = output.size / 256
        if (env != null && session != null && frameCount > 0) {
            val usableSamples = frameCount * 256
            val batchInput = FloatBuffer.wrap(output, 0, usableSamples)
            try {
                OnnxTensor.createTensor(
                    env,
                    batchInput,
                    longArrayOf(frameCount.toLong(), 256L)
                ).use { tensor ->
                    session.run(mapOf("input" to tensor)).use { results ->
                        @Suppress("UNCHECKED_CAST")
                        val outMatrix = results[0].value as? Array<FloatArray>
                        if (outMatrix != null && outMatrix.size == frameCount) {
                            val mix = (state.timbreIntensity * 0.45f).coerceIn(0f, 1f)
                            for (f in 0 until frameCount) {
                                val row = outMatrix[f]
                                val base = f * 256
                                for (k in 0 until 256) {
                                    output[base + k] = (output[base + k] * (1f - mix) + row[k] * mix)
                                        .coerceIn(-0.98f, 0.98f)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Keep DSP-processed output cleanly if ORT fails
            }
        }
        return output
    }

    private fun readRing(ring: FloatArray, writeIdx: Int, delay: Float): Float {
        var idx = writeIdx.toFloat() - delay
        while (idx < 0f) idx += ring.size.toFloat()
        val i0 = floor(idx).toInt() % ring.size
        val i1 = (i0 + 1) % ring.size
        val frac = idx - floor(idx)
        return ring[i0] * (1f - frac) + ring[i1] * frac
    }

    fun playLastProcessedClip() {
        val path = _uiState.value.lastProcessedWavPath ?: return
        val file = File(path)
        if (!file.exists()) return

        playJob?.cancel()
        playJob = scope.launch {
            _uiState.update { it.copy(isPlayingClip = true, statusMessage = "Memainkan suara AI...") }
            var track: AudioTrack? = null
            try {
                val allBytes = file.readBytes()
                if (allBytes.size <= 44) return@launch
                val pcmBytes = allBytes.copyOfRange(44, allBytes.size)
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                var offset = 0
                while (currentCoroutineContext().isActive && offset < pcmBytes.size) {
                    val chunk = minOf(minBuf, pcmBytes.size - offset)
                    val written = track.write(pcmBytes, offset, chunk)
                    if (written <= 0) break
                    offset += written
                }
                track.stop()
            } catch (_: Throwable) {
                // Ignored
            } finally {
                track?.release()
                _uiState.update {
                    it.copy(
                        isPlayingClip = false,
                        statusMessage = "Main balik selesai."
                    )
                }
            }
        }
    }

    fun buildShareVoiceNoteIntent(targetPackage: String? = null): Intent? {
        val path = _uiState.value.lastProcessedWavPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        val pm = context.packageManager
        return if (!targetPackage.isNullOrBlank() && sendIntent.resolveActivity(pm) == null) {
            // Fall back to system share sheet if target app not installed
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            sendIntent
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveLoopbackInternal(): Boolean {
        val safety = checkEarphoneSafety()
        if (!safety.isExternalAudioConnected) {
            _uiState.update {
                it.copy(
                    isLoopbackRunning = false,
                    statusMessage = "Disekat Anti-Feedback: Sambung fon telinga/splitter dahulu."
                )
            }
            return false
        }

        if (NativeAudioBridge.isNativeLoaded) {
            syncNativeParams()
            val ok = NativeAudioBridge.nativeStartLoopback()
            _uiState.update {
                it.copy(
                    isLoopbackRunning = ok,
                    statusMessage = if (ok) {
                        "Live Oboe AAudio Loopback aktif ke ${safety.outputDeviceName}."
                    } else {
                        "Gagal memulakan aliran Oboe AAudio."
                    }
                )
            }
            if (ok) {
                scope.launch {
                    while (_uiState.value.isLoopbackRunning) {
                        val rms = NativeAudioBridge.nativeGetInputRms()
                        _uiState.update { it.copy(liveInputRms = rms) }
                        delay(80L)
                    }
                }
            }
            return ok
        }

        // Kotlin AudioRecord -> DSP -> AudioTrack fallback when native .so isn't packaged in debug preview
        fallbackLoopbackJob?.cancel()
        fallbackLoopbackJob = scope.launch {
            val minIn = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            val minOut = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minIn
            )
            val trk = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val buf = ShortArray(512)
            val fBuf = FloatArray(512)
            val outShort = ShortArray(512)
            try {
                rec.startRecording()
                trk.play()
                _uiState.update {
                    it.copy(
                        isLoopbackRunning = true,
                        statusMessage = "Live Loopback aktif ke ${safety.outputDeviceName}."
                    )
                }
                while (currentCoroutineContext().isActive && _uiState.value.isLoopbackRunning) {
                    val read = rec.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val v = buf[i] / 32768f
                            fBuf[i] = v
                            sumSq += v * v
                        }
                        val proc = processWithKotlinAndOrt(fBuf.copyOf(read), _uiState.value)
                        for (i in 0 until read) {
                            outShort[i] = (proc[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        }
                        trk.write(outShort, 0, read)
                        _uiState.update { it.copy(liveInputRms = sqrt(sumSq / read).toFloat()) }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { rec.stop() } catch (_: Throwable) {}
                try { trk.stop() } catch (_: Throwable) {}
                rec.release()
                trk.release()
                _uiState.update { it.copy(isLoopbackRunning = false, liveInputRms = 0f) }
            }
        }
        return true
    }

    fun stopLiveLoopbackInternal() {
        _uiState.update { it.copy(isLoopbackRunning = false, liveInputRms = 0f) }
        fallbackLoopbackJob?.cancel()
        if (NativeAudioBridge.isNativeLoaded) {
            try {
                NativeAudioBridge.nativeStopLoopback()
            } catch (_: Throwable) {
            }
        }
    }

    private fun writeWavFile(file: File, samples: FloatArray, sampleRate: Int) {
        val pcmByteCount = samples.size * 2
        val totalDataLen = pcmByteCount + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmByteCount)
        }.array()

        val pcmStream = ByteArrayOutputStream(pcmByteCount)
        val sampleBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val clamped = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            sampleBuf.clear()
            sampleBuf.putShort(clamped)
            pcmStream.write(sampleBuf.array())
        }

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(pcmStream.toByteArray())
        }
    }
}
