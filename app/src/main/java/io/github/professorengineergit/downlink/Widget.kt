package io.github.professorengineergit.downlink

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.ui.spriteRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --- Widget: der nächste gute Überflug mit gerendertem Satelliten, darunter die zwei danach --- */
object WidgetData {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

    suspend fun publish(ctx: Context, passes: List<Pass>) {
        val now = System.currentTimeMillis()
        val next = passes.filter { it.los > now }.take(3)
        val e = prefs(ctx).edit().clear().putInt("n", next.size)
        next.forEachIndexed { i, p ->
            e.putString("l$i", p.sat.label).putString("s$i", p.sat.sprite).putLong("a$i", p.aos).putLong("o$i", p.los)
                .putFloat("e$i", p.maxEl.toFloat()).putString("v$i", p.verdict).putInt("c$i", p.sat.color.toInt())
        }
        e.apply()
        runCatching { DownlinkWidget().updateAll(ctx) }
    }

    data class Item(val label: String, val sprite: String, val aos: Long, val los: Long, val el: Float, val verdict: String, val color: Int)

    fun read(ctx: Context): List<Item> {
        val p = prefs(ctx)
        return (0 until p.getInt("n", 0)).map { i ->
            Item(p.getString("l$i", "") ?: "", p.getString("s$i", "") ?: "", p.getLong("a$i", 0), p.getLong("o$i", 0), p.getFloat("e$i", 0f), p.getString("v$i", "") ?: "", p.getInt("c$i", 0))
        }.filter { it.los > System.currentTimeMillis() }
    }
}

class DownlinkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = WidgetData.read(context)
        provideContent { Content(items) }
    }

    @Composable
    private fun Content(items: List<WidgetData.Item>) {
        val white = ColorProvider(Color(0xFFE6EDF5))
        val dim = ColorProvider(Color(0xFF9DABBC))
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(actionStartActivity<MainActivity>()).padding(16.dp)) {
            if (items.isEmpty()) {
                Column {
                    Text("Downlink", style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text("Öffne die App, um Überflüge zu berechnen", style = TextStyle(color = dim, fontSize = 13.sp))
                }
                return@Box
            }
            val n = items.first()
            val col = ColorProvider(Color(n.color))
            Column(GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(spriteRes(n.sprite)), n.label, GlanceModifier.size(72.dp))
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(n.label.uppercase(), style = TextStyle(color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                        val inPass = now in n.aos..n.los
                        Text(if (inPass) "jetzt über dir" else fmt.format(Date(n.aos)), style = TextStyle(color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                        Text((if (inPass) "noch ${rel(n.los - now)}" else "in ${rel(n.aos - now)}") + " · %.0f° · %s".format(n.el, n.verdict), style = TextStyle(color = dim, fontSize = 13.sp))
                    }
                }
                if (items.size > 1) {
                    Spacer(GlanceModifier.height(10.dp))
                    items.drop(1).forEach { it2 ->
                        Row(GlanceModifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(ImageProvider(spriteRes(it2.sprite)), null, GlanceModifier.size(22.dp))
                            Spacer(GlanceModifier.width(8.dp))
                            Text(fmt.format(Date(it2.aos)), style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            Spacer(GlanceModifier.width(8.dp))
                            Text("${it2.label} · %.0f°".format(it2.el), style = TextStyle(color = dim, fontSize = 13.sp))
                        }
                    }
                }
            }
        }
    }

    private fun rel(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
}

class DownlinkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DownlinkWidget()
}
