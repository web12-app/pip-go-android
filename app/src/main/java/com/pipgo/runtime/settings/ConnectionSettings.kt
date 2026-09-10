package com.pipgo.runtime.settings

import com.pipgo.runtime.storage.PipGoStorage

/**
 * Connection settings (§26): last used dev server + recent servers list.
 * Development connections are plain LAN URLs — no tunnel, no tokens (§27).
 */
class ConnectionSettings(private val storage: PipGoStorage) {

    fun lastServer(): String? = storage.lastUrl()

    fun recentServers(): List<String> = storage.recentUrls()

    fun rememberServer(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) return
        storage.setLastUrl(clean)
        storage.addRecentUrl(clean)
    }
}
