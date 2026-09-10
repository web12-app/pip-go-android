package com.pipgo.runtime.runtime.renderer

import android.view.View
import android.view.ViewGroup
import java.util.concurrent.ConcurrentHashMap

/**
 * Native node registry (§31): JS node id → native android.view.View.
 * JavaScript only ever speaks in ids; the registry is the single source of
 * truth for the JS ↔ native view correspondence.
 */
class NativeNodeRegistry {
    private val nodes = ConcurrentHashMap<String, View>()

    fun put(id: String, view: View) { nodes[id] = view }
    fun get(id: String): View? = nodes[id]
    fun remove(id: String): View? = nodes.remove(id)
    fun clear() { nodes.clear() }
    fun size(): Int = nodes.size

    /** Remove a view from its parent (if attached). */
    fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun indexOfChild(parent: ViewGroup, childId: String): Int {
        val child = get(childId) ?: return -1
        return parent.indexOfChild(child)
    }
}
