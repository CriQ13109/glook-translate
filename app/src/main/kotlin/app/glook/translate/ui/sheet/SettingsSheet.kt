package app.glook.translate.ui.sheet

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.GlookApp
import app.glook.translate.domain.model.AppSettings
import app.glook.translate.domain.model.CloudProvider
import app.glook.translate.ui.components.SheetTitle
import app.glook.translate.ui.theme.GlookShapes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as GlookApp).container
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val hasKey = c.settings.settings.map { it.provider }.flatMapLatest { c.apiKeys.hasKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val securityLevel get() = c.vault.securityLevel().label

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(t) }
    fun saveKey(key: String) = viewModelScope.launch { if (key.isNotBlank()) c.apiKeys.putKey(settings.value.provider, key) }
    fun deleteKey() = viewModelScope.launch { c.apiKeys.deleteKey(settings.value.provider) }
    fun wipe(onDone: () -> Unit) = viewModelScope.launch { c.resetter.wipeEverything(); onDone() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onOpenPacks: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val keyInput = rememberTextFieldState()
    var model by remember(s.provider, s.model) { mutableStateOf(s.model) }
    var baseUrl by remember(s.provider, s.baseUrl) { mutableStateOf(s.baseUrl) }
    var confirmWipe by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(str(R.string.settings))

            Section(str(R.string.settings_engine)) {
                SwitchRow(str(R.string.settings_cloud_byok), s.cloudEnabled) { v -> vm.update { it.copy(cloudEnabled = v) } }
                if (s.cloudEnabled) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CloudProvider.entries.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = s.provider == p,
                                onClick = { vm.update { it.copy(provider = p, model = p.defaultModel, baseUrl = p.defaultBaseUrl) } },
                                shape = SegmentedButtonDefaults.itemShape(i, CloudProvider.entries.size),
                            ) { Text(p.label) }
                        }
                    }
                    OutlinedSecureTextField(
                        state = keyInput,
                        label = { Text(if (hasKey) str(R.string.api_key_saved) else str(R.string.api_key)) },
                        shape = GlookShapes.Chip,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.saveKey(keyInput.text.toString()); keyInput.clearText() }, enabled = keyInput.text.isNotBlank(), shape = GlookShapes.Chip) { Text(str(R.string.save_encrypted)) }
                        if (hasKey) TextButton(onClick = vm::deleteKey) { Text(str(R.string.delete_key)) }
                    }
                    OutlinedTextField(
                        value = model, onValueChange = { model = it }, label = { Text(str(R.string.model_id)) }, singleLine = true,
                        shape = GlookShapes.Chip, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (HTTPS)") }, singleLine = true,
                        shape = GlookShapes.Chip, modifier = Modifier.fillMaxWidth(),
                    )
                    LaunchedEffect(model, baseUrl) {
                        if (model != s.model || baseUrl != s.baseUrl) vm.update { it.copy(model = model.trim(), baseUrl = baseUrl.trim()) }
                    }
                }
            }

            Section(str(R.string.settings_convenience)) {
                SwitchRow(str(R.string.settings_clipboard), s.clipboardSuggest) { v -> vm.update { it.copy(clipboardSuggest = v) } }
                SwitchRow(str(R.string.settings_auto_speak), s.autoSpeak) { v -> vm.update { it.copy(autoSpeak = v) } }
                SwitchRow(str(R.string.settings_auto_copy), s.autoCopy) { v -> vm.update { it.copy(autoCopy = v) } }
                SwitchRow(str(R.string.settings_haptics), s.haptics) { v -> vm.update { it.copy(haptics = v) } }
                FilledTonalButton(onClick = onOpenPacks, shape = GlookShapes.Chip) { Text(str(R.string.settings_manage_packs)) }
            }

            Section(str(R.string.settings_security)) {
                SwitchRow(str(R.string.settings_block_capture), s.secureScreen) { v -> vm.update { it.copy(secureScreen = v) } }
                SwitchRow(str(R.string.settings_save_history), s.saveHistory) { v -> vm.update { it.copy(saveHistory = v) } }
                Text(str(R.string.settings_key_storage_fmt, vm.securityLevel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    shape = GlookShapes.Chip,
                ) { Text(str(R.string.settings_wipe)) }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(str(R.string.wipe_title)) },
            text = { Text(str(R.string.wipe_message)) },
            confirmButton = { TextButton(onClick = { vm.wipe { confirmWipe = false; onDismiss() } }) { Text(str(R.string.wipe_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(str(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = GlookShapes.Container, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
