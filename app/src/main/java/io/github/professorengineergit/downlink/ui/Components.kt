package io.github.professorengineergit.downlink.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.professorengineergit.downlink.R
import kotlin.math.cos
import kotlin.math.sin

/* --- Bausteine: Glas, Schalter, Tabbar, Satellitenbilder --- */

val GlassStyle = HazeStyle(
    backgroundColor = Ink.space,
    tints = listOf(HazeTint(Color(0x3310182A))),
    blurRadius = 24.dp,
    noiseFactor = 0f,
)

// Glaskarte: halbtransparent mit Lichtkante oben. Mit HazeState echt unscharf, sonst getönt.
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    haze: HazeState? = null,
    tint: Color = Color(0x8C101A27),
    outline: Color = Ink.outline,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .then(if (haze != null) Modifier.hazeEffect(haze, GlassStyle) else Modifier)
            .background(tint)
            .border(2.dp, outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/* --- Schalter: klar, federnd, ohne Leuchthof --- */
@Composable
fun DSwitch(checked: Boolean, haptics: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val x by animateDpAsState(if (checked) 24.dp else 4.dp, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "x")
    val thumb by animateFloatAsState(if (checked) 1f else 0.72f, spring(dampingRatio = 0.55f), label = "s")
    val track by animateColorAsState(if (checked) Mint.primary else Color(0x1FFFFFFF), label = "t")
    val knob by animateColorAsState(if (checked) Mint.onPrimary else Ink.dim, label = "k")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .semantics { role = Role.Switch; stateDescription = if (checked) "An" else "Aus" }
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else Color(0x33FFFFFF), CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) {
                if (haptics) haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onChange(!checked)
            },
    ) {
        Box(Modifier.offset(x = x, y = 4.dp).size(24.dp).scale(thumb).clip(CircleShape).background(knob))
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, haptics: Boolean, onChange: (Boolean) -> Unit) {
    val tint by animateColorAsState(if (checked) Mint.primary else Ink.dim, label = "tint")
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.text)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink.dim)
        }
        DSwitch(checked, haptics, onChange)
    }
}

@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Glass(modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 24.dp) { Column(content = content) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.dim, modifier = modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(Ink.line))
}

/* --- Tabbar: schwebende Pille aus Glas. Aktiver Tab als breite Pille mit Text, die anderen nur Symbol. --- */
data class TabItem(val label: String, val icon: ImageVector)

@Composable
fun GlassTabBar(items: List<TabItem>, selected: Int, haze: HazeState, haptics: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(CircleShape)
                .hazeEffect(haze, GlassStyle)
                .background(Color(0x1A182234))
                .padding(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, it ->
                    val on = i == selected
                    val bg by animateColorAsState(if (on) Mint.primary else Color.Transparent, label = "bg")
                    val fg by animateColorAsState(if (on) Mint.onPrimary else Ink.text.copy(alpha = 0.85f), label = "fg")
                    val w by animateDpAsState(if (on) 0.dp else 0.dp, label = "w")
                    Row(
                        Modifier
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .clickable(remember { MutableInteractionSource() }, null) {
                                if (!on && haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(i)
                            }
                            .padding(horizontal = if (on) 20.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(it.icon, it.label, tint = fg, modifier = Modifier.size(24.dp))
                        if (on) {
                            Spacer(Modifier.width(10.dp + w))
                            Text(it.label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/* --- Gerenderte Satelliten (Blender, Cycles). NASA-Modelle gemeinfrei, eigene Modelle für den Rest. --- */
fun spriteRes(name: String) = when (name) {
    "meteor" -> R.drawable.sat_meteor
    "metop" -> R.drawable.sat_metop
    "tiangong" -> R.drawable.sat_tiangong
    "iss" -> R.drawable.sat_iss
    "hubble" -> R.drawable.sat_hubble
    "npp" -> R.drawable.sat_npp
    "aqua" -> R.drawable.sat_aqua
    "terra" -> R.drawable.sat_terra
    "aura" -> R.drawable.sat_aura
    "landsat" -> R.drawable.sat_landsat
    "so50" -> R.drawable.sat_so50
    "jpss" -> R.drawable.sat_jpss
    "greencube" -> R.drawable.sat_greencube
    "diwata" -> R.drawable.sat_diwata
    "cube1" -> R.drawable.sat_cube1
    else -> R.drawable.sat_cube2
}

@Composable
fun SatSprite(sprite: String, color: Color, modifier: Modifier, glow: Boolean = true) {
    Box(modifier.drawBehind {
        if (glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.08f), Color.Transparent), center, size.minDimension * 0.55f), size.minDimension * 0.55f)
    }, contentAlignment = Alignment.Center) {
        Image(painterResource(spriteRes(sprite)), null, Modifier.fillMaxWidth().padding(2.dp))
    }
}

/* --- Bewertungsring: Wird es klappen? --- */
@Composable
fun ScoreRing(score: Int, color: Color, modifier: Modifier, stroke: Float = 6f) {
    val sweep by animateFloatAsState(score / 100f * 360f, spring(stiffness = Spring.StiffnessVeryLow), label = "ring")
    Canvas(modifier) {
        val r = size.minDimension / 2 - stroke
        val tl = Offset(center.x - r, center.y - r)
        drawCircle(Color(0x1FFFFFFF), r, center, style = Stroke(stroke))
        drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, Color.White, color.copy(alpha = 0.6f)), center), -90f, sweep, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/* --- Flagge der Europäischen Union: zwölf goldene Sterne im Kreis auf Blau --- */
@Composable
fun EuFlag(modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(3.dp))) {
        drawRect(Color(0xFF003399))
        val c = center; val ring = size.height / 3f; val sr = size.height / 18f
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0 - 90)
            val p = Offset(c.x + ring * cos(a).toFloat(), c.y + ring * sin(a).toFloat())
            val star = Path()
            for (j in 0 until 10) {
                val rr = if (j % 2 == 0) sr else sr * 0.4f
                val b = Math.toRadians(j * 36.0 - 90)
                val q = Offset(p.x + rr * cos(b).toFloat(), p.y + rr * sin(b).toFloat())
                if (j == 0) star.moveTo(q.x, q.y) else star.lineTo(q.x, q.y)
            }
            star.close()
            drawPath(star, Color(0xFFFFCC00))
        }
    }
}

@Composable
fun MadeInEu(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EuFlag(Modifier.size(27.dp, 18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Proudly developed in the European Union", style = MaterialTheme.typography.labelSmall, color = Ink.dim)
    }
}

// Kleine Kapsel für Zustände
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(CircleShape).background(if (filled) color else color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, color = if (filled) Ink.space else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}


val HeaderH = 62.dp

/** Fester Titel oben: alles darunter wird weich verwischt und läuft hinter dem Text aus. */
@Composable
fun StickyHeader(title: String, subtitle: String?, haze: HazeState, modifier: Modifier = Modifier, trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    Box(
        modifier.fillMaxWidth().height(HeaderH + androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .hazeEffect(haze, HazeStyle(blurRadius = 22.dp, tints = listOf(HazeTint(Ink.space.copy(alpha = 0.45f))))) {
                progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
            },
    ) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink.text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Ink.text.copy(alpha = 0.7f))
            }
            trailing()
        }
    }
}
