package io.github.professorengineergit.downlink.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import io.github.professorengineergit.downlink.R
import androidx.compose.foundation.layout.requiredSize

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Sample
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.sin

/* --- Einführung in vier Bildern:
   1. Die Atmosphäre im Schnitt, oben Meteor-M auf 830 km, das Signal fällt durch alle Schichten zur Antenne.
   2. Wann: die Kuppel mit einer Bahn.
   3. Was hast du? Die Antwort stellt Objekte und Profil ein.
   4. Standort, Erinnerungen, los. --- */
@Composable
fun Intro(vm: AppViewModel, onLocate: () -> Unit, onNotify: () -> Unit, onDone: () -> Unit) {
    val state = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val inf = rememberInfiniteTransition(label = "intro")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Box(Modifier.fillMaxSize().background(Ink.space)) {
        HorizontalPager(state, Modifier.fillMaxSize()) { i ->
            when (i) {
                0 -> PageAtmosphere()
                1 -> PageWhen(vm)
                2 -> PageGear(vm)
                else -> PageGo(vm, onLocate, onNotify)
            }
        }
        // Kopf: Überspringen
        TextButton(onDone, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Text("Überspringen", color = Ink.text.copy(alpha = 0.8f)) }
        // Fuß: Punkte und Weiter
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { k ->
                    val on = state.currentPage == k
                    Box(Modifier.size(if (on) 26.dp else 8.dp, 8.dp).clip(CircleShape).background(if (on) Mint.primary else Color.White.copy(alpha = 0.25f)))
                }
            }
            val last = state.currentPage == 3
            Button(
                { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
                colors = ButtonDefaults.buttonColors(containerColor = Mint.primary, contentColor = Mint.onPrimary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) { Text(if (last) "Los geht's" else "Weiter", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

// Höhe in km auf die Bildhöhe: logarithmisch, damit Troposphäre und 830 km zusammen passen
private fun altY(km: Double, top: Float, bottom: Float): Float {
    val f = (ln(1 + km) / ln(1 + 1200.0)).toFloat()
    return bottom - f * (bottom - top)
}

@Composable
private fun PageAtmosphere() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cam = remember { io.github.professorengineergit.downlink.globe.GlobeCamera().apply { lat = 34.0; lon = 25.0; dist = 5.0; offsetX = 0.3f; offsetY = -0.62f } }
    val renderer = remember {
        io.github.professorengineergit.downlink.globe.GlobeRenderer(ctx, cam).apply {
            val e = io.github.professorengineergit.downlink.globe.Geo.vec(cam.lat, cam.lon)
            val up = doubleArrayOf(0.0, 1.0, 0.0)
            val r = doubleArrayOf(up[1] * e[2] - up[2] * e[1], up[2] * e[0] - up[0] * e[2], up[0] * e[1] - up[1] * e[0])
            val u = doubleArrayOf(e[1] * r[2] - e[2] * r[1], e[2] * r[0] - e[0] * r[2], e[0] * r[1] - e[1] * r[0])
            sun = FloatArray(3) { i -> (e[i] * 0.45 - r[i] * 0.8 + u[i] * 0.5).toFloat() }
            nightSide = true; atmosphere = true
        }
    }
    var view by remember { mutableStateOf<io.github.professorengineergit.downlink.globe.GLTextureView?>(null) }
    // Erde dreht sich langsam, mit 30 Bildern pro Sekunde reicht das völlig
    LaunchedEffect(view) {
        var last = 0L
        while (true) androidx.compose.runtime.withFrameNanos { n ->
            if (last == 0L) last = n
            if (n - last >= 33_000_000L) { cam.lon += (n - last) / 1e9 * 2.2; last = n; view?.requestRender() }
        }
    }
    // Alle 700 ms ein anderer Satellit
    val shots = remember { listOf("meteor" to R.drawable.intro_meteor, "iss" to R.drawable.intro_iss, "tiangong" to R.drawable.intro_tiangong, "metop" to R.drawable.intro_metop, "hubble" to R.drawable.intro_hubble, "npp" to R.drawable.intro_npp, "aqua" to R.drawable.intro_aqua, "landsat" to R.drawable.intro_landsat) }
    var shot by remember { mutableStateOf(0) }
    val fly = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(shot) { fly.snapTo(0f); fly.animateTo(1f, androidx.compose.animation.core.tween(700, easing = LinearEasing)) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(700); shot = (shot + 1) % shots.size } }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { io.github.professorengineergit.downlink.globe.GLTextureView(it).apply { bufferScale = 0.55f; this.renderer = renderer; view = this } }, modifier = Modifier.fillMaxSize())
        // Der Satellit, riesig über dem Horizont
        BoxWithConstraints(Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(shots[shot].second), null,
                Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.12f).requiredSize(maxWidth * 1.3f, maxHeight * 0.44f)
                    .graphicsLayer {
                        // von links hereinfliegen, kurz stehen, nach rechts hinaus
                        val p = fly.value
                        val e = { x: Float -> 1f - (1f - x) * (1f - x) * (1f - x) }
                        translationX = when {
                            p < 0.32f -> -(1f - e(p / 0.32f)) * size.width * 0.8f
                            p > 0.78f -> (((p - 0.78f) / 0.22f).let { it * it }) * size.width * 0.8f
                            else -> 0f
                        }
                        alpha = when { p < 0.32f -> 0.2f + 0.8f * e(p / 0.32f); p > 0.78f -> 1f - (p - 0.78f) / 0.22f * 0.8f; else -> 1f }
                    },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Column(Modifier.statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 44.dp)) {
            Text("CONNECT TO REAL SATELLITES", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
            Text("Direkt aus dem All.\nOhne Internet.", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp), color = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.space.copy(alpha = 0.6f), Ink.space.copy(alpha = 0.92f)))).padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wettersatelliten funken ihre Bilder offen zur Erde. Kein Server, kein Konto. Nur das Signal und deine Antenne.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.text.copy(alpha = 0.9f))
            MadeInEu()
        }
    }
}

