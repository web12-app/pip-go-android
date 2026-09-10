package com.pipgo.runtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Error screen (§44): development shows message + stack; [Reload] re-runs
 * the last-known-good cached bundle.
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    stack: String?,
    onReload: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1C0A0A))
            .padding(24.dp),
    ) {
        Text("⚠ $title", color = Color(0xFFF87171), fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(message, color = Color(0xFFFCA5A5), fontSize = 14.sp)
        }
        if (!stack.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        stack,
                        color = Color(0xFF7F1D1D),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()).height(180.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) { Text("Reload") }
        androidx.compose.material3.OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Disconnect")
        }
    }
}
