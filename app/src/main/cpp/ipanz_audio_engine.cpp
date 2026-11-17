#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <oboe/Oboe.h>
#include <onnxruntime_cxx_api.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "iPanzAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kSampleRate = 48000;
constexpr int kFrameSize = 256;
constexpr int kRingCapacity = 16384;
constexpr float kPi = 3.14159265358979323846f;

struct VoiceEngineParams {
    std::atomic<int> presetId{0};
    std::atomic<float> pitchRatio{1.0f};
    std::atomic<float> formantShift{1.0f};
    std::atomic<float> timbreIntensity{0.65f};
    std::atomic<float> denoiseStrength{0.75f};
    std::atomic<float> inputRms{0.0f};
    std::atomic<float> outputRms{0.0f};
    std::atomic<bool> nnapiActive{false};
};

// RNNoise-style multi-band spectral gate + adaptive noise floor tracker
class AdaptiveSpectralDenoiser {
public:
    AdaptiveSpectralDenoiser() {
        reset();
    }

    void reset() {
        noiseFloor_ = 0.0025f;
        speechProb_ = 0.0f;
        hpPrevIn_ = 0.0f;
        hpPrevOut_ = 0.0f;
        lpPrevOut_ = 0.0f;
        gateEnv_ = 0.0f;
    }

    void processInPlace(float* buffer, int count, float strength) {
        if (count <= 0) return;

        // 1. High-pass rumble filter (~85 Hz @ 48 kHz) to kill fan/handling thumps
        const float hpAlpha = 0.9889f;
        // 2. Gentle de-hiss low-pass (~14.5 kHz)
        const float lpAlpha = 0.76f;

        double sumSq = 0.0;
        for (int i = 0; i < count; ++i) {
            float x = buffer[i];
            float hp = hpAlpha * (hpPrevOut_ + x - hpPrevIn_);
            hpPrevIn_ = x;
            hpPrevOut_ = hp;

            float lp = lpPrevOut_ + lpAlpha * (hp - lpPrevOut_);
            lpPrevOut_ = lp;

            buffer[i] = lp;
            sumSq += static_cast<double>(lp) * static_cast<double>(lp);
        }

        float rms = static_cast<float>(std::sqrt(sumSq / count));

        // Adaptive noise floor tracking (slower upward, faster downward)
        if (rms < noiseFloor_ * 1.35f) {
            noiseFloor_ = 0.96f * noiseFloor_ + 0.04f * rms;
        } else {
            noiseFloor_ = 0.9985f * noiseFloor_ + 0.0015f * rms;
        }
        noiseFloor_ = std::clamp(noiseFloor_, 0.0008f, 0.045f);

        // Soft VAD probability estimate
        float snr = rms / (noiseFloor_ + 1e-6f);
        float targetProb = std::clamp((snr - 1.35f) / 2.4f, 0.0f, 1.0f);
        float smooth = (targetProb > speechProb_) ? 0.35f : 0.08f;
        speechProb_ += smooth * (targetProb - speechProb_);

        // Spectral attenuation gain scaled by user denoiseStrength
        float minGain = 1.0f - std::clamp(strength, 0.0f, 1.0f) * 0.92f;
        float targetGain = minGain + (1.0f - minGain) * speechProb_;
        float envCoeff = (targetGain > gateEnv_) ? 0.45f : 0.12f;
        gateEnv_ += envCoeff * (targetGain - gateEnv_);

        for (int i = 0; i < count; ++i) {
            float s = buffer[i] * gateEnv_;
            // Soft limiter
            buffer[i] = std::tanh(s * 1.05f);
        }
    }

private:
    float noiseFloor_{0.0025f};
    float speechProb_{0.0f};
    float hpPrevIn_{0.0f};
    float hpPrevOut_{0.0f};
    float lpPrevOut_{0.0f};
    float gateEnv_{0.0f};
};

// Real-time dual-tap delay-line pitch & formant processor (zero allocation on audio thread)
class RealTimePitchFormantShifter {
public:
    RealTimePitchFormantShifter() {
        ring_.fill(0.0f);
    }

