package io.github.professorengineergit.downlink

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Observer

/* --- Alle Optionen. Im Profil "Standard" bleiben die Nerd-Optionen verborgen, wirken aber weiter. --- */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nerd by mutableStateOf(p.getBoolean("nerd", false))
    var gear by mutableStateOf(p.getString("gear", "sdr") ?: "sdr") // phone, sdr, dish
    var minElevation by mutableFloatStateOf(p.getFloat("minEl", 15f))
    var hoursAhead by mutableIntStateOf(p.getInt("hours", 48))
    var utc by mutableStateOf(p.getBoolean("utc", false))
    var compassSky by mutableStateOf(p.getBoolean("compassSky", false))
    var footprints by mutableStateOf(p.getBoolean("footprints", true))
    var nightLights by mutableStateOf(p.getBoolean("nightLights", true))
    var atmosphere by mutableStateOf(p.getBoolean("atmosphere", true))
    var sdrPpm by mutableIntStateOf(p.getInt("sdrPpm", 0))
    var sdrGain by mutableIntStateOf(p.getInt("sdrGain", -1)) // Zehntel dB, -1 = automatisch
    var sdrAntenna by mutableStateOf(p.getString("sdrAntenna", "mag") ?: "mag") // mag, vdipole, qfh
    var sdrCalibrated by mutableStateOf(p.getLong("sdrCal", 0L))
    var nightSats by mutableStateOf(p.getStringSet("nightSats", null) ?: setOf("m23", "m24", "iss"))
    var nightMinEl by mutableFloatStateOf(p.getFloat("nightMinEl", 20f))
    var nightFreeAz by mutableIntStateOf(p.getInt("nightFreeAz", -1)) // Mitte der freien Himmelshälfte in Grad, -1 = rundum frei
    var nightUntil by mutableIntStateOf(p.getInt("nightUntil", 7)) // Stunde, bis zu der aufgenommen wird, -1 = offen
    var trackMinutes by mutableIntStateOf(p.getInt("trackMinutes", 90))
    var showSun by mutableStateOf(p.getBoolean("showSun", true))
    var dopplerChart by mutableStateOf(p.getBoolean("doppler", true))
    var tuningTable by mutableStateOf(p.getBoolean("tuning", false))
    var directional by mutableStateOf(p.getBoolean("directional", false))
    var haptics by mutableStateOf(p.getBoolean("haptics", true))
    var remind by mutableStateOf(p.getBoolean("remind", true))
    var remindMinutes by mutableIntStateOf(p.getInt("remindMin", 10))
    var remindMinEl by mutableFloatStateOf(p.getFloat("remindMinEl", 30f))
    var useGps by mutableStateOf(p.getBoolean("useGps", true))
    var introDone by mutableStateOf(p.getBoolean("introDone", false))
    var askedLocation by mutableStateOf(p.getBoolean("askedLocation", false))
    var lat by mutableFloatStateOf(p.getFloat("lat", 50.11f))
    var lon by mutableFloatStateOf(p.getFloat("lon", 8.68f))
    var alt by mutableFloatStateOf(p.getFloat("alt", 110f))
    var enabled by mutableStateOf(p.getStringSet("enabled2", null) ?: Catalog.filter { it.defaultOn }.map { it.key }.toSet())

    val observer get() = Observer(lat.toDouble(), lon.toDouble(), alt.toDouble())

    fun save() {
        p.edit()
            .putBoolean("nerd", nerd).putString("gear", gear)
            .putFloat("minEl", minElevation).putInt("hours", hoursAhead).putBoolean("utc", utc)
            .putBoolean("compassSky", compassSky).putBoolean("footprints", footprints).putBoolean("nightLights", nightLights)
            .putBoolean("atmosphere", atmosphere).putStringSet("nightSats", nightSats).putFloat("nightMinEl", nightMinEl).putInt("nightFreeAz", nightFreeAz).putInt("nightUntil", nightUntil).putInt("trackMinutes", trackMinutes).putInt("sdrPpm", sdrPpm).putInt("sdrGain", sdrGain).putString("sdrAntenna", sdrAntenna).putLong("sdrCal", sdrCalibrated).putBoolean("showSun", showSun)
            .putBoolean("doppler", dopplerChart).putBoolean("tuning", tuningTable).putBoolean("directional", directional)
            .putBoolean("haptics", haptics).putBoolean("remind", remind).putInt("remindMin", remindMinutes).putFloat("remindMinEl", remindMinEl)
            .putBoolean("useGps", useGps).putBoolean("introDone", introDone).putBoolean("askedLocation", askedLocation)
            .putFloat("lat", lat).putFloat("lon", lon).putFloat("alt", alt).putStringSet("enabled2", enabled)
            .apply()
    }
}
