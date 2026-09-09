package com.pipgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Connection screen (§16): manual URL entry, recent servers, QR scan and
 * mDNS discovery entry points.
 */
@Composable
fun ConnectScreen(
    initialUrl: String,
    recents: List<String>,
    statusLine: String,
    connecting: Boolean,
    onConnect: (String) -> Unit,
    onScanQr: () -> Unit,
    onDiscover: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⣿ Pip-Go", style = MaterialTheme.typography.headlineMedium)
        Text(
            "React live app runtime",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Development server URL") },
            placeholder = { Text("http://192.168.1.20:3000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onConnect(url) },
            enabled = !connecting && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (connecting) "Connecting…" else "Connect")
        }
        Spacer(Modifier.height(8.dp))
        Text(statusLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
            Text("📷  Scan QR code")
        }
        TextButton(onClick = onDiscover, modifier = Modifier.fillMaxWidth()) {
            Text("📡  Discover on this network")
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Recent", style = MaterialTheme.typography.labelLarge)
                    LazyColumn {
                        items(recents) { recent ->
                            TextButton(onClick = { onConnect(recent) }) {
                                Text(recent, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
