package io.github.professorengineergit.downlink.sdr

/** Receives raw unsigned 8-bit I/Q pairs from the stick, called on the native reader thread. */
fun interface SampleSink { fun onSamples(buf: ByteArray, len: Int) }

/** Thin bridge to librtlsdr (src/main/cpp/rtl_jni.c). */
object RtlNative {
    init { System.loadLibrary("downlinksdr") }
    @JvmStatic external fun open(fd: Int): Long
    @JvmStatic external fun close(h: Long)
    @JvmStatic external fun tunerType(h: Long): Int
    @JvmStatic external fun setFrequency(h: Long, hz: Int): Int
    @JvmStatic external fun setSampleRate(h: Long, sps: Int): Int
    @JvmStatic external fun setPpm(h: Long, ppm: Int): Int
    @JvmStatic external fun setBiasTee(h: Long, on: Boolean): Int
    @JvmStatic external fun setGain(h: Long, tenthDb: Int): Int
    @JvmStatic external fun gains(h: Long): IntArray
    @JvmStatic external fun product(h: Long): String
    @JvmStatic external fun start(h: Long, sink: SampleSink): Boolean
    @JvmStatic external fun stop(h: Long)

    fun tunerName(t: Int) = when (t) { 1 -> "E4000"; 2 -> "FC0012"; 3 -> "FC0013"; 4 -> "FC2580"; 5 -> "R820T/R860"; 6 -> "R828D"; else -> "unbekannt" }
}
