package com.pipgo.app.bridge

import android.webkit.JavascriptInterface

/**
 * Native bridge exposed to the WebView as `window.PipGoBridge`
 * (docs/bridge-protocol.md — approved APIs only).
 *
 * All methods are synchronous JSON-string in / JSON-string out and run on a
 * WebView background thread; storage access is fast (SharedPreferences) so no
 * async callback channel is needed for the v1 surface.
 *
 * Native → JS events use `window.__pipgoNativeEvent(type, payloadJson)`.
 */
class PipGoBridge(
    private val host: BridgeHost,
) {

    interface BridgeHost {
        fun currentProjectName(): String
        fun deviceInfoJson(): String
        fun viewportInfoJson(): String
        fun networkStatusJson(): String
        fun storageGet(nsKey: String): String?
        fun storageSet(nsKey: String, value: String): Boolean
        fun storageRemove(nsKey: String): Boolean
        fun viewCreate(specJson: String): Boolean
        fun viewUpdate(specJson: String): Boolean
        fun viewDestroy(id: String): Boolean
        fun requestReload()
        fun log(level: String, message: String)
    }

    private fun ok(data: String): String = """{"ok":true,"data":$data}"""
    private fun fail(error: String): String = """{"ok":false,"error":"${error.replace("\"", "'")}"}"""

    @JavascriptInterface
    fun deviceInfo(): String = try {
        ok(host.deviceInfoJson())
    } catch (e: Exception) {
        fail(e.message ?: "deviceInfo failed")
    }

    @JavascriptInterface
    fun viewportInfo(): String = try {
        ok(host.viewportInfoJson())
    } catch (e: Exception) {
        fail(e.message ?: "viewportInfo failed")
    }

    @JavascriptInterface
    fun networkStatus(): String = try {
        ok(host.networkStatusJson())
    } catch (e: Exception) {
        fail(e.message ?: "networkStatus failed")
    }

    @JavascriptInterface
    fun storageGet(key: String): String = try {
        val raw = host.storageGet("${host.currentProjectName()}:$key")
        if (raw == null) """{"ok":true,"data":null}""" else ok(JSONObject.quote(raw))
    } catch (e: Exception) {
        fail(e.message ?: "storageGet failed")
    }

    @JavascriptInterface
    fun storageSet(key: String, value: String): String = try {
        ok(host.storageSet("${host.currentProjectName()}:$key", value).toString())
    } catch (e: Exception) {
        fail(e.message ?: "storageSet failed")
    }

    @JavascriptInterface
    fun storageRemove(key: String): String = try {
        ok(host.storageRemove("${host.currentProjectName()}:$key").toString())
    } catch (e: Exception) {
        fail(e.message ?: "storageRemove failed")
    }

    @JavascriptInterface
    fun viewCreate(specJson: String): String = try {
        ok(host.viewCreate(specJson).toString())
    } catch (e: Exception) {
        fail(e.message ?: "viewCreate failed")
    }

    @JavascriptInterface
    fun viewUpdate(specJson: String): String = try {
        ok(host.viewUpdate(specJson).toString())
    } catch (e: Exception) {
        fail(e.message ?: "viewUpdate failed")
    }

    @JavascriptInterface
    fun viewDestroy(id: String): String = try {
        ok(host.viewDestroy(id).toString())
    } catch (e: Exception) {
        fail(e.message ?: "viewDestroy failed")
    }

    @JavascriptInterface
    fun reload(): String = try {
        host.requestReload()
        """{"ok":true,"data":true}"""
    } catch (e: Exception) {
        fail(e.message ?: "reload failed")
    }

    @JavascriptInterface
    fun status(level: String, message: String): String = try {
        host.log(level, message)
        """{"ok":true,"data":true}"""
    } catch (e: Exception) {
        fail(e.message ?: "status failed")
    }
}
