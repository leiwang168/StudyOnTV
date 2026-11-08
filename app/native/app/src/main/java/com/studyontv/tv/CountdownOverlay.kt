package com.studyontv.tv

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * 看电视期间右上角显示的剩余时间角标：按分钟更新，最后 1 分钟保持红色提醒。
 * 纯展示（FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE，不吃遥控器按键、不挡触摸），
 * 由 GateService 的每秒 tick 驱动；答题页自身不显示（它有自己的顶部胶囊）。
 */
class CountdownOverlay(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var attached = false
    private var lastMinutes: Long? = null
    private var lastUrgent: Boolean? = null
    var lastError: String = ""
        private set

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun chip(): TextView = view ?: TextView(context).apply {
        textSize = 15f
        setPadding(dp(14), dp(6), dp(14), dp(6))
        // 部分电视桌面/播放器底色很亮，加一层阴影保证任何画面下都看得清
        setShadowLayer(4f, 0f, 1f, Color.argb(200, 0, 0, 0))
        view = this
    }

    private fun paint(urgent: Boolean) {
        val chip = view ?: return
        // lastUrgent 用 null 表示“还没画过”：否则首次调用（urgent=false）会直接 return，
        // 文字颜色和背板永远不生效，角标会变成默认灰字、无背板，在暗色画面上看不清。
        if (lastUrgent == urgent) return
        lastUrgent = urgent
        chip.setTextColor(if (urgent) Color.rgb(255, 92, 92) else Color.WHITE)
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (urgent) Color.argb(180, 40, 0, 0) else Color.argb(150, 0, 0, 0))
        }
    }

    /** seconds：剩余总秒数（答题余额 + 每日免费时长余额）。<=0 时自动隐藏。 */
    fun update(seconds: Long) {
        if (seconds <= 0) { hide(); return }
        val urgent = seconds <= 60
        val chip = chip()
        val minutes = (seconds + 59) / 60
        if (lastMinutes != minutes) {
            chip.text = "剩余 $minutes 分钟"
            lastMinutes = minutes
        }
        paint(urgent)
        if (!attached) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                GateOverlay.applicationWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.TOP or Gravity.END
            params.x = dp(20); params.y = dp(12)
            params.title = "StudyOnTV countdown"
            try {
                manager.addView(chip, params)
                attached = true
                lastError = ""
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
                return
            }
        }
    }

    fun hide() {
        val chip = view ?: return
        if (attached) try { manager.removeViewImmediate(chip) } catch (_: Exception) { }
        attached = false
    }

    fun destroy() {
        hide()
        view = null
        lastMinutes = null
        lastUrgent = null
    }
}
