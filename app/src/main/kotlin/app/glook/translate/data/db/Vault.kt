package app.glook.translate.data.db

import android.content.Context
import app.glook.translate.core.security.DatabaseKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 암호화 DB 의 수명 관리 (지연 오픈, 완전 파기 후 재생성). */
class Vault(private val context: Context, private val keyManager: DatabaseKeyManager) {
    private val mutex = Mutex()
    private var db: GlookDatabase? = null

    /** 파기될 때마다 증가 → 관찰 Flow 들이 새 DB 로 재구독(flatMapLatest). */
    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch

    suspend fun get(): GlookDatabase = mutex.withLock {
        db ?: withContext(Dispatchers.IO) {
            val result = keyManager.obtainDatabaseKey()
            if (result.createdNew) {
                // 새 키가 만들어졌다면 남아 있는 옛 DB 파일은 어차피 복호화 불가 → 삭제
                context.deleteDatabase(GlookDatabase.NAME)
            }
            GlookDatabase.open(context, result.key).also { db = it }
        }
    }

    /** DB 파일 삭제 + Keystore KEK 삭제 + wrapped DEK 삭제 (crypto-shredding). */
    suspend fun wipe() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                db?.close()
                db = null
                context.deleteDatabase(GlookDatabase.NAME)
                keyManager.destroy()
            }
        }
        _epoch.value += 1
    }

    fun securityLevel() = keyManager.securityLevel()
}
