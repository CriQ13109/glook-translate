package app.glook.translate.ui.home

import app.glook.translate.R
import app.glook.translate.core.str

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxHeight
import app.glook.translate.ui.components.LanguageBar
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import app.glook.translate.ui.adaptive.rememberFoldPosture
import app.glook.translate.data.speech.TtsState
import app.glook.translate.domain.model.Language
import app.glook.translate.ui.common.TranslatorUiState
import app.glook.translate.ui.components.ChipIconButton
import app.glook.translate.ui.components.Haptic
import app.glook.translate.ui.components.LanguageButton
import app.glook.translate.ui.components.LanguagePickerSheet
import app.glook.translate.ui.components.MorphSwapButton
import app.glook.translate.ui.components.ShapedIcon
import app.glook.translate.ui.components.TtsWaveControl
import app.glook.translate.ui.components.bouncyClick
import app.glook.translate.ui.components.rememberHaptics
import app.glook.translate.ui.icons.AutoAwesome
import app.glook.translate.ui.icons.CloudDownload
import app.glook.translate.ui.icons.CollectionsBookmark
import app.glook.translate.ui.icons.ContentCopy
import app.glook.translate.ui.icons.ContentPasteGo
import app.glook.translate.ui.icons.Forum
import app.glook.translate.ui.icons.Fullscreen
import app.glook.translate.ui.icons.Description
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.Image
import app.glook.translate.ui.icons.Mic
import app.glook.translate.ui.icons.PhotoCamera
import app.glook.translate.ui.icons.StarBorder
import app.glook.translate.ui.icons.Stop
import app.glook.translate.ui.icons.VolumeUp
import app.glook.translate.ui.theme.GlookShapes

/** 홈 화면이 밖으로 내보내는 모든 동작. (화면은 상태 없이 State + Callbacks 만 받음 → 미리보기/스크린샷 테스트 용이) */
class HomeCallbacks(
    val onClear: () -> Unit = {},
    val onPaste: () -> Unit = {},
    val onSwap: () -> Unit = {},
    val onPickSource: (Language) -> Unit = {},
    val onPickTarget: (Language) -> Unit = {},
    val onVoice: () -> Unit = {},
    val onImage: () -> Unit = {},
    val onConversation: () -> Unit = {},
    val onSpeak: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onBookmark: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onFullscreen: () -> Unit = {},
    val onLibrary: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onOfflinePacks: () -> Unit = {},
    val onDocument: () -> Unit = {},
    val onClipboardAccept: () -> Unit = {},
    val onClipboardDismiss: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: TranslatorUiState,
    tts: TtsState,
    listening: Boolean,
    partial: String,
    cloudOn: Boolean,
    field: TextFieldState,
    focus: FocusRequester,
    cb: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    val fold = rememberFoldPosture()
    val imeVisible = WindowInsets.isImeVisible
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(str(R.string.app_name), style = MaterialTheme.typography.titleLargeEmphasized)
                        // 넓은 화면은 제목 줄 빈 공간에 최근 언어를 둠
                        if (wide) Box(Modifier.padding(start = 24.dp).weight(1f)) { RecentChips(state, onPick = cb.onPickTarget) }
                    }
                },
                actions = {
                    ChipIconButton(GlookIcons.CollectionsBookmark, str(R.string.library), onClick = cb.onLibrary)
                    ChipIconButton(Icons.Filled.Settings, str(R.string.settings), onClick = cb.onSettings)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        // 한 화면에 모두 들어가는 고정 레이아웃(스크롤 없음). 긴 글은 각 카드 안에서만 스크롤.
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hasResult = state.output.isNotBlank() || state.error != null || state.streaming
            val showTiles = !hasResult && state.input.isBlank() && !imeVisible
            val chipsAndBanner: @Composable () -> Unit = {
                if (!wide) RecentChips(state, onPick = cb.onPickTarget)
                AnimatedVisibility(state.clipboardOffer, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    ClipboardBanner(cb.onClipboardAccept, cb.onClipboardDismiss)
                }
            }
            val langBar: @Composable () -> Unit = {
                LanguageBar(
                    sourceLabel = if (state.source.isAuto) state.detected?.let { str(R.string.detected_fmt, it.displayName) } ?: str(R.string.auto_detect) else state.source.displayName,
                    targetLabel = state.target.displayName,
                    onSource = { picker = Picker.SOURCE }, onTarget = { picker = Picker.TARGET }, onSwap = cb.onSwap,
                )
            }
            if (wide) {
                // 넓은 창(태블릿·펼친 폴더블·크롬북): 같은 높이의 헤더 줄(왼쪽 언어 선택 | 오른쪽 기능 타일) 아래에
                // 같은 높이의 두 카드(입력 | 결과). 북 자세에서는 힌지 폭만큼 사이를 띄움.
                val gap = if (fold.book && fold.separating) with(LocalDensity.current) { fold.hingeWidthPx.toDp() }.coerceAtLeast(12.dp) else 12.dp
                // 하드웨어 키보드가 있으면 바로 입력 가능하게 포커스
                val hasKeyboard = LocalConfiguration.current.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
                LaunchedEffect(hasKeyboard) { if (hasKeyboard) runCatching { focus.requestFocus() } }
                val mid = maxOf(56.dp, gap)
                chipsAndBanner()
                // 기능 타일 + 오프라인 언어팩: 가로 스크롤 한 줄 (좁으면 넘겨서 봄)
                FeatureStrip(cb)
                // 언어 선택은 각자 자기 카드 바로 위(원문 | 번역), 가운데 교체 버튼
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LanguageButton(
                        text = if (state.source.isAuto) state.detected?.let { str(R.string.detected_fmt, it.displayName) } ?: str(R.string.auto_detect) else state.source.displayName,
                        onClick = { picker = Picker.SOURCE }, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.width(mid), contentAlignment = Alignment.Center) { MorphSwapButton(onSwap = cb.onSwap) }
                    LanguageButton(text = state.target.displayName, onClick = { picker = Picker.TARGET }, modifier = Modifier.weight(1f), accent = true)
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    InputCard(state, listening, partial, field, focus, onClear = cb.onClear, onPaste = cb.onPaste, modifier = Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(mid))
                    if (hasResult) OutputCard(state, tts, cb, Modifier.weight(1f).fillMaxHeight())
                    else Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            } else {
                langBar()
                chipsAndBanner()
                InputCard(state, listening, partial, field, focus, onClear = cb.onClear, onPaste = cb.onPaste, modifier = Modifier.weight(1f))
                if (hasResult) OutputCard(state, tts, cb, Modifier.weight(1.2f))
                else if (showTiles) FeatureGrid(cb)
            }
        }
    }

    picker?.let { p ->
        LanguagePickerSheet(
            includeAuto = p == Picker.SOURCE,
            selected = if (p == Picker.SOURCE) state.source.code else state.target.code,
            recents = state.recents,
            onPick = { if (p == Picker.SOURCE) cb.onPickSource(it) else cb.onPickTarget(it); picker = null },
            onDismiss = { picker = null },
        )
    }
}

