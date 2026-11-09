package io.github.professorengineergit.downlink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.orbit.Live
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Sample
import io.github.professorengineergit.downlink.orbit.Tracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/* --- Der Himmel als Kuppel von oben: Mitte ist der Zenit, der Rand der Horizont.
   Die Farben folgen der Sonne, die Kuppel läuft an den Seiten aus, Bahnen lassen sich antippen,
   und mit langem Druck fährt man mit dem Finger die Bahn ab und liest die genauen Werte. --- */
@Composable
fun SkyDome(
    passes: List<Pass>,
    focus: Pass?,
    live: List<Live>,
    minEl: Float,
    sun: Pair<Double, Double>,
    heading: Float,
    utc: Boolean,
    scrub: Long?,
    onSelect: (Pass?) -> Unit,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    radiusFactor: Float = 0.56f,
    showSun: Boolean = true,
    haptics: Boolean = true,
) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(focus?.aos, focus?.sat?.key) { draw.snapTo(0f); draw.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    val inf = rememberInfiniteTransition(label = "sky")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val twinkle by inf.animateFloat(0f, 6.283f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val text = rememberTextMeasurer()
    val fmt = remember(utc) { SimpleDateFormat("HH:mm", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }
    val fmtS = remember(utc) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") } }
    val tone = skyTone(sun.second)
    val haptic = LocalHapticFeedback.current

    // Satellitenbilder für die Live-Punkte, einmal geladen
    val res = LocalContext.current.resources
    val sprites = remember { HashMap<String, ImageBitmap>() }
    live.forEach { lv -> if (lv.sat.sprite !in sprites) sprites[lv.sat.sprite] = ImageBitmap.imageResource(res, spriteRes(lv.sat.sprite)) }

    val cur by rememberUpdatedState(Triple(passes, focus, heading))
    var geo = remember { floatArrayOf(0f, 0f, 1f) } // cx, cy, r, für die Gesten

    fun posOf(az: Double, el: Double): Offset {
        val r = ((90 - el) / 90).toFloat().coerceIn(0f, 1.1f) * geo[2]
        val a = Math.toRadians(az - cur.third)
        return Offset(geo[0] + r * sin(a).toFloat(), geo[1] - r * cos(a).toFloat())
    }
    fun nearest(p: Pass, at: Offset): Pair<Sample, Float> = p.samples.map { it to (posOf(it.az, it.el) - at).getDistance() }.minBy { it.second }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val hit = cur.first.map { it to nearest(it, at).second }.filter { it.second < 70f }.minByOrNull { it.second }?.first
                    if (hit != null && haptics) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onSelect(hit)
                }
            }
            .pointerInput(Unit) {
                var last: Long? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        val p = cur.second ?: cur.first.minByOrNull { nearest(it, at).second } ?: return@detectDragGesturesAfterLongPress
                        if (p != cur.second) onSelect(p)
                        last = nearest(p, at).first.t; onScrub(last)
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { ch, _ ->
                        val p = cur.second ?: return@detectDragGesturesAfterLongPress
                        val t = nearest(p, ch.position).first.t
                        if (t != last) { last = t; onScrub(t); if (haptics && (t / 10_000) % 6 == 0L) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
                    },
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) },
                )
            },
    ) {
        val r = size.width * radiusFactor
        val c = Offset(size.width / 2, size.height / 2)
        geo[0] = c.x; geo[1] = c.y; geo[2] = r
        fun pos(az: Double, el: Double) = posOf(az, el)

        // --- Himmel: Halo nach außen, Farbverlauf vom Zenit zum Horizont, Sonnenschein am Rand
        drawCircle(Brush.radialGradient(0.6f to tone.horizon.copy(alpha = 0.45f), 1f to Color.Transparent, center = c, radius = r * 1.45f), r * 1.45f, c)
        drawCircle(Brush.radialGradient(0f to tone.zenith, 0.55f to tone.mid, 0.92f to tone.horizon, 1f to tone.horizon.copy(alpha = 0.85f), center = c, radius = r), r, c)
        val sunP = pos(sun.first, sun.second.coerceAtLeast(-4.0))
        val sunGlow = ((sun.second + 18) / 26).toFloat().coerceIn(0f, 1f)
        if (sunGlow > 0f) drawCircle(Brush.radialGradient(listOf(tone.glow.copy(alpha = 0.75f * sunGlow), tone.glow.copy(alpha = 0.18f * sunGlow), Color.Transparent), sunP, r * 0.95f), r * 0.95f, sunP)
        // Sterne funkeln, je dunkler desto mehr
        if (tone.stars > 0.02f) STARS.forEachIndexed { i, (sx, sy, b) ->
            val tw = 0.65f + 0.35f * sin(twinkle + i * 1.7f)
            drawCircle(Color.White.copy(alpha = (b * tone.stars * tw).coerceIn(0f, 1f)), 1.1f + b * 2.2f, Offset(c.x + sx * r, c.y + sy * r))
        }
        // Raster: Höhenringe, Achsen, Gradstriche am Horizont
        for (el in listOf(30, 60)) drawCircle(Color.White.copy(alpha = 0.08f), (90 - el) / 90f * r, c, style = Stroke(1.2f))
        drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(1.4f))
        rotate(-heading, c) {
            for (deg in 0 until 360 step 5) {
                val a = Math.toRadians(deg.toDouble())
                val len = if (deg % 90 == 0) 18f else if (deg % 30 == 0) 11f else if (deg % 10 == 0) 6f else 3f
                drawLine(Color.White.copy(alpha = if (deg % 30 == 0) 0.5f else 0.22f), Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat()), Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat()), 1.6f)
            }
            drawLine(Color.White.copy(alpha = 0.06f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            drawLine(Color.White.copy(alpha = 0.06f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        }
        listOf("N" to 0.0, "O" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (n, az) ->
            val p = pos(az, 8.0)
            if (p.x in 12f..size.width - 12f) label(text, n, p, if (n == "N") Coral else Ink.text.copy(alpha = 0.8f), 13, bold = true)
        }
        label(text, "30°", Offset(c.x + 16f, c.y - (60 / 90f) * r + 12f), Color.White.copy(alpha = 0.35f), 10)
        label(text, "60°", Offset(c.x + 16f, c.y - (30 / 90f) * r + 12f), Color.White.copy(alpha = 0.35f), 10)
        // Unter der Mindesthöhe: gestrichelte Grenze
        val maskR = ((90 - minEl) / 90f) * r
        drawCircle(Coral.copy(alpha = 0.35f), maskR, c, style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 9f))))

        // Sonne
        if (showSun && sun.second > -8) {
            drawCircle(Amber.copy(alpha = 0.25f), 26f, sunP)
            drawCircle(Color(0xFFFFE2A8), 10f, sunP)
        }

        // --- Bahnen
        passes.filter { it != focus }.forEach { p ->
            val col = Color(p.sat.color)
            drawPassPath(p, ::pos, col.copy(alpha = 0.16f), 12f, 1f)
            drawPassPath(p, ::pos, col.copy(alpha = 0.55f), 3f, 1f)
            val a = pos(p.aosAz, 0.0)
            drawCircle(col.copy(alpha = 0.7f), 4f, a)
        }
        if (focus != null) {
            val col = Color(focus.sat.color)
            drawPassPath(focus, ::pos, col.copy(alpha = 0.12f), 34f, draw.value)
            drawPassPath(focus, ::pos, col.copy(alpha = 0.25f), 14f, draw.value)
            drawPassGradient(focus, ::pos, col, draw.value)
            // Minutenpunkte
            val n = (focus.samples.size * draw.value).toInt()
            focus.samples.take(n).forEachIndexed { k, smp ->
                if (k == 0 || k % 6 != 0) return@forEachIndexed
                val o = pos(smp.az, smp.el)
                drawCircle(Color.Black.copy(alpha = 0.5f), 4.5f, o)
                drawCircle(Color.White.copy(alpha = 0.9f), 2.4f, o)
            }
            val a = pos(focus.aosAz, 0.0)
            val tca = focus.samples.maxBy { it.el }
            val t = pos(tca.az, tca.el)
            val l = pos(focus.losAz, 0.0)
            drawCircle(col, 8f, a); drawCircle(Ink.space, 3.5f, a)
            chip(text, fmt.format(Date(focus.aos)), a, col, below = true)
            if (draw.value > 0.5f) { drawCircle(Color.White, 6f, t); chip(text, "%.0f°".format(focus.maxEl), t, Color.White, below = false) }
            if (draw.value > 0.95f) {
                val before = focus.samples[(focus.samples.size - 4).coerceAtLeast(0)]
                val bo = pos(before.az, before.el)
                val ang = kotlin.math.atan2((l.y - bo.y).toDouble(), (l.x - bo.x).toDouble())
                val head = Path().apply {
                    moveTo(l.x + 16f * cos(ang).toFloat(), l.y + 16f * sin(ang).toFloat())
                    lineTo(l.x + 10f * cos(ang + 2.4).toFloat(), l.y + 10f * sin(ang + 2.4).toFloat())
                    lineTo(l.x + 10f * cos(ang - 2.4).toFloat(), l.y + 10f * sin(ang - 2.4).toFloat())
                    close()
                }
                drawPath(head, Sky)
                chip(text, fmt.format(Date(focus.los)), l, Sky, below = true)
            }
            // Fingerabfrage: Marker auf der Bahn und genaue Werte
            if (scrub != null) {
                val smp = focus.samples.minBy { kotlin.math.abs(it.t - scrub) }
                val o = pos(smp.az, smp.el)
                drawCircle(Color.White.copy(alpha = 0.18f), 30f, o)
                drawCircle(Color.White, 11f, o, style = Stroke(3f))
                drawCircle(col, 6f, o)
                // Linie zum Rand: Richtung am Horizont
                val rim = pos(smp.az, 0.0)
                drawLine(Color.White.copy(alpha = 0.5f), o, rim, 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                readout(text, listOf(fmtS.format(Date(smp.t)), "El %.1f°".format(smp.el), "Az %.0f° %s".format(smp.az, Tracker.compass(smp.az)), "%.0f km".format(smp.range)), o, size)
            }
        }

        // --- Live: was gerade über dem Horizont steht
        live.filter { it.el > -3 }.forEach { lv ->
            val p = pos(lv.az, lv.el)
            val col = Color(lv.sat.color)
            drawCircle(col.copy(alpha = 0.4f * (1 - pulse)), 14f + 34f * pulse, p)
            sprites[lv.sat.sprite]?.let { img ->
                val s = 64
                drawImage(img, dstOffset = IntOffset((p.x - s / 2).toInt(), (p.y - s / 2).toInt()), dstSize = IntSize(s, s))
            } ?: drawCircle(col, 8f, p)
            label(text, lv.sat.label, p + Offset(0f, -44f), col, 11, bold = true)
        }
    }
}

