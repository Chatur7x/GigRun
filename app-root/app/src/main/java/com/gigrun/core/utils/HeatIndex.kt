package com.gigrun.core.utils

/**
 * Heat Index (Robinson/Rothfusz simplified form) from ambient temperature and
 * relative humidity. Input: temperature in °C, relative humidity in % (0..100).
 * Returns heat index in °C. Standard Steadman/Rothfusz approximation.
 */
object HeatIndex {
    fun compute(temperatureC: Double, relativeHumidity: Double): Double {
        val t = temperatureC.coerceIn(-40.0, 60.0)
        val rh = relativeHumidity.coerceIn(0.0, 100.0)
        val T = t * 9.0 / 5.0 + 32.0 // to Fahrenheit
        // Rothfusz regression (full)
        var hi = -42.379 + 2.04901523 * T + 10.14333127 * rh - 0.22475541 * T * rh -
            0.00683783 * T * T - 0.05481717 * rh * rh + 0.00122874 * T * T * rh +
            0.00085282 * T * rh * rh - 0.00000199 * T * T * rh * rh
        // Low-humidity and high-temperature corrections
        if (rh < 13 && T in 80.0..112.0) {
            hi -= ((13 - rh) / 4.0) * Math.sqrt((17 - Math.abs(T - 95)) / 17.0)
        } else if (rh > 85 && T in 80.0..87.0) {
            hi += ((rh - 85) / 10.0) * ((87 - T) / 5.0)
        }
        return (hi - 32) * 5.0 / 9.0
    }

    /** True when conditions reach the "extreme heat / caution" tier that warrants a break. */
    fun isDangerous(temperatureC: Double, relativeHumidity: Double): Boolean =
        compute(temperatureC, relativeHumidity) > 40.0
}