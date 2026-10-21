package com.isaac.ridelab.ui.overlay

/** One proportional scale, fitted to both available screen dimensions. */
object CapsuleGeometry {
    fun fit(percent: Int, availableWidth: Int, availableHeight: Int, density: Float, collapsed: Boolean = false): Pair<Int, Int> {
        val width = if (collapsed) 174f else 320f
        val height = width * 92f / (if (collapsed) 260f else 480f)
        val scale = minOf(percent.coerceIn(60, 140) / 100f * density,
            availableWidth.coerceAtLeast(1) / width, availableHeight.coerceAtLeast(1) / height)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}
