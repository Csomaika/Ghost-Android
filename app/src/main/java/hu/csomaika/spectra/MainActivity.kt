package hu.csomaika.spectra

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
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
