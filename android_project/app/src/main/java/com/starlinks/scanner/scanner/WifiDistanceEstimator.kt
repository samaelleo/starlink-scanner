package com.starlinks.scanner.scanner

import kotlin.math.pow

object WifiDistanceEstimator {

    enum class Environment(val label: String, val pathLossExponent: Double) {
        OPEN_SPACE("فضای باز", 2.0),
        INDOOR_LIGHT("محیط داخلی خلوت", 2.5),
        INDOOR_NORMAL("محیط داخلی معمولی", 3.0),
        INDOOR_DENSE("محیط متراکم با دیوار", 3.8),
        MULTI_FLOOR("بین طبقات", 4.5)
    }

    enum class SignalQuality(val label: String, val colorHex: Long) {
        EXCELLENT("عالی", 0xFF10B981),
        GOOD("خوب", 0xFF06B6D4),
        FAIR("متوسط", 0xFFF59E0B),
        WEAK("ضعیف", 0xFFF97316),
        VERY_WEAK("بسیار ضعیف", 0xFFEF4444)
    }

    data class DistanceResult(
        val estimatedMeters: Double,
        val minMeters: Double,
        val maxMeters: Double,
        val signalQuality: SignalQuality,
        val accuracy: Int
    )

    fun referencePowerForFrequency(frequency: Int): Double {
        return if (frequency > 3000) -45.0 else -40.0
    }

    fun classifySignalQuality(rssi: Int): SignalQuality {
        return when {
            rssi >= -50 -> SignalQuality.EXCELLENT
            rssi >= -60 -> SignalQuality.GOOD
            rssi >= -70 -> SignalQuality.FAIR
            rssi >= -80 -> SignalQuality.WEAK
            else -> SignalQuality.VERY_WEAK
        }
    }

    fun estimateDistance(
        rssi: Int,
        frequency: Int,
        env: Environment = Environment.INDOOR_NORMAL
    ): DistanceResult {
        val p0 = referencePowerForFrequency(frequency)
        val n = env.pathLossExponent
        val exponent = (p0 - rssi.toDouble()) / (10.0 * n)
        val estimated = 10.0.pow(exponent)

        val uncertainty = if (n <= 2.5) 0.25 else if (n <= 3.5) 0.35 else 0.45
        val minDist = (estimated * (1.0 - uncertainty)).coerceAtLeast(0.5)
        val maxDist = estimated * (1.0 + uncertainty)

        val accuracy = when {
            rssi >= -50 -> 90
            rssi >= -65 -> 75
            rssi >= -75 -> 60
            rssi >= -85 -> 40
            else -> 25
        }

        return DistanceResult(
            estimatedMeters = (estimated * 10).toInt() / 10.0,
            minMeters = (minDist * 10).toInt() / 10.0,
            maxMeters = (maxDist * 10).toInt() / 10.0,
            signalQuality = classifySignalQuality(rssi),
            accuracy = accuracy
        )
    }

    fun estimateHeightDifference(pressureDiffHpa: Float): String {
        // Standard atmospheric lapse rate ~ 8.4 meters per hPa near sea level
        val heightMeters = pressureDiffHpa * 8.4
        return when {
            kotlin.math.abs(heightMeters) < 1.5 -> "هم‌سطح (Same Level)"
            heightMeters in 1.5..4.5 -> "~۱ طبقه پایین‌تر"
            heightMeters in -4.5..-1.5 -> "~۱ طبقه بالاتر"
            heightMeters in 4.5..8.0 -> "~۲ طبقه پایین‌تر"
            heightMeters in -8.0..-4.5 -> "~۲ طبقه بالاتر"
            heightMeters > 8.0 -> "+۳ طبقه پایین‌تر"
            else -> "+۳ طبقه بالاتر"
        }
    }
}
