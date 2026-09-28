@file:JvmName("Nodes")

package opus.core

/** UI tree node. Layout fields are filled by Yoga; culling uses [isOutsideViewport]. */
class UiNode(val type: String) {
    /** Stable across recompositions of the same emitted node, until its group leaves the tree. */
    var identity: String? = null
        internal set
    val children = mutableListOf<UiNode>()
    val props = mutableMapOf<String, Any?>()
    var modifier: Modifier = Modifier

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var height: Float = 0f
    var visible: Boolean = true

    fun isOutsideViewport(viewX: Float, viewY: Float, viewW: Float, viewH: Float): Boolean =
        x + width < viewX || y + height < viewY || x > viewX + viewW || y > viewY + viewH

    @Deprecated("Use isOutsideViewport", ReplaceWith("isOutsideViewport(viewX, viewY, viewW, viewH)"))
    fun isOffview(viewX: Float, viewY: Float, viewW: Float, viewH: Float): Boolean =
        isOutsideViewport(viewX, viewY, viewW, viewH)

    /** Depth-first walk that skips subtrees outside the viewport. */
    fun visitVisible(viewX: Float, viewY: Float, viewW: Float, viewH: Float, action: (UiNode) -> Unit) {
        if (!visible || isOutsideViewport(viewX, viewY, viewW, viewH)) return
        action(this)
        children.forEach { it.visitVisible(viewX, viewY, viewW, viewH, action) }
    }

    fun findAll(type: String): List<UiNode> {
        val out = ArrayList<UiNode>()
        if (this.type == type) out += this
        children.forEach { out += it.findAll(type) }
        return out
    }
}

/** Emits a node into the tree currently being composed. */
fun emit(type: String, modifier: Modifier = Modifier, props: Map<String, Any?> = emptyMap(), content: (@Composable () -> Unit)? = null) {
    val composer = Composition.currentComposer ?: error("emit() outside of setContent { }")
    val parent = currentParentNode()
    val node = UiNode(type)
    node.identity = composer.nodeIdentity(parent.children.size)
    node.modifier = modifier
    node.props.putAll(props)
    parent.children += node
    if (content != null) {
        composer.startGroup(type)
        try {
            withParentNode(node) { content() }
        } finally {
            composer.endGroup()
        }
    }
}

private val parentStack = ThreadLocal.withInitial { ArrayDeque<UiNode>() }

internal fun currentParentNode(): UiNode {
    val stack = parentStack.get()
    return stack.lastOrNull() ?: error("No parent node — emit() must run inside setContent { }")
}

internal fun <T> withParentNode(node: UiNode, block: () -> T): T {
    parentStack.get().addLast(node)
    try {
        return block()
    } finally {
        parentStack.get().removeLast()
    }
}
