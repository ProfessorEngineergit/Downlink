package io.github.professorengineergit.downlink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* --- Der Schalter, den man anhaben will: leuchtende Spur, federnder Knopf, Haptik --- */
@Composable
fun GlowSwitch(checked: Boolean, accent: Accent, haptics: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val x by animateDpAsState(if (checked) 26.dp else 2.dp, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "x")
    val glow by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "glow")
    val thumbScale by animateFloatAsState(if (checked) 1f else 0.78f, spring(dampingRatio = 0.5f), label = "s")
    Box(
        Modifier
            .size(56.dp, 32.dp)
            .semantics { role = Role.Switch; stateDescription = if (checked) "An" else "Aus" }
            .drawBehind {
                // weiches Leuchten unter der Spur, nur wenn an: läuft rund aus, ohne Kante
                if (glow > 0f) drawCircle(
                    Brush.radialGradient(
                        0f to accent.primary.copy(alpha = 0.32f * glow),
                        0.55f to accent.primary.copy(alpha = 0.10f * glow),
                        1f to Color.Transparent,
                        center = center, radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = center,
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) Brush.horizontalGradient(listOf(accent.primary, accent.glow.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(Color(0xFF252B27), Color(0xFF252B27)))
            )
            .border(1.dp, if (checked) Color.White.copy(alpha = 0.25f) else Color(0xFF8A938B), RoundedCornerShape(16.dp))
            .clickable {
                if (haptics) haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onChange(!checked)
            }
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(28.dp)
                .scale(thumbScale)
                .clip(CircleShape)
                .background(if (checked) Color.White else Color(0xFF8A938B)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = accent.onPrimary, modifier = Modifier.size(16.dp).alpha(glow))
        }
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, accent: Accent, haptics: Boolean, onChange: (Boolean) -> Unit) {
    val tile by animateColorAsState(if (checked) accent.container else MaterialTheme.colorScheme.surfaceContainerHigh, label = "tile")
    val tint by animateColorAsState(if (checked) accent.onContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "tint")
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlowSwitch(checked, accent, haptics, onChange)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Glas: halbtransparent über dem Globus, mit Lichtkante oben
@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC121813))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(20.dp))
    ) { content() }
}

// Abzeichen auf geteilten Satellitenbildern
@Composable
fun CertifiedBadge(accent: Accent, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(accent.primary.copy(alpha = 0.22f), accent.glow.copy(alpha = 0.22f))))
            .border(1.dp, accent.primary.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent.primary))
        Spacer(Modifier.width(8.dp))
        Text("RECEIVED WITH DOWNLINK", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.4.sp, color = accent.onContainer)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
fun Divider16() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
