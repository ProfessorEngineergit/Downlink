package io.github.professorengineergit.downlink.ui

import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.automirrored.rounded.ArrowForward

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.SatInfo
import io.github.professorengineergit.downlink.orbit.Tracker
import java.util.Date
import kotlin.math.abs
import kotlin.math.sin

/* --- Ein Überflug im Detail: Satellit, Bewertung, Kuppel, Diagramme und Antennenhilfe teilen sich die Fingerzeit --- */
@Composable
fun PassScreen(vm: AppViewModel, p: Pass, bottom: Dp, onReceive: () -> Unit = {}, onBack: () -> Unit) {
    val s = vm.settings
    val col = Color(p.sat.color)
    val lv = vm.live.firstOrNull { it.sat.key == p.sat.key }
    val fmt = remember(s.utc) { timeFmt(s.utc, "HH:mm:ss") }
    var scrub by remember { mutableStateOf<Long?>(null) }
    val sun = Tracker.sunAzEl(s.observer, p.tca)
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(Ink.space)) {
        Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(0f to col.copy(alpha = 0.32f), 0.45f to Color(0xFF0B1730), 1f to Ink.space)))
        Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(bottom = bottom + 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
                Spacer(Modifier.weight(1f))
                Pill(p.sat.kind.title, col)
            }
            // Held: das 3D-Modell, mit dem Finger drehbar
            SatHero(p.sat, Modifier.fillMaxWidth().height(400.dp))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.sat.label, style = MaterialTheme.typography.displaySmall, color = Ink.text)
                Text(if (p.sat.freqMHz > 0) "%.3f MHz · %s · %s".format(p.sat.freqMHz, p.sat.mode, p.sat.operator) else "${p.sat.mode} · ${p.sat.operator}",
                    fontFamily = Mono, fontSize = 13.sp, color = Ink.dim)
            }
            Spacer(Modifier.height(16.dp))
            if (p.sat.freqMHz in 24.0..1700.0) {
                Glass(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(), radius = 24.dp, tint = Mint.primary.copy(alpha = 0.16f), outline = Ink.outlineOn, onClick = onReceive) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Sensors, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mit dem Stick empfangen", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                            Text(if (p.sat.key in setOf("m23", "m24")) "Nimmt das Bild beim Überflug von selbst auf, mit Doppler-Nachführung." else "Schaltet den Ton beim Überflug von selbst ein, mit Doppler-Nachführung.",
                                style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                        }
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.text)
                    }
                }
            }

            // Bewertung
            Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 28.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        ScoreRing(p.score, scoreColor(p.score), Modifier.size(84.dp), stroke = 8f)
                        Text("${p.score}", fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink.text)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wird es klappen?", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                        Text(p.verdict, style = MaterialTheme.typography.headlineMedium, color = scoreColor(p.score))
                        val high = p.samples.count { it.el >= 20 } / 6
                        Text("Höchstens %.0f°, %.0f Minuten lang, davon %d über 20°.%s".format(p.maxEl, p.minutes, high, if (p.visible) " Am Himmel als Lichtpunkt zu sehen." else ""),
                            style = MaterialTheme.typography.bodyMedium, color = Ink.text.copy(alpha = 0.85f))
                    }
                }
            }

            // Die Kuppel für genau diesen Pass
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                SkyDome(
                    passes = listOf(p), focus = p, live = listOfNotNull(lv), minEl = s.minElevation, sun = sun,
                    heading = if (s.compassSky) vm.heading else 0f, utc = s.utc, scrub = scrub,
                    onSelect = {}, onScrub = { scrub = it }, radiusFactor = 0.44f, showSun = s.showSun, haptics = s.haptics,
                    modifier = Modifier.fillMaxWidth().height(maxWidth * 1.02f),
                )
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Aufgang", fmt.format(Date(p.aos)), "%.0f° %s".format(p.aosAz, Tracker.compass(p.aosAz)), Modifier.weight(1f))
                Stat("Höchster Punkt", "%.1f°".format(p.maxEl), fmt.format(Date(p.tca)), Modifier.weight(1f))
                Stat("Untergang", fmt.format(Date(p.los)), "%.0f° %s".format(p.losAz, Tracker.compass(p.losAz)), Modifier.weight(1f))
            }
            Section("Höhe über dem Horizont", "Leg den Finger auf die Kurve") {
                ElevationChart(p, vm.now, s.minElevation, s.utc, scrub, { scrub = it }, Modifier.fillMaxWidth().height(170.dp), s.haptics)
            }
            if (s.dopplerChart && p.sat.freqMHz > 0) Section("Dopplerverschiebung", "Um so viel wandert die Frequenz während des Passes") {
                DopplerChart(p, vm.now, p.sat.freqMHz, s.utc, scrub, { scrub = it }, Modifier.fillMaxWidth().height(130.dp))
            }
            if (s.nerd && s.tuningTable && p.sat.freqMHz > 0) Section("Abstimmtabelle", "Jede Minute die Frequenz, auf die du drehst") {
                TuningTable(p, s.utc)
            }
            Section("Antennenhilfe", if (s.directional) "Richte die Antenne auf den Pfeil" else "Halte das Handy flach vor dir") {
                AntennaHelper(vm, p, lv?.let { it.az to it.el })
            }
            if (p.sat.freqMHz > 0) Section("Empfang", null) {
                ReceiveInfo(p.sat)
            }
        }
    }
}

