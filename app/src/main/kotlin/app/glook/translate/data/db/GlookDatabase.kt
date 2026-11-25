package app.glook.translate.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.transaction
import app.glook.translate.domain.model.Bookmark
import app.glook.translate.domain.model.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher(AES-256) 전체 암호화 DB. Room 없이 SupportSQLite API 를 직접 사용합니다(KSP/어노테이션 처리 불필요).
 * 모든 사용자 데이터 — 히스토리, 북마크, BYOK API 키, 앱 설정 — 는 이 파일 하나에만 저장됩니다.
 */
class GlookDatabase private constructor(private val helper: SupportSQLiteOpenHelper) {

    private val db: SupportSQLiteDatabase get() = helper.writableDatabase
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun <T> observe(table: String, query: () -> T): Flow<T> =
        changes.onStart { emit(table) }
            .filter { it == table }
            .map { query() }
            .flowOn(Dispatchers.IO)

    // ---------- history ----------
    fun history(query: String): Flow<List<HistoryEntry>> = observe(T_HISTORY) {
        val like = "%${escapeLike(query)}%"
        db.query(
            "SELECT id, source, translated, src_lang, tgt_lang, engine, created_at FROM $T_HISTORY " +
                "WHERE source LIKE ? ESCAPE '\\' OR translated LIKE ? ESCAPE '\\' ORDER BY created_at DESC LIMIT 500",
            arrayOf(like, like),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(HistoryEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6)))
            }
        }
    }

    /** 타이핑 중 연속 번역이 기록을 도배하지 않도록, 직전 기록의 원문이 새 원문의 접두/연장이면 갱신합니다. */
    suspend fun upsertHistory(source: String, translated: String, src: String, tgt: String, engine: String) = io(T_HISTORY) {
        db.transaction {
            val last = query("SELECT id, source FROM $T_HISTORY ORDER BY created_at DESC LIMIT 1").use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null
            }
            val values = ContentValues().apply {
                put("source", source); put("translated", translated)
                put("src_lang", src); put("tgt_lang", tgt); put("engine", engine)
                put("created_at", System.currentTimeMillis())
            }
            if (last != null && (source.startsWith(last.second) || last.second.startsWith(source))) {
                update(T_HISTORY, CONFLICT_REPLACE, values, "id = ?", arrayOf(last.first))
            } else {
                insert(T_HISTORY, CONFLICT_REPLACE, values)
            }
        }
    }

    suspend fun deleteHistory(id: Long) = io(T_HISTORY) { db.delete(T_HISTORY, "id = ?", arrayOf(id)) }
    suspend fun clearHistory() = io(T_HISTORY) { db.delete(T_HISTORY, null, null) }

    // ---------- bookmarks ----------
    fun bookmarks(query: String): Flow<List<Bookmark>> = observe(T_BOOKMARK) {
        val like = "%${escapeLike(query)}%"
        db.query(
            "SELECT id, source, translated, src_lang, tgt_lang, created_at FROM $T_BOOKMARK " +
                "WHERE source LIKE ? ESCAPE '\\' OR translated LIKE ? ESCAPE '\\' ORDER BY created_at DESC",
            arrayOf(like, like),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5)))
            }
        }
    }

    suspend fun isBookmarked(source: String, translated: String): Boolean = withContext(Dispatchers.IO) {
        db.query("SELECT 1 FROM $T_BOOKMARK WHERE source = ? AND translated = ?", arrayOf(source, translated)).use { it.moveToFirst() }
    }

    /** @return 토글 후 북마크 여부 */
    suspend fun toggleBookmark(source: String, translated: String, src: String, tgt: String): Boolean = io(T_BOOKMARK) {
        val removed = db.delete(T_BOOKMARK, "source = ? AND translated = ?", arrayOf(source, translated))
        if (removed > 0) false else {
            db.insert(T_BOOKMARK, CONFLICT_IGNORE, ContentValues().apply {
                put("source", source); put("translated", translated)
                put("src_lang", src); put("tgt_lang", tgt); put("created_at", System.currentTimeMillis())
            })
            true
        }
    }

    suspend fun deleteBookmark(id: Long) = io(T_BOOKMARK) { db.delete(T_BOOKMARK, "id = ?", arrayOf(id)) }

    // ---------- api keys ----------
    suspend fun getApiKey(provider: String): String? = withContext(Dispatchers.IO) {
        db.query("SELECT secret FROM $T_KEYS WHERE provider = ?", arrayOf(provider)).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun putApiKey(provider: String, secret: String) = io(T_KEYS) {
        db.insert(T_KEYS, CONFLICT_REPLACE, ContentValues().apply {
            put("provider", provider); put("secret", secret); put("updated_at", System.currentTimeMillis())
        })
    }

    suspend fun deleteApiKey(provider: String) = io(T_KEYS) { db.delete(T_KEYS, "provider = ?", arrayOf(provider)) }

    fun observeApiKeyPresence(provider: String): Flow<Boolean> = observe(T_KEYS) {
        db.query("SELECT 1 FROM $T_KEYS WHERE provider = ?", arrayOf(provider)).use { it.moveToFirst() }
    }

    // ---------- settings (key/value) ----------
    fun observeSettings(): Flow<Map<String, String>> = observe(T_SETTINGS) {
        db.query("SELECT k, v FROM $T_SETTINGS").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
    }

    suspend fun putSettings(values: Map<String, String>) = io(T_SETTINGS) {
        db.transaction {
            values.forEach { (k, v) ->
                insert(T_SETTINGS, CONFLICT_REPLACE, ContentValues().apply { put("k", k); put("v", v) })
            }
        }
    }

    fun close() = helper.close()

    private suspend fun <T> io(table: String, block: () -> T): T = withContext(Dispatchers.IO) {
        block().also { changes.tryEmit(table) }
    }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val NAME = "glook_vault.db"
        private const val VERSION = 1
        private const val T_HISTORY = "history"
        private const val T_BOOKMARK = "bookmark"
        private const val T_KEYS = "api_key"
        private const val T_SETTINGS = "settings"

        init {
            System.loadLibrary("sqlcipher")
        }

        /**
         * @param rawKey 32-byte DEK. SQLCipher raw-key 형식(x'HEX')으로 전달하여 PBKDF2 를 생략합니다
         * (이미 256-bit 무작위 키이므로 KDF 로 얻는 보안 이득이 없고 시작 지연만 늘어남).
         * 전달 후 배열은 0 으로 덮어씁니다.
         */
        fun open(context: Context, rawKey: ByteArray): GlookDatabase {
            val hex = rawKey.joinToString("") { "%02x".format(it) }
            rawKey.fill(0)
            val passphrase = "x'$hex'".toByteArray(Charsets.US_ASCII)
            val factory = SupportOpenHelperFactory(passphrase) // 기본값: 사용 후 passphrase 배열 0 으로 클리어
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(VERSION) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").close()
                        db.query("PRAGMA foreign_keys = ON").close()
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE $T_HISTORY (id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, translated TEXT NOT NULL, src_lang TEXT NOT NULL, tgt_lang TEXT NOT NULL, engine TEXT NOT NULL, created_at INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX idx_history_created ON $T_HISTORY(created_at)")
                        db.execSQL("CREATE TABLE $T_BOOKMARK (id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, translated TEXT NOT NULL, src_lang TEXT NOT NULL, tgt_lang TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(source, translated))")
                        db.execSQL("CREATE TABLE $T_KEYS (provider TEXT PRIMARY KEY, secret TEXT NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE $T_SETTINGS (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
            val helper = factory.create(config)
            helper.writableDatabase // 즉시 열어 키 검증
            return GlookDatabase(helper)
        }
    }
}
