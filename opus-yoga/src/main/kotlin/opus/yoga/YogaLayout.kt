package opus.yoga

import opus.core.Align
import opus.core.AlignItems
import opus.core.Composition
import opus.core.ControlMetrics
import opus.core.DropdownMetrics
import opus.core.FillMax
import opus.core.FixedSize
import opus.core.InputMetrics
import opus.core.InputOptions
import opus.core.Justify
import opus.core.JustifyContent
import opus.core.Padding
import opus.core.TextRole
import opus.core.UiNode
import opus.core.Weight
import opus.core.elements
import opus.core.isTextInput
import org.lwjgl.util.yoga.YGMeasureFunc
import org.lwjgl.util.yoga.YGMeasureFuncI
import org.lwjgl.util.yoga.Yoga

/** Measures leaf content (text). Implemented by :opus-minecraft with MC's Font. */
interface TextMeasurer {
    fun wrappedHeight(text: String, maxWidth: Float): Float =
        kotlin.math.ceil(measure(text, TextRole.LABEL, Float.MAX_VALUE).first / maxWidth.coerceAtLeast(1f)).coerceAtLeast(1f) * lineHeight(TextRole.LABEL)
    fun measure(text: String, role: TextRole, maxWidth: Float): Pair<Float, Float>
    fun measure(text: String, role: TextRole, maxWidth: Float, fontId: String?): Pair<Float, Float> =
        measure(text, role, maxWidth)
    /** Sized contract. Legacy measurers retain their metrics, scaled from their declared line height. */
    fun measure(text: String, role: TextRole, maxWidth: Float, fontId: String?, size: Float): Pair<Float, Float> {
        val scale = size / lineHeight(role)
        val measured = measure(text, role, maxWidth / scale, fontId)
        return measured.first * scale to measured.second * scale
    }
    fun lineHeight(role: TextRole): Float
}

/**
 * Owns the native Yoga tree mirror of an [UiNode] tree.
 *
 * - Rebuilds native nodes only when the composition changed (compositionVersion).
 * - Recalculates layout only when size changed or the tree is dirty.
 * - Writes x/y/width/height back to [UiNode]s every frame (cheap traversal).
 * - Measure callbacks are native closures owned by the tree and freed with its nodes.
 */
class YogaTree(private val measurer: TextMeasurer) : AutoCloseable {
    private var rootHandle: Long = 0L
    private val handles = java.util.IdentityHashMap<UiNode, Long>()
    private val measureFuncs = mutableListOf<YGMeasureFunc>()
    private var lastCompositionVersion = -1
    private var lastRoot: UiNode? = null
    private var lastBuildWidth = Float.NaN
    private var lastW = Float.NaN
    private var lastH = Float.NaN
    private val config: Long = Yoga.YGConfigNew()

    /**
     * Physical pixels per layout unit, such as Minecraft's GUI scale. Yoga rounds positions to whole
     * physical pixels, so at GUI scale 2 a centered child may land on a half unit instead of being
     * pushed a whole unit up or down.
     */
    var pointScaleFactor: Float = 1f
        set(value) {
            require(value.isFinite() && value > 0f) { "pointScaleFactor must be positive and finite" }
            if (value == field) return
            field = value
            Yoga.YGConfigSetPointScaleFactor(config, value)
            lastW = Float.NaN // recalculate with the new rounding
        }

    fun layout(root: UiNode, composition: Composition, width: Float, height: Float) {
        if (root !== lastRoot || composition.compositionVersion != lastCompositionVersion || width != lastBuildWidth || rootHandle == 0L) {
            rebuild(root, width)
            lastRoot = root
            lastCompositionVersion = composition.compositionVersion
            lastBuildWidth = width
            lastW = Float.NaN // force recalc after structural change
        }
        if (width != lastW || height != lastH || Yoga.YGNodeIsDirty(rootHandle)) {
            Yoga.YGNodeCalculateLayout(rootHandle, width, height, Yoga.YGDirectionLTR)
            lastW = width
            lastH = height
        }
        writeBack(root, 0f, 0f)
    }

    private fun rebuild(root: UiNode, width: Float) {
        freeNative()
        rootHandle = build(root, null, null, width)
    }

    /** Frees the node tree first: it must never call a measure closure after that closure is freed. */
    private fun freeNative() {
        if (rootHandle != 0L) {
            Yoga.YGNodeFreeRecursive(rootHandle)
            rootHandle = 0L
        }
        handles.clear()
        measureFuncs.forEach { it.free() }
        measureFuncs.clear()
    }

    /**
     * A lambda passed straight to YGNodeSetMeasureFunc allocates a native closure that is never
     * released. Wrap it explicitly so every rebuild can free the previous tree's closures.
     */
    private fun setMeasureFunc(handle: Long, measure: YGMeasureFuncI) {
        val callback = YGMeasureFunc.create(measure)
        measureFuncs += callback
        Yoga.YGNodeSetMeasureFunc(handle, callback)
    }

