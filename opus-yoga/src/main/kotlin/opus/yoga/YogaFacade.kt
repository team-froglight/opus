package opus.yoga

import org.lwjgl.util.yoga.Yoga

/**
 * Thin facade over LWJGL's Yoga bindings (raw C API, zero wrapper overhead).
 * Handles are raw YGNodeRef values (Long).
 */
object YogaFacade {
    fun createNode(): Long = Yoga.YGNodeNew()
    fun freeTree(node: Long) = Yoga.YGNodeFreeRecursive(node)
    fun insertChild(parent: Long, child: Long, index: Int) = Yoga.YGNodeInsertChild(parent, child, index)
    fun calculateLayout(node: Long, width: Float, height: Float) {
        Yoga.YGNodeCalculateLayout(node, width, height, Yoga.YGDirectionLTR)
    }

    fun isDirty(node: Long): Boolean = Yoga.YGNodeIsDirty(node)

    fun layoutX(node: Long): Float = Yoga.YGNodeLayoutGetLeft(node)
    fun layoutY(node: Long): Float = Yoga.YGNodeLayoutGetTop(node)
    fun layoutWidth(node: Long): Float = Yoga.YGNodeLayoutGetWidth(node)
    fun layoutHeight(node: Long): Float = Yoga.YGNodeLayoutGetHeight(node)
}
