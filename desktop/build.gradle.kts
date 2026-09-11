import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("ir.mahozad.vlc-setup") version "0.1.0"
}

kotlin {
    jvmToolchain(21)
}

val appResourcesDir = layout.projectDirectory.dir("appResources")

vlcSetup {
    vlcVersion = "3.0.21"
    // UPX-compressing the VLC libs makes libvlc decompress+probe every plugin during
    // NativeDiscovery (libvlc_new), which cost ~108s on first launch. Keep them uncompressed
    // for fast startup; the jpackage installer compresses the payload anyway.
    shouldCompressVlcFiles = false
    // Filtered plugin set lacks HTTP access + some demuxers; include all for reliable audio.
    shouldIncludeAllVlcFiles = true
    pathToCopyVlcWindowsFilesTo = appResourcesDir.dir("windows/vlc").asFile
    pathToCopyVlcLinuxFilesTo = appResourcesDir.dir("linux/vlc").asFile
    pathToCopyVlcMacosFilesTo = appResourcesDir.dir("macos/vlc").asFile
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.innertubex.desktop)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)

    implementation(libs.vlcj)
    implementation(libs.vlcj.natives)
}

compose.desktop {
    application {
        mainClass = "com.metrolist.music.desktop.MainKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"

        // ProGuard on windows-latest hits a Compose Desktop NPE (getStandardOutput must not be null).
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // EXE only while iterating slices (faster CI than MSI+WiX).
            targetFormats(TargetFormat.Exe)
            packageName = "MuSicX"
            packageVersion = "13.11.0"
            description = "MuSicX Desktop — YouTube Music client with Spotify integration"
            copyright = "© 2026 ufoptg / MuSicX contributors"
            vendor = "ufoptg"
            // Bundled LibVLC from vlc-setup (appResources/<os>/vlc)
            appResourcesRootDir.set(appResourcesDir.asFile)

            windows {
                menuGroup = "MuSicX"
                upgradeUuid = "A7C3E9F1-2B4D-4E6A-9C8D-1F0E2D3C4B5A"
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("artwork/ic_launcher.ico"))
            }
        }
    }
}
