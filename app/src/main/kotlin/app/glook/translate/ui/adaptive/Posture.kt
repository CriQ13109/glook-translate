package app.glook.translate.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** 힌지가 가로(세로 방향 접힘 = 테이블탑)인지 세로(북 자세)인지와 힌지 영역(창 좌표, px). */
@Immutable
data class FoldPosture(
    val tabletop: Boolean = false,
    val book: Boolean = false,
    val hingeLeft: Int = 0, val hingeTop: Int = 0, val hingeRight: Int = 0, val hingeBottom: Int = 0,
    /** 힌지가 화면을 물리적으로 가리거나 분리하는지 (true 면 힌지 폭만큼 콘텐츠를 띄워야 함). */
    val separating: Boolean = false,
) {
    val hingeWidthPx get() = hingeRight - hingeLeft
    val hingeHeightPx get() = hingeBottom - hingeTop
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** androidx.window FoldingFeature 를 구독. Activity 가 아닌 컨텍스트(프리뷰·테스트)에서는 항상 평면 자세. */
@Composable
fun rememberFoldPosture(): FoldPosture {
    val activity = LocalContext.current.findActivity() ?: return FoldPosture()
    val posture by produceState(FoldPosture(), activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            value = if (f == null || f.state != FoldingFeature.State.HALF_OPENED) FoldPosture()
            else {
                val b = f.bounds
                FoldPosture(
                    tabletop = f.orientation == FoldingFeature.Orientation.HORIZONTAL,
                    book = f.orientation == FoldingFeature.Orientation.VERTICAL,
                    hingeLeft = b.left, hingeTop = b.top, hingeRight = b.right, hingeBottom = b.bottom,
                    separating = f.isSeparating || f.occlusionType == FoldingFeature.OcclusionType.FULL,
                )
            }
        }
    }
    return posture
}
