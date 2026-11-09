package io.github.professorengineergit.downlink.sdr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** One decoded MSU-MR channel (filled by native code). */
class LrptChannel(val apid: Int, val width: Int, val rows: Int, val pixels: ByteArray)

fun interface DecodeProgress { fun onProgress(fraction: Float, frames: Long, packets: Long, snr: Float) }

object MeteorDecoder {
    init { System.loadLibrary("downlinksdr") }
    @JvmStatic external fun decodeFile(path: String, rate: Double, progress: DecodeProgress?): Array<LrptChannel>
}

/* --- Meteor-Bilder: Aufnahme decodieren, als PNG in der App ablegen und das Farbbild in Fotos sichern. --- */
object MeteorImages {
    var decoding by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var frames by mutableLongStateOf(0L); private set
    var packets by mutableLongStateOf(0L); private set
    var snr by mutableFloatStateOf(0f); private set
    var message by mutableStateOf<String?>(null); private set
    var version by mutableIntStateOf(0); private set

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null), "images").apply { mkdirs() }
    fun pictures(ctx: Context): List<File> = dir(ctx).listFiles { f -> f.name.endsWith(".png") && !f.name.contains("_ch") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun channelsOf(picture: File): List<File> = picture.parentFile?.listFiles { f -> f.name.startsWith(picture.nameWithoutExtension + "_ch") }?.sortedBy { it.name } ?: emptyList()

    fun title(key: String) = when {
        key.startsWith("m23") -> "Meteor-M2 3"
        key.startsWith("m24") -> "Meteor-M2 4"
        else -> "Meteor"
    }

    fun decode(ctx: Context, recording: File, rate: Int = Receiver.BB_RATE) {
        if (decoding) return
        if (recording.length() < rate * 2L * 5) { message = "Die Aufnahme ist zu kurz für ein Bild."; return }
        val app = ctx.applicationContext
        decoding = true; progress = 0f; frames = 0; packets = 0; message = null
        worker.execute {
            val result = runCatching {
                val chans = MeteorDecoder.decodeFile(recording.absolutePath, rate.toDouble()) { f, fr, pk, s ->
                    main.post { progress = f; frames = fr; packets = pk; snr = s }
                }
                save(app, recording, chans.filter { it.rows >= 16 })
            }
            main.post {
                decoding = false
                message = result.getOrElse { "Fehler beim Decodieren: ${it.message}" }
                version++
            }
        }
    }

    private fun save(ctx: Context, recording: File, chans: List<LrptChannel>): String {
        if (chans.isEmpty()) return if (frames > 0) "Signal gefunden ($frames Rahmen), aber keine Bildzeilen. Die Aufnahme war wohl zu schwach." else "Kein Meteor-Signal in der Aufnahme gefunden."
        val key = recording.name.substringBefore('_')
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(recording.lastModified()))
        val base = "${key}_$stamp"
        val byApid = chans.associateBy { it.apid }
        // Kanäle einzeln
        chans.forEach { c -> png(grey(c), File(dir(ctx), "${base}_ch${c.apid}.png")) }
        // Farbbild: sichtbare Kanäle als 2-2-1, sonst der Kanal mit den meisten Zeilen
        val a = byApid[64]; val b = byApid[65]
        val picture = if (a != null && b != null) rgb(b, b, a) else grey(chans.maxBy { it.rows })
        png(picture, File(dir(ctx), "$base.png"))
        val saved = runCatching { toPhotos(ctx, picture, "Downlink_$base") }.isSuccess
        val rows = chans.maxOf { it.rows }
        return "${title(key)}: $rows Zeilen aus ${chans.size} Kanälen." + if (saved) " Das Bild liegt in Fotos unter Downlink." else ""
    }

    private fun grey(c: LrptChannel): Bitmap {
        val px = IntArray(c.width * c.rows) { val v = c.pixels[it].toInt() and 0xff; (0xff shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, c.width, c.rows, Bitmap.Config.ARGB_8888)
    }

    private fun rgb(r: LrptChannel, g: LrptChannel, b: LrptChannel): Bitmap {
        val w = r.width; val h = minOf(r.rows, g.rows, b.rows)
        val px = IntArray(w * h) { i -> (0xff shl 24) or ((r.pixels[i].toInt() and 0xff) shl 16) or ((g.pixels[i].toInt() and 0xff) shl 8) or (b.pixels[i].toInt() and 0xff) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun png(bmp: Bitmap, f: File) = FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Ab Android 10 braucht das Schreiben in die eigene Fotos-Mappe keine Berechtigung. */
    private fun toPhotos(ctx: Context, bmp: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Downlink")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore")
        r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        r.update(uri, values, null, null)
    }
}
