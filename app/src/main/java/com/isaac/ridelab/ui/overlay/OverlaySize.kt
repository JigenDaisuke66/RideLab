package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.data.model.OverlayStyle

/** Logical sizes are saved as percentages so rotation/density changes do not overwrite them. */
data class OverlaySize private constructor(val widthPercent: Int, val heightPercent: Int) {
    fun widthDp(style: OverlayStyle) = (if (style == OverlayStyle.STRIP) 360 else 300) * widthPercent / 100
    val heightDp get() = 480 * heightPercent / 100
    /** Compact natural window sizes, preserving user scale without a large invisible touch area. */
    fun windowHeightDp(style: OverlayStyle, collapsed: Boolean, charts: Boolean, font: Int): Int {
        val textScale = font.coerceIn(80, 140) / 100f
        if (collapsed) return (116 * textScale).toInt().coerceAtLeast(104)
        val base = when (style) {
            OverlayStyle.PANEL -> 258
            OverlayStyle.STRIP -> 204
            OverlayStyle.DASHBOARD -> 294
        }
        return ((base + if (charts) 164 else 0) * textScale * heightPercent / 100)
            .toInt().coerceAtLeast((152 * textScale).toInt())
    }
    companion object {
        const val MIN_WIDTH = 80
        const val MAX_WIDTH = 160
        const val MIN_HEIGHT = 40
        const val MAX_HEIGHT = 140
        fun restore(width: Int = 100, height: Int = 100) = OverlaySize(
            width.coerceIn(MIN_WIDTH, MAX_WIDTH), height.coerceIn(MIN_HEIGHT, MAX_HEIGHT))
    }
}
