package com.smartsiddur.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small icons that aren't in material-icons-core. */
object SiddurIcons {
    val Moon: ImageVector by lazy {
        ImageVector.Builder("Moon", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Crescent: outer circle arc minus an offset inner arc
                moveTo(12.5f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 21f, 14.5f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12.5f, 3f)
                close()
            }
        }.build()
    }

    val Sun: ImageVector by lazy {
        ImageVector.Builder("Sun", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 17f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 7f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Eight rays
                moveTo(12f, 1.5f); lineTo(12f, 4.5f)
                moveTo(12f, 19.5f); lineTo(12f, 22.5f)
                moveTo(1.5f, 12f); lineTo(4.5f, 12f)
                moveTo(19.5f, 12f); lineTo(22.5f, 12f)
                moveTo(4.6f, 4.6f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(19.4f, 19.4f)
                moveTo(4.6f, 19.4f); lineTo(6.7f, 17.3f)
                moveTo(17.3f, 6.7f); lineTo(19.4f, 4.6f)
            }
        }.build()
    }
}