// feste Sterne in der Kuppel
private val STARS = kotlin.random.Random(11).let { rnd ->
    List(130) {
        val a = rnd.nextDouble(0.0, 2 * Math.PI)
        val d = kotlin.math.sqrt(rnd.nextDouble()) * 0.98
        Triple((d * cos(a)).toFloat(), (d * sin(a)).toFloat(), (0.1 + 0.6 * Math.pow(rnd.nextDouble(), 3.0)).toFloat())
    }
}

private fun DrawScope.drawPassPath(p: Pass, pos: (Double, Double) -> Offset, color: Color, width: Float, share: Float) {
    val path = Path()
    val n = (p.samples.size * share).toInt().coerceAtLeast(1)
    p.samples.take(n).forEachIndexed { i, s -> val o = pos(s.az, s.el); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// Farbe läuft mit der Zeit: am Anfang satt, zum Ende kühles Blau
private fun DrawScope.drawPassGradient(p: Pass, pos: (Double, Double) -> Offset, color: Color, share: Float) {
    val n = (p.samples.size * share).toInt()
    for (i in 1 until n) {
        val a = pos(p.samples[i - 1].az, p.samples[i - 1].el)
        val b = pos(p.samples[i].az, p.samples[i].el)
        val f = i / p.samples.size.toFloat()
        drawLine(lerpColor(color, Sky, f), a, b, 5.5f, cap = StrokeCap.Round)
    }
}

fun lerpColor(a: Color, b: Color, f: Float) = Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f)

fun DrawScope.label(m: TextMeasurer, s: String, at: Offset, color: Color, sp: Int, bold: Boolean = false) {
    val r = m.measure(s, TextStyle(color = color, fontSize = sp.sp, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(r, topLeft = Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f))
}

// Beschriftung mit dunkler Kapsel, damit sie auf jedem Himmel lesbar bleibt
fun DrawScope.chip(m: TextMeasurer, s: String, at: Offset, color: Color, below: Boolean) {
    val r = m.measure(s, TextStyle(color = color, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold))
    val w = r.size.width + 16f; val h = r.size.height + 8f
    val tl = Offset(at.x - w / 2, if (below) at.y + 14f else at.y - 14f - h)
    drawRoundRect(Color(0xB304070D), tl, Size(w, h), CornerRadius(h / 2))
    drawText(r, topLeft = Offset(tl.x + 8f, tl.y + 4f))
}

// Ablesekasten neben dem Finger, bleibt im Bild
fun DrawScope.readout(m: TextMeasurer, lines: List<String>, at: Offset, area: Size) {
    val rs = lines.mapIndexed { i, s -> m.measure(s, TextStyle(color = if (i == 0) Color.White else Ink.text.copy(alpha = 0.85f), fontSize = if (i == 0) 13.sp else 12.sp, fontFamily = Mono, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)) }
    val w = rs.maxOf { it.size.width } + 28f
    val h = rs.sumOf { it.size.height } + 22f
    var x = at.x + 28f; var y = at.y - h - 20f
    if (x + w > area.width - 8f) x = at.x - w - 28f
    if (y < 8f) y = at.y + 28f
    drawRoundRect(Color(0xE6070D18), Offset(x, y), Size(w, h), CornerRadius(18f))
    var yy = y + 11f
    rs.forEach { drawText(it, topLeft = Offset(x + 14f, yy)); yy += it.size.height }
}

fun hypotOf(o: Offset) = hypot(o.x, o.y)
