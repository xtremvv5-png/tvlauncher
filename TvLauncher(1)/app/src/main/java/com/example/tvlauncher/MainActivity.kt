package com.example.tvlauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var bg: ImageView
    private lateinit var clock: TextView
    private lateinit var dateView: TextView
    private lateinit var row: LinearLayout
    private lateinit var scroller: HorizontalScrollView
    private val h = Handler(Looper.getMainLooper())
    private var lastCount = -1

    private val tick = object : Runnable {
        override fun run() {
            val d = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
            dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d)
            h.postDelayed(this, 15000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0B1020"))

        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))

        val shade = View(this)
        shade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x66000000, 0x00000000, 0xCC000000.toInt())
        )
        root.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))

        // Большие часы. Долгое нажатие OK на часах — скрытые настройки (PIN).
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextView(this).apply {
            textSize = 96f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setOnLongClickListener { openHidden(); true }
            setOnFocusChangeListener { v, f -> v.alpha = if (f) 0.75f else 1f }
        }
        dateView = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.parseColor("#CCFFFFFF"))
        }
        top.addView(clock)
        top.addView(dateView)
        root.addView(top, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(60), dp(40), 0, 0)
        })

        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(48), 0, dp(48), 0)
        }
        scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row, ViewGroup.LayoutParams(WRAP, WRAP))
        }
        root.addView(scroller, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            bottomMargin = dp(30)
        })

        setContentView(root)

        if (getSharedPreferences("p", MODE_PRIVATE).getBoolean("dns", false)) Dns.set(this, true)
    }

    override fun onResume() {
        super.onResume()
        loadWallpaper()
        loadApps()
        h.removeCallbacks(tick)
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroller.smoothScrollTo(0, 0)
        if (row.childCount > 0) row.getChildAt(0).requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* лаунчер: Назад ничего не делает */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { openHidden(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun openHidden() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun loadWallpaper() {
        val f = File(filesDir, "wallpaper.jpg")
        if (f.exists()) bg.setImageBitmap(BitmapFactory.decodeFile(f.path))
        else bg.setImageDrawable(
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1B2A5E"), Color.parseColor("#0B1020"))
            )
        )
    }

    private fun queryApps(): List<ResolveInfo> {
        val pm = packageManager
        val a = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val b = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return (a + b)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    private fun loadApps() {
        val apps = queryApps()
        if (apps.size == lastCount) return
        lastCount = apps.size
        row.removeAllViews()
        val pm = packageManager
        for (ri in apps) {
            val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
            addTile(ri.loadLabel(pm).toString(), ri.loadIcon(pm)) {
                startActivity(Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        addTile("Настройки", getDrawable(android.R.drawable.ic_menu_manage)) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (row.childCount > 0) row.getChildAt(0).requestFocus()
    }

    private fun tileBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setColor(if (focused) Color.parseColor("#66FFFFFF") else Color.parseColor("#44000000"))
        if (focused) setStroke(dp(3), Color.WHITE)
    }

    private fun addTile(label: String, icon: Drawable?, onClick: () -> Unit) {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(22), dp(16), dp(18))
            isFocusable = true
            isClickable = true
            background = tileBg(false)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = tileBg(f)
                val s = if (f) 1.12f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }
        t.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(150), dp(150)))
        t.addView(TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(200), WRAP).apply { topMargin = dp(12) })
        row.addView(t, LinearLayout.LayoutParams(dp(230), WRAP).apply {
            setMargins(dp(12), dp(24), dp(12), dp(24))
        })
    }
}
