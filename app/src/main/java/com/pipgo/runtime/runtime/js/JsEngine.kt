package com.pipgo.runtime.runtime.js

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.pipgo.runtime.storage.PipGoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Embedded JavaScript runtime (§7): executes the self-contained bundle.js in
 * QuickJS (ES2020: async/await, promises, generators, JSON, modules — no npm
 * at runtime). All evaluations are serialized onto a single engine thread;
 * JS-driven view work is forwarded to the UI thread by the dispatcher (§35).
 *
 * Kotlin → JS re-entry (events §16, timers §7, http §17) goes through
 * [evaluate], which queues behind any running evaluation.
 */
class JsEngine(
    private val storage: PipGoStorage,
    private val onBatch: (String) -> Unit,
    private val onError: (message: String, stack: String?) -> Unit,
) {

    private val engineThread = Executors.newSingleThreadExecutor { r -> Thread(r, "pipgo-js") }
    private val engineDispatcher = engineThread.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private var quickJs: QuickJs? = null

    val isRunning: Boolean get() = quickJs != null

    /** Install host bindings, then execute the bundle (§32/§47). */
    fun start(bundleCode: String, filename: String = "bundle.js") {
        scope.launch {
            try {
                val qs = QuickJs.create(engineDispatcher)
                quickJs = qs
                installBindings(qs)
                qs.evaluate<Unit>(bundleCode, filename = filename)
                Log.i(TAG, "bundle executed")
            } catch (e: Throwable) {
                Log.e(TAG, "bundle failed", e)
                main.post { onError(e.message ?: "bundle execution failed", e.stackTraceToString().take(1200)) }
            }
        }
    }

    private fun installBindings(qs: QuickJs) {
        // Single bridge entry — everything the bundle sends crosses here (§33).
        qs.define("__PIP_GO_BRIDGE__") {
            function("post") { args ->
                val json = args.firstOrNull()?.toString() ?: "{}"
                onBatch(json)
            }
        }
        // Synchronous host APIs (§38/§39) — no private device data (§47).
        qs.define("__PIP_GO_HOST__") {
            function("deviceInfoJson") { _ ->
                val dm = android.content.res.Resources.getSystem().displayMetrics
                val info = JSONObject().apply {
                    put("platform", "android")
                    put("version", android.os.Build.VERSION.RELEASE ?: "")
                    put("model", android.os.Build.MODEL ?: "")
                    put("width", dm.widthPixels)
                    put("height", dm.heightPixels)
                    put("density", dm.density)
                    put("safeArea", JSONObject().put("top", 0).put("bottom", 0).put("left", 0).put("right", 0))
                }
                info.toString()
            }
            function("storageGet") { args ->
                storage.get(args.getOrNull(0)?.toString() ?: "")
            }
            function("storageSet") { args ->
                storage.set(args.getOrNull(0)?.toString() ?: "", args.getOrNull(1)?.toString() ?: "")
            }
            function("storageRemove") { args ->
                storage.remove(args.getOrNull(0)?.toString() ?: "")
            }
        }
    }

    /** Queue a small evaluation on the engine thread (events/timers/http). */
    fun evaluate(code: String, debugName: String = "eval") {
        val qs = quickJs ?: return
        scope.launch {
            try {
                qs.evaluate<Unit>(code, filename = debugName)
            } catch (e: Throwable) {
                Log.e(TAG, "evaluate failed ($debugName)", e)
                main.post { onError(e.message ?: "script error", e.stackTraceToString().take(1200)) }
            }
        }
    }

    /** Fire a JS timer scheduled via the `timer` op (§7). */
    fun fireTimer(callId: String) = evaluate("globalThis.__pipgo.fireTimer(${JSONObject.quote(callId)})", "timer:$callId")

    fun stop() {
        scope.cancel()
        try { quickJs?.close() } catch (_: Exception) {}
        quickJs = null
        engineDispatcher.close()
        engineThread.shutdown()
    }

    companion object { private const val TAG = "PipGo.JsEngine" }
}
