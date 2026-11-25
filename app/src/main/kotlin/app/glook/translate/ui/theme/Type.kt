package app.glook.translate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.glook.translate.R

/**
 * Google Sans Flex 가변 폰트(용량 절감을 위해 wght 300–700 축만 남기고 ROND=100 등 나머지 축은 고정한 사본). 라틴/숫자에만 적용되며 한글·일본어·중국어는 시스템 폰트로 폴백됩니다.
 * (폰트 출처·라이선스: THIRD_PARTY_NOTICES.md)
 */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.Setting("wght", weight.toFloat())),
)

val GlookFontFamily = FontFamily(flex(400), flex(500), flex(600), flex(700))

private fun TextStyle.glook() = copy(fontFamily = GlookFontFamily)

fun glookTypography(base: Typography = Typography()): Typography = base.copy(
    displayLarge = base.displayLarge.glook(), displayMedium = base.displayMedium.glook(), displaySmall = base.displaySmall.glook(),
    headlineLarge = base.headlineLarge.glook(), headlineMedium = base.headlineMedium.glook(), headlineSmall = base.headlineSmall.glook(),
    titleLarge = base.titleLarge.glook(), titleMedium = base.titleMedium.glook(), titleSmall = base.titleSmall.glook(),
    bodyLarge = base.bodyLarge.glook(), bodyMedium = base.bodyMedium.glook(), bodySmall = base.bodySmall.glook(),
    labelLarge = base.labelLarge.glook(), labelMedium = base.labelMedium.glook(), labelSmall = base.labelSmall.glook(),
    displayLargeEmphasized = base.displayLargeEmphasized.glook(), displayMediumEmphasized = base.displayMediumEmphasized.glook(),
    displaySmallEmphasized = base.displaySmallEmphasized.glook(), headlineLargeEmphasized = base.headlineLargeEmphasized.glook(),
    headlineMediumEmphasized = base.headlineMediumEmphasized.glook(), headlineSmallEmphasized = base.headlineSmallEmphasized.glook(),
    titleLargeEmphasized = base.titleLargeEmphasized.glook(), titleMediumEmphasized = base.titleMediumEmphasized.glook(),
    titleSmallEmphasized = base.titleSmallEmphasized.glook(), bodyLargeEmphasized = base.bodyLargeEmphasized.glook(),
    bodyMediumEmphasized = base.bodyMediumEmphasized.glook(), bodySmallEmphasized = base.bodySmallEmphasized.glook(),
    labelLargeEmphasized = base.labelLargeEmphasized.glook(), labelMediumEmphasized = base.labelMediumEmphasized.glook(),
    labelSmallEmphasized = base.labelSmallEmphasized.glook(),
)
