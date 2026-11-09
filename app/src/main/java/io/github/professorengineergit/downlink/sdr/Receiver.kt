package io.github.professorengineergit.downlink.sdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* --- Der Empfänger: verbindet den RTL-SDR-Stick, zeigt Spektrum und Wasserfall, macht FM hörbar,
   nimmt Meteor-Basisband auf und misst den Stick ein. Ein einziges Gerät, deshalb ein Objekt. --- */
object Receiver : SampleSink {
    enum class Link { NoDevice, NeedsPermission, Connecting, Ready, Failed }
    enum class Mode { Idle, Spectrum, Audio, Record }

    const val RATE_AUDIO = 1_536_000   // /8 -> 192 k -> /4 -> 48 k
    const val RATE_RECORD = 1_152_000  // /6 -> 192 k Basisband für Meteor-LRPT
    const val BB_RATE = 192_000
    const val OFFSET = 250_000          // Abstand zur Mittenfrequenz, damit die DC-Spitze nicht stört
    const val BINS = 512

    var link by mutableStateOf(Link.NoDevice); private set
    var error by mutableStateOf<String?>(null); private set
    var product by mutableStateOf(""); private set
    var tuner by mutableStateOf(""); private set
    var gains by mutableStateOf(IntArray(0)); private set
    var mode by mutableStateOf(Mode.Idle); private set
    var targetHz by mutableLongStateOf(137_900_000L); private set
    var centerHz by mutableLongStateOf(0L); private set
    var dopplerHz by mutableFloatStateOf(0f)
    var rate by mutableIntStateOf(RATE_AUDIO); private set
    var gain by mutableIntStateOf(-1); private set
    var ppm by mutableIntStateOf(0); private set
    var levelDb by mutableFloatStateOf(-100f); private set   // Spitze nahe der Zielfrequenz
    var noiseDb by mutableFloatStateOf(-100f); private set   // Median des Spektrums
    var bandDb by mutableFloatStateOf(-100f); private set    // mittlere Leistung im Kanal
    var clipping by mutableFloatStateOf(0f); private set
    var frame by mutableLongStateOf(0L); private set
    var recordedBytes by mutableLongStateOf(0L); private set
    var recordFile by mutableStateOf<File?>(null); private set

    /** Spektrum (dB) und Wasserfall-Ring, vom UI gelesen. */
    val spectrum = FloatArray(BINS) { -100f }
    const val ROWS = 220
    /** Wasserfall als fertige Farben, ein Ring von Zeilen; die neueste liegt bei waterRow, ältere darunter. */
    val waterColors = IntArray(BINS * ROWS) { 0xFF040A16.toInt() }
    @Volatile var waterRow = 0; private set

    private var handle = 0L
    private var appCtx: Context? = null
    private var conn: UsbDeviceConnection? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val ACTION = "io.github.professorengineergit.downlink.USB_PERMISSION"

    // DSP-Zustand (nur auf dem Lesethread benutzt)
    private val fft = Fft(2048)
    private val fRe = FloatArray(2048); private val fIm = FloatArray(2048); private val fDb = FloatArray(2048)
    private var bRe = FloatArray(0); private var bIm = FloatArray(0)
    private var dRe = FloatArray(0); private var dIm = FloatArray(0)
    private var aRe = FloatArray(0); private var aIm = FloatArray(0)
    private val audio = FloatArray(8192)
    private var mixer = Mixer(RATE_AUDIO.toDouble())
    private var dec1 = Decimator(lowpass(63, 0.042), 8)
    private var dec2 = Decimator(lowpass(63, 0.045), 4)
    private var fm = FmDemod(48_000.0, 5_000.0)
    private var track: AudioTrack? = null
    private var out: BufferedOutputStream? = null
    private var rms = 0.05f
    private var tick = 0
    private var capture: Capture? = null
    private var lastPost = 0L
    private var lastSlow = 0L
    /** Rauschboden für die Zeichnung, ohne Compose-Zustand */
    @Volatile var liveNoise = -100f; private set
    /** Spitzenwerte seit resetPeaks(): Kanalleistung und stärkster Punkt über dem Rauschen, dazu Anzahl der Bilder mit auffälliger Spitze (Pakete). */
    @Volatile var peakBandSnr = -100f; private set
    @Volatile var peakBinSnr = -100f; private set
    @Volatile var hits = 0; private set
    fun resetPeaks() { peakBandSnr = -100f; peakBinSnr = -100f; hits = 0 }