    void reset() {
        ring_.fill(0.0f);
        writeIdx_ = 0;
        phase_ = 0.0f;
        bandState1_ = 0.0f;
        bandState2_ = 0.0f;
        cyborgPhase_ = 0.0f;
    }

    void process(
        const float* input,
        float* output,
        int count,
        int presetId,
        float pitchRatio,
        float formantShift,
        float timbreIntensity
    ) {
        const float clampedPitch = std::clamp(pitchRatio, 0.55f, 1.85f);
        const float clampedFormant = std::clamp(formantShift, 0.65f, 1.60f);
        const float windowLen = 1024.0f;
        const float phaseStep = (1.0f - clampedPitch) / windowLen;

        for (int i = 0; i < count; ++i) {
            ring_[writeIdx_] = input[i];

            float shifted = input[i];
            if (std::abs(clampedPitch - 1.0f) > 0.015f) {
                float p1 = phase_;
                float p2 = p1 + 0.5f;
                if (p2 >= 1.0f) p2 -= 1.0f;

                float d1 = p1 * windowLen;
                float d2 = p2 * windowLen;

                float s1 = readInterpolated(d1);
                float s2 = readInterpolated(d2);

                // Raised-cosine crossfade between dual taps
                float w1 = 0.5f - 0.5f * std::cos(2.0f * kPi * p1);
                float w2 = 1.0f - w1;
                shifted = s1 * w1 + s2 * w2;

                phase_ += phaseStep;
                while (phase_ >= 1.0f) phase_ -= 1.0f;
                while (phase_ < 0.0f) phase_ += 1.0f;
            }

            // Formant emphasis & character shaping
            float lp = bandState1_ + 0.16f * (shifted - bandState1_);
            bandState1_ = lp;
            float hp = shifted - lp;
            float bp = bandState2_ + 0.34f * (hp - bandState2_);
            bandState2_ = bp;

            float shaped = shifted;
            switch (presetId) {
                case 0: // Gadis Anime HD
                case 1: // Wanita Dewasa Natural
                    shaped = shifted + hp * (0.35f * clampedFormant * timbreIntensity) - lp * 0.18f;
                    break;
                case 2: // Komander Garau
                    shaped = shifted + lp * (0.65f * timbreIntensity) - hp * 0.15f;
                    break;
                case 3: { // Cyborg Taktikal (ring modulation + metallic resonance)
                    cyborgPhase_ += 2.0f * kPi * 115.0f / static_cast<float>(kSampleRate);
                    if (cyborgPhase_ > 2.0f * kPi) cyborgPhase_ -= 2.0f * kPi;
                    float mod = 0.65f + 0.35f * std::sin(cyborgPhase_);
                    shaped = (shifted * mod) + bp * (0.45f * timbreIntensity);
                    break;
                }
                case 4: // Radio Skuad E-Sukan (bandpass punch + soft grit)
                    shaped = std::tanh((bp * 1.75f) * (1.0f + 0.4f * timbreIntensity));
                    break;
                default: // Pro Mic Enhancer
                    shaped = shifted + bp * (0.22f * timbreIntensity);
                    break;
            }

            output[i] = std::clamp(std::tanh(shaped * 0.95f), -0.98f, 0.98f);
            writeIdx_ = (writeIdx_ + 1) % kRingCapacity;
        }
    }

private:
    float readInterpolated(float delaySamples) const {
        float idx = static_cast<float>(writeIdx_) - delaySamples;
        while (idx < 0.0f) idx += static_cast<float>(kRingCapacity);
        int i0 = static_cast<int>(idx) % kRingCapacity;
        int i1 = (i0 + 1) % kRingCapacity;
        float frac = idx - std::floor(idx);
        return ring_[i0] * (1.0f - frac) + ring_[i1] * frac;
    }

    std::array<float, kRingCapacity> ring_{};
    int writeIdx_{0};
    float phase_{0.0f};
    float bandState1_{0.0f};
    float bandState2_{0.0f};
    float cyborgPhase_{0.0f};
};

class OnnxModelRunner {
public:
    OnnxModelRunner() : env_(ORT_LOGGING_LEVEL_WARNING, "iPanzVoiceORT") {}

