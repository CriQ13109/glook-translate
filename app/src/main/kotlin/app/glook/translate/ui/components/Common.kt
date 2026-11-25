package app.glook.translate.ui.components

import app.glook.translate.R
import app.glook.translate.core.str

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import app.glook.translate.data.speech.TtsState
import app.glook.translate.domain.model.Language
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.Stop
import app.glook.translate.ui.icons.VolumeUp

/** Expressive 아이콘 버튼: 눌리면 모양이 스프링으로 변형(IconButtonDefaults.shapes). 햅틱 포함. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChipIconButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, selected: Boolean = false) {
    val haptic = rememberHaptics()
    val click = { haptic(Haptic.TICK); onClick() }
    if (selected) {
        FilledTonalIconButton(onClick = click, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, label) }
    } else {
        IconButton(onClick = click, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, label) }
    }
}

/** 강조 아이콘 버튼 (마이크 등). 평소엔 원, 눌리면 둥근 사각형으로 변형. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircleActionButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, enabled: Boolean = true) {
    val haptic = rememberHaptics()
    val click = { haptic(Haptic.CONFIRM); onClick() }
    if (active) {
        FilledIconButton(onClick = click, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, label) }
    } else {
        FilledTonalIconButton(onClick = click, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, label) }
    }
}

/** MaterialShapes 다각형(쿠키·클로버 등)을 컨테이너로 쓰는 아이콘. Expressive 의 시그니처 장식. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedIcon(
    polygon: RoundedPolygon,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    contentDescription: String? = null,
) {
    val shape = polygon.toShape()
    Box(modifier.size(size).clip(shape).background(containerColor), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = contentColor, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * 물결형 TTS 재생 바: 재생 중에만 LinearWavyProgressIndicator 가 일렁이며 진행률 표시.
 * TTS 엔진이 단어 단위 콜백을 주지 않으면 불확정(indeterminate) 물결로 표시.
 */
@Composable
fun TtsWaveControl(state: TtsState, onToggle: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipIconButton(
            icon = if (state.speaking) GlookIcons.Stop else GlookIcons.VolumeUp,
            label = if (state.speaking) str(R.string.pronounce_stop) else str(R.string.pronounce_listen),
            onClick = onToggle,
            enabled = enabled,
            selected = state.speaking,
        )
        if (state.speaking) {
            val p = state.progress
            if (p != null) LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.weight(1f))
            else LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
        }
    }
}

/** 앱 전체 공용 텍스트 버튼. 언어 선택·토글·저장 등 모든 텍스트 버튼은 이 한 종류만 씁니다. [selected] 면 강조(filled), 아니면 tonal. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    val haptic = rememberHaptics()
    Button(
        onClick = { haptic(Haptic.TICK); onClick() },
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
    }
}

@Composable
fun LanguageButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) =
    ActionButton(text, onClick, modifier, selected = accent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(includeAuto: Boolean, selected: String, recents: List<Language>, onPick: (Language) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        val query = rememberTextFieldState()
        val q = query.text.toString().trim()
        val all = if (includeAuto) listOf(Language.Auto) + Language.All else Language.All
        val list = if (q.isEmpty()) {
            val rec = recents.filter { r -> all.any { it.code == r.code } }
            (rec + all.filter { a -> rec.none { it.code == a.code } })
        } else all.filter { it.displayName.contains(q, true) || it.code.contains(q, true) }

        Column(Modifier.padding(bottom = 24.dp)) {
            SheetTitle(str(R.string.pick_language))
            androidx.compose.material3.OutlinedTextField(
                state = query,
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(str(R.string.search_language)) },
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                items(list, key = { it.code }) { lang ->
                    ListItem(
                        onClick = { onPick(lang) },
                        supportingContent = if (!lang.isAuto) ({ Text(lang.code) }) else null,
                        colors = ListItemDefaults.colors(
                            containerColor = if (lang.code == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    ) { Text(lang.displayName, fontWeight = if (lang.code == selected) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
fun SheetTitle(text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmallEmphasized)
    }
}

/** 홈·문서 번역 등 모든 화면이 쓰는 공용 언어 바: [원문 | 스왑 | 대상]. */
@Composable
fun LanguageBar(sourceLabel: String, targetLabel: String, onSource: () -> Unit, onTarget: () -> Unit, onSwap: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        LanguageButton(text = sourceLabel, onClick = onSource, modifier = Modifier.weight(1f))
        MorphSwapButton(onSwap = onSwap, modifier = Modifier.padding(horizontal = 8.dp))
        LanguageButton(text = targetLabel, onClick = onTarget, modifier = Modifier.weight(1f), accent = true)
    }
}
