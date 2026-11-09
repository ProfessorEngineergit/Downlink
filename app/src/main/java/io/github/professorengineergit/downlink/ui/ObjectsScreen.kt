package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.AppViewModel
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Kind
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.SatInfo
import java.util.Date

/* --- Objekte: alles, was Downlink verfolgen kann, als gerenderte Modelle. Antippen öffnet das Datenblatt. --- */
@Composable
fun ObjectsScreen(vm: AppViewModel, bottom: Dp, onOpen: (SatInfo) -> Unit) {
    val s = vm.settings
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(Color(0xFF14284A), Ink.space))))
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = HeaderH + androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kind.entries.forEach { k ->
                val list = Catalog.filter { it.kind == k }
                item(span = { GridItemSpan(2) }) {
                    Column(Modifier.padding(start = 8.dp, top = 14.dp)) {
                        Text(k.title, style = MaterialTheme.typography.titleLarge, color = Ink.text)
                        Text(k.blurb, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
                    }
                }
                items(list, key = { it.key }) { info ->
                    val on = info.key in s.enabled
                    val col = Color(info.color)
                    Glass(Modifier.fillMaxWidth(), radius = 24.dp, tint = if (on) Mint.primary.copy(alpha = 0.12f) else Color(0x1AFFFFFF), outline = if (on) Ink.outlineOn else Ink.outline, onClick = { onOpen(info) }) {
                        Column(Modifier.padding(12.dp)) {
                            SatSprite(info.sprite, col, Modifier.fillMaxWidth().aspectRatio(1.25f), glow = on)
                            Text(info.label, style = MaterialTheme.typography.titleSmall, color = Ink.text, maxLines = 1)
                            Text(if (info.freqMHz > 0) "%.1f MHz · %s".format(info.freqMHz, info.mode) else info.mode, fontFamily = Mono, fontSize = 11.sp, color = Ink.dim, maxLines = 1)
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (on) "Verfolgt" else "Aus", style = MaterialTheme.typography.labelSmall, color = if (on) col else Ink.faint, modifier = Modifier.weight(1f))
                                DSwitch(on, s.haptics) { v ->
                                    s.enabled = if (v) s.enabled + info.key else s.enabled - info.key
                                    s.save(); vm.recompute()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Datenblatt eines Objekts
@Composable
fun ObjectScreen(vm: AppViewModel, info: SatInfo, bottom: Dp, onBack: () -> Unit, onPass: (Pass) -> Unit) {
    val s = vm.settings
    val col = Color(info.color)
    val lv = vm.live.firstOrNull { it.sat.key == info.key } ?: vm.tracker.live(info, s.observer, vm.now)
    val next = vm.passes.filter { it.sat.key == info.key && it.los > vm.now }.take(4)
    val on = info.key in s.enabled
    val fmt = timeFmt(s.utc, "EEE HH:mm")
    Box(Modifier.fillMaxSize().background(Ink.space)) {
        Box(Modifier.fillMaxWidth().height(600.dp).background(Brush.verticalGradient(0f to col.copy(alpha = 0.30f), 0.5f to Color(0xFF0B1730), 1f to Ink.space)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = bottom + 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", false, onBack)
                Spacer(Modifier.weight(1f))
                Pill(info.kind.title, col)
            }
            SatHero(info, Modifier.fillMaxWidth().height(400.dp))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.label, style = MaterialTheme.typography.displaySmall, color = Ink.text)
                Text("${info.operator} · seit ${info.launched}", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
                Text(info.about, style = MaterialTheme.typography.bodyLarge, color = Ink.text.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            GlassGroup {
                ToggleRow(Icons.Rounded.Satellite, "Verfolgen", "Überflüge berechnen und erinnern", on, s.haptics) { v ->
                    s.enabled = if (v) s.enabled + info.key else s.enabled - info.key; s.save(); vm.recompute()
                }
            }
            SectionTitle("Jetzt")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Höhe", if (lv != null) "%.0f km".format(lv.altKm) else "–", Modifier.weight(1f))
                Fact("Über dir", if (lv != null) "%.0f°".format(lv.el) else "–", Modifier.weight(1f), if ((lv?.el ?: -1.0) > 0) Mint.primary else Ink.text)
                Fact("Entfernung", if (lv != null) "%.0f km".format(lv.rangeKm) else "–", Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Breite", if (lv != null) "%.1f°".format(lv.lat) else "–", Modifier.weight(1f))
                Fact("Länge", if (lv != null) "%.1f°".format(lv.lon) else "–", Modifier.weight(1f))
                Fact("Signal", if (info.freqMHz > 0) "%.1f MHz".format(info.freqMHz) else "Licht", Modifier.weight(1f))
            }
            SectionTitle("Empfang")
            GlassGroup {
                Text(info.note, style = MaterialTheme.typography.bodyLarge, color = Ink.text, modifier = Modifier.padding(16.dp))
            }
            SectionTitle("Nächste Überflüge")
            if (next.isEmpty()) Text(if (on) "Keine über ${s.minElevation.toInt()}° in den nächsten ${s.hoursAhead} Stunden." else "Schalte Verfolgen ein, dann rechnet Downlink seine Überflüge.",
                color = Ink.dim, modifier = Modifier.padding(horizontal = 24.dp))
            next.forEach { p ->
                Glass(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), radius = 20.dp, onClick = { onPass(p) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(p.aos)), fontFamily = Mono, fontWeight = FontWeight.Bold, color = Ink.text, modifier = Modifier.width(110.dp))
                        Sparkline(p, Modifier.size(70.dp, 24.dp))
                        Spacer(Modifier.weight(1f))
                        Text("%.0f° · %s".format(p.maxEl, p.verdict), style = MaterialTheme.typography.labelSmall, color = scoreColor(p.score))
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier, color: Color = Ink.text) {
    Glass(modifier, radius = 18.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.dim, maxLines = 1)
            Text(value, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}