    bool loadModelFromAsset(AAssetManager* assetMgr, const std::string& assetPath) {
        if (!assetMgr) return false;
        AAsset* asset = AAssetManager_open(assetMgr, assetPath.c_str(), AASSET_MODE_BUFFER);
        if (!asset) {
            LOGE("Failed to open asset: %s", assetPath.c_str());
            return false;
        }
        off64_t length = AAsset_getLength64(asset);
        const void* buffer = AAsset_getBuffer(asset);
        std::vector<uint8_t> fallbackCopy;
        if (!buffer && length > 0) {
            fallbackCopy.resize(static_cast<size_t>(length));
            AAsset_read(asset, fallbackCopy.data(), static_cast<size_t>(length));
            buffer = fallbackCopy.data();
        }

        bool ok = false;
        try {
            Ort::SessionOptions opts;
            opts.SetIntraOpNumThreads(2);
            opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_BASIC);
            bool nnapi = tryEnableNnapi(opts);

            std::lock_guard<std::mutex> lock(sessionMutex_);
            session_ = std::make_unique<Ort::Session>(
                env_, buffer, static_cast<size_t>(length), opts
            );
            nnapiEnabled_ = nnapi;
            ok = true;
            LOGI("Loaded ONNX model %s (%lld bytes, nnapi=%d)", assetPath.c_str(), static_cast<long long>(length), nnapi ? 1 : 0);
        } catch (const Ort::Exception& e) {
            LOGE("ORT exception loading %s: %s", assetPath.c_str(), e.what());
        } catch (...) {
            LOGE("Unknown error loading ONNX model %s", assetPath.c_str());
        }

        AAsset_close(asset);
        return ok;
    }

    void runSpectralEnvelopeFrame(std::array<float, kFrameSize>& frame, float mix) {
        std::lock_guard<std::mutex> lock(sessionMutex_);
        if (!session_) return;
        try {
            Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(
                OrtArenaAllocator, OrtMemTypeDefault
            );
            std::array<int64_t, 2> shape{1, kFrameSize};
            Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
                memInfo, frame.data(), frame.size(), shape.data(), shape.size()
            );
            const char* inputNames[] = {"input"};
            const char* outputNames[] = {"output"};
            auto outputs = session_->Run(
                Ort::RunOptions{nullptr}, inputNames, &inputTensor, 1, outputNames, 1
            );
            if (!outputs.empty() && outputs[0].IsTensor()) {
                const float* outData = outputs[0].GetTensorData<float>();
                float clampedMix = std::clamp(mix, 0.0f, 1.0f);
                for (int i = 0; i < kFrameSize; ++i) {
                    float blended = frame[i] * (1.0f - clampedMix) + outData[i] * clampedMix;
                    frame[i] = std::clamp(blended, -0.98f, 0.98f);
                }
            }
        } catch (const Ort::Exception& e) {
            LOGE("ORT inference error: %s", e.what());
        }
    }

    bool isNnapiEnabled() const { return nnapiEnabled_; }

private:
    bool tryEnableNnapi(Ort::SessionOptions& opts) {
        using NnapiFn = OrtStatus* (*)(OrtSessionOptions*, uint32_t);
        void* handle = dlopen("libonnxruntime.so", RTLD_LAZY | RTLD_NOLOAD);
        if (!handle) {
            handle = dlopen("libonnxruntime.so", RTLD_LAZY);
        }
        if (!handle) return false;
        auto fn = reinterpret_cast<NnapiFn>(
            dlsym(handle, "OrtSessionOptionsAppendExecutionProvider_Nnapi")
        );
        if (!fn) return false;
        OrtSessionOptions* rawOpts = opts;
        OrtStatus* status = fn(rawOpts, 0);
        if (status != nullptr) {
            Ort::GetApi().ReleaseStatus(status);
            return false;
        }
        return true;
    }

    Ort::Env env_;
    std::unique_ptr<Ort::Session> session_;
    std::mutex sessionMutex_;
    bool nnapiEnabled_{false};
};

