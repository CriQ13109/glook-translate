package app.glook.translate.domain.repository

import app.glook.translate.domain.model.AppSettings
import app.glook.translate.domain.model.Bookmark
import app.glook.translate.domain.model.CloudProvider
import app.glook.translate.domain.model.HistoryEntry
import app.glook.translate.domain.model.TranslationRequest
import app.glook.translate.domain.model.TranslationUpdate
import kotlinx.coroutines.flow.Flow

interface TranslationRepository {
    fun translate(request: TranslationRequest): Flow<TranslationUpdate>
}

interface LibraryRepository {
    fun bookmarks(query: String): Flow<List<Bookmark>>
    fun history(query: String): Flow<List<HistoryEntry>>
    suspend fun addHistory(source: String, translated: String, sourceLang: String, targetLang: String, engine: String)
    suspend fun toggleBookmark(source: String, translated: String, sourceLang: String, targetLang: String): Boolean
    suspend fun isBookmarked(source: String, translated: String): Boolean
    suspend fun deleteBookmark(id: Long)
    suspend fun deleteHistory(id: Long)
    suspend fun clearHistory()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** API 키는 SQLCipher DB 안에만 저장됩니다. 평문 키는 메모리에서만 다룹니다. */
interface ApiKeyRepository {
    suspend fun getKey(provider: CloudProvider): String?
    suspend fun putKey(provider: CloudProvider, key: String)
    suspend fun deleteKey(provider: CloudProvider)
    fun hasKey(provider: CloudProvider): Flow<Boolean>
}

/** 암호화 DB 를 완전히 파기하고 새 키로 재생성. */
interface VaultResetter {
    suspend fun wipeEverything()
}
