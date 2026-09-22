package com.isaac.ridelab.ui.overlay

enum class RideOverlayState { OFF, VISIBLE, PERMISSION_REQUIRED, FAILED }
data class OverlayPosition(val x: Int, val y: Int)
fun fitOverlay(x: Int, y: Int, screenWidth: Int, screenHeight: Int, width: Int, height: Int, snap: Boolean): OverlayPosition {
    val maxX = (screenWidth - width).coerceAtLeast(0)
    val maxY = (screenHeight - height).coerceAtLeast(0)
    val fittedX = x.coerceIn(0, maxX)
    return OverlayPosition(if (snap) { if (fittedX < maxX / 2) 0 else maxX } else fittedX, y.coerceIn(0, maxY))
}
