package app.glook.translate.core.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * FLAG_SECURE: 스크린샷·화면 녹화 차단 + 최근 앱(Recents) 미리보기를 빈 화면으로 표시.
 * 설정 로드 전에는 안전한 쪽(차단)을 기본값으로 둡니다.
 */
fun Activity.applySecureFlag(requested: Boolean) {
    // -PnoSecure 빌드(app.glook.translate.BuildConfig.ALLOW_SECURE=false)에서는 항상 해제
    val secure = requested && app.glook.translate.BuildConfig.ALLOW_SECURE
    if (secure) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+: Recents 썸네일만 별도로 숨김
        setRecentsScreenshotEnabled(!secure)
    }
}
