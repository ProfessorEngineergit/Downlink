package io.github.professorengineergit.downlink.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Tracker
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/* --- AR: Kamera nach oben halten, Bahnen und Satelliten liegen auf dem echten Himmel.
   Lage aus dem Drehvektor-Sensor, Projektion mit dem Bildwinkel der Hauptkamera. --- */
@Composable
fun ArScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val rot = remember { mutableStateOf(FloatArray(9).also { it[0] = 1f; it[4] = 1f; it[8] = 1f }) }
    val text = rememberTextMeasurer()
    val res = ctx.resources
    val sprites = remember { HashMap<String, ImageBitmap>() }
    vm.live.forEach { lv -> if (lv.sat.sprite !in sprites) sprites[lv.sat.sprite] = ImageBitmap.imageResource(res, spriteRes(lv.sat.sprite)) }
    val s = vm.settings

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val l = object : SensorEventListener {
            val raw = FloatArray(9); val out = FloatArray(9)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, e.values)
                // Bildschirmdrehung berücksichtigen (die App läuft hochkant, aber sicher ist sicher)
                @Suppress("DEPRECATION") val r = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                when (r) {
                    Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, out)
                    Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, out)
                    else -> System.arraycopy(raw, 0, out, 0, 9)
                }
                // leicht glätten
                val prev = rot.value
                rot.value = FloatArray(9) { prev[it] + (out[it] - prev[it]) * 0.25f }
            }
            override fun onAccuracyChanged(sn: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(l) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { c ->
            PreviewView(c).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                    runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview) }
                }, ContextCompat.getMainExecutor(c))
            }
        }, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val R = rot.value
            // Bildwinkel über die Breite (hochkant); typische Hauptkamera mit FILL_CENTER
            val f = (size.width / 2) / tan(Math.toRadians(30.0)).toFloat()
            val c = Offset(size.width / 2, size.height / 2)
            fun proj(az: Double, el: Double): Offset? {
                val a = Math.toRadians(az); val e = Math.toRadians(el)
                val wx = (sin(a) * cos(e)).toFloat(); val wy = (cos(a) * cos(e)).toFloat(); val wz = sin(e).toFloat()
                // Welt (Ost, Nord, oben) -> Gerät: transponierte Rotationsmatrix
                val dx = R[0] * wx + R[3] * wy + R[6] * wz
                val dy = R[1] * wx + R[4] * wy + R[7] * wz
                val dz = R[2] * wx + R[5] * wy + R[8] * wz
                if (dz > -0.05f) return null // hinter der Kamera
                return Offset(c.x + f * dx / -dz, c.y - f * dy / -dz)
            }
            fun line(pts: List<Pair<Double, Double>>, color: Color, w: Float, dash: Boolean = false) {
                val path = Path(); var on = false
                pts.forEach { (az, el) -> val p = proj(az, el); if (p == null) on = false else { if (!on) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y); on = true } }
                drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(10f, 12f)) else null))
            }
            // Horizont und Himmelsrichtungen
            line((0..360 step 3).map { it.toDouble() to 0.0 }, Color.White.copy(alpha = 0.6f), 2f)
            line((0..360 step 3).map { it.toDouble() to 30.0 }, Color.White.copy(alpha = 0.18f), 1.5f, dash = true)
            listOf("N" to 0.0, "NO" to 45.0, "O" to 90.0, "SO" to 135.0, "S" to 180.0, "SW" to 225.0, "W" to 270.0, "NW" to 315.0).forEach { (n, az) ->
                proj(az, 2.0)?.let { chip(text, n, it, if (n == "N") Coral else Color.White, below = false) }
            }
            // Bahnen der nächsten Pässe
            vm.passes.filter { it.los > vm.now && it.aos < vm.now + 12 * 3_600_000L }.take(6).forEach { p ->
                val col = Color(p.sat.color)
                line(p.samples.map { it.az to it.el }, col.copy(alpha = 0.3f), 16f)
                line(p.samples.map { it.az to it.el }, col, 5f)
                proj(p.aosAz, 0.0)?.let { chip(text, "${p.sat.label} ${timeFmt(s.utc).format(java.util.Date(p.aos))}", it, col, below = true) }
            }
            // Sonne
            val sun = Tracker.sunAzEl(s.observer, vm.now)
            proj(sun.first, sun.second)?.let { drawCircle(Amber.copy(alpha = 0.3f), 50f, it); drawCircle(Color(0xFFFFE2A8), 18f, it) }
            // Satelliten jetzt
            vm.live.filter { it.el > -5 }.forEach { lv ->
                proj(lv.az, lv.el)?.let { o ->
                    val col = Color(lv.sat.color)
                    drawCircle(col.copy(alpha = 0.25f), 60f, o)
                    sprites[lv.sat.sprite]?.let { img -> drawImage(img, dstOffset = IntOffset((o.x - 60).toInt(), (o.y - 60).toInt()), dstSize = IntSize(120, 120)) }
                    chip(text, "%s · %.0f°".format(lv.sat.label, lv.el), o + Offset(0f, 54f), col, below = true)
                }
            }
            // Fadenkreuz
            drawCircle(Color.White.copy(alpha = 0.5f), 18f, c, style = Stroke(1.5f))
        }

        Row(Modifier.statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
            Spacer(Modifier.width(12.dp))
            Glass(radius = 20.dp, tint = Color(0x99070D18)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Himmel live", style = MaterialTheme.typography.titleSmall, color = Ink.text)
                    Text("Halte das Handy zum Himmel", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                }
            }
        }
        Text("Kompass kalibrieren: Handy in einer Acht schwenken", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp))
    }
}
