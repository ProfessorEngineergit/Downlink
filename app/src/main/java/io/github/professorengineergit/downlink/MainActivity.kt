package io.github.professorengineergit.downlink

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ViewInAr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.SatInfo
import io.github.professorengineergit.downlink.ui.ArScreen
import io.github.professorengineergit.downlink.ui.DownlinkTheme
import io.github.professorengineergit.downlink.ui.GlassTabBar
import io.github.professorengineergit.downlink.ui.HomeScreen
import io.github.professorengineergit.downlink.ui.Ink
import io.github.professorengineergit.downlink.ui.Intro
import io.github.professorengineergit.downlink.ui.MapScreen
import io.github.professorengineergit.downlink.ui.ObjectScreen
import io.github.professorengineergit.downlink.ui.ObjectsScreen
import io.github.professorengineergit.downlink.ui.PassScreen
import io.github.professorengineergit.downlink.ui.SettingsScreen
import io.github.professorengineergit.downlink.ui.TabItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        Reminders.channel(this)
        setContent { DownlinkTheme { Surface(color = Ink.space, contentColor = Ink.text) { App(viewModel()) } } }
    }
}

private sealed interface Route {
    data class PassR(val p: Pass) : Route
    data class Obj(val info: SatInfo) : Route
    data object Ar : Route
    data object Gear : Route
    data class Rx(val p: Pass?) : Route
}

private val TABS = listOf(
    TabItem("Himmel", Icons.Rounded.Radar),
    TabItem("Karte", Icons.Rounded.Public),
    TabItem("Objekte", Icons.Rounded.Satellite),
    TabItem("Optionen", Icons.Rounded.Tune),
)

@Composable
private fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    val haze = remember { HazeState() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.locate() }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.reschedule() }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) stack = stack + Route.Ar }
    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val locate = { if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) vm.locate() else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val notify = { if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val openAr = { if (granted(Manifest.permission.CAMERA)) stack = stack + Route.Ar else askCamera.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(Unit) { if (granted(Manifest.permission.ACCESS_COARSE_LOCATION) && s.useGps) vm.locate() }

    if (!s.introDone) {
        Intro(vm, onLocate = locate, onNotify = notify, onDone = { s.introDone = true; s.save() })
        return
    }

    val top = stack.lastOrNull()
    val compassOn = s.compassSky || top is Route.PassR
    DisposableEffect(compassOn) { vm.compass(compassOn); onDispose { vm.compass(false) } }
    if (top != null) BackHandler { stack = stack.dropLast(1) }

    if (top is Route.Ar) { ArScreen(vm) { stack = stack.dropLast(1) }; return }

    val bottom = 96.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (top is Route.Rx) Modifier else Modifier.hazeSource(haze))) {
            AnimatedContent(top to tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { (r, t) ->
                when (r) {
                    is Route.Rx -> io.github.professorengineergit.downlink.ui.ReceiverScreen(vm, r.p, bottom) { stack = stack.dropLast(1) }
                    is Route.Gear -> io.github.professorengineergit.downlink.ui.GearShopScreen(bottom) { stack = stack.dropLast(1) }
                    is Route.PassR -> PassScreen(vm, r.p, bottom, onReceive = { stack = stack + Route.Rx(r.p) }) { stack = stack.dropLast(1) }
                    is Route.Obj -> ObjectScreen(vm, r.info, bottom, onBack = { stack = stack.dropLast(1) }, onPass = { stack = stack + Route.PassR(it) })
                    else -> when (t) {
                        0 -> HomeScreen(vm, bottom, onOpen = { stack = stack + Route.PassR(it) }, onAr = openAr)
                        1 -> MapScreen(vm, bottom) { stack = stack + Route.Obj(it) }
                        2 -> ObjectsScreen(vm, bottom) { stack = stack + Route.Obj(it) }
                        else -> SettingsScreen(vm, bottom, onLocate = locate, onNotify = notify, onIntro = { s.introDone = false; s.save() }, onGear = { stack = stack + Route.Gear }, onReceiver = { stack = stack + Route.Rx(null) })
                    }
                }
            }
        }
        if (top == null && tab != 1) {
            when (tab) {
                0 -> {
                    val sun = io.github.professorengineergit.downlink.orbit.Tracker.sunAzEl(s.observer, vm.now)
                    io.github.professorengineergit.downlink.ui.StickyHeader("Downlink", "${io.github.professorengineergit.downlink.ui.skyName(sun.second)} · %.2f° %.2f°".format(s.lat, s.lon), haze) {
                        if (vm.computing) androidx.compose.material3.LinearProgressIndicator(Modifier.width(40.dp).padding(end = 12.dp), color = io.github.professorengineergit.downlink.ui.Mint.primary, trackColor = io.github.professorengineergit.downlink.ui.Ink.line)
                        io.github.professorengineergit.downlink.ui.RoundIcon(androidx.compose.material.icons.Icons.Rounded.Explore, "Kompass", s.compassSky) { s.compassSky = !s.compassSky; s.save() }
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        io.github.professorengineergit.downlink.ui.RoundIcon(androidx.compose.material.icons.Icons.Rounded.ViewInAr, "AR-Ansicht", false, openAr)
                    }
                }
                2 -> io.github.professorengineergit.downlink.ui.StickyHeader("Objekte", "${s.enabled.size} von ${io.github.professorengineergit.downlink.orbit.Catalog.size} werden verfolgt", haze)
                else -> io.github.professorengineergit.downlink.ui.StickyHeader("Optionen", null, haze)
            }
        }
        GlassTabBar(TABS, tab, haze, s.haptics, onSelect = { tab = it; stack = emptyList() }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
