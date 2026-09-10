package com.pipgo.runtime.runtime.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge protocol (§33). JS pushes one JSON string per batch:
 * `{"op":"batch","ops":[...]}`. Parsed here, applied on the UI thread (§35).
 */

sealed class Op {
    abstract val id: String?

    data class Create(
        override val id: String,
        val component: String,
        val parent: String,
        val before: String?,
        val props: JSONObject,
    ) : Op()

    data class Update(override val id: String, val props: JSONObject) : Op()
    data class Remove(override val id: String) : Op()
    data class PushScreen(override val id: String, val name: String, val params: JSONObject?) : Op()
    data class PopScreen(override val id: String?) : Op()
    data class Http(override val id: String, val callId: String, val method: String, val url: String,
                    val headers: JSONObject, val body: String?, val timeoutMs: Long) : Op()
    data class HttpCancel(override val id: String, val callId: String) : Op()
    data class Timer(override val id: String, val callId: String, val kind: String, val delayMs: Long) : Op()
    data class TimerClear(override val id: String, val callId: String) : Op()
    data class Log(override val id: String?, val level: String, val args: JSONArray) : Op()
}

object OpParser {

    fun parseBatch(json: String): List<Op> {
        val root = JSONObject(json)
        return when (root.optString("op")) {
            "batch" -> parseOps(root.getJSONArray("ops"))
            else -> parseOps(JSONArray().put(root))
        }
    }

    private fun parseOps(arr: JSONArray): List<Op> {
        val out = ArrayList<Op>(arr.length())
        for (i in 0 until arr.length()) out.add(parseOne(arr.getJSONObject(i)))
        return out
    }

    private fun parseOne(o: JSONObject): Op = when (o.optString("op")) {
        "create" -> Op.Create(
            id = o.getString("id"),
            component = o.optString("component", "View"),
            parent = o.optString("parent", "screen:0"),
            before = if (o.has("before") && !o.isNull("before")) o.getString("before") else null,
            props = o.optJSONObject("props") ?: JSONObject(),
        )
        "update" -> Op.Update(o.getString("id"), o.optJSONObject("props") ?: JSONObject())
        "remove" -> Op.Remove(o.getString("id"))
        "pushScreen" -> Op.PushScreen(o.getString("id"), o.optString("name", ""),
            if (o.has("params") && !o.isNull("params")) o.getJSONObject("params") else null)
        "popScreen" -> Op.PopScreen(null)
        "http" -> Op.Http(
            id = o.optString("callId"),
            callId = o.getString("callId"),
            method = o.optString("method", "GET"),
            url = o.optString("url", ""),
            headers = o.optJSONObject("headers") ?: JSONObject(),
            body = if (o.has("body") && !o.isNull("body")) o.get("body").toString() else null,
            timeoutMs = o.optLong("timeoutMs", 15000L),
        )
        "http-cancel" -> Op.HttpCancel(null, o.optString("callId"))
        "timer" -> Op.Timer(null, o.getString("callId"), o.optString("kind", "timeout"), o.optLong("delayMs", 0L))
        "timer-clear" -> Op.TimerClear(null, o.optString("callId"))
        "log" -> Op.Log(null, o.optString("level", "info"), o.optJSONArray("args") ?: JSONArray())
        else -> Op.Log(null, "warn", JSONArray().put("unknown op: ${o.optString("op")}"))
    }
}
