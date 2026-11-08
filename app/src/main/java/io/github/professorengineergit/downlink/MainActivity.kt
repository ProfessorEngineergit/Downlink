package io.github.professorengineergit.downlink

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.Tracker
import io.github.professorengineergit.downlink.ui.AimNeedle
import io.github.professorengineergit.downlink.ui.DopplerChart
import io.github.professorengineergit.downlink.ui.DownlinkTheme
import io.github.professorengineergit.downlink.ui.Divider16
import io.github.professorengineergit.downlink.ui.ElevationChart
import io.github.professorengineergit.downlink.ui.Mint
import io.github.professorengineergit.downlink.ui.Mono
import io.github.professorengineergit.downlink.ui.SectionTitle
import io.github.professorengineergit.downlink.ui.SkyPlot
import io.github.professorengineergit.downlink.ui.Sparkline
import io.github.professorengineergit.downlink.ui.ToggleRow
import io.github.professorengineergit.downlink.ui.WorldMap
import io.github.professorengineergit.downlink.ui.rememberMapData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DownlinkTheme { App(viewModel()) } }
    }
}

private enum class Tab { Sky, Map, Settings }

@Composable
private fun App(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.Sky) }
    var detail by remember { mutableStateOf<Pass?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.locate() }
    val ctx = LocalContext.current
    // Einmal fragen; danach nur noch über die Optionen, damit der Dialog nicht bei jedem Start kommt
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && vm.settings.useGps) vm.locate()
        else if (vm.settings.useGps && !vm.settings.askedLocation) {
            vm.settings.askedLocation = true
            vm.settings.save()
            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val compassOn = vm.settings.compassSky || detail != null
    DisposableEffect(compassOn) { vm.compass(compassOn); onDispose { vm.compass(false) } }

    detail?.let { p ->
        BackHandler { detail = null }
        PassDetail(vm, p) { detail = null }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(tab == Tab.Sky, { tab = Tab.Sky }, { Icon(Icons.Rounded.Radar, null) }, label = { Text("Himmel") })
                NavigationBarItem(tab == Tab.Map, { tab = Tab.Map }, { Icon(Icons.Rounded.Map, null) }, label = { Text("Karte") })
                NavigationBarItem(tab == Tab.Settings, { tab = Tab.Settings }, { Icon(Icons.Rounded.Tune, null) }, label = { Text("Optionen") })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                Tab.Sky -> SkyScreen(vm) { detail = it }
                Tab.Map -> MapScreen(vm)
                Tab.Settings -> SettingsScreen(vm) { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            }
        }
    }
}

private fun timeFmt(utc: Boolean, pattern: String = "HH:mm") = SimpleDateFormat(pattern, Locale.getDefault()).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") }

private fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d h %02d min".format(s / 3600, (s % 3600) / 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SkyScreen(vm: AppViewModel, onOpen: (Pass) -> Unit) {
    val s = vm.settings
    val upcoming = vm.passes.filter { it.los > vm.now }
    val focus = upcoming.firstOrNull()
    val sun = if (s.showSun) Tracker.sunAzEl(s.observer, vm.now) else null
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Downlink", style = MaterialTheme.typography.headlineMedium)
                    Text("%.2f° %.2f° · %s".format(s.lat, s.lon, if (s.utc) "UTC" else "Ortszeit"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.computing) LinearProgressIndicator(Modifier.width(48.dp))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                SkyPlot(
                    focus = focus, others = upcoming.drop(1).take(4), live = vm.live, minEl = s.minElevation, sun = sun,
                    heading = if (s.compassSky) vm.heading else 0f, utc = s.utc,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                )
                if (focus != null) {
                    val inPass = vm.now in focus.aos..focus.los
                    Column(Modifier.padding(16.dp).align(Alignment.TopStart)) {
                        Text(focus.sat.label, style = MaterialTheme.typography.titleMedium, color = Color(focus.sat.color))
                        Text(if (inPass) "über dir · noch ${countdown(focus.los - vm.now)}" else "in ${countdown(focus.aos - vm.now)}",
                            fontFamily = Mono, fontSize = 15.sp, color = if (inPass) Mint.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (s.compassSky) Icon(Icons.Rounded.Explore, "Kompass an", tint = Mint.primary, modifier = Modifier.padding(16.dp).align(Alignment.TopEnd))
            }
        }
        item { Text("Nächste Überflüge", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        if (!vm.computing && upcoming.isEmpty()) item {
            Text("Keine Überflüge über ${s.minElevation.toInt()}° in den nächsten ${s.hoursAhead} Stunden.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        }
        items(upcoming.take(30), key = { it.sat.key + it.aos }) { p -> PassCard(p, vm.now, s.utc) { onOpen(p) } }
    }
}

@Composable
private fun PassCard(p: Pass, now: Long, utc: Boolean, onClick: () -> Unit) {
    val col = Color(p.sat.color)
    val fmtDay = remember(utc) { timeFmt(utc, "EEE") }
    val fmt = remember(utc) { timeFmt(utc) }
    val quality = when { p.maxEl >= 50 -> "Sehr gut"; p.maxEl >= 30 -> "Gut"; else -> "Flach" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
            Text(fmtDay.format(Date(p.aos)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt.format(Date(p.aos)), fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(8.dp))
                Text(p.sat.label, style = MaterialTheme.typography.titleSmall)
            }
            Text("%s → %s · %.0f min · %s".format(Tracker.compass(p.aosAz), Tracker.compass(p.losAz), p.minutes, p.sat.mode),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (now in p.aos..p.los) Text("jetzt über dir", style = MaterialTheme.typography.labelSmall, color = Mint.primary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Sparkline(p, Modifier.size(64.dp, 28.dp))
            Text("%.0f° · %s".format(p.maxEl, quality), style = MaterialTheme.typography.labelSmall, color = if (p.maxEl >= 30) col else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MapScreen(vm: AppViewModel) {
    val s = vm.settings
    val data = rememberMapData()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bodenspuren", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            WorldMap(data, vm.live, vm.tracks, s.observer, vm.now, s.terminator, s.footprints, s.nightLights, Modifier.fillMaxWidth().aspectRatio(2f))
        }
        Text("Gestrichelt: die letzten ${s.trackMinutes / 4} Minuten, durchgezogen: die nächsten ${s.trackMinutes}. Kreise: Wo der Satellit gerade über dem Horizont steht.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.live.forEach { lv ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(lv.sat.color)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(lv.sat.label, style = MaterialTheme.typography.titleSmall)
                    Text("%.1f° %.1f° · %.0f km hoch".format(lv.lat, lv.lon, lv.altKm), fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("El %.1f°".format(lv.el), fontFamily = Mono, fontSize = 13.sp, color = if (lv.el > 0) Mint.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Az %.0f° · %.0f km".format(lv.az, lv.rangeKm), fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PassDetail(vm: AppViewModel, p: Pass, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.settings
    val col = Color(p.sat.color)
    val lv = vm.live.firstOrNull { it.sat.key == p.sat.key }
    val fmt = remember(s.utc) { timeFmt(s.utc, "HH:mm:ss") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Column {
                Text(p.sat.label, style = MaterialTheme.typography.titleLarge, color = col)
                Text("%.3f MHz · %s".format(p.sat.freqMHz, p.sat.mode), fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                SkyPlot(p, emptyList(), listOfNotNull(lv), s.minElevation, if (s.showSun) Tracker.sunAzEl(s.observer, vm.now) else null, vm.heading, s.utc, Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Beginn", fmt.format(Date(p.aos)), "%.0f° %s".format(p.aosAz, Tracker.compass(p.aosAz)), Modifier.weight(1f))
                Stat("Höchster", "%.1f°".format(p.maxEl), fmt.format(Date(p.tca)), Modifier.weight(1f))
                Stat("Ende", fmt.format(Date(p.los)), "%.0f° %s".format(p.losAz, Tracker.compass(p.losAz)), Modifier.weight(1f))
            }
            Card("Höhe über dem Horizont") { ElevationChart(p, vm.now, s.minElevation, Modifier.fillMaxWidth().height(140.dp)) }
            if (s.dopplerChart) Card("Dopplerverschiebung bei %.1f MHz".format(p.sat.freqMHz)) { DopplerChart(p, vm.now, p.sat.freqMHz, Modifier.fillMaxWidth().height(120.dp)) }
            Card("Antenne ausrichten") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Vor dem Pass zeigt die Nadel auf den Aufgangspunkt, währenddessen live auf den Satelliten
                    val visible = lv != null && lv.el > 0
                    val az = if (visible) lv!!.az else p.aosAz
                    val el = if (visible) lv!!.el else 0.0
                    AimNeedle(az, vm.heading, el, Modifier.size(120.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Az %.1f°".format(az), fontFamily = Mono, fontSize = 22.sp)
                        Text("El %.1f°".format(el), fontFamily = Mono, fontSize = 22.sp, color = if (el > 0) Mint.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (visible) "Live" else "Aufgang des Passes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Card("Empfang") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.sat.note, style = MaterialTheme.typography.bodyMedium)
                    Text("Downlink startet den rtl_tcp-Treiber für einen RTL-SDR-Stick am USB-C-Anschluss. Aufnahme und Dekodierung der Bilder kommen als nächster Schritt.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = {
                        val hz = (p.sat.freqMHz * 1_000_000).toLong()
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("iqsrc://-a 127.0.0.1 -p 14423 -f $hz -s 1024000"))) }
                    }) { Icon(Icons.Rounded.Satellite, null); Spacer(Modifier.size(8.dp)); Text("SDR-Treiber starten") }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(sub, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel, onLocate: () -> Unit) {
    val s = vm.settings
    val a = Mint
    fun set(recompute: Boolean = false, block: () -> Unit) { block(); s.save(); if (recompute) vm.recompute() }
    val age = if (vm.tracker.tleAge > 0) ((System.currentTimeMillis() - vm.tracker.tleAge) / 86_400_000.0) else -1.0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp)) {
        Text("Optionen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))

        SectionTitle("Satelliten")
        Group {
            Catalog.forEachIndexed { i, info ->
                if (i > 0) Divider16()
                ToggleRow(Icons.Rounded.Satellite, info.label, "%.1f MHz · %s · %s".format(info.freqMHz, info.mode, info.note), info.key in s.enabled, a, s.haptics) { on ->
                    set(true) { s.enabled = if (on) s.enabled + info.key else s.enabled - info.key }
                }
            }
        }

        SectionTitle("Überflüge")
        Group {
            Column(Modifier.padding(16.dp)) {
                Text("Mindesthöhe ${s.minElevation.toInt()}°", style = MaterialTheme.typography.bodyLarge)
                Text("Darunter verdecken Häuser und Bäume meist das Signal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(s.minElevation, { s.minElevation = it }, valueRange = 0f..60f, steps = 11, onValueChangeFinished = { set(true) {} })
                Text("Vorschau", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    listOf(24, 48, 72).forEachIndexed { i, h ->
                        SegmentedButton(s.hoursAhead == h, { set(true) { s.hoursAhead = h } }, SegmentedButtonDefaults.itemShape(i, 3)) { Text("$h h") }
                    }
                }
            }
        }

        SectionTitle("Himmelskarte")
        Group {
            ToggleRow(Icons.Rounded.Explore, "Kompass", "Die Karte dreht sich mit dem Handy", s.compassSky, a, s.haptics) { set { s.compassSky = it } }
            Divider16()
            ToggleRow(Icons.Rounded.WbSunny, "Sonne zeigen", "Damit du nicht gegen das Licht schaust", s.showSun, a, s.haptics) { set { s.showSun = it } }
            Divider16()
            ToggleRow(Icons.Rounded.ShowChart, "Doppler-Kurve", "Frequenzverschiebung im Pass", s.dopplerChart, a, s.haptics) { set { s.dopplerChart = it } }
        }

        SectionTitle("Weltkarte")
        Group {
            ToggleRow(Icons.Rounded.TripOrigin, "Sichtkreise", "Wo der Satellit über dem Horizont steht", s.footprints, a, s.haptics) { set { s.footprints = it } }
            Divider16()
            ToggleRow(Icons.Rounded.Public, "Tag und Nacht", "Grenze der Dämmerung", s.terminator, a, s.haptics) { set { s.terminator = it } }
            Divider16()
            ToggleRow(Icons.Rounded.Bedtime, "Nachtlichter", "Städte bei Nacht als Grund", s.nightLights, a, s.haptics) { set { s.nightLights = it } }
            Column(Modifier.padding(16.dp)) {
                Text("Spurlänge ${s.trackMinutes} min", style = MaterialTheme.typography.bodyLarge)
                Slider(s.trackMinutes.toFloat(), { s.trackMinutes = it.toInt() }, valueRange = 30f..300f, steps = 8, onValueChangeFinished = { set {} })
            }
        }

        SectionTitle("Zeit und Gefühl")
        Group {
            ToggleRow(Icons.Rounded.Schedule, "UTC", "Statt Ortszeit, wie im Funk üblich", s.utc, a, s.haptics) { set { s.utc = it } }
            Divider16()
            ToggleRow(Icons.Rounded.Vibration, "Haptik", "Klicken beim Umschalten", s.haptics, a, s.haptics) { set { s.haptics = it } }
        }

        SectionTitle("Standort")
        Group {
            ToggleRow(Icons.Rounded.MyLocation, "Standort vom Handy", "Ungefähr genügt für Bahnen", s.useGps, a, s.haptics) { set { s.useGps = it }; if (it) onLocate() }
            Text("%.4f°  %.4f°  %.0f m".format(s.lat, s.lon, s.alt), fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, bottom = 14.dp))
        }

        SectionTitle("Bahndaten")
        Group {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quelle: ${vm.tracker.source}", style = MaterialTheme.typography.bodyLarge)
                    Text(if (age >= 0) "Stand vor %.1f Tagen. Älter als eine Woche wird ungenau.".format(age) else "Noch keine Bahndaten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.refreshing) LinearProgressIndicator(Modifier.width(40.dp))
                else TextButton(onClick = vm::refreshTle) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(6.dp)); Text("Laden") }
            }
        }

        SectionTitle("Über")
        Text("Downlink ist Open Source. Bahnen: SGP4 über predict4java, Bahndaten von CelesTrak. Karte: Natural Earth. Erdbilder: Solar System Scope, CC BY 4.0.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) { content() }
}
