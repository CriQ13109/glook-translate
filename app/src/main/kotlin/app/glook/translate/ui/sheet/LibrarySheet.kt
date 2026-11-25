package app.glook.translate.ui.sheet

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.glook.translate.GlookApp
import app.glook.translate.ui.components.SheetTitle
import app.glook.translate.ui.theme.GlookShapes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryItem(val id: Long, val source: String, val translated: String, val meta: String)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val lib = (app as GlookApp).container.library
    val query = MutableStateFlow("")
    val tab = MutableStateFlow(0) // 0=북마크, 1=기록

    val items = combine(query.debounce(150), tab) { q, t -> q to t }.flatMapLatest { (q, t) ->
        if (t == 0) lib.bookmarks(q).map { l -> l.map { LibraryItem(it.id, it.sourceText, it.translatedText, "${it.sourceLang} → ${it.targetLang}") } }
        else lib.history(q).map { l -> l.map { LibraryItem(it.id, it.sourceText, it.translatedText, "${it.sourceLang} → ${it.targetLang}") } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { if (tab.value == 0) lib.deleteBookmark(id) else lib.deleteHistory(id) }
    fun clearHistory() = viewModelScope.launch { lib.clearHistory() }
}

/** 암호화 보관함: 북마크/기록 + 로컬 검색 (검색은 SQLCipher DB 내부 쿼리, 외부 인덱스 없음). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(onDismiss: () -> Unit, onOpen: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            SheetTitle(str(R.string.library))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { vm.tab.value = 0 }, text = { Text(str(R.string.bookmarks)) })
                Tab(selected = tab == 1, onClick = { vm.tab.value = 1 }, text = { Text(str(R.string.history)) })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(str(R.string.search_library)) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (tab == 1 && items.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(str(R.string.clear_history)) }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Surface(
                        shape = GlookShapes.Chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clip(GlookShapes.Chip).clickable { onOpen(item.source) },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.translated, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(item.meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { vm.delete(item.id) }) { Icon(Icons.Filled.Delete, str(R.string.delete)) }
                        }
                    }
                }
                if (items.isEmpty()) item {
                    Text(if (tab == 0) str(R.string.no_bookmarks) else str(R.string.no_history), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