@Composable
private fun Section(title: String, hint: String?, content: @Composable () -> Unit) {
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 26.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink.text)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            }
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Glass(modifier, radius = 20.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.dim, maxLines = 1)
            Text(value, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink.text)
            Text(sub, fontFamily = Mono, fontSize = 11.sp, color = Ink.dim)
        }
    }
}

@Composable
private fun TuningTable(p: Pass, utc: Boolean) {
    val fmt = remember(utc) { timeFmt(utc) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row { listOf("Zeit", "Höhe", "Richtung", "Frequenz").forEachIndexed { i, h -> Text(h, Modifier.weight(if (i == 3) 1.6f else 1f), style = MaterialTheme.typography.labelSmall, color = Ink.dim) } }
        p.samples.filterIndexed { i, _ -> i % 6 == 0 }.forEach { smp ->
            val f = p.sat.freqMHz + doppler(smp.rangeRate, p.sat.freqMHz) / 1000
            Row {
                Text(fmt.format(Date(smp.t)), Modifier.weight(1f), fontFamily = Mono, fontSize = 13.sp, color = Ink.text)
                Text("%.0f°".format(smp.el), Modifier.weight(1f), fontFamily = Mono, fontSize = 13.sp, color = Ink.text)
                Text("%.0f° %s".format(smp.az, Tracker.compass(smp.az)), Modifier.weight(1f), fontFamily = Mono, fontSize = 13.sp, color = Ink.text)
                Text("%.4f".format(f), Modifier.weight(1.6f), fontFamily = Mono, fontSize = 13.sp, color = Mint.primary)
            }
        }
    }
}

@Composable
private fun AntennaHelper(vm: AppViewModel, p: Pass, live: Pair<Double, Double>?) {
    val s = vm.settings
    val haptic = LocalHapticFeedback.current
    val axis = passAxis(p)
    // Für die Öffnung reicht die Achse; ob nach vorn oder hinten, ist für den Dipol gleich
    val diffRaw = ((axis - vm.heading + 540) % 360 - 180)
    val diff = if (abs(diffRaw) > 90) (if (diffRaw > 0) diffRaw - 180 else diffRaw + 180) else diffRaw
    val target = if (live != null && live.second > 0) live else p.aosAz to 0.0
    val aligned = if (s.directional) abs(((target.first - vm.heading + 540) % 360 - 180)) < 6 else abs(diff) < 6
    var was by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) { if (aligned && !was && s.haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm); was = aligned }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        if (s.nerd) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!s.directional, { s.directional = false; s.save() }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("V-Dipol") }
            SegmentedButton(s.directional, { s.directional = true; s.save() }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Richtantenne") }
        }
        AntennaDial(p, vm.heading, target, s.directional, Modifier.size(250.dp))
        if (s.directional) {
            Text("Az %.0f° · El %.0f°".format(target.first, target.second), fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (aligned) Mint.primary else Ink.text)
            Text(if (live != null && live.second > 0) "Der Satellit ist jetzt dort. Folge dem Pfeil." else "So weit über dem Horizont geht er im ${Tracker.compass(p.aosAz)} auf.",
                style = MaterialTheme.typography.bodyMedium, color = Ink.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            Text(if (aligned) "Passt so" else if (diff > 0) "Dreh dich %.0f° nach rechts".format(diff) else "Dreh dich %.0f° nach links".format(-diff),
                style = MaterialTheme.typography.headlineSmall, color = if (aligned) Mint.primary else Ink.text)
            Text("Leg die Antenne waagerecht. Die Arme stehen 120° auseinander, die Öffnung des V zeigt entlang der Flugbahn nach %s (%.0f°). So hört sie den Satelliten von Aufgang bis Untergang.".format(Tracker.compass(axis), axis),
                style = MaterialTheme.typography.bodyMedium, color = Ink.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Für 137 MHz: jeder Arm 53,4 cm", style = MaterialTheme.typography.labelSmall, color = Ink.faint)
        }
    }
}

