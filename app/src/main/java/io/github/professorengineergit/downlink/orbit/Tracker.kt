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

enum class Kind(val title: String, val blurb: String) {
    Weather("Wetter", "Funken Bilder offen zur Erde"),
    Station("Raumstationen", "Bewohnt, hell am Himmel"),
    Telescope("Teleskope", "Schauen nach draußen"),
    Earth("Erdbeobachtung", "Kartieren Land, Eis und Luft"),
    Ham("Amateurfunk", "Relais für Funkamateure"),
}

data class SatInfo(
    val key: String,
    val match: String, // Name im TLE
    val label: String,
    val freqMHz: Double, // 0 = kein Signal für uns, nur sichtbar
    val mode: String,
    val note: String,
    val color: Long,
    val defaultOn: Boolean,
    val kind: Kind,
    val sprite: String, // Name des gerenderten Bildes unter res/drawable-nodpi/sat_*.webp
    val operator: String,
    val launched: Int,
    val about: String,
    val receivable: Boolean = false, // mit Stick und Draht
)

val Catalog = listOf(
    SatInfo("m23", "METEOR-M2 3", "Meteor-M2 3", 137.9, "LRPT", "Mit SDR-Stick und V-Dipol empfangbar", 0xFF7CDAA9, true, Kind.Weather, "meteor", "Roskosmos", 2023,
        "Russischer Wettersatellit auf etwa 830 km. Sendet laufend Bilder im sichtbaren und infraroten Licht, digital als LRPT.", receivable = true),
    SatInfo("m24", "METEOR-M2 4", "Meteor-M2 4", 137.9, "LRPT", "Mit SDR-Stick und V-Dipol empfangbar", 0xFF8FD3F0, true, Kind.Weather, "meteor", "Roskosmos", 2024,
        "Schwester von Meteor-M2 3 auf ähnlicher Bahn. Zusammen ergeben sie mehrere gute Überflüge am Tag.", receivable = true),
    SatInfo("mb", "METOP-B", "Metop-B", 1701.3, "AHRPT", "Braucht eine Schüssel und Nachführung", 0xFFD7BDFF, false, Kind.Weather, "metop", "EUMETSAT", 2012,
        "Europäischer Wettersatellit, Rückgrat der Wettervorhersage. Hochaufgelöste Bilder im L-Band."),
    SatInfo("mc", "METOP-C", "Metop-C", 1701.3, "AHRPT", "Braucht eine Schüssel und Nachführung", 0xFFFFB0CC, false, Kind.Weather, "metop", "EUMETSAT", 2018,
        "Der jüngste der drei europäischen Metop-Satelliten."),
    SatInfo("n20", "NOAA 20 (JPSS-1)", "NOAA 20", 7812.0, "HRD", "X-Band, nur mit großer Schüssel", 0xFFFFD27A, false, Kind.Weather, "jpss", "NOAA · NASA", 2017,
        "Amerikanischer Polar-Wettersatellit der JPSS-Reihe. Seine Daten gehen in jedes Wettermodell."),
    SatInfo("n21", "NOAA 21 (JPSS-2)", "NOAA 21", 7812.0, "HRD", "X-Band, nur mit großer Schüssel", 0xFFFFC08A, false, Kind.Weather, "jpss", "NOAA · NASA", 2022,
        "Der neueste JPSS-Satellit, gleiche Bauart wie NOAA 20."),
    SatInfo("npp", "SUOMI NPP", "Suomi NPP", 7812.0, "HRD", "X-Band, nur mit großer Schüssel", 0xFFF5E19B, false, Kind.Weather, "npp", "NASA · NOAA", 2011,
        "Vorläufer der JPSS-Reihe, berühmt für die Nachtbilder der Erde."),
    SatInfo("iss", "ISS (ZARYA)", "ISS", 145.8, "FM · SSTV", "Funkhandy reicht, Bilder bei SSTV-Aktionen", 0xFFFFB86B, true, Kind.Station, "iss", "NASA · Roskosmos · ESA · JAXA · CSA", 1998,
        "Die Internationale Raumstation auf etwa 420 km. So groß wie ein Fußballfeld und oft heller als jeder Stern.", receivable = true),
    SatInfo("css", "CSS (TIANHE)", "Tiangong", 0.0, "Sichtbar", "Hell mit bloßem Auge", 0xFFFF8A80, true, Kind.Station, "tiangong", "CMSA", 2021,
        "Die chinesische Raumstation auf etwa 390 km. In der Dämmerung gut als wandernder Lichtpunkt zu sehen."),
    SatInfo("hst", "HST", "Hubble", 0.0, "Sichtbar", "Mit bloßem Auge in der Dämmerung", 0xFFB8C7FF, false, Kind.Telescope, "hubble", "NASA · ESA", 1990,
        "Das Weltraumteleskop auf etwa 530 km. Fliegt nah am Äquator, von Mitteleuropa aus tief im Süden."),
    SatInfo("aqua", "AQUA", "Aqua", 8160.0, "X-Band", "Direktempfang nur für Profis", 0xFF6FD6E8, false, Kind.Earth, "aqua", "NASA", 2002,
        "Misst Wasser in allen Formen: Wolken, Regen, Eis, Ozeantemperatur."),
    SatInfo("terra", "TERRA", "Terra", 8212.5, "X-Band", "Direktempfang nur für Profis", 0xFF9BE08F, false, Kind.Earth, "terra", "NASA", 1999,
        "Das Flaggschiff der NASA-Erdbeobachtung, seit über 25 Jahren im Dienst."),
    SatInfo("aura", "AURA", "Aura", 8160.0, "X-Band", "Direktempfang nur für Profis", 0xFFC5A3FF, false, Kind.Earth, "aura", "NASA", 2004,
        "Beobachtet die Ozonschicht und die Luftqualität."),
    SatInfo("l8", "LANDSAT 8", "Landsat 8", 8200.0, "X-Band", "Direktempfang nur für Profis", 0xFFE8C07A, false, Kind.Earth, "landsat", "NASA · USGS", 2013,
        "Fotografiert jeden Fleck der Erde alle 16 Tage mit 30 m Auflösung."),
    SatInfo("l9", "LANDSAT 9", "Landsat 9", 8200.0, "X-Band", "Direktempfang nur für Profis", 0xFFE0A86A, false, Kind.Earth, "landsat", "NASA · USGS", 2021,
        "Zwilling von Landsat 8, zusammen alle 8 Tage über jedem Ort."),
    SatInfo("so50", "SAUDISAT 1C (SO-50)", "SO-50", 436.795, "FM-Relais", "Funkhandy und Handantenne", 0xFF80E0C0, false, Kind.Ham, "so50", "KACST · AMSAT", 2002,
        "Kleiner FM-Umsetzer. Mit Amateurfunklizenz kannst du über ihn mit anderen sprechen.", receivable = true),
    SatInfo("io117", "GREENCUBE (IO-117)", "GreenCube", 435.31, "Digipeater", "Hohe Bahn, lange Überflüge", 0xFFA8E06A, false, Kind.Ham, "greencube", "Uni Rom · AMSAT Italia", 2022,
        "Würfelsatellit auf fast 6000 km Höhe. Ein Überflug dauert Stunden statt Minuten.", receivable = true),
    SatInfo("po101", "DIWATA-2B", "Diwata-2 (PO-101)", 145.9, "FM-Relais", "Funkhandy und Handantenne", 0xFF7AB8FF, false, Kind.Ham, "diwata", "PhilSA", 2018,
        "Philippinischer Mikrosatellit mit FM-Umsetzer für Funkamateure.", receivable = true),
)

