package com.pipgo.runtime.runtime.events

import android.os.Handler
import android.os.Looper
import com.pipgo.runtime.runtime.js.JsEngine
import org.json.JSONObject

/**
 * Event flow (§16): Android event → native bridge → JS event dispatcher →
 * JavaScript callback. Events cross back into the bundle through the single
 * `__pipgo.dispatchEvent` global installed by the SDK.
 */
class EventDispatcher(private val engine: JsEngine) {

    private val main = Handler(Looper.getMainLooper())

    fun dispatch(nodeId: String, event: String, payload: JSONObject? = null) {
        // Marshalled onto the engine thread; payload embedded as JSON literal.
        val payloadJson = payload?.toString() ?: "null"
        val safeId = JSONObject.quote(nodeId)
        val safeEvent = JSONObject.quote(event)
        val code = "globalThis.__pipgo.dispatchEvent($safeId, $safeEvent, $payloadJson)"
        main.post { engine.evaluate(code, "event:$event") }
    }
}
