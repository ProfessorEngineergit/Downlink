package io.github.professorengineergit.downlink.orbit

import android.content.Context
import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.Satellite
import com.github.amsacode.predict4java.SatelliteFactory
import com.github.amsacode.predict4java.TLE
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/* --- Bahnen: SGP4 über predict4java, Bahndaten von CelesTrak, Pässe durch Abtasten --- */

data class SatInfo(
    val key: String,
    val match: String, // Name im TLE
    val label: String,
    val freqMHz: Double,
    val mode: String,
    val note: String,
    val color: Long,
    val defaultOn: Boolean,
)

val Catalog = listOf(
    SatInfo("m23", "METEOR-M2 3", "Meteor-M2 3", 137.9, "LRPT", "Mit SDR-Stick und V-Dipol empfangbar", 0xFF7CDAA9, true),
    SatInfo("m24", "METEOR-M2 4", "Meteor-M2 4", 137.9, "LRPT", "Mit SDR-Stick und V-Dipol empfangbar", 0xFFA5CDDE, true),
    SatInfo("iss", "ISS (ZARYA)", "ISS", 145.8, "FM · SSTV", "Bilder nur bei SSTV-Aktionen", 0xFFFFB86B, true),
    SatInfo("mb", "METOP-B", "Metop-B", 1701.3, "HRPT", "Braucht eine Parabolantenne", 0xFFD7BDFF, false),
    SatInfo("mc", "METOP-C", "Metop-C", 1701.3, "HRPT", "Braucht eine Parabolantenne", 0xFFFFB0CC, false),
)

data class Observer(val lat: Double, val lon: Double, val altM: Double)

data class Sample(val t: Long, val az: Double, val el: Double, val rangeRate: Double)

data class Pass(val sat: SatInfo, val aos: Long, val los: Long, val tca: Long, val maxEl: Double, val samples: List<Sample>) {
    val aosAz get() = samples.first().az
    val losAz get() = samples.last().az
    val minutes get() = ((los - aos) / 60_000.0)
}

data class Live(val sat: SatInfo, val lat: Double, val lon: Double, val altKm: Double, val az: Double, val el: Double, val rangeKm: Double, val rangeRate: Double)

class Tracker(private val ctx: Context) {
    private val cache = File(ctx.filesDir, "tle.txt")
    private var sats: Map<String, Satellite> = emptyMap()
    var tleAge: Long = 0L; private set // Epoche der Bahndaten, älteste
    var source = "App"; private set

    fun load() {
        val text = if (cache.exists()) { source = "CelesTrak"; cache.readText() } else ctx.assets.open("tle.txt").bufferedReader().readText()
        parse(text)
    }

    // Holt frische Bahndaten; bei Fehler bleibt der alte Stand
    fun refresh(): Boolean = try {
        val weather = fetch("https://celestrak.org/NORAD/elements/gp.php?GROUP=weather&FORMAT=tle")
        val iss = fetch("https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=tle")
        val text = weather + "\n" + iss
        parse(text)
        cache.writeText(text)
        source = "CelesTrak"
        true
    } catch (_: Exception) {
        false
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Downlink (open source satellite tracker)")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parse(text: String) {
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val out = mutableMapOf<String, Satellite>()
        var oldest = Long.MAX_VALUE
        var i = 0
        while (i + 2 < lines.size) {
            val name = lines[i].trim()
            val l1 = lines[i + 1]
            val l2 = lines[i + 2]
            if (!l1.startsWith("1 ") || !l2.startsWith("2 ")) { i++; continue }
            Catalog.firstOrNull { it.match == name }?.let { info ->
                val tle = TLE(arrayOf(name, l1, l2))
                out[info.key] = SatelliteFactory.createSatellite(tle)
                oldest = minOf(oldest, epochMillis(l1))
            }
            i += 3
        }
        if (out.isNotEmpty()) {
            sats = out
            tleAge = oldest
        }
    }

    // Epoche aus Zeile 1: Jahr (2 Stellen) und Tag des Jahres mit Bruchteil
    private fun epochMillis(l1: String): Long = try {
        val yy = l1.substring(18, 20).trim().toInt()
        val day = l1.substring(20, 32).trim().toDouble()
        val year = if (yy < 57) 2000 + yy else 1900 + yy
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        c.clear(); c.set(year, 0, 1)
        c.timeInMillis + ((day - 1) * 86_400_000).toLong()
    } catch (_: Exception) { 0L }

