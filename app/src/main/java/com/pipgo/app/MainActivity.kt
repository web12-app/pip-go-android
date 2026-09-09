package com.pipgo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pipgo.app.runtime.RuntimeManager
import com.pipgo.app.runtime.RuntimeState
import com.pipgo.app.settings.SettingsStore
import com.pipgo.app.ui.ConnectScreen
import com.pipgo.app.ui.ErrorScreen
import com.pipgo.app.ui.PreviewScreen
import com.pipgo.app.ui.theme.PipGoTheme

/**
 * Pip-Go — React live app runtime (Expo Go style, WebView architecture).
 * MainActivity hosts the three runtime screens driven by RuntimeManager state.
 */
class MainActivity : ComponentActivity() {

    private lateinit var runtime: RuntimeManager
    private lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = RuntimeManager(this)
        settings = SettingsStore(this)

        setContent {
            PipGoTheme {
                val state by runtime.state.collectAsState()
                var scanningQr by remember { mutableStateOf(false) }
                var discovering by remember { mutableStateOf(false) }
                var statusLine by remember { mutableStateOf("Waiting for Pip-Go…") }

                when (val s = state) {
                    is RuntimeState.Disconnected, is RuntimeState.Connecting -> {
                        ConnectScreen(
                            initialUrl = settings.lastUrl() ?: "",
                            recents = settings.recentUrls(),
                            statusLine = statusLine,
                            connecting = s is RuntimeState.Connecting,
                            onConnect = { url ->
                                statusLine = "Pinging $url …"
                                runtime.connect(url)
                            },
                            onScanQr = { scanningQr = true },
                            onDiscover = { discovering = true },
                        )
                    }

                    is RuntimeState.Live, is RuntimeState.Reconnecting, is RuntimeState.OfflineFallback -> {
                        PreviewScreen(
                            state = s,
                            webView = runtime.ensureWebView(),
                            statusLog = runtime.statusLog.joinToString("\n"),
                        )
                    }

                    is RuntimeState.AppError -> {
                        ErrorScreen(
                            title = "Application Error",
                            message = s.message,
                            onRetry = { runtime.connect(s.url) },
                            onReload = { runtime.connect(s.url) },
                        )
                    }
                }

                if (scanningQr) {
                    QrScanOverlay(
                        onUrl = { url ->
                            scanningQr = false
                            runtime.connect(url)
                        },
                        onCancel = { scanningQr = false },
                    )
                }
                if (discovering) {
                    DiscoveryOverlay(
                        onPick = { url ->
                            discovering = false
                            runtime.connect(url)
                        },
                        onCancel = { discovering = false },
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        // keep the user inside the runtime preview
        val s = runtime.state.value
        if (s is RuntimeState.Live || s is RuntimeState.Reconnecting || s is RuntimeState.OfflineFallback) {
            runtime.disconnect()
        } else {
            super.onBackPressed()
        }
    }
}
