package app.glook.translate.data.repository

import app.glook.translate.data.db.Vault
import app.glook.translate.domain.model.AppSettings
import app.glook.translate.domain.model.Bookmark
import app.glook.translate.domain.model.CloudProvider
import app.glook.translate.domain.model.HistoryEntry
import app.glook.translate.domain.repository.ApiKeyRepository
import app.glook.translate.domain.repository.LibraryRepository
import app.glook.translate.domain.repository.SettingsRepository
import app.glook.translate.domain.repository.VaultResetter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Vault.observe(block: suspend (app.glook.translate.data.db.GlookDatabase) -> Flow<T>): Flow<T> =
    epoch.flatMapLatest { flow { block(get()).collect { emit(it) } } }

class LibraryRepositoryImpl(
    private val vault: Vault,
    private val settings: SettingsRepository,
) : LibraryRepository {
    override fun bookmarks(query: String): Flow<List<Bookmark>> = vault.observe { it.bookmarks(query) }
    override fun history(query: String): Flow<List<HistoryEntry>> = vault.observe { it.history(query) }

    override suspend fun addHistory(source: String, translated: String, sourceLang: String, targetLang: String, engine: String) {
        if (!settings.settings.first().saveHistory) return
        vault.get().upsertHistory(source, translated, sourceLang, targetLang, engine)
    }

    override suspend fun toggleBookmark(source: String, translated: String, sourceLang: String, targetLang: String) =
        vault.get().toggleBookmark(source, translated, sourceLang, targetLang)

    override suspend fun isBookmarked(source: String, translated: String) = vault.get().isBookmarked(source, translated)
    override suspend fun deleteBookmark(id: Long) { vault.get().deleteBookmark(id) }
    override suspend fun deleteHistory(id: Long) { vault.get().deleteHistory(id) }
    override suspend fun clearHistory() { vault.get().clearHistory() }
}

class ApiKeyRepositoryImpl(private val vault: Vault) : ApiKeyRepository {
    override suspend fun getKey(provider: CloudProvider) = vault.get().getApiKey(provider.name)
    override suspend fun putKey(provider: CloudProvider, key: String) { vault.get().putApiKey(provider.name, key.trim()) }
    override suspend fun deleteKey(provider: CloudProvider) { vault.get().deleteApiKey(provider.name) }
    override fun hasKey(provider: CloudProvider): Flow<Boolean> = vault.observe { it.observeApiKeyPresence(provider.name) }
}

/** 설정도 암호화 DB(key/value 테이블)에 저장 → 기기 로컬 평문 설정 파일 없음. */
class SettingsRepositoryImpl(private val vault: Vault) : SettingsRepository {
    override val settings: Flow<AppSettings> = vault.observe { db -> db.observeSettings().map { it.toSettings() } }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.first())
        vault.get().putSettings(next.toMap())
    }

    private fun Map<String, String>.toSettings(): AppSettings {
        val d = AppSettings()
        val provider = this["provider"]?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() } ?: d.provider
        return AppSettings(
            secureScreen = this["secure_screen"]?.toBooleanStrictOrNull() ?: d.secureScreen,
            sourceLang = this["source_lang"] ?: d.sourceLang,
            targetLang = this["target_lang"] ?: d.targetLang,
            cloudEnabled = this["cloud_enabled"]?.toBooleanStrictOrNull() ?: d.cloudEnabled,
            provider = provider,
            model = this["model"] ?: provider.defaultModel,
            baseUrl = this["base_url"] ?: provider.defaultBaseUrl,
            saveHistory = this["save_history"]?.toBooleanStrictOrNull() ?: d.saveHistory,
            recentLangs = this["recent_langs"]?.split(',')?.filter { it.isNotBlank() } ?: d.recentLangs,
            autoSpeak = this["auto_speak"]?.toBooleanStrictOrNull() ?: d.autoSpeak,
            autoCopy = this["auto_copy"]?.toBooleanStrictOrNull() ?: d.autoCopy,
            clipboardSuggest = this["clipboard_suggest"]?.toBooleanStrictOrNull() ?: d.clipboardSuggest,
            haptics = this["haptics"]?.toBooleanStrictOrNull() ?: d.haptics,
        )
    }

    private fun AppSettings.toMap() = mapOf(
        "secure_screen" to secureScreen.toString(),
        "source_lang" to sourceLang,
        "target_lang" to targetLang,
        "cloud_enabled" to cloudEnabled.toString(),
        "provider" to provider.name,
        "model" to model,
        "base_url" to baseUrl,
        "save_history" to saveHistory.toString(),
        "recent_langs" to recentLangs.joinToString(","),
        "auto_speak" to autoSpeak.toString(),
        "auto_copy" to autoCopy.toString(),
        "clipboard_suggest" to clipboardSuggest.toString(),
        "haptics" to haptics.toString(),
    )
}

class VaultResetterImpl(private val vault: Vault, private val onWiped: () -> Unit) : VaultResetter {
    override suspend fun wipeEverything() {
        vault.wipe()
        onWiped()
    }
}
