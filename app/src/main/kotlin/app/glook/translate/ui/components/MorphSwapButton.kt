package app.glook.translate.ui.components

import app.glook.translate.R
import app.glook.translate.core.str

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.SwapHoriz
import kotlinx.coroutines.launch

/** Morph(진행률) → Compose Shape. MaterialShapes 다각형은 0..1 로 정규화되어 있어 크기만큼 확대합니다. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress = progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * 언어 스왑 버튼: 탭할 때마다 원형 ⇄ 9각 쿠키(MaterialShapes) 로 Shape Morph + 180° 회전 + 햅틱.
 * 애니메이션 스펙은 MotionScheme.expressive() 의 공간(spatial) 스프링을 그대로 사용합니다.
 * (MaterialShapes / Morph.toPath 는 Material3 1.5.0 에서도 @ExperimentalMaterial3ExpressiveApi)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphSwapButton(onSwap: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme

    val shape = MorphShape(morph, progress.value)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(if (enabled) colors.primaryContainer else colors.surfaceContainerHighest)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            ) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY,
                )
                onSwap()
                scope.launch { progress.animateTo(if (progress.targetValue < 0.5f) 1f else 0f, spatial) }
                scope.launch { rotation.animateTo(rotation.targetValue + 180f, spatial) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GlookIcons.SwapHoriz,
            contentDescription = str(R.string.swap_languages),
            tint = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.rotate(rotation.value),
        )
    }
}
