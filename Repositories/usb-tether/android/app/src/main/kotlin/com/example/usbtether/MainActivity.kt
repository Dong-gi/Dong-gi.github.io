package com.example.usbtether

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var toggleButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        toggleButton = findViewById(R.id.toggleButton)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        toggleButton.setOnClickListener {
            val intent = Intent(this, TetherService::class.java)
            if (TetherService.isRunning) {
                stopService(intent)
            } else {
                ContextCompat.startForegroundService(this, intent)
            }
            handler.postDelayed({ updateUi() }, 200)
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }

    private fun updateUi() {
        val running = TetherService.isRunning
        statusText.text = if (running) getString(R.string.status_running) else getString(R.string.status_stopped)
        toggleButton.text = if (running) getString(R.string.stop) else getString(R.string.start)
        statsText.text = buildString {
            append("Active TCP: ").append(TetherService.tcpCount).append('\n')
            append("Bytes in:   ").append(formatBytes(TetherService.bytesIn.get())).append('\n')
            append("Bytes out:  ").append(formatBytes(TetherService.bytesOut.get()))
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1_024L               -> "$bytes B"
        bytes < 1_024L * 1_024       -> "%.1f KB".format(bytes / 1_024.0)
        bytes < 1_024L * 1_024 * 1_024 -> "%.1f MB".format(bytes / (1_024.0 * 1_024))
        else                         -> "%.2f GB".format(bytes / (1_024.0 * 1_024 * 1_024))
    }
}
