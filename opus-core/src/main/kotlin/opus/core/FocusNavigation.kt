package opus.core

/** Shared focus order follows composition order and excludes invisible, disabled and outgoing views. */
object FocusNavigation {
    private val controls = setOf("Button", "Checkbox", "RadioButton", "Switch", "Slider", "RadioGroup",
        "Dropdown", "MultiSelect", "Pagination", "Accordion", "TextField", "TextArea", "NumberField", "DateField")

    @JvmStatic fun targets(root: UiNode, visuals: VisualTree): List<UiNode> = buildList {
        fun visit(node: UiNode) {
            val bounds = visuals.bounds(node) ?: return
            if (!bounds.inputEnabled) return
            if (node.props["enabled"] != false && (node.type in controls || node.modifier.elements().any { it is Clickable })) {
                if (node.props["options"] !is List<*> || (node.props["options"] as List<*>).isNotEmpty()) add(node)
            }
            node.children.forEach(::visit)
        }
        visit(root)
    }

    @JvmStatic fun next(targets: List<UiNode>, current: String?, reverse: Boolean): UiNode? {
        if (targets.isEmpty()) return null
        val index = targets.indexOfFirst { it.identity == current }
        return targets[if (index < 0) (if (reverse) targets.lastIndex else 0)
            else Math.floorMod(index + if (reverse) -1 else 1, targets.size)]
    }
}
