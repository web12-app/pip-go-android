package com.pipgo.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pipgo.runtime.material.PipGoTheme
import com.pipgo.runtime.storage.PipGoStorage
import com.pipgo.runtime.ui.ConnectScreen
import com.pipgo.runtime.ui.ConsoleScreen
import com.pipgo.runtime.ui.ErrorScreen
import com.pipgo.runtime.ui.QrScanOverlay

/**
 * Pip-Go Android runtime (§1/§43): the app screen IS the native view tree
 * produced by bundle.js — there is no HTML preview and no WebView rendering
 * of application UI (WebView exists only as an explicit <WebView> component).
 */
class MainActivity : ComponentActivity() {

    private val runtime by lazy { PipGoRuntime(this) }
    private val storage by lazy { PipGoStorage(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PipGoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RuntimeApp()
                }
            }
        }
    }

    @Composable
    private fun RuntimeApp() {
        val state by runtime.state.collectAsState()
        val console by runtime.console.collectAsState()
        var scanning by remember { mutableStateOf(false) }
        var showConsole by remember { mutableStateOf(false) }

        when (val s = state) {
            is RuntimeUiState.Disconnected, is RuntimeUiState.Connecting -> {
                ConnectScreen(
                    initialUrl = storage.lastUrl() ?: "",
                    recents = storage.recentUrls(),
                    statusLine = if (s is RuntimeUiState.Connecting) "Connecting…" else "",
                    connecting = s is RuntimeUiState.Connecting,
                    onConnect = { url -> runtime.connectWithCacheFallback(url) },
                    onScanQr = { scanning = true },
                )
            }

            is RuntimeUiState.Live -> {
                Box(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
                    // the REAL native tree built from bundle.js (§31/§32/§43)
                    AndroidView(
                        factory = { runtime.ensureRootContainer() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    StatusChip(
                        text = "● Live v${s.version}",
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        onConsole = { showConsole = true },
                    )
                }
            }

            is RuntimeUiState.Error -> {
                ErrorScreen(
                    title = s.title,
                    message = s.message,
                    stack = s.stack,
                    onReload = { runtime.reloadFromCache() },
                    onBack = { runtime.disconnect() },
                )
            }
        }

        if (scanning) {
            QrScanOverlay(
                onUrl = { url -> scanning = false; runtime.connectWithCacheFallback(url) },
                onCancel = { scanning = false },
            )
        }
        if (showConsole) {
            ConsoleScreen(lines = console, onClose = { showConsole = false })
        }
    }

    @Composable
    private fun StatusChip(text: String, modifier: Modifier, onConsole: () -> Unit) {
        Surface(
            color = Color(0xCC1E293B),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier,
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(text, color = Color(0xFF4ADE80), fontSize = 11.sp)
                TextButton(onClick = onConsole, contentPadding = PaddingValues(0.dp)) {
                    Text("console", fontSize = 10.sp)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // keep the user inside the runtime; pop native screens first (§37)
        val state = runtime.state.value
        if (state is RuntimeUiState.Live) {
            if (!runtime.handleBack()) runtime.disconnect()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) runtime.disconnect()
    }
}
