package com.gigrun.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches current weather from Open-Meteo (no API key required). Used by the
 * heat-index safety check. Results are cached via [CachedWeather] so the network
 * is hit at most once per hour.
 */
object HeatIndexProvider {

    private const val CACHE_TTL_MS = 60 * 60 * 1000L

    data class Conditions(val temperatureC: Double, val humidity: Double, val fetchedAt: Long)

    private object Cache {
        @Volatile var last: Conditions? = null
    }

    /** Pure-network fetch against Open-Meteo current conditions. */
    suspend fun fetch(lat: Double, lon: Double): Conditions? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val current = JSONObject(body).getJSONObject("current")
            Conditions(
                temperatureC = current.getDouble("temperature_2m"),
                humidity = current.getDouble("relative_humidity_2m"),
                fetchedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    /** Respects the hourly cache; falls back to stale cache on network failure. */
    suspend fun current(lat: Double, lon: Double): Conditions? {
        val cached = Cache.last
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) return cached
        val fresh = fetch(lat, lon)
        if (fresh != null) Cache.last = fresh
        return fresh ?: cached
    }
}