package com.pipgo.runtime.storage

import android.content.Context

/**
 * Key/value storage backing the SDK `Storage` API (§38). SharedPreferences is
 * sufficient for the expected payload sizes; larger structured data should go
 * through the app's own backend.
 */
class PipGoStorage(context: Context) {

    private val prefs = context.getSharedPreferences("pipgo_storage", Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences("pipgo_runtime", Context.MODE_PRIVATE)

    // ---- app-facing (JS §38) ----
    fun get(key: String): String? = prefs.getString(key, null)
    fun set(key: String, value: String): Boolean = try {
        prefs.edit().putString(key, value).apply(); true
    } catch (_: Exception) { false }
    fun remove(key: String): Boolean = try {
        prefs.edit().remove(key).apply(); true
    } catch (_: Exception) { false }

    // ---- runtime-facing (recent servers §26, last connection) ----
    fun lastUrl(): String? = runtimePrefs.getString("last_url", null)
    fun setLastUrl(url: String) { runtimePrefs.edit().putString("last_url", url).apply() }
    fun recentUrls(): List<String> {
        val raw = runtimePrefs.getString("recent_urls", null) ?: return emptyList()
        return try {
            org.json.JSONArray(raw).let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it, null) } }
        } catch (_: Exception) { emptyList() }
    }
    fun addRecentUrl(url: String) {
        val list = recentUrls().toMutableList()
        list.remove(url)
        list.add(0, url)
        while (list.size > 5) list.removeAt(list.size - 1)
        runtimePrefs.edit().putString("recent_urls", org.json.JSONArray(list).toString()).apply()
    }
}
