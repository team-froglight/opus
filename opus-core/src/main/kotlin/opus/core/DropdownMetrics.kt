package opus.core

/** Shared geometry for dropdown layout, painting and hit testing. The menu floats below the header. */
object DropdownMetrics {
    const val HEADER_HEIGHT = 26f
    const val MENU_GAP = 4f
    const val MENU_PADDING = 4f
    const val ROW_HEIGHT = 22f

    @JvmStatic fun menuHeight(count: Int): Float = MENU_PADDING * 2 + ROW_HEIGHT * count.coerceAtLeast(0)

    /** Option under [menuY], measured from the menu's top edge. Padding is deliberately not an option. */
    @JvmStatic fun optionAt(menuY: Float, count: Int): Int {
        val rowY = menuY - MENU_PADDING
        return if (rowY >= 0 && rowY < count * ROW_HEIGHT) (rowY / ROW_HEIGHT).toInt() else -1
    }
}

/** Shared geometry for the remaining built-in controls, used by layout, painting and hit testing. */
object ControlMetrics {
    /** Row pitch of [RadioGroup] and [MultiSelect] options. */
    const val OPTION_ROW_HEIGHT = 18f
    /** Height of the clickable [Accordion] title bar; content starts below it. */
    const val ACCORDION_HEADER_HEIGHT = 18f

    /** Option index under [localY], or -1 outside the option rows. */
    @JvmStatic fun optionAt(localY: Float, count: Int): Int {
        val index = kotlin.math.floor(localY / OPTION_ROW_HEIGHT).toInt()
        return if (index in 0 until count) index else -1
    }

    /** Width of each previous/next action at either end of a [Pagination] bar. */
    @JvmStatic fun paginationActionWidth(width: Float): Float = minOf(60f, width / 3f)
}