    private class Capture(val n: Int, val blocks: Int) {
        val f = Fft(n); val acc = DoubleArray(n); val re = FloatArray(n); val im = FloatArray(n); val db = FloatArray(n)
        var done = 0; var clips = 0L; var total = 0L
        val result = CompletableDeferred<DoubleArray>()
    }

    // ---------- Verbindung ----------

    fun device(ctx: Context): UsbDevice? {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList.values.firstOrNull { it.vendorId == 0x0bda && it.productId in setOf(0x2832, 0x2838) }
    }

    fun connect(ctx: Context, savedPpm: Int, savedGain: Int) {
        appCtx = ctx.applicationContext
        if (link == Link.Ready || link == Link.Connecting) return
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val d = device(ctx) ?: run { link = Link.NoDevice; return }
        if (!um.hasPermission(d)) {
            link = Link.NeedsPermission
            val app = ctx.applicationContext
            val filter = IntentFilter(ACTION)
            val rec = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    runCatching { app.unregisterReceiver(this) }
                    if (um.hasPermission(d)) open(um, d, savedPpm, savedGain) else { link = Link.Failed; error = "Zugriff auf den Stick wurde abgelehnt." }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(rec, filter, Context.RECEIVER_NOT_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(rec, filter)
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            um.requestPermission(d, PendingIntent.getBroadcast(app, 0, Intent(ACTION).setPackage(app.packageName), flags))
            return
        }
        open(um, d, savedPpm, savedGain)
    }

    private fun open(um: UsbManager, d: UsbDevice, savedPpm: Int, savedGain: Int) {
        link = Link.Connecting; error = null
        io.execute {
            val c = um.openDevice(d)
            if (c == null) { post { link = Link.Failed; error = "USB-Gerät ließ sich nicht öffnen." }; return@execute }
            val h = RtlNative.open(c.fileDescriptor)
            if (h == 0L) { c.close(); post { link = Link.Failed; error = "Der Stick antwortet nicht. Kurz ab- und wieder anstecken." }; return@execute }
            conn = c; handle = h
            val p = RtlNative.product(h).split("|")
            val g = RtlNative.gains(h)
            val t = RtlNative.tunerName(RtlNative.tunerType(h))
            RtlNative.setSampleRate(h, RATE_AUDIO)
            RtlNative.setPpm(h, savedPpm)
            RtlNative.setGain(h, savedGain)
            post {
                product = listOf(p.getOrNull(0), p.getOrNull(1)).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "RTL-SDR" }
                tuner = t; gains = g; ppm = savedPpm; gain = savedGain; rate = RATE_AUDIO
                link = Link.Ready
            }
        }
    }

    fun disconnect() {
        val h = handle
        handle = 0L
        io.execute {
            if (h != 0L) { RtlNative.stop(h); RtlNative.close(h) }
            conn?.close(); conn = null
            closeOutputs()
        }
        mode = Mode.Idle; link = Link.NoDevice
    }

    // ---------- Steuerung ----------

    fun setPpmNow(v: Int) { ppm = v; val h = handle; if (h != 0L) io.execute { RtlNative.setPpm(h, v) } }
    fun setGainNow(v: Int) { gain = v; val h = handle; if (h != 0L) io.execute { RtlNative.setGain(h, v) } }
    fun setBias(on: Boolean) { val h = handle; if (h != 0L) io.execute { RtlNative.setBiasTee(h, on) } }

