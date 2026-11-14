package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WallpaperActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var urlBox: EditText
    private val p by lazy { getSharedPreferences("p", MODE_PRIVATE) }

    private fun sources(): MutableList<String> =
        (p.getString("sources", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()

    private fun saveSources(l: List<String>) = p.edit().putString("sources", l.joinToString("\n")).apply()

    private fun norm(u: String): String {
        val t = u.trim()
        return if (t.contains("://")) t else "https://$t"
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101522"))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlBox = EditText(this).apply {
            hint = "Адрес сайта или картинки"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> web.loadUrl(norm(text.toString())); true }
        }
        bar.addView(urlBox, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply { text = "Перейти"; setOnClickListener { web.loadUrl(norm(urlBox.text.toString())) } })
        bar.addView(Button(this).apply { text = "Источники"; setOnClickListener { showSources() } })
        bar.addView(Button(this).apply {
            text = "Эту картинку — в обои"
            setOnClickListener { web.url?.let { confirmSet(it) } }
        })
        col.addView(bar)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorage = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
            override fun onPageFinished(v: WebView, url: String?) { urlBox.setText(url) }
        }
        // Долгое нажатие OK на картинке в браузере = поставить обоями
        web.setOnLongClickListener {
            val r = web.hitTestResult
            if (r.type == WebView.HitTestResult.IMAGE_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                r.extra?.let { confirmSet(it) }
                true
            } else false
        }
        col.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(col)

        sources().firstOrNull()?.let { web.loadUrl(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSources(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun showSources() {
        val list = sources()
        val items = (list + listOf("＋ Добавить текущую страницу", "✕ Удалить источник…")).toTypedArray()
        AlertDialog.Builder(this).setTitle("Источники обоев").setItems(items) { _, i ->
            when {
                i < list.size -> web.loadUrl(list[i])
                i == list.size -> web.url?.let { list.add(it); saveSources(list) }
                else -> AlertDialog.Builder(this).setTitle("Удалить")
                    .setItems(list.toTypedArray()) { _, j -> list.removeAt(j); saveSources(list) }.show()
            }
        }.show()
    }

    private fun confirmSet(url: String) {
        AlertDialog.Builder(this).setMessage("Поставить обоями?")
            .setPositiveButton("Да") { _, _ -> download(url) }
            .setNegativeButton("Нет", null).show()
    }

    private fun download(u: String) {
        if (!u.startsWith("http")) { Toast.makeText(this, "Не поддерживается", Toast.LENGTH_SHORT).show(); return }
        val ua = web.settings.userAgentString
        val ref = web.url
        Toast.makeText(this, "Загрузка…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", ua)
                if (ref != null) c.setRequestProperty("Referer", ref)
                c.connectTimeout = 15000
                c.readTimeout = 30000
                val bytes = c.inputStream.use { it.readBytes() }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var s = 1
                while (o.outWidth / s > 2400) s *= 2
                val bmp: Bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }
                ) ?: throw Exception("не картинка")
                FileOutputStream(File(filesDir, "wallpaper.jpg")).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                runOnUiThread { Toast.makeText(this, "Обои установлены", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
