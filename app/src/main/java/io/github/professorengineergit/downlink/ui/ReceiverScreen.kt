package io.github.professorengineergit.downlink.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.sdr.Receiver
import io.github.professorengineergit.downlink.sdr.Receiver.Link
import io.github.professorengineergit.downlink.sdr.Receiver.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class Preset(val label: String, val hz: Long, val mode: Mode, val key: String)

private val PRESETS = listOf(
    Preset("Meteor 137,9", 137_900_000, Mode.Record, "meteor"),
    Preset("Meteor 137,1", 137_100_000, Mode.Record, "meteor"),
    Preset("ISS APRS 145,825", 145_825_000, Mode.Audio, "iss"),
    Preset("ISS Sprache 145,8", 145_800_000, Mode.Audio, "iss"),
    Preset("SO-50 436,795", 436_795_000, Mode.Audio, "so50"),
    Preset("UKW-Test 100,0", 100_000_000, Mode.Spectrum, "fm"),
)

/* --- Empfänger: Stick koppeln, Spektrum und Wasserfall, hören, aufnehmen, einmessen. --- */
@Composable
fun ReceiverScreen(vm: AppViewModel, pass: Pass?, bottom: Dp, onBack: () -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val link = Receiver.link
    val meteor = pass?.sat?.key in setOf("m23", "m24")
    val passHz = pass?.sat?.freqMHz?.takeIf { it > 0 }?.let { (it * 1e6).toLong() }

    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false; if (Receiver.mode != Mode.Record) Receiver.stop() } }

    // Koppeln: sofort versuchen, danach jede Sekunde nach einem eingesteckten Stick schauen
    LaunchedEffect(Unit) {
        while (true) {
            if (Receiver.link == Link.NoDevice || (Receiver.link == Link.Failed && Receiver.device(ctx) != null && Receiver.error == null)) Receiver.connect(ctx, s.sdrPpm, s.sdrGain)
            if (Receiver.link == Link.Ready && Receiver.device(ctx) == null) Receiver.disconnect()
            delay(1000)
        }
    }
    LaunchedEffect(link) {
        if (link == Link.Ready) {
            if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (Receiver.mode == Mode.Idle) { Receiver.tune(passHz ?: Receiver.targetHz); Receiver.start(Mode.Spectrum) }
        }
    }
    // Doppler nachführen und beim Pass selbst starten und stoppen
    if (pass != null) LaunchedEffect(pass, link) {
        var started = false
        while (true) {
            val now = System.currentTimeMillis()
            val f = pass.sat.freqMHz
            vm.tracker.live(pass.sat, s.observer, now)?.let { lv -> if (f > 0) Receiver.updateDoppler((doppler(lv.rangeRate, f) * 1000).toFloat()) }
            if (link == Link.Ready && !started && now >= pass.aos - 45_000 && now < pass.los) {
                started = true
                passHz?.let { Receiver.tune(it) }
                Receiver.start(if (meteor) Mode.Record else Mode.Audio, ctx, pass.sat.key)
            }
            if (started && now > pass.los + 30_000) { Receiver.stop(); Receiver.start(Mode.Spectrum); started = false; break }
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.space)) {
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(Color(0xFF0F2A3C), Ink.space))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = bottom + 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Empfänger", style = MaterialTheme.typography.headlineMedium, color = Ink.text)
                    Text(pass?.let { "für ${it.sat.label}" } ?: "RTL-SDR am Handy", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                }
                Pill(when (link) { Link.Ready -> "Gekoppelt"; Link.Connecting -> "Verbinde"; Link.NeedsPermission -> "Zugriff"; Link.Failed -> "Fehler"; else -> "Kein Stick" },
                    if (link == Link.Ready) Mint.primary else if (link == Link.Failed) Coral else Amber, filled = link == Link.Ready)
            }

            AnimatedContent(link == Link.Ready, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pair") { ready ->
                if (!ready) PairingCard(link, Receiver.error) { Receiver.connect(ctx, s.sdrPpm, s.sdrGain) }
                else Column {
                    DeviceCard()
                    if (pass != null) PassBanner(pass, s.utc, meteor)
                    LiveCard()
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { p -> Chip(p.label, Receiver.targetHz == p.hz, s.haptics) { Receiver.tune(p.hz); if (Receiver.mode == Mode.Idle) Receiver.start(Mode.Spectrum) } }
                    }
                    Controls(s.haptics, meteor) { label -> Receiver.start(Mode.Record, ctx, label) }
                    GainCard(s.haptics) { s.sdrGain = it; s.save() }
                }
            }

            if (link == Link.Ready) {
                SectionTitle("Einmessen")
                Calibration(vm)
            }
            SectionTitle("Antenne aufstellen")
            AntennaGuide(s.sdrAntenna, s.haptics) { s.sdrAntenna = it; s.save() }
            if (link == Link.Ready) AntennaMeter(s.haptics)
            SectionTitle("Bilder")
            Pictures()
            SectionTitle("Aufnahmen")
            Recordings()
        }
    }
}

