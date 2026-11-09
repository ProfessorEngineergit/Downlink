package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Tracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* --- Diagramme über die Zeit eines Passes. Finger drauf: Linie und genaue Werte, geteilt mit der Kuppel. --- */

// Zieht den Finger über das Diagramm und meldet die Zeit unter dem Finger, ohne langes Drücken
private fun Modifier.scrubbable(pass: Pass, onScrub: (Long?) -> Unit) = pointerInput(pass) {
    fun t(x: Float) = pass.aos + ((x / size.width).coerceIn(0f, 1f) * (pass.los - pass.aos)).toLong()
    awaitEachGesture {
        val down = awaitFirstDown()
        onScrub(t(down.position.x))
        drag(down.id) { ch -> onScrub(t(ch.position.x)); ch.consume() }
        onScrub(null)
    }
}

@Composable
private fun TickHaptic(scrub: Long?, on: Boolean) {
    val haptic = LocalHapticFeedback.current
    var lastMin by remember { mutableIntStateOf(-1) }
    LaunchedEffect(scrub) {
        if (scrub == null) { lastMin = -1; return@LaunchedEffect }
        val m = (scrub / 60_000).toInt()
        if (on && lastMin != -1 && m != lastMin) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        lastMin = m
    }
}

@Composable
fun ElevationChart(pass: Pass, now: Long, minEl: Float, utc: Boolean, scrub: Long?, onScrub: (Long?) -> Unit, modifier: Modifier = Modifier, haptics: Boolean = true) {
    val col = Color(pass.sat.color)
    val text = rememberTextMeasurer()
    val fmt = remember(utc) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }
    val fmtM = remember(utc) { SimpleDateFormat("HH:mm", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }
    TickHaptic(scrub, haptics)
    Canvas(modifier.scrubbable(pass, onScrub)) {
        val w = size.width
        val h = size.height - 22f
        fun x(t: Long) = ((t - pass.aos).toFloat() / (pass.los - pass.aos)) * w
        fun y(el: Double) = (h - (el / 90.0).toFloat() * h)
        for (el in listOf(30.0, 60.0)) drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, y(el)), Offset(w, y(el)), 1f)
        drawLine(Coral.copy(alpha = 0.45f), Offset(0f, y(minEl.toDouble())), Offset(w, y(minEl.toDouble())), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val line = Path(); val fill = Path()
        pass.samples.forEachIndexed { i, s ->
            val o = Offset(x(s.t), y(s.el))
            if (i == 0) { line.moveTo(o.x, o.y); fill.moveTo(o.x, h); fill.lineTo(o.x, o.y) } else { line.lineTo(o.x, o.y); fill.lineTo(o.x, o.y) }
        }
        fill.lineTo(w, h); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.45f), col.copy(alpha = 0.05f)), 0f, h))
        drawPath(line, col.copy(alpha = 0.3f), style = Stroke(12f, cap = StrokeCap.Round))
        drawPath(line, Brush.horizontalGradient(listOf(col, Sky)), style = Stroke(4f, cap = StrokeCap.Round))
        if (now in pass.aos..pass.los) {
            drawLine(Mint.primary, Offset(x(now), 0f), Offset(x(now), h), 2f)
        }
        listOf("30°" to 30.0, "60°" to 60.0).forEach { (l, e) ->
            drawText(text.measure(l, TextStyle(color = Ink.faint, fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, y(e) - 16f))
        }
        // Zeitachse unten
        val steps = 4
        for (k in 0..steps) {
            val t = pass.aos + (pass.los - pass.aos) * k / steps
            val r = text.measure(fmtM.format(Date(t)), TextStyle(color = Ink.faint, fontSize = 10.sp, fontFamily = Mono))
            drawText(r, topLeft = Offset((x(t) - r.size.width / 2f).coerceIn(0f, w - r.size.width), h + 6f))
        }
        if (scrub != null) {
            val s = pass.samples.minBy { abs(it.t - scrub) }
            val sx = x(s.t); val sy = y(s.el)
            drawLine(Color.White.copy(alpha = 0.8f), Offset(sx, 0f), Offset(sx, h), 1.5f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, sy), Offset(w, sy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawCircle(Color.White, 8f, Offset(sx, sy)); drawCircle(col, 4.5f, Offset(sx, sy))
            readout(text, listOf("%.1f°".format(s.el), fmt.format(Date(s.t)), "Az %.0f° %s".format(s.az, Tracker.compass(s.az))), Offset(sx, sy), Size(w, size.height))
        }
    }
}

