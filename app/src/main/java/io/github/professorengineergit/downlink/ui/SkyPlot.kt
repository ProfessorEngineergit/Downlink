package io.github.professorengineergit.downlink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.orbit.Live
import io.github.professorengineergit.downlink.orbit.Pass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/* --- Die Himmelskarte: Mitte ist der Zenit, Rand der Horizont, Norden oben (oder dort, wo das Handy hinzeigt) ---
   Der Hauptpass leuchtet im Verlauf seiner Zeit, die Bahn zeichnet sich beim Wechsel neu ein. */
@Composable
fun SkyPlot(
    focus: Pass?,
    others: List<Pass>,
    live: List<Live>,
    minEl: Float,
    sun: Pair<Double, Double>?, // Azimut, Höhe
    heading: Float,
    utc: Boolean,
    modifier: Modifier = Modifier,
) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(focus?.aos, focus?.sat?.key) { draw.snapTo(0f); draw.animateTo(1f, tween(1400)) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val text = rememberTextMeasurer()
    val fmt = remember(utc) { SimpleDateFormat("HH:mm", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }

    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f
        val c = Offset(size.width / 2, size.height / 2)
        fun pos(az: Double, el: Double): Offset {
            val rr = ((90 - el) / 90).toFloat().coerceIn(0f, 1.08f) * r
            val a = Math.toRadians(az - heading)
            return Offset(c.x + rr * sin(a).toFloat(), c.y - rr * cos(a).toFloat())
        }

        // Grund: tiefer Nachthimmel mit leuchtendem Rand
        drawCircle(Brush.radialGradient(0f to Color(0xFF0A120E), 0.75f to Color(0xFF0F1B15), 1f to Color(0xFF16261D), center = c, radius = r), r, c)
        STARS.forEach { (sx, sy, b) -> drawCircle(Color.White.copy(alpha = b), 1.2f + b * 1.6f, Offset(c.x + sx * r, c.y + sy * r)) }
        drawCircle(Brush.radialGradient(0.9f to Color.Transparent, 1f to Mint.primary.copy(alpha = 0.18f), center = c, radius = r * 1.06f), r * 1.06f, c)
        drawCircle(Mint.primary.copy(alpha = 0.35f), r, c, style = Stroke(1.5f))
        // unter der Mindesthöhe: zarter Ring zwischen Grenze und Horizont
        val maskR = ((90 - minEl) / 90f) * r
        drawCircle(Color(0x12FFB4AB), (r + maskR) / 2, c, style = Stroke(r - maskR))
        drawCircle(Color(0x55FFB4AB), maskR, c, style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))))

        for (el in listOf(0, 30, 60)) drawCircle(Color(0xFF2E3A33), (90 - el) / 90f * r, c, style = Stroke(1.5f))
        rotate(-heading, c) {
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians(deg.toDouble())
                val len = if (deg % 90 == 0) 16f else if (deg % 30 == 0) 10f else 5f
                val o = Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
                val i = Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat())
                drawLine(Color(0xFF55625A), i, o, 2f)
            }
            drawLine(Color(0xFF2E3A33), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            drawLine(Color(0xFF2E3A33), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        }
        listOf("N" to 0.0, "O" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (n, az) ->
            label(text, n, pos(az, -12.0), if (n == "N") Color(0xFFFFB4AB) else Color(0xFFC0C9C0), 13)
        }
        label(text, "30°", Offset(c.x + 6f, c.y - (60 / 90f) * r + 10f), Color(0xFF6E7A72), 10)
        label(text, "60°", Offset(c.x + 6f, c.y - (30 / 90f) * r + 10f), Color(0xFF6E7A72), 10)

        // Sonne, damit man sieht, ob man gegen das Licht steht
        if (sun != null && sun.second > -6) {
            val s = pos(sun.first, sun.second.coerceAtLeast(0.0))
            drawCircle(Amber.copy(alpha = 0.2f), 20f, s)
            drawCircle(Amber, 8f, s)
        }

        others.forEach { p -> drawPassPath(p, ::pos, Color(p.sat.color).copy(alpha = 0.28f), 3f, 1f) }

        if (focus != null) {
            val col = Color(focus.sat.color)
            drawPassPath(focus, ::pos, col.copy(alpha = 0.22f), 16f, draw.value)
            drawPassGradient(focus, ::pos, col, draw.value)
            // Minutenmarken entlang der Bahn, alle fünf Minuten beschriftet
            val n = (focus.samples.size * draw.value).toInt()
            focus.samples.take(n).forEachIndexed { k, smp ->
                if (k == 0 || k % 6 != 0) return@forEachIndexed
                val o = pos(smp.az, smp.el)
                val minute = k / 6
                drawCircle(Color(0xFF0F1511), 4.5f, o)
                drawCircle(Color.White.copy(alpha = 0.85f), 2.2f, o)
                if (minute % 5 == 0) label(text, "+$minute", o + Offset(16f, -12f), Color.White.copy(alpha = 0.7f), 10)
            }
            val a = pos(focus.aosAz, 0.0)
            val tca = focus.samples.maxBy { it.el }
            val t = pos(tca.az, tca.el)
            val l = pos(focus.losAz, 0.0)
            drawCircle(col, 7f, a)
            drawCircle(Color(0xFF0F1511), 3f, a)
            label(text, fmt.format(Date(focus.aos)), a + Offset(0f, 18f), col, 11)
            if (draw.value > 0.5f) {
                drawCircle(Color.White, 5f, t)
                label(text, "%.0f°".format(focus.maxEl), t + Offset(0f, -18f), Color.White, 12)
            }
            if (draw.value > 0.95f) {
                // Pfeil in Flugrichtung am Ende
                val before = focus.samples[focus.samples.size - 4]
                val bo = pos(before.az, before.el)
                val ang = kotlin.math.atan2((l.y - bo.y).toDouble(), (l.x - bo.x).toDouble())
                val head = Path().apply {
                    moveTo(l.x + 14f * cos(ang).toFloat(), l.y + 14f * sin(ang).toFloat())
                    lineTo(l.x + 9f * cos(ang + 2.4).toFloat(), l.y + 9f * sin(ang + 2.4).toFloat())
                    lineTo(l.x + 9f * cos(ang - 2.4).toFloat(), l.y + 9f * sin(ang - 2.4).toFloat())
                    close()
                }
                drawPath(head, col)
                label(text, fmt.format(Date(focus.los)), l + Offset(0f, 18f), col.copy(alpha = 0.8f), 11)
            }
        }

        // Live: was gerade über dem Horizont steht, pulsiert
        live.filter { it.el > -2 }.forEach { lv ->
            val p = pos(lv.az, lv.el)
            val col = Color(lv.sat.color)
            drawCircle(col.copy(alpha = 0.35f * (1 - pulse)), 10f + 26f * pulse, p)
            drawCircle(col, 8f, p)
            drawCircle(Color.White, 3f, p)
            label(text, lv.sat.label, p + Offset(0f, -20f), col, 11)
        }
    }
}

