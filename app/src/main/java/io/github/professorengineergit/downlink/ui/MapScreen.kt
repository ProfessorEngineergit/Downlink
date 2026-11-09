package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.globe.Geo
import io.github.professorengineergit.downlink.globe.GLTextureView
import io.github.professorengineergit.downlink.globe.GlobeCamera
import io.github.professorengineergit.downlink.globe.GlobeRenderer
import io.github.professorengineergit.downlink.orbit.SatInfo
import io.github.professorengineergit.downlink.orbit.Tracker
import kotlin.math.cos
import kotlin.math.sin

/* --- Karte: echte Erde als Globus mit Tag und Nacht. Bahnen in Flughöhe, Sichtkreise am Boden,
   die Satelliten als gerenderte Modelle. Antippen wählt einen aus. --- */
@Composable
fun MapScreen(vm: AppViewModel, bottom: Dp, onOpen: (SatInfo) -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val cam = remember { GlobeCamera().apply { lat = s.lat.toDouble() * 0.6; lon = s.lon.toDouble(); dist = 8.2; offsetY = 0.04f } }
    val renderer = remember { GlobeRenderer(ctx, cam) }
    var view by remember { mutableStateOf<GLTextureView?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    var picked by remember { mutableStateOf<String?>(null) }
    val text = rememberTextMeasurer()
    val res = ctx.resources
    val sprites = remember { HashMap<String, ImageBitmap>() }
    vm.live.forEach { lv -> if (lv.sat.sprite !in sprites) sprites[lv.sat.sprite] = ImageBitmap.imageResource(res, spriteRes(lv.sat.sprite)) }

    fun redraw() { view?.requestRender(); tick++ }
    LaunchedEffect(vm.now, s.nightLights, s.atmosphere) {
        val (sl, so) = Tracker.subsolar(vm.now)
        val v = Geo.vec(sl, so)
        renderer.sun = floatArrayOf(v[0].toFloat(), v[1].toFloat(), v[2].toFloat())
        renderer.nightSide = s.nightLights
        renderer.atmosphere = s.atmosphere
        redraw()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = {
            GLTextureView(it).apply { this.renderer = renderer; view = this }
        }, modifier = Modifier.fillMaxSize())

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val k = cam.dist * 0.021
                        cam.lon -= pan.x * k
                        cam.lat = (cam.lat + pan.y * k).coerceIn(-80.0, 80.0)
                        cam.dist = (cam.dist / zoom).coerceIn(2.2, 12.0)
                        redraw()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val tmp = FloatArray(2)
                        picked = vm.live.mapNotNull { lv ->
                            val h = 1 + lv.altKm / 6371.0
                            val v = Geo.vec(lv.lat, lv.lon).let { doubleArrayOf(it[0] * h, it[1] * h, it[2] * h) }
                            cam.project(v, tmp)?.let { lv.sat.key to (Offset(it[0], it[1]) - at).getDistance() }
                        }.filter { it.second < 90f }.minByOrNull { it.second }?.first
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            val tmp = FloatArray(2)
            // Beobachter
            cam.project(Geo.vec(s.lat.toDouble(), s.lon.toDouble()), tmp)?.let {
                val o = Offset(it[0], it[1])
                drawCircle(Coral.copy(alpha = 0.3f), 18f, o); drawCircle(Coral, 7f, o); drawCircle(Color.White, 2.5f, o)
            }
            vm.live.forEach { lv ->
                val col = Color(lv.sat.color)
                val isPicked = picked == lv.sat.key
                val h = 1 + lv.altKm / 6371.0
                // Bodenspur in Flughöhe: vergangen gestrichelt, kommend durchgezogen
                vm.tracks[lv.sat.key]?.let { tr ->
                    val nowIdx = tr.size / 5
                    for (part in 0..1) {
                        val pts = if (part == 0) tr.take(nowIdx + 1) else tr.drop(nowIdx)
                        val path = Path(); var started = false
                        pts.forEach { (la, lo) ->
                            val v = Geo.vec(la, lo).let { doubleArrayOf(it[0] * h, it[1] * h, it[2] * h) }
                            val p = cam.project(v, tmp)
                            if (p == null) started = false
                            else { if (!started) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1]); started = true }
                        }
                        if (part == 1) drawPath(path, col.copy(alpha = if (isPicked) 0.3f else 0.15f), style = Stroke(if (isPicked) 14f else 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(path, col.copy(alpha = if (part == 0) 0.45f else 0.95f), style = Stroke(if (isPicked) 4f else 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                            pathEffect = if (part == 0) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null))
                    }
                }
                // Sichtkreis am Boden
                if (s.footprints) {
                    val rad = Math.toRadians(Tracker.footprintDeg(lv.altKm))
                    val c = Geo.vec(lv.lat, lv.lon)
                    val up = doubleArrayOf(0.0, 1.0, 0.0)
                    val e1 = cross(up, c).norm(); val e2 = cross(c, e1)
                    val path = Path(); var started = false
                    for (k in 0..72) {
                        val a = k / 72.0 * 2 * Math.PI
                        val v = DoubleArray(3) { i -> c[i] * cos(rad) + (e1[i] * cos(a) + e2[i] * sin(a)) * sin(rad) }
                        val p = cam.project(v, tmp)
                        if (p == null) started = false else { if (!started) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1]); started = true }
                    }
                    drawPath(path, col.copy(alpha = if (isPicked) 0.9f else 0.4f), style = Stroke(1.6f))
                }
                // Satellit
                val v = Geo.vec(lv.lat, lv.lon).let { doubleArrayOf(it[0] * h, it[1] * h, it[2] * h) }
                cam.project(v, tmp)?.let {
                    val o = Offset(it[0], it[1])
                    val sz = if (isPicked) 170 else 110
                    drawCircle(col.copy(alpha = 0.22f), sz * 0.45f, o)
                    sprites[lv.sat.sprite]?.let { img -> drawImage(img, dstOffset = IntOffset((o.x - sz / 2).toInt(), (o.y - sz / 2).toInt()), dstSize = IntSize(sz, sz)) }
                    chip(text, lv.sat.label, o + Offset(0f, sz * 0.38f), col, below = true)
                }
            }
        }

        Column(Modifier.statusBarsPadding().padding(start = 24.dp, top = 12.dp)) {
            Text("Karte", style = MaterialTheme.typography.headlineLarge, color = Ink.text)
            Text("${vm.live.size} Objekte · zieh zum Drehen, zwei Finger zum Zoomen", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
        }

        // Ausgewählt: Datenleiste aus Glas
        val lv = vm.live.firstOrNull { it.sat.key == picked }
        if (lv != null) {
            Glass(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottom + 12.dp).fillMaxWidth(), radius = 26.dp, tint = Color(0xCC0A1322), onClick = { onOpen(lv.sat) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SatSprite(lv.sat.sprite, Color(lv.sat.color), Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lv.sat.label, style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("%.1f° %.1f° · %.0f km hoch · %.1f km/s".format(lv.lat, lv.lon, lv.altKm, 7.9 * kotlin.math.sqrt(6371 / (6371 + lv.altKm))), fontFamily = Mono, fontSize = 12.sp, color = Ink.dim)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.0f°".format(lv.el), fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (lv.el > 0) Mint.primary else Ink.dim)
                        Text(if (lv.el > 0) "über dir" else "unter dem Horizont", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                    }
                }
            }
        }
    }
}

private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
private fun DoubleArray.norm(): DoubleArray { val n = kotlin.math.sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2]); return doubleArrayOf(this[0] / n, this[1] / n, this[2] / n) }
private val unusedArrangement = Arrangement.Top
