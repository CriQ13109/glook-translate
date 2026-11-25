package app.glook.translate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Glook 기본 팔레트 (Android 12 미만, 또는 설정에서 "기기 테마 색"을 끈 경우).
 *
 * 전 토큰을 Material Color Utilities(HCT, TonalSpot 2021 스펙, materialyoucolor 파이썬 포트로 계산)에서
 * Seed #3B6939 로 산출했습니다. 단 tertiary 계열만 사용자가 앞서 지정한 살몬(#9A4522 = T40, #FFB59A = T80)의
 * 같은 톤 팔레트(hue 39)로 유지했습니다. 이전 판의 추정값은 모두 이 계산값으로 대체되었습니다.
 */
object GlookPalette {
    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF3B6939),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBCF0B4),
        onPrimaryContainer = Color(0xFF245024),
        secondary = Color(0xFF52634F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD5E8CE),
        onSecondaryContainer = Color(0xFF3B4B38),
        tertiary = Color(0xFF9A4522),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBCF),
        onTertiaryContainer = Color(0xFF380D00),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF7FBF1),
        onBackground = Color(0xFF191D17),
        surface = Color(0xFFF7FBF1),
        onSurface = Color(0xFF191D17),
        surfaceVariant = Color(0xFFDEE5D8),
        onSurfaceVariant = Color(0xFF424940),
        outline = Color(0xFF72796F),
        outlineVariant = Color(0xFFC2C9BD),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF2D322C),
        inverseOnSurface = Color(0xFFEFF2E9),
        inversePrimary = Color(0xFFA1D39A),
        surfaceDim = Color(0xFFD8DBD2),
        surfaceBright = Color(0xFFF7FBF1),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F5EB),
        surfaceContainer = Color(0xFFECEFE6),
        surfaceContainerHigh = Color(0xFFE6E9E0),
        surfaceContainerHighest = Color(0xFFE0E4DB),
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFA1D39A),
        onPrimary = Color(0xFF0A390F),
        primaryContainer = Color(0xFF245024),
        onPrimaryContainer = Color(0xFFBCF0B4),
        secondary = Color(0xFFBACCB3),
        onSecondary = Color(0xFF253423),
        secondaryContainer = Color(0xFF3B4B38),
        onSecondaryContainer = Color(0xFFD5E8CE),
        tertiary = Color(0xFFFFB59A),
        onTertiary = Color(0xFF5B1B00),
        tertiaryContainer = Color(0xFF7B2E0C),
        onTertiaryContainer = Color(0xFFFFDBCF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF10140F),
        onBackground = Color(0xFFE0E4DB),
        surface = Color(0xFF10140F),
        onSurface = Color(0xFFE0E4DB),
        surfaceVariant = Color(0xFF424940),
        onSurfaceVariant = Color(0xFFC2C9BD),
        outline = Color(0xFF8C9388),
        outlineVariant = Color(0xFF424940),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE0E4DB),
        inverseOnSurface = Color(0xFF2D322C),
        inversePrimary = Color(0xFF3B6939),
        surfaceDim = Color(0xFF10140F),
        surfaceBright = Color(0xFF363A34),
        surfaceContainerLowest = Color(0xFF0B0F0A),
        surfaceContainerLow = Color(0xFF191D17),
        surfaceContainer = Color(0xFF1D211B),
        surfaceContainerHigh = Color(0xFF272B25),
        surfaceContainerHighest = Color(0xFF323630),
    )
}