// feste Sterne im Kreis, damit die Karte nach Nacht aussieht
private val STARS = kotlin.random.Random(7).let { rnd ->
    List(70) {
        val a = rnd.nextDouble(0.0, 2 * Math.PI)
        val d = kotlin.math.sqrt(rnd.nextDouble()) * 0.97
        Triple((d * cos(a)).toFloat(), (d * sin(a)).toFloat(), (0.08 + 0.4 * Math.pow(rnd.nextDouble(), 3.0)).toFloat())
    }
}

private fun DrawScope.drawPassPath(p: Pass, pos: (Double, Double) -> Offset, color: Color, width: Float, share: Float) {
    val path = Path()
    val n = (p.samples.size * share).toInt().coerceAtLeast(1)
    p.samples.take(n).forEachIndexed { i, s -> val o = pos(s.az, s.el); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

// Farbe läuft mit der Zeit: am Anfang satt, zum Ende kühler
private fun DrawScope.drawPassGradient(p: Pass, pos: (Double, Double) -> Offset, color: Color, share: Float) {
    val n = (p.samples.size * share).toInt()
    for (i in 1 until n) {
        val a = pos(p.samples[i - 1].az, p.samples[i - 1].el)
        val b = pos(p.samples[i].az, p.samples[i].el)
        val f = i / p.samples.size.toFloat()
        drawLine(lerp(color, Color(0xFF7CC8FF), f), a, b, 5f, cap = StrokeCap.Round)
    }
}

private fun lerp(a: Color, b: Color, f: Float) = Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)

private fun DrawScope.label(m: TextMeasurer, s: String, at: Offset, color: Color, sp: Int) {
    val r = m.measure(s, TextStyle(color = color, fontSize = sp.sp, fontFamily = Mono))
    drawText(r, topLeft = Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f))
}
