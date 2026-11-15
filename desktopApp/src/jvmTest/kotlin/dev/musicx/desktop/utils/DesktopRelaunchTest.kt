package dev.musicx.desktop.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopRelaunchTest {
    private val recovery = listOf("--crash-report", "report-id")

    @Test
    fun nativePackagesForwardRecoveryArgumentsAndRestartWithoutTheReport() {
        for (launcher in listOf(
            "/tmp/app image/MuSicX/bin/MuSicX",
            "/tmp/app image/MuSicX/bin/MuSicX.bin",
            "/opt/metrolist/bin/MuSicX",
            "/app/metrolist/bin/MuSicX",
            "C:\\Program Files\\MuSicX\\MuSicX.exe",
            "/Applications/MuSicX.app/Contents/MacOS/MuSicX",
        )) {
            val arguments = listOf("--profile", "profile with spaces")

            fun command(args: List<String>) = desktopRestartCommand(null, launcher, args, emptyList(), "", "", launcher)
            assertEquals(listOf(launcher) + arguments + recovery, command(arguments) + recovery)
            assertEquals(listOf(launcher) + arguments, command(recovery + arguments))
        }
    }

    @Test
    fun appImageTakesPrecedenceOverItsMountedLauncher() {
        val image = "/home/listener/MuSicX Desktop.AppImage"
        assertEquals(
            listOf(image, "--profile", "test"),
            desktopRestartCommand(
                image,
                "/tmp/.mount_Metro/bin/MuSicX",
                recovery + listOf("--profile", "test"),
                emptyList(),
                "",
                "",
                "/tmp/.mount_Metro/bin/MuSicX",
            ),
        )
    }

    @Test
    fun jpackagePathDoesNotReplayJavaVmOptions() {
        for (command in listOf(null, "/runtime/bin/java", "C:\\Runtime\\bin\\javaw.exe")) {
            assertEquals(
                listOf("/opt/MuSicX"),
                desktopRestartCommand(
                    null,
                    command,
                    listOf("-Xmx2g", "-cp", "app.jar", "Main") + recovery,
                    emptyList(),
                    "",
                    "",
                    "/opt/MuSicX",
                ),
            )
        }
    }

    @Test
    fun javaAndGradleKeepVmOptionsAndApplicationEntryPoint() {
        for (java in listOf("/jdk/bin/java", "C:\\Java\\bin\\java.exe", "C:\\Java\\bin\\javaw.exe")) {
            val arguments = listOf("-Xmx2g", "-Duser.home=/tmp/profile", "-cp", "classes and libs", "Main")
            assertEquals(
                listOf(java) + arguments + listOf("--profile", "test"),
                desktopRestartCommand(null, java, arguments + recovery + listOf("--profile", "test"), emptyList(), "", "Main"),
            )
            assertEquals(
                listOf(java) + arguments + recovery,
                desktopRestartCommand(null, java, null, arguments.take(2), "classes and libs", "Main") + recovery,
            )
        }
    }

    @Test
    fun javaJarKeepsTheJarPathIncludingSpaces() {
        val arguments = listOf("-Xmx2g", "-jar", "/home/listener/MuSicX Desktop.jar")
        assertEquals(
            listOf("/jdk/bin/java") + arguments,
            desktopRestartCommand(
                null,
                "/jdk/bin/java",
                arguments + recovery,
                emptyList(),
                "/home/listener/MuSicX Desktop.jar",
                "/home/listener/MuSicX",
            ),
        )
    }

    @Test
    fun javaJarReconstructsTheEntryPointWhenProcessArgumentsAreUnavailable() {
        val jar = "/home/listener/MuSicX Desktop.jar"
        assertEquals(
            listOf("/jdk/bin/java", "-Xmx2g", "-jar", jar) + recovery,
            desktopRestartCommand(null, "/jdk/bin/java", null, listOf("-Xmx2g"), jar, jar) + recovery,
        )
    }

    @Test
    fun childLauncherReloadsItsConfigurationAndPreservesProfileEnvironment() {
        val child = ProcessBuilder("/opt/metrolist")
        child.environment()["_JPACKAGE_LAUNCHER"] = "parent-library-path-hash"
        child.environment()["XDG_DATA_HOME"] = "/tmp/isolated profile"
        child.environment()["JAVA_TOOL_OPTIONS"] = "-Duser.home=/tmp/isolated"
        child.prepareDesktopRelaunch()
        assertFalse(child.environment().containsKey("_JPACKAGE_LAUNCHER"))
        assertEquals("/tmp/isolated profile", child.environment()["XDG_DATA_HOME"])
        assertEquals("-Duser.home=/tmp/isolated", child.environment()["JAVA_TOOL_OPTIONS"])
    }
}
