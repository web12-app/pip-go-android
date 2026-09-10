package com.pipgo.runtime.runtime.bundle

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Bundle storage (§28): files/pipgo/bundles/{current.js, previous.js} +
 * metadata.json. Startup loads the cached bundle immediately (§29); a new
 * bundle is only activated after integrity checks pass (§30).
 */
class BundleCache(context: Context) {

    private val dir: File = File(context.filesDir, "pipgo/bundles").apply { mkdirs() }
    private val currentFile: File get() = File(dir, "current.js")
    private val previousFile: File get() = File(dir, "previous.js")
    private val metaFile: File = File(dir, "metadata.json")

    data class Meta(val version: Int, val hash: String, val source: String, val savedAt: Long)

    fun currentCode(): String? = try {
        currentFile.takeIf { it.length() > 0 }?.readText()
    } catch (_: Exception) { null }

    fun currentMeta(): Meta? = try {
        val o = JSONObject(metaFile.takeIf { it.exists() }?.readText() ?: return null)
        Meta(o.optInt("version", 0), o.optString("hash"), o.optString("source"), o.optLong("savedAt", 0))
    } catch (_: Exception) { null }

    /** §30: promote validated temp → current, old current → previous. */
    fun activate(tempFile: File, meta: Meta) {
        try {
            if (currentFile.exists()) {
                previousFile.delete()
                currentFile.copyTo(previousFile, overwrite = true)
            }
            tempFile.copyTo(currentFile, overwrite = true)
            tempFile.delete()
            metaFile.writeText(
                JSONObject()
                    .put("version", meta.version)
                    .put("hash", meta.hash)
                    .put("source", meta.source)
                    .put("savedAt", meta.savedAt)
                    .toString()
            )
            Log.i(TAG, "bundle v${meta.version} activated (${meta.source})")
        } catch (e: Exception) {
            Log.e(TAG, "activate failed", e)
        }
    }

    /** Validate (§30): non-empty + size plausible (+ hash when known). */
    fun validate(tempFile: File, expectedHash: String?, expectedSize: Long?): Boolean {
        return try {
            if (!tempFile.exists() || tempFile.length() == 0L) return false
            val code = tempFile.readText()
            if (code.isBlank() || code.length < 8) return false
            if (expectedSize != null && expectedSize > 0 &&
                Math.abs(tempFile.length() - expectedSize) > 64) return false
            if (expectedHash != null && expectedHash.isNotEmpty()) {
                val actual = hashOf(code)
                if (!actual.startsWith(expectedHash)) return false
            }
            true
        } catch (_: Exception) { false }
    }

    fun newTempFile(): File = File(dir, "incoming-${System.currentTimeMillis()}.js")

    fun hashOf(code: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object { private const val TAG = "PipGo.BundleCache" }
}
