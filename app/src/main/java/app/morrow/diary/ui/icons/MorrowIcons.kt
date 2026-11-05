package app.morrow.diary.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons from the design (24×24, 1.75 stroke, round caps). They're drawn in black and
 * tinted by `Icon(tint = …)`, so they follow the theme colours.
 */
object MorrowIcons {
    val Calendar by lazy {
        stroke("Calendar", "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V7.5A2.5 2.5 0 0 1 6 5z", "M3.5 10h17M8 3v4M16 3v4")
    }
    val Sliders by lazy {
        stroke("Sliders", "M4 7h9M17 7h3M4 17h3M11 17h9", "M13 7a2 2 0 1 0 4 0a2 2 0 1 0-4 0z", "M7 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0z")
    }
    val Plus by lazy { stroke("Plus", "M12 5v14M5 12h14") }
    val Check by lazy { stroke("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val ArrowRight by lazy { stroke("ArrowRight", "M5 12h14M13 6l6 6-6 6") }
    val ArrowDown by lazy { stroke("ArrowDown", "M12 5v14M6 13l6 6 6-6") }
    val ChevronLeft by lazy { stroke("ChevronLeft", "M15 5l-7 7 7 7") }
    val ChevronRight by lazy { stroke("ChevronRight", "M9 5l7 7-7 7") }
    val Close by lazy { stroke("Close", "M7 7l10 10M17 7L7 17") }
    val Moon by lazy { stroke("Moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z") }
    val Flame by lazy {
        stroke("Flame", "M12 3.5c.6 2.8 3.8 4.6 3.8 8.8a3.8 3.8 0 0 1-7.6 0c0-1.7.8-2.9 1.9-3.8.1 1.4.8 2.3 1.7 2.6-.4-2.6-.6-5 .2-7.6z")
    }
    val Lock by lazy {
        stroke("Lock", "M7 11h10a2 2 0 0 1 2 2v5.5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V13a2 2 0 0 1 2-2z", "M8 11V8a4 4 0 0 1 8 0v3")
    }
    val Fingerprint by lazy {
        stroke(
            "Fingerprint",
            "M5.2 7.5A8 8 0 0 1 20 12.5v1",
            "M4.2 15.5c.2-1 .3-2 .3-3.5 0-.9.1-1.7.4-2.5",
            "M8 12a4 4 0 0 1 8 0v1c0 2.5-.4 4.8-1.3 7",
            "M8 15.5c-.3 1.7-.9 3.3-1.7 4.6",
            "M12 12v1.5c0 3-.6 5.4-1.8 7.5",
            "M19.6 16.5c-.2 1.3-.5 2.5-.9 3.6",
            strokeWidth = 1.5f,
        )
    }
    val Search by lazy { stroke("Search", "M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0z", "M16 16l4.5 4.5") }
    val Backspace by lazy { stroke("Backspace", "M9 5.5h11v13H9l-6-6.5z", "M12.5 9.5l5 5M17.5 9.5l-5 5") }
    val Download by lazy { stroke("Download", "M12 4v11M7 10l5 5 5-5", "M5 19.5h14") }
    val Upload by lazy { stroke("Upload", "M12 15V4M7 9l5-5 5 5", "M5 19.5h14") }

    private fun stroke(name: String, vararg paths: String, strokeWidth: Float = 1.75f): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
