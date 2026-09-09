package com.pipgo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pipgo.app.runtime.RuntimeState

/**
 * Preview screen (§17/§18): the WebView occupies ALL available space; chrome
 * is limited to a slim status bar on top and a viewport info footer.
 */
@Composable
fun PreviewScreen(
    state: RuntimeState,
    webView: android.webkit.WebView,
    statusLog: String,
) {
    val (statusText, statusColor) = when (state) {
        is RuntimeState.Live -> "● Live" to Color(0xFF4ADE80)
        is RuntimeState.Reconnecting -> "◌ Reconnecting…" to Color(0xFFFBBF24)
        is RuntimeState.OfflineFallback -> "◐ Offline — cached bundle" to Color(0xFFFBBF24)
        is RuntimeState.Connecting -> "◌ Connecting…" to Color(0xFF38BDF8)
        else -> "○ Idle" to Color(0xFF64748B)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // status bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⣿ Pip-Go",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFE2E8F0),
            )
            Spacer(Modifier.weight(1f))
            Text(
                statusText,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
        }
        if (state is RuntimeState.OfflineFallback || state is RuntimeState.Reconnecting) {
            Text(
                statusLog.takeLast(160),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        // the app viewport — full remaining space (§18)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // viewport footer (§19)
        Surface(color = Color(0xFF1E293B), shadowElevation = 4.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val viewport = when (state) {
                    is RuntimeState.Live -> state.viewport
                    else -> ""
                }
                Text(
                    viewport.ifBlank { "Device Preview" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when (state) {
                        is RuntimeState.Live -> state.project
                        is RuntimeState.OfflineFallback -> state.cachedVersion
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}
