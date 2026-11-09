package io.github.professorengineergit.downlink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Tracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun timeFmt(utc: Boolean, pattern: String = "HH:mm") = SimpleDateFormat(pattern, Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") }

fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s >= 86400 -> "%d T %d h".format(s / 86400, (s % 86400) / 3600)
        s >= 3600 -> "%d h %02d min".format(s / 3600, (s % 3600) / 60)
        else -> "%02d:%02d".format(s / 60, s % 60)
    }
}

/* --- Startseite: der Himmel über dir, frei über die ganze Breite, darunter die Überflüge auf Glas --- */
@Composable
fun HomeScreen(vm: AppViewModel, bottom: Dp, onOpen: (Pass) -> Unit, onAr: () -> Unit) {
    val s = vm.settings
    val upcoming = vm.passes.filter { it.los > vm.now }
    val sun = Tracker.sunAzEl(s.observer, vm.now)
    val tone = skyTone(sun.second)
    val focus = vm.selected ?: upcoming.firstOrNull()
    var scrub by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        // Farbe des Himmels oben, läuft nach unten ins Dunkel aus
        Box(Modifier.fillMaxWidth().height(760.dp).background(Brush.verticalGradient(0f to tone.mid, 0.35f to tone.zenith, 1f to Ink.space)))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(HeaderH)) }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    SkyDome(
                        passes = upcoming.filter { it.aos < vm.now + 24 * 3_600_000L }.take(6),
                        focus = focus, live = vm.live, minEl = s.minElevation, sun = sun,
                        heading = if (s.compassSky) vm.heading else 0f, utc = s.utc, scrub = scrub,
                        onSelect = { vm.selected = it }, onScrub = { scrub = it },
                        showSun = s.showSun, haptics = s.haptics,
                        modifier = Modifier.fillMaxWidth().height(maxWidth * 1.12f),
                    )
                }
            }
            item {
                Text(
                    "Tippe auf eine Bahn. Halte und zieh, um Höhe und Zeit genau abzulesen.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.dim,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (focus != null) item {
                AnimatedContent(focus, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "focus") { f ->
                    FocusCard(f, vm.now, s.utc) { onOpen(f) }
                }
            }
            item { SectionTitle("Nächste Überflüge") }
            if (!vm.computing && upcoming.isEmpty()) item {
                Text("Keine Überflüge über ${s.minElevation.toInt()}° in den nächsten ${s.hoursAhead} Stunden. Nimm unter Objekte mehr Satelliten dazu oder senke die Mindesthöhe.",
                    color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp))
            }
            items(upcoming.take(40), key = { it.sat.key + it.aos }) { p ->
                PassRow(p, vm.now, s.utc, p == vm.selected) { vm.selected = p; onOpen(p) }
            }
        }
    }
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Glass(Modifier.size(48.dp), radius = 24.dp, tint = if (on) Mint.primary.copy(alpha = 0.9f) else Ink.glassHi, onClick = onClick) {
        Icon(icon, label, tint = if (on) Mint.onPrimary else Ink.text, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}

// Der ausgewählte Pass groß: Satellit, Countdown, Bewertung
@Composable
private fun FocusCard(p: Pass, now: Long, utc: Boolean, onClick: () -> Unit) {
    val col = Color(p.sat.color)
    val fmt = remember(utc) { timeFmt(utc) }
    val inPass = now in p.aos..p.los
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(), radius = 28.dp, tint = Color(0x1FFFFFFF), outline = Ink.outlineOn, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SatSprite(p.sat.sprite, col, Modifier.size(112.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.sat.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = col)
                Text(if (inPass) "noch ${countdown(p.los - now)}" else "in ${countdown(p.aos - now)}", fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (inPass) Mint.primary else Ink.text)
                Text("${fmt.format(Date(p.aos))} – ${fmt.format(Date(p.los))} · %.0f°".format(p.maxEl), style = MaterialTheme.typography.bodyMedium, color = Ink.dim)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Pill(p.verdict, scoreColor(p.score))
                    if (p.visible) Pill("Sichtbar", Amber)
                    if (inPass) Pill("Jetzt", Mint.primary, filled = true)
                }
            }
            Box(contentAlignment = Alignment.Center) {
                ScoreRing(p.score, scoreColor(p.score), Modifier.size(54.dp), stroke = 5f)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Öffnen", tint = Ink.text, modifier = Modifier.size(20.dp))
            }
        }
    }
}

fun scoreColor(score: Int) = when { score >= 75 -> Mint.primary; score >= 50 -> Sky; score >= 30 -> Amber; else -> Coral }

@Composable
private fun PassRow(p: Pass, now: Long, utc: Boolean, selected: Boolean, onClick: () -> Unit) {
    val col = Color(p.sat.color)
    val fmtDay = remember(utc) { timeFmt(utc, "EEE") }
    val fmt = remember(utc) { timeFmt(utc) }
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), radius = 22.dp, tint = if (selected) Color(0x2EFFFFFF) else Color(0x1FFFFFFF), outline = if (selected) Ink.outlineOn else Ink.outline, onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 56.dp)) {
                Text(fmtDay.format(Date(p.aos)).uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.dim, maxLines = 1)
                Text(fmt.format(Date(p.aos)), fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink.text, maxLines = 1, softWrap = false)
            }
            SatSprite(p.sat.sprite, col, Modifier.size(58.dp), glow = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.sat.label, style = MaterialTheme.typography.titleSmall, color = Ink.text)
                Text("%s → %s · %.0f min".format(Tracker.compass(p.aosAz), Tracker.compass(p.losAz), p.minutes), style = MaterialTheme.typography.bodySmall, color = Ink.dim, maxLines = 2)
                if (now in p.aos..p.los) Text("jetzt über dir", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
                else if (p.visible) Text("mit bloßem Auge sichtbar", style = MaterialTheme.typography.labelSmall, color = Amber)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Sparkline(p, Modifier.size(60.dp, 24.dp))
                Text("%.0f° · %s".format(p.maxEl, p.verdict), style = MaterialTheme.typography.labelSmall, color = scoreColor(p.score), maxLines = 1, softWrap = false)
            }
        }
    }
}