    private fun build(node: UiNode, parentType: String?, gridCellWidth: Float?, availableWidth: Float): Long {
        val h = Yoga.YGNodeNewWithConfig(config)
        handles[node] = h

        if (!node.visible) {
            Yoga.YGNodeStyleSetDisplay(h, Yoga.YGDisplayNone)
        }

        when (node.type) {
            "Column", "Root", "ListColumn" ->
                Yoga.YGNodeStyleSetFlexDirection(h, Yoga.YGFlexDirectionColumn)
            "Row" -> Yoga.YGNodeStyleSetFlexDirection(h, Yoga.YGFlexDirectionRow)
            "Box" -> Yoga.YGNodeStyleSetFlexDirection(h, Yoga.YGFlexDirectionColumn)
            "Grid" -> {
                Yoga.YGNodeStyleSetFlexDirection(h, Yoga.YGFlexDirectionRow)
                Yoga.YGNodeStyleSetFlexWrap(h, Yoga.YGWrapWrap)
                val gap = (node.props["gap"] as? Number)?.toFloat() ?: 0f
                if (gap > 0f) Yoga.YGNodeStyleSetGap(h, Yoga.YGGutterAll, gap)
            }
        }

        var flexGrow = 0f
        var resolvedWidth = availableWidth
        var horizontalPadding = 0f
        for (el in node.modifier.elements()) {
            when (el) {
                is Padding -> {
                    Yoga.YGNodeStyleSetPadding(h, Yoga.YGEdgeLeft, el.start)
                    Yoga.YGNodeStyleSetPadding(h, Yoga.YGEdgeTop, el.top)
                    Yoga.YGNodeStyleSetPadding(h, Yoga.YGEdgeRight, el.end)
                    Yoga.YGNodeStyleSetPadding(h, Yoga.YGEdgeBottom, el.bottom)
                    horizontalPadding = el.start + el.end
                }
                is FixedSize -> {
                    val w: Float? = el.width
                    val hh: Float? = el.height
                    if (w != null) {
                        Yoga.YGNodeStyleSetWidth(h, w)
                        resolvedWidth = w
                    }
                    if (hh != null) Yoga.YGNodeStyleSetHeight(h, hh)
                }
                is FillMax -> {
                    if (el.widthFraction > 0f) {
                        Yoga.YGNodeStyleSetWidthPercent(h, el.widthFraction * 100f)
                        resolvedWidth = availableWidth * el.widthFraction
                    }
                    if (el.heightFraction > 0f) Yoga.YGNodeStyleSetHeightPercent(h, el.heightFraction * 100f)
                }
                is Weight -> flexGrow = el.weight
                is AlignItems -> Yoga.YGNodeStyleSetAlignItems(h, when (el.align) {
                    Align.START -> Yoga.YGAlignFlexStart
                    Align.CENTER -> Yoga.YGAlignCenter
                    Align.END -> Yoga.YGAlignFlexEnd
                    Align.STRETCH -> Yoga.YGAlignStretch
                })
                is JustifyContent -> Yoga.YGNodeStyleSetJustifyContent(h, when (el.justify) {
                    Justify.START -> Yoga.YGJustifyFlexStart
                    Justify.CENTER -> Yoga.YGJustifyCenter
                    Justify.END -> Yoga.YGJustifyFlexEnd
                    Justify.SPACE_BETWEEN -> Yoga.YGJustifySpaceBetween
                })
                else -> Unit // Offset/Background/Border/Shadow/Clip/Cutout/Clickable are draw-time
            }
        }
        if (flexGrow > 0f) {
            Yoga.YGNodeStyleSetFlexGrow(h, flexGrow)
            Yoga.YGNodeStyleSetFlexBasis(h, 0f)
        }

        // Structural widgets paint fixed-size visuals; give Yoga matching
        // minimums so measured bounds never underflow the drawn output
        // (which caused section overlap in the showcase).
        val (minW, minH) = widgetMinSize(node)
        if (minW > 0f) Yoga.YGNodeStyleSetMinWidth(h, minW)
        if (minH > 0f) Yoga.YGNodeStyleSetMinHeight(h, minH)
        // Cross-axis stretch (e.g. Switch in a Column) would inflate visuals
        // far past their painted size; cap those.
        val (maxW, maxH) = widgetMaxSize(node)
        if (maxW > 0f) Yoga.YGNodeStyleSetMaxWidth(h, maxW)
        if (maxH > 0f) Yoga.YGNodeStyleSetMaxHeight(h, maxH)

        // Reserve gap space before sizing cells; 100% / columns would force
        // the last cell of each row onto the next line when gap is nonzero.
        if (parentType == "Grid") {
            resolvedWidth = gridCellWidth ?: 0f
            Yoga.YGNodeStyleSetWidth(h, resolvedWidth)
        }

        if (isTextInput(node)) {
            val options = node.props["inputOptions"] as InputOptions
            val rows = node.props["rows"] as Int
            val support = options.error ?: options.helperText
            setMeasureFunc(h) { _, width, widthMode, _, _, out ->
                val available = if (widthMode == Yoga.YGMeasureModeUndefined) 180f else width.coerceAtLeast(1f)
                out.width(available)
                // An open DateField calendar floats like a dropdown menu and takes no space.
                out.height(InputMetrics.LABEL_HEIGHT + InputMetrics.fieldHeight(rows) +
                    if (support.isNotEmpty()) 5f + measurer.wrappedHeight(support, available) else 0f)
            }
        } else if (node.children.isEmpty() && node.type in leafMeasuredTypes) {
            val label = leafText(node)
            val role = (node.props["role"] as? TextRole) ?: TextRole.PARAGRAPH
            setMeasureFunc(h) { _, width, widthMode, _, _, out ->
                val maxW = if (widthMode == Yoga.YGMeasureModeUndefined) Float.MAX_VALUE else width
                val fid = node.props["fontId"] as? String
                val size = (node.props["textSize"] as? Number)?.toFloat() ?: node.theme.typography.sizeFor(role)
                val (w, hh) = if (node.type == "Text") measurer.measure(label, role, maxW, fid, size)
                    else measurer.measure(label, role, maxW, fid)
                out.width(w)
                out.height(hh)
            }
        } else if (node.type == "Accordion" && node.props["expanded"] != true) {
            // Collapsed: children take no space (rebuilt on toggle via recompose).
        } else {
            val contentWidth = (resolvedWidth - horizontalPadding).coerceAtLeast(0f)
            val cellWidth = if (node.type == "Grid") {
                val columns = (node.props["columns"] as? Int ?: 1).coerceAtLeast(1)
                val gap = (node.props["gap"] as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f
                kotlin.math.floor((contentWidth - gap * (columns - 1)).coerceAtLeast(0f) / columns)
            } else null
            node.children.forEachIndexed { i, child ->
                Yoga.YGNodeInsertChild(h, build(child, node.type, cellWidth, cellWidth ?: contentWidth), i)
            }
        }
        return h
    }

    /** Yoga positions are parent-relative; [UiNode] coordinates are absolute, with the root at the origin. */
    private fun writeBack(node: UiNode, parentX: Float, parentY: Float) {
        val h = handles[node] ?: return
        val isRoot = node.type == "Root"
        node.x = if (isRoot) 0f else parentX + Yoga.YGNodeLayoutGetLeft(h)
        node.y = if (isRoot) 0f else parentY + Yoga.YGNodeLayoutGetTop(h)
        node.width = Yoga.YGNodeLayoutGetWidth(h)
        node.height = Yoga.YGNodeLayoutGetHeight(h)
        node.children.forEach { writeBack(it, node.x, node.y) }
    }

    private fun widgetMinSize(node: UiNode): Pair<Float, Float> {
        if (isTextInput(node)) {
            val options = node.props["inputOptions"] as? InputOptions ?: InputOptions()
            return 40f to InputMetrics.height(node.props["rows"] as? Int ?: 1, options)
        }
        fun optCount(): Int = (node.props["options"] as? List<*>)?.size ?: 0
        return when (node.type) {
            "Checkbox", "RadioButton" -> 0f to 14f
            "Switch" -> 34f to 18f
            "Slider" -> 0f to 18f
            "ProgressBar" -> 0f to 8f
            "ItemSlot" -> 20f to 20f
            "Pagination" -> 0f to 24f
            // The open menu floats above following content; only the header takes space.
            "Dropdown" -> 0f to DropdownMetrics.HEADER_HEIGHT
            "RadioGroup", "MultiSelect" -> 0f to ControlMetrics.OPTION_ROW_HEIGHT * optCount()
            else -> 0f to 0f
        }
    }

    private fun widgetMaxSize(node: UiNode): Pair<Float, Float> = when (node.type) {
        "Switch" -> 40f to 0f
        else -> 0f to 0f
    }

    private fun leafText(node: UiNode): String = when (node.type) {
        "Text", "Button" -> node.props["text"] as? String ?: ""
        "Checkbox", "RadioButton" -> node.props["label"] as? String ?: "□"
        else -> ""
    }

    private var configFreed = false

    override fun close() {
        freeNative()
        lastRoot = null
        // Nodes are freed first; the config outlives every node created with it.
        if (!configFreed) {
            Yoga.YGConfigFree(config)
            configFreed = true
        }
    }

    private companion object {
        /** Childless nodes whose size comes from their text. */
        val leafMeasuredTypes = setOf("Text", "Button", "Checkbox", "RadioButton")
    }
}
