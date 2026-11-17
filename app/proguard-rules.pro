# iPanz Mod Release R8 / ProGuard Rules

# Preserve source line numbers for clean diagnostics
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# ONNX Runtime Mobile (Java + JNI)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Google Oboe & Native C++ JNI Bridge
-keep class com.ipanz.mod.x7pro.voice.NativeAudioBridge {
    native <methods>;
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Shizuku API & Binder IPC
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.ipanz.mod.x7pro.shizuku.** { *; }
-keep class com.ipanz.mod.x7pro.aidl.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Kotlinx Serialization & Type-Safe Navigation Compose Routes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ipanz.mod.x7pro.**$$serializer { *; }
-keepclassmembers class com.ipanz.mod.x7pro.** {
    *** Companion;
}
-keepclasseswithmembers class com.ipanz.mod.x7pro.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose & ViewModel / SavedState
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.kotlin.**
