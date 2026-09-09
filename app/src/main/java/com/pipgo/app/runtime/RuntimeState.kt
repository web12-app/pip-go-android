package com.pipgo.app.runtime

/**
 * Runtime state machine (docs/architecture.md):
 * disconnected → connecting → live ⇄ (reconnecting | offlineFallback) → error
 */
sealed class RuntimeState {
    data object Disconnected : RuntimeState()
    data class Connecting(val url: String) : RuntimeState()
    data class Live(
        val url: String,
        val project: String,
        val bundleVersion: Int,
        val viewport: String,
        val fastRefresh: Boolean
    ) : RuntimeState()

    data class Reconnecting(val url: String, val attempt: Int) : RuntimeState()
    data class OfflineFallback(val url: String, val cachedVersion: String) : RuntimeState()
    data class AppError(val url: String, val message: String) : RuntimeState()

    val isBusy: Boolean get() = this is Connecting || this is Reconnecting
}