/* ---------- Koppeln ---------- */

@Composable
private fun PairingCard(link: Link, error: String?, retry: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pair")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val plug by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "plug")
    Glass(Modifier.padding(16.dp).fillMaxWidth(), radius = 28.dp, tint = Color(0x1FFFFFFF), outline = if (link == Link.Connecting) Ink.outlineOn else Ink.outline) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                val w = size.width; val h = size.height
                // Handy links
                val phone = Offset(w * 0.2f, h * 0.55f)
                drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(phone.x - 40f, phone.y - 110f), Size(80f, 220f), CornerRadius(22f))
                drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(phone.x - 40f, phone.y - 110f), Size(80f, 220f), CornerRadius(22f), style = Stroke(3f))
                // Stick rechts, mit Antenne obendrauf
                val shift = if (link == Link.NoDevice) 60f * (1 - plug) else 0f
                val stick = Offset(w * 0.62f + shift, h * 0.62f)
                drawRoundRect(Color(0xFF26313F), Offset(stick.x - 70f, stick.y - 22f), Size(140f, 44f), CornerRadius(12f))
                drawRoundRect(Mint.primary.copy(alpha = 0.7f), Offset(stick.x - 70f, stick.y - 22f), Size(140f, 44f), CornerRadius(12f), style = Stroke(2.5f))
                drawRect(Color(0xFFB8C4D0), Offset(stick.x - 98f, stick.y - 12f), Size(28f, 24f))
                val ant = Offset(w * 0.84f, h * 0.62f)
                drawLine(Color.White.copy(alpha = 0.8f), Offset(ant.x, ant.y), Offset(ant.x, ant.y - 130f), 4f, StrokeCap.Round)
                drawCircle(Color(0xFF3A4654), 18f, ant)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(stick.x + 70f, stick.y), ant, 3f)
                // Kabel vom Handy zum Stick
                val cable = Path().apply { moveTo(phone.x, phone.y + 110f); cubicTo(phone.x, h, stick.x - 160f, h, stick.x - 98f, stick.y) }
                drawPath(cable, Color.White.copy(alpha = 0.35f), style = Stroke(3f))
                if (link == Link.Connecting || link == Link.NeedsPermission) {
                    drawPath(cable, Mint.primary, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 26f), -t * 80f)))
                }
                // Funkwellen von oben auf die Antenne
                for (k in 0 until 3) {
                    val f = (t + k / 3f) % 1f
                    val r = 30f + 120f * (1 - f)
                    drawArc(Mint.primary.copy(alpha = f * 0.8f), 200f, 140f, false, Offset(ant.x - r, ant.y - 130f - r * 0.6f), Size(r * 2, r * 1.2f), style = Stroke(3f))
                }
            }
            Text(when (link) {
                Link.NoDevice -> "Stick einstecken"
                Link.NeedsPermission -> "Zugriff erlauben"
                Link.Connecting -> "Verbinde mit dem Stick"
                Link.Failed -> "Das hat nicht geklappt"
                else -> ""
            }, style = MaterialTheme.typography.titleLarge, color = Ink.text)
            Spacer(Modifier.height(6.dp))
            Text(when (link) {
                Link.NoDevice -> "Den RTL-SDR-Stick mit dem OTG-Adapter ans Handy stecken. Auf manchen Handys muss OTG in den Einstellungen eingeschaltet sein."
                Link.NeedsPermission -> "Android fragt gleich, ob Downlink den Stick benutzen darf. Tippe auf Zulassen und setz den Haken bei immer."
                Link.Connecting -> "Treiber laden, Tuner erkennen, erste Proben lesen."
                Link.Failed -> error ?: "Stick kurz ab- und wieder anstecken."
                else -> ""
            }, style = MaterialTheme.typography.bodyMedium, color = Ink.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            val steps = listOf("USB-Verbindung", "Zugriff erlaubt", "Tuner bereit")
            val done = when (link) { Link.NoDevice, Link.Failed -> 0; Link.NeedsPermission -> 1; Link.Connecting -> 2; else -> 3 }
            steps.forEachIndexed { i, st ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (i < done) Mint.primary else Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        if (i < done) Icon(Icons.Rounded.Check, null, tint = Mint.onPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(st, color = if (i < done) Ink.text else Ink.dim, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (link == Link.Failed || link == Link.NeedsPermission) {
                Spacer(Modifier.height(10.dp))
                Chip("Nochmal versuchen", true) { retry() }
            }
        }
    }
}

@Composable
private fun DeviceCard() {
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp, tint = Mint.primary.copy(alpha = 0.10f), outline = Ink.outlineOn) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Mint.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Mint.onPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Receiver.product, style = MaterialTheme.typography.titleMedium, color = Ink.text, maxLines = 1)
                Text("Tuner ${Receiver.tuner} · Korrektur ${Receiver.ppm} ppm · ${if (Receiver.gain < 0) "Verstärkung automatisch" else "%.1f dB".format(Receiver.gain / 10f)}",
                    style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            }
        }
    }
}

