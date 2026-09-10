package com.pipgo.runtime.runtime.renderer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.pipgo.runtime.runtime.bridge.Op
import com.pipgo.runtime.runtime.components.DialogHost
import com.pipgo.runtime.runtime.components.HasContentContainer
import com.pipgo.runtime.runtime.components.PropApplier

/**
 * Applies op batches to the native view tree (§31-§35). All calls happen on
 * the Android UI thread — the bridge dispatcher guarantees ordering (§35).
 */
class NativeRenderer(
    private val context: Context,
    /** The full-screen root container owned by the runtime. */
    val rootContainer: FrameLayout,
    private val onLog: (level: String, args: List<String>) -> Unit = { _, _ -> },
    private val onHttp: (op: Op.Http) -> Unit = { _ -> },
    private val onHttpCancel: (callId: String) -> Unit = { _ -> },
    private val onTimer: (callId: String, kind: String, delayMs: Long) -> Unit = { _, _, _ -> },
    private val onTimerClear: (callId: String) -> Unit = { _ -> },
) {

    val registry = NativeNodeRegistry()
    private val density = context.resources.displayMetrics.density
    private val viewTags = HashMap<View, String>()

    init {
        registry.put(ROOT_CONTAINER, rootContainer)
        com.pipgo.runtime.runtime.components.Factories.registerAll()
    }

    fun applyBatch(ops: List<Op>) {
        for (op in ops) {
            try { apply(op) } catch (e: Exception) {
                onLog("error", listOf("op ${op::class.simpleName} failed: ${e.message}"))
            }
        }
    }

    private fun apply(op: Op) {
        when (op) {
            is Op.Create -> applyCreate(op)
            is Op.Update -> applyUpdate(op)
            is Op.Remove -> applyRemove(op)
            is Op.PushScreen -> applyPushScreen(op)
            is Op.PopScreen -> popTopScreen()
            is Op.Http -> onHttp(op)
            is Op.HttpCancel -> onHttpCancel(op.callId)
            is Op.Timer -> onTimer(op.callId, op.kind, op.delayMs)
            is Op.TimerClear -> onTimerClear(op.callId)
            is Op.Log -> {
                val args = (0 until op.args.length()).map { op.args.opt(it)?.toString() ?: "null" }
                onLog(op.level, args)
            }
        }
    }

    private fun applyCreate(op: Op.Create) {
        if (registry.get(op.id) != null) return // idempotent (fast refresh replay)
        val parent = resolveParent(op.parent) ?: return
        val view = ComponentRegistry_create(op.component) ?: run {
            onLog("error", listOf("unknown component: ${op.component}"))
            return
        }
        view.id = View.generateViewId()
        registry.put(op.id, view)
        viewTags[view] = op.component

        val props = op.props
        PropApplier.applyStatic(context, view, op.component, props, op.id, isNew = true)

        val beforeView = op.before?.let { registry.get(it) }
        val index = if (beforeView != null && parent == beforeView.parent) parent.indexOfChild(beforeView) else -1
        if (index >= 0) parent.addView(view, index) else parent.addView(view)

        PropApplier.applyLayout(view, parent, props)
    }

    private fun applyUpdate(op: Op.Update) {
        val view = registry.get(op.id) ?: return
        val tag = viewTags[view] ?: return
        PropApplier.applyStatic(context, view, tag, op.props, op.id, isNew = false)
    }

    private fun applyRemove(op: Op.Remove) {
        val view = registry.remove(op.id) ?: return
        if (view is DialogHost) PropApplier.dismissDialog(view)
        // dismiss any dialogs nested under this subtree
        if (view is ViewGroup) dismissDialogsDeep(view)
        registry.detachFromParent(view)
    }

    private fun dismissDialogsDeep(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            if (c is DialogHost) PropApplier.dismissDialog(c)
            if (c is ViewGroup) dismissDialogsDeep(c)
        }
    }

    private fun applyPushScreen(op: Op.PushScreen) {
        // Native screen container (§37): a full-screen FrameLayout on top of the stack
        val screen = FrameLayout(context)
        screen.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        registry.put(op.id, screen)
        rootContainer.addView(screen)
    }

    /** Pop the top-most pushed screen container (native back handling). */
    fun popTopScreen() {
        if (rootContainer.childCount <= 1) return
        val top = rootContainer.getChildAt(rootContainer.childCount - 1)
        rootContainer.removeView(top)
        // drop registry entries for this subtree is handled by JS remove ops
    }

    private fun resolveParent(parentId: String): ViewGroup? {
        val parentView = registry.get(parentId) ?: return null
        // Scroll children go into the inner content container
        val target = if (parentView is HasContentContainer) parentView.contentView else parentView
        return target as? ViewGroup
    }

    /** Component creation via the registry (kept as a function for clarity). */
    private fun ComponentRegistry_create(tag: String): View? =
        com.pipgo.runtime.runtime.components.ComponentRegistry.create(tag, context)

    companion object {
        const val ROOT_CONTAINER = "screen:0"
    }
}
