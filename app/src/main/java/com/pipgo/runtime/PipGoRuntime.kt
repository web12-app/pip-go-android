package com.pipgo.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import com.pipgo.runtime.dev.DevConnection
import com.pipgo.runtime.runtime.bridge.Op
import com.pipgo.runtime.runtime.bridge.OpParser
import com.pipgo.runtime.runtime.bundle.BundleCache
import com.pipgo.runtime.runtime.components.PropApplier
import com.pipgo.runtime.runtime.events.EventDispatcher
import com.pipgo.runtime.runtime.js.JsEngine
import com.pipgo.runtime.runtime.renderer.NativeRenderer
import com.pipgo.runtime.storage.PipGoStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** UI state machine for the Compose shell. */
sealed class RuntimeUiState {
    data object Disconnected : RuntimeUiState()
    data object Connecting : RuntimeUiState()
    data class Live(val url: String, val version: Int) : RuntimeUiState()
    data class Error(val title: String, val message: String, val stack: String?) : RuntimeUiState()
}

/**
 * The Pip-Go runtime (§2/§31-§36): JS engine ⇄ bridge ⇄ native renderer,
 * plus dev connection, bundle cache/integrity and lifecycle (§24/§26/§28-§30).
 */
class PipGoRuntime(private val context: Context) {

    private val storage = PipGoStorage(context)
    private val cache = BundleCache(context)
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<RuntimeUiState>(RuntimeUiState.Disconnected)
    val state: StateFlow<RuntimeUiState> = _state

    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console

    var devConnection: DevConnection? = null
        private set
    private var engine: JsEngine? = null
    private var renderer: NativeRenderer? = null
    private var rootContainer: FrameLayout? = null
    private var currentUrl: String? = null
    private var currentVersion: Int = 0
    private val pendingCalls = HashSet<String>()
    private val timers = HashMap<String, Runnable>()

    /** The native container the app's views render into (created on demand). */
    fun ensureRootContainer(): FrameLayout {
        if (rootContainer == null) {
            val container = FrameLayout(context)
            container.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            rootContainer = container
            renderer = NativeRenderer(
                context = context,
                rootContainer = container,
                onLog = { level, args -> handleRuntimeLog(level, args) },
                onHttp = { op -> performHttp(op) },
                onHttpCancel = { callId -> pendingCalls.remove(callId) },
                onTimer = { callId, kind, delayMs -> scheduleTimer(callId, kind, delayMs) },
                onTimerClear = { callId -> timers.remove(callId) },
            )
            renderer!!.registry.put("screen:0", container)
        }
        return rootContainer!!
    }

    /* ------------------------- connection (§26) ------------------------- */

    fun connect(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) return
        _state.value = RuntimeUiState.Connecting
        storage.setLastUrl(clean)
        storage.addRecentUrl(clean)
        currentUrl = clean
        currentVersion = 0

