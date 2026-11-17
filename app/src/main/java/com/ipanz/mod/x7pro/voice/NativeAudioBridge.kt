package com.ipanz.mod.x7pro.voice

import android.content.res.AssetManager

object NativeAudioBridge {

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("ipanz_audio")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun nativeInit(assetManager: AssetManager, modelAssetPath: String): Boolean

    external fun nativeUpdateParams(
        presetId: Int,
        pitchRatio: Float,
        formantShift: Float,
        timbreIntensity: Float,
        denoiseStrength: Float
    )

    external fun nativeStartLoopback(): Boolean

    external fun nativeStopLoopback()

    external fun nativeProcessClip(pcmInput: FloatArray): FloatArray?

    external fun nativeGetInputRms(): Float

    external fun nativeIsNnapiActive(): Boolean
}
