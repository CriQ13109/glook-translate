package app.glook.translate.core.security

import app.glook.translate.R
import app.glook.translate.core.str

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

private val Context.wrappedKeyStore: DataStore<Preferences> by preferencesDataStore(name = "glook_wrapped_db_key")

/** 키가 어느 보안 영역에 있는지 (설정 화면 표시용). */
enum class KeySecurityLevel(@androidx.annotation.StringRes private val labelRes: Int) {
    STRONGBOX(R.string.sec_strongbox),
    TEE(R.string.sec_tee),
    SOFTWARE(R.string.sec_software),
    UNKNOWN(R.string.sec_unknown);
    val label: String get() = str(labelRes)
}

/**
 * SQLCipher DB 패스프레이즈 관리 (envelope encryption).
 *
 * 1. 최초 실행: SecureRandom 으로 256-bit DEK(데이터 암호화 키) 생성
 * 2. Android Keystore 에 비추출(non-exportable) AES-256-GCM KEK 생성 — StrongBox 가능 시 StrongBox, 아니면 TEE
 * 3. DEK 를 KEK 로 GCM 암호화한 "wrapped DEK + IV" 만 DataStore 에 저장 (평문 DEK 는 디스크에 절대 기록하지 않음)
 *
 * 요구사항의 "EncryptedDataStore" 는 공식 AndroidX 클래스가 아니며, androidx.security:security-crypto 는
 * 1.1.0 에서 전체 API 가 deprecated 되었습니다. 따라서 공식 권장안대로 javax.crypto + AndroidKeyStore 를 직접
 * 사용하고, DataStore 에는 이미 Keystore 로 감싼 암호문만 넣는 방식으로 동일한 목적을 달성합니다.
 *
 * 한계(정직한 고지): Keystore 는 키 "추출"을 막을 뿐, 루팅되어 앱 프로세스 권한을 얻은 공격자가 앱이 실행 중일 때
 * Keystore 에 복호화를 "요청"하는 것까지 막지는 못합니다. ADB 백업·파일 복사만으로 DB 를 다른 기기에서 여는 것은 불가능합니다.
 */
class DatabaseKeyManager(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** 반환된 배열은 호출자가 사용 후 즉시 0 으로 덮어써야 합니다. */
    suspend fun obtainDatabaseKey(): ObtainResult {
        val prefs = context.wrappedKeyStore.data.first()
        val wrapped = prefs[KEY_WRAPPED]
        val iv = prefs[KEY_IV]
        val kek = keyStore.getKey(KEK_ALIAS, null) as? SecretKey

        if (wrapped != null && iv != null && kek != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, b64d(iv)))
            return ObtainResult(cipher.doFinal(b64d(wrapped)), createdNew = false)
        }

        // wrapped DEK 는 있는데 KEK 가 사라졌다면(Keystore 초기화, 잠금화면 제거 등) 기존 DB 는 영구히 복호화 불가.
        val lostPrevious = wrapped != null
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val newKek = kek ?: generateKek()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, newKek) // IV 는 Keystore 가 무작위 생성 (randomizedEncryptionRequired)
        val encrypted = cipher.doFinal(dek)
        context.wrappedKeyStore.edit {
            it[KEY_WRAPPED] = b64e(encrypted)
            it[KEY_IV] = b64e(cipher.iv)
        }
        return ObtainResult(dek, createdNew = true, previousKeyLost = lostPrevious)
    }

    private fun generateKek(): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            // 기기가 잠겨 있는 동안에는 KEK 사용 불가 (API 28+)
            .setUnlockedDeviceRequired(true)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            generator.init(spec(strongBox = true))
            generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(spec(strongBox = false))
            generator.generateKey()
        } catch (_: Exception) {
            // 일부 기기는 StrongBox 에서 AES-256 을 지원하지 않아 다른 예외를 던짐
            generator.init(spec(strongBox = false))
            generator.generateKey()
        }
    }

    fun securityLevel(): KeySecurityLevel {
        val key = keyStore.getKey(KEK_ALIAS, null) as? SecretKey ?: return KeySecurityLevel.UNKNOWN
        return try {
            val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                    else -> KeySecurityLevel.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) KeySecurityLevel.TEE else KeySecurityLevel.SOFTWARE
            }
        } catch (_: Exception) {
            KeySecurityLevel.UNKNOWN
        }
    }

    /** KEK 삭제 + wrapped DEK 삭제 → 기존 DB 는 암호학적으로 복구 불가 (crypto-shredding). */
    suspend fun destroy() {
        runCatching { keyStore.deleteEntry(KEK_ALIAS) }
        context.wrappedKeyStore.edit { it.clear() }
    }

    data class ObtainResult(val key: ByteArray, val createdNew: Boolean, val previousKeyLost: Boolean = false)

    private fun b64e(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEK_ALIAS = "glook_db_kek_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        val KEY_WRAPPED = stringPreferencesKey("wrapped_dek")
        val KEY_IV = stringPreferencesKey("wrapped_dek_iv")
    }
}
