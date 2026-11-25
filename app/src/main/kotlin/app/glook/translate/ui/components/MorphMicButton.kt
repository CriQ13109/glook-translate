package app.glook.translate.ui.components

import app.glook.translate.R
import app.glook.translate.core.str

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.Mic
import app.glook.translate.ui.icons.Stop

/** 듣는 중에는 원 → 9각 쿠키로 변형되며 천천히 회전하는 마이크 버튼. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphMicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = animateFloatAsState(if (listening) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "micMorph")
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "spinAngle",
    )
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size).rotate(if (listening) spin.value else 0f).clip(MorphShape(morph, progress.value))
            .background(if (listening) cs.primary else cs.primaryContainer)
            .bouncyClick(Haptic.CONFIRM, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (listening) GlookIcons.Stop else GlookIcons.Mic,
            if (listening) str(R.string.mic_stop) else str(R.string.mic_speak),
            tint = if (listening) cs.onPrimary else cs.onPrimaryContainer,
            modifier = Modifier.size(size * 0.42f).rotate(if (listening) -spin.value else 0f),
        )
    }
}
