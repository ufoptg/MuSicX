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
import androidx.compose.ui.awt.SwingPanel.Alignment
import androidx.compose.ui.window.Window
import javafx.embed.swing.JFXPanel
import javax.swing.JPanel

/**
 * In-app sign-in window backed by an embedded JavaFX WebView. Loads YouTube Music,
 * watches each successful page load for a readable `SAPISID` cookie (the same one the
 * YTM web client uses for SAPISIDHASH), and hands it to [onSignedIn] exactly once.
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
        SwingPanel(
            factory = {
                val panel = JPanel()
                panel.layout = java.awt.BorderLayout()
                // JFXPanel construction starts the JavaFX toolkit.
                panel.add(JFXPanel(), java.awt.BorderLayout.CENTER)
                javafx.application.Platform.setImplicitExit(false)
                javafx.application.Platform.runLater {
                    val webView = javafx.scene.web.WebView()
                    val engine = webView.engine
                    (panel.components.first() as JFXPanel).scene =
                        javafx.scene.Scene(webView, 900.0, 700.0)
                    var captured = false
                    engine.loadWorker.stateProperty().addListener { _, _, state ->
                        if (state == javafx.concurrent.Worker.State.SUCCEEDED && !captured) {
                            runCatching {
                                val cookies = engine.executeScript("document.cookie") as? String
                                val location = engine.location ?: ""
                                if (!cookies.isNullOrBlank() &&
                                    "SAPISID=" in cookies &&
                                    location.substringAfter("://").substringBefore('/').endsWith("youtube.com")
                                ) {
                                    captured = true
                                    javax.swing.SwingUtilities.invokeLater { onSignedIn(cookies) }
                                }
                            }
                        }
                    }
                    engine.load("https://music.youtube.com")
                }
                panel
            },
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.Center,
        )
    }
}