    /** Stellt die Zielfrequenz ein; die Hardware sitzt OFFSET darunter, den Rest macht der Mischer. */
    fun tune(hz: Long) {
        targetHz = hz
        val c = hz - OFFSET
        centerHz = c
        mixer.offsetHz = (hz + dopplerHz - c).toDouble()
        val h = handle; if (h != 0L) io.execute { RtlNative.setFrequency(h, c.toInt()) }
    }

    fun updateDoppler(hz: Float) { dopplerHz = hz; mixer.offsetHz = (targetHz + hz - centerHz).toDouble() }

    fun start(newMode: Mode, ctx: Context? = null, label: String = "") {
        val h = handle; if (h == 0L) return
        stopStream()
        val r = if (newMode == Mode.Record) RATE_RECORD else RATE_AUDIO
        rate = r
        mixer = Mixer(r.toDouble())
        dec1 = if (r == RATE_RECORD) Decimator(lowpass(63, 0.062), 6) else Decimator(lowpass(63, 0.042), 8)
        dec2 = Decimator(lowpass(63, 0.045), 4)
        fm = FmDemod(48_000.0, 5_000.0)
        if (newMode == Mode.Audio) track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(48_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(48_000 * 4).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
        if (newMode == Mode.Record && ctx != null) {
            val dir = File(ctx.getExternalFilesDir(null), "baseband").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val f = File(dir, "${label.ifBlank { "pass" }}_${stamp}_${BB_RATE}SPS_${targetHz}Hz.s8")
            out = BufferedOutputStream(FileOutputStream(f), 1 shl 18)
            recordFile = f; recordedBytes = 0
        }
        tune(targetHz)
        io.execute { RtlNative.setSampleRate(h, r); RtlNative.setFrequency(h, centerHz.toInt()); RtlNative.start(h, this) }
        mode = newMode
    }

    fun stop() { stopStream(); mode = Mode.Idle }

    private fun stopStream() {
        val h = handle
        if (h != 0L) io.submit { RtlNative.stop(h) }.get()
        val wasRecording = out != null
        closeOutputs()
        // Nach einer Meteor-Aufnahme gleich das Bild daraus machen
        val f = recordFile
        val c = appCtx
        if (wasRecording && f != null && c != null && f.name.contains("Hz.s8") && targetHz in 137_000_000L..138_000_000L) MeteorImages.decode(c, f)
    }

    private fun closeOutputs() {
        track?.runCatching { stop(); release() }; track = null
        out?.runCatching { flush(); close() }; out = null
    }

    private fun post(block: () -> Unit) = main.post(block)

    // ---------- Proben verarbeiten (Lesethread) ----------

    override fun onSamples(buf: ByteArray, len: Int) {
        val n = len / 2
        if (bRe.size < n) { bRe = FloatArray(n); bIm = FloatArray(n); dRe = FloatArray(n / 6 + 8); dIm = FloatArray(n / 6 + 8); aRe = FloatArray(n / 24 + 8); aIm = FloatArray(n / 24 + 8) }
        var clips = 0
        for (i in 0 until n) {
            val a = buf[2 * i].toInt() and 0xff; val b = buf[2 * i + 1].toInt() and 0xff
            if (a == 0 || a == 255 || b == 0 || b == 255) clips++
            bRe[i] = (a - 127.5f) / 127.5f; bIm[i] = (b - 127.5f) / 127.5f
        }
        capture?.let { feedCapture(it, n, clips) }

        // Spektrum etwa 20-mal pro Sekunde
        if (++tick % 2 == 0 && n >= 2048) {
            val w = fft.window
            for (i in 0 until 2048) { fRe[i] = bRe[i] * w[i]; fIm[i] = bIm[i] * w[i] }
            fft.powerDb(fRe, fIm, fDb)
            for (k in 0 until BINS) { var m = -200f; for (j in 0 until 4) m = max(m, fDb[k * 4 + j]); spectrum[k] = spectrum[k] * 0.6f + m * 0.4f; rowTmp[k] = m }
            val sorted = fDb.copyOf().also { it.sort() }
            val med = sorted[1024]
            val r = (waterRow - 1 + ROWS) % ROWS
            val base = r * BINS
            for (k in 0 until BINS) waterColors[base + k] = heat(rowTmp[k], med)
            waterRow = r
            val binHz = rate / 2048f
            val center = 1024 + ((targetHz + dopplerHz - centerHz) / binHz).roundToInt()
            val half = (15_000 / binHz).roundToInt().coerceAtLeast(2)
            var peak = -200f; var sum = 0.0; var cnt = 0
            for (i in (center - half).coerceAtLeast(0)..(center + half).coerceAtMost(2047)) { peak = max(peak, fDb[i]); sum += Math.pow(10.0, fDb[i] / 10.0); cnt++ }
            val band = (10 * log10(sum / cnt.coerceAtLeast(1) + 1e-12)).toFloat()
            val clipF = clips / n.toFloat()
            val now = System.nanoTime()
            liveNoise = med
            if (mode == Mode.Record || mode == Mode.Audio) {
                peakBandSnr = max(peakBandSnr, band - med - 1.6f)
                peakBinSnr = max(peakBinSnr, peak - med)
                if (peak - med > 14f) hits++
            }
            if (now - lastPost > 33_000_000L) {
                lastPost = now
                val slow = now - lastSlow > 250_000_000L
                if (slow) lastSlow = now
                post { frame++; if (slow) { noiseDb = med; levelDb = peak; bandDb = band; clipping = clipF } }
            }
        }

        when (mode) {
            Mode.Audio -> {
                mixer.process(bRe, bIm, n)
                val m = dec1.process(bRe, bIm, n, dRe, dIm)
                val k = dec2.process(dRe, dIm, m, aRe, aIm)
                fm.process(aRe, aIm, k, audio)
                track?.write(audio, 0, k, AudioTrack.WRITE_NON_BLOCKING)
            }
            Mode.Record -> {
                mixer.process(bRe, bIm, n)
                val m = dec1.process(bRe, bIm, n, dRe, dIm)
                var e = 0f
                for (i in 0 until m) e += dRe[i] * dRe[i] + dIm[i] * dIm[i]
                rms = rms * 0.95f + sqrt(e / m.coerceAtLeast(1)) * 0.05f
                val scale = 38f / rms.coerceAtLeast(1e-4f)
                val bytes = ByteArray(m * 2)
                for (i in 0 until m) {
                    bytes[2 * i] = (dRe[i] * scale).roundToInt().coerceIn(-127, 127).toByte()
                    bytes[2 * i + 1] = (dIm[i] * scale).roundToInt().coerceIn(-127, 127).toByte()
                }
                out?.write(bytes)
                pendingBytes += bytes.size
                if (tick % 8 == 0) { val t = pendingBytes; pendingBytes = 0; post { recordedBytes += t } }
            }
            else -> {}
        }
    }
    private var pendingBytes = 0L
    private val rowTmp = FloatArray(BINS)

    // Farbskala des Wasserfalls: Nachtblau, Petrol, Mint, helles Gelb
    private val stops = intArrayOf(0xFF040A16.toInt(), 0xFF0E4A6E.toInt(), 0xFF3FD6A4.toInt(), 0xFFFFF4C2.toInt())
    private fun heat(db: Float, floor: Float): Int {
        val x = ((db - floor + 6f) / 36f).coerceIn(0f, 0.999f) * 3f
        val i = x.toInt(); val f = x - i
        val a = stops[i]; val b = stops[i + 1]
        fun ch(s: Int) = (((a shr s) and 0xff) + (((b shr s) and 0xff) - ((a shr s) and 0xff)) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun feedCapture(c: Capture, n: Int, clips: Int) {
        c.clips += clips; c.total += n
        var off = 0
        while (off + c.n <= n && c.done < c.blocks) {
            val w = c.f.window
            for (i in 0 until c.n) { c.re[i] = bRe[off + i] * w[i]; c.im[i] = bIm[off + i] * w[i] }
            c.f.powerDb(c.re, c.im, c.db)
            for (i in 0 until c.n) c.acc[i] += Math.pow(10.0, c.db[i] / 10.0)
            c.done++; off += c.n
        }
        if (c.done >= c.blocks) { capture = null; for (i in 0 until c.n) c.acc[i] /= c.blocks; c.result.complete(c.acc) }
    }

    /** Gemitteltes Leistungsspektrum (linear, DC in der Mitte) plus Anteil übersteuerter Proben. */
    suspend fun averaged(n: Int, blocks: Int): Pair<DoubleArray, Float> {
        val c = Capture(n, blocks); capture = c
        val r = c.result.await()
        return r to (c.clips / c.total.coerceAtLeast(1).toFloat())
    }

    private suspend fun hw(block: (Long) -> Unit) { val h = handle; if (h == 0L) return; val d = CompletableDeferred<Unit>(); io.execute { block(h); d.complete(Unit) }; d.await() }

    // ---------- Kalibrierung ----------

    data class PpmResult(val stationHz: Long, val snrDb: Float, val offsetHz: Double, val ppm: Int)

    /** Sucht den stärksten UKW-Sender und misst, wie weit seine Mitte neben der Sollfrequenz liegt. */
    suspend fun measurePpm(progress: (Float, String) -> Unit): PpmResult? {
        if (mode != Mode.Spectrum) start(Mode.Spectrum)
        var bestHz = 0L; var bestLvl = -1e9
        val steps = (88_000_000L..107_400_000L step 1_200_000L).toList()
        steps.forEachIndexed { i, c ->
            progress(i / (steps.size + 4f), "Suche UKW-Sender bei %.1f MHz".format(c / 1e6))
            hw { RtlNative.setFrequency(it, c.toInt()) }; delay(130)
            val (p, _) = averaged(4096, 6)
            val binHz = RATE_AUDIO / 4096.0
            for (k in 0 until 4096) {
                val f = (k - 2048) * binHz
                if (abs(f) < 30_000 || abs(f) > 600_000) continue
                var s = 0.0; for (j in -12..12) s += p[(k + j).coerceIn(0, 4095)]
                if (s > bestLvl) { bestLvl = s; bestHz = c + f.toLong() }
            }
        }
        if (bestHz == 0L) return null
        val nominal = (bestHz / 50_000.0).roundToInt() * 50_000L
        progress(steps.size / (steps.size + 4f), "Messe %.2f MHz genau".format(nominal / 1e6))
        val c = nominal - 300_000
        hw { RtlNative.setFrequency(it, c.toInt()) }; delay(200)
        var snr = 0f
        // Mitte des Senders: dreimal je etwa 1,4 Sekunden, Median
        suspend fun centre(label: String, base: Float): Double? {
            val offs = ArrayList<Double>()
            repeat(3) { rep ->
                progress(base + rep * 0.06f, "%s (%d/3)".format(label, rep + 1))
                val (p, _) = averaged(16384, 130)
                val binHz = RATE_AUDIO / 16384.0
                val sorted = p.copyOf().also { it.sort() }; val noise = sorted[8192]
                var w = 0.0; var fsum = 0.0; var peak = 0.0
                for (k in 0 until 16384) {
                    val f = (k - 8192) * binHz - 300_000
                    if (abs(f) > 90_000) continue
                    val v = p[k] - noise; if (v <= 0) continue
                    w += v; fsum += v * f; peak = max(peak, p[k])
                }
                if (w > 0) { offs += fsum / w; snr = (10 * log10(peak / noise)).toFloat() }
            }
            return offs.sorted().getOrNull(offs.size / 2)
        }
        // Zwei Messungen mit verschiedener Korrektur ergeben die Steigung; daraus der Nullpunkt.
        // So spielt es keine Rolle, wie stark und in welche Richtung der Treiber die Korrektur umsetzt.
        val p1 = ppm
        val off = centre("Messe %.2f MHz".format(nominal / 1e6), 0.55f) ?: return null
        val p2 = p1 + 10
        hw { RtlNative.setPpm(it, p2) }; delay(250)
        val off2 = centre("Gegenprobe mit +10 ppm", 0.75f)
        hw { RtlNative.setPpm(it, p1) }
        var slope = if (off2 != null) (off2 - off) / (p2 - p1) else 0.0
        if (abs(slope) < nominal * 0.3e-6) slope = nominal * 1e-6
        val total = (p1 - off / slope).roundToInt().coerceIn(-100, 100)
        tune(targetHz)
        return PpmResult(nominal, snr, off, total)
    }

    data class GainPoint(val tenthDb: Int, val floorDb: Float, val clip: Float)
    data class GainResult(val best: Int, val points: List<GainPoint>)

    /** Geht die Verstärkungsstufen durch: so hoch wie möglich, solange nichts übersteuert und das Rauschen noch sinnvoll steigt. */
    suspend fun measureGain(freqHz: Long, progress: (Float, String) -> Unit): GainResult? {
        if (gains.isEmpty()) return null
        if (mode != Mode.Spectrum) start(Mode.Spectrum)
        hw { RtlNative.setFrequency(it, (freqHz - OFFSET).toInt()) }
        val list = gains.filterIndexed { i, _ -> i % 2 == 0 || i == gains.lastIndex }
        val pts = ArrayList<GainPoint>()
        list.forEachIndexed { i, g ->
            progress(i / list.size.toFloat(), "Verstärkung %.1f dB".format(g / 10f))
            hw { RtlNative.setGain(it, g) }; delay(150)
            val (p, clip) = averaged(2048, 8)
            val sorted = p.copyOf().also { it.sort() }
            pts += GainPoint(g, (10 * log10(sorted[1024] + 1e-15)).toFloat(), clip)
        }
        val floor0 = pts.first().floorDb
        var best = pts.last { it.clip < 0.002f }.tenthDb
        // Sobald das Antennenrauschen das Eigenrauschen deutlich übersteigt, bringt mehr Verstärkung nichts mehr
        pts.firstOrNull { it.floorDb - floor0 >= 8f && it.clip < 0.002f }?.let { p -> val idx = pts.indexOf(p); best = pts[(idx + 1).coerceAtMost(pts.lastIndex)].takeIf { it.clip < 0.002f }?.tenthDb ?: p.tenthDb }
        setGainNow(best)
        tune(targetHz)
        return GainResult(best, pts)
    }

    data class Spur(val hz: Long, val db: Float)

    /** Schmale Störer im Band rund um die Zielfrequenz: Netzteile, LEDs, das Handy selbst. */
    suspend fun findSpurs(): List<Spur> {
        if (mode == Mode.Idle) start(Mode.Spectrum)
        val (p, _) = averaged(8192, 24)
        val binHz = rate / 8192.0
        val db = DoubleArray(8192) { 10 * log10(p[it] + 1e-15) }
        val med = db.copyOf().also { it.sort() }[4096]
        val res = ArrayList<Spur>()
        var k = 8
        while (k < 8184) {
            val f = (k - 4096) * binHz
            if (abs(f) > 20_000 && db[k] > med + 12 && db[k] >= db[k - 1] && db[k] >= db[k + 1] && db[k] - db[k - 6] > 6 && db[k] - db[k + 6] > 6) {
                res += Spur(centerHz + f.toLong(), (db[k] - med).toFloat()); k += 20
            } else k++
        }
        return res.sortedByDescending { it.db }.take(6)
    }
}