@Composable
private fun PageWhen(vm: AppViewModel) {
    val demo = remember {
        // eine schöne Beispielbahn, falls noch keine echten Pässe berechnet sind
        val now = System.currentTimeMillis()
        val samples = (0..84).map { i ->
            val f = i / 84.0
            Sample(now + i * 10_000L, (330 + 150 * f) % 360, 72 * sin(Math.PI * f), -6.5 + 13 * f, 2600 - 1800 * sin(Math.PI * f))
        }
        Pass(Catalog.first(), samples.first().t, samples.last().t, samples[42].t, 72.0, samples)
    }
    val p = vm.passes.maxByOrNull { it.maxEl } ?: demo
    var scrub by remember { mutableStateOf<Long?>(null) }
    Box(Modifier.fillMaxSize()) {
        val tone = skyTone(-10.0)
        Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(listOf(tone.mid, Ink.space))))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp)) {
            Column(Modifier.padding(horizontal = 28.dp)) {
                Text("WANN", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
                Text("Downlink weiß, wann er über dir ist.", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                SkyDome(listOf(p), p, emptyList(), 15f, 200.0 to -10.0, 0f, false, scrub, {}, { scrub = it }, Modifier.fillMaxWidth().height(maxWidth * 1.0f), radiusFactor = 0.5f, showSun = false)
            }
            Text("Die Bahnen rechnet dein Handy selbst, aus Bahndaten, die einmal pro Woche reichen. Halte und zieh über die Bahn: Uhrzeit, Höhe und Richtung auf die Sekunde.",
                style = MaterialTheme.typography.bodyLarge, color = Ink.text.copy(alpha = 0.9f), modifier = Modifier.padding(horizontal = 28.dp))
        }
    }
}

private data class Gear(val key: String, val title: String, val text: String, val sats: Set<String>, val nerd: Boolean, val sprite: String)