// Full-Duplex Oboe Live Loopback Engine (AAudio Low-Latency)
class LiveLoopbackEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    LiveLoopbackEngine(VoiceEngineParams& params, OnnxModelRunner& onnx)
        : params_(params), onnx_(onnx) {}

    ~LiveLoopbackEngine() override {
        stop();
    }

    bool start() {
        std::lock_guard<std::mutex> lock(streamLock_);
        if (running_) return true;

        denoiser_.reset();
        shifter_.reset();
        scratch_.resize(2048, 0.0f);

        oboe::AudioStreamBuilder outBuilder;
        outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(kSampleRate)
            ->setDataCallback(this)
            ->setErrorCallback(this);

        oboe::Result outRes = outBuilder.openStream(outputStream_);
        if (outRes != oboe::Result::OK || !outputStream_) {
            LOGE("Oboe output open failed: %s", oboe::convertToText(outRes));
            return false;
        }

        oboe::AudioStreamBuilder inBuilder;
        inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setInputPreset(oboe::InputPreset::VoiceCommunication)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(outputStream_->getSampleRate());

        oboe::Result inRes = inBuilder.openStream(inputStream_);
        if (inRes != oboe::Result::OK || !inputStream_) {
            LOGE("Oboe input open failed: %s", oboe::convertToText(inRes));
            outputStream_->close();
            outputStream_.reset();
            return false;
        }

        inputStream_->requestStart();
        outputStream_->requestStart();
        running_ = true;
        LOGI("Oboe Low-Latency Loopback started @ %d Hz", outputStream_->getSampleRate());
        return true;
    }

    void stop() {
        std::lock_guard<std::mutex> lock(streamLock_);
        running_ = false;
        if (outputStream_) {
            outputStream_->stop();
            outputStream_->close();
            outputStream_.reset();
        }
        if (inputStream_) {
            inputStream_->stop();
            inputStream_->close();
            inputStream_.reset();
        }
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* /*audioStream*/,
        void* audioData,
        int32_t numFrames
    ) override {
        auto* out = static_cast<float*>(audioData);
        if (!running_ || !inputStream_) {
            std::memset(out, 0, sizeof(float) * numFrames);
            return oboe::DataCallbackResult::Continue;
        }

        if (static_cast<int>(scratch_.size()) < numFrames) {
            std::memset(out, 0, sizeof(float) * numFrames);
            return oboe::DataCallbackResult::Continue;
        }

        auto readRes = inputStream_->read(scratch_.data(), numFrames, 0);
        int framesRead = (readRes) ? readRes.value() : 0;
        if (framesRead <= 0) {
            std::memset(out, 0, sizeof(float) * numFrames);
            return oboe::DataCallbackResult::Continue;
        }

        // Compute input RMS
        double inSum = 0.0;
        for (int i = 0; i < framesRead; ++i) {
            inSum += scratch_[i] * scratch_[i];
        }
        params_.inputRms.store(static_cast<float>(std::sqrt(inSum / framesRead)));

        // Step 1: Real-time adaptive RNNoise-style denoise
        denoiser_.processInPlace(scratch_.data(), framesRead, params_.denoiseStrength.load());

        // Step 2: Real-time Pitch & Formant Shifter
        shifter_.process(
            scratch_.data(),
            out,
            framesRead,
            params_.presetId.load(),
            params_.pitchRatio.load(),
            params_.formantShift.load(),
            params_.timbreIntensity.load()
        );

        // Zero-fill any tail if read was short
        if (framesRead < numFrames) {
            std::memset(out + framesRead, 0, sizeof(float) * (numFrames - framesRead));
        }

        double outSum = 0.0;
        for (int i = 0; i < numFrames; ++i) {
            outSum += out[i] * out[i];
        }
        params_.outputRms.store(static_cast<float>(std::sqrt(outSum / numFrames)));

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) override {
        LOGE("Oboe stream error after close: %s", oboe::convertToText(error));
        running_ = false;
    }

private:
    VoiceEngineParams& params_;
    OnnxModelRunner& onnx_;
    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> outputStream_;
    std::vector<float> scratch_;
    AdaptiveSpectralDenoiser denoiser_;
    RealTimePitchFormantShifter shifter_;
    std::mutex streamLock_;
    std::atomic<bool> running_{false};
};