@Composable
private fun PassBanner(p: Pass, utc: Boolean, meteor: Boolean) {
    val fmt = remember(utc) { timeFmt(utc) }
    val now = System.currentTimeMillis()
    val text = when {
        now < p.aos - 45_000 -> "Startet von selbst um ${fmt.format(Date(p.aos - 45_000))}, ${if (meteor) "nimmt das Bild auf" else "schaltet den Ton ein"}. Bildschirm anlassen."
        now <= p.los -> "Läuft jetzt. Doppler wird nachgeführt."
        else -> "Der Überflug ist vorbei."
    }
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 22.dp, tint = Color(0x1FFFFFFF)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SatSprite(p.sat.sprite, Color(p.sat.color), Modifier.size(56.dp), glow = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.sat.label} · ${fmt.format(Date(p.aos))}–${fmt.format(Date(p.los))} · %.0f°".format(p.maxEl), style = MaterialTheme.typography.titleSmall, color = Ink.text)
                Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            }
        }
    }
}

/* ---------- Live: Frequenz, Pegel, Spektrum, Wasserfall ---------- */

@Composable
private fun LiveCard() {
    val snr = (Receiver.bandDb - Receiver.noiseDb - 1.6f).coerceAtLeast(0f)
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 26.dp, tint = Color(0xCC07101C)) {
        Column(Modifier.padding(top = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("%.4f".format(Receiver.targetHz / 1e6).replace('.', ',') + " MHz", fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink.text, maxLines = 1, softWrap = false)
                    Text("Doppler %+.1f kHz · %s".format(Receiver.dopplerHz / 1000f, when (Receiver.mode) { Mode.Audio -> "Ton an"; Mode.Record -> "Aufnahme"; Mode.Spectrum -> "Spektrum"; else -> "Aus" }),
                        fontFamily = Mono, fontSize = 12.sp, color = Ink.dim)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.0f dB".format(snr), fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (snr > 12) Mint.primary else if (snr > 5) Amber else Ink.dim)
                    Text("über dem Rauschen", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                }
            }
            if (Receiver.clipping > 0.003f) Text("Übersteuert, Verstärkung senken", color = Coral, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Spectrum(Modifier.fillMaxWidth().height(110.dp))
            Waterfall(Modifier.fillMaxWidth().height(190.dp))
        }
    }
}