data class Observer(val lat: Double, val lon: Double, val altM: Double)

data class Sample(val t: Long, val az: Double, val el: Double, val rangeRate: Double, val range: Double = 0.0)

data class Pass(val sat: SatInfo, val aos: Long, val los: Long, val tca: Long, val maxEl: Double, val samples: List<Sample>, val visible: Boolean = false) {
    // Wird es klappen? 0..100 aus Höhe, Dauer und Zeit über 20°. Für reine Sichtobjekte zählt, ob man ihn sieht.
    val score: Int get() {
        val high = samples.count { it.el >= 20 } * 10 / 60.0 // Minuten über 20°
        val v = (maxEl.coerceAtMost(70.0) / 70 * 55) + (minutes.coerceAtMost(15.0) / 15 * 20) + (high.coerceAtMost(8.0) / 8 * 25)
        return if (sat.freqMHz == 0.0) (if (visible) v else v * 0.3).toInt() else v.toInt()
    }
    val verdict get() = when { score >= 75 -> "Sehr gut"; score >= 50 -> "Gut"; score >= 30 -> "Möglich"; else -> "Schwierig" }
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
        val text = listOf("weather", "stations", "amateur", "science", "resource")
            .joinToString("\n") { fetch("https://celestrak.org/NORAD/elements/gp.php?GROUP=$it&FORMAT=tle") }
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
                        if (ev >= 0) samples += Sample(u, deg(p.azimuth), ev, p.rangeRate, p.range)
                        else if (samples.isNotEmpty()) break
                        u += 10_000L
                        if (u - tn > 30 * 60_000L) break
                    }
                    if (samples.size > 2) {
                        val top = samples.maxBy { it.el }
                        val vis = samples.filter { it.el >= 10 }.any { sm ->
                            sunAzEl(obs, sm.t).second < -6 && sunlit(s.getPosition(gs, Date(sm.t)).let { Triple(deg(it.latitude), deg(it.longitude), it.altitude) }, sm.t)
                        }
                        if (top.el >= minEl && samples.last().t > from) out += Pass(info, samples.first().t, samples.last().t, top.t, top.el, samples, vis)
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

        // Liegt der Satellit im Sonnenlicht? Zylinderschatten der Erde genügt.
        fun sunlit(pos: Triple<Double, Double, Double>, t: Long): Boolean {
            fun v(la: Double, lo: Double) = (la * PI / 180).let { a -> (lo * PI / 180).let { o -> Triple(cos(a) * cos(o), cos(a) * sin(o), sin(a)) } }
            val (sla, slo) = subsolar(t)
            val sv = v(sla, slo); val u = v(pos.first, pos.second); val r = RE + pos.third
            val dot = (u.first * sv.first + u.second * sv.second + u.third * sv.third) * r
            if (dot > 0) return true
            val px = u.first * r - dot * sv.first; val py = u.second * r - dot * sv.second; val pz = u.third * r - dot * sv.third
            return kotlin.math.sqrt(px * px + py * py + pz * pz) > RE
        }

        fun compass(az: Double): String = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")[(((az + 22.5) % 360) / 45).toInt()]
    }
}
