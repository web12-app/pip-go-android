package com.pipgo.runtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Connect screen (§26): manual URL entry, recent dev servers, QR scan.
 * The dark shell is Compose chrome — the app itself is native (§43).
 */
@Composable
fun ConnectScreen(
    initialUrl: String,
    recents: List<String>,
    statusLine: String,
    connecting: Boolean,
    onConnect: (String) -> Unit,
    onScanQr: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("⣿ Pip-Go", color = Color(0xFF38BDF8), fontSize = 34.sp)
        Text(
            "React-like JavaScript →\nreal native Android UI",
            color = Color(0xFF94A3B8), fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("http://192.168.1.20:3000", color = Color(0xFF475569), fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onConnect(url) }),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (url.isNotBlank()) onConnect(url) },
            enabled = !connecting && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (connecting) statusLine else "Connect")
        }
        OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Scan QR (pipgo://)")
        }

        if (statusLine.isNotBlank() && connecting) {
            Text(statusLine, color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("RECENT SERVERS", color = Color(0xFF475569), fontSize = 12.sp)
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    recents.take(4).forEach { recent ->
                        TextButton(onClick = { onConnect(recent) }, modifier = Modifier.fillMaxWidth()) {
                            Text(recent, fontSize = 13.sp, color = Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "No tunnel · No WebView · Real native views",
            color = Color(0xFF334155), fontSize = 11.sp,
        )
    }
}

/** Development console (§45): runtime logs streamed from the bundle. */
@Composable
fun ConsoleScreen(lines: List<String>, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF020617))) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Runtime console", color = Color(0xFF94A3B8), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(lines) { line ->
                    SelectionContainer {
                        Text(
                            line,
                            color = if (line.startsWith("[error]")) Color(0xFFF87171) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
