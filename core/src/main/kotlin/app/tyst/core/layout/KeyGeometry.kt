package app.tyst.core.layout

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/** A key placed on the plate: [slot] is the touch area (gaps included), [visual] the drawn keycap. */
data class PlacedKey(val key: Key, val row: Int, val slot: Box, val visual: Box)

/**
 * Lays keys out in pixels. Every row is divided into the same number of slots as the widest row
 * (10 on the letters layout), so 1 key unit = width ÷ 10. Gaps belong to the touch slots.
 */
class KeyGeometry(
    val layout: KeyboardLayout,
    val width: Float,
    val keyHeight: Float,
    val hGap: Float,
    val vGap: Float,
    val padSides: Float,
    val padTop: Float,
    val padBottom: Float,
) {
    val keys: List<PlacedKey>
    val height: Float

    init {
        val units = layout.rows.maxOf { r -> r.keys.sumOf { it.width.toDouble() } }.toFloat()
        val unit = (width - 2 * padSides) / units
        val rowPitch = keyHeight + vGap
        val placed = ArrayList<PlacedKey>()
        layout.rows.forEachIndexed { r, row ->
            val rowUnits = row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = padSides + (units - rowUnits) * unit / 2
            val top = padTop + r * rowPitch
            for (key in row.keys) {
                val w = key.width * unit
                if (key.type != KeyType.SPACER) {
                    val slot = Box(x, top, x + w, top + rowPitch)
                    val visual = Box(x + hGap / 2, top + vGap / 2, x + w - hGap / 2, top + vGap / 2 + keyHeight)
                    placed += PlacedKey(key, r, slot, visual)
                }
                x += w
            }
        }
        keys = placed
        height = padTop + layout.rows.size * rowPitch + padBottom
    }

    /** The key under a touch, falling back to the nearest key for touches in spacers or padding. */
    fun keyAt(x: Float, y: Float): PlacedKey? {
        if (keys.isEmpty()) return null
        keys.firstOrNull { it.slot.contains(x, y) }?.let { return it }
        return keys.minByOrNull { k ->
            val dx = maxOf(k.slot.left - x, 0f, x - k.slot.right)
            val dy = maxOf(k.slot.top - y, 0f, y - k.slot.bottom)
            dx * dx + dy * dy
        }
    }
}

/**
 * Where a touch at ([x], [y]) landed on [key], relative to its centre, in key widths and row heights:
 * the offset autocorrect uses to weigh the neighbouring keys.
 */
fun app.tyst.core.correct.TapOffset.Companion.of(key: PlacedKey, x: Float, y: Float): app.tyst.core.correct.TapOffset =
    app.tyst.core.correct.TapOffset(
        dx = ((x - key.visual.centerX) / (key.slot.width / key.key.width)).coerceIn(-1f, 1f),
        dy = ((y - key.visual.centerY) / key.slot.height).coerceIn(-1f, 1f),
    )
