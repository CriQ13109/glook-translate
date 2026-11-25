package app.glook.translate.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * 어디서든(Composable·ViewModel·엔진) 현재 앱 언어로 문자열을 얻는 헬퍼.
 * Application 컨텍스트의 리소스는 시스템/앱별 언어 변경 시 갱신되며, 언어가 바뀌면 액티비티가 재생성됩니다.
 */
object AppStrings {
    @Volatile private var ctx: Context? = null
    fun init(context: Context) { ctx = context.applicationContext ?: context }
    fun get(@StringRes id: Int, vararg args: Any): String {
        val c = checkNotNull(ctx) { "AppStrings.init() 가 호출되지 않았습니다" }
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}

fun str(@StringRes id: Int, vararg args: Any): String = AppStrings.get(id, *args)