// Global singleton instances managed via JNI lifecycle
struct NativeEngineState {
    VoiceEngineParams params;
    OnnxModelRunner onnxRunner;
    std::unique_ptr<LiveLoopbackEngine> loopback;
};

NativeEngineState& getEngine() {
    static NativeEngineState instance;
    return instance;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jobject assetManager,
    jstring modelAssetPath
) {
    auto& engine = getEngine();
    if (!engine.loopback) {
        engine.loopback = std::make_unique<LiveLoopbackEngine>(engine.params, engine.onnxRunner);
    }
    const char* pathChars = env->GetStringUTFChars(modelAssetPath, nullptr);
    std::string pathStr(pathChars ? pathChars : "");
    if (pathChars) env->ReleaseStringUTFChars(modelAssetPath, pathChars);

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    bool loaded = engine.onnxRunner.loadModelFromAsset(mgr, pathStr);
    engine.params.nnapiActive.store(engine.onnxRunner.isNnapiEnabled());
    return loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeUpdateParams(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint presetId,
    jfloat pitchRatio,
    jfloat formantShift,
    jfloat timbreIntensity,
    jfloat denoiseStrength
) {
    auto& p = getEngine().params;
    p.presetId.store(presetId);
    p.pitchRatio.store(pitchRatio);
    p.formantShift.store(formantShift);
    p.timbreIntensity.store(timbreIntensity);
    p.denoiseStrength.store(denoiseStrength);
}

JNIEXPORT jboolean JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeStartLoopback(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    auto& engine = getEngine();
    if (!engine.loopback) {
        engine.loopback = std::make_unique<LiveLoopbackEngine>(engine.params, engine.onnxRunner);
    }
    return engine.loopback->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeStopLoopback(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    auto& engine = getEngine();
    if (engine.loopback) {
        engine.loopback->stop();
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeProcessClip(
    JNIEnv* env,
    jobject /*thiz*/,
    jfloatArray pcmInput
) {
    if (!pcmInput) return nullptr;
    jsize len = env->GetArrayLength(pcmInput);
    if (len <= 0) return pcmInput;

    std::vector<float> buf(static_cast<size_t>(len));
    env->GetFloatArrayRegion(pcmInput, 0, len, buf.data());

    auto& engine = getEngine();
    const int preset = engine.params.presetId.load();
    const float pitch = engine.params.pitchRatio.load();
    const float formant = engine.params.formantShift.load();
    const float timbre = engine.params.timbreIntensity.load();
    const float denoise = engine.params.denoiseStrength.load();

    AdaptiveSpectralDenoiser denoiser;
    RealTimePitchFormantShifter shifter;

    std::vector<float> out(static_cast<size_t>(len));
    // Process in 256-sample frames so RNNoise denoiser + Pitch/Formant + ONNX model operate in tandem
    std::array<float, kFrameSize> ortFrame{};
    for (int offset = 0; offset < len; offset += kFrameSize) {
        int chunk = std::min(kFrameSize, static_cast<int>(len) - offset);
        denoiser.processInPlace(buf.data() + offset, chunk, denoise);
        shifter.process(buf.data() + offset, out.data() + offset, chunk, preset, pitch, formant, timbre);

        if (chunk == kFrameSize) {
            std::copy_n(out.data() + offset, kFrameSize, ortFrame.begin());
            engine.onnxRunner.runSpectralEnvelopeFrame(ortFrame, timbre * 0.45f);
            std::copy_n(ortFrame.begin(), kFrameSize, out.data() + offset);
        }
    }

    jfloatArray result = env->NewFloatArray(len);
    if (result) {
        env->SetFloatArrayRegion(result, 0, len, out.data());
    }
    return result;
}

JNIEXPORT jfloat JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeGetInputRms(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return getEngine().params.inputRms.load();
}

JNIEXPORT jboolean JNICALL
Java_com_ipanz_mod_x7pro_voice_NativeAudioBridge_nativeIsNnapiActive(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return getEngine().params.nnapiActive.load() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
