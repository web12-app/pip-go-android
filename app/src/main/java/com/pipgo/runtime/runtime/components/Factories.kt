package com.pipgo.runtime.runtime.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject

/** Scroll hosts children inside an inner container, not the scroll view itself. */
interface HasContentContainer { val contentView: ViewGroup }

/** Input = TextInputLayout + TextInputEditText (§10). */
class InputHost(context: Context) : LinearLayout(context) {
    val layout = TextInputLayout(context)
    val edit = TextInputEditText(context)
    init {
        orientation = VERTICAL
        layout.addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); layout.isEnabled = enabled; edit.isEnabled = enabled }
}

/** Dialog (§10): imperative component — a placeholder view plus managed dialog. */
class DialogHost(context: Context) : FrameLayout(context) {
    var dialog: androidx.appcompat.app.AlertDialog? = null
}

/** List (§10): RecyclerView + adapter over serialized item trees. */
class ListHost(context: Context) : RecyclerView(context) {
    val adapter = ItemTreeAdapter(context)
    init {
        layoutManager = LinearLayoutManager(context)
        setAdapter(this.adapter)
        overScrollMode = View.OVER_SCROLL_NEVER
    }
}

/**
 * All §10 native mappings. Material components where applicable (§41):
 * Button→MaterialButton, Card→MaterialCardView, Switch→SwitchMaterial,
 * Input→TextInputLayout, List→RecyclerView, Dialog→MaterialAlertDialog, …
 */
object Factories {

    fun registerAll() {
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "View"
            override fun create(context: Context) = FrameLayout(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Column"
            override fun create(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Row"
            override fun create(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Text"
            override fun create(context: Context) = AppCompatTextView(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Button"
            override fun create(context: Context) = MaterialButton(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Input"
            override fun create(context: Context) = InputHost(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Image"
            override fun create(context: Context) = AppCompatImageView(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Card"
            override fun create(context: Context) = MaterialCardView(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Scroll"
            override fun create(context: Context) = object : androidx.core.widget.NestedScrollView(context), HasContentContainer {
                override val contentView = FrameLayout(context).also {
                    addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                }
            }
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "List"
            override fun create(context: Context) = ListHost(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Switch"
            override fun create(context: Context) = SwitchMaterial(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Checkbox"
            override fun create(context: Context) = MaterialCheckBox(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Radio"
            override fun create(context: Context) = MaterialRadioButton(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Progress"
            override fun create(context: Context) = CircularProgressIndicator(context).apply { isIndeterminate = true }
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Divider"
            override fun create(context: Context) = MaterialDivider(context)
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "WebView"
            override fun create(context: Context) = android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        })
        ComponentRegistry.register(object : ComponentFactory {
            override val tag = "Dialog"
            override fun create(context: Context) = DialogHost(context)
        })
    }
}

/**
 * Adapter for <List> (§10): binds serialized item trees
 * `{key, tree:{id,c,p,ch}}` produced by the SDK reconciler into recycled
 * FrameLayout holders, creating REAL native views (§31/§32).
 */
class ItemTreeAdapter(private val context: Context) : RecyclerView.Adapter<ItemTreeAdapter.Holder>() {

    private var items: JSONArray = JSONArray()
    private val reuse = HashMap<String, View>()

    class Holder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    fun setItems(json: Any?) {
        items = when (json) {
            is JSONArray -> json
            is String -> try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            null -> JSONArray()
            else -> JSONArray()
        }
        notifyDataSetChanged() // keyed rebind optimization can come later
    }

    override fun getItemCount(): Int = items.length()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val container = FrameLayout(parent.context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder(container)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items.optJSONObject(position) ?: return
        val tree = item.optJSONObject("tree") ?: return
        holder.container.removeAllViews()
        val view = ItemTree.build(context, tree, holder.container)
        if (view != null) holder.container.addView(view)
    }
}

/** Builds a native view subtree from a serialized item tree. */
object ItemTree {
    fun build(context: Context, node: JSONObject, parent: ViewGroup): View? {
        val tag = node.optString("c", "View")
        val view = ComponentRegistry.create(tag, context) ?: return null
        val props = node.optJSONObject("p") ?: JSONObject()
        val nodeId = node.optString("id", "")
        PropApplier.applyStatic(context, view, tag, props, nodeId)
        PropApplier.applyEvents(view, props, nodeId)
        val children = node.optJSONArray("ch")
        if (children != null && view is ViewGroup) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                val cv = build(context, child, view) ?: continue
                PropApplier.applyLayout(cv, view, child.optJSONObject("p") ?: JSONObject())
                view.addView(cv)
            }
        }
        return view
    }
}
