package io.github.professorengineergit.downlink.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.professorengineergit.downlink.orbit.Live
import io.github.professorengineergit.downlink.orbit.Observer
import io.github.professorengineergit.downlink.orbit.Tracker
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/* --- Weltkarte (plattkarte): Nachtlichter, Tag-Nacht-Grenze, Bodenspuren und Sichtkreise --- */
class MapData(val land: List<List<Pair<Double, Double>>>, val night: ImageBitmap?)

@Composable
fun rememberMapData(): MapData {
    val ctx = LocalContext.current
    return remember {
        val json = ctx.assets.open("land.json").bufferedReader().readText()
        val arr = JSONArray(json)
        val land = (0 until arr.length()).map { i ->
            val poly = arr.getJSONArray(i)
            (0 until poly.length()).map { k -> poly.getJSONArray(k).let { it.getDouble(1) to it.getDouble(0) } }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val night = ctx.assets.open("earth_night.webp").use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        MapData(land, night)
    }
}

@Composable
fun WorldMap(
    data: MapData,
    live: List<Live>,
    tracks: Map<String, List<Pair<Double, Double>>>,
    observer: Observer,
    now: Long,
    showTerminator: Boolean,
    showFootprints: Boolean,
    showNight: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun xy(lat: Double, lon: Double) = Offset(((lon + 180) / 360 * w).toFloat(), ((90 - lat) / 180 * h).toFloat())

        drawRect(Color(0xFF0B1410))
        if (showNight && data.night != null) {
            drawImage(data.night, srcSize = IntSize(data.night.width, data.night.height), dstOffset = IntOffset.Zero, dstSize = IntSize(w.toInt(), h.toInt()), alpha = 0.55f)
        }
        // Land als Fläche mit feiner Küste
        data.land.forEach { poly ->
            val path = Path()
            poly.forEachIndexed { i, (la, lo) -> val o = xy(la, lo); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            path.close()
            drawPath(path, Color(0x3398F7C4))
            drawPath(path, Color(0x667CDAA9), style = Stroke(1f))
        }
        // Gradnetz
        for (lo in -150..150 step 30) drawLine(Color(0x14FFFFFF), xy(90.0, lo.toDouble()), xy(-90.0, lo.toDouble()), 1f)
        for (la in -60..60 step 30) drawLine(Color(0x14FFFFFF), xy(la.toDouble(), -180.0), xy(la.toDouble(), 180.0), 1f)

        if (showTerminator) drawNight(now, ::xy, w, h)

        // Bodenspuren: Vergangenheit gestrichelt, Zukunft durchgezogen
        tracks.forEach { (key, pts) ->
            val lv = live.firstOrNull { it.sat.key == key } ?: return@forEach
            val col = Color(lv.sat.color)
            val split = pts.size / 5
            drawTrack(pts.take(split + 1), ::xy, w, col.copy(alpha = 0.5f), dashed = true)
            drawTrack(pts.drop(split), ::xy, w, col, dashed = false)
        }

        if (showFootprints) live.forEach { lv ->
            val ring = circle(lv.lat, lv.lon, Tracker.footprintDeg(lv.altKm))
            drawTrack(ring, ::xy, w, Color(lv.sat.color).copy(alpha = 0.7f), dashed = false, width = 1.5f)
        }

        // Beobachter
        val me = xy(observer.lat, observer.lon)
        drawCircle(Color.White.copy(alpha = 0.25f), 12f, me)
        drawCircle(Color.White, 4.5f, me)

        live.forEach { lv ->
            val p = xy(lv.lat, lv.lon)
            val col = Color(lv.sat.color)
            drawCircle(col.copy(alpha = 0.3f), 16f, p)
            drawCircle(col, 7f, p)
            drawCircle(Color(0xFF0F1511), 2.5f, p)
        }
    }
}

// Nachtseite: zwischen Tag-Nacht-Grenze und dem Pol, der gerade im Dunkeln liegt
private fun DrawScope.drawNight(now: Long, xy: (Double, Double) -> Offset, w: Float, h: Float) {
    val (decl, sunLon) = Tracker.subsolar(now)
    val d = decl * PI / 180
    val path = Path()
    val pole = if (decl > 0) -90.0 else 90.0
    path.moveTo(xy(pole, -180.0).x, xy(pole, -180.0).y)
    for (lo in -180..180 step 2) {
        val hrs = (lo - sunLon) * PI / 180
        val lat = atan(-cos(hrs) / tan(if (abs(d) < 1e-4) 1e-4 else d)) * 180 / PI
        val o = xy(lat, lo.toDouble())
        path.lineTo(o.x, o.y)
    }
    path.lineTo(xy(pole, 180.0).x, xy(pole, 180.0).y)
    path.close()
    clipRect(0f, 0f, w, h) { drawPath(path, Color(0x66000000)) }
}

private fun DrawScope.drawTrack(pts: List<Pair<Double, Double>>, xy: (Double, Double) -> Offset, w: Float, color: Color, dashed: Boolean, width: Float = 2.5f) {
    val path = Path()
    var prev: Offset? = null
    pts.forEach { (la, lo) ->
        val o = xy(la, lo)
        // über die Datumsgrenze nicht quer übers Bild ziehen
        if (prev == null || abs(o.x - prev!!.x) > w / 2) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        prev = o
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null))
}

// Punkte im Abstand `radiusDeg` um einen Mittelpunkt auf der Kugel
private fun circle(lat: Double, lon: Double, radiusDeg: Double): List<Pair<Double, Double>> {
    val la = lat * PI / 180
    val lo = lon * PI / 180
    val r = radiusDeg * PI / 180
    return (0..96).map { i ->
        val b = 2 * PI * i / 96
        val pla = asin(sin(la) * cos(r) + cos(la) * sin(r) * cos(b))
        val plo = lo + atan2(sin(b) * sin(r) * cos(la), cos(r) - sin(la) * sin(pla))
        pla * 180 / PI to Tracker.lon180(plo * 180 / PI)
    }
}
