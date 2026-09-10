package com.pipgo.runtime.dev

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Development connection (§23/§24/§26): WebSocket to `pip-go dev`.
 *
 * outbound: hello{device}, log, reload-ok, error
 * inbound:  welcome, meta, bundle{code}, bundle-updated{version,hash}
 *
 * No tunnel (§27) — plain LAN http/ws only.
 */
class DevConnection(private val listener: Listener) {

    interface Listener {
        fun onMeta(meta: JSONObject)
        fun onBundle(version: Int, hash: String, code: String)
        fun onBundleUpdated(version: Int, hash: String, sizeBytes: Long)
        fun onState(state: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: no read timeout
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    val isConnected: Boolean get() = ws != null

    fun connect(baseUrl: String) {
        val wsUrl = baseUrl
            .replace(Regex("^http://"), "ws://")
            .replace(Regex("^https://"), "wss://")
            .trimEnd('/') + "/__pipgo/ws"
        val request = Request.Builder().url(wsUrl).build()
        listener.onState("connecting")
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws open: $wsUrl")
                listener.onState("connected")
                val hello = JSONObject()
                    .put("type", "hello")
                    .put("device", JSONObject()
                        .put("model", android.os.Build.MODEL ?: "device")
                        .put("sdkInt", android.os.Build.VERSION.SDK_INT))
                webSocket.send(hello.toString())
                webSocket.send(JSONObject().put("type", "request-bundle").toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "welcome" -> listener.onState("ready")
                        "meta" -> listener.onMeta(msg)
                        "bundle" -> listener.onBundle(
                            msg.optInt("version", 0),
                            msg.optString("hash", ""),
                            msg.optString("code", ""),
                        )
                        "bundle-updated" -> listener.onBundleUpdated(
                            msg.optInt("version", 0),
                            msg.optString("hash", ""),
                            msg.optLong("sizeBytes", 0),
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "bad ws message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ws failure: ${t.message}")
                ws = null
                listener.onState("failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ws = null
                listener.onState("closed")
            }
        })
    }

    fun sendLog(level: String, args: List<String>) {
        val msg = JSONObject()
            .put("type", "log")
            .put("level", level)
            .put("args", org.json.JSONArray(args))
        ws?.send(msg.toString())
    }

    fun sendReloadOk(version: Int) {
        ws?.send(JSONObject().put("type", "reload-ok").put("version", version).toString())
    }

    fun sendError(message: String, stack: String?) {
        val msg = JSONObject().put("type", "error").put("message", message)
        if (stack != null) msg.put("stack", stack)
        ws?.send(msg.toString())
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
    }

    companion object { private const val TAG = "PipGo.DevConnection" }
}