@Composable
fun DopplerChart(pass: Pass, now: Long, freqMHz: Double, utc: Boolean, scrub: Long?, onScrub: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val fmt = remember(utc) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }
    Canvas(modifier.scrubbable(pass, onScrub)) {
        val w = size.width; val h = size.height
        val shifts = pass.samples.map { doppler(it.rangeRate, freqMHz) }
        val maxAbs = (shifts.maxOfOrNull { abs(it) } ?: 1.0).coerceAtLeast(0.5)
        fun x(t: Long) = ((t - pass.aos).toFloat() / (pass.los - pass.aos)) * w
        fun y(k: Double) = (h / 2 - (k / maxAbs).toFloat() * h * 0.42f)
        drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val path = Path()
        pass.samples.forEachIndexed { i, s -> val o = Offset(x(s.t), y(shifts[i])); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, Brush.horizontalGradient(listOf(Mint.primary, Sky)), alpha = 0.3f, style = Stroke(12f, cap = StrokeCap.Round))
        drawPath(path, Brush.horizontalGradient(listOf(Mint.primary, Sky)), style = Stroke(3.5f, cap = StrokeCap.Round))
        if (now in pass.aos..pass.los) drawLine(Mint.primary, Offset(x(now), 0f), Offset(x(now), h), 2f)
        drawText(text.measure("+%.1f kHz".format(maxAbs), TextStyle(color = Ink.faint, fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, 2f))
        drawText(text.measure("−%.1f kHz".format(maxAbs), TextStyle(color = Ink.faint, fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, h - 16f))
        if (scrub != null) {
            val i = pass.samples.indices.minBy { abs(pass.samples[it].t - scrub) }
            val sx = x(pass.samples[i].t); val sy = y(shifts[i])
            drawLine(Color.White.copy(alpha = 0.8f), Offset(sx, 0f), Offset(sx, h), 1.5f)
            drawCircle(Color.White, 7f, Offset(sx, sy))
            readout(text, listOf("%+.2f kHz".format(shifts[i]), "%.4f MHz".format(freqMHz + shifts[i] / 1000), fmt.format(Date(pass.samples[i].t))), Offset(sx, sy), size)
        }
    }
}

fun doppler(rangeRateKmS: Double, freqMHz: Double) = -rangeRateKmS / 299_792.458 * freqMHz * 1000.0 // kHz

/* --- Antennenhilfe ---
   V-Dipol: liegt waagerecht, die Öffnung zeigt entlang der Flugrichtung (bei Polarbahnen ungefähr Nord oder Süd).
   Das Zifferblatt dreht sich mit dem Handy; oben ist immer die Richtung, in die das Handy zeigt. */
fun passAxis(p: Pass): Double {
    // Flugrichtung als Peilung vom Aufgang zum Untergang über den Zenit, auf 0..180 gefaltet
    val a = Math.toRadians(p.aosAz); val b = Math.toRadians(p.losAz)
    val dx = sin(b) - sin(a); val dy = cos(b) - cos(a)
    val brg = (Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360) % 360
    return brg
}

@Composable
fun AntennaDial(p: Pass, heading: Float, target: Pair<Double, Double>?, directional: Boolean, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val col = Color(p.sat.color)
    val axis = passAxis(p)
    val cur by rememberUpdatedState(heading)
    Canvas(modifier) {
        val c = center; val r = size.minDimension / 2 * 0.92f
        // Grund: dunkle Scheibe mit feinem Rand
        drawCircle(Brush.radialGradient(listOf(Color(0xFF13233A), Color(0xFF0A1322)), c, r), r, c)
        drawCircle(Color.White.copy(alpha = 0.18f), r, c, style = Stroke(1.5f))
        rotate(-cur, c) {
            for (deg in 0 until 360 step 5) {
                val a = Math.toRadians(deg.toDouble())
                val len = if (deg % 90 == 0) 16f else if (deg % 30 == 0) 10f else 4f
                drawLine(Color.White.copy(alpha = if (deg % 30 == 0) 0.55f else 0.2f), Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat()), Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat()), 1.6f)
            }
            // die Bahn klein auf dem Zifferblatt
            val path = Path()
            p.samples.forEachIndexed { i, s ->
                val rr = ((90 - s.el) / 90).toFloat() * r * 0.82f
                val a = Math.toRadians(s.az)
                val o = Offset(c.x + rr * sin(a).toFloat(), c.y - rr * cos(a).toFloat())
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, col.copy(alpha = 0.25f), style = Stroke(10f, cap = StrokeCap.Round))
            drawPath(path, col, style = Stroke(3f, cap = StrokeCap.Round))
            if (!directional) {
                // V-Dipol: zwei Arme, 120° auseinander, Öffnung entlang der Achse
                val apex = c - Offset(sin(Math.toRadians(axis)).toFloat(), -cos(Math.toRadians(axis)).toFloat()) * (r * 0.28f)
                for (s in listOf(-60.0, 60.0)) {
                    val a = Math.toRadians(axis + s)
                    val end = apex + Offset(sin(a).toFloat(), -cos(a).toFloat()) * (r * 0.62f)
                    drawLine(Mint.primary.copy(alpha = 0.25f), apex, end, 16f, cap = StrokeCap.Round)
                    drawLine(Color.White, apex, end, 5f, cap = StrokeCap.Round)
                }
                drawCircle(Amber, 7f, apex)
            }
        }
        // Himmelsrichtungen bleiben lesbar (nicht mitdrehen, nur mitwandern)
        listOf("N" to 0.0, "O" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (n, az) ->
            val a = Math.toRadians(az - cur)
            label(text, n, Offset(c.x + (r - 30f) * sin(a).toFloat(), c.y - (r - 30f) * cos(a).toFloat()), if (n == "N") Coral else Ink.text, 13, bold = true)
        }
        if (directional && target != null) {
            val off = ((target.first - cur + 540) % 360 - 180).toFloat()
            val ok = abs(off) < 6
            rotate(off, c) {
                val tip = Offset(c.x, c.y - r * 0.78f)
                val path = Path().apply { moveTo(tip.x, tip.y); lineTo(c.x - r * 0.12f, c.y); lineTo(c.x + r * 0.12f, c.y); close() }
                drawPath(path, if (ok) Mint.primary else Amber)
            }
            val sweep = (target.second.coerceIn(0.0, 90.0) / 90.0 * 360).toFloat()
            drawArc(Sky, -90f, sweep, false, topLeft = Offset(c.x - r - 8f, c.y - r - 8f), size = Size((r + 8f) * 2, (r + 8f) * 2), style = Stroke(5f, cap = StrokeCap.Round))
        }
        // Markierung oben: so zeigt das Handy
        val top = Path().apply { moveTo(c.x, c.y - r - 2f); lineTo(c.x - 11f, c.y - r - 20f); lineTo(c.x + 11f, c.y - r - 20f); close() }
        drawPath(top, Color.White)
        drawCircle(Ink.space, 6f, c); drawCircle(Color.White.copy(alpha = 0.6f), 6f, c, style = Stroke(1.5f))
    }
}

// Höhenverlauf als Mini-Kurve in der Liste
@Composable
fun Sparkline(pass: Pass, modifier: Modifier = Modifier) {
    val col = Color(pass.sat.color)
    Canvas(modifier) {
        val path = Path(); val fill = Path()
        pass.samples.forEachIndexed { i, s ->
            val x = i / (pass.samples.size - 1f) * size.width
            val y = size.height - (s.el / 90.0).toFloat() * size.height
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, col, style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}

