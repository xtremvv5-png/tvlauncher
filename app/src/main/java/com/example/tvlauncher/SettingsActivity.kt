package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

class SettingsActivity : Activity() {
    private val p by lazy { getSharedPreferences("p", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#101522")))
        askPin()
    }

    private fun pinField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = "PIN"
    }

    private fun askPin() {
        val et = pinField()
        AlertDialog.Builder(this).setTitle("Доступ").setView(et).setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (et.text.toString() == p.getString("pin", "1234")) showMenu() else finish()
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .show()
    }

    private fun dnsLabel() = "Защита DNS (AdGuard): " + if (p.getBoolean("dns", false)) "ВКЛ" else "ВЫКЛ"

    private fun showMenu() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(200, 40, 200, 40)
        }
        fun btn(text: String, click: (Button) -> Unit): Button {
            val bt = Button(this).apply {
                this.text = text
                textSize = 22f
                setOnClickListener { click(this) }
            }
            col.addView(bt, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 12) })
            return bt
        }

        // Выглядит как обычный пункт «Оформление», внутри — встроенный браузер с обоями
        btn("Оформление") { startActivity(Intent(this, WallpaperActivity::class.java)) }

        btn(dnsLabel()) { bt ->
            val want = !p.getBoolean("dns", false)
            if (Dns.set(this, want)) {
                p.edit().putBoolean("dns", want).apply()
                bt.text = dnsLabel()
            } else {
                AlertDialog.Builder(this).setTitle("Нужно разрешение")
                    .setMessage("Один раз выполни на компьютере (ADB по сети):\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\nПотом нажми кнопку снова.")
                    .setPositiveButton("OK", null).show()
            }
        }

        btn("Сменить PIN") {
            val et = pinField()
            AlertDialog.Builder(this).setTitle("Новый PIN (от 4 цифр)").setView(et)
                .setPositiveButton("OK") { _, _ ->
                    val v = et.text.toString()
                    if (v.length >= 4) p.edit().putString("pin", v).apply()
                }
                .setNegativeButton("Отмена", null).show()
        }

        btn("Системные настройки") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        btn("Назад") { finish() }
        setContentView(col)
    }
}
