package io.github.professorengineergit.downlink

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Live
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app), SensorEventListener {
    val settings = Settings(app)
    val tracker = Tracker(app)
    var now by mutableLongStateOf(System.currentTimeMillis())
    var passes by mutableStateOf<List<Pass>>(emptyList())
    var live by mutableStateOf<List<Live>>(emptyList())
    var tracks by mutableStateOf<Map<String, List<Pair<Double, Double>>>>(emptyMap())
    var heading by mutableFloatStateOf(0f)
    var refreshing by mutableStateOf(false)
    var computing by mutableStateOf(true)
    var selected by mutableStateOf<Pass?>(null) // angetippte Bahn auf der Startseite

    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    init {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { tracker.load() }
            recompute()
            // Live-Positionen jede Sekunde, Bodenspuren alle 30 s
            var n = 0
            while (true) {
                now = System.currentTimeMillis()
                val enabled = enabledSats()
                live = withContext(Dispatchers.Default) { enabled.mapNotNull { tracker.live(it, settings.observer, now) } }
                if (n % 30 == 0) tracks = withContext(Dispatchers.Default) {
                    enabled.associate { it.key to tracker.track(it, now - settings.trackMinutes * 15_000L, now + settings.trackMinutes * 60_000L) }
                }
                if (n % 300 == 299) recompute()
                n++
                delay(1000)
            }
        }
    }

    fun reschedule() = Reminders.schedule(getApplication(), passes, settings)

    fun enabledSats() = Catalog.filter { it.key in settings.enabled && tracker.available(it) }

    fun recompute() {
        viewModelScope.launch {
            computing = true
            passes = withContext(Dispatchers.Default) {
                tracker.passes(enabledSats(), settings.observer, System.currentTimeMillis(), settings.hoursAhead, settings.minElevation.toDouble())
            }
            tracks = emptyMap()
            computing = false
            if (selected != null && passes.none { it.sat.key == selected!!.sat.key && it.aos == selected!!.aos }) selected = null
            val app = getApplication<Application>()
            Reminders.schedule(app, passes, settings)
            WidgetData.publish(app, passes)
        }
    }

    fun refreshTle() {
        viewModelScope.launch {
            refreshing = true
            withContext(Dispatchers.IO) { tracker.refresh() }
            refreshing = false
            recompute()
        }
    }

    @SuppressLint("MissingPermission")
    fun locate() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull() ?: return
        settings.lat = loc.latitude.toFloat()
        settings.lon = loc.longitude.toFloat()
        if (loc.hasAltitude()) settings.alt = loc.altitude.toFloat()
        settings.save()
        recompute()
    }

    // Kompass nur, solange er gebraucht wird
    fun compass(on: Boolean) {
        sensors.unregisterListener(this)
        if (on) sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        else heading = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, e.values)
        val o = FloatArray(3)
        SensorManager.getOrientation(r, o)
        val az = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
        // glätten, damit die Karte nicht zittert
        var d = az - heading
        if (d > 180) d -= 360
        if (d < -180) d += 360
        heading = (heading + d * 0.15f + 360) % 360
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onCleared() { sensors.unregisterListener(this) }
}
