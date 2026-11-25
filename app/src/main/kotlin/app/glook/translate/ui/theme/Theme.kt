package app.glook.translate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Bento 곡률 토큰: 큰 컨테이너 28dp, 칩/버튼 12dp. */
object GlookShapes {
    val Container = RoundedCornerShape(28.dp)
    val Chip = RoundedCornerShape(12.dp)
}

/** 햅틱 사용 여부 (설정에서 끌 수 있음). */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

private val GlookM3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * MaterialExpressiveTheme + MotionScheme.expressive() (Compose Material3 1.5.0 에서 안정 API — 소스 확인).
 * 색: Android 12+ 는 기기 테마(동적 색)가 기본(토글 없음), Android 12 미만은 [GlookPalette]. 화면 코드는 역할 색(primary/secondary/tertiary 컨테이너)만 사용.
 * 타이포그래피는 Google Sans Flex(ROND 100) 를 라틴 글자에 적용하고 Emphasized 스타일을 활용합니다.
 */
@Composable
fun GlookTheme(darkTheme: Boolean = isSystemInDarkTheme(), haptics: Boolean = true, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> GlookPalette.Dark
        else -> GlookPalette.Light
    }
    CompositionLocalProvider(LocalHapticsEnabled provides haptics) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = GlookM3Shapes,
            typography = glookTypography(),
            content = content,
        )
    }
}
