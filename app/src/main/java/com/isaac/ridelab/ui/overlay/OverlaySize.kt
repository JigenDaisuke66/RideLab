package com.isaac.ridelab.ui.overlay

import com.isaac.ridelab.data.model.OverlayStyle

/** Logical sizes are saved as percentages so rotation/density changes do not overwrite them. */
data class OverlaySize private constructor(val widthPercent: Int, val heightPercent: Int) {
    fun widthDp(style: OverlayStyle) = (if (style == OverlayStyle.STRIP) 360 else 300) * widthPercent / 100
    val heightDp get() = 480 * heightPercent / 100
    companion object {
        const val MIN_WIDTH = 80
        const val MAX_WIDTH = 160
        const val MIN_HEIGHT = 40
        const val MAX_HEIGHT = 140
        fun restore(width: Int = 100, height: Int = 100) = OverlaySize(
            width.coerceIn(MIN_WIDTH, MAX_WIDTH), height.coerceIn(MIN_HEIGHT, MAX_HEIGHT))
    }
}
