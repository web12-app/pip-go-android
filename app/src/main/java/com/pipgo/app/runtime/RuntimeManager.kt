package com.pipgo.app.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.pipgo.app.bridge.PipGoBridge
import com.pipgo.app.cache.BundleCache
import com.pipgo.app.dev.DevConnection
import com.pipgo.app.nativeview.NativeViewHost
import com.pipgo.app.webview.RuntimeWebViewFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Central runtime manager: owns the WebView, the dev connection, the bundle
 * cache and the native view host. Exposes a single [state] flow the UI reacts
 * to (see docs/architecture.md for the state machine).
 */
class RuntimeManager(
    private val context: Context,
) : PipGoBridge.BridgeHost {

    private val cache = BundleCache(context)
    private val settings = com.pipgo.app.settings.SettingsStore(context)
    private val connection = DevConnection()

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Disconnected)
    val state: StateFlow<RuntimeState> = _state

    val statusLog = ArrayDeque<String>()

    @Volatile var projectName: String = "app"
        private set

    lateinit var webView: WebView
        private set

    val nativeViewHost = NativeViewHost()

    private val bridge by lazy { PipGoBridge(this) }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ---- lifecycle ----------------------------------------------------------

    fun ensureWebView(): WebView {
        if (!this::webView.isInitialized) {
            webView = RuntimeWebViewFactory.create(context, bridge, cache) { event, detail ->
                handlePageEvent(event, detail)
            }
            connection.listener = devListener
        }
        return webView
    }

    fun connect(rawUrl: String) {
        val url = rawUrl.trim().removeSuffix("/")
        if (url.isBlank()) return

        _state.value = RuntimeState.Connecting(url)
        Thread {
            val ping = DevConnection.ping(url)
            if (ping == null) {
                // server unreachable → offline fallback if we have a cache (§21)
                if (cache.loadBundle() != null) {
                    mainHandler.post {
                        _state.value = RuntimeState.OfflineFallback(url, cache.cachedVersionLabel())
                        loadOffline()
                    }
                } else {
                    mainHandler.post {
                        _state.value = RuntimeState.AppError(url, "Development server unreachable:\n$url")
                    }
                }
                return@Thread
            }
            projectName = ping.optString("project", "app").ifBlank { "app" }
            val version = ping.optInt("bundleVersion", 0)
            val fastRefresh = ping.optBoolean("fastRefresh", false)

            // warm the offline cache with the first good bundle (§21)
            DevConnection.downloadBundle(url)?.let { bytes ->
                cache.save(projectName, url, version, bytes)
            }

            settings.rememberUrl(url)
            mainHandler.post {
                _state.value = RuntimeState.Live(
                    url = url,
                    project = projectName,
                    bundleVersion = version,
                    viewport = viewportInfoJson(),
                    fastRefresh = fastRefresh,
                )
                ensureWebView().loadUrl(url)
                connection.start(url)
            }
        }.start()
    }

    fun reload() {
        if (this::webView.isInitialized) {
            mainHandler.post { webView.reload() }
        }
    }

    fun disconnect() {
        connection.disconnect()
        _state.value = RuntimeState.Disconnected
    }

    private fun loadOffline() {
        ensureWebView().loadUrl(RuntimeWebViewFactory.CACHE_BASE_URL + "index.html")
    }

    private fun handlePageEvent(event: String, detail: String) {
        when (event) {
            "console" -> appendLog(detail)
            "pageFinished" -> appendLog("page finished: $detail")
        }
    }

    @Synchronized
    private fun appendLog(line: String) {
        statusLog.addLast(line)
        while (statusLog.size > 50) statusLog.removeFirst()
    }

    // ---- dev connection listener ---------------------------------------------

    private val devListener = object : DevConnection.Listener {
        override fun onReload(reason: String) {
            // fast-refresh swaps are handled in-page; native reload covers the rest
            appendLog("reload requested ($reason)")
            reload()
        }

        override fun onFastRefresh(version: Int) {
            appendLog("fast refresh v$version (handled in page)")
        }

        override fun onBuildError(errors: org.json.JSONArray) {
            appendLog("build error: ${errors.length()} issue(s)")
        }

        override fun onBuildFixed() {
            appendLog("build fixed")
        }

        override fun onFailure(message: String) {
            appendLog("ws failure: $message")
            val s = _state.value
            if (s is RuntimeState.Live) {
                _state.value = RuntimeState.Reconnecting(s.url, 1)
            }
        }

        override fun onReconnecting(attempt: Int) {
            val s = _state.value
            if (s is RuntimeState.Live || s is RuntimeState.Reconnecting) {
                _state.value = RuntimeState.Reconnecting((s as? RuntimeState.Live)?.url ?: s.url.let { baseUrl() }, attempt)
            }
        }

        override fun onOpen() {
            val s = _state.value
            if (s is RuntimeState.Reconnecting) {
                _state.value = RuntimeState.Live(
                    url = s.url,
                    project = projectName,
                    bundleVersion = 0,
                    viewport = viewportInfoJson(),
                    fastRefresh = true,
                )
            }
        }
    }

    private fun baseUrl(): String = (state.value as? RuntimeState.Live)?.url ?: ""

    // ---- BridgeHost (docs/bridge-protocol.md) ---------------------------------

    override fun currentProjectName(): String = projectName

    override fun deviceInfoJson(): String = JSONObject().apply {
        val dm = context.resources.displayMetrics
        put("platform", "android")
        put("model", Build.MANUFACTURER + " " + Build.MODEL)
        put("osVersion", Build.VERSION.RELEASE ?: "")
        put("appVersion", "1.0.0")
        put("screenWidth", dm.widthPixels)
        put("screenHeight", dm.heightPixels)
        put("widthDp", dm.widthPixels / dm.density)
        put("heightDp", dm.heightPixels / dm.density)
        put("density", dm.density)
        put("dpr", maxOf(1, minOf(3, Math.round(dm.density))))
        put("orientation", if (dm.widthPixels >= dm.heightPixels) "landscape" else "portrait")
        put(
            "safeArea", JSONObject().apply {
                val insets = (webView.rootWindowInsets)
                if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val displayCutout = insets.displayCutout
                    put("top", displayCutout?.safeInsetTop ?: 0)
                    put("bottom", displayCutout?.safeInsetBottom ?: 0)
                    put("left", displayCutout?.safeInsetLeft ?: 0)
                    put("right", displayCutout?.safeInsetRight ?: 0)
                } else {
                    put("top", 0); put("bottom", 0); put("left", 0); put("right", 0)
                }
            }
        )
    }.toString()

    override fun viewportInfoJson(): String = JSONObject().apply {
        val dm = context.resources.displayMetrics
        put("width", dm.widthPixels / dm.density)
        put("height", dm.heightPixels / dm.density)
        put("dpr", maxOf(1, minOf(3, Math.round(dm.density))))
        put("keyboardVisible", false)
    }.toString()

    override fun networkStatusJson(): String = JSONObject().apply {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "none"
        }
        put("online", online)
        put("type", type)
        put("isMetered", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true)
    }.toString()

    override fun storageGet(nsKey: String): String? =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(nsKey, null)

    override fun storageSet(nsKey: String, value: String): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(nsKey, value).apply().let { true }

    override fun storageRemove(nsKey: String): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(nsKey).apply().let { true }

    override fun viewCreate(specJson: String): Boolean {
        appendLog("viewCreate ${specJson.take(60)}")
        nativeViewHost.create(webView, specJson)
        return true
    }

    override fun viewUpdate(specJson: String): Boolean {
        nativeViewHost.update(specJson)
        return true
    }

    override fun viewDestroy(id: String): Boolean {
        nativeViewHost.destroy(id)
        return true
    }

    override fun requestReload() = reload()

    override fun log(level: String, message: String) {
        when (level) {
            "error" -> Log.e("PipGoApp", message)
            "warn" -> Log.w("PipGoApp", message)
            else -> Log.i("PipGoApp", message)
        }
        appendLog("[$level] $message")
    }

    companion object {
        private const val STORE = "pipgo_storage"
    }
}
