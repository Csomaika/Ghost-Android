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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.MotionEvent
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max
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
    private val samples = ArrayDeque<Float>()
    private val log = ArrayDeque<String>()
    private var baseline = 0.0
    private var m2cal = 0.0
    private var calCount = 0
    private var calStart = 0L
    private var calibrated = false
    private var moving = 0f
    private var lastEvent = 0L
    private lateinit var dashboard: Instrument

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
        dashboard = Instrument()
        setContentView(dashboard)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            status.text = "Magnetometer unavailable on this device."
        } else {
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) }
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

    private inner class Instrument : View(this) {
        private val paint=Paint(3)
        private val cyan=android.graphics.Color.rgb(54,232,210)
        private val white=android.graphics.Color.rgb(231,243,250)
        private val muted=android.graphics.Color.rgb(113,139,158)
        private val navy=android.graphics.Color.rgb(15,28,43)
        private val touch=ArrayList<Pair<RectF,()->Unit>>()
        private val start=SystemClock.elapsedRealtime()
        private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int) {
            paint.color=color;paint.style=Paint.Style.FILL;paint.textSize=size
            c.drawText(s,x,y,paint)
        }
        private fun box(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int) {
            paint.color=color;paint.style=Paint.Style.FILL;c.drawRoundRect(x,y,x+w,y+h,17f,17f,paint)
        }
        private fun button(c:Canvas,s:String,x:Float,y:Float,w:Float,run:()->Unit) {
            box(c,x,y,w,48f,navy);text(c,s,x+12,y+30,14f,cyan)
            touch.add(RectF(x,y,x+w,y+48) to run)
        }
        override fun onDraw(c:Canvas) {
            c.drawColor(android.graphics.Color.rgb(7,13,23))
            c.save();c.scale(width/390f,width/390f)
            touch.clear()
            text(c,"SPECTRA",18f,42f,30f,cyan)
            text(c,"FIELD MONITOR  /  LIVE SENSOR DATA",18f,62f,11f,muted)
            box(c,15f,78f,360f,190f,navy)
            text(c,"MAGNETIC FIELD",29f,105f,13f,muted)
            text(c,String.format(Locale.US,"%.2f µT",if(samples.isEmpty())0f else samples.last()),29f,170f,46f,white)
            text(c,if(calibrated)"BASELINE ACTIVE" else if(calStart>0)"CALIBRATING..." else "NOT CALIBRATED",29f,205f,14f,cyan)
            text(c,"Samples: "+count+"  |  Accuracy: "+accuracyLabel(),29f,238f,12f,muted)
            val seconds=(SystemClock.elapsedRealtime()-start)/1000f
            for(i in 0..3) {
                paint.color=android.graphics.Color.argb(65,54,232,210)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
                c.drawCircle(320f,216f,11f+i*11f,paint)
            }
            paint.style=Paint.Style.FILL;paint.color=cyan
            c.drawCircle(320f+kotlin.math.cos(seconds)*28f,216f+kotlin.math.sin(seconds)*28f,4f,paint)
            box(c,15f,280f,360f,174f,navy)
            text(c,"LIVE MAGNETIC SIGNAL",28f,308f,13f,muted)
            if(samples.size>1) {
                val lo=(samples.minOrNull()?:0f)-2
                val hi=(samples.maxOrNull()?:1f)+2
                val p=Path()
                samples.forEachIndexed { i,v ->
                    val x=29+i*330f/max(1,samples.size-1)
                    val y=430-(v-lo)/max(1f,hi-lo)*105f
                    if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
                }
                paint.color=cyan;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.5f
                c.drawPath(p,paint);paint.style=Paint.Style.FILL
            }
            text(c,"MAGNETOMETER AXES  (µT)",19f,482f,12f,muted)
            for(i in 0..2) {
                val x=15f+i*124f
                box(c,x,495f,112f,72f,navy)
                text(c,arrayOf("X","Y","Z")[i],x+12,519f,14f,cyan)
                text(c,String.format(Locale.US,"%.1f",fieldValue(i)),x+12,548f,22f,white)
            }
            box(c,15f,580f,360f,65f,navy)
            text(c,"MOTION",29f,606f,12f,muted)
            text(c,String.format(Locale.US,"%.2f m/s²",moving),29f,629f,19f,if(moving>0.8f)android.graphics.Color.YELLOW else cyan)
            text(c,if(moving>0.8f)"MOVING" else "STABLE",276f,619f,13f,cyan)
            button(c,if(calStart>0&&!calibrated)"CALIBRATING..." else "CALIBRATE 30s",15f,665f,173f) {
                calStart=SystemClock.elapsedRealtime();calibrated=false;calCount=0;baseline=0.0;m2cal=0.0
            }
            button(c,"EVENT LOG ("+log.size+")",201f,665f,174f) {
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("ANOMALY LOG")
                    .setMessage(if(log.isEmpty())"No events recorded." else log.joinToString("\\n"))
                    .setPositiveButton("OK",null).show()
            }
            button(c,"CHECK UPDATES",15f,726f,360f) { checkForUpdatesProxy() }
            text(c,"Sensor changes do not prove paranormal activity.",20f,798f,11f,muted)
            c.restore()
            postInvalidateDelayed(60)
        }
        private fun accuracyLabel()=if(sensorAccuracy==3)"HIGH" else "CHECK"
        private fun fieldValue(i:Int):Float= when(i){0->lastX;1->lastY;else->lastZ}
        override fun onTouchEvent(e:MotionEvent):Boolean {
            if(e.action==MotionEvent.ACTION_UP) {
                val x=e.x/(width/390f);val y=e.y/(width/390f)
                touch.firstOrNull{it.first.contains(x,y)}?.second?.invoke()
                return true
            }
            return true
        }
    }
    private var lastX=0f
    private var lastY=0f
    private var lastZ=0f
    private var sensorAccuracy=0
    private fun checkForUpdatesProxy() {
        val b=Button(this)
        checkForUpdates(b)
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            moving = kotlin.math.abs(kotlin.math.sqrt(event.values.sumOf { (it*it).toDouble() })-9.81).toFloat()
            return
        }
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 100) return
        last = now
        lastX=event.values[0];lastY=event.values[1];lastZ=event.values[2]
        sensorAccuracy=event.accuracy
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val total = sqrt(x*x + y*y + z*z)
        count++
        val delta = total - mean
        mean += delta / count
        m2 += delta * (total - mean)
        val sd = if (count > 1) sqrt(m2 / (count - 1)) else 0.0
        samples.addLast(total.toFloat())
        if(samples.size>150) samples.removeFirst()
        if(calStart>0L && !calibrated) {
            calCount++
            val diff=total-baseline
            baseline+=diff/calCount
            m2cal+=diff*(total-baseline)
            if(now-calStart>=30000) calibrated=true
        } else if(calibrated && moving<0.8f && now-lastEvent>6000) {
            val threshold=max(8.0,4*kotlin.math.sqrt(m2cal/max(1,calCount-1)))
            if(abs(total-baseline)>threshold) {
                lastEvent=now
                log.addFirst(String.format(Locale.US,"%.1f µT at %tT",total,java.util.Date()))
                if(log.size>30) log.removeLast()
            }
        }
        dashboard.invalidate()
        magnitude.text = String.format(Locale.US, "%.2f µT", total)
        vector.text = String.format(Locale.US, "X %.2f   Y %.2f   Z %.2f µT", x, y, z)
        status.text = String.format(Locale.US, "Samples: %d  |  Mean: %.2f µT  |  SD: %.2f µT\nAccuracy status: %d", count, mean, sd, event.accuracy)
    }
}
