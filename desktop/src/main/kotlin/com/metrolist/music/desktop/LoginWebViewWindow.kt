/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefApp.CefAppState
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookieManager
import javax.swing.JPanel
import java.awt.BorderLayout
import java.io.File

/**
 * In-app sign-in window backed by an embedded Chromium instance via JCEF. Loads
 * YouTube Music's Google sign-in page, then waits for the user to land on
 * youtube.com (successful login / return flow) before pulling the auth
 * cookies from the embedded browser's own cookie store and handing them to the
 * existing session store. No external browser windows, no DPAPI parsing.
 */
@Composable
fun LoginWebViewWindow(
    onSignedIn: (String) -> Unit,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Sign in to YouTube Music",
    ) {
        DesktopLog.log("LoginWebViewWindow composing")
        SwingPanel(
            factory = {
                try {
                    val profileDir =
                        File(
                            (System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "."),
                            "MuSicX/cef-profile",
                        ).apply { mkdirs() }

                    val builder = CefAppBuilder()
                    builder.setInstallDir(File(profileDir, "jcef-bundle"))
                    builder.getCefSettings().windowless_rendering_enabled = false
                    builder.getCefSettings().cache_path = profileDir.absolutePath
                    builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
                        override fun stateHasChanged(state: CefAppState) {
                            DesktopLog.log("CefApp state: $state")
                        }
                    })
                    DesktopLog.log("Initialising CEF (bundle at ${profileDir.absolutePath})")
                    val cefApp: CefApp = builder.build()

                    val client: CefClient = cefApp.createClient()
                    DesktopLog.log("Creating Chromium browser")
                    val browser =
                        client.createBrowser(
                            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/",
                            false,
                            false,
                        )
                    val cefPanel = JPanel().apply {
                        layout = BorderLayout()
                        add(browser.getUIComponent(), BorderLayout.CENTER)
                    }

                    // Poll the cookie store in the embedded browser for the Google
                    // auth cookies (SAPISID etc.) while the user signs in. The
                    // instance only has a window of a few seconds after the user
                    // lands on YouTube, which is enough for the cookies to appear.
                    var captured = false
                    val processor = Thread {
                        val deadline = System.currentTimeMillis() + 10 * 60 * 1000
                        var cookies = emptyList<Pair<String, String>>()
                        while (!captured && System.currentTimeMillis() < deadline) {
                            runCatching {
                                cookies = collectYoutubeCookies()
                                if (cookies.any { it.first == "SAPISID" }) {
                                    captured = true
                                    val header = cookies.joinToString("; ") { "${it.first}=${it.second}" }
                                    DesktopLog.log("JCEF sign-in detected (SAPISID present)")
                                    javax.swing.SwingUtilities.invokeLater { onSignedIn(header) }
                                }
                            }.onFailure { DesktopLog.log("JCEF cookie poll failed", it) }
                            if (!captured) Thread.sleep(2000)
                        }
                    }
                    processor.isDaemon = true
                    processor.start()

                    cefPanel
                } catch (t: Throwable) {
                    DesktopLog.log("LoginWebViewWindow factory failed", t)
                    JPanel()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Returns the embedded Chromium instance's youtube.com cookies (name/value
 * pairs). Only populated for hosts under *.youtube.com. Requires the cookie
 * manager containing cookies for the given URL.
 */
private fun collectYoutubeCookies(): List<Pair<String, String>> {
    val manager: CefCookieManager =
        runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return emptyList()
    val found = mutableListOf<Pair<String, String>>()
    val visitor =
        object : CefCookieVisitor {
            override fun visit(cookie: org.cef.network.CefCookie?, count: Int, total: Int, delete: org.cef.misc.BoolRef?): Boolean {
                if (cookie != null) {
                    val domain = cookie.domain ?: ""
                    if (domain == "youtube.com" || domain.endsWith(".youtube.com")) {
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

