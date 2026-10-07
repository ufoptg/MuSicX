/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.window.Window
import com.metrolist.spotify.SpotifyAuth
import org.cef.callback.CefCookieVisitor
import org.cef.network.CefCookieManager
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Embedded Chromium Spotify sign-in. Loads [SpotifyAuth.LOGIN_URL] and polls
 * CEF cookies for `sp_dc` (and optional `sp_key`), mirroring [LoginWebViewWindow].
 */
@Composable
fun SpotifyLoginWindow(
    onSignedIn: (spDc: String, spKey: String) -> Unit,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Sign in to Spotify",
    ) {
        DesktopLog.log("SpotifyLoginWindow composing")
        SwingPanel(
            factory = {
                try {
                    val cefApp = runCatching { globalCefApp() }.getOrElse { t -> throw t }
                    val client = cefApp.createClient()
                    DesktopLog.log("Creating Spotify Chromium browser")
                    val browser =
                        client.createBrowser(
                            SpotifyAuth.LOGIN_URL,
                            false,
                            false,
                        )
                    val cefPanel =
                        JPanel().apply {
                            layout = BorderLayout()
                            add(browser.getUIComponent(), BorderLayout.CENTER)
                        }

                    var captured = false
                    val processor =
                        Thread {
                            val deadline = System.currentTimeMillis() + 10 * 60 * 1000
                            while (!captured && System.currentTimeMillis() < deadline) {
                                runCatching {
                                    val cookies = collectSpotifyCookies()
                                    val spDc = cookies.firstOrNull { it.first == "sp_dc" }?.second
                                    val spKey = cookies.firstOrNull { it.first == "sp_key" }?.second.orEmpty()
                                    DesktopLog.log(
                                        "Spotify JCEF cookie poll: ${cookies.size} cookies, sp_dc ${if (spDc.isNullOrBlank()) "no" else "present"}",
                                    )
                                    if (!spDc.isNullOrBlank()) {
                                        captured = true
                                        DesktopLog.log("Spotify sign-in detected (sp_dc present)")
                                        javax.swing.SwingUtilities.invokeLater {
                                            onSignedIn(spDc, spKey)
                                        }
                                    }
                                }.onFailure { DesktopLog.log("Spotify JCEF cookie poll failed", it) }
                                if (!captured) Thread.sleep(2000)
                            }
                        }
                    processor.isDaemon = true
                    processor.start()

                    cefPanel
                } catch (t: Throwable) {
                    DesktopLog.log("SpotifyLoginWindow factory failed", t)
                    JPanel()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun collectSpotifyCookies(): List<Pair<String, String>> {
    val manager: CefCookieManager =
        runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return emptyList()
    val found = mutableListOf<Pair<String, String>>()
    val visitor =
        object : CefCookieVisitor {
            override fun visit(
                cookie: org.cef.network.CefCookie?,
                count: Int,
                total: Int,
                delete: org.cef.misc.BoolRef?,
            ): Boolean {
                if (cookie != null) {
                    val domain = cookie.domain ?: ""
                    if (domain == "spotify.com" || domain.endsWith(".spotify.com")) {
                        val name = cookie.name ?: ""
                        val value = cookie.value ?: ""
                        found += name to value
                    }
                }
                return true
            }
        }
    runCatching { manager.visitAllCookies(visitor) }
    return found
}
