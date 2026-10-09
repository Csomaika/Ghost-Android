package hu.csomaika.spectra

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread
import android.graphics.Color
import android.view.Gravity
import kotlin.math.sqrt
import java.util.Locale

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var status: TextView
    private lateinit var vector: TextView
    private lateinit var magnitude: TextView
    private var count = 0
    private var mean = 0.0
    private var m2 = 0.0
    private var last = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 70, 42, 24)
            setBackgroundColor(Color.rgb(10, 18, 24))
        }
        fun line(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.rgb(90, 230, 187))
            setPadding(0, 12, 0, 12)
            panel.addView(this)
        }
        line("SPECTRA  /  FIELD MONITOR", 22f)
        line("Live magnetic-field measurement", 15f)
        magnitude = line("Waiting for sensor…", 30f)
        vector = line("X —   Y —   Z —", 18f)
        status = line("Initializing", 16f)
        line("Anomalies are not evidence of paranormal activity. Nearby magnets, electronics and phone movement can affect measurements.", 13f)
        val update = Button(this).apply {
            text = "CHECK FOR UPDATES"
            setOnClickListener { checkUpdates(this) }
        }
        panel.addView(update)
        setContentView(panel)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            status.text = "Magnetometer unavailable on this device."
        } else {
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            status.text = "Sensor: ${sensor.name}"
        }
    }

    private fun checkUpdates(button: Button) {
        button.isEnabled = false
        button.text = "CHECKING..."
        thread {
            try {
                val connection = URL("https://api.github.com/repos/Csomaika/Ghost-Android/releases/latest").openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "SPECTRA-Android")
                val release = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
                val tag = release.getString("tag_name")
                val latest = tag.removePrefix("v")
                val installed = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                val newer = compareVersion(latest, installed) > 0
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "CHECK FOR UPDATES"
                    val dialog = android.app.AlertDialog.Builder(this)
                    if (newer) {
                        dialog.setTitle("Update available: " + tag)
                            .setMessage("Installed: " + installed + ". Open the official GitHub release to download the APK?")
                            .setPositiveButton("OPEN RELEASE") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.getString("html_url"))))
                            }
                            .setNegativeButton("CANCEL", null)
                    } else {
                        dialog.setMessage("You have the latest version (" + installed + ").")
                            .setPositiveButton("OK", null)
                    }
                    dialog.show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "CHECK FOR UPDATES"
                    android.app.AlertDialog.Builder(this)
                        .setMessage("Cannot check releases: " + (e.message ?: "network error"))
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 100) return
        last = now
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val total = sqrt(x*x + y*y + z*z)
        count++
        val delta = total - mean
        mean += delta / count
        m2 += delta * (total - mean)
        val sd = if (count > 1) sqrt(m2 / (count - 1)) else 0.0
        magnitude.text = String.format(Locale.US, "%.2f µT", total)
        vector.text = String.format(Locale.US, "X %.2f   Y %.2f   Z %.2f µT", x, y, z)
        status.text = String.format(Locale.US, "Samples: %d  |  Mean: %.2f µT  |  SD: %.2f µT\nAccuracy status: %d", count, mean, sd, event.accuracy)
    }
}
