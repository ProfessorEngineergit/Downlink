package io.github.professorengineergit.downlink

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Observer

/* --- Alle Optionen; Downlink ist nerdig, also gibt es viele --- */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var minElevation by mutableFloatStateOf(p.getFloat("minEl", 15f))
    var hoursAhead by mutableIntStateOf(p.getInt("hours", 48))
    var utc by mutableStateOf(p.getBoolean("utc", false))
    var compassSky by mutableStateOf(p.getBoolean("compassSky", false))
    var footprints by mutableStateOf(p.getBoolean("footprints", true))
    var terminator by mutableStateOf(p.getBoolean("terminator", true))
    var nightLights by mutableStateOf(p.getBoolean("nightLights", true))
    var trackMinutes by mutableIntStateOf(p.getInt("trackMinutes", 90))
    var showSun by mutableStateOf(p.getBoolean("showSun", true))
    var dopplerChart by mutableStateOf(p.getBoolean("doppler", true))
    var haptics by mutableStateOf(p.getBoolean("haptics", true))
    var useGps by mutableStateOf(p.getBoolean("useGps", true))
    var askedLocation by mutableStateOf(p.getBoolean("askedLocation", false))
    var lat by mutableFloatStateOf(p.getFloat("lat", 50.11f))
    var lon by mutableFloatStateOf(p.getFloat("lon", 8.68f))
    var alt by mutableFloatStateOf(p.getFloat("alt", 110f))
    var enabled by mutableStateOf(p.getStringSet("enabled", null) ?: Catalog.filter { it.defaultOn }.map { it.key }.toSet())

    val observer get() = Observer(lat.toDouble(), lon.toDouble(), alt.toDouble())

    fun save() {
        p.edit()
            .putFloat("minEl", minElevation).putInt("hours", hoursAhead).putBoolean("utc", utc)
            .putBoolean("compassSky", compassSky).putBoolean("footprints", footprints).putBoolean("terminator", terminator)
            .putBoolean("nightLights", nightLights).putInt("trackMinutes", trackMinutes).putBoolean("showSun", showSun)
            .putBoolean("doppler", dopplerChart).putBoolean("haptics", haptics).putBoolean("useGps", useGps).putBoolean("askedLocation", askedLocation)
            .putFloat("lat", lat).putFloat("lon", lon).putFloat("alt", alt).putStringSet("enabled", enabled)
            .apply()
    }
}
