package com.pipgo.app.qr

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Overlays used from MainActivity.
 *
 * QrScanOverlay — reuses [QrCameraContent] with ML Kit QR detection (§16-A).
 * DiscoveryOverlay — mDNS browse for `_pipgo._tcp.` services (§16-B) emitted
 * by `pip-go dev` via bonjour-service.
 */

@Composable
fun QrScanOverlay(onUrl: (String) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xE60F172A))) {
        QrCameraContent(onUrl = onUrl, modifier = Modifier.fillMaxSize())
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Cancel", color = Color.White)
        }
    }
}

@Composable
fun DiscoveryOverlay(onPick: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val services = remember { mutableStateListOf<String>() }
    var discovering by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                nsd?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val url = "http://$host:${info.port}"
                        if (!services.contains(url)) services.add(url)
                    }
                })
            }
            override fun onStartDiscoveryFailed(type: String, code: Int) { discovering = false }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
        }
        nsd?.discoverServices("_pipgo._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        onDispose { try { nsd?.stopServiceDiscovery(listener) } catch (_: Exception) {} }
    }

    Box(Modifier.fillMaxSize().background(Color(0xE60F172A))) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).align(Alignment.Center),
        ) {
            Text("📡 Pip-Go servers on this network", color = Color.White)
            services.forEach { url ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = { onPick(url) }) { Text(url) }
                }
            }
            if (services.isEmpty() && discovering) {
                Text("Searching…", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Cancel")
            }
        }
    }
}

/** Extracted camera logic shared by the QR overlay. */
@Composable
fun QrCameraContent(onUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    LaunchedEffect(Unit) { /* camera lifecycle bound in factory below */ }

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
                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
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
                                        if (!handled && value.startsWith("http")) {
                                            handled = true
                                            onUrl(value)
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
