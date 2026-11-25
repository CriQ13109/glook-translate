package app.glook.translate.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import app.glook.translate.ui.theme.LocalHapticsEnabled

enum class Haptic { TICK, CONFIRM, REJECT, HEAVY }

@Composable
fun rememberHaptics(): (Haptic) -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { { kind -> if (enabled) view.perform(kind) } }
}

fun View.perform(kind: Haptic) {
    val c = when (kind) {
        Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.REJECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Haptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(c)
}

/**
 * 눌린 동안 스프링으로 살짝 눌렸다가 복귀하는 클릭 (MotionScheme 의 공간 스프링 사용) + 햅틱.
 * 카드·타일처럼 Material 버튼이 아닌 클릭 가능한 면에 사용합니다.
 */
@Composable
fun Modifier.bouncyClick(haptic: Haptic = Haptic.TICK, scaleDown: Float = 0.96f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState().value
    val scale = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val perform = rememberHaptics()
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) scaleDown else 1f, spec) }
    return this
        .scale(scale.value)
        .clickable(interactionSource = source, indication = null, role = Role.Button) {
            perform(haptic)
            onClick()
        }
}
