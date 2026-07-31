package com.example.usbtether

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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
    private lateinit var hotspotButton: Button
    private lateinit var ssidInput: EditText
    private lateinit var passphraseInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val hotspotPrefs by lazy { HotspotPreferences(this) }

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
        hotspotButton = findViewById(R.id.hotspotButton)
        ssidInput = findViewById(R.id.ssidInput)
        passphraseInput = findViewById(R.id.passphraseInput)

        ssidInput.setText(hotspotPrefs.ssid())
        passphraseInput.setText(hotspotPrefs.passphraseOrCreate())

        requestPermissionsIfNeeded()

        toggleButton.setOnClickListener {
            if (TetherService.isRunning) {
                stopService(Intent(this, TetherService::class.java))
            } else {
                ContextCompat.startForegroundService(this, Intent(this, TetherService::class.java))
            }
            handler.postDelayed({ updateUi() }, 200)
        }

        hotspotButton.setOnClickListener {
            if (TetherService.hotspotActive) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, TetherService::class.java).apply {
                        action = TetherService.ACTION_HOTSPOT_OFF
                    },
                )
                handler.postDelayed({ updateUi() }, 200)
            } else {
                startHotspotFromCurrentInputs()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    private fun startHotspotFromCurrentInputs() {
        if (TetherService.hotspotActive) return
        val ssid = ssidInput.text.toString().trim()
        val pass = passphraseInput.text.toString()
        hotspotPrefs.save(ssid, pass)
        ContextCompat.startForegroundService(
            this,
            Intent(this, TetherService::class.java).apply {
                action = TetherService.ACTION_HOTSPOT_ON
                putExtra(TetherService.EXTRA_SSID, ssid)
                putExtra(TetherService.EXTRA_PASSPHRASE, pass)
            },
        )
        handler.postDelayed({ updateUi() }, 200)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed += android.Manifest.permission.POST_NOTIFICATIONS
            }
            if (checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                needed += android.Manifest.permission.NEARBY_WIFI_DEVICES
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed += android.Manifest.permission.ACCESS_FINE_LOCATION
            }
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
    }

    private fun updateUi() {
        val running = TetherService.isRunning
        val hotspotOn = TetherService.hotspotActive
        // 서비스가 살아 있다는 것과 프록시가 실제로 듣고 있다는 것은 다르다.
        // 포트가 하나도 잡히지 않았는데 "Running" 만 보여주면 사용자는 왜 접속이
        // 안 되는지 알 수 없다. 아래 proxyError 줄과 짝을 이룬다.
        val anyProxyUp = TetherService.socksPort > 0 || TetherService.httpPort > 0
        statusText.text = when {
            !running -> getString(R.string.status_stopped)
            anyProxyUp -> getString(R.string.status_running)
            else -> getString(R.string.status_no_proxy)
        }
        toggleButton.text = if (running) getString(R.string.stop) else getString(R.string.start)
        hotspotButton.text = if (hotspotOn) getString(R.string.hotspot_stop) else getString(R.string.hotspot_start)
        ssidInput.isEnabled = !hotspotOn
        passphraseInput.isEnabled = !hotspotOn

        statsText.text = buildString {
            val hotspotLine = when {
                hotspotOn -> "Hotspot: ${TetherService.hotspotSsid ?: "?"} @ 192.168.49.1"
                TetherService.hotspotError != null -> "Hotspot: ${TetherService.hotspotError}"
                else -> "Hotspot: off"
            }
            append(hotspotLine).append('\n')
            TetherService.proxyError?.let { append("⚠ ").append(it).append('\n') }
            append("SOCKS5 port: ").append(portLabel(TetherService.socksPort)).append('\n')
            append("HTTP port:   ").append(portLabel(TetherService.httpPort)).append('\n')
            append("Bytes in:    ").append(formatBytes(TetherService.bytesIn.get())).append('\n')
            append("Bytes out:   ").append(formatBytes(TetherService.bytesOut.get()))
        }
    }

    private fun portLabel(port: Int): String = if (port > 0) port.toString() else "—"

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1_024L               -> "$bytes B"
        bytes < 1_024L * 1_024       -> "%.1f KB".format(bytes / 1_024.0)
        bytes < 1_024L * 1_024 * 1_024 -> "%.1f MB".format(bytes / (1_024.0 * 1_024))
        else                         -> "%.2f GB".format(bytes / (1_024.0 * 1_024 * 1_024))
    }
}
