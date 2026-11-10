package io.github.professorengineergit.downlink.night

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.professorengineergit.downlink.MainActivity
import io.github.professorengineergit.downlink.R
import io.github.professorengineergit.downlink.Settings
import io.github.professorengineergit.downlink.orbit.Catalog
import io.github.professorengineergit.downlink.orbit.Pass
import io.github.professorengineergit.downlink.orbit.SatInfo
import io.github.professorengineergit.downlink.orbit.Tracker
import io.github.professorengineergit.downlink.sdr.MeteorImages
import io.github.professorengineergit.downlink.sdr.Receiver
import io.github.professorengineergit.downlink.sdr.Receiver.Link
import io.github.professorengineergit.downlink.sdr.Receiver.Mode
import io.github.professorengineergit.downlink.ui.doppler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --- Nachtmodus: das Handy hängt am Ladegerät, der Stick an der Antenne, und die App nimmt jeden gewählten Überflug
   von selbst auf. Der Zustand liegt hier, damit der Bildschirm ihn lesen kann, auch wenn der Dienst weiterläuft. --- */
object Night {
    data class Plan(val key: String, val label: String, val aos: Long, val los: Long, val maxEl: Double)
    data class Entry(val id: String, val key: String, val label: String, val aos: Long, val maxEl: Double, val result: String, val snr: Float, val peak: Float, val hits: Int, val mb: Long, val note: String)

    var running by mutableStateOf(false)
    var status by mutableStateOf("Aus")
    var upcoming by mutableStateOf<List<Plan>>(emptyList())
    var log by mutableStateOf<List<Entry>>(emptyList())
    var tleAgeDays by mutableStateOf(-1.0)

    private fun file(ctx: Context) = File(ctx.filesDir, "night_log.json")

