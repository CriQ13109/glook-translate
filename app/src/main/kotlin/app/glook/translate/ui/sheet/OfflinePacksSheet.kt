package app.glook.translate.ui.sheet

import app.glook.translate.R
import app.glook.translate.core.str

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.domain.model.Language
import app.glook.translate.ui.components.SheetTitle
import app.glook.translate.ui.icons.GlookIcons
import app.glook.translate.ui.icons.CloudDownload
import app.glook.translate.ui.icons.DeleteForever
import app.glook.translate.ui.icons.OfflinePin
import app.glook.translate.ui.theme.GlookShapes
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class PackState { Missing, Downloading, Ready, Builtin }

class OfflinePacksViewModel : ViewModel() {
    private val manager = RemoteModelManager.getInstance()
    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    val states: StateFlow<Map<String, PackState>> = _states

    private fun code(l: Language) = TranslateLanguage.fromLanguageTag(l.code)
    private fun model(code: String) = TranslateRemoteModel.Builder(code).build()

    /** 온디바이스 엔진이 지원하는 언어만. */
    val languages: List<Language> = Language.All.filter { code(it) != null }

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val m = languages.associate { l ->
            val c = code(l)!!
            l.code to when {
                c == TranslateLanguage.ENGLISH -> PackState.Builtin
                runCatching { manager.isModelDownloaded(model(c)).await() }.getOrDefault(false) -> PackState.Ready
                else -> PackState.Missing
            }
        }
        _states.value = m
    }

    fun download(l: Language) = viewModelScope.launch {
        val c = code(l) ?: return@launch
        set(l, PackState.Downloading)
        val ok = runCatching { manager.download(model(c), DownloadConditions.Builder().build()).await() }.isSuccess
        set(l, if (ok) PackState.Ready else PackState.Missing)
    }

    fun delete(l: Language) = viewModelScope.launch {
        val c = code(l) ?: return@launch
        runCatching { manager.deleteDownloadedModel(model(c)).await() }
        refresh()
    }

    private fun set(l: Language, s: PackState) = _states.update { it + (l.code to s) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfflinePacksSheet(onDismiss: () -> Unit, vm: OfflinePacksViewModel = viewModel()) {
    val states by vm.states.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SheetTitle(str(R.string.packs_title))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                items(vm.languages, key = { it.code }) { l ->
                    val st = states[l.code] ?: PackState.Missing
                    ListItem(
                        onClick = {},
                        content = { Text(l.displayName) },
                        supportingContent = {
                            Text(when (st) {
                                PackState.Builtin -> str(R.string.pack_builtin)
                                PackState.Ready -> str(R.string.pack_downloaded)
                                PackState.Downloading -> str(R.string.pack_downloading)
                                PackState.Missing -> str(R.string.pack_not_downloaded)
                            })
                        },
                        trailingContent = {
                            when (st) {
                                PackState.Builtin -> Icon(GlookIcons.OfflinePin, null, tint = MaterialTheme.colorScheme.primary)
                                PackState.Downloading -> CircularProgressIndicator(Modifier.padding(8.dp))
                                PackState.Ready -> FilledTonalIconButton(onClick = { vm.delete(l) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(GlookIcons.DeleteForever, str(R.string.pack_delete_fmt, l.displayName))
                                }
                                PackState.Missing -> FilledTonalIconButton(onClick = { vm.download(l) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(GlookIcons.CloudDownload, str(R.string.pack_download_fmt, l.displayName))
                                }
                            }
                        },
                        shapes = ListItemDefaults.shapes(),
                    )
                }
            }
        }
    }
}
