package opus.yoga

import opus.core.Composition
import opus.core.UiNode
import opus.core.TextRole
import opus.core.elements
import opus.core.FillMax
import opus.core.FixedSize
import opus.core.Padding
import opus.core.Weight
import org.lwjgl.util.yoga.YGMeasureFuncI
import org.lwjgl.util.yoga.Yoga

/** Measures leaf content (text). Implemented by :opus-minecraft with MC's Font. */
interface TextMeasurer {
    fun wrappedHeight(text: String, maxWidth: Float): Float =
        kotlin.math.ceil(measure(text, TextRole.LABEL, Float.MAX_VALUE).first / maxWidth.coerceAtLeast(1f)).coerceAtLeast(1f) * lineHeight(TextRole.LABEL)
    fun measure(text: String, role: TextRole, maxWidth: Float): Pair<Float, Float>
    fun measure(text: String, role: TextRole, maxWidth: Float, fontId: String?): Pair<Float, Float> =
        measure(text, role, maxWidth)
    fun lineHeight(role: TextRole): Float
}

/**
 * Owns the native Yoga tree mirror of an [UiNode] tree.
 *
 * - Rebuilds native nodes only when the composition changed (compositionVersion).
 * - Recalculates layout only when size changed or the tree is dirty.
 * - Writes x/y/width/height back to [UiNode]s every frame (cheap traversal).
 * - Measure callbacks are strongly referenced for the tree lifetime (native safety).
 */
class YogaTree(private val measurer: TextMeasurer) : AutoCloseable {
    private var rootHandle: Long = 0L
    private val handles = java.util.IdentityHashMap<UiNode, Long>()
    private val measureFuncs = mutableListOf<YGMeasureFuncI>()
    private var lastCompositionVersion = -1
    private var lastBuildWidth = Float.NaN
    private var lastW = Float.NaN
    private var lastH = Float.NaN

    fun layout(root: UiNode, composition: Composition, width: Float, height: Float) {
        if (composition.compositionVersion != lastCompositionVersion || width != lastBuildWidth || rootHandle == 0L) {
            rebuild(root, width)
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
        if (rootHandle != 0L) {
            Yoga.YGNodeFreeRecursive(rootHandle)
            rootHandle = 0L
        }
        handles.clear()
        measureFuncs.clear()
        rootHandle = build(root, null, null, width)
    }

    private fun build(node: UiNode, parentType: String?, gridCellWidth: Float?, availableWidth: Float): Long {
        val h = Yoga.YGNodeNew()
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
            Yoga.YGNodeStyleSetWidth(h, gridCellWidth ?: 0f)
        }

        if (node.type in setOf("TextField", "TextArea", "NumberField", "DateField")) {
            val options = node.props["inputOptions"] as opus.core.InputOptions
            val rows = node.props["rows"] as Int
            val support = options.error ?: options.helperText
            val cb = YGMeasureFuncI { _, width, widthMode, _, _, out ->
                val available = if (widthMode == Yoga.YGMeasureModeUndefined) 180f else width.coerceAtLeast(1f)
                out.width(available)
                val extra = if (node.type == "DateField" && node.props["expanded"] == true)
                    opus.core.DateInput.CALENDAR_HEIGHT + opus.core.DateInput.CALENDAR_GAP else 0f
                out.height(opus.core.InputMetrics.LABEL_HEIGHT + opus.core.InputMetrics.fieldHeight(rows) + extra +
                    if (support.isNotEmpty()) 5f + measurer.wrappedHeight(support, available) else 0f)
            }
            measureFuncs += cb
            Yoga.YGNodeSetMeasureFunc(h, cb)
        } else if (node.children.isEmpty() && isLeafMeasurable(node)) {
            val label = leafText(node)
            val role = (node.props["role"] as? TextRole) ?: TextRole.PARAGRAPH
            val cb = YGMeasureFuncI { _, width, widthMode, _, _, out ->
                val maxW = if (widthMode == Yoga.YGMeasureModeUndefined) Float.MAX_VALUE else width
                val fid = node.props["fontId"] as? String
                val (w, hh) = measurer.measure(label, role, maxW, fid)
                out.width(w)
                out.height(hh)
            }
            measureFuncs += cb
            Yoga.YGNodeSetMeasureFunc(h, cb)
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

    private fun writeBack(node: UiNode, accX: Float, accY: Float) {
        val h = handles[node] ?: return
        // Root coordinates are absolute; children are relative to parents.
        val parentIsRoot = node.type == "Root"
        val x = if (parentIsRoot) 0f else accX + Yoga.YGNodeLayoutGetLeft(h)
        val y = if (parentIsRoot) 0f else accY + Yoga.YGNodeLayoutGetTop(h)
        if (node.type == "Root") {
            node.x = 0f; node.y = 0f
            node.width = Yoga.YGNodeLayoutGetWidth(h)
            node.height = Yoga.YGNodeLayoutGetHeight(h)
        } else {
            node.x = x; node.y = y
            node.width = Yoga.YGNodeLayoutGetWidth(h)
            node.height = Yoga.YGNodeLayoutGetHeight(h)
        }
        node.children.forEach { writeBack(it, node.x, node.y) }
    }

    private fun isLeafMeasurable(node: UiNode): Boolean = when (node.type) {
        "Text", "Button", "Checkbox", "RadioButton", "Label" -> true
        else -> false
    }

    private fun widgetMinSize(node: UiNode): Pair<Float, Float> {
        if (node.type in setOf("TextField", "TextArea", "NumberField", "DateField")) {
            val options = node.props["inputOptions"] as? opus.core.InputOptions ?: opus.core.InputOptions()
            return 40f to opus.core.InputMetrics.height(node.props["rows"] as? Int ?: 1, options)
        }
        fun optCount(): Int = (node.props["options"] as? List<*>)?.size ?: 0
        return when (node.type) {
            "Checkbox", "RadioButton" -> 0f to 14f
            "Switch" -> 34f to 18f
            "Slider" -> 0f to 18f
            "ProgressBar" -> 0f to 8f
            "ItemSlot" -> 20f to 20f
            "Pagination" -> 0f to 24f
            "Dropdown" -> {
                val expanded = node.props["expanded"] as? Boolean ?: false
                val progress = (node.props["expansion"] as? Number)?.toFloat() ?: if (expanded) 1f else 0f
                0f to opus.core.DropdownMetrics.height(optCount(), progress)
            }
            "RadioGroup", "MultiSelect" -> 0f to 18f * optCount()
            else -> 0f to 0f
        }
    }

    private fun widgetMaxSize(node: UiNode): Pair<Float, Float> = when (node.type) {
        "Switch" -> 40f to 0f
        else -> 0f to 0f
    }

    private fun leafText(node: UiNode): String = when (node.type) {
        "Text" -> node.props["text"] as? String ?: ""
        "Button" -> node.props["text"] as? String ?: ""
        "Checkbox", "RadioButton" -> node.props["label"] as? String ?: "□"
        else -> ""
    }

    override fun close() {
        if (rootHandle != 0L) {
            Yoga.YGNodeFreeRecursive(rootHandle)
            rootHandle = 0L
        }
        handles.clear()
        measureFuncs.clear()
    }
}
