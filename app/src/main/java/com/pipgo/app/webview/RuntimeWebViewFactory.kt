package com.pipgo.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.pipgo.app.bridge.PipGoBridge
import com.pipgo.app.cache.BundleCache

/**
 * WebView factory + client for the runtime preview (§17/§18).
 *
 * - Full-screen viewport, JS enabled, DOM storage on.
 * - `PipGoBridge` injected as `window.PipGoBridge`.
 * - WebViewAssetLoader serves the offline fallback (§21) under
 *   `https://appassets.androidplatform.net/pipgo/`: a minimal generated host
 *   page plus the last-good `bundle.js` from [BundleCache].
 * - Console messages forwarded to the native status log.
 */
object RuntimeWebViewFactory {

    const val CACHE_BASE_URL = "https://appassets.androidplatform.net/pipgo/"

    private val OFFLINE_INDEX: ByteArray = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Pip-Go Offline</title>
<style>
  :root { --pipgo-safe-top: env(safe-area-inset-top, 0px); }
  html, body { height: 100%; margin: 0; background: #0f172a; color: #e2e8f0;
    font-family: system-ui, sans-serif; }
  #root { min-height: 100%; }
</style>
</head>
<body>
<div id="root"></div>
<script src="bundle.js"></script>
</body>
</html>
""".trimIndent().toByteArray(Charsets.UTF_8)

    private fun response(mime: String, bytes: ByteArray): WebResourceResponse =
        WebResourceResponse(mime, "utf-8", bytes.inputStream())

    private fun assetLoader(context: Context, cache: BundleCache): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler("/pipgo/") { path: String ->
                when (path) {
                    "index.html" -> response("text/html", OFFLINE_INDEX)
                    "bundle.js" -> cache.loadBundle()
                        ?.let { response("text/javascript", it) }
                        ?: response("text/plain", "// no cached bundle".toByteArray())
                    else -> response("text/plain", ByteArray(0))
                }
            }
            .build()

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        bridge: PipGoBridge,
        cache: BundleCache,
        onPageEvent: (event: String, detail: String) -> Unit,
    ): WebView {
        val loader = assetLoader(context, cache)
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(bridge, "PipGoBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                onPageEvent("pageFinished", url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val level = when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> "error"
                    ConsoleMessage.MessageLevel.WARNING -> "warn"
                    else -> "info"
                }
                onPageEvent(
                    "console",
                    "[$level] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                )
                return true
            }
        }
        return webView
    }
}
