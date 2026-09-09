package com.pipgo.app.nativeview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * Hosts native child WebViews for the SDK's `<View src="..."/>` element
 * (docs/bridge-protocol.md → viewCreate/viewUpdate/viewDestroy).
 *
 * Rects arrive in CSS px relative to the document and are converted with the
 * host density; scroll sync is done by the JS side re-emitting viewUpdate on
 * scroll (see pip-go-sdk View.tsx ResizeObserver/scroll listeners).
 */
class NativeViewHost {

    private val views = LinkedHashMap<String, WebView>()
    private var container: FrameLayout? = null
    private var density = 1f
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(containerView: FrameLayout, hostDensity: Float) {
        container = containerView
        density = hostDensity
    }

    fun detach() {
        mainHandler.post {
            views.values.forEach { it.destroy() }
            views.clear()
            (container?.removeAllViews())
        }
        container = null
    }

    fun create(hostWebView: WebView, specJson: String) {
        val spec = try { JSONObject(specJson) } catch (_: Exception) { return }
        val id = spec.optString("id")
        val src = spec.optString("src")
        if (id.isBlank() || src.isBlank()) return
        mainHandler.post {
            val parent = container ?: return@post
            if (views.containsKey(id)) return@post
            val child = makeChild(hostWebView, src)
            views[id] = child
            parent.addView(
                child,
                FrameLayout.LayoutParams(
                    Math.max(1, px(spec.optDouble("width", 100.0))),
                    Math.max(1, px(spec.optDouble("height", 100.0)))
                )
            )
            position(child, spec)
        }
    }

    fun update(specJson: String) {
        val spec = try { JSONObject(specJson) } catch (_: Exception) { return }
        val id = spec.optString("id")
        mainHandler.post {
            val child = views[id] ?: return@post
            child.layoutParams?.let { lp ->
                lp.width = Math.max(1, px(spec.optDouble("width", 100.0)))
                lp.height = Math.max(1, px(spec.optDouble("height", 100.0)))
                child.layoutParams = lp
            }
            position(child, spec)
        }
    }

    fun destroy(id: String) {
        mainHandler.post {
            val child = views.remove(id) ?: return@post
            (child.parent as? FrameLayout)?.removeView(child)
            child.destroy()
        }
    }

    private fun position(child: WebView, spec: JSONObject) {
        child.translationX = px(spec.optDouble("x", 0.0)).toFloat()
        child.translationY = px(spec.optDouble("y", 0.0)).toFloat()
    }

    private fun px(cssPx: Double): Int = Math.round(cssPx.toFloat() * density)

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeChild(hostWebView: WebView, src: String): WebView {
        return WebView(hostWebView.context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            setBackgroundColor(android.graphics.Color.WHITE)
            loadUrl(src)
        }
    }

    companion object {
        private const val TAG = "NativeViewHost"
    }
}
