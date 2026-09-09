package com.pipgo.app.settings

import android.content.Context

/** Persisted settings: last dev URL + recent projects (connection UX, §16). */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pipgo_settings", Context.MODE_PRIVATE)

    fun lastUrl(): String? = prefs.getString(KEY_LAST_URL, null)

    fun recentUrls(): List<String> =
        prefs.getStringSet(KEY_RECENTS, emptySet())?.sortedDescending().orEmpty()

    fun rememberUrl(url: String) {
        prefs.edit()
            .putString(KEY_LAST_URL, url)
            .putStringSet(KEY_RECENTS, (recentUrls() + url).distinct().takeLast(5).toSet())
            .apply()
    }

    private companion object {
        const val KEY_LAST_URL = "last_url"
        const val KEY_RECENTS = "recent_urls"
    }
}
