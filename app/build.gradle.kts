import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val sdkDirPath: String =
  System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: ""
val hasLocalNdkAndCmake: Boolean =
  sdkDirPath.isNotEmpty() &&
    file("$sdkDirPath/ndk").let { it.exists() && (it.listFiles()?.isNotEmpty() == true) } &&
    file("$sdkDirPath/cmake").let { it.exists() && (it.listFiles()?.isNotEmpty() == true) }
val enableNativeCxxBuild: Boolean =
  hasLocalNdkAndCmake ||
    project.findProperty("forceNativeBuild")?.toString()?.toBoolean() == true

val ortNativeDir = layout.buildDirectory.dir("generated/onnxruntime-native")

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  if (enableNativeCxxBuild) {
    ndkVersion = "27.2.12479018"
  }

  defaultConfig {
    applicationId = "com.ipanz.mod.x7pro"
    minSdk = 24
    targetSdk = 36
    versionCode = 100
    versionName = "1.0.0-rodin"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }

    if (enableNativeCxxBuild) {
      externalNativeBuild {
        cmake {
          cppFlags += listOf("-std=c++17", "-O3", "-fexceptions", "-frtti")
          arguments += listOf(
            "-DANDROID_STL=c++_shared",
            "-DORT_NATIVE_DIR=${ortNativeDir.get().asFile.absolutePath.replace("\\", "/")}"
          )
        }
      }
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isMinifyEnabled = false
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
    prefab = true
    aidl = true
  }

  if (enableNativeCxxBuild) {
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
      }
    }
  }

  packaging {
    jniLibs {
      useLegacyPackaging = false
      pickFirsts += listOf("lib/arm64-v8a/libc++_shared.so")
    }
    resources {
      excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
    }
  }

  androidResources {
    noCompress += listOf("onnx", "ort", "bin")
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val extractOnnxRuntimeNative by tasks.registering(Sync::class) {
  group = "ipanz"
  description = "Mengekstrak header dan libonnxruntime.so daripada AAR onnxruntime-android untuk CMake"
  val ortAarFiles =
    configurations.named("releaseRuntimeClasspath").map { cfg ->
      cfg.incoming
        .artifactView {
          componentFilter { id -> id.displayName.contains("onnxruntime-android") }
        }
        .files
    }
  from(ortAarFiles.map { files -> files.map { zipTree(it) } }) {
    include("headers/**")
    include("jni/arm64-v8a/libonnxruntime.so")
  }
  into(ortNativeDir)
}

val prepareAiModels by tasks.registering(Exec::class) {
  group = "ipanz"
  description = "Menyediakan pek model suara AI ONNX (~49 MB FP16/INT8) ke dalam assets/models/"
  val scriptFile = rootProject.file("scripts/prepare_ai_models.py")
  val modelsDir = project.file("src/main/assets/models")
  val markerFile = project.file("src/main/assets/models/ipanz_models_manifest.json")
  inputs.file(scriptFile)
  outputs.dir(modelsDir)
  onlyIf { !markerFile.exists() }
  commandLine("python3", scriptFile.absolutePath, "--output-dir", modelsDir.absolutePath)
}

tasks.named("preBuild").configure {
  dependsOn(prepareAiModels)
  dependsOn(extractOnnxRuntimeNative)
}

tasks.configureEach {
  if (
    name.startsWith("configureCMake") ||
      name.startsWith("buildCMake") ||
      name.startsWith("externalNativeBuild")
  ) {
    dependsOn(extractOnnxRuntimeNative)
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  // Native Audio DSP + ONNX Runtime Mobile (arm64-v8a Prefab) + Optional Shizuku API
  implementation(libs.google.oboe)
  implementation(libs.onnxruntime.android)
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)

  // Unused template dependencies commented out for fast builds & APK size budget (70-95 MB)
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.converter.moshi)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.retrofit)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
