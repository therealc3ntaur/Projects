package com.smartsiddur.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Detects two-finger pinch gestures without interfering with one-finger scrolling.
 *
 * Runs in the Initial pass so the pinch is consumed before a scrolling child sees it.
 * [onZoom] receives the incremental zoom factor; [onZoomEnd] fires when the fingers lift.
 */
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit, onZoomEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoomed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        zoomed = true
                        onZoom(zoom)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
            if (zoomed) onZoomEnd()
        }
    }
