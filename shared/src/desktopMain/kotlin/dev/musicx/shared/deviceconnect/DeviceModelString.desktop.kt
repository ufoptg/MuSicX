/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.shared.deviceconnect

import dev.musicx.shared.utils.OsUtil
import com.sun.jna.Native
import com.sun.jna.platform.unix.LibC
import com.sun.jna.platform.win32.Kernel32Util

@Suppress("SameReturnValue")
actual fun getDeviceModelString(): String = "Desktop"

internal actual fun getDefaultDeviceName(): String = resolveDesktopDeviceName(::getDesktopHostName)

internal fun resolveDesktopDeviceName(hostName: () -> String): String =
    runCatching(hostName).getOrNull()?.takeIf { it.isNotBlank() } ?: getDeviceModelString()

private fun getDesktopHostName(): String {
    if (OsUtil.isWindows) return Kernel32Util.getComputerName()

    // Read the OS hostname directly; DNS resolution can fail even when a hostname is configured.
    val buffer = ByteArray(256)
    check(LibC.INSTANCE.gethostname(buffer, buffer.size) == 0)
    return Native.toString(buffer)
}
