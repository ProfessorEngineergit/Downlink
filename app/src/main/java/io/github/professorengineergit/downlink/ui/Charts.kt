package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.orbit.Pass

/* --- Höhe über die Zeit und Dopplerverschiebung, mit Markierung für jetzt --- */
@Composable
fun ElevationChart(pass: Pass, now: Long, minEl: Float, modifier: Modifier = Modifier) {
    val col = Color(pass.sat.color)
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(t: Long) = ((t - pass.aos).toFloat() / (pass.los - pass.aos)) * w
        fun y(el: Double) = (h - (el / 90.0).toFloat() * h)
        for (el in listOf(30.0, 60.0)) drawLine(Color(0xFF2E3A33), Offset(0f, y(el)), Offset(w, y(el)), 1f)
        drawLine(Color(0x66FFB4AB), Offset(0f, y(minEl.toDouble())), Offset(w, y(minEl.toDouble())), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val line = Path()
        val fill = Path()
        pass.samples.forEachIndexed { i, s ->
            val o = Offset(x(s.t), y(s.el))
            if (i == 0) { line.moveTo(o.x, o.y); fill.moveTo(o.x, h); fill.lineTo(o.x, o.y) } else { line.lineTo(o.x, o.y); fill.lineTo(o.x, o.y) }
        }
        fill.lineTo(w, h); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, col, style = Stroke(3.5f, cap = StrokeCap.Round))
        if (now in pass.aos..pass.los) drawLine(Color.White, Offset(x(now), 0f), Offset(x(now), h), 2f)
        listOf("30°" to 30.0, "60°" to 60.0).forEach { (l, e) ->
            drawText(text.measure(l, TextStyle(color = Color(0xFF6E7A72), fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, y(e) - 16f))
        }
    }
}

@Composable
fun DopplerChart(pass: Pass, now: Long, freqMHz: Double, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Verschiebung in kHz: -v/c * f
        val shifts = pass.samples.map { -it.rangeRate / 299_792.458 * freqMHz * 1000.0 }
        val maxAbs = (shifts.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0).coerceAtLeast(0.5)
        fun x(t: Long) = ((t - pass.aos).toFloat() / (pass.los - pass.aos)) * w
        fun y(k: Double) = (h / 2 - (k / maxAbs).toFloat() * h * 0.42f)
        drawLine(Color(0xFF2E3A33), Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val path = Path()
        pass.samples.forEachIndexed { i, s -> val o = Offset(x(s.t), y(shifts[i])); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, Brush.horizontalGradient(listOf(Mint.primary, Sky)), style = Stroke(3f, cap = StrokeCap.Round))
        if (now in pass.aos..pass.los) drawLine(Color.White, Offset(x(now), 0f), Offset(x(now), h), 2f)
        drawText(text.measure("+%.1f kHz".format(maxAbs), TextStyle(color = Color(0xFF6E7A72), fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, 2f))
        drawText(text.measure("−%.1f kHz".format(maxAbs), TextStyle(color = Color(0xFF6E7A72), fontSize = 10.sp, fontFamily = Mono)), topLeft = Offset(4f, h - 16f))
    }
}

// Kompassnadel: wohin die Antenne zeigen soll, relativ zur Blickrichtung des Handys
@Composable
fun AimNeedle(targetAz: Double, heading: Float, elevation: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.9f
        drawCircle(Color(0xFF1B211D), r, c)
        drawCircle(Color(0xFF404943), r, c, style = Stroke(2f))
        val off = ((targetAz - heading + 540) % 360 - 180).toFloat()
        val onTarget = kotlin.math.abs(off) < 8
        rotate(off, c) {
            val tip = Offset(c.x, c.y - r * 0.82f)
            val path = Path().apply { moveTo(tip.x, tip.y); lineTo(c.x - r * 0.16f, c.y); lineTo(c.x + r * 0.16f, c.y); close() }
            drawPath(path, if (onTarget) Mint.primary else Amber)
        }
        drawCircle(Color(0xFF0F1511), r * 0.12f, c)
        // Höhe als Bogen außen
        val sweep = (elevation.coerceIn(0.0, 90.0) / 90.0 * 360).toFloat()
        drawArc(Mint.primary.copy(alpha = 0.6f), -90f, sweep, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(6f, cap = StrokeCap.Round))
    }
}

// Höhenverlauf als Mini-Kurve in der Liste
@Composable
fun Sparkline(pass: Pass, modifier: Modifier = Modifier) {
    val col = Color(pass.sat.color)
    Canvas(modifier) {
        val path = Path()
        pass.samples.forEachIndexed { i, s ->
            val x = i / (pass.samples.size - 1f) * size.width
            val y = size.height - (s.el / 90.0).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, col, style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}