    fun available(info: SatInfo) = sats.containsKey(info.key)

    fun live(info: SatInfo, obs: Observer, t: Long): Live? {
        val s = sats[info.key] ?: return null
        val p = s.getPosition(GroundStationPosition(obs.lat, obs.lon, obs.altM), Date(t))
        return Live(info, deg(p.latitude), lon180(deg(p.longitude)), p.altitude, deg(p.azimuth), deg(p.elevation), p.range, p.rangeRate)
    }

    fun track(info: SatInfo, t0: Long, t1: Long, stepS: Int = 30): List<Pair<Double, Double>> {
        val s = sats[info.key] ?: return emptyList()
        val gs = GroundStationPosition(0.0, 0.0, 0.0)
        return (t0..t1 step stepS * 1000L).map { t -> s.getPosition(gs, Date(t)).let { deg(it.latitude) to lon180(deg(it.longitude)) } }
    }

    // Grob in 30-s-Schritten suchen, dann jeden Pass fein abtasten
    fun passes(infos: List<SatInfo>, obs: Observer, from: Long, hours: Int, minEl: Double): List<Pass> {
        val gs = GroundStationPosition(obs.lat, obs.lon, obs.altM)
        val out = mutableListOf<Pass>()
        for (info in infos) {
            val s = sats[info.key] ?: continue
            fun el(t: Long) = deg(s.getPosition(gs, Date(t)).elevation)
            var t = from - 20 * 60_000L
            val end = from + hours * 3_600_000L
            var prev = el(t)
            while (t < end) {
                val tn = t + 30_000L
                val e = el(tn)
                if (prev < 0 && e >= 0) {
                    val samples = mutableListOf<Sample>()
                    var u = tn - 30_000L
                    while (true) {
                        val p = s.getPosition(gs, Date(u))
                        val ev = deg(p.elevation)
                        if (ev >= 0) samples += Sample(u, deg(p.azimuth), ev, p.rangeRate)
                        else if (samples.isNotEmpty()) break
                        u += 10_000L
                        if (u - tn > 30 * 60_000L) break
                    }
                    if (samples.size > 2) {
                        val top = samples.maxBy { it.el }
                        if (top.el >= minEl && samples.last().t > from) out += Pass(info, samples.first().t, samples.last().t, top.t, top.el, samples)
                    }
                    t = u
                    prev = -1.0
                    continue
                }
                prev = e
                t = tn
            }
        }
        return out.sortedBy { it.aos }
    }

    companion object {
        const val RE = 6371.0
        fun deg(r: Double) = r * 180 / PI
        fun lon180(l: Double): Double { var x = l % 360; if (x > 180) x -= 360; if (x < -180) x += 360; return x }

        // Sichtkreis am Boden: wie weit der Satellit über den Horizont schaut
        fun footprintDeg(altKm: Double) = deg(acos(RE / (RE + altKm)))

        fun subsolar(t: Long): Pair<Double, Double> {
            val days = t / 86_400_000.0
            val decl = -23.44 * cos(2 * PI / 365.2422 * ((days % 365.2422) + 10))
            val hours = (t % 86_400_000L) / 3_600_000.0
            return decl to lon180(-15.0 * (hours - 12))
        }

        // Sonnenhöhe am Beobachter und Azimut, aus dem Subsolarpunkt
        fun sunAzEl(obs: Observer, t: Long): Pair<Double, Double> {
            val (sl, so) = subsolar(t)
            val la = obs.lat * PI / 180
            val d = sl * PI / 180
            val h = (obs.lon - so) * PI / 180
            val el = asin(sin(la) * sin(d) + cos(la) * cos(d) * cos(h))
            val az = atan2(-sin(h), tan(d) * cos(la) - sin(la) * cos(h))
            return ((deg(az) + 360) % 360) to deg(el)
        }

        fun compass(az: Double): String = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")[(((az + 22.5) % 360) / 45).toInt()]
    }
}
