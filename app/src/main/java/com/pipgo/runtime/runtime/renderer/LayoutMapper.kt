package com.pipgo.runtime.runtime.renderer

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Layout system (§13): maps the cross-platform layout API onto Android
 * LayoutParams. Values: "match" | "wrap" | number(dp) | "fill".
 */
object LayoutMapper {

    const val MATCH = -1
    const val WRAP = -2
    const val FILL = -3 // alias of match (§13)

    fun sizeToPx(value: Any?, density: Float, fallback: Int = WRAP): Int = when (value) {
        null -> fallback
        is String -> when (value.lowercase()) {
            "match", "fill", "match_parent", "fill_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            "wrap", "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> value.removeSuffix("dp").toFloatOrNull()?.let { (it * density).toInt() } ?: fallback
        }
        is Number -> (value.toDouble() * density).toInt()
        else -> fallback
    }

    /** Parse gravity strings: "center", "start", "end", "top", "bottom", combos like "center|bottom". */
    fun gravityToFlags(value: String?, default: Int): Int {
        if (value.isNullOrBlank()) return default
        var g = 0
        for (part in value.split("|", ",")) {
            when (part.trim().lowercase()) {
                "center" -> g = g or Gravity.CENTER
                "center_horizontal" -> g = g or Gravity.CENTER_HORIZONTAL
                "center_vertical" -> g = g or Gravity.CENTER_VERTICAL
                "start", "left" -> g = g or Gravity.START
                "end", "right" -> g = g or Gravity.END
                "top" -> g = g or Gravity.TOP
                "bottom" -> g = g or Gravity.BOTTOM
            }
        }
        return if (g == 0) default else g
    }

    fun applyGravity(view: View, gravity: String?) {
        (view.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = gravityToFlags(gravity, it.gravity)
            view.layoutParams = it
        }
        (view.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.gravity = gravityToFlags(gravity, it.gravity)
            view.layoutParams = it
        }
    }

    fun newLayoutParams(parentKind: ParentKind, width: Int, height: Int): ViewGroup.LayoutParams = when (parentKind) {
        ParentKind.LINEAR -> LinearLayout.LayoutParams(width, height)
        ParentKind.FRAME -> FrameLayout.LayoutParams(width, height)
        ParentKind.OTHER -> ViewGroup.LayoutParams(width, height)
    }
}

enum class ParentKind { LINEAR, FRAME, OTHER }

fun parentKindOf(parent: ViewGroup): ParentKind = when (parent) {
    is LinearLayout -> ParentKind.LINEAR
    is FrameLayout -> ParentKind.FRAME
    else -> ParentKind.OTHER
}