@Composable
private fun Spectrum(modifier: Modifier) {
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = Receiver.frame // nur neu zeichnen, nicht neu aufbauen
        val sp = Receiver.spectrum; val n = sp.size
        val floor = Receiver.liveNoise
        val lo = floor - 6f; val hi = floor + 40f
        // Linienzug statt Pfad: Pfade würde Android bei jedem Bild neu rastern
        val pts = ArrayList<Offset>(n)
        val fillCol = Mint.primary.copy(alpha = 0.16f)
        for (i in 0 until n) {
            val x = i / (n - 1f) * size.width
            val y = size.height * (1 - ((sp[i] - lo) / (hi - lo)).coerceIn(0f, 1f))
            pts += Offset(x, y)
            if (i % 3 == 0) drawLine(fillCol, Offset(x, y), Offset(x, size.height), size.width / n * 3f)
        }
        drawPoints(pts, androidx.compose.ui.graphics.PointMode.Polygon, Mint.primary, strokeWidth = 2f)
        // Zielmarke mit Kanalbreite
        val span = Receiver.rate.toFloat()
        val off = (Receiver.targetHz + Receiver.dopplerHz - Receiver.centerHz) / span
        val x = (0.5f + off) * size.width
        val bw = 30_000f / span * size.width
        drawRect(Amber.copy(alpha = 0.14f), Offset(x - bw / 2, 0f), Size(bw, size.height))
        drawLine(Amber, Offset(x, 0f), Offset(x, size.height), 2f)
        label(text, "%.3f".format((Receiver.centerHz - span / 2) / 1e6), Offset(36f, size.height - 14f), Ink.dim, 9)
        label(text, "%.3f".format((Receiver.centerHz + span / 2) / 1e6), Offset(size.width - 36f, size.height - 14f), Ink.dim, 9)
    }
}

