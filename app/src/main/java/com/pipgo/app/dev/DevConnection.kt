package com.pipgo.app.dev

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for the reload protocol (docs/reload-protocol.md) plus
 * blocking HTTP helpers for /__pipgo/ping and bundle prefetch (§21).
 * Auto-reconnects with capped backoff while connected.
 */
class DevConnection(private val client: OkHttpClient = defaultClient()) : WebSocketListener() {

    interface Listener {
        fun onOpen() {}
        fun onReconnecting(attempt: Int) {}
        fun onReload(reason: String) {}
        fun onFastRefresh(version: Int) {}
        fun onBuildError(errors: JSONArray) {}
        fun onBuildFixed() {}
        fun onFailure(message: String) {}
    }

    var listener: Listener? = null

    @Volatile private var shouldRun = false
    @Volatile private var attempt = 0
    @Volatile private var baseUrl: String = ""
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(url: String) {
        disconnect()
        shouldRun = true
        attempt = 0
        baseUrl = url
        openSocket()
    }

    fun disconnect() {
        shouldRun = false
        mainHandler.removeCallbacksAndMessages(null)
        try { webSocket?.close(1000, "client disconnect") } catch (_: Exception) {}
        webSocket = null
    }

    private fun openSocket() {
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/__pipgo/ws"
        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), this)
    }

    private fun scheduleReconnect() {
        val delay = minOf(800L * attempt, 5000L)
        mainHandler.postDelayed({
            if (shouldRun) {
                listener?.onReconnecting(attempt)
                openSocket()
            }
        }, delay)
    }

    // ---- WebSocketListener --------------------------------------------------

    override fun onOpen(webSocket: WebSocket, response: Response) {
        attempt = 0
        listener?.onOpen()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = try { JSONObject(text) } catch (_: Exception) { return }
        when (msg.optString("type")) {
            "reload" -> listener?.onReload(msg.optString("reason", "bundle-changed"))
            "fast-refresh" -> listener?.onFastRefresh(msg.optInt("version", 0))
            "build-error" -> listener?.onBuildError(msg.optJSONArray("errors") ?: JSONArray())
            "build-fixed" -> listener?.onBuildFixed()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (!shouldRun) return
        attempt += 1
        listener?.onFailure(t.message ?: "connection failed")
        scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!shouldRun) return
        attempt += 1
        scheduleReconnect()
    }

    companion object {
        private const val TAG = "DevConnection"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived WebSocket
            .build()

        /** Blocking ping of the dev server: parsed JSON or null. */
        fun ping(url: String, client: OkHttpClient = defaultClient()): JSONObject? = try {
            val req = Request.Builder().url(url.trimEnd('/') + "/__pipgo/ping").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) JSONObject(resp.body?.string() ?: "") else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ping failed: ${e.message}")
            null
        }

        /** Blocking bundle download for the offline cache (§21). */
        fun downloadBundle(url: String, client: OkHttpClient = defaultClient()): ByteArray? = try {
            val req = Request.Builder().url(url.trimEnd('/') + "/bundle.js").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "bundle download failed: ${e.message}")
            null
        }
    }
}
