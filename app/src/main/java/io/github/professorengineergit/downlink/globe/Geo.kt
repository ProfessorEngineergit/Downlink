package io.github.professorengineergit.downlink.globe

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* --- Kugelgeometrie. Koordinaten wie im Globus: lon 0 zeigt nach +Z, Norden nach +Y --- */
object Geo {
    private const val R_KM = 6371.0
    const val DEG = PI / 180

    fun vec(latDeg: Double, lonDeg: Double): DoubleArray {
        val la = latDeg * DEG
        val lo = lonDeg * DEG
        return doubleArrayOf(cos(la) * sin(lo), sin(la), cos(la) * cos(lo))
    }

    fun latLon(v: DoubleArray): Pair<Double, Double> {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return asin((v[1] / n).coerceIn(-1.0, 1.0)) / DEG to atan2(v[0], v[2]) / DEG
    }

    fun angle(a: DoubleArray, b: DoubleArray) = acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0))

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) = angle(vec(lat1, lon1), vec(lat2, lon2)) * R_KM

    // Punkt auf dem Großkreis zwischen a und b
    fun slerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
        val w = angle(a, b)
        if (w < 1e-6) return a
        val s = sin(w)
        val ka = sin((1 - t) * w) / s
        val kb = sin(t * w) / s
        return doubleArrayOf(a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb)
    }

    // Punkt, an dem die Sonne zu dieser Zeit senkrecht steht (genau genug für Tag und Nacht)
    fun subsolar(utcMillis: Long): Pair<Double, Double> {
        val days = utcMillis / 86_400_000.0
        val dayOfYear = (days % 365.2422)
        val decl = -23.44 * cos(2 * PI / 365.2422 * (dayOfYear + 10))
        val hours = (utcMillis % 86_400_000L) / 3_600_000.0
        var lon = -15.0 * (hours - 12)
        if (lon < -180) lon += 360
        return decl to lon
    }
}
