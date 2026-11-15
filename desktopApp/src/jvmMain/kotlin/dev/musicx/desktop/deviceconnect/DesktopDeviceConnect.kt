package dev.musicx.desktop.deviceconnect

import dev.musicx.shared.deviceconnect.DeviceConnectDeviceType
import dev.musicx.shared.deviceconnect.DeviceConnectManager

object DesktopDeviceConnect {
    val manager = DeviceConnectManager(localDeviceType = DeviceConnectDeviceType.DESKTOP)
}
