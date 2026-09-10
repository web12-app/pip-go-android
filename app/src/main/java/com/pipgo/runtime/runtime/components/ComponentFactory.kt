package com.pipgo.runtime.runtime.components

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Component factory (§10/§32): a JS component tag → a real native Android
 * view. Registered per tag; the renderer consults the registry.
 */
interface ComponentFactory {
    /** The JS component tag this factory serves, e.g. "Text", "Button". */
    val tag: String

    fun create(context: Context): View
}

object ComponentRegistry {
    private val factories = LinkedHashMap<String, ComponentFactory>()

    fun register(factory: ComponentFactory) { factories[factory.tag] = factory }

    fun create(tag: String, context: Context): View? =
        factories[tag]?.create(context)

    fun knownTags(): Set<String> = factories.keys
}