private enum class Picker { SOURCE, TARGET }

@Composable
private fun RecentChips(state: TranslatorUiState, onPick: (Language) -> Unit) {
    val list = state.recents.filter { !it.isAuto }
    if (list.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.code }) { lang ->
            FilterChip(selected = lang.code == state.target.code, onClick = { onPick(lang) }, label = { Text(lang.displayName) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardBanner(onAccept: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().bouncyClick(Haptic.CONFIRM, onClick = onAccept)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(MaterialShapes.Cookie6Sided, GlookIcons.ContentPasteGo, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, size = 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(str(R.string.clipboard_offer), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            ChipIconButton(Icons.Filled.Close, str(R.string.close), onClick = onDismiss)
        }
    }
}

@Composable
private fun InputCard(
    state: TranslatorUiState, listening: Boolean, partial: String,
    field: TextFieldState, focus: FocusRequester, onClear: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier,
) {
    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            val textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)
            // 입력창의 진실 공급원은 TextFieldState 하나. IME 조합(특히 천지인 등 10키)을 건드리지 않음.
            BasicTextField(
                state = field,
                textStyle = textStyle,
                inputTransformation = InputTransformation.maxLength(MAX_CHARS),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus),
                decorator = { inner ->
                    Box {
                        if (field.text.isEmpty()) {
                            Text(
                                if (listening) partial.ifBlank { str(R.string.listening) } else str(R.string.input_hint),
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        }
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(state.input.isNotEmpty()) { ChipIconButton(Icons.Filled.Clear, str(R.string.clear), onClick = onClear) }
                AnimatedVisibility(state.input.isEmpty()) { ChipIconButton(GlookIcons.ContentPasteGo, str(R.string.paste), onClick = onPaste) }
                Spacer(Modifier.weight(1f))
                if (state.input.isNotEmpty()) {
                    Text("${state.input.length} / $MAX_CHARS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

private const val MAX_CHARS = 5000

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutputCard(state: TranslatorUiState, tts: TtsState, cb: HomeCallbacks, modifier: Modifier = Modifier) {
    val hasOutput = state.output.isNotBlank()
    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                when {
                    state.error != null && !hasOutput -> Text(state.error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    !hasOutput && state.streaming -> LoadingIndicator(Modifier.size(48.dp))
                    else -> Text(state.output, style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (hasOutput && state.engine != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    ShapedIcon(
                        if (state.engine.name == "ON_DEVICE") MaterialShapes.Cookie9Sided else MaterialShapes.Sunny,
                        GlookIcons.AutoAwesome,
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 28.dp,
                    )
                    Text(
                        text = state.engine.label + if (state.fellBack) str(R.string.engine_fallback_suffix) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (tts.speaking) TtsWaveControl(tts, onToggle = cb.onSpeak, enabled = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            // Expressive 플로팅 툴바 — 수동 톤 버튼/문법 해설 없음 (요구사항 제외 사항)
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { if (hasOutput) cb.onFullscreen() }) {
                        Icon(GlookIcons.Fullscreen, str(R.string.fullscreen_zoom))
                    }
                },
            ) {
                ChipIconButton(if (tts.speaking) GlookIcons.Stop else GlookIcons.VolumeUp, str(R.string.native_pronounce), onClick = cb.onSpeak, enabled = hasOutput, selected = tts.speaking)
                ChipIconButton(GlookIcons.ContentCopy, str(R.string.copy), onClick = cb.onCopy, enabled = hasOutput)
                ChipIconButton(if (state.bookmarked) Icons.Filled.Star else GlookIcons.StarBorder, str(R.string.bookmark), onClick = cb.onBookmark, enabled = hasOutput, selected = state.bookmarked)
                ChipIconButton(Icons.Filled.Share, str(R.string.share), onClick = cb.onShare, enabled = hasOutput)
            }
        }
    }
}

/** 입력이 비어 있을 때의 기능 진입점: 2x2 타일 + 작고 긴 언어팩 줄. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeatureGrid(cb: HomeCallbacks) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(str(R.string.tile_conversation), MaterialShapes.Cookie9Sided, GlookIcons.Forum, cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary, Modifier.weight(1f), cb.onConversation)
            FeatureTile(str(R.string.tile_image), MaterialShapes.Sunny, GlookIcons.PhotoCamera, cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary, Modifier.weight(1f), cb.onImage)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(str(R.string.tile_voice), MaterialShapes.Clover4Leaf, GlookIcons.Mic, cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, cs.onTertiary, Modifier.weight(1f), cb.onVoice)
            FeatureTile(str(R.string.tile_document), MaterialShapes.Flower, GlookIcons.Description, cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary, Modifier.weight(1f), cb.onDocument)
        }
        PacksBar(cb)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeatureTile(
    title: String,
    polygon: androidx.graphics.shapes.RoundedPolygon, icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color, content: Color, accent: Color, onAccent: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit, height: androidx.compose.ui.unit.Dp = 104.dp,
) {
    Surface(shape = GlookShapes.Container, color = container, contentColor = content, modifier = modifier.height(height).bouncyClick(onClick = onClick)) {
        Column(Modifier.padding(if (height < 100.dp) 10.dp else 14.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            ShapedIcon(polygon, icon, accent, onAccent, size = if (height < 100.dp) 36.dp else 44.dp)
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PacksBar(cb: HomeCallbacks) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = GlookShapes.Container, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, modifier = Modifier.fillMaxWidth().height(76.dp).bouncyClick(onClick = cb.onOfflinePacks)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShapedIcon(MaterialShapes.Flower, GlookIcons.CloudDownload, cs.secondary, cs.onSecondary, size = 48.dp)
            Text(str(R.string.tile_packs), style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

/** 와이드 화면용: 대화·이미지·음성·문서·오프라인 언어팩을 한 줄에. 폭이 남으면 균등 분할, 부족하면 가로 스크롤. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeatureStrip(cb: HomeCallbacks) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = ((maxWidth - 8.dp * 4) / 5).coerceAtLeast(148.dp)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FeatureTile(str(R.string.tile_conversation), MaterialShapes.Cookie9Sided, GlookIcons.Forum, cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary, Modifier.width(w), cb.onConversation, height = 80.dp) }
            item { FeatureTile(str(R.string.tile_image), MaterialShapes.Sunny, GlookIcons.PhotoCamera, cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary, Modifier.width(w), cb.onImage, height = 80.dp) }
            item { FeatureTile(str(R.string.tile_voice), MaterialShapes.Clover4Leaf, GlookIcons.Mic, cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, cs.onTertiary, Modifier.width(w), cb.onVoice, height = 80.dp) }
            item { FeatureTile(str(R.string.tile_document), MaterialShapes.Flower, GlookIcons.Description, cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary, Modifier.width(w), cb.onDocument, height = 80.dp) }
            item { FeatureTile(str(R.string.tile_packs), MaterialShapes.Cookie6Sided, GlookIcons.CloudDownload, cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary, Modifier.width(w), cb.onOfflinePacks, height = 80.dp) }
        }
    }
}
