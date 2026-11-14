package com.kithandkeen.qastellan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons on a 24x24 grid, drawn with a round stroke like the mockups. */
object Icons {
    const val back = "M15 18l-6-6 6-6"
    const val chevron = "M9 18l6-6-6-6"
    const val home = "M3 10l9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    const val apps = "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z"
    const val history = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 7v5l3 2"
    const val shield = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
    const val shieldAlert = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M12 8v4 M12 16h.01"
    const val search = "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14 M21 21l-4.35-4.35"
    const val phone = "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M12 18h.01"
    const val file = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6"
    const val lock = "M5 11h14v10H5z M8 11V7a4 4 0 0 1 8 0v4"
    const val audit = "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M9 12l2 2 4-4"
    const val settings = "M4 7h16 M4 17h16 M9 4.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5 M15 14.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5"
    const val checkCircle = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20 M8 12l3 3 5-6"
    const val alertCircle = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20 M12 7v6 M12 17h.01"
    const val alert = "M12 7v6 M12 17h.01"
    const val warningTriangle = "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z M12 9v4 M12 17h.01"
    const val waiting = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20"
    const val sync = "M4 12a8 8 0 0 1 14-5 M20 12a8 8 0 0 1-14 5 M18 3v4h-4 M6 21v-4h4"
    const val bell = "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.7 21a2 2 0 0 1-3.4 0"
    const val download = "M12 4v12 M7 11l5 5 5-5 M5 20h14"
    const val eye = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6"
    const val trash = "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13"
}

@Composable
fun Icon(path: String, color: Color, size: Dp = 22.dp, description: String? = null, strokeWidth: Float = 2f, modifier: Modifier = Modifier) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(
        modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val s = this.size.width / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(parsed, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
