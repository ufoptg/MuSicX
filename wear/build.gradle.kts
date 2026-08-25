plugins {
    id("com.android.application")
    alias(libs.plugins.compose.compiler)
}

// Mirror the phone app's env-var overrides so PR/CI builds stay consistent.
val applicationIdOverride = System.getenv("METROLIST_APPLICATION_ID")?.takeIf { it.isNotBlank() }
val debugKeystorePathOverride = System.getenv("METROLIST_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val debugKeystorePassword = System.getenv("METROLIST_DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
val debugKeyAlias = System.getenv("METROLIST_DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
val debugKeyPassword = System.getenv("METROLIST_DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"

android {
    namespace = "dev.ufoptg.musicx.wear"
    compileSdk = 37

    defaultConfig {
        // The wear app must share the phone's applicationId for the Wear Data Layer to pair them.
        // In PR builds the phone gets a unique id (e.g. dev.ufoptg.musicx.pr.p42) so the wear
        // module reads the same override; without it the two APKs would have different ids and
        // the Data Layer would refuse to pair them.
        applicationId = applicationIdOverride ?: "dev.ufoptg.musicx"
        minSdk = 30          // Wear OS 3+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Mirror the phone app's signing so phone + watch share the same certificate
    // (required for the Wear Data Layer to pair the two apps).
    val persistentDebugKeystore = rootProject.file("app/persistent-debug.keystore")
    val workflowDebugKeystoreFile = debugKeystorePathOverride
        ?.let { rootProject.file("app/$it") }

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("workflowDebug") {
            storeFile = workflowDebugKeystoreFile ?: persistentDebugKeystore
            storePassword = debugKeystorePassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
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
            // Match phone: only append .debug when there's no applicationId override
            // (PR builds pass a fully-qualified id already).
            if (applicationIdOverride == null) {
                applicationIdSuffix = ".debug"
            }
            signingConfig = when {
                workflowDebugKeystoreFile != null -> signingConfigs.getByName("workflowDebug")
                persistentDebugKeystore.exists()  -> signingConfigs.getByName("persistentDebug")
                else                              -> signingConfigs.getByName("debug")
            }
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
