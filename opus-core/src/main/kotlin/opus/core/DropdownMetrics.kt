package opus.core

/** Shared geometry for dropdown layout, painting and hit testing. */
object DropdownMetrics {
    const val HEADER_HEIGHT = 26f
    const val MENU_GAP = 4f
    const val MENU_PADDING = 4f
    const val ROW_HEIGHT = 22f

    @JvmStatic fun menuHeight(count: Int): Float = MENU_PADDING * 2 + ROW_HEIGHT * count.coerceAtLeast(0)
    @JvmStatic fun height(count: Int, progress: Float): Float =
        HEADER_HEIGHT + (MENU_GAP + menuHeight(count)) * progress.coerceIn(0f, 1f)

    /** Padding and the gap are deliberately not options. */
    @JvmStatic fun optionAt(localY: Float, count: Int): Int {
        val rowY = localY - HEADER_HEIGHT - MENU_GAP - MENU_PADDING
        return if (rowY >= 0 && rowY < count * ROW_HEIGHT) (rowY / ROW_HEIGHT).toInt() else -1
    }
}
