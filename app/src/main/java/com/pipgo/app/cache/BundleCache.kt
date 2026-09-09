package com.pipgo.app.cache

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Last-good bundle cache (§21 Offline Cache).
 *
 * Layout:
 *   <cacheDir>/pipgo/
 *     bundle.js      — last successfully served bundle
 *     meta.json      — { "project": "...", "version": N, "url": "...", "ts": millis }
 *
 * The offline host page (assets/offline/index.html) is shipped as a runtime
 * asset and loads bundle.js relative to the WebViewAssetLoader base URL.
 */
class BundleCache(context: Context) {

    private val dir: File = File(context.applicationContext.cacheDir, "pipgo").apply { mkdirs() }
    private val bundleFile: File get() = File(dir, "bundle.js")
    private val metaFile: File get() = File(dir, "meta.json")

    fun save(project: String, url: String, bundleVersion: Int, bytes: ByteArray) {
        try {
            val tmp = File(dir, "bundle.js.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(bundleFile)) {
                bundleFile.writeBytes(bytes)
                tmp.delete()
            }
            metaFile.writeText(
                JSONObject()
                    .put("project", project)
                    .put("version", bundleVersion)
                    .put("url", url)
                    .put("ts", System.currentTimeMillis())
                    .toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "cache save failed: ${e.message}")
        }
    }

    /** Reads the cached bundle bytes, or null when no cache exists. */
    fun loadBundle(): ByteArray? = bundleFile.takeIf { it.exists() }?.readBytes()

    fun meta(): JSONObject? = try {
        if (metaFile.exists()) JSONObject(metaFile.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun cachedVersionLabel(): String {
        val m = meta() ?: return "unknown"
        return "${m.optString("project", "?")} v${m.optInt("version", 0)}"
    }

    fun clear() {
        bundleFile.delete()
        metaFile.delete()
    }

    companion object {
        private const val TAG = "BundleCache"
    }
}