@Composable
private fun Waterfall(modifier: Modifier) {
    val bmp = remember { android.graphics.Bitmap.createBitmap(Receiver.BINS, Receiver.ROWS, android.graphics.Bitmap.Config.ARGB_8888) }
    val img = remember(bmp) { bmp.asImageBitmap() }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = Receiver.frame
        bmp.setPixels(Receiver.waterColors, 0, Receiver.BINS, 0, 0, Receiver.BINS, Receiver.ROWS)
        val top = Receiver.waterRow; val rows = Receiver.ROWS
        val h1 = (rows - top) / rows.toFloat() * size.height
        val w = size.width.toInt()
        drawImage(img, IntOffset(0, top), IntSize(Receiver.BINS, rows - top), IntOffset.Zero, IntSize(w, h1.toInt().coerceAtLeast(1)), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
        if (top > 0) drawImage(img, IntOffset(0, 0), IntSize(Receiver.BINS, top), IntOffset(0, h1.toInt()), IntSize(w, (size.height - h1).toInt().coerceAtLeast(1)), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
        val span = Receiver.rate.toFloat()
        val x = (0.5f + (Receiver.targetHz + Receiver.dopplerHz - Receiver.centerHz) / span) * size.width
        drawLine(Amber.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

@Composable
private fun Controls(haptics: Boolean, meteor: Boolean, onRecord: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val mode = Receiver.mode
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton("Hören", Icons.Rounded.Headphones, mode == Mode.Audio, Modifier.weight(1f)) {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            if (mode == Mode.Audio) Receiver.start(Mode.Spectrum) else Receiver.start(Mode.Audio)
        }
        BigButton(if (mode == Mode.Record) "Stop" else "Aufnehmen", if (mode == Mode.Record) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, mode == Mode.Record, Modifier.weight(1f), danger = true) {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            if (mode == Mode.Record) Receiver.start(Mode.Spectrum) else onRecord(if (Receiver.targetHz in 137_000_000..138_000_000) "meteor" else "baseband")
        }
    }
    if (mode == Mode.Record) {
        val mb = Receiver.recordedBytes / 1e6
        val sec = Receiver.recordedBytes / (Receiver.BB_RATE * 2.0)
        Text("Nimmt auf: %.0f MB · %d:%02d min · %s".format(mb, (sec / 60).toInt(), (sec % 60).toInt(), Receiver.recordFile?.name ?: ""),
            fontFamily = Mono, fontSize = 11.sp, color = Coral, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    } else if (meteor || Receiver.targetHz in 137_000_000..138_000_000) {
        Text("Meteor-Bilder: Aufnehmen speichert das Basisband. Bis der Decoder in der App ist, öffnest du die Datei in SatDump (siehe unten).",
            style = MaterialTheme.typography.bodySmall, color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

@Composable
private fun BigButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val col = if (danger) Coral else Mint.primary
    Box(modifier.height(56.dp).clip(RoundedCornerShape(if (on) 28.dp else 16.dp)).background(if (on) col else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (on) Ink.space else col, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = if (on) Ink.space else Ink.text)
        }
    }
}

@Composable
private fun GainCard(haptics: Boolean, onSave: (Int) -> Unit) {
    val g = Receiver.gains
    if (g.isEmpty()) return
    val colors = SliderDefaults.colors(thumbColor = Mint.primary, activeTrackColor = Mint.primary, inactiveTrackColor = Ink.line, activeTickColor = Mint.onPrimary, inactiveTickColor = Ink.faint)
    var idx by remember(Receiver.gain) { mutableFloatStateOf(g.indexOfFirst { it >= Receiver.gain }.coerceAtLeast(0).toFloat()) }
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), radius = 22.dp, tint = Color(0x1FFFFFFF)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verstärkung", style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.weight(1f))
                Chip("Auto", Receiver.gain < 0, haptics) { Receiver.setGainNow(-1); onSave(-1) }
            }
            Text(if (Receiver.gain < 0) "Der Stick regelt selbst. Für Satelliten ist ein fester Wert meist besser." else "%.1f dB".format(Receiver.gain / 10f), style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            HSlider(idx, { idx = it }, 0f..(g.size - 1).toFloat(), g.size - 2, haptics, colors) {
                val v = g[idx.toInt().coerceIn(0, g.lastIndex)]; Receiver.setGainNow(v); onSave(v)
            }
        }
    }
}

/* ---------- Einmessen ---------- */

@Composable
private fun Calibration(vm: AppViewModel) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var prog by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var ppmText by remember { mutableStateOf(if (s.sdrCalibrated > 0) "Gespeichert: ${s.sdrPpm} ppm" else "Noch nicht gemessen") }
    var gainText by remember { mutableStateOf(if (s.sdrGain >= 0) "Gespeichert: %.1f dB".format(s.sdrGain / 10f) else "Automatisch") }
    var gainPts by remember { mutableStateOf<List<Receiver.GainPoint>>(emptyList()) }
    var spurText by remember { mutableStateOf("Noch nicht geprüft") }
    val target = Receiver.targetHz

    @Composable
    fun Step(n: Int, title: String, text: String, result: String, key: String, run: suspend () -> Unit) {
        Glass(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), radius = 22.dp, tint = Color(0x1FFFFFFF), outline = if (busy == key) Ink.outlineOn else Ink.outline,
            onClick = if (busy == null) ({ busy = key; scope.launch { runCatching { run() }; busy = null; Receiver.tune(target); Receiver.start(Mode.Spectrum) } }) else null) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Mint.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("$n", color = Mint.primary, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = Ink.text)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    Text(if (busy == key) "…" else "Start", color = Mint.primary, fontWeight = FontWeight.SemiBold)
                }
                if (busy == key) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth(), color = Mint.primary, trackColor = Ink.line)
                    Text(status, fontFamily = Mono, fontSize = 11.sp, color = Ink.dim, modifier = Modifier.padding(top = 6.dp))
                } else Text(result, fontFamily = Mono, fontSize = 12.sp, color = Ink.text, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    Step(1, "Frequenzfehler", "Sucht den stärksten UKW-Sender und misst, wie genau der Stick abstimmt.", ppmText, "ppm") {
        val r = Receiver.measurePpm { p, t -> prog = p; status = t }
        ppmText = if (r == null) "Kein Sender gefunden. Antenne prüfen." else {
            Receiver.setPpmNow(r.ppm); s.sdrPpm = r.ppm; s.sdrCalibrated = System.currentTimeMillis(); s.save()
            "%.2f MHz, %.0f dB über dem Rauschen\nAbweichung %+.0f Hz → Korrektur %d ppm%s".format(r.stationHz / 1e6, r.snrDb, r.offsetHz, r.ppm, if (kotlin.math.abs(r.ppm) <= 2) "\nSehr genau, der Stick hat einen guten Quarz." else "")
        }
    }
    Step(2, "Verstärkung", "Geht alle Stufen bei ${"%.1f".format(target / 1e6)} MHz durch und wählt die beste ohne Übersteuern.", gainText, "gain") {
        val r = Receiver.measureGain(target) { p, t -> prog = p; status = t }
        if (r != null) { s.sdrGain = r.best; s.save(); gainPts = r.points; gainText = "Beste Stufe: %.1f dB".format(r.best / 10f) }
    }
    if (gainPts.isNotEmpty()) GainChart(gainPts, s.sdrGain)
    Step(3, "Störquellen", "Sucht schmale Störer rund um die Frequenz: Netzteile, LED-Lampen, das Handy selbst.", spurText, "spur") {
        prog = 0.5f; status = "Höre 2 Sekunden hin"
        val r = Receiver.findSpurs()
        spurText = if (r.isEmpty()) "Keine auffälligen Störer. Gut." else
            r.joinToString("\n") { "%.3f MHz  +%.0f dB".format(it.hz / 1e6, it.db) } + "\nTipp: Handy und Powerbank weiter weg, Ladegeräte und LED-Lampen aus."
    }
}

