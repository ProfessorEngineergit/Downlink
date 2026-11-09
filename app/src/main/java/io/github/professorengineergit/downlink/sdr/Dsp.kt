package io.github.professorengineergit.downlink.sdr

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/* Small DSP toolbox for the receiver: FFT, mixer, decimating FIR, FM discriminator. */

/** In-place radix-2 complex FFT. */
class Fft(val n: Int) {
    private val levels = Integer.numberOfTrailingZeros(n)
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2 * PI * it / n).toFloat() }
    private val rev = IntArray(n) { Integer.reverse(it) ushr (32 - levels) }
    val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }

    fun run(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) { val j = rev[i]; if (j > i) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t } }
        var size = 2
        while (size <= n) {
            val half = size / 2; val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val l = j + half
                    val tr = re[l] * cosT[k] + im[l] * sinT[k]
                    val ti = -re[l] * sinT[k] + im[l] * cosT[k]
                    re[l] = re[j] - tr; im[l] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }

    /** Power spectrum in dB, DC in the middle. */
    fun powerDb(re: FloatArray, im: FloatArray, out: FloatArray) {
        run(re, im)
        val h = n / 2
        for (i in 0 until n) {
            val k = (i + h) % n
            out[i] = (10 * log10((re[k] * re[k] + im[k] * im[k]) / (n.toFloat() * n) + 1e-12f)).toFloat()
        }
    }
}

/** Low-pass FIR design, windowed sinc (Blackman). cutoff as fraction of the sample rate. */
fun lowpass(taps: Int, cutoff: Double): FloatArray {
    val m = taps - 1
    val h = DoubleArray(taps) { i ->
        val x = i - m / 2.0
        val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
        sinc * (0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m))
    }
    val s = h.sum()
    return FloatArray(taps) { (h[it] / s).toFloat() }
}

/** Complex decimating FIR: keeps history across blocks, outputs every `factor`-th sample. */
class Decimator(private val taps: FloatArray, private val factor: Int) {
    private val n = taps.size
    private val hr = FloatArray(n * 2); private val hi = FloatArray(n * 2)
    private var pos = 0; private var phase = 0

    /** Returns number of output samples written to oRe/oIm. */
    fun process(iRe: FloatArray, iIm: FloatArray, count: Int, oRe: FloatArray, oIm: FloatArray): Int {
        var o = 0
        for (s in 0 until count) {
            hr[pos] = iRe[s]; hr[pos + n] = iRe[s]; hi[pos] = iIm[s]; hi[pos + n] = iIm[s]
            pos = (pos + 1) % n
            if (++phase == factor) {
                phase = 0
                var ar = 0f; var ai = 0f
                for (k in 0 until n) { val t = taps[k]; ar += hr[pos + k] * t; ai += hi[pos + k] * t }
                oRe[o] = ar; oIm[o] = ai; o++
            }
        }
        return o
    }
}

/** Numerically controlled oscillator: shifts a signal at +offset Hz down to 0 Hz. */
class Mixer(private val rate: Double) {
    private var c = 1.0; private var s = 0.0
    private var dc = 1.0; private var ds = 0.0
    var offsetHz = 0.0
        set(v) { field = v; val w = -2 * PI * v / rate; dc = cos(w); ds = sin(w) }

    fun process(re: FloatArray, im: FloatArray, count: Int) {
        var cc = c; var ss = s
        for (i in 0 until count) {
            val r = re[i]; val q = im[i]
            re[i] = (r * cc - q * ss).toFloat(); im[i] = (r * ss + q * cc).toFloat()
            val nc = cc * dc - ss * ds; ss = cc * ds + ss * dc; cc = nc
        }
        val mag = kotlin.math.sqrt(cc * cc + ss * ss); c = cc / mag; s = ss / mag
    }
}

/** Polar FM discriminator with a simple audio low-pass and DC block. */
class FmDemod(private val rate: Double, deviation: Double) {
    private var lr = 1f; private var li = 0f
    private val gain = (rate / (2 * PI * deviation)).toFloat()
    private var lp = 0f; private var dc = 0f
    private val a = (1 - kotlin.math.exp(-2 * PI * 3800.0 / rate)).toFloat()

    fun process(re: FloatArray, im: FloatArray, count: Int, out: FloatArray): Int {
        for (i in 0 until count) {
            val r = re[i] * lr + im[i] * li
            val q = im[i] * lr - re[i] * li
            lr = re[i]; li = im[i]
            val v = atan2(q, r) * gain
            lp += a * (v - lp)
            dc += 0.0005f * (lp - dc)
            out[i] = (lp - dc).coerceIn(-1f, 1f)
        }
        return count
    }
}
