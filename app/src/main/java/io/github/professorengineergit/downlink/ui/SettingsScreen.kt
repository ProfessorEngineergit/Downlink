package io.github.professorengineergit.downlink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.AppViewModel

/* --- Optionen. Zwei Profile: Standard zeigt das Nötige, Nerd alles. --- */
@Composable
fun SettingsScreen(vm: AppViewModel, bottom: Dp, onLocate: () -> Unit, onNotify: () -> Unit, onIntro: () -> Unit, onGear: () -> Unit = {}, onReceiver: () -> Unit = {}, onNight: () -> Unit = {}) {
    val s = vm.settings
    fun set(recompute: Boolean = false, block: () -> Unit) { block(); s.save(); if (recompute) vm.recompute() }
    val age = if (vm.tracker.tleAge > 0) ((System.currentTimeMillis() - vm.tracker.tleAge) / 86_400_000.0) else -1.0
    val sliderColors = SliderDefaults.colors(thumbColor = Mint.primary, activeTrackColor = Mint.primary, inactiveTrackColor = Ink.line, activeTickColor = Mint.onPrimary, inactiveTickColor = Ink.faint)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(Color(0xFF122440), Ink.space))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 24.dp)) {
            Spacer(Modifier.statusBarsPadding().height(HeaderH))

            // Profil
            SectionTitle("Profil")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Standard", !s.nerd, s.haptics) { set { s.nerd = false } }
                Chip("Nerd", s.nerd, s.haptics) { set { s.nerd = true } }
            }
            Text(if (s.nerd) "Alles: Doppler-Tabelle, Richtantenne, Spurlänge, UTC." else "Klar und ruhig. Das Wichtigste für jeden Überflug.",
                style = MaterialTheme.typography.bodySmall, color = Ink.dim, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp))

            SectionTitle("Erinnerungen")
            GlassGroup {
                ToggleRow(Icons.Rounded.Notifications, "Vor guten Überflügen", "Benachrichtigung, bevor ein Satellit aufgeht", s.remind, s.haptics) { set { s.remind = it }; if (it) onNotify(); vm.reschedule() }
                AnimatedVisibility(s.remind) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("${s.remindMinutes} Minuten vorher", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                        HSlider(s.remindMinutes.toFloat(), { s.remindMinutes = it.toInt() }, 5f..30f, 4, s.haptics, sliderColors) { set {}; vm.reschedule() }
                        Text("Nur ab ${s.remindMinEl.toInt()}° Höhe", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                        HSlider(s.remindMinEl, { s.remindMinEl = it }, 10f..70f, 11, s.haptics, sliderColors) { set {}; vm.reschedule() }
                    }
                }
            }

            SectionTitle("Überflüge")
            GlassGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("Mindesthöhe ${s.minElevation.toInt()}°", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                    Text("Darunter verdecken Häuser und Bäume meist das Signal", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    HSlider(s.minElevation, { s.minElevation = it }, 0f..60f, 11, s.haptics, sliderColors) { set(true) {} }
                    Text("Vorschau", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24, 48, 72).forEach { h -> Chip("$h h", s.hoursAhead == h, s.haptics) { set(true) { s.hoursAhead = h } } }
                    }
                }
            }

            SectionTitle("Himmel")
            GlassGroup {
                ToggleRow(Icons.Rounded.Explore, "Kompass", "Die Kuppel dreht sich mit dem Handy", s.compassSky, s.haptics) { set { s.compassSky = it } }
                Hairline()
                ToggleRow(Icons.Rounded.WbSunny, "Sonne zeigen", "Damit du nicht gegen das Licht schaust", s.showSun, s.haptics) { set { s.showSun = it } }
                Hairline()
                ToggleRow(Icons.Rounded.Vibration, "Haptik", "Feines Klicken beim Ablesen und Umschalten", s.haptics, s.haptics) { set { s.haptics = it } }
            }

            SectionTitle("Karte")
            GlassGroup {
                ToggleRow(Icons.Rounded.TripOrigin, "Sichtkreise", "Wo der Satellit über dem Horizont steht", s.footprints, s.haptics) { set { s.footprints = it } }
                Hairline()
                ToggleRow(Icons.Rounded.Bedtime, "Lichter der Nacht", "Städte auf der Nachtseite", s.nightLights, s.haptics) { set { s.nightLights = it } }
                Hairline()
                ToggleRow(Icons.Rounded.Cloud, "Atmosphäre", "Leuchtender Saum um die Erde", s.atmosphere, s.haptics) { set { s.atmosphere = it } }
            }

            AnimatedVisibility(s.nerd) {
                Column {
                    SectionTitle("Nerd")
                    GlassGroup {
                        ToggleRow(Icons.AutoMirrored.Rounded.ShowChart, "Doppler-Kurve", "Frequenzverschiebung im Pass", s.dopplerChart, s.haptics) { set { s.dopplerChart = it } }
                        Hairline()
                        ToggleRow(Icons.Rounded.TableRows, "Abstimmtabelle", "Frequenz für jede Minute des Passes", s.tuningTable, s.haptics) { set { s.tuningTable = it } }
                        Hairline()
                        ToggleRow(Icons.Rounded.Schedule, "UTC", "Statt Ortszeit, wie im Funk üblich", s.utc, s.haptics) { set { s.utc = it } }
                        Column(Modifier.padding(16.dp)) {
                            Text("Spurlänge ${s.trackMinutes} min", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                            HSlider(s.trackMinutes.toFloat(), { s.trackMinutes = it.toInt() }, 30f..300f, 8, s.haptics, sliderColors) { set {} }
                        }
                    }
                }
            }

            SectionTitle("Standort")
            GlassGroup {
                ToggleRow(Icons.Rounded.MyLocation, "Standort vom Handy", "Ungefähr genügt für die Bahnen", s.useGps, s.haptics) { set { s.useGps = it }; if (it) onLocate() }
                Text("%.4f°  %.4f°  %.0f m".format(s.lat, s.lon, s.alt), fontFamily = Mono, fontSize = 14.sp, color = Ink.dim, modifier = Modifier.padding(start = 52.dp, bottom = 14.dp))
            }

            SectionTitle("Bahndaten")
            GlassGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quelle: ${vm.tracker.source}", style = MaterialTheme.typography.bodyLarge, color = Ink.text)
                        Text(if (age >= 0) "Stand vor %.1f Tagen. Älter als eine Woche wird ungenau.".format(age) else "Noch keine Bahndaten", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    if (vm.refreshing) LinearProgressIndicator(Modifier.width(40.dp), color = Mint.primary, trackColor = Ink.line)
                    else TextButton(onClick = vm::refreshTle) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(6.dp)); Text("Laden") }
                }
            }

            SectionTitle("Empfang & Zubehör")
            Glass(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth(), radius = 24.dp, tint = Color(0x1FFFFFFF), onClick = onReceiver) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sensors, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Empfänger", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("RTL-SDR-Stick koppeln, einmessen, hören und aufnehmen.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.text)
                }
            }
            Glass(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth(), radius = 24.dp, tint = Color(0x1FFFFFFF), onClick = onNight) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NightsStay, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nachtmodus", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("Nimmt Überflüge die ganze Nacht von selbst auf, am Ladegerät.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.text)
                }
            }
            Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 24.dp, tint = Mint.primary.copy(alpha = 0.12f), outline = Ink.outlineOn, onClick = onGear) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sensors, null, tint = Mint.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Was brauche ich?", style = MaterialTheme.typography.titleMedium, color = Ink.text)
                        Text("SDR-Stick, Dipol und OTG-Adapter ab etwa 54 Euro, mit Links, Preisen und Bewertungen.", style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Ink.text)
                }
            }

            SectionTitle("Über")
            GlassGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MadeInEu()
                    Text("Downlink ist Open Source (GPL-3.0), ohne Werbung, ohne Konto. Alles wird auf deinem Gerät berechnet.", style = MaterialTheme.typography.bodyMedium, color = Ink.text)
                    Text("Bahnen: SGP4 über predict4java, Bahndaten von CelesTrak. 3D-Modelle: NASA 3D Resources (gemeinfrei) und eigene Modelle, gerendert mit Blender. Erde: Solar System Scope, CC BY 4.0. Schrift: Roboto, SIL Open Font License.",
                        style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Text("GitHub: professorengineergit/Downlink", color = Mint.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { uri.openUri("https://github.com/professorengineergit/Downlink") }.padding(vertical = 6.dp))
                    Text("Einführung nochmal ansehen", color = Mint.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onIntro).padding(vertical = 6.dp))
                }
            }
        }
    }
}

// Auswahl-Schalter: gewählt = ganz runde Kapsel, nicht gewählt = leicht eckiges Rechteck
@Composable
fun Chip(label: String, on: Boolean, haptics: Boolean = true, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val r by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 12.dp, androidx.compose.animation.core.tween(220), label = "chip")
    val shape = RoundedCornerShape(r)
    Box(
        Modifier.clip(shape).background(if (on) Mint.primary else Ink.glassHi)
            .clickable {
                if (haptics && !on) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                onClick()
            }.padding(horizontal = 20.dp, vertical = 11.dp),
    ) { Text(label, color = if (on) Mint.onPrimary else Ink.text, fontWeight = FontWeight.SemiBold) }
}

/** Slider mit feinem Klicken bei jeder Stufe */
@Composable
fun HSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int, haptics: Boolean, colors: androidx.compose.material3.SliderColors, onFinish: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var last by remember { mutableStateOf(Float.NaN) }
    Slider(value, {
        if (haptics && it != last && !last.isNaN()) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
        last = it; onChange(it)
    }, valueRange = range, steps = steps, onValueChangeFinished = { last = Float.NaN; onFinish() }, colors = colors)
}
