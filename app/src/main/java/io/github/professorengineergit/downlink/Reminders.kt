package io.github.professorengineergit.downlink

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.ui.spriteRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --- Erinnerung vor einem guten Überflug. Wecker über den AlarmManager, keine Dienste im Hintergrund. --- */
object Reminders {
    private const val MAX = 16
    const val CHANNEL = "passes"

    fun schedule(ctx: Context, passes: List<Pass>, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX) PendingIntent.getBroadcast(ctx, i, Intent(ctx, PassAlarm::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it) }
        if (!s.remind) return
        val now = System.currentTimeMillis()
        passes.filter { it.maxEl >= s.remindMinEl && it.aos - s.remindMinutes * 60_000L > now }.take(MAX).forEachIndexed { i, p ->
            val intent = Intent(ctx, PassAlarm::class.java)
                .putExtra("label", p.sat.label).putExtra("aos", p.aos).putExtra("el", p.maxEl).putExtra("verdict", p.verdict)
                .putExtra("sprite", p.sat.sprite).putExtra("color", p.sat.color.toInt()).putExtra("mode", p.sat.mode)
                .putExtra("visible", p.visible).putExtra("freq", p.sat.freqMHz)
            val pi = PendingIntent.getBroadcast(ctx, i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.aos - s.remindMinutes * 60_000L, pi)
        }
    }

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Überflüge", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Kurz bevor ein Satellit über dir aufgeht"
        })
    }
}

class PassAlarm : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Reminders.channel(ctx)
        val aos = i.getLongExtra("aos", 0L)
        val mins = ((aos - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(aos))
        val label = i.getStringExtra("label") ?: "Satellit"
        val freq = i.getDoubleExtra("freq", 0.0)
        val what = if (freq > 0) "%.3f MHz · %s".format(freq, i.getStringExtra("mode")) else if (i.getBooleanExtra("visible", false)) "mit bloßem Auge sichtbar" else "Überflug"
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sat)
            .setLargeIcon(BitmapFactory.decodeResource(ctx.resources, spriteRes(i.getStringExtra("sprite") ?: "")))
            .setColor(i.getIntExtra("color", 0xFF7CDAA9.toInt()))
            .setContentTitle("$label in $mins min")
            .setContentText("Aufgang $time · höchstens %.0f° · %s · %s".format(i.getDoubleExtra("el", 0.0), i.getStringExtra("verdict"), what))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(aos).setUsesChronometer(true).setChronometerCountDown(true)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(aos.toInt(), n) }
    }
}
