package app.glook.translate.core.crash

import app.glook.translate.R
import app.glook.translate.core.str

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.system.exitProcess

/**
 * 크래시 보고: 앱이 죽으면 별도 프로세스(:crash)의 화면에 스택 트레이스를 띄웁니다.
 * USB 디버깅 없이 원인을 확인하기 위한 장치이며, 데이터는 기기 밖으로 전송되지 않습니다(복사·공유는 사용자가 직접).
 * Compose/Hilt 등 앱 코드에 의존하지 않도록 순수 View 로만 그립니다.
 */
object CrashReporter {
    private const val EXTRA_TRACE = "trace"

    fun isCrashProcess(): Boolean = Application.getProcessName()?.endsWith(":crash") == true

    fun install(app: Application) {
        if (isCrashProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = buildString {
                    append("Glook ").append(runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()).append('\n')
                    append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") ")
                        .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append("thread=").append(thread.name).append("\n\n")
                    append(Log.getStackTraceString(error))
                }
                app.startActivity(
                    Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra(EXTRA_TRACE, text.take(60_000)),
                )
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    class CrashActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty()
            val dp = resources.displayMetrics.density
            val pad = (16 * dp).toInt()
            val title = TextView(this).apply { text = str(R.string.crash_title); textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
            val body = TextView(this).apply { text = trace; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
            val copy = Button(this).apply {
                text = str(R.string.copy)
                setOnClickListener {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", trace))
                }
            }
            val share = Button(this).apply {
                text = str(R.string.share)
                setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace), null)) }
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START; addView(copy); addView(share) }
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad)
                addView(title); addView(row); addView(ScrollView(this@CrashActivity).apply { addView(body) })
            }
            setContentView(root)
        }
    }
}
