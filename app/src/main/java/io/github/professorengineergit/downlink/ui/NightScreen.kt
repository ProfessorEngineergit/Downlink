package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.night.Night
import io.github.professorengineergit.downlink.night.NightService
import io.github.professorengineergit.downlink.sdr.Receiver
import io.github.professorengineergit.downlink.sdr.Receiver.Link
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --- Nachtmodus: Start und Stopp, Auswahl der Satelliten, was als Nächstes kommt und was in der Nacht angekommen ist. --- */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NightScreen(vm: AppViewModel, bottom: Dp, onBack: () -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
    LaunchedEffect(Unit) { Night.load(ctx) }
    // Stick koppeln, damit Android den Dienst für das USB-Gerät zulässt
    LaunchedEffect(Unit) {
        while (true) {
            if (!Night.running && (Receiver.link == Link.NoDevice || (Receiver.link == Link.Failed && Receiver.device(ctx) != null && Receiver.error == null))) Receiver.connect(ctx, s.sdrPpm, s.sdrGain)
            delay(1000)
        }
    }
    val ready = Receiver.link == Link.Ready

    Box(Modifier.fillMaxSize().background(Ink.space)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = bottom + 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nachtmodus", style = MaterialTheme.typography.headlineMedium, color = Ink.text)
                    Text("Nimmt Überflüge von selbst auf", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                }
                Pill(if (Night.running) "Läuft" else "Aus", if (Night.running) Mint.primary else Ink.dim, filled = Night.running)
            }

            Glass(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 24.dp, tint = if (Night.running) Mint.primary.copy(alpha = 0.12f) else Color(0x8C101A27),
                outline = if (Night.running) Ink.outlineOn else Ink.outline) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NightsStay, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(Night.status, style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.weight(1f))
                    }
                    if (!Night.running && !ready) Text("Zuerst den Stick anstecken und den Zugriff erlauben. Dann lässt Android den Dienst im Hintergrund laufen.", style = MaterialTheme.typography.bodySmall, color = Amber)
                    Box(
                        Modifier.fillMaxWidth().clip(CircleShape).background(if (Night.running) Coral else if (ready) Mint.primary else Ink.glassHi)
                            .clickable(enabled = Night.running || ready) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (Night.running) NightService.stop(ctx) else { Night.status = "Startet"; NightService.start(ctx) }
                            }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (Night.running) { Icon(Icons.Rounded.Stop, null, tint = Ink.space); Spacer(Modifier.width(8.dp)) }
                            Text(if (Night.running) "Beenden" else "Nachtmodus starten", color = if (ready || Night.running) Ink.space else Ink.dim, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Handy am Ladegerät lassen, der Bildschirm darf aus. Die App plant die nächsten 24 Stunden, nimmt jeden gewählten Überflug auf und legt Meteor-Bilder in Fotos ab.",
                        style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                }
            }

            SectionTitle("Was aufnehmen")
            GlassGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Night.candidates().forEach { sat ->
                            Chip(sat.label, sat.key in s.nightSats, s.haptics) {
                                s.nightSats = if (sat.key in s.nightSats) s.nightSats - sat.key else s.nightSats + sat.key
                                s.save()
                            }
                        }
                    }
                    Text("Mindesthöhe: %.0f°".format(s.nightMinEl), color = Ink.text, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10f, 20f, 30f, 45f).forEach { v -> Chip("%.0f°".format(v), s.nightMinEl == v, s.haptics) { s.nightMinEl = v; s.save() } }
                    }
                    Text("Freier Himmel (Antenne hinter einer Wand?)", color = Ink.text, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Rundum" to -1, "Osten" to 90, "Süden" to 180, "Westen" to 270, "Norden" to 0).forEach { (n, v) -> Chip(n, s.nightFreeAz == v, s.haptics) { s.nightFreeAz = v; s.save() } }
                    }
                    Text("Aufnehmen bis", color = Ink.text, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 6, 7, 8).forEach { h -> Chip("%02d:00".format(h), s.nightUntil == h, s.haptics) { s.nightUntil = h; s.save() } }
                        Chip("Offen", s.nightUntil < 0, s.haptics) { s.nightUntil = -1; s.save() }
                    }
                    Text("Die Einstellungen werden beim Start des Nachtmodus gelesen. Bahndaten werden alle 6 Stunden neu geladen, damit die Zeiten stimmen.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    if (Night.tleAgeDays >= 0) Text("Bahndaten: Stand vor %.1f Tagen".format(Night.tleAgeDays), style = MaterialTheme.typography.bodySmall, color = if (Night.tleAgeDays > 3) Amber else Ink.dim)
                }
            }

            if (Night.upcoming.isNotEmpty()) {
                SectionTitle("Als Nächstes")
                GlassGroup {
                    Night.upcoming.forEachIndexed { i, p ->
                        if (i > 0) Hairline()
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, color = Ink.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("%s · %.0f°".format(fmt.format(Date(p.aos)), p.maxEl), color = Ink.dim)
                        }
                    }
                }
            }

            SectionTitle("Aufgenommen")
            GlassGroup {
                if (Night.log.isEmpty()) Text("Noch nichts. Nach dem ersten Überflug erscheint er hier.", color = Ink.dim, modifier = Modifier.padding(16.dp))
                Night.log.take(40).forEachIndexed { i, e ->
                    if (i > 0) Hairline()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.label, color = Ink.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Pill(e.result, when (e.result) { "Bild", "Signal" -> Mint.primary; "Fehler", "Kein Bild" -> Coral; else -> Ink.dim }, filled = e.result == "Bild" || e.result == "Signal")
                        }
                        Text("%s · %.0f° · %d MB".format(fmt.format(Date(e.aos)), e.maxEl, e.mb), style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                        Text("Spitze %.1f dB über dem Rauschen, Kanal %.1f dB. %s".format(e.peak, e.snr, e.note), style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                }
            }
        }
    }
}
