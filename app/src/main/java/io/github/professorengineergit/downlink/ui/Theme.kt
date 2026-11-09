package io.github.professorengineergit.downlink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.professorengineergit.downlink.R

/* --- Downlink: Nachthimmel, Funk, Messgerät. Tiefes Blau statt Grün, Mint als Signalfarbe. --- */

data class Accent(val name: String, val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color, val glow: Color)

val Mint = Accent("Mint", Color(0xFF7CDAA9), Color(0xFF00321F), Color(0xFF0E4A36), Color(0xFFA6F5CB), Color(0xFF8FD3F0))
val Sky = Color(0xFF8FD3F0)
val Amber = Color(0xFFFFB86B)
val Coral = Color(0xFFFF8A80)
val Mono = FontFamily.Monospace

// Roboto liegt in der App (Variable Font, OFL), damit Überschriften auf jedem Gerät gleich aussehen
@OptIn(ExperimentalTextApi::class)
val Roboto = FontFamily(
    listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium, 600 to FontWeight.SemiBold, 700 to FontWeight.Bold).map { (w, fw) ->
        Font(R.font.roboto, fw, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

// Grundfarben der App
object Ink {
    val space = Color(0xFF04070D)     // ganz unten, fast schwarz
    val deep = Color(0xFF07101C)
    val text = Color(0xFFE6EDF5)
    val dim = Color(0xFF9DABBC)
    val faint = Color(0xFF5E6B7B)
    val line = Color(0x1FFFFFFF)
    val glass = Color(0x8C101A27)     // Material-Fläche mit Durchsicht
    val glassHi = Color(0x24FFFFFF)
    val edge = Color(0x2EFFFFFF)
    val outline = Color(0x40FFFFFF)      // Standard-Umrandung aller Flächen
    val outlineOn = Mint.primary.copy(alpha = 0.85f) // gewählt oder aktiv: immer Mint
}

@Composable
fun DownlinkTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Mint.primary,
        onPrimary = Mint.onPrimary,
        primaryContainer = Mint.container,
        onPrimaryContainer = Mint.onContainer,
        secondary = Sky,
        onSecondary = Color(0xFF00344A),
        secondaryContainer = Color(0xFF16334A),
        onSecondaryContainer = Color(0xFFC6E9FA),
        tertiary = Amber,
        tertiaryContainer = Color(0xFF4A3010),
        onTertiaryContainer = Color(0xFFFFDDB8),
        background = Ink.space,
        onBackground = Ink.text,
        surface = Ink.deep,
        onSurface = Ink.text,
        surfaceVariant = Color(0xFF1C2633),
        onSurfaceVariant = Ink.dim,
        surfaceContainerLowest = Color(0xFF03060B),
        surfaceContainerLow = Color(0xFF0B131F),
        surfaceContainer = Color(0xFF101A27),
        surfaceContainerHigh = Color(0xFF172231),
        surfaceContainerHighest = Color(0xFF1F2B3B),
        outline = Color(0xFF6C7A8C),
        outlineVariant = Color(0xFF253244),
        error = Coral,
    )
    val b0 = Typography()
    fun TextStyle.r() = copy(fontFamily = Roboto)
    val base = b0.copy(
        displayLarge = b0.displayLarge.r(), displayMedium = b0.displayMedium.r(), displaySmall = b0.displaySmall.r(),
        headlineLarge = b0.headlineLarge.r(), headlineMedium = b0.headlineMedium.r(), headlineSmall = b0.headlineSmall.r(),
        titleLarge = b0.titleLarge.r(), titleMedium = b0.titleMedium.r(), titleSmall = b0.titleSmall.r(),
        bodyLarge = b0.bodyLarge.r(), bodyMedium = b0.bodyMedium.r(), bodySmall = b0.bodySmall.r(),
        labelLarge = b0.labelLarge.r(), labelMedium = b0.labelMedium.r(), labelSmall = b0.labelSmall.r(),
    )
    val type = base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.4.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type) { ProvideTextStyle(type.bodyLarge, content) }
}

/* --- Himmelsfarben nach Sonnenstand: Nacht, Dämmerung, Tag. Drei Töne: Zenit, Mitte, Horizont. --- */
data class SkyTone(val zenith: Color, val mid: Color, val horizon: Color, val glow: Color, val stars: Float)

private val NIGHT = SkyTone(Color(0xFF03060F), Color(0xFF07122A), Color(0xFF10284A), Color(0xFF1E4A7A), 1f)
private val ASTRO = SkyTone(Color(0xFF040919), Color(0xFF0E1C44), Color(0xFF2A2D63), Color(0xFF5A4C8C), 0.8f)
private val NAUTICAL = SkyTone(Color(0xFF071230), Color(0xFF1E2D66), Color(0xFF6A4A7C), Color(0xFFD9806A), 0.45f)
private val CIVIL = SkyTone(Color(0xFF0E1F48), Color(0xFF34508E), Color(0xFFE59466), Color(0xFFFFB27A), 0.1f)
private val DAY = SkyTone(Color(0xFF173E78), Color(0xFF3471B8), Color(0xFF8FC0EA), Color(0xFFE8F3FF), 0f)

fun skyTone(sunEl: Double): SkyTone {
    val stops = listOf(-18.0 to NIGHT, -12.0 to ASTRO, -6.0 to NAUTICAL, 0.0 to CIVIL, 8.0 to DAY)
    if (sunEl <= stops.first().first) return NIGHT
    if (sunEl >= stops.last().first) return DAY
    val i = stops.indexOfLast { it.first <= sunEl }
    val (e0, a) = stops[i]; val (e1, b) = stops[i + 1]
    val f = ((sunEl - e0) / (e1 - e0)).toFloat()
    fun l(x: Color, y: Color) = Color(x.red + (y.red - x.red) * f, x.green + (y.green - x.green) * f, x.blue + (y.blue - x.blue) * f, 1f)
    return SkyTone(l(a.zenith, b.zenith), l(a.mid, b.mid), l(a.horizon, b.horizon), l(a.glow, b.glow), a.stars + (b.stars - a.stars) * f)
}

fun skyName(sunEl: Double) = when {
    sunEl < -18 -> "Nacht"
    sunEl < -12 -> "Astronomische Dämmerung"
    sunEl < -6 -> "Nautische Dämmerung"
    sunEl < 0 -> "Bürgerliche Dämmerung"
    else -> "Tag"
}