@Composable
private fun GainChart(pts: List<Receiver.GainPoint>, best: Int) {
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), radius = 22.dp, tint = Color(0x14FFFFFF)) {
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(14.dp)) {
            val lo = pts.minOf { it.floorDb }; val hi = pts.maxOf { it.floorDb } + 1f
            val p = Path()
            pts.forEachIndexed { i, g ->
                val x = i / (pts.size - 1f).coerceAtLeast(1f) * size.width
                val y = size.height * (1 - (g.floorDb - lo) / (hi - lo))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                drawCircle(if (g.tenthDb == best) Mint.primary else if (g.clip > 0.002f) Coral else Ink.dim, if (g.tenthDb == best) 8f else 4f, Offset(x, y))
            }
            drawPath(p, Mint.primary.copy(alpha = 0.6f), style = Stroke(2f))
        }
    }
}

/* ---------- Antenne ---------- */

@Composable
private fun AntennaGuide(kind: String, haptics: Boolean, onKind: (String) -> Unit) {
    val text = rememberTextMeasurer()
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Magnetfuß", kind == "mag", haptics) { onKind("mag") }
        Chip("V-Dipol", kind == "vdipole", haptics) { onKind("vdipole") }
    }
    Glass(Modifier.padding(16.dp).fillMaxWidth(), radius = 26.dp, tint = Color(0x1FFFFFFF)) {
        Column(Modifier.padding(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val w = size.width; val h = size.height
                if (kind == "mag") {
                    // Blech, Magnetfuß, senkrechter Stab mit Maß
                    drawRoundRect(Color(0xFF8A96A3).copy(alpha = 0.5f), Offset(w * 0.15f, h * 0.84f), Size(w * 0.7f, 14f), CornerRadius(6f))
                    drawRoundRect(Color(0xFF2B3440), Offset(w * 0.5f - 36f, h * 0.84f - 26f), Size(72f, 26f), CornerRadius(10f))
                    drawLine(Color.White, Offset(w * 0.5f, h * 0.84f - 26f), Offset(w * 0.5f, h * 0.08f), 5f, StrokeCap.Round)
                    drawLine(Amber, Offset(w * 0.5f + 40f, h * 0.84f - 26f), Offset(w * 0.5f + 40f, h * 0.08f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    label(text, "52 cm", Offset(w * 0.5f + 80f, h * 0.45f), Amber, 13)
                    label(text, "Metallfläche ≥ 30 cm", Offset(w * 0.5f, h * 0.96f), Ink.dim, 11)
                    label(text, "senkrecht", Offset(w * 0.5f - 80f, h * 0.3f), Ink.dim, 11)
                } else {
                    // Draufsicht: V mit 120°, Achse Nord–Süd
                    val c = Offset(w * 0.5f, h * 0.58f)
                    val len = h * 0.42f
                    val a1 = Math.toRadians(-90.0 - 60.0); val a2 = Math.toRadians(-90.0 + 60.0)
                    val e1 = Offset(c.x + (len * cos(a1)).toFloat(), c.y + (len * sin(a1)).toFloat())
                    val e2 = Offset(c.x + (len * cos(a2)).toFloat(), c.y + (len * sin(a2)).toFloat())
                    drawLine(Color.White, c, e1, 5f, StrokeCap.Round); drawLine(Color.White, c, e2, 5f, StrokeCap.Round)
                    drawArc(Amber, 210f, 120f, false, Offset(c.x - 60f, c.y - 60f), Size(120f, 120f), style = Stroke(2f))
                    label(text, "120°", Offset(c.x, c.y - 84f), Amber, 13)
                    label(text, "53,4 cm", Offset((c.x + e1.x) / 2 - 50f, (c.y + e1.y) / 2), Amber, 12)
                    label(text, "53,4 cm", Offset((c.x + e2.x) / 2 + 50f, (c.y + e2.y) / 2), Amber, 12)
                    drawCircle(Color(0xFF2B3440), 16f, c)
                    drawLine(Mint.primary, Offset(w * 0.1f, h * 0.9f), Offset(w * 0.1f, h * 0.15f), 3f)
                    label(text, "N", Offset(w * 0.1f, h * 0.08f), Mint.primary, 13)
                    label(text, "S", Offset(w * 0.1f, h * 0.96f), Mint.primary, 13)
                    label(text, "waagerecht, Arme nach Nord und Süd", Offset(w * 0.55f, h * 0.95f), Ink.dim, 11)
                }
            }
            val tips = if (kind == "mag") listOf(
                "Den längsten Stab ganz ausziehen. Ideal sind 52 cm für Meteor, 49 cm für die ISS.",
                "Unbedingt auf Metall stellen: Backblech, Geländer, Autodach. Das ist die zweite Hälfte der Antenne.",
                "Draußen mit freiem Himmel. Drinnen klappt höchstens die ISS bei hohen Überflügen.",
                "Direkt über dir hört der senkrechte Stab am schlechtesten. Mittlere Höhen sind ideal.",
            ) else listOf(
                "Zwei Arme mit je 53,4 cm, ab dem Anschluss gemessen, 120° auseinander.",
                "Waagerecht aufstellen, die Arme zeigen ungefähr nach Norden und Süden.",
                "Möglichst hoch und frei, weg von Hauswänden und Dachrinnen.",
                "Ein Arm an die Mitte, einer an die Masse des Kabels. Bauen lässt er sich aus zwei Kleiderbügeln.",
            )
            tips.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = Ink.text, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** Antennen-Check: Rauschen im Kanal relativ zum Start. Mehr Rauschen vom Himmel heißt, die Antenne hört mehr. */
@Composable
private fun AntennaMeter(haptics: Boolean) {
    val haptic = LocalHapticFeedback.current
    var base by remember { mutableStateOf<Float?>(null) }
    var best by remember { mutableFloatStateOf(-999f) }
    val now = Receiver.bandDb - Receiver.noiseDb + (Receiver.levelDb - Receiver.noiseDb) * 0.5f
    val rel = base?.let { now - it } ?: 0f
    LaunchedEffect(rel.toInt()) { if (base != null && rel > best + 1f) { best = rel; if (haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), radius = 22.dp, tint = Color(0x1FFFFFFF), onClick = { base = now; best = 0f }) {
        Column(Modifier.padding(16.dp)) {
            Text("Antennen-Check", style = MaterialTheme.typography.titleSmall, color = Ink.text)
            Text(if (base == null) "Tippen, um den jetzigen Stand als Ausgang zu nehmen. Dann die Antenne umstellen: ein Klick heißt besser."
                else "%+.1f dB gegenüber vorher".format(rel), style = MaterialTheme.typography.bodySmall, color = if (rel > 1) Mint.primary else Ink.dim)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Ink.line)) {
                Box(Modifier.fillMaxWidth(((rel + 10f) / 20f).coerceIn(0.02f, 1f)).height(12.dp).clip(CircleShape).background(if (rel > 1) Mint.primary else Sky))
            }
        }
    }
}

/* ---------- Aufnahmen ---------- */

@Composable
private fun Recordings() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val m = Receiver.mode
    val files = remember(Receiver.mode, io.github.professorengineergit.downlink.sdr.MeteorImages.version) { File(ctx.getExternalFilesDir(null), "baseband").listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList() }
    Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 24.dp, tint = Color(0x1FFFFFFF)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) Text("Noch keine Aufnahmen. Bei einem Meteor-Überflug nimmt Downlink das Signal von selbst auf.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
            files.take(8).forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontFamily = Mono, fontSize = 11.sp, color = Ink.text, maxLines = 2)
                        Text("%.0f MB".format(f.length() / 1e6), style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                    }
                    if (f.name.contains("Hz.s8")) Text("Bild", color = Mint.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).clickable(enabled = !io.github.professorengineergit.downlink.sdr.MeteorImages.decoding) { io.github.professorengineergit.downlink.sdr.MeteorImages.decode(ctx, f) }.padding(horizontal = 10.dp, vertical = 8.dp))
                    Icon(Icons.Rounded.Share, "Teilen", tint = Mint.primary, modifier = Modifier.size(36.dp).clip(CircleShape).clickable {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Aufnahme öffnen"))
                    }.padding(8.dp))
                }
            }
            Text("Nach jedem Meteor-Überflug macht Downlink das Bild von selbst. „Bild“ decodiert eine Aufnahme nochmal. Die Rohdaten (s8, 192000 S/s) lassen sich auch in SatDump öffnen.",
                style = MaterialTheme.typography.bodySmall, color = Ink.dim)
        }
    }
}

