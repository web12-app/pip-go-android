package com.pipgo.runtime.runtime.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.pipgo.runtime.runtime.renderer.LayoutMapper
import com.pipgo.runtime.runtime.renderer.parentKindOf
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Props (§12/§13) → native view attributes. Only whitelisted props are ever
 * applied (§47: no arbitrary native access). Function props never cross the
 * bridge — JS sends `events: ["click", …]` names instead (§16).
 */
object PropApplier {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val inflight = HashMap<String, Call>()

    /** Event sink — set by the runtime at boot (routes to the JS engine). */
    @JvmStatic var eventSink: ((id: String, event: String, payload: JSONObject?) -> Unit)? = null

    fun parseColor(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.startsWith("#")) Color.parseColor(value) else null
        } catch (_: Exception) { null }
    }

    /** Apply props that do not depend on the parent (create & update). */
    fun applyStatic(context: Context, view: View, tag: String, props: JSONObject, nodeId: String, isNew: Boolean = false) {
        val density = view.resources.displayMetrics.density

        // ---- common (§12) ----
        if (props.has("opacity")) view.alpha = props.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
        if (props.has("enabled")) view.isEnabled = props.optBoolean("enabled", true)
        when (props.optString("visibility", "visible")) {
            "gone" -> view.visibility = View.GONE
            "invisible" -> view.visibility = View.INVISIBLE
            else -> view.visibility = View.VISIBLE
        }
        parseColor(props.optString("background", null))?.let { bg ->
            val radius = if (props.has("radius")) props.optDouble("radius", 0.0).toFloat() * density else 0f
            view.background = if (radius > 0f) {
                GradientDrawable().apply { setColor(bg); cornerRadius = radius }
            } else ColorDrawable(bg)
        }
        if (props.has("padding")) {
            val p = (props.optDouble("padding", 0.0) * density).toInt()
            view.setPadding(p, p, p, p)
        }
        if (props.has("elevation")) view.elevation = (props.optDouble("elevation", 0.0) * density).toFloat()

        // ---- text family ----
        if (view is TextView) {
            if (props.has("text")) {
                val t = props.optString("text", "")
                if (view.text.toString() != t) view.text = t
            }
            val size = when {
                props.has("size") -> props.optDouble("size", 14.0)
                props.has("textSize") -> props.optDouble("textSize", 14.0)
                else -> null
            }
            size?.let { view.textSize = it.toFloat() }
            when (props.optString("fontWeight", "normal")) {
                "bold" -> view.setTypeface(view.typeface, Typeface.BOLD)
                else -> view.setTypeface(Typeface.create(view.typeface, Typeface.NORMAL))
            }
            parseColor(props.optString("color", null))?.let { view.setTextColor(it) }
            if (props.has("maxLines")) {
                val m = props.optInt("maxLines", Int.MAX_VALUE)
                if (m > 0) view.maxLines = m
            }
        }

        // ---- per-component ----
        when (tag) {
            "Image" -> {
                val iv = view as ImageView
                if (props.has("src")) loadInto(iv, props.optString("src", ""))
                iv.scaleType = when (props.optString("scaleType", "fitCenter")) {
                    "centerCrop" -> ImageView.ScaleType.CENTER_CROP
                    "fitXY" -> ImageView.ScaleType.FIT_XY
                    else -> ImageView.ScaleType.FIT_CENTER
                }
            }
            "Input" -> {
                val host = view as InputHost
                if (props.has("hint")) host.layout.hint = props.optString("hint", "")
                if (props.has("value")) {
                    val v = props.optString("value", "")
                    if (host.edit.text.toString() != v) host.edit.setText(v)
                }
                host.edit.isSingleLine = !props.optBoolean("multiline", false)
            }
            "Progress" -> {
                val p = view as CircularProgressIndicator
                if (props.has("indeterminate")) p.isIndeterminate = props.optBoolean("indeterminate", true)
                if (props.has("progress")) {
                    val v = props.optInt("progress", 0)
                    p.isIndeterminate = false
                    p.setProgressCompat(v, true)
                }
            }
            "WebView" -> {
                val wv = view as android.webkit.WebView
                if (props.has("src")) {
                    val src = props.optString("src", "")
                    val uri = Uri.parse(src)
                    // §47 URL validation — http(s) only
                    if (uri.scheme == "http" || uri.scheme == "https") wv.loadUrl(src)
                }
            }
            "Dialog" -> {
                val host = view as DialogHost
                if (isNew || host.dialog == null) showDialog(context, host, props, nodeId)
            }
            "List" -> {
                (view as ListHost).adapter.setItems(props.opt("items"))
            }
        }

        // ---- selection controls (§10) ----
        if (view is android.widget.CompoundButton) {
            if (props.has("checked")) {
                val want = props.optBoolean("checked", false)
                if (view.isChecked != want) view.isChecked = want
            }
            if (!props.optBoolean("__bound", false)) {
                props.put("__bound", true)
                view.setOnCheckedChangeListener { _, isChecked ->
                    eventSink?.invoke(nodeId, "checked", JSONObject().put("value", isChecked))
                }
            }
        }

        // ---- events (§16) ----
        applyEvents(view, props, nodeId)
    }

    /** Wire native listeners for the event names the JS side subscribed to. */
    fun applyEvents(view: View, props: JSONObject, nodeId: String) {
        if (!nodeId.isNotEmpty()) return
        val events = props.optJSONArray("events") ?: return
        val names = HashSet<String>()
        for (i in 0 until events.length()) events.optString(i, null)?.let { names.add(it.lowercase()) }

        when (view) {
            is InputHost -> {
                val edit: EditText = view.edit
                if ("textchange" in names) {
                    edit.doOnTextChanged { text, _, _, _ ->
                        eventSink?.invoke(nodeId, "textChange", JSONObject().put("value", text?.toString() ?: ""))
                    }
                }
                if ("submit" in names) {
                    edit.setOnEditorActionListener { v, _, _ ->
                        eventSink?.invoke(nodeId, "submit", JSONObject().put("value", v.text?.toString() ?: ""))
                        true
                    }
                }
                if ("focus" in names || "blur" in names) {
                    edit.setOnFocusChangeListener { _, hasFocus ->
                        eventSink?.invoke(nodeId, if (hasFocus) "focus" else "blur", null)
                    }
                }
                if ("click" in names) edit.setOnClickListener { eventSink?.invoke(nodeId, "click", null) }
            }
            else -> {
                if ("click" in names) view.setOnClickListener { eventSink?.invoke(nodeId, "click", null) }
                if ("longpress" in names) {
                    view.setOnLongClickListener { eventSink?.invoke(nodeId, "longPress", null); true }
                }
            }
        }
    }

    /** Show a MaterialAlertDialog for <Dialog> (§10/§41). */
    private fun showDialog(context: Context, host: DialogHost, props: JSONObject, nodeId: String) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(props.optString("title", ""))
            .setMessage(props.optString("message", ""))
            .setPositiveButton(props.optString("confirmText", "OK")) { d, _ ->
                eventSink?.invoke(nodeId, "confirm", null); d.dismiss()
            }
        if (props.has("cancelText")) {
            builder.setNegativeButton(props.optString("cancelText", "Cancel")) { d, _ ->
                eventSink?.invoke(nodeId, "cancel", null); d.dismiss()
            }
        }
        host.dialog?.dismiss()
        host.dialog = builder.show()
    }

    fun dismissDialog(host: DialogHost) {
        host.dialog?.dismiss()
        host.dialog = null
    }

    /** Image loading: https(s) URL or data: URI → bitmap on the UI thread. */
    fun loadInto(view: ImageView, src: String) {
        if (src.startsWith("data:")) {
            val comma = src.indexOf(',')
            if (comma <= 0) return
            val meta = src.substring(5, comma)
            val data = src.substring(comma + 1)
            try {
                val bytes = if (meta.contains("base64", true)) {
                    android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                } else {
                    Uri.decode(data).toByteArray()
                }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                view.setImageBitmap(bmp)
            } catch (_: Exception) { }
            return
        }
        val uri = Uri.parse(src)
        if (uri.scheme != "http" && uri.scheme != "https") return // §47
        val key = src
        inflight.remove(key)?.cancel()
        val call = http.newCall(Request.Builder().url(src).build())
        inflight[key] = call
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { inflight.remove(key) }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                inflight.remove(key)
                val bytes = try { response.body?.bytes() } catch (_: Exception) { null } ?: return
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                view.post { view.setImageBitmap(bmp) }
            }
        })
    }

    /** Layout params (§13) — applied when the view is added to its parent. */
    fun applyLayout(view: View, parent: ViewGroup, props: JSONObject) {
        val density = view.resources.displayMetrics.density
        val width = LayoutMapper.sizeToPx(props.opt("width"), density, ViewGroup.LayoutParams.WRAP_CONTENT)
        val height = LayoutMapper.sizeToPx(props.opt("height"), density, ViewGroup.LayoutParams.WRAP_CONTENT)
        val lp = LayoutMapper.newLayoutParams(parentKindOf(parent), width, height)
        if (lp is ViewGroup.MarginLayoutParams && props.has("margin")) {
            val m = (props.optDouble("margin", 0.0) * density).toInt()
            lp.setMargins(m, m, m, m)
        }
        if (lp is android.widget.LinearLayout.LayoutParams && props.has("weight")) {
            lp.weight = props.optDouble("weight", 0.0).toFloat()
        }
        val align = props.optString("align", "")
        val gravity = props.optString("gravity", "")
        val g = if (align.isNotEmpty()) align else gravity
        if (g.isNotEmpty()) {
            when (lp) {
                is android.widget.LinearLayout.LayoutParams -> lp.gravity = LayoutMapper.gravityToFlags(g, lp.gravity)
                is android.widget.FrameLayout.LayoutParams -> lp.gravity = LayoutMapper.gravityToFlags(g, lp.gravity)
            }
        }
        view.layoutParams = lp
    }
}
