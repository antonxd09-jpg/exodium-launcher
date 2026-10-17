package com.exodium.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private val BG     = Color.rgb(12, 15, 23)
    private val PANEL  = Color.rgb(20, 25, 37)
    private val ACCENT = Color.rgb(151, 174, 255)
    private val TEXT   = Color.rgb(238, 242, 255)
    private val MUTED  = Color.rgb(133, 145, 169)
    private val GREEN  = Color.rgb(100, 220, 140)
    private val RED    = Color.rgb(255, 90, 90)

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val SRC = "/sdcard/komarq_full"
    private val LOG = "/data/local/tmp/exodium-local"

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        root.addView(label("EXODIUM  /  LAUNCHER", 11f, ACCENT, true))
        root.addView(label("Standoff 2 Injector", 26f, TEXT, true).apply { setPadding(0, dp(6), 0, dp(2)) })
        root.addView(label("root · manualmap · ARM64", 12f, MUTED, false).apply { setPadding(0, 0, 0, dp(16)) })

        val statusCard = card()
        statusText = label("○ Ожидание", 14f, MUTED, true)
        statusCard.addView(statusText)

        val startBtn = btn("СТАРТ", ACCENT)
        startBtn.setOnClickListener { startInjector() }
        statusCard.addView(startBtn, lp().apply { topMargin = dp(10) })

        val stopBtn = btn("СТОП", RED)
        stopBtn.setOnClickListener { stopInjector() }
        statusCard.addView(stopBtn, lp().apply { topMargin = dp(6) })
        root.addView(statusCard, lp().apply { bottomMargin = dp(10) })

        val logCard = card()
        logCard.addView(label("Логи", 13f, MUTED, false))
        logText = TextView(this).apply {
            textSize = 10f; setTextColor(TEXT)
            typeface = Typeface.MONOSPACE
            background = rounded(0x11FFFFFF, 6)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            text = "—"
        }
        logCard.addView(logText, lp().apply { topMargin = dp(6) })
        val refreshBtn = btn("ОБНОВИТЬ ЛОГИ", MUTED)
        refreshBtn.setOnClickListener { updateLogs() }
        logCard.addView(refreshBtn, lp().apply { topMargin = dp(8) })
        root.addView(logCard, lp())

        scroll.addView(root)
        setContentView(scroll)
        checkStatus()
    }

    private fun startInjector() {
        if (!File("$SRC/start.sh").exists()) {
            setStatus("❌ Распакуй komarq_full в /sdcard/", RED); return
        }
        setStatus("⏳ Запуск...", MUTED)
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "sh '$SRC/start.sh' 2>&1"))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                handler.post {
                    if (proc.exitValue() == 0) setStatus("● Меню запущено", GREEN)
                    else setStatus("❌ Ошибка (код ${proc.exitValue()})", RED)
                    logText.text = out.takeLast(2000)
                }
            } catch (e: Exception) { handler.post { setStatus("❌ ${e.message}", RED) } }
        }.start()
    }

    private fun stopInjector() {
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "sh '$SRC/stop.sh' 2>&1"))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                handler.post { setStatus("○ Остановлено", MUTED); logText.text = out }
            } catch (e: Exception) { handler.post { setStatus("❌ ${e.message}", RED) } }
        }.start()
    }

    private fun checkStatus() {
        Thread {
            try {
                val ready = File("$LOG/rest.ready")
                val exit  = File("$LOG/rest.exit")
                handler.post {
                    when {
                        ready.exists() -> setStatus("● Меню активно", GREEN)
                        exit.exists()  -> setStatus("❌ Ошибка (код ${exit.readText().trim()})", RED)
                        else           -> setStatus("○ Не запущено", MUTED)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun updateLogs() {
        Thread {
            try {
                val sb = StringBuilder()
                val i = File("$LOG/injector.log")
                val r = File("$LOG/rest.log")
                if (i.exists()) sb.append("── injector.log ──\n${i.readText().takeLast(800)}\n\n")
                if (r.exists()) sb.append("── rest.log ──\n${r.readText().takeLast(800)}")
                handler.post { logText.text = if (sb.isEmpty()) "Логи не найдены" else sb.toString() }
            } catch (e: Exception) { handler.post { logText.text = "Ошибка: ${e.message}" } }
        }.start()
    }

    private fun setStatus(msg: String, color: Int) { statusText.text = msg; statusText.setTextColor(color) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(PANEL, 18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }
    private fun btn(text: String, color: Int) = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (color == ACCENT) Color.rgb(15, 20, 32) else TEXT)
        background = rounded(color, 12)
        setPadding(0, dp(14), 0, dp(14))
    }
    private fun label(v: String, sz: Float, c: Int, bold: Boolean) = TextView(this).apply {
        text = v; textSize = sz; setTextColor(c)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), 0x222F3B55)
    }
    private fun lp() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