/* ---------- Bilder ---------- */

@Composable
private fun Pictures() {
    val ctx = LocalContext.current
    val mi = io.github.professorengineergit.downlink.sdr.MeteorImages
    val pics = remember(mi.version) { mi.pictures(ctx) }
    var open by remember { mutableStateOf<File?>(null) }
    if (mi.decoding) {
        val inf = rememberInfiniteTransition(label = "dec")
        val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t")
        Glass(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), radius = 22.dp, tint = Mint.primary.copy(alpha = 0.12f), outline = Ink.outlineOn) {
            Column(Modifier.padding(16.dp)) {
                Text("Das Bild entsteht", style = MaterialTheme.typography.titleSmall, color = Ink.text)
                Text("%d Rahmen · %d Bildpakete · %.0f %%".format(mi.frames, mi.packets, mi.progress * 100), fontFamily = Mono, fontSize = 12.sp, color = Ink.dim)
                Spacer(Modifier.height(10.dp))
                // Zeilen, die sich wie bei einem Fax von oben nach unten aufbauen
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val rows = 12
                    for (r in 0 until rows) {
                        val done = r < (mi.progress * rows).toInt()
                        val shimmer = ((t * rows).toInt() == r)
                        drawRect(if (done) Mint.primary.copy(alpha = 0.25f + 0.5f * (r % 3) / 2f) else if (shimmer) Mint.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                            Offset(0f, r * size.height / rows), Size(size.width, size.height / rows - 1f))
                    }
                }
                LinearProgressIndicator(progress = { mi.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Mint.primary, trackColor = Ink.line)
            }
        }
    }
    mi.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
    if (pics.isEmpty() && !mi.decoding) {
        Text("Hier erscheinen die Wetterbilder deiner Überflüge. Jedes Farbbild wird auch in Fotos gesichert.", style = MaterialTheme.typography.bodySmall, color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pics.take(20).forEach { f ->
            val thumb = remember(f) { android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }
            Glass(Modifier.size(150.dp, 190.dp), radius = 20.dp, tint = Color(0x1FFFFFFF), onClick = { open = f }) {
                Column {
                    if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.fillMaxWidth().height(140.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Text(f.nameWithoutExtension.substringAfter('_').replace('_', ' '), fontFamily = Mono, fontSize = 10.sp, color = Ink.dim, modifier = Modifier.padding(8.dp), maxLines = 2)
                }
            }
        }
    }
    open?.let { f -> PictureViewer(f) { open = null } }
}

@Composable
private fun PictureViewer(f: File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val mi = io.github.professorengineergit.downlink.sdr.MeteorImages
    val files = remember(f) { listOf(f) + mi.channelsOf(f) }
    var idx by remember { mutableStateOf(0) }
    val img = remember(files[idx]) { android.graphics.BitmapFactory.decodeFile(files[idx].absolutePath)?.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (img != null) androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 8f); off += pan } }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onClose)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Share, "Teilen", false) {
                    val src = files[idx]
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", src)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Bild teilen"))
                }
            }
            if (files.size > 1) Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                files.forEachIndexed { i, ff ->
                    Chip(if (i == 0) "Farbe" else "Kanal " + ff.nameWithoutExtension.substringAfterLast("_ch"), i == idx) { idx = i; scale = 1f; off = Offset.Zero }
                }
            }
        }
    }
}
