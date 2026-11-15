package dev.musicx.android.listentogether

import android.app.Application
import dev.musicx.android.deviceconnect.AndroidDeviceConnect
import dev.musicx.shared.deviceconnect.DcPlaybackAction
import dev.musicx.shared.deviceconnect.DcPlaybackCommand
import dev.musicx.shared.settings.Settings
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [33])
class ListenTogetherLocalVolumeTest {
    @Test
    fun guestVolumeAndMuteReachLocalPlaybackWhileTransportAndQueueStayLocked() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
        @Suppress("UNCHECKED_CAST")
        val guestLock =
            AndroidListenTogetherManager::class.java
                .getDeclaredField("_isGuestPlaybackLocked")
                .apply { isAccessible = true }
                .get(null) as MutableStateFlow<Boolean>
        val previousLock = guestLock.value
        try {
            Settings.shared.listenTogetherSyncHostVolume = false
            guestLock.value = true
            for (volume in listOf(0.4f, 0f, 0.4f)) {
                assertFalse(AndroidDeviceConnect.intercept(DcPlaybackCommand(DcPlaybackAction.VOLUME, volume = volume)))
            }
            for (action in listOf(DcPlaybackAction.PAUSE, DcPlaybackAction.SEEK, DcPlaybackAction.REMOVE, DcPlaybackAction.REORDER)) {
                assertTrue(AndroidDeviceConnect.intercept(DcPlaybackCommand(action)))
            }
        } finally {
            guestLock.value = previousLock
            Settings.replaceSharedForTest(null)
        }
    }
}
