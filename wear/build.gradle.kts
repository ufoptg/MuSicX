plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.ufoptg.musicx.wear"
    compileSdk = 37

    defaultConfig {
        // Same applicationId as the phone app so the Wear Data Layer pairs the two.
        // Debug builds mirror the phone's `.debug` suffix (dev.ufoptg.musicx.debug).
        applicationId = "dev.ufoptg.musicx"
        minSdk = 30          // Wear OS 3+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        resValue("string", "app_name", "MuSicX")
    }

    // Mirror the phone app's signing so phone + watch share the same certificate
    // (required for the Wear Data Layer to pair the two apps). Locally both fall
    // back to the default debug keystore; in CI both use app/persistent-debug.keystore.
    val persistentDebugKeystore = rootProject.file("app/persistent-debug.keystore")

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig =
                if (persistentDebugKeystore.exists()) signingConfigs.getByName("persistentDebug")
                else signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Unsigned: CI signs with the release KEYSTORE secret (same cert as the phone release).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

dependencies {
    // Compose (versions inherited from the shared version catalog -> matches the app).
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.activity)
    implementation(libs.lifecycle.process)

    // Compose for Wear OS. 1.5.x is built against Compose 1.10+ and is compatible
    // with the project's Compose 1.11.4 runtime.
    implementation("androidx.wear.compose:compose-material:1.5.1")
    implementation("androidx.wear.compose:compose-foundation:1.5.1")

    // Wear Data Layer + Task.await() support.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