private val GEARS = listOf(
    Gear("phone", "Nur mein Handy", "Raumstationen und Hubble mit bloßem Auge sehen, dazu alle Bahnen.", setOf("iss", "css", "hst"), false, "iss"),
    Gear("sdr", "SDR-Stick und Antenne", "Wetterbilder von Meteor-M empfangen. Ein Stick für 30 Euro und zwei Drähte reichen.", setOf("m23", "m24", "iss", "css"), false, "meteor"),
    Gear("dish", "Schüssel und Nachführung", "Hochaufgelöste Bilder von Metop und NOAA im L- und X-Band.", setOf("m23", "m24", "mb", "mc", "n20", "n21", "npp", "iss"), true, "metop"),
)

@Composable
private fun PageGear(vm: AppViewModel) {
    val s = vm.settings
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(Color(0xFF16305A), Ink.space))))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text("WAS HAST DU?", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
                Text("Downlink stellt sich darauf ein.", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            GEARS.forEach { g ->
                val on = s.gear == g.key
                Glass(Modifier.fillMaxWidth(), radius = 26.dp, tint = if (on) Mint.primary.copy(alpha = 0.16f) else Ink.glass, outline = if (on) Ink.outlineOn else Ink.outline, onClick = {
                    s.gear = g.key; s.enabled = g.sats; s.nerd = g.nerd; s.save(); vm.recompute()
                }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SatSprite(g.sprite, if (on) Mint.primary else Sky, Modifier.size(72.dp), glow = on)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium, color = if (on) Mint.primary else Ink.text)
                            Text(g.text, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                        }
                        if (on) Icon(Icons.Rounded.Check, null, tint = Mint.primary)
                    }
                }
            }
            Text("Du kannst das jederzeit unter Objekte und Optionen ändern. Was du für den Funkempfang brauchst, steht unter Optionen → Empfang & Zubehör.", style = MaterialTheme.typography.bodySmall, color = Ink.dim, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun PageGo(vm: AppViewModel, onLocate: () -> Unit, onNotify: () -> Unit) {
    val s = vm.settings
    var located by remember { mutableStateOf(false) }
    var notified by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(Color(0xFF0E3A3A), Ink.space))))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text("FAST FERTIG", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
                Text("Wo bist du, und darf Downlink dich wecken?", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Glass(Modifier.fillMaxWidth(), radius = 26.dp, tint = if (located) Mint.primary.copy(alpha = 0.16f) else Ink.glass, outline = if (located) Ink.outlineOn else Ink.outline, onClick = { onLocate(); located = true }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MyLocation, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Standort", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("Ungefähr reicht. Er bleibt auf dem Gerät.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    if (located) Icon(Icons.Rounded.Check, null, tint = Mint.primary)
                }
            }
            Glass(Modifier.fillMaxWidth(), radius = 26.dp, tint = if (notified) Mint.primary.copy(alpha = 0.16f) else Ink.glass, outline = if (notified) Ink.outlineOn else Ink.outline, onClick = { onNotify(); notified = true; s.remind = true; s.save() }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Erinnerungen", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("${s.remindMinutes} Minuten vor jedem guten Überflug.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    if (notified) Icon(Icons.Rounded.Check, null, tint = Mint.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("PROFIL", style = MaterialTheme.typography.labelSmall, color = Ink.dim, modifier = Modifier.padding(horizontal = 8.dp))
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Standard", !s.nerd, s.haptics) { s.nerd = false; s.save() }
                Chip("Nerd", s.nerd, s.haptics) { s.nerd = true; s.save() }
            }
            Spacer(Modifier.weight(1f))
            MadeInEu(Modifier.padding(horizontal = 8.dp, vertical = 100.dp))
        }
    }
}

private val STARS = kotlin.random.Random(5).let { rnd -> List(120) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.45f, 0.15f + 0.7f * rnd.nextFloat() * rnd.nextFloat()) } }