        val dev = DevConnection(object : DevConnection.Listener {
            override fun onMeta(meta: JSONObject) { /* cached bundle version check (§29) */ }
            override fun onBundle(version: Int, hash: String, code: String) {
                acceptBundle(code, hash, null, version, "dev-ws")
            }
            override fun onBundleUpdated(version: Int, hash: String, sizeBytes: Long) {
                if (version <= currentVersion) return
                fetchAndActivate("$clean/bundle.js", version, hash, sizeBytes)
            }
            override fun onState(state: String) {
                Log.d(TAG, "dev state: $state")
            }
        })
        devConnection = dev
        dev.connect(clean)
    }

    /** Cached-first startup (§29): load offline bundle instantly, then connect. */
    fun connectWithCacheFallback(url: String) {
        val cached = cache.currentCode()
        if (cached != null) {
            executeCached(cached)
            connect(url)
        } else {
            connect(url)
        }
    }

    fun disconnect() {
        devConnection?.disconnect()
        devConnection = null
        stopEngine()
        _state.value = RuntimeUiState.Disconnected
    }

    /* -------------------- bundle integrity (§29/§30) -------------------- */

    private fun acceptBundle(code: String, hash: String?, sizeBytes: Long?, version: Int, source: String) {
        val temp = cache.newTempFile()
        try {
            temp.writeText(code)
            if (!cache.validate(temp, hash, sizeBytes)) {
                Log.w(TAG, "bundle v$version failed validation — keeping previous (§30)")
                appendConsole("warn", "bundle v$version rejected (integrity)")
                return
            }
            val known = hash ?: cache.hashOf(code)
            cache.activate(temp, BundleCache.Meta(version, known.take(16), source, System.currentTimeMillis()))
            executeBundle(code, version)
        } catch (e: Exception) {
            Log.e(TAG, "acceptBundle failed", e)
            appendConsole("error", "bundle v$version activation failed: ${e.message}")
        }
    }

    private fun fetchAndActivate(bundleUrl: String, version: Int, hash: String, sizeBytes: Long) {
        val request = Request.Builder().url(bundleUrl).build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.w(TAG, "bundle fetch failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                val code = try { response.body?.string() } catch (_: Exception) { null } ?: return
                acceptBundle(code, hash, sizeBytes, version, "dev-http")
            }
        })
    }

    /* ----------------------- execution (§7/§32/§43) ----------------------- */

    private fun executeCached(code: String) {
        val meta = cache.currentMeta()
        executeBundle(code, meta?.version ?: 0)
    }

    private fun executeBundle(code: String, version: Int) {
        main.post {
            stopEngine()
            currentVersion = version
            ensureRootContainer()
            val r = renderer!!
            val e = JsEngine(
                storage = storage,
                onBatch = { json -> main.post { r.applyBatch(OpParser.parseBatch(json)) } },
                onError = { message, stack -> handleJsError(message, stack) },
            )
            engine = e
            val dispatcher = EventDispatcher(e)
            PropApplier.eventSink = { nodeId, event, payload ->
                dispatcher.dispatch(nodeId, event, payload)
            }
            _state.value = RuntimeUiState.Live(currentUrl ?: "", version)
            e.start(code)
        }
    }

    private fun stopEngine() {
        timers.values.forEach { main.removeCallbacks(it) }
        timers.clear()
        engine?.stop()
        engine = null
        renderer = null
        rootContainer?.removeAllViews()
        rootContainer = null
    }

    /* -------------------------- ops plumbing -------------------------- */

    private fun handleRuntimeLog(level: String, args: List<String>) {
        appendConsole(level, args.joinToString(" "))
        devConnection?.sendLog(level, args)
    }

    private fun appendConsole(level: String, message: String) {
        val line = "[${level}] $message"
        _console.value = (_console.value + line).takeLast(300)
    }

    private fun handleJsError(message: String, stack: String?) {
        appendConsole("error", message)
        devConnection?.sendError(message, stack)
        _state.value = RuntimeUiState.Error("Pip-Go Runtime Error", message, stack)
    }

    /* ------------------ timers (§7) via the timer op ------------------ */

    private fun scheduleTimer(callId: String, kind: String, delayMs: Long) {
        val runnable = Runnable {
            timers.remove(callId)
            engine?.fireTimer(callId)
            if (kind == "interval") scheduleTimer(callId, kind, delayMs)
        }
        timers[callId] = runnable
        main.postDelayed(runnable, delayMs.coerceAtLeast(0))
    }

    /* --------------------- http (§17) via OkHttp --------------------- */

    private fun performHttp(op: Op.Http) {
        val callId = op.callId
        pendingCalls.add(callId)
        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = op.body?.toRequestBody(mediaType)
            val builder = Request.Builder().url(op.url)
            val headers = op.headers
            for (key in headers.keys()) builder.header(key, headers.optString(key, ""))
            when (op.method.uppercase()) {
                "POST" -> builder.post(body ?: "{}".toRequestBody(mediaType))
                "PUT" -> builder.put(body ?: "{}".toRequestBody(mediaType))
                "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
                else -> builder.get()
            }
            val call = http.newCall(builder.build())
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (pendingCalls.remove(callId)) {
                        resolve(callId, null, e.message ?: "network error")
                    }
                }
                override fun onResponse(call: Call, response: okhttp3.Response) {
                    if (!pendingCalls.remove(callId)) return
                    val text = try { response.body?.string() } catch (_: Exception) { null }
                    val ok = response.isSuccessful
                    val payload = JSONObject()
                        .put("ok", ok)
                        .put("status", response.code)
                        .put("data", try { org.json.JSONTokener(text ?: "").nextValue() } catch (_: Exception) { JSONObject.NULL })
                        .put("error", if (ok) JSONObject.NULL else (text ?: "HTTP ${response.code}"))
                    resolve(callId, payload.toString(), null)
                }
            })
        } catch (e: Exception) {
            pendingCalls.remove(callId)
            resolve(callId, null, e.message ?: "http failed")
        }
    }

    private fun resolve(callId: String, valueJson: String?, error: String?) {
        val value = if (valueJson != null) JSONObject.quote(valueJson) else "null"
        val err = if (error != null) JSONObject.quote(error) else "null"
        engine?.evaluate(
            "globalThis.__pipgo.resolveCallJson(${JSONObject.quote(callId)}, $value, $err)",
            "http:$callId",
        )
    }

    /* ---------------------------- back (§37) ---------------------------- */

    /** Returns true if the runtime consumed the back press (popped a screen). */
    fun handleBack(): Boolean {
        val r = renderer ?: return false
        if ((r.rootContainer.childCount) > 1) {
            r.popTopScreen()
            engine?.evaluate("globalThis.__pipgo.dispatchEvent(\"__nav__\", \"back\", {})", "nav-back")
            return true
        }
        return false
    }

    fun reloadFromCache() {
        val code = cache.currentCode() ?: return
        executeBundle(code, cache.currentMeta()?.version ?: 0)
    }

    fun setDevConnectionForReload(dev: DevConnection?) { devConnection = dev }

    companion object { private const val TAG = "PipGo.Runtime" }
}
