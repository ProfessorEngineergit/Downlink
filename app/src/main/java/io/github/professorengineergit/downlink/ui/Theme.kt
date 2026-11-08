package io.github.professorengineergit.downlink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* --- Material 3, dunkel, Mint: Funk, Messgerät, Nacht --- */

data class Accent(val name: String, val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color, val glow: Color)

val Mint = Accent("Mint", Color(0xFF7CDAA9), Color(0xFF003822), Color(0xFF005234), Color(0xFF98F7C4), Color(0xFF7CC8FF))
val Sky = Color(0xFFA5CDDE)
val Amber = Color(0xFFFFB86B)
val Mono = FontFamily.Monospace

@Composable
fun DownlinkTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Mint.primary,
        onPrimary = Mint.onPrimary,
        primaryContainer = Mint.container,
        onPrimaryContainer = Mint.onContainer,
        secondary = Color(0xFFB4CCBC),
        secondaryContainer = Color(0xFF364B3E),
        onSecondaryContainer = Color(0xFFD0E8D7),
        tertiary = Sky,
        tertiaryContainer = Color(0xFF244C5C),
        onTertiaryContainer = Color(0xFFC1E9FA),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFDFE4DD),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFDFE4DD),
        surfaceVariant = Color(0xFF404943),
        onSurfaceVariant = Color(0xFFC0C9C0),
        surfaceContainerLowest = Color(0xFF0A0F0C),
        surfaceContainerLow = Color(0xFF171D19),
        surfaceContainer = Color(0xFF1B211D),
        surfaceContainerHigh = Color(0xFF252B27),
        surfaceContainerHighest = Color(0xFF303631),
        outline = Color(0xFF8A938B),
        outlineVariant = Color(0xFF404943),
        error = Color(0xFFFFB4AB),
    )
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
