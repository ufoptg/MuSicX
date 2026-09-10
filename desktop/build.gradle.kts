import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
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
}

compose.desktop {
    application {
        mainClass = "com.metrolist.music.desktop.MainKt"

        // ProGuard on windows-latest hits a Compose Desktop NPE (getStandardOutput must not be null).
        // Re-enable once packaging is stable / we have keep rules for Spotify + InnerTubeX.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MuSicX"
            // Windows MSI/EXE require MAJOR.MINOR.BUILD
            packageVersion = "13.11.0"
            description = "MuSicX Desktop — YouTube Music client with Spotify integration"
            copyright = "© 2026 ufoptg / MuSicX contributors"
            vendor = "ufoptg"

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
