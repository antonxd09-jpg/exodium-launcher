package com.exodium.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
    private lateinit var startBtn: TextView
    private lateinit var pathText: EditText
    private val handler = Handler(Looper.getMainLooper())

    private val SEARCH_PATHS = listOf(
        "/sdcard/komarq_full",
        "/sdcard/Download/komarq_full",
        "/sdcard/Documents/komarq_full",
        "/data/local/tmp/komarq_full",
        "/sdcard/DCIM/komarq_full"
    )
    private val LOG_PATH = "/data/local/tmp/exodium-local"

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        root.addView(label("EXODIUM  /  LAUNCHER", 11f, ACCENT, true))
        root.addView(label("Standoff 2 Injector", 26f, TEXT, true).apply { setPadding(0, dp(6), 0, dp(2)) })
        root.addView(label("root · manualmap · ARM64", 12f, MUTED, false).apply { setPadding(0, 0, 0, dp(16)) })

        // path card
        val pathCard = card()
        pathCard.addView(label("Путь к komarq_full", 13f, MUTED, false))
        pathText = EditText(this).apply {
            textSize = 13f; setTextColor(TEXT)
            setHintTextColor(MUTED); hint = "/sdcard/komarq_full"
            background = rounded(0x22FFFFFF, 8)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setText(findPath())
        }
        pathCard.addView(pathText, lp().apply { topMargin = dp(8) })

        val findBtn = btn("НАЙТИ АВТОМАТИЧЕСКИ", MUTED)
        findBtn.setOnClickListener {
            val found = findPath()
            if (found.isNotEmpty()) { pathText.setText(found); setStatus("Найдено: $found", GREEN) }
            else setStatus("Папка не найдена. Укажи путь вручную.", RED)
        }
        pathCard.addView(findBtn, lp().apply { topMargin = dp(8) })
        root.addView(pathCard, lp().apply { bottomMargin = dp(10) })

        // status card
        val statusCard = card()
        statusText = label("○ Ожидание", 14f, MUTED, true)
        statusCard.addView(statusText)
        startBtn = btn("СТАРТ", ACCENT)
        startBtn.setOnClickListener { startInjector() }
        statusCard.addView(startBtn, lp().apply { topMargin = dp(10) })

        val stopBtn = btn("СТОП", RED)
        stopBtn.setOnClickListener { stopInjector() }
        statusCard.addView(stopBtn, lp().apply { topMargin = dp(6) })
        root.addView(statusCard, lp().apply { bottomMargin = dp(10) })

        // log card
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

    private fun findPath(): String =
        SEARCH_PATHS.firstOrNull { File(it).isDirectory && File("$it/start.sh").exists() } ?: ""

    private fun startInjector() {
        val src = pathText.text.toString().trim()
        if (src.isEmpty() || !File("$src/start.sh").exists()) {
            setStatus("❌ start.sh не найден в: $src", RED); return
        }
        setStatus("⏳ Запуск...", MUTED)
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c",
                    "sh '$src/start.sh' 2>&1"))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                val rc = proc.exitValue()
                handler.post {
                    if (rc == 0) setStatus("● Меню запущено", GREEN)
                    else setStatus("❌ Ошибка (код $rc)", RED)
                    logText.text = out.takeLast(2000)
                }
            } catch (e: Exception) {
                handler.post { setStatus("❌ ${e.message}", RED) }
            }
        }.start()
    }

    private fun stopInjector() {
        val src = pathText.text.toString().trim()
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c",
                    "sh '$src/stop.sh' 2>&1"))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                handler.post { setStatus("○ Остановлено", MUTED); logText.text = out }
            } catch (e: Exception) {
                handler.post { setStatus("❌ ${e.message}", RED) }
            }
        }.start()
    }

    private fun checkStatus() {
        Thread {
            try {
                val ready = File("$LOG_PATH/rest.ready")
                val exit  = File("$LOG_PATH/rest.exit")
                handler.post {
                    when {
                        ready.exists() -> setStatus("● Меню активно", GREEN)
                        exit.exists()  -> setStatus("❌ rest.sh упал (код ${exit.readText().trim()})", RED)
                        else           -> setStatus("○ Не запущено", MUTED)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun updateLogs() {
        Thread {
            try {
                val injLog  = File("$LOG_PATH/injector.log")
                val restLog = File("$LOG_PATH/rest.log")
                val sb = StringBuilder()
                if (injLog.exists()) sb.append("── injector.log ──\n${injLog.readText().takeLast(800)}\n\n")
                if (restLog.exists()) sb.append("── rest.log ──\n${restLog.readText().takeLast(800)}")
                handler.post { logText.text = if (sb.isEmpty()) "Логи не найдены" else sb.toString() }
            } catch (e: Exception) {
                handler.post { logText.text = "Ошибка чтения логов: ${e.message}" }
            }
        }.start()
    }

    private fun setStatus(msg: String, color: Int) {
        statusText.text = msg; statusText.setTextColor(color)
    }

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
        setColor(color); cornerRadius = dp(r).toFloat()
        setStroke(dp(1), 0x222F3B55)
    }

    private fun lp() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
