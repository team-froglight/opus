@file:JvmName("Layouts")

package opus.core

/** Flex-like containers. Responsive by default: children with [Weight] share free space. */

@Composable
@JvmOverloads
fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Column", modifier, content = content)
}

@Composable
@JvmOverloads
fun Row(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Row", modifier, content = content)
}

@Composable
@JvmOverloads
fun Box(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Box", modifier, content = content)
}

@Composable
@JvmOverloads
fun Grid(
    columns: Int,
    modifier: Modifier = Modifier,
    gap: Float = 0f,
    content: @Composable () -> Unit
) {
    emit("Grid", modifier, mapOf("columns" to columns, "gap" to gap), content)
}

@Composable
@JvmOverloads
fun Spacer(modifier: Modifier = Modifier) {
    emit("Spacer", modifier)
}

@Composable
@JvmOverloads
fun Divider(modifier: Modifier = Modifier, color: Int = 0x33FFFFFF) {
    emit("Divider", modifier, mapOf("color" to color))
}
