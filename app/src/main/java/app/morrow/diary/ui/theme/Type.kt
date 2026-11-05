package app.morrow.diary.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.morrow.diary.R

// Both fonts are variable fonts bundled in res/font (SIL Open Font License, see assets/licenses).
// Fraunces also has an optical-size axis: large text uses a high "opsz" for finer detail,
// small text a low one so it stays sturdy.

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, opsz: Float, italic: Boolean = false) = Font(
    resId = if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Fraunces tuned for big text: dates, quotes, screen titles. */
val FrauncesDisplay = FontFamily(fraunces(500, 72f), fraunces(600, 72f))

/** Fraunces tuned for text sizes: section titles, greetings. */
val FrauncesText = FontFamily(
    fraunces(400, 24f),
    fraunces(600, 24f),
    fraunces(400, 24f, italic = true),
)

val Inter = FontFamily(inter(400), inter(500), inter(600))

/** Named text styles from the design. Colours are applied where they're used. */
object MorrowType {
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)
    val dayTitle = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.4).sp)
    val screenTitle = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-0.36).sp)
    val quote = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.3).sp)
    val quoteMark = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Medium, fontSize = 56.sp, lineHeight = 56.sp)
    val greeting = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 25.sp)
    val sectionTitle = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp)
    val goal = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val bodyMedium = body.copy(fontWeight = FontWeight.Medium)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
}

/** Material defaults, so stock components (dialogs, text fields) also use the Morrow fonts. */
val MorrowMaterialTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FrauncesDisplay),
        displayMedium = base.displayMedium.copy(fontFamily = FrauncesDisplay),
        displaySmall = base.displaySmall.copy(fontFamily = FrauncesDisplay),
        headlineLarge = base.headlineLarge.copy(fontFamily = FrauncesDisplay),
        headlineMedium = base.headlineMedium.copy(fontFamily = FrauncesDisplay),
        headlineSmall = base.headlineSmall.copy(fontFamily = FrauncesText),
        titleLarge = base.titleLarge.copy(fontFamily = FrauncesText),
        titleMedium = base.titleMedium.copy(fontFamily = Inter),
        titleSmall = base.titleSmall.copy(fontFamily = Inter),
        bodyLarge = MorrowType.body,
        bodyMedium = MorrowType.small,
        bodySmall = MorrowType.caption,
        labelLarge = MorrowType.button,
        labelMedium = base.labelMedium.copy(fontFamily = Inter),
        labelSmall = base.labelSmall.copy(fontFamily = Inter),
    )
}
