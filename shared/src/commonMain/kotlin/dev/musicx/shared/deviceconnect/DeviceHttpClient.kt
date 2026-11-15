/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Dedicated WebSocket HTTP client for DeviceConnect (with the WebSockets plugin
 * installed). Mirrors the existing [dev.musicx.shared.network.createHttpClient].
 */

package dev.musicx.shared.deviceconnect

import io.ktor.client.HttpClient

expect fun createDeviceConnectHttpClient(): HttpClient
