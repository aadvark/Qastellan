package com.kithandkeen.qastellan.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kithandkeen.qastellan.BuildConfig
import com.kithandkeen.qastellan.Qastellan
import com.kithandkeen.qastellan.device.DeviceScanner
import com.kithandkeen.qastellan.device.ProtectionService
import com.kithandkeen.qastellan.device.StorageAccess
import com.kithandkeen.qastellan.engine.Finding
import com.kithandkeen.qastellan.update.UpdateWork
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Iteration 1 test screen: proves the engine, updates and real-time protection
 * work on a real phone. The designed screens replace it in iteration 2.
 */
class MainActivity : Activity() {
    private val background = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var results: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 15f }
        results = TextView(this).apply { textSize = 14f; setPadding(0, pad, 0, 0) }
        column.addView(TextView(this).apply { text = "Qastellan ${BuildConfig.VERSION_NAME} (engine test build)"; textSize = 20f })
        column.addView(status)
        column.addView(button("Scan now") { runScan() })
        column.addView(button("Check for updates") { UpdateWork.checkOnStartup(this); refreshStatus() })
        column.addView(button("Turn on real-time protection") { ProtectionService.start(this) })
        column.addView(button("Allow all files access") { StorageAccess.requestIntent(this)?.let(::startActivity) })
        column.addView(results)
        setContentView(ScrollView(this).apply { addView(column) })

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        background.shutdown()
        super.onDestroy()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun refreshStatus() {
        val app = Qastellan.from(this)
        val checked = app.prefs.lastDefinitionsCheck.takeIf { it > 0 }
            ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "never"
        val update = app.prefs.appUpdateVersion?.let { "\nApp update available: $it" } ?: ""
        status.text = buildString {
            append("Definitions: ${app.definitions.version} (${app.definitions.signatureCount} signatures)\n")
            append("Last checked: $checked\n")
            append("All files access: ${if (StorageAccess.canReadAllFiles(this@MainActivity)) "on" else "off"}")
            append(update)
        }
        app.prefs.appUpdateUrl?.let { url ->
            status.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    private fun runScan() {
        results.text = "Scanning…"
        background.execute {
            val started = System.currentTimeMillis()
            val findings = DeviceScanner(this).scan { p ->
                if (p.checked % 25 == 0) runOnUiThread { results.text = "${p.stage}: ${p.checked} checked\n${p.current}" }
            }
            val seconds = (System.currentTimeMillis() - started) / 1000
            val text = if (findings.isEmpty()) {
                "No threats found ($seconds s)."
            } else {
                findings.joinToString("\n\n", prefix = "${findings.size} found ($seconds s):\n\n") { f ->
                    when (f) {
                        is Finding.KnownThreat -> "${f.title}\n  ${f.detection}"
                        is Finding.RiskyApp -> "${f.title} (risk ${f.report.score})\n  " + f.report.reasons.joinToString("\n  ")
                    }
                }
            }
            runOnUiThread { results.text = text }
        }
    }
}