    fun load(ctx: Context) {
        if (log.isNotEmpty()) return
        log = runCatching {
            val a = JSONArray(file(ctx).readText())
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let { o ->
                    Entry(o.getString("id"), o.getString("key"), o.getString("label"), o.getLong("aos"), o.getDouble("el"), o.getString("result"),
                        o.getDouble("snr").toFloat(), o.getDouble("peak").toFloat(), o.getInt("hits"), o.getLong("mb"), o.getString("note"))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun put(ctx: Context, e: Entry) {
        log = (listOf(e) + log.filter { it.id != e.id }).take(120)
        runCatching {
            val a = JSONArray()
            log.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("key", x.key).put("label", x.label).put("aos", x.aos).put("el", x.maxEl).put("result", x.result)
                    .put("snr", x.snr.toDouble()).put("peak", x.peak.toDouble()).put("hits", x.hits).put("mb", x.mb).put("note", x.note))
            }
            file(ctx).writeText(a.toString())
        }
    }

    /** Mittenfrequenz je Satellit. Bei der ISS liegt sie zwischen Sprache (145,800) und APRS (145,825), beide passen ins Band. */
    fun hzFor(s: SatInfo): Long = when (s.key) {
        "iss" -> 145_812_500L
        else -> (s.freqMHz * 1e6).toLong()
    }

    /** Satelliten, die der Nachtmodus aufnehmen kann */
    fun candidates() = Catalog.filter { it.receivable && it.key != "io117" }
}

class NightService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val pending = java.util.concurrent.CopyOnWriteArrayList<Job>()
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == STOP) { shutdown(); return START_NOT_STICKY }
        val ok = runCatching {
            ServiceCompat.startForeground(this, ID, notification("Nachtmodus startet"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.isSuccess
        if (!ok) { Night.status = "Android lässt den Dienst nicht starten. Stick anstecken und Zugriff erlauben."; stopSelf(); return START_NOT_STICKY }
        if (job == null) {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "downlink:night").apply { acquire(14 * 3600_000L) }
            job = scope.launch {
                runCatching { night() }
                // Bilder zu Ende rechnen lassen, bevor der Dienst endet, damit das Ergebnis im Protokoll landet
                if (isActive) { say("Fertig, rechne noch Bilder"); pending.forEach { j -> withTimeoutOrNull(25 * 60_000L) { j.join() } }; say("Fertig, Nachtmodus beendet") }
                shutdown()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { cleanup(); scope.cancel() }

    private fun shutdown() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        job?.cancel(); job = null
        if (Receiver.mode == Mode.Record) Receiver.stop()
        wake?.runCatching { if (isHeld) release() }; wake = null
        Night.running = false
        if (!Night.status.startsWith("Akku") && !Night.status.startsWith("Speicher") && !Night.status.startsWith("Fertig")) Night.status = "Aus"
        Night.upcoming = emptyList()
    }

    // ---------- Benachrichtigung ----------

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Nachtmodus", NotificationManager.IMPORTANCE_LOW).apply { description = "Läuft, solange Downlink Überflüge aufnimmt" })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, NightService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sat).setContentTitle("Downlink Nachtmodus").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Beenden", stop).build()
    }

    private fun say(text: String) {
        Night.status = text
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification(text)) }
    }

    // ---------- Ablauf ----------

    private fun battery(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return (level * 100 / scale.coerceAtLeast(1)) to ((i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
    }

    private suspend fun ensureStick(s: Settings): Boolean {
        repeat(25) {
            if (Receiver.link == Link.Ready && Receiver.device(this) == null) Receiver.disconnect()
            if (Receiver.link == Link.Ready) return true
            if (Receiver.link == Link.NoDevice || Receiver.link == Link.Failed) Receiver.connect(this, s.sdrPpm, s.sdrGain)
            delay(1000)
        }
        return Receiver.link == Link.Ready
    }

    private suspend fun night() {
        Night.running = true
        Night.load(this)
        val tracker = Tracker(this)
        withContext(Dispatchers.IO) { tracker.load() }
        var lastTle = 0L
        val done = HashSet<String>()
        val deadline = Settings(this).nightUntil.let { h ->
            if (h < 0) Long.MAX_VALUE else java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (scope.isActive && job?.isActive != false) {
            val s = Settings(this)
            if (System.currentTimeMillis() - lastTle > 6 * 3600_000L) {
                say("Lade frische Bahndaten")
                withContext(Dispatchers.IO) { tracker.refresh() }
                lastTle = System.currentTimeMillis()
            }
            Night.tleAgeDays = if (tracker.tleAge > 0) (System.currentTimeMillis() - tracker.tleAge) / 86_400_000.0 else -1.0
            val sats = Catalog.filter { it.key in s.nightSats && tracker.available(it) }
            val now = System.currentTimeMillis()
            val passes = tracker.passes(sats, s.observer, now, 24, s.nightMinEl.toDouble())
                .filter { it.los > now && "${it.sat.key}${it.aos}" !in done && it.aos - 180_000L < deadline && freeEl(it, s) >= s.nightMinEl }
            Night.upcoming = passes.take(8).map { Night.Plan(it.sat.key, it.sat.label, it.aos, it.los, it.maxEl) }
            val next = passes.firstOrNull()
            if (next == null) {
                if (deadline != Long.MAX_VALUE && System.currentTimeMillis() < deadline) { say("Fertig, keine weiteren Überflüge bis ${fmt.format(Date(deadline))}"); return }
                say("Wartet, keine passenden Überflüge in den nächsten 24 Stunden"); delay(10 * 60_000L); continue
            }

            val meteor = next.sat.key.startsWith("m2")
            val start = next.aos - if (meteor) 90_000L else 180_000L
            val recheck = System.currentTimeMillis() + 30 * 60_000L
            while (System.currentTimeMillis() < start && System.currentTimeMillis() < recheck) {
                val mins = ((start - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                val (lvl, plugged) = battery()
                if (lvl < 15 && !plugged) { say("Akku bei $lvl %, Nachtmodus beendet. Ladegerät anschließen."); return }
                say("Nächster: ${next.sat.label} um ${fmt.format(Date(next.aos))}, ${"%.0f".format(next.maxEl)}° · noch $mins min")
                delay(15_000L)
            }
            if (System.currentTimeMillis() < start) continue
            done += "${next.sat.key}${next.aos}"
            if (!record(next, tracker, s)) return
        }
    }

    /** Nimmt einen Überflug auf. Gibt false zurück, wenn der Nachtmodus ganz enden soll. */
    private suspend fun record(p: Pass, tracker: Tracker, s: Settings): Boolean {
        val id = "${p.sat.key}${p.aos}"
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val (lvl, plugged) = battery()
        if (lvl < 15 && !plugged) { say("Akku bei $lvl %, Nachtmodus beendet. Ladegerät anschließen."); return false }
        if (!ensureStick(s)) {
            Night.put(this, Night.Entry(id, p.sat.key, p.sat.label, p.aos, p.maxEl, "Fehler", 0f, 0f, 0, 0, "Der Stick war nicht bereit. Ist er angesteckt und der Zugriff erlaubt?"))
            return true
        }
        val dir = File(getExternalFilesDir(null), "baseband").apply { mkdirs() }
        if (dir.usableSpace < 1_500_000_000L) { say("Speicher fast voll, Nachtmodus beendet"); return false }
        val hz = Night.hzFor(p.sat)
        Receiver.tune(hz); Receiver.updateDoppler(0f); Receiver.resetPeaks()
        Receiver.start(Mode.Record, this, p.sat.key)
        val file = Receiver.recordFile
        val end = p.los + 120_000L
        var lost = false
        while (System.currentTimeMillis() < end && job?.isActive != false) {
            val now = System.currentTimeMillis()
            tracker.live(p.sat, s.observer, now)?.let { lv -> Receiver.updateDoppler((doppler(lv.rangeRate, hz / 1e6) * 1000).toFloat()) }
            if (Receiver.link != Link.Ready || Receiver.device(this) == null) { lost = true; break }
            val el = tracker.live(p.sat, s.observer, now)?.el ?: 0.0
            say("Nimmt ${p.sat.label} auf · %.0f° hoch · Signal %.1f dB · bis %s".format(el, Receiver.peakBandSnr.coerceAtLeast(0f), fmt.format(Date(end))))
            delay(1000)
        }
        val snr = Receiver.peakBandSnr; val peak = Receiver.peakBinSnr; val hits = Receiver.hits
        Receiver.stop()
        val mb = (file?.length() ?: 0L) / 1_000_000L
        val result = when {
            lost -> "Fehler"
            p.sat.key.startsWith("m2") -> "Aufgenommen"
            hits >= 3 -> "Signal"
            else -> "Ruhig"
        }
        val note = when {
            lost -> "Der Stick wurde mitten im Überflug getrennt."
            p.sat.key.startsWith("m2") -> "Wird decodiert"
            hits >= 3 -> "$hits Spitzen im Spektrum, die Aufnahme liegt auf dem Handy."
            else -> "Nichts über dem Rauschen."
        }
        Night.put(this, Night.Entry(id, p.sat.key, p.sat.label, p.aos, p.maxEl, result, snr, peak, hits, mb, note))
        // Bei Meteor das Ergebnis der Bildberechnung nachtragen
        if (p.sat.key.startsWith("m2") && file != null) pending += scope.launch {
            delay(1500)
            var w = 0
            while (MeteorImages.decoding && w++ < 600) delay(1000)
            val msg = MeteorImages.message ?: "Fertig"
            Night.put(this@NightService, Night.Entry(id, p.sat.key, p.sat.label, p.aos, p.maxEl, if (msg.contains("Zeilen")) "Bild" else "Kein Bild", snr, peak, hits, mb, msg))
        }
        // Ruhige FM-Aufnahmen brauchen wir nicht, und der Speicher ist begrenzt
        if (result == "Ruhig" && file != null) file.delete()
        prune(dir, file)
        return true
    }

    /** Höchster Stand dieses Überflugs innerhalb der freien Himmelshälfte (Antenne hinter einer Hauswand) */
    private fun freeEl(p: Pass, s: Settings): Double {
        if (s.nightFreeAz < 0) return p.maxEl
        return p.samples.filter { sm -> val d = Math.abs(((sm.az - s.nightFreeAz + 540) % 360) - 180); d <= 90 }.maxOfOrNull { it.el } ?: 0.0
    }

    private fun prune(dir: File, keep: File?) {
        val cap = 4_000_000_000L
        val files = dir.listFiles { f -> f.name.endsWith(".s8") }?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= cap) break; if (f == keep) continue; total -= f.length(); f.delete() }
    }

    companion object {
        const val ID = 4711
        const val CHANNEL = "night"
        const val STOP = "io.github.professorengineergit.downlink.NIGHT_STOP"

        fun start(ctx: Context) = androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, NightService::class.java))
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, NightService::class.java).setAction(STOP)) }
    }
}
