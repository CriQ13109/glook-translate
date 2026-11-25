package app.glook.translate.widget

import app.glook.translate.core.str

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.glook.translate.R
import app.glook.translate.ui.main.MainActivity

/**
 * Material You 홈 화면 위젯 (Jetpack Glance 1.2).
 * - GlanceTheme: 앱과 같은 고정 팔레트(GlookPalette).
 * - 크기 3단계(Responsive): 작게=입력 타일, 가로=입력 알약+아이콘 3개, 크게=입력 알약+2x2 타일.
 * - 번역 내용·기록은 표시하지 않음(잠금 화면 없이 홈에 개인 데이터 노출 방지).
 */
class GlookWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme(colors = WidgetColors) { Content(context) } }
    }

    @Composable
    private fun Content(context: Context) {
        fun open(action: String): Action = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        val size = LocalSize.current
        val actions = listOf(
            Tile(str(R.string.tile_voice), R.drawable.ic_widget_mic, GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer, open(MainActivity.ACTION_VOICE)),
            Tile(str(R.string.widget_camera), R.drawable.ic_widget_camera, GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer, open(MainActivity.ACTION_OCR)),
            Tile(str(R.string.tile_conversation), R.drawable.ic_widget_forum, GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer, open(MainActivity.ACTION_CONVERSATION)),
        )
        val input = open(MainActivity.ACTION_FOCUS_INPUT)

        Box(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).padding(10.dp),
        ) {
            when {
                size.width < Wide.width -> Column(GlanceModifier.fillMaxSize()) {
                    InputPill(input, compact = true, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
                }
                size.height < Large.height -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    InputPill(input, compact = false, modifier = GlanceModifier.defaultWeight().fillMaxSize())
                    actions.forEach {
                        Spacer(GlanceModifier.width(8.dp))
                        ActionTile(it, iconOnly = true, modifier = GlanceModifier.size(56.dp))
                    }
                }
                else -> Column(GlanceModifier.fillMaxSize()) {
                    InputPill(input, compact = false, modifier = GlanceModifier.fillMaxWidth().height(56.dp))
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        actions.forEachIndexed { i, t ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            ActionTile(t, iconOnly = false, modifier = GlanceModifier.defaultWeight().fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun InputPill(onClick: Action, compact: Boolean, modifier: GlanceModifier) {
        Row(
            modifier.background(GlanceTheme.colors.surfaceVariant).cornerRadius(if (compact) 24.dp else 100.dp)
                .padding(horizontal = if (compact) 8.dp else 16.dp).clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_edit), str(R.string.widget_input_desc), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary), modifier = GlanceModifier.size(24.dp))
            if (!compact) {
                Spacer(GlanceModifier.width(12.dp))
                Text(str(R.string.widget_input_hint), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium))
            }
        }
    }

    @Composable
    private fun ActionTile(t: Tile, iconOnly: Boolean, modifier: GlanceModifier) {
        Column(
            modifier.background(t.container).cornerRadius(if (iconOnly) 20.dp else 24.dp).clickable(t.onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(t.icon), t.label, colorFilter = ColorFilter.tint(t.content), modifier = GlanceModifier.size(24.dp))
            if (!iconOnly) {
                Spacer(GlanceModifier.height(6.dp))
                Text(t.label, style = TextStyle(color = t.content, fontWeight = FontWeight.Medium))
            }
        }
    }

    private class Tile(val label: String, val icon: Int, val container: ColorProvider, val content: ColorProvider, val onClick: Action)

    companion object {
        private val Small = DpSize(110.dp, 110.dp)
        private val Wide = DpSize(250.dp, 110.dp)
        private val Large = DpSize(250.dp, 180.dp)
    }
}

private val WidgetColors = androidx.glance.material3.ColorProviders(
    light = app.glook.translate.ui.theme.GlookPalette.Light,
    dark = app.glook.translate.ui.theme.GlookPalette.Dark,
)

class GlookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlookWidget()
}
