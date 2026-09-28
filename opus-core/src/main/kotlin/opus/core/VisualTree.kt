package opus.core

import java.util.IdentityHashMap

/** Resolved paint/input coordinates after layout, shared by renderers and hit testing. */
data class VisualBounds(val x: Float, val y: Float, val opacity: Float, val inputEnabled: Boolean)

/** Rebuild after layout or recomposition. Offsets and opacity apply to the whole subtree. */
class VisualTree(private val root: UiNode) {
    private val bounds = IdentityHashMap<UiNode, VisualBounds>()
    private val identities = mutableMapOf<String, UiNode>()

    init { resolve(root, 0f, 0f, 1f, true) }

    fun bounds(node: UiNode): VisualBounds? = bounds[node]
    fun find(identity: String): UiNode? = identities[identity]

    private fun resolve(node: UiNode, parentX: Float, parentY: Float, parentAlpha: Float, parentInput: Boolean) {
        var x = parentX
        var y = parentY
        var alpha = parentAlpha
        var enabled = parentInput && node.visible
        for (element in node.modifier.elements()) when (element) {
            is Offset -> { x += element.x; y += element.y }
            is Opacity -> alpha *= element.alpha
            is InputEnabled -> enabled = enabled && element.enabled
            else -> Unit
        }
        if (!node.visible) alpha = 0f
        bounds[node] = VisualBounds(node.x + x, node.y + y, alpha, enabled && alpha > 0f)
        node.identity?.let { identities[it] = node }
        if (node.type != "Accordion" || node.props["expanded"] == true) {
            node.children.forEach { resolve(it, x, y, alpha, enabled) }
        }
    }

    /** Topmost path, leaf first. Disabled visible containers block clicks through their bounds. */
    fun hitPath(x: Float, y: Float): List<UiNode> {
        fun visit(node: UiNode): List<UiNode> {
            val b = bounds[node] ?: return emptyList()
            if (b.opacity <= 0f) return emptyList()
            val inside = x >= b.x && x <= b.x + node.width && y >= b.y && y <= b.y + node.height
            if (!b.inputEnabled) return if (inside) listOf(node) else emptyList()
            for (child in node.children.asReversed()) {
                val hit = visit(child)
                if (hit.isNotEmpty()) return if (inside && node.type != "Root") hit + node else hit
            }
            return if (inside && node.type != "Root") listOf(node) else emptyList()
        }
        return visit(root)
    }
}
