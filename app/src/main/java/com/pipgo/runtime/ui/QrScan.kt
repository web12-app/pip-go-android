package com.pipgo.runtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/** QR connection overlay (§26): scans `pipgo://connect?url=http://…` codes. */

@Composable
fun QrScanOverlay(onUrl: (String) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xE60F172A))) {
        QrCameraContent(onUrl = onUrl, modifier = Modifier.fillMaxSize())
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Cancel", color = Color.White)
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrCameraContent(onUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    var handled by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scanner = remember {
        com.google.mlkit.vision.barcode.BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)
            val providerFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    if (!handled) {
                        val media = proxy.image
                        if (media != null) {
                            val input = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                                media, proxy.imageInfo.rotationDegrees
                            )
                            scanner.process(input)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { value ->
                                        if (!handled && (value.startsWith("pipgo://") || value.startsWith("http"))) {
                                            val url = if (value.startsWith("pipgo://connect?url=")) {
                                                android.net.Uri.parse(value).getQueryParameter("url") ?: value
                                            } else value
                                            if (!handled) {
                                                handled = true
                                                onUrl(url)
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else proxy.close()
                    } else proxy.close()
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    ctx as androidx.lifecycle.LifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
