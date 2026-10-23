@file:JvmName("Nodes")

package opus.core

/** UI tree node. Layout fields are filled by Yoga; culling uses [isOutsideViewport]. */
class UiNode(val type: String) {
    /** Stable across recompositions of the same emitted node, until its group leaves the tree. */
    var identity: String? = null
        internal set
    val children = mutableListOf<UiNode>()
    val props = mutableMapOf<String, Any?>()
    /** Typed callbacks from the `events` prop; nodes without one have none. */
    val events: ControlEvents get() = props["events"] as? ControlEvents ?: ControlEvents.NONE
    val options: List<String> get() {
        val value = props["options"] ?: return emptyList()
        require(value is List<*> && value.all { it is String }) { "$type.options must be a list of strings" }
        return value.map { it as String }
    }
    val selectedOptions: Set<Int> get() {
        val value = props["selected"] ?: return emptySet()
        require(value is Set<*> && value.all { it is Int }) { "$type.selected must be a set of indices" }
        return value.map { it as Int }.toSet()
    }
    /** Scope is captured while composing; rendering never consults the thread-local theme. */
    var theme: Theme = Theme()
        internal set
    var modifier: Modifier = Modifier

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var height: Float = 0f
    var visible: Boolean = true

    fun isOutsideViewport(viewX: Float, viewY: Float, viewW: Float, viewH: Float): Boolean =
        x + width < viewX || y + height < viewY || x > viewX + viewW || y > viewY + viewH

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
    node.theme = LocalTheme.current
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
