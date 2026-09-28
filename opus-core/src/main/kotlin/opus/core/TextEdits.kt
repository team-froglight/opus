package opus.core

/** UTF-16 limits that preserve complete surrogate pairs and account for replaced selections. */
object TextEdits {
    @JvmStatic fun limit(text: String, maximum: Int): String {
        var end = maximum.coerceIn(0, text.length)
        if (end < text.length && end > 0 && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return text.substring(0, end)
    }
    @JvmStatic fun insertion(text: String, currentLength: Int, selectedLength: Int, maximum: Int): String =
        limit(text, (maximum.toLong() - currentLength + selectedLength).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
}