@Composable
private fun ReceiveInfo(sat: SatInfo) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(sat.note, style = MaterialTheme.typography.bodyLarge, color = Ink.text)
        if (sat.receivable && sat.freqMHz < 200) {
            Text("Steck einen RTL-SDR-Stick per USB-C ans Handy. Downlink startet den Treiber auf der richtigen Frequenz, ein Decoder wie SatDump setzt die Bilder zusammen.",
                style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            FilledTonalButton(onClick = {
                val hz = (sat.freqMHz * 1_000_000).toLong()
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("iqsrc://-a 127.0.0.1 -p 14423 -f $hz -s 1024000"))) }
            }) { Icon(Icons.Rounded.Satellite, null); Spacer(Modifier.size(8.dp)); Text("SDR-Treiber starten") }
        }
    }
}

/* --- Satellit groß: Meteor-M als echte 3D-Drehung (36 Bilder aus Blender), alle anderen schweben sanft --- */
@Composable
fun SatHero(sat: SatInfo, modifier: Modifier) {
    val col = Color(sat.color)
    val inf = rememberInfiniteTransition(label = "hero")
    val float by inf.animateFloat(0f, 6.283f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "f")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.32f), col.copy(alpha = 0.06f), Color.Transparent), center, size.minDimension * 0.62f), size.minDimension * 0.62f)
        }
        if (sat.sprite in TURNABLE) Turntable(Modifier.size(540.dp).offset(y = (6 * sin(float)).dp), sat.sprite)
        else SatSprite(sat.sprite, col, Modifier.size(480.dp).offset(y = (6 * sin(float)).dp), glow = false)
    }
}

@Composable
fun Turntable(modifier: Modifier, key: String = "meteor", autoSpin: Boolean = true) {
    val ctx = LocalContext.current
    val frames = remember(key) {
        (0 until 36).mapNotNull { i -> runCatching { ctx.assets.open("turn/${key}_%02d.webp".format(i)).use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    }
    var angle by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    if (autoSpin) LaunchedEffect(dragging) {
        if (dragging) return@LaunchedEffect
        var last = 0L
        while (true) androidx.compose.runtime.withFrameNanos { t -> if (last != 0L) angle += (t - last) / 1e9f * 24f; last = t }
    }
    if (frames.isEmpty()) return
    Canvas(modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { ch, dx -> angle -= dx * 0.6f; ch.consume() }
    }) {
        val f = ((angle % 360 + 360) % 360) / 10f
        val i = f.toInt().coerceIn(0, frames.size - 1)
        val mix = f - i
        drawFrame(frames[i], 1f)
        if (mix > 0.02f) drawFrame(frames[(i + 1) % frames.size], mix)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(img: ImageBitmap, alpha: Float) {
    val s = size.minDimension.toInt()
    drawImage(img, dstOffset = IntOffset(((size.width - s) / 2).toInt(), ((size.height - s) / 2).toInt()), dstSize = IntSize(s, s), alpha = alpha, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
}

private val unusedOffset = Offset.Zero

private val TURNABLE = setOf("meteor", "metop", "npp", "iss", "tiangong", "hubble", "aqua", "terra", "aura", "landsat", "so50", "greencube", "diwata", "jpss")
