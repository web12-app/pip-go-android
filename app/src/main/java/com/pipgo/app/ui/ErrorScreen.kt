package com.pipgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Error screen (§20): actionable failure info with Retry / Reload.
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry") }
            Spacer(Modifier.height(0.dp))
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = onReload, modifier = Modifier.weight(1f)) { Text("Reload") }
        }
        Spacer(Modifier.height(28.dp))
    }
}
